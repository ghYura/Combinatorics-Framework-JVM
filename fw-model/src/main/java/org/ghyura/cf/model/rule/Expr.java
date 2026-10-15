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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The parsed form of a {@code when} predicate: the subset of Python expressions the legacy sieve evaluated with a
 * restricted {@code eval} (sheet namespaces with their parameter attributes, literals, arithmetic, comparisons,
 * boolean logic and the functions abs, min, max, round, len, int, float and bool). Built by {@link ExprParser};
 * evaluated by the constraints module, never compiled to Java source.
 */
public sealed interface Expr {

    /** A literal: number, text, True, False or None. */
    record Constant(RuleValue value) implements Expr {
        public Constant {
            Objects.requireNonNull(value, "value");
        }
    }

    /** A bare name: a sheet's namespace or one of the allowed functions. */
    record Name(String name) implements Expr {
        public Name {
            Objects.requireNonNull(name, "name");
        }
    }

    /** {@code target.name}: a parameter attribute of a sheet's chosen value. */
    record Attribute(Expr target, String name) implements Expr {
        public Attribute {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(name, "name");
        }
    }

    /** {@code function(args)}; only a bare name can be called. */
    record Call(String function, List<Expr> args) implements Expr {
        public Call {
            Objects.requireNonNull(function, "function");
            args = List.copyOf(args);
        }
    }

    /** {@code target[index]}. */
    record Subscript(Expr target, Expr index) implements Expr {
        public Subscript {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(index, "index");
        }
    }

    /** Unary operators. */
    enum UnaryOp { NEGATE, PLUS, INVERT }

    record Unary(UnaryOp op, Expr operand) implements Expr {
        public Unary {
            Objects.requireNonNull(op, "op");
            Objects.requireNonNull(operand, "operand");
        }
    }

    /** Binary arithmetic and bit operators. */
    enum BinaryOp {
        ADD("+"), SUBTRACT("-"), MULTIPLY("*"), DIVIDE("/"), FLOOR_DIVIDE("//"), MODULO("%"), POWER("**"),
        BIT_OR("|"), BIT_XOR("^"), BIT_AND("&"), SHIFT_LEFT("<<"), SHIFT_RIGHT(">>");

        private final String symbol;

