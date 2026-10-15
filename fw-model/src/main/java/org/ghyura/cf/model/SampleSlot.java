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
 * The place where one measurement lives: run, candidate, repeat index and environment. Several attempts
 * (retries) of the same slot can exist; the highest attempt is the final one and earlier attempts are kept
 * (architecture.xml, results rules). {@code environmentId} is empty for the default environment.
 */
public record SampleSlot(RunId runId, CandidateId candidateId, int repeatIndex, String environmentId) {

    public static final int MAX_ENVIRONMENT_LENGTH = 64;

    public SampleSlot {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(environmentId, "environmentId");
        if (repeatIndex < 0) {
            throw new IllegalArgumentException("repeatIndex must be >= 0: " + repeatIndex);
        }
        if (!Identifiers.isEnvironmentToken(environmentId, MAX_ENVIRONMENT_LENGTH)) {
            throw new IllegalArgumentException("an environment id is 0.." + MAX_ENVIRONMENT_LENGTH
                    + " characters of [A-Za-z0-9._:-] (empty = default): '" + environmentId + "'");
        }
    }

    /** The slot of a plain K=1 run: repeat 0, default environment. */
    public static SampleSlot first(RunId runId, CandidateId candidateId) {
        return new SampleSlot(runId, candidateId, 0, "");
    }

    public SampleKey attempt(int attempt) {
        return new SampleKey(runId, candidateId, attempt, repeatIndex, environmentId);
    }
}
