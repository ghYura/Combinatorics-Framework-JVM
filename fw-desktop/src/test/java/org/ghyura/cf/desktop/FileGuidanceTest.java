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
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A newcomer is told what file each field takes, and a file of the wrong kind is named for what it is, with what to choose
 * instead, before anything reads it.
 */
class FileGuidanceTest {

    private static final Examples NONE = new Examples(List.of());

    @Test
    void aFileThatIsNotAScenarioIsNamedForWhatItIsWithTheScenarioToChooseInstead(@TempDir Path dir) throws Exception {
        Path scenario = write(dir.resolve("shop.json"), "{\"sheets\": {\"FW_Seq\": []}}");
        Path rules = write(dir.resolve("shop.constraints.json"), "{\"version\": 1, \"constraints\": []}");
        Path settings = write(dir.resolve("shop.properties"), "cf.run.id=shop");
        Path metrics = write(dir.resolve("run.kv"), "candidate_id=a latency=1 FW_VAR=0\n");
        assertNull(ScenarioView.notAScenario(scenario));
        assertNull(ScenarioView.notAScenario(dir.resolve("missing.xlsx")), "validation reports a missing file");
        assertTrue(ScenarioView.notAScenario(rules).contains("holds the rules (constraints) of a scenario")
                && ScenarioView.notAScenario(rules).contains("(shop.json in the same folder)"), ScenarioView.notAScenario(rules));
        assertTrue(ScenarioView.notAScenario(settings).contains("(shop.json in the same folder)"), ScenarioView.notAScenario(settings));
        assertTrue(ScenarioView.notAScenario(metrics).contains("step 8"), ScenarioView.notAScenario(metrics));
        assertTrue(ScenarioView.notAScenario(dir).contains("is a folder"), ScenarioView.notAScenario(dir));

        // the scenario tab says so instead of validating the wrong file
        Session session = Session.resolve(null, Map.of(), List.of());
        try (FileTrail trail = FileTrail.inMemory()) {
            ScenarioView view = edt(() -> new ScenarioView(session, rules, new Navigator(trail, () -> rules, NONE)));
            assertNull(edt(view::validate).get(30, TimeUnit.SECONDS));
            assertTrue(edt(view::output).contains("Choose the scenario itself (shop.json in the same folder)"), edt(view::output));
        }
    }

    @Test
    void theAnalysisTakesMetricsAndRefusesARunsOtherFilesWithTheWayToItsMetrics(@TempDir Path dir) throws Exception {
        Path kv = write(dir.resolve("run-metrics.kv"), "candidate_id=a latency=1 FW_VAR=0\n");
        Path log = write(dir.resolve("sut.log"), "candidate_id=a latency=1 FW_VAR=0\n");
        Path table = write(dir.resolve("run-samples.csv"), "candidate_id,repeat_idx,env_id,attempt,outcome\n");
        Path attempts = write(dir.resolve("run-results.csv"), "run_id,candidate_id,attempt,outcome\n");
        Path report = write(dir.resolve("run.report"), "<?xml version=\"1.0\"?>\n<cf-export kind=\"report\" schema=\"1\">\n");
        Path checkpoint = write(dir.resolve("run.checkpoint"), "<cf-checkpoint schema=\"1\" payloadSha256=\"\">\n");
        assertNull(AnalysisView.notMetrics(kv));
        assertNull(AnalysisView.notMetrics(log), "a program's output with key=value lines is a corpus too");
        for (Path wrong : List.of(table, attempts, report, checkpoint)) {
            String why = AnalysisView.notMetrics(wrong);
            assertTrue(why != null && why.contains("Save as \"metrics for step 8 (.kv)\""), wrong + ": " + why);
        }
        assertTrue(AnalysisView.notMetrics(table).contains("is a table (CSV) saved in step 6"));
        assertTrue(AnalysisView.notMetrics(attempts).contains("is a table (CSV) saved in step 6"));
        assertTrue(AnalysisView.notMetrics(report).contains("is the summary report of a run"), "found by its content, not its name");
        assertTrue(AnalysisView.notMetrics(checkpoint).contains("is a checkpoint of a run"));

        // a run's export renamed to look like metrics is still that export, whatever the name says (D004)
        Path checkpointKv = write(dir.resolve("disguised.kv"), "<?xml version=\"1.0\"?>\n<cf-checkpoint schema=\"1\" payloadSha256=\"\">\n");
        Path tableKv = write(dir.resolve("table.kv"), "candidate_id,repeat_idx,env_id,attempt,outcome,verdict_code,latency\n1_0_0,0,,1,PASS,0,3\n");
        Path attemptsTxt = write(dir.resolve("attempts.txt"), "run_id,candidate_id,attempt,outcome\nr,1_0_0,1,PASS\n" + "x".repeat(300));
        assertTrue(AnalysisView.notMetrics(checkpointKv).contains("is a checkpoint of a run"), AnalysisView.notMetrics(checkpointKv));
        assertTrue(AnalysisView.notMetrics(tableKv).contains("is a table (CSV) saved in step 6"), AnalysisView.notMetrics(tableKv));
        assertTrue(AnalysisView.notMetrics(attemptsTxt).contains("is a table (CSV) saved in step 6"), AnalysisView.notMetrics(attemptsTxt));
        assertNull(AnalysisView.notMetrics(kv), "a real metrics file still passes");
        assertNull(AnalysisView.notMetrics(log), "and so does a program's output with key=value lines");
    }

