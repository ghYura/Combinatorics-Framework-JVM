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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * The versioned frame one {@link KeyRow} travels in (B04, B14). It is independent of any PostgreSQL
 * array or COPY format and carries no Java serialization.
 *
 * <pre>
 * offset 0   u8   schema version (1)
 * offset 1   u8   {@link KeyEncoding#wireId()}
 * offset 2   u32  key count          big-endian, at most Integer.MAX_VALUE
 * offset 6   u32  payload octets     big-endian, at most Integer.MAX_VALUE
 * offset 10  payload (key count times bytes-per-key octets)
 * </pre>
 *
 * <p>Decoding is exact: a frame that is short, long, internally inconsistent, of an unknown version or
 * encoding, or that spells a reserved label is rejected with a specific code. Nothing is repaired.</p>
 */
public final class KeyWire {

    public static final int SCHEMA_VERSION = 1;
    public static final int HEADER_BYTES = 10;

    private KeyWire() {
    }

    public static byte[] encode(KeyRow row) {
        Objects.requireNonNull(row, "row");
        byte[] frame = new byte[HEADER_BYTES + row.payloadLength()];
        ByteBuffer.wrap(frame, 0, HEADER_BYTES)
                .put((byte) SCHEMA_VERSION)
                .put((byte) row.encoding().wireId())
                .putInt(row.size())
                .putInt(row.payloadLength());
        row.copyPayloadTo(frame, HEADER_BYTES);
        return frame;
    }

    public static KeyRow decode(byte[] frame) {
        return decode(frame, 0, frame.length, SourceLocation.NONE);
    }

    public static KeyRow decode(byte[] frame, SourceLocation where) {
        return decode(frame, 0, frame.length, where);
    }

    /**
     * Decodes the {@code length} octets at {@code offset} as exactly one frame.
     *
     * <p>Checks run in this order, so the code names the first thing that is wrong: header present
     * (KEY_WIRE_TRUNCATED), schema version, encoding, impossible negative counts (KEY_WIRE_MALFORMED), payload
     * ends mid-key (KEY_WIRE_TRUNCATED_KEY), declared count versus declared payload octets
     * (KEY_WIRE_COUNT_MISMATCH), declared payload versus available octets (KEY_WIRE_TRUNCATED), trailing
     * octets (KEY_WIRE_TRAILING_BYTES), then every label (KEY_LABEL_RESERVED).</p>
     */
    public static KeyRow decode(byte[] buffer, int offset, int length, SourceLocation where) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(where, "where");
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length < HEADER_BYTES) {
            throw fail(ErrorCode.KEY_WIRE_TRUNCATED, "frame has " + length + " octets but the header needs "
                    + HEADER_BYTES, where, "availableBytes", Integer.toString(length));
        }
        ByteBuffer header = ByteBuffer.wrap(buffer, offset, HEADER_BYTES);
        int version = header.get() & 0xFF;
        int encodingId = header.get() & 0xFF;
        int keyCount = header.getInt();
        int payloadBytes = header.getInt();

        if (version != SCHEMA_VERSION) {
            throw fail(ErrorCode.KEY_WIRE_VERSION_UNSUPPORTED, "schema version " + version
                    + " is not readable (this build reads " + SCHEMA_VERSION + ")", where,
                    "version", Integer.toString(version));
        }
        KeyEncoding encoding = KeyEncoding.byWireId(encodingId).orElseThrow(() -> fail(
                ErrorCode.KEY_WIRE_ENCODING_UNSUPPORTED, "encoding id " + encodingId + " is unknown", where,
                "encodingId", Integer.toString(encodingId)));
        if (keyCount < 0 || payloadBytes < 0) {
            throw fail(ErrorCode.KEY_WIRE_MALFORMED, "header declares a negative count (keys=" + keyCount
                    + ", payloadBytes=" + payloadBytes + "; counts above 2^31-1 are not representable)", where,
                    "keyCount", Integer.toString(keyCount), "payloadBytes", Integer.toString(payloadBytes));
        }
        if (payloadBytes % encoding.bytesPerKey() != 0) {
            throw fail(ErrorCode.KEY_WIRE_TRUNCATED_KEY, "declared payload of " + payloadBytes
                    + " octets ends inside a key of " + encoding, where, "encoding", encoding.name(),
                    "payloadBytes", Integer.toString(payloadBytes));
        }
        long expectedBytes = (long) keyCount * encoding.bytesPerKey();
        if (expectedBytes != payloadBytes) {
            throw fail(ErrorCode.KEY_WIRE_COUNT_MISMATCH, "header declares " + keyCount + " keys ("
                    + expectedBytes + " octets in " + encoding + ") but " + payloadBytes + " payload octets",
                    where, "keyCount", Integer.toString(keyCount), "expectedBytes", Long.toString(expectedBytes),
                    "payloadBytes", Integer.toString(payloadBytes));
        }
        long available = (long) length - HEADER_BYTES;
        if (available < payloadBytes) {
            throw fail(ErrorCode.KEY_WIRE_TRUNCATED, "frame carries " + available + " payload octets but the header"
                    + " declares " + payloadBytes, where, "availableBytes", Long.toString(available),
                    "payloadBytes", Integer.toString(payloadBytes));
        }
        if (available > payloadBytes) {
            throw fail(ErrorCode.KEY_WIRE_TRAILING_BYTES, (available - payloadBytes)
                    + " octets follow the declared payload", where, "extraBytes",
                    Long.toString(available - payloadBytes));
        }
        return KeyRow.fromPayload(encoding, buffer, offset + HEADER_BYTES, payloadBytes, where);
    }

    private static FrameworkException fail(ErrorCode code, String message, SourceLocation where, String... attributes) {
        return new FrameworkException(Diagnostic.error(code, message, where, attributes));
    }
}
