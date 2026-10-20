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
import org.ghyura.cf.desktop.session.FileSeeker;
import org.ghyura.cf.desktop.session.FileTrail;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * The results-analysis tab: a metrics corpus (key=value lines with FW_VAR, which the run tab saves as its metrics kind)
 * analysed under the session's goals as {@code analyze --results} does, optionally with imported coverage reports. A run's
 * report, CSV tables and checkpoint are refused with how to get the corpus instead, as the corpus reader would misread them.
 */
final class AnalysisView {

    private static final String NO_COVERAGE = "no coverage import";

    private final Session session;
    private final JTextField results = FieldNames.placeholder(Hints.of(new JTextField(), "The metrics file to study: one line per test case of "
            + "key=value measurements ending with its verdict FW_VAR. Filled when you choose a file with Browse..., press Use on a "
            + "suggested file or Use the example; you can also type its path. Step 6 saves one as metrics for step 8."),
            "for example run-metrics.kv, saved in step 6 as \"metrics for step 8 (.kv)\"");
    private final JComboBox<String> format = Hints.of(new JComboBox<>(new String[]{NO_COVERAGE, "jacoco", "coverage-py", "gcov"}), "The kind of code-coverage report given below, or none.");
    private final JTextField report = FieldNames.placeholder(Hints.of(new JTextField(), "Optional: a code-coverage report to read with the "
            + "results. Filled by you: type its path or choose it with Browse...; if every result has its own report, write {lineNo} "
            + "where the result\'s line number (1, 2, 3, ...) goes."), "for example jacoco.xml, or report-{lineNo}.xml for one report per line");
    private final JButton reportBrowse = Hints.of(new JButton("Browse..."), "Choose the code-coverage report your coverage tool wrote.");
    private final JButton reportSearch;
    private final JTextArea output = Hints.of(new JTextArea(), "The analysis: measurements per setting, the best combinations, and how much of the code the tests reached. Filled after you press Analyze.");
    private final JButton analyze = Hints.of(new JButton("Analyze"), "Study the metrics file: measurements, the best combinations, and code coverage when a report is given.");
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));

    AnalysisView(Session session, Navigator navigator) {
        this.session = session;
        JButton browse = Hints.of(new JButton("Browse..."), "Choose the metrics file to study.");
        browse.addActionListener(e -> {
            Path chosen = navigator.choose(panel, Navigator.Purpose.RESULTS_OPEN, held());
            if (chosen != null) {
                results.setText(chosen.toString());
                String problem = notMetrics(chosen);
                if (problem != null) output.setText(problem);
            }
        });
        navigator.bind(browse, () -> Navigator.Purpose.RESULTS_OPEN, this::held);
        reportBrowse.addActionListener(e -> {
            Path chosen = navigator.choose(panel, Navigator.Purpose.COVERAGE_OPEN, reportHeld());
            if (chosen != null) {
                report.setText(chosen.toString());
            }
        });
        navigator.bind(reportBrowse, () -> Navigator.Purpose.COVERAGE_OPEN, this::reportHeld);
        AutoSearch search = new AutoSearch(navigator);
        reportSearch = search.button(this::coverageKind, this::reportHeld, chosen -> report.setText(chosen.toString()),
                "Search the folders where reports of the chosen coverage kind usually are, then pick one from the list.");
        format.addActionListener(e -> coverageBoxes());
        coverageBoxes();
        analyze.addActionListener(e -> analyze());
        results.getDocument().addDocumentListener(FormulaEditor.listener(this::offer));
        offer();
        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
        JPanel first = new JPanel(new BorderLayout(6, 6));
        first.add(FieldNames.label("Metrics file (.kv):", results), BorderLayout.WEST);
        first.add(results, BorderLayout.CENTER);
        JPanel firstButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        firstButtons.add(browse);
        firstButtons.add(search.button(() -> FileSeeker.Kind.METRICS, this::held, chosen -> {
            results.setText(chosen.toString());
            String problem = notMetrics(chosen);
            if (problem != null) output.setText(problem);
        }, "Search the folders where runs save metrics files, then pick one from the list it shows."));
        Path example = navigator.example(Navigator.Purpose.RESULTS_OPEN);
        if (example != null) {
            JButton use = Hints.of(new JButton("Use the example"), "Put an example metrics file from the library of examples into the box.");
            use.setToolTipText(example.toString());
            use.addActionListener(e -> results.setText(example.toString()));
            firstButtons.add(use);
        }
        first.add(firstButtons, BorderLayout.EAST);
        JLabel note = FieldNames.note("One line per test case: key=value measurements and its verdict, e.g. candidate_id=7 "
                + "latency_ms=4.6 FW_VAR=0 (0 = pass). Step 6 saves one: Save as \"metrics for step 8 (.kv)\", then Export...");
        JPanel second = new JPanel(new BorderLayout(6, 6));
        JPanel coverage = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        coverage.add(FieldNames.label("Code coverage (optional):", format));
        coverage.add(format);
        coverage.add(FieldNames.label("report file:", report));
        second.add(coverage, BorderLayout.WEST);
        second.add(report, BorderLayout.CENTER);
        JPanel secondButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        secondButtons.add(reportBrowse);
        secondButtons.add(reportSearch);
        secondButtons.add(analyze);
        second.add(secondButtons, BorderLayout.EAST);
        JPanel hint = navigator.hint(results, Navigator.Purpose.RESULTS_OPEN);
        for (JComponent c : new JComponent[]{first, note, hint, second}) {
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
        return path(results.getText());
    }

    /** The coverage report the field names, or null (also for a {lineNo} pattern). */
    private Path reportHeld() {
        return path(report.getText().replace("{lineNo}", "1"));
    }

    private static Path path(String text) {
        try {
            return text.isBlank() ? null : Path.of(text.strip());
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    private boolean analyzing;

    /** Analyze needs a metrics file in the box, and no analysis going. */
    private void offer() {
        analyze.setEnabled(!analyzing && !results.getText().isBlank());
    }

    /** The coverage report box and its buttons are active only while a coverage format is chosen. */
    private void coverageBoxes() {
        boolean on = !NO_COVERAGE.equals(format.getSelectedItem());
        report.setEnabled(on);
        reportBrowse.setEnabled(on);
        reportSearch.setEnabled(on);
    }

    /** What the coverage report's search looks for: reports of the chosen format; null while none is chosen. */
    private FileSeeker.Kind coverageKind() {
        return switch (String.valueOf(format.getSelectedItem())) {
            case "jacoco" -> FileSeeker.Kind.JACOCO;
            case "coverage-py" -> FileSeeker.Kind.COVERAGE_PY;
            case "gcov" -> FileSeeker.Kind.GCOV;
            default -> null;
        };
    }

    /**
     * Why a file is not a metrics corpus, in plain words with how to get one; null when it may be one (a corpus, any text, a
     * file of no known kind, or a missing file, which the analysis reports). A run's report, table or checkpoint is known by
     * its content whatever its name says, so one renamed to .kv or .txt is refused too.
     */
    static String notMetrics(Path file) {
        String instead = " In step 6, run the scenario, choose Save as \"metrics for step 8 (.kv)\" and press Export..., then choose "
                + "that file here.\n";
        if (Files.isDirectory(file)) {
            return file + " is a folder. Choose a metrics file (.kv) inside it.\n";
        }
        FileTrail.Kind kind = Files.isRegularFile(file) ? FileTrail.classify(file) : null;
        if (kind == null || kind == FileTrail.Kind.METRICS || kind == FileTrail.Kind.TEXT) {
            // the name says metrics, text or nothing; the content decides: a run's export renamed to .kv is still that export
            kind = Files.isRegularFile(file) ? sniff(file) : null;
            if (kind == null) {
                return null;
            }
        }
        String name = file.getFileName().toString();
        return switch (kind) {
            case REPORT -> name + " is the summary report of a run (XML); the analysis reads metrics, one line per test case." + instead;
            case RESULTS -> name + " is a table (CSV) saved in step 6; the analysis reads metrics, one line per test case." + instead;
            case CHECKPOINT -> name + " is a checkpoint of a run, for resuming it; the analysis reads metrics, one line per test case."
                    + instead;
            case SCENARIO, CONSTRAINTS, SETTINGS, LIBRARY -> name + " describes a scenario, not the results of running it." + instead;
            default -> name + " is not a metrics file." + instead;
        };
    }

    /** The kind of a run's export whose name says nothing, from its first bytes: XML (report, checkpoint) or a CSV table. */
    private static FileTrail.Kind sniff(Path file) {
        String head;
        try (var in = Files.newInputStream(file)) {
            head = new String(in.readNBytes(1024), java.nio.charset.StandardCharsets.UTF_8).stripLeading();
        } catch (java.io.IOException e) {
            return null;
        }
        String first = head.lines().findFirst().orElse("");
        if (head.startsWith("<")) {
            return head.contains("<cf-checkpoint") ? FileTrail.Kind.CHECKPOINT : FileTrail.Kind.REPORT;
        }
        return first.contains(",") && first.contains("candidate_id") && !first.contains("=") ? FileTrail.Kind.RESULTS : null;
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
            output.setText("Choose a metrics file first: in step 6 save the run as \"metrics for step 8 (.kv)\", or press Browse...\n");
            return CompletableFuture.completedFuture(null);
        }
        Path chosen = path(file);
        String problem = chosen == null ? null : notMetrics(chosen);
        if (problem != null) {
            output.setText(problem);
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
        analyzing = true;
        offer();
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
            analyzing = false;
            offer();
        }, failure -> {
            output.setText("The analysis failed:\n" + Background.describe(failure));
            analyzing = false;
            offer();
        });
    }
}
