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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A12: every front against an exhaustive dominance oracle written here from the definition (mixed min, max and target
 * goals, missing values, duplicate points, zero weights), for every ranker, with deterministic diagnostics.
 */
class MultiObjectiveTest {

    private static final Goal[] GOALS = {Goal.min("latency"), Goal.max("throughput"), new Goal("size", Goal.Mode.TARGET, 50, 0)};

    /** The oracle: i is on the front when no j is at least as good on every comparable axis and better on one. */
    private static List<Integer> oracle(double[][] v) {
        List<Integer> front = new ArrayList<>();
        for (int i = 0; i < v.length; i++) {
            boolean beaten = false;
            for (int j = 0; j < v.length && !beaten; j++) {
                if (i == j) {
                    continue;
                }
                boolean noWorse = true;
                boolean better = false;
                for (int a = 0; a < GOALS.length; a++) {
                    if (Double.isNaN(v[i][a]) || Double.isNaN(v[j][a])) {
                        continue;
                    }
                    double cj = cost(a, v[j][a]);
                    double ci = cost(a, v[i][a]);
                    noWorse &= cj <= ci;
                    better |= cj < ci;
                }
                beaten = noWorse && better;
            }
            if (!beaten) {
                front.add(i);
            }
        }
        return front;
    }

    private static double cost(int axis, double value) {
        return switch (axis) {
            case 0 -> value;
            case 1 -> -value;
            default -> Math.abs(value - 50);
        };
    }

    private static double[][] population(Random rng, int n, boolean withMissing) {
        double[][] v = new double[n][3];
        for (int i = 0; i < n; i++) {
            if (i > 0 && rng.nextInt(6) == 0) {
                v[i] = v[rng.nextInt(i)].clone();
                continue;
            }
            for (int a = 0; a < 3; a++) {
                v[i][a] = withMissing && rng.nextInt(9) == 0 ? Double.NaN : rng.nextInt(7) * 10;
            }
        }
        return v;
    }

    @Test
    void everyFrontAndRankerAgreesWithTheExhaustiveOracle() {
        Random rng = new Random(20261002L);
        for (int round = 0; round < 200; round++) {
            boolean missing = round % 2 == 1;
            double[][] v = population(rng, 1 + rng.nextInt(24), missing);
            List<Integer> expected = oracle(v);
            assertEquals(expected, Dominance.front(v, GOALS), "round " + round);
            if (!missing) {
                assertEquals(expected, Analysis.archive(v, GOALS), "the archive is exact when nothing is missing, round " + round);
            }
            for (RunConfig.FrontAlgorithm algorithm : RunConfig.FrontAlgorithm.values()) {
                List<FrontRanking.Rank> ranks = FrontRanking.rank(algorithm, v, GOALS);
                List<Integer> rankOne = ranks.stream().filter(r -> r.rank() == 1).map(FrontRanking.Rank::index).toList();
                assertEquals(expected, rankOne, algorithm + " round " + round);
                assertEquals(ranks, FrontRanking.rank(algorithm, v, GOALS), "deterministic diagnostics, " + algorithm);
            }
        }
    }

    @Test
    void tiesZeroWeightsAndTheRankersDiagnosticsBehaveAsTheLegacyOnes() {
        double[][] v = {{10, 90, 50}, {10, 90, 50}, {20, 100, 40}, {30, 50, 70}};
        assertEquals(List.of(0, 1, 2), Dominance.front(v, GOALS), "identical points never dominate each other; zero weight still counts as an axis");
        assertFalse(Dominance.dominates(v[0], v[1], GOALS));
        assertTrue(Dominance.dominates(v[0], v[3], GOALS));
        List<FrontRanking.Rank> nsga = FrontRanking.rank(RunConfig.FrontAlgorithm.NSGA_II, v, GOALS);
        assertEquals(List.of(1, 1, 1, 2), nsga.stream().map(FrontRanking.Rank::rank).toList());
        assertEquals(Double.POSITIVE_INFINITY, nsga.get(3).diagnostics().get("crowding"), "a front of one point has an infinite crowding distance");
        List<FrontRanking.Rank> spea = FrontRanking.rank(RunConfig.FrontAlgorithm.SPEA2, v, GOALS);
        assertEquals(0.0, spea.get(0).diagnostics().get("rawFitness"), "nobody dominates a front member");
        assertEquals(3.0, spea.get(3).diagnostics().get("rawFitness"), "point 3 is dominated by the three others, each of strength 1");
        assertEquals(13, FrontRanking.referencePoints(2, 12).length);
        assertEquals(36, FrontRanking.referencePoints(3, 7).length);
        double[][] withMissing = {{10, Double.NaN, 50}, {20, 80, 50}};
        assertEquals(List.of(0), Dominance.front(withMissing, GOALS), "the missing throughput is skipped for the pair");
    }
}
