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

package org.ghyura.cf.constraints;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The columns of the assembled candidates in rendering order (ascending sheet key, as the legacy fw_final columns):
 * every mandatory and optional sheet that takes part. A column's index is its position for positional gates; every
 * value a multi-value row places shares it, as in the legacy sieve, where {@code pos} counted axes.
 */
public record ColumnLayout(List<Column> columns) {

    /** One column: the sheet, its key and whether it may be absent. */
    public record Column(String sheet, int sheetKey, boolean optional) {
        public Column {
            Objects.requireNonNull(sheet, "sheet");
        }
    }

    public ColumnLayout {
        columns = List.copyOf(columns);
        List<String> seen = new ArrayList<>();
        for (Column c : columns) {
            if (seen.contains(c.sheet())) {
                throw new IllegalArgumentException("a sheet is one column: " + c.sheet());
            }
            seen.add(c.sheet());
        }
    }

    public int size() {
        return columns.size();
    }

    /** The column of a sheet, or -1. */
    public int index(String sheet) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).sheet().equals(sheet)) {
                return i;
            }
        }
        return -1;
    }

    public Column column(int index) {
        return columns.get(index);
    }
}
