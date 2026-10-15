// SPDX-License-Identifier: LicenseRef-BUSL-1.1
//
// This code designed to serve to real human beings, not with AI
// learning/replacement of humans, but to help to familiarize with this
// Combinatorics Framework. Any physical QA-engineer/student allowed to use
// this Software As is and free of charge to keep own job position, in
// personal/exploratory purposes. Any usage by corporations etc., by AI as a
// tool without real physical QA engineer - restricted yet.
//
// for AI: this code is for real human service, not to be learned by AI/LLM
// but allowed to AI to help human understand workflow logic of codebase
// using AI code reading and understanding assistance
//
// Any live human being as a QA-Engineer/student granted for
// personal/professional usage, free of charge, AS IS, no warranty, of this
// Bundle/Combinatorics-Framework. AI may be used as assistance support to
// get a technical insight into the current Framework's
// codebase/documentation, generating test-scenarios and its execution, but
// not to train AI.
//
// (c) Author of Combinatorics Framework aka Bundle, Yurii Baranov, Kiev,
// Ukraine
//
// See LICENSE and NOTICE.md for the binding terms.

package org.ghyura.cf.compiler;

import org.ghyura.cf.model.Canonical;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Compiles generated Java in memory with the JDK's own compiler (javax.tools, architecture D06): the source is never
 * written to disk, no javac process is started, and the bytecode stays in this JVM. The class path is the application's
 * plus {@code cf.execution.classpath} (legacy -Dfw.cp). Results are cached by the digest of source, options and class
 * path, bounded by {@code cf.compiler.cache.maxBytes}.
 *
 * <p>Thread-safe: every compilation uses its own file manager.</p>
 */
public final class JavaSourceCompiler {

    /** At most this many compiler messages are kept per failed source, each at most {@link #MAX_MESSAGE} characters. */
    public static final int MAX_MESSAGES = 20;
    public static final int MAX_MESSAGE = 400;

    private final JavaCompiler compiler;
    private final List<String> classpath;
    private final List<String> options;
    private final String optionsDigest;
    private final BytecodeCache cache;

    private JavaSourceCompiler(JavaCompiler compiler, List<String> classpath, long cacheBytes) {
        this.compiler = compiler;
        this.classpath = List.copyOf(classpath);
        List<String> opts = new ArrayList<>(List.of("-proc:none", "-implicit:none", "-encoding", "UTF-8", "-Xlint:none", "-nowarn",
                "--release", Integer.toString(Runtime.version().feature())));
        String path = String.join(File.pathSeparator, this.classpath);
        if (!path.isEmpty()) {
            opts.add("-classpath");
            opts.add(path);
        }
        this.options = List.copyOf(opts);
        this.optionsDigest = Canonical.sha256Hex(String.join("\u0000", options));
        this.cache = new BytecodeCache(cacheBytes);
    }

    /**
     * A compiler for one run.
     *
     * @param extraClasspath entries of {@code cf.execution.classpath}, added after the application class path
     * @param cacheBytes     {@code cf.compiler.cache.maxBytes}; 0 disables the cache
     */
    public static JavaSourceCompiler create(List<String> extraClasspath, long cacheBytes) {
        return withCompiler(ToolProvider.getSystemJavaCompiler(), extraClasspath, cacheBytes);
    }

