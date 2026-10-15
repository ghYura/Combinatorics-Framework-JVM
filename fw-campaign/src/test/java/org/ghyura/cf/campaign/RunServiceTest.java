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

package org.ghyura.cf.campaign;

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.CustomVar;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run service end to end on a generated-Java scenario built in memory: plan, generation, assembly, in-memory
 * compilation, FW_VAR with its carrier, FW_RunMeFirstOnce arguments, the refusal of pending legacy settings and of an
 * unknown SUT. The typed plugins of fw-sut-fixtures run in fw-acceptance, the only module allowed to use them.
 */
class RunServiceTest {

    private static SourceLocation at(String where) {
        return SourceLocation.ofPath("mem.json", where);
    }

    /** HEAD opens a class, X sets FW_VAR (its second value is a FW_VAR = FW_EXIT_CODE cell of sheet key 2), TAIL closes. */
    private static ScenarioSpec scenario(List<LocatedText> runOnce, List<LocatedText> arguments) {
        List<SheetSpec> sheets = List.of(
                new SheetSpec("HEAD", 1, List.of(ValueCell.of("public class M { public static int FW_VAR = -1; "
                        + "public static void main(String[] a) { System.out.println(String.join(\"+\", a));", ValueKind.TEXT, at("HEAD"))), at("HEAD")),
                new SheetSpec("X", 2, List.of(ValueCell.of("FW_VAR = a.length == 2 ? 0 : 9;", ValueKind.TEXT, at("X!0")),
                        ValueCell.of("FW_VAR = 2;", ValueKind.EXIT_CODE_VAR, at("X!1"))), at("X")),
                new SheetSpec("TAIL", 3, List.of(ValueCell.of("} }", ValueKind.TEXT, at("TAIL"))), at("TAIL")));
        List<SeqRow> seq = new ArrayList<>();
        for (String sheet : List.of("HEAD", "X", "TAIL")) {
            seq.add(new SeqRow(seq.size(), List.of(new LocatedText(sheet, at("FW_Seq")), new LocatedText("FW_Combi(1)", at("FW_Seq")))));
        }
        ControlSpec controls = new ControlSpec(List.of(), runOnce, arguments, List.of(new CustomVar(2, "CARRIED_BY_X", at("FW_CUSTOM_VAR"))));
        return new ScenarioSpec("mem.json", sheets, controls, true, seq);
    }

