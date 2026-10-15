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

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gate A04: every operator against an exhaustive recursive oracle written independently of it, for n up to 7,
 * with n=0, k=0, k greater than n, duplicates and multisets. The order of the sequence and the multiset of its
 * tuples are compared separately, every rank round-trips, and tuples that are not members are refused.
 */
class ExhaustiveOracleTest {

    private static final int MAX = 7;

    // ---- the oracles: plain recursion, sharing no code with the operators ----------------------------------

    private static void choose(int n, int k, int from, List<Integer> prefix, List<int[]> out) {
        if (prefix.size() == k) {
            out.add(prefix.stream().mapToInt(Integer::intValue).toArray());
            return;
        }
        for (int x = from; x < n; x++) {
            prefix.add(x);
            choose(n, k, x + 1, prefix, out);
            prefix.removeLast();
        }
    }

    private static void chooseWithRepetition(int n, int k, int from, List<Integer> prefix, List<int[]> out) {
        if (prefix.size() == k) {
            out.add(prefix.stream().mapToInt(Integer::intValue).toArray());
            return;
        }
        for (int x = from; x < n; x++) {
            prefix.add(x);
            chooseWithRepetition(n, k, x, prefix, out);
            prefix.removeLast();
        }
    }

    private static void arrange(List<Integer> remaining, List<Integer> prefix, List<int[]> out) {
        if (remaining.isEmpty()) {
            out.add(prefix.stream().mapToInt(Integer::intValue).toArray());
            return;
        }
        for (int i = 0; i < remaining.size(); i++) {
            List<Integer> rest = new ArrayList<>(remaining);
            prefix.add(rest.remove(i));
            arrange(rest, prefix, out);
            prefix.removeLast();
        }
    }

    /** Johnson-Trotter by its classical definition: insert the new largest element into each permutation of the
     * smaller ones, sweeping from the right end leftwards, then from the left end rightwards, alternately. */
    private static List<int[]> johnsonTrotter(int n) {
        List<int[]> current = new ArrayList<>();
        current.add(new int[]{0});
        for (int m = 2; m <= n; m++) {
            List<int[]> next = new ArrayList<>();
            boolean rightToLeft = true;
            for (int[] smaller : current) {
                for (int s = 0; s < m; s++) {
                    int at = rightToLeft ? m - 1 - s : s;
                    int[] p = new int[m];
                    for (int i = 0, j = 0; i < m; i++) {
                        p[i] = i == at ? m - 1 : smaller[j++];
                    }
                    next.add(p);
                }
                rightToLeft = !rightToLeft;
            }
            current = next;
        }
        return current;
    }

    /** Distinct arrangements of a multiset given as class counts, classes tried in increasing order. */
    private static void arrangeMultiset(int[] counts, int[] firstPosition, List<Integer> prefix, int length, List<int[]> out) {
        if (prefix.size() == length) {
            out.add(prefix.stream().mapToInt(Integer::intValue).toArray());
            return;
        }
        for (int c = 0; c < counts.length; c++) {
            if (counts[c] > 0) {
                counts[c]--;
                prefix.add(firstPosition[c]);
                arrangeMultiset(counts, firstPosition, prefix, length, out);
                prefix.removeLast();
                counts[c]++;
            }
        }
    }

    private static void words(int n, int k, List<Integer> prefix, List<int[]> out) {
        if (prefix.size() == k) {
            out.add(prefix.stream().mapToInt(Integer::intValue).toArray());
            return;
        }
        for (int x = 0; x < n; x++) {
            prefix.add(x);
            words(n, k, prefix, out);
            prefix.removeLast();
        }
    }

    /** The binary order by its recursive definition: the subsets of the first n-1, then each of them plus n-1. */
    private static List<int[]> subsetsOracle(int n) {
        List<int[]> current = new ArrayList<>();
        current.add(new int[0]);
        for (int m = 1; m <= n; m++) {
            List<int[]> next = new ArrayList<>(current);
            for (int[] s : current) {
                int[] t = Arrays.copyOf(s, s.length + 1);
                t[s.length] = m - 1;
                next.add(t);
            }
            current = next;
        }
        return current;
    }

    private static void product(int[] sizes, List<Integer> prefix, List<int[]> out) {
        if (prefix.size() == sizes.length) {
            out.add(prefix.stream().mapToInt(Integer::intValue).toArray());
            return;
        }
        for (int x = 0; x < sizes[prefix.size()]; x++) {
            prefix.add(x);
            product(sizes, prefix, out);
            prefix.removeLast();
        }
    }

    // ---- comparison --------------------------------------------------------------------------------------------

    private static final Comparator<int[]> TUPLE_ORDER = Arrays::compare;

