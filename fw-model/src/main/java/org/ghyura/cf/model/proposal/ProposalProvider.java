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

package org.ghyura.cf.model.proposal;

import java.util.List;
import java.util.Objects;

/** Local value proposal boundary; values are frozen before planning or execution. */
public interface ProposalProvider {
    record Identity(String id, String version, String contentDigest) {
        public Identity {
            Objects.requireNonNull(id); Objects.requireNonNull(version); Objects.requireNonNull(contentDigest);
            if (id.isBlank() || version.isBlank() || !contentDigest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("provider identity needs id, version and SHA-256 content digest");
        }
    }
    record Request(String slot, String role, String context, int count, long seed) {
        public Request {
            Objects.requireNonNull(slot); Objects.requireNonNull(role); Objects.requireNonNull(context);
            if (slot.isBlank() || count < 0) throw new IllegalArgumentException("nonblank slot and nonnegative count required");
        }
    }
    Identity identity();
    /** Ordered proposals; duplicate and empty values are meaningful. No implicit remote fallback. */
    List<String> propose(Request request);
}
