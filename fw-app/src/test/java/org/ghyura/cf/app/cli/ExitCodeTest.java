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


package org.ghyura.cf.app.cli;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The exit code of a failure follows its first error (supplement to revision 2: cancellation by its own code). */
class ExitCodeTest {

    private static ExitCode exitFor(ErrorCode code) {
        return ExitCode.forFailure(List.of(Diagnostic.warning(ErrorCode.GEN_EMPTY_TABLE, "a warning first", SourceLocation.NONE),
                new Diagnostic(Severity.ERROR, code, "the error", SourceLocation.NONE, java.util.Map.of())));
    }

    @Test
    void cancellationAloneExitsCancelledAndNoOtherErrorInheritsIt() {
        assertEquals(ExitCode.CANCELLED, exitFor(ErrorCode.RUN_CANCELLED));
        for (ErrorCode code : ErrorCode.values()) {
            if (code != ErrorCode.RUN_CANCELLED) {
                assertNotEquals(ExitCode.CANCELLED, exitFor(code), code.name());
            }
        }
        assertEquals(ExitCode.FAILED, exitFor(ErrorCode.GEN_REPLACEMENT_INVALID));
        assertEquals(ExitCode.RESOURCE_LIMIT, exitFor(ErrorCode.RESOURCE_LIMIT));
        assertEquals(ExitCode.FAILED, ExitCode.forFailure(List.of()), "no error at all is a framework defect");
    }
}
