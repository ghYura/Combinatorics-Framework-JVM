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
import org.ghyura.cf.model.Xml;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What an analysis found, with its exactness stated.
 *
 * @param samples       samples offered
 * @param eligible      samples that could win: PASS with at least one metric
 * @param excluded      samples that could not, by reason (domain failure, no verdict, no metrics)
 * @param goals         the goals used, declared first, then discovered ones
 * @param discovered    the keys of the discovered goals
 * @param formal        whether the mode was formal (declared goals only)
 * @param algorithm     the ranker whose diagnostics the front carries
 * @param metrics       statistics of every metric key over the eligible samples
 * @param points        analysis points: an eligible candidate in one environment, its repeats aggregated by their median
 * @param front         the exact Pareto front of the points over the goals, ordered by id
 * @param frontMethod   how the front was computed: archive (no missing value, dominance transitive) or pairwise
 * @param ranksComplete whether every point was ranked (the diagnostics then cover the whole population); otherwise the
 *                      diagnostics are computed among the front only
 * @param top           the best points by score, at most top-K, best first
 * @param balanced      the weighted-sum, Tchebycheff and distance-to-ideal picks of the front
 * @param repeated      whether some point aggregates more than one sample (its intervals are then reported)
 * @param all           every point, ordered by id and environment (kept in memory for seeds; not rendered)
 */
