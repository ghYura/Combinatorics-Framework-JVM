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

import org.ghyura.cf.model.SourceLocation;

import java.util.Objects;

/**
 * One cell that exists in the source: its 0-based column, how it was stored, the text that becomes its value,
 * and where it came from. A cell that is absent from the source is simply not present in its row; a cell that
 * exists but is empty is {@link CellKind#BLANK} or a {@link CellKind#TEXT} cell with empty text, and the
 * legacy parser gives each of those a key, so the model never conflates them with absence.
 */
public record InputCell(int column, CellKind kind, String text, SourceLocation location) {

    public InputCell {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(location, "location");
        if (column < 0) {
            throw new IllegalArgumentException("column must be >= 0: " + column);
        }
        if (kind == CellKind.BLANK && !text.isEmpty()) {
            throw new IllegalArgumentException("a blank cell has empty text");
        }
    }

    public static InputCell text(int column, String text, SourceLocation location) {
        return new InputCell(column, CellKind.TEXT, text, location);
    }

    public boolean isFormula() {
        return kind.isFormula();
    }
}
