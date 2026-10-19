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

import org.ghyura.cf.config.CapabilityCatalog;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.FileSeeker;
import org.ghyura.cf.desktop.session.Session;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Desktop runner choices. Each apply creates an immutable session; already prepared operations keep theirs. The Python
 * boxes are active only while the Python runner is chosen; with a navigator, the path boxes have buttons that choose them.
 */
final class RunnerSettings {
    /** The keys only the Python runner reads. */
    private static final List<String> PYTHON = List.of("cf.python.executable", "cf.python.path", "cf.python.workdir", "cf.python.env",
            "cf.python.sandbox");

    private final Session base;
    private volatile Session current;
    private final Path installedPython;
    private final Navigator navigator;
    private final JComboBox<String> runner;
    private final Map<String, JTextField> fields = new LinkedHashMap<>();
    private final Map<String, List<JButton>> choosers = new LinkedHashMap<>();
    private final List<JLabel> summaries = new ArrayList<>();
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JLabel status = new JLabel("Settings apply to the next operation. Prepared runs keep their settings.");

    RunnerSettings(Session base) {
        this(base, installedPython());
    }

    RunnerSettings(Session base, Path installedPython) {
        this(base, installedPython, null);
    }

    /** Settings whose path boxes have buttons that choose the paths through the navigator's dialogs. */
    static RunnerSettings withChoosers(Session base, Navigator navigator) {
        return new RunnerSettings(base, installedPython(), navigator);
    }

