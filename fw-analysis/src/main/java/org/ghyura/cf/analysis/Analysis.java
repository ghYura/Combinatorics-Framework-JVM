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
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The analysis of samples, in two passes so every score uses one frozen profile (audit AN-02): first eligibility, goals,
 * statistics and the per-goal ranges, then the exact Pareto front, the ranker's diagnostics, scores, top-K, balanced picks
 * and repeat intervals. Only PASS samples with metrics are eligible: a domain failure, a sample without verdict (broken,
 * timed out, failed infrastructure, skipped, cancelled) or without metrics never wins (audit AN-07). Samples of one
 * candidate in one environment are one point, each goal metric at the median of its repeats. Everything is ordered by
 * candidate id, environment and repeat, so the report does not depend on the order samples arrived in.
 */
public final class Analysis {

    /** Points up to which every point is ranked; above it the diagnostics are computed among the front. */
    static final int FULL_RANKING_LIMIT = 2000;

    /** Why a sample may or may not win. */
    public enum Eligibility {
        ELIGIBLE("eligible"),
        DOMAIN_FAIL("domain-failure"),
        NO_VERDICT("no-verdict"),
        NO_METRICS("no-metrics");

        private final String token;

        Eligibility(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /**
     * One sample: its candidate id, display id, repeat and environment, whether it may win, its verdict and its metrics.
     */
    public record Sample(String id, String display, int repeat, String environment, Eligibility eligibility, Integer verdict,
                         Map<String, Double> metrics) {
        public Sample {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(eligibility, "eligibility");
            metrics = Map.copyOf(metrics);
            if (eligibility == Eligibility.ELIGIBLE && metrics.isEmpty()) {
                eligibility = Eligibility.NO_METRICS;
            }
        }
    }

    /**
     * How to analyse.
     *
     * @param declared  the declared goals
     * @param discovery how undeclared keys become goals (exploratory mode only)
     * @param formal    declared goals only, at least one, and at least one eligible sample
     * @param topK      points listed by score
     * @param front     the ranker
     * @param alpha     the repeat intervals' level
     */
    public record Options(List<Goal> declared, Discovery discovery, boolean formal, int topK, RunConfig.FrontAlgorithm front, double alpha) {
        public Options {
            declared = List.copyOf(declared);
            Objects.requireNonNull(discovery, "discovery");
            Objects.requireNonNull(front, "front");
            if (topK < 1) {
                throw new IllegalArgumentException("top-K is at least 1: " + topK);
            }
        }

        /** The options a configuration gives (cf.analysis.*). */
        public static Options of(RunConfig c) {
            List<Goal> goals = new ArrayList<>();
            if (!c.analysisGoals().isEmpty()) {
                c.analysisGoals().forEach(g -> goals.add(Goal.of(g)));
            } else {
                c.analysisMinimize().forEach(k -> goals.add(Goal.min(k)));
                for (String k : c.analysisMaximize()) {
                    if (goals.stream().noneMatch(g -> g.key().equals(k))) {
                        goals.add(Goal.max(k));
                    }
                }
            }
            boolean formal = c.analysisMode() == RunConfig.AnalysisMode.FORMAL;
            Goal.Mode forced = switch (c.analysisDiscoverMode()) {
                case AUTO -> null;
                case MIN -> Goal.Mode.MINIMIZE;
                case MAX -> Goal.Mode.MAXIMIZE;
            };
            Discovery discovery = formal ? Discovery.DECLARED_ONLY : new Discovery(c.analysisDiscover(), forced, c.analysisDiscoverWeight());
            return new Options(goals, discovery, formal, c.analysisTopK(), c.analysisFront(), RepeatStatistics.DEFAULT_ALPHA);
        }
    }

    private Analysis() {
    }

    /**
     * The samples of a run's final observations: a PASS with metrics is eligible, a DOMAIN_FAIL is a domain failure, every
     * other outcome has no verdict.
     */
    public static List<Sample> samplesOf(List<Observation> finals) {
        List<Sample> samples = new ArrayList<>(finals.size());
        for (Observation o : finals) {
            Eligibility e = switch (o.outcome()) {
                case PASS -> Eligibility.ELIGIBLE;
                case DOMAIN_FAIL -> Eligibility.DOMAIN_FAIL;
                default -> Eligibility.NO_VERDICT;
            };
            Map<String, Double> metrics = o.domain().map(d -> d.metrics()).orElse(Map.of());
            samples.add(new Sample(o.key().candidateId().hex(), o.display(), o.key().repeatIndex(), o.key().environmentId(), e,
                    o.verdictCode().orElse(null), metrics));
        }
        return samples;
    }

    /** Admits the observation adapter until analysis finishes; observations and the returned report belong to the caller. */
    @SuppressWarnings("try")
    public static AnalysisReport analyzeObservations(List<Observation> finals, Options options, MemoryBudget budget) {
        try (MemoryBudget.Lease samples = budget.reserve(MemorySizes.add(1024, MemorySizes.multiply(512, finals.size())),
                "analysis observation adapters")) {
            long metrics = 0;
            for (Observation o : finals) metrics = MemorySizes.add(metrics, o.domain().map(d -> d.metrics().size()).orElse(0));
            try (MemoryBudget.Lease maps = budget.reserve(MemorySizes.multiply(128, metrics), "analysis adapter metric maps")) {
                return analyze(samplesOf(finals), options, budget);
            }
        }
    }

    /** @throws FrameworkException ANALYSIS_FORMAL_INCOMPLETE in formal mode without declared goals or eligible samples */
    public static AnalysisReport analyze(List<Sample> input, Options options) {
        return analyze(input, options, null);
    }

    /**
     * Budgeted analysis admits sorting/repeat workspace, metric summaries, point vectors and ranking workspace before
     * each phase allocates them. Estimates include the worst-case dominance relations and reference directions of the
     * selected ranker. RESOURCE_LIMIT never substitutes a smaller or approximate front. The caller owns the input
     * samples (a MetricCorpus keeps its own lease live) and takes ownership of the returned report; operation leases
     * are released on success and failure. These conservative estimates are not a JVM heap measurement.
     *
     * @throws FrameworkException ANALYSIS_FORMAL_INCOMPLETE as above, RESOURCE_LIMIT when the budget cannot hold the points
     */
    @SuppressWarnings("try")
    public static AnalysisReport analyze(List<Sample> input, Options options, MemoryBudget budget) {
        // Sorted/eligible references, merge-sort scratch, repeat groups and one metric's numeric sorting buffers.
        try (MemoryBudget.Lease workspace = budget == null ? null : budget.reserve(
                MemorySizes.add(1024, MemorySizes.multiply(320, input.size())), "analysis sample and repeat workspace")) {
            return analyzeAdmitted(input, options, budget);
        }
    }

    @SuppressWarnings("try")
    private static AnalysisReport analyzeAdmitted(List<Sample> input, Options options, MemoryBudget budget) {
        List<Sample> samples = new ArrayList<>(input);
        samples.sort(Comparator.comparing(Sample::id).thenComparing(Sample::environment).thenComparingInt(Sample::repeat));
        Map<String, Long> excluded = new TreeMap<>();
        List<Sample> eligible = new ArrayList<>();
        for (Sample s : samples) {
            if (s.eligibility() == Eligibility.ELIGIBLE) {
                eligible.add(s);
            } else {
                excluded.merge(s.eligibility().token(), 1L, Long::sum);
            }
        }
        if (options.formal() && (options.declared().isEmpty() || eligible.isEmpty())) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.ANALYSIS_FORMAL_INCOMPLETE, "formal analysis has "
                    + options.declared().size() + " declared goals and " + eligible.size() + " eligible samples; it needs at least one of each",
                    SourceLocation.NONE));
        }

