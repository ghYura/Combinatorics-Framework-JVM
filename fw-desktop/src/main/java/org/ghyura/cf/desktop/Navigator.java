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
import org.ghyura.cf.desktop.session.FileTrail.Kind;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Every file button of the desktop opens through here, at the place the file trail knows for what the button is for:
 * an open dialog at the newest file of its kinds, selected, or in the current scenario's folder when none is known; a
 * save dialog where such files went last (else beside the scenario) with a new name proposed that overwrites nothing.
 * Assisted hints say it before the click: a bound button's tooltip names the place it opens, and a hint beside a file
 * field offers the newest matching file with a Use button; both follow the trail as it learns. What a dialog returns is
 * recorded in the trail.
 */
final class Navigator {

    /** What a file button is for: open or save, the kinds of file it deals with, the name it proposes. */
    enum Purpose {
        SCENARIO_OPEN(true, EnumSet.of(Kind.SCENARIO), "", "Scenarios (xlsx, json, yaml, toml)", "xlsx", "json", "yaml", "yml", "toml"),
        DOCUMENT_OPEN(true, EnumSet.of(Kind.SCENARIO), "", "Scenarios (xlsx, json, yaml, toml)", "xlsx", "json", "yaml", "yml", "toml"),
        DOCUMENT_SAVE(false, EnumSet.of(Kind.SCENARIO), "", "Scenarios (xlsx, json, yaml, toml)", "xlsx", "json", "yaml", "yml",
                "toml"),
        CONSTRAINTS_OPEN(true, EnumSet.of(Kind.CONSTRAINTS), "", "Constraint sidecars (json)", "json"),
        CONSTRAINTS_SAVE(false, EnumSet.of(Kind.CONSTRAINTS), ".constraints.json", "Constraint sidecars (json)", "json"),
        GRAPH_SAVE(false, EnumSet.of(Kind.GRAPH), ".graph.dot", "Graphs (dot, json)", "dot", "json"),
        EXPORT_REPORT(false, EnumSet.of(Kind.REPORT), "-report.xml", "Run reports (xml)", "xml"),
        EXPORT_RESULTS(false, EnumSet.of(Kind.RESULTS, Kind.METRICS), "-results.csv", "Results (csv)", "csv"),
        EXPORT_SAMPLES(false, EnumSet.of(Kind.RESULTS, Kind.METRICS), "-samples.csv", "Samples (csv)", "csv"),
        EXPORT_METRICS(false, EnumSet.of(Kind.RESULTS, Kind.METRICS), "-metrics.kv", "Metrics files (kv)", "kv"),
        EXPORT_CHECKPOINT(false, EnumSet.of(Kind.CHECKPOINT), ".checkpoint", "Checkpoints", "checkpoint", "ckpt", "xml"),
        RESULTS_OPEN(true, EnumSet.of(Kind.METRICS), "", "Metrics files (kv, txt, log)", "kv", "txt", "log"),
        COVERAGE_OPEN(true, EnumSet.noneOf(Kind.class), "", "Coverage reports (xml, json, gcov, txt)", "xml", "json", "gcov", "txt"),
        TEXT_OPEN(true, EnumSet.of(Kind.TEXT), "", "Texts", "txt", "log", "out", "sh"),
        RULES_OPEN(true, EnumSet.of(Kind.RULES), "", "Scoring rules (json)", "json"),
        SCORES_SAVE(false, EnumSet.of(Kind.SCORES), "-scores.csv", "Scores (csv)", "csv"),
        LIBRARY_OPEN(true, EnumSet.of(Kind.LIBRARY), "", "Library folders and their example lists (json)", "json"),
        ADDON_OPEN(true, EnumSet.noneOf(Kind.class), "", "Add-ons (jar) and class folders", "jar"),
        PROGRAM_OPEN(true, EnumSet.noneOf(Kind.class), "", ""),
        IMPORTS_OPEN(true, EnumSet.noneOf(Kind.class), "", ""),
        WORKDIR_OPEN(true, EnumSet.noneOf(Kind.class), "", "");

