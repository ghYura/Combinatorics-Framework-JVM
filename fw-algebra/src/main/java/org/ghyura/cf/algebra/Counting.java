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

/**
 * Exact counts in BigInteger arithmetic (audit CB02): the legacy library overflowed {@code long} beyond 20! and
 * multiplied before dividing; here every count is exact for any argument, and no overflow becomes semantics.
 */
public final class Counting {

    private Counting() {
    }

    public static BigInteger factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("factorial of a negative number: " + n);
        }
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    /** C(n, k): 0 when k is negative, larger than n, or n is negative (the legacy guards). */
    public static BigInteger binomial(int n, int k) {
        if (n < 0 || k < 0 || k > n) {
            return BigInteger.ZERO;
        }
        int small = Math.min(k, n - k);
        BigInteger result = BigInteger.ONE;
        for (int i = 1; i <= small; i++) {
            // exact at every step: the product of i consecutive integers is divisible by i!
            result = result.multiply(BigInteger.valueOf(n - small + i)).divide(BigInteger.valueOf(i));
        }
        return result;
    }

    /** base^exponent with 0^0 = 1. */
    public static BigInteger power(int base, int exponent) {
        if (base < 0 || exponent < 0) {
            throw new IllegalArgumentException("power needs a non-negative base and exponent: " + base + "^" + exponent);
        }
        return BigInteger.valueOf(base).pow(exponent);
    }

    /** (c0 + c1 + ...)! / (c0! c1! ...): the number of distinct arrangements of a multiset. */
    public static BigInteger multinomial(int[] counts) {
        BigInteger result = BigInteger.ONE;
        int total = 0;
        for (int count : counts) {
            if (count < 0) {
                throw new IllegalArgumentException("a negative multiplicity: " + count);
            }
            // multiply by C(total + count, count) class by class: exact and small intermediate values
            result = result.multiply(binomial(total + count, count));
            total += count;
        }
        return result;
    }
}
