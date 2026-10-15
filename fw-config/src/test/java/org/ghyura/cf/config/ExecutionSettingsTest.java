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

package org.ghyura.cf.config;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings of work package M04 (run id, SUT, extra class path, verdict field) and the import of the legacy settings
 * M04 owns, read the way the legacy code read them.
 */
class ExecutionSettingsTest {

    private static RunConfig resolve(String... overrides) {
        List<ConfigResolver.Override> list = new java.util.ArrayList<>();
        for (String o : overrides) {
            int eq = o.indexOf('=');
            list.add(new ConfigResolver.Override(o.substring(0, eq), o.substring(eq + 1), "--set"));
        }
        return ConfigResolver.resolve(null, Map.of(), list).config();
    }

    private static LegacyImport of(String text) {
        return LegacyImporter.importSources(List.of(new LegacyImporter.Source("fw.properties", text)), Map.of());
    }

    private static Map<String, String> overrides(LegacyImport imported) {
        Map<String, String> map = new LinkedHashMap<>();
        for (LegacyImport.TargetValue v : imported.overrides()) {
            if (v.where().isKnown()) {
                map.put(v.key(), v.value());
            }
        }
        return map;
    }

    @Test
    void theDefaultsRunGeneratedJavaUnderAFixedRunIdReadingFwVar() {
        RunConfig c = RunConfig.defaults();
        assertEquals("run", c.runId());
        assertEquals(RunConfig.GENERATED_JAVA, c.executionSut());
        assertEquals(List.of(), c.executionClasspath());
        assertEquals(RunConfig.VerdictField.FW_VAR, c.executionVerdict());
        RunConfig set = resolve("cf.run.id=nightly-7", "cf.execution.sut=tutor", "cf.execution.verdict=FW_CUSTOM_VAR",
                "cf.execution.classpath= a.jar " + File.pathSeparator + File.pathSeparator + "lib/b.jar");
        assertEquals("nightly-7", set.runId());
        assertEquals("tutor", set.executionSut());
        assertEquals(List.of("a.jar", "lib/b.jar"), set.executionClasspath(), "entries trimmed, empty ones dropped");
        assertEquals(RunConfig.VerdictField.FW_CUSTOM_VAR, set.executionVerdict());
        for (String bad : List.of("cf.run.id=a b", "cf.run.id=", "cf.execution.sut=x/y", "cf.execution.verdict=FW_EXIT_CODE")) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> resolve(bad), bad);
            assertEquals(ErrorCode.CONFIG_INVALID_VALUE, e.diagnostics().getFirst().code(), bad);
        }
    }

    @Test
    void theM04LegacySettingsImportAsTheLegacyReadThem() {
        assertEquals(Map.of("cf.execution.verdict", "FW_CUSTOM_VAR"), overrides(of("results.db.onlyFW_CUSTOM_VAR=true\n")));
        assertEquals(Map.of("cf.execution.verdict", "FW_VAR"), overrides(of("results.db.onlyFW_CUSTOM_VAR=false\n")));
        LegacyImport yes = of("results.db.onlyFW_CUSTOM_VAR=yes\n");
        assertEquals(Map.of("cf.execution.verdict", "FW_VAR"), overrides(yes), "Boolean.parseBoolean read 'yes' as false");
        assertTrue(yes.diagnostics().stream().map(Diagnostic::code).toList().contains(ErrorCode.CONFIG_LEGACY_COERCED));
        assertEquals(Map.of("cf.run.id", "nightly-7"), overrides(of("reader.handoff.runId= nightly-7\n")));
        LegacyImport blank = of("reader.handoff.runId=\n");
        assertEquals(Map.of(), overrides(blank), "blank minted an id per run in the legacy; this build keeps its fixed default");
        assertEquals(Map.of("cf.execution.classpath", "a.jar" + File.pathSeparator + "b.jar"),
                overrides(of("fw.cp=a.jar" + File.pathSeparator + "b.jar\n")));
        LegacyImport layout = of("results.db.onlyFW_EXIT_CODEcolumns=true\nresults.db.customvarmapCreateTable=true\n");
        assertEquals(List.of(), layout.pending(), "no results table layout is left to choose");
        assertEquals(List.of(LegacySetting.Disposition.SUPERSEDED, LegacySetting.Disposition.SUPERSEDED), layout.entries().stream()
                .filter(e -> e.key().startsWith("results.db.")).map(LegacyImport.Entry::disposition).toList());
    }
}
