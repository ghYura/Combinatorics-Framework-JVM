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

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/**
 * Unchecked carrier for one or more {@link Diagnostic}s. Thrown where an operation cannot continue
 * (a label that does not fit, a malformed frame); validation passes that want to report everything at once
 * collect diagnostics themselves and throw one exception holding all of them.
 */
public final class FrameworkException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    // Never Java-serialized (the architecture forbids native serialization); transient keeps the
    // serial lint honest without pretending Diagnostic is a wire type.
    private final transient List<Diagnostic> diagnostics;

    public FrameworkException(Diagnostic diagnostic) {
        this(List.of(Objects.requireNonNull(diagnostic, "diagnostic")), null);
    }

    public FrameworkException(Diagnostic diagnostic, Throwable cause) {
        this(List.of(Objects.requireNonNull(diagnostic, "diagnostic")), cause);
    }

    public FrameworkException(List<Diagnostic> diagnostics) {
        this(diagnostics, null);
    }

    private FrameworkException(List<Diagnostic> diagnostics, Throwable cause) {
        super(primaryOf(diagnostics).toString(), cause);
        this.diagnostics = List.copyOf(diagnostics);
    }

    private static Diagnostic primaryOf(List<Diagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.isEmpty()) {
            throw new IllegalArgumentException("a FrameworkException needs at least one diagnostic");
        }
        for (Diagnostic d : diagnostics) {
            if (d.severity().isError()) {
                return d;
            }
        }
        return diagnostics.getFirst();
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /** The first ERROR diagnostic, or the first diagnostic when none is an error. */
    public Diagnostic primary() {
        return primaryOf(diagnostics);
    }

    public ErrorCode code() {
        return primary().code();
    }
}
