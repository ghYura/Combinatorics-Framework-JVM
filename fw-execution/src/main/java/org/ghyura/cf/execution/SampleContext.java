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

package org.ghyura.cf.execution;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.MemoryReservation;
import org.ghyura.cf.spi.OutputCapture;
import org.ghyura.cf.store.MemoryBudget;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * The context of one attempt. Its cancellation fires at the deadline or when the run is cancelled; its randomness comes
 * from the run seed, the plan, the candidate, the repeat and the environment (never the attempt or the thread); its
 * reservations come from the run's budget and are all returned when the attempt ends, whatever the plugin did.
 */
final class SampleContext implements ExecutionContext {

    private final SampleKey key;
    private final Cancellation cancellation;
    private final long deadlineNanos;
    private final RandomGenerator random;
    private final Capture capture;
    private final MemoryBudget budget;
    private final List<MemoryBudget.Lease> leases = new ArrayList<>();

    SampleContext(SampleKey key, Cancellation cancellation, long deadlineNanos, long seed, String planDigest, Capture capture,
                  MemoryBudget budget) {
        this.key = key;
        this.cancellation = cancellation;
        this.deadlineNanos = deadlineNanos;
        this.random = new SplittableRandom(seedOf(seed, planDigest, key));
        this.capture = capture;
        this.budget = budget;
    }

    /** The first eight bytes of SHA-256 over seed, plan, candidate, repeat and environment. */
    static long seedOf(long seed, String planDigest, SampleKey key) {
        byte[] digest = Canonical.sha256(Canonical.utf8("sample-seed/1\n" + seed + "\n" + planDigest + "\n" + key.candidateId().hex()
                + "\n" + key.repeatIndex() + "\n" + Canonical.escape(key.environmentId()) + "\n"));
        return ByteBuffer.wrap(digest, 0, 8).getLong();
    }

    @Override
    public SampleKey sample() {
        return key;
    }

    @Override
    public Cancellation cancellation() {
        return cancellation;
    }

    @Override
    public Duration remaining() {
        return Duration.ofNanos(deadlineNanos - System.nanoTime());
    }

    @Override
    public RandomGenerator random() {
        return random;
    }

    @Override
    public OutputCapture output() {
        return capture;
    }

    @Override
    public MemoryReservation reserve(long bytes, String purpose) {
        MemoryBudget.Lease lease = budget.reserve(bytes, "sample: " + purpose);
        synchronized (leases) {
            leases.add(lease);
        }
        return new MemoryReservation() {
            @Override
            public long bytes() {
                return lease.bytes();
            }

            @Override
            public void close() {
                lease.close();
            }
        };
    }

    /** Returns every reservation the attempt still holds. */
    void releaseAll() {
        synchronized (leases) {
            for (MemoryBudget.Lease lease : leases) {
                lease.close();
            }
            leases.clear();
        }
    }
}
