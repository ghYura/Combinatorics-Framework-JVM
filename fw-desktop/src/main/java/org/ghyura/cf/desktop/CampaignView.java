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

import org.ghyura.cf.campaign.Campaign;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.desktop.session.Session;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The campaign tab (AN-13; the legacy run UI's iterate follower): run the chosen scenario as a campaign under the
 * session's cf.campaign.* settings, pause, resume and cancel it, and watch the running iteration's state and committed
 * samples (polled four times a second) and each iteration's lineage entry (run id, state, counts, winners, front size,
 * stable transitions, derivation) as it is recorded; the final lineage and findings follow. Work stays off the event
 * thread. A campaign ends FAILED when an iteration uses up its error budget (decision M09-CAMPAIGN-ERRORS).
 */
final class CampaignView {

    private final CampaignController controller;
    private final Supplier<Path> scenario;
    private final JButton start = Hints.of(new JButton("Run campaign"), "Start a campaign: several runs of the scenario of step 3, each steered by the best results of the one before.");
    private final JButton pause = Hints.of(new JButton("Pause"), "Pause the campaign after the current step.");
    private final JButton resume = Hints.of(new JButton("Resume"), "Continue a paused campaign.");
    private final JButton cancel = Hints.of(new JButton("Cancel"), "Stop the campaign. The rounds so far are kept.");
    private final JLabel status = new JLabel("No campaign yet.");
    private final ListTableModel<Campaign.Iteration> lineage = new ListTableModel<>(List.of("#", "Run id", "State", "Generated", "Executable",
            "Samples", "Winners", "Front", "Stable", "Derivation"), List.<Function<Campaign.Iteration, Object>>of(Campaign.Iteration::index,
            Campaign.Iteration::runId, Campaign.Iteration::state, Campaign.Iteration::generated, Campaign.Iteration::executable,
            Campaign.Iteration::samples, Campaign.Iteration::winners, it -> it.frontIds().size(), Campaign.Iteration::stableTransitions,
            Campaign.Iteration::derivation));
    private final JTextArea output = Hints.of(new JTextArea(), "What the campaign reports while it runs. Filled while a campaign runs (press Run campaign).");
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private final Timer timer = new Timer(250, e -> refresh());
    private boolean busy;
    private boolean closing;
    private String preparationFailure;
    private CompletableFuture<Void> closingTask;

    CampaignView(Session session, Supplier<Path> scenario) {
        this(() -> session, scenario);
    }

    CampaignView(java.util.function.Supplier<Session> session, Supplier<Path> scenario) {
        this.controller = new CampaignController(session);
        this.scenario = scenario;
        start.addActionListener(e -> start());
        pause.addActionListener(e -> controller.pause());
        resume.addActionListener(e -> controller.resume());
        cancel.addActionListener(e -> controller.cancel());
        JPanel buttons = new JPanel();
        for (JButton b : new JButton[]{start, pause, resume, cancel}) {
            buttons.add(b);
        }
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(buttons, BorderLayout.WEST);
        top.add(status, BorderLayout.CENTER);
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, TableTitles.titled(Hints.of(new JTable(lineage), "One row per round: its run, how many test cases it made and ran, its best results, and whether they have stopped improving."), "Campaign lineage: each iteration's run, counts, winners and front"), FieldNames.titled(output, "Campaign report"));
        split.setResizeWeight(0.5);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        refresh();
        timer.start();
    }

    JComponent component() {
        return panel;
    }

    CampaignController controller() {
        return controller;
    }

    String status() {
        return status.getText();
    }

    String output() {
        return output.getText();
    }

    ListTableModel<Campaign.Iteration> lineage() {
        return lineage;
    }

    /** Starts a campaign of the scenario chosen on the scenario tab; a refusal is shown, not thrown. */
    CompletableFuture<Void> start() {
        if (busy || closing) return CompletableFuture.completedFuture(null);
        Path file = scenario.get();
        if (file == null) {
            output.setText("Choose a scenario in step 3 first, or send an example there from step 1 · Library.\n");
            return CompletableFuture.completedFuture(null);
        }
        busy = true;
        preparationFailure = null;
        output.setText("Preparing a campaign of " + file + " ...\n");
        refresh();
        return Background.run("prepare-campaign", () -> {
            controller.start(file);
            return null;
        }, ignored -> {
            busy = false;
            output.setText("Running a campaign of " + file + " ...\n");
            refresh();
        }, failure -> {
            busy = false;
            preparationFailure = "The campaign could not start:\n" + Background.describe(failure);
            refresh();
        });
    }

    /** Whether step 3 names a scenario to run. */
    private boolean chosen() {
        try { return scenario.get() != null; } catch (java.nio.file.InvalidPathException e) { return false; }
    }

    /** Shows the latest state; enables what the state allows. */
    void refresh() {
        CampaignController.View v = controller.view();
        RunState s = v.state();
        if (preparationFailure != null) {
            status.setText("The campaign could not start.");
        } else if (busy) {
            status.setText("Preparing ...");
        } else if (v.iteration() == 0 && !v.going() && v.report().isEmpty()) {
            status.setText(busy ? "Preparing ..." : "No campaign yet.");
        } else if (v.going()) {
            status.setText("  iteration " + v.iteration() + " of " + v.requested() + " (" + v.strategy() + ")" + (s == null ? "" : ", " + s)
                    + ", " + v.committed() + " samples committed");
        } else {
            // the campaign's own end, not the idle state its last run left behind
            status.setText("  campaign " + (v.outcome() == null ? "failed to run" : v.outcome()) + " after " + v.iterations().size() + " of "
                    + v.requested() + " iterations (" + v.strategy() + ")");
        }
        if (!lineage.rows().equals(v.iterations())) {
            lineage.show(v.iterations());
        }
        start.setEnabled(!busy && !closing && !v.going() && chosen());
        pause.setEnabled(!closing && v.going() && s == RunState.RUNNING);
        resume.setEnabled(!closing && v.going() && (s == RunState.PAUSED || s == RunState.PAUSING));
        cancel.setEnabled(!closing && v.going());
        if (preparationFailure != null) {
            output.setText(preparationFailure);
        } else if (!busy && !v.report().isEmpty() && !output.getText().equals(v.report())) {
            output.setText(v.report());
        }
    }

    CompletableFuture<Void> close() {
        if (closingTask != null) return closingTask;
        closing = true;
        timer.stop();
        refresh();
        closingTask = Background.run("close-campaign", () -> {
            controller.close();
            return null;
        }, ignored -> { }, failure -> output.setText(Background.describe(failure)));
        return closingTask;
    }
}
