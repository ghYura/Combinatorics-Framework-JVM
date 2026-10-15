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

import java.util.List;
import java.util.Objects;

/**
 * A physical row of a sheet: the rows that exist in the source, including a row with no cells (a JSON
 * {@code []}, an XLSX row that only carries formatting). The legacy parser numbers FW_Seq rows by their position
 * among physical rows, so empty rows are kept; {@code sourceRow} is the 0-based row number in the source and is
 * only used for locations. Cells are in strictly increasing column order, as POI iterates them.
 */
public record InputRow(int sourceRow, List<InputCell> cells) {

    public InputRow {
        Objects.requireNonNull(cells, "cells");
        if (sourceRow < 0) {
            throw new IllegalArgumentException("sourceRow must be >= 0: " + sourceRow);
        }
        cells = List.copyOf(cells);
        int previous = -1;
        for (InputCell cell : cells) {
            if (cell.column() <= previous) {
                throw new IllegalArgumentException("cells of row " + sourceRow + " are not in strictly increasing column order");
            }
            previous = cell.column();
        }
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }
}
