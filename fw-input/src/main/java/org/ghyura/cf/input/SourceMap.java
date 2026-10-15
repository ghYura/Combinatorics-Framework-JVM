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

package org.ghyura.cf.input;

import com.fasterxml.jackson.core.JsonLocation;
import org.ghyura.cf.model.SourceLocation;

/**
 * Maps a position inside a text handed to the JSON parser back to the position in the original file. A JSON
 * document is parsed as is (identity mapping); a YAML row or a TOML array is cut out of a larger file, so its
 * first line starts at {@code baseColumn} of {@code baseLine} and later lines start at column 1.
 */
final class SourceMap {

    private final String origin;
    private final int baseLine;
    private final int baseColumn;

    SourceMap(String origin, int baseLine, int baseColumn) {
        this.origin = origin;
        this.baseLine = baseLine;
        this.baseColumn = baseColumn;
    }

    static SourceMap whole(String origin) {
        return new SourceMap(origin, 1, 1);
    }

    String origin() {
        return origin;
    }

    /** @param path a logical position such as a JSON pointer, or empty */
    SourceLocation at(JsonLocation relative, String path) {
        if (relative == null || relative.getLineNr() < 1) {
            return SourceLocation.ofPath(origin, path);
        }
        int line = baseLine + relative.getLineNr() - 1;
        int column = relative.getLineNr() == 1 ? baseColumn + Math.max(relative.getColumnNr(), 1) - 1
                : Math.max(relative.getColumnNr(), 1);
        return new SourceLocation(origin, line, column, path);
    }
}
