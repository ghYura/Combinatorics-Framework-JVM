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

import org.ghyura.cf.model.SampleKey;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * What the executor hands a running sample. Every value belongs to this sample; nothing is ambient or global.
 */
public interface ExecutionContext {

    /** The sample's identity: run, candidate, attempt, repeat and environment. */
    SampleKey sample();

    /**
     * Cancelled when the sample's cooperative deadline passes or the run is cancelled. Plugins poll it; the executor
     * cannot stop code that does not.
     */
    Cancellation cancellation();

    /** Time left before the cooperative deadline; zero or negative once it has passed. */
    Duration remaining();

    /** Randomness derived from the run seed, plan, candidate, repeat and environment, never from scheduling. */
    RandomGenerator random();

    /** Where the sample's own messages go, bounded by {@code cf.execution.output.maxBytes}. */
    OutputCapture output();

    /**
     * Reserves memory from the run's budget before the plugin allocates it.
     *
     * @throws org.ghyura.cf.model.FrameworkException RESOURCE_LIMIT when the budget cannot cover it
     */
    MemoryReservation reserve(long bytes, String purpose);
}
