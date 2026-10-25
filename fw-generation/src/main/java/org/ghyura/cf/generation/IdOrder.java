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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.LongColumn;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.util.SortedMap;

/**
 * A table as the legacy later passes read it: SheetWorker loaded fw_ into a map by combi_id (JavaIntermediateTableStore
 * .readFwAsMap: the last row with an id wins) and walked the ids upwards, skipping the missing ones. A table whose
 * ids already ascend is its own id order; otherwise the order is built once into a budgeted column of ranks.
 */
final class IdOrder implements RowSource, AutoCloseable {

    /** An upper bound of the temporary sort arrays per row: two longs and a long of merge space. */
    static final long SORT_BYTES_PER_ROW = 24;

    private final RowSource source;
    private final LongColumn ranks;

    private IdOrder(RowSource source, LongColumn ranks) {
        this.source = source;
        this.ranks = ranks;
    }

    /**
     * @throws FrameworkException RESOURCE_LIMIT when the order does not fit the budget, RUN_CANCELLED when cancelled
     */
    static RowSource of(RowSource source, MemoryBudget budget, String purpose, Cancellation cancellation, Arena arena) {
        if (source.idsAscending()) {
            return source;
        }
        BigInteger size = source.size();
        if (size.bitLength() > 31) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, purpose + ": a table of " + size + " rows whose "
                    + "ids do not ascend cannot be put in id order in one array", SourceLocation.NONE, "purpose", purpose));
        }
        int n = size.intValue();
        long[] ids;
        long[] order;
        MemoryBudget.Lease scratch = budget.reserve(64 + SORT_BYTES_PER_ROW * n, purpose + " (sorting by id)");
        try {
            ids = new long[n];
            order = new long[n];
            try (RowCursor rows = source.open(cancellation)) {
                for (int i = 0; i < n; i++) {
                    Row row = rows.next();
                    if (row.id().bitLength() > 63) {
                        throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, purpose + ": id " + row.id()
                                + " is beyond 2^63 - 1", SourceLocation.NONE, "purpose", purpose));
                    }
                    ids[i] = row.id().longValue();
                    order[i] = i;
                }
            }
            sort(order, ids, new long[n]);
            LongColumn column = new LongColumn(purpose + " (id order)", budget);
            IdOrder result = arena.own(new IdOrder(source, column));
            for (int i = 0; i < n; i++) {
                // equal ids sort by rank, so the last of a run of equal ids is the row the map kept
                if (i == n - 1 || ids[(int) order[i + 1]] != ids[(int) order[i]]) {
                    column.add(order[i]);
                }
            }
            return result;
        } finally {
            scratch.close();
        }
    }

    RowSource source() {
        return source;
    }

    /** A stable merge sort of ranks by id (ties stay in rank order). */
    private static void sort(long[] order, long[] ids, long[] space) {
        int n = order.length;
        for (int width = 1; width < n; width *= 2) {
            for (int low = 0; low < n - width; low += 2 * width) {
                int mid = low + width;
                int high = Math.min(low + 2 * width, n);
                int i = low;
                int j = mid;
                int k = low;
                while (i < mid && j < high) {
                    space[k++] = ids[(int) order[j]] < ids[(int) order[i]] ? order[j++] : order[i++];
                }
                while (i < mid) {
                    space[k++] = order[i++];
                }
                while (j < high) {
                    space[k++] = order[j++];
                }
                System.arraycopy(space, low, order, low, high - low);
            }
        }
    }

    @Override
    public BigInteger size() {
        return BigInteger.valueOf(ranks.size());
    }

    @Override
    public Row at(BigInteger rank) {
        RowSource.checkRank(rank, size());
        return source.at(BigInteger.valueOf(ranks.get(rank.longValue())));
    }

    @Override
    public int uniformLength() {
        return ranks.size() == 0 ? 0 : source.uniformLength();
    }

    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        return source.lengths();
    }

    @Override
    public String formula() {
        return source.formula();
    }

    @Override
    public boolean idsAscending() {
        return true;
    }

    @Override
    public boolean stored() {
        return source.stored();
    }

    @Override
    public long reservedBytes() {
        return ranks.reservedBytes();
    }

    @Override
    public void close() {
        ranks.close();
    }
}
