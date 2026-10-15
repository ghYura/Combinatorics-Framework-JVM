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

import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The triage command: against the legacy {@code bundle triage} (the CLI cases of triage-goldens.jsonl, made by
 * handoff/outbox/devtools/triage/triage_goldens.py), and on checkpoints of this framework, typed and harvested.
 */
class TriageCommandTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> goldens() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream in = TriageCommandTest.class.getResourceAsStream("triage-goldens.jsonl")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    out.add((Map<String, Object>) Json.parse(line, "triage-goldens.jsonl"));
                }
            }
        }
        return out;
    }

    private static void metrics(Path run, Object base64) throws IOException {
        Files.createDirectories(run);
        Files.write(run.resolve("metrics.kv"), Base64.getDecoder().decode((String) base64));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theCommandPrintsWhatTheLegacyCommandPrinted(@TempDir Path dir) throws IOException {
        int cases = 0;
        for (Map<String, Object> g : goldens()) {
            String name = (String) g.get("case");
            if (!g.containsKey("argv")) {
                continue;
            }
            cases++;
            Path root = dir.resolve(name);
            if (g.get("runs") instanceof Map<?, ?> runs) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) runs).entrySet()) {
                    metrics(root.resolve("runs").resolve(e.getKey()), e.getValue());
                }
                Files.createDirectories(root.resolve("runs").resolve(".zz-hidden"));
            }
            if (g.get("nested") instanceof Map<?, ?> nested) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) nested).entrySet()) {
                    metrics(root.resolve(e.getKey()), e.getValue());
                }
            }
            if (g.get("metrics") != null) {
                metrics(root, g.get("metrics"));
            }
            List<String> args = new ArrayList<>(List.of("triage", "--input"));
            ((List<String>) g.get("argv")).forEach(a -> args.add(a.replace("{RUN}", root.toString())));
            Result r = run(args.toArray(String[]::new));
            String expected = ((String) g.get("stdout")).replace("{RUN}", root.toString());
            long status = (Long) g.get("status");
            if (status == 0) {
                assertEquals(0, r.code(), name + ": " + r.err());
                assertEquals(expected, r.out(), name);
            } else if (name.equals("cli-missing")) {
                assertEquals(3, r.code(), "a missing run is a typed input refusal (the legacy tool exited 1 for every refusal)");
                assertTrue(r.err().contains("INPUT_UNREADABLE") && r.err().contains("no run directory under " + root), r.err());
                assertEquals("", r.out());
            } else {
                int cut = expected.indexOf("\n  ✗ ");
                assertEquals(1, r.code(), name + ": " + r.err());
                assertEquals(expected.substring(0, cut), r.out(), name + ": the report and the written file, then the refusal");
                String message = expected.substring(cut + 5).strip();
                assertTrue(r.err().contains("ANALYSIS_FINDINGS") && r.err().contains(message), r.err());
            }
        }
        assertEquals(4, cases);
        assertTrue(Files.isRegularFile(dir.resolve("cli-nested-fail/db/runs/x/triage.json")));
        assertTrue(Files.readString(dir.resolve("cli-clean/out.json")).startsWith("{\n  \"corpus\": {\n"));
    }

    @Test
    void aCheckpointOfTypedObservationsIsTriagedFromTheirVerdictsAndAttributes(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("typed.json"), "{\"sheets\": {\"FW_Seq\": [[\"P\", \"FW_Combi(1)\"], [\"M\", \"FW_Combi(1)\"]], "
                + "\"P\": [[\"proto=http;\"], [\"proto=ws;\"]], \"M\": [[\"mode=a FW_VAR=0 inv=none\"], [\"mode=b FW_VAR=1 inv=I4\"]]}}",
                StandardCharsets.UTF_8);
        Path checkpoint = dir.resolve("typed.cp.xml");
        Result ran = run("run", "--scenario", scenario.toString(), "--sut", "inline-metrics", "--run-id", "typed", "--checkpoint",
                checkpoint.toString());
        assertEquals(0, ran.code(), ran.err());

        Result text = run("triage", "--input", checkpoint.toString(), "--write");
        assertEquals(0, text.code(), text.err());
        assertTrue(text.out().startsWith("triage typed\n  candidates 4   failing 2   distinct findings 1\n  outcomes   DOMAIN_FAIL=2  PASS=2\n"),
                text.out());
        assertTrue(text.out().contains("  1. inv=I4 mode=b" + " ".repeat(33) + "     2 candidates  100.0%\n"), text.out());
        assertTrue(text.out().contains("     minimal witness  4_0_0.java  (31B)\n     inv=I4  mode=b  proto=ws\n"),
                "the smaller failing source (its kept attachment) is the witness: " + text.out());
        assertTrue(text.out().contains("  proto          http                        1/2       50.0%  lift  1.00  covers 50.0%"), text.out());
        assertTrue(text.out().endsWith("  ✓ triage report -> " + dir.resolve("triage.json") + "\n"), text.out());
        assertTrue(Files.readString(dir.resolve("triage.json")).contains("\"path\": \"" + checkpoint + "\""));

        Result xml = run("triage", "--input", checkpoint.toString(), "--fail-on-findings", "--format", "xml");
        assertEquals(1, xml.code(), xml.err());
        assertTrue(xml.out().contains("<cf-report command=\"triage\" status=\"FAILED\" exitCode=\"1\" exitName=\"FAILED\">"), xml.out());
        assertTrue(xml.out().contains("<finding rank=\"1\" label=\"inv=I4 mode=b\" count=\"2\" share=\"1.0\">"), xml.out());
        assertTrue(xml.out().contains("ANALYSIS_FINDINGS"), xml.out());

        Result notACheckpoint = run("triage", "--input", scenario.toString());
        assertEquals(3, notACheckpoint.code(), notACheckpoint.err());
        Result badTop = run("triage", "--input", checkpoint.toString(), "--top", "many");
        assertEquals(2, badTop.code(), badTop.err());
    }

    @Test
    void harvestedMetricsLinesAreTheLegacyCorpus(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("lines.json"), """
                {"sheets": {"FW_Seq": [["HEAD", "FW_Combi(1)"], ["Z", "FW_Combi(1)"], ["X", "FW_Combi(1)"], ["TAIL", "FW_Combi(1)"]],
                 "HEAD": [["public class M { public static int FW_VAR = -1; public static void main(String[] a) {"]],
                 "Z": [["String zone = \\"z1\\";"], ["String zone = \\"z2\\";"]],
                 "X": [["FW_VAR = 0; System.out.println(\\"app=demo FW_VAR=0 inv=none zone=\\" + zone);"],
                       ["FW_VAR = 1; System.out.println(\\"app=demo FW_VAR=1 inv=I4 zone=\\" + zone);"]],
                 "TAIL": [["} }"]],
                 "FW_CUSTOM_VAR": [[1, "I4"]]}}
                """, StandardCharsets.UTF_8);
        Path checkpoint = dir.resolve("lines.cp.xml");
        Result ran = run("run", "--scenario", scenario.toString(), "--run-id", "lines", "--checkpoint", checkpoint.toString());
        assertEquals(0, ran.code(), ran.err());
        Result text = run("triage", "--input", checkpoint.toString());
        assertEquals(0, text.code(), text.err());
        assertTrue(text.out().startsWith("triage lines\n  candidates 4   failing 2   distinct findings 1\n"), text.out());
        assertTrue(text.out().contains("  1. app=demo inv=I4 "), text.out());
        assertTrue(text.out().matches("(?s).*     minimal witness  \\S+\\.java  \\(\\d+B\\)\n     app=demo  inv=I4  zone=z\\d\n.*"),
                "failing sources are kept as attachments by default, so the witness is measured: " + text.out());
    }
}
