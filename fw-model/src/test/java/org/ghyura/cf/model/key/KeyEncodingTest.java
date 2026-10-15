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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigInteger;
import java.util.Arrays;

import static org.ghyura.cf.model.TestSupport.expectCode;
import static org.ghyura.cf.model.TestSupport.hex;
import static org.ghyura.cf.model.TestSupport.hexOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A02, byte-migration.xml acceptance cases 1 and 2. Expected octets are written by hand
 * (two's complement, big-endian) or derived with BigInteger, which shares no code with the shift-and-mask
 * implementation under test.
 */
class KeyEncodingTest {

    @Test
    void signedByteBoundaryLabelsRoundTripToHandDerivedOctets() {
        int[] labels = {-127, -1, 0, 1, 127};
        String[] octets = {"81", "FF", "00", "01", "7F"};
        for (int i = 0; i < labels.length; i++) {
            KeyRow row = KeyRow.of(KeyEncoding.SIGNED_BYTE, labels[i]);
            assertEquals(octets[i], hexOf(row.payload()), "octets of " + labels[i]);
            assertEquals(labels[i], KeyRow.fromPayload(KeyEncoding.SIGNED_BYTE, hex(octets[i])).label(0));
        }
    }

    @Test
    void signedByteRejectsMinus128AsReservedAndPlus128AsOutOfRange() {
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> KeyRow.of(KeyEncoding.SIGNED_BYTE, -128));
        expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE, () -> KeyRow.of(KeyEncoding.SIGNED_BYTE, 128));
        for (int label : new int[]{-129, 255, 256, 32767, -32767, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE, () -> KeyRow.of(KeyEncoding.SIGNED_BYTE, label));
        }
    }

    @Test
    void packedBoundaryLabelsRoundTripToHandDerivedOctets() {
        // the exact list from byte-migration.xml, with each label's 16-bit two's complement worked out by hand
        int[] labels = {-32767, -129, -128, -127, -1, 0, 1, 127, 128, 255, 256, 32767};
        String[] octets = {"80 01", "FF 7F", "FF 80", "FF 81", "FF FF", "00 00", "00 01", "00 7F", "00 80", "00 FF",
                "01 00", "7F FF"};
        for (int i = 0; i < labels.length; i++) {
            KeyRow row = KeyRow.of(KeyEncoding.PACKED_LEGACY16, labels[i]);
            assertEquals(octets[i], hexOf(row.payload()), "octets of " + labels[i]);
            assertEquals(labels[i], KeyRow.fromPayload(KeyEncoding.PACKED_LEGACY16, hex(octets[i])).label(0));
        }
    }

    @Test
    void packedRejectsMinus32768AsReservedAndPlus32768AsOutOfRange() {
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> KeyRow.of(KeyEncoding.PACKED_LEGACY16, -32768));
        expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE, () -> KeyRow.of(KeyEncoding.PACKED_LEGACY16, 32768));
        for (int label : new int[]{-32769, 65535, 65536, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE, () -> KeyRow.of(KeyEncoding.PACKED_LEGACY16, label));
        }
    }

    @Test
    void minus128IsAnOrdinaryLabelInPackedMode() {
        KeyRow row = KeyRow.of(KeyEncoding.PACKED_LEGACY16, 1, -128, 2);
        assertEquals(3, row.size());
        assertArrayEquals(new int[]{1, -128, 2}, row.labels());
        assertTrue(KeyEncoding.PACKED_LEGACY16.accepts(-128));
        assertFalse(KeyEncoding.SIGNED_BYTE.accepts(-128));
    }

    @ParameterizedTest
    @EnumSource(KeyEncoding.class)
    void everyLegalLabelMatchesAnIndependentTwosComplementOracle(KeyEncoding encoding) {
        int visited = 0;
        for (int label = encoding.minLabel(); label <= encoding.maxLabel(); label++) {
            byte[] expected = twosComplement(label, encoding.bytesPerKey());
            byte[] actual = KeyRow.of(encoding, label).payload();
            if (!Arrays.equals(expected, actual)) {
                throw new AssertionError("label " + label + ": expected " + hexOf(expected) + " got " + hexOf(actual));
            }
            assertEquals(label, KeyRow.fromPayload(encoding, expected).label(0), "decode of " + hexOf(expected));
            visited++;
        }
        assertEquals(encoding.capacity(), visited);
    }

    @ParameterizedTest
    @EnumSource(KeyEncoding.class)
    void everyLabelJustOutsideTheDomainIsRejectedWithTheRightCode(KeyEncoding encoding) {
        for (int offset = 1; offset <= 300; offset++) {
            int below = encoding.minLabel() - offset;
            int above = encoding.maxLabel() + offset;
            ErrorCode belowCode = offset == 1 ? ErrorCode.KEY_LABEL_RESERVED : ErrorCode.KEY_LABEL_OUT_OF_RANGE;
            expectCode(belowCode, () -> encoding.requireLegal(below));
            expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE, () -> encoding.requireLegal(above));
        }
    }

    @Test
    void reservedOctetPatternsAreRejectedWhenDecoding() {
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> KeyRow.fromPayload(KeyEncoding.SIGNED_BYTE, hex("80")));
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> KeyRow.fromPayload(KeyEncoding.PACKED_LEGACY16, hex("80 00")));
        // the same octets that are reserved at 16 bits are legal -128 / ordinary labels at the other widths
        assertEquals(-128, KeyRow.fromPayload(KeyEncoding.PACKED_LEGACY16, hex("FF 80")).label(0));
    }

    @Test
    void rejectionReportsWhichKeyOfARowWasBad() {
        FrameworkException e = expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE,
                () -> KeyRow.of(KeyEncoding.SIGNED_BYTE, 1, 2, 200, 4));
        assertEquals("2", e.primary().attributes().get("index"));
        assertEquals("200", e.primary().attributes().get("label"));
    }

    @Test
    void payloadThatEndsInsideAKeyIsRejected() {
        expectCode(ErrorCode.KEY_WIRE_TRUNCATED_KEY, () -> KeyRow.fromPayload(KeyEncoding.PACKED_LEGACY16, hex("00 01 7F")));
        assertEquals(0, KeyRow.fromPayload(KeyEncoding.PACKED_LEGACY16, hex("")).size());
    }

    @Test
    void capacitiesAndReservedValuesMatchTheLegacyDispatcher() {
        // DataTypeDispatcher.BYTE_CAPACITY = 255, SHORT_CAPACITY = 65535; MIN_VALUE of each width is reserved
        assertEquals(255, KeyEncoding.SIGNED_BYTE.capacity());
        assertEquals(65535, KeyEncoding.PACKED_LEGACY16.capacity());
        assertEquals(-128, KeyEncoding.SIGNED_BYTE.reservedLabel());
        assertEquals(-32768, KeyEncoding.PACKED_LEGACY16.reservedLabel());
        assertEquals(1, KeyEncoding.SIGNED_BYTE.bytesPerKey());
        assertEquals(2, KeyEncoding.PACKED_LEGACY16.bytesPerKey());
    }

    @Test
    void wireIdsAreStableAndDistinct() {
        assertEquals(1, KeyEncoding.SIGNED_BYTE.wireId());
        assertEquals(2, KeyEncoding.PACKED_LEGACY16.wireId());
        assertEquals(KeyEncoding.PACKED_LEGACY16, KeyEncoding.byWireId(2).orElseThrow());
        assertTrue(KeyEncoding.byWireId(0).isEmpty());
        assertTrue(KeyEncoding.byWireId(3).isEmpty());
    }

    /**
     * Review finding BM02: {@code keyIndex * bytesPerKey} was not checked, so {@code Integer.MIN_VALUE * 2}
     * wrapped to 0 and PACKED_LEGACY16 read and overwrote key zero. Every index outside the complete keys of
     * the payload is refused, and a refused write changes no octet.
     */
    @ParameterizedTest
    @EnumSource(KeyEncoding.class)
    void keyIndicesOutsideTheCompleteKeysOfThePayloadAreRefusedWithoutWriting(KeyEncoding encoding) {
        for (int length : new int[]{0, 1, 2, 3, 6, 7}) {
            byte[] payload = new byte[length];
            for (int i = 0; i < length; i++) {
                payload[i] = (byte) (0x11 * (i + 1));
            }
            int complete = length / encoding.bytesPerKey();
            int[] invalid = {-1, -2, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, complete, complete + 1, Integer.MAX_VALUE,
                    Integer.MAX_VALUE / 2 + 1};
            for (int index : invalid) {
                byte[] before = payload.clone();
                String what = encoding + " length " + length + " index " + index;
                assertThrows(IndexOutOfBoundsException.class, () -> encoding.get(payload, index), what);
                assertThrows(IndexOutOfBoundsException.class, () -> encoding.put(payload, index, 9), what);
                assertThrows(IndexOutOfBoundsException.class, () -> encoding.put(payload, index, 9, SourceLocation.NONE), what);
                assertArrayEquals(before, payload, what + ": a refused write must leave every octet unchanged");
            }
            if (complete > 0) {
                int last = complete - 1;
                byte[] before = payload.clone();
                encoding.put(payload, last, -5);
                assertEquals(-5, encoding.get(payload, last), encoding + " length " + length + ": the last complete key");
                byte[] expected = before.clone();
                System.arraycopy(twosComplement(-5, encoding.bytesPerKey()), 0, expected, last * encoding.bytesPerKey(),
                        encoding.bytesPerKey());
                assertArrayEquals(expected, payload, "only the octets of the last complete key changed");
            }
        }
    }

    @Test
    void theReviewersExactAliasingCallsAreRefused() {
        byte[] payload = {0, 7};
        assertThrows(IndexOutOfBoundsException.class, () -> KeyEncoding.PACKED_LEGACY16.get(payload, Integer.MIN_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> KeyEncoding.PACKED_LEGACY16.put(payload, Integer.MIN_VALUE, 9));
        assertArrayEquals(new byte[]{0, 7}, payload);
        assertEquals(7, KeyEncoding.PACKED_LEGACY16.get(payload, 0));
        // an odd-length packed payload: its trailing octet is not a key
        byte[] odd = {0, 1, 0x7F};
        assertEquals(1, KeyEncoding.PACKED_LEGACY16.get(odd, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> KeyEncoding.PACKED_LEGACY16.get(odd, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> KeyEncoding.PACKED_LEGACY16.put(odd, 1, 3));
        assertArrayEquals(new byte[]{0, 1, 0x7F}, odd);
    }

    @Test
    void anIllegalLabelAtAValidIndexIsRefusedWithoutWritingEither() {
        byte[] compact = {1, 2};
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> KeyEncoding.SIGNED_BYTE.put(compact, 1, -128));
        expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE, () -> KeyEncoding.SIGNED_BYTE.put(compact, 0, 128));
        assertArrayEquals(new byte[]{1, 2}, compact);
        byte[] packed = {0, 1, 0, 2};
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> KeyEncoding.PACKED_LEGACY16.put(packed, 1, -32768));
        expectCode(ErrorCode.KEY_LABEL_OUT_OF_RANGE, () -> KeyEncoding.PACKED_LEGACY16.put(packed, 0, 32768));
        assertArrayEquals(new byte[]{0, 1, 0, 2}, packed);
    }

    /** Minimal two's complement of {@code label}, sign-extended to {@code width} octets, via BigInteger. */
    private static byte[] twosComplement(int label, int width) {
        byte[] minimal = BigInteger.valueOf(label).toByteArray();
        byte[] out = new byte[width];
        Arrays.fill(out, (byte) (label < 0 ? 0xFF : 0x00));
        System.arraycopy(minimal, 0, out, width - minimal.length, minimal.length);
        return out;
    }
}
