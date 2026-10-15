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
import org.ghyura.cf.model.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One constraint of a sidecar: a bond the legacy sieve evaluated with {@code row_violations}. A rule has exactly one
 * body; an optional context {@link #condition()} makes it inert on candidates where the condition does not hold.
 *
 * @param id          the rule's id as written, or null (reports then call it {@code <unnamed>})
 * @param polarity    what a holding body does: forbid removes the candidate, require removes it when the body fails
 * @param gate        which placements of the target sheets a pairs, sets or when body looks at
 * @param condition   the context guard, or null
 * @param body        the rule's one body
 * @param description the author's description; documentation, not part of the rule's meaning
 * @param location    where the rule was written
 */
public record Rule(String id, Polarity polarity, Gate gate, Condition condition, Body body, String description,
                   SourceLocation location) {

    /** The legacy name of a rule without an id. */
    public static final String UNNAMED = "<unnamed>";

    public enum Polarity {
        FORBID("forbid"), REQUIRE("require");

        private final String token;

        Polarity(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    public Rule {
        Objects.requireNonNull(polarity, "polarity");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(location, "location");
        if (id != null && id.isEmpty()) {
            throw new IllegalArgumentException("a rule id is not empty");
        }
    }

    /** The body of a rule: exactly one of the five legacy bond families. */
    public sealed interface Body {
    }

    /**
     * Enumerated tuples: the body holds for a placement combination when some entry names every target sheet with the
     * combination's value.
     *
     * @param sheets  the target sheets: written, or the entries' sheets in first-seen order
     * @param entries {sheet: value} objects in written order
     */
    public record Pairs(List<String> sheets, List<Map<String, RuleValue>> entries) implements Body {
        public Pairs {
            sheets = List.copyOf(sheets);
            List<Map<String, RuleValue>> copy = new ArrayList<>();
            for (Map<String, RuleValue> e : entries) {
                copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(e)));
            }
            entries = List.copyOf(copy);
        }
    }

    /** The cross product of per-sheet value sets: the body holds when every placement's value is in its sheet's set. */
    public record Sets(Map<String, List<RuleValue>> sets) implements Body {
        public Sets {
            Map<String, List<RuleValue>> copy = new LinkedHashMap<>();
            sets.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            sets = Collections.unmodifiableMap(copy);
        }
    }

    /**
     * A predicate over the parameters of the placed values.
     *
     * @param sheets     the sheets the predicate ranges over, in written order
     * @param text       the predicate as written
     * @param expression its parsed form
     */
    public record When(List<String> sheets, String text, Expr expression) implements Body {
        public When {
            sheets = List.copyOf(sheets);
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(expression, "expression");
        }
    }

    /**
     * A dependent allowed set: for each chosen source value listed in {@code allow}, the target selection must lie
     * within the allowed values.
     */
    public record Mapping(String source, String target, Map<String, List<RuleValue>> allow) implements Body {
        public Mapping {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Map<String, List<RuleValue>> copy = new LinkedHashMap<>();
            allow.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            allow = Collections.unmodifiableMap(copy);
        }
    }

    /** A condition as the body: require it to hold, or forbid it. Exempt from the two-sheet floor of bonds. */
    public record Assert(Condition condition) implements Body {
        public Assert {
            Objects.requireNonNull(condition, "condition");
        }
    }

    /** The id reports use. */
    public String name() {
        return id == null ? UNNAMED : id;
    }

    /** The sheets the body ranges over (legacy {@code _constraint_sheets}). */
    public List<String> targetSheets() {
        return switch (body) {
            case Assert a -> a.condition().sheets();
            case Mapping m -> List.of(m.source(), m.target());
            case When w -> w.sheets();
            case Sets s -> List.copyOf(s.sets().keySet());
            case Pairs p -> p.sheets();
        };
    }

    /** Every sheet the rule touches: its target sheets, then its condition's (legacy {@code _referenced_sheets}). */
    public List<String> referencedSheets() {
        List<String> out = new ArrayList<>(targetSheets());
        if (condition != null) {
            for (String s : condition.sheets()) {
                if (!out.contains(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** A deterministic text of the rule's meaning (id, polarity, gate, condition, body); the description is left out. */
    public String canonical() {
        StringBuilder out = new StringBuilder("rule\t").append(id == null ? "-" : Canonical.escape(id)).append('\t')
                .append(polarity.token()).append('\t').append(gate.canonical()).append('\t')
                .append(condition == null ? "-" : condition.canonical()).append('\t');
        switch (body) {
            case Assert a -> out.append("assert\t").append(a.condition().canonical());
            case Mapping m -> {
                out.append("mapping\t").append(Canonical.escape(m.source())).append('\t').append(Canonical.escape(m.target()));
                m.allow().forEach((k, v) -> out.append('\t').append(Canonical.escape(k)).append('=')
                        .append(new RuleValue.ListValue(v).canonical()));
            }
            case When w -> {
                out.append("when\t");
                w.sheets().forEach(s -> out.append(Canonical.escape(s)).append(','));
                out.append('\t').append(Canonical.escape(w.text()));
            }
            case Sets s -> {
                out.append("sets");
                s.sets().forEach((k, v) -> out.append('\t').append(Canonical.escape(k)).append('=')
                        .append(new RuleValue.ListValue(v).canonical()));
            }
            case Pairs p -> {
                out.append("pairs\t");
                p.sheets().forEach(s -> out.append(Canonical.escape(s)).append(','));
                for (Map<String, RuleValue> e : p.entries()) {
                    out.append('\t').append(new RuleValue.MapValue(e).canonical());
                }
            }
        }
        return out.toString();
    }
}
