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

import org.ghyura.cf.desktop.session.CellTemplates;
import org.ghyura.cf.desktop.session.ControlSheets;
import org.ghyura.cf.desktop.session.FileSeeker;
import org.ghyura.cf.desktop.session.OperandDependencies;
import org.ghyura.cf.desktop.session.ScenarioDocument;
import org.ghyura.cf.desktop.session.ScenarioValidation;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.input.SeqGraph;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The authoring tab: create or open a scenario in any input format, edit its sheets (add, rename, remove) and cells (the
 * grid grows as you type; a cleared cell is removed; rows insert and delete), validate the unsaved edits with the
 * validation pipeline, and save in the format of the file's extension. A saved scenario becomes the scenario tab's choice.
 * The legacy palette puts every cell form the Core reads into the selected cell; every validation also shows the operand
 * dependencies: what each joiner reads, and which operands an earlier joiner leaves empty, and draws the FW_Seq dependency
 * graph (the legacy fwseq_graph: sheets, braces and what each is computed from, with its issues; copied or saved as DOT or
 * JSON) (GEN:operator-authoring-ui).
 */
final class AuthoringView {

    private final java.util.function.Supplier<Session> session;
    private final Navigator navigator;
    private final Consumer<Path> saved;
    private final DefaultListModel<String> sheetNames = new DefaultListModel<>();
    private final JList<String> sheets = Hints.of(new JList<>(sheetNames), "The scenario's sheets. Each sheet is a list of values; the sheet FW_Seq says how they are combined. Pick a sheet to edit its values.");
    private final SheetTableModel grid = new SheetTableModel();
    private final JTable table = Hints.of(new JTable(grid), "The values of the selected sheet, one per row. Click a cell and type, or fill it from the templates on the right.");
    private final JScrollPane gridPane = TableTitles.titled(table, "Cells of the selected sheet");
    private final JLabel fileLabel = new JLabel("untitled");
    private final JTextArea output = Hints.of(new JTextArea(6, 40), "What the last check of the scenario found: problems to fix, and a summary. Filled after you press Validate.");
    private final JList<CellTemplates.CellTemplate> palette = Hints.of(new JList<>(CellTemplates.ALL.toArray(CellTemplates.CellTemplate[]::new)), "Ready-made cell contents that say how a sheet's values are picked, ordered or joined. Rest the pointer on one to read what it does; double-click it to put it into the selected cell.");
    private final ListTableModel<OperandDependencies.Join> dependencies = new ListTableModel<>(List.of("Joiner", "Multiplicity", "First",
            "Second", "Helper sheets", "Directive"), List.<java.util.function.Function<OperandDependencies.Join, Object>>of(
            OperandDependencies.Join::target, OperandDependencies.Join::multiplicity, j -> describe(j.first()), j -> describe(j.second()),
            j -> String.join(", ", j.helpers()), OperandDependencies.Join::directive));
    private final javax.swing.JTabbedPane results = new javax.swing.JTabbedPane();
    private final GraphView graphView = Hints.of(new GraphView(), "A diagram of the scenario: each sheet as a box, with arrows from the sheets its values are built from. Problems are marked in red.");
    private final JLabel graphInfo = new JLabel("Validate the scenario to see its FW_Seq dependency graph.");
    private final JTextArea graphIssues = Hints.of(new JTextArea(3, 40), "Problems found in the diagram, such as a sheet that is used before it is built. Filled after you press Validate; it says No issues. when there are none.");
    private final JScrollPane validationPane = FieldNames.titled(output, "Check results");
    private JComponent graphTab;
    private JComponent controlsTab;
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private ScenarioDocument document = ScenarioDocument.create();
    private Path file;
    private long openRequest;
    private CompletableFuture<Path> saving = CompletableFuture.completedFuture(null);

    AuthoringView(Session session, Consumer<Path> saved, Navigator navigator) {
        this(() -> session, saved, navigator);
    }

