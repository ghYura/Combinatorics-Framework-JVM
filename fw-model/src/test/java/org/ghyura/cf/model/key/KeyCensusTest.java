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

import static org.ghyura.cf.model.TestSupport.expectCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A02, byte-migration.xml acceptance case 3: 255 compact labels fit, a 256th fails the strict census,
 * and AUTO_PRESERVE keeps the larger valid original labels instead of relabelling.
 */
class KeyCensusTest {

    private static final SourceLocation WHERE = SourceLocation.of("scenario.json");

    @Test
    void anEmptyCensusFitsEverythingAndPrefersTheCompactEncoding() {
        KeyCensus none = new KeyCensus();
        assertEquals(KeyEncoding.SIGNED_BYTE, none.select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
        assertEquals(KeyEncoding.SIGNED_BYTE, none.select(KeyEncodingPolicy.SIGNED_BYTE, WHERE));
        assertEquals(KeyEncoding.PACKED_LEGACY16, none.select(KeyEncodingPolicy.PACKED_LEGACY16, WHERE));
    }

    @Test
    void exactly255CompactLabelsPassStrictModeAnd256DoNot() {
        KeyCensus full = new KeyCensus().addRange(-127, 255);
        assertEquals(255, full.count());
        assertEquals(KeyEncoding.SIGNED_BYTE, full.select(KeyEncodingPolicy.SIGNED_BYTE, WHERE));
        assertEquals(KeyEncoding.SIGNED_BYTE, full.select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));

        KeyCensus overflow = new KeyCensus().addRange(-127, 256);
        FrameworkException failure = expectCode(ErrorCode.KEY_CENSUS_OVERFLOW,
                () -> overflow.select(KeyEncodingPolicy.SIGNED_BYTE, WHERE));
        assertEquals("256", failure.primary().attributes().get("labelCount"));
        assertEquals("128", failure.primary().attributes().get("max"));
        assertEquals("127", failure.primary().attributes().get("allowedMax"));
        assertEquals(WHERE, failure.primary().location());
    }

    @Test
    void theLegacyIdentityNumberingWithManySheetsOverflowsStrictByteModeEarly() {
        // S+1..S+C with S=3 starts at label 4: C=124 (labels 4..127) is the last count that fits, C=125 is not
        assertEquals(KeyEncoding.SIGNED_BYTE, new KeyCensus().addRange(4, 124).select(KeyEncodingPolicy.SIGNED_BYTE, WHERE));
        expectCode(ErrorCode.KEY_CENSUS_OVERFLOW, () -> new KeyCensus().addRange(4, 125).select(KeyEncodingPolicy.SIGNED_BYTE, WHERE));
    }

