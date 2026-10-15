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

package org.ghyura.cf.constraints;

import org.ghyura.cf.spi.Cancellation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic greedy t-wise selection over rows of levels (the legacy fwgen {@code nwise_greedy},
 * {@code nwise_optimal} and {@code pick_n_for_budget}). A row is one candidate's mandatory part: for each factor (a
 * mandatory column) the index of the row of that column's table. A t-tuple is t factors in ascending order with one
 * level each; the rows cover the t-tuples they contain.
 *
 * <p>Only tuples that some allowed row contains are <em>feasible</em>; the others are counted as infeasible, never as
 * uncovered. Both strategies are greedy: {@link Method#GREEDY} keeps a row when it brings a tuple not seen yet, in
 * row order; {@link Method#SET_COVER} repeatedly takes the row covering the most uncovered tuples (the lowest index on
 * ties). Neither is a proven minimum, and no result claims one.</p>
 */
public final class Coverage {

    private Coverage() {
    }

    /** The two legacy strategies. */
    public enum Method { GREEDY, SET_COVER }

    /**
     * The tuple universe of a strength over factors with these level counts: {@code size} tuples in all, numbered
     * subset by subset (subsets of factors in lexicographic order, levels in mixed radix with the last factor fastest).
     */
    public static final class Universe {
        private final int[] levels;
        private final int strength;
        private final int[][] subsets;
        private final long[] offsets;
        private final long size;

        private Universe(int[] levels, int strength) {
            this.levels = levels.clone();
            this.strength = strength;
            List<int[]> list = new ArrayList<>();
            subsets(levels.length, strength, 0, new int[strength], 0, list);
            this.subsets = list.toArray(new int[0][]);
            this.offsets = new long[subsets.length + 1];
            BigInteger total = BigInteger.ZERO;
            for (int s = 0; s < subsets.length; s++) {
                offsets[s] = total.longValueExact();
                BigInteger product = BigInteger.ONE;
                for (int f : subsets[s]) {
                    product = product.multiply(BigInteger.valueOf(levels[f]));
                }
                total = total.add(product);
                if (total.bitLength() > 62) {
                    throw new ArithmeticException("the t-tuple universe has more than 2^62 tuples");
                }
            }
            offsets[subsets.length] = total.longValueExact();
            this.size = total.longValueExact();
        }

        private static void subsets(int n, int t, int from, int[] current, int depth, List<int[]> out) {
            if (depth == t) {
                out.add(current.clone());
                return;
            }
            for (int i = from; i <= n - (t - depth); i++) {
                current[depth] = i;
                subsets(n, t, i + 1, current, depth + 1, out);
            }
        }

        /** Every t-tuple over these levels, feasible or not. */
        public long size() {
            return size;
        }

        public int strength() {
            return strength;
        }

        /** The bytes a bit set over the universe takes (what {@link #select} allocates, twice for set cover). */
        public long bitSetBytes() {
            return 16 + 8 * ((size + 63) / 64);
        }

        /** The numbers of the tuples a row contains, one per subset of factors, in subset order. */
        void tuples(int[] row, long[] out) {
            for (int s = 0; s < subsets.length; s++) {
                long code = 0;
                for (int f : subsets[s]) {
                    code = code * levels[f] + row[f];
                }
                out[s] = offsets[s] + code;
            }
        }

        int perRow() {
            return subsets.length;
        }
    }

    /**
     * The universe of strength {@code t} over factors with these level counts.
     *
     * @throws IllegalArgumentException when t is not from 1 to the number of factors, or a level count is not positive
     */
    public static Universe universe(int[] levels, int t) {
        if (t < 1 || t > levels.length) {
            throw new IllegalArgumentException("a strength is from 1 to the number of factors (" + levels.length + "): " + t);
        }
        for (int l : levels) {
            if (l < 1) {
                throw new IllegalArgumentException("every factor needs at least one level");
            }
        }
        return new Universe(levels, t);
    }

    /**
     * What a selection kept and what it covers.
     *
     * @param kept       indexes of the kept rows, ascending
     * @param strength   t; 0 when every row is kept (no reduction)
     * @param universe   all t-tuples over the factors' levels
     * @param feasible   t-tuples some allowed row contains
     * @param covered    feasible t-tuples the kept rows contain (equal to {@code feasible} for a complete selection)
     * @param method     the strategy, or null when every row is kept
     */
    public record Selection(int[] kept, int strength, long universe, long feasible, long covered, Method method) {
        public Selection {
            kept = kept.clone();
        }

        /** Tuples no allowed row contains: infeasible under the rules, reported apart from uncovered ones. */
        public long infeasible() {
            return universe - feasible;
        }

        @Override
        public int[] kept() {
            return kept.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Selection s && Arrays.equals(kept, s.kept) && strength == s.strength && universe == s.universe
                    && feasible == s.feasible && covered == s.covered && method == s.method;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(kept), strength, universe, feasible, covered, method);
        }

        @Override
        public String toString() {
            return "Selection[kept=" + kept.length + ", t=" + strength + ", universe=" + universe + ", feasible=" + feasible
                    + ", covered=" + covered + ", method=" + method + "]";
        }
    }

    /** Every row kept: the selection of strength 0. */
    public static Selection all(int rows) {
        int[] kept = new int[rows];
        for (int i = 0; i < rows; i++) {
            kept[i] = i;
        }
        return new Selection(kept, 0, 0, 0, 0, null);
    }

    /**
     * Selects rows covering every feasible t-tuple.
     *
     * @param rows   the allowed rows in their candidate order, each with one level index per factor
     * @param levels the level count of each factor
     */
    public static Selection select(List<int[]> rows, int[] levels, int t, Method method, Cancellation cancellation) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(cancellation, "cancellation");
        if (t > levels.length) {
            // the legacy kept a row shorter than t as it was: with fewer factors than t every row is kept
            Selection every = all(rows.size());
            return new Selection(every.kept(), t, 0, 0, 0, method);
        }
        Universe u = universe(levels, t);
        for (int[] row : rows) {
            if (row.length != levels.length) {
                throw new IllegalArgumentException("a row has one level per factor");
            }
            for (int f = 0; f < row.length; f++) {
                if (row[f] < 0 || row[f] >= levels[f]) {
                    throw new IllegalArgumentException("level " + row[f] + " of factor " + f + " is outside 0.." + (levels[f] - 1));
                }
            }
        }
        LongBits feasible = new LongBits(u.size());
        long[] tuples = new long[u.perRow()];
        for (int i = 0; i < rows.size(); i++) {
            if ((i & 1023) == 0) {
                cancellation.throwIfCancelled();
            }
            u.tuples(rows.get(i), tuples);
            for (long code : tuples) {
                feasible.set(code);
            }
        }
        long feasibleCount = feasible.cardinality();
        int[] kept = method == Method.GREEDY ? greedy(rows, u, cancellation) : setCover(rows, u, feasible, feasibleCount, cancellation);
        LongBits covered = new LongBits(u.size());
        for (int i : kept) {
            u.tuples(rows.get(i), tuples);
            for (long code : tuples) {
                covered.set(code);
            }
        }
        return new Selection(kept, t, u.size(), feasibleCount, covered.cardinality(), method);
    }

    /** nwise_greedy: keep a row when it brings a tuple not seen before. */
    private static int[] greedy(List<int[]> rows, Universe u, Cancellation cancellation) {
        LongBits seen = new LongBits(u.size());
        long[] tuples = new long[u.perRow()];
        int[] kept = new int[rows.size()];
        int n = 0;
        for (int i = 0; i < rows.size(); i++) {
            if ((i & 1023) == 0) {
                cancellation.throwIfCancelled();
            }
            u.tuples(rows.get(i), tuples);
            boolean fresh = false;
            for (long code : tuples) {
                if (!seen.get(code)) {
                    fresh = true;
                    break;
                }
            }
            if (fresh) {
                for (long code : tuples) {
                    seen.set(code);
                }
                kept[n++] = i;
            }
        }
        return Arrays.copyOf(kept, n);
    }

    /** nwise_optimal: repeatedly take the row covering the most uncovered tuples, the lowest index on ties. */
    private static int[] setCover(List<int[]> rows, Universe u, LongBits feasible, long feasibleCount, Cancellation cancellation) {
        LongBits uncovered = feasible.copy();
        long remaining = feasibleCount;
        boolean[] taken = new boolean[rows.size()];
        long[] tuples = new long[u.perRow()];
        while (remaining > 0) {
            int best = -1;
            int bestGain = 0;
            for (int i = 0; i < rows.size(); i++) {
                if ((i & 1023) == 0) {
                    cancellation.throwIfCancelled();
                }
                if (taken[i]) {
                    continue;
                }
                u.tuples(rows.get(i), tuples);
                int gain = 0;
                for (long code : tuples) {
                    if (uncovered.get(code)) {          // a row's tuples are distinct: one per subset of factors
                        gain++;
                    }
                }
                if (gain > bestGain) {
                    bestGain = gain;
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            taken[best] = true;
            u.tuples(rows.get(best), tuples);
            for (long code : tuples) {
                if (uncovered.get(code)) {
                    uncovered.clear(code);
                    remaining--;
                }
            }
        }
        int n = 0;
        for (boolean b : taken) {
            n += b ? 1 : 0;
        }
        int[] kept = new int[n];
        int j = 0;
        for (int i = 0; i < taken.length; i++) {
            if (taken[i]) {
                kept[j++] = i;
            }
        }
        return kept;
    }

    /**
     * pick_n_for_budget: the strongest t (from 1 to factors - 1) whose selection keeps at most {@code budget} rows,
     * searched upwards and stopped at the first that overruns (suite sizes grow with t); every row when they all fit.
     * As in the legacy, strength 1 is returned even when its selection exceeds the budget.
     */
    public static Selection forBudget(List<int[]> rows, int[] levels, long budget, Method method, Cancellation cancellation) {
        if (rows.size() <= budget) {
            return all(rows.size());
        }
        int ceiling = Math.max(1, levels.length - 1);
        Selection best = select(rows, levels, 1, method, cancellation);
        for (int t = 2; t <= ceiling; t++) {
            Selection next = select(rows, levels, t, method, cancellation);
            if (next.kept().length > budget) {
                break;
            }
            best = next;
        }
        return best;
    }

    /** A bit set addressed by long. */
    static final class LongBits {
        private final long[] words;

        LongBits(long bits) {
            long words = (bits + 63) / 64;
            if (words > Integer.MAX_VALUE - 8) {
                throw new IllegalArgumentException("too many bits for one array: " + bits);
            }
            this.words = new long[(int) Math.max(1, words)];
        }

        private LongBits(long[] words) {
            this.words = words;
        }

        void set(long i) {
            words[(int) (i >>> 6)] |= 1L << (i & 63);
        }

        void clear(long i) {
            words[(int) (i >>> 6)] &= ~(1L << (i & 63));
        }

        boolean get(long i) {
            return (words[(int) (i >>> 6)] & (1L << (i & 63))) != 0;
        }

        long cardinality() {
            long n = 0;
            for (long w : words) {
                n += Long.bitCount(w);
            }
            return n;
        }

        LongBits copy() {
            return new LongBits(words.clone());
        }
    }
}
