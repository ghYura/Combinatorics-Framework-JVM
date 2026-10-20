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
import org.ghyura.cf.desktop.session.FileSeeker;
import org.ghyura.cf.desktop.session.PlanEstimates;
import org.ghyura.cf.desktop.session.ScenarioLibrary;
import org.ghyura.cf.desktop.session.ScenarioValidation;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.generation.Cardinality;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.spi.Cancellation;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The scenario library tab (the legacy Face 1 scenario library, intake/scenario_library.py): open a generator tree, or one of
 * its intake manifests, choose a suite, read each verified scenario's title, documented counts (passes of candidates),
 * source and description, open a static scenario in the Scenario tab, and check its candidate count: the executable
 * candidates of its plan against the count the library documents. A dynamic scenario is made by its application, which the
 * port does not carry (author scope), and cannot be opened here. Reading and checking stay off the event thread.
 */
final class LibraryView {

    private final java.util.function.Supplier<Session> session;
    private final Consumer<Path> open;
    /** The library found beside this checkout, offered with one button; null when there is none. */
    private final Path example;
    private final String guide;
    private final JLabel where = new JLabel(" ");
    private final DefaultListModel<String> suiteNames = new DefaultListModel<>();
    private final JList<String> suites = Hints.of(new JList<>(suiteNames), "The groups of example scenarios. Pick one to list its examples.");
    private final ListTableModel<ScenarioLibrary.Entry> scenarios = new ListTableModel<>(List.of("Scenario", "Documented", "Source"),
            List.<Function<ScenarioLibrary.Entry, Object>>of(e -> e.title().isEmpty() ? e.id() : e.title(),
                    e -> e.dynamic() ? e.releaseGateCandidates() + " candidates" : e.pass() + "/" + e.candidates() + " PASS",
                    ScenarioLibrary.Entry::sourceLabel));
    private final JTable table = Hints.of(new JTable(scenarios), "The examples of the chosen group, with the number of test cases each one should make. Select one to read about it below.");
    private final JScrollPane tablePane = TableTitles.titled(table, scenariosTitle(null));
    private final JTextArea details = Hints.of(new JTextArea(6, 40), "About the selected example: its name, what it checks, the counts it should give, and where its file is. Filled when you select an example in the table above.");
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private List<ScenarioLibrary.Entry> entries = List.of();
    private List<ScenarioLibrary.Suite> suiteList = List.of();
    private boolean busy;

    LibraryView(Session session, Consumer<Path> open, Navigator navigator) {
        this(() -> session, open, navigator);
    }

