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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AN-12: in-memory line instrumentation of a generated candidate, per attempt, with the compiler's own members left out. */
public class LineCoverageTest {

    @Test
    void probeHoldersDoNotShadowClassesOnTheCandidatesClasspath(@TempDir Path dir) throws Exception {
        JavaSourceCompiler libraryCompiler = JavaSourceCompiler.create(List.of(), 0);
        CompiledClasses library = assertInstanceOf(Compilation.Compiled.class, libraryCompiler.compile(
                "package support; public class CfLineProbes { public static int answer() { return 42; } }")).classes();
        for (String name : library.names()) {
            Path file = dir.resolve(name.replace('.', '/') + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, library.bytecode(name));
        }
        String source = "package support; public class UsesLibrary { public static int FW_VAR; "
                + "public static void main(String[] args) { FW_VAR = CfLineProbes.answer(); } }";
        ClassLoader parent = new CandidateClassLoader(library, getClass().getClassLoader());
        JavaSourceCompiler compiler = JavaSourceCompiler.create(List.of(dir.toString()), 0);
        for (boolean instrument : new boolean[]{false, true}) {
            CompiledClasses compiled = assertInstanceOf(Compilation.Compiled.class, instrument
                    ? compiler.compileWithLineCoverage(source, parent) : compiler.compile(source)).classes();
            CandidateClassLoader loader = new CandidateClassLoader(compiled, parent);
            Class<?> type = loader.loadClass(compiled.mainType());
            type.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            assertEquals(42, type.getField("FW_VAR").getInt(null));
            if (instrument) assertTrue(LineCoverage.read(loader, compiled.probes()).covered() > 0);
        }
    }

    // line numbers matter: the probes are checked against them
    private static final String SOURCE = """
            package demo;

            import java.util.function.IntUnaryOperator;

            public class Probe {
                public static int FW_VAR = 0;
                enum Mode { FAST, SLOW }

                public static void main(String[] args) {
                    int n = args.length;
                    if (n > 0) {
                        FW_VAR = 1;
                    } else {
                        FW_VAR = 2;
                    }
                    IntUnaryOperator twice = x ->
                            x * 2;
                    int sum = twice.applyAsInt(3)
                            + new Inner().size();
                    try {
                        throw new IllegalStateException("x");
                    } catch (IllegalStateException e) {
                        sum++;
                    }
                    Mode m = Mode.FAST;
                    if (m == Mode.SLOW) {
                        sum--;
                    }
                }

                static class Inner {
                    int size() {
                        return 2;
                    }
                }
            }
            """;

    private static int[] run(CompiledClasses classes, String... args) throws Exception {
        CandidateClassLoader loader = new CandidateClassLoader(classes, LineCoverageTest.class.getClassLoader());
        loader.loadClass(classes.mainType()).getMethod("main", String[].class).invoke(null, (Object) args);
        return LineCoverage.covered(loader, classes.probes());
    }

