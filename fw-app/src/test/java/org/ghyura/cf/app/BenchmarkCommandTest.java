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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The benchmark command: the legacy table, the versioned JSON report, and the legacy refusals with their exit codes. */
class BenchmarkCommandTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void everyStageRunsAtTheProfileAndTheReportCarriesItsConditions(@TempDir Path dir) throws Exception {
        Path json = dir.resolve("bench.json");
        Result table = run("benchmark", "--profile", "60", "--out", json.toString());
        assertEquals(0, table.code(), table.err());
        assertTrue(table.out().startsWith("benchmark cf.benchmark/v1  profile=60  candidates=60\n  host: "), table.out());
        assertTrue(table.out().contains("\n  toolchain: java " + Runtime.version().feature()), table.out());
        for (String stage : new String[] {"generator", "sieve", "executor", "analyzer"}) {
            assertTrue(table.out().contains("\n  " + stage + " ".repeat(18 - stage.length()) + "        60"), stage + ": " + table.out());
        }
        assertTrue(table.out().endsWith("\n  ✓ benchmark -> " + json + "\n"), table.out());
        String report = Files.readString(json);
        assertTrue(report.startsWith("{\n  \"schema\": \"cf.benchmark/v1\",\n  \"profile\": \"60\",\n  \"candidates\": 60,\n  \"environment\": {"), report);
        assertTrue(report.contains("\"measured_stages\": [\n      \"generator\",\n      \"sieve\",\n      \"executor\",\n      \"analyzer\"\n    ]"), report);

        Result xml = run("benchmark", "--profile", "1_0", "--stages", "generator", "--format", "xml");
        assertEquals(0, xml.code(), xml.err());
        assertTrue(xml.out().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<cf-report command=\"benchmark\" status=\"OK\" exitCode=\"0\" "
                + "exitName=\"OK\">\n  <benchmark schema=\"cf.benchmark/v1\" profile=\"1_0\" candidates=\"10\">\n    <stage name=\"generator\" "
                + "candidates=\"10\" rows=\"10\" "), xml.out());
    }

    @Test
    void hugeUnknownAndMalformedRequestsAreRefusedWithTheLegacyMessages() {
        Result huge = run("benchmark", "--profile", "10000001");
        assertEquals(5, huge.code(), huge.err());
        assertTrue(huge.err().contains("RESOURCE_LIMIT") && huge.err().contains("profile size 10,000,001 exceeds the 10,000,000 auto-run ceiling"),
                huge.err());
        Result unknown = run("benchmark", "--profile", "abc");
        assertEquals(2, unknown.code(), unknown.err());
        assertTrue(unknown.err().contains("unknown profile 'abc' (known: 10K, 1M, 10M or an integer)"), unknown.err());
        Result stage = run("benchmark", "--profile", "10", "--stages", "generator,nope");
        assertEquals(2, stage.code(), stage.err());
        assertTrue(stage.err().contains("unknown stage(s) [nope]"), stage.err());
        assertEquals("", stage.out(), "nothing is measured before every stage is known");
    }
}
