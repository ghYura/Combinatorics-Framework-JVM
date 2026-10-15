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
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A lazy walk over a half-open rank range of a {@link RankedTuples}. One owner per cursor; each tuple returned is
 * a fresh array. The cursor polls its cancellation token before the first tuple and then every
 * {@link #POLL_INTERVAL} tuples, and stops with RUN_CANCELLED when it is set.
 */
public final class TupleCursor implements AutoCloseable {

    /** How many tuples are produced between two polls of the cancellation token. */
    public static final int POLL_INTERVAL = 256;

    private final RankedTuples tuples;
    private final BigInteger to;
    private final Cancellation cancellation;
    private BigInteger rank;
    private int[] current;
    private int sincePoll = POLL_INTERVAL;
    private boolean closed;

    TupleCursor(RankedTuples tuples, BigInteger from, BigInteger to, Cancellation cancellation) {
        this.tuples = Objects.requireNonNull(tuples, "tuples");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.signum() < 0 || from.compareTo(to) > 0 || to.compareTo(tuples.size()) > 0) {
            throw new IndexOutOfBoundsException("range [" + from + ", " + to + ") is not inside [0, " + tuples.size() + "]");
        }
        this.rank = from;
        this.to = to;
    }

    public boolean hasNext() {
        return !closed && rank.compareTo(to) < 0;
    }

    /** The rank of the tuple the next call to {@link #next} returns. */
    public BigInteger position() {
        return rank;
    }

    public int[] next() {
        if (!hasNext()) {
            throw new NoSuchElementException("the cursor is at the end of its range " + to);
        }
        if (++sincePoll >= POLL_INTERVAL) {
            sincePoll = 0;
            cancellation.throwIfCancelled();
        }
        current = current == null ? tuples.get(rank) : tuples.successor(current, rank.subtract(BigInteger.ONE));
        if (current == null) {
            throw new IllegalStateException("the sequence ended before rank " + rank + " of " + tuples.size());
        }
        rank = rank.add(BigInteger.ONE);
        return current.clone();
    }

    @Override
    public void close() {
        closed = true;
    }
}
