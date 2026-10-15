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

import org.ghyura.cf.spi.DomainObservation;

import java.util.Objects;
import java.util.Optional;

/**
 * One committed attempt of one sample (architecture.xml Observation): its outcome, the domain observation when the
 * system gave a verdict, how long it took, the digests of the source and the execution policy, and, when it could not be
 * judged, a stable failure code with a bounded message. It holds no live object (no Throwable, no class), so equal
 * observations compare equal and a replayed commit can be recognised.
 *
 * @param key            the sample: run, candidate, attempt, repeat, environment
 * @param display        the candidate's legacy display id (finalId_optId_size, the legacy result columns combi_id_final,
 *                       combi_id_optional and fw_optJ)
 * @param outcome        one of the seven outcomes
 * @param domain         the domain observation; present exactly for PASS and DOMAIN_FAIL
 * @param elapsedNanos   wall time of the attempt
 * @param sourceDigest   SHA-256 of the executed source (generated Java) or of the plugin's id and version
 * @param policyDigest   SHA-256 of the execution policy the attempt ran under
 * @param failure        why the sample could not be judged (or was not run); empty for PASS and DOMAIN_FAIL
 * @param failureMessage a bounded human-readable detail of the failure, or empty
 * @param stdout         the sample's captured standard output (bounded)
 * @param stderr         the sample's captured error output (bounded)
 * @param truncated      whether captured output was cut at the bound
 */
public record Observation(SampleKey key, String display, Outcome outcome, Optional<DomainObservation> domain, long elapsedNanos, String sourceDigest,
                          String policyDigest, Optional<ErrorCode> failure, String failureMessage, String stdout, String stderr,
                          boolean truncated) {

    /** The longest failure message kept, in characters. */
    public static final int MAX_MESSAGE = 4096;

    public Observation {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        Objects.requireNonNull(policyDigest, "policyDigest");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(failureMessage, "failureMessage");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        if (elapsedNanos < 0) {
            throw new IllegalArgumentException("negative elapsed time: " + elapsedNanos);
        }
        if (outcome.isDomainVerdict() != domain.isPresent()) {
            throw new IllegalArgumentException("a domain observation belongs to PASS and DOMAIN_FAIL only: " + outcome);
        }
        if (outcome.isDomainVerdict() == failure.isPresent()) {
            throw new IllegalArgumentException(outcome.isDomainVerdict() ? "a verdict carries no failure code"
                    : "an outcome without a verdict needs a failure code: " + outcome);
        }
        if (failureMessage.length() > MAX_MESSAGE) {
            failureMessage = failureMessage.substring(0, MAX_MESSAGE);
        }
    }

    /** The domain verdict code, if the system gave one. */
    public Optional<Integer> verdictCode() {
        return domain.map(DomainObservation::verdictCode);
    }

    /** The slot every attempt of this sample shares. */
    public SampleSlot slot() {
        return key.slot();
    }
}
