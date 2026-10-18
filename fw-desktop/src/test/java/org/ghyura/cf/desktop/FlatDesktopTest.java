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

import com.formdev.flatlaf.FlatLightLaf;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FlatDesktopTest {
    @Test
    void flatDesktopRunsTheSharedWorkflowWithTheSieveWorkbench(@TempDir Path dir) throws Exception {
        var previous = UIManager.getLookAndFeel();
        String nativeSetting = System.getProperty("flatlaf.useNativeLibrary");
        String animations = System.getProperty("flatlaf.animation");
        Session session = Session.resolve(null, Map.of(), List.of(
                new ConfigResolver.Override("cf.execution.sut", "inline-metrics", "test"),
                new ConfigResolver.Override("cf.execution.workers", "1", "test")));
        Path scenario = Files.writeString(dir.resolve("metrics.json"), """
                {"sheets":{"FW_Seq":[["P","FW_Combi(1)"]],
                "P":[["latency=10; throughput=40;"],["latency=20; throughput=30;"],
                     ["latency=30; throughput=20;"],["latency=40; throughput=10;"]]}}
                """);
        Desktop flat = null;
        try {
            Desktop original = edt(() -> new Desktop(session, scenario));
            try {
                assertSame(previous, UIManager.getLookAndFeel(), "gui does not install a new look and feel");
                assertEquals(9, edt(() -> original.tabs().getTabCount()));
                assertNotNull(original.constraints(), "the existing constraints editor remains in gui");
            } finally { edt(original::close).get(30, TimeUnit.SECONDS); }
            flat = edt(() -> {
                FlatDesktopLauncher.installLookAndFeel();
                return new Desktop(session, scenario, FileTrail.inMemory(), true);
            });
            Desktop desktop = flat;
            assertInstanceOf(FlatLightLaf.class, UIManager.getLookAndFeel());
            assertEquals("false", System.getProperty("flatlaf.useNativeLibrary"));
            assertNull(desktop.constraints(), "guif uses its Sieve workbench");
            assertNotNull(desktop.sieve());
            edt(() -> {
                assertEquals(9, desktop.tabs().getTabCount());
                assertEquals("3 · Scenario", desktop.tabs().getTitleAt(desktop.tabs().getSelectedIndex()));
                assertTrue(desktop.tabs().getUI().getClass().getName().startsWith("com.formdev.flatlaf."));
                for (int i = 0; i < desktop.tabs().getTabCount(); i++) {
                    JComponent tab = (JComponent) desktop.tabs().getComponentAt(i);
                    assertFalse(Boolean.TRUE.equals(tab.getClientProperty("cf.desktop.stub")));
                }
                return null;
            });
            edt(() -> desktop.scenario().validate()).get(30, TimeUnit.SECONDS);
            assertTrue(edt(() -> desktop.scenario().output()).contains("plan"));
            var preview = edt(() -> desktop.candidates().preview()).get(30, TimeUnit.SECONDS);
            assertEquals(4, preview.page().rows().size());
            edt(() -> desktop.run().start()).get(30, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (desktop.run().controller().view().state() != RunState.COMPLETED && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(RunState.COMPLETED, desktop.run().controller().view().state());
            assertEquals(4, desktop.run().controller().view().committed());
            Path results = dir.resolve("results.csv");
            edt(() -> desktop.run().export("results", results)).get(30, TimeUnit.SECONDS);
            assertEquals(5, Files.readAllLines(results).size());
        } finally {
            if (flat != null) edt(flat::close).get(30, TimeUnit.SECONDS);
            edt(() -> { UIManager.setLookAndFeel(previous); return null; });
            restore("flatlaf.useNativeLibrary", nativeSetting);
            restore("flatlaf.animation", animations);
        }
    }

    /** The author's rule: every table of the FlatLaf desktop sets odd rows apart from even ones, whatever its columns hold. */
    @Test
    void everyTableOfTheFlatDesktopTintsItsOddRows() throws Exception {
        var previous = UIManager.getLookAndFeel();
        String nativeSetting = System.getProperty("flatlaf.useNativeLibrary");
        String animations = System.getProperty("flatlaf.animation");
        Session session = Session.resolve(null, Map.of(), List.of());
        Desktop flat = null;
        try {
            flat = edt(() -> {
                FlatDesktopLauncher.installLookAndFeel();
                return new Desktop(session, null, FileTrail.inMemory(), true);
            });
            Desktop desktop = flat;
            List<String> plain = edt(() -> {
                List<String> problems = new java.util.ArrayList<>();
                List<javax.swing.JTable> tables = new java.util.ArrayList<>();
                tables(desktop.component(), tables);
                assertTrue(tables.size() >= 12, "tables found: " + tables.size());
                Map<Class<?>, Object> samples = Map.of(Object.class, "text", Integer.class, 7, Double.class, 0.5, Boolean.class, true);
                for (javax.swing.JTable table : tables) {
                    samples.forEach((type, value) -> {
                        var renderer = table.getDefaultRenderer(type);
                        var even = renderer.getTableCellRendererComponent(table, value, false, false, 0, 0).getBackground();
                        var odd = renderer.getTableCellRendererComponent(table, value, false, false, 1, 0).getBackground();
                        if (even.equals(odd)) problems.add(type.getSimpleName() + " cells of " + Hints.own(table));
                        else assertEquals(java.awt.Color.decode(FlatDesktopLauncher.ALTERNATE_ROW), odd);
                    });
                }
                return problems;
            });
            assertEquals(List.of(), plain, "tables whose odd and even rows look alike");
        } finally {
            if (flat != null) edt(flat::close).get(30, TimeUnit.SECONDS);
            edt(() -> { UIManager.setLookAndFeel(previous); return null; });
            restore("flatlaf.useNativeLibrary", nativeSetting);
            restore("flatlaf.animation", animations);
        }
    }

    private static void tables(Component component, List<javax.swing.JTable> out) {
        if (component instanceof javax.swing.JTable table) out.add(table);
        if (component instanceof Container parent) {
            for (Component child : parent.getComponents()) tables(child, out);
        }
    }

    private static boolean hasAction(Component component) {
        if (component instanceof AbstractButton) return true;
        if (component instanceof Container parent) {
            for (Component child : parent.getComponents()) if (hasAction(child)) return true;
        }
        return false;
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }

    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        SwingUtilities.invokeAndWait(task);
        return task.get(30, TimeUnit.SECONDS);
    }
}
