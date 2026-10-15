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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A command line that passed the syntax rules: the command, its option values and flags. */
public final class ParsedCommand {

    private final CommandSpec spec;
    private final Map<String, List<String>> values;
    private final Set<String> flags;

    ParsedCommand(CommandSpec spec, Map<String, List<String>> values, Set<String> flags) {
        this.spec = Objects.requireNonNull(spec, "spec");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        values.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.values = Collections.unmodifiableMap(copy);
        this.flags = Set.copyOf(flags);
    }

    public CommandSpec spec() {
        return spec;
    }

    /** The single value of a non-repeatable option, if it was given. */
    public Optional<String> value(String option) {
        List<String> all = values.get(option);
        return all == null || all.isEmpty() ? Optional.empty() : Optional.of(all.getLast());
    }

    /** All values of a repeatable option, in command-line order; empty when it was not given. */
    public List<String> values(String option) {
        List<String> all = values.get(option);
        return all == null ? List.of() : new ArrayList<>(all);
    }

    public boolean flag(String option) {
        return flags.contains(option);
    }

    /** The validated {@code --format}, text when not given. */
    public OutputFormat format() {
        return value("--format").flatMap(OutputFormat::parse).orElse(OutputFormat.TEXT);
    }
}
