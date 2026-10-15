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

import java.util.Objects;

/**
 * One value of a data sheet: the payload the dictionary will give a key. {@code optional} and {@code refined}
 * are the two flags the legacy parser kept in its dictionary table (FW_Optional marks the value that comes
 * before it, FW_RefineCodeExceptQuoted rewrites it); here they are immutable facts of the spec, not database
 * updates.
 */
public record ValueCell(String text, ValueKind kind, boolean optional, boolean refined, SourceLocation location) {

    public ValueCell {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
    }

    public static ValueCell of(String text, ValueKind kind, SourceLocation location) {
        return new ValueCell(text, kind, false, false, location);
    }

    public ValueCell asOptional() {
        return new ValueCell(text, kind, true, refined, location);
    }

    /** The same value with its text replaced by the refined text and the refined flag set. */
    public ValueCell refinedTo(String refinedText) {
        return new ValueCell(refinedText, kind, optional, true, location);
    }
}
