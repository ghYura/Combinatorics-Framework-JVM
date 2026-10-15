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

package org.ghyura.cf.config;

import java.util.Locale;
import java.util.Objects;

/**
 * One declared setting: its name, what it means, and how its text is parsed. The set of keys is closed
 * ({@link ConfigKeys}); a {@code cf.*} name that is not declared is a validation error, never ignored.
 *
 * @param <T> the parsed value type
 */
public final class ConfigKey<T> {

    private final String name;
    private final String description;
    private final boolean secret;
    private final Parsers.Parser<T> parser;

    ConfigKey(String name, String description, boolean secret, Parsers.Parser<T> parser) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.secret = secret;
        this.parser = Objects.requireNonNull(parser, "parser");
        if (!name.startsWith("cf.")) {
            throw new IllegalArgumentException("target settings live under cf.*: " + name);
        }
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /** Secret values are redacted from every report. */
    public boolean secret() {
        return secret;
    }

    /**
     * The one environment variable that may supply this key: {@code CF_} plus the name without its
     * {@code cf.} prefix, upper-cased, dots as underscores ({@code cf.execution.workers} gives
     * {@code CF_EXECUTION_WORKERS}). Only declared aliases are read; other variables are ignored.
     */
    public String environmentAlias() {
        return "CF_" + name.substring("cf.".length()).toUpperCase(Locale.ROOT).replace('.', '_');
    }

    T parse(String raw) throws ConfigValueException {
        return parser.parse(raw);
    }

    /** Only ever applied to a value that this key's own parser produced, so the cast cannot fail. */
    @SuppressWarnings("unchecked")
    T cast(Object parsedValue) {
        return (T) parsedValue;
    }

    @Override
    public String toString() {
        return name;
    }
}
