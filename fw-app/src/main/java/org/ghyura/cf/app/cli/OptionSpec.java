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

package org.ghyura.cf.app.cli;

import java.util.Objects;

/**
 * One command-line option of the frozen syntax. A {@code valueName} of {@code null} makes it a flag.
 *
 * @param name        the long name including the leading dashes, for example {@code --scenario}
 * @param valueName   placeholder for the value in help ({@code FILE}), or {@code null} for a flag
 * @param repeatable  whether the option may appear more than once
 */
public record OptionSpec(String name, String valueName, boolean repeatable, String description) {

    public OptionSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        if (!name.startsWith("--") || name.length() < 3 || name.indexOf('=') >= 0) {
            throw new IllegalArgumentException("an option is a long name such as --scenario: " + name);
        }
        if (valueName != null && valueName.isBlank()) {
            throw new IllegalArgumentException("valueName must be null for a flag or a real placeholder");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("an option needs a description: " + name);
        }
        if (repeatable && valueName == null) {
            throw new IllegalArgumentException("a flag cannot be repeatable: " + name);
        }
    }

    public boolean takesValue() {
        return valueName != null;
    }
}
