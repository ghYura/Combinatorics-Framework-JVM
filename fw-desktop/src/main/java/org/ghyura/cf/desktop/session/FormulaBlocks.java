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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.Expr;
import org.ghyura.cf.model.rule.ExprParser;
import org.ghyura.cf.model.rule.RuleValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The legacy constraint editor's nested block formula builder (constraints/editor.py, the formula dialog's "Blocks" mode,
 * the counterpart of its text field): a typed tree of blocks plugged into slots, compiled to a {@code when} predicate exactly
 * as its compileT did. Compare, and/or and not give a truth value; arithmetic, a sheet's parameter and a number give a
 * number; a slot takes only a block of its type, and a block moves only into an empty slot outside itself. A slot is named
 * by the legacy path: "" is the root, "a" and "b" the operands of a binary block, "x" the operand of not, joined by dots.
 * The way back, from a text to blocks, is this port's: a text whose parts all have a block opens as blocks.
 */
public final class FormulaBlocks {

    /** What a block gives and what a slot takes: a truth value or a number (the legacy "bool" and "arith"). */
    public enum Type { BOOL, ARITH }

    /** The palette's block kinds in its order, with its labels. */
    public enum Kind {
        CMP("compare"), LOGIC("and / or"), NOT("not"), ARITH("+ − ×"), PARAM("sheet.param"), NUM("number");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** The legacy produces(t): compare, and/or and not give a truth value, the others a number. */
        public Type produces() {
            return this == CMP || this == LOGIC || this == NOT ? Type.BOOL : Type.ARITH;
        }
    }

    /** A block; an empty slot is null. */
    public sealed interface Block permits Binary, Not, Param, Num {
        Kind kind();
    }

    /** compare, and/or, or arithmetic: an operator between the slots a and b. */
    public record Binary(Kind kind, String op, Block a, Block b) implements Block {
        public Binary {
            Objects.requireNonNull(kind, "kind");
            if (ops(kind).stream().noneMatch(o -> o.value().equals(op))) {
                throw new IllegalArgumentException("'" + op + "' is not an operator of the " + kind.label() + " block");
            }
        }
    }

    /** not, over the slot x. */
    public record Not(Block x) implements Block {
        @Override
        public Kind kind() {
            return Kind.NOT;
        }
    }

    /** A parameter of a sheet's chosen value: value, pos, or one of the sidecar's params. */
    public record Param(String sheet, String param) implements Block {
        public Param {
            Objects.requireNonNull(sheet, "sheet");
            Objects.requireNonNull(param, "param");
        }

        @Override
        public Kind kind() {
            return Kind.PARAM;
        }
    }

    /** A number, as its field holds it. */
    public record Num(String val) implements Block {
        public Num {
            Objects.requireNonNull(val, "val");
        }

        @Override
        public Kind kind() {
            return Kind.NUM;
        }
    }

    /** An operator of a block: what the formula says and what the block shows. */
    public record Op(String value, String label) {
    }