        // Every occurrence could introduce a distinct key. Admit the union, goal objects and summaries before building them.
        long metricSlots = options.declared().size();
        int longestKey = 0;
        for (Sample s : eligible) {
            metricSlots = MemorySizes.add(metricSlots, s.metrics().size());
            for (String key : s.metrics().keySet()) longestKey = Math.max(longestKey, key.length());
        }
        // Discovery lowercases one key at a time; include its temporary text as well as the shared key references.
        long metricBytes = MemorySizes.add(MemorySizes.add(1024, MemorySizes.multiply(256, metricSlots)),
                MemorySizes.multiply(8, longestKey));
        try (MemoryBudget.Lease metrics = budget == null ? null : budget.reserve(
                metricBytes, "analysis metric summaries and goals")) {
            return analyzeMetrics(samples, eligible, excluded, options, budget);
        }
    }

    private static AnalysisReport analyzeMetrics(List<Sample> samples, List<Sample> eligible, Map<String, Long> excluded,
                                                  Options options, MemoryBudget budget) {
        // pass 1: goals, statistics, points
        List<Goal> goals = new ArrayList<>(options.declared());
        List<String> discovered = new ArrayList<>();
        TreeSet<String> keys = new TreeSet<>();
        eligible.forEach(s -> keys.addAll(s.metrics().keySet()));
        for (String key : keys) {
            if (goals.stream().noneMatch(g -> g.key().equals(key))) {
                Goal g = options.discovery().goalFor(key);
                if (g != null) {
                    goals.add(g);
                    discovered.add(key);
                }
            }
        }
        List<AnalysisReport.MetricSummary> metrics = new ArrayList<>();
        for (String key : keys) {
            KeyStats stats = new KeyStats(key);
            List<Double> values = new ArrayList<>();
            for (Sample s : eligible) {
                Double v = s.metrics().get(key);
                if (v != null) {
                    stats.add(v, s.display());
                    values.add(v);
                }
            }
            double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            metrics.add(new AnalysisReport.MetricSummary(key, stats.count(), stats.mean(), stats.stdev(), stats.min(), stats.max(),
                    nearestRank(sorted, 0.10), nearestRank(sorted, 0.50), nearestRank(sorted, 0.90), stats.argmin(), stats.argmax()));
        }
        Goal[] axes = goals.toArray(new Goal[0]);
        List<PointBuilder> points = new ArrayList<>();
        PointBuilder current = null;
        for (Sample s : eligible) {
            if (current == null || !current.id.equals(s.id()) || !current.environment.equals(s.environment())) {
                current = new PointBuilder(s);
                points.add(current);
            }
            current.samples.add(s);
        }
        int n = points.size();
        // Point/report collections, per-goal maps and repeat intervals, including immutable copies and vectors.
        MemoryBudget.Lease lease = budget == null ? null : budget.reserve(
                MemorySizes.multiply(n, MemorySizes.add(1024, MemorySizes.multiply(384, axes.length))),
                "analysis: " + n + " points, their goal vectors and the exact Pareto front");
        try {
            return analyzePoints(samples, eligible, excluded, goals, discovered, metrics, axes, points, options, budget);
        } finally {
            if (lease != null) {
                lease.close();
            }
        }
    }

    @SuppressWarnings("try")
    private static AnalysisReport analyzePoints(List<Sample> samples, List<Sample> eligible, Map<String, Long> excluded, List<Goal> goals,
                                                List<String> discovered, List<AnalysisReport.MetricSummary> metrics, Goal[] axes,
                                                List<PointBuilder> points, Options options, MemoryBudget budget) {
        int n = points.size();
        double[][] vectors = new double[n][axes.length];
        boolean missing = false;
        boolean repeated = false;
        List<Map<String, RepeatStatistics.Interval>> intervals = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PointBuilder p = points.get(i);
            repeated |= p.samples.size() > 1;
            Map<String, RepeatStatistics.Interval> iv = new LinkedHashMap<>();
            for (int g = 0; g < axes.length; g++) {
                List<Double> values = new ArrayList<>();
                for (Sample s : p.samples) {
                    Double v = s.metrics().get(axes[g].key());
                    if (v != null) {
                        values.add(v);
                    }
                }
                if (values.isEmpty()) {
                    vectors[i][g] = Double.NaN;
                    missing = true;
                    continue;
                }
                double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                vectors[i][g] = RepeatStatistics.median(sorted);
                if (p.samples.size() > 1) {
                    Goal goal = axes[g];
                    iv.put(goal.key(), RepeatStatistics.interval(values.stream().mapToDouble(goal::deviation).toArray(), options.alpha()));
                }
            }
            intervals.add(iv);
        }
        // the frozen profile: per goal, the best and worst deviation over the points that have the metric
        double[] devMin = new double[axes.length];
        double[] devMax = new double[axes.length];
        boolean[] active = new boolean[axes.length];
        for (int g = 0; g < axes.length; g++) {
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            double rawMin = Double.POSITIVE_INFINITY;
            double rawMax = Double.NEGATIVE_INFINITY;
            for (double[] v : vectors) {
                if (!Double.isNaN(v[g])) {
                    lo = Math.min(lo, axes[g].deviation(v[g]));
                    hi = Math.max(hi, axes[g].deviation(v[g]));
                    rawMin = Math.min(rawMin, v[g]);
                    rawMax = Math.max(rawMax, v[g]);
                    active[g] = true;
                }
            }
            if (axes[g].mode() == Goal.Mode.TARGET && active[g]) {
                lo = 0;
                hi = Math.max(Math.abs(rawMax - axes[g].target()), Math.abs(rawMin - axes[g].target()));
            }
            devMin[g] = lo;
            devMax[g] = hi;
        }

        // pass 2: front, ranks, scores
        List<Integer> frontIndexes = missing ? Dominance.front(vectors, axes) : archive(vectors, axes);
        int[] rank = new int[n];
        List<Map<String, Double>> diagnostics = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            diagnostics.add(Map.of());
        }
        boolean complete = n <= FULL_RANKING_LIMIT;
        int rankedPoints = complete ? n : frontIndexes.size();
        try (MemoryBudget.Lease ranking = budget == null ? null : budget.reserve(
                FrontRanking.workingBytes(options.front(), rankedPoints, axes.length, !complete), "analysis ranking workspace")) {
            double[][] rankedVectors = vectors;
            if (!complete) {
                rankedVectors = new double[frontIndexes.size()][];
                for (int k = 0; k < frontIndexes.size(); k++) rankedVectors[k] = vectors[frontIndexes.get(k)];
            }
            for (FrontRanking.Rank r : FrontRanking.rank(options.front(), rankedVectors, axes)) {
                int index = complete ? r.index() : frontIndexes.get(r.index());
                rank[index] = complete ? r.rank() : 1;
                diagnostics.set(index, r.diagnostics());
            }
        }
        List<AnalysisReport.Point> built = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            PointBuilder p = points.get(i);
            Map<String, Double> values = new LinkedHashMap<>();
            double score = 0;
            for (int g = 0; g < axes.length; g++) {
                if (!Double.isNaN(vectors[i][g])) {
                    values.put(axes[g].key(), vectors[i][g]);
                }
                if (active[g]) {
                    score += axes[g].weight() * normalized(axes[g], vectors[i][g], devMin[g], devMax[g]);
                }
            }
            built.add(new AnalysisReport.Point(p.id, p.display, p.environment, p.samples.size(), values, score, rank[i], diagnostics.get(i),
                    intervals.get(i)));
        }
        List<AnalysisReport.Point> front = new ArrayList<>();
        frontIndexes.forEach(i -> front.add(built.get(i)));
        List<AnalysisReport.Point> top = new ArrayList<>(built);
        top.sort(Comparator.comparingDouble(AnalysisReport.Point::score).thenComparing(AnalysisReport.Point::id)
                .thenComparing(AnalysisReport.Point::environment));
        List<AnalysisReport.Point> best = top.subList(0, Math.min(options.topK(), top.size()));
        List<AnalysisReport.Pick> balanced = new ArrayList<>();
        for (String strategy : List.of("weighted-sum", "tchebycheff", "distance-to-ideal")) {
            balanced.add(pick(strategy, front, frontIndexes, vectors, axes, active, devMin, devMax));
        }
        return new AnalysisReport(samples.size(), eligible.size(), excluded, goals, discovered, options.formal(), options.front(), metrics, n,
                front, missing ? "pairwise" : "archive", complete, best, balanced, repeated, built);
    }

    /** The exact front by an archive of non-dominated points; valid when no value is missing (dominance is then transitive). */
    static List<Integer> archive(double[][] vectors, Goal[] goals) {
        List<Integer> archive = new ArrayList<>();
        for (int i = 0; i < vectors.length; i++) {
            boolean dominated = false;
            for (int j : archive) {
                if (Dominance.dominates(vectors[j], vectors[i], goals)) {
                    dominated = true;
                    break;
                }
            }
            if (dominated) {
                continue;
            }
            int candidate = i;
            archive.removeIf(j -> Dominance.dominates(vectors[candidate], vectors[j], goals));
            archive.add(i);
        }
        archive.sort(Integer::compare);
        return archive;
    }

    /** weight-free normalised deviation in [0, 1], 0 best; a missing value is the worst, 1. */
    static double normalized(Goal goal, double value, double devMin, double devMax) {
        if (Double.isNaN(value)) {
            return 1;
        }
        double range = devMax - devMin;
        if (range <= 1e-15) {
            return 0;
        }
        return Math.max(0, Math.min(1, (goal.deviation(value) - devMin) / range));
    }

    /** A balanced pick of the front (legacy BalancedOptimumSelector: a member missing an active goal metric is skipped). */
    private static AnalysisReport.Pick pick(String strategy, List<AnalysisReport.Point> front, List<Integer> indexes, double[][] vectors,
                                            Goal[] axes, boolean[] active, double[] devMin, double[] devMax) {
        boolean any = false;
        for (boolean a : active) {
            any |= a;
        }
        if (front.isEmpty() || !any) {
            return new AnalysisReport.Pick(strategy, null, Double.NaN, Map.of(), front.isEmpty() ? "empty front" : "no active goal");
        }
        AnalysisReport.Point best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        Map<String, Double> bestContrib = Map.of();
        for (int k = 0; k < front.size(); k++) {
            double[] v = vectors[indexes.get(k)];
            Map<String, Double> contrib = new LinkedHashMap<>();
            boolean usable = true;
            for (int g = 0; g < axes.length && usable; g++) {
                if (!active[g]) {
                    continue;
                }
                if (Double.isNaN(v[g])) {
                    usable = false;
                } else {
                    contrib.put(axes[g].key(), axes[g].weight() * normalized(axes[g], v[g], devMin[g], devMax[g]));
                }
            }
            if (!usable) {
                continue;
            }
            double score = switch (strategy) {
                case "weighted-sum" -> contrib.values().stream().mapToDouble(Double::doubleValue).sum();
                case "tchebycheff" -> contrib.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
                default -> Math.sqrt(contrib.values().stream().mapToDouble(x -> x * x).sum());
            };
            if (score < bestScore) {
                bestScore = score;
                best = front.get(k);
                bestContrib = contrib;
            }
        }
        return best == null ? new AnalysisReport.Pick(strategy, null, Double.NaN, Map.of(), "every front member misses a goal metric")
                : new AnalysisReport.Pick(strategy, best, bestScore, bestContrib, "scalarised over the active goals");
    }

    private static double nearestRank(double[] sorted, double p) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        int rank = (int) Math.ceil(p * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    private static final class PointBuilder {
        final String id;
        final String display;
        final String environment;
        final List<Sample> samples = new ArrayList<>();

        PointBuilder(Sample first) {
            this.id = first.id();
            this.environment = first.environment();
            this.display = (first.display() == null ? first.id() : first.display()) + (first.environment().isEmpty() ? "" : "@" + first.environment());
        }
    }
}
