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

package org.ghyura.cf.analysis;

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.AbstractList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The analysis end to end: who may win, discovered directions, the front, frozen-profile scores, picks and repeats. */
class AnalysisTest {

    @Test
    void observationAdaptersAreAdmittedBeforeCopyingAndStayChargedThroughAnalysis() {
        MemoryBudget budget = new MemoryBudget(1 << 20);
        List<Observation> oversized = new AbstractList<>() {
            @Override public int size() { return 1_000_000; }
            @Override public Observation get(int i) { throw new AssertionError("adapter read before admission"); }
        };
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> Analysis.analyzeObservations(oversized, options(List.of(), false, true), budget)).code());
        Observation o = new Observation(new SampleKey(new RunId("r"), new CandidateId("a".repeat(64)), 0, 0, ""), "a", Outcome.PASS,
                Optional.of(new DomainObservation(0, Map.of("latency", 7.0), Map.of())), 0, "s", "p", Optional.empty(), "", "", "", false);
        List<Observation> observations = List.of(o);
        Analysis.Options opts = options(List.of(Goal.min("latency")), true, false);
        try (MemoryBudget.Lease owner = budget.reserve(4096, "caller observations")) {
            assertEquals(Analysis.analyze(Analysis.samplesOf(observations), opts), Analysis.analyzeObservations(observations, opts, budget));
            assertEquals(owner.bytes(), budget.reservedBytes());
            assertTrue(budget.highWaterBytes() > owner.bytes() + 1024);
            assertThrows(FrameworkException.class, () -> Analysis.analyzeObservations(observations, options(List.of(), true, false), budget));
            assertEquals(owner.bytes(), budget.reservedBytes());
        }
        assertEquals(0, budget.reservedBytes());
    }

    private static Analysis.Sample s(String id, int repeat, String env, Analysis.Eligibility e, Integer verdict, Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        }
        return new Analysis.Sample(id, id, repeat, env, e, verdict, m);
    }

    private static Analysis.Options options(List<Goal> goals, boolean formal, boolean discover) {
        return new Analysis.Options(goals, formal ? Discovery.DECLARED_ONLY : new Discovery(discover, null, 1), formal, 2,
                RunConfig.FrontAlgorithm.NSGA_II, 0.05);
    }

    @Test
    void onlyPassingSamplesWinTheScoresUseOneProfileAndInputOrderChangesNothing() {
        List<Analysis.Sample> samples = new ArrayList<>(List.of(
                s("a", 0, "", Analysis.Eligibility.ELIGIBLE, 0, "latency", 10, "throughput", 100),
                s("b", 0, "", Analysis.Eligibility.ELIGIBLE, 0, "latency", 20, "throughput", 200),
                s("c", 0, "", Analysis.Eligibility.ELIGIBLE, 0, "latency", 30, "throughput", 150),
                s("d", 0, "", Analysis.Eligibility.DOMAIN_FAIL, 7, "latency", 1, "throughput", 999),
                s("e", 0, "", Analysis.Eligibility.NO_VERDICT, null),
                s("f", 0, "", Analysis.Eligibility.ELIGIBLE, 0)));
        AnalysisReport r = Analysis.analyze(samples, options(List.of(), false, true));
        assertEquals(6, r.samples());
        assertEquals(3, r.eligible());
        assertEquals(Map.of("domain-failure", 1L, "no-verdict", 1L, "no-metrics", 1L), r.excluded(), "the best numbers of a domain failure never win");
        assertEquals(List.of(Goal.min("latency"), Goal.max("throughput")), r.goals(), "directions from the key names");
        assertEquals(List.of("a", "b"), r.front().stream().map(AnalysisReport.Point::id).toList(), "c is dominated by b");
        // latency deviations span [10, 30], throughput deviations [-200, -100]: a = 0 + 1, b = 0.5 + 0, c = 1 + 0.5
        assertEquals(List.of("b", "a"), r.top().stream().map(AnalysisReport.Point::id).toList());
        assertEquals(0.5, r.top().getFirst().score(), 1e-15);
        assertEquals(List.of("b", "b", "b"), r.balanced().stream().map(p -> p.chosen().id()).toList());
        assertEquals("archive", r.frontMethod());
        AnalysisReport.MetricSummary latency = r.metrics().getFirst();
        assertEquals(20.0, latency.mean(), 1e-12);
        assertEquals("a", latency.argmin());
        Collections.shuffle(samples, new Random(3));
        assertEquals(r, Analysis.analyze(samples, options(List.of(), false, true)), "the same report in any input order");
    }

    @Test
    void repeatsAggregateByTheirMedianEnvironmentsStayApartAndFormalModeUsesDeclaredGoalsOnly() {
        List<Analysis.Sample> samples = new ArrayList<>();
        for (int k = 0; k < 10; k++) {
            samples.add(s("r", k, "x", Analysis.Eligibility.ELIGIBLE, 0, "latency", k + 1, "noise", k));
        }
        samples.add(s("r", 0, "y", Analysis.Eligibility.ELIGIBLE, 0, "latency", 9));
        samples.add(s("q", 0, "x", Analysis.Eligibility.ELIGIBLE, 0, "latency", 4));
        AnalysisReport r = Analysis.analyze(samples, options(List.of(Goal.min("latency")), true, false));
        assertEquals(List.of(Goal.min("latency")), r.goals(), "formal: no discovered axis");
        assertEquals(3, r.points(), "r in x, r in y and q are three points");
        assertTrue(r.repeated());
        AnalysisReport.Point rx = r.top().stream().filter(p -> p.display().equals("r@x")).findFirst().orElseThrow();
        assertEquals(5.5, rx.values().get("latency"), "the median of 1..10");
        assertTrue(rx.intervals().get("latency").valid());
        assertEquals(List.of("q@x"), r.front().stream().map(AnalysisReport.Point::display).toList());
        FrameworkException e = assertThrows(FrameworkException.class, () -> Analysis.analyze(samples, options(List.of(), true, false)));
        assertEquals(ErrorCode.ANALYSIS_FORMAL_INCOMPLETE, e.diagnostics().getFirst().code());
        AnalysisReport target = Analysis.analyze(samples, options(List.of(Goal.target("latency", 9)), false, false));
        assertEquals(List.of("r@y"), target.front().stream().map(AnalysisReport.Point::display).toList(), "exactly on target");
        // exact-budgeted: the points are reserved before they are built; too small a budget is RESOURCE_LIMIT, not a smaller front
        org.ghyura.cf.store.MemoryBudget budget = new org.ghyura.cf.store.MemoryBudget(1 << 20);
        assertEquals(r, Analysis.analyze(samples, options(List.of(Goal.min("latency")), true, false), budget));
        assertEquals(0, budget.reservedBytes(), "returned after the analysis");
        FrameworkException full = assertThrows(FrameworkException.class, () -> Analysis.analyze(samples,
                options(List.of(Goal.min("latency")), true, false), new org.ghyura.cf.store.MemoryBudget(100)));
        assertEquals(ErrorCode.RESOURCE_LIMIT, full.diagnostics().getFirst().code());
    }

    @Test
    void sampleAdmissionPrecedesCopyingAndPreservesTheCallersReservation() {
        List<Analysis.Sample> oversized = new AbstractList<>() {
            @Override public int size() { return 1_000_000; }
            @Override public Analysis.Sample get(int index) { throw new AssertionError("copied before admission"); }
        };
        MemoryBudget budget = new MemoryBudget(4096);
        try (MemoryBudget.Lease owner = budget.reserve(512, "caller-owned corpus")) {
            assertEquals(512, owner.bytes());
            FrameworkException refusal = assertThrows(FrameworkException.class,
                    () -> Analysis.analyze(oversized, options(List.of(), false, true), budget));
            assertEquals(ErrorCode.RESOURCE_LIMIT, refusal.primary().code());
            assertEquals(512, budget.reservedBytes());
            assertThrows(FrameworkException.class, () -> Analysis.analyze(List.of(), options(List.of(), true, false), budget));
            assertEquals(512, budget.reservedBytes(), "formal-validation failure also returns its workspace");
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void repeatsAndMetricSummariesDoNotEscapeAnalysisAdmission() {
        List<Analysis.Sample> repeats = Collections.nCopies(5000, s("r", 0, "", Analysis.Eligibility.ELIGIBLE, 0, "latency", 7));
        Analysis.Options options = options(List.of(Goal.min("latency")), true, false);
        MemoryBudget small = new MemoryBudget(64 << 10);
        FrameworkException repeated = assertThrows(FrameworkException.class, () -> Analysis.analyze(repeats, options, small));
        assertEquals(ErrorCode.RESOURCE_LIMIT, repeated.primary().code());
        assertEquals(0, small.reservedBytes());
        Map<String, Double> manyMetrics = new LinkedHashMap<>();
        for (int i = 0; i < 2000; i++) manyMetrics.put("m" + i, (double) i);
        manyMetrics.put("latency", 7.0);
        Analysis.Sample wide = new Analysis.Sample("w", "w", 0, "", Analysis.Eligibility.ELIGIBLE, 0, manyMetrics);
        FrameworkException metrics = assertThrows(FrameworkException.class, () -> Analysis.analyze(List.of(wide), options, small));
        assertEquals(ErrorCode.RESOURCE_LIMIT, metrics.primary().code());
        assertEquals(0, small.reservedBytes());
        MemoryBudget enough = new MemoryBudget(8 << 20);
        AnalysisReport report = Analysis.analyze(repeats, options, enough);
        assertEquals(5000, report.eligible());
        assertEquals(1, report.points());
        assertEquals(7.0, report.front().getFirst().values().get("latency"));
        assertTrue(enough.highWaterBytes() > small.totalBytes(), "repeat workspace grows even when the point count stays one");
        assertEquals(0, enough.reservedBytes());
    }

    @Test
    void rankingAdmitsDominanceEdgesAndReferenceDirectionsWithoutApproximating() {
        List<Analysis.Sample> chain = new ArrayList<>();
        for (int i = 0; i < 800; i++) chain.add(s(Integer.toString(i), 0, "", Analysis.Eligibility.ELIGIBLE, 0, "latency", i));
        MemoryBudget small = new MemoryBudget(8 << 20);
        FrameworkException edges = assertThrows(FrameworkException.class,
                () -> Analysis.analyze(chain, options(List.of(Goal.min("latency")), true, false), small));
        assertEquals(ErrorCode.RESOURCE_LIMIT, edges.primary().code());
        assertTrue(edges.primary().message().contains("analysis ranking"), edges.getMessage());
        assertEquals(0, small.reservedBytes());
        MemoryBudget enough = new MemoryBudget(32 << 20);
        AnalysisReport exact = Analysis.analyze(chain, options(List.of(Goal.min("latency")), true, false), enough);
        assertEquals(List.of("0"), exact.front().stream().map(AnalysisReport.Point::id).toList());
        assertEquals(800, exact.points());
        assertTrue(exact.ranksComplete());
        assertEquals(800, exact.all().stream().mapToInt(AnalysisReport.Point::rank).max().orElseThrow());
        assertEquals(0, enough.reservedBytes());

        Map<String, Double> metrics = new LinkedHashMap<>();
        List<Goal> goals = new ArrayList<>();
        for (int i = 0; i < 60; i++) { metrics.put("m" + i, 1.0); goals.add(Goal.min("m" + i)); }
        Analysis.Sample wide = new Analysis.Sample("w", "w", 0, "", Analysis.Eligibility.ELIGIBLE, 0, metrics);
        for (RunConfig.FrontAlgorithm algorithm : List.of(RunConfig.FrontAlgorithm.NSGA_III, RunConfig.FrontAlgorithm.MOEA_D)) {
            MemoryBudget budget = new MemoryBudget(1 << 20);
            FrameworkException refs = assertThrows(FrameworkException.class, () -> Analysis.analyze(List.of(wide),
                    new Analysis.Options(goals, Discovery.DECLARED_ONLY, true, 2, algorithm, 0.05), budget));
            assertEquals(ErrorCode.RESOURCE_LIMIT, refs.primary().code());
            assertTrue(refs.primary().message().contains("analysis ranking"), refs.getMessage());
            assertEquals(0, budget.reservedBytes());
        }
        List<Analysis.Sample> tiny = chain.subList(0, 4);
        for (RunConfig.FrontAlgorithm algorithm : RunConfig.FrontAlgorithm.values()) {
            Analysis.Options opts = new Analysis.Options(List.of(Goal.min("latency")), Discovery.DECLARED_ONLY, true, 2, algorithm, 0.05);
            assertEquals(Analysis.analyze(tiny, opts), Analysis.analyze(tiny, opts, enough), algorithm.toString());
            assertEquals(0, enough.reservedBytes());
        }

        // Above the full-ranking threshold only the known Pareto front is ranked; it has no dominance edges to store.
        List<Analysis.Sample> antichain = new ArrayList<>();
        for (int i = 0; i < 2100; i++)
            antichain.add(s(Integer.toString(i), 0, "", Analysis.Eligibility.ELIGIBLE, 0, "latency", i, "throughput", i));
        AnalysisReport front = Analysis.analyze(antichain,
                options(List.of(Goal.min("latency"), Goal.max("throughput")), true, false), small);
        assertEquals(2100, front.front().size());
        assertTrue(!front.ranksComplete());
        assertTrue(front.front().stream().allMatch(p -> p.rank() == 1));
        assertEquals(0, small.reservedBytes());
    }
}
