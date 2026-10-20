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
import org.ghyura.cf.desktop.session.PlanEstimates;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.spi.CancellationSource;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The candidates tab (AN-13; the legacy run UI's plan panel and the sieve's dry run): the chosen scenario's plan counts
 * with how sure each is, every rule with what it removes, and pages of generated candidates in canonical order with
 * their fate (kept, removed by named rules, left out by covering selection) and source. Every page is read off the event
 * thread by the shared session, reading at most the scan limit of candidates, and can be cancelled.
 */
final class CandidatesView {

    private final java.util.function.Supplier<Session> session;
    private final Supplier<Path> scenario;
    private final JTextField scan = Hints.of(new JTextField("1000000", 9), "The most test cases the preview looks at. Filled with 1000000 at the start; type a smaller number for a faster answer on a very large scenario.");
    private final JSpinner size = Hints.of(new JSpinner(new SpinnerNumberModel(50, 1, CandidatePreview.MAX_PAGE, 10)), "How many test cases one page shows.");
    private final JCheckBox keptOnly = Hints.of(new JCheckBox("Executable only", true), "Show only the test cases that will really run, not the ones a rule removes.");
    private final JButton preview = Hints.of(new JButton("Preview"), "Count the test cases of the scenario of step 3 and show the first page.");
    private final JButton next = Hints.of(new JButton("Next page"), "Show the next page of test cases.");
    private final JButton cancel = Hints.of(new JButton("Cancel"), "Stop the preview while it is still counting.");
    private final JLabel status = new JLabel("No preview yet.");
    private final ListTableModel<PlanEstimates.Stage> estimates = new ListTableModel<>(List.of("Count", "Confidence", "Figure", "Formula"),
            List.<Function<PlanEstimates.Stage, Object>>of(PlanEstimates.Stage::name, s -> s.count().mode(), s -> s.count().figure(),
                    s -> s.count().formula()));
    private final ListTableModel<CandidatePreview.Rule> rules = new ListTableModel<>(List.of("Rule", "Reads", "Removes"),
            List.<Function<CandidatePreview.Rule, Object>>of(CandidatePreview.Rule::name, r -> r.early() ? "mandatory sheets (early)"
                    : "optional sheets (late)", r -> r.removed() == null ? "not counted within the scan" : r.removed()));
    private final ListTableModel<CandidatePreview.Row> rows = new ListTableModel<>(List.of("Ordinal", "Display id", "Id", "Fate", "Rules"),
            List.<Function<CandidatePreview.Row, Object>>of(CandidatePreview.Row::ordinal, CandidatePreview.Row::display,
                    r -> r.id().substring(0, 12), CandidatePreview.Row::status, r -> String.join(", ", r.rules())));
    private final JTable rowTable = Hints.of(new JTable(rows), "The test cases of this page: their number, their names, and whether they run or a rule removes them. Select one to see its code on the right.");
    private final CandidateCodeView source = new CandidateCodeView();
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private CancellationSource running;
    private BigInteger nextFirst;
    private Path shown;
    private boolean closing;
    private CompletableFuture<CandidatePreview> pending = CompletableFuture.completedFuture(null);

    CandidatesView(Session session, Supplier<Path> scenario) {
        this(() -> session, scenario);
    }

