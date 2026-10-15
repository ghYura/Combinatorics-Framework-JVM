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

import org.ghyura.cf.config.ConfigOrigin;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RunnerSettingsTest {
    private static Session session() { return Session.resolve(null, Map.of(), List.of()); }

    @Test
    void absentPythonAddonIsReportedWithoutChangingTheSession() {
        Session original = session();
        RunnerSettings settings = new RunnerSettings(original, null);
        assertFalse(settings.python());
        assertSame(original, settings.session());
        assertTrue(settings.status().contains("addons/fw-python.jar"), settings.status());
    }

    @Test
    void failedApplyKeepsThePreviousSessionAndBlankDefaultsKeepTheirOrigins(@TempDir Path dir) throws Exception {
        RunnerSettings settings = new RunnerSettings(session(), null);
        assertTrue(settings.apply());
        Session original = settings.session();
        assertEquals(ConfigOrigin.DEFAULT, original.configuration().entries().stream()
                .filter(e -> e.key().equals("cf.python.workdir")).findFirst().orElseThrow().origin());
        field(settings.component(), "Python environment variables").setText("invalid assignment");
        assertFalse(settings.apply());
        assertSame(original, settings.session());
        field(settings.component(), "Python environment variables").setText("CODEX_GUI_VALUE=hello world");
        field(settings.component(), "Python working directory").setText(dir.toString());
        assertTrue(settings.apply());
        assertEquals(dir.toString(), settings.session().configuration().config().pythonWorkdir());
        assertEquals(List.of("CODEX_GUI_VALUE=hello world"), settings.session().configuration().config().pythonEnv());
        assertEquals("", original.configuration().config().pythonWorkdir());
        assertNull(System.getenv("CODEX_GUI_VALUE"));
        assertEquals(ConfigOrigin.DEFAULT, settings.session().configuration().entries().stream()
                .filter(e -> e.key().equals("cf.execution.workers")).findFirst().orElseThrow().origin());
        Path tree = dir.resolve("generator_trunk"); Files.createDirectories(tree.resolve("nested"));
        Files.writeString(tree.resolve("sut_paths.py"), "# tree marker, never executed\n");
        Path scenario = Files.writeString(tree.resolve("nested/example.toml"), "[[slots]]\nsheet = \"A\"\nvalues = [\"FW_VAR = 0\"]\n");
        RunnerSettings defaults = new RunnerSettings(session(), null);
        assertTrue(defaults.apply());
        assertEquals(tree.toString(), defaults.session().configurationFor(scenario).config().pythonWorkdir());
        assertEquals(ConfigOrigin.SCENARIO, defaults.session().configurationFor(scenario).entries().stream()
                .filter(e -> e.key().equals("cf.python.workdir")).findFirst().orElseThrow().origin());
    }

    static JTextField field(Component component, String name) {
        if (component instanceof JTextField text && name.equals(text.getAccessibleContext().getAccessibleName())) return text;
        if (component instanceof Container parent) for (Component child : parent.getComponents()) {
            JTextField found = fieldOrNull(child, name); if (found != null) return found;
        }
        throw new IllegalArgumentException("No field: " + name);
    }

    private static JTextField fieldOrNull(Component component, String name) {
        if (component instanceof JTextField text && name.equals(text.getAccessibleContext().getAccessibleName())) return text;
        if (component instanceof Container parent) for (Component child : parent.getComponents()) {
            JTextField found = fieldOrNull(child, name); if (found != null) return found;
        }
        return null;
    }
}
