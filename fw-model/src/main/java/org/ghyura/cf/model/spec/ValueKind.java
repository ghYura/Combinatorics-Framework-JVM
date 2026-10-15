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

package org.ghyura.cf.model.spec;

/** How a value cell of a data sheet came to be; part of its logical identity and of its provenance. */
public enum ValueKind {
    /** An ordinary value: a string, number, boolean, error or blank cell, rendered as its text. */
    TEXT('t'),
    /** A formula cell: the value is the formula source, which the framework never evaluates. */
    FORMULA('f'),
    /** The content of the file named by an {@code FW_File=} cell. */
    FILE('F'),
    /** An {@code FW_EMPTY_STRING} cell: the empty value. */
    EMPTY('e'),
    /** An {@code FW_VAR = FW_EXIT_CODE} cell: the text has every FW_EXIT_CODE replaced by the sheet's key. */
    EXIT_CODE_VAR('x');

    private final char code;

    ValueKind(char code) {
        this.code = code;
    }

    /** One stable character for canonical text forms. */
    public char code() {
        return code;
    }
}