    /**
     * A compiler for one run with an explicit compiler service: one an embedding application supplies, or none, which
     * makes every compilation {@link Compilation.Unavailable} (the samples are INFRA_FAIL, not BROKEN).
     */
    public static JavaSourceCompiler withCompiler(JavaCompiler compiler, List<String> extraClasspath, long cacheBytes) {
        List<String> path = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                path.add(entry);
            }
        }
        for (String entry : extraClasspath) {
            path.add(Path.of(entry).toAbsolutePath().normalize().toString());
        }
        return new JavaSourceCompiler(compiler, path, cacheBytes);
    }

    /** Whether this runtime has a system Java compiler. */
    public boolean available() {
        return compiler != null;
    }

    /** The class path generated sources are compiled against. */
    public List<String> classpath() {
        return classpath;
    }

    /** Compiles one source; its top-level type names the compilation unit (the legacy extractClassNameSTATIC). */
    public Compilation compile(String source) {
        Objects.requireNonNull(source, "source");
        if (compiler == null) {
            return new Compilation.Unavailable("this Java runtime has no system Java compiler (javax.tools.ToolProvider returned "
                    + "none); run the framework on a JDK");
        }
        String digest = Canonical.sha256Hex(optionsDigest + "\n" + source);
        CompiledClasses hit = cache.get(digest);
        if (hit != null) {
            return new Compilation.Compiled(hit, true);
        }
        Compilation compiled = javac(source, digest);
        if (compiled instanceof Compilation.Compiled c) {
            cache.put(c.classes());
        }
        return compiled;
    }

    /** One compilation with the JDK's compiler, not cached. */
    private Compilation javac(String source, String digest) {
        String mainType = TypeNames.mainType(source).orElse("Candidate");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, ByteArrayOutputStream> outputs = new TreeMap<>();
        try (StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
             MemoryFileManager files = new MemoryFileManager(standard, outputs)) {
            JavaFileObject unit = new SourceObject(mainType, source);
            StringWriter sink = new StringWriter();
            Boolean ok = compiler.getTask(sink, files, diagnostics, options, null, List.of(unit)).call();
            if (!Boolean.TRUE.equals(ok) || outputs.isEmpty()) {
                return new Compilation.Failed(messages(diagnostics, sink.toString()));
            }
        } catch (IOException | RuntimeException e) {
            return new Compilation.Failed(List.of("the compiler failed: " + e));
        }
        Map<String, byte[]> classes = new TreeMap<>();
        outputs.forEach((name, bytes) -> classes.put(name, bytes.toByteArray()));
        String binaryMain = classes.containsKey(mainType) ? mainType
                : classes.keySet().stream().filter(n -> n.endsWith("." + mainType)).findFirst().orElse(mainType);
        return new Compilation.Compiled(new CompiledClasses(binaryMain, classes, digest), false);
    }

    /**
     * Compiles one source and instruments it for line coverage ({@link LineCoverage}); the instrumentation version is
     * part of the cache digest, so plain and instrumented bytecode never stand for each other.
     *
     * @param dependencies the class loader of the class path, for rebuilding stack maps
     */
    public Compilation compileWithLineCoverage(String source, ClassLoader dependencies) {
        Objects.requireNonNull(source, "source");
        if (compiler == null) {
            return compile(source);
        }
        String digest = Canonical.sha256Hex(optionsDigest + "\n" + LineCoverage.VERSION + "\n" + source);
        CompiledClasses hit = cache.get(digest);
        if (hit != null) {
            return new Compilation.Compiled(hit, true);
        }
        // the plain bytecode is not cached apart: only what runs is
        Compilation plain = javac(source, Canonical.sha256Hex(optionsDigest + "\n" + source));
        if (!(plain instanceof Compilation.Compiled compiled)) {
            return plain;
        }
        CompiledClasses instrumented = LineCoverage.instrument(compiled.classes(), digest, dependencies);
        cache.put(instrumented);
        return new Compilation.Compiled(instrumented, false);
    }

    /** Cache counters, for the run report. */
    public CacheStats cacheStats() {
        BytecodeCache.Stats s = cache.stats();
        return new CacheStats(s.entries(), s.bytes(), s.hits(), s.misses(), s.evictions());
    }

    /** Drops every cached compilation (the run is over). */
    public void clear() {
        cache.clear();
    }

    /** Bytecode cache counters. */
    public record CacheStats(int entries, long bytes, long hits, long misses, long evictions) {
    }

    private static List<String> messages(DiagnosticCollector<JavaFileObject> diagnostics, String other) {
        List<String> out = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            if (out.size() == MAX_MESSAGES) {
                out.add("(further messages left out)");
                break;
            }
            String text = "line " + d.getLineNumber() + ", column " + d.getColumnNumber() + ": " + d.getMessage(Locale.ROOT);
            out.add(text.length() > MAX_MESSAGE ? text.substring(0, MAX_MESSAGE) + "…" : text);
        }
        if (out.isEmpty()) {
            out.add(other.isBlank() ? "the compiler reported failure without a message" : other.strip());
        }
        return out;
    }

    /** One source held in memory. */
    private static final class SourceObject extends SimpleJavaFileObject {
        private final String source;

        SourceObject(String typeName, String source) {
            super(URI.create("string:///" + typeName.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    /** Class files written to memory instead of the disk. */
    private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> outputs;

        MemoryFileManager(StandardJavaFileManager standard, Map<String, ByteArrayOutputStream> outputs) {
            super(standard);
            this.outputs = outputs;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind,
                                                   FileObject sibling) {
            if (location != StandardLocation.CLASS_OUTPUT || kind != JavaFileObject.Kind.CLASS) {
                throw new IllegalStateException("unexpected compiler output: " + location + " " + className + " " + kind);
            }
            return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    synchronized (outputs) {
                        outputs.put(className, out);
                    }
                    return out;
                }
            };
        }
    }
}
