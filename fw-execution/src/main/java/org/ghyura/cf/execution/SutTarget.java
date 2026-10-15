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

import org.ghyura.cf.spi.SutCandidate;

import java.util.Map;

/**
 * What a run executes candidates against: a typed plugin or generated Java. An attempt runs on the calling thread and
 * reports a verdict or a failure; it does not throw for anything the system under test does.
 */
public sealed interface SutTarget extends AutoCloseable permits PluginTarget, GeneratedJavaTarget {

    /** The plugin id, or {@code generated-java}. */
    String id();

    String version();

    /** Names of the domain verdict codes, from the plugin or the FW_CUSTOM_VAR sheet. */
    Map<Integer, String> verdictNames();

    /** SHA-256 of what runs for this candidate: its generated source, or the plugin's id and version. */
    String sourceDigest(SutCandidate candidate);

    /**
     * Work done for an attempt before its deadline starts, such as compiling the candidate: framework work, not the system
     * under test. Its result is either a failure that ends the attempt or what {@link #attempt} needs.
     */
    default Prepared prepare(SutCandidate candidate) {
        return Prepared.NOTHING;
    }

    /** One attempt of one sample, under the deadline. */
    AttemptResult attempt(SutCandidate candidate, Prepared prepared, SampleContext context);

    /** What {@link #prepare} produced: a payload for the attempt, or the failure that ends it. */
    record Prepared(Object payload, AttemptResult.Failure failure) {
        static final Prepared NOTHING = new Prepared(null, null);

        static Prepared failed(AttemptResult.Failure failure) {
            return new Prepared(null, failure);
        }
    }

    /** Releases what the target holds for the run (class loaders, caches). */
    @Override
    void close();

    /** Whether the legacy {@code app=} metrics line of the candidate's output becomes metrics (generated Java, audit EX07). */
    default boolean harvestsMetricLines() {
        return false;
    }
}
