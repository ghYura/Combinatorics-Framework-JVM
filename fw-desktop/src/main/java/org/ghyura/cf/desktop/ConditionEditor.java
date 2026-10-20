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

import org.ghyura.cf.desktop.session.ConditionBlocks;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.rule.Condition;
import org.ghyura.cf.model.rule.RuleValue;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Dimension;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A condition tree of the sieve as blocks at any depth (a rule's "only when" condition, an assert rule's body): all of, any
 * of and not hold their parts, a leaf tests one sheet's selection. A new condition goes beside the selected one (the legacy
 * builder's next term); all of, any of and not wrap the selected node or turn an all/any the other way, Delete and Up/Down
 * edit the nesting, and the row below edits the selected leaf's sheet, operator and operand (a new sheet or operator takes
 * the legacy defaultVal). The tree's JSON stays editable as the legacy JSON fallback.
 */
final class ConditionEditor {

    private static final String VALUE = "value";
    private static final String VALUES = "values";
    private static final String FLAG = "flag";
    private static final String OTHER = "other";
    private static final String COUNT = "count";

    /** A node of the tree: its path and what it shows. */
    private record Node(List<Integer> path, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final JPanel panel = new JPanel();
    private final JTree tree = Hints.of(new JTree(new DefaultMutableTreeNode()), "The condition as a tree: \"all of\" (every part must hold), \"any of\" (one part is enough), \"not\" (the opposite), and simple tests. Select a part to change it.");
    private final JButton add = Hints.of(new JButton("+ condition"), "Add a simple test next to the selected one. Inside a group it joins that group; otherwise both are grouped under \"all of\".");
    private final JButton all = Hints.of(new JButton("all of"), "Make the selected group \"all of\" (every part must hold), or put the selected test into a new \"all of\" group.");
    private final JButton any = Hints.of(new JButton("any of"), "Make the selected group \"any of\" (one part is enough), or put the selected test into a new \"any of\" group.");
    private final JButton not = Hints.of(new JButton("not"), "Turn the selected part into its opposite, or remove a \"not\".");
    private final JButton delete = Hints.of(new JButton("Delete"), "Delete the selected part.");
    private final JButton up = Hints.of(new JButton("Up"), "Move the selected part up.");
    private final JButton down = Hints.of(new JButton("Down"), "Move the selected part down.");
    private final JComboBox<String> sheet = Hints.of(new JComboBox<String>(), "The sheet the test looks at.");
    private final JComboBox<String> op = Hints.of(new JComboBox<String>(), "How the test compares: is, is not, any of, none of, contains, a number comparison, a count, whether the sheet is present, or a comparison with another column.");
    private final WrapLayout.Cards operand = new WrapLayout.Cards();
    private final JComboBox<String> value = Hints.of(new JComboBox<String>(), "The value the sheet is compared with.");
    private final JList<String> values = Hints.of(new JList<String>(), "The values for an \"any of\" or \"none of\" test; select several.");
    private final JComboBox<String> flag = Hints.of(new JComboBox<>(new String[]{"yes", "no"}), "Yes or no, for a test that asks whether the sheet takes part.");
    private final JComboBox<String> other = Hints.of(new JComboBox<String>(), "The other column, for a test that compares two columns.");
    private final JSpinner count = Hints.of(new JSpinner(new SpinnerNumberModel(1, 0, Integer.MAX_VALUE, 1)), "The number, for a test that counts values.");
    private final JTextField json = Hints.of(new JTextField(34), "The selected part as JSON text. Filled when you select a part of the condition; type to change it, then press Apply JSON.");
    private final JButton applyJson = Hints.of(new JButton("Apply JSON"), "Use the JSON text for the selected part.");
    private final JLabel hint = new JLabel(" ");
    private final String empty;
    private final Runnable changed;
    private RuleValue root;
    private Map<String, List<String>> sheetValues = Map.of();
    private boolean updating;
    /** Whether there is something to give a condition: an editor for no rule offers no edit, so it starts no draft. */
    private boolean active = true;

    ConditionEditor(String title, String empty, Runnable changed) {
        this.empty = empty;
        this.changed = changed;
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        FieldNames.section(panel, title);
        JPanel buttons = WrapLayout.row();
        buttons.add(new JLabel(title));
        for (JButton b : List.of(add, all, any, not, delete, up, down)) {
            buttons.add(b);
        }
        add.addActionListener(e -> add());
        all.addActionListener(e -> join("all"));
        any.addActionListener(e -> join("any"));
        not.addActionListener(e -> negate());
        delete.addActionListener(e -> delete());
        up.addActionListener(e -> shift(-1));
        down.addActionListener(e -> shift(1));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> showSelected());
        tree.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "delete-condition");
        tree.getActionMap().put("delete-condition", FormulaEditor.action(this::delete));
        JScrollPane scroll = FieldNames.titled(tree, "Parts of the condition");
        scroll.setPreferredSize(new Dimension(420, 110));
        for (ConditionBlocks.LeafOp o : ConditionBlocks.OPS) {
            op.addItem(o.label() + "  (" + o.op().token() + ")");
        }
        value.setEditable(true);
        values.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        values.setVisibleRowCount(3);
        operand.add(FieldNames.named("value:", value), VALUE);
        operand.add(FieldNames.titled(values, "Values"), VALUES);
        operand.add(FieldNames.named("present:", flag), FLAG);
        operand.add(FieldNames.named("other column:", other), OTHER);
        operand.add(FieldNames.named("count:", count), COUNT);
        sheet.addActionListener(e -> {
            if (!updating && sheet.getSelectedItem() != null) {
                setSheet((String) sheet.getSelectedItem());
            }
        });
        op.addActionListener(e -> {
            if (!updating && op.getSelectedIndex() >= 0) {
                setOp(ConditionBlocks.OPS.get(op.getSelectedIndex()).op());
            }
        });
        value.addActionListener(e -> {
            if (!updating && value.getSelectedItem() != null) {
                setOperand(new RuleValue.Text(value.getSelectedItem().toString()));
            }
        });
        values.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting()) {
                setOperand(new RuleValue.ListValue(values.getSelectedValuesList().stream().<RuleValue>map(RuleValue.Text::new).toList()));
            }
        });
        flag.addActionListener(e -> {
            if (!updating) {
                setOperand(new RuleValue.Bool(flag.getSelectedIndex() == 0));
            }
        });
        other.addActionListener(e -> {
            if (!updating && other.getSelectedItem() != null) {
                setOperand(new RuleValue.Text((String) other.getSelectedItem()));
            }
        });
        count.addChangeListener(e -> {
            if (!updating) {
                setOperand(new RuleValue.Int(BigInteger.valueOf(((Number) count.getValue()).longValue())));
            }
        });
        JPanel leaf = WrapLayout.row();
        leaf.add(new JLabel("selected condition:"));
        leaf.add(FieldNames.label("sheet:", sheet));
        leaf.add(sheet);
        leaf.add(FieldNames.label("test:", op));
        leaf.add(op);
        leaf.add(operand);
        applyJson.addActionListener(e -> applyJson(json.getText()));
        json.addActionListener(e -> applyJson(json.getText()));
        JPanel jsonRow = new JPanel(new java.awt.BorderLayout(4, 0));
        jsonRow.add(FieldNames.label("JSON:", json), java.awt.BorderLayout.WEST);
        jsonRow.add(json, java.awt.BorderLayout.CENTER);
        jsonRow.add(applyJson, java.awt.BorderLayout.EAST);
        for (JComponent c : List.of(buttons, scroll, leaf, jsonRow, hint)) {
            c.setAlignmentX(0f);
            panel.add(c);
        }
    }

    JComponent component() {
        return panel;
    }

    /** Starts from a condition tree (null for none) over the scenario's sheets and their values. */
    void show(RuleValue condition, Map<String, List<String>> sheets) {
        root = condition == null || ConditionBlocks.kind(condition).equals("always") ? null : condition;
        sheetValues = new java.util.LinkedHashMap<>(sheets);
        hint.setText(" ");
        rebuild(List.of());
    }

    /** On while a rule is shown; off, nothing here can change and every control says so by being off. */
    void active(boolean on) {
        active = on;
        tree.setEnabled(on);
        json.setEnabled(on);
        showSelected();
    }

    /** The tree as edited, null for none. */
    RuleValue condition() {
        return root;
    }

    // ---- edits on the selected node ----------------------------------------------------------------------------------

    void select(List<Integer> path) {
        DefaultMutableTreeNode node = find(path);
        if (node != null) {
            tree.setSelectionPath(new TreePath(node.getPath()));
        }
    }

    /** The selected node's path, null when none is (or the tree is empty). */
    List<Integer> selected() {
        TreePath p = tree.getSelectionPath();
        return p == null || root == null ? null : ((Node) ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject()).path();
    }

    void add() {
        if (!active) return;
        List<Integer> at = selected();
        ConditionBlocks.Added added = ConditionBlocks.add(root, at == null ? List.of() : at, ConditionBlocks.newLeaf(sheetValues));
        edit(added.root(), added.path());
    }

    void join(String key) {
        List<Integer> at = selected();
        if (root != null) {
            edit(ConditionBlocks.join(root, at == null ? List.of() : at, key), at == null ? List.of() : at);
        }
    }

    void negate() {
        List<Integer> at = selected();
        if (root != null) {
            edit(ConditionBlocks.negate(root, at == null ? List.of() : at), at == null ? List.of() : at);
        }
    }

    void delete() {
        List<Integer> at = selected();
        if (root != null && at != null) {
            edit(ConditionBlocks.remove(root, at), at.isEmpty() ? List.of() : at.subList(0, at.size() - 1));
        }
    }

    void shift(int by) {
        List<Integer> at = selected();
        if (root != null && at != null && !at.isEmpty()) {
            RuleValue shifted = ConditionBlocks.shift(root, at, by);
            List<Integer> to = new ArrayList<>(at);
            if (shifted != root) {
                to.set(to.size() - 1, at.getLast() + by);
            }
            edit(shifted, to);
        }
    }

    /** The selected leaf on another sheet, its operand the legacy default for it. */
    void setSheet(String sheetName) {
        ConditionBlocks.Leaf l = selectedLeaf();
        if (l != null && !l.sheet().equals(sheetName)) {
            replaceLeaf(sheetName, l.op(), ConditionBlocks.defaultOperand(sheetName, l.op(), sheetValues));
        }
    }

    /** The selected leaf with another operator, its operand the legacy default for it. */
    void setOp(Condition.Op operator) {
        ConditionBlocks.Leaf l = selectedLeaf();
        if (l != null && l.op() != operator) {
            replaceLeaf(l.sheet(), operator, ConditionBlocks.defaultOperand(l.sheet(), operator, sheetValues));
        }
    }

    void setOperand(RuleValue v) {
        ConditionBlocks.Leaf l = selectedLeaf();
        if (l != null && !v.equals(l.operand())) {
            replaceLeaf(l.sheet(), l.op(), v);
        }
    }

    /** The legacy JSON fallback: a tree as JSON replaces the edited one (an empty text removes it). */
    String applyJson(String text) {
        if (!active) {
            hint.setText("Select a rule first: there is no condition to set.");
            return hint.getText();
        }
        if (text.isBlank()) {
            if (root != null) edit(null, List.of());
            return null;
        }
        try {
            RuleValue parsed = ConstraintBonds.parse(text, "condition");
            if (!(parsed instanceof RuleValue.MapValue)) {
                hint.setText("bad JSON: a condition is an object");
                return hint.getText();
            }
            // the same condition again changes nothing, so it leaves no draft behind
            if (!parsed.equals(root)) edit(parsed, List.of());
            return null;
        } catch (FrameworkException e) {
            hint.setText("bad JSON: " + e.diagnostics().getFirst().message());
            return hint.getText();
        }
    }

    private ConditionBlocks.Leaf selectedLeaf() {
        List<Integer> at = selected();
        return at == null ? null : ConditionBlocks.leaf(ConditionBlocks.at(root, at));
    }

    private void replaceLeaf(String sheetName, Condition.Op operator, RuleValue v) {
        List<Integer> at = selected();
        edit(ConditionBlocks.with(root, at, ConditionBlocks.leaf(sheetName, operator, v)), at);
    }

    private void edit(RuleValue changedRoot, List<Integer> select) {
        root = changedRoot;
        hint.setText(" ");
        rebuild(select);
        changed.run();
    }

    // ---- the tree ----------------------------------------------------------------------------------------------------

    private void rebuild(List<Integer> select) {
        DefaultMutableTreeNode top;
        if (root == null) {
            top = new DefaultMutableTreeNode(new Node(List.of(), empty));
        } else {
            Map<List<Integer>, DefaultMutableTreeNode> nodes = new HashMap<>();
            top = null;
            for (List<Integer> path : ConditionBlocks.paths(root)) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Node(path, ConditionBlocks.label(ConditionBlocks.at(root, path))));
                nodes.put(path, node);
                if (path.isEmpty()) {
                    top = node;
                } else {
                    nodes.get(path.subList(0, path.size() - 1)).add(node);
                }
            }
        }
        tree.setModel(new DefaultTreeModel(top));
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        updating = true;
        json.setText(root == null ? "" : ConstraintBonds.pythonJson(root, false, false));
        updating = false;
        select(find(select) != null ? select : List.of());
        showSelected();
    }

    private DefaultMutableTreeNode find(List<Integer> path) {
        java.util.Enumeration<javax.swing.tree.TreeNode> all = ((DefaultMutableTreeNode) tree.getModel().getRoot()).preorderEnumeration();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) all.nextElement();
            if (((Node) n.getUserObject()).path().equals(path)) {
                return n;
            }
        }
        return null;
    }

    /** Fills the leaf row for a selected leaf and enables what applies to the selection. */
    private void showSelected() {
        List<Integer> at = selected();
        ConditionBlocks.Leaf l = selectedLeaf();
        updating = true;
        try {
            boolean some = active && root != null && at != null;
            add.setEnabled(active);
            applyJson.setEnabled(active);
            all.setEnabled(some);
            any.setEnabled(some);
            not.setEnabled(some);
            delete.setEnabled(some);
            up.setEnabled(some && !at.isEmpty() && at.getLast() > 0 && ConditionBlocks.shift(root, at, -1) != root);
            down.setEnabled(some && !at.isEmpty() && ConditionBlocks.shift(root, at, 1) != root);
            sheet.removeAllItems();
            sheetValues.keySet().forEach(sheet::addItem);
            for (JComponent c : List.of(sheet, op, value, values, flag, other, count)) {
                c.setEnabled(l != null);
            }
            if (l == null) {
                return;
            }
            if (!sheetValues.containsKey(l.sheet())) {
                sheet.addItem(l.sheet());
            }
            sheet.setSelectedItem(l.sheet());
            for (int i = 0; i < ConditionBlocks.OPS.size(); i++) {
                if (ConditionBlocks.OPS.get(i).op() == l.op()) {
                    op.setSelectedIndex(i);
                }
            }
            List<String> own = sheetValues.getOrDefault(l.sheet(), List.of());
            switch (l.op().kind()) {
                case VALUE, ORDINAL -> {
                    value.removeAllItems();
                    own.forEach(value::addItem);
                    value.setSelectedItem(text(l.operand()));
                    operand.show(VALUE);
                }
                case VALUES -> {
                    values.setListData(own.toArray(String[]::new));
                    List<String> chosen = l.operand() instanceof RuleValue.ListValue list ? list.items().stream().map(ConditionEditor::text).toList()
                            : List.of();
                    List<Integer> indices = new ArrayList<>();
                    for (int i = 0; i < own.size(); i++) {
                        if (chosen.contains(own.get(i))) {
                            indices.add(i);
                        }
                    }
                    values.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
                    operand.show(VALUES);
                }
                case FLAG -> {
                    flag.setSelectedIndex(truthy(l.operand()) ? 0 : 1);
                    operand.show(FLAG);
                }
                case SHEET, ORDINAL_SHEET -> {
                    other.removeAllItems();
                    sheetValues.keySet().stream().filter(s -> !s.equals(l.sheet())).forEach(other::addItem);
                    other.setSelectedItem(text(l.operand()));
                    operand.show(OTHER);
                }
                case COUNT -> {
                    count.setValue(l.operand() instanceof RuleValue.Int i ? i.value().min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue() : 0);
                    operand.show(COUNT);
                }
            }
        } finally {
            updating = false;
        }
    }

    private static String text(RuleValue v) {
        return v instanceof RuleValue.Text t ? t.value() : ConstraintBonds.pythonJson(v, false, false);
    }

    /** Python truth of a flag operand, as the sieve reads present. */
    private static boolean truthy(RuleValue v) {
        return switch (v) {
            case RuleValue.Bool b -> b.value();
            case RuleValue.Null n -> false;
            case RuleValue.Int i -> i.value().signum() != 0;
            case RuleValue.Real r -> r.value() != 0;
            case RuleValue.Text t -> !t.value().isEmpty();
            case RuleValue.ListValue l -> !l.items().isEmpty();
            case RuleValue.MapValue m -> !m.entries().isEmpty();
        };
    }

    // ---- for tests ---------------------------------------------------------------------------------------------------

    /** The tree as indented lines. */
    String dump() {
        return ConditionTree.dump((DefaultMutableTreeNode) tree.getModel().getRoot());
    }

    String hint() {
        return hint.getText();
    }

    String json() {
        return json.getText();
    }
}
