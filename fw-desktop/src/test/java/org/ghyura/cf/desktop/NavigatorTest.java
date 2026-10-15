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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GEN:operator-authoring-ui: every file button opens where the files of its kind already are, or are to be written,
 * and says so before the click (assisted hints).
 */
class NavigatorTest {

    private static <T> T edt(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    private static void until(BooleanSupplier condition, String what) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (!edt(condition::getAsBoolean)) {
            assertTrue(System.currentTimeMillis() < end, "not in time: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void openDialogsGoToTheNewestFileOfTheirKindAndSaveDialogsProposeAFreshName(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("shop.json"), "{\"sheets\": {\"FW_Seq\": []}}", StandardCharsets.UTF_8);
        try (FileTrail trail = FileTrail.inMemory()) {
            Navigator navigator = new Navigator(trail, () -> scenario);
            trail.record(scenario);
            trail.settle(5_000);

            // nothing of a kind known yet: its folder is the scenario's, and only a file of its kind is selected
            Navigator.Place results = navigator.place(Navigator.Purpose.RESULTS_OPEN, null);
            assertEquals(dir, results.folder());
            assertNull(results.file(), "a results dialog does not select the scenario");
            assertEquals(scenario, navigator.place(Navigator.Purpose.SCENARIO_OPEN, null).file());

            // a save is proposed beside the scenario, under a name that overwrites nothing
            Navigator.Place report = navigator.place(Navigator.Purpose.EXPORT_REPORT, null);
            assertEquals(dir.resolve("shop-report.xml"), report.file());
            Files.writeString(report.file(), "<cf-export kind=\"report\" schema=\"1\">", StandardCharsets.UTF_8);
            until(() -> report.file().equals(navigator.place(Navigator.Purpose.EXPORT_REPORT, null).folder().resolve("shop-report.xml"))
                    && navigator.place(Navigator.Purpose.EXPORT_REPORT, null).file().equals(dir.resolve("shop-report-2.xml")),
                    "the next report proposed as shop-report-2.xml once the first exists");
            assertEquals(dir.resolve("untitled.json"), navigator.place(Navigator.Purpose.DOCUMENT_SAVE, null).file());
            assertEquals(scenario, navigator.place(Navigator.Purpose.DOCUMENT_SAVE, scenario).file(), "a saved document saves to its file");
            assertEquals(dir.resolve("shop.graph.dot"), navigator.place(Navigator.Purpose.GRAPH_SAVE, null).file());

            // results exported elsewhere, by anyone: the analysis opens there, the file selected, and exports follow
            Path out = Files.createDirectories(dir.resolve("runs"));
            trail.watch(out);
            trail.settle(5_000);
            Path exported = Files.writeString(out.resolve("shop-results.csv"), "candidate_id,repeat_idx,env_id\n", StandardCharsets.UTF_8);
            until(() -> exported.equals(navigator.place(Navigator.Purpose.RESULTS_OPEN, null).file()), "the newest results file selected");
            assertEquals(out.resolve("shop-samples.csv"), navigator.place(Navigator.Purpose.EXPORT_SAMPLES, null).file(),
                    "the next results export goes where the last went");

            JFileChooser chooser = edt(() -> navigator.chooser(Navigator.Purpose.RESULTS_OPEN, null));
            assertEquals(out.toFile(), chooser.getCurrentDirectory());
            assertEquals(exported.toFile(), chooser.getSelectedFile());
            assertTrue(chooser.getDialogTitle().startsWith("Open: the newest results"), chooser.getDialogTitle());
        }
    }

    @Test
    void buttonsAndFieldsHintWhereTheFilesAreAndTheHintsFollowTheTrail(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("shop.json"), "{\"sheets\": {\"FW_Seq\": []}}", StandardCharsets.UTF_8);
        try (FileTrail trail = FileTrail.inMemory()) {
            Navigator navigator = edt(() -> new Navigator(trail, () -> scenario));
            trail.record(scenario);
            trail.settle(5_000);
            JButton browse = new JButton("Browse...");
            JTextField field = new JTextField();
            JPanel hint = edt(() -> {
                navigator.bind(browse, () -> Navigator.Purpose.RESULTS_OPEN, () -> null);
                return navigator.hint(field, Navigator.Purpose.RESULTS_OPEN);
            });
            String tooltip = edt(browse::getToolTipText);
            assertTrue(tooltip.contains("Opens in " + dir), tooltip);
            assertFalse(edt(hint::isVisible), "nothing to offer yet");

            Path exported = Files.writeString(dir.resolve("shop-results.csv"), "candidate_id,repeat_idx,env_id\n", StandardCharsets.UTF_8);
            until(() -> hint.isVisible() && browse.getToolTipText().contains("shop-results.csv"), "the hint and the tooltip follow the trail");
            JButton use = edt(() -> (JButton) hint.getComponent(1));
            edt(() -> {
                use.doClick();
                return null;
            });
            assertEquals(exported.toString(), edt(field::getText), "Use puts the newest file in the field");
            assertFalse(edt(hint::isVisible), "and the hint steps back: the field holds it");
        }
    }

    @Test
    void everyFileButtonOfTheDesktopOpensThroughTheNavigator(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("shop.json"), "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(1)\"]], \"A\": [[\"a\"]]}}",
                StandardCharsets.UTF_8);
        org.ghyura.cf.desktop.session.Session session = org.ghyura.cf.desktop.session.Session.resolve(null, java.util.Map.of(),
                java.util.List.of());
        Desktop desktop = edt(() -> new Desktop(session, scenario));
        java.util.List<String> bound = new java.util.ArrayList<>();
        edt(() -> {
            collect(desktop.component(), bound);
            return null;
        });
        assertEquals(java.util.List.of("Browse...", "Browse...", "Browse...", "Export CSV...", "Export...", "Load rules...", "Open a library...",
                "Open...", "Save DOT or JSON...", "Save as..."), bound.stream().sorted().toList(), "the file buttons, each with a hint of its place");
        assertEquals(dir.resolve("shop.json"), desktop.navigator().place(Navigator.Purpose.SCENARIO_OPEN, null).file());
        edt(desktop::close).get(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void collect(java.awt.Container c, java.util.List<String> out) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof JButton b && b.getToolTipText() != null && b.getToolTipText().startsWith("<html>")
                    && (b.getToolTipText().contains("Opens in") || b.getToolTipText().contains("Saves in"))) {
                out.add(b.getText());
            }
            if (child instanceof java.awt.Container inner) {
                collect(inner, out);
            }
        }
    }
}
