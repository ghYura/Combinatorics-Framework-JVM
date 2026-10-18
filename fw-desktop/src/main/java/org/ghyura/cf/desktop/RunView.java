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

import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.desktop.session.Session;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The run tab: run the chosen scenario under the session's configuration, pause, resume and cancel it, watch its state
 * and committed samples (polled four times a second from the latest progress, never queued), read its report and export it
 * as the export command does (report, results, csv), as a checkpoint for resume, or as the metrics corpus step 8 analyzes.
 */
final class RunView {

    private final RunController controller;
    private final Supplier<Path> scenario;
    private final Navigator navigator;
    private final JButton run = Hints.of(new JButton("Run"), "Run all test cases of the scenario of step 3.");
    private final JButton pause = Hints.of(new JButton("Pause"), "Pause the run; test cases already started finish first.");
    private final JButton resume = Hints.of(new JButton("Resume"), "Continue a paused run.");
    private final JButton cancel = Hints.of(new JButton("Cancel"), "Stop the run. The results so far are kept.");
    /** What each export kind is, as the list shows it. */
    private static final Map<String, String> KINDS = Map.of("report", "summary report (.xml)", "results", "every attempt (.csv)",
            "csv", "measurements table (.csv)", "metrics", "metrics for step 8 (.kv)", "checkpoint", "checkpoint to resume (.checkpoint)");

    private final JComboBox<String> exportKind = Hints.of(new JComboBox<>(new String[]{"report", "results", "csv", "metrics", "checkpoint"}),
            "What to save: a summary report, every result row, a table of measurements (CSV), the metrics that step 8 analyzes, or a "
            + "checkpoint to continue the run later.");
    private final JButton export = Hints.of(new JButton("Export..."), "Save the chosen kind of results to a file.");
    private final JLabel status = new JLabel("No run yet.");
    private final JTextArea output = Hints.of(new JTextArea(), "Progress and results of the run: how many test cases passed, failed or could not run, and their result codes. Filled while a run goes and when it ends (press Run).");
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private final Timer timer = new Timer(250, e -> refresh());
    private String exported = "";
    private boolean busy;
    private boolean closing;
    private CompletableFuture<Void> closingTask;

    RunView(Session session, Supplier<Path> scenario, Navigator navigator) {
        this(() -> session, scenario, navigator);
    }

    RunView(java.util.function.Supplier<Session> session, Supplier<Path> scenario, Navigator navigator) {
        this.navigator = navigator;
        this.controller = new RunController(session);
        this.scenario = scenario;
        run.addActionListener(e -> start());
        pause.addActionListener(e -> controller.pause());
        resume.addActionListener(e -> controller.resume());
        cancel.addActionListener(e -> controller.cancel());
        export.addActionListener(e -> {
            String kind = (String) exportKind.getSelectedItem();
            Path chosen = navigator.choose(panel, Navigator.Purpose.export(kind), null);
            if (chosen != null) {
                export(kind, chosen);
            }
        });
        navigator.bind(export, () -> Navigator.Purpose.export((String) exportKind.getSelectedItem()), () -> null);
        exportKind.addActionListener(e -> navigator.refreshHints());
        ListCellRenderer<? super String> plain = exportKind.getRenderer();
        exportKind.setRenderer((list, kind, index, selected, focus) ->
                plain.getListCellRendererComponent(list, KINDS.getOrDefault(kind, kind), index, selected, focus));
        JPanel buttons = new JPanel();
        for (JButton b : new JButton[]{run, pause, resume, cancel}) {
            buttons.add(b);
        }
        buttons.add(FieldNames.label("Save as:", exportKind));
        buttons.add(exportKind);
        buttons.add(export);
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(buttons, BorderLayout.WEST);
        top.add(status, BorderLayout.CENTER);
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(FieldNames.titled(output, "Run report"), BorderLayout.CENTER);
        refresh();
        timer.start();
    }

    JComponent component() {
        return panel;
    }

    RunController controller() {
        return controller;
    }

    String status() {
        return status.getText();
    }

    String output() {
        return output.getText();
    }

    /** Starts a run of the scenario chosen on the scenario tab; a refusal is shown, not thrown. */
    CompletableFuture<Void> start() {
        if (busy || closing) return CompletableFuture.completedFuture(null);
        Path file = scenario.get();
        if (file == null) {
            output.setText("Choose a scenario in step 3 first, or send an example there from step 1 · Library.\n");
            return CompletableFuture.completedFuture(null);
        }
        exported = "";
        busy = true;
        output.setText("Preparing " + file + " ...\n");
        refresh();
        return Background.run("prepare-run", () -> {
            controller.start(file);
            return null;
        }, ignored -> {
            busy = false;
            output.setText("Running " + file + " ...\n");
            refresh();
        }, failure -> {
            busy = false;
            output.setText("The run could not start:\n" + Background.describe(failure));
            refresh();
        });
    }

    /** Exports the ended run (report, results, csv or checkpoint); the outcome is shown in the status. */
    CompletableFuture<Path> export(String kind, Path target) {
        if (busy || closing) return CompletableFuture.completedFuture(null);
        busy = true;
        refresh();
        return Background.run("export-run", () -> {
            controller.export(kind, target);
            return target;
        }, written -> {
            busy = false;
            exported = "exported the run's " + kind + " to " + target + (kind.equals("metrics") ? "; study it in step 8 · Results analysis" : "");
            navigator.trail().record(target);
            refresh();
        }, failure -> {
            busy = false;
            exported = Background.describe(failure).strip();
            refresh();
        });
    }

    /** Shows the latest state; enables what the state allows. */
    void refresh() {
        RunController.View v = controller.view();
        RunState s = v.state();
        boolean going = s != null && !s.terminal();
        status.setText((s == null ? "No run yet." : "  " + s + ", " + v.committed() + " samples committed")
                + (exported.isEmpty() ? "" : "; " + exported));
        run.setEnabled(!busy && !closing && !going);
        pause.setEnabled(!closing && s == RunState.RUNNING);
        resume.setEnabled(!closing && (s == RunState.PAUSED || s == RunState.PAUSING));
        cancel.setEnabled(!closing && going);
        export.setEnabled(!busy && !closing && s != null && s.terminal());
        if (!v.report().isEmpty() && !output.getText().equals(v.report())) {
            output.setText(v.report());
        }
    }

    CompletableFuture<Void> close() {
        if (closingTask != null) return closingTask;
        closing = true;
        timer.stop();
        refresh();
        closingTask = Background.run("close-run", () -> {
            controller.close();
            return null;
        }, ignored -> { }, failure -> output.setText(Background.describe(failure)));
        return closingTask;
    }
}
