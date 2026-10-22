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

import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JTabbedPane;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * The desktop's content: the scenario (validate and plan), its candidates and sieve (bounded pages with their fate),
 * authoring (edit, validate unsaved edits, save, the dependency graph), constraints (bond matrix, graph, rule parts), its run
 * (start, pause, resume, cancel, progress and report), campaigns (iteration progress and lineage), results analysis, the
 * textual scorer and the scenario library, all over one session; the FlatLaf desktop adds the wizard that composes a first
 * scenario (step 0). It holds no window, so it is built and driven headless in tests.
 */
final class Desktop {

    private final RunnerSettings runners;
    private final ScenarioView scenario;
    private final CandidatesView candidates;
    private final AuthoringView authoring;
    private final ConstraintsView constraints;
    private final SieveWorkbench sieve;
    private final RunView run;
    private final CampaignView campaign;
    private final AnalysisView analysis;
    private final ScorerView scorer;
    private final LibraryView library;
    private final WizardView wizard;
    private final JTabbedPane tabs = new JTabbedPane();
    private final HintLayer hints = new HintLayer();
    private final JLayer<JComponent> layer;
    private final JToggleButton hintsButton = Hints.of(new JToggleButton("Hints on", true), "Shows or hides the pop-up hints that explain each part "
            + "of the desktop, tooltips included. The F1 key does the same.");
    private final JButton aiDelegationButton;
    private final JButton creditsButton;
    private final FileTrail trail;
    private final Navigator navigator;
    private CompletableFuture<Void> closing;

    /** A desktop whose file trail lasts this session only (tests; the window's launcher remembers it instead). */
    Desktop(Session session, Path scenarioFile) {
        this(session, scenarioFile, FileTrail.inMemory());
    }

    /**
     * @param trail where the session's files are: every file button opens through it, at the files it knows; it watches
     *              the working folder and every folder the session meets, and closes with the desktop
     */
    Desktop(Session session, Path scenarioFile, FileTrail trail) {
        this(session, scenarioFile, trail, false);
    }

