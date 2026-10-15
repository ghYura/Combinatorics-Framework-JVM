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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The boolean condition language of the legacy sieve ({@code _eval_condition}): a node ({@code all}, {@code any},
 * {@code not}) or a leaf that tests one sheet's selection, the list of values the candidate holds for that sheet
 * (empty when the sheet is absent). Used as a rule's context guard ({@code condition}) and as an assertion body.
 */
public sealed interface Condition {

    /**
     * The empty condition {@code {}} where the legacy grammar allows one (a child of all/any/not, the operand of not):
     * it always holds, as {@code _eval_condition({})} did.
     */
    enum Always implements Condition {
        ALWAYS
    }

    /** Every child holds. */
    record All(List<Condition> children) implements Condition {
        public All {
            children = List.copyOf(children);
            if (children.isEmpty()) {
                throw new IllegalArgumentException("'all' needs at least one condition");
            }
        }
    }

    /** At least one child holds. */
    record Any(List<Condition> children) implements Condition {
        public Any {
            children = List.copyOf(children);
            if (children.isEmpty()) {
                throw new IllegalArgumentException("'any' needs at least one condition");
            }
        }
    }

    /** The child does not hold. */
    record Not(Condition child) implements Condition {
        public Not {
            Objects.requireNonNull(child, "child");
        }
    }

    /** The leaf operators, by the kind of operand each takes. */
    enum Op {
        EQ("eq", Kind.VALUE), NE("ne", Kind.VALUE), HAS("has", Kind.VALUE), HASNT("hasnt", Kind.VALUE),
        IN("in", Kind.VALUES), NIN("nin", Kind.VALUES), HAS_ANY("hasAny", Kind.VALUES),
        PRESENT("present", Kind.FLAG),
        EQ_SHEET("eqSheet", Kind.SHEET), NE_SHEET("neSheet", Kind.SHEET), SUB_OF("subOf", Kind.SHEET), SUP_OF("supOf", Kind.SHEET),
        GE("ge", Kind.ORDINAL), GT("gt", Kind.ORDINAL), LE("le", Kind.ORDINAL), LT("lt", Kind.ORDINAL),
        GE_SHEET("geSheet", Kind.ORDINAL_SHEET), GT_SHEET("gtSheet", Kind.ORDINAL_SHEET), LE_SHEET("leSheet", Kind.ORDINAL_SHEET),
        LT_SHEET("ltSheet", Kind.ORDINAL_SHEET),
        COUNT("count", Kind.COUNT), COUNT_GE("countGe", Kind.COUNT), COUNT_LE("countLe", Kind.COUNT);

        /** What the operator's operand is. */
        public enum Kind { VALUE, VALUES, FLAG, SHEET, ORDINAL, ORDINAL_SHEET, COUNT }

        private final String token;
        private final Kind kind;

        Op(String token, Kind kind) {
            this.token = token;
            this.kind = kind;
        }

        public String token() {
            return token;
        }

        public Kind kind() {
            return kind;
        }

        /** The operator written as {@code token}, or null. */
        public static Op ofToken(String token) {
            for (Op op : values()) {
                if (op.token.equals(token)) {
                    return op;
                }
            }
            return null;
        }
    }

    /**
     * One test of one sheet's selection.
     *
     * @param sheet  the sheet whose selection is tested
     * @param op     the operator
     * @param value  the operand of VALUE and ORDINAL operators, else null
     * @param values the operand of VALUES operators, else empty
     * @param other  the other sheet of SHEET and ORDINAL_SHEET operators, else null
     * @param count  the operand of COUNT operators (a Python int, so any size)
     * @param flag   the operand of {@code present}
     */
    record Leaf(String sheet, Op op, RuleValue value, List<RuleValue> values, String other, java.math.BigInteger count,
                boolean flag) implements Condition {
        public Leaf {
            Objects.requireNonNull(sheet, "sheet");
            Objects.requireNonNull(op, "op");
            values = List.copyOf(values);
            switch (op.kind()) {
                case VALUE, ORDINAL -> require(value != null && other == null && count == null, op);
                case VALUES -> require(value == null && other == null && count == null, op);
                case SHEET, ORDINAL_SHEET -> require(value == null && other != null && !other.isEmpty() && count == null, op);
                case COUNT -> require(value == null && other == null && count != null, op);
                case FLAG -> require(value == null && other == null && count == null, op);
            }
        }

        private static void require(boolean ok, Op op) {
            if (!ok) {
                throw new IllegalArgumentException("operand does not fit the operator " + op.token());
            }
        }

        public static Leaf value(String sheet, Op op, RuleValue value) {
            return new Leaf(sheet, op, value, List.of(), null, null, false);
        }

        public static Leaf values(String sheet, Op op, List<RuleValue> values) {
            return new Leaf(sheet, op, null, values, null, null, false);
        }

        public static Leaf sheet(String sheet, Op op, String other) {
            return new Leaf(sheet, op, null, List.of(), other, null, false);
        }

        public static Leaf count(String sheet, Op op, java.math.BigInteger count) {
            return new Leaf(sheet, op, null, List.of(), null, count, false);
        }

        public static Leaf present(String sheet, boolean present) {
            return new Leaf(sheet, Op.PRESENT, null, List.of(), null, null, present);
        }
    }

    /** The sheets the condition reads, in the legacy order (children first, then the leaf's sheet and other sheet). */
    default List<String> sheets() {
        List<String> out = new ArrayList<>();
        collect(this, out);
        return out;
    }

    private static void collect(Condition c, List<String> out) {
        switch (c) {
            case All a -> a.children().forEach(x -> collect(x, out));
            case Any a -> a.children().forEach(x -> collect(x, out));
            case Not n -> collect(n.child(), out);
            case Always a -> { }
            case Leaf l -> {
                add(out, l.sheet());
                if (l.other() != null) {
                    add(out, l.other());
                }
            }
        }
    }

    private static void add(List<String> out, String sheet) {
        if (!out.contains(sheet)) {
            out.add(sheet);
        }
    }

    /** Whether any leaf ranks values (an ordinal operator). */
    default boolean usesOrdinal() {
        return switch (this) {
            case All a -> a.children().stream().anyMatch(Condition::usesOrdinal);
            case Any a -> a.children().stream().anyMatch(Condition::usesOrdinal);
            case Not n -> n.child().usesOrdinal();
            case Always a -> false;
            case Leaf l -> l.op().kind() == Op.Kind.ORDINAL || l.op().kind() == Op.Kind.ORDINAL_SHEET;
        };
    }

    /** A deterministic text of the condition, for canonical forms. */
    default String canonical() {
        return switch (this) {
            case All a -> "all(" + join(a.children()) + ")";
            case Any a -> "any(" + join(a.children()) + ")";
            case Not n -> "not(" + n.child().canonical() + ")";
            case Always a -> "always";
            case Leaf l -> {
                String operand = switch (l.op().kind()) {
                    case VALUE, ORDINAL -> l.value().canonical();
                    case VALUES -> new RuleValue.ListValue(l.values()).canonical();
                    case SHEET, ORDINAL_SHEET -> "sheet:" + Canonical.escape(l.other());
                    case COUNT -> l.count().toString();
                    case FLAG -> Boolean.toString(l.flag());
                };
                yield l.op().token() + "(" + Canonical.escape(l.sheet()) + "," + operand + ")";
            }
        };
    }

    private static String join(List<Condition> children) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < children.size(); i++) {
            out.append(i == 0 ? "" : ",").append(children.get(i).canonical());
        }
        return out.toString();
    }
}