        private final boolean open;
        private final Set<Kind> kinds;
        private final String suffix;
        private final String description;
        private final String[] extensions;

        Purpose(boolean open, Set<Kind> kinds, String suffix, String description, String... extensions) {
            this.open = open;
            this.kinds = kinds;
            this.suffix = suffix;
            this.description = description;
            this.extensions = extensions;
        }

        boolean open() {
            return open;
        }

        Set<Kind> kinds() {
            return kinds;
        }

        /** The export a run's kind name asks for. */
        static Purpose export(String kind) {
            return switch (kind) {
                case "results" -> EXPORT_RESULTS;
                case "csv" -> EXPORT_SAMPLES;
                case "metrics" -> EXPORT_METRICS;
                case "checkpoint" -> EXPORT_CHECKPOINT;
                default -> EXPORT_REPORT;
            };
        }

        /** What the dialog opens or saves, in a few words for its title. */
        String what() {
            return switch (this) {
                case SCENARIO_OPEN, DOCUMENT_OPEN -> "a scenario file";
                case DOCUMENT_SAVE -> "the scenario";
                case CONSTRAINTS_OPEN -> "a constraints file";
                case CONSTRAINTS_SAVE -> "the rules as a constraints file";
                case GRAPH_SAVE -> "the dependency graph";
                case EXPORT_REPORT -> "the run's summary report";
                case EXPORT_RESULTS -> "every attempt of the run";
                case EXPORT_SAMPLES -> "the run's measurements table";
                case EXPORT_METRICS -> "the run's metrics for step 8";
                case EXPORT_CHECKPOINT -> "the run's checkpoint";
                case RESULTS_OPEN -> "a metrics file to analyze";
                case COVERAGE_OPEN -> "a code-coverage report";
                case TEXT_OPEN -> "a text file to score";
                case RULES_OPEN -> "scoring rules";
                case SCORES_SAVE -> "the scored lines";
                case LIBRARY_OPEN -> "a library of examples";
                case ADDON_OPEN -> "an add-on jar or folder";
                case PROGRAM_OPEN -> "the Python interpreter";
                case IMPORTS_OPEN -> "a folder Python imports from";
                case WORKDIR_OPEN -> "the folder Python test cases start in";
            };
        }

