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

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A12: merged statistics equal one pass over the same samples whatever the partition (Welford, weighted reservoirs),
 * and the exact order-statistic intervals of repeats (binomial values derived by hand).
 */
class StatisticsTest {

    @Test
    void welfordAndReservoirMergesEqualOnePassWhateverThePartition() {
        Random rng = new Random(7);
        double[] values = new double[1000];
        for (int i = 0; i < values.length; i++) {
            values[i] = rng.nextGaussian() * 3 + 10;
        }
        KeyStats whole = new KeyStats("latency");
        KeyStats small = new KeyStats("latency");
        KeyStats large = new KeyStats("latency");
        PriorityReservoir all = new PriorityReservoir(1, "latency", 50);
        PriorityReservoir first = new PriorityReservoir(1, "latency", 50);
        PriorityReservoir second = new PriorityReservoir(1, "latency", 50);
        for (int i = 0; i < values.length; i++) {
            String id = "c" + i;
            double weight = 1 + (i % 3);
            whole.add(values[i], id);
            all.offer(id, values[i], weight);
            (i < 10 ? small : large).add(values[i], id);
            (i < 10 ? first : second).offer(id, values[i], weight);
        }
        small.merge(large);
        first.merge(second);
        assertEquals(whole.count(), small.count());
        assertEquals(whole.mean(), small.mean(), 1e-12);
        assertEquals(whole.stdev(), small.stdev(), 1e-12);
        assertEquals(whole.min(), small.min());
        assertEquals(whole.argmax(), small.argmax());
        assertEquals(all.items(), first.items(), "a 10/990 split merges into exactly the one-pass sample");
        assertEquals(1000, first.seen());
        assertFalse(first.exact());
        PriorityReservoir reversed = new PriorityReservoir(1, "latency", 50);
        for (int i = values.length - 1; i >= 0; i--) {
            reversed.offer("c" + i, values[i], 1 + (i % 3));
        }
        assertEquals(all.items(), reversed.items(), "the order samples arrive in does not matter");
        PriorityReservoir few = new PriorityReservoir(9, "x", 50);
        few.offer("a", 1, 1);
        assertTrue(few.exact());
    }

    @Test
    void repeatIntervalsAreExactOrderStatisticsAndNeverInventedForOneSample() {
        double[] ten = {10, 1, 9, 2, 8, 3, 7, 4, 6, 5};
        RepeatStatistics.Interval iv = RepeatStatistics.interval(ten, 0.05);
        // P(Bin(10, 1/2) <= 1) = 11/1024 <= 0.025 < P(<= 2) = 56/1024: the 2nd and 9th order statistics, coverage 1 - 22/1024
        assertTrue(iv.valid());
        assertEquals("order_statistic", iv.method());
        assertEquals(5.5, iv.median());
        assertEquals(2.0, iv.low());
        assertEquals(9.0, iv.high());
        assertEquals(1 - 22.0 / 1024, iv.coverage(), 1e-15);
        RepeatStatistics.Interval five = RepeatStatistics.interval(new double[] {1, 2, 3, 4, 5}, 0.05);
        assertFalse(five.valid(), "1/32 > 0.025: five samples cannot give a 95% interval");
        assertEquals("range_insufficient_n", five.method());
        RepeatStatistics.Interval one = RepeatStatistics.interval(new double[] {4}, 0.05);
        assertEquals("point_k1", one.method());
        assertFalse(one.valid(), "one sample is a point, not evidence of noise");
        assertTrue(RepeatStatistics.tied(new RepeatStatistics.Interval[] {iv}, new RepeatStatistics.Interval[] {
                RepeatStatistics.interval(new double[] {8, 9, 10, 11, 12, 13, 14, 15, 16, 17}, 0.05)}), "[2, 9] and [9, 16] overlap");
    }
}
