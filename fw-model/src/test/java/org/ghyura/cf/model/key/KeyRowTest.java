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
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;

import static org.ghyura.cf.model.TestSupport.expectCode;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gate A02, byte-migration.xml acceptance cases 4 and 6: ordering, equality, hashing, ownership, text form. */
class KeyRowTest {

    private static final KeyEncoding BYTE = KeyEncoding.SIGNED_BYTE;
    private static final KeyEncoding WIDE = KeyEncoding.PACKED_LEGACY16;

    @Test
    void groupOrderIsDecodedSignedLexicographicThenLength() {
        // the exact rows of the legacy KeyCodecVerify section C, with the expected order stated exactly
        List<KeyRow> rows = new ArrayList<>(List.of(
                KeyRow.of(WIDE, 5, 1), KeyRow.of(WIDE, -2, 9), KeyRow.of(WIDE, 5),
                KeyRow.of(WIDE, 5, 1, 0), KeyRow.of(WIDE, -2)));
        rows.sort(KeyRow.LABEL_ORDER);
        assertEquals(List.of("[-2]", "[-2, 9]", "[5]", "[5, 1]", "[5, 1, 0]"),
                rows.stream().map(KeyRow::describe).toList());
    }

    @Test
    void rawUnsignedOctetOrderWouldBeWrongAcrossTheSignBoundary() {
        KeyRow minusOne = KeyRow.of(BYTE, -1);
        KeyRow one = KeyRow.of(BYTE, 1);
        assertTrue(KeyRow.LABEL_ORDER.compare(minusOne, one) < 0);
        // the naive comparison this test exists to rule out:
        assertTrue(Arrays.compareUnsigned(minusOne.payload(), one.payload()) > 0);

        KeyRow wideMinusOne = KeyRow.of(WIDE, -1);
        KeyRow wideOne = KeyRow.of(WIDE, 1);
        assertTrue(KeyRow.LABEL_ORDER.compare(wideMinusOne, wideOne) < 0);
        assertTrue(Arrays.compareUnsigned(wideMinusOne.payload(), wideOne.payload()) > 0);
    }

    @Test
    void groupOrderAgreesWithAnIndependentIntArrayOracleOnRandomRows() {
        SplittableRandom random = new SplittableRandom(20261001L);
        for (KeyEncoding encoding : KeyEncoding.values()) {
            int bound = Math.min(encoding.maxLabel(), 300);
            for (int i = 0; i < 3000; i++) {
                KeyRow a = randomRow(random, encoding, bound);
                KeyRow b = randomRow(random, encoding, bound);
                assertEquals(Integer.signum(Arrays.compare(a.labels(), b.labels())),
                        Integer.signum(KeyRow.LABEL_ORDER.compare(a, b)), a + " vs " + b);
            }
        }
    }

    @Test
    void groupOrderIgnoresTheEncodingWhileEqualityDoesNot() {
        KeyRow narrow = KeyRow.of(BYTE, 3, -4);
        KeyRow wide = KeyRow.of(WIDE, 3, -4);
        assertEquals(0, KeyRow.LABEL_ORDER.compare(narrow, wide));
        assertNotEquals(narrow, wide);
        assertEquals(2, new HashSet<>(List.of(narrow, wide)).size());
    }

