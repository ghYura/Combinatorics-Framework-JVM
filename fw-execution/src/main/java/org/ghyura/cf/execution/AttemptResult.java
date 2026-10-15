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
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.spi.DomainObservation;

import java.util.Objects;

/** What one attempt produced before the deadline logic: a judged domain observation, or why there is none. */
sealed interface AttemptResult {

    /** The system gave an observation and the oracle judged it PASS or DOMAIN_FAIL. */
    record Verdict(DomainObservation observation, Outcome outcome) implements AttemptResult {
        public Verdict {
            Objects.requireNonNull(observation, "observation");
            if (!outcome.isDomainVerdict()) {
                throw new IllegalArgumentException("a verdict is PASS or DOMAIN_FAIL: " + outcome);
            }
        }
    }

    /** The sample could not be judged, or was deliberately not run. */
    record Failure(Outcome outcome, ErrorCode code, String message) implements AttemptResult {
        public Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            if (outcome.isDomainVerdict()) {
                throw new IllegalArgumentException("a failure is not a verdict: " + outcome);
            }
        }
    }
}