    private static EffectiveConfig config(String... settings) {
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        for (String s : settings) {
            overrides.add(new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test"));
        }
        return ConfigResolver.resolve(null, Map.of(), overrides);
    }

    private static SeqRow row(int index, String... cells) {
        List<LocatedText> out = new ArrayList<>();
        for (String cell : cells) {
            out.add(new LocatedText(cell, at("FW_Seq")));
        }
        return new SeqRow(index, out);
    }

    @Test
    void aJoinTargetColumnHoldsItsOperandsValuesAndNotItsOwnCell() {
        List<SheetSpec> sheets = List.of(
                new SheetSpec("HEAD", 1, List.of(ValueCell.of("public class J { public static int FW_VAR = -1; public static void main(String[] a) "
                        + "{ int n = 0;", ValueKind.TEXT, at("HEAD"))), at("HEAD")),
                new SheetSpec("L", 2, List.of(ValueCell.of("n += 1;", ValueKind.TEXT, at("L!0")), ValueCell.of("n += 2;", ValueKind.TEXT, at("L!1"))),
                        at("L")),
                new SheetSpec("R", 3, List.of(ValueCell.of("n *= 3;", ValueKind.TEXT, at("R!0"))), at("R")),
                new SheetSpec("JOIN", 4, List.of(ValueCell.of("(the join builds this column)", ValueKind.TEXT, at("JOIN!0"))), at("JOIN")),
                new SheetSpec("TAIL", 5, List.of(ValueCell.of("FW_VAR = n > 0 ? 0 : 1; } }", ValueKind.TEXT, at("TAIL"))), at("TAIL")));
        List<SeqRow> seq = List.of(row(0, "HEAD", "FW_Combi(1)"), row(1, "L", "FW_Exclude", "FW_Combi(1)", "FW_Reuse"),
                row(2, "R", "FW_Exclude", "FW_Combi(1)", "FW_Reuse"), row(3, "JOIN", "FW_(,,L,,R,,,,M:N)"), row(4, "TAIL", "FW_Combi(1)"));
        ScenarioSpec spec = new ScenarioSpec("join.json", sheets, new ControlSpec(List.of(), List.of(), List.of(), List.of()), true, seq);
        try (RunHandle run = RunService.prepare(spec, config("cf.run.id=join"))) {
            RunSummary s = run.await();
            assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
            assertEquals(2, s.count(Outcome.PASS), "one candidate per value of L, each joined with R");
            Reconciliation r = s.reconciliation();
            assertEquals(0, r.count(Reconciliation.Status.FAILS), "the join target's own cell is no value the run must hold: " + r.failures());
            assertEquals(List.of("HEAD 1/1", "TAIL 1/1", "L 2/2", "R 1/1"), s.execution().axes().stream()
                    .map(a -> a.sheet() + " " + a.exercised() + "/" + a.declared()).toList(), "the join column's values are its operands'");
        }
    }

    @Test
    void reconciliationTracksValuesInTheSignedLegacyLabelWindow() {
        List<ValueCell> values = new ArrayList<>();
        for (int i = 0; i < 32_768; i++) {
            values.add(ValueCell.of("latency=" + i + ";", ValueKind.TEXT, at("P")));
        }
        ScenarioSpec spec = new ScenarioSpec("signed.json", List.of(new SheetSpec("P", 1, values, at("P"))),
                new ControlSpec(List.of(), List.of(), List.of(), List.of()), true,
                List.of(new SeqRow(0, List.of(new LocatedText("P", at("FW_Seq")), new LocatedText("FW_Combi(1)", at("FW_Seq"))))));
        try (RunHandle run = RunService.prepare(spec, config("cf.execution.sut=inline-metrics", "cf.keys.logical.labels=legacy-window",
                "cf.run.stop.candidates=1"))) {
            RunSummary summary = run.await();
            assertEquals(RunState.COMPLETED, summary.state(), summary.diagnostics().toString());
            assertTrue(summary.reconciliation().consistent(), summary.reconciliation().failures().toString());
            assertEquals(1, summary.execution().axes().getFirst().exercised());
            assertEquals(32_768, summary.execution().axes().getFirst().declared());
        }
    }

    @Test
    void aGeneratedJavaScenarioRunsWithItsArgumentsAndNamesTheCarrierOfAFailure() {
        ScenarioSpec spec = scenario(List.of(), List.of(new LocatedText("one two", at("FW_Arguments!0"))));
        AtomicInteger notices = new AtomicInteger();
        try (RunHandle run = RunService.prepare(spec, config("cf.run.id=mini", "cf.execution.workers=2"))) {
            run.observe(p -> notices.incrementAndGet());
            RunSummary s = run.await();
            assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
            assertEquals(BigInteger.TWO, s.generated());
            assertEquals(BigInteger.TWO, s.executable());
            assertEquals(1, s.count(Outcome.PASS), "the FW_Arguments line 'one two' gives main two arguments");
            assertEquals(1, s.count(Outcome.DOMAIN_FAIL));
            assertEquals(Map.of(0, 1L, 2, 1L), s.verdicts());
            assertEquals("CARRIED_BY_X", s.verdictNames().get(2), "FW_CUSTOM_VAR names the verdict codes");
            assertEquals(2, s.compilations());
            Observation failed = run.finals().stream().filter(o -> o.outcome() == Outcome.DOMAIN_FAIL).findFirst().orElseThrow();
            assertEquals("X", failed.domain().orElseThrow().attributes().get("carrier"));
            assertEquals("one+two\n", failed.stdout(), "the candidate's output stays with its sample");
            assertEquals("mini", failed.key().runId().value());
            assertTrue(notices.get() >= 2);
            Reconciliation r = s.reconciliation();
            assertTrue(r.consistent(), r.failures().toString());
            assertEquals(0, r.count(Reconciliation.Status.FAILS), "no notice either: " + r.failures());
            assertEquals(List.of("HEAD 1/1", "X 2/2", "TAIL 1/1"), s.execution().axes().stream()
                    .map(a -> a.sheet() + " " + a.exercised() + "/" + a.declared()).toList(), "every value was held by a candidate");
            assertEquals(2, s.execution().admittedSamples());
        }
    }

    @Test
    void runMeFirstOnceProvidesTheArgumentsAndItsFailureStopsTheRun() {
        LocatedText once = new LocatedText("public class RunMeFirstOnce { public static String FW_ARGS; "
                + "public static void main(String[] a) { FW_ARGS = \" x y \"; } }", at("FW_RunMeFirstOnce!0"));
        try (RunHandle run = RunService.prepare(scenario(List.of(once), List.of()), config())) {
            RunSummary s = run.await();
            assertEquals(1, s.count(Outcome.PASS), "FW_ARGS ' x y ' trimmed and split gives two arguments");
        }
        LocatedText broken = new LocatedText("public class RunMeFirstOnce { }", at("FW_RunMeFirstOnce!0"));
        try (RunHandle run = RunService.prepare(scenario(List.of(broken), List.of()), config())) {
            RunSummary s = run.await();
            assertEquals(RunState.FAILED, s.state());
            assertEquals(ErrorCode.EXEC_PREPARE_FAILED, s.diagnostics().getLast().code());
            assertEquals(0, s.samples(), "no candidate ran");
        }
    }

    @Test
    void pendingLegacySettingsAndUnknownPluginsAreRefusedBeforeAnythingRuns() {
        // no setting of the catalog is deferred since M07: a deferred one of a later package stands in for the next
        org.ghyura.cf.config.LegacyImport later = new org.ghyura.cf.config.LegacyImport("test", List.of("fw.properties"), List.of(), List.of(),
                List.of(), List.of(new org.ghyura.cf.config.LegacyImport.Pending("x.later", SourceLocation.of("fw.properties", 1), "M99",
                "retained behaviour of a later package")), List.of());
        EffectiveConfig withPending = ConfigResolver.resolve(null, Map.of(), List.of(), later);
        FrameworkException e = assertThrows(FrameworkException.class, () -> RunService.prepare(scenario(List.of(), List.of()), withPending));
        assertEquals(ErrorCode.CONFIG_LEGACY_PENDING, e.diagnostics().getFirst().code());
        assertTrue(e.getMessage().contains("x.later") && e.getMessage().contains("M99"), e.getMessage());
        FrameworkException campaign = assertThrows(FrameworkException.class, () -> Campaign.of(scenario(List.of(), List.of()), withPending));
        assertEquals(ErrorCode.CONFIG_LEGACY_PENDING, campaign.diagnostics().getFirst().code());
        FrameworkException unknown = assertThrows(FrameworkException.class,
                () -> RunService.prepare(scenario(List.of(), List.of()), config("cf.execution.sut=no-such-plugin")));
        assertEquals(ErrorCode.CONFIG_SUT_UNKNOWN, unknown.diagnostics().getFirst().code());
    }

    @Test
    void aRunCancelledBeforeItStartsRunsNothing() {
        try (RunHandle run = RunService.prepare(scenario(List.of(), List.of()), config())) {
            run.cancel();
            RunSummary s = run.await();
            assertEquals(RunState.CANCELLED, s.state());
            assertEquals(0, s.samples());
            assertTrue(s.reconciliation().consistent(), s.reconciliation().failures().toString());
            assertEquals(Reconciliation.Status.NOT_APPLICABLE, s.reconciliation().checks().stream()
                    .filter(c -> c.id().equals("completed-admitted-all")).findFirst().orElseThrow().status());
            run.cancel();
            assertEquals(RunState.CANCELLED, run.state(), "cancelling again changes nothing");
        }
    }

    @Test
    void environmentsAndNestedRepeatsComeFromTheConfigurationAndTheLifecycleEndsOnceAfterDraining() {
        List<RunState> seen = Collections.synchronizedList(new ArrayList<>());
        try (RunHandle run = RunService.prepare(scenario(List.of(), List.of(new LocatedText("one two", at("FW_Arguments!0")))),
                config("cf.execution.environments=a,b", "cf.execution.repeat.policy=nested", "cf.execution.repeat.count=2"))) {
            run.observe(p -> seen.add(p.state()));
            RunSummary s = run.await();
            assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
            assertEquals(8, s.samples(), "2 candidates x 2 environments x K=2");
            assertTrue(s.reconciliation().consistent(), s.reconciliation().failures().toString());
            assertEquals(8, s.execution().admittedSamples());
            assertEquals(4, s.count(Outcome.PASS));
            assertEquals(List.of("a", "b"), s.execution().environments());
            assertEquals(Map.of("a", 4L, "b", 4L), run.finals().stream().collect(java.util.stream.Collectors.groupingBy(
                    o -> o.key().environmentId(), java.util.TreeMap::new, java.util.stream.Collectors.counting())));
            assertTrue(s.execution().budgetHighWater() > 0 && s.execution().budgetHighWater() <= s.execution().budgetBytes());
        }
        assertTrue(seen.contains(RunState.RUNNING) && seen.contains(RunState.DRAINING), seen.toString());
        assertEquals(RunState.COMPLETED, seen.getLast());
        assertEquals(1, seen.stream().filter(RunState::terminal).distinct().count(), "one terminal state: " + seen);
    }

    @Test
    void theRunsTimeBudgetCancelsItCooperatively() {
        List<SheetSpec> sheets = List.of(new SheetSpec("S", 1, List.of(ValueCell.of("public class Z { public static int FW_VAR = -1; "
                + "public static void main(String[] a) { try { Thread.sleep(20000); } catch (InterruptedException e) { return; } "
                + "FW_VAR = 0; } }", ValueKind.TEXT, at("S"))), at("S")));
        ScenarioSpec slow = new ScenarioSpec("slow.json", sheets, new ControlSpec(List.of(), List.of(), List.of(), List.of()), true,
                List.of(new SeqRow(0, List.of(new LocatedText("S", at("FW_Seq")), new LocatedText("FW_Combi(1)", at("FW_Seq"))))));
        try (RunHandle run = RunService.prepare(slow, config("cf.run.budget.millis=300"))) {
            RunSummary s = run.await();
            assertEquals(RunState.CANCELLED, s.state());
            assertTrue(s.execution().budgetExhausted());
            assertTrue(s.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RUN_BUDGET_EXHAUSTED), s.diagnostics().toString());
            assertEquals(1, s.count(Outcome.CANCELLED), "the running sample stopped when asked");
            assertTrue(s.reconciliation().consistent(), s.reconciliation().failures().toString());
        }
    }

