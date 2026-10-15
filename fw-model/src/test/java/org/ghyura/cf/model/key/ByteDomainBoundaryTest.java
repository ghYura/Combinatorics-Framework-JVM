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
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

/** All 256 signed byte labels, their neighbours and explicit legacy NULL boundaries. */
class ByteDomainBoundaryTest {
    private static final SourceLocation HERE = SourceLocation.of("byte-boundaries.json");

    @Test
    void theEntireSignedByteDomainAndBothNeighboursSurviveAutomaticEncodingAndWireFrames() {
        for (int label = Byte.MIN_VALUE - 1; label <= Byte.MAX_VALUE + 1; label++) {
            KeyEncoding encoding = new KeyCensus().add(label).select(KeyEncodingPolicy.AUTO_PRESERVE, HERE);
            assertEquals(label >= -127 && label <= 127 ? KeyEncoding.SIGNED_BYTE : KeyEncoding.PACKED_LEGACY16, encoding);
            KeyRow row = KeyRow.of(encoding, label);
            assertEquals(label, KeyWire.decode(KeyWire.encode(row)).label(0));
        }
        int[] all = IntStream.rangeClosed(Byte.MIN_VALUE, Byte.MAX_VALUE).toArray();
        KeyEncoding encoding = new KeyCensus().addRange(Byte.MIN_VALUE, all.length).select(KeyEncodingPolicy.AUTO_PRESERVE, HERE);
        assertEquals(KeyEncoding.PACKED_LEGACY16, encoding, "-128 is data, so the whole row uses the wider byte-backed encoding");
        KeyRow row = KeyRow.of(encoding, all);
        assertEquals(256, row.size());
        assertEquals(512, row.payload().length);
        assertArrayEquals(all, KeyWire.decode(KeyWire.encode(row)).labels());
    }

    @Test
    void rejectedCompactWritesCannotWrapAliasOrPartiallyOverwriteAnExistingRow() {
        KeyRow before = KeyRow.of(KeyEncoding.SIGNED_BYTE, -127, 0, 127);
        for (int label : new int[]{-129, -128, 128, 255, 256}) {
            byte[] bytes = before.payload();
            FrameworkException failure = assertThrows(FrameworkException.class,
                    () -> KeyEncoding.SIGNED_BYTE.put(bytes, 1, label, HERE));
            assertEquals(label == -128 ? ErrorCode.KEY_LABEL_RESERVED : ErrorCode.KEY_LABEL_OUT_OF_RANGE, failure.primary().code());
            assertEquals(HERE, failure.primary().location());
            assertArrayEquals(before.payload(), bytes);
        }
    }

    @Test
    void readerNullWorkaroundsApplyOnlyToTheDeclaredLegacyTierAndNotToNativeData() {
        int[] all = IntStream.rangeClosed(Byte.MIN_VALUE, Byte.MAX_VALUE).toArray();
        var wide = LegacyKeyImport.importCell(LegacyKeyImport.ColumnKind.OPTIONAL,
                LegacyKeyImport.LegacyKeyTier.WIDE, all, KeyEncoding.PACKED_LEGACY16, HERE);
        assertEquals(SelectionState.PRESENT, wide.state());
        assertEquals(0, wide.droppedNullElements());
        assertArrayEquals(all, wide.row().labels(), "-128, -1 and 0 must not disappear from wide data");
        var compact = LegacyKeyImport.importCell(LegacyKeyImport.ColumnKind.OPTIONAL,
                LegacyKeyImport.LegacyKeyTier.BYTE, all, KeyEncoding.PACKED_LEGACY16, HERE);
        assertEquals(1, compact.droppedNullElements());
        assertArrayEquals(Arrays.copyOfRange(all, 1, all.length), compact.row().labels());
        var nullElement = LegacyKeyImport.importCell(LegacyKeyImport.ColumnKind.OPTIONAL,
                LegacyKeyImport.LegacyKeyTier.WIDE, new int[]{-32768}, KeyEncoding.PACKED_LEGACY16, HERE);
        assertEquals(SelectionState.PRESENT, nullElement.state());
        assertTrue(nullElement.row().isEmpty());
        assertEquals(1, nullElement.droppedNullElements());
        for (int[] missing : new int[][]{null, new int[0]}) {
            assertEquals(SelectionState.ABSENT, LegacyKeyImport.importCell(LegacyKeyImport.ColumnKind.OPTIONAL,
                    LegacyKeyImport.LegacyKeyTier.WIDE, missing, KeyEncoding.PACKED_LEGACY16, HERE).state());
            assertEquals(SelectionState.BASELINE_REFERENCE, LegacyKeyImport.importCell(LegacyKeyImport.ColumnKind.MANDATORY_DELTA,
                    LegacyKeyImport.LegacyKeyTier.WIDE, missing, KeyEncoding.PACKED_LEGACY16, HERE).state());
        }
    }
}