        /** What to pick, in plain words: what the file is, what it looks like, and what it is not. Paragraphs end with \n. */
        String guide() {
            return switch (this) {
                case SCENARIO_OPEN, DOCUMENT_OPEN -> "A scenario is a workbook whose sheets list the values to combine; its FW_Seq "
                        + "sheet says how they are combined.\nPick an .xlsx workbook, or the same sheets saved as .json, .yaml or .toml.\n"
                        + "Not a *.constraints.json or *.properties file: those sit beside a scenario and are found with it.";
                case DOCUMENT_SAVE -> "Where the scenario goes. .xlsx keeps the workbook; .json, .yaml and .toml save the same sheets "
                        + "as text.";
                case CONSTRAINTS_OPEN -> "Rules that forbid or require combinations of values, as JSON: usually a "
                        + "<scenario>.constraints.json beside a scenario.\nImporting replaces the rules shown; they reach the scenario "
                        + "when you press Save for scenario.";
                case CONSTRAINTS_SAVE -> "A copy of the rules as JSON.\nRuns read only <scenario>.constraints.json beside the scenario, "
                        + "which Save for scenario writes; a file of any other name is a copy to import later.";
                case GRAPH_SAVE -> "A file ending in .dot opens in Graphviz and other diagram tools; .json keeps the graph as data.";
                case EXPORT_REPORT -> "An XML summary of the run: its counts and verdicts, for people and tools.\nStep 8 does not read "
                        + "it: save the metrics for step 8 instead.";
                case EXPORT_RESULTS -> "One CSV row per attempt (the results_v2 table): outcome, verdict and duration, for a spreadsheet."
                        + "\nStep 8 does not read it: save the metrics for step 8 instead.";
                case EXPORT_SAMPLES -> "One CSV row per test case with a column per measurement, for a spreadsheet.\nStep 8 does not "
                        + "read it: save the metrics for step 8 instead.";
                case EXPORT_METRICS -> "One line per test case: its key=value measurements and its verdict as FW_VAR (0 = pass).\n"
                        + "This is the file step 8 (Results analysis) and analyze --results read.";
                case EXPORT_CHECKPOINT -> "The run's exact state, to resume it later (resume --checkpoint) or to export it again.";
                case RESULTS_OPEN -> "A text file with one line per test case: key=value measurements ending with the verdict, for "
                        + "example\ncandidate_id=7 latency_ms=4.6 FW_VAR=0\n(FW_VAR 0 means pass).\nMake one in step 6: Save as "
                        + "\"metrics for step 8 (.kv)\".\nNot the report, results or CSV exports of step 6.";
                case COVERAGE_OPEN -> "A report your coverage tool already wrote: JaCoCo XML, coverage.py JSON or gcov output. "
                        + "Nothing is run.\nFor one report per results line, put {lineNo} in the path where the line number goes.";
                case TEXT_OPEN -> "Any text whose lines you want ranked, such as a log or a program's output (.txt, .log, .out). "
                        + "Every line is scored on its own.";
                case RULES_OPEN -> "A JSON file of scoring rules, such as {\"required_tokens\": [...], \"token_weights\": {...}}.\n"
                        + "It replaces the rules in the box.";
                case SCORES_SAVE -> "A CSV table of every scored line with its score and decision, for a spreadsheet.";
                case LIBRARY_OPEN -> "The generator_trunk folder of a " + Examples.QA_EDITION + " checkout: the folder that holds "
                        + "intake/verified_examples.json.\nSelect the folder and press Open, or open intake and pick that .json file.";
                case ADDON_OPEN -> "A runner add-on, such as addons/fw-python.jar beside the application, or a jar or class folder "
                        + "your plugin needs. It is added to the list.";
                case PROGRAM_OPEN -> "The Python program itself, for example /usr/bin/python3 or the bin/python of a virtual "
                        + "environment.";
                case IMPORTS_OPEN -> "A folder that holds Python modules your test cases import, such as your SUT's package. It is "
                        + "added to the list.";
                case WORKDIR_OPEN -> "The folder each Python test case starts in; relative paths in your SUT are read from here.\n"
                        + "Leave the box empty to use the scenario's folder.";
            };
        }
    }

    /**
     * Where a dialog opens: a folder, and the file it shows selected (an existing one to open, a new name to save as);
     * why, in words, for a hint.
     */
    record Place(Path folder, Path file, String why) {
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final FileTrail trail;
    private final Supplier<Path> scenario;
    private final Examples examples;
    private final List<Runnable> hints = new CopyOnWriteArrayList<>();

    /**
     * @param scenario the current scenario: the anchor of a purpose with no file of its kinds yet, and the stem of the
     *                 names a save proposes
     */
    Navigator(FileTrail trail, Supplier<Path> scenario) {
        this(trail, scenario, Examples.near());
    }

    /** @param examples the example inputs a dialog and a field can point to */
    Navigator(FileTrail trail, Supplier<Path> scenario, Examples examples) {
        this.trail = trail;
        this.scenario = scenario;
        this.examples = examples;
        trail.addListener(() -> SwingUtilities.invokeLater(() -> hints.forEach(Runnable::run)));
    }

    FileTrail trail() {
        return trail;
    }

    Examples examples() {
        return examples;
    }