    @Test
    void theCompilerCacheIsReservedFromTheRunBudget() {
        // the cache bound equals the whole budget, which the key dictionary has already started to use
        try (RunHandle run = RunService.prepare(scenario(List.of(), List.of()), config("cf.memory.budget.bytes=4194304",
                "cf.compiler.cache.maxBytes=4194304", "cf.pipeline.queue.maxBytes=1048576", "cf.results.maxAttachmentBytes=1048576",
                "cf.execution.output.maxBytes=65536"))) {
            RunSummary s = run.await();
            assertEquals(RunState.FAILED, s.state());
            assertEquals(ErrorCode.RESOURCE_LIMIT, s.diagnostics().getLast().code());
            assertTrue(s.diagnostics().getLast().message().contains("compiler cache"), s.diagnostics().getLast().message());
            assertEquals(0, s.samples(), "nothing compiled or ran");
        }
    }

    @Test
    void aggregateOnlyRunsWithTheSameTotalsAndDropsObservationsAndAttachments() {
        RunSummary full;
        ScenarioSpec spec = scenario(List.of(), List.of());
        String[] settings = {"cf.execution.repeat.count=3", "cf.execution.repeat.scope=metrics", "cf.results.attach=all"};
        try (RunHandle run = RunService.prepare(spec, config(settings))) { full = run.await(); }
        List<String> aggregateSettings = new ArrayList<>(List.of(settings));
        aggregateSettings.add("cf.results.retention=aggregate-only");
        try (RunHandle run = RunService.prepare(spec, config(aggregateSettings.toArray(String[]::new)))) {
            RunSummary aggregate = run.await();
            assertEquals(RunState.COMPLETED, aggregate.state(), aggregate.diagnostics().toString());
            assertEquals(full.samples(), aggregate.samples());
            assertEquals(full.attempts(), aggregate.attempts());
            assertEquals(full.retried(), aggregate.retried());
            assertEquals(full.outcomes(), aggregate.outcomes());
            assertEquals(full.verdicts(), aggregate.verdicts());
            assertEquals(full.execution().measurements(), aggregate.execution().measurements());
            assertEquals(full.execution().measurementOutcomes(), aggregate.execution().measurementOutcomes());
            assertTrue(run.observations().isEmpty() && run.finals().isEmpty() && run.attachments().isEmpty());
            assertEquals("aggregate-only", aggregate.execution().retention());
            assertTrue(aggregate.reconciliation().consistent(), aggregate.reconciliation().failures().toString());
            assertEquals(0, aggregate.execution().attachmentsKept());
            assertEquals(full.execution().attachmentsKept(), aggregate.execution().attachmentsOmitted());
            assertTrue(aggregate.diagnostics().stream().anyMatch(d -> d.message().contains("omitted by aggregate-only")));
        }
    }

