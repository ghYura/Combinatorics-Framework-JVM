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

package org.ghyura.cf.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One finding about an input, a configuration or a run: what, how serious, where, and the machine-readable
 * facts behind it (for example {@code expected}/{@code actual} key counts). Immutable; attributes keep
 * insertion order so rendered reports are deterministic.
 */
public record Diagnostic(Severity severity,
                         ErrorCode code,
                         String message,
                         SourceLocation location,
                         Map<String, String> attributes) {

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(attributes, "attributes");
        if (message.isBlank()) {
            throw new IllegalArgumentException("a diagnostic needs a message");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        attributes.forEach((k, v) -> copy.put(Objects.requireNonNull(k, "attribute name"),
                Objects.requireNonNull(v, "attribute value for " + k)));
        attributes = Collections.unmodifiableMap(copy);
    }

    /**
     * @param attributePairs alternating attribute names and values; the length must be even
     */
    public static Diagnostic of(Severity severity, ErrorCode code, String message, SourceLocation location,
                                String... attributePairs) {
        if (attributePairs.length % 2 != 0) {
            throw new IllegalArgumentException("attribute pairs must be name,value,name,value...");
        }
        Map<String, String> attrs = new LinkedHashMap<>();
        for (int i = 0; i < attributePairs.length; i += 2) {
            if (attrs.put(attributePairs[i], attributePairs[i + 1]) != null) {
                throw new IllegalArgumentException("duplicate attribute " + attributePairs[i]);
            }
        }
        return new Diagnostic(severity, code, message, location, attrs);
    }

    public static Diagnostic error(ErrorCode code, String message, SourceLocation location, String... attributePairs) {
        return of(Severity.ERROR, code, message, location, attributePairs);
    }

    public static Diagnostic warning(ErrorCode code, String message, SourceLocation location, String... attributePairs) {
        return of(Severity.WARNING, code, message, location, attributePairs);
    }

    public static Diagnostic info(ErrorCode code, String message, SourceLocation location, String... attributePairs) {
        return of(Severity.INFO, code, message, location, attributePairs);
    }

    /** {@code SEVERITY CODE [location]: message {name=value, ...}} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity).append(' ').append(code);
        if (location.isKnown()) {
            sb.append(" [").append(location).append(']');
        }
        sb.append(": ").append(message);
        if (!attributes.isEmpty()) {
            sb.append(" {");
            boolean first = true;
            for (Map.Entry<String, String> e : attributes.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            sb.append('}');
        }
        return sb.toString();
    }
}