    @Test
    void theLibraryTabShowsTheFirstStepsOpensTheExampleLibraryAndSaysWhenAFolderHoldsNoExamples(@TempDir Path dir) throws Exception {
        Path library = Files.createDirectories(dir.resolve(Examples.QA_EDITION).resolve("generator_trunk").resolve("intake")).getParent();
        write(library.resolve("intake").resolve("verified_examples.json"), "[]");
        Session session = Session.resolve(null, Map.of(), List.of());
        try (FileTrail trail = FileTrail.inMemory()) {
            LibraryView none = edt(() -> new LibraryView(session, chosen -> { }, new Navigator(trail, () -> null, NONE)));
            assertTrue(edt(none::details).startsWith("How to start\n1. Press \"Open a library...\""), edt(none::details));
            assertTrue(edt(none::where).startsWith("No library open yet: choose the generator_trunk folder"), edt(none::where));
            assertNull(button(edt(none::component), "Open the example library"), "no example library is near");
            edt(() -> {
                none.openSelected();
                return null;
            });
            assertTrue(edt(none::details).startsWith("Open a library first, then select an example in the table."), edt(none::details));
            Path empty = Files.createDirectories(dir.resolve("empty"));
            edt(() -> none.open(empty)).get(30, TimeUnit.SECONDS);
            assertTrue(edt(none::where).endsWith(": no examples here. A library is the generator_trunk folder that holds "
                    + "intake/verified_examples.json."), edt(none::where));

            LibraryView near = edt(() -> new LibraryView(session, chosen -> { }, new Navigator(trail, () -> null, new Examples(List.of(dir)))));
            assertTrue(edt(near::details).contains("1. Press \"Open the example library\": it opens " + library), edt(near::details));
            JButton bundled = button(edt(near::component), "Open the example library");
            assertEquals(library.toString(), bundled.getToolTipText());
            edt(() -> {
                bundled.doClick();
                return null;
            });
            long end = System.currentTimeMillis() + 10_000;
            while (!edt(near::where).startsWith(library.toString()) && System.currentTimeMillis() < end) Thread.sleep(20);
            assertTrue(edt(near::where).startsWith(library + ": no examples here"), edt(near::where));
        }
    }

    @Test
    void thePythonBoxesAreActiveOnlyForThePythonRunnerAndThePathBoxesHaveButtons() throws Exception {
        Session session = Session.resolve(null, Map.of(), List.of());
        try (FileTrail trail = FileTrail.inMemory()) {
            RunnerSettings settings = edt(() -> new RunnerSettings(session, null, new Navigator(trail, () -> null, NONE)));
            Container panel = edt(settings::component);
            JTextField interpreter = field(panel, "Python interpreter");
            JTextField addons = field(panel, "Add-on and dependency paths");
            List<AbstractButton> choosers = new ArrayList<>();
            for (String text : List.of("Add...", "Browse...")) choosers.addAll(buttons(panel, text));
            assertEquals(4, choosers.size(), "add-ons, interpreter, import folders and working folder");
            assertTrue(edt(addons::isEnabled) && !edt(interpreter::isEnabled), "generated-java needs no Python box");
            assertEquals(1, choosers.stream().filter(Component::isEnabled).count(), "only the add-ons button");
            assertFalse(edt(settings::python), "no Python add-on is installed here");
            assertTrue(edt(interpreter::isEnabled) && choosers.stream().allMatch(Component::isEnabled), "the Python runner uses them all");
            assertTrue(interpreter.getClientProperty("JTextField.placeholderText").toString().contains("python3"));
        }
    }

    private static Path write(Path file, String text) throws Exception {
        return Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static JTextField field(Component root, String name) {
        if (root instanceof JTextField text && name.equals(text.getAccessibleContext().getAccessibleName())) return text;
        if (root instanceof Container parent) {
            for (Component child : parent.getComponents()) {
                JTextField found = field(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton button(Component root, String text) {
        List<AbstractButton> found = buttons(root, text);
        return found.isEmpty() ? null : (JButton) found.getFirst();
    }

    private static List<AbstractButton> buttons(Component root, String text) {
        List<AbstractButton> out = new ArrayList<>();
        if (root instanceof AbstractButton b && text.equals(b.getText())) out.add(b);
        if (root instanceof Container parent) {
            for (Component child : parent.getComponents()) out.addAll(buttons(child, text));
        }
        return out;
    }

    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        SwingUtilities.invokeAndWait(task);
        return task.get(30, TimeUnit.SECONDS);
    }
}