    /** The FlatLaf GUI shares the workflows and offers the integrated Sieve workbench. */
    Desktop(Session session, Path scenarioFile, FileTrail trail, boolean flatWorkbench) {
        this.trail = trail;
        trail.watch(Path.of("").toAbsolutePath());
        this.navigator = new Navigator(trail, this::currentScenario);
        runners = RunnerSettings.withChoosers(session, navigator);
        scenario = new ScenarioView(runners::session, scenarioFile, navigator);
        candidates = new CandidatesView(runners::session, scenario::scenario);
        authoring = new AuthoringView(runners::session, scenario::setScenario, navigator);
        constraints = flatWorkbench ? null : new ConstraintsView(runners::session, scenario::scenario);
        sieve = flatWorkbench ? new SieveWorkbench(runners::session, scenario::scenario, navigator) : null;
        run = new RunView(runners::session, scenario::scenario, navigator);
        campaign = new CampaignView(runners::session, scenario::scenario);
        // the steps that work on the step 3 scenario follow it at once
        scenario.onChange(candidates::offer);
        scenario.onChange(run::refresh);
        scenario.onChange(campaign::refresh);
        if (sieve != null) scenario.onChange(sieve::offer);
        analysis = new AnalysisView(session, navigator);
        scorer = new ScorerView(session, navigator, hintsButton);
        library = new LibraryView(runners::session, chosen -> {
            scenario.setScenario(chosen);
            tabs.setSelectedComponent(scenario.component());
        }, navigator);
        // the wizard hands its saved scenario on: to step 3 with its covering selection, then to step 5 or step 2
        wizard = flatWorkbench ? new WizardView(runners::session, navigator, new WizardView.Handoff() {
            @Override
            public String use(Path chosen, java.util.Map<String, String> selection) {
                runners.select(selection);
                scenario.setScenario(chosen);
                tabs.setSelectedComponent(scenario.component());
                return runners.status();
            }

            @Override
            public void testCases() {
                tabs.setSelectedComponent(candidates.component());
                candidates.preview();
            }

            @Override
            public void edit(Path chosen) {
                authoring.open(chosen);
                tabs.setSelectedComponent(authoring.component());
            }
        }) : null;
        Runnable runnerSettings = () -> tabs.setSelectedComponent(scenario.component());
        RunnerSettings.attach(scenario.component(), runners.component());
        RunnerSettings.attach(run.component(), runners.compact(runnerSettings, () -> { }));
        RunnerSettings.attach(library.component(), runners.compact(runnerSettings, library::openSelected));
        // the steps in the order a user works through them: find or write a scenario, open it, constrain it, look at its test
        // cases, run them, iterate, then study the results
        Steps.add(tabs, "1", "Library", library.component(), "Step 1 · Library: ready-made example scenarios that are known to work. "
                + "Open a library folder, pick a group and an example, then send it to step 3 to use it.");
        Steps.add(tabs, "2", "Authoring", authoring.component(), "Step 2 · Authoring: write a new scenario or change one: its sheets "
                + "(lists of values), its special control sheets, then check it. Saving makes it the scenario of step 3.");
        Steps.add(tabs, "3", "Scenario", scenario.component(), "Step 3 · Scenario: choose the scenario file the next steps work on, "
                + "check it, and see how its sheets will be combined into test cases.");
        Steps.add(tabs, "4", flatWorkbench ? "Constraints / Sieve" : "Constraints",
                flatWorkbench ? sieve.component() : constraints.component(),
                flatWorkbench ? "Step 4 · Constraints / Sieve (Face 2): draw or edit rules, inspect their impact and save the constraints beside the scenario."
                : "Step 4 · Constraints: rules that remove or require combinations of "
                + "values, for example two values that must never appear together. Draw or write them, see how many test cases they "
                + "remove, and save them.");
        Steps.add(tabs, "5", "Candidates", candidates.component(), "Step 5 · Candidates: the test cases the scenario makes, before you "
                + "run them: how many there are, how many each rule removes, and the code of each one.");
        Steps.add(tabs, "6", "Run", run.component(), "Step 6 · Run: run every test case of the scenario and watch how many pass or fail; "
                + "pause, continue or stop, then save the results.");
        Steps.add(tabs, "7", "Campaign", campaign.component(), "Step 7 · Campaign: run the scenario in several rounds, where the best "
                + "results of one round steer the next; the table shows every round.");
        Steps.add(tabs, "8", "Results analysis", analysis.component(), "Step 8 · Results analysis: open saved results to compare their "
                + "measurements, find the best combinations and see how much of the code the tests reached.");
        Steps.add(tabs, "9", "Text scorer", scorer.component(), "Step 9 · Text scorer: give every line of a text, such as a log, a "
                + "score from rules you write or that are learned, to find the lines worth a closer look.");
        if (flatWorkbench) {
            // after step 9, as the keys 1 to 9 and then 0 lie on a keyboard
            Steps.add(tabs, "0", "Wizard", wizard.component(), "Step 0 · Wizard: compose your first scenario by answering questions: "
                    + "what varies, how one test case uses it, what must never or must always come together, and how many test cases "
                    + "are enough. It shows the instructions that fit and their counts, then saves the scenario for the other steps.");
            tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        }
        if (scenarioFile != null) {
            tabs.setSelectedComponent(scenario.component());
        }
        // every table's columns fit their names and data, and fit again whenever the data changes
        ColumnFit.installAll(tabs);
        // an opaque pop-up over the desktop says what the element the pointer rests on is for; its own button (or F1) turns it off.
        // In guif, AI_delegation stands before it (the agreement for handing work to an AI assistant, then its manual's path)
        // and Credits, last, after it (the author, in his own words)
        aiDelegationButton = flatWorkbench ? new AiDelegationDialog(AiDelegation.of(trail, navigator.examples()), hintsButton).button() : null;
        creditsButton = flatWorkbench ? new Credits(hintsButton).button() : null;
        JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 2));
        if (aiDelegationButton != null) bar.add(aiDelegationButton);
        bar.add(hintsButton);
        if (creditsButton != null) bar.add(creditsButton);
        JPanel root = new JPanel(new java.awt.BorderLayout());
        root.add(bar, java.awt.BorderLayout.NORTH);
        root.add(tabs, java.awt.BorderLayout.CENTER);
        layer = new JLayer<>(root, hints);
        hints.control(hintsButton);
        hintsButton.addItemListener(e -> {
            boolean on = hintsButton.isSelected();
            hintsButton.setText(on ? "Hints on" : "Hints off");
            hints.showHints(layer, on);
        });
    }

    /** The desktop as the window shows it: the steps under the hints layer. */
    JComponent component() {
        return layer;
    }

    /** The steps themselves. */
    JTabbedPane tabs() {
        return tabs;
    }

    HintLayer hints() {
        return hints;
    }

    /** The AI_delegation button beside Hints, or null in the Swing desktop. */
    JButton aiDelegationButton() {
        return aiDelegationButton;
    }

    /** The Credits button, last in the row after Hints, or null in the Swing desktop. */
    JButton creditsButton() {
        return creditsButton;
    }

    /** The button that turns the hints on and off. */
    JToggleButton hintsButton() {
        return hintsButton;
    }

    RunnerSettings runners() {
        return runners;
    }

    ScenarioView scenario() {
        return scenario;
    }

    CandidatesView candidates() {
        return candidates;
    }

    AuthoringView authoring() {
        return authoring;
    }

    ConstraintsView constraints() {
        return constraints;
    }

    SieveWorkbench sieve() { return sieve; }

    boolean allowClose() { return sieve == null || sieve.allowClose(); }

    RunView run() {
        return run;
    }

    CampaignView campaign() {
        return campaign;
    }

    AnalysisView analysis() {
        return analysis;
    }

    ScorerView scorer() {
        return scorer;
    }

    LibraryView library() {
        return library;
    }

    /** The wizard of step 0, or null in the Swing desktop. */
    WizardView wizard() {
        return wizard;
    }

    /** The scenario the scenario tab names, or null (none yet, or text that names no path). */
    private Path currentScenario() {
        try {
            return scenario == null ? null : scenario.scenario();
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    Navigator navigator() {
        return navigator;
    }

    /** Cancels and releases previews, a run and a campaign still going, stops the progress timers and the file trail. */
    CompletableFuture<Void> close() {
        if (closing == null) {
            var work = CompletableFuture.allOf(candidates.close(), run.close(), campaign.close(),
                    sieve == null ? CompletableFuture.completedFuture(null) : sieve.close());
            var files = work.handle((ignored, failure) -> null).thenCompose(ignored ->
                    Background.run("close-file-trail", () -> { trail.close(); return null; }, value -> { }, failure -> { }));
            closing = CompletableFuture.allOf(work, files);
        }
        return closing;
    }
}
