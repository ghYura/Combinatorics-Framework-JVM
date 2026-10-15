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

package org.ghyura.cf.model;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The shared pieces of the canonical text forms that give the logical models (the workbook as read, the
 * scenario as interpreted, the plan as parsed) a content identity: one escaping rule so that no value can
 * forge a line break or a field separator, and one digest.
 *
 * <p>The identity boundary is injective. Java strings may hold unpaired surrogates (a JSON string escape of
 * U+D800 or an OOXML {@code _xD800_} escape produces one, and the legacy framework accepted them as ordinary
 * values), but UTF-8 cannot encode them: the JDK's encoder silently replaces each with {@code '?'}, which
 * would give a lone surrogate and a question mark one digest. So {@link #escape} writes every unpaired
 * surrogate as a backslash-u escape of four lowercase hex digits (a literal backslash is always doubled, so
 * the escape cannot be confused with text that spells it), and {@link #sha256Hex(String)} refuses text that
 * is not well-formed instead of digesting a lossy encoding of it. Valid surrogate pairs (supplementary
 * characters) are kept.</p>
 */
public final class Canonical {

    private static final HexFormat HEX = HexFormat.of();

    private Canonical() {
    }

    /**
     * Escapes backslash, tab, newline, carriage return, every other control character and every unpaired
     * surrogate; leaves everything else, supplementary characters included, as it is.
     */
    public static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> {
                    if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                        out.append(c).append(text.charAt(++i));
                    } else if (c < 0x20 || c == 0x7F || Character.isSurrogate(c)) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * The UTF-8 octets of well-formed text.
     *
     * @throws IllegalArgumentException when the text holds an unpaired surrogate, which UTF-8 cannot encode;
     *                                  {@link String#getBytes} would silently write {@code '?'} instead
     */
    public static byte[] utf8(String text) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(text));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("the text is not well-formed UTF-16 (it holds an unpaired surrogate); "
                    + "escape it with Canonical.escape before it becomes part of an identity", e);
        }
    }

    /**
     * SHA-256 of the text encoded as UTF-8, as 64 lowercase hex characters.
     *
     * @throws IllegalArgumentException when the text is not well-formed (see {@link #utf8})
     */
    public static String sha256Hex(String canonicalText) {
        return sha256Hex(utf8(canonicalText));
    }

    /** SHA-256 of the octets, as 64 lowercase hex characters. */
    public static String sha256Hex(byte[] bytes) {
        return HEX.formatHex(sha256(bytes));
    }

    /** SHA-256 of the octets with the JDK's standard implementation. */
    public static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform must provide SHA-256", e);
        }
    }
}
