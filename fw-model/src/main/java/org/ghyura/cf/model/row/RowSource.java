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


package org.ghyura.cf.model.row;

import org.ghyura.cf.spi.Cancellation;

import java.math.BigInteger;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A table of rows in a fixed order with exact random access (contracts sketch: RankedRows, here with each row's
 * provenance): either computed from its inputs on every access, holding no rows, or stored in budgeted blocks.
 * Sizes and ranks are exact integers, so a computed table may be longer than any machine word. Implementations
 * do not change once built and may be read from several threads; every row returned is a fresh object.
 */
public interface RowSource {

    /** How many rows the table has. */
    BigInteger size();

    /**
     * The row at {@code rank}.
     *
     * @throws IndexOutOfBoundsException when the rank is outside {@code [0, size())}
     */
    Row at(BigInteger rank);

    /** The rows with ranks {@code from} (inclusive) to {@code to} (exclusive), lazily, polling {@code cancellation}. */
    default RowCursor open(BigInteger from, BigInteger to, Cancellation cancellation) {
        return new RankCursor(this, from, to, cancellation);
    }

    /** Every row, lazily. */
    default RowCursor open(Cancellation cancellation) {
        return open(BigInteger.ZERO, size(), cancellation);
    }

    /** The number of keys of every row, or -1 when rows differ in length; an empty table answers 0. */
    int uniformLength();

    /**
     * How many rows have each number of keys, by length, when that is known without reading the rows: from the
     * operators' parameters for a computed table, counted while a stored table was filled; null when it would take a
     * pass over the rows. Lengths without rows are absent.
     */
    default SortedMap<Integer, BigInteger> lengths() {
        SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
        int length = uniformLength();
        if (size().signum() > 0) {
            if (length < 0) {
                return null;
            }
            lengths.put(length, size());
        }
        return lengths;
    }

    /** How {@link #size()} follows from the table's inputs, as a short formula. */
    default String formula() {
        return size() + (stored() ? " stored rows" : " computed rows");
    }

    /** Whether ids strictly increase with rank, so no id repeats and the id order is the rank order. */
    boolean idsAscending();

    /** Whether the rows are held in memory (true) or computed on every access (false). */
    boolean stored();

    /** The bytes this table keeps reserved from the run's memory budget for itself; 0 when it holds nothing. */
    long reservedBytes();

    /** An empty table. */
    static RowSource empty() {
        return Empty.INSTANCE;
    }

    /** Checks a rank against a size; the message names both. */
    static void checkRank(BigInteger rank, BigInteger size) {
        if (rank.signum() < 0 || rank.compareTo(size) >= 0) {
            throw new IndexOutOfBoundsException("rank " + rank + " is outside [0, " + size + ")");
        }
    }

    /** The table without rows. */
    enum Empty implements RowSource {
        INSTANCE;

        @Override
        public BigInteger size() {
            return BigInteger.ZERO;
        }

        @Override
        public Row at(BigInteger rank) {
            throw new IndexOutOfBoundsException("rank " + rank + " of an empty table");
        }

        @Override
        public int uniformLength() {
            return 0;
        }

        @Override
        public boolean idsAscending() {
            return true;
        }

        @Override
        public boolean stored() {
            return false;
        }

        @Override
        public long reservedBytes() {
            return 0;
        }
    }
}
