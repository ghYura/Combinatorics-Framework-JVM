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

/**
 * What happened to one executed sample (architecture.xml Observation). Only {@link #PASS} and
 * {@link #DOMAIN_FAIL} are verdicts about the system under test; the others say the sample could not be
 * judged and must never be counted as domain findings or become optimization winners.
 */
public enum Outcome {

    /** The SUT ran and the typed or FW_VAR verdict says success. */
    PASS(true),
    /** The SUT ran and a valid nonzero verdict, or an explicit oracle rule, says the behavior is wrong. */
    DOMAIN_FAIL(true),
    /** The candidate could not be judged: it did not compile, produced no verdict, or the observation is malformed. */
    BROKEN(false),
    /** The cooperative deadline passed before the SUT finished. */
    TIMEOUT(false),
    /** The framework or its environment failed (compiler service, allocation), not the candidate. */
    INFRA_FAIL(false),
    /** The candidate was deliberately not executed (excluded, deduplicated, budget). */
    SKIPPED(false),
    /** The run was cancelled before or while this sample ran. */
    CANCELLED(false);

    private final boolean domainVerdict;

    Outcome(boolean domainVerdict) {
        this.domainVerdict = domainVerdict;
    }

    /** True for {@link #PASS} and {@link #DOMAIN_FAIL}. */
    public boolean isDomainVerdict() {
        return domainVerdict;
    }
}
