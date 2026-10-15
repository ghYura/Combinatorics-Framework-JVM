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

import org.ghyura.cf.analysis.BundleSeed;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.results.CampaignCheckpoint;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.Condition;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.rule.Gate;
import org.ghyura.cf.model.rule.Rule;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gate A13: campaigns. Profiles P trade latency (min) against throughput (max): p1..p4 form the front, p5..p8 are dominated;
 * the neutral sheet Q (mode a..d) carries no metric, so the front is p1..p4 with every mode (16 candidates of 32). Derived by
 * hand from the legacy bias rules: the winners hold p1..p4 of P (coverage 0.5), which keeps p1..p5 (neighbours) and forbids
 * p6, p7, p8; Q is fully covered and stays. The biased iteration runs 20 candidates and finds the same front.
 */
class CampaignTest {

    @Test
    void accumulatedHistoryAndTheNextIterationShareOneFiniteBudget() {
        Campaign campaign = Campaign.of(scenario(ConstraintSet.NONE), config("cf.campaign.iterations=100",
                "cf.campaign.strategy=noop", "cf.memory.budget.bytes=16777216", "cf.compiler.cache.maxBytes=0",
                "cf.pipeline.queue.maxBytes=65536", "cf.results.maxAttachmentBytes=65536", "cf.execution.output.maxBytes=65536"));
        AtomicReference<org.ghyura.cf.store.MemoryBudget> memory = new AtomicReference<>();
        AtomicReference<CampaignCheckpoint> saved = new AtomicReference<>();
        campaign.checkpointWithBudgetTo("camp.json", (checkpoint, budget) -> { memory.set(budget); saved.set(checkpoint); });
        Campaign.Result result = campaign.run();
        assertEquals(Campaign.Status.FAILED, result.status(), result.reason());
        assertTrue(result.iterations().size() > 1 && result.iterations().size() < 100);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)
                || result.iterations().stream().anyMatch(it -> it.summary().diagnostics().stream()
                .anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)));
        assertTrue(saved.get().status().isEmpty(), "resource exhaustion must preserve a resumable barrier");
        assertTrue(memory.get().highWaterBytes() <= memory.get().totalBytes());
        assertEquals(0, memory.get().reservedBytes());
    }

    private static final SourceLocation AT = SourceLocation.of("camp.json");

    private static ScenarioSpec scenario(ConstraintSet constraints) {
        return scenario(constraints, "");
    }

    /** With a suffix of every mode, such as FW_VAR=1 (every candidate a domain failure). */
    private static ScenarioSpec scenario(ConstraintSet constraints, String modeSuffix) {
        int[][] profiles = {{10, 100}, {20, 200}, {30, 300}, {40, 400}, {50, 150}, {60, 250}, {70, 350}, {80, 120}};
        List<ValueCell> p = new ArrayList<>();
        for (int[] x : profiles) {
            p.add(ValueCell.of("latency=" + x[0] + "; throughput=" + x[1] + ";", ValueKind.TEXT, AT));
        }
        List<ValueCell> q = new ArrayList<>();
        for (String m : List.of("a", "b", "c", "d")) {
            q.add(ValueCell.of("mode=" + m + ";" + modeSuffix, ValueKind.TEXT, AT));
        }
        List<SeqRow> seq = List.of(new SeqRow(0, List.of(new LocatedText("P", AT), new LocatedText("FW_Combi(1)", AT))),
                new SeqRow(1, List.of(new LocatedText("Q", AT), new LocatedText("FW_Combi(1)", AT))));
        return new ScenarioSpec("camp.json", List.of(new SheetSpec("P", 1, p, AT), new SheetSpec("Q", 2, q, AT)),
                new ControlSpec(List.of(), List.of(), List.of(), List.of()), true, seq, constraints);
    }

    private static EffectiveConfig config(String... settings) {
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        for (String s : List.of("cf.execution.sut=inline-metrics", "cf.analysis.minimize=latency", "cf.analysis.maximize=throughput",
                "cf.run.id=camp")) {
            overrides.add(new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test"));
        }
        for (String s : settings) {
            overrides.add(new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test"));
        }
        return ConfigResolver.resolve(null, Map.of(), overrides);
    }

    @Test
    void aBiasCampaignDerivesEachIterationFromTheBaseAndThePreviousSeedWithItsLineage() {
        ScenarioSpec base = scenario(ConstraintSet.NONE);
        String baseDigest = base.contentDigest();
        Campaign.Result r = Campaign.of(base, config("cf.campaign.iterations=3")).run();
        assertEquals(Campaign.Status.COMPLETED, r.status(), r.reason() + r.diagnostics());
        assertEquals(3, r.iterations().size());
        Campaign.Iteration it1 = r.iterations().get(0);
        Campaign.Iteration it2 = r.iterations().get(1);
        Campaign.Iteration it3 = r.iterations().get(2);
        assertEquals(List.of("camp-it1", "camp-it2", "camp-it3"), r.iterations().stream().map(Campaign.Iteration::runId).toList());
        // the first iteration is the full space; it names no parent and no input seed
        assertEquals("", it1.planStatus());
        assertEquals("", it1.inputSeedDigest());
        assertEquals("", it1.parentPlanDigest());
        assertEquals(baseDigest, it1.scenarioDigest());
        assertEquals(BigInteger.valueOf(32), it1.executable());
        assertEquals(16, it1.frontIds().size());
        // the second is the base scenario biased by the first one's seed
        assertEquals("BIASED", it2.planStatus());
        assertEquals(it1.seedDigest(), it2.inputSeedDigest());
        assertEquals(it1.scenarioDigest(), it2.parentScenarioDigest());
        assertEquals(it1.planDigest(), it2.parentPlanDigest());
        assertNotEquals(it1.scenarioDigest(), it2.scenarioDigest());
        assertEquals(BigInteger.valueOf(32), it2.preBiasEstimate(), "the full space is the previous run's generated count");
        assertEquals(BigInteger.valueOf(20), it2.postBiasEstimate());
        assertEquals(BigInteger.valueOf(12), it2.excluded(), "p6, p7 and p8 with each of the four modes");
        assertEquals(BigInteger.valueOf(20), it2.executable());
        assertEquals(it1.frontIds(), it2.frontIds());
        assertEquals(1, it2.stableTransitions());
        // not cumulative: the third is the base scenario biased by the second seed, which forbids the same values again
        assertEquals(it2.seedDigest(), it3.inputSeedDigest());
        assertEquals(it2.scenarioDigest(), it3.scenarioDigest());
        assertEquals(2, it3.stableTransitions());
        assertEquals(baseDigest, base.contentDigest(), "no iteration changed the base scenario");
        assertEquals(r.iterations().getLast().seedDigest(), r.lastSeed().orElseThrow().contentDigest());
    }

    @Test
    void oneAndFourWorkersGiveTheSameLineage() {
        String one = Campaign.of(scenario(ConstraintSet.NONE), config("cf.campaign.iterations=3", "cf.campaign.stopWhenStable=2",
                "cf.execution.workers=1")).run().canonicalLineage();
        String four = Campaign.of(scenario(ConstraintSet.NONE), config("cf.campaign.iterations=3", "cf.campaign.stopWhenStable=2",
                "cf.execution.workers=4")).run().canonicalLineage();
        assertEquals(one, four);
        assertTrue(one.startsWith("cf.campaign/1\nCAMPAIGN\tSTABLE_FRONT\t"), one);
    }

    @Test
    void aBiasCampaignPausedInsideIterationTwoResumesWithTheExactLineageAndPriorReport() throws Exception {
        ScenarioSpec base = scenario(ConstraintSet.NONE);
        EffectiveConfig effective = config("cf.campaign.iterations=3", "cf.execution.workers=1", "cf.pipeline.ordinal.window=2");
        Campaign.Result expected = Campaign.of(base, effective).run();
        Campaign campaign = Campaign.of(base, effective);
        AtomicReference<CampaignCheckpoint> paused = new AtomicReference<>();
        campaign.checkpointTo("camp.json", cp -> { if (cp.currentRun().isPresent()) paused.set(cp); });
        campaign.observe((i, p) -> { if (i == 2 && p.committed() == 5) campaign.pause(); });
        CompletableFuture<Campaign.Result> future = CompletableFuture.supplyAsync(campaign::run);
        CampaignCheckpoint checkpoint;
        try {
            for (int i = 0; i < 1000 && paused.get() == null && !future.isDone(); i++) Thread.sleep(10);
            assertEquals(RunState.PAUSED, campaign.state());
            checkpoint = paused.get();
            assertTrue(checkpoint != null, "drained checkpoint callback ran");
            assertEquals(2, checkpoint.iteration());
            assertEquals(1, checkpoint.completedIterations().size());
            assertTrue(checkpoint.currentRun().orElseThrow().observations().size() >= 5);
        } finally { campaign.cancel(); }
        Campaign.Result cancelled = future.get(10, TimeUnit.SECONDS);
        assertEquals(Campaign.Status.CANCELLED, cancelled.status());
        Campaign.Result resumed = Campaign.resume(base, effective, checkpoint).run();
        assertEquals(expected.canonicalLineage(), resumed.canonicalLineage());
        assertEquals(cancelled.iterations().getFirst(), resumed.iterations().getFirst(), "all prior summary fields survive exactly");
        Checkpoint child = checkpoint.currentRun().orElseThrow();
        Checkpoint altered = new Checkpoint(child.runId(), child.state(), child.scenarioPath(), child.scenarioDigest(), child.planDigest(),
                "another dictionary", child.policyDigest(), child.plugin(), child.pluginVersion(), child.configuration(), child.observations(),
                child.attachments());
        Campaign mismatch = Campaign.resume(base, effective, checkpoint.withCurrentRun(altered));
        mismatch.checkpointTo("camp.json", cp -> { throw new AssertionError("identity mismatch overwrote the pause checkpoint"); });
        assertEquals(ErrorCode.CHECKPOINT_MISMATCH, assertThrows(FrameworkException.class, mismatch::run).diagnostics().getFirst().code());
    }

    @Test
    void aCampaignResumesFromABarrierAndCompletedStableCampaignsRestoreWithoutRunning() {
        ScenarioSpec base = scenario(ConstraintSet.NONE);
        for (String strategy : List.of("bias", "elitism", "noop")) {
            EffectiveConfig effective = config("cf.campaign.iterations=5", "cf.campaign.stopWhenStable=2", "cf.campaign.strategy=" + strategy);
            Campaign.Result expected = Campaign.of(base, effective).run();
            Campaign campaign = Campaign.of(base, effective);
            AtomicReference<CampaignCheckpoint> barrier = new AtomicReference<>();
            campaign.checkpointTo("camp.json", cp -> { barrier.set(cp); if (cp.status().isEmpty()) campaign.cancel(); });
            Campaign.Result cancelled = campaign.run();
            assertEquals(Campaign.Status.CANCELLED, cancelled.status());
            assertEquals(2, barrier.get().iteration());
            Campaign resumed = Campaign.resume(base, effective, barrier.get());
            AtomicReference<CampaignCheckpoint> completed = new AtomicReference<>();
            resumed.checkpointTo("camp.json", cp -> {
                if (cp.status().isEmpty() && cp.iteration() == 3) throw new IllegalStateException("simulated checkpoint write failure");
                completed.set(cp);
            });
            Campaign.Result result = resumed.run();
            assertEquals(expected.canonicalLineage(), result.canonicalLineage(), strategy);
            assertEquals(cancelled.iterations().getFirst(), result.iterations().getFirst());
            Campaign terminal = Campaign.resume(base, effective, completed.get());
            terminal.observe((i, p) -> { throw new AssertionError("terminal restore executed work"); });
            assertEquals(result, terminal.run());
        }
    }

    @Test
    void aCampaignCheckpointOfAnotherBaseOrConfigurationIsRefusedBeforeStarting() {
        EffectiveConfig effective = config("cf.campaign.iterations=3");
        Campaign campaign = Campaign.of(scenario(ConstraintSet.NONE), effective);
        AtomicReference<CampaignCheckpoint> saved = new AtomicReference<>();
        campaign.checkpointTo("camp.json", cp -> { saved.set(cp); campaign.cancel(); });
        campaign.run();
        assertEquals(ErrorCode.CHECKPOINT_MISMATCH, assertThrows(FrameworkException.class,
                () -> Campaign.resume(scenario(ConstraintSet.NONE, " FW_VAR=1"), effective, saved.get())).diagnostics().getFirst().code());
        assertEquals(ErrorCode.CHECKPOINT_MISMATCH, assertThrows(FrameworkException.class,
                () -> Campaign.resume(scenario(ConstraintSet.NONE), config("cf.campaign.iterations=4"), saved.get()))
                .diagnostics().getFirst().code());
    }

    @Test
    void elitismAddsTheWinnersAsSeedSheetsAndNoopRepeatsTheScenario() {
        ScenarioSpec base = scenario(ConstraintSet.NONE);
        Campaign.Result elite = Campaign.of(base, config("cf.campaign.iterations=2", "cf.campaign.strategy=elitism")).run();
        assertEquals(Campaign.Status.COMPLETED, elite.status(), elite.reason() + elite.diagnostics());
        Campaign.Iteration second = elite.iterations().get(1);
        assertTrue(second.derivation().startsWith("Elitism: added "), second.derivation());
        assertNotEquals(elite.iterations().get(0).scenarioDigest(), second.scenarioDigest());
        assertEquals(BigInteger.valueOf(32), second.executable(), "the Seed_ sheets are data sheets FW_Seq does not use");
        BundleSeed seed = elite.iterations().get(0).summary().seed().orElseThrow();
        Campaign.Elitism once = Campaign.elitism(base, seed);
        assertTrue(once.scenario().sheets().stream().anyMatch(s -> s.name().startsWith("Seed_") && s.name().endsWith("_pareto")));
        assertEquals(once.scenario().contentDigest(), Campaign.elitism(once.scenario(), seed).scenario().contentDigest(),
                "applying elitism twice adds nothing (legacy idempotence)");

        Campaign.Result noop = Campaign.of(base, config("cf.campaign.iterations=5", "cf.campaign.strategy=noop",
                "cf.campaign.stopWhenStable=2")).run();
        assertEquals(Campaign.Status.STABLE_FRONT, noop.status());
        assertEquals(3, noop.iterations().size(), "the front repeats from the second iteration and twice is enough");
        assertEquals(1, noop.iterations().stream().map(Campaign.Iteration::scenarioDigest).distinct().count());
    }

    @Test
    void aSeedFileWrittenByOneRunBiasesTheFirstIterationOfAnother(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("seed.json");
        RunSummary s;
        try (RunHandle run = RunService.prepare(scenario(ConstraintSet.NONE), config("cf.analysis.enabled=true",
                "cf.campaign.seed.output=" + file))) {
            s = run.await();
        }
        BundleSeed written = BundleSeed.read(Files.readString(file, StandardCharsets.UTF_8), file.toString());
        assertEquals(s.seed().orElseThrow().contentDigest(), written.contentDigest());
        assertFalse(written.winners().getFirst().selections().isEmpty(), "a seed of this build carries the winners' selections");
        // a seed that cannot be written is reported apart; the run and its analysis stand
        Path blocked = Files.writeString(dir.resolve("a-file"), "x");
        try (RunHandle run = RunService.prepare(scenario(ConstraintSet.NONE), config("cf.analysis.enabled=true",
                "cf.campaign.seed.output=" + blocked.resolve("seed.json")))) {
            RunSummary failed = run.await();
            assertEquals(RunState.COMPLETED, failed.state());
            assertTrue(failed.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESULT_EXPORT_FAILED), failed.diagnostics().toString());
            assertTrue(failed.analysis().isPresent() && failed.seed().isPresent());
        }

        Campaign.Result r = Campaign.of(scenario(ConstraintSet.NONE), config("cf.campaign.seed.input=" + file)).run();
        assertEquals(1, r.iterations().size());
        assertEquals(written.contentDigest(), r.inputSeedDigest());
        assertEquals("BIASED", r.iterations().getFirst().planStatus());
        assertEquals(BigInteger.valueOf(20), r.iterations().getFirst().executable());
    }

    @Test
    void exhaustedSpaceMissingWinnersAndCancellationStopTheCampaign() {
        Rule everything = new Rule("all", Rule.Polarity.FORBID, Gate.ANY, null, new Rule.Assert(Condition.Leaf.present("P", true)), null, AT);
        Campaign.Result empty = Campaign.of(scenario(new ConstraintSet(List.of(), Map.of(), Map.of(), List.of(everything), List.of())),
                config("cf.campaign.iterations=3")).run();
        assertEquals(Campaign.Status.EXHAUSTED, empty.status());
        assertEquals(1, empty.iterations().size());
        assertEquals(ErrorCode.CAMPAIGN_EXHAUSTED, empty.diagnostics().getFirst().code());

        Campaign.Result noMetrics = Campaign.of(scenario(ConstraintSet.NONE, " FW_VAR=1"), config("cf.campaign.iterations=3")).run();
        assertEquals(Campaign.Status.FAILED, noMetrics.status(), noMetrics.reason());
        assertEquals(ErrorCode.CAMPAIGN_NO_WINNERS, noMetrics.diagnostics().getFirst().code());

        Campaign cancelled = Campaign.of(scenario(ConstraintSet.NONE), config("cf.campaign.iterations=3"));
        cancelled.cancel();
        Campaign.Result c = cancelled.run();
        assertEquals(Campaign.Status.CANCELLED, c.status());
        assertEquals(0, c.iterations().size());
    }

    @Test
    void aUsedUpErrorBudgetEndsTheCampaignWhileBrokenSamplesAloneDoNot() {
        // mode d's text has a malformed FW_VAR: its eight candidates end BROKEN; the other 24 still give a front
        List<ValueCell> p = new ArrayList<>();
        int[][] profiles = {{10, 100}, {20, 200}, {30, 300}, {40, 400}, {50, 150}, {60, 250}, {70, 350}, {80, 120}};
        for (int[] x : profiles) {
            p.add(ValueCell.of("latency=" + x[0] + "; throughput=" + x[1] + ";", ValueKind.TEXT, AT));
        }
        List<ValueCell> q = List.of(ValueCell.of("mode=a;", ValueKind.TEXT, AT), ValueCell.of("mode=b;", ValueKind.TEXT, AT),
                ValueCell.of("mode=c;", ValueKind.TEXT, AT), ValueCell.of("mode=d; FW_VAR=x", ValueKind.TEXT, AT));
        ScenarioSpec scenario = new ScenarioSpec("camp.json", List.of(new SheetSpec("P", 1, p, AT), new SheetSpec("Q", 2, q, AT)),
                new ControlSpec(List.of(), List.of(), List.of(), List.of()), true, List.of(
                new SeqRow(0, List.of(new LocatedText("P", AT), new LocatedText("FW_Combi(1)", AT))),
                new SeqRow(1, List.of(new LocatedText("Q", AT), new LocatedText("FW_Combi(1)", AT)))), ConstraintSet.NONE);

        Campaign.Result tolerated = Campaign.of(scenario, config("cf.campaign.iterations=2", "cf.campaign.strategy=noop")).run();
        assertEquals(Campaign.Status.COMPLETED, tolerated.status(), tolerated.reason());
        assertEquals(2, tolerated.iterations().size(), "BROKEN samples without an error budget are results, not a campaign failure");

        Campaign.Result budgeted = Campaign.of(scenario, config("cf.campaign.iterations=3", "cf.campaign.strategy=noop",
                "cf.run.stop.errors=1")).run();
        assertEquals(Campaign.Status.FAILED, budgeted.status());
        assertTrue(budgeted.reason().startsWith("iteration 1 used up its error budget (cf.run.stop.errors)"), budgeted.reason());
        assertEquals(1, budgeted.iterations().size(), "no further iteration starts");
        assertEquals(RunState.COMPLETED, budgeted.iterations().getFirst().state(), "the iteration's results stay");
        assertEquals(ErrorCode.RUN_ERROR_BUDGET_EXHAUSTED, budgeted.diagnostics().getLast().code());
    }
}
