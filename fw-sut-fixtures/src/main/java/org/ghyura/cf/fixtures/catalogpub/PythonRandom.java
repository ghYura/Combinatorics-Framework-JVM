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

package org.ghyura.cf.fixtures.catalogpub;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

/** CPython integer-seeded MT19937 and the draw semantics used by tester_vs_pub. Run-owned, never shared. */
public final class PythonRandom {
    private final int[] state = new int[624];
    private int index = 624;
    public PythonRandom(BigInteger seed) {
        BigInteger magnitude = seed.abs();
        int[] key = new int[Math.max(1, (magnitude.bitLength() + 31) / 32)];
        for (int k = 0; k < key.length; k++) { key[k] = magnitude.intValue(); magnitude = magnitude.shiftRight(32); }
        state[0] = 19650218;
        for (int i = 1; i < 624; i++) state[i] = 1812433253 * (state[i - 1] ^ (state[i - 1] >>> 30)) + i;
        int i = 1, j = 0;
        for (int k = Math.max(624, key.length); k > 0; k--) {
            state[i] = (state[i] ^ ((state[i - 1] ^ (state[i - 1] >>> 30)) * 1664525)) + key[j] + j;
            i++; j++; if (i >= 624) { state[0] = state[623]; i = 1; } if (j >= key.length) j = 0;
        }
        for (int k = 623; k > 0; k--) {
            state[i] = (state[i] ^ ((state[i - 1] ^ (state[i - 1] >>> 30)) * 1566083941)) - i;
            i++; if (i >= 624) { state[0] = state[623]; i = 1; }
        }
        state[0] = 0x80000000;
    }
    private int word() {
        if (index >= 624) {
            for (int i = 0; i < 624; i++) {
                int y = (state[i] & 0x80000000) | (state[(i + 1) % 624] & 0x7fffffff);
                state[i] = state[(i + 397) % 624] ^ (y >>> 1) ^ ((y & 1) == 0 ? 0 : 0x9908b0df);
            }
            index = 0;
        }
        int y = state[index++]; y ^= y >>> 11; y ^= (y << 7) & 0x9d2c5680; y ^= (y << 15) & 0xefc60000; y ^= y >>> 18;
        return y;
    }
    public double random() { long a = word() >>> 5, b = word() >>> 6; return (a * 67108864.0 + b) / 9007199254740992.0; }
    public BigInteger getrandbits(int bits) {
        if (bits < 0) throw new IllegalArgumentException("number of bits must be non-negative");
        if (bits == 0) return BigInteger.ZERO;
        BigInteger result = BigInteger.ZERO;
        for (int offset = 0; offset < bits; offset += 32) {
            int take = Math.min(32, bits - offset);
            long part = Integer.toUnsignedLong(word()) >>> (32 - take);
            result = result.or(BigInteger.valueOf(part).shiftLeft(offset));
        }
        return result;
    }
    public BigInteger randbelow(BigInteger bound) {
        if (bound.signum() <= 0) throw new IllegalArgumentException("positive bound required");
        BigInteger value; do { value = getrandbits(bound.bitLength()); } while (value.compareTo(bound) >= 0); return value;
    }
    public <T> T choice(List<T> items) {
        if (items.isEmpty()) throw new PythonValues.PythonError("IndexError", "Cannot choose from an empty sequence");
        return items.get(randbelow(BigInteger.valueOf(items.size())).intValueExact());
    }
    public double uniform(double a, double b) { return a + (b - a) * random(); }
    public <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) Collections.swap(items, i, randbelow(BigInteger.valueOf(i + 1L)).intValueExact());
    }
    public <T> T weightedChoice(List<T> items, List<Double> weights) {
        if (items.isEmpty() || items.size() != weights.size()) throw new IllegalArgumentException("invalid weights");
        double[] cumulative = new double[weights.size()]; double total = 0;
        for (int i = 0; i < weights.size(); i++) { total += weights.get(i); cumulative[i] = total; }
        if (!(total > 0) || !Double.isFinite(total)) throw new IllegalArgumentException("invalid total weight");
        double value = random() * total;
        int lo = 0, hi = items.size() - 1;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (value < cumulative[mid]) hi = mid; else lo = mid + 1; }
        return items.get(lo);
    }
}
