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

import org.ghyura.cf.store.LongColumn;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The running totals of how many rows each row of a table expands into, kept so that a rank of the expansion finds
 * its source row by binary search. Totals are exact: they live in a budgeted 64-bit column while they fit and in
 * budgeted BigInteger entries after that.
 */
final class PrefixCounts implements AutoCloseable {

    /** An upper bound of the heap one BigInteger total keeps, with its list slot. */
    static final long BIG_ENTRY_BYTES = 96;
    static final int BIG_CHUNK = 1024;

    private final String purpose;
    private final MemoryBudget budget;
    private final LongColumn small;
    private final List<BigInteger> big = new ArrayList<>();
    private final List<MemoryBudget.Lease> bigLeases = new ArrayList<>();
    private BigInteger total = BigInteger.ZERO;

    PrefixCounts(String purpose, MemoryBudget budget) {
        this.purpose = purpose;
        this.budget = budget;
        this.small = new LongColumn(purpose, budget);
    }

    /** Adds the next row's count; the stored value is the total up to and including it. */
    void add(BigInteger count) {
        total = total.add(count);
        if (big.isEmpty() && total.bitLength() <= 63) {
            small.add(total.longValue());
            return;
        }
        if (big.size() % BIG_CHUNK == 0) {
            bigLeases.add(budget.reserve(BIG_ENTRY_BYTES * BIG_CHUNK, purpose));
        }
        big.add(total);
    }

    BigInteger total() {
        return total;
    }

    long entries() {
        return small.size() + big.size();
    }

    BigInteger get(long index) {
        return index < small.size() ? BigInteger.valueOf(small.get(index)) : big.get((int) (index - small.size()));
    }

    /** The index of the first entry whose total exceeds {@code rank}: the row that expands into that rank. */
    long rowOf(BigInteger rank) {
        long low = 0;
        long high = entries() - 1;
        while (low < high) {
            long mid = (low + high) >>> 1;
            if (get(mid).compareTo(rank) > 0) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }

    /** The total before entry {@code index}. */
    BigInteger before(long index) {
        return index == 0 ? BigInteger.ZERO : get(index - 1);
    }

    long reservedBytes() {
        return small.reservedBytes() + bigLeases.stream().mapToLong(MemoryBudget.Lease::bytes).sum();
    }

    @Override
    public void close() {
        small.close();
        big.clear();
        bigLeases.forEach(MemoryBudget.Lease::close);
        bigLeases.clear();
    }
}
