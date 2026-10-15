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

import org.ghyura.cf.app.cli.CliSpec;
import org.ghyura.cf.app.cli.CommandSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The whole program, in-process: arguments in, exit code and two UTF-8 streams out. */
class ApplicationTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(Map<String, String> env, String... args) {
        return run(new CommandRegistry(), env, args);
    }

    private static Result run(CommandRegistry registry, Map<String, String> env, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(registry, args, env, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Result run(String... args) {
        return run(Map.of(), args);
    }

    private static Element rootOf(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }

    @Test
    void capabilitiesTextAdmitsOnlyWhatTheModulesProve() {
        Result r = run("capabilities");
        assertEquals(0, r.code());
        assertEquals("", r.err());
        for (int i = 1; i <= 18; i++) {
            assertTrue(r.out().contains(String.format("C%02d ", i)), "C" + i);
        }
        assertTrue(r.out().contains("C08  INCOMPATIBLE"), r.out());
        // C01 includes run-owned metadata. C03 is complete with the candidate export (0111), C05 with the library (0108).
        assertTrue(r.out().contains("C01  IMPLEMENTED"), r.out());
        assertTrue(r.out().contains("C03  IMPLEMENTED"), r.out());
        assertTrue(r.out().contains("C05  IMPLEMENTED"), r.out());
        assertTrue(r.out().contains("candidates --export writes the rendered candidates"), r.out());
        assertTrue(r.out().contains("mapping-entry editing"), r.out());
        // M02 and M03 prove the intermediate, final and optional tables (C02); M03 to M10 the constraint layer, spec documents,
        // planning, decomposition and the stage benchmark of C04; M04 the dynamic Java of the executor (C06), the in-memory
        // hand-over (C03) and the result repository (C09); M05 completes C06 with the legacy hand-over import
        assertTrue(r.out().contains("C02  IMPLEMENTED"), r.out());
        assertTrue(r.out().contains("C04  IMPLEMENTED"), r.out());
        assertTrue(r.out().contains("C06  IMPLEMENTED"), r.out());
        assertTrue(r.out().contains("C09  IMPLEMENTED"), r.out());
        // M07: the results_v2 export completes C09; quiescent checkpoints, resume and exports are C18
        assertTrue(r.out().contains("C18  IMPLEMENTED"), r.out());
        // M06 to M09: the analyzer (C10: algorithms, imports, scorer, Swing views, optional line instrumentation) and its
        // numerical optimisation without Python (C11)
        assertTrue(r.out().contains("C10  IMPLEMENTED"), r.out());
        assertTrue(r.out().contains("C11  IMPLEMENTED"), r.out());
        assertTrue(r.out().endsWith("10 implemented, 1 partial, 6 not implemented, 1 incompatible by design\n"), r.out());
    }

    @Test
    void capabilitiesXmlIsWellFormed() throws Exception {
        Result r = run("capabilities", "--format", "xml");
        assertEquals(0, r.code());
        Element root = rootOf(r.out());
        assertEquals("capabilities", root.getTagName());
        assertEquals("10", root.getAttribute("implemented"));
        assertEquals("1", root.getAttribute("partial"));
        assertEquals("6", root.getAttribute("notImplemented"));
        assertTrue(r.out().contains("<capability id=\"C12\" status=\"PARTIAL\""), r.out());
        assertEquals(18, root.getElementsByTagName("capability").getLength());
    }

    @Test
    void validateReportsTheDefaultsWithTheirOrigin() {
        Result r = run("validate");
        assertEquals(0, r.code());
        assertEquals("", r.err());
        assertTrue(r.out().contains("cf.execution.workers"), r.out());
        assertTrue(r.out().contains("= 4  [DEFAULT]"), r.out());
    }

    @Test
    void validateLayersFileEnvironmentAndCommandLineAndShowsEachOrigin(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("run.properties");
        Files.writeString(file, "cf.execution.workers=8\ncf.execution.retry.maxAttempts=2\n", StandardCharsets.UTF_8);
        Result r = run(Map.of("CF_RANDOM_SEED", "9", "CF_EXECUTION_RETRY_MAX_ATTEMPTS", "99"),
                "validate", "--config", file.toString(), "--repeat", "3", "--set", "cf.execution.retry.maxAttempts=4");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("= 8  [FILE " + file + ":1]"), r.out());
        assertTrue(r.out().contains("= 9  [ENVIRONMENT environment (CF_RANDOM_SEED)]"), r.out());
        assertTrue(r.out().contains("= 3  [OVERRIDE --repeat (cf.execution.repeat.count)]"), r.out());
        assertTrue(r.out().contains("= 4  [OVERRIDE --set (cf.execution.retry.maxAttempts)]"), r.out());
    }

    /** Decision F6-F31: a legacy fw.properties imports below every cf.* source, and every key is reported. */
    @Test
    void validateImportsLegacyConfigurationBelowEveryCfSourceAndReportsEveryKey(@TempDir Path dir) throws Exception {
        Path legacy = dir.resolve("fw.properties");
        Files.writeString(legacy, "core.providedNofWorkerThreadsIntoFixedThreadPool=6\nseq.autoPromoteSingleVerb=false\n"
                + "db.host=localhost\ndb.password=s3cr3t-value\nfw.analyzer.seedOutputPath=seeds.json\n", StandardCharsets.ISO_8859_1);
        Result text = run(Map.of("CORE_DB_PASSWORD", "env-s3cr3t"), "validate", "--legacy-config", legacy.toString(),
                "--set", "cf.execution.repeat.count=2");
        assertEquals(0, text.code(), text.err());
        assertTrue(text.out().contains("= 6  [LEGACY " + legacy + ":1]"), text.out());
        assertTrue(text.out().contains("= false  [LEGACY " + legacy + ":2]"), text.out());
        assertTrue(text.out().contains("legacy import (settings catalog of 0bc3e5acf6cf): 5 keys from " + legacy
                + "; 2 legacy defaults reproduced for keys they leave out"), text.out());
        assertTrue(text.out().matches("(?s).*core\\.threading\\.parallelizeSubCombosIfPossible +mapped -> "
                + "cf\\.generation\\.library=COMBINATORICSLIB3  \\[absent: legacy default].*"), "decision G6: " + text.out());
        assertTrue(text.out().contains("db.host") && text.out().contains("retired"), text.out());
        assertTrue(text.out().matches("(?s).*fw\\.analyzer\\.seedOutputPath +mapped -> cf\\.campaign\\.seed\\.output=seeds\\.json.*"),
                "mapped since M07: " + text.out());
        assertFalse(text.out().contains("CONFIG_LEGACY_PENDING"), text.out());
        assertTrue(text.out().contains("environment CORE_DB_PASSWORD  retired"), text.out());
        assertFalse((text.out() + text.err()).contains("s3cr3t"), "a secret leaked");

        Result xml = run(Map.of(), "validate", "--legacy-config", legacy.toString(), "--format", "xml");
        assertEquals(0, xml.code(), xml.err());
        Element imported = (Element) rootOf(xml.out()).getElementsByTagName("legacy-import").item(0);
        assertEquals("5", imported.getAttribute("keys"));
        assertEquals("2", imported.getAttribute("reproducedDefaults"), "the library switch and, since M03, the label strategy");
        assertEquals("0", imported.getAttribute("pending"));
        assertEquals("5", imported.getAttribute("mapped"), "workers, the promotion switch, the seed output and the two reproduced defaults");
        assertFalse(xml.out().contains("s3cr3t"));

        // a cf.* file wins over the legacy layer; an unknown legacy key fails validation with its location
        Path cf = dir.resolve("cf.properties");
        Files.writeString(cf, "cf.execution.workers=3\n", StandardCharsets.UTF_8);
        Result layered = run("validate", "--legacy-config", legacy.toString(), "--config", cf.toString());
        assertTrue(layered.out().contains("= 3  [FILE " + cf + ":1]"), layered.out());
        Path bad = dir.resolve("bad.properties");
        Files.writeString(bad, "db.host=x\nseq.autoPromoteSingelVerb=true\n", StandardCharsets.ISO_8859_1);
        Result refused = run("validate", "--legacy-config", bad.toString());
        assertEquals(3, refused.code());
        assertTrue(refused.err().contains("CONFIG_LEGACY_UNCLASSIFIED [" + bad + ":2]"), refused.err());
    }

    @Test
    void invalidConfigurationExitsThreeWithLocatedDiagnosticsOnStandardError(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bad.properties");
        Files.writeString(file, "# comment\ncf.execution.workers=zero\ncf.bogus=1\n", StandardCharsets.UTF_8);
        Result r = run("validate", "--config", file.toString());
        assertEquals(3, r.code());
        assertEquals("", r.out());
        assertTrue(r.err().contains("CONFIG_INVALID_VALUE [" + file + ":2]"), r.err());
        assertTrue(r.err().contains("CONFIG_UNKNOWN_KEY [" + file + ":3]"), r.err());
    }

    @Test
    void invalidConfigurationInXmlFormatIsAReportOnStandardOutput(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.properties");
        Files.writeString(file, "cf.execution.workers=zero\n", StandardCharsets.UTF_8);
        Result r = run("validate", "--config", file.toString(), "--format", "xml");
        assertEquals(3, r.code());
        assertEquals("", r.err());
        Element root = rootOf(r.out());
        assertEquals("cf-report", root.getTagName());
        assertEquals("FAILED", root.getAttribute("status"));
        assertEquals("3", root.getAttribute("exitCode"));
        Element diagnostic = (Element) root.getElementsByTagName("diagnostic").item(0);
        assertEquals("CONFIG_INVALID_VALUE", diagnostic.getAttribute("code"));
        Element location = (Element) diagnostic.getElementsByTagName("location").item(0);
        assertEquals(file.toString(), location.getAttribute("origin"));
        assertEquals("1", location.getAttribute("line"));
    }

    @Test
    void outputIsUtf8RegardlessOfThePlatformCharset(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("cyrillic.properties");
        Files.writeString(file, "cf.keys.encoding=бобер\n", StandardCharsets.UTF_8);
        Result text = run("validate", "--config", file.toString());
        assertEquals(3, text.code());
        assertTrue(text.err().contains("'бобер'"), text.err());
        Result xml = run("validate", "--config", file.toString(), "--format", "xml");
        Element attribute = (Element) rootOf(xml.out()).getElementsByTagName("attribute").item(1);
        assertEquals("value", attribute.getAttribute("name"));
        assertEquals("бобер", attribute.getAttribute("value"));
    }

    @Test
    void aMissingScenarioFileIsAValidationFailureAndABrokenConfigurationIsReportedFirst() {
        Result missing = run("validate", "--scenario", "no-such-scenario.json");
        assertEquals(3, missing.code());
        assertTrue(missing.err().contains("INPUT_UNREADABLE") && missing.err().contains("no-such-scenario.json"), missing.err());
        assertEquals("", missing.out());
        // a broken configuration is reported first, as a validation failure
        Result broken = run("validate", "--scenario", "no-such-scenario.json", "--workers", "none");
        assertEquals(3, broken.code());
        assertTrue(broken.err().contains("CONFIG_INVALID_VALUE") && !broken.err().contains("INPUT_UNREADABLE"), broken.err());
    }

    @Test
    void everyCommandIsBuiltAndTheDesktopRefusesAHeadlessJvm() {
        CommandRegistry registry = new CommandRegistry();
        for (CommandSpec spec : CliSpec.COMMANDS) {
            assertTrue(registry.isAvailable(spec.name()), spec.name() + " is built (desktop since M09)");
        }
        Result desktop = run("desktop");
        assertEquals(4, desktop.code(), desktop.err());
        assertEquals("", desktop.out());
        assertTrue(desktop.err().contains("UNSUPPORTED_CAPABILITY") && desktop.err().contains("headless"), desktop.err());
        Result flat = run("guif");
        assertEquals(4, flat.code(), flat.err());
        assertEquals("", flat.out());
        assertTrue(flat.err().contains("UNSUPPORTED_CAPABILITY") && flat.err().contains("headless"), flat.err());
        Result unbuilt = run(new CommandRegistry().withHandler("desktop", new UnavailableCommand(CliSpec.command("desktop").orElseThrow())),
                Map.of(), "desktop");
        assertEquals(4, unbuilt.code());
        assertTrue(unbuilt.err().contains("UNSUPPORTED_CAPABILITY") && unbuilt.err().contains("work package M09"), unbuilt.err());
    }

    @Test
    void syntaxIsCheckedBeforeAnUnbuiltCommandRefuses() {
        Result r = run("run");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("USAGE_MISSING_OPTION"), r.err());
    }

    @Test
    void usageErrorsExitTwoWithAHint() {
        Result r = run("valdate");
        assertEquals(2, r.code());
        assertEquals("", r.out());
        assertTrue(r.err().contains("did you mean 'validate'"), r.err());
        assertTrue(r.err().contains("--help"), r.err());
        assertEquals(2, run().code());
    }

    @Test
    void helpListsEveryCommandAndMarksNoneUnbuilt() {
        Result r = run("--help");
        assertEquals(0, r.code());
        for (CommandSpec spec : CliSpec.COMMANDS) {
            assertTrue(r.out().contains("  " + spec.name() + " "), spec.name());
        }
        assertFalse(r.out().contains("planned for work package"), "every command is built (desktop since M09): " + r.out());
        assertFalse(r.out().contains("planned for work package M00"), r.out());
        assertTrue(r.out().contains("130  The process received an interrupt signal"), r.out());
    }

    @Test
    void commandHelpShowsTheRequiredSyntaxAndAvailability() {
        Result run = run("run", "--help");
        assertEquals(0, run.code());
        // run takes --scenario or --legacy-handoff (M05), so neither is required by the parser
        assertTrue(run.out().startsWith("Usage: java -jar fw-app.jar run [options]"), run.out());
        assertTrue(run.out().contains("--legacy-handoff FILE"), run.out());
        assertFalse(run.out().contains("Not available"), run.out());
        Result plan = run("plan", "--help");
        assertTrue(plan.out().startsWith("Usage: java -jar fw-app.jar plan --scenario FILE [options]"), plan.out());
        assertTrue(plan.out().contains("(required) Scenario to plan"), plan.out());
        Result desktop = run("desktop", "--help");
        assertTrue(desktop.out().startsWith("Usage: java -jar fw-app.jar desktop [options]"), desktop.out());
        assertFalse(desktop.out().contains("Not available"), desktop.out());
        Result capabilities = run("capabilities", "--help");
        assertFalse(capabilities.out().contains("Not available"), capabilities.out());
    }

    @Test
    void versionBannerCarriesTheBuildVersionAndJavaFeatureRelease() {
        Result r = run("--version");
        assertEquals(0, r.code());
        assertTrue(r.out().startsWith("fw-app 0.1.0-SNAPSHOT (handoff architecture revision 2, Java "), r.out());
        assertEquals(1, r.out().split("\n").length);
    }

    @Test
    void anUnexpectedFailureIsAnInternalErrorAndHidesTheStackUnlessAskedTo() {
        CommandRegistry broken = new CommandRegistry().withHandler("capabilities", (command, context) -> {
            throw new IllegalStateException("boom");
        });
        Result quiet = run(broken, Map.of(), "capabilities");
        assertEquals(1, quiet.code());
        assertTrue(quiet.err().contains("INTERNAL_ERROR"), quiet.err());
        assertTrue(quiet.err().contains("java.lang.IllegalStateException: boom"), quiet.err());
        assertFalse(quiet.err().contains("\tat "), quiet.err());

        Result loud = run(broken, Map.of(), "capabilities", "--debug");
        assertEquals(1, loud.code());
        assertTrue(loud.err().contains("\tat "), loud.err());
    }

    @Test
    void theApplicationNeverTerminatesTheJvmItRunsIn() {
        // Application.run returns codes; only Main may call System.exit. Reaching the next line proves it for
        // every path exercised here, and the architecture gate scans bytecode for the rest.
        int[] codes = {run("capabilities").code(), run("bogus").code(), run("desktop").code(), run("run", "--scenario", "x").code()};
        assertEquals(List.of(0, 2, 4, 3), List.of(codes[0], codes[1], codes[2], codes[3]));
    }
}
