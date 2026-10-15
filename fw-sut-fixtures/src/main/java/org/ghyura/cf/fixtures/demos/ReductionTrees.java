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


package org.ghyura.cf.fixtures.demos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** D7 sut.py and oracle.py: exact represented inputs, binary64 nodes and inclusive frozen error budgets. */
public final class ReductionTrees {
    private ReductionTrees() { }
    public record NodeValue(String node, BigDecimal exact, double binary64) { }
    public record Result(BigDecimal value, double binary64, BigDecimal reference, BigDecimal error,
                         boolean finite, boolean passed, List<NodeValue> nodes) {
        public Result { nodes = List.copyOf(nodes); }
    }
    private record Tree(int leaf, Tree left, Tree right) {
        String canonical() { return leaf >= 0 ? Integer.toString(leaf) : "(" + left.canonical() + "," + right.canonical() + ")"; }
        void leaves(List<Integer> found) {
            if (leaf >= 0) found.add(leaf); else { left.leaves(found); right.leaves(found); }
        }
    }
    private static final class Parser {
        private final String text;
        private int at;
        Parser(String text) { this.text = text; }
        Tree node() {
            if (at == text.length()) throw new IllegalArgumentException("incomplete tree");
            char c = text.charAt(at++);
            if (c >= '0' && c <= '4') return new Tree(c - '0', null, null);
            if (c != '(') throw new IllegalArgumentException("malformed tree");
            Tree left = node(); need(','); Tree right = node(); need(')');
            return new Tree(-1, left, right);
        }
        void need(char c) {
            if (at == text.length() || text.charAt(at++) != c) throw new IllegalArgumentException("malformed tree");
        }
    }
    private static Tree parse(String text) {
        if (text == null || text.length() != 17) throw new IllegalArgumentException("a canonical five-leaf tree has 17 characters");
        Parser parser = new Parser(text); Tree tree = parser.node();
        List<Integer> leaves = new ArrayList<>(); tree.leaves(leaves);
        if (parser.at != text.length() || !tree.canonical().equals(text) || !leaves.equals(List.of(0, 1, 2, 3, 4)))
            throw new IllegalArgumentException("tree must contain leaves 0..4 once each, in order");
        return tree;
    }
    /** Reference deliberately reads only the represented inputs, never a tree or a policy. */
    public static BigDecimal reference(List<Double> values) {
        if (values.size() != 5) throw new IllegalArgumentException("five represented binary64 inputs required");
        BigDecimal sum = BigDecimal.ZERO;
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("nonfinite input");
            sum = sum.add(new BigDecimal(value));
        }
        return sum;
    }
    private static NodeValue walk(Tree tree, List<Double> values, boolean rational, List<NodeValue> nodes) {
        if (tree.leaf >= 0) {
            double value = values.get(tree.leaf);
            return new NodeValue(tree.canonical(), rational ? new BigDecimal(value) : null, value);
        }
        NodeValue left = walk(tree.left, values, rational, nodes), right = walk(tree.right, values, rational, nodes);
        BigDecimal exact = rational ? left.exact.add(right.exact) : null;
        NodeValue result = new NodeValue(tree.canonical(), exact, rational ? exact.doubleValue() : left.binary64 + right.binary64);
        nodes.add(result); return result;
    }
    public static Result evaluate(String policy, String text, List<Double> values, BigDecimal budget) {
        if (!List.of("tree_binary64", "flat_fsum", "tree_rational").contains(policy)) throw new IllegalArgumentException("unknown policy");
        if (budget.signum() < 0) throw new IllegalArgumentException("negative error budget");
        Tree tree = parse(text); BigDecimal ref = reference(values);
        List<NodeValue> nodes = new ArrayList<>();
        NodeValue output;
        if (policy.equals("flat_fsum")) {
            // All four source-catalog vectors have a finite sum. Exact decimal addition of represented
            // doubles followed by one round gives the same correctly rounded binary64 result as fsum.
            double rounded = ref.doubleValue();
            if (!Double.isFinite(rounded)) throw new ArithmeticException("fsum overflow");
            output = new NodeValue(text, null, rounded);
        } else output = walk(tree, values, policy.equals("tree_rational"), nodes);
        boolean finite = output.exact != null || Double.isFinite(output.binary64);
        BigDecimal value = output.exact != null ? output.exact : finite ? new BigDecimal(output.binary64) : null;
        BigDecimal error = finite ? value.subtract(ref).abs() : null;
        return new Result(value, output.binary64, ref, error, finite, finite && error.compareTo(budget) <= 0, nodes);
    }
}
