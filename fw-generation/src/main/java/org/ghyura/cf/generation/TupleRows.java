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


package org.ghyura.cf.generation;

import org.ghyura.cf.algebra.RankedTuples;
import org.ghyura.cf.algebra.TupleCursor;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.row.RankCursor;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.spi.Cancellation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.SortedMap;

/**
 * A first pass as a computed table: the tuples of an operator over a sheet's values, mapped to keys on every
 * access, holding no rows. With one source a tuple's positions index it; with two (a Cartesian product) the first
 * position indexes {@code a} and the second {@code b}. Row ids continue the sheet's first-table counter.
 */
final class TupleRows implements RowSource {

    private final RankedTuples tuples;
    private final int[] a;
    private final int[] b;
    private final KeyEncoding encoding;
    private final BigInteger idBase;
    private final int uniformLength;

    TupleRows(RankedTuples tuples, int[] a, int[] b, KeyEncoding encoding, BigInteger idBase, int uniformLength) {
        this.tuples = Objects.requireNonNull(tuples, "tuples");
        this.a = a.clone();
        this.b = b == null ? null : b.clone();
        this.encoding = Objects.requireNonNull(encoding, "encoding");
        this.idBase = Objects.requireNonNull(idBase, "idBase");
        this.uniformLength = uniformLength;
    }

    @Override
    public BigInteger size() {
        return tuples.size();
    }

    @Override
    public Row at(BigInteger rank) {
        RowSource.checkRank(rank, tuples.size());
        return row(tuples.get(rank), rank);
    }

    private Row row(int[] tuple, BigInteger rank) {
        int[] labels = new int[tuple.length];
        for (int i = 0; i < tuple.length; i++) {
            labels[i] = b == null ? a[tuple[i]] : (i == 0 ? a[tuple[0]] : b[tuple[1]]);
        }
        return new Row(idBase.add(rank).add(BigInteger.ONE), BigInteger.ZERO, KeyRow.of(encoding, labels));
    }

    @Override
    public RowCursor open(BigInteger from, BigInteger to, Cancellation cancellation) {
        RankCursor.checkRange(from, to, tuples.size());
        TupleCursor cursor = tuples.cursor(from, to, cancellation);
        return new RowCursor() {
            @Override
            public boolean hasNext() {
                return cursor.hasNext();
            }

            @Override
            public Row next() {
                BigInteger rank = cursor.position();
                return row(cursor.next(), rank);
            }

            @Override
            public BigInteger position() {
                return cursor.position();
            }

            @Override
            public void close() {
                cursor.close();
            }
        };
    }

    @Override
    public int uniformLength() {
        return tuples.size().signum() == 0 ? 0 : uniformLength;
    }

    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        return tuples.lengths();
    }

    @Override
    public String formula() {
        return tuples.formula();
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
