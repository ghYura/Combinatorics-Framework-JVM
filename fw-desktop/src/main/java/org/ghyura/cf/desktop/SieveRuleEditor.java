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

import org.ghyura.cf.desktop.session.SieveDocument;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.RuleValue;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.ghyura.cf.desktop.session.SieveDocument.*;

/** Structured editing over the original rule. Fields that were not edited keep their exact JSON representation. */
final class SieveRuleEditor {
    private final JPanel root = new JPanel(new BorderLayout(4, 4));
    private final Form form = new Form();
    private final JScrollPane scroll = new JScrollPane(form);
    private final JTextField id = new JTextField(), description = new JTextField();
    private final JComboBox<String> polarity = new JComboBox<>(new String[]{"forbid", "require"});
    private final JCheckBox adjacent = new JCheckBox("Adjacent"), distance = new JCheckBox("Within");
    private final JSpinner within = new JSpinner(new SpinnerNumberModel(0L, 0L, Long.MAX_VALUE, 1L));
    private final JLabel kind = new JLabel("Select or create a rule");
    private final DefaultListModel<String> sheetNames = new DefaultListModel<>();
    private final JList<String> sheets = new JList<>(sheetNames);
    private final JTable table = new JTable();
    private final JPanel body = new JPanel(new BorderLayout(4, 4));
    private final JTabbedPane parts = new JTabbedPane();
    private final JComboBox<String> source = new JComboBox<>(), target = new JComboBox<>();
    private final FormulaEditor formula;
    private final ConditionEditor assertion, guard;
    private final Consumer<RuleValue.MapValue> apply;
    private final Runnable changed;
    private RuleValue.MapValue original;
    private Map<String, List<String>> values = Map.of();
    private List<String> columns = List.of();
    private final Map<java.awt.Component, Boolean> readOnly = new java.util.IdentityHashMap<>();
    private boolean editable = true;
    private boolean loading, dirty, bodyDirty, guardDirty, sheetsDirty, mappingSheetsDirty;

