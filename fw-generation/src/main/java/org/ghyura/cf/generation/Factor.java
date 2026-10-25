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

import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.LongColumn;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.RowBlocks;

import java.math.BigInteger;

/**
 * One column of the candidates as the assembly reads it: the sheet's final table, or (cf.assembly.distinct) its first
 * occurrences by content, which is exactly what the legacy de-duplication of fw_final and fw_opt kept, because two
 * product rows have equal content exactly when each of their sheet rows has. The original rank of every kept row is
 * kept too, so the legacy display ids (the combination ids of fw_final and fw_opt) can be computed.
 */
final class Factor {
    final String sheet;
    final int key;
    final boolean optional;
    final SourceLocation where;
    final String concatenator;
    private final RowSource table;
    private final RowSource original;
    private final LongColumn originalRanks;

    private Factor(String sheet, int key, boolean optional, SourceLocation where, String concatenator, RowSource table,
                   RowSource original, LongColumn originalRanks) {
        this.sheet = sheet;
        this.key = key;
        this.optional = optional;
        this.where = where;
        this.concatenator = concatenator;
        this.table = table;
        this.original = original;
        this.originalRanks = originalRanks;
    }

    /** The sheet's table as it is. */
    static Factor of(String sheet, int key, boolean optional, SourceLocation where, String concatenator, RowSource rows) {
        return new Factor(sheet, key, optional, where, concatenator, rows, rows, null);
    }

    /**
     * The first occurrences of the sheet's rows, stored and budgeted; the arena owns the copy and the rank column.
     * A table that cannot repeat a row (empty or one row) is used as it is.
     */
    static Factor distinct(String sheet, int key, boolean optional, SourceLocation where, String concatenator, RowSource rows,
                           MemoryBudget budget, Cancellation cancellation, Arena arena) {
        if (RowSources.distinct(rows)) {
            return of(sheet, key, optional, where, concatenator, rows);
        }
        String purpose = "the first occurrences of sheet " + sheet + " for the de-duplicated candidates";
        RowBlocks kept = arena.own(new RowBlocks(purpose, rows.at(BigInteger.ZERO).keys().encoding(), budget));
        LongColumn ranks = arena.own(new LongColumn("the original ranks of " + purpose, budget));
        kept.indexContent();
        long rank = 0;
        try (RowCursor cursor = rows.open(cancellation)) {
            while (cursor.hasNext()) {
                Row row = cursor.next();
                if (kept.appendIfAbsent(row.id(), row.parent(), row.keys())) {
                    ranks.add(rank);
                }
                rank++;
            }
        }
        kept.dropContentIndex();
        if (kept.count() == rank) {
            arena.release(kept);
            arena.release(ranks);
            return of(sheet, key, optional, where, concatenator, rows);
        }
        return new Factor(sheet, key, optional, where, concatenator, kept, rows, ranks);
    }

    BigInteger size() {
        return table.size();
    }

    /** The size of the sheet's table before de-duplication. */
    BigInteger originalSize() {
        return original.size();
    }

    Row row(BigInteger rank) {
        return table.at(rank);
    }

    /** The rank in the sheet's table of the row at {@code rank} here. */
    BigInteger originalRank(BigInteger rank) {
        return originalRanks == null ? rank : BigInteger.valueOf(originalRanks.get(rank.longValueExact()));
    }

    /** The legacy MAX(combi_id) of the sheet's table (what FinalTableAssembler multiplied for its cut-off). */
    BigInteger maxId(Cancellation cancellation) {
        if (original.size().signum() == 0) {
            return BigInteger.ZERO;
        }
        if (original.idsAscending()) {
            return original.at(original.size().subtract(BigInteger.ONE)).id();
        }
        BigInteger max = BigInteger.ZERO;
        try (RowCursor cursor = original.open(cancellation)) {
            while (cursor.hasNext()) {
                max = max.max(cursor.next().id());
            }
        }
        return max;
    }

    boolean deduplicated() {
        return originalRanks != null;
    }
}
