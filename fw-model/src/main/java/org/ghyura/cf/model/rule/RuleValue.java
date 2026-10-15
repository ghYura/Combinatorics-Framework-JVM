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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A value written in a constraint sidecar (a parameter attribute, a value of a rule, an item of a declared order),
 * typed as JSON typed it. The legacy sieve compared these values with Python's {@code ==}, which never makes a
 * number equal to a text: a rule value {@code 2} does not match a cell whose text is {@code "2"}. Keeping the
 * type here keeps that behaviour.
 */
public sealed interface RuleValue {

    /** A text. */
    record Text(String value) implements RuleValue {
        public Text {
            Objects.requireNonNull(value, "value");
        }
    }

    /** An integer of any size (Python int). */
    record Int(BigInteger value) implements RuleValue {
        public Int {
            Objects.requireNonNull(value, "value");
        }

        public static Int of(long value) {
            return new Int(BigInteger.valueOf(value));
        }
    }

    /** A floating-point number (Python float). */
    record Real(double value) implements RuleValue {
    }

    /** true or false (Python bool, a kind of int). */
    record Bool(boolean value) implements RuleValue {
    }

    /** JSON null (Python None). */
    enum Null implements RuleValue {
        NULL
    }

    /** A list of values. */
    record ListValue(List<RuleValue> items) implements RuleValue {
        public ListValue {
            items = List.copyOf(items);
        }
    }

    /** An object: text keys in written order. */
    record MapValue(Map<String, RuleValue> entries) implements RuleValue {
        public MapValue {
            entries = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
    }

    /** Whether this is a single value: a text, number or boolean (the legacy _SCALAR_VALUE). */
    default boolean scalar() {
        return this instanceof Text || this instanceof Int || this instanceof Real || this instanceof Bool;
    }

    /** Python {@code value == text} for a cell text: only an equal {@link Text} is equal. */
    default boolean equalsText(String text) {
        return this instanceof Text t && t.value().equals(text);
    }

    /**
     * Python {@code ==} between two sidecar values: numbers (booleans included) compare by numeric value across
     * int and float, texts by content, lists element by element, objects by entries; values of different kinds are
     * unequal.
     */
    static boolean pyEquals(RuleValue a, RuleValue b) {
        if (a instanceof Text ta) {
            return b instanceof Text tb && ta.value().equals(tb.value());
        }
        if (isNumber(a) || isNumber(b)) {
            return isNumber(a) && isNumber(b) && compareNumbers(a, b) == 0;
        }
        if (a == Null.NULL) {
            return b == Null.NULL;
        }
        if (a instanceof ListValue la) {
            if (!(b instanceof ListValue lb) || la.items().size() != lb.items().size()) {
                return false;
            }
            for (int i = 0; i < la.items().size(); i++) {
                if (!pyEquals(la.items().get(i), lb.items().get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof MapValue ma) {
            if (!(b instanceof MapValue mb) || !ma.entries().keySet().equals(mb.entries().keySet())) {
                return false;
            }
            for (Map.Entry<String, RuleValue> e : ma.entries().entrySet()) {
                if (!pyEquals(e.getValue(), mb.entries().get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** Whether the value is a number for Python (int, float or bool). */
    static boolean isNumber(RuleValue v) {
        return v instanceof Int || v instanceof Real || v instanceof Bool;
    }

    /**
     * Exact numeric comparison of two numbers (Python compares int and float exactly, not by rounding the int).
     * NaN compares as unordered: this returns 2 for any comparison that involves NaN.
     */
    static int compareNumbers(RuleValue a, RuleValue b) {
        double da = a instanceof Real r ? r.value() : 0;
        double db = b instanceof Real r ? r.value() : 0;
        if ((a instanceof Real && Double.isNaN(da)) || (b instanceof Real && Double.isNaN(db))) {
            return 2;
        }
        if (a instanceof Real && Double.isInfinite(da) || b instanceof Real && Double.isInfinite(db)) {
            double xa = a instanceof Real ? da : 0;
            double xb = b instanceof Real ? db : 0;
            if (a instanceof Real && b instanceof Real) {
                return Double.compare(da, db) == 0 ? 0 : Double.compare(da, db) < 0 ? -1 : 1;
            }
            if (a instanceof Real) {
                return xa < 0 ? -1 : 1;
            }
            return xb < 0 ? 1 : -1;
        }
        return exact(a).compareTo(exact(b));
    }

    private static BigDecimal exact(RuleValue v) {
        return switch (v) {
            case Int i -> new BigDecimal(i.value());
            case Bool b -> b.value() ? BigDecimal.ONE : BigDecimal.ZERO;
            case Real r -> new BigDecimal(r.value());
            default -> throw new IllegalArgumentException("not a number: " + v);
        };
    }

    /** A deterministic, unambiguous text of the value and its type, for canonical forms and digests. */
    default String canonical() {
        return switch (this) {
            case Text t -> "s:" + Canonical.escape(t.value());
            case Int i -> "i:" + i.value();
            case Real r -> "f:" + Double.toString(r.value());
            case Bool b -> "b:" + b.value();
            case Null n -> "null";
            case ListValue l -> {
                StringBuilder out = new StringBuilder("[");
                for (int i = 0; i < l.items().size(); i++) {
                    out.append(i == 0 ? "" : ",").append(l.items().get(i).canonical());
                }
                yield out.append(']').toString();
            }
            case MapValue m -> {
                StringBuilder out = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<String, RuleValue> e : m.entries().entrySet()) {
                    out.append(first ? "" : ",").append(Canonical.escape(e.getKey())).append('=').append(e.getValue().canonical());
                    first = false;
                }
                yield out.append('}').toString();
            }
        };
    }

    /** How a report shows the value: a text as written, other values in their JSON form. */
    default String display() {
        return switch (this) {
            case Text t -> t.value();
            case Int i -> i.value().toString();
            case Real r -> Double.toString(r.value());
            case Bool b -> Boolean.toString(b.value());
            case Null n -> "null";
            case ListValue l -> {
                StringBuilder out = new StringBuilder("[");
                for (int i = 0; i < l.items().size(); i++) {
                    out.append(i == 0 ? "" : ", ").append(l.items().get(i).display());
                }
                yield out.append(']').toString();
            }
            case MapValue m -> m.entries().toString();
        };
    }
}
