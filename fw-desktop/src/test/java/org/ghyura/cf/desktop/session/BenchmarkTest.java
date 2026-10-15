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


package org.ghyura.cf.desktop.session;

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GEN:diagnostics-reporting-release, the benchmark, against the unchanged legacy bundle/benchmark.py: benchmark-goldens.jsonl
 * (made by handoff/outbox/devtools/benchmark/benchmark_goldens.py on a scratch copy) holds the profile resolutions and their
 * refusals, the factor pairs and format_table of a synthetic report.
 */
class BenchmarkTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> goldens() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream in = BenchmarkTest.class.getResourceAsStream("benchmark-goldens.jsonl")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    out.add((Map<String, Object>) Json.parse(line, "benchmark-goldens.jsonl"));
                }
            }
        }
        return out;
    }

    private static double number(Object v) {
        return ((Number) v).doubleValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void profilesFactorPairsAndTheTableAreTheLegacyHarnesses() throws IOException {
        int profiles = 0;
        int pairs = 0;
        for (Map<String, Object> g : goldens()) {
            switch ((String) g.get("case")) {
                case "profile" -> {
                    String profile = (String) g.get("profile");
                    boolean huge = (Boolean) g.get("huge");
                    if (g.containsKey("n")) {
                        assertEquals(((Number) g.get("n")).longValue(), Benchmark.resolveProfile(profile, huge), profile);
                    } else {
                        FrameworkException e = assertThrows(FrameworkException.class, () -> Benchmark.resolveProfile(profile, huge), profile);
                        assertEquals(g.get("error"), e.diagnostics().getFirst().message(), profile);
                        assertEquals(((String) g.get("error")).contains("auto-run ceiling") ? ErrorCode.RESOURCE_LIMIT : ErrorCode.USAGE_INVALID_VALUE,
                                e.diagnostics().getFirst().code(), profile);
                    }
                    profiles++;
                }
                case "factor" -> {
                    long n = ((Number) g.get("n")).longValue();
                    List<Object> pair = (List<Object>) g.get("pair");
                    long[] ab = Benchmark.factorPair(n);
                    assertEquals(List.of(((Number) pair.get(0)).longValue(), ((Number) pair.get(1)).longValue()), List.of(ab[0], ab[1]), "n=" + n);
                    pairs++;
                }
                case "table" -> {
                    Map<String, Object> report = (Map<String, Object>) g.get("report");
                    List<String> legacy = List.of(((String) g.get("text")).split("\n"));
                    List<Map<String, Object>> stages = (List<Map<String, Object>>) report.get("stages");
                    assertEquals(legacy.get(3), Benchmark.headerLine());
                    for (int i = 0; i < stages.size(); i++) {
                        Map<String, Object> st = stages.get(i);
                        Benchmark.Stage stage = new Benchmark.Stage((String) st.get("stage"), ((Number) st.get("candidates")).longValue(),
                                ((Number) st.get("rows")).longValue(), number(st.get("wall_seconds")), number(st.get("cpu_seconds")),
                                ((Number) st.get("peak_memory_bytes")).longValue(), 0, number(st.get("throughput_per_s")), (Boolean) st.get("skipped"),
                                (String) st.get("skip_reason"), "", "");
                        assertEquals(legacy.get(4 + i), Benchmark.stageLine(stage), (String) st.get("stage"));
                    }
                }
                case "disclaimer" -> assertEquals(g.get("text"), Benchmark.DISCLAIMER);
                default -> throw new AssertionError(g.get("case"));
            }
        }
        assertEquals(13, profiles);
        assertEquals(13, pairs);
    }

    @Test
    void everyStageRunsInThisJvmAndReportsWhatItProcessed() throws IOException {
        Benchmark.Report report = Benchmark.run("60", List.of(), false, extra -> ConfigResolver.resolve(null, Map.of(), extra));
        assertEquals(List.of("generator", "sieve", "executor", "analyzer"), report.stages().stream().map(Benchmark.Stage::stage).toList());
        for (Benchmark.Stage st : report.stages()) {
            assertEquals(60, st.rows(), st.stage() + ": " + st.notes());
            assertTrue(st.wallSeconds() > 0 && st.throughputPerS() > 0 && !st.skipped(), st.toString());
        }
        assertEquals("sieved 60 rows; 15 removed", report.stages().get(1).notes(), "the legacy rule removes strict with unicode, one in four");
        String json = Benchmark.json(report);
        // the conditions that make the figures comparable (A20): JDK, heap, hardware, corpus, workers and seed
        for (String key : List.of("\"java\": \"" + Runtime.version() + "\"", "\"heap_max_bytes\": " + Runtime.getRuntime().maxMemory(),
                "\"cpu_count\": " + Runtime.getRuntime().availableProcessors(), "\"mem_total\"", "\"corpus\": \"", "\"workers\": 4", "\"seed\": ")) {
            assertTrue(json.contains(key), key + " in " + json);
        }
        assertTrue(json.contains("\"schema\": \"cf.benchmark/v1\"") && json.contains("billion"), json);
        String table = Benchmark.table(report);
        assertTrue(table.startsWith("benchmark cf.benchmark/v1  profile=60  candidates=60\n  host: "), table);
        assertTrue(table.endsWith(Benchmark.DISCLAIMER), table);
        FrameworkException stage = assertThrows(FrameworkException.class, () -> Benchmark.run("10", List.of("core"), false,
                extra -> ConfigResolver.resolve(null, Map.of(), extra)));
        assertEquals(ErrorCode.USAGE_INVALID_VALUE, stage.diagnostics().getFirst().code());
    }

    @Test
    void aStageTheKeysOrABudgetRefuseIsSkippedWithItsReasonAndTheOthersAreStillMeasured() throws IOException {
        // 40 009 is prime: exactly n candidates as a product is 1 x 40 009 values, more labels than the 16-bit key tier holds
        Benchmark.Report report = Benchmark.run("40009", List.of("generator", "analyzer"), false,
                extra -> ConfigResolver.resolve(null, Map.of(), extra));
        Benchmark.Stage generator = report.stages().getFirst();
        assertTrue(generator.skipped() && generator.skipReason().startsWith("KEY_CENSUS_OVERFLOW: key census under policy AUTO_PRESERVE: "
                + "labels 1..40012 do not fit PACKED_LEGACY16"), generator.toString());
        Benchmark.Stage analyzer = report.stages().get(1);
        assertTrue(!analyzer.skipped() && analyzer.rows() == 40009, analyzer.toString());
        assertTrue(Benchmark.table(report).contains("\n  generator         (skipped: KEY_CENSUS_OVERFLOW: key census under po)\n"),
                Benchmark.table(report));
        assertTrue(Benchmark.json(report).contains("\"measured_stages\": [\n      \"analyzer\"\n    ],\n    \"skipped_stages\": [\n      {\n"
                + "        \"stage\": \"generator\",\n        \"reason\": \"KEY_CENSUS_OVERFLOW: "), Benchmark.json(report));
    }
}