        BinaryOp(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    record Binary(BinaryOp op, Expr left, Expr right) implements Expr {
        public Binary {
            Objects.requireNonNull(op, "op");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    /** Comparison operators; a chain {@code a < b < c} holds when every link holds. */
    enum CompareOp {
        LESS("<"), LESS_EQUAL("<="), GREATER(">"), GREATER_EQUAL(">="), EQUAL("=="), NOT_EQUAL("!="), IN("in"),
        NOT_IN("not in"), IS("is"), IS_NOT("is not");

        private final String symbol;

        CompareOp(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    record Compare(Expr first, List<CompareOp> ops, List<Expr> rest) implements Expr {
        public Compare {
            Objects.requireNonNull(first, "first");
            ops = List.copyOf(ops);
            rest = List.copyOf(rest);
            if (ops.isEmpty() || ops.size() != rest.size()) {
                throw new IllegalArgumentException("a comparison chain needs one operand per operator");
            }
        }
    }

    /** {@code a and b and ...}: the first falsy operand, or the last. */
    record And(List<Expr> operands) implements Expr {
        public And {
            operands = List.copyOf(operands);
        }
    }

    /** {@code a or b or ...}: the first truthy operand, or the last. */
    record Or(List<Expr> operands) implements Expr {
        public Or {
            operands = List.copyOf(operands);
        }
    }

    record Not(Expr operand) implements Expr {
        public Not {
            Objects.requireNonNull(operand, "operand");
        }
    }

    /** {@code then if test else otherwise}. */
    record IfElse(Expr test, Expr then, Expr otherwise) implements Expr {
        public IfElse {
            Objects.requireNonNull(test, "test");
            Objects.requireNonNull(then, "then");
            Objects.requireNonNull(otherwise, "otherwise");
        }
    }

    /** {@code [a, b]}. */
    record ListDisplay(List<Expr> items) implements Expr {
        public ListDisplay {
            items = List.copyOf(items);
        }
    }

    /** {@code (a, b)}. */
    record TupleDisplay(List<Expr> items) implements Expr {
        public TupleDisplay {
            items = List.copyOf(items);
        }
    }

    /** The bare names the expression reads (sheets and functions), in first-use order. */
    default List<String> names() {
        List<String> out = new ArrayList<>();
        collectNames(this, out);
        return out;
    }

    private static void collectNames(Expr e, List<String> out) {
        switch (e) {
            case Constant c -> { }
            case Name n -> {
                if (!out.contains(n.name())) {
                    out.add(n.name());
                }
            }
            case Attribute a -> collectNames(a.target(), out);
            case Call c -> {
                if (!out.contains(c.function())) {
                    out.add(c.function());
                }
                c.args().forEach(x -> collectNames(x, out));
            }
            case Subscript s -> {
                collectNames(s.target(), out);
                collectNames(s.index(), out);
            }
            case Unary u -> collectNames(u.operand(), out);
            case Binary b -> {
                collectNames(b.left(), out);
                collectNames(b.right(), out);
            }
            case Compare c -> {
                collectNames(c.first(), out);
                c.rest().forEach(x -> collectNames(x, out));
            }
            case And a -> a.operands().forEach(x -> collectNames(x, out));
            case Or o -> o.operands().forEach(x -> collectNames(x, out));
            case Not n -> collectNames(n.operand(), out);
            case IfElse i -> {
                collectNames(i.test(), out);
                collectNames(i.then(), out);
                collectNames(i.otherwise(), out);
            }
            case ListDisplay l -> l.items().forEach(x -> collectNames(x, out));
            case TupleDisplay t -> t.items().forEach(x -> collectNames(x, out));
        }
    }

    /** The attributes read from the namespace of {@code sheet} ({@code sheet.attr}), in first-use order. */
    default List<String> attributesOf(String sheet) {
        List<String> out = new ArrayList<>();
        collectAttributes(this, sheet, out);
        return out;
    }

    private static void collectAttributes(Expr e, String sheet, List<String> out) {
        switch (e) {
            case Attribute a -> {
                if (a.target() instanceof Name n && n.name().equals(sheet)) {
                    if (!out.contains(a.name())) {
                        out.add(a.name());
                    }
                } else {
                    collectAttributes(a.target(), sheet, out);
                }
            }
            case Call c -> c.args().forEach(x -> collectAttributes(x, sheet, out));
            case Subscript s -> {
                collectAttributes(s.target(), sheet, out);
                collectAttributes(s.index(), sheet, out);
            }
            case Unary u -> collectAttributes(u.operand(), sheet, out);
            case Binary b -> {
                collectAttributes(b.left(), sheet, out);
                collectAttributes(b.right(), sheet, out);
            }
            case Compare c -> {
                collectAttributes(c.first(), sheet, out);
                c.rest().forEach(x -> collectAttributes(x, sheet, out));
            }
            case And a -> a.operands().forEach(x -> collectAttributes(x, sheet, out));
            case Or o -> o.operands().forEach(x -> collectAttributes(x, sheet, out));
            case Not n -> collectAttributes(n.operand(), sheet, out);
            case IfElse i -> {
                collectAttributes(i.test(), sheet, out);
                collectAttributes(i.then(), sheet, out);
                collectAttributes(i.otherwise(), sheet, out);
            }
            case ListDisplay l -> l.items().forEach(x -> collectAttributes(x, sheet, out));
            case TupleDisplay t -> t.items().forEach(x -> collectAttributes(x, sheet, out));
            case Constant c -> { }
            case Name n -> { }
        }
    }
}
