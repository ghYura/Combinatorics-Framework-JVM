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

package org.ghyura.cf.model;

import java.util.Objects;

/**
 * Where a diagnostic or a spec element came from.
 *
 * <p>{@code origin} names the document (file path as given, URI, or a logical name such as
 * {@code "<builtin>"}); {@code line} and {@code column} are 1-based with 0 meaning "unknown"; {@code path}
 * is a position inside the document that is not a line (a JSON pointer, a workbook cell such as
 * {@code "Sheet1!B7"}, or a properties key). Any part may be empty. A location is a value: it carries no
 * reference to a live parser, workbook or file handle.</p>
 */
public record SourceLocation(String origin, int line, int column, String path) {

    /** No location known. */
    public static final SourceLocation NONE = new SourceLocation("", 0, 0, "");

    public SourceLocation {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(path, "path");
        if (line < 0) {
            throw new IllegalArgumentException("line must be >= 0 (0 = unknown): " + line);
        }
        if (column < 0) {
            throw new IllegalArgumentException("column must be >= 0 (0 = unknown): " + column);
        }
        if (column > 0 && line == 0) {
            throw new IllegalArgumentException("a column needs a line");
        }
    }

    public static SourceLocation of(String origin) {
        return new SourceLocation(origin, 0, 0, "");
    }

    public static SourceLocation of(String origin, int line) {
        return new SourceLocation(origin, line, 0, "");
    }

    public static SourceLocation of(String origin, int line, int column) {
        return new SourceLocation(origin, line, column, "");
    }

    /** A location addressed by a logical path rather than a line, e.g. a JSON pointer or a cell reference. */
    public static SourceLocation ofPath(String origin, String path) {
        return new SourceLocation(origin, 0, 0, path);
    }

    public boolean isKnown() {
        return !origin.isEmpty() || line > 0 || !path.isEmpty();
    }

    /** {@code origin[:line[:column]][ (path)]}, or {@code <unknown>} when nothing is known. */
    @Override
    public String toString() {
        if (!isKnown()) {
            return "<unknown>";
        }
        StringBuilder sb = new StringBuilder(origin);
        if (line > 0) {
            if (!sb.isEmpty()) {
                sb.append(':');
            }
            sb.append(line);
            if (column > 0) {
                sb.append(':').append(column);
            }
        }
        if (!path.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append('(').append(path).append(')');
        }
        return sb.toString();
    }
}
