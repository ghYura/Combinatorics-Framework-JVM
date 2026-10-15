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

import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Helpers shared by the fw-model tests. */
public final class TestSupport {

    private TestSupport() {
    }

    /** Parses "FF 7F 00" into octets. Hand-written expectations are written this way so they can be read as hex. */
    public static byte[] hex(String spaced) {
        String trimmed = spaced.trim();
        if (trimmed.isEmpty()) {
            return new byte[0];
        }
        String[] parts = trimmed.split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    public static String hexOf(byte[] octets) {
        StringBuilder sb = new StringBuilder();
        for (byte b : octets) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /** Asserts the executable throws a {@link FrameworkException} whose primary diagnostic has this code. */
    public static FrameworkException expectCode(ErrorCode expected, Executable action) {
        FrameworkException thrown = assertThrows(FrameworkException.class, action);
        assertEquals(expected, thrown.code(), () -> "unexpected diagnostic: " + thrown.primary());
        return thrown;
    }
}
