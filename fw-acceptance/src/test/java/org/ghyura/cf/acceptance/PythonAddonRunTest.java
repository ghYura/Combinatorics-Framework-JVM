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

import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Outcome;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Python add-on runs an original demo's own Python unchanged (C07, author decision 2026-10-12): the unchanged D10
 * scenario, whose candidates inline D10's audited sut.py, oracle.py and runtime.py, runs through the framework with
 * cf.execution.sut=python, and every one of the 3072 records the original Python printed agrees, field by field, with
 * the independent golden the Java port of D10 is held to. The Python is the oracle here; the Java port is not used.
 */
class PythonAddonRunTest {

    /** The SHA-256 of the three sources D10 inlines, as its build_spec.py recorded them. */
    private static final Map<String, String> D10_SOURCES = Map.of(
            "sut", "852d78e2a07ec29a4536282fef549fa9fb07536cd555eae37c90f40958fb2b52",
            "oracle", "92f396396dc74c924552fbdb1e8b567baaec6a1139d50d3ae9cd2f0c4ed32a79",
            "runtime", "9a4f9372f224f8960240c6f6a045cea23957a870ac09647ac06da22389da23f4");

    @BeforeAll
    static void needsPython() {
        boolean found = false;
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            found |= !dir.isEmpty() && Files.isExecutable(Path.of(dir, "python3"));
        }
        Assumptions.assumeTrue(found, "MISSING_AUTHORIZED_BACKEND: no python3 on PATH, so the Python add-on the author requires "
                + "(decision 2026-10-12) cannot be verified here");
    }

    private static Map<String, Map<String, String>> golden() throws Exception {
        var f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element root;
        try (var in = Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d10-golden.xml"))) {
            root = f.newDocumentBuilder().parse(in).getDocumentElement();
        }
        Map<String, Map<String, String>> cases = new TreeMap<>();
        var nodes = root.getElementsByTagName("case");
        for (int i = 0; i < nodes.getLength(); i++) {
            var c = (Element) nodes.item(i);
            Map<String, String> fields = new TreeMap<>();
            var fs = c.getElementsByTagName("field");
            for (int j = 0; j < fs.getLength(); j++) {
                var e = (Element) fs.item(j);
                fields.put(e.getAttribute("name"), e.getTextContent());
            }
            cases.put(c.getAttribute("id"), fields);
        }
        return cases;
    }

    /** A record value in the golden's text: lists as Python writes them, [1, 3, 4]. */
    private static String text(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(PythonAddonRunTest::text).collect(Collectors.joining(", ", "[", "]"));
        }
        return String.valueOf(value);
    }

    private static Path spec(Path dir) throws Exception {
        Path spec = dir.resolve("d10.toml");
        try (var in = PythonAddonRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/demos/d10/spec.toml")) {
            assertNotNull(in);
            Files.write(spec, in.readAllBytes());
        }
        return spec;
    }

    @Test
    void theUnchangedD10RunsItsOwnPythonAndEveryRecordAgreesWithTheIndependentGolden(@TempDir Path dir) throws Exception {
        var config = ConfigResolver.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.execution.sut", "python", "test"),
                new ConfigResolver.Override("cf.execution.workers", "8", "test"),
                new ConfigResolver.Override("cf.generation.distinctTables", "true", "original Subsets to Subsets distinctification")));
        Map<String, Map<String, String>> expected = golden();
        assertEquals(3072, expected.size());
        Map<String, Map<String, Object>> records = new TreeMap<>();
        try (var run = RunService.prepare(ScenarioLoader.load(spec(dir)).spec(), config)) {
            var s = run.await();
            assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
            assertEquals(BigInteger.valueOf(3072), s.generated());
            assertEquals(3072, s.samples());
            assertEquals(3072, s.count(Outcome.PASS), "the original Python judged every case");
            for (var o : run.finals()) {
                assertEquals(Outcome.PASS, o.outcome(), o.failureMessage());
                Map<String, String> a = o.domain().orElseThrow().attributes();
                assertEquals("d10_sensors", a.get("app"), "the legacy metrics line of the original runtime.py: " + a);
                byte[] raw = Base64.getUrlDecoder().decode(a.get("rec"));
                assertEquals(a.get("rec_sha256"), Canonical.sha256Hex(raw), "the record is the one its own digest names");
                @SuppressWarnings("unchecked")
                Map<String, Object> rec = (Map<String, Object>) Json.parse(new String(raw, StandardCharsets.US_ASCII), "D10 record");
                assertEquals(a.get("case"), rec.get("case_id"));
                assertEquals(D10_SOURCES, rec.get("source_sha256"), "the audited original sources ran, unchanged");
                assertNull(records.put(String.valueOf(rec.get("case_id")), rec), "each case once");
            }
        }
        assertEquals(expected.keySet(), records.keySet());
        for (var e : expected.entrySet()) {
            Map<String, Object> rec = records.get(e.getKey());
            for (var field : e.getValue().entrySet()) {
                assertEquals(field.getValue(), text(rec.get(field.getKey())), e.getKey() + " " + field.getKey());
            }
        }
    }

    @Test
    void pythonRendersEveryValueAsWrittenAndRefusesAnExplicitRefinement() {
        EffectiveConfig python = ConfigResolver.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.execution.sut", "python", "test")));
        assertTrue(python.config().renderPreserveWhitespace(), "Python's line breaks are its syntax: values render as written");
        FrameworkException refined = assertThrows(FrameworkException.class, () -> ConfigResolver.resolve(null, Map.of(),
                List.of(new ConfigResolver.Override("cf.execution.sut", "python", "test"),
                        new ConfigResolver.Override("cf.render.preserveWhitespace", "false", "test"))));
        assertTrue(refined.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.CONFIG_INCONSISTENT
                && d.message().contains("cf.render.preserveWhitespace=false")), refined.diagnostics().toString());
        assertTrue(!ConfigResolver.resolve(null, Map.of(), List.of()).config().renderPreserveWhitespace(), "Java keeps the legacy default");
    }
}
