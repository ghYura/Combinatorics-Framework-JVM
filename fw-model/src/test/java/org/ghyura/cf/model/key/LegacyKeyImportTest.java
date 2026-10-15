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

package org.ghyura.cf.model.key;

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.key.LegacyKeyImport.ColumnKind;
import org.ghyura.cf.model.key.LegacyKeyImport.ImportedCell;
import org.ghyura.cf.model.key.LegacyKeyImport.LegacyKeyTier;
import org.junit.jupiter.api.Test;

import static org.ghyura.cf.model.TestSupport.expectCode;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gate A02, byte-migration.xml acceptance case 6: the null sentinel is imported per encoding, and an empty
 * row, an absent optional and the zero key remain three different things.
 */
class LegacyKeyImportTest {

    private static final SourceLocation WHERE = SourceLocation.of("legacy-dump.csv", 8);

    private static ImportedCell importCell(ColumnKind kind, LegacyKeyTier tier, int[] labels, KeyEncoding target) {
        return LegacyKeyImport.importCell(kind, tier, labels, target, WHERE);
    }

    @Test
    void sentinelsAreMinimumValuesOfTheLegacyWidths() {
        assertEquals(-128, LegacyKeyTier.BYTE.nullSentinel());
        assertEquals(-32768, LegacyKeyTier.WIDE.nullSentinel());
        assertEquals(KeyEncoding.SIGNED_BYTE, LegacyKeyTier.BYTE.encoding());
        assertEquals(KeyEncoding.PACKED_LEGACY16, LegacyKeyTier.WIDE.encoding());
    }

    @Test
    void aLegacyByteTierNullElementIsDroppedAndCounted() {
        for (KeyEncoding target : KeyEncoding.values()) {
            ImportedCell cell = importCell(ColumnKind.OPTIONAL, LegacyKeyTier.BYTE, new int[]{1, -128, 2}, target);
            assertEquals(SelectionState.PRESENT, cell.state());
            assertArrayEquals(new int[]{1, 2}, cell.row().labels());
            assertEquals(1, cell.droppedNullElements());
            assertEquals(target, cell.row().encoding());
        }
    }

    @Test
    void aLegacyWideTierNullElementIsDroppedAndMinus128SurvivesWhenPacked() {
        ImportedCell cell = importCell(ColumnKind.OPTIONAL, LegacyKeyTier.WIDE, new int[]{1, -32768, -128, 2},
                KeyEncoding.PACKED_LEGACY16);
        assertArrayEquals(new int[]{1, -128, 2}, cell.row().labels());
        assertEquals(1, cell.droppedNullElements());
    }

    @Test
    void minus128FromAWideArrayCannotBeSilentlyReinterpretedAsCompact() {
        expectCode(ErrorCode.KEY_LABEL_RESERVED,
                () -> importCell(ColumnKind.OPTIONAL, LegacyKeyTier.WIDE, new int[]{1, -128}, KeyEncoding.SIGNED_BYTE));
        expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE,
                () -> importCell(ColumnKind.OPTIONAL, LegacyKeyTier.WIDE, new int[]{300}, KeyEncoding.SIGNED_BYTE));
    }

    @Test
    void labelsOutsideTheLegacyWidthAreRejectedBeforeTheTargetIsConsulted() {
        expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE,
                () -> importCell(ColumnKind.OPTIONAL, LegacyKeyTier.BYTE, new int[]{128}, KeyEncoding.PACKED_LEGACY16));
        expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE,
                () -> importCell(ColumnKind.OPTIONAL, LegacyKeyTier.WIDE, new int[]{32768}, KeyEncoding.PACKED_LEGACY16));
        // -32768 in a byte-tier array is not its sentinel (-128 is), so it is just an impossible label
        expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE,
                () -> importCell(ColumnKind.OPTIONAL, LegacyKeyTier.BYTE, new int[]{-32768}, KeyEncoding.PACKED_LEGACY16));
    }

    @Test
    void aNullColumnMeansBaselineForMandatoryDeltasAndAbsenceForOptionals() {
        for (LegacyKeyTier tier : LegacyKeyTier.values()) {
            ImportedCell mandatory = importCell(ColumnKind.MANDATORY_DELTA, tier, null, KeyEncoding.PACKED_LEGACY16);
            assertEquals(SelectionState.BASELINE_REFERENCE, mandatory.state());
            assertNull(mandatory.row());

            ImportedCell optional = importCell(ColumnKind.OPTIONAL, tier, null, KeyEncoding.PACKED_LEGACY16);
            assertEquals(SelectionState.ABSENT, optional.state());
            assertNull(optional.row());
        }
    }

    @Test
    void anEmptyLegacyArrayFollowsTheSameColumnConvention() {
        assertEquals(SelectionState.BASELINE_REFERENCE,
                importCell(ColumnKind.MANDATORY_DELTA, LegacyKeyTier.WIDE, new int[0], KeyEncoding.PACKED_LEGACY16).state());
        assertEquals(SelectionState.ABSENT,
                importCell(ColumnKind.OPTIONAL, LegacyKeyTier.WIDE, new int[0], KeyEncoding.PACKED_LEGACY16).state());
    }

    @Test
    void absentPresentEmptyAndZeroKeyAreThreeDistinctImportResults() {
        ImportedCell absent = importCell(ColumnKind.OPTIONAL, LegacyKeyTier.WIDE, null, KeyEncoding.PACKED_LEGACY16);
        // an array that existed but held only NULL elements renders nothing, yet it was a value
        ImportedCell presentEmpty = importCell(ColumnKind.OPTIONAL, LegacyKeyTier.WIDE, new int[]{-32768},
                KeyEncoding.PACKED_LEGACY16);
        ImportedCell zeroKey = importCell(ColumnKind.OPTIONAL, LegacyKeyTier.WIDE, new int[]{0}, KeyEncoding.PACKED_LEGACY16);

        assertEquals(SelectionState.ABSENT, absent.state());
        assertEquals(SelectionState.PRESENT, presentEmpty.state());
        assertEquals(SelectionState.PRESENT, zeroKey.state());
        assertEquals(0, presentEmpty.row().size());
        assertEquals(1, presentEmpty.droppedNullElements());
        assertEquals(1, zeroKey.row().size());
        assertEquals(0, zeroKey.row().label(0));
        assertNotEquals(presentEmpty.row(), zeroKey.row());
        assertNotEquals(absent, presentEmpty);
    }

    @Test
    void importedCellEnforcesThatARowExistsExactlyWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new ImportedCell(SelectionState.PRESENT, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ImportedCell(SelectionState.ABSENT, KeyRow.empty(KeyEncoding.SIGNED_BYTE), 0));
        assertThrows(IllegalArgumentException.class, () -> new ImportedCell(SelectionState.ABSENT, null, -1));
    }
}