    /** @param navigator opens the dialogs of the path boxes' buttons; null shows no buttons */
    RunnerSettings(Session base, Path installedPython, Navigator navigator) {
        this.base = base;
        this.current = base;
        this.installedPython = installedPython;
        this.navigator = navigator;
        LinkedHashSet<String> ids = new LinkedHashSet<>(List.of("generated-java", "python", base.configuration().config().executionSut()));
        try {
            CapabilityCatalog.load(base.configuration().config().executionClasspath()).plugins().forEach(p -> ids.add(p.id()));
        } catch (org.ghyura.cf.model.FrameworkException ignored) {
            // Existing invalid class paths stay editable; preparing a run reports the problem.
        }
        runner = Hints.of(new JComboBox<>(ids.toArray(String[]::new)),
                "Choose what runs the test cases: Java in this JVM, Python through its installed add-on, or a named plugin. Press Apply runner settings.");
        runner.setSelectedItem(base.configuration().config().executionSut());
        row(0, "Runner:", runner);
        JLabel note = FieldNames.note("The default runner, generated-java, needs nothing below. The Python boxes are used only "
                + "when the runner is python.");
        GridBagConstraints n = new GridBagConstraints();
        n.gridx = 1; n.gridy = 1; n.gridwidth = GridBagConstraints.REMAINDER; n.anchor = GridBagConstraints.WEST;
        n.insets = new java.awt.Insets(0, 4, 4, 4);
        panel.add(note, n);
        String separated = "separated by '" + File.pathSeparator + "'";
        field(2, "cf.execution.classpath", "Add-on and dependency paths:",
                "Jars or directories, " + separated + ". Filled from the current configuration, by typing paths or with Add...; "
                + "the installed Python jar is added when Python is chosen. Press Apply runner settings.",
                "empty: only the built-in runners; jars or class folders, " + separated);
        field(3, "cf.python.executable", "Python interpreter:",
                "The Python executable or its full path. Filled from the current configuration, by typing or with Browse...; empty "
                + "uses python3. Press Apply runner settings.", "empty: python3; or a full path such as /usr/bin/python3");
        field(4, "cf.python.path", "Python import directories:",
                "Directories for Python imports, " + separated + ". Filled from the current configuration, by typing or with Add...; "
                + "empty uses the scenario's defaults and inherited import paths. Press Apply runner settings.",
                "empty: the scenario's defaults; folders of your Python modules, " + separated);
        field(5, "cf.python.workdir", "Python working directory:",
                "Where Python test cases work. Filled from the current configuration, by typing or with Browse...; leave empty to use "
                + "the scenario's directory or its generator tree. Press Apply runner settings.", "empty: the scenario's folder");
        field(6, "cf.python.env", "Python environment variables:",
                "NAME=VALUE assignments separated by ';'. Filled from the current configuration or by typing. They affect only each "
                + "Python child process. Press Apply runner settings.", "for example: MODE=fast;LEVEL=2");
        field(7, "cf.python.sandbox", "Python sandbox:",
                "Where each Python test case runs. Filled from the current configuration or by typing: empty runs it directly; bwrap "
                + "isolates it with a read-only file system and no network; bwrap-network keeps the network; docker:IMAGE or "
                + "docker:IMAGE@NETWORK runs it in a read-only container. Press Apply runner settings.",
                "empty: runs directly; or bwrap, bwrap-network, docker:IMAGE");
        // which combinations become test cases: the covering selection, which the wizard (step 0) fills in too
        field(8, "cf.coverage.strength", "Covering strength t:",
                "Which combinations become test cases: 0 runs every one, 2 keeps enough that every pair of values of any two sheets "
                + "appears (pairwise), 3 every triple. Filled from the current configuration, by typing, or by the wizard of step 0. "
                + "Press Apply runner settings.", "0: every combination; 2: pairwise; 3: every triple");
        field(9, "cf.coverage.budget", "Most covering rows:",
                "When more than 0, the strongest covering that keeps at most this many rows is chosen, and the strength above is not "
                + "used. Filled from the current configuration, by typing, or by the wizard of step 0. Press Apply runner settings.",
                "0: no budget; or the most rows to keep, such as 100");
        field(10, "cf.coverage.method", "Covering method:",
                "How the covering rows are chosen: greedy keeps a row when it covers something new, set-cover takes the row that "
                + "covers most each time and usually keeps fewer. Filled from the current configuration, by typing, or by the wizard of "
                + "step 0. Press Apply runner settings.", "greedy or set-cover");
        if (navigator != null) {
            chooser(2, "cf.execution.classpath", "Add...", Navigator.Purpose.ADDON_OPEN, true,
                    "Choose a jar or a class folder to add to the add-on and dependency paths.");
            chooser(3, "cf.python.executable", "Browse...", Navigator.Purpose.PROGRAM_OPEN, false,
                    "Choose the Python program that runs the test cases.");
            chooser(4, "cf.python.path", "Add...", Navigator.Purpose.IMPORTS_OPEN, true,
                    "Choose a folder of Python modules to add to the import directories.");
            chooser(5, "cf.python.workdir", "Browse...", Navigator.Purpose.WORKDIR_OPEN, false,
                    "Choose the folder where each Python test case starts.");
            AutoSearch search = new AutoSearch(navigator);
            searcher(2, "cf.execution.classpath", search, FileSeeker.Kind.ADDON, true,
                    "Search this application, this checkout and your folders for runner add-ons, then pick one to add.");
            searcher(3, "cf.python.executable", search, FileSeeker.Kind.PYTHON, false,
                    "Search your PATH and your virtual environments for Python interpreters, then pick one of them.");
            searcher(4, "cf.python.path", search, FileSeeker.Kind.PYTHON_MODULES, true,
                    "Search around the step 3 scenario for folders of Python modules, then pick one to add.");
            searcher(5, "cf.python.workdir", search, FileSeeker.Kind.PYTHON_WORKDIR, false,
                    "List the folders a Python test case can start in, the scenario's own first, then pick one.");
        }
        runner.addActionListener(e -> pythonBoxes());
        pythonBoxes();
        JButton apply = Hints.of(new JButton("Apply runner settings"), "Use these settings for the next validation, candidate preview, run or campaign. "
                + "An already started run keeps its settings. Problems are shown here.");
        apply.addActionListener(e -> apply());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bottom.add(apply);
        bottom.add(status);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 11; c.gridwidth = GridBagConstraints.REMAINDER; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(bottom, c);
        panel.setBorder(BorderFactory.createTitledBorder("Runner settings (optional): what runs the test cases, and which of them"));
    }

