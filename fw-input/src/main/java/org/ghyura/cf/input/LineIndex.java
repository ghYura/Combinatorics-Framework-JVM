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

import org.ghyura.cf.model.SourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Offset to 1-based line and column of a text; a line ends at LF, CR LF or a lone CR. */
final class LineIndex {

    private final String origin;
    private final int[] lineStarts;

    LineIndex(String origin, String text) {
        this.origin = origin;
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n'))) {
                starts.add(i + 1);
            }
        }
        lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    int lineOf(int offset) {
        int low = 0;
        int high = lineStarts.length - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (lineStarts[mid] <= offset) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low + 1;
    }

    int columnOf(int offset) {
        return offset - lineStarts[lineOf(offset) - 1] + 1;
    }

    SourceLocation at(int offset, String path) {
        return new SourceLocation(origin, lineOf(offset), columnOf(offset), path);
    }

    SourceMap mapFrom(int offset) {
        return new SourceMap(origin, lineOf(offset), columnOf(offset));
    }
}