    @Test
    void equalityAndHashCompareEncodingAndContent() {
        KeyRow a = KeyRow.of(WIDE, 1, -200, 300);
        KeyRow b = KeyRow.fromPayload(WIDE, a.payload());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, KeyRow.of(WIDE, 1, -200, 301));
        assertNotEquals(a, KeyRow.of(WIDE, 1, -200));
        Set<KeyRow> set = new HashSet<>(List.of(a));
        assertTrue(set.contains(b));
    }

    @Test
    void rowsOwnTheirOctets() {
        byte[] source = {1, 2, 3};
        KeyRow row = KeyRow.fromPayload(BYTE, source);
        source[0] = 99;
        assertArrayEquals(new int[]{1, 2, 3}, row.labels());

        byte[] leaked = row.payload();
        leaked[1] = 99;
        int[] leakedLabels = row.labels();
        leakedLabels[2] = 99;
        assertArrayEquals(new int[]{1, 2, 3}, row.labels());

        byte[] window = {9, 8, 1, 2, 7};
        assertArrayEquals(new int[]{1, 2}, KeyRow.fromPayload(BYTE, window, 2, 2, SourceLocation.NONE).labels());
    }

    @Test
    void anEmptyRowAndTheZeroLabelAreDifferentValues() {
        for (KeyEncoding encoding : KeyEncoding.values()) {
            KeyRow empty = KeyRow.empty(encoding);
            KeyRow zero = KeyRow.of(encoding, 0);
            assertTrue(empty.isEmpty());
            assertEquals(0, empty.size());
            assertFalse(zero.isEmpty());
            assertEquals(1, zero.size());
            assertEquals(0, zero.label(0));
            assertNotEquals(empty, zero);
            assertEquals(empty, KeyRow.of(encoding));
        }
    }

    @Test
    void concatAndSliceKeepTheEncoding() {
        KeyRow joined = KeyRow.of(WIDE, 1, -2).concat(KeyRow.of(WIDE, 300));
        assertEquals("[1, -2, 300]", joined.describe());
        assertEquals("[-2, 300]", joined.slice(1, 3).describe());
        assertEquals(KeyRow.empty(WIDE), joined.slice(2, 2));
        expectCode(ErrorCode.KEY_ENCODING_MISMATCH, () -> KeyRow.of(WIDE, 1).concat(KeyRow.of(BYTE, 1)));
        assertThrowsIndex(() -> joined.slice(2, 4));
        assertThrowsIndex(() -> joined.label(3));
    }

    @Test
    void sizeCountsKeysNotOctets() {
        assertEquals(3, KeyRow.of(WIDE, 1, 2, 3).size());
        assertEquals(6, KeyRow.of(WIDE, 1, 2, 3).payloadLength());
        assertEquals(3, KeyRow.of(BYTE, 1, 2, 3).size());
        assertEquals(3, KeyRow.of(BYTE, 1, 2, 3).payloadLength());
    }

    @Test
    void textFormIsTheLegacyBracketedList() {
        assertEquals("[1, -2, 3]", KeyRow.of(BYTE, 1, -2, 3).describe());
        assertEquals("[]", KeyRow.empty(BYTE).describe());
        assertEquals("PACKED_LEGACY16[-32767, 32767]", KeyRow.of(WIDE, -32767, 32767).toString());
    }

    @Test
    void parseRoundTripsDescribeForNegativeAndLargeLabels() {
        for (KeyRow row : List.of(KeyRow.of(BYTE, -127, 0, 127), KeyRow.of(WIDE, -32767, -129, -128, 0, 256, 32767),
                KeyRow.empty(WIDE))) {
            assertEquals(row, KeyRow.parse(row.encoding(), row.describe(), SourceLocation.NONE));
        }
        assertEquals(KeyRow.of(BYTE, 1, -2, 3), KeyRow.parse(BYTE, "  [ 1 ,\t-2,3 ]  ", SourceLocation.NONE));
    }

    @Test
    void parseIsStrictAndNeverSkipsATokenSilently() {
        for (String bad : new String[]{"", "1, 2", "[1, 2", "[1,, 2]", "[1 2]", "[a]", "[01]", "[-0]", "[+1]", "[1]x",
                "[1,]", "[-]", "[ , ]", "[99999999999]", "[1.5]", "]["}) {
            expectCode(ErrorCode.KEY_PARSE_INVALID, () -> KeyRow.parse(BYTE, bad, SourceLocation.NONE));
        }
    }

    @Test
    void parseChecksTheEncodingAndKeepsTheCallersLocation() {
        SourceLocation where = SourceLocation.of("scenario.json", 14, 3);
        FrameworkException e = expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE, () -> KeyRow.parse(BYTE, "[1, 128]", where));
        assertEquals(where, e.primary().location());
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> KeyRow.parse(BYTE, "[-128]", where));
        assertEquals(KeyRow.of(WIDE, 128), KeyRow.parse(WIDE, "[128]", where));
    }

    @Test
    void textFormDoesNotDependOnTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"));
            assertEquals("[-1234, 5]", KeyRow.of(WIDE, -1234, 5).describe());
            assertEquals(KeyRow.of(WIDE, -1234, 5), KeyRow.parse(WIDE, "[-1234, 5]", SourceLocation.NONE));
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void randomLegalRowsSurviveEveryRepresentation() {
        SplittableRandom random = new SplittableRandom(7_654_321L);
        for (KeyEncoding encoding : KeyEncoding.values()) {
            for (int i = 0; i < 2000; i++) {
                KeyRow row = randomRow(random, encoding, encoding.maxLabel());
                KeyRow viaPayload = KeyRow.fromPayload(encoding, row.payload());
                KeyRow viaText = KeyRow.parse(encoding, row.describe(), SourceLocation.NONE);
                KeyRow viaFrame = KeyWire.decode(KeyWire.encode(row));
                for (KeyRow copy : List.of(viaPayload, viaText, viaFrame)) {
                    assertEquals(row, copy);
                    assertEquals(row.hashCode(), copy.hashCode());
                    assertArrayEquals(row.labels(), copy.labels());
                }
            }
        }
    }

    private static KeyRow randomRow(SplittableRandom random, KeyEncoding encoding, int bound) {
        int length = random.nextInt(7);
        int[] labels = new int[length];
        for (int i = 0; i < length; i++) {
            int span = Math.min(bound, encoding.maxLabel());
            labels[i] = random.nextInt(-span, span + 1);
        }
        return KeyRow.of(encoding, labels);
    }

    private static void assertThrowsIndex(Executable action) {
        assertThrows(IndexOutOfBoundsException.class, action);
    }
}
