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
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.ghyura.cf.desktop.session.SieveDocument.*;

/** Parameters and declared ordering, with explicit JSON types and reorderable lists. */
final class SieveDataEditor {
    private final JPanel root = new JPanel(new BorderLayout(4, 4));
    private final DefaultTableModel params = new DefaultTableModel(new String[]{"Sheet", "Value", "Attribute", "Type", "Content"}, 0);
    private final JTable table = new JTable(params);
    private final JComboBox<String> orderSheet = new JComboBox<>(), mode = new JComboBox<>(new String[]{"none", "numeric", "date", "list"});
    private final JComboBox<String> orderValue = new JComboBox<>();
    private final DefaultListModel<RuleValue> ranks = new DefaultListModel<>();
    private final JList<RuleValue> orderList = new JList<>(ranks);
    private final Map<String, RuleValue> orders = new LinkedHashMap<>();
    private Map<String, List<String>> values = Map.of();
    private SieveDocument original = SieveDocument.empty();
    private boolean loading, dirty, paramsDirty, orderDirty;
    private String currentSheet;
    private final Runnable changed;
    /** What edits attributes and orders: off until a scenario's values are shown, so no draft starts with nothing to describe. */
    private final List<JComponent> editing = new ArrayList<>();
    private JButton removeAttribute, up, down, drop, revert;

    /** Off before a scenario is open; removals and moves need a selection, Discard data draft a draft. */
    private void gates() {
        boolean on = !values.isEmpty();
        int i = orderList.getSelectedIndex();
        for (JComponent c : editing) c.setEnabled(on);
        removeAttribute.setEnabled(on && table.getSelectedRow() >= 0);
        up.setEnabled(on && i > 0); down.setEnabled(on && i >= 0 && i < ranks.size() - 1); drop.setEnabled(on && i >= 0);
        revert.setEnabled(on && dirty());
    }