    /** The Python boxes and their buttons are active only while the Python runner is chosen. */
    private void pythonBoxes() {
        boolean python = "python".equals(runner.getSelectedItem());
        for (String key : PYTHON) {
            fields.get(key).setEnabled(python);
            for (JButton button : choosers.getOrDefault(key, List.of())) button.setEnabled(python);
        }
    }

    /** An Auto-search… button beside a path box: the found path replaces it, or joins its list. */
    private void searcher(int row, String key, AutoSearch search, FileSeeker.Kind kind, boolean append, String hint) {
        JTextField field = fields.get(key);
        JButton button = search.button(() -> kind, () -> held(field.getText()), chosen -> {
            String held = field.getText().strip();
            field.setText(append && !held.isEmpty() ? held + File.pathSeparator + chosen : chosen.toString());
        }, hint);
        choosers.computeIfAbsent(key, k -> new ArrayList<>()).add(button);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 3; c.gridy = row; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new java.awt.Insets(2, 0, 2, 4);
        panel.add(button, c);
    }

    /** A button beside a path box that fills it with a dialog: the chosen path replaces it, or joins its list. */
    private void chooser(int row, String key, String text, Navigator.Purpose purpose, boolean append, String hint) {
        JTextField field = fields.get(key);
        JButton button = Hints.of(new JButton(text), hint);
        button.addActionListener(e -> {
            Path chosen = navigator.choose(panel, purpose, start(purpose, field.getText()));
            if (chosen == null) return;
            String held = field.getText().strip();
            field.setText(append && !held.isEmpty() ? held + File.pathSeparator + chosen : chosen.toString());
        });
        navigator.bind(button, () -> purpose, () -> start(purpose, field.getText()));
        choosers.computeIfAbsent(key, k -> new ArrayList<>()).add(button);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 2; c.gridy = row; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new java.awt.Insets(2, 0, 2, 4);
        panel.add(button, c);
    }

    /** Where a path box's dialog starts: its last path when that exists; the interpreter found on PATH; else null. */
    private Path start(Navigator.Purpose purpose, String value) {
        String[] entries = value.strip().split(java.util.regex.Pattern.quote(File.pathSeparator));
        String last = entries[entries.length - 1].strip();
        try {
            if (!last.isEmpty() && Files.exists(Path.of(last))) return Path.of(last).toAbsolutePath();
            if (purpose == Navigator.Purpose.PROGRAM_OPEN) return onPath(last.isEmpty() ? "python3" : last);
            if (purpose == Navigator.Purpose.ADDON_OPEN && installedPython != null && installedPython.getParent() != null
                    && Files.isDirectory(installedPython.getParent())) {
                return installedPython.getParent();
            }
        } catch (java.nio.file.InvalidPathException ignored) {
            // text that names no path opens where the purpose opens without one
        }
        return null;
    }

