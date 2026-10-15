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


package org.ghyura.cf.analysis;

import org.ghyura.cf.model.Json;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GEN:diagnostics-reporting-release, triage, against the unchanged legacy bundle/triage.py: triage-goldens.jsonl holds, per
 * run directory, the legacy text report and the JSON write_json_atomic wrote (made by
 * handoff/outbox/devtools/triage/triage_goldens.py on a scratch copy of the reference tree).
 */
class TriageTest {

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> goldens() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream in = TriageTest.class.getResourceAsStream("triage-goldens.jsonl")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    out.add((Map<String, Object>) Json.parse(line, "triage-goldens.jsonl"));
                }
            }
        }
        return out;
    }

    /** Writes a golden's run directory: metrics.kv, executor-summary.json and src/ files of the recorded sizes. */
    @SuppressWarnings("unchecked")
    static void write(Path run, Map<String, Object> g) throws IOException {
        Files.createDirectories(run);
        if (g.get("metrics") instanceof String b64) {
            Files.write(run.resolve("metrics.kv"), Base64.getDecoder().decode(b64));
        }
        if (g.get("summary") != null) {
            Files.writeString(run.resolve("executor-summary.json"), Json.write(g.get("summary")), StandardCharsets.UTF_8);
        }
        if (g.get("sizes") instanceof Map<?, ?> sizes) {
            Files.createDirectories(run.resolve("src"));
            for (Map.Entry<String, Object> e : ((Map<String, Object>) sizes).entrySet()) {
                Files.writeString(run.resolve("src").resolve(e.getKey()), "x".repeat(((Long) e.getValue()).intValue()), StandardCharsets.UTF_8);
            }
        }
    }

    @Test
    void everyReportIsTheLegacyTextAndJsonExactly(@TempDir Path dir) throws IOException {
        int cases = 0;
        for (Map<String, Object> g : goldens()) {
            String name = (String) g.get("case");
            if (!g.containsKey("text")) {
                continue;
            }
            Path run = dir.resolve(name);
            write(run, g);
            Triage.Report report = Triage.ofLegacyRun(run, new MemoryBudget(64L << 20));
            String root = run.toString();
            assertEquals(((String) g.get("text")).replace("{RUN}", root), Triage.text(report, ((Long) g.get("top")).intValue()), name + ": text");
            assertEquals(((String) g.get("json")).replace("{RUN}", root), Triage.json(report), name + ": json");
            cases++;
        }
        assertEquals(9, cases);
    }

    @Test
    void theCorpusIsReadAsPythonReadIt() {
        List<Triage.Record> records = Triage.parse("candidate_id=a FW_VAR=+0 k=1 k=2 éx=3 a-b=4 candidate_id=b FW_VAR=1.0\r\n   \n"
                + "candidate_id= x=1\u0085candidate_id=c FW_VAR=");
        assertEquals(List.of("a", "b", "c"), records.stream().map(Triage.Record::candidateId).toList());
        assertEquals(List.of(false, true, false), records.stream().map(Triage.Record::failed).toList(),
                "int('+0') is 0; int('1.0') refuses, so a non-empty verdict fails; an empty one passes");
        assertEquals(Map.of("candidate_id", "a", "FW_VAR", "+0", "k", "2", "x", "3", "b", "4"), records.getFirst().fields());
        assertEquals(List.of("candidate_id", "FW_VAR", "k", "x", "b"), List.copyOf(records.getFirst().fields().keySet()),
                "a repeated key keeps its first place; a combining mark is not a word character");
    }

    @Test
    void numbersAndStringsFollowCPython() {
        assertEquals(List.of("1e-05", "1e+16", "1000000000000000.0", "1.5e-07", "5e-324", "1.7976931348623157e+308", "0.0001", "123456789.123",
                "-0.0"), List.of(Py.repr(1e-05), Py.repr(1e16), Py.repr(1e15), Py.repr(1.5e-07), Py.repr(5e-324), Py.repr(1.7976931348623157e308),
                Py.repr(0.0001), Py.repr(123456789.123), Py.repr(-0.0)));
        assertEquals(List.of("6.2", "0.12", "2.67", "1.12", "-0.0"), List.of(Py.fixed(6.25, 1), Py.fixed(0.125, 2), Py.fixed(2.675, 2),
                Py.fixed(1.125, 2), Py.fixed(-0.04, 1)), "C rounds the exact binary value half to even");
        for (String s : List.of("+0", "0_0", "٠")) {
            assertEquals(0, Py.integer(s).signum(), s);
        }
        assertEquals(new BigDecimal(7), Py.integer(" 7 "));
        for (String s : List.of("1_", "_1", "1__0", "", "0x1", "1.0")) {
            assertNull(Py.integer(s), s);
        }
        for (String s : List.of("1_0.5", ".5", "5.", "inf", "-Infinity", "nan", "1e1_0", "١٢")) {
            assertTrue(Py.isFloat(s), s);
        }
        for (String s : List.of("1_.5", ".", "e5", "1e", "infin", "0x1")) {
            assertFalse(Py.isFloat(s), s);
        }
        assertTrue(Py.compare("￿", "𝐀") < 0, "code point order, not UTF-16 order");
        assertEquals(List.of("a", "b", "c", "d", "", "e"), Py.splitLines("a\r\nb\rc\u000bd\n e\n"));
    }

    @Test
    void secretShapedKeysAreRedactedInTheJsonOnly(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("metrics.kv"), "candidate_id=1 source_ref=1.java FW_VAR=1 inv=x Api_Key=k1 pw_TOKEN=t\n"
                + "candidate_id=2 source_ref=2.java FW_VAR=0 inv=none\n", StandardCharsets.UTF_8);
        Triage.Report report = Triage.ofLegacyRun(dir, new MemoryBudget(16L << 20));
        assertTrue(Triage.text(report, 12).contains("Api_Key=k1  pw_TOKEN=t"), Triage.text(report, 12));
        String json = Triage.json(report);
        assertTrue(json.contains("\"Api_Key\": \"***REDACTED***\"") && json.contains("\"pw_TOKEN\": \"***REDACTED***\""), json);
        // as the legacy writer: mapping values only, so a classifying key still names its value in the label and signature
        assertTrue(json.contains("\"label\": \"Api_Key=k1 inv=x pw_TOKEN=t\""), json);
    }

    @Test
    void aSourceRefOutsideTheRunsSourcesIsNotMeasured(@TempDir Path dir) throws IOException {
        Path run = dir.resolve("run");
        Files.createDirectories(run.resolve("src"));
        Files.writeString(dir.resolve("outside.java"), "x".repeat(5), StandardCharsets.UTF_8);
        Files.writeString(run.resolve("src").resolve("in.java"), "x".repeat(50), StandardCharsets.UTF_8);
        Files.writeString(run.resolve("metrics.kv"), "candidate_id=1 source_ref=../../outside.java FW_VAR=1 inv=x\n"
                + "candidate_id=2 source_ref=in.java FW_VAR=1 inv=x\ncandidate_id=3 source_ref=in.java FW_VAR=0 inv=none\n", StandardCharsets.UTF_8);
        Triage.Finding finding = Triage.ofLegacyRun(run, new MemoryBudget(16L << 20)).findings().getFirst();
        assertEquals("2", finding.witness().candidateId(), "the 5-byte file outside src/ is never measured, so the sized source wins");
        assertEquals(50L, finding.witnessBytes());
    }
}