    /** The current scenario (of step 3), or null when there is none or its text names no path. */
    Path scenario() {
        try {
            return scenario.get();
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    /** The example of what a purpose opens (a scenario, the library, a metrics corpus), or null when there is none near. */
    Path example(Purpose purpose) {
        return switch (purpose) {
            case SCENARIO_OPEN, DOCUMENT_OPEN -> examples.scenario();
            case LIBRARY_OPEN -> examples.library();
            case RESULTS_OPEN -> examples.corpus();
            default -> null;
        };
    }

    // ---- places -----------------------------------------------------------------------------------------------------

    /** The place a purpose's dialog opens at now, given the file the view holds for it (or null). */
    Place place(Purpose purpose, Path current) {
        FileTrail.Seen newest = trail.newest(purpose.kinds());
        Path anchor = current != null ? current : scenario.get();
        Path anchorFolder = anchor == null ? null : Files.isDirectory(anchor) ? anchor : anchor.toAbsolutePath().getParent();
        if (purpose.open()) {
            if (newest != null) {
                return new Place(folderOf(newest.file()), newest.file(), "the newest " + newest.kind().label() + ", written "
                        + when(newest.modified()));
            }
            if (anchor != null && Files.exists(anchor)) {
                Path a = anchor.toAbsolutePath();
                if (current != null && purpose.kinds().isEmpty()) {
                    // a path field's own value (a program, a folder, a jar): shown selected in its folder
                    return new Place(a.getParent() == null ? a : a.getParent(), a, Files.isDirectory(a) ? "the folder in use" : "the file in use");
                }
                boolean ofKind = !Files.isDirectory(a) && purpose.kinds().contains(FileTrail.classify(a));
                return new Place(anchorFolder, ofKind ? a : null, ofKind ? "the file in use" : "the scenario's folder");
            }
            Path example = anchorFolder == null ? example(purpose) : null;
            if (example != null) {
                // nothing to go by yet (a first start): the example, selected, so Open is one click away
                return new Place(example.getParent(), example, "the example found nearby");
            }
            return new Place(anchorFolder != null ? anchorFolder : workingFolder(), null, anchorFolder != null ? "the scenario's folder"
                    : "the working folder");
        }
        Path folder = newest != null ? folderOf(newest.file()) : anchorFolder != null ? anchorFolder : workingFolder();
        String why = newest != null ? "where the last " + newest.kind().label() + " went" : anchorFolder != null ? "beside the scenario"
                : "the working folder";
        if (purpose == Purpose.DOCUMENT_SAVE) {
            if (current != null && !Files.isDirectory(current)) {
                return new Place(current.toAbsolutePath().getParent(), current.toAbsolutePath(), "the document's own file");
            }
            return new Place(folder, fresh(folder, "untitled.json"), why);
        }
        return new Place(folder, fresh(folder, stem(anchor) + purpose.suffix), why);
    }

    /**
     * A dialog at the purpose's place, filtered to its files (all files stay one choice away), titled with what it opens or
     * saves and beside it, what to pick in plain words with a button to the example when one is near.
     */
    JFileChooser chooser(Purpose purpose, Path current) {
        Place place = place(purpose, current);
        JFileChooser chooser = new JFileChooser(place.folder() == null ? null : place.folder().toFile());
        switch (purpose) {
            case LIBRARY_OPEN, ADDON_OPEN -> chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            case IMPORTS_OPEN, WORKDIR_OPEN -> chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            default -> { }
        }
        if (purpose.extensions.length > 0) {
            chooser.setFileFilter(new FileNameExtensionFilter(purpose.description, purpose.extensions));
        }
        if (place.file() != null) {
            chooser.setSelectedFile(place.file().toFile());
        }
        chooser.setDialogTitle((purpose.open() ? "Open " : "Save ") + purpose.what() + " · here: " + place.why());
        chooser.setAccessory(guide(purpose, chooser));
        return chooser;
    }

    /** The side panel of a dialog: what to pick, and a button that shows the example when one is near. */
    private JPanel guide(Purpose purpose, JFileChooser chooser) {
        StringBuilder html = new StringBuilder("<html><div style='width:190px'><b>What to pick</b>");
        for (String paragraph : purpose.guide().split("\n")) {
            html.append("<p style='margin-top:6px'>").append(escape(paragraph)).append("</p>");
        }
        JLabel text = new JLabel(html.append("</div></html>").toString());
        text.setVerticalAlignment(JLabel.TOP);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setName("What to pick");
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 4));
        panel.add(text, BorderLayout.CENTER);
        Path example = example(purpose);
        if (example != null) {
            JButton show = new JButton("Show the example");
            show.setToolTipText(example.toString());
            show.addActionListener(e -> {
                chooser.setCurrentDirectory(example.getParent().toFile());
                chooser.setSelectedFile(example.toFile());
            });
            panel.add(show, BorderLayout.SOUTH);
        }
        return panel;
    }

