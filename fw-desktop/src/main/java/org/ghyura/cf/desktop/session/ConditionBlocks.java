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
import org.ghyura.cf.model.rule.Condition;
import org.ghyura.cf.model.rule.RuleValue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The condition trees of the sieve as editable blocks (a rule's {@code condition}, an assert rule's {@code assert}): all of,
 * any of and not hold their parts at any depth, a leaf tests one sheet's selection. The legacy editor built only one leaf or
 * one all/any of leaves this way and left anything nested to its JSON field; here every node is a block. A node is named by
 * its path from the root: the index of a part of all/any, 0 for the part of not. The edits keep the tree one the sieve reads:
 * removing a part takes an all/any left empty with it and leaves one left with a single part as that part (the legacy
 * builder's termsToCond), and a new leaf's operand is the legacy builder's defaultVal.
 */
public final class ConditionBlocks {

    /** A leaf's operator with the legacy builder's label; its operators first in its order, then the others the sieve reads. */
    public record LeafOp(Condition.Op op, String label) {
    }

    public static final List<LeafOp> OPS = List.of(new LeafOp(Condition.Op.EQ, "is"), new LeafOp(Condition.Op.NE, "is not"),
            new LeafOp(Condition.Op.PRESENT, "present?"), new LeafOp(Condition.Op.HAS, "contains"), new LeafOp(Condition.Op.IN, "any of"),
            new LeafOp(Condition.Op.NIN, "none of"), new LeafOp(Condition.Op.COUNT_GE, "count ≥"),
            new LeafOp(Condition.Op.COUNT_LE, "count ≤"), new LeafOp(Condition.Op.GE, "≥"), new LeafOp(Condition.Op.LE, "≤"),
            new LeafOp(Condition.Op.GT, ">"), new LeafOp(Condition.Op.LT, "<"), new LeafOp(Condition.Op.EQ_SHEET, "equals column"),
            new LeafOp(Condition.Op.GE_SHEET, "≥ column"), new LeafOp(Condition.Op.LE_SHEET, "≤ column"),
            new LeafOp(Condition.Op.SUB_OF, "⊆ column"), new LeafOp(Condition.Op.SUP_OF, "⊇ column"),
            new LeafOp(Condition.Op.HASNT, "does not contain"), new LeafOp(Condition.Op.HAS_ANY, "contains any of"),
            new LeafOp(Condition.Op.COUNT, "count ="), new LeafOp(Condition.Op.NE_SHEET, "differs from column"),
            new LeafOp(Condition.Op.GT_SHEET, "> column"), new LeafOp(Condition.Op.LT_SHEET, "< column"));

    /** A leaf's parts: the sheet tested, the operator, and its operand as the sidecar holds it. */
    public record Leaf(String sheet, Condition.Op op, RuleValue operand) {
    }

    private ConditionBlocks() {
    }

    // ---- reading -----------------------------------------------------------------------------------------------------

    /** "all", "any", "not", "always" for the empty condition, else "leaf". */
    public static String kind(RuleValue node) {
        if (node instanceof RuleValue.MapValue m) {
            for (String key : List.of("all", "any", "not")) {
                if (m.entries().containsKey(key)) {
                    return key;
                }
            }
            if (m.entries().isEmpty()) {
                return "always";
            }
        }
        return "leaf";
    }

    /** The parts of all/any, the part of not, none for a leaf. */
    public static List<RuleValue> parts(RuleValue node) {
        return switch (kind(node)) {
            case "all", "any" -> ((RuleValue.MapValue) node).entries().get(kind(node)) instanceof RuleValue.ListValue l ? l.items() : List.of();
            case "not" -> List.of(((RuleValue.MapValue) node).entries().get("not"));
            default -> List.of();
        };
    }

    /** The node at a path, null when there is none. */
    public static RuleValue at(RuleValue root, List<Integer> path) {
        RuleValue n = root;
        for (int i : path) {
            List<RuleValue> parts = n == null ? List.of() : parts(n);
            if (i < 0 || i >= parts.size()) {
                return null;
            }
            n = parts.get(i);
        }
        return n;
    }

    /** The legacy leafToTerm: a leaf's sheet with its first operator in the builder's order, null for anything else. */
    public static Leaf leaf(RuleValue node) {
        if (!(node instanceof RuleValue.MapValue m) || !kind(node).equals("leaf") || !(m.entries().get("sheet") instanceof RuleValue.Text sheet)) {
            return null;
        }
        for (LeafOp o : OPS) {
            if (m.entries().containsKey(o.op().token())) {
                return new Leaf(sheet.value(), o.op(), m.entries().get(o.op().token()));
            }
        }
        return null;
    }

    /** How a node shows in the tree: all of, any of, not, always, else the legacy phrase of the leaf. */
    public static String label(RuleValue node) {
        return switch (kind(node)) {
            case "all" -> "all of";
            case "any" -> "any of";
            case "not" -> "not";
            case "always" -> "always";
            default -> {
                String phrase = ConstraintBonds.describeCondition(node);
                yield phrase.isEmpty() ? ConstraintBonds.pythonJson(node, false, false) : phrase;
            }
        };
    }

    // ---- making ------------------------------------------------------------------------------------------------------

    /** The legacy termToLeaf: {"sheet": s, op: operand}. */
    public static RuleValue.MapValue leaf(String sheet, Condition.Op op, RuleValue operand) {
        Map<String, RuleValue> m = new LinkedHashMap<>();
        m.put("sheet", new RuleValue.Text(sheet));
        m.put(op.token(), operand);
        return new RuleValue.MapValue(m);
    }

    /**
     * The legacy defaultVal: a count 1, present true, another column the first other sheet (else the first, else ""), a set
     * of values the sheet's first value, one value the sheet's first value (else "").
     */
    public static RuleValue defaultOperand(String sheet, Condition.Op op, Map<String, List<String>> values) {
        return switch (op.kind()) {
            case COUNT -> new RuleValue.Int(BigInteger.ONE);
            case FLAG -> new RuleValue.Bool(true);
            case SHEET, ORDINAL_SHEET -> new RuleValue.Text(values.keySet().stream().filter(s -> !s.equals(sheet)).findFirst()
                    .orElse(values.isEmpty() ? "" : values.keySet().iterator().next()));
            case VALUES -> new RuleValue.ListValue(values.getOrDefault(sheet, List.of()).stream().limit(1)
                    .<RuleValue>map(RuleValue.Text::new).toList());
            case VALUE, ORDINAL -> new RuleValue.Text(values.getOrDefault(sheet, List.of()).stream().findFirst().orElse(""));
        };
    }

    /** A new leaf as the legacy builder added it: the first sheet "is" its first value. */
    public static RuleValue.MapValue newLeaf(Map<String, List<String>> values) {
        String sheet = values.isEmpty() ? "" : values.keySet().iterator().next();
        return leaf(sheet, Condition.Op.EQ, defaultOperand(sheet, Condition.Op.EQ, values));
    }

    private static RuleValue.MapValue node(String key, RuleValue body) {
        return new RuleValue.MapValue(Map.of(key, body));
    }

    // ---- editing (each returns the new root; null is no condition) ----------------------------------------------------

    /** The tree with the node at a path replaced. */
    public static RuleValue with(RuleValue root, List<Integer> path, RuleValue node) {
        if (path.isEmpty()) {
            return node;
        }
        String kind = kind(root);
        List<RuleValue> parts = new ArrayList<>(parts(root));
        int i = path.getFirst();
        if (i < 0 || i >= parts.size()) {
            throw new IllegalArgumentException("no condition at " + path);
        }
        parts.set(i, with(parts.get(i), path.subList(1, path.size()), node));
        return kind.equals("not") ? node("not", parts.getFirst()) : node(kind, new RuleValue.ListValue(parts));
    }

    /**
     * The tree without the node at a path: an all/any left without parts goes with it, one left with a single part becomes
     * that part, a not loses itself with its part; null when the root goes.
     */
    public static RuleValue remove(RuleValue root, List<Integer> path) {
        if (path.isEmpty()) {
            return null;
        }
        List<Integer> parentPath = path.subList(0, path.size() - 1);
        RuleValue parent = at(root, parentPath);
        if (parent == null || at(root, path) == null) {
            throw new IllegalArgumentException("no condition at " + path);
        }
        String kind = kind(parent);
        if (kind.equals("not")) {
            return remove(root, parentPath);
        }
        List<RuleValue> parts = new ArrayList<>(parts(parent));
        parts.remove((int) path.getLast());
        if (parts.isEmpty()) {
            return remove(root, parentPath);
        }
        return with(root, parentPath, parts.size() == 1 ? parts.getFirst() : node(kind, new RuleValue.ListValue(parts)));
    }

    /**
     * The tree with a node added beside the one at a path: as the last part of an all/any there, after it in the all/any
     * holding it, else joined with it under a new all (the legacy builder's second term); the root of an empty tree.
     *
     * @return the new root and the path of the added node
     */
    public static Added add(RuleValue root, List<Integer> path, RuleValue node) {
        if (root == null) {
            return new Added(node, List.of());
        }
        RuleValue there = at(root, path);
        if (there == null) {
            throw new IllegalArgumentException("no condition at " + path);
        }
        String kind = kind(there);
        if (kind.equals("all") || kind.equals("any")) {
            List<RuleValue> parts = new ArrayList<>(parts(there));
            parts.add(node);
            return new Added(with(root, path, node(kind, new RuleValue.ListValue(parts))), append(path, parts.size() - 1));
        }
        if (!path.isEmpty()) {
            List<Integer> parentPath = path.subList(0, path.size() - 1);
            String parentKind = kind(at(root, parentPath));
            if (parentKind.equals("all") || parentKind.equals("any")) {
                List<RuleValue> parts = new ArrayList<>(parts(at(root, parentPath)));
                parts.add(path.getLast() + 1, node);
                return new Added(with(root, parentPath, node(parentKind, new RuleValue.ListValue(parts))), append(parentPath, path.getLast() + 1));
            }
        }
        return new Added(with(root, path, node("all", new RuleValue.ListValue(List.of(there, node)))), append(path, 1));
    }

    /** A tree after an addition, and where the added node is. */
    public record Added(RuleValue root, List<Integer> path) {
        public Added {
            path = List.copyOf(path);
        }
    }

    /** An all/any at a path joined the other way; any other node wrapped as the single part of a new all/any. */
    public static RuleValue join(RuleValue root, List<Integer> path, String key) {
        if (!key.equals("all") && !key.equals("any")) {
            throw new IllegalArgumentException("a join is all or any, not " + key);
        }
        RuleValue there = at(root, path);
        String kind = kind(there);
        return with(root, path, kind.equals("all") || kind.equals("any") ? node(key, new RuleValue.ListValue(parts(there)))
                : node(key, new RuleValue.ListValue(List.of(there))));
    }

    /** A not at a path replaced by its part; any other node wrapped in a not. */
    public static RuleValue negate(RuleValue root, List<Integer> path) {
        RuleValue there = at(root, path);
        return with(root, path, kind(there).equals("not") ? parts(there).getFirst() : node("not", there));
    }

    /** The node at a path swapped with its neighbour in its all/any (-1 before, +1 after); the same tree at either end. */
    public static RuleValue shift(RuleValue root, List<Integer> path, int by) {
        if (path.isEmpty()) {
            return root;
        }
        List<Integer> parentPath = path.subList(0, path.size() - 1);
        RuleValue parent = at(root, parentPath);
        String kind = kind(parent);
        int i = path.getLast();
        int j = i + by;
        List<RuleValue> parts = new ArrayList<>(parts(parent));
        if (!(kind.equals("all") || kind.equals("any")) || j < 0 || j >= parts.size()) {
            return root;
        }
        RuleValue moving = parts.remove(i);
        parts.add(j, moving);
        return with(root, parentPath, node(kind, new RuleValue.ListValue(parts)));
    }

    /** Every path of the tree, a node before its parts. */
    public static List<List<Integer>> paths(RuleValue root) {
        List<List<Integer>> out = new ArrayList<>();
        if (root != null) {
            collect(root, List.of(), out);
        }
        return out;
    }

    private static void collect(RuleValue n, List<Integer> path, List<List<Integer>> out) {
        out.add(path);
        List<RuleValue> parts = parts(n);
        for (int i = 0; i < parts.size(); i++) {
            collect(parts.get(i), append(path, i), out);
        }
    }

    static List<Integer> append(List<Integer> path, int i) {
        List<Integer> out = new ArrayList<>(path);
        out.add(i);
        return List.copyOf(out);
    }
}
