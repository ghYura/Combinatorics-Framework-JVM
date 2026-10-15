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

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Admission control for everything a run keeps on the heap (architecture rule R08): bytes are reserved before
 * they are allocated, the reservation is an {@link AutoCloseable} lease, and a request that does not fit is
 * refused with RESOURCE_LIMIT instead of being allowed to push the JVM into an out-of-memory error or being
 * dropped silently. Thread-safe.
 */
public final class MemoryBudget {

    private final long total;
    private final AtomicLong reserved = new AtomicLong();
    private final AtomicLong highWater = new AtomicLong();

    public MemoryBudget(long totalBytes) {
        if (totalBytes <= 0) {
            throw new IllegalArgumentException("a memory budget must be positive: " + totalBytes);
        }
        this.total = totalBytes;
    }

    /** The budget the configuration allows ({@code cf.memory.budget.bytes}). */
    public static MemoryBudget of(RunConfig config) {
        return new MemoryBudget(Objects.requireNonNull(config, "config").memoryBudgetBytes());
    }

    public long totalBytes() {
        return total;
    }

    public long reservedBytes() {
        return reserved.get();
    }

    /** The most bytes ever reserved at the same time. */
    public long highWaterBytes() {
        return highWater.get();
    }

    public long availableBytes() {
        return total - reserved.get();
    }

    /**
     * @param bytes   the estimated size of what is about to be allocated, rounded up; never an underestimate
     * @param purpose what the memory is for, named in the refusal
     * @throws FrameworkException RESOURCE_LIMIT when the request does not fit in what is still free
     */
    public Lease reserve(long bytes, String purpose) {
        Objects.requireNonNull(purpose, "purpose");
        if (bytes < 0) {
            throw new IllegalArgumentException("cannot reserve a negative size: " + bytes);
        }
        while (true) {
            long current = reserved.get();
            if (bytes > total - current) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, "reserving " + bytes
                        + " bytes for " + purpose + " would exceed the memory budget of " + total + " bytes ("
                        + current + " already reserved); raise cf.memory.budget.bytes or shrink the scenario",
                        SourceLocation.NONE, "purpose", purpose, "requestedBytes", Long.toString(bytes), "availableBytes",
                        Long.toString(total - current), "totalBytes", Long.toString(total)));
            }
            if (reserved.compareAndSet(current, current + bytes)) {
                highWater.accumulateAndGet(current + bytes, Math::max);
                return new Lease(bytes, purpose);
            }
        }
    }

    /** One reservation. Closing it returns the bytes; closing twice returns them once. */
    public final class Lease implements AutoCloseable {
        private final long bytes;
        private final String purpose;
        private final AtomicBoolean open = new AtomicBoolean(true);

        private Lease(long bytes, String purpose) {
            this.bytes = bytes;
            this.purpose = purpose;
        }

        public long bytes() {
            return bytes;
        }

        public String purpose() {
            return purpose;
        }

        public boolean isOpen() {
            return open.get();
        }

        @Override
        public void close() {
            if (open.compareAndSet(true, false)) {
                reserved.addAndGet(-bytes);
            }
        }
    }
}
