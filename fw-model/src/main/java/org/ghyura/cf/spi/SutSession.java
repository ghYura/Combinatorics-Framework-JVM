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

package org.ghyura.cf.spi;

/** One sample's session with the system under test; closed after the sample whatever happened. */
public interface SutSession extends AutoCloseable {

    /**
     * Runs the candidate and reports what the system did. Returning {@code null} means no verdict (the sample is
     * BROKEN); an exception means the sample could not be judged (BROKEN) unless the plugin itself turns it into a
     * domain observation, which is the explicit oracle rule the architecture asks for.
     */
    DomainObservation evaluate(SutCandidate candidate, ExecutionContext context) throws Exception;

    @Override
    void close();
}
