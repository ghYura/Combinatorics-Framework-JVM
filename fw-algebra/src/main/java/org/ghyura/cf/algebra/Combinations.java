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
 * The k-element combinations of n positions without repetition, as strictly increasing tuples in lexicographic
 * order: for n=3, k=2 the tuples (0,1), (0,2), (1,2). Both legacy libraries emit this order (CombinationGenerator,
 * combinatoricslib3 simple(k)). C(n, k) tuples; none when k exceeds n.
 */
public final class Combinations implements RankedTuples {

    private final int n;
    private final int k;
    private final BigInteger size;

    public Combinations(int n, int k) {
        if (n < 0 || k < 0) {
            throw new IllegalArgumentException("combinations need n >= 0 and k >= 0: n=" + n + ", k=" + k);
        }
        this.n = n;
        this.k = k;
        this.size = Counting.binomial(n, k);
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
        return "C(" + n + "," + k + ")";
    }

    @Override
    public int[] get(BigInteger rank) {
        RankedTuples.checkRank(rank, size);
        int[] tuple = new int[k];
        BigInteger left = rank;
        int next = 0;
        for (int i = 0; i < k; i++) {
            for (int x = next; ; x++) {
                BigInteger count = Counting.binomial(n - x - 1, k - i - 1);
                if (left.compareTo(count) < 0) {
                    tuple[i] = x;
                    next = x + 1;
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
        int next = 0;
        for (int i = 0; i < k; i++) {
            if (tuple[i] < next || tuple[i] >= n) {
                throw RankedTuples.notMember(tuple, "positions must increase strictly within 0.." + (n - 1));
            }
            for (int x = next; x < tuple[i]; x++) {
                rank = rank.add(Counting.binomial(n - x - 1, k - i - 1));
            }
            next = tuple[i] + 1;
        }
        return rank;
    }

    @Override
    public int[] successor(int[] tuple, BigInteger rank) {
        int[] next = tuple.clone();
        int i = k - 1;
        while (i >= 0 && next[i] == n - k + i) {
            i--;
        }
        if (i < 0) {
            return null;
        }
        next[i]++;
        for (int j = i + 1; j < k; j++) {
            next[j] = next[j - 1] + 1;
        }
        return next;
    }
}
