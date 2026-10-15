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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.ghyura.cf.model.TestSupport.expectCode;
import static org.ghyura.cf.model.TestSupport.hex;
import static org.ghyura.cf.model.TestSupport.hexOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Gate A02, byte-migration.xml acceptance case 7: payload length and declared key count mismatch,
 * unsupported encoding and truncated final key are rejected. Every frame below is written octet by octet.
 */
class KeyWireTest {

    @Test
    void handWrittenFramesEncodeAndDecodeExactly() {
        byte[] signed = hex("01 01 00 00 00 03 00 00 00 03 FF 00 01");
        byte[] packed = hex("01 02 00 00 00 02 00 00 00 04 FF 7F 01 00");
        byte[] emptyPacked = hex("01 02 00 00 00 00 00 00 00 00");

        assertEquals(KeyRow.of(KeyEncoding.SIGNED_BYTE, -1, 0, 1), KeyWire.decode(signed));
        assertEquals(KeyRow.of(KeyEncoding.PACKED_LEGACY16, -129, 256), KeyWire.decode(packed));
        assertEquals(KeyRow.empty(KeyEncoding.PACKED_LEGACY16), KeyWire.decode(emptyPacked));

        assertArrayEquals(signed, KeyWire.encode(KeyRow.of(KeyEncoding.SIGNED_BYTE, -1, 0, 1)));
        assertArrayEquals(packed, KeyWire.encode(KeyRow.of(KeyEncoding.PACKED_LEGACY16, -129, 256)));
        assertArrayEquals(emptyPacked, KeyWire.encode(KeyRow.empty(KeyEncoding.PACKED_LEGACY16)));
    }

    @Test
    void anEmptyRowIsAValidFrameAndIsNotTheAbsenceOfOne() {
        byte[] frame = KeyWire.encode(KeyRow.empty(KeyEncoding.SIGNED_BYTE));
        assertEquals("01 01 00 00 00 00 00 00 00 00", hexOf(frame));
        assertEquals(KeyRow.empty(KeyEncoding.SIGNED_BYTE), KeyWire.decode(frame));
    }

    @Test
    void headerProblemsAreReportedWithTheirOwnCodes() {
        expectCode(ErrorCode.KEY_WIRE_TRUNCATED, () -> KeyWire.decode(new byte[0]));
        expectCode(ErrorCode.KEY_WIRE_TRUNCATED, () -> KeyWire.decode(hex("01 02 00 00 00 00 00 00 00")));

        expectCode(ErrorCode.KEY_WIRE_VERSION_UNSUPPORTED, () -> KeyWire.decode(frame(0, 1, 0, 0, "")));
        expectCode(ErrorCode.KEY_WIRE_VERSION_UNSUPPORTED, () -> KeyWire.decode(frame(2, 1, 0, 0, "")));

        expectCode(ErrorCode.KEY_WIRE_ENCODING_UNSUPPORTED, () -> KeyWire.decode(frame(1, 0, 0, 0, "")));
        expectCode(ErrorCode.KEY_WIRE_ENCODING_UNSUPPORTED, () -> KeyWire.decode(frame(1, 3, 0, 0, "")));
        expectCode(ErrorCode.KEY_WIRE_ENCODING_UNSUPPORTED, () -> KeyWire.decode(frame(1, 255, 0, 0, "")));

        expectCode(ErrorCode.KEY_WIRE_MALFORMED, () -> KeyWire.decode(frame(1, 1, 0xFFFFFFFFL, 0, "")));
        expectCode(ErrorCode.KEY_WIRE_MALFORMED, () -> KeyWire.decode(frame(1, 1, 0, 0x80000000L, "")));
    }

    @Test
    void declaredCountAndPayloadLengthMustAgree() {
        expectCode(ErrorCode.KEY_WIRE_COUNT_MISMATCH, () -> KeyWire.decode(frame(1, 1, 3, 2, "01 02")));
        expectCode(ErrorCode.KEY_WIRE_COUNT_MISMATCH, () -> KeyWire.decode(frame(1, 1, 1, 2, "01 02")));
        expectCode(ErrorCode.KEY_WIRE_COUNT_MISMATCH, () -> KeyWire.decode(frame(1, 2, 2, 2, "00 01")));
        expectCode(ErrorCode.KEY_WIRE_COUNT_MISMATCH, () -> KeyWire.decode(frame(1, 2, 1, 4, "00 01 00 02")));
        // a count whose octet total overflows 32 bits must be a mismatch, not an arithmetic wraparound
        expectCode(ErrorCode.KEY_WIRE_COUNT_MISMATCH, () -> KeyWire.decode(frame(1, 2, 0x7FFFFFFFL, 4, "00 01 00 02")));
    }

