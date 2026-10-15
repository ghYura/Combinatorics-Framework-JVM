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

import java.util.List;
import java.util.Objects;

/**
 * An ordered sheet of physical rows. Sheets whose name starts with {@code FW_} are control sheets; all others
 * are data sheets (decided by the interpreter, not here). The name is kept exactly as the source gave it.
 */
public record InputSheet(String name, SourceLocation location, List<InputRow> rows) {

    public InputSheet {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rows, "rows");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a sheet needs a name");
        }
        rows = List.copyOf(rows);
        int previous = -1;
        for (InputRow row : rows) {
            if (row.sourceRow() <= previous) {
                throw new IllegalArgumentException("rows of sheet '" + name + "' are not in strictly increasing order");
            }
            previous = row.sourceRow();
        }
    }

    public boolean isControl() {
        return name.startsWith("FW_");
    }

    /** Number of cells across all rows. */
    public int cellCount() {
        int count = 0;
        for (InputRow row : rows) {
            count += row.cells().size();
        }
        return count;
    }
}
