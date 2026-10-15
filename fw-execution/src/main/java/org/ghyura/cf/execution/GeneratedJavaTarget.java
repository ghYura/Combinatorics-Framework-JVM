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

package org.ghyura.cf.execution;

import org.ghyura.cf.compiler.CandidateClassLoader;
import org.ghyura.cf.compiler.Compilation;
import org.ghyura.cf.compiler.CompiledClasses;
import org.ghyura.cf.compiler.ForbiddenCalls;
import org.ghyura.cf.compiler.JavaSourceCompiler;
import org.ghyura.cf.compiler.LineCoverage;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.Oracle;
import org.ghyura.cf.spi.SutCandidate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generated Java candidates (the legacy Reader and Executor path, architecture D06): each candidate's rendered source is
 * compiled in memory (cached by digest), checked for calls that would stop the virtual machine, and run in a class
 * loader of its own, so every sample starts with fresh static fields however often the bytecode is reused. The verdict is
 * the static int field {@code cf.execution.verdict} names, read after {@code public static void main(String[])} returned:
 * 0 passes, any other value is a domain failure kept exactly. Under FW_VAR, a nonzero verdict also names its carrier,
 * the sheet whose FW_EXIT_CODE equals it.
 *
 * <p>The arguments of main are the legacy ones: the FW_Arguments cells joined with spaces and split at single spaces,
 * or, when FW_RunMeFirstOnce is given, the String FW_ARGS that code leaves after running once for the run.</p>
 */
public final class GeneratedJavaTarget implements SutTarget {

    private final JavaSourceCompiler compiler;
    private final RunConfig.VerdictField verdict;
    private final Map<Integer, String> verdictNames;
    private final Map<Integer, String> carriers;
    private final URLClassLoader classpathLoader;
    private final ClassLoader parent;
    private final Map<String, List<String>> forbidden = new ConcurrentHashMap<>();
    private final String version;
    private final boolean lineCoverage;
    private final AtomicLong coverageOmitted = new AtomicLong();
    private volatile String coverageNote = "";
    private String[] arguments;

    /**
     * @param compiler     the run's compiler
     * @param verdict      the field holding the verdict
     * @param verdictNames verdict code names (the FW_CUSTOM_VAR sheet)
     * @param carriers     sheet names by FW_EXIT_CODE, for carrier attribution under FW_VAR
     * @param arguments    the FW_Arguments cells, in reading order
     */
    public GeneratedJavaTarget(JavaSourceCompiler compiler, RunConfig.VerdictField verdict, Map<Integer, String> verdictNames,
                               Map<Integer, String> carriers, List<String> arguments, List<String> extraClasspath) {
        this(compiler, verdict, verdictNames, carriers, arguments, extraClasspath, RunConfig.Coverage.NONE);
    }

