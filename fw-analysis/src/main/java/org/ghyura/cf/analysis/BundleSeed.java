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

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The winners of an analysis as the legacy feedback loop exchanged them (Analyzer BundleSeed, schema 1): pareto winners,
 * the champions of each goal metric (champion-min:KEY, champion-max:KEY) and the balanced picks (balanced:STRATEGY), with
 * the metric ranges observed. A seed written by this build adds each winner's sheet selections, so the next iteration's
 * bias needs no database; a legacy seed has none and cannot be joined to rows here.
 *
 * @param generatedAtEpochMs      when it was written (left out of the content digest)
 * @param sourceRunId             the run it came from
 * @param totalCandidatesObserved samples the analysis read
 * @param declaredMetrics         the declared goal keys
 * @param observedRanges          statistics of each metric key
 * @param winners                 the winners, front first
 */
public record BundleSeed(long generatedAtEpochMs, String sourceRunId, long totalCandidatesObserved, List<String> declaredMetrics,
                         Map<String, MetricRange> observedRanges, List<Winner> winners) {

    public BundleSeed {
        Objects.requireNonNull(sourceRunId, "sourceRunId");
        declaredMetrics = List.copyOf(declaredMetrics);
        observedRanges = Collections.unmodifiableMap(new LinkedHashMap<>(observedRanges));
        winners = List.copyOf(winners);
    }

    /**
     * One winner.
     *
     * @param lineNo            its position among the analysis points (1-based, by id)
     * @param score             its goal score (NaN when unknown)
     * @param role              pareto, champion-min:KEY, champion-max:KEY or balanced:STRATEGY
     * @param kvPairs           candidate_id and the goal metric values, as text
     * @param crowdingDistance  its crowding distance, NaN when unknown
     * @param originalLine      the line it came from (legacy), or null
     * @param lineType          the legacy line type, or null
     * @param selections        sheet to selected values (this build's extension), empty for a legacy winner
     */
    public record Winner(int lineNo, double score, String role, Map<String, String> kvPairs, double crowdingDistance, String originalLine,
                         String lineType, Map<String, List<String>> selections) {
        public Winner {
            Objects.requireNonNull(role, "role");
            kvPairs = Collections.unmodifiableMap(new LinkedHashMap<>(kvPairs));
            Map<String, List<String>> copy = new LinkedHashMap<>();
            selections.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            selections = Collections.unmodifiableMap(copy);
        }

        public String candidateId() {
            return kvPairs.getOrDefault("candidate_id", "");
        }
    }

    public record MetricRange(String key, long n, double min, double max, double mean, double stdev) {
    }

    /**
     * The seed of an analysis.
     *
     * @param selections sheet selections of winner candidates by candidate id; a winner without an entry has none
     */
    public static BundleSeed of(AnalysisReport report, Map<String, Map<String, List<String>>> selections, String runId, long epochMs) {
        List<AnalysisReport.Point> byId = report.all();
        Map<AnalysisReport.Point, Integer> lineNo = new java.util.IdentityHashMap<>();
        for (int i = 0; i < byId.size(); i++) {
            lineNo.put(byId.get(i), i + 1);
        }
        List<Winner> winners = new ArrayList<>();
        for (Chosen c : chosen(report)) {
            winners.add(winner(c.point(), c.role(), lineNo, selections, report.goals()));
        }
        Map<String, MetricRange> ranges = new LinkedHashMap<>();
        for (AnalysisReport.MetricSummary m : report.metrics()) {
            ranges.put(m.key(), new MetricRange(m.key(), m.n(), m.min(), m.max(), m.mean(), m.stdev()));
        }
        List<String> declared = report.goals().stream().map(Goal::key).filter(k -> !report.discovered().contains(k)).toList();
        return new BundleSeed(epochMs, runId, report.samples(), declared, ranges, winners);
    }

    /** The candidate ids of the points a seed of this analysis names as winners, in winner order. */
    public static java.util.Set<String> winnerIds(AnalysisReport report) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        chosen(report).forEach(c -> ids.add(c.point().id()));
        return ids;
    }

    private record Chosen(AnalysisReport.Point point, String role) {
    }

    /** Pareto winners, then the champions of each goal metric, then the balanced picks (legacy BundleSeed roles). */
    private static List<Chosen> chosen(AnalysisReport report) {
        List<Chosen> out = new ArrayList<>();
        for (AnalysisReport.Point p : report.front()) {
            out.add(new Chosen(p, "pareto"));
        }
        for (Goal g : report.goals()) {
            List<AnalysisReport.Point> having = report.all().stream().filter(p -> p.values().containsKey(g.key())).toList();
            if (having.isEmpty()) {
                continue;
            }
            AnalysisReport.Point min = having.stream().min(Comparator.comparingDouble(p -> p.values().get(g.key()))).orElseThrow();
            AnalysisReport.Point max = having.stream().max(Comparator.comparingDouble((AnalysisReport.Point p) -> p.values().get(g.key()))
                    .thenComparing(AnalysisReport.Point::id, Comparator.reverseOrder())).orElseThrow();
            out.add(new Chosen(min, "champion-min:" + g.key()));
            out.add(new Chosen(max, "champion-max:" + g.key()));
        }
        for (AnalysisReport.Pick pick : report.balanced()) {
            if (pick.chosen() != null) {
                out.add(new Chosen(pick.chosen(), "balanced:" + pick.strategy()));
            }
        }
        return out;
    }

    private static Winner winner(AnalysisReport.Point p, String role, Map<AnalysisReport.Point, Integer> lineNo,
                                 Map<String, Map<String, List<String>>> selections, List<Goal> goals) {
        Map<String, String> kv = new LinkedHashMap<>();
        kv.put("candidate_id", p.display());
        if (!p.environment().isEmpty()) {
            kv.put("env_id", p.environment());
        }
        for (Goal g : goals) {
            Double v = p.values().get(g.key());
            if (v != null) {
                kv.put(g.key(), Double.toString(v));
            }
        }
        double crowding = p.diagnostics().getOrDefault("crowding", Double.NaN);
        return new Winner(lineNo.getOrDefault(p, -1), p.score(), role, kv, crowding, null, null, selections.getOrDefault(p.id(), Map.of()));
    }

    /** Winners whose role starts with the prefix. */
    public List<Winner> withRolePrefix(String prefix) {
        return winners.stream().filter(w -> w.role().startsWith(prefix)).toList();
    }

    /** The seed as JSON: the legacy fields in the legacy order, then this build's selections per winner. */
    public String toJson() {
        return Json.write(tree(true));
    }

    /** SHA-256 of the seed's content without its generation time. */
    public String contentDigest() {
        return Canonical.sha256Hex(Json.write(tree(false)));
    }

    private Map<String, Object> tree(boolean withTime) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1L);
        if (withTime) {
            root.put("generatedAtEpochMs", generatedAtEpochMs);
        }
        if (!sourceRunId.isEmpty()) {
            root.put("sourceRunId", sourceRunId);
        }
        root.put("totalCandidatesObserved", totalCandidatesObserved);
        root.put("declaredMetrics", declaredMetrics);
        Map<String, Object> ranges = new LinkedHashMap<>();
        observedRanges.forEach((k, r) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", r.key());
            m.put("n", r.n());
            m.put("min", finiteOrNull(r.min()));
            m.put("max", finiteOrNull(r.max()));
            m.put("mean", finiteOrNull(r.mean()));
            m.put("stdev", finiteOrNull(r.stdev()));
            ranges.put(k, m);
        });
        root.put("observedRanges", ranges);
        List<Object> list = new ArrayList<>();
        for (Winner w : winners) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", (long) w.lineNo());
            m.put("score", finiteOrNull(w.score()));
            m.put("role", w.role());
            m.put("kvPairs", w.kvPairs());
            m.put("crowdingDistance", Double.isInfinite(w.crowdingDistance()) ? "Infinity" : finiteOrNull(w.crowdingDistance()));
            if (w.originalLine() != null) {
                m.put("originalLine", w.originalLine());
            }
            if (w.lineType() != null) {
                m.put("lineType", w.lineType());
            }
            if (!w.selections().isEmpty()) {
                m.put("selections", w.selections());
            }
            list.add(m);
        }
        root.put("winners", list);
        return root;
    }

    private static Object finiteOrNull(double v) {
        return Double.isFinite(v) ? (Object) v : null;
    }

    /**
     * Reads a seed, failing closed as the legacy load_seed did: schemaVersion must be 1 and winners a list.
     *
     * @throws FrameworkException INPUT_MALFORMED, INPUT_VERSION_UNSUPPORTED or INPUT_SCHEMA
     */
    public static BundleSeed read(String text, String origin) {
        if (!(Json.parse(text, origin) instanceof Map<?, ?> root)) {
            throw schema(origin, "a seed is a JSON object");
        }
        Object version = root.get("schemaVersion");
        if (!(version instanceof Long v) || v != 1L) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_VERSION_UNSUPPORTED, "seed " + origin + ": schemaVersion "
                    + version + " is not 1; only BundleSeed schema 1 is read", SourceLocation.of(origin)));
        }
        if (!(root.get("winners") instanceof List<?> rawWinners)) {
            throw schema(origin, "winners must be a list");
        }
        List<Winner> winners = new ArrayList<>();
        for (Object o : rawWinners) {
            if (!(o instanceof Map<?, ?> w)) {
                throw schema(origin, "a winner is an object");
            }
            Map<String, String> kv = new LinkedHashMap<>();
            if (w.get("kvPairs") instanceof Map<?, ?> m) {
                m.forEach((k, v2) -> kv.put(String.valueOf(k), v2 == null ? "" : text(v2)));
            }
            Map<String, List<String>> selections = new LinkedHashMap<>();
            if (w.get("selections") instanceof Map<?, ?> s) {
                s.forEach((sheet, values) -> {
                    List<String> vs = new ArrayList<>();
                    if (values instanceof List<?> l) {
                        l.forEach(x -> vs.add(String.valueOf(x)));
                    }
                    selections.put(String.valueOf(sheet), vs);
                });
            }
            Object crowd = w.get("crowdingDistance");
            double crowding = crowd instanceof String c && c.equalsIgnoreCase("Infinity") ? Double.POSITIVE_INFINITY : number(crowd);
            winners.add(new Winner(w.get("lineNo") instanceof Long l ? (int) (long) l : -1, number(w.get("score")),
                    w.get("role") instanceof String r ? r : "", kv, crowding, w.get("originalLine") instanceof String ol ? ol : null,
                    w.get("lineType") instanceof String lt ? lt : null, selections));
        }
        List<String> declared = new ArrayList<>();
        if (root.get("declaredMetrics") instanceof List<?> d) {
            d.forEach(x -> declared.add(String.valueOf(x)));
        }
        Map<String, MetricRange> ranges = new LinkedHashMap<>();
        if (root.get("observedRanges") instanceof Map<?, ?> r) {
            r.forEach((k, entry) -> {
                if (entry instanceof Map<?, ?> m) {
                    ranges.put(String.valueOf(k), new MetricRange(String.valueOf(k), m.get("n") instanceof Long n ? n : 0,
                            number(m.get("min")), number(m.get("max")), number(m.get("mean")), number(m.get("stdev"))));
                }
            });
        }
        return new BundleSeed(root.get("generatedAtEpochMs") instanceof Long t ? t : 0L, root.get("sourceRunId") instanceof String s ? s : "",
                root.get("totalCandidatesObserved") instanceof Long n ? n : 0L, declared, ranges, winners);
    }

    private static String text(Object v) {
        return v instanceof BigDecimal d ? d.toPlainString() : String.valueOf(v);
    }

    private static double number(Object v) {
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof BigDecimal d) {
            return d.doubleValue();
        }
        return Double.NaN;
    }

    private static FrameworkException schema(String origin, String why) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_SCHEMA, "seed " + origin + ": " + why, SourceLocation.of(origin)));
    }
}
