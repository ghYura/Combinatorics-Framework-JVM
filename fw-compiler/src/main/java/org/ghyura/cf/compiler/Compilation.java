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

package org.ghyura.cf.compiler;

import java.util.List;
import java.util.Objects;

/** The result of compiling one source: classes, or the reasons it did not compile, or no compiler at all. */
public sealed interface Compilation {

    /** The source compiled; {@code cached} tells whether the bytecode came from the cache. */
    record Compiled(CompiledClasses classes, boolean cached) implements Compilation {
        public Compiled {
            Objects.requireNonNull(classes, "classes");
        }
    }

    /** The source did not compile; the compiler's messages, each with its line and column, bounded in number and length. */
    record Failed(List<String> messages) implements Compilation {
        public Failed {
            messages = List.copyOf(messages);
        }
    }

    /** This runtime has no system Java compiler (a JRE image without jdk.compiler). */
    record Unavailable(String reason) implements Compilation {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