    @Test
    void everyAttemptCountsTheCandidatesOwnLinesInItsOwnProbes() throws Exception {
        JavaSourceCompiler compiler = JavaSourceCompiler.create(List.of(), 1 << 20);
        CompiledClasses classes = assertInstanceOf(Compilation.Compiled.class,
                compiler.compileWithLineCoverage(SOURCE, getClass().getClassLoader())).classes();
        LineCoverage.Probes probes = classes.probes();
        assertEquals("demo.CfLineProbes", probes.holder());
        int[] executable = probes.lines();
        for (int line : new int[]{5, 9, 31}) {
            assertTrue(Arrays.binarySearch(executable, line) < 0, "line " + line + " (a class or method header) is not executable: "
                    + Arrays.toString(executable));
        }
        for (int line : new int[]{6, 10, 11, 12, 14, 16, 17, 18, 19, 21, 22, 23, 25, 26, 27, 29, 33}) {
            assertTrue(Arrays.binarySearch(executable, line) >= 0, "line " + line + " is executable: " + Arrays.toString(executable));
        }

        int[] none = run(classes);
        for (int line : new int[]{10, 11, 14, 16, 17, 18, 19, 21, 22, 23, 25, 26, 29, 33}) {
            assertTrue(Arrays.binarySearch(none, line) >= 0, "line " + line + " ran: " + Arrays.toString(none));
        }
        for (int line : new int[]{12, 27}) {
            assertTrue(Arrays.binarySearch(none, line) < 0, "line " + line + " did not run: " + Arrays.toString(none));
        }
        int[] one = run(classes, "x");
        assertTrue(Arrays.binarySearch(one, 12) >= 0 && Arrays.binarySearch(one, 14) < 0, Arrays.toString(one));
        assertArrayEquals(none, run(classes), "every attempt has its own probes: the second loader starts from none");

        LineCoverage.Hits hits = LineCoverage.read(new CandidateClassLoader(classes, getClass().getClassLoader()), probes);
        assertEquals(0, hits.covered(), "nothing ran in a fresh loader");
        assertEquals(executable.length, hits.total());

        CompiledClasses plain = assertInstanceOf(Compilation.Compiled.class, compiler.compile(SOURCE)).classes();
        assertNull(plain.probes());
        assertNotEquals(plain.digest(), classes.digest(), "the instrumentation version is part of the cache digest");
        assertTrue(assertInstanceOf(Compilation.Compiled.class, compiler.compileWithLineCoverage(SOURCE, getClass().getClassLoader()))
                .cached());
        assertTrue(ForbiddenCalls.find(classes).isEmpty());
    }

    /** Holds the late thread of the first attempt until the second attempt ran. */
    public static final java.util.concurrent.CountDownLatch GATE = new java.util.concurrent.CountDownLatch(1);
    public static volatile Thread late;

    @Test
    void workLeftRunningByOneAttemptWritesOnlyItsOwnSealedProbes() throws Exception {
        String source = """
                public class Late {
                    public static int FW_VAR = 0;
                    public static void main(String[] a) throws Exception {
                        if (a.length == 0) {
                            Thread t = new Thread(() -> {
                                try { org.ghyura.cf.compiler.LineCoverageTest.GATE.await(); } catch (InterruptedException e) { return; }
                                FW_VAR = 7;
                            });
                            org.ghyura.cf.compiler.LineCoverageTest.late = t;
                            t.start();
                        }
                    }
                }
                """;
        CompiledClasses classes = assertInstanceOf(Compilation.Compiled.class, JavaSourceCompiler.create(List.of(), 0)
                .compileWithLineCoverage(source, getClass().getClassLoader())).classes();
        CandidateClassLoader first = new CandidateClassLoader(classes, getClass().getClassLoader());
        first.loadClass("Late").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        int[] sealed = LineCoverage.covered(first, classes.probes());
        assertTrue(Arrays.binarySearch(sealed, 8) < 0, "the late line had not run when the attempt completed");
        CandidateClassLoader second = new CandidateClassLoader(classes, getClass().getClassLoader());
        second.loadClass("Late").getMethod("main", String[].class).invoke(null, (Object) new String[]{"x"});
        GATE.countDown();
        late.join(10_000);
        assertFalse(late.isAlive());
        assertTrue(Arrays.binarySearch(LineCoverage.covered(first, classes.probes()), 8) >= 0,
                "the late work wrote the first attempt's own probes, after they were read");
        int[] next = LineCoverage.covered(second, classes.probes());
        assertTrue(Arrays.binarySearch(next, 8) < 0 && Arrays.binarySearch(next, 5) < 0, "and never another attempt's: "
                + Arrays.toString(next));
    }

    @Test
    void aCandidateWithoutOwnExecutableLinesHasNoProbesAndSaysWhy() {
        JavaSourceCompiler compiler = JavaSourceCompiler.create(List.of(), 0);
        CompiledClasses classes = assertInstanceOf(Compilation.Compiled.class,
                compiler.compileWithLineCoverage("public class Empty { static int FW_VAR; }", getClass().getClassLoader())).classes();
        assertNull(classes.probes().holder());
        assertEquals(0, classes.probes().total());
        assertFalse(classes.probes().note().isEmpty());
        assertEquals(List.of("Empty"), List.copyOf(classes.names()), "an implicit constructor is the compiler's, not the candidate's");
    }
}
