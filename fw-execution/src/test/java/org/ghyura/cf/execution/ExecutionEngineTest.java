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

import org.ghyura.cf.compiler.JavaSourceCompiler;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.model.key.SelectionState;
import org.ghyura.cf.results.ResultRepository;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.spi.CancellationSource;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.SheetSelection;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExecutionEngineTest {

    private static final RunId RUN = new RunId("t");

    private record TestCandidate(CandidateId id, String behaviour, String source) implements SutCandidate {
        static TestCandidate of(String behaviour, String source) {
            return new TestCandidate(new CandidateId(Canonical.sha256Hex(behaviour + "\n" + source)), behaviour, source);
        }

        @Override
        public String displayId() {
            return behaviour;
        }

        @Override
        public BigInteger ordinal() {
            return BigInteger.ZERO;
        }

        @Override
        public List<SheetSelection> selections() {
            return List.of(new SheetSelection("behaviour", SelectionState.PRESENT, List.of(behaviour)));
        }
    }

    /** A plugin whose evaluation is the given function of the candidate and the context; it counts its sessions. */
    private static final class LambdaPlugin implements SutPlugin {
        final AtomicInteger sessions = new AtomicInteger();
        private final Function<Object[], DomainObservation> body;
        private final boolean runsSource;

        LambdaPlugin(Function<Object[], DomainObservation> body) {
            this(body, false);
        }

        LambdaPlugin(Function<Object[], DomainObservation> body, boolean runsSource) {
            this.body = body;
            this.runsSource = runsSource;
        }

        @Override
        public boolean runsCandidateSource() {
            return runsSource;
        }

        @Override
        public String id() {
            return "lambda";
        }

        @Override
        public String version() {
            return "1";
        }

        @Override
        public SutSession open(ExecutionContext context) {
            sessions.incrementAndGet();
            return new SutSession() {
                @Override
                public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) {
                    return body.apply(new Object[] {candidate, ctx});
                }

                @Override
                public void close() {
                }
            };
        }
    }

    /**
     * The recorded source hash (results_v2 source_hash, cross-check with the legacy 2026-10-13): a plugin that runs the
     * candidate's rendered source, as the Python add-on does, records the SHA-256 of that source's UTF-8 bytes, as the legacy
     * executors hashed the candidate file; a typed plugin keeps its identity digest, the same for every candidate.
     */
    @Test
    void aPluginRunningTheCandidateSourceRecordsItsSha256AndATypedPluginItsIdentity() {
        TestCandidate a = TestCandidate.of("a", "FW_VAR = 0\n");
        TestCandidate b = TestCandidate.of("b", "FW_VAR = 1  # \u0436\n");
        PluginTarget typed = new PluginTarget(new LambdaPlugin(args -> DomainObservation.of(0)));
        assertEquals(typed.sourceDigest(a), typed.sourceDigest(b), "a typed plugin's identity digest");
        PluginTarget source = new PluginTarget(new LambdaPlugin(args -> DomainObservation.of(0), true));
        assertEquals(Canonical.sha256Hex(b.source().getBytes(java.nio.charset.StandardCharsets.UTF_8)), source.sourceDigest(b));
        assertTrue(!source.sourceDigest(a).equals(source.sourceDigest(b)), "each candidate's own source");
    }

    private static ExecutionPolicy policy(long timeout, long grace, int repeats, int attempts, int workers) {
        return new ExecutionPolicy(timeout, grace, repeats, attempts, 1 << 16, RunConfig.VerdictField.FW_VAR, 42L, workers, 4);
    }

    private static Map<String, Observation> byBehaviour(List<? extends SutCandidate> candidates, List<Observation> finals) {
        Map<CandidateId, String> names = new HashMap<>();
        for (SutCandidate c : candidates) {
            names.put(c.id(), c.displayId());
        }
        Map<String, Observation> map = new HashMap<>();
        for (Observation o : finals) {
            map.put(names.get(o.key().candidateId()), o);
        }
        return map;
    }

    private static String main(String body) {
        return "public class C { public static int FW_VAR = -1; public static void main(String[] a) throws Exception { " + body + " } }";
    }

    @Test
    void generatedJavaGivesEveryOutcomeItCanAndKeepsCustomVerdictsExact() {
        List<TestCandidate> candidates = List.of(
                TestCandidate.of("pass", main("FW_VAR = 0;")),
                TestCandidate.of("verdict300", main("FW_VAR = 300;")),
                TestCandidate.of("carrier", main("FW_VAR = 3;")),
                TestCandidate.of("print", main("System.out.println(\"to the sample\"); System.err.print(\"err\"); FW_VAR = 0;")),
                TestCandidate.of("compile", "public class C { void m() { int x = ; } }"),
                TestCandidate.of("nomain", "public class C { public static int FW_VAR = 0; }"),
                TestCandidate.of("noverdict", "public class C { public static void main(String[] a) { } }"),
                TestCandidate.of("exit", main("if (a.length == 0) System.exit(1); FW_VAR = 0;")),
                TestCandidate.of("throw", main("throw new IllegalStateException(\"boom\");")),
                TestCandidate.of("skip", main("throw new org.ghyura.cf.spi.SkipSample(\"not here\");")),
                TestCandidate.of("oom", main("throw new OutOfMemoryError(\"simulated\");")),
                TestCandidate.of("sleep", main("while (true) { Thread.sleep(5); }")));
        MemoryBudget budget = new MemoryBudget(64L << 20);
        GeneratedJavaTarget target = new GeneratedJavaTarget(JavaSourceCompiler.create(List.of(), 1 << 20), RunConfig.VerdictField.FW_VAR,
                Map.of(300, "ABOVE_127"), Map.of(3, "PAYMENT"), List.of(), List.of());
        try (ResultRepository results = new ResultRepository(RUN, 1000, budget)) {
            ExecutionEngine engine = new ExecutionEngine(target, policy(300, 300, 1, 1, 4), results, budget, "plan");
            ExecutionEngine.Report report = engine.run(candidates.iterator(), Cancellation.NONE, null);
            assertEquals(12, report.admittedSamples());
            Map<String, Observation> o = byBehaviour(candidates, results.finals());
            assertEquals(Outcome.PASS, o.get("pass").outcome());
            assertEquals(Outcome.DOMAIN_FAIL, o.get("verdict300").outcome());
            assertEquals(300, o.get("verdict300").verdictCode().orElseThrow());
            assertEquals("PAYMENT", o.get("carrier").domain().orElseThrow().attributes().get("carrier"),
                    "FW_VAR = k names the sheet whose FW_EXIT_CODE is k");
            assertEquals("to the sample\n", o.get("print").stdout());
            assertEquals("err", o.get("print").stderr());
            Map<String, ErrorCode> broken = Map.of("compile", ErrorCode.EXEC_COMPILE_ERROR, "nomain", ErrorCode.EXEC_NO_ENTRY_POINT,
                    "noverdict", ErrorCode.EXEC_NO_VERDICT, "exit", ErrorCode.EXEC_FORBIDDEN_CALL, "throw", ErrorCode.EXEC_SUT_EXCEPTION);
            broken.forEach((name, code) -> {
                assertEquals(Outcome.BROKEN, o.get(name).outcome(), name + ": malformed code or a missing verdict is never DOMAIN_FAIL");
                assertEquals(code, o.get(name).failure().orElseThrow(), name);
            });
            assertTrue(o.get("compile").failureMessage().startsWith("line 1, column "), o.get("compile").failureMessage());
            assertEquals(Outcome.SKIPPED, o.get("skip").outcome());
            assertEquals(Outcome.INFRA_FAIL, o.get("oom").outcome());
            assertEquals(ErrorCode.EXEC_OUT_OF_MEMORY, o.get("oom").failure().orElseThrow());
            assertEquals(Outcome.TIMEOUT, o.get("sleep").outcome());
            assertEquals(ErrorCode.EXEC_DEADLINE, o.get("sleep").failure().orElseThrow(), "the interrupt stopped it within the grace");
            assertEquals(0, report.abandonedThreads());
        } finally {
            target.close();
        }
        assertEquals(0, budget.reservedBytes());
    }

    /** A main a candidate can inherit, so that the candidate itself has no executable line. */
    public static class InheritedMain {
        public static void main(String[] a) {
            // nothing: the candidate's verdict field keeps its default 0
        }
    }

    @Test
    void lineCoverageReportsTheShareOfTheCandidatesOwnLinesEachVerdictSampleRan() {
        List<TestCandidate> candidates = List.of(
                TestCandidate.of("branch", """
                        public class B {
                            public static int FW_VAR = -1;
                            public static void main(String[] a) {
                                if (a.length > 0) {
                                    FW_VAR = 1;
                                    return;
                                }
                                FW_VAR = 0;
                            }
                        }
                        """),
                TestCandidate.of("straight", """
                        public class S {
                            public static int FW_VAR = -1;
                            public static void main(String[] a) {
                                FW_VAR = 0;
                            }
                        }
                        """),
                TestCandidate.of("inherited", "public class I extends org.ghyura.cf.execution.ExecutionEngineTest.InheritedMain { static int FW_VAR; }"));
        MemoryBudget budget = new MemoryBudget(64L << 20);
        GeneratedJavaTarget target = new GeneratedJavaTarget(JavaSourceCompiler.create(List.of(), 1 << 20), RunConfig.VerdictField.FW_VAR,
                Map.of(), Map.of(), List.of(), List.of(), RunConfig.Coverage.LINES);
        GeneratedJavaTarget plain = new GeneratedJavaTarget(JavaSourceCompiler.create(List.of(), 1 << 20), RunConfig.VerdictField.FW_VAR,
                Map.of(), Map.of(), List.of(), List.of());
        try (ResultRepository results = new ResultRepository(RUN, 1000, budget)) {
            ExecutionEngine engine = new ExecutionEngine(target, policy(300, 300, 2, 1, 2), results, budget, "plan");
            engine.run(candidates.iterator(), Cancellation.NONE, null);
            Map<String, Observation> o = byBehaviour(candidates, results.finals());
            Map<String, Double> branch = o.get("branch").domain().orElseThrow().metrics();
            assertEquals(Outcome.PASS, o.get("branch").outcome());
            // executable: the field initializer, if, FW_VAR = 1, return, FW_VAR = 0 and main's closing brace; the 1 and the return did not run
            assertEquals(6.0, branch.get("coverage_line_total"), branch.toString());
            assertEquals(4.0, branch.get("coverage_line_covered"), branch.toString());
            assertEquals(4.0 / 6.0, branch.get("coverage_line"), 1e-12);
            assertEquals(1.0, o.get("straight").domain().orElseThrow().metrics().get("coverage_line"));
            DomainObservation inherited = o.get("inherited").domain().orElseThrow();
            assertEquals(Outcome.PASS, o.get("inherited").outcome());
            assertFalse(inherited.metrics().containsKey("coverage_line"), "no executable line of its own: omitted, never invented");
            assertTrue(inherited.attributes().get("coverage").startsWith("omitted: "), inherited.attributes().toString());
            assertEquals(2, target.coverageOmitted(), "both repeats of the inherited candidate");
            assertNotEquals(plain.sourceDigest(candidates.getFirst()), target.sourceDigest(candidates.getFirst()),
                    "the instrumentation version is part of what ran");
            assertEquals(o.get("branch").sourceDigest(), target.sourceDigest(candidates.getFirst()));
        } finally {
            target.close();
            plain.close();
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void withoutACompilerServiceGeneratedCandidatesAreInfrastructureFailures() {
        MemoryBudget budget = new MemoryBudget(1 << 20);
        GeneratedJavaTarget target = new GeneratedJavaTarget(JavaSourceCompiler.withCompiler(null, List.of(), 0), RunConfig.VerdictField.FW_VAR,
                Map.of(), Map.of(), List.of(), List.of());
        try (ResultRepository results = new ResultRepository(RUN, 10, budget)) {
            new ExecutionEngine(target, policy(1000, 100, 1, 1, 1), results, budget, "plan").run(
                    List.of(TestCandidate.of("pass", main("FW_VAR = 0;"))).iterator(), Cancellation.NONE, null);
            Observation o = results.finals().getFirst();
            assertEquals(Outcome.INFRA_FAIL, o.outcome(), "a missing compiler is the framework's failure, not the candidate's");
            assertEquals(ErrorCode.EXEC_COMPILER_UNAVAILABLE, o.failure().orElseThrow());
        } finally {
            target.close();
        }
    }

    @Test
    void anUncooperativeSampleIsReportedLeftRunningAndItsLateResultIgnored() throws Exception {
        AtomicInteger finished = new AtomicInteger();
        LambdaPlugin spinner = new LambdaPlugin(args -> {
            long end = System.nanoTime() + 1_000_000_000L;
            while (System.nanoTime() < end) {
                Thread.onSpinWait();
            }
            finished.incrementAndGet();
            return DomainObservation.of(0);
        });
        MemoryBudget budget = new MemoryBudget(1 << 20);
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            ExecutionEngine engine = new ExecutionEngine(new PluginTarget(spinner), policy(100, 100, 1, 1, 1), results, budget, "plan");
            TestCandidate c = TestCandidate.of("spin", "");
            ExecutionEngine.Report report = engine.run(List.of(c).iterator(), Cancellation.NONE, null);
            Observation o = results.finals().getFirst();
            assertEquals(Outcome.TIMEOUT, o.outcome());
            assertEquals(ErrorCode.EXEC_UNCOOPERATIVE, o.failure().orElseThrow());
            assertTrue(o.failureMessage().contains("still running (not stopped"), o.failureMessage());
            assertEquals(1, report.abandonedThreads());
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (finished.get() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(1, finished.get(), "the thread ran to its end on its own");
            assertEquals(1, results.all().size(), "its late pass was never committed");
            assertEquals(Outcome.TIMEOUT, results.finals().getFirst().outcome());
        }
    }

    @Test
    void repeatsGetFreshSessionsRetriesFollowTimeoutsAndInfraFailuresAndRandomnessIsPerSample() {
        Map<String, AtomicInteger> tries = new ConcurrentHashMap<>();
        Map<String, Long> draws = new ConcurrentHashMap<>();
        LambdaPlugin flaky = new LambdaPlugin(args -> {
            ExecutionContext ctx = (ExecutionContext) args[1];
            String slot = ctx.sample().candidateId().hex() + "/" + ctx.sample().repeatIndex();
            draws.put(slot + "/" + ctx.sample().attempt(), ctx.random().nextLong());
            if (tries.computeIfAbsent(slot, s -> new AtomicInteger()).getAndIncrement() == 0) {
                ctx.reserve(Long.MAX_VALUE / 2, "too much");
            }
            return DomainObservation.of(0);
        });
        List<TestCandidate> candidates = List.of(TestCandidate.of("a", ""), TestCandidate.of("b", ""));
        MemoryBudget budget = new MemoryBudget(1 << 20);
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            ExecutionEngine engine = new ExecutionEngine(new PluginTarget(flaky), policy(1000, 100, 3, 2, 2), results, budget, "plan");
            engine.run(candidates.iterator(), Cancellation.NONE, null);
            ResultRepository.Totals t = results.totals();
            assertEquals(6, t.samples(), "2 candidates x K=3 repeats");
            assertEquals(12, t.attempts(), "every sample failed once (INFRA_FAIL) and passed on its retry");
            assertEquals(6, t.retried());
            assertEquals(6, t.count(Outcome.PASS));
            assertEquals(12, flaky.sessions.get(), "a fresh session for every attempt of every repeat");
            assertTrue(results.all().stream().filter(o -> o.key().attempt() == 0)
                    .allMatch(o -> o.outcome() == Outcome.INFRA_FAIL && o.failure().orElseThrow() == ErrorCode.RESOURCE_LIMIT));
            String a = candidates.getFirst().id().hex();
            assertEquals(draws.get(a + "/0/0"), draws.get(a + "/0/1"), "a retry draws the same numbers as its first attempt");
            assertNotEquals(draws.get(a + "/0/0"), draws.get(a + "/1/0"), "another repeat draws other numbers");
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void cancellingStopsAdmissionAndRunningSamplesAndPausingHoldsAdmission() throws Exception {
        LambdaPlugin sleeper = new LambdaPlugin(args -> {
            ExecutionContext ctx = (ExecutionContext) args[1];
            while (!ctx.cancellation().isCancelled()) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    break;
                }
            }
            ctx.cancellation().throwIfCancelled();
            return DomainObservation.of(0);
        });
        List<TestCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            candidates.add(TestCandidate.of("c" + i, ""));
        }
        MemoryBudget budget = new MemoryBudget(1 << 20);
        CancellationSource cancel = new CancellationSource();
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            ExecutionEngine engine = new ExecutionEngine(new PluginTarget(sleeper), policy(60_000, 500, 1, 1, 1), results, budget, "plan");
            List<ExecutionEngine.Report> reports = new ArrayList<>();
            Thread runner = Thread.ofPlatform().start(() -> reports.add(engine.run(candidates.iterator(), cancel, null)));
            while (sleeper.sessions.get() == 0) {
                Thread.sleep(5);
            }
            cancel.cancel();
            runner.join(10_000);
            assertFalse(runner.isAlive());
            ExecutionEngine.Report report = reports.getFirst();
            assertTrue(report.cancelled());
            assertTrue(report.admittedCandidates() < 20, "admission stopped");
            assertTrue(results.finals().stream().allMatch(o -> o.outcome() == Outcome.CANCELLED), "nothing claims a verdict");
            assertEquals(report.admittedSamples(), results.totals().samples(), "every admitted sample has its final observation");
            assertEquals(1, sleeper.sessions.get(), "the queued samples never started");
        }
        // pause: nothing is admitted until resume
        LambdaPlugin quick = new LambdaPlugin(args -> DomainObservation.of(0));
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            ExecutionEngine engine = new ExecutionEngine(new PluginTarget(quick), policy(1000, 100, 1, 1, 2), results, budget, "plan");
            engine.pause();
            Thread runner = Thread.ofPlatform().start(() -> engine.run(candidates.iterator(), Cancellation.NONE, null));
            Thread.sleep(150);
            assertEquals(0, quick.sessions.get(), "paused: no candidate admitted");
            engine.resume();
            runner.join(10_000);
            assertEquals(20, results.totals().count(Outcome.PASS));
        }
    }

    // ---- M05: environments, repeat scope, delivery, admission, lifecycle, attachments --------------------------------

    private static List<TestCandidate> named(String prefix, int n) {
        List<TestCandidate> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(TestCandidate.of(String.format(java.util.Locale.ROOT, "%s%02d", prefix, i), ""));
        }
        return list;
    }

    @Test
    void environmentsAndRepeatPoliciesKeepEverySampleApartWithItsLatestAttempt() {
        // the legacy assignment (BundleControlPlane.planUnits) for K = 2 over environments a, b, c
        List<String> envs = List.of("a", "b", "c");
        assertEquals(List.of(List.of(new ExecutionEngine.Slot(0, "b"), new ExecutionEngine.Slot(1, "b"))),
                ExecutionEngine.plan(1, 2, envs, RunConfig.RepeatPolicy.LOCAL, RunConfig.RepeatScope.METRICS), "local: candidate 1 -> b");
        assertEquals(List.of(List.of(new ExecutionEngine.Slot(0, "c")), List.of(new ExecutionEngine.Slot(1, "a"))),
                ExecutionEngine.plan(2, 2, envs, RunConfig.RepeatPolicy.DISPERSE, RunConfig.RepeatScope.ALL), "disperse: (2 + r) mod 3");
        assertEquals(6, ExecutionEngine.plan(0, 2, envs, RunConfig.RepeatPolicy.NESTED, RunConfig.RepeatScope.ALL).size());
        assertEquals(3, ExecutionEngine.plan(0, 2, envs, RunConfig.RepeatPolicy.NESTED, RunConfig.RepeatScope.METRICS).size());
        // nested K = 2 over environments a and b; repeat 1 in b fails once (INFRA_FAIL) and is retried
        LambdaPlugin plugin = new LambdaPlugin(args -> {
            ExecutionContext ctx = (ExecutionContext) args[1];
            SampleKey k = ctx.sample();
            if (k.environmentId().equals("b") && k.repeatIndex() == 1 && k.attempt() == 0) {
                ctx.reserve(Long.MAX_VALUE / 2, "too much");
            }
            return DomainObservation.of(k.environmentId().equals("a") ? 0 : 7);
        });
        MemoryBudget budget = new MemoryBudget(1 << 20);
        List<TestCandidate> candidates = named("e", 2);
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            ExecutionPolicy p = policy(1000, 100, 2, 2, 3).withEnvironments(List.of("a", "b"), RunConfig.RepeatPolicy.NESTED);
            new ExecutionEngine(new PluginTarget(plugin), p, results, budget, "plan").run(candidates.iterator(), Cancellation.NONE, null);
            ResultRepository.Totals t = results.totals();
            assertEquals(8, t.samples(), "2 candidates x 2 environments x K=2, each its own sample");
            assertEquals(10, t.attempts());
            assertEquals(2, t.retried());
            assertEquals(4, t.count(Outcome.PASS));
            assertEquals(Map.of(0, 4L, 7, 4L), t.verdicts());
            for (TestCandidate c : candidates) {
                SampleKey retried = new SampleKey(RUN, c.id(), 0, 1, "b");
                assertEquals(Outcome.INFRA_FAIL, results.find(retried).orElseThrow().outcome(), "the first attempt is kept");
                Observation latest = results.latest(retried.slot()).orElseThrow();
                assertEquals(1, latest.key().attempt(), "the latest attempt of (repeat 1, environment b) is final");
                assertEquals(Outcome.DOMAIN_FAIL, latest.outcome());
                assertEquals(0, results.latest(new SampleKey(RUN, c.id(), 0, 1, "a").slot()).orElseThrow().key().attempt());
            }
        }
    }

    @Test
    void underTheMetricsScopeRepeatZeroIsTheVerdictAndTheOtherRepeatsMeasurementsSkippedWithoutAVerdict() {
        LambdaPlugin plugin = new LambdaPlugin(args -> {
            if (((SutCandidate) args[0]).displayId().equals("broken")) {
                throw new IllegalStateException("boom");
            }
            ExecutionContext ctx = (ExecutionContext) args[1];
            return new DomainObservation(0, Map.of("latency", 1.0 + ctx.sample().repeatIndex()), Map.of());
        });
        MemoryBudget budget = new MemoryBudget(1 << 20);
        try (ResultRepository results = new ResultRepository(RUN, 100, budget, ResultRepository.Scope.METRICS, 0)) {
            ExecutionPolicy p = policy(1000, 100, 3, 1, 2).withScope(RunConfig.RepeatScope.METRICS);
            new ExecutionEngine(new PluginTarget(plugin), p, results, budget, "plan").run(
                    List.of(TestCandidate.of("ok", ""), TestCandidate.of("broken", "")).iterator(), Cancellation.NONE, null);
            ResultRepository.Totals t = results.totals();
            assertEquals(2, t.samples(), "one verdict sample per candidate (V = C)");
            assertEquals(1, t.count(Outcome.PASS));
            assertEquals(1, t.count(Outcome.BROKEN));
            assertEquals(4, t.measurements());
            assertEquals(2L, t.measurementOutcomes().get(Outcome.PASS));
            assertEquals(2L, t.measurementOutcomes().get(Outcome.SKIPPED), "no measurement after a verdict sample without verdict");
            assertTrue(results.finals().stream().filter(o -> o.outcome() == Outcome.SKIPPED)
                    .allMatch(o -> o.failure().orElseThrow() == ErrorCode.EXEC_NOT_ATTEMPTED));
            assertEquals(4, plugin.sessions.get(), "3 invocations of the good candidate, 1 of the broken one");
        }
    }

    @Test
    void listenersHearCommittedResultsInCandidateOrderWithRetriesWithinTheWindowWhateverTheWorkers() {
        LambdaPlugin plugin = new LambdaPlugin(args -> {
            ExecutionContext ctx = (ExecutionContext) args[1];
            try {
                Thread.sleep(ctx.random().nextInt(4));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return DomainObservation.of(0);
        });
        List<TestCandidate> candidates = named("c", 30);
        List<List<String>> sequences = new ArrayList<>();
        for (int workers : new int[] {1, 4}) {
            MemoryBudget budget = new MemoryBudget(1 << 20);
            try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
                List<String> heard = Collections.synchronizedList(new ArrayList<>());
                Set<SampleKey> failedOnce = ConcurrentHashMap.newKeySet();
                AtomicInteger uncommitted = new AtomicInteger();
                ExecutionPolicy p = policy(1000, 100, 1, 1, workers).withAdmission(4, 1 << 20, 3);
                ExecutionEngine.Report report = new ExecutionEngine(new PluginTarget(plugin), p, results, budget, "plan").run(
                        candidates.iterator(), Cancellation.NONE, o -> {
                            if (results.find(o.key()).isEmpty()) {
                                uncommitted.incrementAndGet();
                            }
                            if (failedOnce.add(o.key())) {
                                throw new IllegalStateException("the first delivery fails");
                            }
                            heard.add(o.display());
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                assertEquals(0, uncommitted.get(), "nothing is published before it is committed");
                assertEquals(30, report.deliveries());
                assertEquals(30, report.listenerRetries());
                assertEquals(0, report.listenerFailures());
                assertTrue(report.peakWindow() <= 3, "window: " + report.peakWindow());
                sequences.add(heard);
            }
        }
        assertEquals(candidates.stream().map(TestCandidate::displayId).toList(), sequences.get(0), "candidate order");
        assertEquals(sequences.get(0), sequences.get(1), "the same sequence with 1 and 4 workers");
        // a listener that never takes one observation: counted once, the totals do not depend on it
        MemoryBudget budget = new MemoryBudget(1 << 20);
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            ExecutionEngine.Report report = new ExecutionEngine(new PluginTarget(plugin), policy(1000, 100, 1, 1, 2), results, budget, "plan")
                    .run(candidates.iterator(), Cancellation.NONE, o -> {
                        if (o.display().equals("c07")) {
                            throw new IllegalStateException("never");
                        }
                    });
            assertEquals(1, report.listenerFailures());
            assertEquals(2, report.listenerRetries(), "three deliveries of the same observation");
            assertTrue(report.firstListenerFailure().contains("never"), report.firstListenerFailure());
            assertEquals(30, results.totals().count(Outcome.PASS));
        }
    }

    @Test
    void admissionIsBoundedByBytesAndFaultsOrExhaustionStopTheRunWithoutDeadlock() {
        LambdaPlugin quick = new LambdaPlugin(args -> DomainObservation.of(0));
        List<TestCandidate> candidates = named("q", 20);
        long each = candidates.getFirst().footprintBytes();
        MemoryBudget budget = new MemoryBudget(1 << 20);
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            ExecutionEngine.Report report = new ExecutionEngine(new PluginTarget(quick), policy(1000, 100, 1, 1, 4)
                    .withAdmission(100, 2 * each, 100), results, budget, "plan").run(candidates.iterator(), Cancellation.NONE, null);
            assertTrue(report.peakQueuedBytes() <= 2 * each, "at most two candidates' bytes admitted: " + report.peakQueuedBytes());
            assertEquals(20, results.totals().count(Outcome.PASS));
        }
        // a candidate larger than the whole byte bound is RESOURCE_LIMIT, not a wait
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> new ExecutionEngine(new PluginTarget(quick),
                    policy(1000, 100, 1, 1, 2).withAdmission(100, each - 1, 100), results, budget, "plan")
                    .run(candidates.iterator(), Cancellation.NONE, null));
            assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
        }
        // producer fault: the candidate source fails after three candidates
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            Iterator<TestCandidate> failing = new Iterator<>() {
                int i;

                @Override
                public boolean hasNext() {
                    return true;
                }

                @Override
                public TestCandidate next() {
                    if (i == 3) {
                        throw new IllegalStateException("the generator broke");
                    }
                    return candidates.get(i++);
                }
            };
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> new ExecutionEngine(new PluginTarget(quick),
                    policy(1000, 100, 1, 1, 2), results, budget, "plan").run(failing, Cancellation.NONE, null));
            assertEquals("the generator broke", e.getMessage());
            assertEquals(3, results.totals().samples(), "every admitted sample has its final observation");
        }
        // consumer fault: the result history is full after five samples
        try (ResultRepository results = new ResultRepository(RUN, 5, budget)) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> new ExecutionEngine(new PluginTarget(quick),
                    policy(1000, 100, 1, 1, 4), results, budget, "plan").run(candidates.iterator(), Cancellation.NONE, null));
            assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
            assertEquals(5, results.all().size());
        }
        // output buffers are reserved from the run budget as well
        MemoryBudget small = new MemoryBudget(4096);
        try (ResultRepository results = new ResultRepository(RUN, 100, new MemoryBudget(1 << 20))) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> new ExecutionEngine(new PluginTarget(quick),
                    policy(1000, 100, 1, 1, 1).withAdmission(1, 4096, 1), results, small, "plan")
                    .run(candidates.iterator(), Cancellation.NONE, null));
            assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
            assertTrue(e.getMessage().contains("output capture") || e.diagnostics().getFirst().message().contains("output capture"));
        }
        assertEquals(0, budget.reservedBytes());
        assertEquals(0, small.reservedBytes());
    }

    @Test
    void cancellingWhileFullOrEmptyInterruptingPausingAndSerializingNeverDeadlock() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger most = new AtomicInteger();
        LambdaPlugin slow = new LambdaPlugin(args -> {
            ExecutionContext ctx = (ExecutionContext) args[1];
            most.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                for (int i = 0; i < 10 && !ctx.cancellation().isCancelled(); i++) {
                    Thread.sleep(2);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
            ctx.cancellation().throwIfCancelled();
            return DomainObservation.of(0);
        });
        List<TestCandidate> candidates = named("s", 40);
        MemoryBudget budget = new MemoryBudget(1 << 20);
        // full: the queue of one item is full and the producer waits when the run is cancelled
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            CancellationSource cancel = new CancellationSource();
            ExecutionEngine engine = new ExecutionEngine(new PluginTarget(slow), policy(60_000, 500, 1, 1, 2).withAdmission(1, 1 << 20, 100),
                    results, budget, "plan");
            List<ExecutionEngine.Report> reports = Collections.synchronizedList(new ArrayList<>());
            Thread runner = Thread.ofPlatform().start(() -> reports.add(engine.run(candidates.iterator(), cancel, null)));
            Thread.sleep(60);
            cancel.cancel();
            runner.join(10_000);
            assertFalse(runner.isAlive());
            assertEquals(reports.getFirst().admittedSamples(), results.totals().samples(), "every admitted sample is final");
        }
        // empty: the workers wait on an empty queue while the candidate source is slow
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            CancellationSource cancel = new CancellationSource();
            Iterator<TestCandidate> slowSource = new Iterator<>() {
                @Override
                public boolean hasNext() {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return true;
                }

                @Override
                public TestCandidate next() {
                    return TestCandidate.of("late" + System.nanoTime(), "");
                }
            };
            ExecutionEngine engine = new ExecutionEngine(new PluginTarget(slow), policy(60_000, 500, 1, 1, 2), results, budget, "plan");
            Thread runner = Thread.ofPlatform().start(() -> engine.run(slowSource, cancel, null));
            Thread.sleep(50);
            cancel.cancel();
            runner.join(10_000);
            assertFalse(runner.isAlive());
        }
        // interrupt: interrupting the calling thread cancels the run
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            ExecutionEngine engine = new ExecutionEngine(new PluginTarget(slow), policy(60_000, 500, 1, 1, 2).withAdmission(1, 1 << 20, 100),
                    results, budget, "plan");
            List<ExecutionEngine.Report> reports = Collections.synchronizedList(new ArrayList<>());
            List<Boolean> flag = Collections.synchronizedList(new ArrayList<>());
            Thread runner = Thread.ofPlatform().start(() -> {
                reports.add(engine.run(candidates.iterator(), Cancellation.NONE, null));
                flag.add(Thread.currentThread().isInterrupted());
            });
            Thread.sleep(60);
            runner.interrupt();
            runner.join(10_000);
            assertFalse(runner.isAlive());
            assertTrue(reports.getFirst().cancelled(), "an interrupt is a cancellation");
            assertEquals(List.of(true), flag, "and the interrupt is kept");
            assertEquals(reports.getFirst().admittedSamples(), results.totals().samples());
        }
        // pause: admission stops, the admitted samples drain, then the callback fires; serialize: one sample at a time
        try (ResultRepository results = new ResultRepository(RUN, 100, budget)) {
            most.set(0);
            ExecutionEngine engine = new ExecutionEngine(new PluginTarget(slow), policy(60_000, 500, 1, 1, 4)
                    .withDelivery(3, RunConfig.Attach.NONE, true), results, budget, "plan");
            java.util.concurrent.CountDownLatch drained = new java.util.concurrent.CountDownLatch(1);
            Thread runner = Thread.ofPlatform().start(() -> engine.run(candidates.iterator(), Cancellation.NONE, null));
            Thread.sleep(40);
            engine.pause(drained::countDown);
            assertTrue(drained.await(10, java.util.concurrent.TimeUnit.SECONDS), "drained");
            int sessions = slow.sessions.get();
            Thread.sleep(60);
            assertEquals(sessions, slow.sessions.get(), "nothing admitted while paused");
            engine.resume();
            runner.join(20_000);
            assertEquals(40, results.totals().count(Outcome.PASS));
            assertEquals(1, most.get(), "cf.execution.serialize: one sample executes at a time");
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void failingSourcesAreAttachedWithinTheirBoundAndMetricLinesBecomeTypedMetrics() {
        List<TestCandidate> candidates = List.of(
                TestCandidate.of("f1", main("FW_VAR = 1;")),
                TestCandidate.of("f2", main("FW_VAR = 2;")),
                TestCandidate.of("f3", main("FW_VAR = 3;")),
                TestCandidate.of("pass", main("System.out.println(\"noise\"); System.out.println(\"  app=perf algo=simd "
                        + "latency_ms=4.648 n=NaN FW_VAR=0\"); FW_VAR = 0;")));
        long sourceBytes = candidates.getFirst().source().length();
        MemoryBudget budget = new MemoryBudget(64L << 20);
        GeneratedJavaTarget target = new GeneratedJavaTarget(JavaSourceCompiler.create(List.of(), 1 << 20), RunConfig.VerdictField.FW_VAR,
                Map.of(), Map.of(), List.of(), List.of());
        try (ResultRepository results = new ResultRepository(RUN, 100, budget, ResultRepository.Scope.ALL, 2 * sourceBytes + 1)) {
            new ExecutionEngine(target, policy(30_000, 300, 1, 1, 1).withDelivery(3, RunConfig.Attach.FAILED, false), results, budget, "plan")
                    .run(candidates.iterator(), Cancellation.NONE, null);
            ResultRepository.AttachmentTotals attached = results.attachmentTotals();
            assertEquals(2, attached.kept(), "two failing sources fit the bound");
            assertEquals(1, attached.omitted(), "the third is omitted and counted");
            Map<String, Observation> o = byBehaviour(candidates, results.finals());
            assertEquals(List.of(), results.attachments(o.get("pass").key()), "a passing source is not attached by default");
            assertEquals(candidates.getFirst().source(), results.attachments(o.get("f1").key()).getFirst().text());
            DomainObservation pass = o.get("pass").domain().orElseThrow();
            assertEquals(Map.of("latency_ms", 4.648), pass.metrics());
            assertEquals("simd", pass.attributes().get("algo"));
            assertEquals("NaN", pass.attributes().get("n"), "a non-finite number stays text");
            assertEquals("app=perf algo=simd latency_ms=4.648 n=NaN FW_VAR=0", pass.attributes().get("metrics.line"));
        } finally {
            target.close();
        }
    }
}
