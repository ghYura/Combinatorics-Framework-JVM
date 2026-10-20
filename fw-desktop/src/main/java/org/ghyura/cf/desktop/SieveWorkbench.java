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

import org.ghyura.cf.desktop.session.*;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.model.rule.RuleValue;
import org.ghyura.cf.plan.SheetRole;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigInteger;
import java.net.URI;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import static org.ghyura.cf.desktop.session.SieveDocument.*;

/** One Sieve document across graph, matrix, rule inspector, parameter/order editor and advanced JSON. */
final class SieveWorkbench {
    private record Snapshot(SieveDocument document, String layout, int selected, long layoutRevision, long cost) {
        Snapshot(SieveDocument document, String layout, int selected, long layoutRevision) {
            this(document, layout, selected, layoutRevision, 2L * (document.json().length() + layout.length()));
        }
    }
    private record Opened(Session.OpenedSieve sieve, SieveFiles.Text layout, String warning) { }
    private record Saved(String fingerprint, String layoutFingerprint, String layoutProblem, String warnings) { }
    private record Bound(int rule, ConstraintBonds.Bond bond) { }
    private final JTabbedPane views = new JTabbedPane();
    private final JPanel root = new JPanel(new BorderLayout(5, 5));
    private final Supplier<Session> sessions;
    private final Supplier<Path> scenario;
    private final Navigator navigator;
    private final JLabel location = new JLabel(" "), state = new JLabel(" ");
    private final JTextArea output = new JTextArea(4, 60), json = new JTextArea();
    private final JTextField search = new JTextField(14), graphSearch = new JTextField(12), rowFilter = new JTextField(7), colFilter = new JTextField(7);
    private final DefaultTableModel ruleRows = new DefaultTableModel(new String[]{"Rule ID", "Kind", "In words"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable rules = new JTable(ruleRows), matrix = new JTable();
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(ruleRows);
    private final JComboBox<String> sheetA = new JComboBox<>(), sheetB = new JComboBox<>();
    private final JComboBox<String> polarity = new JComboBox<>(new String[]{"forbid", "require"});
    private final JSpinner scan = new JSpinner(new SpinnerNumberModel(100_000, 1, 1_000_000, 1_000));
    private final JButton cancel = new JButton("Cancel work");
    private final SieveCanvas canvas;
    private final SieveRuleEditor editor;
    private final SieveDataEditor data;
    private final Deque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Map<Component, Boolean> disabled = new IdentityHashMap<>();
    private SieveDocument document = SieveDocument.empty(), savedDocument = document;
    private Map<String, List<String>> values = Map.of();
    private Set<String> optional = Set.of();
    private List<Bound> bonds = List.of();
    private Path opened;
    private String fingerprint = "", layoutFingerprint = "";
    private int selected = -1, editingIndex = -1;
    private long layoutRevision, savedLayoutRevision, nextLayoutRevision;
    private boolean refreshing, jsonDirty, newDraft, busy, closed;
    private CompletableFuture<?> pending = CompletableFuture.completedFuture(null);
    private Face2Bridge face2;
    private long face2Revision;

    SieveWorkbench(Supplier<Session> sessions, Supplier<Path> scenario, Navigator navigator) {
        this.sessions = sessions; this.scenario = scenario; this.navigator = navigator;
        canvas = new SieveCanvas(this::selectRule, this::pair, this::removeRule, before -> {
            remember(new Snapshot(document, before, selected, layoutRevision)); layoutRevision = ++nextLayoutRevision; status();
        });
        // a draft that cannot be applied says why in the messages below, not in a dialog
        editor = new SieveRuleEditor(this::applyRule, this::discardRule, this::status, output::setText);
        data = new SieveDataEditor(this::applyData, () -> { dataShow(); status(); }, this::status, output::setText);
        output.setEditable(false); output.setLineWrap(true); output.setWrapStyleWord(true);
        Hints.of(output, "What the last action did, or what to do first. Filled after every action on this step.");
        json.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); json.setName("Sieve execution JSON");
        Hints.of(json, "The rules as the JSON text the scenario's rules file holds. Filled when a scenario is opened; type to change "
                + "it, then press Apply JSON.");
        Hints.of(canvas, "The rules drawn as cards: drag from a value's right pin to another value to pair them, or shift-click "
                + "values to group them.");
        JPanel top = new JPanel(new BorderLayout()); JPanel fileBar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        Hints.of(button(fileBar, "Open step 3 scenario", () -> { if (confirmDiscard()) open(); }), "Load the scenario chosen in step 3 "
                + "with its rules, to see and edit them here; no file dialog opens.");
        JButton load = Hints.of(button(fileBar, "Import constraints…", this::chooseImport), "Replace the rules shown with the rules of "
                + "another constraints file; nothing is saved until you press Save for scenario.");
        fileBar.add(new AutoSearch(navigator).button(() -> ready() ? FileSeeker.Kind.CONSTRAINTS : null, () -> opened, found -> {
            if (!busy && !closed && confirmDiscard()) importFrom(found);
        }, "Search the folders where constraints files usually are, then import the rules of one here."));
        JButton export = Hints.of(button(fileBar, "Export JSON…", this::chooseExport), "Save a copy of the rules as a JSON file; runs "
                + "keep reading the scenario's own rules file.");
        Hints.of(button(fileBar, "Save for scenario", () -> save()), "Save the rules beside the scenario as the rules file that every "
                + "run and preview reads.");
        JButton browser = Hints.of(button(fileBar, "Face 2 (Sieve) ↗", this::launchFace2), "Open the Face 2 web editor in your browser; "
                + "its Submit button saves the rules for this scenario.");
        browser.setName("Open Face 2 in browser");
        browser.setToolTipText("Open the original QA Face 2 web editor in your browser. Submit validates and saves its rules for this scenario.");
        JPanel workBar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        Hints.of(button(workBar, "Undo", this::undo), "Take back the last change to the rules or the layout.");
        Hints.of(button(workBar, "Redo", this::redo), "Make again the change that Undo took back.");
        Hints.of(button(workBar, "Validate", () -> validate(false)), "Check the rules for mistakes without counting test cases.");
        Hints.of(button(workBar, "Check impact", () -> validate(true)), "Count how many test cases each rule removes; nothing is run.");
        Hints.of(scan, "The most test cases each counting pass looks at; a smaller number answers faster.");
        Hints.of(cancel, "Stop the counting or saving that is still going.");
        workBar.add(FieldNames.label("Scan ≤", scan)); workBar.add(scan); workBar.add(cancel); cancel.setEnabled(false);
        scan.setToolTipText("Candidate limit for each counting pass. Scenario generation has the configured memory limits. Nothing executes the SUT.");
        cancel.addActionListener(e -> { cancelled.set(true); output.setText("Cancellation requested; waiting for the current operation to release its resources."); });
        navigator.bind(load, () -> Navigator.Purpose.CONSTRAINTS_OPEN, () -> opened);
        navigator.bind(export, () -> Navigator.Purpose.CONSTRAINTS_SAVE, () -> opened);
        top.add(rows(fileBar, workBar)); JPanel labels = new JPanel(new GridLayout(0, 1)); labels.add(location); labels.add(state); top.add(labels, BorderLayout.SOUTH); root.add(top, BorderLayout.NORTH);
        JPanel graph = new JPanel(new BorderLayout()); JPanel graphBar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        Hints.of(graphSearch, "Show only the cards whose sheet or value holds this text. Filled by you: type part of a name.");
        graphBar.add(FieldNames.label("Find cards", graphSearch)); graphBar.add(graphSearch);
        Hints.of(button(graphBar, "Fit", canvas::fit), "Zoom the graph so that every card is in view.");
        Hints.of(button(graphBar, "+", () -> canvas.zoomAt(new Point(canvas.getWidth() / 2, canvas.getHeight() / 2), 1.2)), "Zoom into the middle of the graph.");
        Hints.of(button(graphBar, "−", () -> canvas.zoomAt(new Point(canvas.getWidth() / 2, canvas.getHeight() / 2), 1 / 1.2)), "Zoom out from the middle of the graph.");
        Hints.of(button(graphBar, "Arrange", canvas::arrange), "Lay the cards out again in tidy columns.");
        JPanel groupBar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        Hints.of(polarity, "Whether the values selected on the graph are forbidden or required together when you group them.");
        groupBar.add(FieldNames.label("Group as", polarity)); groupBar.add(polarity);
        Hints.of(button(groupBar, "Group selected values", this::group), "Make one rule from the values selected on the graph, as the choice beside it says.");
        graph.add(rows(graphBar, groupBar), BorderLayout.NORTH); graph.add(canvas); JLabel instructions = new JLabel("<html>Drag right pin → value: pair. Shift-click values: group.<br>Wheel: zoom · empty space/Space: pan · 0: fit</html>");
        graph.add(instructions, BorderLayout.SOUTH); views.addTab("Sieve graph", graph); views.addTab("Bond matrix", matrixPanel());
        views.addTab("Parameters / orders", data.component());
        JPanel raw = new JPanel(new BorderLayout()); raw.add(FieldNames.titled(json, "Constraints as JSON")); JPanel rawActions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        Hints.of(button(rawActions, "Apply JSON", () -> applyJson(json.getText())), "Use the JSON text as the rules; it is checked before it is used.");
        Hints.of(button(rawActions, "Discard JSON draft", () -> { refreshJson(); status(); }), "Throw away the changes to the JSON text and show the rules again.");
        rawActions.add(new JLabel("Framework sidecar format; layout is saved separately.")); raw.add(rawActions, BorderLayout.SOUTH); views.addTab("Advanced JSON", raw);
        String[] viewHints = {"To see and draw the rules as cards, with links between the values of the scenario's sheets.",
                "To find and add pair rules on a grid of one sheet's values against another's, with how many rules forbid or require each pair.",
                "To give values the numbers that formula rules use, and to rank each sheet's values.",
                "To read or change the rules as JSON text, for what the other editors cannot show."};
        for (int i = 0; i < viewHints.length; i++) ((JComponent) views.getComponentAt(i)).putClientProperty(Hints.TAB, viewHints[i]);
        int[] activeView = {0};
        views.addChangeListener(e -> {
            int next = views.getSelectedIndex(), previous = activeView[0];
            if (next == previous) return;
            boolean pendingDraft = previous == 2 ? data.dirty() : previous == 3 ? jsonDirty : next >= 2 && (newDraft || editor.dirty());
            if (pendingDraft) {
                views.setSelectedIndex(previous);
                output.setText("Apply or discard the current draft before switching editors. Your edits are still present.");
                return;
            }
            activeView[0] = next;
            editor.editable(next < 2);
        });
        rules.setRowSorter(sorter); rules.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rules.getSelectionModel().addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            int view = rules.getSelectedRow(); if (view >= 0 && !selectRule(rules.convertRowIndexToModel(view))) selectTable();
        });
        JPanel ruleList = new JPanel(new BorderLayout()); JPanel ruleBar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        Hints.of(search, "Show only the rules whose name, kind or words hold this text. Filled by you: type part of it.");
        ruleBar.add(FieldNames.label("Find rules", search)); ruleBar.add(search);
        JComboBox<String> type = Hints.of(new JComboBox<>(new String[]{"pairs", "sets", "when", "mapping", "assert"}),
                "The kind of rule New rule starts: value pairs, sets, a formula, a mapping or an assertion.");
        ruleBar.add(FieldNames.label("Kind", type)); ruleBar.add(type);
        Hints.of(button(ruleBar, "New rule", () -> newRule((String) type.getSelectedItem())), "Start a new rule of the kind beside it, in the editor below.");
        JPanel ruleActions = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        Hints.of(button(ruleActions, "Duplicate", this::duplicateRule), "Make a copy of the selected rule under a new name.");
        Hints.of(button(ruleActions, "Delete", this::removeRule), "Delete the selected rule for good.");
        Hints.of(button(ruleActions, "↑", () -> moveRule(-1)), "Move the selected rule one place up.");
        Hints.of(button(ruleActions, "↓", () -> moveRule(1)), "Move the selected rule one place down.");
        Hints.of(button(ruleActions, "Find on graph", () -> { views.setSelectedIndex(0); graphSearch.setText(""); canvas.focusRule(selected); }),
                "Show the selected rule's cards on the graph.");
        Hints.of(rules, "All rules, one per row, with their kind and a summary in words. Select one to edit it below.");
        ruleList.add(rows(ruleBar, ruleActions), BorderLayout.NORTH); ruleList.add(new JScrollPane(rules));
        JSplitPane inspector = new JSplitPane(JSplitPane.VERTICAL_SPLIT, ruleList, editor.component()); inspector.setResizeWeight(.32);
        inspector.setDividerLocation(190); inspector.setMinimumSize(new Dimension(380, 300)); views.setMinimumSize(new Dimension(330, 300));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, views, inspector); split.setResizeWeight(.55); split.setDividerLocation(590); root.add(split);
        root.add(FieldNames.titled(output, "Messages"), BorderLayout.SOUTH); root.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        SieveRuleEditor.listen(search, () -> { String q = search.getText(); sorter.setRowFilter(q.isBlank() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(q))); });
        SieveRuleEditor.listen(graphSearch, () -> { if (!refreshing) refreshGraph(); });
        SieveRuleEditor.listen(json, () -> { if (!refreshing) { jsonDirty = true; status(); } });
        binding("control Z", "undo", this::undo); binding("control Y", "redo", this::redo); binding("control S", "save", () -> save());
        output.setText("Press Open step 3 scenario to load the scenario chosen in step 3 with its rules (constraints). Edit them here, "
                + "validate or check impact, then press Save for scenario.\nGraph, matrix and inspector share one document. "
                + "Forbid/require, gates and conditions retain Framework semantics.");
        root.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { if (opened == null) waiting(); }
        });
        waiting();
        refresh();
    }

    /** Before a scenario is opened, the location line says which one Open step 3 scenario loads. */
    private void waiting() {
        Path next;
        try { next = scenario.get(); } catch (java.nio.file.InvalidPathException e) { next = null; }
        location.setText(next == null ? "No scenario is chosen in step 3 yet: choose one there, then press Open step 3 scenario."
                : "Step 3 scenario: " + next + "   ·   press Open step 3 scenario to edit its rules.");
    }

    JComponent component() { return root; }
    SieveCanvas canvas() { return canvas; }
    SieveDocument document() { return document; }
    Path opened() { return opened; }
    String output() { return output.getText(); }
    boolean dirty() { return opened != null && (!document.equals(savedDocument) || layoutRevision != savedLayoutRevision || drafts()); }
    private boolean drafts() { return newDraft || editor.dirty() || data.dirty() || jsonDirty; }
    private static JPanel rows(JComponent... children) { JPanel panel = new JPanel(new GridLayout(0, 1)); for (JComponent child : children) panel.add(child); return panel; }

    private JButton button(JPanel panel, String text, Runnable action) {
        JButton button = new JButton(text); button.addActionListener(e -> { if (!busy && !closed) { try { action.run(); } catch (RuntimeException failure) { output.setText(Background.describe(failure)); } } }); panel.add(button); return button;
    }
    private void binding(String key, String name, Runnable run) {
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), name);
        root.getActionMap().put(name, new AbstractAction() { public void actionPerformed(ActionEvent e) { if (!busy && !closed) run.run(); } });
    }
    private boolean ready() { if (opened == null) { output.setText("Press Open step 3 scenario first."); return false; } return !busy && !closed; }
    private boolean cleanDrafts() {
        if (drafts()) { output.setText("Apply or discard the pending rule, data or JSON draft before this action. Your edits are still in their editor."); return false; }
        return ready();
    }
    private void status() {
        state.setText((busy ? "Working… " : "") + (dirty() ? "Unsaved edits · " : "") + document.rules().size() + " rules"
                + (drafts() ? " · pending draft: apply or discard" : "") + " · " + (opened == null ? "" : "Save for scenario makes these rules available to every Framework entry point."));
    }

    CompletableFuture<?> open() {
        Path chosen = scenario.get();
        if (chosen == null) { output.setText("Choose a scenario in step 3 first, then press Open step 3 scenario."); return CompletableFuture.completedFuture(null); }
        Session session = sessions.get(); Path file = chosen.toAbsolutePath().normalize();
        return work("open-sieve", () -> {
            var openedSieve = session.openSieve(file);
            try { return new Opened(openedSieve, SieveFiles.read(SieveFiles.layoutOf(ConstraintSidecarReader.companionOf(file)), InputSource::read, 4 * 1024 * 1024), ""); }
            catch (RuntimeException problem) { return new Opened(openedSieve, new SieveFiles.Text("", ""), "\nLayout could not be read: " + problem.getMessage() + ". Constraints are intact."); }
        }, result -> {
            closeFace2();
            opened = file; document = result.sieve().document(); savedDocument = document; fingerprint = result.sieve().fingerprint(); layoutFingerprint = result.layout().fingerprint();
            Map<String, List<String>> offered = new LinkedHashMap<>();
            result.sieve().summary().spec().sheets().forEach(s -> offered.put(s.name(), s.cells().stream().map(v -> org.ghyura.cf.constraints.Py.strip(v.text())).distinct().toList()));
            values = Collections.unmodifiableMap(offered); Set<String> opts = new LinkedHashSet<>(); result.sieve().summary().plan().sheets().forEach(s -> { if (s.role() == SheetRole.OPTIONAL) opts.add(s.name()); }); optional = Set.copyOf(opts);
            undo.clear(); redo.clear(); selected = -1; layoutRevision = savedLayoutRevision = nextLayoutRevision = 0; canvas.clearLayout();
            refreshing = true; sheetA.removeAllItems(); sheetB.removeAllItems(); values.keySet().forEach(s -> { sheetA.addItem(s); sheetB.addItem(s); }); if (sheetB.getItemCount() > 1) sheetB.setSelectedIndex(1); refreshing = false;
            refresh();
            String warning = result.warning();
            try { if (!result.layout().content().isBlank()) canvas.restoreLayout(result.layout().content()); else canvas.fit(); }
            catch (RuntimeException problem) { warning = "\nLayout could not be restored: " + problem.getMessage() + ". Constraints are intact."; canvas.fit(); }
            navigator.trail().record(ConstraintSidecarReader.companionOf(opened));
            location.setText("Scenario: " + opened + "   →   " + ConstraintSidecarReader.companionOf(opened).getFileName());
            output.setText("Opened " + document.rules().size() + " rules. Rule IDs, order and JSON types are preserved." + warning + "\n" + canvas.note()); status();
        });
    }

    private void launchFace2() {
        openFace2().thenAccept(uri -> Background.run("face2-browser", () -> {
            try {
                if (!java.awt.Desktop.isDesktopSupported() || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE))
                    throw new IllegalStateException("Automatic browser opening is unavailable.");
                java.awt.Desktop.getDesktop().browse(uri);
                return uri;
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }, ignored -> { }, problem -> output.append("\n" + Background.describe(problem) + "\nOpen this address in your browser:\n" + uri)));
    }

    /** The button and browser tests share this path; Desktop.browse is only the final convenience. */
    CompletableFuture<URI> openFace2() {
        Path chosen = scenario.get();
        if (chosen == null) {
            output.setText("Select a scenario in step 3 first.");
            return CompletableFuture.failedFuture(new IllegalStateException(output.getText()));
        }
        if (!chosen.toAbsolutePath().normalize().equals(opened)) {
            if (dirty()) {
                output.setText("Save or discard the current scenario's Sieve edits before opening another scenario in Face 2.");
                return CompletableFuture.failedFuture(new IllegalStateException(output.getText()));
            }
            return open().thenCompose(ignored -> openFace2());
        }
        if (!cleanDrafts()) return CompletableFuture.failedFuture(new IllegalStateException(output.getText()));
        Face2Bridge bridge = prepareFace2();
        return work("open-face2", () -> {
            try { return bridge.start(); }
            catch (IOException | RuntimeException failure) { bridge.close(); throw new IllegalStateException("Face 2 could not start its local browser connection: " + failure.getMessage(), failure); }
        }, uri -> output.setText("Face 2 (Sieve) is ready in your browser:\n" + uri
                + "\nSubmit in Face 2 validates and saves the rules here. The scenario's runs and previews acquire them automatically."
                + "\nFinish browser edits before changing rules in the JVM; stale submissions are refused."));
    }

    /** Prepare on the event thread; starting its socket is a separate background operation. */
    Face2Bridge prepareFace2() {
        if (!cleanDrafts()) throw new IllegalStateException(output.getText());
        closeFace2();
        Session session = sessions.get(); Path file = opened; SieveDocument snapshot = document;
        var expected = new java.util.concurrent.atomic.AtomicReference<>(snapshot);
        long revision = face2Revision;
        face2 = new Face2Bridge(session, file, snapshot, values, optional, (Integer) scan.getValue(),
                next -> acceptFace2(revision, session, file, expected.get(), next).thenRun(() -> expected.set(next)));
        return face2;
    }

    private CompletableFuture<Void> acceptFace2(long revision, Session session, Path file, SieveDocument snapshot, SieveDocument next) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                if (closed || busy || revision != face2Revision || !Objects.equals(opened, file)
                        || !document.equals(snapshot) || drafts() || sessions.get() != session
                        || scenario.get() == null || !scenario.get().toAbsolutePath().normalize().equals(file))
                    throw new IllegalStateException("The JVM scenario, configuration or Sieve edits changed. Reopen Face 2 before submitting; your browser edits have not been saved.");
                // Acquire only after the normal save succeeds. A failed save leaves both editors retryable.
                saveTo(ConstraintSidecarReader.companionOf(opened), true, next, true).whenComplete((ignored, failure) -> {
                    if (failure == null) result.complete(null); else result.completeExceptionally(failure);
                });
            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private void closeFace2() {
        face2Revision++;
        if (face2 != null) { face2.close(); face2 = null; }
    }

    boolean selectRule(int index) {
        if (refreshing || index == selected) return true;
        if (!cleanDrafts()) return false;
        selected = index; editingIndex = index; editor.show(document, index < 0 ? null : document.rules().get(index), values); canvas.select(index); selectTable(); status(); return true;
    }
    private void selectTable() {
        refreshing = true;
        try { rules.clearSelection(); if (selected >= 0 && selected < ruleRows.getRowCount()) { int view = rules.convertRowIndexToView(selected); if (view >= 0) rules.setRowSelectionInterval(view, view); } }
        finally { refreshing = false; }
    }
    private void refresh() {
        refreshing = true;
        try {
            ruleRows.setRowCount(0); List<Bound> bindings = new ArrayList<>();
            var rs = document.rules();
            for (int i = 0; i < rs.size(); i++) {
                var r = rs.get(i); ruleRows.addRow(new Object[]{SieveDocument.label(r, i), kind(r), ConstraintBonds.describe(r)});
                for (var b : ConstraintBonds.read(object("constraints", new RuleValue.ListValue(List.of(r)))).bonds()) bindings.add(new Bound(i, b));
            }
            bonds = List.copyOf(bindings); selected = Math.min(selected, rs.size() - 1); editingIndex = selected; newDraft = false;
            editor.show(document, selected < 0 ? null : rs.get(selected), values); data.show(document, values); json.setText(document.json()); jsonDirty = false;
            refreshGraph(); matrix.setModel(new Matrix()); canvas.select(selected);
        } finally { refreshing = false; }
        selectTable(); status();
    }
    private void refreshGraph() { canvas.show(document, values, optional, graphSearch.getText()); canvas.select(selected); }
    private void refreshJson() { refreshing = true; try { json.setText(document.json()); jsonDirty = false; } finally { refreshing = false; } }
    private void dataShow() { data.show(document, values); }
    private void discardRule() { newDraft = false; editingIndex = selected; editor.show(document, selected < 0 ? null : document.rules().get(selected), values); status(); }
    private Snapshot snapshot() { return new Snapshot(document, canvas.layoutJson(), selected, layoutRevision); }
    private void remember(Snapshot previous) { undo.addLast(previous); redo.clear(); trim(undo); }
    private void trim(Deque<Snapshot> history) { long bytes = history.stream().mapToLong(Snapshot::cost).sum(); while (history.size() > 32 || bytes > 8L * 1024 * 1024) bytes -= history.removeFirst().cost(); }
    private void commit(SieveDocument next, int selection) { remember(snapshot()); document = next; selected = selection; refresh(); }
    void undo() { if (cleanDrafts() && !undo.isEmpty()) { redo.addLast(snapshot()); trim(redo); restore(undo.removeLast()); } }
    void redo() { if (cleanDrafts() && !redo.isEmpty()) { undo.addLast(snapshot()); trim(undo); restore(redo.removeLast()); } }
    private void restore(Snapshot s) { document = s.document(); selected = s.selected(); layoutRevision = s.layoutRevision(); refresh(); canvas.restoreLayout(s.layout()); output.setText("Restored edit. Validate or check impact before saving."); status(); }

    CompletableFuture<?> applyJson(String text) {
        if (!ready() || newDraft || editor.dirty() || data.dirty()) { output.setText("Apply or discard the rule/data draft first."); return CompletableFuture.completedFuture(null); }
        return work("sieve-json", () -> SieveDocument.parse(text, "Sieve JSON draft"), next -> commit(next, Math.min(selected, next.rules().size() - 1)));
    }
    private void applyRule(RuleValue.MapValue rule) {
        if (!ready() || data.dirty() || jsonDirty) { output.setText("Apply or discard data/JSON drafts first."); return; }
        int nextIndex = editingIndex < 0 ? document.rules().size() : editingIndex;
        SieveDocument candidate = document.replace(editingIndex, rule);
        work("sieve-rule", () -> SieveDocument.parse(candidate.json(), "rule draft"), next -> commit(next, nextIndex));
    }
    private void applyData(SieveDocument candidate) {
        if (!ready() || newDraft || editor.dirty() || jsonDirty) { output.setText("Apply or discard rule/JSON drafts first."); return; }
        work("sieve-data", () -> SieveDocument.parse(candidate.json(), "parameter/order draft"), next -> commit(next, selected));
    }
    private void newRule(String type) {
        if (!cleanDrafts()) return;
        views.setSelectedIndex(0);
        List<String> sheets = values.keySet().stream().limit(2).toList();
        if (sheets.isEmpty() || !type.equals("assert") && sheets.size() < 2) { output.setText("This rule needs " + (type.equals("assert") ? "a sheet." : "two sheets.")); return; }
        Map<String, RuleValue> fields = new LinkedHashMap<>(); fields.put("id", new RuleValue.Text(document.nextId())); fields.put("polarity", new RuleValue.Text(type.equals("assert") ? "require" : "forbid"));
        switch (type) {
            case "pairs", "sets" -> {
                Map<String, RuleValue> sides = new LinkedHashMap<>(); sheets.forEach(s -> sides.put(s, type.equals("sets") ? texts(values.getOrDefault(s, List.of()).stream().limit(1).toList()) : new RuleValue.Text(values.getOrDefault(s, List.of()).stream().findFirst().orElse(""))));
                fields.put(type, type.equals("sets") ? new RuleValue.MapValue(sides) : new RuleValue.ListValue(List.of(new RuleValue.MapValue(sides)))); fields.put("sheets", texts(sheets));
            }
            case "mapping" -> fields.put(type, object("source", new RuleValue.Text(sheets.get(0)), "target", new RuleValue.Text(sheets.get(1)), "allow", new RuleValue.MapValue(Map.of())));
            case "when" -> { fields.put(type, new RuleValue.Text("True")); fields.put("sheets", texts(sheets)); }
            case "assert" -> fields.put(type, object("sheet", new RuleValue.Text(sheets.getFirst()), "present", new RuleValue.Bool(true)));
            default -> throw new IllegalArgumentException("Unknown rule kind: " + type);
        }
        editingIndex = -1; newDraft = true; editor.show(document, new RuleValue.MapValue(fields), values); status();
    }
    void pair(SieveCanvas.Value a, SieveCanvas.Value b) {
        if (!cleanDrafts() || a.sheet().equals(b.sheet())) return;
        var sides = object(a.sheet(), new RuleValue.Text(a.value()), b.sheet(), new RuleValue.Text(b.value()));
        var rule = object("id", new RuleValue.Text(document.nextId()), "polarity", new RuleValue.Text((String) polarity.getSelectedItem()), "sheets", texts(List.of(a.sheet(), b.sheet())), "pairs", new RuleValue.ListValue(List.of(sides)));
        commit(document.replace(-1, rule), document.rules().size()); output.setText("Pair added. Select the rule to add a gate or an Only when condition. Save after validation.");
    }
    private void group() {
        if (!cleanDrafts()) return;
        Map<String, List<String>> selectedValues = new LinkedHashMap<>(); canvas.chosenValues().forEach(v -> selectedValues.computeIfAbsent(v.sheet(), s -> new ArrayList<>()).add(v.value()));
        if (selectedValues.size() < 2) { output.setText("Shift-click values from at least two sheets, then create the group."); return; }
        Map<String, RuleValue> sets = new LinkedHashMap<>(); selectedValues.forEach((s, vs) -> sets.put(s, texts(vs)));
        var rule = object("id", new RuleValue.Text(document.nextId()), "polarity", new RuleValue.Text((String) polarity.getSelectedItem()), "sets", new RuleValue.MapValue(sets));
        commit(document.replace(-1, rule), document.rules().size()); canvas.clearChosen(); output.setText("Group added as a Cartesian product of the chosen values. Use a tuple rule for a list of specific combinations.");
    }
    private void removeRule() { if (cleanDrafts() && selected >= 0) { commit(document.remove(selected), selected - 1); output.setText("Rule deleted. Undo restores it."); } }
    private void duplicateRule() { if (cleanDrafts() && selected >= 0) { var r = new LinkedHashMap<>(document.rules().get(selected).entries()); r.put("id", new RuleValue.Text(document.nextId())); commit(document.replace(-1, new RuleValue.MapValue(r)), document.rules().size()); } }
    private void moveRule(int delta) { int next = selected + delta; if (cleanDrafts() && selected >= 0 && next >= 0 && next < document.rules().size()) commit(document.move(selected, next), next); }

    private JComponent matrixPanel() {
        // each axis on its own row, so the names fit beside their fields in the narrow left pane
        JPanel panel = new JPanel(new BorderLayout()); JPanel rowBar = new JPanel(new FlowLayout(FlowLayout.LEADING)), colBar = new JPanel(new FlowLayout(FlowLayout.LEADING));
        Hints.of(sheetA, "The sheet whose values are the rows of the matrix.");
        Hints.of(sheetB, "The sheet whose values are the columns of the matrix.");
        Hints.of(rowFilter, "Show only the row values that hold this text; up to 50 matches are shown. Filled by you.");
        Hints.of(colFilter, "Show only the column values that hold this text; up to 50 matches are shown. Filled by you.");
        rowBar.add(FieldNames.label("Rows", sheetA)); rowBar.add(sheetA); rowBar.add(FieldNames.label("Row filter", rowFilter)); rowBar.add(rowFilter);
        colBar.add(FieldNames.label("Columns", sheetB)); colBar.add(sheetB); colBar.add(FieldNames.label("Column filter", colFilter)); colBar.add(colFilter);
        rowFilter.setToolTipText("Find row values; up to 50 matches are shown."); colFilter.setToolTipText("Find column values; up to 50 matches are shown.");
        JPanel matrixActions = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        Hints.of(button(matrixActions, "Add pair at cell", () -> { int r = matrix.getSelectedRow(), c = matrix.getSelectedColumn(); if (r >= 0 && c > 0) pair(new SieveCanvas.Value((String) sheetA.getSelectedItem(), rowValues().get(r)), new SieveCanvas.Value((String) sheetB.getSelectedItem(), columnValues().get(c - 1))); else output.setText("Select a value cell first (not the row label)."); }),
                "Make a new rule from the selected cell's row value and column value.");
        Hints.of(button(matrixActions, "Edit cell rule", () -> {
            int r = matrix.getSelectedRow(), c = matrix.getSelectedColumn();
            if (r >= 0 && c > 0 && matrix.getModel() instanceof Matrix model) {
                int first = model.first[r][c - 1]; if (first >= 0) selectRule(first); else output.setText("No tuple/set rule in this cell.");
            }
        }), "Select the rule that the selected cell counts, to edit it below.");
        SieveRuleEditor.listen(rowFilter, () -> { if (!refreshing) matrix.setModel(new Matrix()); });
        SieveRuleEditor.listen(colFilter, () -> { if (!refreshing) matrix.setModel(new Matrix()); });
        Hints.of(matrix, "Row values against column values: F counts the rules that forbid a pair, R those that require it.");
        panel.add(rows(rowBar, colBar, matrixActions), BorderLayout.NORTH); matrix.setCellSelectionEnabled(true); matrix.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); matrix.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        matrix.setToolTipText("Numbers count tuple/set memberships, including gated rules. Formula, mapping and assertion logic is in the rule list. Add pair creates a new independent rule.");
        sheetA.addActionListener(e -> { if (!refreshing) matrix.setModel(new Matrix()); }); sheetB.addActionListener(e -> { if (!refreshing) matrix.setModel(new Matrix()); });
        panel.add(new JScrollPane(matrix)); panel.add(new JLabel("Forbid/require counts · first 50 matching values per axis · use filters to find other values."), BorderLayout.SOUTH); return panel;
    }
    private List<String> rowValues() { return matching((String) sheetA.getSelectedItem(), rowFilter.getText()); }
    private List<String> columnValues() { return matching((String) sheetB.getSelectedItem(), colFilter.getText()); }
    private List<String> matching(String sheet, String query) { if (sheet == null) return List.of(); String q = query.toLowerCase(Locale.ROOT); return values.getOrDefault(sheet, List.of()).stream().filter(v -> v.toLowerCase(Locale.ROOT).contains(q)).limit(50).toList(); }
    @SuppressWarnings("serial")
    private final class Matrix extends AbstractTableModel {
        final List<String> rows = rowValues(), cols = columnValues();
        final int[][] first = new int[rows.size()][cols.size()], forbids = new int[rows.size()][cols.size()], requires = new int[rows.size()][cols.size()];
        Matrix() {
            int[][] last = new int[rows.size()][cols.size()];
            for (int r = 0; r < rows.size(); r++) { Arrays.fill(first[r], -1); Arrays.fill(last[r], -1); }
            String a = (String) sheetA.getSelectedItem(), b = (String) sheetB.getSelectedItem();
            if (Objects.equals(a, b)) return;
            Map<String, Integer> rowIndex = new HashMap<>(), colIndex = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) rowIndex.put(rows.get(i), i);
            for (int i = 0; i < cols.size(); i++) colIndex.put(cols.get(i), i);
            for (Bound binding : bonds) {
                List<Integer> rs = binding.bond().sides().getOrDefault(a, List.of()).stream().filter(v -> v instanceof RuleValue.Text).map(v -> rowIndex.get(text(v))).filter(Objects::nonNull).toList();
                List<Integer> cs = binding.bond().sides().getOrDefault(b, List.of()).stream().filter(v -> v instanceof RuleValue.Text).map(v -> colIndex.get(text(v))).filter(Objects::nonNull).toList();
                for (int r : rs) for (int c : cs) if (last[r][c] != binding.rule()) {
                    if (first[r][c] < 0) first[r][c] = binding.rule();
                    last[r][c] = binding.rule(); if (binding.bond().polarity().equals("forbid")) forbids[r][c]++; else requires[r][c]++;
                }
            }
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.size() + 1; }
        public String getColumnName(int col) { return col == 0 ? String.valueOf(sheetA.getSelectedItem()) : cols.get(col - 1); }
        public Object getValueAt(int row, int col) { return col == 0 ? rows.get(row) : first[row][col - 1] < 0 ? "" : "F:" + forbids[row][col - 1] + " R:" + requires[row][col - 1]; }
    }

    CompletableFuture<?> validate(boolean impact) {
        if (!cleanDrafts()) return CompletableFuture.completedFuture(null);
        Session session = sessions.get(); String content = document.json(); Path file = opened; int limit = (Integer) scan.getValue();
        return work(impact ? "sieve-impact" : "sieve-validate", () -> impact
                ? session.preview(file, content, new CandidatePreview.Request(BigInteger.valueOf(limit), BigInteger.ZERO, 1, false, true), cancelled::get)
                : session.validateSieve(file, content, cancelled::get), result -> {
            StringBuilder text = new StringBuilder(impact ? "Impact using the actual Framework sieve (no SUT execution).\n" : "Valid for execution; no candidate page scanned.\n");
            if (result.estimates() != null) text.append(result.estimates().toText()).append('\n');
            for (var r : result.rules()) text.append(r.name()).append(": ").append(r.removed() == null ? "not counted exactly within the scan limit" : r.removed() + " removed").append('\n');
            result.diagnostics().forEach(d -> text.append(d).append('\n'));
            if (!document.contradictions().isEmpty()) text.append("Opposite-polarity rules with identical definitions: ").append(document.contradictions()).append("\nCheck impact: guards or gates may make these inactive.");
            output.setText(text.toString());
        });
    }
    CompletableFuture<?> save() { return cleanDrafts() ? saveTo(ConstraintSidecarReader.companionOf(opened), true) : CompletableFuture.completedFuture(null); }
    private CompletableFuture<?> saveTo(Path target, boolean companion) {
        return saveTo(target, companion, document, false);
    }
    private CompletableFuture<?> saveTo(Path target, boolean companion, SieveDocument snapshot, boolean acquire) {
        Session session = sessions.get(); String content = snapshot.json(), layout = canvas.layoutJson(); Path file = opened;
        long savedRevision = layoutRevision;
        String expected = companion ? fingerprint : null, expectedLayout = companion ? layoutFingerprint : null;
        return work("save-sieve", () -> {
            String fileStamp = expected == null ? SieveFiles.fingerprint(target) : expected;
            Path layoutFile = SieveFiles.layoutOf(target); String layoutStamp = expectedLayout == null ? SieveFiles.fingerprint(layoutFile) : expectedLayout;
            var conflicts = snapshot.contradictions();
            String warnings = conflicts.isEmpty() ? "" : "\nOpposite-polarity rules: " + conflicts + ". Check impact to see whether they are active.";
            session.validateSieve(file, content, cancelled::get); if (cancelled.get()) throw new IllegalStateException("Save cancelled; no files written.");
            String written = SieveFiles.write(target, content, fileStamp), layoutWritten = layoutStamp, problem = "";
            try { layoutWritten = SieveFiles.write(layoutFile, layout, layoutStamp); } catch (RuntimeException failure) { problem = failure.getMessage(); }
            return new Saved(written, layoutWritten, problem, warnings);
        }, result -> {
            if (acquire) commit(snapshot, -1);
            if (companion) { fingerprint = result.fingerprint(); savedDocument = snapshot; layoutFingerprint = result.layoutFingerprint(); if (result.layoutProblem().isEmpty()) savedLayoutRevision = savedRevision; else layoutRevision = ++nextLayoutRevision; }
            navigator.trail().record(target);
            output.setText("Saved executable constraints: " + target + result.warnings() + (result.layoutProblem().isEmpty() ? "\nLayout saved separately: " + SieveFiles.layoutOf(target)
                    : "\nConstraints were saved, but layout was not: " + result.layoutProblem()) + (companion ? "\nFramework runs and previews now acquire this companion automatically." : "\nThe current scenario companion is unchanged; import this file later or use it as cf.constraints.file.")); status();
        });
    }
    private void chooseExport() {
        if (!cleanDrafts()) return;
        Path target = navigator.choose(root, Navigator.Purpose.CONSTRAINTS_SAVE, opened); if (target == null) return;
        target = target.toAbsolutePath().normalize();
        if (!target.getFileName().toString().endsWith(".json")) target = target.resolveSibling(target.getFileName() + ".constraints.json");
        boolean companion = target.equals(ConstraintSidecarReader.companionOf(opened));
        if (!companion && (Files.exists(target) || Files.exists(SieveFiles.layoutOf(target))) && JOptionPane.showConfirmDialog(root, "Replace constraints and layout at " + target + "?", "Export", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        saveTo(target, companion);
    }
    private void chooseImport() {
        if (!ready() || !confirmDiscard()) return;
        Path source = navigator.choose(root, Navigator.Purpose.CONSTRAINTS_OPEN, opened); if (source == null) return;
        importFrom(source);
    }
    /** Replaces the rules shown with the rules of a constraints file; Save for scenario attaches them. */
    CompletableFuture<?> importFrom(Path source) {
        return work("import-sieve", () -> {
            var text = SieveFiles.read(source, InputSource::read, SieveDocument.MAX_BYTES);
            if (text.fingerprint().isEmpty()) throw new IllegalArgumentException("No file at " + source);
            return SieveDocument.parse(text.content(), source.toString());
        }, next -> { commit(next, -1); output.setText("Imported " + source + ". Review and save to attach it to " + opened + "."); });
    }
    boolean confirmDiscard() {
        if (!dirty()) return true;
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            // no one can be asked: nothing is discarded
            output.setText("Unsaved constraints, layout or drafts are kept: apply or discard the drafts, or press Save for scenario, first.");
            return false;
        }
        return JOptionPane.showConfirmDialog(root, "Discard unsaved constraints, layout or pending drafts?\nChoose Cancel to apply drafts and Save for scenario first.", "Unsaved Sieve edits", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }
    boolean allowClose() { return confirmDiscard(); }
    CompletableFuture<Void> close() { closed = true; closeFace2(); cancelled.set(true); canvas.cancelGesture(); return pending.handle((value, failure) -> null); }

    private <T> CompletableFuture<T> work(String name, Supplier<T> task, Consumer<T> done) {
        if (busy || closed) return CompletableFuture.failedFuture(new IllegalStateException("Another Sieve operation is still running."));
        busy = true; cancelled.set(false); disable(root); cancel.setEnabled(true); status();
        var future = Background.run(name, task, result -> {
            enable(); if (!closed) { try { done.accept(result); } finally { status(); } }
        }, failure -> { enable(); if (!closed) { output.setText(Background.describe(failure)); status(); } });
        pending = future; return future;
    }
    private void disable(Component component) { if (component == cancel) return; disabled.put(component, component.isEnabled()); component.setEnabled(false); if (component instanceof Container container) for (Component child : container.getComponents()) disable(child); }
    private void enable() { disabled.forEach(Component::setEnabled); disabled.clear(); busy = false; cancel.setEnabled(false); }

}
