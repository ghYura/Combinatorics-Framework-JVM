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

import org.ghyura.cf.analysis.AutoPlan;
import org.ghyura.cf.analysis.CorpusProfile;
import org.ghyura.cf.analysis.RuleSet;
import org.ghyura.cf.analysis.ScoreBreakdown;
import org.ghyura.cf.analysis.TextScoring;
import org.ghyura.cf.desktop.session.FileSeeker;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Json;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * The text-scorer tab, after the legacy analyzer window: an input file, the manual rules as JSON, the tuning (minimum
 * support, common items, opt and watch thresholds, with the legacy spinner ranges), the result table with its filter,
 * the selected line's breakdown, the profile summary with the optimal variant, the legacy CSV export and optimization charts. Lines are
 * scored off the event thread; nothing is executed and no database is used.
 */
final class ScorerView {

    private final Session session;
    private final Navigator navigator;
    /** The desktop's Hints button, which the chart window's hints follow. */
    private final AbstractButton hintsSwitch;
    private final JTextField file = FieldNames.placeholder(Hints.of(new JTextField(), "The text file to score; every line is scored on its own. Filled when you choose a file with Browse...; you can also type its path."),
            "for example a log or a program's output: sut.log, run.out, notes.txt");
    private final JTextArea rules = Hints.of(new JTextArea("{}", 8, 40), "Your scoring rules as JSON: words that must appear, how much each word weighs, allowed number ranges, and what counts as worth improving. Filled with {} (no rules) at the start; type rules or press Load rules...");
    private final JSpinner minSupport = Hints.of(new JSpinner(new SpinnerNumberModel(3, 1, 1000, 1)), "How many lines must share a pattern before the scorer learns it.");
    private final JSpinner topK = Hints.of(new JSpinner(new SpinnerNumberModel(12, 1, 1000, 1)), "How many learned patterns are kept.");
    private final JSpinner optThreshold = Hints.of(new JSpinner(new SpinnerNumberModel(75.0, 0.0, 100.0, 0.5)), "Lines scoring at least this much are marked as worth improving.");
    private final JSpinner watchThreshold = Hints.of(new JSpinner(new SpinnerNumberModel(45.0, 0.0, 100.0, 0.5)), "Lines scoring at least this much are marked to watch.");
    private final JButton score = Hints.of(new JButton("Score"), "Give every line of the text a score.");
    private final JButton export = Hints.of(new JButton("Export CSV..."), "Save the scored lines as a table (CSV) for a spreadsheet.");
    private final JButton charts = Hints.of(new JButton("Optimization charts"), "Open a window with charts of the scores.");
    private final JButton autoPlan = Hints.of(new JButton("Auto-plan"), "Suggest the two thresholds from the scores.");
    private final JTextField filter = Hints.of(new JTextField(), "Show only the lines that contain this text; empty shows every line. Filled by you: type the text to look for.");
    private final ScoreTableModel model = new ScoreTableModel();
    private final JTable table = Hints.of(new JTable(model), "Every line with its score, the decision it led to, and the features that made the score. Select a line to see why below.");
    private final TableRowSorter<ScoreTableModel> sorter = new TableRowSorter<>(model);
    private final JTextArea detail = Hints.of(new JTextArea(), "Why the selected line got its score. Filled when you select a line in the table above.");
    private final JTextArea summary = Hints.of(new JTextArea(), "A summary of all scores. Filled after you press Score.");
    private final JLabel status = new JLabel("Idle");
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private TextScoring.Report last;

