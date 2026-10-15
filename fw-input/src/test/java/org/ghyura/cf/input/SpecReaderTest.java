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

package org.ghyura.cf.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ghyura.cf.config.ConfigEntry;
import org.ghyura.cf.config.ConfigKeys;
import org.ghyura.cf.config.ConfigOrigin;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.rule.RuleValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * GEN:authoring: fwgen spec documents against goldens of the unchanged legacy compiler and Python's tomllib
 * (handoff/outbox/devtools/spec_goldens.py), and a spec as a scenario with its own constraints and settings.
 */
class SpecReaderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void slotsTextInsideATomlStringOrNestedTableDoesNotTurnAWorkbookIntoASpec() {
        for (String text : List.of("note = '''\n[[slots]]\n'''\n[sheets]\nP = [[\"a\"]]\n",
                "[metadata]\nslots = []\n[sheets]\nP = [[\"a\"]]\n")) {
            assertFalse(SpecReader.isSpec(InputFormat.TOML, InputSource.ofText("workbook.toml", text)));
        }
    }

    @Test
    void expansionAndTomlNestingAreRefusedBeforeUnboundedAllocationOrRecursion() {
        String oversized = "{\"slots\":[{\"sheet\":\"P\",\"values\":[\"" + "${a|b}".repeat(40) + "\"]}]}";
        FrameworkException expansion = assertThrows(FrameworkException.class,
                () -> SpecReader.read(InputSource.ofText("large.json", oversized), InputFormat.JSON));
        assertEquals(ErrorCode.KEY_CENSUS_OVERFLOW, expansion.diagnostics().getFirst().code());
        String largeText = "{\"slots\":[{\"sheet\":\"P\",\"values\":[\"" + "x".repeat(200_000) + "${a|b}".repeat(8) + "\"]}]}";
        FrameworkException memory = assertThrows(FrameworkException.class,
                () -> SpecReader.read(InputSource.ofText("text.json", largeText), InputFormat.JSON));
        assertEquals(ErrorCode.RESOURCE_LIMIT, memory.diagnostics().getFirst().code());
        for (String nested : List.of("x=" + "[".repeat(2_000) + "0" + "]".repeat(2_000), "x.".repeat(2_000) + "y=1")) {
            FrameworkException depth = assertThrows(FrameworkException.class, () -> TomlReader.read("deep.toml", nested));
            assertEquals(ErrorCode.INPUT_LIMIT, depth.diagnostics().getFirst().code());
        }
    }

    private static List<Element> goldens(String tag) throws Exception {
        try (var in = SpecReaderTest.class.getResourceAsStream("spec-goldens.xml")) {
            NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName(tag);
            List<Element> out = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                out.add((Element) nodes.item(i));
            }
            return out;
        }
    }

    private static byte[] decoded(Element e, String child) {
        return Base64.getDecoder().decode(e.getElementsByTagName(child).item(0).getTextContent().strip());
    }

    @Test
    void tomlDocumentsAreReadAsPythonsTomllibReadsThem() throws Exception {
        List<Element> cases = goldens("toml");
        assertEquals(35, cases.size());
        for (Element c : cases) {
            String name = c.getAttribute("name");
            String text = new String(decoded(c, "source"), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
            String expected = new String(decoded(c, "expected"), StandardCharsets.UTF_8);
            StringBuilder actual = new StringBuilder();
            try {
                actual.append("{\"tree\":");
                typed(actual, TomlReader.read(name + ".toml", text));
                actual.append('}');
            } catch (FrameworkException e) {
                Diagnostic d = e.diagnostics().getFirst();
                assertEquals(ErrorCode.INPUT_MALFORMED, d.code(), name);
                assertTrue(d.message().startsWith("not valid TOML: "), d.message());
                actual.setLength(0);
                actual.append("{\"error\":");
                DocTree.appendJsonString(actual, d.message().substring("not valid TOML: ".length()));
                actual.append('}');
            }
            assertEquals(expected, actual.toString(), name);
        }
    }

    /** The generator's typed form of a tomllib value: every scalar a text with its Python type in front. */
    private static void typed(StringBuilder out, DocTree n) {
        switch (n) {
            case DocTree.Obj o -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, DocTree> e : o.fields().entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    DocTree.appendJsonString(out, e.getKey());
                    out.append(':');
                    typed(out, e.getValue());
                }
                out.append('}');
            }
            case DocTree.Arr a -> {
                out.append('[');
                for (int i = 0; i < a.items().size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    typed(out, a.items().get(i));
                }
                out.append(']');
            }
            case DocTree.Leaf l -> DocTree.appendJsonString(out, switch (l.temporal()) {
                case DATE_TIME -> "D:";
                case DATE -> "d:";
                case TIME -> "T:";
                case NONE -> switch (l.value()) {
                    case RuleValue.Text t -> "s:";
                    case RuleValue.Int i -> "i:";
                    case RuleValue.Real r -> "f:";
                    default -> "b:";
                };
            } + PyText.str(l.value()));
        }
    }

    @Test
    void specsCompileExactlyAsTheLegacyCompilerCompiledThem(@TempDir Path dir) throws Exception {
        List<Element> cases = goldens("case");
        assertEquals(44, cases.size());
        int compiled = 0;
        for (Element c : cases) {
            String name = c.getAttribute("name");
            Path file = Files.createDirectories(dir.resolve(Integer.toString(compiled++))).resolve(c.getAttribute("file"));
            Files.write(file, decoded(c, "source"));
            String expectedText = new String(decoded(c, "expected"), StandardCharsets.UTF_8);
            JsonNode expected = JSON.readTree(expectedText);
            InputFormat format = WorkbookFormats.formatOf(file.getFileName().toString());
            InputSource source = InputSource.read(file);
            assertTrue(SpecReader.isSpec(format, source), name + " is recognized as a spec");
            SpecReader.Compiled result;
            try {
                result = SpecReader.read(source, format);
            } catch (FrameworkException e) {
                Diagnostic d = e.diagnostics().getFirst();
                if (name.equals("delta-target-goal")) {
                    assertEquals(ErrorCode.INPUT_SCHEMA, d.code());
                    assertTrue(d.message().contains("a spec goal is min or max"), d.message());
                } else if (name.equals("delta-duplicate-slot")) {
                    assertEquals(ErrorCode.INPUT_DUPLICATE_SHEET, d.code());
                } else if (!expected.get("gate").isNull()) {
                    assertEquals(expected.get("gate").asText(), d.message(), name + ": the legacy dependency gate refused it");
                } else if (expected.get("error").isNull()) {
                    fail(name + ": the legacy compiler accepted it, but here: " + e.diagnostics());
                } else {
                    String type = expected.get("error").get("type").asText();
                    String message = expected.get("error").get("message").asText();
                    switch (type) {
                        case "ValueError" -> assertEquals(message, d.message(), name);
                        case "TOMLDecodeError" -> assertEquals("not valid TOML: " + message, d.message(), name);
                        case "JSONDecodeError" -> assertEquals(ErrorCode.INPUT_MALFORMED, d.code(), name);
                        default -> fail(name + ": unexpected legacy failure " + type);
                    }
                }
                continue;
            }
            assertTrue(expected.get("error").isNull() && expected.get("gate").isNull(),
                    name + ": the legacy compiler refused it (" + expected + "), but it compiled here");
            assertEquals(expectedText, actual(result), name);
        }
    }

    /** The JVM side of a golden expectation, in the generator's key order and Python's compact JSON. */
    private static String actual(SpecReader.Compiled result) {
        Map<String, RuleValue> out = new LinkedHashMap<>();
        out.put("error", RuleValue.Null.NULL);
        out.put("warnings", new RuleValue.ListValue(result.warnings().stream().map(d -> (RuleValue) new RuleValue.Text(d.message())).toList()));
        out.put("gate", RuleValue.Null.NULL);
        StringBuilder json = new StringBuilder("{");
        appendFields(json, out);
        json.append(",\"workbook\":").append(result.sheetsJson()).append(",\"sidecar\":").append(result.sidecarJson());
        Map<String, String> settings = new LinkedHashMap<>();
        result.document().settings().forEach(v -> settings.put(v.key(), v.value()));
        List<RuleValue> goals = new ArrayList<>();
        String goalSetting = settings.getOrDefault(ConfigKeys.ANALYSIS_GOALS.name(), "");
        for (String piece : goalSetting.isEmpty() ? new String[0] : goalSetting.split(",")) {
            String[] parts = piece.split(":");
            goals.add(new RuleValue.ListValue(List.of(new RuleValue.Text(parts[0]), new RuleValue.Text(parts[1]))));
        }
        Map<String, RuleValue> rest = new LinkedHashMap<>();
        rest.put("goals", new RuleValue.ListValue(goals));
        Map<String, RuleValue> coverage = new LinkedHashMap<>();
        coverage.put("strength", RuleValue.Int.of(Long.parseLong(settings.getOrDefault(ConfigKeys.COVERAGE_STRENGTH.name(), "0"))));
        coverage.put("optimal", new RuleValue.Bool("set-cover".equals(settings.get(ConfigKeys.COVERAGE_METHOD.name()))));
        coverage.put("budget", RuleValue.Int.of(Long.parseLong(settings.getOrDefault(ConfigKeys.COVERAGE_BUDGET.name(), "0"))));
        List<RuleValue> present = new ArrayList<>();
        if (settings.containsKey(ConfigKeys.COVERAGE_STRENGTH.name())) {
            present.add(new RuleValue.Text("coverage_strength"));
        }
        if (settings.containsKey(ConfigKeys.COVERAGE_METHOD.name())) {
            present.add(new RuleValue.Text("coverage_optimal"));
        }
        if (settings.containsKey(ConfigKeys.COVERAGE_BUDGET.name())) {
            present.add(new RuleValue.Text("coverage_budget"));
        }
        coverage.put("present", new RuleValue.ListValue(present));
        rest.put("coverage", new RuleValue.MapValue(coverage));
        rest.put("title", new RuleValue.Text(result.document().title()));
        rest.put("note", new RuleValue.Text(result.document().note()));
        rest.put("spec_version", new RuleValue.Text(result.document().specVersion()));
        rest.put("aliases", new RuleValue.ListValue(result.document().aliases().stream().map(a -> (RuleValue) new RuleValue.ListValue(List.of(
                new RuleValue.Text(a.sheet()), new RuleValue.Text(a.alias()), new RuleValue.Text(a.verb()),
                new RuleValue.ListValue(a.flags().stream().map(f -> (RuleValue) new RuleValue.Text(f)).toList()),
                new RuleValue.ListValue(a.chain().stream().map(f -> (RuleValue) new RuleValue.Text(f)).toList())))).toList()));
        json.append(',');
        appendFields(json, rest);
        return json.append('}').toString();
    }

    private static void appendFields(StringBuilder json, Map<String, RuleValue> fields) {
        boolean first = true;
        for (Map.Entry<String, RuleValue> e : fields.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            DocTree.appendJsonString(json, e.getKey());
            json.append(':');
            DocTree.appendJson(json, e.getValue());
        }
    }

    @Test
    void aSpecIsAScenarioWithItsOwnConstraintsAndChoosesSettingsBelowExplicitOnes(@TempDir Path dir) throws Exception {
        Path spec = Files.writeString(dir.resolve("shop.toml"), """
                title = "Shop"
                goals = ["latency_ms", {key = "conversion", dir = "max"}]
                coverage_strength = 2
                constraints = [{id = "no-pg-on-win", pairs = [{OS = "windows", DB = "pg"}], polarity = "forbid"}]

                [[slots]]
                sheet = "OS"
                values = ["linux", "windows", "mac"]

                [[slots]]
                sheet = "DB"
                values = ["pg", "lite"]

                [[slots]]
                sheet = "UI"
                alias = "choose_k(2)"
                values = ["dark", "light", "${small|large}"]
                """, StandardCharsets.UTF_8);
        ScenarioLoader.Loaded loaded = ScenarioLoader.load(spec, ScenarioLoader.ConstraintFiles.NONE);
        assertEquals(3, loaded.spec().sheetCount());
        assertEquals(4, loaded.spec().sheet("UI").orElseThrow().cells().size(), "the ${small|large} group expands to two values");
        assertEquals(1, loaded.spec().constraints().rules().size());
        assertEquals("spec", loaded.spec().constraints().sources().getFirst().kind());
        assertEquals("Shop", loaded.authored().title());
        assertEquals("FW_Combi(2)", loaded.authored().aliases().getFirst().verb());
        assertEquals(List.of("cf.analysis.goals=latency_ms:min,conversion:max", "cf.coverage.strength=2"),
                loaded.settings().stream().map(v -> v.key() + "=" + v.value()).toList());
        assertEquals(3, loaded.settings().get(1).where().line(), "a chosen setting points at its line in the spec");

        EffectiveConfig defaults = ConfigResolver.resolveText("test.properties", "", Map.of(), List.of());
        EffectiveConfig chosen = ConfigResolver.withScenario(defaults, loaded.settings());
        ConfigEntry strength = chosen.entry(ConfigKeys.COVERAGE_STRENGTH);
        assertEquals(ConfigOrigin.SCENARIO, strength.origin());
        assertEquals(2, chosen.config().coverageStrength());
        assertEquals(2, chosen.config().analysisGoals().size());
        EffectiveConfig explicit = ConfigResolver.withScenario(ConfigResolver.resolveText("test.properties", "cf.coverage.strength=3\n",
                Map.of(), List.of()), loaded.settings());
        assertEquals(3, explicit.config().coverageStrength(), "an explicit setting wins over the spec's choice");
        assertEquals(ConfigOrigin.SCENARIO, explicit.conflicts().getFirst().overridden().origin());
        assertEquals(ConfigOrigin.FILE, explicit.conflicts().getFirst().winner().origin());

        FrameworkException notAWorkbook = assertThrows(FrameworkException.class, () -> WorkbookFormats.readFile(spec));
        assertEquals(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, notAWorkbook.diagnostics().getFirst().code());
        Files.writeString(ConstraintSidecarReader.companionOf(spec), "{\"version\": 1}", StandardCharsets.UTF_8);
        FrameworkException ambiguous = assertThrows(FrameworkException.class, () -> ScenarioLoader.load(spec, ScenarioLoader.ConstraintFiles.NONE));
        assertEquals(ErrorCode.CONSTRAINT_AMBIGUOUS, ambiguous.diagnostics().getFirst().code());

        Path yaml = Files.writeString(dir.resolve("spec.yaml"), "slots:\n  - sheet: A\n    values: [a]\n", StandardCharsets.UTF_8);
        assertEquals(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, assertThrows(FrameworkException.class,
                () -> ScenarioLoader.load(yaml, ScenarioLoader.ConstraintFiles.NONE)).diagnostics().getFirst().code());
        Path workbook = Files.writeString(dir.resolve("plain.toml"), "[sheets]\nFW_Seq = [[\"A\", null, null, \"FW_Combi(1)\"]]\nA = [[\"a\"]]\n",
                StandardCharsets.UTF_8);
        ScenarioLoader.Loaded plain = ScenarioLoader.load(workbook, ScenarioLoader.ConstraintFiles.NONE);
        assertNull(plain.authored(), "a Core TOML schedule is still a workbook");
        assertTrue(plain.settings().isEmpty());
    }
}
