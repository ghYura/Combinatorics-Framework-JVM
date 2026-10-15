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
import org.ghyura.cf.desktop.session.Session;

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

/** Desktop runner choices. Each apply creates an immutable session; already prepared operations keep theirs. */
final class RunnerSettings {
    private final Session base;
    private volatile Session current;
    private final Path installedPython;
    private final JComboBox<String> runner;
    private final Map<String, JTextField> fields = new LinkedHashMap<>();
    private final List<JLabel> summaries = new ArrayList<>();
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JLabel status = new JLabel("Settings apply to the next operation. Prepared runs keep their settings.");

    RunnerSettings(Session base) {
        this(base, installedPython());
    }

    RunnerSettings(Session base, Path installedPython) {
        this.base = base;
        this.current = base;
        this.installedPython = installedPython;
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
        field(1, "cf.execution.classpath", "Add-on and dependency paths:",
                "Jars or directories, separated by '" + File.pathSeparator + "'. Filled from the current configuration or by typing paths; "
                + "the installed Python jar is added when Python is chosen. Press Apply runner settings.");
        field(2, "cf.python.executable", "Python interpreter:",
                "The Python executable or its full path. Filled from the current configuration or by typing; empty uses python3. Press Apply runner settings.");
        field(3, "cf.python.path", "Python import directories:",
                "Directories for Python imports, separated by '" + File.pathSeparator + "'. Filled from the current configuration or by typing; "
                + "empty uses the scenario's defaults and inherited import paths. Press Apply runner settings.");
        field(4, "cf.python.workdir", "Python working directory:",
                "Where Python test cases work. Filled from the current configuration or by typing; leave empty to use the scenario's "
                + "directory or its generator tree. Press Apply runner settings.");
        field(5, "cf.python.env", "Python environment variables:",
                "NAME=VALUE assignments separated by ';'. Filled from the current configuration or by typing. They affect only each "
                + "Python child process. Press Apply runner settings.");
        field(6, "cf.python.sandbox", "Python sandbox:",
                "Where each Python test case runs. Filled from the current configuration or by typing: empty runs it directly; bwrap "
                + "isolates it with a read-only file system and no network; bwrap-network keeps the network; docker:IMAGE or "
                + "docker:IMAGE@NETWORK runs it in a read-only container. Press Apply runner settings.");
        JButton apply = Hints.of(new JButton("Apply runner settings"), "Use these settings for the next validation, candidate preview, run or campaign. "
                + "An already started run keeps its settings. Problems are shown here.");
        apply.addActionListener(e -> apply());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bottom.add(apply);
        bottom.add(status);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 7; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        panel.add(bottom, c);
    }

    Session session() { return current; }
    JComponent component() { return panel; }
    String status() { return status.getText(); }

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
            status.setText("Applied: " + id + ". Prepared runs keep their settings.");
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

    private void field(int row, String key, String name, String hint) {
        JTextField field = Hints.of(new JTextField(40), hint);
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
