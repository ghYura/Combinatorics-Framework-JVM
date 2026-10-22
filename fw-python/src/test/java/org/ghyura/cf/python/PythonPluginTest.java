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

package org.ghyura.cf.python;

import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.MemoryReservation;
import org.ghyura.cf.spi.OutputCapture;
import org.ghyura.cf.spi.RunInputs;
import org.ghyura.cf.spi.SheetSelection;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutSession;
import org.ghyura.cf.spi.SutUnavailable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Python add-on against the legacy executor's contract (py_executor.run_candidate), with candidates written by the
 * tests themselves (the project holds no Python files): verdicts, the custom verdict, no verdict, arguments, working
 * directory and PYTHONPATH, the preprocessor, captured output, an interpreter that is missing, and cancellation that
 * kills the candidate and the process it started.
 */
class PythonPluginTest {

    @BeforeAll
    static void needsPython() {
        boolean found = false;
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            found |= !dir.isEmpty() && Files.isExecutable(Path.of(dir, "python3"));
        }
        Assumptions.assumeTrue(found, "MISSING_AUTHORIZED_BACKEND: no python3 on PATH, so the Python add-on the author requires "
                + "(decision 2026-10-12) cannot be verified here");
    }

    /** A sample's context: bounded capture, a cancellation flag, nothing else used. */
    static final class Context implements ExecutionContext {
        final StringBuilder out = new StringBuilder();
        final StringBuilder err = new StringBuilder();
        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public SampleKey sample() {
            return null;
        }

        @Override
        public Cancellation cancellation() {
            return cancelled::get;
        }

        @Override
        public Duration remaining() {
            return Duration.ofMinutes(1);
        }

        @Override
        public RandomGenerator random() {
            return RandomGenerator.getDefault();
        }

        @Override
        public OutputCapture output() {
            return new OutputCapture() {
                @Override
                public void stdout(String text) {
                    synchronized (out) {
                        out.append(text);
                    }
                }

                @Override
                public void stderr(String text) {
                    synchronized (err) {
                        err.append(text);
                    }
                }

                @Override
                public boolean truncated() {
                    return false;
                }
            };
        }

        @Override
        public MemoryReservation reserve(long bytes, String purpose) {
            return new MemoryReservation() {
                @Override
                public long bytes() {
                    return bytes;
                }

                @Override
                public void close() {
                }
            };
        }

        String out() {
            synchronized (out) {
                return out.toString();
            }
        }

        String err() {
            synchronized (err) {
                return err.toString();
            }
        }
    }

    static SutCandidate candidate(String source) {
        return new SutCandidate() {
            @Override
            public CandidateId id() {
                return new CandidateId("0".repeat(CandidateId.HEX_LENGTH));
            }

            @Override
            public String displayId() {
                return "7_0_0";
            }

            @Override
            public BigInteger ordinal() {
                return BigInteger.ZERO;
            }

            @Override
            public List<SheetSelection> selections() {
                return List.of();
            }

            @Override
            public String source() {
                return source;
            }
        };
    }

    static PythonPlugin plugin(List<String> arguments, Optional<String> once, String verdict, Map<String, String> settings) {
        return (PythonPlugin) new PythonPlugin().forRun(new RunInputs(arguments, once, verdict, Map.of(), Optional.empty(), settings));
    }

    static DomainObservation evaluate(PythonPlugin plugin, String source, Context context) throws Exception {
        try (SutSession session = plugin.open(context)) {
            return session.evaluate(candidate(source), context);
        }
    }

    @Test
    void theVerdictIsFwVarZeroPassesAndAnyOtherCodeIsKeptExactly() throws Exception {
        PythonPlugin p = plugin(List.of(), Optional.empty(), "FW_VAR", Map.of());
        assertEquals(0, evaluate(p, "x = 1\nif x:\n    FW_VAR = 0\n", new Context()).verdictCode());
        assertEquals(31, evaluate(p, "FW_VAR = 31\n", new Context()).verdictCode());
        assertEquals(-999, evaluate(p, "y = 2\n", new Context()).verdictCode(), "a candidate that sets no FW_VAR gets the legacy -999");
        assertEquals(PythonPlugin.ID, p.id());
        assertTrue(p.harvestsMetricLines(), "the legacy app= metrics line becomes metrics");
    }

    @Test
    void underFwCustomVarTheCustomVariableIsTheVerdict() throws Exception {
        PythonPlugin p = plugin(List.of(), Optional.empty(), "FW_CUSTOM_VAR", Map.of());
        assertEquals(3, evaluate(p, "FW_VAR = 0\nFW_CUSTOM_VAR = 3\n", new Context()).verdictCode());
    }

    @Test
    void anExceptionASyntaxErrorOrAnEmptyCandidateGiveNoVerdict() throws Exception {
        PythonPlugin p = plugin(List.of(), Optional.empty(), "FW_VAR", Map.of());
        Context raised = new Context();
        assertNull(evaluate(p, "raise ValueError('the system under test refused')\nFW_VAR = 0\n", raised));
        assertTrue(raised.err().contains("ValueError: the system under test refused"), raised.err());
        assertNull(evaluate(p, "def broken(:\n", new Context()), "a syntax error");
        assertNull(evaluate(p, "", new Context()), "an empty candidate");
        assertNull(evaluate(p, "FW_VAR = 'not a number'\n", new Context()), "the runner cannot print a verdict it cannot convert");
    }

    @Test
    void argumentsWorkingDirectoryAndPythonPathReachTheCandidate(@TempDir Path temp) throws Exception {
        Path work = Files.createDirectories(temp.resolve("work"));
        Path lib = Files.createDirectories(temp.resolve("lib"));
        Files.writeString(lib.resolve("cf_helper.py"), "VALUE = 5\n", StandardCharsets.UTF_8);
        PythonPlugin p = plugin(List.of("noargs", "two words"), Optional.empty(), "FW_VAR",
                Map.of("cf.python.workdir", work.toString(), "cf.python.path", lib.toString()));
        Context context = new Context();
        String source = "import os, sys, cf_helper\n"
                + "print('argv', sys.argv[2:], 'cwd', os.getcwd(), 'value', cf_helper.VALUE)\n"
                + "FW_VAR = 0 if sys.argv[2:] == ['noargs', 'two words'] and os.path.samefile(os.getcwd(), " + pyString(work.toString())
                + ") and cf_helper.VALUE == 5 else 1\n";
        assertEquals(0, evaluate(p, source, context).verdictCode(), context.out() + context.err());
        assertEquals(work, p.workdir());
        assertFalse(Files.exists(lib.resolve("__pycache__")), "no bytecode cache is written beside the imported code");
    }

    /**
     * cf.python.sandbox=bwrap (author 2026-10-13, a workaround for the legacy Executor sandbox): the unchanged candidate still
     * runs and gives its verdict, but cannot write into its working directory and has no network, only its own loopback
     * interface; it can write its private /tmp. bwrap-network keeps the host's interfaces; the file system stays read-only.
     */
    @Test
    void aBwrapSandboxKeepsTheFileSystemReadOnlyAndTheNetworkClosed(@TempDir Path temp) throws Exception {
        Assumptions.assumeTrue(onPath("bwrap"), "bwrap is not installed, so the Python sandbox cannot be verified here");
        var probe = PythonDoctor.probe("python3", temp, Map.of(), Sandbox.of("bwrap"), List.of("pass\n"));
        boolean namespacesDenied = probe.stream().flatMap(check -> check.details().stream()).anyMatch(detail ->
                detail.contains("No permissions to create new namespace")
                        || detail.contains("Creating new namespace failed: Operation not permitted"));
        Assumptions.assumeFalse(namespacesDenied,
                "bwrap cannot create namespaces here: the Python sandbox cannot be verified: " + probe);
        Path work = Files.createDirectories(temp.resolve("work"));
        List<String> host;
        try (var interfaces = Files.list(Path.of("/sys/class/net"))) {
            host = interfaces.map(p -> p.getFileName().toString()).sorted().toList();
        }
        String source = "import os, socket, tempfile\n"
                + "def wrote(path):\n    try:\n        open(path, 'w').write('x')\n        return True\n    except OSError:\n        return False\n"
                + "names = sorted(n for _, n in socket.if_nameindex())\n"
                + "print('interfaces', names)\n"
                + "FW_VAR = (1 if wrote(os.path.join(os.getcwd(), 'escaped.txt')) else 0) + (0 if wrote(os.path.join(tempfile.gettempdir(), "
                + "'scratch.txt')) else 2) + (0 if names == ['lo'] else 4) + (8 if names == " + pyList(host) + " else 0)\n";
        Context closed = new Context();
        int expectedClosed = host.equals(List.of("lo")) ? 8 : 0;
        assertEquals(expectedClosed, evaluate(plugin(List.of(), Optional.empty(), "FW_VAR", Map.of("cf.python.workdir", work.toString(),
                "cf.python.sandbox", "bwrap")), source, closed).verdictCode(), closed.out() + closed.err());
        Context open = new Context();
        assertEquals(host.equals(List.of("lo")) ? 8 : 12, evaluate(plugin(List.of(), Optional.empty(), "FW_VAR", Map.of("cf.python.workdir",
                work.toString(), "cf.python.sandbox", "bwrap-network")), source, open).verdictCode(), open.out() + open.err());
        assertFalse(Files.exists(work.resolve("escaped.txt")), "nothing was written into the working directory");
        List<org.ghyura.cf.spi.SutDoctor.Check> blocked = PythonDoctor.probe(temp.resolve("absent-interpreter").toString(), work, Map.of(),
                Sandbox.of("bwrap"), List.of("import os\n"));
        assertEquals(org.ghyura.cf.model.Severity.ERROR, blocked.getFirst().severity(), "a sandbox that cannot start the interpreter blocks: " + blocked);
        assertTrue(blocked.getFirst().message().contains("could not start the interpreter"), blocked.toString());
    }

    private static String pyList(List<String> names) {
        StringBuilder b = new StringBuilder("[");
        for (String n : names) {
            b.append(b.length() > 1 ? ", " : "").append(pyString(n));
        }
        return b.append(']').toString();
    }

    /**
     * cf.python.sandbox=docker:IMAGE@NETWORK wraps the unchanged command as the legacy sandbox built its container
     * (Executor_trunk/sandbox.py): never pulled, read-only, no capabilities, only the add-on's own variables, the working
     * directory read-only at its own path, the candidate's directory writable, the named network; without one, none.
     */
    @Test
    void aDockerSandboxWrapsTheUnchangedCommandAsTheLegacySandboxBuiltItsContainer(@TempDir Path temp) {
        Path candidateDir = temp.resolve("cf-python-1");
        List<String> command = List.of("python3", "-c", "RUNNER", candidateDir.resolve("7_0_0.py").toString());
        List<String> wrapped = Sandbox.of("docker:as0927_d14b_client:20261012t185442z@as0927_d14b_net_20261012t185442z").wrap(command, temp,
                Optional.of(candidateDir), Map.of("D14B_FIXTURE", "as0927_d14b_sql_20261012t185442z", "DOCKER_HOST", "unix:///run/docker.sock",
                        "PYTHONPATH", temp.toString()), "cf-python-test");
        assertEquals(List.of("docker", "run", "--rm", "-i", "--pull", "never", "--name", "cf-python-test", "--network",
                "as0927_d14b_net_20261012t185442z", "--read-only", "--cap-drop", "ALL", "--security-opt", "no-new-privileges"), wrapped.subList(0, 15));
        assertTrue(java.util.Collections.indexOfSubList(wrapped, List.of("--env", "D14B_FIXTURE")) > 0, wrapped.toString());
        assertEquals(-1, java.util.Collections.indexOfSubList(wrapped, List.of("--env", "DOCKER_HOST")), "the client's own variables stay outside");
        assertTrue(java.util.Collections.indexOfSubList(wrapped, List.of("--volume", temp + ":" + temp + ":ro")) > 0, wrapped.toString());
        assertTrue(java.util.Collections.indexOfSubList(wrapped, List.of("--volume", candidateDir + ":" + candidateDir)) > 0, wrapped.toString());
        List<String> tail = new java.util.ArrayList<>(List.of("--workdir", temp.toString(), "as0927_d14b_client:20261012t185442z"));
        tail.addAll(command);
        assertEquals(tail, wrapped.subList(wrapped.size() - tail.size(), wrapped.size()));
        List<String> digest = Sandbox.of("docker:python@sha256:0123abcd").wrap(command, temp, Optional.empty(), Map.of(), "n");
        assertEquals(List.of("--network", "none"), digest.subList(8, 10), "a digest reference is an image, not a network");
        assertTrue(digest.contains("python@sha256:0123abcd"), digest.toString());
        assertTrue(Sandbox.of("docker:python@sha256:0123abcd").failedToStart(125, ""), "docker's own error is not the candidate's");
        assertFalse(Sandbox.of("bwrap").failedToStart(1, "Traceback (most recent call last):\n"), "a candidate's failure stays its own");
    }

    private static boolean onPath(String program) {
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, program))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aPythonRunMeFirstOncePatchesTheCandidateFirstAndTheJavaStubIsIgnored() throws Exception {
        String patch = "import sys\n"
                + "p = sys.argv[1]\n"
                + "s = open(p, encoding='utf-8').read()\n"
                + "open(p, 'w', encoding='utf-8').write(s.replace('FW_VAR = 1', 'FW_VAR = 0'))\n";
        assertEquals(0, evaluate(plugin(List.of(), Optional.of(patch), "FW_VAR", Map.of()), "FW_VAR = 1\n", new Context()).verdictCode());
        String stub = "class RunMeFirstOnce { public static String FW_ARGS; public static void main(String[] args){ FW_ARGS = \"\"; } }";
        assertTrue(PythonPlugin.isJavaStub(stub));
        assertEquals(1, evaluate(plugin(List.of(), Optional.of(stub), "FW_VAR", Map.of()), "FW_VAR = 1\n", new Context()).verdictCode(),
                "the Java stub is not run as Python");
    }

    @Test
    void aScenarioInALegacyGeneratorTreeRunsWithTheBundlesSettingsAndCfPythonEnvWins(@TempDir Path temp) throws Exception {
        Path repo = Files.createDirectories(temp.resolve("QA-edition"));
        Path tree = Files.createDirectories(repo.resolve("generator_trunk"));
        Files.writeString(tree.resolve("sut_paths.py"), "ROOT = 'tree'\n", StandardCharsets.UTF_8);
        Path scenarioDir = Files.createDirectories(tree.resolve("family/case"));
        Files.createDirectories(temp.resolve("SUT/fin_tech_to_test"));   // a sibling SUT checkout the legacy rule finds
        PythonPlugin p = (PythonPlugin) new PythonPlugin().forRun(new RunInputs(List.of(), Optional.empty(), "FW_VAR", Map.of(),
                Optional.of(scenarioDir), Map.of("cf.python.env", "OOT_MODE=demo; BUNDLE_SUT_ROOT=" + temp.resolve("SUT"))));
        assertEquals(tree, p.workdir(), "the generator tree is the working directory, as the legacy bundle ran its candidates");
        Context context = new Context();
        String source = "import os, sut_paths\n"
                + "ok = os.path.samefile(os.getcwd(), " + pyString(tree.toString()) + ") and sut_paths.ROOT == 'tree' "
                + "and os.environ['BUNDLE_REPO_ROOT'] == " + pyString(repo.toString()) + " and os.environ['OOT_MODE'] == 'demo' "
                + "and os.environ['BUNDLE_SUT_ROOT'] == " + pyString(temp.resolve("SUT").toString()) + "\n"
                + "FW_VAR = 0 if ok else 1\n";
        assertEquals(0, evaluate(p, source, context).verdictCode(), context.out() + context.err());
        assertEquals(temp.resolve("SUT"), PythonPlugin.legacySutRoot(repo), "a sibling SUT checkout holding a known project");
        assertEquals(Optional.empty(), PythonPlugin.generatorTree(temp), "no tree above an ordinary directory");
    }

    @Test
    void theCandidatesOutputIsCapturedWithItsMetricsLine() throws Exception {
        Context context = new Context();
        evaluate(plugin(List.of(), Optional.empty(), "FW_VAR", Map.of()),
                "import sys\nprint('app=demo FW_VAR=0 latency=12.5')\nprint('a warning', file=sys.stderr)\nFW_VAR = 0\n", context);
        assertTrue(context.out().startsWith("app=demo FW_VAR=0 latency=12.5\n"), context.out());
        assertTrue(context.out().contains("__FWV__ 0 -999\n"), "the runner's marker is part of the output, as legacy kept it: " + context.out());
        assertEquals("a warning\n", context.err());
    }

    @Test
    void anInterpreterThatCannotStartIsUnavailableNotAVerdict() {
        PythonPlugin p = plugin(List.of(), Optional.empty(), "FW_VAR", Map.of("cf.python.executable", "/nonexistent/cf-python-missing"));
        SutUnavailable missing = assertThrows(SutUnavailable.class, () -> evaluate(p, "FW_VAR = 0\n", new Context()));
        assertTrue(missing.getMessage().contains("/nonexistent/cf-python-missing"), missing.getMessage());
    }

    @Test
    void cancellationKillsTheCandidateAndTheProcessItStarted(@TempDir Path temp) throws Exception {
        Path pidFile = temp.resolve("child.pid");
        String source = "import subprocess, sys, time\n"
                + "child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(120)'])\n"
                + "open(" + pyString(pidFile.toString()) + ", 'w').write(str(child.pid))\n"
                + "time.sleep(120)\n"
                + "FW_VAR = 0\n";
        PythonPlugin p = plugin(List.of(), Optional.empty(), "FW_VAR", Map.of());
        Context context = new Context();
        CompletableFuture<Object> running = CompletableFuture.supplyAsync(() -> {
            try {
                return evaluate(p, source, context);
            } catch (Exception e) {
                return e;
            }
        });
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!(Files.exists(pidFile) && Files.size(pidFile) > 0) && System.nanoTime() < until) {
            Thread.sleep(20);
        }
        long child = Long.parseLong(Files.readString(pidFile).trim());
        assertTrue(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false), "the child process runs");
        context.cancelled.set(true);
        Object ended = running.get(10, TimeUnit.SECONDS);
        assertTrue(ended instanceof InterruptedException, "a cancelled candidate gives no verdict: " + ended);
        long gone = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < gone) {
            Thread.sleep(20);
        }
        assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false), "the process the candidate started was killed too");
    }

    @Test
    void aCancelledSampleDoesNotTryToStartTheInterpreter() throws Exception {
        PythonProcess.Result result = PythonProcess.run(List.of("/nonexistent/cf-python-missing"), Path.of("."),
                Map.of(), Long.MAX_VALUE, () -> true, text -> { }, text -> { });
        assertEquals(PythonProcess.End.CANCELLED, result.end());
        assertEquals(-1, result.exitCode());
    }

    @Test
    void longVerdictNumbersKeepTheFirstMarkerAcrossChunksWithoutRetainingTheDigits() {
        VerdictMarker padded = new VerdictMarker();
        padded.accept("__FWV__ -");
        for (int i = 0; i < 200; i++) padded.accept("0".repeat(8192));
        padded.accept("2147483648 2147483647\n__FWV__ 0 0\n");
        assertEquals(Integer.MIN_VALUE, padded.values().orElseThrow()[0]);
        assertEquals(Integer.MAX_VALUE, padded.values().orElseThrow()[1]);

        for (boolean first : List.of(true, false)) {
            VerdictMarker wide = new VerdictMarker();
            wide.accept(first ? "__FWV__ " : "__FWV__ 0 ");
            for (int i = 0; i < 200; i++) wide.accept("9".repeat(8192));
            wide.accept(first ? " 0\n__FWV__ 0 0\n" : "\n__FWV__ 0 0\n");
            assertThrows(NumberFormatException.class, wide::values, "a later marker cannot replace the first wide verdict");
        }
        VerdictMarker malformed = new VerdictMarker();
        malformed.accept("___FWV__ --9 __FWV__ 8 x___FWV__ -0 -42!");
        assertEquals(0, malformed.values().orElseThrow()[0]);
        assertEquals(-42, malformed.values().orElseThrow()[1]);
    }

    @Test
    void theMarkerIsFoundAcrossPiecesAndOnlyTheFirstCounts() {
        VerdictMarker m = new VerdictMarker();
        m.accept("x".repeat(10_000) + "__FW");
        m.accept("V__ 1");
        m.accept("2 -3\n__FWV__ 0 0\n");
        m.end();
        assertEquals(List.of(12, -3), List.of(m.values().orElseThrow()[0], m.values().orElseThrow()[1]));
        VerdictMarker atEnd = new VerdictMarker();
        atEnd.accept("__FWV__ 4 5");
        assertTrue(atEnd.values().isEmpty(), "the second value may still go on");
        atEnd.end();
        assertEquals(5, atEnd.values().orElseThrow()[1]);
        VerdictMarker huge = new VerdictMarker();
        huge.accept("__FWV__ 99999999999 0\n");
        assertThrows(NumberFormatException.class, huge::values, "no verdict code is beyond a Java int");
        assertEquals("1_0_0", PythonPlugin.fileName("1_0_0"));
        assertEquals("a_b_c", PythonPlugin.fileName("a/b c"));
    }

    /** A Python string literal of the text. */
    static String pyString(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
