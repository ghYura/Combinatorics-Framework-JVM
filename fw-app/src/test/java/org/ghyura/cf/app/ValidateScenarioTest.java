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

package org.ghyura.cf.app;

import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.input.InputFormat;
import org.ghyura.cf.input.WorkbookFormats;
import org.ghyura.cf.input.builder.Flag;
import org.ghyura.cf.input.builder.JoinSpec;
import org.ghyura.cf.input.builder.Multiplicity;
import org.ghyura.cf.input.builder.ScheduleBuilder;
import org.ghyura.cf.input.builder.Verb;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code validate --scenario} end to end, in-process: a real file in, exit code and two streams out. */
class ValidateScenarioTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Result runWith(Map<String, String> environment, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, environment, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static String planDigest(Result xml) {
        assertEquals(0, xml.code(), xml.err());
        Matcher m = Pattern.compile("planDigest=\"([0-9a-f]{64})\"").matcher(xml.out());
        assertTrue(m.find(), xml.out());
        return m.group(1);
    }

    private static String originOf(Result xml, String key) {
        Matcher m = Pattern.compile("<setting key=\"" + Pattern.quote(key) + "\"[^>]*origin=\"([A-Z]+)\"").matcher(xml.out());
        assertTrue(m.find(), xml.out());
        return m.group(1);
    }

    /** The scenario the legacy ProgrammaticScheduleBuilder documents: five sheets, nine values, one joiner. */
    private static WorkbookData example() {
        return ScheduleBuilder.newSchedule()
                .sheet("HEAD").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1)).row("scenario=demo")
                .sheet("COST").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1))
                .rows(" id=1 cost=0.10", " id=2 cost=0.30", " id=3 cost=0.50")
                .sheet("LAT").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1))
                .rows(" latency=10ms", " latency=30ms", " latency=80ms")
                .sheet("TAIL").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1)).row(" status=ok")
                .join("ROW", JoinSpec.from("HEAD").via("COST", "LAT").to("TAIL").as(Multiplicity.M_N)).row("FW_EMPTY_STRING")
                .runMeFirstOnce("class RunMeFirstOnce { public static String FW_ARGS; }")
                .arguments("noargs")
                .customVar(2, "FWCUSTOMVAR=2 nominal")
                .build();
    }

    private static Path write(Path dir, String name, byte[] content) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static Path write(Path dir, String name, String content) throws IOException {
        return write(dir, name, content.getBytes(StandardCharsets.UTF_8));
    }

    /** The value of one line of the scenario block of the text report. */
    private static String field(String out, String label) {
        Matcher m = Pattern.compile("(?m)^  " + Pattern.quote(label) + "\\s+(.*)$").matcher(out);
        assertTrue(m.find(), "no '" + label + "' line in:\n" + out);
        return m.group(1);
    }

    private static Element rootOf(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> list = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            list.add((Element) nodes.item(i));
        }
        return list;
    }

    // ---- a valid scenario ---------------------------------------------------------------------------------------

    @Test
    void aValidScenarioIsSummarisedAndMeansTheSameInEveryFormat(@TempDir Path dir) throws IOException {
        List<String> identities = new ArrayList<>();
        for (InputFormat format : InputFormat.values()) {
            Path file = write(dir, "scenario." + format.token(), WorkbookFormats.writer(format).write(example()));
            Result r = run("validate", "--scenario", file.toString());
            assertEquals(0, r.code(), format + ": " + r.err());
            assertTrue(r.out().startsWith("cf.schema.version"), "the configuration comes first: " + r.out());
            assertTrue(r.out().contains("\nscenario: " + file + "\n"), r.out());
            assertEquals(format.token(), field(r.out(), "format"));
            assertEquals("5", field(r.out(), "data sheets"));
            assertEquals("9 (labels 6..14)", field(r.out(), "values"));
            assertEquals("SIGNED_BYTE (policy AUTO_PRESERVE, highest label 14)", field(r.out(), "key encoding"));
            assertEquals("FW_SheetNames 5, FW_CUSTOM_VAR 1, FW_Arguments 1, FW_RunMeFirstOnce 1", field(r.out(), "control"));
            assertEquals("5 FW_Seq rows", field(r.out(), "sequence"));
            assertEquals("5 sheets have directives; roles: 1 mandatory, 0 optional, 4 excluded, 0 without values; 0 virtual sheets; "
                    + "0 single verbs repeated", field(r.out(), "plan"));
            assertTrue(field(r.out(), "findings").startsWith("0 errors, 1 warnings, 0 notes"), field(r.out(), "findings"));
            identities.add(field(r.out(), "identity"));
            // the one warning goes to standard error with the position of the cell in this very file
            assertEquals(1, r.err().lines().count(), r.err());
            assertTrue(r.err().contains("WARNING SPEC_RUNMEFIRSTONCE_SHAPE") && r.err().contains(file.toString()), r.err());
        }
        assertEquals(4, identities.size());
        assertEquals(1, identities.stream().distinct().count(), "the same scenario has the same identity in every format: " + identities);
        assertTrue(identities.getFirst().matches("scenario [0-9a-f]{64}, plan [0-9a-f]{64}"), identities.getFirst());
    }

    @Test
    void theXmlReportCarriesTheConfigurationTheScenarioItsSheetsAndEveryFinding(@TempDir Path dir) throws Exception {
        Path file = write(dir, "scenario.json", WorkbookFormats.writer(InputFormat.JSON).write(example()));
        Result r = run("validate", "--scenario", file.toString(), "--format", "xml");
        assertEquals(0, r.code(), r.err());
        assertEquals("", r.err(), "in XML the findings are part of the report");
        Element root = rootOf(r.out());
        assertEquals("cf-report", root.getTagName());
        assertEquals("validate", root.getAttribute("command"));
        assertEquals("OK", root.getAttribute("status"));
        assertEquals("0", root.getAttribute("exitCode"));
        assertEquals(103, children(root, "setting").size(), "the effective configuration is embedded");

        Element scenario = children(root, "scenario").getFirst();
        Map<String, String> expected = Map.ofEntries(Map.entry("format", "json"), Map.entry("dataSheets", "5"), Map.entry("values", "9"),
                Map.entry("firstLabel", "6"), Map.entry("lastLabel", "14"), Map.entry("keyEncoding", "SIGNED_BYTE"),
                Map.entry("keyPolicy", "AUTO_PRESERVE"), Map.entry("highestKeyLabel", "14"), Map.entry("sequenceRows", "5"),
                Map.entry("virtualSheets", "0"), Map.entry("activeSheets", "5"), Map.entry("mandatory", "1"), Map.entry("optional", "0"),
                Map.entry("excluded", "4"), Map.entry("withoutValues", "0"), Map.entry("promoted", "0"));
        expected.forEach((name, value) -> assertEquals(value, scenario.getAttribute(name), name));
        assertTrue(scenario.getAttribute("scenarioDigest").matches("[0-9a-f]{64}"));
        assertTrue(scenario.getAttribute("planDigest").matches("[0-9a-f]{64}"));

        List<Element> sheets = children(scenario, "sheet");
        assertEquals(List.of("HEAD", "COST", "LAT", "TAIL", "ROW"), sheets.stream().map(e -> e.getAttribute("name")).toList());
        assertEquals(List.of("EXCLUDED", "EXCLUDED", "EXCLUDED", "EXCLUDED", "MANDATORY"), sheets.stream().map(e -> e.getAttribute("role")).toList());
        assertEquals(List.of("true", "true", "true", "true", "false"), sheets.stream().map(e -> e.getAttribute("reuse")).toList());
        assertEquals(List.of("1", "3", "3", "1", "1"), sheets.stream().map(e -> e.getAttribute("values")).toList());
        assertEquals(List.of("FW_Combi(1)", "FW_Combi(1)"), children(sheets.getFirst(), "directive").stream().map(Element::getTextContent).toList());
        assertEquals(List.of("FW_(HEAD,,COST,,LAT,,TAIL,,M:N)"), children(sheets.get(4), "directive").stream().map(Element::getTextContent).toList());

        List<Element> findings = children(root, "diagnostic");
        assertEquals(1, findings.size());
        assertEquals("SPEC_RUNMEFIRSTONCE_SHAPE", findings.getFirst().getAttribute("code"));
        assertEquals("WARNING", findings.getFirst().getAttribute("severity"));
    }

    @Test
    void notesAreCountedInTextAndListedInXml(@TempDir Path dir) throws Exception {
        // the same value twice in one sheet is a note; the same sheet in two FW_Seq rows is another
        String json = "{\"sheets\": {\n  \"FW_Seq\": [[\"A\", \"FW_Combi(1)\"], [\"A\", \"FW_Combi(2)\"]],\n  \"A\": [[\"x\", \"x\"]]\n}}\n";
        Path file = write(dir, "scenario.json", json);
        Result text = run("validate", "--scenario", file.toString());
        assertEquals(0, text.code(), text.err());
        assertTrue(field(text.out(), "findings").startsWith("0 errors, 0 warnings, 2 notes"), field(text.out(), "findings"));
        assertEquals("", text.err());
        Result xml = run("validate", "--scenario", file.toString(), "--format", "xml");
        assertEquals(List.of("SPEC_DUPLICATE_VALUE", "PLAN_ROW_OVERWRITTEN"),
                children(rootOf(xml.out()), "diagnostic").stream().map(e -> e.getAttribute("code")).toList());
    }

    @Test
    void fileCellsResolveAgainstTheDirectoryOfTheScenarioNotTheWorkingDirectory(@TempDir Path dir) throws IOException {
        write(dir, "code.txt", "line1\r\nline2");
        Path file = write(dir, "scenario.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(1)\"]], \"A\": [[\"FW_File=code.txt\"]]}}");
        Result r = run("validate", "--scenario", file.toString());
        assertEquals(0, r.code(), r.err());
        assertEquals("1 (labels 2..2)", field(r.out(), "values"));

        Path missing = write(dir, "broken.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(1)\"]], \"A\": [[\"FW_File=gone.txt\"]]}}");
        Result gone = run("validate", "--scenario", missing.toString());
        assertEquals(ExitCode.VALIDATION_FAILED.code(), gone.code());
        assertTrue(gone.err().contains("INPUT_UNREADABLE") && gone.err().contains("FW_File=gone.txt") && gone.err().contains("broken.json:1:"), gone.err());
    }

    // ---- scenarios with problems --------------------------------------------------------------------------------

    @Test
    void everyErrorIsReportedWithItsPositionInTheFileAndTheExitCodeIsThree(@TempDir Path dir) throws IOException {
        // line 3 holds the problem cell at column 15 (the opening quote of "FW_Optionl")
        String json = "{\"sheets\": {\n  \"FW_Seq\": [[\"A\", \"FW_Combi(1)\"]],\n  \"A\": [[\"x\", \"FW_Optionl\"], [\"FW_DBURL=x\"]]\n}}\n";
        Path file = write(dir, "scenario.json", json);
        Result r = run("validate", "--scenario", file.toString());
        assertEquals(3, r.code());
        assertEquals("", r.out(), "a failed validation prints no report on standard output");
        List<String> lines = r.err().lines().toList();
        assertEquals(2, lines.size(), r.err());
        assertTrue(lines.get(0).startsWith("ERROR SPEC_UNKNOWN_DIRECTIVE [" + file + ":3:15"), lines.get(0));
        assertTrue(lines.get(0).contains("did you mean FW_Optional?"), lines.get(0));
        assertTrue(lines.get(1).startsWith("ERROR UNSUPPORTED_CAPABILITY [" + file + ":3:"), lines.get(1));
    }

    @Test
    void theXmlFormReportsTheSameFailureAsADocument(@TempDir Path dir) throws Exception {
        Path file = write(dir, "scenario.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(abc)\"]], \"A\": [[\"x\"]]}}");
        Result r = run("validate", "--scenario", file.toString(), "--format", "xml");
        assertEquals(3, r.code());
        Element root = rootOf(r.out());
        assertEquals("FAILED", root.getAttribute("status"));
        assertEquals("3", root.getAttribute("exitCode"));
        Element diagnostic = children(root, "diagnostic").getFirst();
        assertEquals("PLAN_DIRECTIVE_SYNTAX", diagnostic.getAttribute("code"));
        assertEquals("ERROR", diagnostic.getAttribute("severity"));
    }

    @Test
    void anErrorInALaterStageCarriesTheWarningsOfTheEarlierOnes(@TempDir Path dir) throws IOException {
        // the interpreter warns about the RunMeFirstOnce cell; the planner then finds that there is no FW_Seq sheet
        Path file = write(dir, "scenario.json", "{\"sheets\": {\"FW_RunMeFirstOnce\": [[\"class Other {}\"]], \"A\": [[\"x\"]]}}");
        Result r = run("validate", "--scenario", file.toString());
        assertEquals(3, r.code());
        List<String> lines = r.err().lines().toList();
        assertEquals(2, lines.size(), r.err());
        assertTrue(lines.get(0).startsWith("WARNING SPEC_RUNMEFIRSTONCE_SHAPE"), lines.get(0));
        assertTrue(lines.get(1).startsWith("ERROR PLAN_NO_SEQUENCE"), lines.get(1));
    }

    @Test
    void anUnreadableOrUnrecognizedFileIsAValidationFailure(@TempDir Path dir) throws IOException {
        Path text = write(dir, "scenario.txt", "{}");
        Result unknown = run("validate", "--scenario", text.toString());
        assertEquals(3, unknown.code());
        assertTrue(unknown.err().contains("INPUT_UNRECOGNIZED_FORMAT") && unknown.err().contains("scenario.txt"), unknown.err());
        Path malformed = write(dir, "bad.json", "{\"sheets\": ");
        Result bad = run("validate", "--scenario", malformed.toString());
        assertEquals(3, bad.code());
        assertTrue(bad.err().contains("INPUT_MALFORMED") && bad.err().contains("bad.json"), bad.err());
        Result directory = run("validate", "--scenario", dir.toString());
        assertEquals(3, directory.code());
        assertTrue(directory.err().contains("INPUT_UNRECOGNIZED_FORMAT") || directory.err().contains("INPUT_UNREADABLE"), directory.err());
    }

    // ---- the key encoding and the memory budget come from the configuration -----------------------------------

    private static String scenarioWithValues(int values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values; i++) {
            row.append(i == 0 ? "" : ", ").append("\"v").append(i).append('"');
        }
        return "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(1)\"]], \"A\": [[" + row + "]]}}";
    }

    @Test
    void oneByteKeysAreChosenWhileTheLabelsFitAndStrictModeRefusesTheRest(@TempDir Path dir) throws IOException {
        // The whole usable byte window fits 255 values; a 256th value requires wider keys.
        Path fits = write(dir, "fits.json", scenarioWithValues(255));
        Path over = write(dir, "over.json", scenarioWithValues(256));
        assertTrue(field(run("validate", "--scenario", fits.toString()).out(), "key encoding").startsWith("SIGNED_BYTE (policy AUTO_PRESERVE, highest label 127)"));
        Result wide = run("validate", "--scenario", over.toString());
        assertEquals(0, wide.code(), wide.err());
        assertTrue(field(wide.out(), "key encoding").startsWith("PACKED_LEGACY16 (policy AUTO_PRESERVE, highest label 257)"), wide.out());

        Result strict = run("validate", "--scenario", over.toString(), "--set", "cf.keys.encoding=SIGNED_BYTE");
        assertEquals(3, strict.code());
        assertTrue(strict.err().contains("KEY_CENSUS_OVERFLOW") && strict.err().contains("255"), strict.err());
        assertEquals(0, run("validate", "--scenario", fits.toString(), "--set", "cf.keys.encoding=SIGNED_BYTE").code());
        assertTrue(field(run("validate", "--scenario", fits.toString(), "--set", "cf.keys.encoding=PACKED_LEGACY16").out(), "key encoding")
                .startsWith("PACKED_LEGACY16 (policy PACKED_LEGACY16"));
    }

    @Test
    void aMemoryBudgetTooSmallForTheDictionaryIsAResourceLimitFailure(@TempDir Path dir) throws IOException {
        Path file = write(dir, "scenario.json", scenarioWithValues(20));
        // every sub-budget must stay within the total, so they are lowered together with it
        Result r = run("validate", "--scenario", file.toString(), "--set", "cf.memory.budget.bytes=500", "--set", "cf.pipeline.queue.maxBytes=1",
                "--set", "cf.compiler.cache.maxBytes=1", "--set", "cf.results.maxAttachmentBytes=1", "--set", "cf.execution.output.maxBytes=1");
        assertEquals(ExitCode.RESOURCE_LIMIT.code(), r.code(), r.err());
        assertTrue(r.err().contains("RESOURCE_LIMIT") && r.err().contains("cf.memory.budget.bytes"), r.err());
        assertEquals(0, run("validate", "--scenario", file.toString()).code());
    }

    @Test
    void aScenarioTooLargeToReadWithinTheBudgetIsRefusedBeforeItIsParsed(@TempDir Path dir) throws IOException {
        Path file = write(dir, "scenario.json", scenarioWithValues(4000));
        long reading = 10 * Files.size(file);
        Result refused = run("validate", "--scenario", file.toString(), "--set", "cf.memory.budget.bytes=" + (reading - 1), "--set",
                "cf.pipeline.queue.maxBytes=1", "--set", "cf.compiler.cache.maxBytes=1", "--set", "cf.results.maxAttachmentBytes=1", "--set",
                "cf.execution.output.maxBytes=1");
        assertEquals(ExitCode.RESOURCE_LIMIT.code(), refused.code(), refused.err());
        assertTrue(refused.err().contains("reading the scenario input " + file) && refused.err().contains("cf.memory.budget.bytes"),
                refused.err());
        Result admitted = run("validate", "--scenario", file.toString(), "--set", "cf.memory.budget.bytes=" + Math.max(reading * 4, 64 << 20));
        assertEquals(0, admitted.code(), admitted.err());
    }

    @Test
    void validatingAScenarioTwiceGivesByteForByteTheSameReport(@TempDir Path dir) throws IOException {
        Path file = write(dir, "scenario.json", WorkbookFormats.writer(InputFormat.JSON).write(example()));
        Result first = run("validate", "--scenario", file.toString(), "--format", "xml");
        Result second = run("validate", "--scenario", file.toString(), "--format", "xml");
        assertEquals(first, second);
        assertFalse(first.out().isBlank());
    }

    /**
     * Architect decision G4-F25, review finding HG06: a cf.plan.* setting reaches the planner from the properties
     * file, the environment, the command line and the embedding API in the declared precedence, and changes the
     * plan and its digest; a blank prefix is refused as a configuration error before the scenario is read.
     */
    @Test
    void planOptionsReachThePlannerFromEveryLayerInPrecedenceOrder(@TempDir Path dir) throws Exception {
        // a lone combination verb: cf.plan.autoPromoteSingleVerb decides whether the planner repeats it
        Path scenario = write(dir, "promote.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(1)\"]], \"A\": [[\"a\"], [\"b\"]]}}");
        Path off = write(dir, "off.properties", "cf.plan.autoPromoteSingleVerb=false\n");
        String key = "cf.plan.autoPromoteSingleVerb";
        Map<String, String> envOn = Map.of("CF_PLAN_AUTOPROMOTESINGLEVERB", "true");

        Result defaults = runWith(Map.of(), "validate", "--scenario", scenario.toString(), "--format", "xml");
        Result file = runWith(Map.of(), "validate", "--scenario", scenario.toString(), "--config", off.toString(), "--format", "xml");
        Result envOverFile = runWith(envOn, "validate", "--scenario", scenario.toString(), "--config", off.toString(), "--format", "xml");
        Result cliOverEnv = runWith(envOn, "validate", "--scenario", scenario.toString(), "--config", off.toString(),
                "--set", key + "=false", "--format", "xml");
        assertNotEquals(planDigest(defaults), planDigest(file), "the option changes the plan identity");
        assertEquals(planDigest(defaults), planDigest(envOverFile));
        assertEquals(planDigest(file), planDigest(cliOverEnv));
        assertEquals(List.of("DEFAULT", "FILE", "ENVIRONMENT", "OVERRIDE"),
                List.of(originOf(defaults, key), originOf(file, key), originOf(envOverFile, key), originOf(cliOverEnv, key)));

        // the embedding API: the same resolver with an embedding override, then the planner
        org.ghyura.cf.config.RunConfig embedded = org.ghyura.cf.config.ConfigResolver.resolve(null, Map.of(),
                List.of(new org.ghyura.cf.config.ConfigResolver.Override(key, "false", "embedding"))).config();
        org.ghyura.cf.model.spec.ScenarioSpec spec = org.ghyura.cf.input.ScenarioLoader.load(scenario).spec();
        assertEquals(planDigest(file), org.ghyura.cf.plan.SeqPlanner.plan(spec, org.ghyura.cf.plan.PlanOptions.of(embedded))
                .plan().contentDigest());
        assertNotEquals(planDigest(file), org.ghyura.cf.plan.SeqPlanner.plan(spec,
                org.ghyura.cf.plan.PlanOptions.of(org.ghyura.cf.config.RunConfig.defaults())).plan().contentDigest());

        // a blank or malformed prefix is a configuration error; the scenario named does not even exist
        for (String prefix : List.of(" ", "A B", "V,", "V(")) {
            Result refused = runWith(Map.of(), "validate", "--scenario", dir.resolve("missing.json").toString(),
                    "--set", "cf.plan.virtualSheetPrefix=" + prefix);
            assertEquals(ExitCode.VALIDATION_FAILED.code(), refused.code(), prefix);
            assertTrue(refused.err().contains("CONFIG_INVALID_VALUE") && refused.err().contains("cf.plan.virtualSheetPrefix"),
                    refused.err());
            assertFalse(refused.err().contains("missing.json"), "the scenario was never opened: " + refused.err());
        }
    }

    @Test
    void anFwgenSpecIsValidatedPlannedAndAssembledWithItsChoicesBelowExplicitSettings(@TempDir Path dir) throws IOException {
        Path spec = Files.writeString(dir.resolve("checkout.toml"), """
                title = "Checkout"
                goals = ["latency_ms"]
                coverage_strength = 2
                constraints = [{id = "no-card-offline", pairs = [{PAY = "card", NET = "offline"}], polarity = "forbid"}]

                [[slots]]
                sheet = "PAY"
                values = ["card", "cash", "voucher"]

                [[slots]]
                sheet = "NET"
                values = ["online", "offline"]

                [[slots]]
                sheet = "LANG"
                alias = "choose_one"
                values = ["en", "uk"]
                """, StandardCharsets.UTF_8);
        Result validate = run("validate", "--scenario", spec.toString(), "--format", "xml");
        assertEquals(0, validate.code(), validate.err());
        assertTrue(validate.out().contains("<setting key=\"cf.coverage.strength\" value=\"2\" origin=\"SCENARIO\""), validate.out());
        assertTrue(validate.out().contains("<setting key=\"cf.analysis.goals\" value=\"latency_ms:min\" origin=\"SCENARIO\""), validate.out());
        assertTrue(validate.out().contains("<authored format=\"bundle.spec/v1\" specVersion=\"legacy\" name=\"checkout\" title=\"Checkout\" slots=\"3\">"),
                validate.out());
        assertTrue(validate.out().contains("<alias sheet=\"LANG\" alias=\"choose_one\" verb=\"FW_Combi(1)\"/>"), validate.out());

        Result overridden = run("validate", "--scenario", spec.toString(), "--set", "cf.coverage.strength=0", "--format", "xml");
        assertEquals(0, overridden.code(), overridden.err());
        assertTrue(overridden.out().contains("<setting key=\"cf.coverage.strength\" value=\"0\" origin=\"OVERRIDE\""), overridden.out());
        assertTrue(overridden.out().contains("<overridden key=\"cf.coverage.strength\" value=\"2\" origin=\"SCENARIO\""), overridden.out());

        Result candidates = run("candidates", "--scenario", spec.toString(), "--limit", "3");
        assertEquals(0, candidates.code(), candidates.err());
        Result plan = run("plan", "--scenario", spec.toString());
        assertEquals(0, plan.code(), plan.err());
        assertTrue(plan.out().contains("authored      bundle.spec/v1 spec 'Checkout'"), plan.out());
    }
}
