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

import java.util.Optional;

/**
 * Which {@link KeyEncoding} a plan uses (cf.keys.encoding). The choice is made once for the whole immutable
 * plan, from a census of every cell, sheet and virtual key, and never changes mid-row or mid-run (B04).
 */
public enum KeyEncodingPolicy {

    /** SIGNED_BYTE when every label fits -127..127, otherwise PACKED_LEGACY16. The default full-parity mode. */
    AUTO_PRESERVE,

    /** Strict compact mode: validation fails before execution for any label outside -127..127 (B05). */
    SIGNED_BYTE,

    /** Always two octets per key, regardless of how small the labels are. */
    PACKED_LEGACY16;

    /** Exact, case-sensitive match of the property value; no aliases. */
    public static Optional<KeyEncodingPolicy> parse(String text) {
        for (KeyEncodingPolicy p : values()) {
            if (p.name().equals(text)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