    @Test
    @SuppressWarnings("try")
    void anAnalysedRunOfMetricBearingCandidatesFindsItsFrontThroughTheInlineMetricsPlugin() {
        List<SheetSpec> sheets = List.of(
                new SheetSpec("L", 1, List.of(ValueCell.of("latency=10;", ValueKind.TEXT, at("L!0")), ValueCell.of("latency=20;", ValueKind.TEXT,
                        at("L!1"))), at("L")),
                new SheetSpec("T", 2, List.of(ValueCell.of("throughput=200;", ValueKind.TEXT, at("T!0")), ValueCell.of("throughput=100;",
                        ValueKind.TEXT, at("T!1"))), at("T")));
        List<SeqRow> seq = List.of(new SeqRow(0, List.of(new LocatedText("L", at("FW_Seq")), new LocatedText("FW_Combi(1)", at("FW_Seq")))),
                new SeqRow(1, List.of(new LocatedText("T", at("FW_Seq")), new LocatedText("FW_Combi(1)", at("FW_Seq")))));
        ScenarioSpec metrics = new ScenarioSpec("metrics.json", sheets, new ControlSpec(List.of(), List.of(), List.of(), List.of()), true, seq);
        try (RunHandle run = RunService.prepare(metrics, config("cf.execution.sut=inline-metrics", "cf.analysis.enabled=true"))) {
            RunSummary s = run.await();
            assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
            assertEquals(4, s.count(Outcome.PASS));
            org.ghyura.cf.analysis.AnalysisReport a = s.analysis().orElseThrow();
            assertEquals(4, a.eligible());
            assertTrue(s.reconciliation().checks().stream().filter(c -> c.stage().equals("aggregation"))
                    .allMatch(c -> c.status() == Reconciliation.Status.HOLDS), s.reconciliation().checks().toString());
            assertEquals(List.of("latency", "throughput"), a.discovered(), "directions inferred: latency min, throughput max");
            assertEquals(1, a.front().size(), "latency 10 with throughput 200 dominates the three others");
            assertEquals(Map.of("latency", 10.0, "throughput", 200.0), a.front().getFirst().values());
        }
        var budget = new org.ghyura.cf.store.MemoryBudget(4 << 20);
        try (var owner = budget.reserve(4096, "another owner");
             RunHandle run = RunService.prepare(metrics, config("cf.execution.sut=inline-metrics", "cf.analysis.enabled=true",
                     "cf.execution.output.maxBytes=65536"))
                .useMemoryBudget(budget)) {
            RunSummary s = run.await();
            assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
            assertEquals(4, s.count(Outcome.PASS));
            assertEquals(1, s.analysis().orElseThrow().front().size());
            assertEquals(4, run.finals().size());
            assertEquals(4096, budget.reservedBytes(), "completed reports transfer to the caller; other owners remain charged");
        }
        assertEquals(0, budget.reservedBytes());
    }

