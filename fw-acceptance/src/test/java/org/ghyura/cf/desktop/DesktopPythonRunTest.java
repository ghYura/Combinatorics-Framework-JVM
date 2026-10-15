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

import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.config.ConfigResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.*;

/** A two-case Python library example exercises the actual desktop controls, imports, environment, workdir and run. */
class DesktopPythonRunTest {
    @Test
    void libraryPythonChoiceAndAppliedFieldsReachPreviewAndRuns(@TempDir Path dir) throws Exception {
        Path library = dir.resolve("examples"), work = dir.resolve("work"), imports = dir.resolve("imports");
        Files.createDirectories(library.resolve("intake")); Files.createDirectories(work); Files.createDirectories(imports);
        Files.writeString(imports.resolve("codex_gui_helper.py"), "VALUE = 7\n");
        String source = "import codex_gui_helper, os\n"
                + "FW_VAR = 0 if codex_gui_helper.VALUE == 7 and os.getenv('CODEX_GUI_VALUE') == 'child only' and os.path.isfile('marker') else 1\n";
        Files.writeString(work.resolve("marker"), "present");
        Path scenario = Files.writeString(library.resolve("example.toml"), "[[slots]]\nsheet = \"A\"\nraw = true\nvalues = ['''" + source
                + "''', '''" + source + "''']\n");
        Files.writeString(library.resolve("intake/verified_examples.json"), "[{\"id\":\"tiny-python\",\"spec\":\"example.toml\","
                + "\"title\":\"Tiny Python\",\"candidates\":\"2\",\"pass\":\"2\"}]");
        String pathBefore = System.getenv("PATH"), pythonPathBefore = System.getenv("PYTHONPATH");
        Desktop desktop = edt(() -> new Desktop(Session.resolve(null, Map.of(), List.of()), null));
        try {
            edt(() -> desktop.library().open(library)).get(30, TimeUnit.SECONDS);
            edt(() -> {
                field(desktop.runners().component(), "Python interpreter").setText(System.getProperty("cf.python.executable", "python3"));
                field(desktop.runners().component(), "Python import directories").setText(imports.toString());
                field(desktop.runners().component(), "Python working directory").setText(work.toString());
                field(desktop.runners().component(), "Python environment variables").setText("CODEX_GUI_VALUE=child only");
                assertTrue(desktop.runners().python(), desktop.runners().status());
                desktop.library().openSelected();
                return null;
            });
            assertEquals(scenario.toRealPath(), edt(() -> desktop.scenario().scenario()));
            var preview = desktop.runners().session().preview(scenario, new org.ghyura.cf.desktop.session.CandidatePreview.Request(
                    java.math.BigInteger.TEN, java.math.BigInteger.ZERO, 2, true, true), org.ghyura.cf.spi.Cancellation.NONE);
            assertTrue(preview.page().rows().getFirst().source().contains("\nFW_VAR"), "Python line breaks survive the preview");
            edt(() -> desktop.run().start()).get(30, TimeUnit.SECONDS);
            waitForRun(desktop);
            assertTrue(desktop.run().controller().view().report().contains("PASS 2"), desktop.run().controller().view().report());
            edt(() -> {
                field(desktop.runners().component(), "Python environment variables").setText("CODEX_GUI_VALUE=changed");
                assertTrue(desktop.runners().apply()); return null;
            });
            edt(() -> desktop.run().start()).get(30, TimeUnit.SECONDS);
            waitForRun(desktop);
            assertTrue(desktop.run().controller().view().report().contains("DOMAIN_FAIL 2"), desktop.run().controller().view().report());
            assertEquals(pathBefore, System.getenv("PATH")); assertEquals(pythonPathBefore, System.getenv("PYTHONPATH"));
            assertNull(System.getenv("CODEX_GUI_VALUE"));
        } finally { edt(desktop::close).get(30, TimeUnit.SECONDS); }
    }

    private static void waitForRun(Desktop desktop) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < end) {
            RunController.View view = desktop.run().controller().view();
            if (view.state() == RunState.COMPLETED && !view.report().isEmpty()) return;
            Thread.sleep(20);
        }
        fail("Python desktop run did not finish: " + desktop.run().controller().view());
    }

    private static JTextField field(Component component, String name) {
        if (component instanceof JTextField text && name.equals(text.getAccessibleContext().getAccessibleName())) return text;
        if (component instanceof Container parent) for (Component child : parent.getComponents()) {
            JTextField found = find(child, name); if (found != null) return found;
        }
        throw new IllegalArgumentException("No field: " + name);
    }

    private static JTextField find(Component component, String name) {
        if (component instanceof JTextField text && name.equals(text.getAccessibleContext().getAccessibleName())) return text;
        if (component instanceof Container parent) for (Component child : parent.getComponents()) {
            JTextField found = find(child, name); if (found != null) return found;
        }
        return null;
    }

    private static <T> T edt(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task); SwingUtilities.invokeAndWait(future); return future.get();
    }
}
