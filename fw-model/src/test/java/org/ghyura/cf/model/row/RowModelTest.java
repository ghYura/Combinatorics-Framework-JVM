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

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rows with their legacy provenance, and the cursor every row table has. */
class RowModelTest {

    private static final KeyRow KEYS = KeyRow.of(KeyEncoding.SIGNED_BYTE, 3, -4);

    /** A computed table of {@code size} rows whose id is the rank plus one. */
    private static RowSource counting(BigInteger size) {
        return new RowSource() {
            @Override
            public BigInteger size() {
                return size;
            }

            @Override
            public Row at(BigInteger rank) {
                RowSource.checkRank(rank, size);
                return new Row(rank.add(BigInteger.ONE), BigInteger.ZERO, KEYS);
            }

            @Override
            public int uniformLength() {
                return 2;
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
        };
    }

    @Test
    void aRowsIdStartsAtOneAndItsParentIsZeroOrAnEarlierRow() {
        Row row = new Row(BigInteger.TWO.pow(70), BigInteger.TEN, KEYS);
        assertTrue(row.hasParent());
        Row orphan = row.withoutParent();
        assertFalse(orphan.hasParent());
        assertEquals(row.id(), orphan.id());
        assertEquals(KEYS, orphan.keys());
        assertSame(orphan, orphan.withoutParent());
        assertThrows(IllegalArgumentException.class, () -> new Row(BigInteger.ZERO, BigInteger.ZERO, KEYS));
        assertThrows(IllegalArgumentException.class, () -> new Row(BigInteger.ONE, BigInteger.valueOf(-1), KEYS));
    }

    @Test
    void theCursorWalksARankRangeBeyondLongAndPollsCancellationEvery256Rows() {
        BigInteger size = BigInteger.TWO.pow(80);
        RowSource table = counting(size);
        BigInteger from = size.subtract(BigInteger.valueOf(600));
        AtomicInteger polls = new AtomicInteger();
        Cancellation counted = () -> {
            polls.incrementAndGet();
            return false;
        };
        try (RowCursor cursor = table.open(from, size, counted)) {
            int rows = 0;
            while (cursor.hasNext()) {
                assertEquals(from.add(BigInteger.valueOf(rows)), cursor.position());
                assertEquals(from.add(BigInteger.valueOf(rows + 1)), cursor.next().id());
                rows++;
            }
            assertEquals(600, rows);
            assertEquals(3, polls.get(), "before the first row, then after 256 and 512 rows");
            assertThrows(NoSuchElementException.class, cursor::next);
        }
        assertThrows(IndexOutOfBoundsException.class, () -> table.open(BigInteger.ONE, BigInteger.ZERO, Cancellation.NONE));
        assertThrows(IndexOutOfBoundsException.class, () -> table.open(BigInteger.ZERO, size.add(BigInteger.ONE), Cancellation.NONE));
        assertThrows(IndexOutOfBoundsException.class, () -> table.at(size));
    }

    @Test
    void aCancelledCursorStopsWithRunCancelled() {
        try (RowCursor cursor = counting(BigInteger.TEN).open(() -> true)) {
            FrameworkException e = assertThrows(FrameworkException.class, cursor::next);
            assertEquals(ErrorCode.RUN_CANCELLED, e.diagnostics().getFirst().code());
        }
    }

    @Test
    void theEmptyTableHasNoRows() {
        RowSource empty = RowSource.empty();
        assertEquals(BigInteger.ZERO, empty.size());
        assertEquals(0, empty.uniformLength());
        assertFalse(empty.open(Cancellation.NONE).hasNext());
        assertThrows(IndexOutOfBoundsException.class, () -> empty.at(BigInteger.ZERO));
    }
}
