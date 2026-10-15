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

package org.ghyura.cf.model.rule;

import org.ghyura.cf.model.Canonical;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A scenario's constraint layer, as one or more sidecars wrote it (legacy constraints/sieve.py sidecar: params,
 * orders, constraints). Immutable. Values in it are the texts the author wrote; the sieve matched them against the
 * scenario's cell texts with their surrounding white space stripped, and so does the evaluator.
 *
 * @param sources the files the rules came from, in reading order, each with the SHA-256 of its bytes
 * @param params  sheet to value to attribute to value: what a {@code when} predicate reads as {@code SHEET.attribute}
 * @param orders  how the ordinal operators rank each sheet's values
 * @param rules   the rules in written order
 * @param bonds   lines of legacy compiled bonds files, in reading order
 */
public record ConstraintSet(List<Source> sources, Map<String, Map<String, Map<String, RuleValue>>> params,
                            Map<String, OrderSpec> orders, List<Rule> rules, List<LegacyBond> bonds) {

    /** No constraint at all. */
    public static final ConstraintSet NONE = new ConstraintSet(List.of(), Map.of(), Map.of(), List.of(), List.of());

    /** A file a constraint set was read from. */
    public record Source(String name, String sha256, String kind) {
        public Source {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(kind, "kind");
        }
    }

    public ConstraintSet {
        sources = List.copyOf(sources);
        Map<String, Map<String, Map<String, RuleValue>>> p = new LinkedHashMap<>();
        params.forEach((sheet, values) -> {
            Map<String, Map<String, RuleValue>> v = new LinkedHashMap<>();
            values.forEach((value, attrs) -> v.put(value, Collections.unmodifiableMap(new LinkedHashMap<>(attrs))));
            p.put(sheet, Collections.unmodifiableMap(v));
        });
        params = Collections.unmodifiableMap(p);
        orders = Collections.unmodifiableMap(new LinkedHashMap<>(orders));
        rules = List.copyOf(rules);
        bonds = List.copyOf(bonds);
    }

    public boolean isEmpty() {
        return rules.isEmpty() && params.isEmpty() && orders.isEmpty() && bonds.isEmpty();
    }

    /** This set followed by another one's rules, params and orders (a later value of the same key replaces an earlier). */
    public ConstraintSet plus(ConstraintSet other) {
        if (other.isEmpty() && other.sources().isEmpty()) {
            return this;
        }
        List<Source> s = new java.util.ArrayList<>(sources);
        s.addAll(other.sources());
        Map<String, Map<String, Map<String, RuleValue>>> p = new LinkedHashMap<>(params);
        other.params().forEach((sheet, values) -> {
            Map<String, Map<String, RuleValue>> merged = new LinkedHashMap<>(p.getOrDefault(sheet, Map.of()));
            merged.putAll(values);
            p.put(sheet, merged);
        });
        Map<String, OrderSpec> o = new LinkedHashMap<>(orders);
        o.putAll(other.orders());
        List<Rule> r = new java.util.ArrayList<>(rules);
        r.addAll(other.rules());
        List<LegacyBond> b = new java.util.ArrayList<>(bonds);
        b.addAll(other.bonds());
        return new ConstraintSet(s, p, o, r, b);
    }

    /** A deterministic text of the constraint layer's meaning (descriptions and file names left out). */
    public String canonicalText() {
        StringBuilder out = new StringBuilder("cf.constraints/1\n");
        params.forEach((sheet, values) -> values.forEach((value, attrs) -> {
            out.append("PARAM\t").append(Canonical.escape(sheet)).append('\t').append(Canonical.escape(value));
            attrs.forEach((k, v) -> out.append('\t').append(Canonical.escape(k)).append('=').append(v.canonical()));
            out.append('\n');
        }));
        orders.forEach((sheet, order) -> out.append("ORDER\t").append(Canonical.escape(sheet)).append('\t')
                .append(order.canonical()).append('\n'));
        for (Rule rule : rules) {
            out.append(rule.canonical()).append('\n');
        }
        for (LegacyBond bond : bonds) {
            out.append(bond.canonical()).append('\n');
        }
        return out.toString();
    }

    public String contentDigest() {
        return Canonical.sha256Hex(canonicalText());
    }
}