    AuthoringView(java.util.function.Supplier<Session> session, Consumer<Path> saved, Navigator navigator) {
        this.session = session;
        this.saved = saved;
        this.navigator = navigator;
        JButton create = Hints.of(new JButton("New"), "Start a new, empty scenario.");
        create.addActionListener(e -> newDocument());
        JButton open = Hints.of(new JButton("Open..."), "Open a scenario file to change it.");
        open.addActionListener(e -> {
            Path chosen = navigator.choose(panel, Navigator.Purpose.DOCUMENT_OPEN, file);
            if (chosen != null) {
                open(chosen);
            }
        });
        navigator.bind(open, () -> Navigator.Purpose.DOCUMENT_OPEN, () -> file);
        JButton search = new AutoSearch(navigator).button(() -> FileSeeker.Kind.SCENARIO, () -> file, this::open,
                "Search the folders where scenario files usually are, then open one here to change it.");
        JButton save = Hints.of(new JButton("Save"), "Save the scenario to its file. It also becomes the scenario of step 3.");
        save.addActionListener(e -> {
            if (file != null) {
                save(file);
            } else {
                saveAs();
            }
        });
        JButton saveAs = Hints.of(new JButton("Save as..."), "Save the scenario to a new file. It also becomes the scenario of step 3.");
        saveAs.addActionListener(e -> saveAs());
        navigator.bind(saveAs, () -> Navigator.Purpose.DOCUMENT_SAVE, () -> file);
        JButton validate = Hints.of(new JButton("Validate"), "Check the scenario for mistakes without saving it. The result appears in tab 2.2 Validation.");
        validate.addActionListener(e -> validate());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (JButton b : new JButton[]{create, open, search, save, saveAs, validate}) {
            top.add(b);
        }
        top.add(fileLabel);

        sheets.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sheets.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && sheets.getSelectedIndex() >= 0) grid.show(document, sheets.getSelectedIndex());
        });
        JButton add = Hints.of(new JButton("Add sheet"), "Add a new sheet (a list of values) with a name you choose.");
        add.addActionListener(e -> ask("Name of the new sheet:", "").ifPresent(this::addSheet));
        JButton rename = Hints.of(new JButton("Rename"), "Give the selected sheet a new name.");
        rename.addActionListener(e -> {
            int i = sheets.getSelectedIndex();
            if (i >= 0) ask("New name:", sheetNames.get(i)).ifPresent(name -> renameSheet(i, name));
        });
        JButton remove = Hints.of(new JButton("Remove"), "Delete the selected sheet with all its values.");
        remove.addActionListener(e -> removeSheet(sheets.getSelectedIndex()));
        JPanel sheetButtons = new JPanel(new GridLayout(0, 1, 4, 4));
        sheetButtons.add(add);
        sheetButtons.add(rename);
        sheetButtons.add(remove);
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(FieldNames.label("Sheets", sheets), BorderLayout.NORTH);
        left.add(new JScrollPane(sheets), BorderLayout.CENTER);
        left.add(sheetButtons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(220, 400));

        JButton insertRow = Hints.of(new JButton("Insert row"), "Add an empty row above the selected row.");
        insertRow.addActionListener(e -> insertRow(Math.max(0, table.getSelectedRow())));
        JButton removeRow = Hints.of(new JButton("Remove row"), "Delete the selected row.");
        removeRow.addActionListener(e -> {
            if (table.getSelectedRow() >= 0) removeRow(table.getSelectedRow());
        });
        JPanel rowButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rowButtons.add(insertRow);
        rowButtons.add(removeRow);
        table.setCellSelectionEnabled(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(rowButtons, BorderLayout.NORTH);
        center.add(gridPane, BorderLayout.CENTER);
        grid.addTableModelListener(e -> TableTitles.name(gridPane, table, gridTitle()));

        // the legacy palette: every cell form the Core reads, put into the selected cell
        palette.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        palette.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                CellTemplates.CellTemplate t = (CellTemplates.CellTemplate) value;
                // the legacy labels already name their family; the colour shows the category
                java.awt.Component c = super.getListCellRendererComponent(list, t.label(), index, selected, focus);
                if (!selected) {
                    c.setForeground(java.awt.Color.decode(t.color()));
                }
                setToolTipText("<html><b>" + escapeHtml(t.label()) + "</b> (" + escapeHtml(t.category()) + "): " + escapeHtml(t.description())
                        + "<br>Puts into the cell: <code>" + escapeHtml(t.directive()).replace("\n", "<br>") + "</code></html>");
                return c;
            }
        });
        palette.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && palette.getSelectedValue() != null) insert(palette.getSelectedValue().id());
            }
        });
        JButton insert = Hints.of(new JButton("Insert into the selected cell"), "Put the template chosen in the list into the selected cell of the sheet.");
        insert.addActionListener(e -> {
            if (palette.getSelectedValue() != null) insert(palette.getSelectedValue().id());
        });
        JPanel paletteBox = new JPanel(new BorderLayout(4, 4));
        paletteBox.add(FieldNames.label("Cell templates", palette), BorderLayout.NORTH);
        paletteBox.add(new JScrollPane(palette), BorderLayout.CENTER);
        paletteBox.add(insert, BorderLayout.SOUTH);
        paletteBox.setPreferredSize(new Dimension(260, 400));
        JSplitPane withPalette = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, paletteBox);
        withPalette.setResizeWeight(1.0);

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        controlsTab = controlsPanel();
        graphTab = graphPanel();
        Steps.add(results, "2.1", "Control sheets", controlsTab, "Step 2.1: the three special sheets: code that runs once before the test "
                + "cases, the arguments every test case gets, and your own result codes.");
        Steps.add(results, "2.2", "Validation", validationPane, "Step 2.2: what the last check of the scenario found.");
        Steps.add(results, "2.3", "Operand dependencies", TableTitles.titled(Hints.of(new JTable(dependencies), "Each place where two sheets are joined: which sheets it reads, how their rows are paired, and whether those sheets still hold their values when they are needed."),
                "Operand dependencies: each joiner's operands and their lifetimes"), "Step 2.3: how the sheets feed each other where they are joined.");
        Steps.add(results, "2.4", "Dependency graph", graphTab, "Step 2.4: a diagram of how the sheets depend on each other.");
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, withPalette);
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, split, results);
        vertical.setResizeWeight(0.75);
        // a drawing needs room: showing the graph raises the divider when it leaves the graph less than a few rows of nodes
        results.addChangeListener(e -> {
            if (results.getSelectedComponent() == graphTab && vertical.getHeight() > 0
                    && vertical.getHeight() - vertical.getDividerLocation() < 380) {
                vertical.setDividerLocation(Math.max(160, vertical.getHeight() - 420));
            }
        });
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(vertical, BorderLayout.CENTER);
        showDocument();
    }

    JComponent component() {
        return panel;
    }

    ScenarioDocument document() {
        return document;
    }

    String output() {
        return output.getText();
    }

    private java.util.Optional<String> ask(String question, String initial) {
        return java.util.Optional.ofNullable(JOptionPane.showInputDialog(panel, question, initial)).map(String::trim).filter(s -> !s.isEmpty());
    }

    void newDocument() {
        openRequest++;
        document = ScenarioDocument.create();
        file = null;
        fileLabel.setText("untitled");
        showDocument();
        output.setText("A new scenario: add data sheets and their FW_Seq rows.\n");
    }

    /** Reads a scenario off the event thread and shows it. */
    CompletableFuture<ScenarioDocument> open(Path path) {
        long request = ++openRequest;
        output.setText("Reading " + path + " ...\n");
        return Background.run("open", () -> ScenarioDocument.read(path), read -> {
            if (request != openRequest) return;
            document = read;
            file = path;
            fileLabel.setText(path.toString());
            showDocument();
            output.setText("Read " + path + ": " + read.sheetCount() + " sheets.\n");
        }, failure -> output.setText("The scenario cannot be read:\n" + Background.describe(failure)));
    }

    /** Writes the document in the extension's format off the event thread; the scenario tab then uses it. */
    CompletableFuture<Path> save(Path path) {
        stopEditing();
        ScenarioDocument original = document;
        ScenarioDocument shown = document.copy();
        CompletableFuture<Path> previous = saving;
        saving = Background.run("save", () -> {
            previous.handle((value, failure) -> null).join();
            shown.save(path);
            return path;
        }, written -> {
            if (document != original) return;
            file = written;
            fileLabel.setText(written.toString());
            output.setText("Saved " + written + ".\n");
            saved.accept(written);
        }, failure -> output.setText("The scenario was not saved:\n" + Background.describe(failure)));
        return saving;
    }

    private void saveAs() {
        Path chosen = navigator.choose(panel, Navigator.Purpose.DOCUMENT_SAVE, file);
        if (chosen != null) {
            save(chosen);
        }
    }

    /** Validates the unsaved document as if saved where it is (or as untitled.json in the working directory). */
    CompletableFuture<ScenarioValidation.Summary> validate() {
        stopEditing();
        ScenarioDocument shown = document.copy();
        Path as = file != null ? file : Path.of("untitled.json");
        output.setText("Validating ...\n");
        record Validated(ScenarioValidation.Summary summary, OperandDependencies dependencies) {
        }
        return Background.run("validate-edits", () -> {
            ScenarioValidation.Summary summary = session.get().validate(shown, as);
            return new Validated(summary, OperandDependencies.of(summary.plan()));
        }, validated -> {
            StringBuilder text = new StringBuilder(validated.summary().toText());
            for (Diagnostic d : validated.summary().findings()) text.append(d).append('\n');
            for (Diagnostic d : validated.dependencies().findings()) text.append(d).append('\n');
            output.setText(text.toString());
            output.setCaretPosition(0);
            showValidation();
            dependencies.show(validated.dependencies().joins());
            showGraph(validated.summary());
        }, failure -> {
            dependencies.show(List.of());
            showGraph(null);
            output.setText("The scenario is not valid:\n" + Background.describe(failure));
            output.setCaretPosition(0);
            showValidation();
        }).thenApply(Validated::summary);
    }

    // ---- the FW_Seq dependency graph ----------------------------------------------------------------------------------

    private JComponent graphPanel() {
        graphIssues.setEditable(false);
        graphIssues.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JButton copy = Hints.of(new JButton("Copy DOT"), "Copy the diagram as text in the Graphviz DOT format, to paste into a diagram tool.");
        copy.addActionListener(e -> {
            if (graphView.graph() != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(graphView.graph().dot()), null);
            }
        });
        JButton save = Hints.of(new JButton("Save DOT or JSON..."), "Save the diagram to a file, as Graphviz DOT or as JSON.");
        save.addActionListener(e -> {
            if (graphView.graph() != null) {
                Path chosen = navigator.choose(panel, Navigator.Purpose.GRAPH_SAVE, file);
                if (chosen != null) {
                    saveGraph(chosen);
                }
            }
        });
        navigator.bind(save, () -> Navigator.Purpose.GRAPH_SAVE, () -> file);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(copy);
        buttons.add(save);
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(graphInfo, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.EAST);
        JPanel box = new JPanel(new BorderLayout(4, 4));
        box.add(top, BorderLayout.NORTH);
        box.add(new JScrollPane(graphView), BorderLayout.CENTER);
        box.add(FieldNames.titled(graphIssues, "Problems in the diagram"), BorderLayout.SOUTH);
        return box;
    }

    private void showGraph(ScenarioValidation.Summary summary) {
        graphView.show(summary == null ? null : summary.graph());
        graphInfo.setText(summary == null ? "The scenario is not valid: see the validation." : summary.graphLine());
        graphInfo.setToolTipText(graphInfo.getText());
        StringBuilder issues = new StringBuilder();
        if (summary != null) {
            for (SeqGraph.Issue i : summary.graph().validate()) {
                issues.append(i.level().equals(SeqGraph.ERROR) ? "✗" : "⚠").append(" [").append(i.code()).append("] ")
                        .append(i.message()).append('\n');
            }
        }
        graphIssues.setText(issues.isEmpty() ? (summary == null ? "" : "No issues.") : issues.toString());
        graphIssues.setCaretPosition(0);
    }

    /** Writes the shown graph: Graphviz DOT when the name ends in .dot, its JSON otherwise. */
    void saveGraph(Path path) {
        SeqGraph graph = graphView.graph();
        String text = path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dot") ? graph.dot() : graph.json();
        try {
            AtomicFiles.write(path, text.getBytes(StandardCharsets.UTF_8));
            graphInfo.setText("Saved " + path + " (" + graph.hash() + ").");
        } catch (IOException e) {
            graphInfo.setText("The graph was not saved: " + e.getMessage());
        }
    }

    GraphView graphView() {
        return graphView;
    }

    /** After a check started from the control sheets, its findings come to the front; the other result tabs stay in front. */
    private void showValidation() {
        if (results.getSelectedComponent() == controlsTab) {
            results.setSelectedComponent(validationPane);
        }
    }

    /** Brings the dependency graph to the front of the results. */
    void showGraph() {
        results.setSelectedComponent(graphTab);
    }

    String graphInfo() {
        return graphInfo.getText();
    }

    String graphIssues() {
        return graphIssues.getText();
    }

    /** Puts a palette template into the selected cell of the shown sheet (a FW_ReplaceRE line joins its FW_Group cell). */
    void insert(String templateId) {
        CellTemplates.CellTemplate t = CellTemplates.byId(templateId).orElseThrow();
        int row = table.getSelectedRow();
        int column = table.getSelectedColumn();
        if (grid.sheet() < 0 || row < 0 || column < 0) {
            output.setText("Select a cell of the sheet first, then insert " + t.label() + ".\n");
            return;
        }
        stopEditing();
        grid.setValueAt(CellTemplates.apply((String) grid.getValueAt(row, column), t), row, column);
    }

    // ---- the runtime control sheets (FW_RunMeFirstOnce, FW_Arguments, FW_CUSTOM_VAR) ---------------------------------

    private final JTextArea runOnce = Hints.of(new JTextArea(6, 40), "FW_RunMeFirstOnce: Java code that runs once before all test cases, for example to prepare data. Filled from the scenario when you press Load from the document, or by you typing the code.");
    private final JTextArea arguments = Hints.of(new JTextArea(4, 20), "FW_Arguments: the arguments every test case gets, one per line. Filled from the scenario when you press Load from the document, or by you typing them.");
    private final javax.swing.table.DefaultTableModel verdicts = new javax.swing.table.DefaultTableModel(new Object[]{"Verdict code", "Name"}, 0);
    private final JLabel controlIssues = new JLabel(" ");

    private JComponent controlsPanel() {
        runOnce.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        arguments.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JButton load = Hints.of(new JButton("Load from the document"), "Fill these three boxes from the scenario's control sheets.");
        load.addActionListener(e -> loadControls());
        JButton apply = Hints.of(new JButton("Apply to the document"), "Write these three boxes back into the scenario's control sheets.");
        apply.addActionListener(e -> applyControls());
        JButton add = Hints.of(new JButton("Add verdict"), "Add a row for one of your own result codes and its name.");
        add.addActionListener(e -> verdicts.addRow(new Object[]{"", ""}));
        JTable verdictTable = Hints.of(new JTable(verdicts), "FW_CUSTOM_VAR: your own result codes, each with a name, used instead of a plain pass or fail.");
        JButton remove = Hints.of(new JButton("Remove verdict"), "Delete the selected result code.");
        remove.addActionListener(e -> {
            if (verdictTable.getSelectedRow() >= 0) verdicts.removeRow(verdictTable.getSelectedRow());
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (JButton b : new JButton[]{load, apply, add, remove}) {
            buttons.add(b);
        }
        buttons.add(controlIssues);
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(FieldNames.label("FW_RunMeFirstOnce: code run once before the test cases", runOnce), BorderLayout.NORTH);
        left.add(new JScrollPane(runOnce), BorderLayout.CENTER);
        JPanel middle = new JPanel(new BorderLayout(4, 4));
        middle.add(FieldNames.label("FW_Arguments: arguments, one per line", arguments), BorderLayout.NORTH);
        middle.add(new JScrollPane(arguments), BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(TableTitles.titled(verdictTable, "FW_CUSTOM_VAR: verdict codes and their names"), BorderLayout.CENTER);
        JPanel editors = new JPanel(new java.awt.GridLayout(1, 3, 8, 0));
        editors.add(left);
        editors.add(middle);
        editors.add(right);
        JPanel box = new JPanel(new BorderLayout(4, 4));
        box.add(buttons, BorderLayout.NORTH);
        box.add(editors, BorderLayout.CENTER);
        return box;
    }

    /** Fills the control editors from the document. */
    void loadControls() {
        stopEditing();
        setControls(ControlSheets.of(document));
    }

    void setControls(ControlSheets c) {
        runOnce.setText(c.runOnce());
        arguments.setText(String.join("\n", c.arguments()));
        verdicts.setRowCount(0);
        for (ControlSheets.Verdict v : c.verdicts()) {
            verdicts.addRow(new Object[]{v.code(), v.name()});
        }
        controlIssues.setText(" ");
    }

    /** The control editors' values. */
    ControlSheets controls() {
        List<String> args = new ArrayList<>(List.of(arguments.getText().split("\n", -1)));
        if (!args.isEmpty() && args.getLast().isEmpty()) {
            args.removeLast();
        }
        List<ControlSheets.Verdict> rows = new ArrayList<>();
        for (int r = 0; r < verdicts.getRowCount(); r++) {
            String code = String.valueOf(verdicts.getValueAt(r, 0)).strip();
            String name = String.valueOf(verdicts.getValueAt(r, 1));
            if (!code.isEmpty() || !name.isEmpty()) {
                rows.add(new ControlSheets.Verdict(code, name));
            }
        }
        return new ControlSheets(runOnce.getText(), args, rows);
    }

    /** Writes the control editors into the document's control sheets and shows face1's issues; returns them. */
    List<ControlSheets.Issue> applyControls() {
        ControlSheets c = controls();
        c.applyTo(document);
        int shown = sheets.getSelectedIndex();
        showDocument();
        if (shown >= 0 && shown < document.sheetCount()) {
            sheets.setSelectedIndex(shown);
        }
        List<ControlSheets.Issue> issues = c.issues();
        controlIssues.setText(issues.isEmpty() ? "Applied; no issue." : issues.size() + " issue(s): " + issues.getFirst().message());
        StringBuilder text = new StringBuilder("Applied the control sheets.\n");
        for (ControlSheets.Issue i : issues) {
            text.append(i.severity()).append(' ').append(i.message()).append('\n');
        }
        output.setText(text.toString());
        return issues;
    }

    /** Selects a cell of the shown sheet, as a click would. */
    void selectCell(int row, int column) {
        table.changeSelection(row, column, false, false);
    }

    ListTableModel<OperandDependencies.Join> dependencies() {
        return dependencies;
    }

    private static String describe(OperandDependencies.Operand o) {
        String name = o.sheet() == null ? o.written() + " (unresolved)" : o.written().equals(o.sheet()) ? o.sheet() : o.written() + " = " + o.sheet();
        String life = switch (o.lifetime()) {
            case KEPT -> "rows kept (FW_Reuse)";
            case TABLE_ONLY -> "rows consumed here (FW_ReuseTableOnly)";
            case CONSUMED -> "rows consumed here";
        };
        return name + ": " + (o.readsNothing() ? "reads none, consumed by " + o.consumedBy() : life);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    void addSheet(String name) {
        try {
            document.addSheet(name);
            showDocument();
            sheets.setSelectedIndex(document.sheetCount() - 1);
        } catch (IllegalArgumentException e) {
            output.setText(e.getMessage() + "\n");
        }
    }

    void renameSheet(int index, String name) {
        try {
            document.renameSheet(index, name);
            showDocument();
            sheets.setSelectedIndex(index);
        } catch (IllegalArgumentException e) {
            output.setText(e.getMessage() + "\n");
        }
    }

    void removeSheet(int index) {
        if (index >= 0 && index < document.sheetCount()) {
            document.removeSheet(index);
            showDocument();
        }
    }

    /** What the grid holds: the cells of the sheet it shows. */
    private String gridTitle() {
        int sheet = grid.sheet();
        return sheet >= 0 && sheet < sheetNames.size() ? "Cells of sheet " + sheetNames.get(sheet) : "Cells of the selected sheet";
    }

    void selectSheet(int index) {
        sheets.setSelectedIndex(index);
    }

    /** Types a cell of the selected sheet as the grid's editor would. */
    void type(int row, int column, String text) {
        grid.setValueAt(text, row, column);
    }

    void insertRow(int row) {
        if (grid.sheet() >= 0) {
            document.insertRow(grid.sheet(), row);
            grid.show(document, grid.sheet());
        }
    }

    void removeRow(int row) {
        if (grid.sheet() >= 0) {
            document.removeRow(grid.sheet(), row);
            grid.show(document, grid.sheet());
        }
    }

    private void stopEditing() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
    }

    private void showDocument() {
        sheetNames.clear();
        for (String name : document.sheetNames()) sheetNames.addElement(name);
        if (document.sheetCount() > 0) {
            sheets.setSelectedIndex(0);
            grid.show(document, 0);
        } else {
            grid.show(document, -1);
        }
    }
}
