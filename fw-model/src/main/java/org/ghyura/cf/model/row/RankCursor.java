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
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * The cursor every {@link RowSource} has: one {@link RowSource#at} per row. It polls the cancellation token before
 * the first row and then every {@link #POLL_INTERVAL} rows. Sources with a cheaper sequential walk provide their own.
 */
public final class RankCursor implements RowCursor {

    /** How many rows are returned between two polls of the cancellation token. */
    public static final int POLL_INTERVAL = 256;

    private final RowSource source;
    private final BigInteger to;
    private final Cancellation cancellation;
    private BigInteger rank;
    private int sincePoll = POLL_INTERVAL;
    private boolean closed;

    public RankCursor(RowSource source, BigInteger from, BigInteger to, Cancellation cancellation) {
        this.source = Objects.requireNonNull(source, "source");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        checkRange(from, to, source.size());
        this.rank = from;
        this.to = to;
    }

    /** Checks that {@code [from, to)} lies inside {@code [0, size]}. */
    public static void checkRange(BigInteger from, BigInteger to, BigInteger size) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.signum() < 0 || from.compareTo(to) > 0 || to.compareTo(size) > 0) {
            throw new IndexOutOfBoundsException("range [" + from + ", " + to + ") is not inside [0, " + size + "]");
        }
    }

    @Override
    public boolean hasNext() {
        return !closed && rank.compareTo(to) < 0;
    }

    @Override
    public Row next() {
        if (!hasNext()) {
            throw new NoSuchElementException("the cursor is at the end of its range " + to);
        }
        if (++sincePoll >= POLL_INTERVAL) {
            sincePoll = 0;
            cancellation.throwIfCancelled();
        }
        Row row = source.at(rank);
        rank = rank.add(BigInteger.ONE);
        return row;
    }

    @Override
    public BigInteger position() {
        return rank;
    }

    @Override
    public void close() {
        closed = true;
    }
}
