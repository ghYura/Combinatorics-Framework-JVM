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

import java.math.BigInteger;
import java.util.SortedMap;

/**
 * The k-element combinations of n positions with repetition, as non-decreasing tuples in lexicographic order: for
 * n=3, k=2 the tuples (0,0), (0,1), (0,2), (1,1), (1,2), (2,2). Both legacy libraries emit this order
 * (MultiCombinationGenerator, combinatoricslib3 multi(k)); they differ only for n=0, which {@link Operators}
 * handles. C(n+k-1, k) tuples for n at least 1.
 */
public final class MultiCombinations implements RankedTuples {

    private final int n;
    private final int k;
    private final BigInteger size;

    public MultiCombinations(int n, int k) {
        if (n < 1 || k < 0) {
            throw new IllegalArgumentException("combinations with repetition need n >= 1 and k >= 0: n=" + n + ", k=" + k);
        }
        this.n = n;
        this.k = k;
        this.size = Counting.binomial(n + k - 1, k);
    }

    @Override
    public BigInteger size() {
        return size;
    }

    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        return RankedTuples.lengthOf(k, size);
    }

    @Override
    public String formula() {
        return "C(" + n + "+" + k + "-1," + k + ")";
    }

    @Override
    public int[] get(BigInteger rank) {
        RankedTuples.checkRank(rank, size);
        int[] tuple = new int[k];
        BigInteger left = rank;
        int last = 0;
        for (int i = 0; i < k; i++) {
            for (int x = last; ; x++) {
                BigInteger count = Counting.binomial(n - x + k - i - 2, k - i - 1);
                if (left.compareTo(count) < 0) {
                    tuple[i] = x;
                    last = x;
                    break;
                }
                left = left.subtract(count);
            }
        }
        return tuple;
    }

    @Override
    public BigInteger rankOf(int[] tuple) {
        if (tuple.length != k) {
            throw RankedTuples.notMember(tuple, "it has " + tuple.length + " positions, not " + k);
        }
        BigInteger rank = BigInteger.ZERO;
        int last = 0;
        for (int i = 0; i < k; i++) {
            if (tuple[i] < last || tuple[i] >= n) {
                throw RankedTuples.notMember(tuple, "positions must not decrease and stay within 0.." + (n - 1));
            }
            for (int x = last; x < tuple[i]; x++) {
                rank = rank.add(Counting.binomial(n - x + k - i - 2, k - i - 1));
            }
            last = tuple[i];
        }
        return rank;
    }

    @Override
    public int[] successor(int[] tuple, BigInteger rank) {
        int[] next = tuple.clone();
        int i = k - 1;
        while (i >= 0 && next[i] == n - 1) {
            i--;
        }
        if (i < 0) {
            return null;
        }
        next[i]++;
        for (int j = i + 1; j < k; j++) {
            next[j] = next[i];
        }
        return next;
    }
}
