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
 * The permutations of n positions in lexicographic order of the positions (the parallel library's
 * PermutationGenerator: Lehmer code, factorial number system). n! tuples; one empty tuple for n=0.
 */
public final class LexicographicPermutations implements RankedTuples {

    private final int n;
    private final BigInteger size;

    public LexicographicPermutations(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("permutations need n >= 0: " + n);
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
        int[] items = new int[n];
        for (int i = 0; i < n; i++) {
            items[i] = i;
        }
        int[] tuple = new int[n];
        BigInteger left = rank;
        int remaining = n;
        for (int i = 0; i < n; i++) {
            BigInteger block = Counting.factorial(remaining - 1);
            BigInteger[] qr = left.divideAndRemainder(block);
            int index = qr[0].intValueExact();
            tuple[i] = items[index];
            System.arraycopy(items, index + 1, items, index, remaining - index - 1);
            remaining--;
            left = qr[1];
        }
        return tuple;
    }

    @Override
    public BigInteger rankOf(int[] tuple) {
        checkPermutation(tuple, n);
        BigInteger rank = BigInteger.ZERO;
        for (int i = 0; i < n; i++) {
            int smallerLater = 0;
            for (int j = i + 1; j < n; j++) {
                if (tuple[j] < tuple[i]) {
                    smallerLater++;
                }
            }
            rank = rank.add(BigInteger.valueOf(smallerLater).multiply(Counting.factorial(n - 1 - i)));
        }
        return rank;
    }

    @Override
    public int[] successor(int[] tuple, BigInteger rank) {
        int[] next = tuple.clone();
        int i = n - 2;
        while (i >= 0 && next[i] > next[i + 1]) {
            i--;
        }
        if (i < 0) {
            return null;
        }
        int j = n - 1;
        while (next[j] < next[i]) {
            j--;
        }
        swap(next, i, j);
        for (int a = i + 1, b = n - 1; a < b; a++, b--) {
            swap(next, a, b);
        }
        return next;
    }

    static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    static void checkPermutation(int[] tuple, int n) {
        if (tuple.length != n) {
            throw RankedTuples.notMember(tuple, "a permutation of " + n + " positions has " + n + " entries");
        }
        boolean[] seen = new boolean[n];
        for (int p : tuple) {
            if (p < 0 || p >= n || seen[p]) {
                throw RankedTuples.notMember(tuple, "every position 0.." + (n - 1) + " must appear exactly once");
            }
            seen[p] = true;
        }
    }
}
