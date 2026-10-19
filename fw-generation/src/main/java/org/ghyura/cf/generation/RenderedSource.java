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

package org.ghyura.cf.generation;

import org.ghyura.cf.model.SourceLocation;

import java.util.List;
import java.util.Objects;

/** A read-only source view. Offsets are UTF-16 (the same units used by Swing), with exclusive ends. */
public record RenderedSource(String text, List<Chunk> chunks, boolean truncated) {
    public RenderedSource {
        Objects.requireNonNull(text, "text");
        chunks = List.copyOf(chunks);
        int end = 0;
        for (Chunk chunk : chunks) {
            if (chunk.start() < end || chunk.end() > text.length()) {
                throw new IllegalArgumentException("source chunks must be ordered, disjoint and within the text");
            }
            end = chunk.end();
        }
    }

    public enum Kind { VALUE, PREFIX, SHEET_SEPARATOR, VALUE_SEPARATOR }

    /**
     * The original sheet and location, and the assembly column that emitted it. These can differ after a join or reuse.
     * Occurrence is the 1-based position within that column's row (zero for a sheet decoration).
     * A repeated value retains a separate occurrence. A marker spanning several cells retains every contributor.
     */
    public record Origin(String sheet, String assembledSheet, Integer label, int occurrence, boolean optional,
                         Kind kind, SourceLocation location, String detail) {
        public Origin {
            Objects.requireNonNull(sheet);
            Objects.requireNonNull(assembledSheet);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(location);
            Objects.requireNonNull(detail);
        }

        Origin marker() {
            return new Origin(sheet, assembledSheet, label, occurrence, optional, kind, location,
                    "candidate id substituted for marker" + (detail.isEmpty() ? "" : "; " + detail));
        }
    }

    public record Chunk(int start, int end, List<Origin> origins) {
        public Chunk {
            if (start < 0 || end <= start) throw new IllegalArgumentException("empty or negative source span");
            origins = List.copyOf(origins);
        }
    }
}