    LibraryView(java.util.function.Supplier<Session> session, Consumer<Path> open, Navigator navigator) {
        this.session = session;
        this.open = open;
        this.example = navigator.example(Navigator.Purpose.LIBRARY_OPEN);
        String place = "the generator_trunk folder of a " + Examples.QA_EDITION + " checkout (the folder that holds "
                + "intake/verified_examples.json)";
        this.guide = "How to start\n"
                + (example != null ? "1. Press \"Open the example library\": it opens " + example + ". Or press \"Open a library...\" and choose "
                + place + ".\n" : "1. Press \"Open a library...\" and choose " + place + ". Clone that checkout next to this one, and the "
                + "desktop finds it by itself next time.\n")
                + "2. Pick a group of examples on the left, then an example in the table.\n"
                + "3. Press \"Open in the Scenario tab\": the example becomes the scenario of step 3.\n";
        where.setText(example != null ? "No library open yet: press Open the example library, or choose another with Open a library..."
                : "No library open yet: choose " + place + " with Open a library...");
        JButton choose = Hints.of(new JButton("Open a library..."), "Choose a library: the generator_trunk folder of a QA edition checkout, "
                + "or the verified_examples.json file inside its intake folder.");
        choose.addActionListener(e -> {
            Path chosen = navigator.choose(panel, Navigator.Purpose.LIBRARY_OPEN, null);
            if (chosen != null) {
                open(chosen);
            }
        });
        navigator.bind(choose, () -> Navigator.Purpose.LIBRARY_OPEN, () -> null);
        openScenario = Hints.of(new JButton("Open in the Scenario tab"), "Use the selected example: it becomes the scenario of step 3, and the desktop switches there.");
        openScenario.addActionListener(e -> openSelected());
        check = Hints.of(new JButton("Check the candidate count"), "Count the test cases of the selected example and compare the number with the one the library expects.");
        check.addActionListener(e -> checkSelected());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        if (example != null) {
            JButton bundled = Hints.of(new JButton("Open the example library"), "Open the library of verified examples found next to this "
                    + "checkout.");
            bundled.setToolTipText(example.toString());
            bundled.addActionListener(e -> open(example));
            buttons.add(bundled);
        }
        buttons.add(choose);
        buttons.add(new AutoSearch(navigator).button(() -> FileSeeker.Kind.LIBRARY, () -> null, this::open,
                "Search this checkout, the checkouts next to it and your folders for libraries of examples."));
        buttons.add(openScenario);
        buttons.add(check);
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(buttons, BorderLayout.NORTH);
        where.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        top.add(where, BorderLayout.SOUTH);
        suites.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suites.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSuite(suites.getSelectedIndex());
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetails(table.getSelectedRow());
            offer();
        });
        offer();
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        details.setText(guide);
        JPanel suitesBox = new JPanel(new BorderLayout(4, 4));
        suitesBox.add(FieldNames.label("Groups of examples", suites), BorderLayout.NORTH);
        suitesBox.add(new JScrollPane(suites), BorderLayout.CENTER);
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePane, FieldNames.titled(details, "About the selected example"));
        right.setResizeWeight(0.7);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, suitesBox, right);
        split.setResizeWeight(0.25);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
    }

    JComponent component() {
        return panel;
    }

    /** Reads a generator tree's manifests, or one manifest (its tree is the directory above intake, else its own). */
    CompletableFuture<List<ScenarioLibrary.Entry>> open(Path chosen) {
        Path path = chosen.toAbsolutePath().normalize();
        where.setText("Reading " + path + " ...");
        return Background.run("library", () -> {
            if (Files.isDirectory(path)) {
                return ScenarioLibrary.load(path);
            }
            Path parent = path.getParent();
            Path root = parent.getFileName() != null && parent.getFileName().toString().equals("intake") ? parent.getParent() : parent;
            return ScenarioLibrary.load(root, List.of(path));
        }, read -> {
            entries = read;
            suiteList = ScenarioLibrary.suites(read);
            suiteNames.clear();
            for (ScenarioLibrary.Suite s : suiteList) {
                suiteNames.addElement(s.title() + " (" + ScenarioLibrary.inSuite(read, s.id()).size() + ")");
            }
            where.setText(read.isEmpty() ? path + ": no examples here. A library is the generator_trunk folder that holds "
                    + "intake/verified_examples.json." : path + ": " + read.size() + " scenario(s) in " + suiteList.size() + " suite(s)");
            if (!suiteList.isEmpty()) suites.setSelectedIndex(0);
            else showSuite(-1);
        }, failure -> where.setText("The library cannot be read: " + Background.describe(failure)));
    }

    /** What the scenarios table holds: the scenarios of the shown suite. */
    private static String scenariosTitle(String suite) {
        return (suite == null ? "Scenarios of the selected suite" : "Scenarios of suite " + suite) + ": documented counts and sources";
    }

    private void showSuite(int index) {
        scenarios.show(index < 0 ? List.of() : ScenarioLibrary.inSuite(entries, suiteList.get(index).id()));
        TableTitles.name(tablePane, table, scenariosTitle(index < 0 ? null : suiteList.get(index).title()));
        details.setText(index < 0 ? guide : "");
        if (scenarios.getRowCount() > 0) table.setRowSelectionInterval(0, 0);
    }

    private void showDetails(int row) {
        if (row < 0) {
            details.setText("");
            return;
        }
        ScenarioLibrary.Entry e = scenarios.row(row);
        StringBuilder text = new StringBuilder();
        text.append(e.title().isEmpty() ? e.id() : e.title()).append('\n');
        text.append("id ").append(e.id()).append(" · ").append(e.dynamic() ? "made fresh by its application (not carried), "
                + e.releaseGateCandidates() + " candidates" : "documented " + e.pass() + "/" + e.candidates() + " PASS");
        if (!e.analyzer().isEmpty()) text.append(" · goals ").append(e.analyzer());
        text.append("\n").append(e.sourceLabel()).append("\n\n").append(e.blurb()).append('\n');
        details.setText(text.toString());
        details.setCaretPosition(0);
    }

    /** A button that needs an example was pressed with none selected: say what to do first. */
    private void nothingSelected() {
        details.setText((entries.isEmpty() ? "Open a library first, then select an example in the table.\n\n"
                : "Select an example in the table first.\n\n") + (entries.isEmpty() ? guide : ""));
        details.setCaretPosition(0);
    }

    private JButton openScenario, check;

    /** Both need a selected example that is read, not made by its application; a count also needs none going. */
    private void offer() {
        ScenarioLibrary.Entry e = selected();
        boolean usable = e != null && !e.dynamic();
        openScenario.setEnabled(usable);
        check.setEnabled(usable && !busy);
    }

    private ScenarioLibrary.Entry selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : scenarios.row(row);
    }

    /** Puts the selected static scenario in the Scenario tab. */
    void openSelected() {
        ScenarioLibrary.Entry e = selected();
        if (e == null) {
            nothingSelected();
            return;
        }
        if (e.dynamic()) {
            details.append("\nThis scenario is made fresh by its application, which this edition does not carry: scenarios and specs are inputs, "
                    + "their applications are not ported.\n");
            details.setCaretPosition(details.getDocument().getLength());
            return;
        }
        open.accept(e.spec());
    }

    /** The plan's executable candidates of the selected scenario against the count the library documents. */
    CompletableFuture<String> checkSelected() {
        ScenarioLibrary.Entry e = selected();
        if (e == null) nothingSelected();
        if (e == null || e.dynamic() || busy) return CompletableFuture.completedFuture(null);
        busy = true;
        offer();
        details.append("\nChecking the candidate count of " + e.spec() + " ...\n");
        return Background.run("library-check", () -> {
            Session chosenSession = session.get();
            CandidatePreview preview = chosenSession.preview(e.spec(), new CandidatePreview.Request(BigInteger.valueOf(1_000_000), BigInteger.ZERO, 1,
                    true, true), Cancellation.NONE);
            ScenarioValidation.Summary summary = chosenSession.validate(e.spec());
            var config = chosenSession.configurationFor(e.spec()).config();
            boolean keepsDuplicates = !config.generationDistinctTables() || !config.assemblyDistinct();
            return check(e, stage(preview, "generated"), stage(preview, "executable"), keepsDuplicates ? growingPromotions(summary)
                    + grouped(summary) : 0);
        }, verdict -> {
            busy = false;
            offer();
            details.append(verdict + "\n");
            details.setCaretPosition(details.getDocument().getLength());
        }, failure -> {
            busy = false;
            offer();
            details.append("The scenario cannot be planned: " + Background.describe(failure) + "\n");
            details.setCaretPosition(details.getDocument().getLength());
        });
    }

    /**
     * The sheets whose single verb the plan repeats (the Core's auto-promotion) and that grow when repeated: every repeated
     * verb but FW_Combi(k), FW_Combi(size) and FW_Combi(), whose second pass gives each row back once.
     */
    static long growingPromotions(ScenarioValidation.Summary summary) {
        return summary.plan().sheets().stream().filter(SheetPlan::promoted).filter(s -> !s.directives().isEmpty())
                .map(s -> s.directives().getFirst().normalized())
                .filter(v -> !v.matches("FW_Combi(\\((\\d+|(?i:size))?\\))?")).count();
    }

    /** The sheets with an FW_Group pass, whose rows a de-duplicating legacy configuration also collapsed. */
    static long grouped(ScenarioValidation.Summary summary) {
        return summary.plan().sheets().stream().filter(s -> s.directives().stream().anyMatch(d -> d.normalized().startsWith("FW_Group")))
                .count();
    }

    private static Cardinality stage(CandidatePreview preview, String name) {
        return preview.estimates().candidates().stream().filter(s -> s.name().equals(name)).findFirst().map(PlanEstimates.Stage::count)
                .orElse(null);
    }

    /**
     * The comparison in words: the plan's count, how sure it is, and whether it equals the documented one; a different count
     * names the two known reasons a legacy run counted otherwise (handoff 0056). Legacy runs read the Core's shipped
     * fw.properties, which de-duplicated every pass and the final tables (distinctify__fwX_Y__Tables and
     * distinctify__fw_final_opt__Tables true), while this framework keeps the Core's code defaults (decision G7): a lone
     * FW_Subsets, FW_Permut(k), FW_PermutR(k) or FW_CombiR(k) row, which the Core's auto-promotion repeats, or an FW_Group
     * pass then gives more rows. And the legacy bundle applied a spec's constraints only with --sieve, where this framework
     * always applies them.
     *
     * @param duplicating the sheets that give duplicate rows while de-duplication is off (0 when it is on)
     */
    static String check(ScenarioLibrary.Entry e, Cardinality generated, Cardinality executable, long duplicating) {
        if (executable == null) return "The plan gives no executable count.";
        String documented = e.candidates().strip();
        String plan = "plan: " + executable.value() + " executable candidates (" + executable.mode() + ")";
        if (!documented.matches("\\d+")) return plan + "; the library documents no count (" + (documented.isEmpty() ? "none" : documented) + ")";
        boolean equal = executable.value() != null && executable.value().equals(new BigInteger(documented));
        StringBuilder text = new StringBuilder(plan).append("; the library documents ").append(documented).append(": ")
                .append(equal ? "equal" : "different");
        if (!equal && duplicating > 0) {
            text.append("; ").append(duplicating).append(" sheet(s) give duplicate rows that legacy runs removed: their fw.properties "
                    + "de-duplicated every pass and the final tables (here cf.generation.distinctTables=true and cf.assembly.distinct=true)");
        }
        if (!equal && generated != null && generated.value() != null && executable.value() != null
                && generated.value().compareTo(executable.value()) > 0) {
            text.append("; the scenario's rules remove ").append(generated.value().subtract(executable.value()))
                    .append(" candidate(s), which a legacy run kept unless it was started with --sieve");
        }
        return text.toString();
    }

    // ---- for tests, on the event thread ------------------------------------------------------------------------------

    void selectSuite(int index) {
        suites.setSelectedIndex(index);
    }

    void selectScenario(int row) {
        table.setRowSelectionInterval(row, row);
    }

    ListTableModel<ScenarioLibrary.Entry> scenarios() {
        return scenarios;
    }

    String details() {
        return details.getText();
    }

    String where() {
        return where.getText();
    }

    List<String> suiteNames() {
        return java.util.Collections.list(suiteNames.elements());
    }
}
