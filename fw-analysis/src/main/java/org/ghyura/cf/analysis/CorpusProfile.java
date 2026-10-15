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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The corpus profile of the textual scorer (legacy AnalyzerCore.CorpusProfile, built by ProfileBuilder and finalize):
 * line counts, Welford mean and population deviation with interpolated percentiles of the line lengths and entropies
 * (over the first 100000 non-empty lines, as the legacy reservoir), document frequencies of tokens (lower-cased, longer
 * than two characters, no stop word), keys, canonical numbers, signatures and line types sorted by count, the most common
 * of them (at least minSupport lines, at most topK), and per goal metric its count, range and 5th-95th percentile band.
 * Ties keep the legacy order, that of a HashMap filled in line order. A profile is frozen before any line is scored.
 */
public record CorpusProfile(int totalLines, int nonemptyLines, int emptyLines, double lengthMean, double lengthMedian,
                            double lengthP10, double lengthP90, double lengthStdev, double entropyMean, double entropyMedian,
                            double entropyP10, double entropyP90, double entropyStdev, Map<String, Integer> tokenFrequency,
                            Map<String, Integer> keyFrequency, Map<String, Integer> numberFrequency,
                            Map<String, Integer> signatureFrequency, Map<String, Integer> lineTypeFrequency,
                            List<String> commonTokens, List<String> commonKeys, List<String> commonNumbers,
                            String dominantLineType, String dominantSignature, Map<String, MetricBand> metrics) {

    /** Stop words the token frequencies leave out (English and Russian, as the legacy list). */
    static final Set<String> STOPWORDS = Set.of("and", "or", "the", "a", "an", "to", "of", "in", "on", "for", "with", "by", "is", "are",
            "was", "were", "be", "this", "that", "it", "as", "at", "from", "not", "no", "if", "then", "else", "when", "while", "into",
            "over", "under", "и", "в", "на", "для", "по", "с", "к", "что", "это", "как", "или", "не", "да", "нет", "то", "же", "ли",
            "бы", "а", "но");

    /** One goal metric over the non-empty lines that carry it. */
    public record MetricBand(int count, double min, double max, double low, double high) {
    }

    /**
     * Rules synthesized from a frozen profile (legacy SynthesizedRules): its dominant line type and signature, common
     * tokens, keys and numbers with their supports, length and entropy bands, the support cutoffs and the confidence
     * 0.5 + 0.5 (1 - e^(-n/100)) of n non-empty lines.
     */
    public record Synthesized(String dominantLineType, String dominantSignature, List<String> topTokens, List<String> topKeys,
                              List<String> topNumbers, Map<String, Integer> lineTypeSupport, Map<String, Integer> signatureSupport,
                              Map<String, Integer> tokenSupport, Map<String, Integer> keySupport, Map<String, Integer> numberSupport,
                              double lengthMedian, double lengthP10, double lengthP90, double entropyMedian, double entropyP10,
                              double entropyP90, int tokenDfCutoff, int numberDfCutoff, double confidence) {
    }

    public CorpusProfile {
        tokenFrequency = Collections.unmodifiableMap(tokenFrequency);
        keyFrequency = Collections.unmodifiableMap(keyFrequency);
        numberFrequency = Collections.unmodifiableMap(numberFrequency);
        signatureFrequency = Collections.unmodifiableMap(signatureFrequency);
        lineTypeFrequency = Collections.unmodifiableMap(lineTypeFrequency);
        commonTokens = List.copyOf(commonTokens);
        commonKeys = List.copyOf(commonKeys);
        commonNumbers = List.copyOf(commonNumbers);
        metrics = Collections.unmodifiableMap(metrics);
    }

    /** The rules of this profile for the given minimum support. */
    public Synthesized synthesize(int minSupport) {
        if (nonemptyLines == 0) {
            return new Synthesized("", "", List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0, 0, 0, 0, 0,
                    0, 1, 1, 0.0);
        }
        double conf = 0.5 + 0.5 * (1.0 - Math.exp(-nonemptyLines / 100.0));
        int cut = Math.max(minSupport, (int) Math.ceil(conf * minSupport));
        return new Synthesized(dominantLineType, dominantSignature, commonTokens, commonKeys, commonNumbers, lineTypeFrequency,
                signatureFrequency, tokenFrequency, keyFrequency, numberFrequency, lengthMedian, lengthP10, lengthP90, entropyMedian,
                entropyP10, entropyP90, cut, cut, conf);
    }

    /** Linear interpolation between the closest ranks of a sorted copy (legacy percentile); 0 for no value. */
    static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double[] a = new double[values.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = values.get(i);
        }
        Arrays.sort(a);
        if (a.length == 1) {
            return a[0];
        }
        double q = Math.max(0.0, Math.min(100.0, p));
        double k = (a.length - 1) * (q / 100.0);
        int f = (int) Math.floor(k);
        int c = (int) Math.ceil(k);
        if (f == c) {
            return a[(int) k];
        }
        return a[f] * (c - k) + a[c] * (k - f);
    }

    /** Accumulates lines in input order; {@link #freeze} ends it. */
    static final class Builder {
        private static final int SAMPLE_CAP = 100_000;
        private final Welford lengths = new Welford();
        private final Welford entropies = new Welford();
        private final List<Double> lengthSamples = new ArrayList<>();
        private final List<Double> entropySamples = new ArrayList<>();
        private final Map<String, List<Double>> metricSamples = new HashMap<>();
        private int totalLines;
        private int nonemptyLines;
        private int emptyLines;
        private final Map<String, Integer> tokenDf = new HashMap<>();
        private final Map<String, Integer> keyDf = new HashMap<>();
        private final Map<String, Integer> numberDf = new HashMap<>();
        private final Map<String, Integer> lineTypeDf = new HashMap<>();
        private final Map<String, Integer> signatureDf = new HashMap<>();

        void accumulate(TextFeatures f, List<RuleSet.Goal> goals) {
            totalLines++;
            if ("empty".equals(f.lineType())) {
                emptyLines++;
                return;
            }
            nonemptyLines++;
            lengths.add(f.length());
            if (lengthSamples.size() < SAMPLE_CAP) lengthSamples.add((double) f.length());
            entropies.add(f.entropy());
            if (entropySamples.size() < SAMPLE_CAP) entropySamples.add(f.entropy());
            for (RuleSet.Goal goal : goals) {
                Double m = RuleScorer.metric(f, goal.key());
                if (m != null) metricSamples.computeIfAbsent(goal.key(), k -> new ArrayList<>()).add(m);
            }
            for (String t : new HashSet<>(f.tokenLowers())) {
                if (!STOPWORDS.contains(t) && t.length() > 2) tokenDf.merge(t, 1, Integer::sum);
            }
            for (String k : f.keyValues().keySet()) {
                keyDf.merge(k, 1, Integer::sum);
            }
            for (Double n : new HashSet<>(f.numbers())) {
                numberDf.merge(TextFeatures.canonicalNumber(n), 1, Integer::sum);
            }
            lineTypeDf.merge(f.lineType(), 1, Integer::sum);
            signatureDf.merge(f.signature(), 1, Integer::sum);
        }

        CorpusProfile freeze(int minSupport, int topK) {
            Map<String, Integer> lineTypes = sortDescByCount(lineTypeDf);
            Map<String, Integer> signatures = sortDescByCount(signatureDf);
            Map<String, MetricBand> metrics = new LinkedHashMap<>();
            for (Map.Entry<String, List<Double>> e : metricSamples.entrySet()) {
                List<Double> values = e.getValue();
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (double v : values) {
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
                metrics.put(e.getKey(), new MetricBand(values.size(), min, max, percentile(values, 5.0), percentile(values, 95.0)));
            }
            return new CorpusProfile(totalLines, nonemptyLines, emptyLines, lengths.mean, percentile(lengthSamples, 50.0),
                    percentile(lengthSamples, 10.0), percentile(lengthSamples, 90.0), lengths.stdev(), entropies.mean,
                    percentile(entropySamples, 50.0), percentile(entropySamples, 10.0), percentile(entropySamples, 90.0), entropies.stdev(),
                    sortDescByCount(tokenDf), sortDescByCount(keyDf), sortDescByCount(numberDf), signatures, lineTypes,
                    top(tokenDf, topK, minSupport), top(keyDf, topK, minSupport), top(numberDf, topK, minSupport),
                    lineTypes.keySet().stream().findFirst().orElse("unknown"), signatures.keySet().stream().findFirst().orElse(""), metrics);
        }

        /** The legacy bounded min-heap top-K: entries below minSupport skipped, ties in heap order, then by count descending. */
        private static List<String> top(Map<String, Integer> source, int k, int minSupport) {
            if (source.isEmpty() || k <= 0) {
                return List.of();
            }
            PriorityQueue<Map.Entry<String, Integer>> heap = new PriorityQueue<>(k + 1, Comparator.comparingInt(Map.Entry::getValue));
            for (Map.Entry<String, Integer> e : source.entrySet()) {
                if (e.getValue() < minSupport) {
                    continue;
                }
                if (heap.size() < k) {
                    heap.offer(e);
                } else if (heap.peek().getValue() < e.getValue()) {
                    heap.poll();
                    heap.offer(e);
                }
            }
            List<Map.Entry<String, Integer>> out = new ArrayList<>(heap);
            out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            return out.stream().map(Map.Entry::getKey).toList();
        }

        /** A stable sort by descending count of the map's own order. */
        private static Map<String, Integer> sortDescByCount(Map<String, Integer> map) {
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
            entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            Map<String, Integer> out = new LinkedHashMap<>(entries.size() * 4 / 3 + 1);
            for (Map.Entry<String, Integer> e : entries) {
                out.put(e.getKey(), e.getValue());
            }
            return out;
        }
    }

    /** Welford's online mean and population variance, in the legacy order of operations. */
    private static final class Welford {
        long n;
        double mean;
        double m2;

        void add(double v) {
            n++;
            double d = v - mean;
            mean += d / n;
            m2 += d * (v - mean);
        }

        double stdev() {
            return Math.sqrt(n < 2 ? 0.0 : m2 / n);
        }
    }
}