    /** The last path a box holds when it exists (where a search starts), else null. */
    private static Path held(String value) {
        String[] entries = value.strip().split(java.util.regex.Pattern.quote(File.pathSeparator));
        String last = entries[entries.length - 1].strip();
        try {
            return last.isEmpty() || !Files.exists(Path.of(last)) ? null : Path.of(last).toAbsolutePath();
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    /** The program of that name on the PATH, or null. */
    private static Path onPath(String program) {
        if (program.contains(File.separator)) return null;
        for (String folder : System.getenv().getOrDefault("PATH", "").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (folder.isBlank()) continue;
            Path candidate = Path.of(folder, program);
            if (Files.isExecutable(candidate) && !Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }

    Session session() { return current; }
    JComponent component() { return panel; }
    String status() { return status.getText(); }

    /** The covering selection a design chose (the wizard's), applied with the runner settings shown; false with the reason in the status. */
    boolean select(Map<String, String> selection) {
        selection.forEach((key, value) -> fields.get(key).setText(value));
        return apply();
    }

    /** Explicit Python selection from Run or Library; imports no interpreter and starts no process. */
    boolean python() {
        runner.setSelectedItem("python");
        return apply();
    }

    boolean apply() {
        try {
            List<ConfigResolver.Override> overrides = new ArrayList<>();
            String id = (String) runner.getSelectedItem();
            put(overrides, "cf.execution.sut", id);
            for (var e : fields.entrySet()) put(overrides, e.getKey(), e.getValue().getText());
            Session chosen = Session.of(ConfigResolver.derive(base.configuration(), overrides));
            if ("python".equals(id)) {
                String cp = fields.get("cf.execution.classpath").getText();
                boolean present = CapabilityCatalog.load(chosen.configuration().config().executionClasspath()).plugins().stream()
                        .anyMatch(p -> p.id().equals("python"));
                if (!present) {
                    if (installedPython == null || !Files.isRegularFile(installedPython)) {
                        throw new IllegalArgumentException("Choose the installed addons/fw-python.jar in Add-on and dependency paths.");
                    }
                    cp = cp.isBlank() ? installedPython.toString() : cp + File.pathSeparator + installedPython;
                    overrides.add(new ConfigResolver.Override("cf.execution.classpath", cp, "desktop runner"));
                }
                chosen = Session.of(ConfigResolver.derive(base.configuration(), overrides));
                fields.get("cf.execution.classpath").setText(cp);
            }
            current = chosen;
            for (JLabel summary : summaries) summary.setText("Runner: " + id);
            var config = chosen.configuration().config();
            String covering = config.coverageBudget() > 0 ? ", covering within " + config.coverageBudget() + " rows"
                    : config.coverageStrength() > 0 ? ", covering t = " + config.coverageStrength() : "";
            status.setText("Applied: " + id + covering + ". Prepared runs keep their settings.");
            return true;
        } catch (RuntimeException failure) {
            status.setText("Settings could not be applied: " + Background.describe(failure).strip());
            return false;
        }
    }

    private void put(List<ConfigResolver.Override> overrides, String key, String value) {
        String original = base.configuration().entries().stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow().rawValue();
        if (!original.equals(value)) overrides.add(new ConfigResolver.Override(key, value, "desktop runner"));
    }

    JComponent compact(Runnable openSettings, Runnable afterPython) {
        JPanel box = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel summary = new JLabel("Runner: " + current.configuration().config().executionSut());
        summaries.add(summary);
        JButton python = Hints.of(new JButton("Use Python"), "Choose the Python runner with the settings of step 3. The installed Python add-on "
                + "must be available. Applying settings starts no test cases.");
        python.addActionListener(e -> {
            if (python()) afterPython.run();
            else openSettings.run();
        });
        JButton settings = Hints.of(new JButton("Runner settings (step 3)"), "Go to step 3 to choose the runner, its add-on paths, and the Python "
                + "interpreter, import directories, working directory and environment variables.");
        settings.addActionListener(e -> openSettings.run());
        box.add(summary); box.add(python); box.add(settings);
        return box;
    }

    static void attach(JComponent view, JComponent controls) {
        BorderLayout layout = (BorderLayout) view.getLayout();
        java.awt.Component old = layout.getLayoutComponent(BorderLayout.NORTH);
        JPanel header = new JPanel(new BorderLayout(4, 4));
        if (old != null) header.add(old, BorderLayout.NORTH);
        header.add(controls, BorderLayout.SOUTH);
        view.add(header, BorderLayout.NORTH);
    }

    private void field(int row, String key, String name, String hint, String placeholder) {
        JTextField field = FieldNames.placeholder(Hints.of(new JTextField(40), hint), placeholder);
        field.setText(base.configuration().entries().stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow().rawValue());
        fields.put(key, field);
        row(row, name, field);
    }

    private void row(int row, String name, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row; c.anchor = GridBagConstraints.WEST; c.insets = new java.awt.Insets(2, 4, 2, 4);
        panel.add(FieldNames.label(name, field), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }

    private static Path installedPython() {
        String explicit = System.getProperty("cf.python.jar", "").trim();
        if (!explicit.isEmpty()) return Path.of(explicit).toAbsolutePath().normalize();
        try {
            Path source = Path.of(RunnerSettings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(source) ? source.getParent().resolve("addons/fw-python.jar") : null;
        } catch (java.net.URISyntaxException | RuntimeException ignored) {
            return null;
        }
    }
}
