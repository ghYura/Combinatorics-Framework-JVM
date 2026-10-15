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

package org.ghyura.cf.desktop;

import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.RuleValue;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;
import java.util.Map;

/**
 * A constraint rule as a tree, the nesting the legacy editor's condition blocks showed: the rule's sheets and gate, its
 * when-predicate, the all/any/not nesting of its condition or assert with each leaf in the legacy plain words
 * (describe_condition), a mapping's entries, and the values of its pairs or sets.
 */
final class ConditionTree {

    private static final int MOST_PAIRS = 200;

    private ConditionTree() {
    }

    static DefaultMutableTreeNode of(RuleValue.MapValue rule) {
        Map<String, RuleValue> c = rule.entries();
        String id = c.get("id") instanceof RuleValue.Text t ? t.value() : "rule";
        String polarity = c.get("polarity") instanceof RuleValue.Text t ? t.value() : c.containsKey("assert") ? "require" : "forbid";
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(id + " (" + polarity + ")");
        if (c.get("sheets") instanceof RuleValue.ListValue sheets && !sheets.items().isEmpty()) {
            root.add(new DefaultMutableTreeNode("sheets: " + String.join(", ", sheets.items().stream().map(ConditionTree::text).toList())));
        }
        if (c.get("gate") instanceof RuleValue.MapValue gate && !gate.entries().isEmpty()) {
            root.add(new DefaultMutableTreeNode("gate: " + ConstraintBonds.pythonJson(gate, false, false)));
        }
        if (c.get("when") instanceof RuleValue.Text when) {
            root.add(new DefaultMutableTreeNode("when: " + when.value()));
        }
        for (String key : List.of("condition", "assert")) {
            if (c.get(key) instanceof RuleValue.MapValue condition && !condition.entries().isEmpty()) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(key);
                node.add(condition(condition));
                root.add(node);
            }
        }
        if (c.get("mapping") instanceof RuleValue.MapValue mapping) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode("mapping");
            mapping.entries().forEach((k, v) -> node.add(new DefaultMutableTreeNode(k + ": " + text(v))));
            root.add(node);
        }
        if (c.get("sets") instanceof RuleValue.MapValue sets) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode("sets (every combination)");
            sets.entries().forEach((sheet, values) -> node.add(new DefaultMutableTreeNode(sheet + " ∈ " + text(values))));
            root.add(node);
        }
        if (c.get("pairs") instanceof RuleValue.ListValue pairs && !pairs.items().isEmpty()) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode("pairs: " + pairs.items().size());
            for (RuleValue p : pairs.items().subList(0, Math.min(MOST_PAIRS, pairs.items().size()))) {
                node.add(new DefaultMutableTreeNode(p instanceof RuleValue.MapValue m ? String.join(", ", m.entries().entrySet().stream()
                        .map(e -> e.getKey() + "=" + text(e.getValue())).toList()) : text(p)));
            }
            if (pairs.items().size() > MOST_PAIRS) {
                node.add(new DefaultMutableTreeNode("... " + (pairs.items().size() - MOST_PAIRS) + " more"));
            }
            root.add(node);
        }
        return root;
    }

    /** all of, any of and not hold their parts; a leaf is the legacy phrase for it. */
    static DefaultMutableTreeNode condition(RuleValue cond) {
        if (cond instanceof RuleValue.MapValue m) {
            for (String key : List.of("all", "any")) {
                if (m.entries().get(key) instanceof RuleValue.ListValue parts) {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(key + " of");
                    parts.items().forEach(p -> node.add(condition(p)));
                    return node;
                }
            }
            if (m.entries().containsKey("not")) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode("not");
                node.add(condition(m.entries().get("not")));
                return node;
            }
        }
        String phrase = ConstraintBonds.describeCondition(cond);
        return new DefaultMutableTreeNode(phrase.isEmpty() ? ConstraintBonds.pythonJson(cond, false, false) : phrase);
    }

    /** The tree as indented lines, for reading it in a test or a report. */
    static String dump(DefaultMutableTreeNode node) {
        StringBuilder out = new StringBuilder();
        java.util.Enumeration<javax.swing.tree.TreeNode> all = node.preorderEnumeration();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) all.nextElement();
            out.append("  ".repeat(n.getLevel())).append(n.getUserObject()).append('\n');
        }
        return out.toString();
    }

    private static String text(RuleValue v) {
        return switch (v) {
            case RuleValue.Text t -> t.value();
            case RuleValue.ListValue l -> "{" + String.join(", ", l.items().stream().map(ConditionTree::text).toList()) + "}";
            default -> ConstraintBonds.pythonJson(v, false, false);
        };
    }
}
