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

import org.ghyura.cf.desktop.session.FormulaBlocks;
import org.ghyura.cf.desktop.session.FormulaBlocks.Block;
import org.ghyura.cf.desktop.session.FormulaBlocks.Kind;
import org.ghyura.cf.model.rule.ExprParser;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A when predicate in the legacy formula dialog's two modes. Text: the predicate as typed. Blocks: the nested block builder
 * as a tree of slots, filled from the palette (compare, and / or, not, + - x, sheet.param, number) into the selected slot
 * when the block gives what the slot takes, its operator, sheet, parameter or number changed in the row below, a block
 * deleted, or moved into an empty slot of its type outside itself; a right click offers the same. The blocks compile to the
 * text exactly as the legacy compileT did, and a text whose parts all have a block opens as blocks.
 */
final class FormulaEditor {

    private static final String TEXT = "text";
    private static final String BLOCKS = "blocks";

    /** A slot of the tree: its legacy path and what it shows. */
    private record Slot(String path, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final JPanel panel = new JPanel(new BorderLayout(4, 4));
    private final JToggleButton textMode = Hints.of(new JToggleButton("Text", true), "Write the formula as text.");
    private final JToggleButton blocksMode = Hints.of(new JToggleButton("Blocks"), "Build the formula from blocks instead of typing it.");
    private final WrapLayout.Cards cards = new WrapLayout.Cards();
    private final JTextField text = Hints.of(new JTextField(36), "The formula as text, for example: A.price + B.price < 100 (sheet name, a dot, then the number attached to its values). Filled with the selected rule's formula; type to change it.");
    private final JLabel hint = new JLabel(" ");
    private final JTree tree = Hints.of(new JTree(new DefaultMutableTreeNode()), "The formula's blocks. Select one to change it; an empty place shows where a block can go.");
    private final javax.swing.JTextArea built = Hints.of(new javax.swing.JTextArea("…", 1, 24), "The formula the blocks make, as text. Filled as you build the formula from blocks.");
    private final Map<Kind, JButton> palette = new EnumMap<>(Kind.class);
    private final JComboBox<String> operator = Hints.of(new JComboBox<String>(), "The operation of the selected block, such as + or <.");
    private final JComboBox<String> sheet = Hints.of(new JComboBox<String>(), "The sheet whose value the selected block uses.");
    private final JComboBox<String> param = Hints.of(new JComboBox<String>(), "Which number attached to that sheet's values to use, such as price.");
    private final JTextField number = Hints.of(new JTextField(6), "A fixed number for the selected place. Filled by you when a number block is selected.");
    private final JLabel dot = new JLabel(".");
    private final JLabel operatorLabel = FieldNames.label("operation:", operator);
    private final JLabel sheetLabel = FieldNames.label("sheet:", sheet);
    private final JLabel paramLabel = FieldNames.label("parameter:", param);
    private final JLabel numberLabel = FieldNames.label("number:", number);
    private final JButton delete = Hints.of(new JButton("Delete"), "Delete the selected block.");
    private final JButton move = Hints.of(new JButton("Move…"), "Pick up the selected block to move it somewhere else.");
    private final JButton moveHere = Hints.of(new JButton("Move here"), "Put the picked-up block into the selected empty place.");
    private final Runnable changed;
    private Block root;
    private String moving;
    private boolean blocks;
    private boolean updating;
    private List<String> sheets = List.of();
    private Function<String, List<String>> paramsOf = s -> List.of("value", "pos");

    FormulaEditor(Runnable changed) {
        this.changed = changed;
        ButtonGroup modes = new ButtonGroup();
        modes.add(textMode);
        modes.add(blocksMode);
        textMode.addActionListener(e -> toText());
        blocksMode.addActionListener(e -> {
            if (!toBlocks()) {
                textMode.setSelected(true);
            }
        });
        JPanel top = WrapLayout.row();
        top.add(FieldNames.label("Formula over the sheets' parameters:", text));
        top.add(textMode);
        top.add(blocksMode);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        text.getDocument().addDocumentListener(listener(() -> {
            if (!updating) {
                changed.run();
            }
        }));
        JPanel textCard = new JPanel(new BorderLayout());
        textCard.add(text, BorderLayout.NORTH);
        cards.add(textCard, TEXT);
        cards.add(blocksCard(), BLOCKS);
        panel.add(top, BorderLayout.NORTH);
        panel.add(cards, BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
    }

    private JPanel blocksCard() {
        JPanel row = WrapLayout.row();
        row.add(new JLabel("palette:"));
        for (Kind kind : Kind.values()) {
            JButton b = Hints.of(new JButton(kind.label()), "Put a new \"" + kind.label() + "\" block in the selected place of the formula; a block "
                    + "already there is replaced, with all it holds.");
            b.addActionListener(e -> put(kind));
            palette.put(kind, b);
            row.add(b);
        }
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(true);
        tree.addTreeSelectionListener(e -> showSelected());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "delete-block");
        tree.getActionMap().put("delete-block", action(this::delete));
        operator.addActionListener(e -> {
            if (!updating && operator.getSelectedIndex() >= 0 && FormulaBlocks.at(root, selected()) instanceof FormulaBlocks.Binary b) {
                setOperator(FormulaBlocks.ops(b.kind()).get(operator.getSelectedIndex()).value());
            }
        });
        sheet.addActionListener(e -> {
            if (!updating && sheet.getSelectedItem() != null) {
                String s = (String) sheet.getSelectedItem();
                List<String> params = paramsOf.apply(s);
                setParam(s, params.isEmpty() ? "value" : params.getFirst());
            }
        });
        param.addActionListener(e -> {
            if (!updating && param.getSelectedItem() != null && FormulaBlocks.at(root, selected()) instanceof FormulaBlocks.Param p) {
                setParam(p.sheet(), (String) param.getSelectedItem());
            }
        });
        number.getDocument().addDocumentListener(listener(() -> {
            if (!updating) {
                setNumber(number.getText());
            }
        }));
        delete.addActionListener(e -> delete());
        move.addActionListener(e -> armMove());
        moveHere.addActionListener(e -> moveHere());
        JPanel edit = WrapLayout.row();
        edit.add(new JLabel("selected:"));
        edit.add(operatorLabel);
        edit.add(operator);
        edit.add(sheetLabel);
        edit.add(sheet);
        edit.add(dot);
        edit.add(paramLabel);
        edit.add(param);
        edit.add(numberLabel);
        edit.add(number);
        edit.add(delete);
        edit.add(move);
        edit.add(moveHere);
        built.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        built.setEditable(false);
        built.setLineWrap(true);
        built.setOpaque(false);
        JPanel formula = new JPanel(new BorderLayout(4, 0));
        formula.add(FieldNames.label("makes:", built), BorderLayout.WEST);
        formula.add(built, BorderLayout.CENTER);
        JScrollPane scroll = FieldNames.titled(tree, "Blocks of the formula");
        scroll.setPreferredSize(new Dimension(420, 150));
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        for (JComponent c : List.of(row, scroll, edit, formula)) {
            c.setAlignmentX(0f);
            card.add(c);
        }
        card.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return card;
    }

    JComponent component() {
        return panel;
    }

    /** Starts from a predicate in text mode; the sheets its parameter blocks offer and the parameters of each. */
    void show(String when, List<String> offered, Function<String, List<String>> params) {
        sheets = List.copyOf(offered);
        paramsOf = params;
        root = null;
        moving = null;
        blocks = false;
        updating = true;
        text.setText(when == null ? "" : when);
        updating = false;
        textMode.setSelected(true);
        cards.show(TEXT);
        hint.setText(" ");
        rebuild("");
    }

    /** The sheets parameter blocks offer, as the rule's chosen sheets change. */
    void offer(List<String> offered) {
        sheets = List.copyOf(offered);
        showSelected();
    }

    /** The predicate of the mode shown: the text as typed, or the blocks compiled (null while a slot is empty). */
    String formula() {
        return blocks ? FormulaBlocks.compile(root) : text.getText();
    }

    boolean blocks() {
        return blocks;
    }

    /** Switches to blocks, reading the text into them; stays on the text with the reason when a part has no block. */
    boolean toBlocks() {
        if (blocks) {
            return true;
        }
        try {
            root = FormulaBlocks.parse(text.getText());
        } catch (ExprParser.SyntaxError e) {
            hint.setText("The text is no formula yet (" + e.getMessage() + "); fix it or clear it to build blocks.");
            return false;
        } catch (FormulaBlocks.NotBlocks e) {
            hint.setText("The blocks have no piece for " + e.getMessage() + "; keep editing it as text.");
            return false;
        }
        blocks = true;
        moving = null;
        blocksMode.setSelected(true);
        cards.show(BLOCKS);
        hint.setText("Select a + slot or a block, then press a palette block to put it there (it replaces a block that is there); right click for more.");
        rebuild("");
        changed.run();
        return true;
    }

    /** Switches to the text, which follows the blocks whenever they are complete. */
    void toText() {
        if (!blocks) {
            return;
        }
        blocks = false;
        textMode.setSelected(true);
        cards.show(TEXT);
        hint.setText(FormulaBlocks.compile(root) == null && root != null
                ? "The blocks have empty slots; the text keeps the last complete formula." : " ");
        changed.run();
    }

    // ---- block edits, on the selected slot ---------------------------------------------------------------------------

    /** Selects the slot at a legacy path. */
    void select(String path) {
        DefaultMutableTreeNode node = find((DefaultMutableTreeNode) tree.getModel().getRoot(), path);
        if (node != null) {
            tree.setSelectionPath(new TreePath(node.getPath()));
        }
    }

    /** The legacy path of the selected slot, "" (the root) when none is. */
    String selected() {
        TreePath p = tree.getSelectionPath();
        return p == null ? "" : ((Slot) ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject()).path();
    }

    /** Puts a new block of a kind into the selected slot when it gives what the slot takes. */
    boolean put(Kind kind) {
        String at = selected();
        if (!FormulaBlocks.fits(root, at, kind)) {
            hint.setText("A " + kind.label() + " block gives " + (kind.produces() == FormulaBlocks.Type.BOOL ? "a truth value" : "a number")
                    + "; this slot takes " + (FormulaBlocks.slot(root, at) == FormulaBlocks.Type.BOOL ? "a truth value" : "a number") + ".");
            return false;
        }
        Block replaced = FormulaBlocks.at(root, at);
        edit(FormulaBlocks.with(root, at, FormulaBlocks.newNode(kind, sheets, paramsOf)), at);
        // replacing takes a whole branch away: say what went, and how to get it back
        hint.setText(replaced == null ? " " : "Replaced " + FormulaBlocks.compileOrLabel(replaced) + " with a new " + kind.label()
                + " block. Discard draft returns to the rule as last applied.");
        return true;
    }

    void delete() {
        String at = selected();
        if (FormulaBlocks.at(root, at) != null) {
            hint.setText(" ");
            edit(FormulaBlocks.with(root, at, null), at);
        }
    }

    /** Marks the selected block to move; Move here then puts it into the selected empty slot. */
    void armMove() {
        String at = selected();
        if (at.isEmpty() || FormulaBlocks.at(root, at) == null) {
            hint.setText("Select a block inside the formula to move it.");
            return;
        }
        moving = at;
        hint.setText("Moving " + FormulaBlocks.compileOrLabel(FormulaBlocks.at(root, at)) + ": select an empty slot of its type and press Move here.");
        showSelected();
    }

    boolean moveHere() {
        String at = selected();
        if (moving == null || !FormulaBlocks.canMove(root, moving, at)) {
            hint.setText(moving == null ? "Press Move… on a block first." : "That block goes only into an empty slot of its type outside itself.");
            return false;
        }
        Block moved = FormulaBlocks.move(root, moving, at);
        moving = null;
        hint.setText(" ");
        edit(moved, at);
        return true;
    }

    void setOperator(String op) {
        String at = selected();
        if (FormulaBlocks.at(root, at) instanceof FormulaBlocks.Binary b && !b.op().equals(op)) {
            edit(FormulaBlocks.with(root, at, new FormulaBlocks.Binary(b.kind(), op, b.a(), b.b())), at);
        }
    }

    void setParam(String sheetName, String paramName) {
        String at = selected();
        if (FormulaBlocks.at(root, at) instanceof FormulaBlocks.Param p && !(p.sheet().equals(sheetName) && p.param().equals(paramName))) {
            edit(FormulaBlocks.with(root, at, new FormulaBlocks.Param(sheetName, paramName)), at);
        }
    }

    void setNumber(String val) {
        String at = selected();
        if (FormulaBlocks.at(root, at) instanceof FormulaBlocks.Num n && !n.val().equals(val)) {
            root = FormulaBlocks.with(root, at, new FormulaBlocks.Num(val));
            DefaultMutableTreeNode node = find((DefaultMutableTreeNode) tree.getModel().getRoot(), at);
            if (node != null) {
                node.setUserObject(new Slot(at, val));
                ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
            }
            compiled();
        }
    }

    private void edit(Block changedRoot, String select) {
        root = changedRoot;
        rebuild(select);
        compiled();
    }

    private void compiled() {
        String formula = FormulaBlocks.compile(root);
        built.setText(formula == null ? "…" : formula);
        if (formula != null) {
            updating = true;
            text.setText(formula);
            updating = false;
        }
        changed.run();
    }

    // ---- the tree ----------------------------------------------------------------------------------------------------

    private void rebuild(String select) {
        Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();
        DefaultMutableTreeNode top = null;
        for (String path : FormulaBlocks.slots(root)) {
            Block b = FormulaBlocks.at(root, path);
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Slot(path, b == null ? empty(FormulaBlocks.slot(root, path))
                    : FormulaBlocks.label(b)));
            nodes.put(path, node);
            if (path.isEmpty()) {
                top = node;
            } else {
                int dot = path.lastIndexOf('.');
                nodes.get(dot < 0 ? "" : path.substring(0, dot)).add(node);
            }
        }
        tree.setModel(new DefaultTreeModel(top));
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        String formula = FormulaBlocks.compile(root);
        built.setText(formula == null ? "…" : formula);
        select(nodes.containsKey(select) ? select : "");
        showSelected();
    }

