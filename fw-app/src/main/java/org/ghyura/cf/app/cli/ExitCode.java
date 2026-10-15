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

import java.util.List;

/**
 * The frozen process exit codes. Domain findings (DOMAIN_FAIL samples) are results, not failures: a run that
 * completes exits 0 however many defects it found.
 */
public enum ExitCode {

    OK(0, "The command completed. For run: the run reached COMPLETED."),
    FAILED(1, "The run reached FAILED, or the command failed unexpectedly (a framework defect)."),
    USAGE(2, "The command line is invalid: unknown command or option, missing or malformed value."),
    VALIDATION_FAILED(3, "Configuration, scenario or plan validation found errors; no run entered RUNNING."),
    UNSUPPORTED_CAPABILITY(4, "The request needs a capability this build does not provide."),
    RESOURCE_LIMIT(5, "A configured budget stopped the work explicitly; nothing was dropped silently."),
    CANCELLED(6, "The run was cancelled."),
    INTERRUPTED(130, "The process received an interrupt signal (SIGINT convention: 128 + 2).");

    private final int code;
    private final String meaning;

    ExitCode(int code, String meaning) {
        this.code = code;
        this.meaning = meaning;
    }

    public int code() {
        return code;
    }

    public String meaning() {
        return meaning;
    }

    /**
     * The exit code for a failed command, decided by its first ERROR diagnostic: cancellation by its own code
     * (supplement to revision 2: a later RUN error must not inherit CANCELLED), everything else by its category.
     */
    public static ExitCode forFailure(List<Diagnostic> diagnostics) {
        for (Diagnostic d : diagnostics) {
            if (d.severity().isError()) {
                return d.code() == ErrorCode.RUN_CANCELLED ? CANCELLED : forCategory(d.code().category());
            }
        }
        return FAILED;
    }

    private static ExitCode forCategory(ErrorCode.Category category) {
        return switch (category) {
            case USAGE -> USAGE;
            case CONFIG, KEY, INPUT, SPEC, PLAN, CONSTRAINT, CHECKPOINT -> VALIDATION_FAILED;
            case ANALYSIS, CAMPAIGN -> FAILED;
            case GENERATION, ASSEMBLY -> FAILED;
            case CAPABILITY -> UNSUPPORTED_CAPABILITY;
            case RESOURCE -> RESOURCE_LIMIT;
            case INTERNAL, RUN, EXECUTION, RESULT -> FAILED;
        };
    }
}
