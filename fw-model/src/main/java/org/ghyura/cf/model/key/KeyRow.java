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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * An immutable sequence of key labels stored as {@code byte[]} in one fixed {@link KeyEncoding}.
 *
 * <p>This is the primitive row of the byte migration: the labels of one sheet stage or one assembled
 * candidate. Presence and baseline state belong to the slot that holds a row ({@link SelectionState}), not to
 * the row, so an empty row is a legitimate present value and is never confused with an absent optional or
 * with the label 0.</p>
 *
 * <p>Equality and hashing compare the encoding and the encoded content; Java array identity never matters
 * (B09). The payload is copied on the way in and on the way out. Ordering is deliberately not
 * {@link Comparable}: {@link #LABEL_ORDER} compares decoded signed labels and ignores the encoding, which
 * would be inconsistent with {@link #equals}.</p>
 */
public final class KeyRow {

    /**
     * The legacy {@code FW_Group} order: decoded signed labels lexicographically, then the shorter row first.
     * Raw octet comparison is wrong across the 127/-127 and multi-octet sign boundaries (B09).
     */
    public static final Comparator<KeyRow> LABEL_ORDER = KeyRow::compareLabels;

    private static final byte[] NO_BYTES = new byte[0];

    private final KeyEncoding encoding;
    private final byte[] payload;
    private int hash;

    private KeyRow(KeyEncoding encoding, byte[] ownedPayload) {
        this.encoding = encoding;
        this.payload = ownedPayload;
    }

    public static KeyRow empty(KeyEncoding encoding) {
        return new KeyRow(Objects.requireNonNull(encoding, "encoding"), NO_BYTES);
    }

    public static KeyRow of(KeyEncoding encoding, int... labels) {
        return ofLabels(encoding, labels, SourceLocation.NONE);
    }

    public static KeyRow ofLabels(KeyEncoding encoding, int[] labels, SourceLocation where) {
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(labels, "labels");
        Objects.requireNonNull(where, "where");
        byte[] bytes = new byte[Math.multiplyExact(labels.length, encoding.bytesPerKey())];
        for (int i = 0; i < labels.length; i++) {
            encoding.put(bytes, i, labels[i], where);
        }
        return new KeyRow(encoding, bytes);
    }

    public static KeyRow fromPayload(KeyEncoding encoding, byte[] payload) {
        return fromPayload(encoding, payload, 0, payload.length, SourceLocation.NONE);
    }

    /**
     * Copies {@code length} octets starting at {@code offset} and validates every key.
     *
     * @throws FrameworkException KEY_WIRE_TRUNCATED_KEY when {@code length} is not a whole number of keys,
     *                            KEY_LABEL_RESERVED when an octet pattern spells the reserved sentinel
     */
    public static KeyRow fromPayload(KeyEncoding encoding, byte[] source, int offset, int length, SourceLocation where) {
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(where, "where");
        Objects.checkFromIndexSize(offset, length, source.length);
        if (length % encoding.bytesPerKey() != 0) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.KEY_WIRE_TRUNCATED_KEY,
                    "payload of " + length + " octets ends inside a key of " + encoding
                            + " (" + encoding.bytesPerKey() + " octets per key)", where,
                    "encoding", encoding.name(), "payloadBytes", Integer.toString(length)));
        }
        byte[] copy = Arrays.copyOfRange(source, offset, offset + length);
        int keys = length / encoding.bytesPerKey();
        for (int i = 0; i < keys; i++) {
            encoding.requireLegal(encoding.decode(copy, i), i, where);
        }
        return new KeyRow(encoding, copy);
    }

    /**
     * Parses the legacy rendering {@code [1, -2, 3]} (what {@link #describe()} produces). Strict on purpose:
     * only canonical decimal integers, no signs other than '-', no leading zeros; anything else is
     * KEY_PARSE_INVALID rather than a skipped token. The result is checked against {@code encoding}.
     */
    public static KeyRow parse(KeyEncoding encoding, CharSequence text, SourceLocation where) {
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(where, "where");
        int n = text.length();
        int i = skipBlank(text, 0);
        if (i >= n || text.charAt(i) != '[') {
            throw parseError(text, where, i, "expected '['");
        }
        i = skipBlank(text, i + 1);
        int[] labels = new int[8];
        int count = 0;
        if (i < n && text.charAt(i) == ']') {
            i++;
        } else {
            while (true) {
                int start = i;
                if (i < n && text.charAt(i) == '-') {
                    i++;
                }
                int digits = i;
                while (i < n && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
                    i++;
                }
                int digitCount = i - digits;
                if (digitCount == 0) {
                    throw parseError(text, where, start, "expected a decimal integer");
                }
                boolean leadingZero = digitCount > 1 && text.charAt(digits) == '0';
                boolean negativeZero = digits > start && digitCount == 1 && text.charAt(digits) == '0';
                if (leadingZero || negativeZero) {
                    throw parseError(text, where, start, "not a canonical decimal integer");
                }
                long value = digitCount > 10 ? Long.MAX_VALUE : Long.parseLong(text.subSequence(start, i).toString());
                if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                    throw parseError(text, where, start, "integer outside the 32-bit range");
                }
                if (count == labels.length) {
                    labels = Arrays.copyOf(labels, count * 2);
                }
                labels[count++] = (int) value;
                i = skipBlank(text, i);
                if (i < n && text.charAt(i) == ',') {
                    i = skipBlank(text, i + 1);
                } else if (i < n && text.charAt(i) == ']') {
                    i++;
                    break;
                } else {
                    throw parseError(text, where, i, "expected ',' or ']'");
                }
            }
        }
        i = skipBlank(text, i);
        if (i != n) {
            throw parseError(text, where, i, "text after ']'");
        }
        return ofLabels(encoding, Arrays.copyOf(labels, count), where);
    }

    private static int skipBlank(CharSequence text, int from) {
        int i = from;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                break;
            }
            i++;
        }
        return i;
    }

    private static FrameworkException parseError(CharSequence text, SourceLocation where, int offset, String why) {
        String shown = text.length() <= 80 ? text.toString() : text.subSequence(0, 80) + "...";
        return new FrameworkException(Diagnostic.error(ErrorCode.KEY_PARSE_INVALID,
                "not a key list: " + why + " at offset " + offset, where,
                "offset", Integer.toString(offset), "text", shown));
    }

    public KeyEncoding encoding() {
        return encoding;
    }

    /** Number of keys (not octets). */
    public int size() {
        return payload.length / encoding.bytesPerKey();
    }

    public boolean isEmpty() {
        return payload.length == 0;
    }

    public int label(int index) {
        Objects.checkIndex(index, size());
        return encoding.decode(payload, index);
    }

    public int[] labels() {
        int n = size();
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = encoding.decode(payload, i);
        }
        return out;
    }

    /** A copy of the encoded octets. */
    public byte[] payload() {
        return payload.clone();
    }

    /** Length of the encoded form in octets ({@link #size()} times the encoding's octets per key). */
    public int payloadLength() {
        return payload.length;
    }

    public void copyPayloadTo(byte[] destination, int offset) {
        Objects.checkFromIndexSize(offset, payload.length, destination.length);
        System.arraycopy(payload, 0, destination, offset, payload.length);
    }

    public KeyRow concat(KeyRow other) {
        requireSameEncoding(other);
        byte[] joined = new byte[Math.addExact(payload.length, other.payload.length)];
        System.arraycopy(payload, 0, joined, 0, payload.length);
        System.arraycopy(other.payload, 0, joined, payload.length, other.payload.length);
        return new KeyRow(encoding, joined);
    }

    /** Keys {@code fromKey} (inclusive) to {@code toKey} (exclusive). */
    public KeyRow slice(int fromKey, int toKey) {
        Objects.checkFromToIndex(fromKey, toKey, size());
        int bpk = encoding.bytesPerKey();
        return new KeyRow(encoding, Arrays.copyOfRange(payload, fromKey * bpk, toKey * bpk));
    }

    private void requireSameEncoding(KeyRow other) {
        if (encoding != other.encoding) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.KEY_ENCODING_MISMATCH,
                    "cannot combine a " + encoding + " row with a " + other.encoding + " row", SourceLocation.NONE,
                    "left", encoding.name(), "right", other.encoding.name()));
        }
    }

    /** Decoded-label lexicographic comparison, then length; independent of either row's encoding. */
    public static int compareLabels(KeyRow a, KeyRow b) {
        int common = Math.min(a.size(), b.size());
        for (int i = 0; i < common; i++) {
            int c = Integer.compare(a.encoding.decode(a.payload, i), b.encoding.decode(b.payload, i));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    /**
     * The legacy text rendering {@code [1, -2, 3]}: brackets, comma and one space, each label through
     * {@link Integer#toString(int)} so the text never depends on the default locale (B10).
     */
    public String describe() {
        int n = size();
        StringBuilder sb = new StringBuilder(2 + n * 4).append('[');
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(Integer.toString(encoding.decode(payload, i)));
        }
        return sb.append(']').toString();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof KeyRow other
                && encoding == other.encoding
                && Arrays.equals(payload, other.payload));
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            h = 31 * Arrays.hashCode(payload) + encoding.wireId();
            hash = h;
        }
        return h;
    }

    @Override
    public String toString() {
        return encoding + describe();
    }
}