    private static String empty(FormulaBlocks.Type type) {
        return type == FormulaBlocks.Type.BOOL ? "+ condition slot (empty)" : "+ number slot (empty)";
    }

    private static DefaultMutableTreeNode find(DefaultMutableTreeNode node, String path) {
        if (node == null) {
            return null;
        }
        java.util.Enumeration<javax.swing.tree.TreeNode> all = node.preorderEnumeration();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) all.nextElement();
            if (((Slot) n.getUserObject()).path().equals(path)) {
                return n;
            }
        }
        return null;
    }

    /** Fills the row below the tree for the selected block and enables what fits the selected slot. */
    private void showSelected() {
        String at = selected();
        Block b = FormulaBlocks.at(root, at);
        updating = true;
        try {
            for (Map.Entry<Kind, JButton> e : palette.entrySet()) {
                e.getValue().setEnabled(FormulaBlocks.isSlot(root, at) && FormulaBlocks.fits(root, at, e.getKey()));
            }
            operator.removeAllItems();
            if (b instanceof FormulaBlocks.Binary binary) {
                for (FormulaBlocks.Op op : FormulaBlocks.ops(binary.kind())) {
                    operator.addItem(op.label());
                    if (op.value().equals(binary.op())) {
                        operator.setSelectedIndex(operator.getItemCount() - 1);
                    }
                }
            }
            operator.setVisible(b instanceof FormulaBlocks.Binary);
            operatorLabel.setVisible(operator.isVisible());
            sheet.removeAllItems();
            param.removeAllItems();
            if (b instanceof FormulaBlocks.Param p) {
                java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(sheets);
                names.add(p.sheet());
                names.forEach(sheet::addItem);
                sheet.setSelectedItem(p.sheet());
                java.util.LinkedHashSet<String> params = new java.util.LinkedHashSet<>(paramsOf.apply(p.sheet()));
                params.add(p.param());
                params.forEach(param::addItem);
                param.setSelectedItem(p.param());
            }
            sheet.setVisible(b instanceof FormulaBlocks.Param);
            sheetLabel.setVisible(sheet.isVisible());
            dot.setVisible(b instanceof FormulaBlocks.Param);
            param.setVisible(b instanceof FormulaBlocks.Param);
            paramLabel.setVisible(param.isVisible());
            number.setText(b instanceof FormulaBlocks.Num n ? n.val() : "");
            number.setVisible(b instanceof FormulaBlocks.Num);
            numberLabel.setVisible(number.isVisible());
            if (number.getParent() != null) {
                number.getParent().revalidate();
            }
            delete.setEnabled(b != null);
            move.setEnabled(b != null && !at.isEmpty());
            moveHere.setEnabled(moving != null && FormulaBlocks.canMove(root, moving, at));
        } finally {
            updating = false;
        }
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath p = tree.getPathForLocation(e.getX(), e.getY());
        if (p == null) {
            return;
        }
        tree.setSelectionPath(p);
        menu().show(tree, e.getX(), e.getY());
    }

    /** The right-click menu of the selected slot: the blocks that fit it, delete, move, move here. */
    JPopupMenu menu() {
        String at = selected();
        JPopupMenu menu = new JPopupMenu();
        JMenu put = new JMenu(FormulaBlocks.at(root, at) == null ? "Put a block here" : "Replace with");
        for (Kind kind : Kind.values()) {
            if (FormulaBlocks.fits(root, at, kind)) {
                JMenuItem item = new JMenuItem(kind.label());
                item.addActionListener(e -> put(kind));
                put.add(item);
            }
        }
        menu.add(put);
        for (JButton b : List.of(delete, move, moveHere)) {
            JMenuItem item = new JMenuItem(b.getText());
            item.setEnabled(b.isEnabled());
            item.addActionListener(e -> b.doClick());
            menu.add(item);
        }
        return menu;
    }

    // ---- for tests ---------------------------------------------------------------------------------------------------

    /** The blocks as indented lines, as the tree shows them. */
    String dump() {
        return ConditionTree.dump((DefaultMutableTreeNode) tree.getModel().getRoot());
    }

    String hint() {
        return hint.getText();
    }

    String built() {
        return built.getText();
    }

    String text() {
        return text.getText();
    }

    void setText(String formula) {
        text.setText(formula);
    }

    boolean paletteEnabled(Kind kind) {
        return palette.get(kind).isEnabled();
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    static DocumentListener listener(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    static javax.swing.Action action(Runnable r) {
        return new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        };
    }
}
