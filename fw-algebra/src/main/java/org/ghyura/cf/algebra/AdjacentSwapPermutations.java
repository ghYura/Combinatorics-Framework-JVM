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
 * The permutations of n distinct positions in the Steinhaus-Johnson-Trotter order of combinatoricslib3 3.3.3
 * simple(): each tuple differs from the one before by one adjacent swap. For n=3: (0,1,2), (0,2,1), (2,0,1),
 * (2,1,0), (1,2,0), (1,0,2). Recursively, the largest position sweeps from the right end to the left across the
 * order of the smaller positions, then from the left to the right, alternately. n! tuples for n at least 1.
 *
 * <p>Rank r of an n-permutation is r' * n + j, where r' is the rank of the permutation of the n-1 smaller
 * positions and j the step of the sweep: the largest position stands at n-1-j when r' is even and at j when it
 * is odd.</p>
 */
public final class AdjacentSwapPermutations implements RankedTuples {

    private final int n;
    private final BigInteger size;

    public AdjacentSwapPermutations(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("the adjacent-swap order needs n >= 1: " + n);
        }
        this.n = n;
        this.size = Counting.factorial(n);
    }

    @Override
    public BigInteger size() {
        return size;
    }

    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        return RankedTuples.lengthOf(n, size);
    }

    @Override
    public String formula() {
        return n + "!";
    }

    @Override
    public int[] get(BigInteger rank) {
        RankedTuples.checkRank(rank, size);
        // peel the sweep steps j_m and the ranks r_m of the smaller permutations, from m = n down to 2
        int[] step = new int[n + 1];
        boolean[] smallerRankOdd = new boolean[n + 1];
        BigInteger r = rank;
        for (int m = n; m >= 2; m--) {
            BigInteger[] qr = r.divideAndRemainder(BigInteger.valueOf(m));
            step[m] = qr[1].intValueExact();
            r = qr[0];
            smallerRankOdd[m] = r.testBit(0);
        }
        int[] perm = new int[n];
        int length = 1;
        perm[0] = 0;
        for (int m = 2; m <= n; m++) {
            int position = smallerRankOdd[m] ? step[m] : m - 1 - step[m];
            System.arraycopy(perm, position, perm, position + 1, length - position);
            perm[position] = m - 1;
            length++;
        }
        return perm;
    }

    @Override
    public BigInteger rankOf(int[] tuple) {
        LexicographicPermutations.checkPermutation(tuple, n);
        int[] position = new int[n + 1];
        int[] work = tuple.clone();
        int length = n;
        for (int m = n; m >= 2; m--) {
            int at = 0;
            while (work[at] != m - 1) {
                at++;
            }
            position[m] = at;
            System.arraycopy(work, at + 1, work, at, length - at - 1);
            length--;
        }
        BigInteger rank = BigInteger.ZERO;
        for (int m = 2; m <= n; m++) {
            int step = rank.testBit(0) ? position[m] : m - 1 - position[m];
            rank = rank.multiply(BigInteger.valueOf(m)).add(BigInteger.valueOf(step));
        }
        return rank;
    }
}
