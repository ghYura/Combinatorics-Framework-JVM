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

import org.ghyura.cf.model.Severity;
import java.util.List;
import java.util.Objects;

/** Optional read-only environment diagnostics supplied by an installed SUT add-on. No sample is opened or evaluated. */
public interface SutDoctor {
    record Check(String name, Severity severity, String message, List<String> details) {
        public Check {
            Objects.requireNonNull(name); Objects.requireNonNull(severity); Objects.requireNonNull(message);
            details = List.copyOf(details);
        }
    }

    /** Sources are an explicitly bounded preview, never code to execute. Preprocessing and candidate imports must not run. */
    List<Check> diagnose(RunInputs inputs, List<String> sources) throws Exception;
}
