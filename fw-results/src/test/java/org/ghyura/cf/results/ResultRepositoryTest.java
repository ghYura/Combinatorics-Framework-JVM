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

package org.ghyura.cf.results;

import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.model.SampleSlot;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultRepositoryTest {

    private static final RunId RUN = new RunId("r1");
    private static final CandidateId A = new CandidateId("a".repeat(64));
    private static final CandidateId B = new CandidateId("b".repeat(64));

    private static Observation verdict(SampleKey key, int code) {
        return new Observation(key, "1_0_0", code == 0 ? Outcome.PASS : Outcome.DOMAIN_FAIL, Optional.of(DomainObservation.of(code)), 10, "s", "p",
                Optional.empty(), "", "", "", false);
    }

    private static Observation timeout(SampleKey key) {
        return new Observation(key, "1_0_0", Outcome.TIMEOUT, Optional.empty(), 10, "s", "p", Optional.of(ErrorCode.EXEC_DEADLINE), "late", "", "",
                false);
    }

    @Test
    void aggregateTotalsMatchFullRetentionAcrossRetriesWhileMemoryStaysBounded() {
        MemoryBudget bounded = new MemoryBudget(64 * 1024);
        try (ResultRepository full = new ResultRepository(RUN, 10000, new MemoryBudget(8 * 1024 * 1024),
                ResultRepository.Scope.METRICS, 0);
             ResultRepository aggregate = new ResultRepository(RUN, 8, bounded, ResultRepository.Scope.METRICS, 1024,
                     ResultRepository.Retention.AGGREGATE_ONLY)) {
            long afterFirst = 0;
            long peakAfterFirst = 0;
            for (int i = 1; i <= 2000; i++) {
                CandidateId id = new CandidateId(String.format(Locale.ROOT, "%064x", i));
                SampleKey key = new SampleKey(RUN, id, 0, 0, "");
                List<Observation> attempts = List.of(timeout(key), verdict(key.nextAttempt(), 0),
                        verdict(new SampleKey(RUN, id, 0, 1, ""), 0), verdict(new SampleKey(RUN, id, 0, 2, ""), 31));
                for (Observation o : attempts) { full.commit(o); aggregate.commit(o); }
                if (i == 1) {
                    assertEquals(ResultRepository.CommitResult.IDENTICAL_REPLAY, aggregate.commit(attempts.getFirst()));
                    assertEquals(ErrorCode.RESULT_CONFLICT, assertThrows(FrameworkException.class,
                            () -> aggregate.commit(verdict(key, 31))).diagnostics().getFirst().code());
                }
                assertFalse(aggregate.attach(key, "source", "abc"));
                for (Observation o : attempts) aggregate.slotFinal(o.slot());
                assertEquals(full.totals(), aggregate.totals());
                for (Observation o : attempts) aggregate.slotDelivered(o.slot());
                if (i == 1) { afterFirst = bounded.reservedBytes(); peakAfterFirst = bounded.highWaterBytes(); }
                assertEquals(afterFirst, bounded.reservedBytes());
                assertEquals(peakAfterFirst, bounded.highWaterBytes());
            }
            assertEquals(8000, aggregate.totals().attempts());
            assertEquals(2000, aggregate.totals().retried());
            assertEquals(4000, aggregate.totals().measurements());
            assertTrue(aggregate.all().isEmpty() && aggregate.finals().isEmpty() && aggregate.allAttachments().isEmpty());
            assertEquals(2000, aggregate.attachmentTotals().omitted());
        }
        assertEquals(0, bounded.reservedBytes());
    }

    @Test
    void aggregateDistinctVerdictsConsumeBudgetAndFailureKeepsCommittedTotals() {
        MemoryBudget budget = new MemoryBudget(4096);
        try (ResultRepository repo = new ResultRepository(RUN, 8, budget, ResultRepository.Scope.ALL, 0,
                ResultRepository.Retention.AGGREGATE_ONLY)) {
            int committed = 0;
            while (true) {
                SampleKey key = new SampleKey(RUN, new CandidateId(String.format(Locale.ROOT, "%064x", committed + 1)), 0, 0, "");
                try { repo.commit(verdict(key, committed)); }
                catch (FrameworkException e) { assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code()); break; }
                repo.slotFinal(key.slot()); repo.slotDelivered(key.slot()); committed++;
            }
            assertTrue(committed > 1 && committed < 100);
            assertEquals(committed, repo.totals().samples());
            assertEquals(committed, repo.totals().verdicts().size());
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void oneObservationPerKeyReplaysAreHarmlessConflictsAreErrors() throws Exception {
        MemoryBudget budget = new MemoryBudget(1 << 20);
        try (ResultRepository repo = new ResultRepository(RUN, 1000, budget)) {
            SampleKey key = new SampleKey(RUN, A, 0, 0, "");
            assertEquals(ResultRepository.CommitResult.INSERTED, repo.commit(verdict(key, 20)));
            assertEquals(ResultRepository.CommitResult.IDENTICAL_REPLAY, repo.commit(verdict(key, 20)));
            FrameworkException e = assertThrows(FrameworkException.class, () -> repo.commit(verdict(key, 0)));
            assertEquals(ErrorCode.RESULT_CONFLICT, e.diagnostics().getFirst().code());
            assertEquals(20, repo.find(key).orElseThrow().verdictCode().orElseThrow(), "the committed observation stays");
            // concurrent duplicates of one payload: exactly one insert
            SampleKey other = new SampleKey(RUN, B, 0, 0, "");
            Observation same = verdict(other, 7);
            AtomicInteger inserted = new AtomicInteger();
            CountDownLatch go = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                threads.add(Thread.ofPlatform().start(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException ex) {
                        return;
                    }
                    if (repo.commit(same) == ResultRepository.CommitResult.INSERTED) {
                        inserted.incrementAndGet();
                    }
                }));
            }
            go.countDown();
            for (Thread t : threads) {
                t.join();
            }
            assertEquals(1, inserted.get());
            assertEquals(2, repo.totals().attempts());
        }
        assertEquals(0, budget.reservedBytes(), "closing returns every reservation");
    }

    @Test
    void theHighestAttemptIsFinalPerRepeatAndTotalsStayApart() {
        try (ResultRepository repo = new ResultRepository(RUN, 1000, new MemoryBudget(1 << 20))) {
            SampleKey r0 = new SampleKey(RUN, A, 0, 0, "");
            SampleKey r1 = new SampleKey(RUN, A, 0, 1, "");
            repo.commit(timeout(r0));
            repo.commit(verdict(r0.nextAttempt(), 0));
            repo.commit(verdict(r1, 31));
            assertEquals(Outcome.PASS, repo.latest(new SampleSlot(RUN, A, 0, "")).orElseThrow().outcome());
            ResultRepository.Totals t = repo.totals();
            assertEquals(3, t.attempts());
            assertEquals(2, t.samples(), "two repeats of one candidate are two samples");
            assertEquals(1, t.retried());
            assertEquals(1, t.count(Outcome.PASS));
            assertEquals(1, t.count(Outcome.DOMAIN_FAIL));
            assertEquals(0, t.count(Outcome.TIMEOUT), "a retried attempt is history, not a final outcome");
            assertEquals(java.util.Map.of(0, 1L, 31, 1L), t.verdicts());
            assertEquals(3, repo.all().size());
            assertEquals(2, repo.finals().size());
        }
    }

    @Test
    void capacityAndBudgetAreExplicitLimits() {
        try (ResultRepository repo = new ResultRepository(RUN, 1, new MemoryBudget(1 << 20))) {
            repo.commit(verdict(new SampleKey(RUN, A, 0, 0, ""), 0));
            FrameworkException e = assertThrows(FrameworkException.class, () -> repo.commit(verdict(new SampleKey(RUN, B, 0, 0, ""), 0)));
            assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
        }
        try (ResultRepository repo = new ResultRepository(RUN, 10, new MemoryBudget(100))) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> repo.commit(verdict(new SampleKey(RUN, A, 0, 0, ""), 0)));
            assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
            assertEquals(0, repo.all().size());
        }
    }

    private static Observation harvested(CandidateId id, int repeat, String env, String display, String line) {
        return new Observation(new SampleKey(RUN, id, 0, repeat, env), display, Outcome.PASS, Optional.of(new DomainObservation(0,
                Map.of(), Map.of(LegacyMetricsCorpus.LINE_ATTRIBUTE, line))), 10, "s", "p", Optional.empty(), "", "", "", false);
    }

    @Test
    void theLegacyMetricsCorpusKeepsTheLegacyLinesAndStringOrderInAnyLocale() {
        CandidateId c = new CandidateId("c".repeat(64));
        // golden: the expected text of the legacy MetricsHarvestCorpusTest (Executor_trunk, K=1 and K>1 cases)
        List<Observation> k1 = List.of(
                harvested(A, 0, "", "9_0_0", "app=perf_opt_java algo=simd threads=8 batch=256 cache=large latency_ms=4.648 "
                        + "throughput_rps=1548.9 memory_mb=244.0 FW_VAR=0"),
                harvested(B, 0, "", "10_0_0", "app=perf_opt_java algo=naive threads=2 batch=64 cache=large latency_ms=33.024 "
                        + "throughput_rps=54.5 memory_mb=114.0 FW_VAR=0"),
                harvested(c, 0, "", "1_0_0", "app=perf_opt_java algo=naive threads=1 batch=16 cache=small latency_ms=100.000 "
                        + "throughput_rps=9.0 memory_mb=18.5 FW_VAR=0"),
                verdict(new SampleKey(RUN, new CandidateId("d".repeat(64)), 0, 0, ""), 0));
        String expected = "candidate_id=10_0_0 source_ref=10_0_0.java run_id=run-XYZ app=perf_opt_java algo=naive threads=2 batch=64 "
                + "cache=large latency_ms=33.024 throughput_rps=54.5 memory_mb=114.0 FW_VAR=0\n"
                + "candidate_id=1_0_0 source_ref=1_0_0.java run_id=run-XYZ app=perf_opt_java algo=naive threads=1 batch=16 cache=small "
                + "latency_ms=100.000 throughput_rps=9.0 memory_mb=18.5 FW_VAR=0\n"
                + "candidate_id=9_0_0 source_ref=9_0_0.java run_id=run-XYZ app=perf_opt_java algo=simd threads=8 batch=256 cache=large "
                + "latency_ms=4.648 throughput_rps=1548.9 memory_mb=244.0 FW_VAR=0\n";
        List<Observation> repeats = List.of(
                harvested(A, 1, "local:run-r", "7_0_0", "app=x FW_VAR=0 latency_ms=90"),
                harvested(A, 0, "local:run-r", "7_0_0", "app=x FW_VAR=0 latency_ms=10"),
                harvested(B, 0, "local:run-r", "12_0_0", "app=x FW_VAR=0 latency_ms=20"));
        String expectedRepeats = "candidate_id=12_0_0 source_ref=12_0_0.java run_id=run-r repeat_idx=0 env_id=local:run-r app=x FW_VAR=0 "
                + "latency_ms=20\n"
                + "candidate_id=7_0_0 source_ref=7_0_0.java run_id=run-r repeat_idx=0 env_id=local:run-r app=x FW_VAR=0 latency_ms=10\n"
                + "candidate_id=7_0_0 source_ref=7_0_0.java run_id=run-r repeat_idx=1 env_id=local:run-r app=x FW_VAR=0 latency_ms=90\n";
        Locale before = Locale.getDefault();
        try {
            for (Locale locale : List.of(Locale.ROOT, Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("ar-EG"))) {
                Locale.setDefault(locale);
                assertEquals(expected, LegacyMetricsCorpus.format(k1, "run-XYZ", false), locale.toString());
                assertEquals(expectedRepeats, LegacyMetricsCorpus.format(repeats, "run-r", true), locale.toString());
            }
        } finally {
            Locale.setDefault(before);
        }
        assertEquals("", LegacyMetricsCorpus.format(List.of(verdict(new SampleKey(RUN, A, 0, 0, ""), 0)), "r", false),
                "nothing harvested: an empty corpus");
    }

    @Test
    void measurementsCountApartAndAttachmentsStayWithinTheirBound() {
        MemoryBudget budget = new MemoryBudget(1 << 20);
        try (ResultRepository repo = new ResultRepository(RUN, 100, budget, ResultRepository.Scope.METRICS, 10)) {
            SampleKey sample = new SampleKey(RUN, A, 0, 0, "");
            SampleKey first = new SampleKey(RUN, A, 0, 1, "");
            repo.commit(verdict(sample, 3));
            repo.commit(verdict(first, 0));
            repo.commit(timeout(new SampleKey(RUN, A, 0, 2, "")));
            ResultRepository.Totals t = repo.totals();
            assertEquals(1, t.samples(), "repeat 0 is the verdict sample");
            assertEquals(Map.of(3, 1L), t.verdicts());
            assertEquals(2, t.measurements());
            assertEquals(1L, t.measurementOutcomes().get(Outcome.PASS));
            assertEquals(1L, t.measurementOutcomes().get(Outcome.TIMEOUT));
            assertEquals(0L, t.count(Outcome.TIMEOUT), "a measurement's outcome is not a verdict sample's");
            assertTrue(repo.attach(sample, "source", "\u00e9\u00e9\u00e9"), "6 UTF-8 bytes fit 10");
            assertTrue(repo.attach(sample, "source", "\u00e9\u00e9\u00e9"), "the same text again changes nothing");
            FrameworkException e = assertThrows(FrameworkException.class, () -> repo.attach(sample, "source", "other"));
            assertEquals(ErrorCode.RESULT_CONFLICT, e.diagnostics().getFirst().code());
            assertFalse(repo.attach(first, "source", "12345"), "6 + 5 bytes exceed the bound: omitted, counted");
            assertEquals(new ResultRepository.AttachmentTotals(1, 6, 1, 5, 10), repo.attachmentTotals());
            assertEquals(List.of(new ResultRepository.Attachment(sample, "source", "\u00e9\u00e9\u00e9")), repo.allAttachments());
        }
        assertEquals(0, budget.reservedBytes());
    }
}
