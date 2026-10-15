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

import java.util.List;
import java.util.Objects;

/**
 * One physical row of the FW_Seq sheet that has at least one non-blank cell. {@code ordinal} is the 0-based
 * position among all physical rows of the sheet, empty ones included: the legacy parser names the target of a
 * headless row after that position, so it is part of the meaning. The cells keep their text exactly as written
 * (no trimming); blank cells carry nothing and are not kept.
 */
public record SeqRow(int ordinal, List<LocatedText> cells) {

    public SeqRow {
        Objects.requireNonNull(cells, "cells");
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be >= 0: " + ordinal);
        }
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("a sequence row has at least one non-blank cell");
        }
        cells = List.copyOf(cells);
    }
}