    private static List<int[]> all(RankedTuples tuples) {
        List<int[]> out = new ArrayList<>();
        try (TupleCursor cursor = tuples.cursor()) {
            while (cursor.hasNext()) {
                out.add(cursor.next());
            }
        }
        return out;
    }

    private static void assertSameSequence(List<int[]> expected, RankedTuples actual, String what) {
        assertEquals(BigInteger.valueOf(expected.size()), actual.size(), what + ": size");
        List<int[]> walked = all(actual);
        // the multiset of tuples, order aside
        List<int[]> a = new ArrayList<>(expected);
        List<int[]> b = new ArrayList<>(walked);
        a.sort(TUPLE_ORDER);
        b.sort(TUPLE_ORDER);
        assertEquals(a.size(), b.size(), what);
        for (int i = 0; i < a.size(); i++) {
            assertArrayEquals(a.get(i), b.get(i), what + ": multiset");
        }
        // the order
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i), walked.get(i), what + ": tuple " + i);
            assertArrayEquals(expected.get(i), actual.get(BigInteger.valueOf(i)), what + ": get(" + i + ")");
            assertEquals(BigInteger.valueOf(i), actual.rankOf(expected.get(i)), what + ": rankOf tuple " + i);
        }
        assertThrows(IndexOutOfBoundsException.class, () -> actual.get(actual.size()), what);
        assertThrows(IndexOutOfBoundsException.class, () -> actual.get(BigInteger.ONE.negate()), what);
        // the tuples by length, from the operator's parameters
        SortedMap<Integer, BigInteger> census = new TreeMap<>();
        for (int[] tuple : expected) {
            census.merge(tuple.length, BigInteger.ONE, BigInteger::add);
        }
        assertEquals(census, actual.lengths(), what + ": lengths");
        assertFalse(actual.formula().isBlank(), what + ": formula");
    }

    private static List<int[]> run(java.util.function.Consumer<List<int[]>> oracle) {
        List<int[]> out = new ArrayList<>();
        oracle.accept(out);
        return out;
    }

    @Test
    void combinationsWithoutRepetitionMatchTheOracleForEveryNAndK() {
        for (int n = 0; n <= MAX; n++) {
            for (int k = 0; k <= n + 2; k++) {
                final int fn = n;
                final int fk = k;
                assertSameSequence(run(out -> choose(fn, fk, 0, new ArrayList<>(), out)), new Combinations(n, k), "C(" + n + "," + k + ")");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new Combinations(3, 2).rankOf(new int[]{1, 1}));
        assertThrows(IllegalArgumentException.class, () -> new Combinations(3, 2).rankOf(new int[]{2, 1}));
        assertThrows(IllegalArgumentException.class, () -> new Combinations(3, 2).rankOf(new int[]{0, 3}));
        assertThrows(IllegalArgumentException.class, () -> new Combinations(3, 2).rankOf(new int[]{0}));
    }

    @Test
    void combinationsWithRepetitionMatchTheOracle() {
        for (int n = 1; n <= MAX; n++) {
            for (int k = 0; k <= MAX; k++) {
                final int fn = n;
                final int fk = k;
                assertSameSequence(run(out -> chooseWithRepetition(fn, fk, 0, new ArrayList<>(), out)), new MultiCombinations(n, k),
                        "CR(" + n + "," + k + ")");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new MultiCombinations(3, 2).rankOf(new int[]{2, 1}));
    }

    @Test
    void permutationsInBothOrdersMatchTheirOracles() {
        for (int n = 0; n <= MAX; n++) {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                items.add(i);
            }
            assertSameSequence(run(out -> arrange(items, new ArrayList<>(), out)), new LexicographicPermutations(n), "lex P(" + n + ")");
            if (n >= 1) {
                assertSameSequence(johnsonTrotter(n), new AdjacentSwapPermutations(n), "SJT P(" + n + ")");
            }
        }
        // adjacent tuples of the Johnson-Trotter order differ by one adjacent swap
        List<int[]> sjt = all(new AdjacentSwapPermutations(6));
        for (int i = 1; i < sjt.size(); i++) {
            int diff = 0;
            int first = -1;
            for (int p = 0; p < 6; p++) {
                if (sjt.get(i)[p] != sjt.get(i - 1)[p]) {
                    diff++;
                    first = first < 0 ? p : first;
                }
            }
            assertEquals(2, diff, "tuple " + i);
            assertEquals(sjt.get(i)[first], sjt.get(i - 1)[first + 1], "an adjacent swap at " + i);
        }
        assertThrows(IllegalArgumentException.class, () -> new LexicographicPermutations(3).rankOf(new int[]{0, 0, 1}));
        assertThrows(IllegalArgumentException.class, () -> new AdjacentSwapPermutations(3).rankOf(new int[]{0, 1}));
    }

    @Test
    void distinctArrangementsOfMultisetsMatchTheOracle() {
        int[][] multisets = {{}, {0}, {0, 0}, {0, 1, 1}, {0, 1, 1, 2}, {0, 0, 0}, {0, 1, 0, 1, 2}, {0, 1, 2, 1, 0, 3, 3}, {0, 1, 2, 3, 4, 5, 6}};
        for (int[] classes : multisets) {
            int count = classes.length == 0 ? 0 : Arrays.stream(classes).max().orElse(-1) + 1;
            int[] counts = new int[count];
            int[] firstPosition = new int[count];
            Arrays.fill(firstPosition, -1);
            for (int i = 0; i < classes.length; i++) {
                counts[classes[i]]++;
                if (firstPosition[classes[i]] < 0) {
                    firstPosition[classes[i]] = i;
                }
            }
            List<int[]> expected = new ArrayList<>();
            arrangeMultiset(counts, firstPosition, new ArrayList<>(), classes.length, expected);
            assertSameSequence(expected, new MultisetPermutations(classes), "multiset " + Arrays.toString(classes));
        }
        // the duplicate counts of the core audit: a,a,b,c has 12 distinct arrangements and 24 by position
        assertEquals(BigInteger.valueOf(12), Operators.distinctArrangements(new int[]{0, 0, 1, 2}).size());
        assertEquals(BigInteger.valueOf(24), Operators.permutations(new int[]{0, 0, 1, 2}, LegacyLibrary.PARALLEL).size());
        assertThrows(IllegalArgumentException.class, () -> new MultisetPermutations(new int[]{1, 0}));
        assertThrows(IllegalArgumentException.class, () -> new MultisetPermutations(new int[]{0, 1, 1}).rankOf(new int[]{0, 2, 1}),
                "position 2 is not the first position of its class");
    }

    @Test
    void permutationsWithRepetitionMatchTheOracleInBothDigitOrders() {
        for (int n = 0; n <= 5; n++) {
            for (int k = 0; k <= 4; k++) {
                final int fn = n;
                final int fk = k;
                List<int[]> lastFastest = run(out -> words(fn, fk, new ArrayList<>(), out));
                assertSameSequence(lastFastest, new RepetitionPermutations(n, k, RepetitionPermutations.Fastest.LAST), "PR last " + n + "," + k);
                // first place fastest: the same counting with the places read backwards
                List<int[]> firstFastest = new ArrayList<>();
                for (int[] w : lastFastest) {
                    int[] r = new int[w.length];
                    for (int i = 0; i < w.length; i++) {
                        r[i] = w[w.length - 1 - i];
                    }
                    firstFastest.add(r);
                }
                assertSameSequence(firstFastest, new RepetitionPermutations(n, k, RepetitionPermutations.Fastest.FIRST), "PR first " + n + "," + k);
            }
        }
    }

    @Test
    void subsetsAndCartesianProductsMatchTheirOracles() {
        for (int n = 0; n <= MAX; n++) {
            assertSameSequence(subsetsOracle(n), new Subsets(n), "subsets of " + n);
        }
        int[][] shapes = {{}, {0}, {3}, {2, 0}, {0, 2}, {1, 1}, {2, 3}, {3, 2}, {2, 1, 3}, {3, 1, 2, 2}};
        for (int[] sizes : shapes) {
            assertSameSequence(run(out -> product(sizes, new ArrayList<>(), out)), new CartesianProduct(sizes), "product " + Arrays.toString(sizes));
        }
        assertThrows(IllegalArgumentException.class, () -> new Subsets(3).rankOf(new int[]{1, 0}));
    }

    @Test
    void concatenationsKeepTheirPartsInOrderAndRankByFirstOccurrence() {
        List<int[]> expected = new ArrayList<>();
        for (int size : new int[]{0, 2, 3}) {
            final int fsize = size;
            expected.addAll(run(out -> choose(5, fsize, 0, new ArrayList<>(), out)));
        }
        RankedTuples bySize = new Concatenation(List.of(new Combinations(5, 0), FixedTuples.NONE, new Combinations(5, 2),
                FixedTuples.NONE, new Combinations(5, 3)));
        assertSameSequence(expected, bySize, "subsets of sizes 0, 2, 3");
        RankedTuples repeated = new Concatenation(List.of(FixedTuples.ONE_EMPTY, FixedTuples.ONE_EMPTY));
        assertEquals(BigInteger.TWO, repeated.size());
        assertEquals(BigInteger.ZERO, repeated.rankOf(new int[0]), "a repeated tuple ranks at its first occurrence");
        assertEquals(BigInteger.ZERO, new Concatenation(List.of()).size());
    }
}