    /** @param problem shows why a draft cannot be applied (no dialog, so it also works without a display) */
    SieveDataEditor(Consumer<SieveDocument> apply, Runnable discard, Runnable changed, Consumer<String> problem) {
        this.changed = changed;
        table.putClientProperty("terminateEditOnFocusLost", true);
        table.setToolTipText("Text is literal. Number and boolean are typed. JSON supports arrays, objects and null.");
        Hints.of(table, "Numbers and other attributes of each sheet's values, which formula rules can use, for example price 10.");
        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"text", "number", "boolean", "JSON"})));
        JPanel attributes = new JPanel(new BorderLayout()); attributes.add(new JScrollPane(table));
        JPanel rows = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton add = Hints.of(new JButton("Add attribute"), "Add a row for one more attribute of a value."),
                remove = Hints.of(new JButton("Remove attribute"), "Delete the selected attribute rows.");
        add.addActionListener(e -> { stopEditing(); String s = values.keySet().stream().findFirst().orElse(""); params.addRow(new Object[]{s, values.getOrDefault(s, List.of()).stream().findFirst().orElse(""), "", "text", ""}); });
        remove.addActionListener(e -> { stopEditing(); int[] selected = table.getSelectedRows(); for (int i = selected.length - 1; i >= 0; i--) params.removeRow(selected[i]); });
        rows.add(add); rows.add(remove); attributes.add(rows, BorderLayout.SOUTH); editing.addAll(List.of(table, add, remove)); removeAttribute = remove;
        JPanel ordering = new JPanel(new BorderLayout(4, 4));
        Hints.of(orderSheet, "The sheet whose values you put in order.");
        Hints.of(mode, "How the sheet's values rank: by number, by date, in the list below, or not at all.");
        Hints.of(orderList, "The sheet's values in their rank, first to last. Select one to move or remove it.");
        Hints.of(orderValue, "A value of the sheet to add to the end of the ranked list.");
        JPanel choices = new JPanel(new java.awt.GridLayout(0, 1)); choices.add(SieveRuleEditor.labeled("Sheet", orderSheet)); choices.add(SieveRuleEditor.labeled("Order", mode));
        ordering.add(choices, BorderLayout.NORTH); ordering.add(FieldNames.titled(orderList, "Ranked values"));
        orderList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof RuleValue.Text t ? t.value() : ConstraintBonds.pythonJson((RuleValue) value, false, false) + " [JSON]", index, selected, focus);
            }
        });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING)); orderValue.setEditable(true); orderValue.setPrototypeDisplayValue("Choose a value…");
        controls.add(FieldNames.label("Value", orderValue)); controls.add(orderValue);
        JButton append = Hints.of(new JButton("Add value"), "Put the value beside it at the end of the ranked list."),
                up = Hints.of(new JButton("↑"), "Move the selected value one rank up."),
                down = Hints.of(new JButton("↓"), "Move the selected value one rank down."),
                drop = Hints.of(new JButton("Remove"), "Take the selected value out of the ranked list.");
        append.addActionListener(e -> { Object v = orderValue.getSelectedItem(); if (v != null) { mode.setSelectedItem("list"); ranks.addElement(new RuleValue.Text(v.toString())); storeOrder(); } });
        up.addActionListener(e -> move(-1)); down.addActionListener(e -> move(1));
        drop.addActionListener(e -> { int i = orderList.getSelectedIndex(); if (i >= 0) { ranks.remove(i); storeOrder(); } });
        controls.add(append); controls.add(up); controls.add(down); controls.add(drop); ordering.add(controls, BorderLayout.SOUTH);
        editing.addAll(List.of(orderSheet, mode, orderList, orderValue, append, up, down, drop)); this.up = up; this.down = down; this.drop = drop;
        JTabbedPane tabs = new JTabbedPane(); tabs.addTab("Parameters", attributes); tabs.addTab("Value orders", ordering); root.add(tabs);
        attributes.putClientProperty(Hints.TAB, "To attach numbers and other attributes to the values of each sheet.");
        ordering.putClientProperty(Hints.TAB, "To put a sheet's values in the order that comparing rules rank them by.");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton use = Hints.of(new JButton("Apply data"), "Use the attributes and orders as edited; they are checked before they are used."),
                revert = Hints.of(new JButton("Discard data draft"), "Throw away the changes to attributes and orders.");
        use.addActionListener(e -> { try { apply.accept(draft()); } catch (RuntimeException failure) { problem.accept("Parameters and orders were not applied: " + failure.getMessage()); } });
        revert.addActionListener(e -> discard.run()); actions.add(use); actions.add(revert); root.add(actions, BorderLayout.SOUTH);
        editing.addAll(List.of(use, revert)); this.revert = revert; editing.forEach(c -> c.setEnabled(false));
        table.getSelectionModel().addListSelectionListener(e -> gates()); orderList.addListSelectionListener(e -> gates());
        params.addTableModelListener(e -> { if (!loading) { paramsDirty = true; touch(); } });
        orderSheet.addActionListener(e -> { if (!loading) showOrder(); });
        mode.addActionListener(e -> { if (!loading) storeOrder(); });
    }

    JComponent component() { return root; }
    boolean dirty() { return dirty || table.isEditing(); }
    void show(SieveDocument document, Map<String, List<String>> values) {
        loading = true;
        try {
            original = document; this.values = values; params.setRowCount(0); orders.clear(); orders.putAll(map(document.root().entries().get("orders")));
            map(document.root().entries().get("params")).forEach((s, vs) -> map(vs).forEach((v, as) -> map(as).forEach((a, value) -> {
                String type = value instanceof RuleValue.Text ? "text" : value instanceof RuleValue.Int || value instanceof RuleValue.Real ? "number" : value instanceof RuleValue.Bool ? "boolean" : "JSON";
                params.addRow(new Object[]{s, v, a, type, value instanceof RuleValue.Text t ? t.value() : ConstraintBonds.pythonJson(value, false, false)});
            })));
            JComboBox<String> sheets = new JComboBox<>(values.keySet().toArray(String[]::new)); sheets.setEditable(true);
            table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(sheets));
            orderSheet.removeAllItems(); var names = new java.util.LinkedHashSet<>(values.keySet()); names.addAll(orders.keySet()); names.forEach(orderSheet::addItem);
        } finally { loading = false; dirty = paramsDirty = orderDirty = false; showOrder(); gates(); }
    }

    private void showOrder() {
        loading = true;
        try {
            currentSheet = (String) orderSheet.getSelectedItem(); ranks.clear(); orderValue.removeAllItems();
            if (currentSheet != null) values.getOrDefault(currentSheet, List.of()).forEach(orderValue::addItem);
            RuleValue value = orders.get(currentSheet);
            mode.setSelectedItem(value instanceof RuleValue.ListValue ? "list" : value instanceof RuleValue.Text t ? t.value() : "none");
            list(value).forEach(ranks::addElement);
        } finally { loading = false; }
    }
    private void storeOrder() {
        if (currentSheet == null) return;
        String kind = (String) mode.getSelectedItem();
        if ("none".equals(kind)) orders.remove(currentSheet);
        else orders.put(currentSheet, "list".equals(kind) ? new RuleValue.ListValue(java.util.Collections.list(ranks.elements())) : new RuleValue.Text(kind));
        orderDirty = true; touch();
    }
    private void move(int delta) {
        int i = orderList.getSelectedIndex(), j = i + delta;
        if (i >= 0 && j >= 0 && j < ranks.size()) { RuleValue v = ranks.remove(i); ranks.add(j, v); orderList.setSelectedIndex(j); storeOrder(); }
    }
    SieveDocument draft() {
        stopEditing(); SieveDocument result = original;
        if (paramsDirty) {
            Map<String, Map<String, Map<String, RuleValue>>> data = new LinkedHashMap<>();
            for (int r = 0; r < params.getRowCount(); r++) {
                String sheet = cell(r, 0), value = cell(r, 1), attr = cell(r, 2), type = cell(r, 3), content = cell(r, 4);
                if (sheet.isBlank() || attr.isBlank()) throw new IllegalArgumentException("Every attribute needs a sheet and an attribute name (row " + (r + 1) + ").");
                RuleValue v = type.equals("text") ? new RuleValue.Text(content) : ConstraintBonds.parse(content, "attribute row " + (r + 1));
                if (type.equals("number") && !(v instanceof RuleValue.Int || v instanceof RuleValue.Real) || type.equals("boolean") && !(v instanceof RuleValue.Bool)) throw new IllegalArgumentException("Value does not match the selected type at row " + (r + 1));
                var attrs = data.computeIfAbsent(sheet, s -> new LinkedHashMap<>()).computeIfAbsent(value, s -> new LinkedHashMap<>());
                if (attrs.putIfAbsent(attr, v) != null) throw new IllegalArgumentException("Repeated attribute: " + sheet + " / " + value + " / " + attr);
            }
            Map<String, RuleValue> all = new LinkedHashMap<>();
            data.forEach((s, vs) -> { Map<String, RuleValue> mapped = new LinkedHashMap<>(); vs.forEach((v, attrs) -> mapped.put(v, new RuleValue.MapValue(attrs))); all.put(s, new RuleValue.MapValue(mapped)); });
            result = result.field("params", new RuleValue.MapValue(all));
        }
        if (orderDirty) result = result.field("orders", new RuleValue.MapValue(orders));
        return result;
    }
    private String cell(int row, int col) { Object v = params.getValueAt(row, col); return v == null ? "" : v.toString(); }
    private void stopEditing() { if (table.isEditing() && !table.getCellEditor().stopCellEditing()) throw new IllegalStateException("Finish the current cell first."); }
    private void touch() { dirty = true; gates(); changed.run(); }
}
