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

/** analyze --results over a legacy metrics corpus (the lines of the legacy MetricsHarvestCorpusTest). */
class AnalyzeCommandTest {

    @Test
    void coverageImportsReachAnalysisWithProvenanceAndExplicitOptionValidation(@TempDir Path dir) throws Exception {
        Path corpus = Files.writeString(dir.resolve("results.kv"), "# header\ncandidate_id=a coverage_line=0.1 FW_VAR=0\n"
                + "candidate_id=b coverage_line=0.9 FW_VAR=0\ncandidate_id=failed FW_VAR=1\ncandidate_id=missing coverage_line=0.2 latency=1 FW_VAR=0\n");
        for (int i = 2; i <= 4; i++) Files.writeString(dir.resolve("coverage-" + i + ".xml"),
                "<counter type=\"LINE\" missed=\"" + (i == 2 ? 1 : i == 3 ? 5 : 0) + "\" covered=\"" + (i == 2 ? 9 : i == 3 ? 5 : 10) + "\"/>");
        Result r = run("analyze", "--results", corpus.toString(), "--coverage-format", "jacoco", "--coverage-report",
                dir.resolve("coverage-{lineNo}.xml").toString(), "--set", "cf.analysis.goals=coverage_line:max", "--set",
                "cf.analysis.discover=false", "--format", "xml");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("<front size=\"1\">\n      <point id=\"a\""), r.out());
        assertTrue(r.out().contains("<excluded reason=\"domain-failure\" count=\"1\"/>"), r.out());
        assertTrue(r.out().contains("<coverage-imports reports=\"4\" corpusSha256=\""), r.out());
        assertTrue(r.out().contains("<import line=\"2\" candidate=\"a\""), r.out());
        assertTrue(r.out().contains("status=\"MISSING\""), r.out());
        assertTrue(r.out().contains("INPUT_UNREADABLE"), r.out());
        for (String[] options : new String[][]{{"--coverage-format", "gcov"}, {"--coverage-report", "missing"},
                {"--coverage-format", "unknown", "--coverage-report", "missing"}}) {
            var args = new java.util.ArrayList<>(java.util.List.of("analyze", "--results", "not-read.kv"));
            args.addAll(java.util.List.of(options));
            Result invalid = run(args.toArray(String[]::new));
            assertEquals(2, invalid.code(), invalid.err());
            assertTrue(invalid.err().contains("USAGE_"), invalid.err());
        }
    }

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void aLegacyCorpusIsAnalysedWithItsGoalsAndADomainFailureNeverWins(@TempDir Path dir) throws Exception {
        String corpus = "candidate_id=10_0_0 source_ref=10_0_0.java run_id=run-XYZ app=perf_opt_java algo=naive threads=2 batch=64 cache=large "
                + "latency_ms=33.024 throughput_rps=54.5 memory_mb=114.0 FW_VAR=0\n"
                + "candidate_id=1_0_0 source_ref=1_0_0.java run_id=run-XYZ app=perf_opt_java algo=naive threads=1 batch=16 cache=small "
                + "latency_ms=100.000 throughput_rps=9.0 memory_mb=18.5 FW_VAR=0\n"
                + "candidate_id=9_0_0 source_ref=9_0_0.java run_id=run-XYZ app=perf_opt_java algo=simd threads=8 batch=256 cache=large "
                + "latency_ms=4.648 throughput_rps=1548.9 memory_mb=244.0 FW_VAR=0\n"
                + "candidate_id=9_0_0 source_ref=9_0_0.java run_id=run-XYZ app=perf_opt_java algo=simd threads=8 batch=256 cache=large "
                + "latency_ms=4.648 throughput_rps=1548.9 memory_mb=244.0 FW_VAR=0\n"
                + "candidate_id=7_0_0 source_ref=7_0_0.java latency_ms=1 throughput_rps=99999 memory_mb=1 FW_VAR=3\n";
        Path file = dir.resolve("corpus.kv");
        Files.writeString(file, corpus, StandardCharsets.UTF_8);
        Result r = run("analyze", "--results", file.toString(), "--set", "cf.analysis.goals=latency_ms:min,throughput_rps:max", "--set",
                "cf.analysis.discover=false", "--format", "xml");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("<results path=\"" + file + "\" samples=\"4\" missingVerdicts=\"0\"/>"), "the repeated identical line counts once");
        assertTrue(r.out().contains("<excluded reason=\"domain-failure\" count=\"1\"/>"), r.out());
        assertTrue(r.out().contains("<front size=\"1\">\n      <point id=\"9_0_0\""), "9_0_0 is both fastest and busiest: " + r.out());
        assertTrue(!r.out().contains("display=\"7_0_0\""), "the domain failure is not a point");
        Files.writeString(file, corpus + "candidate_id=9_0_0 latency_ms=5 FW_VAR=0\n", StandardCharsets.UTF_8);
        Result conflict = run("analyze", "--results", file.toString());
        assertEquals(3, conflict.code(), "a sample identity with two contents is refused");
        assertTrue(conflict.err().contains("INPUT_MALFORMED"), conflict.err());
    }
}