    /**
     * @param coverage {@code cf.execution.coverage}: with LINES every candidate is instrumented ({@link LineCoverage}) and
     *                 each verdict sample reports coverage_line, coverage_line_covered and coverage_line_total
     */
    public GeneratedJavaTarget(JavaSourceCompiler compiler, RunConfig.VerdictField verdict, Map<Integer, String> verdictNames,
                               Map<Integer, String> carriers, List<String> arguments, List<String> extraClasspath,
                               RunConfig.Coverage coverage) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.lineCoverage = coverage == RunConfig.Coverage.LINES;
        this.verdict = Objects.requireNonNull(verdict, "verdict");
        this.verdictNames = Map.copyOf(verdictNames);
        this.carriers = Map.copyOf(carriers);
        this.arguments = legacyArguments(arguments);
        this.classpathLoader = extraClasspath.isEmpty() ? null : new URLClassLoader("cf-execution-classpath", urls(extraClasspath),
                GeneratedJavaTarget.class.getClassLoader());
        this.parent = classpathLoader != null ? classpathLoader : GeneratedJavaTarget.class.getClassLoader();
        this.version = "jdk-" + Runtime.version().feature();
    }

    /** The legacy argument line: " " + each FW_Arguments cell, then trim().split(" "). */
    static String[] legacyArguments(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (String cell : cells) {
            line.append(' ').append(cell);
        }
        String text = line.toString().trim();
        return text.isEmpty() ? new String[0] : text.split(" ");
    }

    private static URL[] urls(List<String> paths) {
        List<URL> urls = new ArrayList<>();
        for (String p : paths) {
            try {
                urls.add(Path.of(p).toAbsolutePath().normalize().toUri().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("not a class path entry: " + p, e);
            }
        }
        return urls.toArray(URL[]::new);
    }

    /** Bytecode cache counters of the run's compiler. */
    public JavaSourceCompiler.CacheStats cacheStats() {
        return compiler.cacheStats();
    }

    /** The arguments main receives, as prepared. */
    public List<String> arguments() {
        return List.of(arguments);
    }

    /**
     * Runs FW_RunMeFirstOnce once for the run, as the legacy executor did before any candidate: compile it, call its
     * main with the FW_Arguments line, and use the String FW_ARGS it leaves, split at single spaces, as every candidate's
     * arguments.
     *
     * @throws FrameworkException EXEC_PREPARE_FAILED when it does not compile, run or leave FW_ARGS
     */
    public void runFirstOnce(String source, SourceLocation where) {
        Compilation compilation = compiler.compile(source);
        if (compilation instanceof Compilation.Unavailable u) {
            throw prepareFailed("FW_RunMeFirstOnce cannot be compiled: " + u.reason(), where);
        }
        if (compilation instanceof Compilation.Failed failed) {
            throw prepareFailed("FW_RunMeFirstOnce does not compile: " + String.join(" | ", failed.messages()), where);
        }
        CompiledClasses classes = ((Compilation.Compiled) compilation).classes();
        if (!ForbiddenCalls.find(classes).isEmpty()) {
            throw prepareFailed("FW_RunMeFirstOnce would stop the virtual machine: " + ForbiddenCalls.find(classes), where);
        }
        try {
            Class<?> type = new CandidateClassLoader(classes, parent).loadClass(classes.mainType());
            Method main = type.getMethod("main", String[].class);
            main.setAccessible(true);
            main.invoke(null, (Object) arguments.clone());
            Field field = type.getDeclaredField("FW_ARGS");
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof String text)) {
                throw prepareFailed("FW_RunMeFirstOnce left FW_ARGS " + (value == null ? "null" : "of type " + value.getClass().getName())
                        + "; a String is required", where);
            }
            this.arguments = text.trim().split(" ");
        } catch (FrameworkException e) {
            throw e;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            throw prepareFailed("FW_RunMeFirstOnce failed: " + Failures.describe(Failures.unwrap(e)), where);
        }
    }

    private static FrameworkException prepareFailed(String message, SourceLocation where) {
        return new FrameworkException(Diagnostic.error(ErrorCode.EXEC_PREPARE_FAILED, message, where));
    }

    @Override
    public String id() {
        return RunConfig.GENERATED_JAVA;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public Map<Integer, String> verdictNames() {
        return verdictNames;
    }

    @Override
    public String sourceDigest(SutCandidate candidate) {
        // what runs is the instrumented code: the instrumentation version is part of the source identity
        return Canonical.sha256Hex(lineCoverage ? LineCoverage.VERSION + "\n" + candidate.source() : candidate.source());
    }

    /** Compiles the candidate (or takes it from the cache) and refuses VM-stopping calls, before the deadline starts. */
    @Override
    public Prepared prepare(SutCandidate candidate) {
        Compilation compilation = lineCoverage ? compiler.compileWithLineCoverage(candidate.source(), parent)
                : compiler.compile(candidate.source());
        CompiledClasses classes;
        switch (compilation) {
            case Compilation.Unavailable u -> {
                return Prepared.failed(new AttemptResult.Failure(Outcome.INFRA_FAIL, ErrorCode.EXEC_COMPILER_UNAVAILABLE, u.reason()));
            }
            case Compilation.Failed failed -> {
                return Prepared.failed(new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_COMPILE_ERROR,
                        String.join(" | ", failed.messages())));
            }
            case Compilation.Compiled compiled -> classes = compiled.classes();
        }
        List<String> stoppers = forbidden.computeIfAbsent(classes.digest(), d -> ForbiddenCalls.find(classes));
        if (!stoppers.isEmpty()) {
            return Prepared.failed(new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_FORBIDDEN_CALL, String.join("; ", stoppers)));
        }
        return new Prepared(classes, null);
    }

    @Override
    public AttemptResult attempt(SutCandidate candidate, Prepared prepared, SampleContext context) {
        CompiledClasses classes = (CompiledClasses) prepared.payload();
        Class<?> type;
        Method main;
        CandidateClassLoader loader = new CandidateClassLoader(classes, parent);
        try {
            type = loader.loadClass(classes.mainType());
            main = type.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_NO_ENTRY_POINT, "the class " + classes.mainType()
                    + " has no public static void main(String[])");
        } catch (ClassNotFoundException | LinkageError e) {
            return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_SUT_EXCEPTION, Failures.describe(e));
        }
        if (!Modifier.isStatic(main.getModifiers()) || main.getReturnType() != void.class) {
            return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_NO_ENTRY_POINT, "main of " + classes.mainType()
                    + " is not static void; the legacy executor called it statically");
        }
        try {
            main.setAccessible(true);
            main.invoke(null, (Object) arguments.clone());
        } catch (InvocationTargetException e) {
            return Failures.classify(e);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_SUT_EXCEPTION, Failures.describe(e));
        }
        int code;
        try {
            Field field = type.getDeclaredField(verdict.token());
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_NO_VERDICT, verdict.token() + " of " + classes.mainType()
                        + " is not a static int field");
            }
            field.setAccessible(true);
            code = field.getInt(null);
        } catch (NoSuchFieldException e) {
            return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_NO_VERDICT, "the class " + classes.mainType()
                    + " has no static int " + verdict.token() + " (cf.execution.verdict)");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_NO_VERDICT, Failures.describe(e));
        }
        Map<String, String> attributes = new java.util.TreeMap<>();
        Map<String, Double> metrics = new java.util.TreeMap<>();
        attributes.put("verdictField", verdict.token());
        if (classes.probes() != null) {
            coverage(loader, classes.probes(), metrics, attributes);
        }
        if (verdict == RunConfig.VerdictField.FW_VAR && code != 0 && carriers.containsKey(code)) {
            attributes.put("carrier", carriers.get(code));
        }
        return PluginTarget.judge(Oracle.VERDICT_CODE, candidate, new DomainObservation(code, metrics, attributes));
    }

    /** Reads this attempt's probes once, now that main returned: the share of the candidate's executable lines it ran. */
    private void coverage(CandidateClassLoader loader, LineCoverage.Probes probes, Map<String, Double> metrics, Map<String, String> attributes) {
        LineCoverage.Hits hits;
        try {
            hits = LineCoverage.read(loader, probes);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            omitted(attributes, "the probes could not be read: " + Failures.describe(e));
            return;
        }
        if (hits == null) {
            omitted(attributes, probes.note());
            return;
        }
        metrics.put("coverage_line", hits.fraction());
        metrics.put("coverage_line_covered", (double) hits.covered());
        metrics.put("coverage_line_total", (double) hits.total());
    }

    private void omitted(Map<String, String> attributes, String why) {
        attributes.put("coverage", "omitted: " + why);
        if (coverageOmitted.getAndIncrement() == 0) {
            coverageNote = why;
        }
    }

    /** Verdict samples whose line coverage was omitted (no executable line of the candidate's own), and the first reason. */
    public long coverageOmitted() {
        return coverageOmitted.get();
    }

    public String coverageNote() {
        return coverageNote;
    }

    @Override
    public void close() {
        compiler.clear();
        if (classpathLoader != null) {
            try {
                classpathLoader.close();
            } catch (IOException e) {
                // the loader only reads jars and directories; failing to close one leaks a file handle, nothing else
            }
        }
    }

    @Override
    public boolean harvestsMetricLines() {
        return true;
    }
}