    @Test
    void autoPreserveKeepsLargerValidOriginalLabelsInsteadOfRelabelling() {
        KeyCensus census = new KeyCensus().addRange(1, 3).addRange(4, 300); // 3 sheets, 300 cells: labels 1..303
        KeyEncoding chosen = census.select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE);
        assertEquals(KeyEncoding.PACKED_LEGACY16, chosen);
        // the original labels are representable as they are: no remapping happened
        KeyRow row = KeyRow.of(chosen, 4, 303);
        assertEquals(303, row.label(1));
    }

    @Test
    void autoPreserveSwitchesAtTheFirstLabelOutsideTheCompactRange() {
        assertEquals(KeyEncoding.SIGNED_BYTE, new KeyCensus().add(127).add(-127).select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
        assertEquals(KeyEncoding.PACKED_LEGACY16, new KeyCensus().add(127).add(128).select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
        assertEquals(KeyEncoding.PACKED_LEGACY16, new KeyCensus().add(-127).add(-129).select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
    }

    @Test
    void minus128IsReservedInCompactModeButALegitimateLabelWhenPacked() {
        KeyCensus census = new KeyCensus().add(-128);
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> census.select(KeyEncodingPolicy.SIGNED_BYTE, WHERE));
        // AUTO does not fail: -128 is an ordinary label at 16 bits, so the plan simply cannot be compact
        assertEquals(KeyEncoding.PACKED_LEGACY16, census.select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
        assertEquals(KeyEncoding.PACKED_LEGACY16, census.select(KeyEncodingPolicy.PACKED_LEGACY16, WHERE));
    }

    @Test
    void labelsBeyondTheLegacyWideDomainFailEveryPolicy() {
        expectCode(ErrorCode.KEY_LABEL_RESERVED,
                () -> new KeyCensus().add(-32768).select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
        expectCode(ErrorCode.KEY_CENSUS_OVERFLOW,
                () -> new KeyCensus().add(32768).select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
        expectCode(ErrorCode.KEY_CENSUS_OVERFLOW,
                () -> new KeyCensus().add(32768).select(KeyEncodingPolicy.PACKED_LEGACY16, WHERE));
        // the legacy counter wrapped silently past 32767; here S+C = 32768 labels is an error, never a wrap
        expectCode(ErrorCode.KEY_CENSUS_OVERFLOW,
                () -> new KeyCensus().addRange(1, 32768).select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
        assertEquals(KeyEncoding.PACKED_LEGACY16,
                new KeyCensus().addRange(1, 32767).select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
    }

    @Test
    void packedPolicyIsHonouredEvenWhenEverythingWouldFitCompactly() {
        assertEquals(KeyEncoding.PACKED_LEGACY16,
                new KeyCensus().addRange(1, 10).select(KeyEncodingPolicy.PACKED_LEGACY16, WHERE));
    }

    @Test
    void rangesAndSinglesAccumulateTogether() {
        KeyCensus census = new KeyCensus().add(500).addRange(-3, 4).add(2);
        assertEquals(6, census.count());
        assertEquals(-3, census.min());
        assertEquals(500, census.max());
        assertTrue(census.fits(KeyEncoding.PACKED_LEGACY16));
        assertFalse(census.fits(KeyEncoding.SIGNED_BYTE));
        assertEquals(6, census.addRange(9, 0).count());
    }

    @Test
    void rangeArgumentsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new KeyCensus().addRange(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new KeyCensus().addRange(Integer.MAX_VALUE, 2));
    }

    private static void assertUnchanged(KeyCensus census, long count, int min, int max) {
        assertEquals(new KeyCensus.Snapshot(count, min, max), census.snapshot());
    }

    /** Review finding BM03: firstLabel + labelCount - 1 overflowed long and admitted an impossible range. */
    @Test
    void rangesThatOverflowAreRefusedBeforeAnyFieldChanges() {
        KeyCensus census = new KeyCensus().addRange(3, 5);
        for (int first : new int[]{2, 0, -1, -2_000_000_000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> census.addRange(first, Long.MAX_VALUE), "first " + first);
            assertUnchanged(census, 5, 3, 7);
        }
        // the reviewer's exact call can no longer select an encoding
        KeyCensus reviewer = new KeyCensus();
        assertThrows(IllegalArgumentException.class, () -> reviewer.addRange(2, Long.MAX_VALUE));
        assertEquals(0, reviewer.count());
        assertEquals(KeyEncoding.SIGNED_BYTE, reviewer.select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE), "still empty");

        // one past the int domain is refused, the whole domain is accepted
        assertThrows(IllegalArgumentException.class, () -> census.addRange(Integer.MIN_VALUE, (1L << 32) + 1));
        assertThrows(IllegalArgumentException.class, () -> census.addRange(Integer.MAX_VALUE - 1, 3));
        assertUnchanged(census, 5, 3, 7);
        census.addRange(Integer.MIN_VALUE, 1L << 32);
        assertUnchanged(census, 5 + (1L << 32), Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertThrows(FrameworkException.class, () -> census.select(KeyEncodingPolicy.AUTO_PRESERVE, WHERE));
        assertEquals(1, new KeyCensus().addRange(Integer.MAX_VALUE, 1).count());
    }

    @Test
    void theCountCannotOverflowEitherThroughSinglesOrThroughRanges() {
        KeyCensus nearlyFull = new KeyCensus(new KeyCensus.Snapshot(Long.MAX_VALUE - 3, 1, 5));
        nearlyFull.add(9).add(-1);
        assertUnchanged(nearlyFull, Long.MAX_VALUE - 1, -1, 9);
        assertThrows(ArithmeticException.class, () -> nearlyFull.addRange(100, 2));
        assertUnchanged(nearlyFull, Long.MAX_VALUE - 1, -1, 9);
        nearlyFull.addRange(100, 1);
        assertUnchanged(nearlyFull, Long.MAX_VALUE, -1, 100);
        assertThrows(ArithmeticException.class, () -> nearlyFull.add(-500));
        assertThrows(ArithmeticException.class, () -> nearlyFull.addRange(-500, 1));
        assertUnchanged(nearlyFull, Long.MAX_VALUE, -1, 100);
        assertEquals(nearlyFull.snapshot(), nearlyFull.addRange(-500, 0).snapshot(), "an empty range adds nothing");
    }

    @Test
    void aSnapshotIsAnImmutableValueThatDoesNotFollowLaterAdditions() {
        KeyCensus census = new KeyCensus().addRange(1, 3);
        KeyCensus.Snapshot before = census.snapshot();
        census.add(32_767);
        assertEquals(new KeyCensus.Snapshot(3, 1, 3), before);
        assertTrue(before.fits(KeyEncoding.SIGNED_BYTE));
        assertFalse(census.fits(KeyEncoding.SIGNED_BYTE));
        assertTrue(KeyCensus.Snapshot.class.isRecord());
        assertTrue(new KeyCensus().snapshot().fits(KeyEncoding.SIGNED_BYTE), "an empty census fits everything");
        assertThrows(IllegalArgumentException.class, () -> new KeyCensus.Snapshot(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new KeyCensus.Snapshot(2, 5, 4));
        assertThrows(IllegalArgumentException.class, () -> new KeyCensus.Snapshot(0, 1, 1));
    }

    @Test
    void policyNamesParseExactlyAsWrittenInTheProperties() {
        assertEquals(KeyEncodingPolicy.AUTO_PRESERVE, KeyEncodingPolicy.parse("AUTO_PRESERVE").orElseThrow());
        assertEquals(KeyEncodingPolicy.SIGNED_BYTE, KeyEncodingPolicy.parse("SIGNED_BYTE").orElseThrow());
        assertEquals(KeyEncodingPolicy.PACKED_LEGACY16, KeyEncodingPolicy.parse("PACKED_LEGACY16").orElseThrow());
        assertTrue(KeyEncodingPolicy.parse("auto_preserve").isEmpty());
        assertTrue(KeyEncodingPolicy.parse(" AUTO_PRESERVE").isEmpty());
        assertTrue(KeyEncodingPolicy.parse("").isEmpty());
    }
}
