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
 * The five-field identity of one committed observation (D05, A08): run, candidate, attempt, repeat index and
 * environment. {@code attempt} counts from 0 (the first try); {@code cf.execution.retry.maxAttempts} is the
 * number of attempts allowed. A K=1 run keeps {@code repeatIndex = 0} and the empty default environment.
 */
public record SampleKey(RunId runId, CandidateId candidateId, int attempt, int repeatIndex, String environmentId) {

    public SampleKey {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(environmentId, "environmentId");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0: " + attempt);
        }
        if (repeatIndex < 0) {
            throw new IllegalArgumentException("repeatIndex must be >= 0: " + repeatIndex);
        }
        if (!Identifiers.isEnvironmentToken(environmentId, SampleSlot.MAX_ENVIRONMENT_LENGTH)) {
            throw new IllegalArgumentException("an environment id is 0.." + SampleSlot.MAX_ENVIRONMENT_LENGTH
                    + " characters of [A-Za-z0-9._:-] (empty = default): '" + environmentId + "'");
        }
    }

    /** The slot this key belongs to: every attempt of one (run, candidate, repeat, environment) shares it. */
    public SampleSlot slot() {
        return new SampleSlot(runId, candidateId, repeatIndex, environmentId);
    }

    public SampleKey nextAttempt() {
        return new SampleKey(runId, candidateId, Math.addExact(attempt, 1), repeatIndex, environmentId);
    }
}