    CandidatesView(java.util.function.Supplier<Session> session, Supplier<Path> scenario) {
        this.session = session;
        this.scenario = scenario;
        preview.addActionListener(e -> preview());
        next.addActionListener(e -> nextPage());
        cancel.addActionListener(e -> cancel());
        offer();
        rowTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rowTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int selected = rowTable.getSelectedRow();
            source.show(selected < 0 ? null : rows.row(rowTable.convertRowIndexToModel(selected)).rendered());
        });
        JPanel controls = new JPanel();
        controls.add(FieldNames.label("Look at most at (test cases):", scan));
        controls.add(scan);
        controls.add(FieldNames.label("Rows per page:", size));
        controls.add(size);
        controls.add(keptOnly);
        controls.add(preview);
        controls.add(next);
        controls.add(cancel);
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(controls, BorderLayout.NORTH);
        top.add(status, BorderLayout.SOUTH);
        JSplitPane counts = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                TableTitles.titled(Hints.of(new JTable(estimates), "How many test cases each stage makes, whether the number is exact or an estimate, and how it is worked out."), "Plan estimates: each count with its confidence and formula"),
                TableTitles.titled(Hints.of(new JTable(rules), "Each rule of step 4: the sheets it looks at and how many test cases it removes."), "Rule impact: what each rule reads and the candidates it removes"));
        counts.setResizeWeight(0.65);
        JSplitPane candidates = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                TableTitles.titled(rowTable, "Candidate page: each candidate's ids and fate under the rules"), source.component());
        candidates.setResizeWeight(0.5);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, counts, candidates);
        split.setResizeWeight(0.4);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
    }

    JComponent component() {
        return panel;
    }

    /** The first page of the chosen scenario, with the plan's counts. */
    CompletableFuture<CandidatePreview> preview() {
        Path file = scenario.get();
        if (file == null) {
            status.setText("Choose a scenario in step 3 first, or send an example there from step 1 · Library.");
            return CompletableFuture.completedFuture(null);
        }
        return load(file, BigInteger.ZERO, true);
    }

    /** The page after the one shown. */
    CompletableFuture<CandidatePreview> nextPage() {
        if (shown == null || nextFirst == null) {
            return CompletableFuture.completedFuture(null);
        }
        return load(shown, nextFirst, false);
    }

    /** Cancels the page being read; the tables keep what they show. */
    void cancel() {
        if (running != null) {
            running.cancel();
        }
    }

    private CompletableFuture<CandidatePreview> load(Path file, BigInteger first, boolean withEstimates) {
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("the candidates view is closed"));
        if (running != null) return pending;
        BigInteger limit;
        try {
            limit = new BigInteger(scan.getText().trim());
        } catch (NumberFormatException e) {
            status.setText("The scan limit is a whole number from 0, not '" + scan.getText() + "'.");
            return CompletableFuture.completedFuture(null);
        }
        CandidatePreview.Request request;
        try {
            request = new CandidatePreview.Request(limit, first, (Integer) size.getValue(), keptOnly.isSelected(), withEstimates);
        } catch (RuntimeException e) {
            status.setText(Background.describe(e).strip());
            return CompletableFuture.completedFuture(null);
        }
        CancellationSource cancellation = new CancellationSource();
        running = cancellation;
        busy(true);
        status.setText("Reading candidates of " + file + " from ordinal " + first + " ...");
        pending = Background.run("preview", () -> session.get().preview(file, request, cancellation), result -> {
            if (withEstimates) {
                List<PlanEstimates.Stage> stages = new ArrayList<>(result.estimates().candidates());
                stages.addAll(result.estimates().execution());
                stages.addAll(result.estimates().resources());
                estimates.show(stages);
            }
            rules.show(result.rules());
            rows.show(result.page().rows());
            source.show(null);
            shown = file;
            nextFirst = result.page().next();
            status.setText(describe(result));
            busy(false);
        }, failure -> {
            status.setText("The preview stopped: " + Background.describe(failure).strip());
            busy(false);
        });
        return pending;
    }

    /** Ends the owned request and completes after its event-thread callback, without blocking the event thread. */
    CompletableFuture<Void> close() {
        closing = true;
        cancel();
        busy(running != null);
        return CompletableFuture.allOf(pending.handle((result, failure) -> null), source.close());
    }

    private static String describe(CandidatePreview result) {
        CandidatePreview.Page page = result.page();
        return result.generated() + " generated; this page holds " + page.rows().size() + " rows from ordinal " + page.first() + ", read "
                + page.visited() + (page.next() == null ? "; no candidate after it" : "; the next page starts at ordinal " + page.next())
                + (page.limit().isEmpty() ? "" : " (" + page.limit() + ")");
    }

    private void busy(boolean working) {
        if (!working) {
            running = null;
        }
        offer();
    }

    /** Preview needs a step 3 scenario and no preview going; Next page another page of that same scenario. */
    void offer() {
        boolean working = running != null;
        Path chosen;
        try { chosen = scenario.get(); } catch (java.nio.file.InvalidPathException e) { chosen = null; }
        preview.setEnabled(!closing && !working && chosen != null);
        next.setEnabled(!closing && !working && nextFirst != null && chosen != null && chosen.equals(shown));
        cancel.setEnabled(!closing && working);
    }

    // ---- for tests, on the event thread ------------------------------------------------------------------------------

    ListTableModel<PlanEstimates.Stage> estimates() {
        return estimates;
    }

    ListTableModel<CandidatePreview.Rule> rules() {
        return rules;
    }

    ListTableModel<CandidatePreview.Row> rows() {
        return rows;
    }

    String status() {
        return status.getText();
    }

    String source() {
        return source.source();
    }

    void select(int row) {
        rowTable.setRowSelectionInterval(row, row);
    }

    void settings(String scanLimit, int pageSize, boolean executableOnly) {
        scan.setText(scanLimit);
        size.setValue(pageSize);
        keptOnly.setSelected(executableOnly);
    }
}
