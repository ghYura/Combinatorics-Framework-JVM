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

import org.ghyura.cf.model.SourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * A data sheet: its name, its key (1..S in workbook order; the sheet namespace, distinct from value labels)
 * and its values in sheet order. A sheet without values is legal here; whether it may take part in a plan is
 * decided by plan validation, not by the reader.
 */
public record SheetSpec(String name, int key, List<ValueCell> cells, SourceLocation location) {

    public SheetSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(location, "location");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a sheet needs a name");
        }
        if (key < 1) {
            throw new IllegalArgumentException("sheet keys start at 1: " + key);
        }
        cells = List.copyOf(cells);
    }

    /** How many FW_VAR = FW_EXIT_CODE cells the sheet holds; the exit code they carry is {@link #key()}. */
    public int exitCodeVarCount() {
        int count = 0;
        for (ValueCell cell : cells) {
            if (cell.kind() == ValueKind.EXIT_CODE_VAR) {
                count++;
            }
        }
        return count;
    }
}
