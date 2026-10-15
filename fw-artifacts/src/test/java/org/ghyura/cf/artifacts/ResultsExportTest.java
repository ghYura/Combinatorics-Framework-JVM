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

package org.ghyura.cf.artifacts;

import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.spi.DomainObservation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gate A11, the legacy result mapping. The expected rows follow the legacy writers line by line (Combinatorics-Framework-QA-edition
 * 0bc3e5a): the column order of Executor_trunk/py_executor.py write_results_v2_batch (line 472); attempt counted from 1
 * (MainWatch.java line 207, py_executor.py line 1257); verdict_message, exit_code, signal and the output references null and
 * duration_ms = TimeUnit.NANOSECONDS.toMillis of the elapsed time, as MainWatch.recordCanonicalResult wrote them; source_hash
 * the SHA-256 of the UTF-8 source (MainWatch.sourceHash).
 */
class ResultsExportTest {

    @Test
    void encodedExportsShareAdmissionAndPreserveExactEscaping() throws Exception {
        Observation o = verdict("aa", "quoted,\"雪\"\n", 0, 0, "env-1", Outcome.PASS, 0, 1, "source");
        Checkpoint c = new Checkpoint("r1", "COMPLETED", "s<&.json", "s", "p", "d", "pol", "generated-java", "25", Map.of(), List.of(o), List.of());
        MemoryBudget budget = new MemoryBudget(2 << 20);
        for (String kind : List.of("results", "csv", "report")) {
            String expected = switch (kind) { case "results" -> ResultsExport.resultsV2(c); case "csv" -> ResultsExport.metricsCsv(c); default -> ResultsExport.report(c); };
            try (MemoryBudget.Lease owner = budget.reserve(1024, "caller checkpoint")) {
                assertEquals(expected, new String(ResultsExport.encode(c, kind, budget), StandardCharsets.UTF_8));
                assertEquals(owner.bytes(), budget.reservedBytes());
            }
            MemoryBudget small = new MemoryBudget(32 << 10);
            assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class, () -> ResultsExport.encode(c, kind, small)).code());
            assertEquals(0, small.reservedBytes());
        }
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static Observation verdict(String hex, String display, int attempt, int repeat, String env, Outcome outcome, int code,
                                       long nanos, String source) throws Exception {
        return new Observation(new SampleKey(new RunId("r1"), new CandidateId(hex.repeat(32)), attempt, repeat, env), display, outcome,
                Optional.of(new DomainObservation(code, Map.of("latency", 4.648), Map.of())), nanos, sha256(source), "pol", Optional.empty(),
                "", "", "", false);
    }

    @Test
    void everyAttemptBecomesALegacyResultsV2Row() throws Exception {
        Observation timeout = new Observation(new SampleKey(new RunId("r1"), new CandidateId("cc".repeat(32)), 0, 0, ""), "3_0_0",
                Outcome.TIMEOUT, Optional.empty(), 2_000_000_000L, sha256("class C {}"), "pol", Optional.of(ErrorCode.EXEC_DEADLINE),
                "deadline", "", "", false);
        List<Observation> committed = List.of(
                verdict("cc", "3_0_0", 1, 0, "", Outcome.PASS, 0, 1_500_000, "class C {}"),
                verdict("bb", "2_0_0", 0, 1, "e1", Outcome.DOMAIN_FAIL, 2, 999_999, "class B {}"),
                timeout,
                verdict("aa", "1_0_0", 0, 0, "", Outcome.PASS, 0, 4_648_000, "class A {}"));
        Checkpoint c = new Checkpoint("r1", "COMPLETED", "s.json", "s", "p", "d", "pol", "generated-java", "25", Map.of(), committed, List.of());
        String expected = String.join("\n",
                "run_id,candidate_id,attempt,outcome,verdict_code,verdict_message,duration_ms,exit_code,signal,worker,source_hash,stdout_ref,"
                        + "stderr_ref,policy_id,policy_hash,repeat_idx,env_id",
                "r1,1_0_0,1,PASS,0,,4,,,," + sha256("class A {}") + ",,,,pol,0,",
                "r1,2_0_0,1,DOMAIN_FAIL,2,,0,,,," + sha256("class B {}") + ",,,,pol,1,e1",
                "r1,3_0_0,1,TIMEOUT,,,2000,,,," + sha256("class C {}") + ",,,,pol,0,",
                "r1,3_0_0,2,PASS,0,,1,,,," + sha256("class C {}") + ",,,,pol,0,") + "\n";
        assertEquals(expected, ResultsExport.resultsV2(c));

        assertEquals("candidate_id,repeat_idx,env_id,attempt,outcome,verdict_code,latency\n1_0_0,0,,1,PASS,0,4.648\n"
                + "2_0_0,1,e1,1,DOMAIN_FAIL,2,4.648\n3_0_0,0,,2,PASS,0,4.648\n", ResultsExport.metricsCsv(c), "final samples only");
        String report = ResultsExport.report(c);
        assertTrue(report.contains("<samples samples=\"3\" attempts=\"4\" retried=\"1\" attachments=\"0\" retention=\"full\"/>"), report);
        assertTrue(report.contains("<outcome name=\"PASS\" count=\"2\"/>") && report.contains("<outcome name=\"TIMEOUT\" count=\"0\"/>"), report);
    }
}