    /** A text whose parts do not all have a block. */
    public static final class NotBlocks extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotBlocks(String message) {
            super(message);
        }
    }

    private static final List<Op> CMP_OPS = List.of(new Op(">", ">"), new Op(">=", "≥"), new Op("<", "<"), new Op("<=", "≤"),
            new Op("==", "="), new Op("!=", "≠"));
    private static final List<Op> LOGIC_OPS = List.of(new Op("and", "and"), new Op("or", "or"));
    private static final List<Op> ARITH_OPS = List.of(new Op("+", "+"), new Op("-", "−"), new Op("*", "×"));

    private FormulaBlocks() {
    }

    /** The legacy opsOf: the operators a compare, and/or or arithmetic block offers; none for the others. */
    public static List<Op> ops(Kind kind) {
        return switch (kind) {
            case CMP -> CMP_OPS;
            case LOGIC -> LOGIC_OPS;
            case ARITH -> ARITH_OPS;
            default -> List.of();
        };
    }

    /**
     * The legacy newNode: compare with "&gt;", and/or with "and", arithmetic with "*", all slots empty; a parameter of the
     * first sheet offered ("" without one) with its first parameter ("value"); the number 0.
     */
    public static Block newNode(Kind kind, List<String> sheets, Function<String, List<String>> paramsOf) {
        return switch (kind) {
            case CMP -> new Binary(Kind.CMP, ">", null, null);
            case LOGIC -> new Binary(Kind.LOGIC, "and", null, null);
            case NOT -> new Not(null);
            case ARITH -> new Binary(Kind.ARITH, "*", null, null);
            case PARAM -> {
                String sheet = sheets.isEmpty() ? "" : sheets.getFirst();
                List<String> params = paramsOf.apply(sheet);
                yield new Param(sheet, params.isEmpty() ? "value" : params.getFirst());
            }
            case NUM -> new Num("0");
        };
    }

    /**
     * The legacy compileT: a parameter as sheet.param, a number as written, not as "not (x)", a binary block as
     * "(a op b)"; null while any slot is empty.
     */
    public static String compile(Block n) {
        if (n == null) {
            return null;
        }
        return switch (n) {
            case Param p -> p.sheet() + "." + p.param();
            case Num num -> num.val();
            case Not not -> {
                String x = compile(not.x());
                yield x == null ? null : "not (" + x + ")";
            }
            case Binary b -> {
                String a = compile(b.a());
                String c = compile(b.b());
                yield a == null || c == null ? null : "(" + a + " " + b.op() + " " + c + ")";
            }
        };
    }

    // ---- slots -------------------------------------------------------------------------------------------------------

    /** The legacy getAt: the block in the slot at a path, null for an empty slot or a path that is no slot. */
    public static Block at(Block root, String path) {
        Block n = root;
        if (path.isEmpty()) {
            return n;
        }
        for (String key : path.split("\\.", -1)) {
            n = child(n, key);
            if (n == null) {
                return null;
            }
        }
        return n;
    }

    /** Whether a path names a slot of the tree: the root, or a slot of a block in it. */
    public static boolean isSlot(Block root, String path) {
        if (path.isEmpty()) {
            return true;
        }
        int dot = path.lastIndexOf('.');
        Block parent = at(root, dot < 0 ? "" : path.substring(0, dot));
        return parent != null && keys(parent).contains(path.substring(dot + 1));
    }

    /** The legacy setAt: the tree with the slot at a path holding a block (null empties it). */
    public static Block with(Block root, String path, Block block) {
        if (path.isEmpty()) {
            return block;
        }
        if (!isSlot(root, path)) {
            throw new IllegalArgumentException("no slot at '" + path + "'");
        }
        int dot = path.indexOf('.');
        String key = dot < 0 ? path : path.substring(0, dot);
        String rest = dot < 0 ? "" : path.substring(dot + 1);
        Block replaced = with(child(root, key), rest, block);
        return switch (root) {
            case Binary b -> key.equals("a") ? new Binary(b.kind(), b.op(), replaced, b.b()) : new Binary(b.kind(), b.op(), b.a(), replaced);
            case Not n -> new Not(replaced);
            default -> throw new IllegalArgumentException("no slot at '" + path + "'");
        };
    }

    /** What the slot at a path takes: a truth value at the root and under and/or and not, a number under compare and arithmetic. */
    public static Type slot(Block root, String path) {
        if (!isSlot(root, path)) {
            throw new IllegalArgumentException("no slot at '" + path + "'");
        }
        if (path.isEmpty()) {
            return Type.BOOL;
        }
        int dot = path.lastIndexOf('.');
        Block parent = at(root, dot < 0 ? "" : path.substring(0, dot));
        return parent.kind() == Kind.CMP || parent.kind() == Kind.ARITH ? Type.ARITH : Type.BOOL;
    }

    /** The legacy dropOK for a palette block: it goes into the slot at a path (empty or not) when it gives what the slot takes. */
    public static boolean fits(Block root, String path, Kind kind) {
        return isSlot(root, path) && slot(root, path) == kind.produces();
    }

    /**
     * The legacy dropOK for a move: a block (never the root) goes only into an empty slot of its type that lies outside
     * itself.
     */
    public static boolean canMove(Block root, String from, String to) {
        if (from.isEmpty() || to.equals(from) || to.startsWith(from + ".") || !isSlot(root, from) || !isSlot(root, to)) {
            return false;
        }
        Block moving = at(root, from);
        return moving != null && at(root, to) == null && slot(root, to) == moving.kind().produces();
    }

    /** The legacy doDrop of a move: the block leaves its slot empty and fills the other. */
    public static Block move(Block root, String from, String to) {
        if (!canMove(root, from, to)) {
            throw new IllegalArgumentException("the block at '" + from + "' cannot move to '" + to + "'");
        }
        Block moving = at(root, from);
        return with(with(root, from, null), to, moving);
    }

    /** Every slot of the tree, filled or empty, in the order the blocks show them (a block before its slots, a before b). */
    public static List<String> slots(Block root) {
        List<String> out = new ArrayList<>();
        collect(root, "", out);
        return out;
    }

    private static void collect(Block n, String path, List<String> out) {
        out.add(path);
        if (n == null) {
            return;
        }
        for (String key : keys(n)) {
            collect(child(n, key), path.isEmpty() ? key : path + "." + key, out);
        }
    }

    private static List<String> keys(Block n) {
        return switch (n) {
            case Binary b -> List.of("a", "b");
            case Not x -> List.of("x");
            default -> List.of();
        };
    }

    private static Block child(Block n, String key) {
        return switch (n) {
            case Binary b when key.equals("a") -> b.a();
            case Binary b when key.equals("b") -> b.b();
            case Not x when key.equals("x") -> x.x();
            case null, default -> null;
        };
    }

    /** A block's formula, or its label while one of its slots is empty. */
    public static String compileOrLabel(Block n) {
        String formula = compile(n);
        return formula != null ? formula : label(n);
    }

    /** How a block shows in the tree: the kind with its operator, the parameter, the number. */
    public static String label(Block n) {
        return switch (n) {
            case Binary b -> (b.kind() == Kind.CMP ? "compare " : b.kind() == Kind.ARITH ? "arithmetic " : "")
                    + ops(b.kind()).stream().filter(o -> o.value().equals(b.op())).findFirst().orElseThrow().label();
            case Not x -> "not";
            case Param p -> p.sheet() + "." + p.param();
            case Num num -> num.val();
        };
    }

    // ---- parameters --------------------------------------------------------------------------------------------------

    /**
     * The legacy paramsOf: value and pos, then every parameter the sidecar's params give any value of the sheet, in the
     * order a browser walked them (integer-like keys first, ascending, then the others as written).
     */
    public static List<String> paramsOf(String sheet, RuleValue params) {
        LinkedHashSet<String> out = new LinkedHashSet<>(List.of("value", "pos"));
        if (params instanceof RuleValue.MapValue all && all.entries().get(sheet) instanceof RuleValue.MapValue values) {
            for (String v : jsKeys(values.entries().keySet())) {
                if (values.entries().get(v) instanceof RuleValue.MapValue p) {
                    out.addAll(jsKeys(p.entries().keySet()));
                }
            }
        }
        return List.copyOf(out);
    }

    /** A JavaScript object's own key order: array indices ascending, then the other keys in insertion order. */
    static List<String> jsKeys(Collection<String> keys) {
        List<String> indices = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String k : keys) {
            (arrayIndex(k) ? indices : others).add(k);
        }
        indices.sort(java.util.Comparator.comparingLong(Long::parseLong));
        indices.addAll(others);
        return indices;
    }

    private static boolean arrayIndex(String k) {
        return k.matches("0|[1-9][0-9]{0,9}") && Long.parseLong(k) < 4_294_967_295L;
    }

    // ---- text to blocks ----------------------------------------------------------------------------------------------

    /**
     * A {@code when} text as blocks: null for an empty text; a comparison, and/or (a longer chain nests to the left), not,
     * + - *, sheet.param and numbers each become their block.
     *
     * @throws ExprParser.SyntaxError when the text is no supported expression
     * @throws NotBlocks              when a part of it has no block
     */
    public static Block parse(String text) {
        String t = text.strip();
        if (t.isEmpty()) {
            return null;
        }
        return block(ExprParser.parse(t), Type.BOOL);
    }

    private static final Map<Expr.CompareOp, String> CMP = Map.of(Expr.CompareOp.GREATER, ">", Expr.CompareOp.GREATER_EQUAL, ">=",
            Expr.CompareOp.LESS, "<", Expr.CompareOp.LESS_EQUAL, "<=", Expr.CompareOp.EQUAL, "==", Expr.CompareOp.NOT_EQUAL, "!=");
    private static final Map<Expr.BinaryOp, String> ARITH = Map.of(Expr.BinaryOp.ADD, "+", Expr.BinaryOp.SUBTRACT, "-",
            Expr.BinaryOp.MULTIPLY, "*");

    private static Block block(Expr e, Type want) {
        Block made = switch (e) {
            case Expr.Compare c when c.ops().size() == 1 && CMP.containsKey(c.ops().getFirst()) ->
                    new Binary(Kind.CMP, CMP.get(c.ops().getFirst()), block(c.first(), Type.ARITH), block(c.rest().getFirst(), Type.ARITH));
            case Expr.Compare c when c.ops().size() > 1 -> throw new NotBlocks("a comparison chain (compare blocks take two numbers each)");
            case Expr.Compare c -> throw new NotBlocks("the operator '" + c.ops().getFirst().symbol() + "'");
            case Expr.And a -> chain("and", a.operands());
            case Expr.Or o -> chain("or", o.operands());
            case Expr.Not n -> new Not(block(n.operand(), Type.BOOL));
            case Expr.Binary b when ARITH.containsKey(b.op()) ->
                    new Binary(Kind.ARITH, ARITH.get(b.op()), block(b.left(), Type.ARITH), block(b.right(), Type.ARITH));
            case Expr.Binary b -> throw new NotBlocks("the operator '" + b.op().symbol() + "' (the arithmetic block has + - *)");
            case Expr.Attribute a when a.target() instanceof Expr.Name sheet -> new Param(sheet.name(), a.name());
            case Expr.Constant c -> new Num(number(c.value(), ""));
            case Expr.Unary u when u.op() == Expr.UnaryOp.NEGATE && u.operand() instanceof Expr.Constant c -> new Num(number(c.value(), "-"));
            case Expr.Name n -> throw new NotBlocks("the bare name " + n.name() + " (a parameter is written sheet.param)");
            case Expr.Call c -> throw new NotBlocks("the function " + c.function() + "()");
            case Expr.IfElse i -> throw new NotBlocks("if ... else");
            default -> throw new NotBlocks("the expression part " + e.getClass().getSimpleName());
        };
        if (made.kind().produces() != want) {
            throw new NotBlocks(want == Type.BOOL ? "the number " + compile(made) + " where a condition belongs"
                    : "the condition " + compile(made) + " where a number belongs");
        }
        return made;
    }

    private static Block chain(String op, List<Expr> operands) {
        Block left = block(operands.getFirst(), Type.BOOL);
        for (Expr e : operands.subList(1, operands.size())) {
            left = new Binary(Kind.LOGIC, op, left, block(e, Type.BOOL));
        }
        return left;
    }

    private static String number(RuleValue v, String sign) {
        return switch (v) {
            case RuleValue.Int i -> sign + i.value();
            case RuleValue.Real r when Double.isFinite(r.value()) -> sign + ConstraintBonds.pythonJson(r, false, false);
            case RuleValue.Text t -> throw new NotBlocks("the text '" + t.value() + "' (the number block holds numbers only)");
            case RuleValue.Bool b -> throw new NotBlocks("the value " + (b.value() ? "True" : "False"));
            case RuleValue.Null n -> throw new NotBlocks("the value None");
            default -> throw new NotBlocks("the value " + ConstraintBonds.pythonJson(v, false, false));
        };
    }
}