    /** Shows the purpose's dialog; the chosen file, recorded in the trail, or null when cancelled. */
    Path choose(Component parent, Purpose purpose, Path current) {
        JFileChooser chooser = chooser(purpose, current);
        int answer = purpose.open() ? chooser.showOpenDialog(parent) : chooser.showSaveDialog(parent);
        if (answer != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return null;
        }
        Path chosen = chooser.getSelectedFile().toPath();
        trail.record(chosen);
        return chosen;
    }

    // ---- assisted hints -----------------------------------------------------------------------------------------------

    /** Keeps a button's tooltip naming the place its dialog opens, as the trail learns. */
    void bind(AbstractButton button, Supplier<Purpose> purpose, Supplier<Path> current) {
        Runnable update = () -> button.setToolTipText(tooltip(purpose.get(), current.get()));
        hints.add(update);
        update.run();
    }

    String tooltip(Purpose purpose, Path current) {
        Place p = place(purpose, current);
        return "<html>" + (purpose.open() ? "Opens in " : "Saves in ") + escape(String.valueOf(p.folder()))
                + (p.file() == null ? "" : (purpose.open() ? "<br>at " : "<br>as ") + escape(p.file().getFileName().toString()))
                + "<br><i>Why here: " + escape(p.why()) + "</i></html>";
    }

    /**
     * A hint beside a file field: the newest file of the purpose's kinds when the field holds another, with a Use button
     * that puts it in the field; hidden while there is nothing better to offer.
     */
    JPanel hint(JTextComponent field, Purpose purpose) {
        JLabel text = new JLabel();
        text.setFont(text.getFont().deriveFont(Font.ITALIC));
        text.setForeground(new Color(0x57606a));
        JButton use = Hints.of(new JButton("Use"), "Put the suggested file into the box next to it.");
        use.setMargin(new java.awt.Insets(0, 6, 0, 6));
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        panel.add(text, BorderLayout.CENTER);
        panel.add(use, BorderLayout.EAST);
        Runnable update = () -> {
            FileTrail.Seen newest = trail.newest(purpose.kinds());
            String held = field.getText().strip();
            boolean offer = newest != null && (held.isEmpty() || !Path.of(held).toAbsolutePath().normalize().equals(newest.file()));
            panel.setVisible(offer);
            if (offer) {
                text.setText("newest " + newest.kind().label() + ": " + newest.file().getFileName() + " in " + newest.file().getParent()
                        + ", written " + when(newest.modified()));
                use.setToolTipText(newest.file().toString());
            }
        };
        use.addActionListener(e -> {
            FileTrail.Seen newest = trail.newest(purpose.kinds());
            if (newest != null) {
                field.setText(newest.file().toString());
                update.run();
            }
        });
        field.getDocument().addDocumentListener(FormulaEditor.listener(update));
        hints.add(update);
        update.run();
        return panel;
    }

    /** Runs every hint now (the trail's listener does it after each change). */
    void refreshHints() {
        hints.forEach(Runnable::run);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    private static Path folderOf(Path file) {
        return Files.isDirectory(file) ? file.getParent() == null ? file : file.getParent() : file.getParent();
    }

    private static Path workingFolder() {
        return Path.of("").toAbsolutePath();
    }

    /** The name a save starts from: the anchor's file name without its extensions, or "scenario". */
    static String stem(Path anchor) {
        if (anchor == null || anchor.getFileName() == null) {
            return "scenario";
        }
        String name = anchor.getFileName().toString();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** The name in a folder, or the first of name-2, name-3 ... that no file has yet. */
    static Path fresh(Path folder, String name) {
        Path candidate = folder.resolve(name);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = name.indexOf('.', Math.max(0, name.lastIndexOf('-')));
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; ; i++) {
            candidate = folder.resolve(base + "-" + i + ext);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }

    private static String when(long millis) {
        return millis <= 0 ? "at an unknown time" : "at " + TIME.format(Instant.ofEpochMilli(millis));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
