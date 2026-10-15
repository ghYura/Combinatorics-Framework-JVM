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

import org.ghyura.cf.desktop.session.CandidatePreview;
import org.ghyura.cf.desktop.session.ConstraintEditing;
import org.ghyura.cf.desktop.session.PlanEstimates;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.model.rule.RuleValue;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.plan.SheetRole;
import org.ghyura.cf.spi.Cancellation;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JOptionPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The constraints tab (GEN:operator-authoring-ui; the legacy bond matrix and unified constraint editor): open the chosen
 * scenario's companion sidecar, draw forbidden or required value pairs between two sheets in a bond matrix under a gate
 * (any position, adjacent, within n), read every rule in plain language and the sidecar's JSON, check its impact on the
 * candidates before saving (the shared session's preview with the unsaved sidecar), and save it beside the scenario.
 * Contradicting bonds are shown and keep it from being saved. The graph draws every sheet's values with the bonds between
 * them as the legacy editor's canvas did (pairs as threads, n-ary tuples and many-to-many sets as hubs), and a selected rule
 * shows its parts as a tree (gate, when-predicate, the nesting of its condition or assert, mapping, sets, pairs). The rule
 * editor edits the selected rule (polarity, gate, a nested "only when" condition, a when rule's formula as text or blocks,
 * an assert rule's tree) or drafts a new formula or assert rule, and the parameters table holds the value parameters the
 * formulas read. Reading, checking and saving stay off the event thread.
 */
final class ConstraintsView {

    private final java.util.function.Supplier<Session> session;
    private final Supplier<Path> scenario;
    private final JLabel file = new JLabel("No scenario opened.");
    private final JComboBox<String> sheetA = Hints.of(new JComboBox<String>(), "The sheet whose values are the rows of the matrix.");
    private final JComboBox<String> sheetB = Hints.of(new JComboBox<String>(), "The sheet whose values are the columns of the matrix.");
    private final JComboBox<String> polarity = Hints.of(new JComboBox<>(new String[]{"forbid", "require"}), "What a click in the matrix makes of that pair of values: forbid (they must never appear together) or require (one only comes with the other).");
    private final JComboBox<String> gateKind = Hints.of(new JComboBox<>(new String[]{"any", "adjacent", "within"}), "When a pair rule applies: always (any), only when the two sheets are next to each other (adjacent), or only within a distance (within).");
    private final JSpinner within = Hints.of(new JSpinner(new SpinnerNumberModel(1, 0, 1000, 1)), "The largest distance between the two sheets when the rule applies only within a distance.");
    private final Matrix matrix = new Matrix();
    private final JTable matrixTable = Hints.of(new JTable(matrix), "Every value of one sheet against every value of another. Click a cell to forbid or require that pair of values.");
    private final ListTableModel<RuleValue.MapValue> rules = new ListTableModel<>(List.of("Rule", "Polarity", "In words"),
            List.<Function<RuleValue.MapValue, Object>>of(c -> text(c.entries().get("id")), c -> text(c.entries().get("polarity")),
                    ConstraintsView::inWords));
    private final JTextArea json = Hints.of(new JTextArea(), "All rules as JSON text, as they will be saved. Filled when you open the scenario's constraints, and updated as you change the rules.");
    private final JTable rulesTable = Hints.of(new JTable(rules), "All rules, each described in words. Select one to see or change it below.");
    private final ConstraintGraphView graph = Hints.of(new ConstraintGraphView(), "A picture of the rules: one column of values per sheet, with lines between values a rule forbids or requires together. Rest the pointer on a value to see its name.");
    private final JScrollPane graphPane = new JScrollPane(graph);
    private final javax.swing.JTree parts = Hints.of(new javax.swing.JTree(new DefaultMutableTreeNode("Select a rule to see its parts.")), "The selected rule taken apart: when it applies, what it checks, and which values it concerns.");
    private final JScrollPane partsPane = FieldNames.titled(parts, "Parts of the selected rule");
    private final JTabbedPane drawings = new JTabbedPane();
    private final JTabbedPane details = new JTabbedPane();
    private final RuleEditor editor = new RuleEditor(this::applied);
    private final ParamsModel paramsModel = new ParamsModel();
    private final JTable paramsTable = Hints.of(new JTable(paramsModel), "Numbers attached to values, which formula rules can use, for example price=10.");
    private Set<String> optional = Set.of();
    private final JTextArea output = Hints.of(new JTextArea(5, 40), "Messages from the last check or save of the rules. Filled after you press Check impact or Save.");
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private final Map<String, List<String>> values = new LinkedHashMap<>();
    private ConstraintEditing editing;
    private Path opened;
    private boolean busy;
    private String jsonDraft;

    ConstraintsView(Session session, Supplier<Path> scenario) {
        this(() -> session, scenario);
    }

    ConstraintsView(java.util.function.Supplier<Session> session, Supplier<Path> scenario) {
        this.session = session;
        this.scenario = scenario;
        JButton open = Hints.of(new JButton("Open the scenario's constraints"), "Load the rules that belong to the scenario of step 3.");
        open.addActionListener(e -> open());
        JButton impact = Hints.of(new JButton("Check impact"), "Count how many test cases the current rules remove, without saving them.");
        impact.addActionListener(e -> checkImpact());
        JButton save = Hints.of(new JButton("Save"), "Save the rules next to the scenario. Not possible while two rules contradict each other.");
        save.addActionListener(e -> save());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(open);
        top.add(impact);
        top.add(save);
        top.add(file);
        JPanel choose = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        choose.add(FieldNames.label("Rows:", sheetA));
        choose.add(sheetA);
        choose.add(FieldNames.label("columns:", sheetB));
        choose.add(sheetB);
        choose.add(FieldNames.label("a click sets:", polarity));
        choose.add(polarity);
        choose.add(FieldNames.label("when:", gateKind));
        choose.add(gateKind);
        choose.add(FieldNames.label("distance:", within));
        choose.add(within);
        for (JComboBox<String> box : List.of(sheetA, sheetB, gateKind)) {
            box.addActionListener(e -> showMatrix());
        }
        within.addChangeListener(e -> showMatrix());
        matrixTable.setCellSelectionEnabled(true);
        matrixTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = matrixTable.rowAtPoint(e.getPoint());
                int column = matrixTable.columnAtPoint(e.getPoint());
                if (row >= 0 && column > 0) {
                    toggle(row, column - 1);
                }
            }
        });
        json.setEditable(false);
        json.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JPanel matrixBox = new JPanel(new BorderLayout(4, 4));
        matrixBox.add(choose, BorderLayout.NORTH);
        matrixBox.add(TableTitles.titled(matrixTable, "Bond matrix: forbidden or required value pairs between two sheets"), BorderLayout.CENTER);
        rulesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = rulesTable.getSelectedRow();
                showParts(row);
                if (row >= 0 && editing != null) {
                    editor.edit(editing, row, values);
                }
            }
        });
        JButton newFormula = Hints.of(new JButton("New formula rule"), "Start a rule with a formula, for example: the price of A plus the price of B must stay below 100.");
        newFormula.addActionListener(e -> newFormulaRule());
        JButton newAssert = Hints.of(new JButton("New assert rule"), "Start a rule that says what must be true when a condition holds.");
        newAssert.addActionListener(e -> newAssertRule());
        JButton remove = Hints.of(new JButton("Remove rule"), "Delete the selected rule.");
        remove.addActionListener(e -> removeRule());
        JPanel ruleActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ruleActions.add(new JLabel("Rules:"));
        ruleActions.add(newFormula);
        ruleActions.add(newAssert);
        ruleActions.add(remove);
        JPanel rulesBox = new JPanel(new BorderLayout(4, 4));
        rulesBox.add(ruleActions, BorderLayout.NORTH);
        rulesBox.add(TableTitles.titled(rulesTable, "Rules of the constraints sidecar, in words"), BorderLayout.CENTER);
        JButton editJson = Hints.of(new JButton("Edit JSON…"), "Edit the selected rule or entry as JSON text; it is checked before it is used.");
        editJson.addActionListener(e -> editJson());
        JPanel jsonPanel = new JPanel(new BorderLayout(4, 4));
        jsonPanel.add(editJson, BorderLayout.NORTH);
        jsonPanel.add(FieldNames.titled(json, "All rules as JSON"), BorderLayout.CENTER);
        Steps.add(details, "4.3", "Parameters", TableTitles.titled(paramsTable, "Value parameters for formulas (the legacy params table)"),
                "Step 4.3: numbers attached to values, which formula rules can use.");
        Steps.add(details, "4.4", "Rule editor", editor.component(), "Step 4.4: change the selected rule, or write a new one.");
        Steps.add(details, "4.5", "Rule parts", partsPane, "Step 4.5: the selected rule taken apart.");
        Steps.add(details, "4.6", "JSON", jsonPanel, "Step 4.6: all rules as JSON text, for copying or careful edits.");
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rulesBox, details);
        right.setResizeWeight(0.5);
        Steps.add(drawings, "4.1", "Bond matrix", matrixBox, "Step 4.1: draw pair rules with clicks: choose two sheets, then click the pairs "
                + "of values to forbid or require.");
        Steps.add(drawings, "4.2", "Graph", graphPane, "Step 4.2: a picture of all rules and the values they connect.");
        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, drawings, right);
        center.setResizeWeight(0.55);
        // the rule editor takes room: the rule list gets shorter and the drawings narrower while it is in front
        details.addChangeListener(e -> {
            if (details.getSelectedComponent() == editor.component() && right.getHeight() > 0) {
                right.setDividerLocation(Math.min(right.getDividerLocation(), (int) (right.getHeight() * 0.28)));
                center.setDividerLocation(Math.min(center.getDividerLocation(), (int) (center.getWidth() * 0.42)));
            }
        });
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, FieldNames.titled(output, "Messages"));
        vertical.setResizeWeight(0.8);
        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(top, BorderLayout.NORTH);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(north, BorderLayout.NORTH);
        panel.add(vertical, BorderLayout.CENTER);
    }

    JComponent component() {
        return panel;
    }

    /** Reads the chosen scenario's sheets and companion sidecar off the event thread. */
    CompletableFuture<Void> open() {
        Path chosen = scenario.get();
        if (chosen == null) {
            output.setText("Choose a scenario on the Scenario tab first.\n");
            return CompletableFuture.completedFuture(null);
        }
        if (busy) {
            return CompletableFuture.completedFuture(null);
        }
        busy = true;
        output.setText("Reading " + chosen + " and its constraints ...\n");
        return Background.run("open-constraints", () -> session.get().openConstraints(chosen), o -> {
            busy = false;
            values.clear();
            for (SheetSpec sheet : o.summary().spec().sheets()) {
                values.put(sheet.name(), sheet.cells().stream().map(ValueCell::text).toList());
            }
            optional = o.summary().plan().sheets().stream().filter(s -> s.role() == SheetRole.OPTIONAL).map(SheetPlan::name)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            editing = o.editing();
            opened = chosen;
            file.setText(ConstraintSidecarReader.companionOf(chosen).toString());
            fill(sheetA, 0);
            fill(sheetB, 1);
            paramsModel.show();
            refresh();
            output.setText("Opened " + editing.bonds().size() + " bond(s) for " + chosen + ".\n");
        }, failure -> {
            busy = false;
            output.setText("The constraints cannot be opened:\n" + Background.describe(failure));
        }).thenApply(o -> null);
    }

    private void fill(JComboBox<String> box, int preferred) {
        box.removeAllItems();
        for (String name : values.keySet()) {
            box.addItem(name);
        }
        if (box.getItemCount() > preferred) {
            box.setSelectedIndex(preferred);
        }
    }

    /** Sets or clears the pair of a row value and a column value with the chosen polarity and gate. */
    void toggle(int row, int column) {
        if (editing == null || sheetA.getSelectedItem() == null || sheetB.getSelectedItem() == null) {
            return;
        }
        String a = (String) sheetA.getSelectedItem();
        String b = (String) sheetB.getSelectedItem();
        if (a.equals(b)) {
            output.setText("A bond joins two different sheets: choose another column sheet.\n");
            return;
        }
        String va = values.get(a).get(row);
        String vb = values.get(b).get(column);
        String chosen = (String) polarity.getSelectedItem();
        String now = editing.pair(a, va, b, vb, gate());
        editing.setPair(a, va, b, vb, gate(), chosen.equals(now) ? null : chosen);
        refresh();
    }

    private RuleValue gate() {
        return ConstraintEditing.gate((String) gateKind.getSelectedItem(), (Integer) within.getValue());
    }

    private void refresh() {
        jsonDraft = null;
        paramsModel.fireTableDataChanged();
        showMatrix();
        List<RuleValue.MapValue> constraints = new ArrayList<>();
        RuleValue list = editing.sidecar().entries().get("constraints");
        for (RuleValue c : ((RuleValue.ListValue) list).items()) {
            constraints.add((RuleValue.MapValue) c);
        }
        rules.show(constraints);
        showParts(-1);
        editor.clear();
        graph.show(values, optional, editing.bonds());
        json.setText(editing.json());
        json.setCaretPosition(0);
        int conflicts = editing.contradictions().size();
        if (conflicts > 0) {
            output.setText(conflicts + " bond(s) forbid what others require (the same sheets, gate and values); remove one of each before "
                    + "saving: " + editing.contradictions() + "\n");
        }
    }

    /** Mapping entries and other sidecar data use the existing JSON form, without a second rule-editing model. */
    private void editJson() {
        if (editing == null || busy) return;
        JTextArea draft = new JTextArea(jsonDraft == null ? editing.json() : jsonDraft, 20, 70);
        draft.setFont(json.getFont());
        if (JOptionPane.showConfirmDialog(component(), new JScrollPane(draft), "Edit constraints JSON (Apply does not save)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) applyJson(draft.getText());
    }

    CompletableFuture<Void> applyJson(String text) {
        if (editing == null || busy) return CompletableFuture.completedFuture(null);
        busy = true;
        jsonDraft = text;
        return Background.run("apply-constraint-json", () -> session.get().constraintDraft(text), changed -> {
            busy = false;
            editing = changed;
            refresh();
            output.setText("Applied JSON. Check impact previews it; Save writes the companion file.\n");
        }, failure -> {
            busy = false;
            output.setText("JSON was not applied; reopen Edit JSON to correct the draft:\n" + Background.describe(failure));
        }).thenApply(ignored -> null);
    }

    private void showMatrix() {
        within.setEnabled("within".equals(gateKind.getSelectedItem()));
        String a = (String) sheetA.getSelectedItem();
        String b = (String) sheetB.getSelectedItem();
        matrix.show(a, values.getOrDefault(a, List.of()), b, values.getOrDefault(b, List.of()));
    }

    /** The candidates the unsaved sidecar leaves, and what each rule removes, through the shared session's preview. */
    CompletableFuture<CandidatePreview> checkImpact() {
        if (editing == null || busy) {
            return CompletableFuture.completedFuture(null);
        }
        busy = true;
        Path target = opened;
        String text = editing.json();
        output.setText("Checking the impact of the unsaved constraints ...\n");
        return Background.run("constraint-impact", () -> session.get().preview(target, text, new CandidatePreview.Request(BigInteger.valueOf(1_000_000),
                BigInteger.ZERO, 1, true, true), Cancellation.NONE), preview -> {
            busy = false;
            StringBuilder out = new StringBuilder();
            for (PlanEstimates.Stage s : preview.estimates().candidates()) {
                if (List.of("generated", "executable").contains(s.name())) {
                    out.append(s.name()).append(": ").append(s.count().describe()).append('\n');
                }
            }
            for (CandidatePreview.Rule r : preview.rules()) {
                out.append(r.name()).append(r.early() ? " (early)" : " (late)").append(" removes ")
                        .append(r.removed() == null ? "more than the scan counts" : r.removed()).append('\n');
            }
            output.setText(out.toString());
        }, failure -> {
            busy = false;
            output.setText("The constraints cannot be applied:\n" + Background.describe(failure));
        });
    }

    /** Drafts a new formula rule in the rule editor, with the polarity and gate the bond matrix sets (the legacy mode and gate). */
    void newFormulaRule() {
        if (editing == null) {
            return;
        }
        rulesTable.clearSelection();
        editor.newWhen(editing, values, (String) polarity.getSelectedItem(), gate());
        details.setSelectedComponent(editor.component());
    }

    /** Drafts a new assert rule in the rule editor, with the polarity the bond matrix sets. */
    void newAssertRule() {
        if (editing == null) {
            return;
        }
        rulesTable.clearSelection();
        editor.newAssert(editing, values, (String) polarity.getSelectedItem());
        details.setSelectedComponent(editor.component());
    }

    /** Removes the selected rule with every bond it comes from. */
    void removeRule() {
        int row = rulesTable.getSelectedRow();
        if (editing == null || row < 0) {
            return;
        }
        String id = editing.rules().get(row).id();
        editing.removeRule(row);
        refresh();
        output.setText("Removed " + id + "; Save writes the sidecar without it.\n" + output.getText());
    }

    /** After the rule editor applied a draft: the sidecar shown anew with the rule selected again (rules regroup). */
    private void applied(int index) {
        refresh();
        String conflicts = editing.contradictions().isEmpty() ? "" : output.getText();
        selectRule(index);
        details.setSelectedComponent(editor.component());
        output.setText("Applied to " + editing.rules().get(index).id() + "; Check impact counts it, Save writes it.\n" + conflicts);
    }

    /** Writes the companion sidecar off the event thread; refused while bonds contradict. */
    CompletableFuture<Path> save() {
        if (editing == null || busy) {
            return CompletableFuture.completedFuture(null);
        }
        busy = true;
        ConstraintEditing snapshot = ConstraintEditing.of(editing.json(), "unsaved");
        Path target = opened;
        return Background.run("save-constraints", () -> snapshot.save(target), written -> {
            busy = false;
            output.setText("Saved " + written + ".\n");
        }, failure -> {
            busy = false;
            output.setText("The constraints were not saved:\n" + Background.describe(failure));
        });
    }

    // ---- for tests, on the event thread ------------------------------------------------------------------------------

    void choose(String rows, String columns, String polarityName, String gate, int n) {
        sheetA.setSelectedItem(rows);
        sheetB.setSelectedItem(columns);
        polarity.setSelectedItem(polarityName);
        gateKind.setSelectedItem(gate);
        within.setValue(n);
    }

    Matrix matrix() {
        return matrix;
    }

    ConstraintGraphView graph() {
        return graph;
    }

    /** Selects a rule of the list, which shows its parts; -1 clears the selection. */
    void selectRule(int row) {
        if (row < 0) {
            rulesTable.clearSelection();
        } else {
            rulesTable.setRowSelectionInterval(row, row);
        }
    }

    /** The parts of the selected rule as indented lines. */
    String parts() {
        return ConditionTree.dump((DefaultMutableTreeNode) parts.getModel().getRoot());
    }

    /** Brings the graph and the selected rule's parts to the front. */
    void showGraph() {
        drawings.setSelectedComponent(graphPane);
        details.setSelectedComponent(partsPane);
    }

    ListTableModel<RuleValue.MapValue> rules() {
        return rules;
    }

    RuleEditor editor() {
        return editor;
    }

    ParamsModel params() {
        return paramsModel;
    }

    /** Brings the rule editor to the front. */
    void showEditor() {
        details.setSelectedComponent(editor.component());
    }

    String json() {
        return json.getText();
    }

    String output() {
        return output.getText();
    }

    private void showParts(int row) {
        DefaultMutableTreeNode root = row < 0 || row >= rules.getRowCount() ? new DefaultMutableTreeNode("Select a rule to see its parts.")
                : ConditionTree.of(rules.row(row));
        parts.setModel(new javax.swing.tree.DefaultTreeModel(root));
        for (int i = 0; i < parts.getRowCount(); i++) {
            parts.expandRow(i);
        }
    }

    /** The rule in words without the legacy marker emoji, which desktop fonts may not draw (the polarity has its column). */
    static String inWords(RuleValue.MapValue constraint) {
        String words = ConstraintBonds.describe(constraint);
        return words.startsWith("\u274c ") || words.startsWith("\u2705 ") ? words.substring(2) : words;
    }

    private static String text(RuleValue v) {
        return v instanceof RuleValue.Text t ? t.value() : v == null ? "" : String.valueOf(v);
    }

    /**
     * The legacy params table: every sheet's value with the parameters its formulas read as sheet.param, written k=x and
     * separated by commas; an edit replaces the value's parameters.
     */
    final class ParamsModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private transient List<Map.Entry<String, String>> rows = List.of();

        void show() {
            List<Map.Entry<String, String>> all = new ArrayList<>();
            values.forEach((sheet, vs) -> vs.forEach(v -> all.add(Map.entry(sheet, v))));
            rows = all;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Sheet: value" : "Parameters for formulas (charge=2, n=1)";
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1 && editing != null;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Map.Entry<String, String> r = rows.get(row);
            return column == 0 ? r.getKey() + ":" + r.getValue() : editing == null ? "" : editing.paramsText(r.getKey(), r.getValue());
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 1 || editing == null) {
                return;
            }
            Map.Entry<String, String> r = rows.get(row);
            editing.setParams(r.getKey(), r.getValue(), String.valueOf(value));
            json.setText(editing.json());
            json.setCaretPosition(0);
            fireTableRowsUpdated(row, row);
        }
    }

    /** Row values against column values; a cell shows the pair's bond under the chosen gate: x forbid, v require. */
    final class Matrix extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private transient String rowsSheet = "";
        private transient List<String> rowValues = List.of();
        private transient String columnsSheet = "";
        private transient List<String> columnValues = List.of();

        void show(String rowsName, List<String> rowsValues, String columnsName, List<String> columnsValues) {
            rowsSheet = rowsName == null ? "" : rowsName;
            rowValues = List.copyOf(rowsValues);
            columnsSheet = columnsName == null ? "" : columnsName;
            columnValues = List.copyOf(columnsValues);
            fireTableStructureChanged();
        }

        @Override
        public int getRowCount() {
            return rowValues.size();
        }

        @Override
        public int getColumnCount() {
            return columnValues.size() + 1;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? rowsSheet + " \\ " + columnsSheet : columnValues.get(column - 1);
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (column == 0) {
                return rowValues.get(row);
            }
            if (editing == null || rowsSheet.equals(columnsSheet)) {
                return "";
            }
            String p = editing.pair(rowsSheet, rowValues.get(row), columnsSheet, columnValues.get(column - 1), gate());
            return p == null ? "" : p.equals("forbid") ? "x forbid" : "v require";
        }
    }
}
