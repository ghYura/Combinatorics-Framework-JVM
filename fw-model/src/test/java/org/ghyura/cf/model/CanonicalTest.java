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

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalTest {

    @Test
    void escapeKeepsPlainTextAndEscapesEverythingThatCouldForgeStructure() {
        assertEquals("plain text, with symbols #[](){}éб", Canonical.escape("plain text, with symbols #[](){}éб"));
        assertEquals("a\\\\b", Canonical.escape("a\\b"));
        assertEquals("a\\tb\\nc\\rd", Canonical.escape("a\tb\nc\rd"));
        assertEquals("\\u0000\\u0001\\u001f\\u007f", Canonical.escape("\u0000\u0001\u001f\u007f"));
        assertEquals("", Canonical.escape(""));
    }

    @Test
    void escapeIsInjective() {
        // two different texts never share an escape: a literal backslash-n differs from a newline
        assertEquals("\\\\n", Canonical.escape("\\n"));
        assertEquals("\\n", Canonical.escape("\n"));
    }

    @Test
    void sha256MatchesTheTwoPublishedTestVectors() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Canonical.sha256Hex("abc"));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Canonical.sha256Hex(""));
    }

    @Test
    void sha256EncodesAsUtf8() {
        // sha256sum of the two octets C3 A9, the UTF-8 form of the letter e with an acute accent
        assertEquals("4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c", Canonical.sha256Hex("\u00e9"));
    }

    // review finding BM01: a lone surrogate and '?' had one digest because String.getBytes(UTF_8) replaces
    // the surrogate with '?'
    private static final String HIGH = String.valueOf((char) 0xD800);
    private static final String LOW = String.valueOf((char) 0xDC00);
    /** U+1F600 as its valid surrogate pair: one supplementary character, four UTF-8 octets. */
    private static final String PAIR = new String(Character.toChars(0x1F600));

    @Test
    void unpairedSurrogatesAreEscapedAndValidPairsAreKept() {
        assertEquals("\\ud800", Canonical.escape(HIGH));
        assertEquals("\\udc00", Canonical.escape(LOW));
        assertEquals("a\\ud800b", Canonical.escape("a" + HIGH + "b"));
        assertEquals("\\udc00\\ud800", Canonical.escape(LOW + HIGH), "a low before a high surrogate is not a pair");
        assertEquals("\\ud800" + PAIR, Canonical.escape(HIGH + PAIR), "a lone high surrogate before a valid pair");
        assertEquals(PAIR, Canonical.escape(PAIR));
        assertEquals("x" + PAIR + "\\ud800", Canonical.escape("x" + PAIR + HIGH), "a high surrogate at the very end");
    }

    @Test
    void distinctTextsKeepDistinctEscapesAndDigests() {
        List<String> texts = List.of(HIGH, LOW, "?", "\ufffd", "\\ud800", "\\\\ud800", "\\udc00", HIGH + LOW, PAIR,
                LOW + HIGH, HIGH + HIGH, "");
        Set<String> escapes = new HashSet<>();
        Set<String> digests = new HashSet<>();
        for (String text : texts) {
            String escaped = Canonical.escape(text);
            assertTrue(escapes.add(escaped), () -> "escape collision for " + escaped);
            assertTrue(digests.add(Canonical.sha256Hex(escaped)), () -> "digest collision for " + escaped);
        }
    }

    @Test
    void theDigestRefusesTextThatUtf8CannotEncodeInsteadOfReplacingIt() {
        IllegalArgumentException high = assertThrows(IllegalArgumentException.class, () -> Canonical.sha256Hex(HIGH));
        assertTrue(high.getMessage().contains("unpaired surrogate"), high.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Canonical.sha256Hex("ok" + LOW));
        assertThrows(IllegalArgumentException.class, () -> Canonical.utf8(LOW + HIGH));
        // the supplementary character is four octets F0 9F 98 80, not two replaced halves
        assertArrayEquals(new byte[]{(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80}, Canonical.utf8(PAIR));
        assertEquals(Canonical.sha256Hex(new byte[]{(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80}),
                Canonical.sha256Hex(PAIR));
    }
}
