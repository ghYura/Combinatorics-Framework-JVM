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

package org.ghyura.cf.acceptance;

import org.ghyura.cf.app.Application;
import org.ghyura.cf.artifacts.LegacyHandoff;
import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Python handoffs use the same run service and preserve source, argv, preprocess and verdict semantics. */
class PythonHandoffRunTest {
    @BeforeAll
    static void needsPython() {
        boolean found = false;
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            found |= !dir.isEmpty() && Files.isExecutable(Path.of(dir, "python3"));
        }
        Assumptions.assumeTrue(found, "MISSING_AUTHORIZED_BACKEND: original Python execution requires python3");
    }

    private record Result(int code, String out, String err) { }

    private static Result cli(String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static List<String> tallies(Result result) throws Exception {
        assertEquals(0, result.code(), result.err());
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(result.out().getBytes(StandardCharsets.UTF_8))).getDocumentElement();
        var tallies = new ArrayList<String>();
        for (String tag : List.of("outcome", "verdict")) {
            var nodes = root.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                var e = (Element) nodes.item(i);
                tallies.add(tag + " " + e.getAttribute(tag.equals("outcome") ? "name" : "code") + " " + e.getAttribute("count"));
            }
        }
        return tallies;
    }

    @Test
    void exportedPythonRunsAgainWithItsArgumentsPreprocessAndCustomVerdicts(@TempDir Path dir) throws Exception {
        Path addon = Reactor.root().resolve("fw-python/target/fw-python-0.1.0-SNAPSHOT.jar");
        assertTrue(Files.isRegularFile(addon), "the explicit Python add-on is packaged");
        Files.writeString(dir.resolve("cf_handoff_helper.py"), "VALUE = 5\n");
        String head = "import sys, cf_handoff_helper\nassert sys.argv[2:] == ['x', 'two words']\n"
                + "assert cf_handoff_helper.VALUE == 5\nFW_VAR = 1\n";
        String first = "import sys\np = sys.argv[1]\ns = open(p, encoding='utf-8').read()\n"
                + "open(p, 'w', encoding='utf-8').write(s.replace('FW_VAR = 1', 'FW_VAR = 0'))\n";
        Map<String, Object> sheets = new LinkedHashMap<>();
        sheets.put("FW_Seq", List.of(List.of("BODY", "FW_Combi(1)")));
        sheets.put("BODY", List.of(List.of(head + "FW_CUSTOM_VAR = 0\n"), List.of(head + "FW_CUSTOM_VAR = 7\n")));
        sheets.put("FW_Arguments", List.of(List.of("x"), List.of("two words")));
        sheets.put("FW_CUSTOM_VAR", List.of(List.of(7, "BAD")));
        sheets.put("FW_RunMeFirstOnce", List.of(List.of(first)));
        Path scenario = Files.writeString(dir.resolve("python.json"), Json.write(Map.of("sheets", sheets)));
        Path export = dir.resolve("export");
        Result written = cli("candidates", "--scenario", scenario.toString(), "--set", "cf.execution.sut=python",
                "--set", "cf.execution.verdict=FW_CUSTOM_VAR", "--export", export.toString(), "--format", "xml");
        assertEquals(0, written.code(), written.err());
        var bundle = LegacyHandoff.read(export.resolve("manifest.json"));
        assertEquals(LegacyHandoff.Language.PYTHON, bundle.language());
        assertEquals(List.of("x", "two words"), bundle.arguments());
        assertEquals(Map.of(7, "BAD"), bundle.verdictNames());
        assertEquals(first, bundle.runFirstOnce().orElseThrow());
        Result direct = cli("run", "--scenario", scenario.toString(), "--sut", "python", "--set",
                "cf.execution.classpath=" + addon, "--set", "cf.execution.verdict=FW_CUSTOM_VAR", "--set",
                "cf.python.workdir=" + dir, "--set", "cf.python.path=" + dir, "--workers", "2", "--format", "xml");
        Result replay = cli("run", "--legacy-handoff", bundle.manifest().toString(), "--sut", "python", "--set",
                "cf.execution.classpath=" + addon, "--set", "cf.python.workdir=" + dir, "--set", "cf.python.path=" + dir,
                "--workers", "2", "--format", "xml");
        assertEquals(tallies(direct), tallies(replay));
        assertTrue(tallies(replay).contains("outcome PASS 1"), replay.out());
        assertTrue(tallies(replay).contains("outcome DOMAIN_FAIL 1"), replay.out());
        Result standard = cli("run", "--legacy-handoff", bundle.manifest().toString(), "--sut", "python", "--set",
                "cf.execution.classpath=" + addon, "--set", "cf.python.workdir=" + dir, "--set", "cf.python.path=" + dir,
                "--set", "cf.execution.verdict=FW_VAR", "--format", "xml");
        assertTrue(tallies(standard).contains("outcome PASS 2"), standard.out());
    }
}