    ScorerView(Session session, Navigator navigator, AbstractButton hintsSwitch) {
        this.session = session;
        this.navigator = navigator;
        this.hintsSwitch = hintsSwitch;
        JButton browse = Hints.of(new JButton("Browse..."), "Choose the text file.");
        browse.addActionListener(e -> {
            Path chosen = navigator.choose(panel, Navigator.Purpose.TEXT_OPEN, text());
            if (chosen != null) {
                file.setText(chosen.toString());
            }
        });
        navigator.bind(browse, () -> Navigator.Purpose.TEXT_OPEN, this::text);
        JButton load = Hints.of(new JButton("Load rules..."), "Load scoring rules from a JSON file.");
        load.addActionListener(e -> {
            Path selected = navigator.choose(panel, Navigator.Purpose.RULES_OPEN, text());
            if (selected != null) {
                loadRules(selected);
            }
        });
        navigator.bind(load, () -> Navigator.Purpose.RULES_OPEN, this::text);
        score.addActionListener(e -> score());
        export.addActionListener(e -> {
            Path chosen = navigator.choose(panel, Navigator.Purpose.SCORES_SAVE, text());
            if (chosen != null) {
                exportCsv(chosen);
            }
        });
        navigator.bind(export, () -> Navigator.Purpose.SCORES_SAVE, this::text);
        export.setEnabled(false);
        charts.addActionListener(e -> {
            if (!GraphicsEnvironment.isHeadless() && last != null) {
                JFrame frame = new JFrame("Optimization charts");
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setContentPane(chartWindowContent());
                frame.setSize(680, 460);
                frame.setLocationByPlatform(true);
                frame.setVisible(true);
            }
        });
        charts.setEnabled(false);
        autoPlan.addActionListener(e -> autoPlan());
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        table.setRowSorter(sorter);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelected();
        });

        AutoSearch search = new AutoSearch(navigator);
        JPanel input = new JPanel(new BorderLayout(6, 6));
        input.add(FieldNames.label("Text file to score:", file), BorderLayout.WEST);
        input.add(file, BorderLayout.CENTER);
        JPanel inputButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        inputButtons.add(browse);
        inputButtons.add(search.button(() -> FileSeeker.Kind.TEXT, this::text, chosen -> file.setText(chosen.toString()),
                "Search the folders where logs and other texts usually are, then pick one to score."));
        input.add(inputButtons, BorderLayout.EAST);
        input.add(navigator.hint(file, Navigator.Purpose.TEXT_OPEN), BorderLayout.SOUTH);
        JPanel tuning = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tuning.add(FieldNames.label("Lines per learned pattern:", minSupport));
        tuning.add(minSupport);
        tuning.add(FieldNames.label("Patterns kept:", topK));
        tuning.add(topK);
        tuning.add(FieldNames.label("Worth improving from score:", optThreshold));
        tuning.add(optThreshold);
        tuning.add(FieldNames.label("Watch from score:", watchThreshold));
        tuning.add(watchThreshold);
        tuning.add(load);
        tuning.add(autoPlan);
        tuning.add(score);
        tuning.add(export);
        tuning.add(charts);
        JPanel rulesPanel = new JPanel(new BorderLayout(4, 4));
        JPanel rulesHead = new JPanel(new BorderLayout(6, 0));
        rulesHead.add(FieldNames.label("Your scoring rules as JSON (required_tokens, token_weights, numeric_ranges, optimizations, ...)", rules),
                BorderLayout.CENTER);
        rulesHead.add(search.button(() -> FileSeeker.Kind.RULES, this::text, this::loadRules,
                "Search the folders where scoring rules files usually are, then load one into the box."), BorderLayout.EAST);
        rulesPanel.add(rulesHead, BorderLayout.NORTH);
        rulesPanel.add(new JScrollPane(rules), BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(input, BorderLayout.NORTH);
        north.add(tuning, BorderLayout.CENTER);
        north.add(rulesPanel, BorderLayout.SOUTH);

        JPanel filterRow = new JPanel(new BorderLayout(6, 6));
        filterRow.add(FieldNames.label("Show lines containing:", filter), BorderLayout.WEST);
        filterRow.add(filter, BorderLayout.CENTER);
        JPanel results = new JPanel(new BorderLayout(4, 4));
        results.add(filterRow, BorderLayout.NORTH);
        results.add(TableTitles.titled(table, "Scored lines: each line's score, decision and features"), BorderLayout.CENTER);
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        for (JTextArea area : new JTextArea[]{rules, detail, summary}) {
            area.setFont(mono);
        }
        detail.setEditable(false);
        summary.setEditable(false);
        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, FieldNames.titled(detail, "Why this score"), FieldNames.titled(summary, "Summary of all scores"));
        bottom.setResizeWeight(0.5);
        JSplitPane center = new JSplitPane(JSplitPane.VERTICAL_SPLIT, results, bottom);
        center.setResizeWeight(0.65);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(north, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
    }

    /** Reads scoring rules from a file into the rules box, off the event thread. */
    private void loadRules(Path selected) {
        Background.run("load-rules", () -> RuleSet.readText(selected, session.analysisBudget()), rules::setText,
                failure -> status.setText("The rules could not be read: " + Background.describe(failure)));
    }

    /** The text the field names, or null. */
    private Path text() {
        try {
            String t = file.getText().strip();
            return t.isEmpty() ? null : Path.of(t);
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    JComponent component() {
        return panel;
    }

    void choose(Path input, String rulesJson) {
        file.setText(input.toString());
        rules.setText(rulesJson);
    }

    void tune(int support, int common, double opt, double watch) {
        minSupport.setValue(support);
        topK.setValue(common);
        optThreshold.setValue(opt);
        watchThreshold.setValue(watch);
    }

    /** Scores the chosen text off the event thread; the table, summary and status follow, or the refusal is shown. */
    CompletableFuture<TextScoring.Report> score() {
        String path = file.getText().trim();
        if (path.isEmpty()) {
            status.setText("Choose a text file first.");
            return CompletableFuture.completedFuture(null);
        }
        String json = rules.getText().isBlank() ? "{}" : rules.getText();
        TextScoring.Settings settings;
        try {
            settings = new TextScoring.Settings(((Number) minSupport.getValue()).intValue(), ((Number) topK.getValue()).intValue(),
                    ((Number) optThreshold.getValue()).doubleValue(), ((Number) watchThreshold.getValue()).doubleValue());
        } catch (IllegalArgumentException e) {
            status.setText(e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
        score.setEnabled(false);
        status.setText("Scoring " + path + " ...");
        return Background.run("score", () -> session.score(Path.of(path), RuleSet.parse(json, "rules editor"), settings), report -> {
            last = report;
            model.set(report.lines());
            detail.setText("");
            summary.setText(summaryText(report));
            status.setText(report.lines().size() + " lines: opt " + report.count(ScoreBreakdown.OPT) + ", watch "
                    + report.count(ScoreBreakdown.WATCH) + ", reject " + report.count(ScoreBreakdown.REJECT));
            score.setEnabled(true);
            export.setEnabled(true);
            charts.setEnabled(true);
        }, failure -> {
            status.setText("Scoring failed");
            summary.setText(Background.describe(failure));
            score.setEnabled(true);
        });
    }

    /**
     * The legacy Auto-Analysis plan of the chosen text's first 500 lines: its suggested optimizations replace those of the
     * rules in the editor (the other rules stay) and the plan is shown; nothing is scored or executed.
     */
    CompletableFuture<AutoPlan.Plan> autoPlan() {
        String path = file.getText().trim();
        if (path.isEmpty()) {
            status.setText("Choose a text file first.");
            return CompletableFuture.completedFuture(null);
        }
        String current = rules.getText().isBlank() ? "{}" : rules.getText();
        return Background.run("auto-plan", () -> session.suggest(Path.of(path)), plan -> {
            String now = rules.getText().isBlank() ? "{}" : rules.getText();
            if (!now.equals(current)) {
                status.setText("Rules changed while planning; run Auto-plan again to apply suggestions.");
                return;
            }
            Object parsed;
            try {
                parsed = Json.parse(current, "rules editor");
            } catch (RuntimeException e) {
                status.setText("Auto-plan kept the rules unchanged: " + Background.describe(e));
                return;
            }
            if (!(parsed instanceof java.util.Map<?, ?> m)) {
                status.setText("Auto-plan kept the rules unchanged: the rules must be a JSON object.");
                return;
            }
            java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
            m.forEach((k, v) -> root.put((String) k, v));
            root.remove("optimization");
            root.put("optimizations", ((java.util.Map<?, ?>) Json.parse(plan.rulesJson(), "auto plan")).get("optimizations"));
            rules.setText(Json.write(root));
            summary.setText(plan.toText());
            status.setText("Auto-plan: " + plan.goals().size() + " goals suggested into the rules; score to apply them.");
        }, failure -> status.setText("Auto-plan failed: " + Background.describe(failure).strip()));
    }

    String rulesText() {
        return rules.getText();
    }

    void setFilter(String text) {
        filter.setText(text);
    }

    /** Rows shown after the filter. */
    int visibleRows() {
        return table.getRowCount();
    }

    void select(int viewRow) {
        table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
    }

    String detail() {
        return detail.getText();
    }

    String summary() {
        return summary.getText();
    }

    String status() {
        return status.getText();
    }

    /** Writes the last report as the legacy CSV, replacing the file only once it is complete. */
    CompletableFuture<Path> exportCsv(Path target) {
        TextScoring.Report report = last;
        if (report == null) {
            status.setText("Nothing scored yet.");
            return CompletableFuture.completedFuture(null);
        }
        return Background.run("export-scores", () -> {
            try {
                AtomicFiles.write(target, report.toCsv().getBytes(StandardCharsets.UTF_8));
                return target;
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, written -> status.setText("CSV saved: " + written),
                failure -> status.setText("The CSV could not be written: " + Background.describe(failure)));
    }

    /** The chart window's content: the charts under hints that follow the desktop's Hints button (F1 there presses it). */
    JLayer<JComponent> chartWindowContent() {
        HintLayer chartHints = new HintLayer();
        chartHints.control(hintsSwitch);
        return new JLayer<>(charts(), chartHints);
    }

    /** The legacy optimization charts of the last report: objective, cost, loss and fitness views of the line scores. */
    JTabbedPane charts() {
        List<Double> scores = last == null ? List.of() : last.lines().stream().map(ScoreBreakdown::score).toList();
        JTabbedPane tabs = new JTabbedPane();
        int step = 0;
        for (ScoreChart.Kind kind : ScoreChart.Kind.values()) {
            Steps.add(tabs, "9." + ++step, kind.title, new ScoreChart(kind, scores), "Chart 9." + step + ": the scores of all lines drawn as the "
                    + kind.title.toLowerCase(java.util.Locale.ROOT) + ", to compare how lines rank.");
        }
        return tabs;
    }

    /** The legacy filter: a case-insensitive literal match in any column. */
    private void applyFilter() {
        String q = filter.getText().trim().toLowerCase(Locale.ROOT);
        sorter.setRowFilter(q.isBlank() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(q)));
    }

    private void showSelected() {
        int view = table.getSelectedRow();
        if (view < 0) {
            return;
        }
        detail.setText(detailText(model.get(table.convertRowIndexToModel(view))));
    }

    static String detailText(ScoreBreakdown line) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "line %d  score %.4f  decision %s%n", line.lineNo(), line.score(), line.decision()));
        text.append(String.format(Locale.ROOT, "type %s  signature %s  length %d  entropy %.4f  placeholders %d%n", line.features().lineType(),
                line.features().signature(), line.features().length(), line.features().entropy(), line.features().placeholderCount()));
        text.append("contributions:\n");
        for (Map.Entry<String, Double> c : line.contributions().entrySet()) {
            text.append(String.format(Locale.ROOT, "  %-28s %10.4f%n", c.getKey(), c.getValue()));
        }
        if (!line.notes().isEmpty()) {
            text.append("notes:\n");
            for (String n : line.notes()) text.append("  ").append(n).append('\n');
        }
        text.append("matched rules: ").append(String.join(", ", line.matchedRules())).append('\n');
        if (!line.features().keyValues().isEmpty()) {
            text.append("keys: ").append(line.features().keyValues()).append('\n');
        }
        text.append("text:\n").append(line.originalLine()).append('\n');
        return text.toString();
    }

    /** The legacy summary (profile, discovery confidence, optimization ranges) and the optimal variant. */
    static String summaryText(TextScoring.Report report) {
        CorpusProfile p = report.profile();
        StringBuilder sb = new StringBuilder();
        sb.append("Total lines: ").append(p.totalLines()).append('\n');
        sb.append("Non-empty lines: ").append(p.nonemptyLines()).append('\n');
        sb.append("Dominant type: ").append(p.dominantLineType()).append('\n');
        sb.append("Dominant signature: ").append(p.dominantSignature()).append('\n');
        sb.append(String.format(Locale.ROOT, "Discovery confidence: %.3f%n", report.synthesized().confidence()));
        if (!p.metrics().isEmpty()) {
            sb.append("Optimization ranges:\n");
            p.metrics().forEach((k, b) -> sb.append(String.format(Locale.ROOT, "  - %s: [%.4g ... %.4g]  P5..P95=[%.4g ... %.4g] (n=%d)%n", k,
                    b.min(), b.max(), b.low(), b.high(), b.count())));
        }
        report.optimal().ifPresent(best -> {
            sb.append("\n=== OPTIMAL VARIANT (Highest Score) ===\n");
            sb.append("Line #").append(best.lineNo()).append('\n');
            sb.append("Data: ").append(best.originalLine()).append('\n');
            sb.append("Score: ").append(String.format(Locale.ROOT, "%.2f", best.score())).append('\n');
            sb.append("Decision: ").append(best.decision()).append('\n');
        });
        return sb.toString();
    }
}
