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
import java.util.Arrays;
import java.util.SortedMap;

/**
 * The tuples that take one position from each of several sources, the last source varying fastest: for sizes
 * (2, 3) the tuples (0,0), (0,1), (0,2), (1,0), (1,1), (1,2). Both legacy libraries emit this order
 * (CartesianProductGenerator, combinatoricslib3 cartesianProduct). Empty when any source is empty.
 */
public final class CartesianProduct implements RankedTuples {

    private final int[] sizes;
    private final BigInteger size;

    public CartesianProduct(int... sizes) {
        this.sizes = sizes.clone();
        BigInteger product = BigInteger.ONE;
        for (int s : sizes) {
            if (s < 0) {
                throw new IllegalArgumentException("a source size is negative: " + Arrays.toString(sizes));
            }
            product = product.multiply(BigInteger.valueOf(s));
        }
        this.size = product;
    }

    @Override
    public BigInteger size() {
        return size;
    }

    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        return RankedTuples.lengthOf(sizes.length, size);
    }

    @Override
    public String formula() {
        return sizes.length == 0 ? "1" : String.join("×", Arrays.stream(sizes).mapToObj(Integer::toString).toList());
    }

    @Override
    public int[] get(BigInteger rank) {
        RankedTuples.checkRank(rank, size);
        int[] tuple = new int[sizes.length];
        BigInteger left = rank;
        for (int i = sizes.length - 1; i >= 0; i--) {
            BigInteger[] qr = left.divideAndRemainder(BigInteger.valueOf(sizes[i]));
            tuple[i] = qr[1].intValueExact();
            left = qr[0];
        }
        return tuple;
    }

    @Override
    public BigInteger rankOf(int[] tuple) {
        if (tuple.length != sizes.length) {
            throw RankedTuples.notMember(tuple, "it takes one position from each of " + sizes.length + " sources");
        }
        BigInteger rank = BigInteger.ZERO;
        for (int i = 0; i < sizes.length; i++) {
            if (tuple[i] < 0 || tuple[i] >= sizes[i]) {
                throw RankedTuples.notMember(tuple, "place " + i + " must be within 0.." + (sizes[i] - 1));
            }
            rank = rank.multiply(BigInteger.valueOf(sizes[i])).add(BigInteger.valueOf(tuple[i]));
        }
        return rank;
    }

    @Override
    public int[] successor(int[] tuple, BigInteger rank) {
        int[] next = tuple.clone();
        for (int i = sizes.length - 1; i >= 0; i--) {
            if (next[i] + 1 < sizes[i]) {
                next[i]++;
                return next;
            }
            next[i] = 0;
        }
        return null;
    }
}
