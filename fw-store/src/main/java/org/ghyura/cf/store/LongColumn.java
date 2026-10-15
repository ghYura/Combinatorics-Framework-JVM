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


package org.ghyura.cf.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A growable column of 64-bit integers in chunks of {@value #CHUNK} values, each chunk reserved from the run's memory
 * budget before it is allocated: the indexes generation builds over a table (cumulative counts, rank orders).
 * Filling is for one thread; a filled column may be read from several.
 */
public final class LongColumn implements AutoCloseable {

    static final int CHUNK = 4096;
    /** The chunk array's header and its slot in the chunk list: an upper bound. */
    static final long CHUNK_OVERHEAD = 64;

    private final String purpose;
    private final MemoryBudget budget;
    private final List<long[]> chunks = new ArrayList<>();
    private final List<MemoryBudget.Lease> leases = new ArrayList<>();
    private long size;
    private long reserved;
    private boolean closed;

    public LongColumn(String purpose, MemoryBudget budget) {
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    /** @throws org.ghyura.cf.model.FrameworkException RESOURCE_LIMIT when a new chunk does not fit the budget */
    public void add(long value) {
        if (closed) {
            throw new IllegalStateException(purpose + " is closed");
        }
        if (size % CHUNK == 0) {
            MemoryBudget.Lease lease = budget.reserve(CHUNK_OVERHEAD + 8L * CHUNK, purpose);
            try {
                chunks.add(new long[CHUNK]);
            } catch (RuntimeException | Error e) {
                lease.close();
                throw e;
            }
            leases.add(lease);
            reserved += lease.bytes();
        }
        chunks.get((int) (size / CHUNK))[(int) (size % CHUNK)] = value;
        size++;
    }

    public long get(long index) {
        Objects.checkIndex(index, size);
        return chunks.get((int) (index / CHUNK))[(int) (index % CHUNK)];
    }

    public void set(long index, long value) {
        Objects.checkIndex(index, size);
        chunks.get((int) (index / CHUNK))[(int) (index % CHUNK)] = value;
    }

    public long size() {
        return size;
    }

    public long reservedBytes() {
        return reserved;
    }

    /** Returns every reservation; closing twice is harmless. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        chunks.clear();
        for (MemoryBudget.Lease lease : leases) {
            lease.close();
        }
        leases.clear();
        reserved = 0;
        size = 0;
    }
}
