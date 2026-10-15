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

package org.ghyura.cf.algebra;

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.spi.CancellationSource;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A04, the rest: deterministic splitting (union and no overlap, the remainder to the first parts, never
 * more parts than ranks), cursors over any sub-range equal to random access, counts and ranks beyond
 * Long.MAX_VALUE, and early cancellation.
 */
class RangesCursorsAndScaleTest {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private static List<RankedTuples> samples() {
        return List.of(new Combinations(7, 3), new MultiCombinations(4, 3), new LexicographicPermutations(5),
                new AdjacentSwapPermutations(5), new MultisetPermutations(new int[]{0, 1, 1, 2, 0}),
                new RepetitionPermutations(3, 4, RepetitionPermutations.Fastest.LAST),
                new RepetitionPermutations(3, 4, RepetitionPermutations.Fastest.FIRST), new Subsets(6), new CartesianProduct(3, 1, 4),
                new Concatenation(List.of(new Combinations(5, 1), FixedTuples.NONE, new Combinations(5, 4))));
    }

    @Test
    void splitsCoverTheWholeRangeInOrderWithoutOverlapAndGiveTheRemainderToTheFirstParts() {
        assertEquals(List.of(), Ranges.split(BigInteger.ZERO, 4));
        assertEquals(List.of(new Ranges.Range(BigInteger.ZERO, BigInteger.ONE), new Ranges.Range(BigInteger.ONE, BigInteger.TWO),
                new Ranges.Range(BigInteger.TWO, BigInteger.valueOf(3))), Ranges.split(BigInteger.valueOf(3), 8), "never more parts than ranks");
        List<Ranges.Range> ten = Ranges.split(BigInteger.TEN, 4);
        assertEquals(List.of(3L, 3L, 2L, 2L), ten.stream().map(r -> r.length().longValueExact()).toList());
        for (long total : new long[]{1, 2, 7, 64, 1000, 1001}) {
            for (int parts = 1; parts <= 9; parts++) {
                List<Ranges.Range> split = Ranges.split(BigInteger.valueOf(total), parts);
                BigInteger next = BigInteger.ZERO;
                for (Ranges.Range r : split) {
                    assertEquals(next, r.from(), "contiguous and in order");
                    assertTrue(r.length().signum() > 0, "no empty part");
                    next = r.to();
                }
                assertEquals(BigInteger.valueOf(total), next, "the union is the whole range");
                BigInteger longest = split.stream().map(Ranges.Range::length).max(BigInteger::compareTo).orElseThrow();
                BigInteger shortest = split.stream().map(Ranges.Range::length).min(BigInteger::compareTo).orElseThrow();
                assertTrue(longest.subtract(shortest).compareTo(BigInteger.ONE) <= 0, "balanced");
            }
        }
        Ranges.Range huge = Ranges.split(Counting.factorial(30), 7).getLast();
        assertEquals(Counting.factorial(30), huge.to());
        assertThrows(IllegalArgumentException.class, () -> Ranges.split(BigInteger.TEN, 0));
    }

