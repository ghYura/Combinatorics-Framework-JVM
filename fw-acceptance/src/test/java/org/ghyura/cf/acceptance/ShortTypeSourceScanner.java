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

package org.ghyura.cf.acceptance;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds every use of the sixteen-bit Java integer type in Java source (R04). It works on the parse tree, so
 * comments and string literals never count, and it sees what a text search would miss: casts, generic
 * arguments, static imports and member selections.
 *
 * <p>The forbidden names are spelled as string data here on purpose; this file is itself scanned and must stay
 * clean.</p>
 */
final class ShortTypeSourceScanner {

    /** Identifiers that are the 16-bit integer's wrapper, buffers or the readers/writers of 16-bit values. */
    static final Set<String> BANNED_NAMES = Set.of("Sh" + "ort", "Sh" + "ortBuffer", "read" + "Short", "write" + "Short",
            "get" + "Short", "put" + "Short", "asSh" + "ortBuffer", "sh" + "ortValue", "parseSh" + "ort");

    record Finding(String source, long line, String what) {
        @Override
        public String toString() {
            return source + ":" + line + " " + what;
        }
    }

    private ShortTypeSourceScanner() {
    }

    static List<Finding> scan(List<Path> files) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("no system Java compiler: run the gates on a JDK, not a JRE");
        }
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            return scanUnits(compiler, fm.getJavaFileObjectsFromPaths(files));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** For the scanner's own positive controls: scan source text held in memory. */
    static List<Finding> scanText(String name, String code) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject unit = new SimpleJavaFileObject(URI.create("string:///" + name), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
        return scanUnits(compiler, List.of(unit));
    }

    private static List<Finding> scanUnits(JavaCompiler compiler, Iterable<? extends JavaFileObject> units) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, units);
        List<Finding> findings = new ArrayList<>();
        try {
            Trees trees = Trees.instance(task);
            SourcePositions positions = trees.getSourcePositions();
            for (CompilationUnitTree unit : task.parse()) {
                String name = unit.getSourceFile().getName();
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitPrimitiveType(PrimitiveTypeTree tree, Void ignored) {
                        if (tree.getPrimitiveTypeKind() == TypeKind.SHORT) {
                            findings.add(finding(unit, positions, tree, name, "primitive 16-bit integer type"));
                        }
                        return super.visitPrimitiveType(tree, ignored);
                    }

                    @Override
                    public Void visitIdentifier(IdentifierTree tree, Void ignored) {
                        if (BANNED_NAMES.contains(tree.getName().toString())) {
                            findings.add(finding(unit, positions, tree, name, "identifier " + tree.getName()));
                        }
                        return super.visitIdentifier(tree, ignored);
                    }

                    @Override
                    public Void visitMemberSelect(MemberSelectTree tree, Void ignored) {
                        if (BANNED_NAMES.contains(tree.getIdentifier().toString())) {
                            findings.add(finding(unit, positions, tree, name, "member " + tree.getIdentifier()));
                        }
                        return super.visitMemberSelect(tree, ignored);
                    }
                }.scan(unit, null);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                throw new IllegalStateException("a source file does not parse, so it cannot be scanned: " + d);
            }
        }
        return findings;
    }

    private static Finding finding(CompilationUnitTree unit, SourcePositions positions, Tree tree, String source, String what) {
        long position = positions.getStartPosition(unit, tree);
        long line = position < 0 ? -1 : unit.getLineMap().getLineNumber(position);
        return new Finding(source, line, what);
    }
}
