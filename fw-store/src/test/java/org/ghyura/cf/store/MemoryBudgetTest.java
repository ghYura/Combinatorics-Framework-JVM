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
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryBudgetTest {

    private static FrameworkException expectLimit(MemoryBudget budget, long bytes, String purpose) {
        FrameworkException e = assertThrows(FrameworkException.class, () -> budget.reserve(bytes, purpose));
        assertEquals(ErrorCode.RESOURCE_LIMIT, e.code(), e.primary().toString());
        return e;
    }

    @Test
    void aBudgetMustBePositiveAndTheConfiguredOneIsTheDefaultOfTheConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryBudget(0));
        assertThrows(IllegalArgumentException.class, () -> new MemoryBudget(-1));
        assertEquals(268_435_456L, MemoryBudget.of(RunConfig.defaults()).totalBytes());
    }

    @Test
    void reservationsAreCountedExactlyAndReturnedWhenTheirLeasesClose() {
        MemoryBudget budget = new MemoryBudget(100);
        MemoryBudget.Lease first = budget.reserve(30, "first");
        assertEquals(30, budget.reservedBytes());
        assertEquals(70, budget.availableBytes());
        MemoryBudget.Lease second = budget.reserve(70, "second");
        assertEquals(100, budget.reservedBytes());
        assertEquals(0, budget.availableBytes());
        first.close();
        assertEquals(70, budget.reservedBytes());
        assertEquals(30, budget.availableBytes());
        budget.reserve(30, "third").close();
        second.close();
        assertEquals(0, budget.reservedBytes());
        assertEquals(100, budget.availableBytes());
    }

    @Test
    void aRequestThatDoesNotFitIsRefusedAndLeavesTheBudgetExactlyAsItWas() {
        MemoryBudget budget = new MemoryBudget(100);
        try (MemoryBudget.Lease held = budget.reserve(60, "held")) {
            FrameworkException e = expectLimit(budget, 41, "the big thing");
            assertEquals(60, budget.reservedBytes(), "a refusal reserves nothing");
            assertEquals("the big thing", e.primary().attributes().get("purpose"));
            assertEquals("41", e.primary().attributes().get("requestedBytes"));
            assertEquals("40", e.primary().attributes().get("availableBytes"));
            assertEquals("100", e.primary().attributes().get("totalBytes"));
            assertTrue(e.primary().message().contains("the big thing") && e.primary().message().contains("cf.memory.budget.bytes"),
                    e.primary().message());
            assertEquals(40, budget.reserve(40, "exactly the rest").bytes(), "the boundary is inclusive");
            assertEquals(60, held.bytes());
        }
    }

    @Test
    void aHugeRequestCannotWrapAroundTheArithmetic() {
        MemoryBudget budget = new MemoryBudget(100);
        budget.reserve(10, "some");
        expectLimit(budget, Long.MAX_VALUE, "everything");
        expectLimit(budget, Long.MAX_VALUE - 5, "almost everything");
        assertEquals(10, budget.reservedBytes());
    }

    @Test
    void zeroBytesAreAllowedAndNegativeSizesAreAProgrammingError() {
        MemoryBudget budget = new MemoryBudget(10);
        try (MemoryBudget.Lease none = budget.reserve(0, "nothing")) {
            assertEquals(0, none.bytes());
            assertEquals(0, budget.reservedBytes());
        }
        assertThrows(IllegalArgumentException.class, () -> budget.reserve(-1, "negative"));
        assertThrows(NullPointerException.class, () -> budget.reserve(1, null));
    }

    @Test
    void closingALeaseTwiceReturnsItsBytesOnce() {
        MemoryBudget budget = new MemoryBudget(100);
        MemoryBudget.Lease a = budget.reserve(40, "a");
        MemoryBudget.Lease b = budget.reserve(40, "b");
        assertTrue(a.isOpen());
        a.close();
        a.close();
        assertFalse(a.isOpen());
        assertEquals(40, budget.reservedBytes(), "b is still held; a gave back 40, not 80");
        assertEquals("a", a.purpose());
        b.close();
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void manyThreadsNeverHoldMoreThanTheBudgetAtOnce() throws Exception {
        MemoryBudget budget = new MemoryBudget(4);
        AtomicInteger holding = new AtomicInteger();
        AtomicInteger mostHeld = new AtomicInteger();
        AtomicLong refusals = new AtomicLong();
        AtomicLong grants = new AtomicLong();
        int threads = 8;
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> pool = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread thread = new Thread(() -> {
                try {
                    go.await();
                    for (int i = 0; i < 5_000; i++) {
                        try (MemoryBudget.Lease lease = budget.reserve(1, "work")) {
                            assertEquals(1, lease.bytes());
                            int now = holding.incrementAndGet();
                            mostHeld.accumulateAndGet(now, Math::max);
                            grants.incrementAndGet();
                            Thread.onSpinWait();
                            holding.decrementAndGet();
                        } catch (FrameworkException refused) {
                            assertEquals(ErrorCode.RESOURCE_LIMIT, refused.code());
                            refusals.incrementAndGet();
                        }
                    }
                } catch (Throwable e) {
                    synchronized (failures) {
                        failures.add(e);
                    }
                }
            });
            pool.add(thread);
            thread.start();
        }
        go.countDown();
        for (Thread thread : pool) {
            thread.join();
        }
        assertEquals(List.of(), failures);
        assertTrue(mostHeld.get() <= 4, "at most the budget was ever held at once, saw " + mostHeld.get());
        assertEquals(0, budget.reservedBytes());
        assertEquals(threads * 5_000L, grants.get() + refusals.get());
        assertTrue(grants.get() > 0);
    }
}
