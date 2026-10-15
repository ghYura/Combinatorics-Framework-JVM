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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code candidates --export} (C03) through the application: the exported hand-over runs again through {@code run
 * --legacy-handoff} with the outcomes and verdicts of the scenario itself, loose files and shards carry the same candidates,
 * and what cannot be exported is refused before anything is written.
 */
class CandidatesExportTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Element root(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    /** The outcome and verdict tallies of a run report, in report order. */
    private static List<String> tallies(Element report) {
        List<String> out = new ArrayList<>();
        for (String tag : List.of("outcome", "verdict")) {
            NodeList nodes = report.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                out.add(tag + " " + e.getAttribute("code") + " " + e.getAttribute("name") + " " + e.getAttribute("count"));
            }
        }
        return out;
    }

    /** Generated Java over three values of X: two pass, the third sets FW_VAR = FW_EXIT_CODE of X. */
    private static Path scenario(Path dir) throws Exception {
        return Files.writeString(dir.resolve("mini.json"), "{\"sheets\": {\"FW_Seq\": [[\"HEAD\", \"FW_Combi(1)\"], [\"X\", \"FW_Combi(1)\"], "
                + "[\"TAIL\", \"FW_Combi(1)\"]], \"HEAD\": [[\"public class M { public static int FW_VAR = -1; public static void main(String[] a) {\"]], "
                + "\"X\": [[\"FW_VAR = 0;\"], [\"FW_VAR = 0 * a.length;\"], [\"FW_VAR = FW_EXIT_CODE;\"]], \"TAIL\": [[\"} }\"]], "
                + "\"FW_Arguments\": [[\"--quiet\"]], \"FW_CUSTOM_VAR\": [[2, \"CARRIED_BY_X\"]]}}", StandardCharsets.UTF_8);
    }

    @Test
    void anExportedHandOverRunsWithTheScenariosOutcomesAndShardsCarryTheSameCandidates(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir);
        Result direct = run("run", "--scenario", scenario.toString(), "--format", "xml");
        assertEquals(0, direct.code(), direct.err());
        Result exported = run("candidates", "--scenario", scenario.toString(), "--export", dir.resolve("loose").toString(), "--format", "xml");
        assertEquals(0, exported.code(), exported.err());
        Element loose = (Element) root(exported.out()).getElementsByTagName("export").item(0);
        assertEquals(List.of("loose-files", "3", "3"), List.of(loose.getAttribute("transport"), loose.getAttribute("candidates"), loose.getAttribute("files")));
        try (var files = Files.list(dir.resolve("loose").resolve("candidates"))) {
            assertEquals(3, files.filter(p -> p.getFileName().toString().endsWith("_0_0.java")).count(), "one <display id>.java per candidate");
        }
        Result again = run("run", "--legacy-handoff", loose.getAttribute("manifest"), "--format", "xml");
        assertEquals(0, again.code(), again.err());
        assertEquals(tallies(root(direct.out())), tallies(root(again.out())), "the hand-over runs as the scenario ran");
        assertNotEquals(List.of(), tallies(root(direct.out())));
        Result sharded = run("candidates", "--scenario", scenario.toString(), "--export", dir.resolve("sharded").toString(), "--transport", "sharded",
                "--format", "xml");
        assertEquals(0, sharded.code(), sharded.err());
        Element shards = (Element) root(sharded.out()).getElementsByTagName("export").item(0);
        assertEquals(List.of("sharded", "3", "1"), List.of(shards.getAttribute("transport"), shards.getAttribute("candidates"), shards.getAttribute("files")));
        assertEquals(List.of(loose.getAttribute("bytes"), loose.getAttribute("index")), List.of(shards.getAttribute("bytes"), shards.getAttribute("index")),
                "the legacy sinks' summary: the same bytes and the same order-independent index");
        assertTrue(Files.isRegularFile(dir.resolve("sharded").resolve("shards").resolve("shard-00000.fwshard")));
        Result text = run("candidates", "--scenario", scenario.toString(), "--export", dir.resolve("text").toString());
        assertEquals(0, text.code(), text.err());
        assertTrue(text.out().contains("  export        3 candidates (" + loose.getAttribute("bytes") + " bytes, index " + loose.getAttribute("index")
                + ") as loose-files, manifest "), text.out());
    }

    @Test
    void aPythonExportNeverWritesAJavaLabelledHandoff(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir);
        for (String transport : List.of("loose-files", "sharded")) {
            Path out = dir.resolve(transport);
            Result result = run("candidates", "--scenario", scenario.toString(), "--set", "cf.execution.sut=python",
                    "--export", out.toString(), "--transport", transport);
            assertEquals(0, result.code(), result.err());
            String manifest = Files.readString(out.resolve("manifest.json"));
            assertTrue(manifest.contains("\"language\":\"python\""), manifest);
            assertTrue(!manifest.contains("\"language\":\"java\""), manifest);
            if (transport.equals("loose-files")) {
                var bundle = org.ghyura.cf.artifacts.LegacyHandoff.read(out.resolve("manifest.json"));
                assertEquals(org.ghyura.cf.artifacts.LegacyHandoff.Language.PYTHON, bundle.language());
                assertEquals(3, bundle.candidates().size());
                assertTrue(bundle.candidates().stream().allMatch(c -> c.file().toString().endsWith(".py")));
                Result wrong = run("run", "--legacy-handoff", out.resolve("manifest.json").toString());
                assertNotEquals(0, wrong.code());
                assertTrue(wrong.err().contains("CONFIG_INCONSISTENT") && wrong.err().contains("Python add-on"), wrong.err());
            }
        }
    }

    @Test
    void whatCannotBeExportedIsRefusedBeforeAnythingIsWritten(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir);
        Result used = run("candidates", "--scenario", scenario.toString(), "--export", dir.toString());
        assertNotEquals(0, used.code());
        assertTrue(used.err().contains("USAGE_INVALID_VALUE") && used.err().contains("is not empty"), used.err());
        Result beyond = run("candidates", "--scenario", scenario.toString(), "--export", dir.resolve("beyond").toString(), "--scan", "2");
        assertNotEquals(0, beyond.code());
        assertTrue(beyond.err().contains("RESOURCE_LIMIT") && beyond.err().contains("raise --scan"), beyond.err());
        assertTrue(!Files.exists(dir.resolve("beyond")), "nothing was written");
        Result network = run("candidates", "--scenario", scenario.toString(), "--export", dir.resolve("grpc").toString(), "--transport", "grpc");
        assertNotEquals(0, network.code());
        assertTrue(network.err().contains("network hop"), network.err());
    }
}
