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

package org.ghyura.cf.model.input;

/**
 * How the source stored a cell. The cell's {@link InputCell#text() text} is always what the legacy reader
 * would use as its value: the string itself, the text POI's DataFormatter renders for a number, boolean or
 * error, an empty string for a blank, and the formula text (not its result) for a formula.
 *
 * <p>Downstream only two distinctions matter (the census predicate of the legacy parser): a formula never
 * counts as a directive, and only a {@link #TEXT} cell can be an {@code FW_} directive. JSON, YAML, TOML and
 * the Java builder produce {@link #TEXT} cells only, exactly as the legacy adapters wrote every value as a
 * string cell.</p>
 */
public enum CellKind {
    /** A string cell; the only kind whose text can be an FW_ directive. */
    TEXT,
    /** A numeric cell, rendered with the General or style format. */
    NUMBER,
    /** A boolean cell, rendered TRUE or FALSE. */
    BOOLEAN,
    /** A formula cell; the text is the formula source, never its evaluated result. */
    FORMULA,
    /** A cell that exists but holds no value; the text is empty. */
    BLANK,
    /** An error cell such as #DIV/0!. */
    ERROR;

    /** True for the one kind that is not a plain value: formulas are kept distinct in the logical model. */
    public boolean isFormula() {
        return this == FORMULA;
    }
}
