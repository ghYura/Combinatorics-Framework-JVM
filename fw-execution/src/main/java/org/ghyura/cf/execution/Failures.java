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

package org.ghyura.cf.execution;

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.spi.SkipSample;
import org.ghyura.cf.spi.SutUnavailable;

import java.lang.reflect.InvocationTargetException;

/**
 * How a throwable that left the system under test becomes an outcome (architecture rule: domain status comes only from a
 * valid verdict; a missing verdict or an exception is BROKEN, an infrastructure failure INFRA_FAIL, a deliberate skip
 * SKIPPED). The deadline and cancellation outcomes are decided by the runner, not here.
 */
final class Failures {

    private Failures() {
    }

    static AttemptResult.Failure classify(Throwable thrown) {
        Throwable t = unwrap(thrown);
        if (t instanceof SkipSample skip) {
            return new AttemptResult.Failure(Outcome.SKIPPED, ErrorCode.EXEC_NOT_ATTEMPTED, "the system under test skipped the sample: "
                    + skip.getMessage());
        }
        if (t instanceof FrameworkException fe && !fe.diagnostics().isEmpty() && fe.diagnostics().getFirst().code() == ErrorCode.RESOURCE_LIMIT) {
            return new AttemptResult.Failure(Outcome.INFRA_FAIL, ErrorCode.RESOURCE_LIMIT, fe.getMessage());
        }
        if (t instanceof OutOfMemoryError) {
            return new AttemptResult.Failure(Outcome.INFRA_FAIL, ErrorCode.EXEC_OUT_OF_MEMORY, describe(t));
        }
        if (t instanceof SutUnavailable unavailable) {
            return new AttemptResult.Failure(Outcome.INFRA_FAIL, ErrorCode.EXEC_SESSION_FAILED, "the system under test could not be started: "
                    + unavailable.getMessage() + (unavailable.getCause() == null ? "" : " (" + unavailable.getCause() + ")"));
        }
        return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_SUT_EXCEPTION, describe(t));
    }

    static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current instanceof InvocationTargetException ite && ite.getCause() != null) {
            current = ite.getCause();
        }
        return current;
    }

    /** The exception's class and message, then up to three causes, then its first frames; no live object is kept. */
    static String describe(Throwable t) {
        StringBuilder text = new StringBuilder(256);
        text.append(t);
        Throwable cause = t.getCause();
        for (int i = 0; cause != null && i < 3; i++, cause = cause.getCause()) {
            text.append("; caused by ").append(cause);
        }
        StackTraceElement[] frames = t.getStackTrace();
        for (int i = 0; i < Math.min(3, frames.length); i++) {
            text.append(i == 0 ? " at " : " < ").append(frames[i]);
        }
        return text.toString();
    }
}
