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

import java.util.List;
import java.util.Objects;

/**
 * What an addon installed on {@code cf.execution.classpath} (a jar of SUT plugins, for instance) states about the
 * architecture's capability mapping, found with {@link java.util.ServiceLoader} in the loader over that class path. An
 * addon depends on fw-model alone, so it states its claims here rather than through fw-config's CapabilityProvider; the
 * application reports them as the addon's, only while the addon is installed.
 */
public interface AddonCapabilities {

    /**
     * One claim: a capability of the mapping (C01..C18), {@code implemented} or {@code partial}, and the evidence (the
     * tests that prove it, what is still missing).
     */
    record Statement(String capabilityId, String status, String evidence) {
        public Statement {
            Objects.requireNonNull(capabilityId, "capabilityId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    /** The addon's name, as reports show it. */
    String addon();

    List<Statement> statements();
}
