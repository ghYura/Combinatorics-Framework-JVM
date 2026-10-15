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
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.ghyura.cf.results.Checkpoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A14: a run paused, checkpointed, cancelled and resumed ends with the same final observations as a run that was never
 * interrupted; a cancelled sample of a checkpoint runs again as its next attempt; a checkpoint of another run is refused
 * before anything runs.
 */
class CheckpointResumeTest {

    @Test
    void checkpointSinkSharesRunAdmissionAndPreservesResourceDiagnostics() {
        java.util.concurrent.atomic.AtomicReference<org.ghyura.cf.store.MemoryBudget> memory = new java.util.concurrent.atomic.AtomicReference<>();
        try (RunHandle run = RunService.prepare(scenario(1), config())) {
            run.checkpointWithBudgetTo("cp.json", (checkpoint, budget) -> {
                memory.set(budget);
                assertTrue(budget.reservedBytes() > 0);
                try (var refused = budget.reserve(budget.totalBytes(), "test checkpoint output")) {
                    throw new AssertionError("unexpected admission of " + refused.bytes());
                }
            });
            RunSummary summary = run.await();
            assertEquals(RunState.COMPLETED, summary.state());
            assertTrue(summary.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT));
            assertTrue(summary.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.CHECKPOINT_WRITE_FAILED));
        }
        assertEquals(0, memory.get().reservedBytes());
    }

    private static final SourceLocation AT = SourceLocation.of("cp.json");

    /** 8 x 4 metric-bearing candidates for the inline-metrics plugin. */
    private static ScenarioSpec scenario(int modes) {
        List<ValueCell> p = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            p.add(ValueCell.of("latency=" + (10 * i) + "; throughput=" + (100 * (9 - i)) + ";", ValueKind.TEXT, AT));
        }
        List<ValueCell> q = new ArrayList<>();
        for (int i = 0; i < modes; i++) {
            q.add(ValueCell.of("mode=m" + i + ";", ValueKind.TEXT, AT));
        }
        List<SeqRow> seq = List.of(new SeqRow(0, List.of(new LocatedText("P", AT), new LocatedText("FW_Combi(1)", AT))),
                new SeqRow(1, List.of(new LocatedText("Q", AT), new LocatedText("FW_Combi(1)", AT))));
        return new ScenarioSpec("cp.json", List.of(new SheetSpec("P", 1, p, AT), new SheetSpec("Q", 2, q, AT)),
                new ControlSpec(List.of(), List.of(), List.of(), List.of()), true, seq);
    }

    private static EffectiveConfig config(String... settings) {
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        for (String s : List.of("cf.execution.sut=inline-metrics", "cf.run.id=cp", "cf.execution.workers=1", "cf.pipeline.ordinal.window=2",
                "cf.execution.repeat.count=2")) {
            overrides.add(new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test"));
        }
        for (String s : settings) {
            overrides.add(new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test"));
        }
        return ConfigResolver.resolve(null, Map.of(), overrides);
    }

    @Test
    void aCheckpointThatAlreadyReachedItsTargetResumesWithoutFalseReconciliationFailure() {
        EffectiveConfig effective = config("cf.run.stop.target=latency>=20");
        List<Checkpoint> written = new CopyOnWriteArrayList<>();
        List<Observation> expected;
        try (RunHandle run = RunService.prepare(scenario(1), effective)) {
            run.checkpointTo("cp.json", written::add);
            RunSummary summary = run.await();
            assertEquals(StopPolicy.Reason.TARGET_REACHED, summary.stop().reason());
            expected = run.finals();
        }
        try (RunHandle resumed = RunService.resume(scenario(1), effective, written.getLast())) {
            RunSummary summary = resumed.await();
            assertEquals(RunState.COMPLETED, summary.state(), summary.diagnostics().toString());
            assertEquals(StopPolicy.Reason.TARGET_REACHED, summary.stop().reason());
            assertEquals(0, summary.execution().deliveries(), "already committed samples must not execute again");
            assertEquals(projection(expected), projection(resumed.finals()));
            assertTrue(summary.reconciliation().consistent(), summary.reconciliation().failures().toString());
            assertTrue(summary.diagnostics().stream().noneMatch(d -> d.code() == ErrorCode.RUN_RECONCILIATION_FAILED));
        }
    }

    @Test
    void resumedStopBoundaryIncludesCandidatesWhoseObservationsWereRestored() {
        EffectiveConfig effective = config("cf.run.stop.target=latency>=60");
        List<Checkpoint> written = new CopyOnWriteArrayList<>();
        try (RunHandle run = RunService.prepare(scenario(1), effective)) {
            run.checkpointTo("cp.json", written::add);
            assertEquals(6, run.await().stop().boundary());
        }
        Checkpoint complete = written.getLast();
        List<Observation> prefix = complete.observations().stream()
                .filter(o -> o.domain().orElseThrow().metrics().get("latency") <= 20).toList();
        assertEquals(4, prefix.size(), "two candidates with two repeats each");
        Checkpoint partial = new Checkpoint(complete.runId(), "PAUSED", complete.scenarioPath(), complete.scenarioDigest(),
                complete.planDigest(), complete.dictionaryDigest(), complete.policyDigest(), complete.plugin(), complete.pluginVersion(),
                complete.configuration(), prefix, List.of());
        try (RunHandle resumed = RunService.resume(scenario(1), effective, partial)) {
            RunSummary summary = resumed.await();
            assertEquals(StopPolicy.Reason.TARGET_REACHED, summary.stop().reason());
            assertEquals(6, summary.stop().boundary(), "the boundary is the sixth candidate, not the fourth newly delivered one");
            assertTrue(summary.reconciliation().consistent(), summary.reconciliation().failures().toString());
        }
    }

    /** The final observation of every sample without its attempt, time or output. */
    private static Map<String, String> projection(List<Observation> finals) {
        Map<String, String> out = new TreeMap<>();
        for (Observation o : finals) {
            out.put(o.key().candidateId().hex() + "/" + o.key().repeatIndex() + "/" + o.key().environmentId(), o.outcome() + " "
                    + o.domain().map(d -> d.verdictCode() + " " + d.metrics() + " " + d.attributes()).orElse("-") + " " + o.sourceDigest());
        }
        return out;
    }

    private static List<Observation> uninterrupted() {
        try (RunHandle run = RunService.prepare(scenario(4), config())) {
            assertEquals(RunState.COMPLETED, run.await().state());
            return run.finals();
        }
    }

    @Test
    void pausedCheckpointedCancelledAndResumedEqualsAnUninterruptedRun() throws Exception {
        List<Observation> expected = uninterrupted();
        assertEquals(64, expected.size());
        List<Checkpoint> written = new CopyOnWriteArrayList<>();
        try (RunHandle run = RunService.prepare(scenario(4), config())) {
            run.checkpointTo("cp.json", written::add);
            run.observe(p -> {
                if (p.committed() == 5) {
                    run.pause();
                }
            });
            run.start();
            for (int i = 0; i < 1000 && run.state() != RunState.PAUSED; i++) {
                Thread.sleep(10);
            }
            assertEquals(RunState.PAUSED, run.state());
            run.cancel();
            assertEquals(RunState.CANCELLED, run.await().state());
        }
        assertEquals(List.of("PAUSED", "CANCELLED"), written.stream().map(Checkpoint::state).toList());
        Checkpoint paused = written.getFirst();
        int kept = paused.observations().size();
        assertTrue(kept >= 5 && kept < 64, "a quiescent part of the run: " + kept);
        assertTrue(paused.observations().stream().noneMatch(o -> o.outcome() == Outcome.CANCELLED), "nothing was in flight");

        try (RunHandle resumed = RunService.resume(scenario(4), config(), paused)) {
            RunSummary s = resumed.await();
            assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
            assertEquals(projection(expected), projection(resumed.finals()));
            assertEquals(64, s.samples());
            assertEquals(64, s.attempts(), "only the missing samples ran: every sample has one attempt");
            assertEquals(kept, s.execution().restoredAttempts());
            assertTrue(s.reconciliation().consistent(), "restored attempts were delivered by the run that made them: "
                    + s.reconciliation().failures());
        }
    }

    @Test
    void aCancelledSampleRunsAgainAsItsNextAttemptAndAnotherRunIsRefused() {
        List<Observation> expected = uninterrupted();
        List<Checkpoint> written = new CopyOnWriteArrayList<>();
        try (RunHandle run = RunService.prepare(scenario(4), config())) {
            run.checkpointTo("cp.json", written::add);
            run.await();
        }
        Checkpoint complete = written.getLast();
        // keep the first 10 observations, and let the eleventh sample have been cancelled
        Observation eleventh = complete.observations().get(10);
        SampleKey key = eleventh.key();
        Observation cancelled = new Observation(key, eleventh.display(), Outcome.CANCELLED, Optional.empty(), 0, eleventh.sourceDigest(),
                eleventh.policyDigest(), Optional.of(ErrorCode.EXEC_CANCELLED), "cancelled", "", "", false);
        List<Observation> part = new ArrayList<>(complete.observations().subList(0, 10));
        part.add(cancelled);
        Checkpoint partial = new Checkpoint(complete.runId(), "CANCELLED", complete.scenarioPath(), complete.scenarioDigest(), complete.planDigest(),
                complete.dictionaryDigest(), complete.policyDigest(), complete.plugin(), complete.pluginVersion(), complete.configuration(),
                part, List.of());
        try (RunHandle resumed = RunService.resume(scenario(4), config(), partial)) {
            assertEquals(RunState.COMPLETED, resumed.await().state());
            assertEquals(projection(expected), projection(resumed.finals()));
            Observation again = resumed.finals().stream().filter(o -> o.key().slot().equals(key.slot())).findFirst().orElseThrow();
            assertEquals(1, again.key().attempt());
        }

        // another scenario, or another SUT version: nothing is restored and nothing runs
        try (RunHandle other = RunService.resume(scenario(3), config(), complete)) {
            RunSummary s = other.await();
            assertEquals(RunState.FAILED, s.state());
            assertEquals(ErrorCode.CHECKPOINT_MISMATCH, s.diagnostics().getLast().code());
            assertTrue(s.diagnostics().getLast().message().contains("scenario"), s.diagnostics().getLast().message());
            assertEquals(0, s.samples());
        }
        Checkpoint older = new Checkpoint(complete.runId(), complete.state(), complete.scenarioPath(), complete.scenarioDigest(),
                complete.planDigest(), complete.dictionaryDigest(), complete.policyDigest(), complete.plugin(), "0", complete.configuration(),
                complete.observations(), complete.attachments());
        try (RunHandle other = RunService.resume(scenario(4), config(), older)) {
            RunSummary s = other.await();
            assertEquals(ErrorCode.CHECKPOINT_MISMATCH, s.diagnostics().getLast().code());
            assertTrue(s.diagnostics().getLast().message().contains("SUT"), s.diagnostics().getLast().message());
        }
    }
}
