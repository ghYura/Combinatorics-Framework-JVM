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
 * The k-tuples over n positions with repetition (n^k tuples), as base-n numerals counted up. The legacy libraries
 * count differently: the parallel library varies the last place fastest ((0,0), (0,1), ... (1,0), ...),
 * combinatoricslib3 3.3.3 the first place ((0,0), (1,0), ... (0,1), ...).
 */
public final class RepetitionPermutations implements RankedTuples {

    /** Which place of the tuple changes from one tuple to the next. */
    public enum Fastest { LAST, FIRST }

    private final int n;
    private final int k;
    private final Fastest fastest;
    private final BigInteger size;

    public RepetitionPermutations(int n, int k, Fastest fastest) {
        if (n < 0 || k < 0) {
            throw new IllegalArgumentException("permutations with repetition need n >= 0 and k >= 0: n=" + n + ", k=" + k);
        }
        this.n = n;
        this.k = k;
        this.fastest = java.util.Objects.requireNonNull(fastest, "fastest");
        this.size = Counting.power(n, k);
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
        return n + "^" + k;
    }

    private int place(int digit) {
        // digit 0 is the fastest-changing digit
        return fastest == Fastest.LAST ? k - 1 - digit : digit;
    }

    @Override
    public int[] get(BigInteger rank) {
        RankedTuples.checkRank(rank, size);
        int[] tuple = new int[k];
        BigInteger left = rank;
        BigInteger base = BigInteger.valueOf(n);
        for (int digit = 0; digit < k; digit++) {
            BigInteger[] qr = left.divideAndRemainder(base);
            tuple[place(digit)] = qr[1].intValueExact();
            left = qr[0];
        }
        return tuple;
    }

    @Override
    public BigInteger rankOf(int[] tuple) {
        if (tuple.length != k) {
            throw RankedTuples.notMember(tuple, "it has " + tuple.length + " places, not " + k);
        }
        BigInteger rank = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(n);
        for (int digit = k - 1; digit >= 0; digit--) {
            int value = tuple[place(digit)];
            if (value < 0 || value >= n) {
                throw RankedTuples.notMember(tuple, "positions must be within 0.." + (n - 1));
            }
            rank = rank.multiply(base).add(BigInteger.valueOf(value));
        }
        return rank;
    }

    @Override
    public int[] successor(int[] tuple, BigInteger rank) {
        int[] next = tuple.clone();
        for (int digit = 0; digit < k; digit++) {
            int at = place(digit);
            if (next[at] + 1 < n) {
                next[at]++;
                return next;
            }
            next[at] = 0;
        }
        return null;
    }
}
