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

import org.ghyura.cf.desktop.session.FileSeeker;
import org.ghyura.cf.desktop.session.FileSeeker.Kind;
import org.ghyura.cf.desktop.session.FileSeeker.Root;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The Auto-search… buttons beside the file fields: each searches the folders where files of its field's kind are most
 * likely, the likeliest first (the folder of what the field holds, the step 3 scenario's folder and its generator tree,
 * folders used before, this checkout, the checkouts next to it, your documents, your home folder; for an interpreter
 * your PATH and virtual environments), off the event thread for a few seconds at most, and lists what it found with
 * why each one is there. The chosen one fills the field (or is opened). Nothing found is run.
 */
final class AutoSearch {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    /** The kinds the file trail knows, so a choice of them is remembered for the hints and the next dialog. */
    private static final Set<Kind> REMEMBERED = Set.of(Kind.SCENARIO, Kind.LIBRARY, Kind.CONSTRAINTS, Kind.METRICS, Kind.TEXT, Kind.RULES);

    private final Navigator navigator;
    private final Path home;
    private final FileSeeker.Budget budget;

    AutoSearch(Navigator navigator) {
        this(navigator, Path.of(System.getProperty("user.home")), FileSeeker.Budget.DEFAULT);
    }

    /** @param home the user's home folder, the widest place searched */
    AutoSearch(Navigator navigator, Path home, FileSeeker.Budget budget) {
        this.navigator = navigator;
        this.home = home.toAbsolutePath().normalize();
        this.budget = budget;
    }

    /**
     * An Auto-search… button.
     *
     * @param kind    what it looks for, asked at the click; null does nothing (the field is not ready)
     * @param current what the field holds now, or null
     * @param use     takes the chosen path
     */
    JButton button(Supplier<Kind> kind, Supplier<Path> current, Consumer<Path> use, String hint) {
        JButton button = Hints.of(new JButton("Auto-search…"), hint);
        button.addActionListener(e -> {
            Kind k = kind.get();
            if (k != null && !GraphicsEnvironment.isHeadless()) show(button, k, current.get(), use);
        });
        return button;
    }

    /** Searches the kind's likely folders off the event thread; done gets the result on the event thread. */
    CompletableFuture<FileSeeker.Result> search(Kind kind, Path current, BooleanSupplier stop, IntConsumer progress,
                                                Consumer<FileSeeker.Result> done) {
        List<Root> roots = roots(kind, current);
        return Background.run("auto-search", () -> FileSeeker.seek(kind, roots, budget, stop, progress), done, failure -> { });
    }

