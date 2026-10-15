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

package org.ghyura.cf.input.builder;

/**
 * The {@code FW_*} flag tokens of columns 2 and 3 of an {@code FW_Seq} row. Adapted from the legacy
 * {@code com.company.excel.Flag}: the {@link #token} is the literal text the engine reads.
 */
public enum Flag {
    EXCLUDE("FW_Exclude"),
    OPTIONAL("FW_Optional"),
    REUSE("FW_Reuse"),
    REUSE_TABLE_ONLY("FW_ReuseTableOnly"),
    HEADING("FW_Heading"),
    LAST_IN_QUEUE("FW_LastInQueue"),
    CONCATENATOR("FW_Concatenator");

    private final String token;

    Flag(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    @Override
    public String toString() {
        return token;
    }
}
