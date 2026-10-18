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
import org.ghyura.cf.desktop.session.FileSeeker.Found;
import org.ghyura.cf.desktop.session.FileSeeker.Kind;
import org.ghyura.cf.desktop.session.FileSeeker.Root;
import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every file field has an Auto-search… button that looks where its kind of file is likely and lists what it finds. */
class AutoSearchTest {

    @Test
    void eachKindSearchesTheLikeliestFoldersFirstFromWhatTheDesktopKnows(@TempDir Path dir) throws Exception {
        Path home = Files.createDirectories(dir.resolve("home"));
        Files.createDirectories(home.resolve("Documents"));
        Path checkout = Files.createDirectories(home.resolve("work/checkout/fw-app")).getParent();
        Files.writeString(checkout.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);
        Path tree = Files.createDirectories(home.resolve("work/qa/generator_trunk"));
        Files.writeString(tree.resolve("sut_paths.py"), "# tree marker\n", StandardCharsets.UTF_8);
        Path scenario = Files.createDirectories(tree.resolve("scenarios/demo")).resolve("scenario.toml");
        Files.writeString(scenario, "spec_version = \"1\"\n[[slots]]\nsheet = \"A\"\n", StandardCharsets.UTF_8);
        Files.writeString(home.resolve("Documents/old.toml"), "[[slots]]\n", StandardCharsets.UTF_8);
        try (FileTrail trail = FileTrail.inMemory()) {
            Navigator navigator = new Navigator(trail, () -> scenario, new Examples(List.of(checkout)));
            AutoSearch search = new AutoSearch(navigator, home, FileSeeker.Budget.DEFAULT);

            List<Root> roots = search.roots(Kind.SCENARIO, null);
            assertEquals(new Root(scenario.getParent(), 1, 0, "the step 3 scenario's folder"), roots.getFirst());
            Map<String, Path> why = new LinkedHashMap<>();
            roots.forEach(r -> why.putIfAbsent(r.why(), r.folder()));
            assertEquals(tree, why.get("the scenario's generator tree"));
            assertEquals(checkout, why.get("this checkout"));
            assertEquals(checkout.getParent(), why.get("the checkouts next to this one"));
            assertEquals(home.resolve("Documents"), why.get("your Documents folder"));
            assertEquals(home, roots.getLast().folder(), "the home folder is the widest and least likely place");

            Root workdir = search.roots(Kind.PYTHON_WORKDIR, null).getFirst();
            assertTrue(workdir.itself() && workdir.folder().equals(scenario.getParent()), "the scenario's folder is offered itself, first");
            Root python = search.roots(Kind.PYTHON, null).getFirst();
            assertEquals("on your PATH", python.why());

            FileSeeker.Result result = search.search(Kind.SCENARIO, null, () -> false, n -> { }, r -> { }).get(30, TimeUnit.SECONDS);
            assertEquals(List.of(scenario, home.resolve("Documents/old.toml")), result.found().stream().map(Found::path).toList(),
                    "the step 3 scenario's folder first, the documents later");
        }
    }

    @Test
    void theResultsListTheLikeliestFirstNarrowByTheFilterAndHandOverTheChosenOne(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("shop.xlsx");
        Path b = dir.resolve("specs/pricing.toml");
        FileSeeker.Result result = new FileSeeker.Result(Kind.SCENARIO, List.of(
                new Found(a, "the step 3 scenario's folder", "workbook with an FW_Seq sheet", 1_000, 0, 0),
                new Found(b, "this checkout", "spec with slots", 2_000, 2, 1)), 2, 42, true, 120, List.of(new Root(dir, 1, 0, "here")));
        AtomicReference<Path> chosen = new AtomicReference<>();
        AutoSearch.Results results = edt(() -> new AutoSearch.Results(Kind.SCENARIO, chosen::set, () -> { }, () -> { }));
        assertTrue(edt(results::status).startsWith("Searching the folders where scenario files usually are"));
        edt(() -> {
            results.show(result);
            return null;
        });
        assertTrue(edt(results::status).startsWith("Found 2 scenario files in 42 folders"), edt(results::status));
        assertEquals(2, edt(() -> results.table().getRowCount()));
        assertEquals(a, edt(results::selected).path(), "the likeliest is selected, so Enter uses it");
        edt(() -> {
            results.filter().setText("PRICING");
            return null;
        });
        assertEquals(1, edt(() -> results.table().getRowCount()), "the filter ignores case");
        edt(() -> {
            results.table().setRowSelectionInterval(0, 0);
            results.use.doClick();
            return null;
        });
        assertEquals(b, chosen.get());
        edt(() -> {
            results.show(new FileSeeker.Result(Kind.SCENARIO, List.of(), 0, 7, true, 5, List.of()));
            return null;
        });
        assertTrue(edt(results::status).startsWith("No scenario files found in 7 folders"), edt(results::status));
    }

    @Test
    void everyTabWithFileFieldsHasAutoSearchButtonsThatSayWhatTheyDo() throws Exception {
        Session session = Session.resolve(null, Map.of(), List.of());
        Desktop desktop = edt(() -> new Desktop(session, null, FileTrail.inMemory(), true));
        try {
            Map<String, Integer> perTab = edt(() -> {
                Map<String, Integer> counts = new LinkedHashMap<>();
                JTabbedPane tabs = desktop.tabs();
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    List<AbstractButton> found = new java.util.ArrayList<>();
                    buttons(tabs.getComponentAt(i), found);
                    for (AbstractButton b : found) {
                        String hint = Hints.own(b);
                        assertTrue(hint != null && hint.endsWith(".") && hint.split(" ").length >= 4, "a hint in plain words: " + hint);
                    }
                    if (!found.isEmpty()) counts.put(tabs.getTitleAt(i), found.size());
                }
                return counts;
            });
            // the scenario tab: the scenario file and the runner's add-ons, interpreter, import folders and working folder
            assertEquals(Map.of("1 · Library", 1, "2 · Authoring", 1, "3 · Scenario", 5, "4 · Constraints / Sieve", 1,
                    "8 · Results analysis", 2, "9 · Text scorer", 2), perTab);
        } finally {
            edt(desktop::close).get(30, TimeUnit.SECONDS);
        }
    }

    private static void buttons(Component c, List<AbstractButton> out) {
        if (c instanceof AbstractButton b && "Auto-search…".equals(b.getText())) out.add(b);
        if (c instanceof Container parent) {
            for (Component child : parent.getComponents()) buttons(child, out);
        }
    }

    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        SwingUtilities.invokeAndWait(task);
        return task.get(30, TimeUnit.SECONDS);
    }
}
