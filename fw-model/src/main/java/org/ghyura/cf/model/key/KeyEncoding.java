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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * How one key label is stored in a {@code byte[]} (byte-migration.xml, rules B01-B05).
 *
 * <p>A <em>label</em> is the logical signed integer the legacy system called a key ("code"); it is what
 * numeric-key regular expressions see and what {@code FW_Group} orders by. An encoding maps labels to octets
 * without ever using a 16-bit Java integer type: arithmetic happens in {@code int}, bounds are validated, and
 * narrowing to {@code byte} happens once.</p>
 *
 * <ul>
 *   <li>{@link #SIGNED_BYTE}: one octet, labels -127..127, -128 reserved (legacy byte tier);</li>
 *   <li>{@link #PACKED_LEGACY16}: two big-endian octets in two's complement, labels -32767..32767,
 *       -32768 reserved (legacy 16-bit tier). -128 is an ordinary label here.</li>
 * </ul>
 *
 * <p>The reserved value of each encoding is the legacy NULL array element. It never appears in a normalized
 * row; {@link LegacyKeyImport} is the only place that recognises it.</p>
 */
public enum KeyEncoding {

    SIGNED_BYTE(1, 1, -127, 127),
    PACKED_LEGACY16(2, 2, -32767, 32767);

    private final int wireId;
    private final int bytesPerKey;
    private final int minLabel;
    private final int maxLabel;

    KeyEncoding(int wireId, int bytesPerKey, int minLabel, int maxLabel) {
        this.wireId = wireId;
        this.bytesPerKey = bytesPerKey;
        this.minLabel = minLabel;
        this.maxLabel = maxLabel;
    }

    /** Stable identifier in the key frame header; never reused. */
    public int wireId() {
        return wireId;
    }

    public int bytesPerKey() {
        return bytesPerKey;
    }

    public int minLabel() {
        return minLabel;
    }

    public int maxLabel() {
        return maxLabel;
    }

    /** The legacy NULL-element value of this encoding's width: one below {@link #minLabel()}. */
    public int reservedLabel() {
        return minLabel - 1;
    }

    /** Number of distinct usable labels (255 or 65535). */
    public int capacity() {
        return maxLabel - minLabel + 1;
    }

    public boolean accepts(int label) {
        return label >= minLabel && label <= maxLabel;
    }

    public static Optional<KeyEncoding> byWireId(int wireId) {
        for (KeyEncoding e : values()) {
            if (e.wireId == wireId) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /**
     * @param keyIndex position of the label inside its row, or -1 when it is not part of a row
     * @throws FrameworkException KEY_LABEL_RESERVED or KEY_LABEL_OUT_OF_RANGE
     */
    public void requireLegal(int label, int keyIndex, SourceLocation where) {
        if (label == reservedLabel()) {
            throw new FrameworkException(withIndex(Diagnostic.error(ErrorCode.KEY_LABEL_RESERVED,
                    "label " + label + " is the reserved NULL sentinel of " + this, where,
                    "label", Integer.toString(label), "encoding", name()), keyIndex));
        }
        if (!accepts(label)) {
            throw new FrameworkException(withIndex(Diagnostic.error(ErrorCode.KEY_LABEL_OUT_OF_RANGE,
                    "label " + label + " does not fit " + this + " (" + minLabel + ".." + maxLabel + ")", where,
                    "label", Integer.toString(label), "encoding", name(),
                    "min", Integer.toString(minLabel), "max", Integer.toString(maxLabel)), keyIndex));
        }
    }

    public void requireLegal(int label, SourceLocation where) {
        requireLegal(label, -1, where);
    }

    public void requireLegal(int label) {
        requireLegal(label, -1, SourceLocation.NONE);
    }

    private static Diagnostic withIndex(Diagnostic d, int keyIndex) {
        if (keyIndex < 0) {
            return d;
        }
        Map<String, String> attrs = new LinkedHashMap<>(d.attributes());
        attrs.put("index", Integer.toString(keyIndex));
        return new Diagnostic(d.severity(), d.code(), d.message(), d.location(), attrs);
    }

    /**
     * Validates the key position and {@code label}, then writes the label at key position {@code keyIndex} of
     * {@code payload}. A refused write leaves every octet of the payload unchanged.
     *
     * @throws IndexOutOfBoundsException when the payload does not hold a complete key at {@code keyIndex}
     */
    public void put(byte[] payload, int keyIndex, int label, SourceLocation where) {
        int at = offsetOf(payload, keyIndex);
        requireLegal(label, keyIndex, where);
        if (this == SIGNED_BYTE) {
            payload[at] = (byte) label;
        } else {
            int unsigned = label & 0xFFFF;
            payload[at] = (byte) (unsigned >>> 8);
            payload[at + 1] = (byte) unsigned;
        }
    }

    public void put(byte[] payload, int keyIndex, int label) {
        put(payload, keyIndex, label, SourceLocation.NONE);
    }

    /**
     * Reads the label at key position {@code keyIndex}.
     *
     * @throws FrameworkException        KEY_LABEL_RESERVED if the octets spell the reserved sentinel
     * @throws IndexOutOfBoundsException when the payload does not hold a complete key at {@code keyIndex}
     */
    public int get(byte[] payload, int keyIndex) {
        int label = decode(payload, keyIndex);
        requireLegal(label, SourceLocation.NONE);
        return label;
    }

    /**
     * Decodes without the reserved-value check; callers validate with {@link #requireLegal}.
     *
     * @throws IndexOutOfBoundsException when the payload does not hold a complete key at {@code keyIndex}
     */
    int decode(byte[] payload, int keyIndex) {
        int at = offsetOf(payload, keyIndex);
        if (this == SIGNED_BYTE) {
            return payload[at];
        }
        int unsigned = ((payload[at] & 0xFF) << 8) | (payload[at + 1] & 0xFF);
        return unsigned >= 0x8000 ? unsigned - 0x10000 : unsigned;
    }

    /**
     * The offset of key {@code keyIndex}, checked before any multiplication: the index must name one of the
     * complete keys of the payload (a trailing partial key of an odd-length packed payload is not one).
     * Unchecked, {@code Integer.MIN_VALUE * 2} wraps to 0 and would alias key zero.
     */
    private int offsetOf(byte[] payload, int keyIndex) {
        int completeKeys = payload.length / bytesPerKey;
        if (keyIndex < 0 || keyIndex >= completeKeys) {
            throw new IndexOutOfBoundsException("key index " + keyIndex + " is outside the " + completeKeys
                    + " complete " + this + " keys of a " + payload.length + "-octet payload");
        }
        return keyIndex * bytesPerKey;
    }
}
