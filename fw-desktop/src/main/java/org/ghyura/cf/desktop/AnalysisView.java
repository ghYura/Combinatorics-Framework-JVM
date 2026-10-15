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

import org.ghyura.cf.analysis.CoverageImport;
import org.ghyura.cf.analysis.MetricCorpus;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.Diagnostic;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * The results-analysis tab: a metrics corpus (key=value lines, an export of the csv kind) analysed under the session's
 * goals as {@code analyze --results} does, optionally with imported coverage reports.
 */
final class AnalysisView {

    private static final String NO_COVERAGE = "no coverage import";

    private final Session session;
    private final JTextField results = Hints.of(new JTextField(), "The saved results file to study, for example one exported in step 6. Filled when you choose a file with Browse... or press Use on a suggested file; you can also type its path.");
    private final JComboBox<String> format = Hints.of(new JComboBox<>(new String[]{NO_COVERAGE, "jacoco", "coverage-py", "gcov"}), "The kind of code-coverage report given below, or none.");
    private final JTextField report = Hints.of(new JTextField(), "Optional: a code-coverage report to read with the results. Filled by you: type its path; if every result has its own report, write {lineNo} where the result\'s line number (1, 2, 3, ...) goes.");
    private final JTextArea output = Hints.of(new JTextArea(), "The analysis: measurements per setting, the best combinations, and how much of the code the tests reached. Filled after you press Analyze.");
    private final JButton analyze = Hints.of(new JButton("Analyze"), "Study the results file: measurements, the best combinations, and code coverage when a report is given.");
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));

    AnalysisView(Session session, Navigator navigator) {
        this.session = session;
        JButton browse = Hints.of(new JButton("Browse..."), "Choose the results file.");
        browse.addActionListener(e -> {
            Path chosen = navigator.choose(panel, Navigator.Purpose.RESULTS_OPEN, held());
            if (chosen != null) {
                results.setText(chosen.toString());
            }
        });
        navigator.bind(browse, () -> Navigator.Purpose.RESULTS_OPEN, this::held);
        analyze.addActionListener(e -> analyze());
        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
        JPanel first = new JPanel(new BorderLayout(6, 6));
        first.add(FieldNames.label("Results file:", results), BorderLayout.WEST);
        first.add(results, BorderLayout.CENTER);
        first.add(browse, BorderLayout.EAST);
        JPanel second = new JPanel(new BorderLayout(6, 6));
        JPanel coverage = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        coverage.add(FieldNames.label("Code coverage:", format));
        coverage.add(format);
        coverage.add(FieldNames.label("report file:", report));
        second.add(coverage, BorderLayout.WEST);
        second.add(report, BorderLayout.CENTER);
        second.add(analyze, BorderLayout.EAST);
        JPanel hint = navigator.hint(results, Navigator.Purpose.RESULTS_OPEN);
        for (JComponent c : new JComponent[]{first, hint, second}) {
            c.setAlignmentX(0f);
            form.add(c);
        }
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(form, BorderLayout.NORTH);
        panel.add(FieldNames.titled(output, "Analysis report"), BorderLayout.CENTER);
    }

    JComponent component() {
        return panel;
    }

    /** The corpus the field names, or null. */
    private Path held() {
        try {
            String text = results.getText().strip();
            return text.isEmpty() ? null : Path.of(text);
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    void choose(Path corpus, String coverageFormat, String coverageReport) {
        results.setText(corpus.toString());
        format.setSelectedItem(coverageFormat == null ? NO_COVERAGE : coverageFormat);
        report.setText(coverageReport == null ? "" : coverageReport);
    }

    String output() {
        return output.getText();
    }

    /** Analyses the chosen corpus off the event thread; the report or the refusal replaces the output. */
    CompletableFuture<Session.Analyzed> analyze() {
        String file = results.getText().trim();
        if (file.isEmpty()) {
            output.setText("Choose a results corpus first.\n");
            return CompletableFuture.completedFuture(null);
        }
        CoverageImport.Source coverage;
        try {
            coverage = NO_COVERAGE.equals(format.getSelectedItem()) ? null
                    : new CoverageImport.Source(CoverageImport.Format.parse((String) format.getSelectedItem()), report.getText().trim());
        } catch (IllegalArgumentException e) {
            output.setText(e.getMessage() + "\n");
            return CompletableFuture.completedFuture(null);
        }
        analyze.setEnabled(false);
        output.setText("Analysing " + file + " ...\n");
        return Background.run("analyze", () -> session.analyze(Path.of(file), coverage), analyzed -> {
            StringBuilder text = new StringBuilder("results: ").append(file).append(" (").append(analyzed.samples()).append(" samples, ")
                    .append(analyzed.missingVerdicts()).append(" without a verdict)\n");
            text.append(analyzed.report().toText());
            for (MetricCorpus.Imported entry : analyzed.imports()) {
                text.append("coverage line ").append(entry.line()).append(": ").append(entry.report().status()).append(' ')
                        .append(entry.report().metrics()).append('\n');
            }
            for (Diagnostic d : analyzed.diagnostics()) {
                text.append(d).append('\n');
            }
            output.setText(text.toString());
            analyze.setEnabled(true);
        }, failure -> {
            output.setText("The analysis failed:\n" + Background.describe(failure));
            analyze.setEnabled(true);
        });
    }
}