    /** HEAD and TAIL wrap X's values; the candidates follow X's order; the values at {@code broken} do not compile. */
    private static ScenarioSpec numbered(int n, java.util.Set<Integer> broken) {
        List<ValueCell> x = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            x.add(ValueCell.of(broken.contains(i) ? "FW_VAR = ;" : "FW_VAR = 0; int v" + i + " = " + i + ";", ValueKind.TEXT, at("X!" + i)));
        }
        List<SheetSpec> sheets = List.of(
                new SheetSpec("HEAD", 1, List.of(ValueCell.of("public class M { public static int FW_VAR = -1; public static void main(String[] a) {",
                        ValueKind.TEXT, at("HEAD"))), at("HEAD")),
                new SheetSpec("X", 2, x, at("X")),
                new SheetSpec("TAIL", 3, List.of(ValueCell.of("} }", ValueKind.TEXT, at("TAIL"))), at("TAIL")));
        List<SeqRow> seq = new ArrayList<>();
        for (String sheet : List.of("HEAD", "X", "TAIL")) {
            seq.add(new SeqRow(seq.size(), List.of(new LocatedText(sheet, at("FW_Seq")), new LocatedText("FW_Combi(1)", at("FW_Seq")))));
        }
        return new ScenarioSpec("numbered.json", sheets, new ControlSpec(List.of(), List.of(), List.of(), List.of()), true, seq);
    }

    @Test
    void theCandidateLimitAdmitsTheFirstCandidatesAndTheErrorBudgetStopsAtTheSameCandidateForAnyWorkerCount() {
        try (RunHandle run = RunService.prepare(numbered(5, java.util.Set.of()), config("cf.run.stop.candidates=2"))) {
            RunSummary s = run.await();
            assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
            assertEquals(StopPolicy.Reason.CANDIDATE_LIMIT, s.stop().reason());
            assertEquals(2, s.admitted());
            assertEquals(2, s.stop().boundary());
            assertEquals(2, s.samples());
            assertTrue(s.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RUN_STOPPED), s.diagnostics().toString());
            assertTrue(s.reconciliation().consistent(), s.reconciliation().failures().toString());
        }
        for (String workers : List.of("1", "4")) {
            try (RunHandle run = RunService.prepare(numbered(6, java.util.Set.of(1, 3, 4)), config("cf.run.stop.errors=2",
                    "cf.execution.workers=" + workers))) {
                RunSummary s = run.await();
                assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
                assertEquals(StopPolicy.Reason.ERROR_BUDGET, s.stop().reason(), workers);
                assertEquals(4, s.stop().boundary(), "the second broken candidate is the fourth in candidate order, with " + workers + " workers");
                assertTrue(s.admitted() >= 4, "candidates admitted before the decision was known finish: " + s.admitted());
                assertTrue(s.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RUN_ERROR_BUDGET_EXHAUSTED), s.diagnostics().toString());
                assertTrue(s.reconciliation().consistent(), s.reconciliation().failures().toString());
            }
        }
        try (RunHandle run = RunService.prepare(numbered(3, java.util.Set.of()), config("cf.run.stop.candidates=5"))) {
            RunSummary s = run.await();
            assertEquals(StopPolicy.Reason.EXHAUSTED, s.stop().reason(), "fewer candidates than the limit: they ran out");
            assertEquals(3, s.admitted());
        }
    }

    @Test
    void aMetricTargetEndsAdmissionAtTheFirstPassingSampleThatReachesIt() {
        List<SheetSpec> sheets = List.of(
                new SheetSpec("L", 1, List.of(ValueCell.of("latency=10;", ValueKind.TEXT, at("L!0")), ValueCell.of("latency=20;", ValueKind.TEXT,
                        at("L!1"))), at("L")),
                new SheetSpec("T", 2, List.of(ValueCell.of("throughput=200;", ValueKind.TEXT, at("T!0")), ValueCell.of("throughput=100;",
                        ValueKind.TEXT, at("T!1"))), at("T")));
        List<SeqRow> seq = List.of(new SeqRow(0, List.of(new LocatedText("L", at("FW_Seq")), new LocatedText("FW_Combi(1)", at("FW_Seq")))),
                new SeqRow(1, List.of(new LocatedText("T", at("FW_Seq")), new LocatedText("FW_Combi(1)", at("FW_Seq")))));
        ScenarioSpec metrics = new ScenarioSpec("metrics.json", sheets, new ControlSpec(List.of(), List.of(), List.of(), List.of()), true, seq);
        try (RunHandle run = RunService.prepare(metrics, config("cf.execution.sut=inline-metrics", "cf.run.stop.target=latency>=20",
                "cf.execution.workers=1"))) {
            RunSummary s = run.await();
            assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
            assertEquals(StopPolicy.Reason.TARGET_REACHED, s.stop().reason());
            assertEquals(3, s.stop().boundary(), "latency 20 first appears in the third candidate (L1, T0)");
            assertTrue(s.stop().detail().contains("latency = 20.0"), s.stop().detail());
            assertTrue(s.reconciliation().consistent(), s.reconciliation().failures().toString());
        }
    }
}