    @Test
    void cursorsOverEverySubRangeAndOverEverySplitEqualRandomAccess() {
        for (RankedTuples tuples : samples()) {
            int size = tuples.size().intValueExact();
            List<int[]> expected = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                expected.add(tuples.get(BigInteger.valueOf(i)));
            }
            for (int from = 0; from <= size; from += Math.max(1, size / 9)) {
                for (int to = from; to <= size; to += Math.max(1, size / 7)) {
                    try (TupleCursor cursor = tuples.cursor(BigInteger.valueOf(from), BigInteger.valueOf(to), org.ghyura.cf.spi.Cancellation.NONE)) {
                        for (int i = from; i < to; i++) {
                            assertEquals(BigInteger.valueOf(i), cursor.position());
                            assertArrayEquals(expected.get(i), cursor.next(), tuples.getClass().getSimpleName() + " at " + i);
                        }
                        assertFalse(cursor.hasNext());
                    }
                }
            }
            for (int parts = 1; parts <= 5; parts++) {
                List<int[]> joined = new ArrayList<>();
                for (Ranges.Range r : Ranges.split(tuples.size(), parts)) {
                    try (TupleCursor cursor = tuples.cursor(r.from(), r.to(), org.ghyura.cf.spi.Cancellation.NONE)) {
                        while (cursor.hasNext()) {
                            joined.add(cursor.next());
                        }
                    }
                }
                assertEquals(size, joined.size());
                for (int i = 0; i < size; i++) {
                    assertArrayEquals(expected.get(i), joined.get(i), "split into " + parts);
                }
            }
        }
    }

    @Test
    void aReturnedTupleBelongsToTheCallerAndARangeOutsideTheSequenceIsRefused() {
        RankedTuples tuples = new Combinations(5, 2);
        try (TupleCursor cursor = tuples.cursor()) {
            int[] first = cursor.next();
            first[0] = 99;
            assertArrayEquals(new int[]{0, 2}, cursor.next(), "mutating a returned tuple does not change the walk");
        }
        int[] got = tuples.get(BigInteger.ZERO);
        got[1] = 42;
        assertArrayEquals(new int[]{0, 1}, tuples.get(BigInteger.ZERO));
        assertThrows(IndexOutOfBoundsException.class, () -> tuples.cursor(BigInteger.ZERO, BigInteger.valueOf(11), org.ghyura.cf.spi.Cancellation.NONE));
        assertThrows(IndexOutOfBoundsException.class, () -> tuples.cursor(BigInteger.valueOf(3), BigInteger.TWO, org.ghyura.cf.spi.Cancellation.NONE));
        TupleCursor closed = tuples.cursor();
        closed.close();
        assertFalse(closed.hasNext());
    }

    @Test
    void countsAndRanksBeyondLongMaxValueAreExact() {
        Random random = new Random(20261001L);
        List<RankedTuples> huge = List.of(new LexicographicPermutations(25), new AdjacentSwapPermutations(22),
                new Combinations(200, 100), new MultiCombinations(60, 40), new RepetitionPermutations(10, 30, RepetitionPermutations.Fastest.LAST),
                new RepetitionPermutations(7, 33, RepetitionPermutations.Fastest.FIRST), new Subsets(130), new CartesianProduct(1000, 999, 998, 997, 996, 995, 994),
                new MultisetPermutations(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 0, 1, 2}));
        for (RankedTuples tuples : huge) {
            String what = tuples.getClass().getSimpleName();
            assertTrue(tuples.size().compareTo(LONG_MAX) > 0, what + " is meant to exceed a long: " + tuples.size());
            for (BigInteger rank : List.of(BigInteger.ZERO, LONG_MAX, LONG_MAX.add(BigInteger.ONE), tuples.size().subtract(BigInteger.ONE),
                    new BigInteger(tuples.size().bitLength() - 1, random))) {
                int[] tuple = tuples.get(rank);
                assertEquals(rank, tuples.rankOf(tuple), what + " rank " + rank);
            }
            // the successor across the long boundary is the next rank
            int[] at = tuples.get(LONG_MAX);
            assertArrayEquals(tuples.get(LONG_MAX.add(BigInteger.ONE)), tuples.successor(at, LONG_MAX), what);
        }
        assertEquals(new BigInteger("15511210043330985984000000"), Counting.factorial(25));
        assertEquals(new BigInteger("90548514656103281165404177077484163874504589675413336841320"), Counting.binomial(200, 100));
        assertEquals(BigInteger.ONE.shiftLeft(130), new Subsets(130).size());
        assertEquals(Counting.factorial(23).divide(BigInteger.valueOf(8)), new MultisetPermutations(
                new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 0, 1, 2}).size(), "23! / (2! 2! 2!)");
    }

    @Test
    void aCancelledCursorStopsWithinOnePollIntervalAndAPreCancelledOneBeforeTheFirstTuple() {
        CancellationSource cancellation = new CancellationSource();
        RankedTuples tuples = new LexicographicPermutations(9);
        int produced = 0;
        try (TupleCursor cursor = tuples.cursor(BigInteger.ZERO, tuples.size(), cancellation)) {
            FrameworkException stopped = assertThrows(FrameworkException.class, () -> {
                int count = 0;
                while (cursor.hasNext()) {
                    cursor.next();
                    if (++count == 1000) {
                        cancellation.cancel();
                    }
                }
            });
            assertEquals(ErrorCode.RUN_CANCELLED, stopped.code());
            produced = cursor.position().intValueExact();
        }
        assertTrue(produced >= 1000 && produced <= 1000 + TupleCursor.POLL_INTERVAL, "stopped after " + produced);
        CancellationSource already = new CancellationSource();
        already.cancel();
        try (TupleCursor cursor = new Subsets(4).cursor(BigInteger.ZERO, BigInteger.valueOf(16), already)) {
            assertEquals(ErrorCode.RUN_CANCELLED, assertThrows(FrameworkException.class, cursor::next).code());
            assertEquals(BigInteger.ZERO, cursor.position(), "nothing was produced");
        }
    }
}
