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

package org.ghyura.cf.spi;

import org.ghyura.cf.model.key.SelectionState;

import java.util.List;
import java.util.Objects;

/**
 * One sheet of a candidate: whether it is present, absent or a baseline reference, and the value texts of the row it
 * stands for, in row order. An absent sheet has no values; a baseline reference carries the baseline row's values; a
 * present row may be empty.
 */
public record SheetSelection(String sheet, SelectionState state, List<String> values) {

    public SheetSelection {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(state, "state");
        values = List.copyOf(values);
        if (state == SelectionState.ABSENT && !values.isEmpty()) {
            throw new IllegalArgumentException("an absent sheet has no values: " + sheet);
        }
    }

    /** Whether the sheet takes part in the candidate (present or a baseline reference). */
    public boolean inCandidate() {
        return state != SelectionState.ABSENT;
    }
}
