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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.math.BigInteger;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * All 2^n subsets of n positions in binary counting order, position i being bit i: (), (0), (1), (0,1), (2),
 * (0,2), (1,2), (0,1,2). Both legacy libraries emit this order (SubSetGenerator, combinatoricslib3 subset().simple()).
 * Each subset lists its positions in increasing order; the empty set is one tuple.
 */
public final class Subsets implements RankedTuples {

    private final int n;
    private final BigInteger size;

    public Subsets(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("subsets need n >= 0: " + n);
        }
        this.n = n;
        this.size = BigInteger.ONE.shiftLeft(n);
    }

    @Override
    public BigInteger size() {
        return size;
    }

    /** Optional histogram; RESOURCE_LIMIT when its big counts exceed the metadata allowance. {@link #size()} stays exact. */
    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        // A histogram is optional report metadata; 2^n itself remains cheap and exact even when n+1 big counts are not.
        long bytes = ((long) n + 1) * (128L + ((long) n + 7) / 8);
        long limit = Math.min(4L * 1024 * 1024, Math.max(1, Runtime.getRuntime().maxMemory() / 32));
        if (bytes > limit) {
            throw new FrameworkException(Diagnostic.error(
                    ErrorCode.RESOURCE_LIMIT, "subset row-length histogram needs about " + bytes
                            + " bytes, above its optional metadata allowance of " + limit,
                    SourceLocation.NONE));
        }
        SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
        BigInteger count = BigInteger.ONE;
        for (int k = 0; k <= n; k++) {
            lengths.put(k, count);
            if (k < n) count = count.multiply(BigInteger.valueOf(n - k)).divide(BigInteger.valueOf(k + 1L));
        }
        return lengths;
    }

    @Override
    public String formula() {
        return "2^" + n;
    }

    @Override
    public int[] get(BigInteger rank) {
        RankedTuples.checkRank(rank, size);
        int[] tuple = new int[rank.bitCount()];
        int at = 0;
        for (int i = 0; i < n; i++) {
            if (rank.testBit(i)) {
                tuple[at++] = i;
            }
        }
        return tuple;
    }

    @Override
    public BigInteger rankOf(int[] tuple) {
        BigInteger rank = BigInteger.ZERO;
        int previous = -1;
        for (int p : tuple) {
            if (p <= previous || p >= n) {
                throw RankedTuples.notMember(tuple, "positions must increase strictly within 0.." + (n - 1));
            }
            rank = rank.setBit(p);
            previous = p;
        }
        return rank;
    }
}
