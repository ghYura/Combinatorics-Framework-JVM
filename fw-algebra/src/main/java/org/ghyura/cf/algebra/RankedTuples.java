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

import org.ghyura.cf.spi.Cancellation;

import java.math.BigInteger;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A finite sequence of index tuples in a fixed order, with exact random access (contracts sketch: RankedRows).
 * The tuples are positions into the source the operator is applied to (the values of a sheet, the rows of a
 * table); mapping positions to keys is the generation module's job. Counts and ranks are BigInteger, so a
 * sequence may be longer than any machine word, and every rank in {@code [0, size())} can be reached directly.
 * Implementations are immutable and thread-safe; every tuple returned is a fresh array the caller owns.
 */
public interface RankedTuples {

    /** How many tuples the sequence holds. */
    BigInteger size();

    /**
     * The tuple at {@code rank}.
     *
     * @throws IndexOutOfBoundsException when the rank is outside {@code [0, size())}
     */
    int[] get(BigInteger rank);

    /**
     * The rank of {@code tuple}: the inverse of {@link #get}.
     *
     * @throws IllegalArgumentException when the tuple is not in the sequence
     */
    BigInteger rankOf(int[] tuple);

    /**
     * The tuple that follows {@code tuple} (whose rank is {@code rank}) in this order, as a fresh array, or null
     * after the last one. Operators with a cheap successor override this; the default unranks the next rank.
     */
    default int[] successor(int[] tuple, BigInteger rank) {
        BigInteger next = rank.add(BigInteger.ONE);
        return next.compareTo(size()) < 0 ? get(next) : null;
    }

    /**
     * How many tuples have each length, by length, computed from the operator's parameters without enumerating a
     * tuple; lengths with no tuple are absent, so an empty sequence answers an empty map.
     */
    SortedMap<Integer, BigInteger> lengths();

    /** How {@link #size()} follows from the operator's parameters, for example {@code C(5,2)} or {@code 3^2}. */
    String formula();

    /** The distribution of one length: {@code count} tuples of {@code length}, or none. */
    static SortedMap<Integer, BigInteger> lengthOf(int length, BigInteger count) {
        SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
        if (count.signum() > 0) {
            lengths.put(length, count);
        }
        return lengths;
    }

    /** The tuples with ranks {@code from} (inclusive) to {@code to} (exclusive), lazily, polling {@code cancellation}. */
    default TupleCursor cursor(BigInteger from, BigInteger to, Cancellation cancellation) {
        return new TupleCursor(this, from, to, cancellation);
    }

    /** Every tuple, lazily. */
    default TupleCursor cursor() {
        return cursor(BigInteger.ZERO, size(), Cancellation.NONE);
    }

    static void checkRank(BigInteger rank, BigInteger size) {
        if (rank.signum() < 0 || rank.compareTo(size) >= 0) {
            throw new IndexOutOfBoundsException("rank " + rank + " is outside [0, " + size + ")");
        }
    }

    static IllegalArgumentException notMember(int[] tuple, String why) {
        return new IllegalArgumentException(java.util.Arrays.toString(tuple) + " is not in the sequence: " + why);
    }
}