    /** The search window over the button: progress, then what was found; the chosen one goes to use. */
    private void show(Component parent, Kind kind, Path current, Consumer<Path> use) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Auto-search · " + kind.what(), Dialog.ModalityType.APPLICATION_MODAL);
        AtomicBoolean stopped = new AtomicBoolean();
        Results results = new Results(kind, chosen -> {
            dialog.dispose();
            if (REMEMBERED.contains(kind)) navigator.trail().record(chosen);
            use.accept(chosen);
        }, () -> stopped.set(true), () -> {
            stopped.set(true);
            dialog.dispose();
        });
        dialog.setContentPane(results.component());
        dialog.getRootPane().setDefaultButton(results.use);
        dialog.getRootPane().registerKeyboardAction(e -> results.close.doClick(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { stopped.set(true); }
        });
        dialog.setSize(980, 520);
        dialog.setLocationRelativeTo(owner);
        search(kind, current, stopped::get, n -> SwingUtilities.invokeLater(() -> results.searching(n)), results::show);
        dialog.setVisible(true);
    }

    // ---- where to look -------------------------------------------------------------------------------------------------

    /** The folders searched for the kind, likeliest first, each with how deep and why. */
    List<Root> roots(Kind kind, Path current) {
        Path field = folderOf(current);
        Path scenario = navigator.scenario();
        Path scenarioFolder = scenario == null ? null : folderOf(scenario.toAbsolutePath());
        Path tree = tree(scenarioFolder);
        Path checkout = navigator.examples().checkout().toAbsolutePath().normalize();
        Path beside = checkout.getParent() == null || checkout.getParent().getParent() == null ? null : checkout.getParent();
        Path work = Path.of("").toAbsolutePath();
        List<Path> used = new ArrayList<>(navigator.trail().folders());
        Collections.reverse(used);
        List<Root> roots = new ArrayList<>();
        switch (kind) {
            case SCENARIO, CONSTRAINTS -> {
                add(roots, field, 1, 0, "the folder of the file in the box");
                add(roots, scenarioFolder, 1, 0, "the step 3 scenario's folder");
                used.stream().limit(8).forEach(f -> add(roots, f, 1, 1, "a folder you used"));
                add(roots, tree, 4, 1, "the scenario's generator tree");
                add(roots, checkout, 5, 2, "this checkout");
                add(roots, beside, 5, 3, "the checkouts next to this one");
                documents(roots, 3, 4);
                add(roots, home, 3, 5, "your home folder");
            }
            case LIBRARY -> {
                add(roots, checkout, 2, 1, "this checkout");
                add(roots, beside, 3, 1, "the checkouts next to this one");
                documents(roots, 3, 3);
                add(roots, home, 4, 4, "your home folder");
            }
            case METRICS -> {
                add(roots, field, 1, 0, "the folder of the file in the box");
                used.stream().limit(8).forEach(f -> add(roots, f, 1, 0, "a folder you used (runs save their metrics there)"));
                add(roots, scenarioFolder, 2, 1, "the step 3 scenario's folder");
                add(roots, work, 2, 1, "the working folder");
                add(roots, tree, 4, 2, "the scenario's generator tree");
                add(roots, checkout, 5, 2, "this checkout");
                add(roots, beside, 5, 3, "the checkouts next to this one");
                documents(roots, 3, 4);
                add(roots, home, 3, 5, "your home folder");
            }
            case JACOCO, COVERAGE_PY, GCOV -> {
                add(roots, field, 1, 0, "the folder of the file in the box");
                add(roots, scenarioFolder, 2, 1, "the step 3 scenario's folder");
                add(roots, tree, 6, 1, "the scenario's generator tree");
                add(roots, work, 6, 1, "the working folder");
                add(roots, checkout, 6, 2, "this checkout (coverage tools write into its build output)");
                add(roots, beside, 6, 3, "the checkouts next to this one");
                documents(roots, 3, 4);
                add(roots, home, 3, 5, "your home folder");
            }
            case TEXT -> {
                add(roots, field, 1, 0, "the folder of the file in the box");
                used.stream().limit(8).forEach(f -> add(roots, f, 1, 1, "a folder you used"));
                add(roots, scenarioFolder, 2, 1, "the step 3 scenario's folder");
                add(roots, work, 4, 2, "the working folder");
                add(roots, checkout, 5, 2, "this checkout");
                add(roots, beside, 4, 3, "the checkouts next to this one");
                documents(roots, 3, 3);
                add(roots, home, 2, 5, "your home folder");
            }
            case RULES -> {
                add(roots, field, 1, 0, "the folder of the file in the box");
                used.stream().limit(8).forEach(f -> add(roots, f, 1, 1, "a folder you used"));
                add(roots, checkout, 5, 2, "this checkout");
                add(roots, beside, 5, 2, "the checkouts next to this one");
                documents(roots, 3, 3);
                add(roots, home, 3, 5, "your home folder");
            }
            case ADDON -> {
                Path application = Examples.applicationFolder();
                add(roots, application == null ? null : application.resolve("addons"), 1, 0, "the add-ons folder of this application");
                add(roots, field, 1, 0, "the folder of the file in the box");
                add(roots, application, 1, 1, "this application's folder");
                add(roots, checkout, 3, 1, "this checkout's build output");
                add(roots, beside, 4, 3, "the checkouts next to this one");
                add(roots, home, 3, 5, "your home folder");
            }
            case PYTHON -> {
                for (String folder : System.getenv().getOrDefault("PATH", "").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                    if (!folder.isBlank()) add(roots, Path.of(folder), 0, 0, "on your PATH");
                }
                add(roots, home.resolve(".venvs"), 2, 1, "your virtual environments");
                add(roots, home.resolve(".virtualenvs"), 2, 1, "your virtual environments");
                add(roots, home.resolve(".pyenv").resolve("versions"), 3, 1, "the Pythons pyenv installed");
                add(roots, home.resolve(".local").resolve("python"), 3, 1, "the Pythons installed for you");
                for (String conda : new String[]{"miniconda3", "anaconda3", "miniforge3", "mambaforge"}) {
                    add(roots, home.resolve(conda), 3, 1, "conda");
                }
                add(roots, tree, 2, 2, "the scenario's generator tree");
                add(roots, checkout, 2, 2, "this checkout");
                add(roots, beside, 3, 2, "the checkouts next to this one");
                documents(roots, 3, 3);
                add(roots, home, 3, 4, "your home folder");
            }
            case PYTHON_MODULES -> {
                add(roots, scenarioFolder, 1, 0, "the step 3 scenario's folder");
                add(roots, tree, 2, 0, "the scenario's generator tree");
                add(roots, field, 1, 1, "the folder in the box");
                add(roots, work, 2, 1, "the working folder");
                add(roots, beside, 3, 2, "the checkouts next to this one");
            }
            case PYTHON_WORKDIR -> {
                addItself(roots, scenarioFolder, 0, "the step 3 scenario's folder");
                addItself(roots, tree, 0, "the scenario's generator tree");
                addItself(roots, work, 1, "the working folder");
                add(roots, tree, 2, 2, "inside the scenario's generator tree");
                add(roots, beside, 3, 3, "the checkouts next to this one");
            }
        }
        return roots;
    }

    private void documents(List<Root> roots, int depth, int rank) {
        for (String name : new String[]{"Documents", "Downloads", "Desktop"}) {
            add(roots, home.resolve(name), depth, rank, "your " + name + " folder");
        }
    }

    /** Adds a root once (the first, likeliest, reason wins), when it is a folder. */
    private static void add(List<Root> roots, Path folder, int depth, int rank, String why) {
        if (folder == null || !Files.isDirectory(folder)) return;
        Path f = folder.toAbsolutePath().normalize();
        if (roots.stream().anyMatch(r -> r.folder().equals(f) && r.depth() >= depth)) return;
        roots.add(new Root(f, depth, rank, why));
    }

    /** Adds a folder that is itself offered (a working folder), searched no deeper. */
    private static void addItself(List<Root> roots, Path folder, int rank, String why) {
        if (folder == null || !Files.isDirectory(folder)) return;
        Path f = folder.toAbsolutePath().normalize();
        if (roots.stream().anyMatch(r -> r.folder().equals(f) && r.itself())) return;
        roots.add(new Root(f, 0, rank, why, true));
    }

    /** The folder a path names: itself when it is a folder, else its parent; null for none. */
    private static Path folderOf(Path path) {
        if (path == null) return null;
        Path p = path.toAbsolutePath().normalize();
        return Files.isDirectory(p) ? p : p.getParent();
    }

    /** The generator tree around a scenario's folder: the nearest folder above it with sut_paths.py or intake; else null. */
    static Path tree(Path folder) {
        int levels = 0;
        for (Path p = folder; p != null && levels < 8; p = p.getParent(), levels++) {
            if (Files.isRegularFile(p.resolve("sut_paths.py")) || Files.isDirectory(p.resolve("intake"))) return p;
        }
        return null;
    }

    // ---- the window's content ----------------------------------------------------------------------------------------

    /** What a search shows: its progress, then what it found, likeliest first and filterable; a row is used by Use or a double click. */
    static final class Results {
        private final Kind kind;
        private final JLabel status = new JLabel(" ");
        private final JTextField filter;
        private final ListTableModel<FileSeeker.Found> model = new ListTableModel<>(List.of("Name", "What it is", "Written", "Why there", "Folder"),
                List.of(f -> f.path().getFileName() == null ? f.path().toString() : f.path().getFileName().toString(), FileSeeker.Found::note,
                        f -> f.modified() <= 0 ? "" : WHEN.format(Instant.ofEpochMilli(f.modified())), FileSeeker.Found::why,
                        f -> f.path().getParent() == null ? "" : f.path().getParent().toString()));
        private final JTable table;
        private final JLabel looked = FieldNames.note(" ");
        final JButton use = Hints.of(new JButton("Use the selected"), "Put the selected file into the box and close this window.");
        final JButton stop = Hints.of(new JButton("Stop"), "Stop searching and keep what was found so far.");
        final JButton close = Hints.of(new JButton("Close"), "Close this window without choosing anything.");
        private final JPanel panel = new JPanel(new BorderLayout(6, 6));
        private List<FileSeeker.Found> all = List.of();

        Results(Kind kind, Consumer<Path> chosen, Runnable stopping, Runnable closing) {
            this.kind = kind;
            filter = FieldNames.placeholder(Hints.of(new JTextField(24), "Show only the rows whose name or folder holds this text. "
                    + "Filled by you: type part of a name or a folder."), "part of a name or folder");
            table = ColumnFit.install(Hints.of(new JTable(model), "What the search found, the likeliest first: its name, folder, what it "
                    + "is, when it was written and why it was looked for there. Double-click one to use it."));
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getSelectionModel().addListSelectionListener(e -> use.setEnabled(table.getSelectedRow() >= 0));
            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) use.doClick();
                }
            });
            use.setEnabled(false);
            use.addActionListener(e -> {
                FileSeeker.Found f = selected();
                if (f != null) chosen.accept(f.path());
            });
            stop.addActionListener(e -> {
                stopping.run();
                stop.setEnabled(false);
            });
            close.addActionListener(e -> closing.run());
            filter.getDocument().addDocumentListener(FormulaEditor.listener(this::refilter));
            JPanel top = new JPanel(new BorderLayout(6, 4));
            top.add(status, BorderLayout.NORTH);
            JPanel filterRow = new JPanel(new BorderLayout(6, 0));
            filterRow.add(FieldNames.label("Show only:", filter), BorderLayout.WEST);
            filterRow.add(filter, BorderLayout.CENTER);
            top.add(filterRow, BorderLayout.SOUTH);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.add(stop);
            buttons.add(use);
            buttons.add(close);
            JPanel bottom = new JPanel(new BorderLayout(6, 4));
            bottom.add(looked, BorderLayout.CENTER);
            bottom.add(buttons, BorderLayout.SOUTH);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            panel.add(top, BorderLayout.NORTH);
            panel.add(TableTitles.titled(table, "Found " + kind.what() + ", likeliest first"), BorderLayout.CENTER);
            panel.add(bottom, BorderLayout.SOUTH);
            searching(0);
        }

        JComponent component() {
            return panel;
        }

        /** While the search goes: how many folders it walked. */
        void searching(int folders) {
            if (stop.isEnabled() || folders == 0) {
                status.setText("Searching the folders where " + kind.what() + " usually are… " + folders + " folders so far.");
            }
        }

        /** The search ended: what it found, with where it looked. */
        void show(FileSeeker.Result result) {
            all = result.found();
            stop.setEnabled(false);
            String seconds = String.format(Locale.ROOT, "%.1f", result.millis() / 1000.0);
            status.setText(all.isEmpty()
                    ? "No " + kind.what() + " found in " + result.folders() + " folders (" + seconds + " s). Use Browse... beside the box to look elsewhere."
                    : "Found " + result.total() + " " + kind.what() + " in " + result.folders() + " folders (" + seconds + " s)"
                    + (result.total() > all.size() ? "; the likeliest " + all.size() + " are listed" : ", the likeliest first")
                    + (result.complete() ? "." : "; the search stopped early, so a few folders were left out."));
            // where it looked: each reason once on the line, every folder with its reason in the tooltip
            looked.setText("Looked in, the likeliest first: " + String.join(" · ", result.roots().stream().map(Root::why).distinct().toList())
                    + ". Rest the pointer here for the folders.");
            StringBuilder folders = new StringBuilder("<html>");
            for (Root r : result.roots()) {
                folders.append(escape(r.why())).append(": ").append(escape(r.folder().toString())).append("<br>");
            }
            looked.setToolTipText(folders.append("</html>").toString());
            refilter();
            if (model.getRowCount() > 0) table.setRowSelectionInterval(0, 0);
        }

        private void refilter() {
            String text = filter.getText().strip().toLowerCase(Locale.ROOT);
            model.show(text.isEmpty() ? all : all.stream().filter(f -> f.path().toString().toLowerCase(Locale.ROOT).contains(text)).toList());
        }

        FileSeeker.Found selected() {
            int row = table.getSelectedRow();
            return row < 0 ? null : model.row(table.convertRowIndexToModel(row));
        }

        String status() {
            return status.getText();
        }

        JTextField filter() {
            return filter;
        }

        JTable table() {
            return table;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
