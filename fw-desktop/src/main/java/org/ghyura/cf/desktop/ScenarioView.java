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

import org.ghyura.cf.desktop.session.FileSeeker;
import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.ScenarioValidation;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.Diagnostic;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** The scenario tab: choose a scenario file and validate it (read, interpret, plan and key it) as {@code validate} does; its plan as a table. */
final class ScenarioView {

    private final java.util.function.Supplier<Session> session;
    private final Navigator navigator;
    private final JTextField file = Hints.of(new JTextField(), "The scenario file the next steps work on. Filled when you choose a file with Browse... or send an example from step 1; you can also type its path.");
    private final JTextArea output = Hints.of(new JTextArea(), "What the last check found: problems to fix, and a summary of how the scenario is built. Filled after you press Validate and plan.");
    private final JButton validate = Hints.of(new JButton("Validate and plan"), "Check the scenario for mistakes and work out how its sheets are combined. Problems appear above, the plan in the table below.");
    private final PlanTableModel plan = new PlanTableModel();
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));

    ScenarioView(Session session, Path initial, Navigator navigator) {
        this(() -> session, initial, navigator);
    }

    ScenarioView(java.util.function.Supplier<Session> session, Path initial, Navigator navigator) {
        this.session = session;
        this.navigator = navigator;
        if (initial != null) {
            file.setText(initial.toString());
            navigator.trail().record(initial);
        }
        JButton browse = Hints.of(new JButton("Browse..."), "Choose the scenario file from a folder.");
        browse.addActionListener(e -> {
            Path chosen = navigator.choose(panel, Navigator.Purpose.SCENARIO_OPEN, current());
            if (chosen != null) {
                file.setText(chosen.toString());
                String problem = notAScenario(chosen);
                if (problem != null) output.setText(problem);
            }
        });
        navigator.bind(browse, () -> Navigator.Purpose.SCENARIO_OPEN, this::current);
        validate.addActionListener(e -> validate());
        file.getDocument().addDocumentListener(FormulaEditor.listener(this::offer));
        offer();
        Path example = navigator.example(Navigator.Purpose.SCENARIO_OPEN);
        FieldNames.placeholder(file, "for example " + (example == null ? "shop.xlsx" : shortest(example))
                + "; or send an example here from step 1");
        JPanel row = new JPanel(new BorderLayout(6, 6));
        row.add(FieldNames.label("Scenario file (xlsx, json, yaml or toml):", file), BorderLayout.WEST);
        row.add(file, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        buttons.add(browse);
        buttons.add(new AutoSearch(navigator).button(() -> FileSeeker.Kind.SCENARIO, this::current, this::setScenario,
                "Search the folders where scenario files usually are, then pick one from the list it shows."));
        if (example != null) {
            JButton use = Hints.of(new JButton("Use the example"), "Put the showcase example scenario that comes with the framework into the box.");
            use.setToolTipText(example.toString());
            use.addActionListener(e -> setScenario(example));
            buttons.add(use);
        }
        buttons.add(validate);
        row.add(buttons, BorderLayout.EAST);
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel under = new JPanel(new BorderLayout(4, 0));
        under.add(FieldNames.note("A scenario is a workbook (.xlsx) whose sheets list the values to combine, or the same sheets as "
                + ".json, .yaml or .toml. Its rules (.constraints.json) and settings (.properties) beside it are found by themselves."),
                BorderLayout.NORTH);
        under.add(navigator.hint(file, Navigator.Purpose.SCENARIO_OPEN), BorderLayout.SOUTH);
        JPanel north = new JPanel(new BorderLayout(4, 0));
        north.add(row, BorderLayout.NORTH);
        north.add(under, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, FieldNames.titled(output, "Check results"), TableTitles.titled(Hints.of(new JTable(plan), "One row per sheet of the scenario: whether its values are always used, optional or only helpers for other sheets, how many values it has, and the instructions (from the FW_Seq sheet) that combine them."), "FW_Seq plan: each sheet's role, values and directives"));
        split.setResizeWeight(0.6);
        panel.add(split, BorderLayout.CENTER);
    }

    /** The plan table after the last validation. */
    PlanTableModel plan() {
        return plan;
    }

    JComponent component() {
        return panel;
    }

    private boolean validating;

    /** Validate and plan needs a scenario file named in the box, and no validation going. */
    private void offer() {
        validate.setEnabled(!validating && current() != null);
    }

    /** Runs whenever the box names another scenario: the steps that work on it follow at once. */
    void onChange(Runnable listener) {
        file.getDocument().addDocumentListener(FormulaEditor.listener(listener));
    }

    /** The chosen scenario, or null. */
    Path scenario() {
        String text = file.getText().trim();
        return text.isEmpty() ? null : Path.of(text);
    }

    void setScenario(Path scenario) {
        file.setText(scenario.toString());
        navigator.trail().record(scenario);
    }

    /** The scenario the field names, or null when it names none (blank or not a path). */
    private Path current() {
        try {
            return scenario();
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    String output() {
        return output.getText();
    }

    /**
     * Why a file is not a scenario, in plain words with what to choose instead; null when it may be one (a scenario, a file
     * of no known kind, or a missing file, which validation reports).
     */
    static String notAScenario(Path file) {
        if (Files.isDirectory(file)) {
            return file + " is a folder. Choose a scenario file inside it: a workbook (.xlsx), or a .json, .yaml or .toml scenario.\n";
        }
        FileTrail.Kind kind = Files.isRegularFile(file) ? FileTrail.classify(file) : null;
        if (kind == null || kind == FileTrail.Kind.SCENARIO) {
            return null;
        }
        String name = file.getFileName().toString();
        return switch (kind) {
            case CONSTRAINTS -> name + " holds the rules (constraints) of a scenario, not the scenario. Choose the scenario itself"
                    + beside(file) + "; its rules are found by themselves.\n";
            case SETTINGS -> name + " holds the settings of a scenario, not the scenario. Choose the scenario itself" + beside(file)
                    + "; its settings are found by themselves.\n";
            case LIBRARY -> name + " lists the examples of a library. Open it in step 1 · Library and send an example here.\n";
            case METRICS, RESULTS, REPORT, CHECKPOINT, PAGE -> name + " holds the results of a run, not a scenario. Study results in "
                    + "step 8 · Results analysis.\n";
            case TEXT, RULES, SCORES -> name + " belongs to the text scorer of step 9, not to a scenario.\n";
            default -> name + " is not a scenario. Choose a workbook (.xlsx), or a .json, .yaml or .toml scenario.\n";
        };
    }

    /** " (name in the same folder)" for the scenario a rules or settings file belongs to, when it is there; else "". */
    private static String beside(Path file) {
        String name = file.getFileName().toString();
        String stem = name.substring(0, name.indexOf('.') > 0 ? name.indexOf('.') : name.length());
        for (String extension : new String[]{"xlsx", "json", "yaml", "yml", "toml"}) {
            Path scenario = file.resolveSibling(stem + "." + extension);
            if (Files.isRegularFile(scenario) && FileTrail.classify(scenario) == FileTrail.Kind.SCENARIO) {
                return " (" + scenario.getFileName() + " in the same folder)";
            }
        }
        return "";
    }

    /** The path relative to the working folder when that is shorter, for an example shown in a box. */
    private static String shortest(Path path) {
        try {
            Path relative = Path.of("").toAbsolutePath().relativize(path.toAbsolutePath());
            return relative.toString().length() < path.toString().length() ? relative.toString() : path.toString();
        } catch (IllegalArgumentException e) {
            return path.toString();
        }
    }

    /** Whether the output shows its first line (a long summary is read from the top). */
    boolean outputAtTop() {
        return output.getCaretPosition() == 0;
    }

    /** Validates the chosen scenario off the event thread; the summary or the findings replace the output. */
    CompletableFuture<ScenarioValidation.Summary> validate() {
        Path scenario = scenario();
        if (scenario == null) {
            output.setText("Choose a scenario file first: press Browse..., or pick an example in step 1 · Library.\n");
            return CompletableFuture.completedFuture(null);
        }
        String problem = notAScenario(scenario);
        if (problem != null) {
            plan.show(null);
            output.setText(problem);
            return CompletableFuture.completedFuture(null);
        }
        validating = true;
        offer();
        output.setText("Validating " + scenario + " ...\n");
        navigator.trail().record(scenario);
        return Background.run("validate", () -> session.get().validate(scenario), summary -> {
            StringBuilder text = new StringBuilder(summary.toText());
            for (Diagnostic d : summary.findings()) {
                text.append(d).append('\n');
            }
            output.setText(text.toString());
            output.setCaretPosition(0);
            plan.show(summary.plan());
            validating = false;
            offer();
        }, failure -> {
            plan.show(null);
            output.setText("The scenario is not valid:\n" + Background.describe(failure));
            output.setCaretPosition(0);
            validating = false;
            offer();
        });
    }
}