    SieveRuleEditor(Consumer<RuleValue.MapValue> apply, Runnable discard, Runnable changed) {
        this.apply = apply; this.changed = changed;
        formula = new FormulaEditor(this::bodyChanged);
        assertion = new ConditionEditor("Assertion", "Build the condition to require or forbid", this::bodyChanged);
        guard = new ConditionEditor("Only when", "Always active (no guard)", () -> { if (!loading) { guardDirty = true; touch(); } });
        JPanel metadata = new JPanel(new GridLayout(0, 1, 3, 3));
        metadata.add(kind); metadata.add(labeled("Rule ID", id)); metadata.add(labeled("Description", description));
        JPanel gates = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        gates.add(polarity); gates.add(adjacent); gates.add(distance); gates.add(within); metadata.add(gates);
        polarity.setToolTipText("Forbid rejects a matching rule body; require rejects when it does not match. The guard and gate limit applicability.");
        within.setToolTipText("Largest column distance, including zero. Adjacent and Within may both be active.");
        ((JSpinner.DefaultEditor) within.getEditor()).getTextField().setColumns(6);
        form.add(metadata, BorderLayout.NORTH);
        parts.addTab("Rule body", body); parts.addTab("Only when", guard.component()); parts.setPreferredSize(new java.awt.Dimension(420, 360)); form.add(parts);
        scroll.getVerticalScrollBar().setUnitIncrement(18); root.add(scroll);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton use = new JButton("Apply rule"), revert = new JButton("Discard draft");
        use.addActionListener(e -> { try { this.apply.accept(draft()); } catch (RuntimeException problem) { error(problem); } });
        revert.addActionListener(e -> discard.run()); actions.add(use); actions.add(revert); root.add(actions, BorderLayout.SOUTH);
        listen(id, this::touch); listen(description, this::touch);
        polarity.addActionListener(e -> touch()); adjacent.addActionListener(e -> touch()); distance.addActionListener(e -> touch());
        within.addChangeListener(e -> touch());
        source.addActionListener(e -> { if (!loading) { mappingSheetsDirty = true; mappingChoices(); bodyChanged(); } }); target.addActionListener(e -> { if (!loading) { mappingSheetsDirty = true; mappingChoices(); bodyChanged(); } });
        sheets.setVisibleRowCount(3); sheets.setToolTipText("Select the sheets, then click Use chosen sheets. Ctrl/Shift selects several.");
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); table.putClientProperty("terminateEditOnFocusLost", true);
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    }

    @SuppressWarnings("serial")
    private static final class Form extends JPanel implements Scrollable {
        Form() { super(new BorderLayout(4, 4)); }
        public java.awt.Dimension getPreferredScrollableViewportSize() { return new java.awt.Dimension(420, 400); }
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) { return 18; }
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 18); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return getParent() != null && getParent().getHeight() > getPreferredSize().height; }
    }

    JComponent component() { return root; }
    boolean dirty() { return dirty || table.isEditing(); }
    void editable(boolean allowed) {
        if (editable == allowed) return;
        editable = allowed;
        if (allowed) { readOnly.forEach(java.awt.Component::setEnabled); readOnly.clear(); }
        else disable(root);
    }
    private void disable(java.awt.Component component) {
        readOnly.put(component, component.isEnabled()); component.setEnabled(false);
        if (component instanceof java.awt.Container parent) for (var child : parent.getComponents()) disable(child);
    }

    void show(SieveDocument document, RuleValue.MapValue rule, Map<String, List<String>> offered) {
        boolean allowed = editable; editable(true);
        loading = true;
        try {
            original = rule; values = offered;
            body.removeAll(); sheetNames.clear();
            if (rule == null) {
                kind.setText("Select a rule, or create one from the toolbar.");
                id.setText(""); description.setText(""); guard.show(null, values);
                return;
            }
            String type = SieveDocument.kind(rule); kind.setText(switch (type) {
                case "pairs" -> "Tuple rows · any listed row may match";
                case "sets" -> "Value groups · Cartesian product of the selected sets";
                case "mapping" -> "Allowed mapping · polarity and gate do not apply";
                case "when" -> "Formula · predicate over the chosen sheets";
                default -> "Assertion · condition tree";
            });
            boolean mappingRule = type.equals("mapping"), gated = !mappingRule && !type.equals("assert");
            polarity.setEnabled(!mappingRule); adjacent.setEnabled(gated); distance.setEnabled(gated); within.setEnabled(gated);
            kind.setToolTipText(mappingRule ? "For each declared source value, every present target value must be allowed. Undeclared source values are unrestricted. Only when still applies."
                    : "Forbid removes a matching combination; require removes a nonmatching combination where the rule applies. Use Check impact for actual outcomes.");
            id.setText(text(rule.entries().get("id"))); description.setText(text(rule.entries().get("desc")));
            polarity.setSelectedItem(text(rule.entries().getOrDefault("polarity", new RuleValue.Text(type.equals("assert") ? "require" : "forbid"))));
            var gate = map(rule.entries().get("gate"));
            adjacent.setSelected(gate.get("adjacent") instanceof RuleValue.Bool b && b.value());
            distance.setSelected(gate.containsKey("within"));
            within.setValue(gate.get("within") instanceof RuleValue.Int n ? n.value().longValueExact() : 0L);
            guard.show(rule.entries().get("condition"), values);
            var names = new LinkedHashSet<>(values.keySet()); names.addAll(SieveDocument.sheets(rule));
            ConstraintBonds.read(object("constraints", new RuleValue.ListValue(List.of(rule)))).bonds().forEach(b -> names.addAll(b.sides().keySet()));
            names.forEach(sheetNames::addElement);
            columns = !list(rule.entries().get("sheets")).isEmpty() ? list(rule.entries().get("sheets")).stream().map(SieveDocument::text).toList()
                    : new ArrayList<>(ConstraintBonds.read(object("constraints", new RuleValue.ListValue(List.of(rule)))).bonds().stream()
                    .flatMap(b -> b.sides().keySet().stream()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
            var allColumns = new LinkedHashSet<>(columns);
            map(rule.entries().get("sets")).keySet().forEach(allColumns::add);
            list(rule.entries().get("pairs")).forEach(v -> allColumns.addAll(map(v).keySet()));
            columns = List.copyOf(allColumns);
            int[] chosen = columns.stream().mapToInt(sheetNames::indexOf).filter(n -> n >= 0).toArray(); sheets.setSelectedIndices(chosen);
            if (type.equals("when")) {
                formula.show(text(rule.entries().get("when")), List.copyOf(columns), document::parameterNames);
                body.add(sheetChooser(), BorderLayout.NORTH); body.add(formula.component());
            } else if (type.equals("assert")) {
                assertion.show(rule.entries().get("assert"), values); body.add(assertion.component());
            } else if (type.equals("mapping")) {
                var mapping = map(rule.entries().get("mapping"));
                source.removeAllItems(); target.removeAllItems(); names.forEach(s -> { source.addItem(s); target.addItem(s); });
                source.setSelectedItem(text(mapping.get("source"))); target.setSelectedItem(text(mapping.get("target")));
                JPanel selectors = new JPanel(new GridLayout(0, 1)); selectors.add(labeled("Source sheet", source)); selectors.add(labeled("Target sheet", target));
                body.add(selectors, BorderLayout.NORTH);
                var rows = new ArrayList<Object[]>();
                map(mapping.get("allow")).forEach((s, targets) -> {
                    if (list(targets).isEmpty()) rows.add(new Object[]{new RuleValue.Text(s), null});
                    else list(targets).forEach(t -> rows.add(new Object[]{new RuleValue.Text(s), t}));
                });
                grid(List.of("Source value", "Allowed target"), rows); body.add(new JScrollPane(table)); body.add(rowButtons(), BorderLayout.SOUTH);
            } else {
                var rows = new ArrayList<Object[]>();
                if (type.equals("pairs")) {
                    list(rule.entries().get("pairs")).forEach(v -> rows.add(columns.stream().map(s -> map(v).get(s)).toArray()));
                } else {
                    var sets = map(rule.entries().get("sets"));
                    int count = sets.values().stream().mapToInt(v -> list(v).size()).max().orElse(0);
                    for (int i = 0; i < count; i++) {
                        Object[] row = new Object[columns.size()];
                        for (int c = 0; c < columns.size(); c++) { var vs = list(sets.get(columns.get(c))); if (i < vs.size()) row[c] = vs.get(i); }
                        rows.add(row);
                    }
                }
                grid(columns, rows); body.add(sheetChooser(), BorderLayout.NORTH); body.add(new JScrollPane(table)); body.add(rowButtons(), BorderLayout.SOUTH);
            }
            parts.setSelectedIndex(0);
        } finally { loading = false; dirty = bodyDirty = guardDirty = sheetsDirty = mappingSheetsDirty = false; body.revalidate(); body.repaint(); editable(allowed); SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new java.awt.Point())); }
    }

    private JComponent sheetChooser() {
        JPanel panel = new JPanel(new BorderLayout()); panel.add(new JScrollPane(sheets));
        JButton use = new JButton("Use chosen sheets"); use.addActionListener(e -> {
            if (sheets.getSelectedValuesList().size() < 2) { error(new IllegalArgumentException("Choose at least two sheets.")); return; }
            List<String> previous = columns; columns = sheets.getSelectedValuesList();
            if (SieveDocument.kind(original).equals("when")) formula.offer(columns);
            else {
                stopEditing(); var rows = new ArrayList<Object[]>();
                for (int r = 0; r < table.getRowCount(); r++) {
                    Object[] row = new Object[columns.size()];
                    for (int c = 0; c < columns.size(); c++) { int old = previous.indexOf(columns.get(c)); if (old >= 0) row[c] = table.getValueAt(r, old); }
                    rows.add(row);
                }
                grid(columns, rows);
            }
            sheetsDirty = true; bodyChanged();
        }); panel.add(use, BorderLayout.SOUTH); return panel;
    }

    private void mappingChoices() {
        if (original == null || !kind(original).equals("mapping")) return;
        stopEditing();
        List<Object[]> rows = new ArrayList<>();
        for (int r = 0; r < table.getRowCount(); r++) rows.add(new Object[]{table.getValueAt(r, 0), table.getValueAt(r, 1)});
        grid(List.of("Source value", "Allowed target"), rows);
    }

    private void grid(List<String> labels, List<Object[]> rows) {
        var model = new DefaultTableModel(rows.toArray(Object[][]::new), labels.toArray());
        model.addTableModelListener(e -> bodyChanged()); table.setModel(model);
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) { super.setValue(display(value)); }
        });
        for (int c = 0; c < labels.size(); c++) {
            String sheet = labels.get(c);
            if (SieveDocument.kind(original).equals("mapping")) sheet = String.valueOf((c == 0 ? source : target).getSelectedItem());
            final JComboBox<Object> choices = new JComboBox<>(); choices.setEditable(true); choices.addItem("");
            values.getOrDefault(sheet, List.of()).forEach(v -> choices.addItem(new Choice(new RuleValue.Text(v))));
            var editor = new DefaultCellEditor(choices) {
                private Object initial;
                @Override public java.awt.Component getTableCellEditorComponent(JTable t, Object v, boolean selected, int row, int col) {
                    initial = v;
                    return super.getTableCellEditorComponent(t, v instanceof RuleValue rv ? new Choice(rv) : "", selected, row, col);
                }
                @Override public Object getCellEditorValue() {
                    Object v = super.getCellEditorValue();
                    if (v instanceof Choice choice) return choice.value();
                    String s = String.valueOf(v);
                    if (s.equals(display(initial))) return initial;
                    return s.isEmpty() ? null : new RuleValue.Text(s);
                }
            };
            table.getColumnModel().getColumn(c).setCellEditor(editor); table.getColumnModel().getColumn(c).setPreferredWidth(155);
        }
    }

    private record Choice(RuleValue value) { @Override public String toString() { return display(value); } }
    private static String display(Object value) {
        return value == null ? "" : value instanceof RuleValue.Text t ? t.value()
                : value instanceof RuleValue rv ? ConstraintBonds.pythonJson(rv, false, false) + " [JSON]" : value.toString();
    }

    private JComponent rowButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton add = new JButton("Add row"), remove = new JButton("Remove row");
        add.addActionListener(e -> { stopEditing(); ((DefaultTableModel) table.getModel()).addRow(new Object[table.getColumnCount()]); });
        remove.addActionListener(e -> { stopEditing(); int[] selected = table.getSelectedRows(); for (int i = selected.length - 1; i >= 0; i--) ((DefaultTableModel) table.getModel()).removeRow(selected[i]); });
        buttons.add(add); buttons.add(remove); buttons.setToolTipText("Choose existing values or type text. Use Advanced JSON for new numeric/boolean literals."); return buttons;
    }

    RuleValue.MapValue draft() {
        if (original == null) throw new IllegalStateException("Select or create a rule first.");
        stopEditing();
        try { within.commitEdit(); } catch (java.text.ParseException e) { throw new IllegalArgumentException("Within must be a nonnegative integer.", e); }
        var fields = new LinkedHashMap<>(original.entries());
        updateText(fields, "id", id.getText()); updateText(fields, "desc", description.getText());
        String defaultPolarity = kind(original).equals("assert") ? "require" : "forbid";
        if (!String.valueOf(polarity.getSelectedItem()).equals(text(fields.getOrDefault("polarity", new RuleValue.Text(defaultPolarity)))))
            fields.put("polarity", new RuleValue.Text(String.valueOf(polarity.getSelectedItem())));
        var gate = new LinkedHashMap<>(map(fields.get("gate")));
        boolean oldAdjacent = gate.get("adjacent") instanceof RuleValue.Bool b && b.value();
        if (adjacent.isSelected() != oldAdjacent) { if (adjacent.isSelected()) gate.put("adjacent", new RuleValue.Bool(true)); else gate.remove("adjacent"); }
        if (distance.isSelected()) gate.put("within", RuleValue.Int.of(((Number) within.getValue()).longValue())); else gate.remove("within");
        if (!gate.equals(map(fields.get("gate")))) { if (gate.isEmpty()) fields.remove("gate"); else fields.put("gate", new RuleValue.MapValue(gate)); }
        if (guardDirty) { RuleValue condition = guard.condition(); if (condition == null) fields.remove("condition"); else fields.put("condition", condition); }
        if (bodyDirty) switch (kind(original)) {
            case "when" -> { String value = formula.formula(); if (value == null || value.isBlank()) throw new IllegalArgumentException("Complete the formula first."); fields.put("when", new RuleValue.Text(value)); if (sheetsDirty) fields.put("sheets", texts(columns)); }
            case "assert" -> { RuleValue value = assertion.condition(); if (value == null) throw new IllegalArgumentException("Complete the assertion first."); fields.put("assert", value); }
            case "mapping" -> {
                Map<String, List<RuleValue>> allow = new LinkedHashMap<>();
                for (int r = 0; r < table.getRowCount(); r++) {
                    Object s = table.getValueAt(r, 0), t = table.getValueAt(r, 1);
                    if (s == null && t == null) continue;
                    if (!(s instanceof RuleValue.Text name)) throw new IllegalArgumentException("Mapping source keys must be text.");
                    var ts = allow.computeIfAbsent(name.value(), ignored -> new ArrayList<>()); if (t instanceof RuleValue v) ts.add(v);
                }
                Map<String, RuleValue> mapped = new LinkedHashMap<>(); allow.forEach((s, ts) -> mapped.put(s, new RuleValue.ListValue(ts)));
                fields.put("mapping", object("source", new RuleValue.Text(String.valueOf(source.getSelectedItem())), "target", new RuleValue.Text(String.valueOf(target.getSelectedItem())), "allow", new RuleValue.MapValue(mapped)));
                if (mappingSheetsDirty) fields.remove("sheets");
            }
            case "pairs" -> {
                List<RuleValue> rows = new ArrayList<>();
                for (int r = 0; r < table.getRowCount(); r++) {
                    Map<String, RuleValue> row = new LinkedHashMap<>();
                    for (int c = 0; c < columns.size(); c++) if (table.getValueAt(r, c) instanceof RuleValue v) row.put(columns.get(c), v);
                    if (!row.isEmpty()) rows.add(new RuleValue.MapValue(row));
                }
                fields.put("pairs", new RuleValue.ListValue(rows)); if (sheetsDirty) fields.put("sheets", texts(columns));
            }
            case "sets" -> {
                Map<String, RuleValue> sets = new LinkedHashMap<>();
                for (int c = 0; c < columns.size(); c++) { List<RuleValue> items = new ArrayList<>(); for (int r = 0; r < table.getRowCount(); r++) if (table.getValueAt(r, c) instanceof RuleValue v) items.add(v); sets.put(columns.get(c), new RuleValue.ListValue(items)); }
                fields.put("sets", new RuleValue.MapValue(sets)); if (sheetsDirty) fields.put("sheets", texts(columns));
            }
            default -> throw new IllegalArgumentException("Unknown rule kind");
        }
        return new RuleValue.MapValue(fields);
    }

    private void updateText(Map<String, RuleValue> fields, String key, String value) {
        if (!value.equals(text(fields.get(key)))) { if (value.isBlank()) fields.remove(key); else fields.put(key, new RuleValue.Text(value)); }
    }
    private void stopEditing() { if (table.isEditing() && !table.getCellEditor().stopCellEditing()) throw new IllegalStateException("Finish editing the current cell."); }
    private void bodyChanged() { if (!loading) { bodyDirty = true; touch(); } }
    private void touch() { if (!loading) { dirty = true; changed.run(); } }
    private void error(RuntimeException failure) { JOptionPane.showMessageDialog(root, failure.getMessage(), "Rule draft", JOptionPane.ERROR_MESSAGE); }
    static JPanel labeled(String label, JComponent field) { JPanel panel = new JPanel(new BorderLayout(4, 0)); JLabel name = new JLabel(label); name.setLabelFor(field); panel.add(name, BorderLayout.WEST); panel.add(field); return panel; }
    static void listen(javax.swing.text.JTextComponent field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { action.run(); }
            public void removeUpdate(DocumentEvent e) { action.run(); }
            public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }
}