public record AnalysisReport(long samples, long eligible, Map<String, Long> excluded, List<Goal> goals, List<String> discovered,
                             boolean formal, RunConfig.FrontAlgorithm algorithm, List<MetricSummary> metrics, int points, List<Point> front,
                             String frontMethod, boolean ranksComplete, List<Point> top, List<Pick> balanced, boolean repeated,
                             List<Point> all) {

    public AnalysisReport {
        excluded = Map.copyOf(excluded);
        goals = List.copyOf(goals);
        discovered = List.copyOf(discovered);
        metrics = List.copyOf(metrics);
        front = List.copyOf(front);
        top = List.copyOf(top);
        balanced = List.copyOf(balanced);
        all = List.copyOf(all);
    }

    /** Statistics of one metric key; percentiles by nearest rank over every eligible value. */
    public record MetricSummary(String key, long n, double mean, double stdev, double min, double max, double p10, double p50,
                                double p90, String argmin, String argmax) {
    }

    /**
     * One analysis point.
     *
     * @param id          the candidate id
     * @param display     its display id, with '@environment' when it has one
     * @param environment its environment
     * @param samples     the samples aggregated (repeats)
     * @param values      the median value of each goal metric it has
     * @param score       the weighted normalised deviation over the goals, 0 best (a missing goal metric counts as worst)
     * @param rank        its non-dominated rank, 1 on the front (0 when ranks were computed among the front only and it is
     *                    not on it)
     * @param diagnostics the ranker's diagnostics
     * @param intervals   per goal key, the interval of its deviation over the repeats (when it has more than one sample)
     */
    public record Point(String id, String display, String environment, int samples, Map<String, Double> values, double score, int rank,
                        Map<String, Double> diagnostics, Map<String, RepeatStatistics.Interval> intervals) {
        public Point {
            values = Map.copyOf(values);
            diagnostics = Map.copyOf(diagnostics);
            intervals = Map.copyOf(intervals);
        }
    }

    /** A balanced pick of the front (legacy BalancedOptimumSelector). */
    public record Pick(String strategy, Point chosen, double score, Map<String, Double> contributions, String reason) {
        public Pick {
            contributions = Map.copyOf(contributions);
        }
    }

    public String toText() {
        StringBuilder t = new StringBuilder();
        t.append("analysis: ").append(eligible).append(" of ").append(samples).append(" samples eligible");
        if (!excluded.isEmpty()) {
            t.append(" (excluded ").append(new java.util.TreeMap<>(excluded)).append(')');
        }
        t.append("; ").append(points).append(" points; mode ").append(formal ? "formal" : "exploratory").append('\n');
        t.append("  goals      ").append(goals.isEmpty() ? "none" : String.join(", ", goals.stream().map(Goal::token).toList()));
        if (!discovered.isEmpty()) {
            t.append(" (discovered: ").append(String.join(", ", discovered)).append(')');
        }
        t.append('\n');
        for (MetricSummary m : metrics) {
            t.append(String.format(Locale.ROOT, "  metric     %s: n=%d mean=%.6g sd=%.6g min=%.6g (%s) p50=%.6g max=%.6g (%s)%n", m.key(), m.n(),
                    m.mean(), m.stdev(), m.min(), m.argmin(), m.p50(), m.max(), m.argmax()));
        }
        t.append("  front      ").append(front.size()).append(" points, exact (").append(frontMethod).append("; ")
                .append(algorithm.token()).append(ranksComplete ? " over every point" : " diagnostics among the front").append(")\n");
        for (Point p : front) {
            t.append("    ").append(p.display()).append(' ').append(format(p.values())).append('\n');
        }
        t.append("  top        ");
        for (int i = 0; i < top.size(); i++) {
            t.append(i == 0 ? "" : ", ").append(top.get(i).display()).append(String.format(Locale.ROOT, " (%.4f)", top.get(i).score()));
        }
        t.append('\n');
        for (Pick p : balanced) {
            t.append("  balanced   ").append(p.strategy()).append(": ").append(p.chosen() == null ? "none (" + p.reason() + ")"
                    : p.chosen().display() + String.format(Locale.ROOT, " (%.4f)", p.score())).append('\n');
        }
        return t.toString();
    }

    public void appendXml(StringBuilder xml, String indent) {
        xml.append(indent).append("<analysis samples=\"").append(samples).append("\" eligible=\"").append(eligible).append("\" points=\"")
                .append(points).append("\" mode=\"").append(formal ? "formal" : "exploratory").append("\" algorithm=\"").append(algorithm.token())
                .append("\" frontMethod=\"").append(frontMethod).append("\" ranksComplete=\"").append(ranksComplete).append("\" repeated=\"")
                .append(repeated).append("\">\n");
        for (Map.Entry<String, Long> e : new java.util.TreeMap<>(excluded).entrySet()) {
            xml.append(indent).append("  <excluded reason=\"").append(e.getKey()).append("\" count=\"").append(e.getValue()).append("\"/>\n");
        }
        for (Goal g : goals) {
            xml.append(indent).append("  <goal key=\"").append(Xml.escape(g.key())).append("\" mode=\"").append(g.mode().token())
                    .append("\" target=\"").append(g.target()).append("\" weight=\"").append(g.weight()).append("\" discovered=\"")
                    .append(discovered.contains(g.key())).append("\"/>\n");
        }
        for (MetricSummary m : metrics) {
            xml.append(indent).append("  <metric key=\"").append(Xml.escape(m.key())).append("\" n=\"").append(m.n()).append("\" mean=\"")
                    .append(m.mean()).append("\" stdev=\"").append(m.stdev()).append("\" min=\"").append(m.min()).append("\" p10=\"")
                    .append(m.p10()).append("\" p50=\"").append(m.p50()).append("\" p90=\"").append(m.p90()).append("\" max=\"")
                    .append(m.max()).append("\" argmin=\"").append(Xml.escape(m.argmin())).append("\" argmax=\"").append(Xml.escape(m.argmax()))
                    .append("\"/>\n");
        }
        appendPoints(xml, indent + "  ", "front", front);
        appendPoints(xml, indent + "  ", "top", top);
        for (Pick p : balanced) {
            xml.append(indent).append("  <balanced strategy=\"").append(p.strategy()).append('"');
            if (p.chosen() != null) {
                xml.append(" candidate=\"").append(Xml.escape(p.chosen().display())).append("\" score=\"").append(p.score()).append('"');
            } else {
                xml.append(" reason=\"").append(Xml.escape(p.reason())).append('"');
            }
            xml.append("/>\n");
        }
        xml.append(indent).append("</analysis>\n");
    }

    private static void appendPoints(StringBuilder xml, String indent, String name, List<Point> points) {
        xml.append(indent).append('<').append(name).append(" size=\"").append(points.size()).append("\">\n");
        for (Point p : points) {
            xml.append(indent).append("  <point id=\"").append(p.id()).append("\" display=\"").append(Xml.escape(p.display()))
                    .append("\" samples=\"").append(p.samples()).append("\" rank=\"").append(p.rank()).append("\" score=\"").append(p.score())
                    .append("\">\n");
            for (Map.Entry<String, Double> e : new java.util.TreeMap<>(p.values()).entrySet()) {
                xml.append(indent).append("    <value key=\"").append(Xml.escape(e.getKey())).append("\" median=\"").append(e.getValue())
                        .append('"');
                RepeatStatistics.Interval iv = p.intervals().get(e.getKey());
                if (iv != null) {
                    xml.append(" low=\"").append(iv.low()).append("\" high=\"").append(iv.high()).append("\" valid=\"").append(iv.valid())
                            .append("\" method=\"").append(iv.method()).append('"');
                }
                xml.append("/>\n");
            }
            for (Map.Entry<String, Double> e : new java.util.TreeMap<>(p.diagnostics()).entrySet()) {
                xml.append(indent).append("    <diagnostic name=\"").append(e.getKey()).append("\" value=\"").append(e.getValue()).append("\"/>\n");
            }
            xml.append(indent).append("  </point>\n");
        }
        xml.append(indent).append("</").append(name).append(">\n");
    }

    private static String format(Map<String, Double> values) {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, Double> e : new java.util.TreeMap<>(values).entrySet()) {
            s.append(s.isEmpty() ? "" : " ").append(e.getKey()).append('=').append(String.format(Locale.ROOT, "%.6g", e.getValue()));
        }
        return s.toString();
    }
}