    @Test
    void aTruncatedFinalKeyIsRejectedWhetherTheHeaderOrTheTransportIsShort() {
        // header says 3 payload octets in a two-octet encoding: the last key is cut inside the header itself
        expectCode(ErrorCode.KEY_WIRE_TRUNCATED_KEY, () -> KeyWire.decode(frame(1, 2, 1, 3, "00 01 7F")));
        expectCode(ErrorCode.KEY_WIRE_TRUNCATED_KEY, () -> KeyWire.decode(frame(1, 2, 2, 3, "00 01 7F")));
        // header is consistent but the frame lost its final octet in transit
        byte[] whole = KeyWire.encode(KeyRow.of(KeyEncoding.PACKED_LEGACY16, 1, 2));
        byte[] cut = java.util.Arrays.copyOf(whole, whole.length - 1);
        expectCode(ErrorCode.KEY_WIRE_TRUNCATED, () -> KeyWire.decode(cut));
    }

    @Test
    void trailingOctetsAreRejectedNotIgnored() {
        byte[] whole = KeyWire.encode(KeyRow.of(KeyEncoding.SIGNED_BYTE, 1, 2));
        byte[] longer = java.util.Arrays.copyOf(whole, whole.length + 1);
        expectCode(ErrorCode.KEY_WIRE_TRAILING_BYTES, () -> KeyWire.decode(longer));
    }

    @Test
    void reservedLabelsInsideAFrameAreRejected() {
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> KeyWire.decode(frame(1, 1, 2, 2, "01 80")));
        expectCode(ErrorCode.KEY_LABEL_RESERVED, () -> KeyWire.decode(frame(1, 2, 1, 2, "80 00")));
        // -128 spelled in two octets is an ordinary label
        assertEquals(KeyRow.of(KeyEncoding.PACKED_LEGACY16, -128), KeyWire.decode(frame(1, 2, 1, 2, "FF 80")));
    }

    @Test
    void decodeReadsExactlyTheNamedWindowOfALargerBuffer() {
        byte[] frame = KeyWire.encode(KeyRow.of(KeyEncoding.PACKED_LEGACY16, 7, -7));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.writeBytes(hex("AA BB CC"));
        buffer.writeBytes(frame);
        buffer.writeBytes(hex("DD"));
        KeyRow row = KeyWire.decode(buffer.toByteArray(), 3, frame.length, SourceLocation.NONE);
        assertEquals(KeyRow.of(KeyEncoding.PACKED_LEGACY16, 7, -7), row);
        expectCode(ErrorCode.KEY_WIRE_TRAILING_BYTES,
                () -> KeyWire.decode(buffer.toByteArray(), 3, frame.length + 1, SourceLocation.NONE));
    }

    @Test
    void diagnosticsCarryTheCallersLocation() {
        SourceLocation where = SourceLocation.ofPath("checkpoint.bin", "block 12/row 3");
        assertEquals(where, expectCode(ErrorCode.KEY_WIRE_TRUNCATED, () -> KeyWire.decode(new byte[3], where))
                .primary().location());
    }

    /** Builds a frame with arbitrary header values; counts are written as unsigned 32-bit big-endian. */
    private static byte[] frame(int version, int encodingId, long keyCount, long payloadBytes, String payloadHex) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(version);
        out.write(encodingId);
        writeU32(out, keyCount);
        writeU32(out, payloadBytes);
        out.writeBytes(hex(payloadHex));
        return out.toByteArray();
    }

    private static void writeU32(ByteArrayOutputStream out, long value) {
        out.write((int) (value >>> 24) & 0xFF);
        out.write((int) (value >>> 16) & 0xFF);
        out.write((int) (value >>> 8) & 0xFF);
        out.write((int) value & 0xFF);
    }
}
