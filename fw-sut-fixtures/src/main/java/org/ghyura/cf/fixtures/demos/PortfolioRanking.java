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

package org.ghyura.cf.fixtures.demos;

import org.ghyura.cf.fixtures.demos.CoalitionAllocation.Fraction;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** D9 whole-portfolio rankings from complete observed costs, using the existing exact fraction type. */
public final class PortfolioRanking {
    public record Observed(String caseId, String design, int demand, int disruption, String verdict, BigInteger totalCost) { }
    public record World(int demand, int disruption) { @Override public String toString() { return demand + "," + disruption; } }
    public record Profile(List<Integer> demandWeights, List<Integer> disruptionWeights, List<Fraction> worldWeights) {
        public Profile { demandWeights = List.copyOf(demandWeights); disruptionWeights = List.copyOf(disruptionWeights); worldWeights = List.copyOf(worldWeights); }
    }
    public record Summary(String design, BigInteger worstCost, List<World> worstWorlds, Map<String, Fraction> expected) {
        public Summary { worstWorlds = List.copyOf(worstWorlds); expected = frozen(expected); }
    }
    public record Sensitivity(Fraction alpha, List<String> ranking, String winner, Fraction winnerExpected) {
        public Sensitivity { ranking = List.copyOf(ranking); }
    }
    public record Ranking(Map<String, Profile> profiles, List<String> minimaxRanking, List<String> primaryTies,
                          Map<String, List<String>> expectedRankings, List<Sensitivity> sensitivity, List<Summary> summaries) {
        public Ranking {
            profiles = frozen(profiles); minimaxRanking = List.copyOf(minimaxRanking); primaryTies = List.copyOf(primaryTies);
            var copy = new LinkedHashMap<String, List<String>>(); expectedRankings.forEach((k,v) -> copy.put(k, List.copyOf(v)));
            expectedRankings = frozen(copy); sensitivity = List.copyOf(sensitivity); summaries = List.copyOf(summaries);
        }
    }
    private PortfolioRanking() { }
    private static <V> Map<String, V> frozen(Map<String, V> values) { return Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    private static int compare(Fraction a, Fraction b) { return a.numerator().multiply(b.denominator()).compareTo(b.numerator().multiply(a.denominator())); }
    private static Fraction mean(BigInteger[] costs, List<Fraction> weights) {
        Fraction sum = Fraction.of(0, 1);
        for (int w = 0; w < 16; w++) sum = sum.add(weights.get(w).multiply(costs[w]));
        return sum;
    }
    private static Profile profile(List<Integer> d, List<Integer> x) {
        int denominator = d.stream().mapToInt(Integer::intValue).sum() * x.stream().mapToInt(Integer::intValue).sum();
        List<Fraction> weights = new ArrayList<>(); for (int a : d) for (int b : x) weights.add(Fraction.of((long)a*b, denominator));
        return new Profile(d, x, weights);
    }
    private static List<String> expectedOrder(List<String> designs, Map<String, Fraction> means, Map<String, BigInteger> worst) {
        return designs.stream().sorted(Comparator.<String, Fraction>comparing(means::get, PortfolioRanking::compare)
                .thenComparing(worst::get).thenComparing(Comparator.naturalOrder())).toList();
    }
    /** Exactly one PASS per original design/world. No expected table, oracle or support renormalization. */
    public static Ranking rank(List<Observed> observations) {
        if (observations.size() > 1024) throw new IllegalArgumentException("extra or duplicate portfolio observations");
        BigInteger[][] costs = new BigInteger[64][16];
        for (Observed o : observations) {
            if (o.design() == null || !o.design().matches("[01]{6}") || o.demand() < 0 || o.demand() > 3 || o.disruption() < 0 || o.disruption() > 3
                    || !("P=" + o.design() + "|D=" + o.demand() + "|X=" + o.disruption()).equals(o.caseId()))
                throw new IllegalArgumentException("unknown or mismatched portfolio identity");
            if (!"PASS".equals(o.verdict())) throw new IllegalArgumentException("non-PASS portfolio observation");
            int p = Integer.parseInt(o.design(), 2), w = 4*o.demand() + o.disruption();
            if (costs[p][w] != null) throw new IllegalArgumentException("duplicate portfolio observation");
            if (o.totalCost() == null) throw new IllegalArgumentException("observed total cost required");
            costs[p][w] = o.totalCost();
        }
        for (int p = 0; p < 64; p++) for (int w = 0; w < 16; w++) if (costs[p][w] == null)
            throw new IllegalArgumentException("missing P=" + RobustPortfolios.design(p) + "|D=" + w/4 + "|X=" + w%4);
        var profiles = new LinkedHashMap<String, Profile>();
        profiles.put("uniform", profile(List.of(1,1,1,1), List.of(1,1,1,1)));
        profiles.put("calm", profile(List.of(6,2,1,1), List.of(7,1,1,1)));
        profiles.put("stress", profile(List.of(1,1,6,2), List.of(1,2,6,1)));
        var designs = new ArrayList<String>(); var worst = new LinkedHashMap<String, BigInteger>();
        var means = new LinkedHashMap<String, Map<String, Fraction>>(); profiles.keySet().forEach(p -> means.put(p, new LinkedHashMap<>()));
        var summaries = new ArrayList<Summary>();
        for (int p = 0; p < 64; p++) {
            String design = RobustPortfolios.design(p); designs.add(design);
            BigInteger max = java.util.Arrays.stream(costs[p]).max(Comparator.naturalOrder()).orElseThrow(); worst.put(design, max);
            var worlds = new ArrayList<World>(); for (int w = 0; w < 16; w++) if (costs[p][w].equals(max)) worlds.add(new World(w/4, w%4));
            var expected = new LinkedHashMap<String, Fraction>();
            for (var entry : profiles.entrySet()) { Fraction value = mean(costs[p], entry.getValue().worldWeights()); means.get(entry.getKey()).put(design, value); expected.put(entry.getKey(), value); }
            summaries.add(new Summary(design, max, worlds, expected));
        }
        var minimax = designs.stream().sorted(Comparator.<String, BigInteger>comparing(worst::get)
                .thenComparing(means.get("uniform")::get, PortfolioRanking::compare).thenComparing(Comparator.naturalOrder())).toList();
        var ties = minimax.stream().filter(p -> Objects.equals(worst.get(p), worst.get(minimax.getFirst()))).toList();
        var expectedOrders = new LinkedHashMap<String, List<String>>(); profiles.keySet().forEach(p -> expectedOrders.put(p, expectedOrder(designs, means.get(p), worst)));
        var sensitivity = new ArrayList<Sensitivity>();
        for (int k = 0; k <= 10; k++) {
            var weights = new ArrayList<Fraction>();
            for (int w = 0; w < 16; w++) {
                var c = profiles.get("calm").worldWeights().get(w); var s = profiles.get("stress").worldWeights().get(w);
                weights.add(new Fraction(c.numerator().multiply(BigInteger.valueOf(10-k)), c.denominator().multiply(BigInteger.TEN))
                        .add(new Fraction(s.numerator().multiply(BigInteger.valueOf(k)), s.denominator().multiply(BigInteger.TEN))));
            }
            var mixedMeans = new LinkedHashMap<String, Fraction>(); for (int p = 0; p < 64; p++) mixedMeans.put(designs.get(p), mean(costs[p], weights));
            var order = expectedOrder(designs, mixedMeans, worst);
            sensitivity.add(new Sensitivity(Fraction.of(k,10), order, order.getFirst(), mixedMeans.get(order.getFirst())));
        }
        return new Ranking(profiles, minimax, ties, expectedOrders, sensitivity, summaries);
    }
}
