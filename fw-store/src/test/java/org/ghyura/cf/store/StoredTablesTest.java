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

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stored tables and index columns: chunked byte blocks reserved from the budget before they are allocated. */
class StoredTablesTest {

    private static final KeyEncoding ENCODING = KeyEncoding.PACKED_LEGACY16;

    private static KeyRow keys(int... labels) {
        return KeyRow.of(ENCODING, labels);
    }

    private static BigInteger big(long value) {
        return BigInteger.valueOf(value);
    }

    @Test
    void rowsComeBackByRankAndThroughTheCursorAcrossPages() {
        MemoryBudget budget = new MemoryBudget(1L << 26);
        try (RowBlocks table = new RowBlocks("test table", ENCODING, budget)) {
            List<Row> written = new ArrayList<>();
            for (int i = 1; i <= 5000; i++) {
                int[] labels = new int[1 + i % 7];
                for (int k = 0; k < labels.length; k++) {
                    labels[k] = 3 + (i * 31 + k) % 900;
                }
                Row row = new Row(big(i), big(i / 2), keys(labels));
                table.append(row);
                written.add(row);
            }
            assertEquals(5000, table.count());
            assertEquals(big(5000), table.size());
            for (int i = 0; i < 5000; i += 97) {
                assertEquals(written.get(i), table.at(big(i)));
            }
            List<Row> read = new ArrayList<>();
            try (RowCursor cursor = table.open(Cancellation.NONE)) {
                while (cursor.hasNext()) {
                    read.add(cursor.next());
                }
            }
            assertEquals(written, read);
            assertEquals(-1, table.uniformLength());
            assertTrue(table.idsAscending());
            assertTrue(table.stored());
            assertEquals(budget.reservedBytes(), table.reservedBytes());
            assertTrue(table.reservedBytes() >= 5000L * 20, "the ids, parents and ends are reserved with the keys");
        }
    }

