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

import java.util.Objects;

/**
 * The deterministic identity of a logical candidate (D05): a SHA-256 digest, written as 64 lowercase
 * hexadecimal characters. How the digest is computed (schema, normalized plan, key dictionary,
 * source/template/plugin version, seed, coordinates) is the generation module's contract; this type only
 * guarantees the shape. Repeat, environment and retry are sample dimensions and never enter a candidate id.
 */
public record CandidateId(String hex) {

    public static final int HEX_LENGTH = 64;

    public CandidateId {
        Objects.requireNonNull(hex, "hex");
        if (hex.length() != HEX_LENGTH) {
            throw new IllegalArgumentException("a candidate id is " + HEX_LENGTH + " hex characters: '" + hex + "'");
        }
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new IllegalArgumentException("a candidate id is lowercase hexadecimal: '" + hex + "'");
            }
        }
    }

    @Override
    public String toString() {
        return hex;
    }
}