    @Test
    void aPageIsReservedBeforeItIsAllocatedAndARefusedRowLeavesTheTableAsItWas() {
        long firstPage = RowBlocks.PAGE_OVERHEAD + 20L * RowBlocks.FIRST_PAGE_ROWS + RowBlocks.FIRST_PAGE_BYTES;
        MemoryBudget budget = new MemoryBudget(firstPage);
        try (RowBlocks table = new RowBlocks("tight table", ENCODING, budget)) {
            for (int i = 1; i <= RowBlocks.FIRST_PAGE_ROWS; i++) {
                table.append(big(i), BigInteger.ZERO, keys(i + 2, i + 3));
            }
            assertEquals(firstPage, budget.reservedBytes());
            FrameworkException e = assertThrows(FrameworkException.class, () -> table.append(big(99), BigInteger.ZERO, keys(5)));
            assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
            assertEquals("tight table", e.diagnostics().getFirst().attributes().get("purpose"));
            assertEquals(RowBlocks.FIRST_PAGE_ROWS, table.count(), "nothing half-written");
            assertEquals(keys(RowBlocks.FIRST_PAGE_ROWS + 2, RowBlocks.FIRST_PAGE_ROWS + 3), table.at(big(RowBlocks.FIRST_PAGE_ROWS - 1)).keys());
            assertEquals(2, table.uniformLength());
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void aRowLongerThanAPageGetsAPageOfItsOwn() {
        MemoryBudget budget = new MemoryBudget(1L << 24);
        try (RowBlocks table = new RowBlocks("wide rows", ENCODING, budget)) {
            int[] wide = new int[50_000];
            for (int i = 0; i < wide.length; i++) {
                wide[i] = 3 + i % 1000;
            }
            table.append(big(1), BigInteger.ZERO, keys(1, 2));
            table.append(big(2), big(1), keys(wide));
            table.append(big(3), BigInteger.ZERO, keys(7));
            assertEquals(keys(wide), table.at(big(1)).keys());
            assertEquals(keys(7), table.at(big(2)).keys());
            assertTrue(table.reservedBytes() >= 100_000, "the wide row's octets are reserved");
        }
    }

    @Test
    void theContentIndexKeepsTheFirstRowOfEveryContentAndReleasesItsReservation() {
        MemoryBudget budget = new MemoryBudget(1L << 24);
        try (RowBlocks table = new RowBlocks("distinct rows", ENCODING, budget)) {
            table.indexContent();
            long rows = 0;
            for (int i = 1; i <= 3000; i++) {
                if (table.appendIfAbsent(big(i), big(i * 10L), keys(3 + i % 400, 3 + i % 3))) {
                    rows++;
                }
            }
            assertEquals(1200, rows, "i mod 400 and i mod 3 repeat together every 1200");
            assertEquals(new Row(big(1), big(10), keys(4, 4)), table.at(BigInteger.ZERO));
            assertFalse(table.appendIfAbsent(big(9999), BigInteger.ZERO, keys(4, 4)));
            assertTrue(table.appendIfAbsent(big(9999), BigInteger.ZERO, keys(4)), "a prefix is other content");
            long withIndex = budget.reservedBytes();
            table.dropContentIndex();
            assertTrue(budget.reservedBytes() < withIndex);
            assertEquals(budget.reservedBytes(), table.reservedBytes());
            assertThrows(IllegalStateException.class, () -> table.appendIfAbsent(big(10_000), BigInteger.ZERO, keys(5)));
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void closingReturnsEverythingOnceAndAClosedTableRefusesRows() {
        MemoryBudget budget = new MemoryBudget(1L << 20);
        RowBlocks table = new RowBlocks("closed table", ENCODING, budget);
        table.append(big(1), BigInteger.ZERO, keys(3));
        assertTrue(budget.reservedBytes() > 0);
        table.close();
        table.close();
        assertEquals(0, budget.reservedBytes());
        assertThrows(IllegalStateException.class, () -> table.append(big(2), BigInteger.ZERO, keys(3)));
    }

    @Test
    void idsBeyondTheLegacyBigintRangeAreRefusedAndIdsThatDoNotAscendAreNoticed() {
        MemoryBudget budget = new MemoryBudget(1L << 20);
        try (RowBlocks table = new RowBlocks("ids", ENCODING, budget)) {
            FrameworkException e = assertThrows(FrameworkException.class,
                    () -> table.append(BigInteger.ONE.shiftLeft(63), BigInteger.ZERO, keys(3)));
            assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
            table.append(big(Long.MAX_VALUE), BigInteger.ZERO, keys(3));
            assertTrue(table.idsAscending());
            table.append(big(5), BigInteger.ZERO, keys(3));
            assertFalse(table.idsAscending());
            assertThrows(IllegalArgumentException.class, () -> table.append(BigInteger.ZERO, BigInteger.ZERO, keys(3)));
            assertThrows(IllegalArgumentException.class, () -> table.append(big(9), BigInteger.ZERO, KeyRow.of(KeyEncoding.SIGNED_BYTE, 3)));
        }
    }

    @Test
    void anIndexColumnReservesEachChunkBeforeAllocatingIt() {
        long chunk = LongColumn.CHUNK_OVERHEAD + 8L * LongColumn.CHUNK;
        MemoryBudget budget = new MemoryBudget(2 * chunk);
        try (LongColumn column = new LongColumn("running totals", budget)) {
            for (long i = 0; i < 2L * LongColumn.CHUNK; i++) {
                column.add(i * 3);
            }
            assertEquals(2 * chunk, budget.reservedBytes());
            assertEquals(3L * (LongColumn.CHUNK + 5), column.get(LongColumn.CHUNK + 5));
            column.set(7, -1);
            assertEquals(-1, column.get(7));
            FrameworkException e = assertThrows(FrameworkException.class, () -> column.add(1));
            assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
            assertEquals(2L * LongColumn.CHUNK, column.size());
        }
        assertEquals(0, budget.reservedBytes());
    }
}
