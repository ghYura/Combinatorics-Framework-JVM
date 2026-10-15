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

package org.ghyura.cf.acceptance;

import org.ghyura.cf.campaign.RunHandle;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.campaign.RunSummary;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.FileResolver;
import org.ghyura.cf.input.ScenarioInterpreter;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.input.builder.ScheduleBuilder;
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Real scenario input, generation and plugin execution; matrix distributions were derived from original Python. */
class CatalogPubRunTest {
    private record Result(RunSummary summary, Map<CandidateId, Object> observations) { }
    private static Result run(String plugin, ScenarioSpec spec, int workers) {
        try (RunHandle handle = RunService.prepare(spec, ConfigResolver.resolve(null, Map.of(), List.of(
                new ConfigResolver.Override("cf.execution.sut", plugin, "test"),
                new ConfigResolver.Override("cf.execution.workers", Integer.toString(workers), "test"))))) {
            RunSummary summary = handle.await(); assertEquals(RunState.COMPLETED, summary.state(), summary.diagnostics().toString());
            assertTrue(summary.countsExact()); assertEquals(summary.generated(), summary.executable());
            assertEquals(BigInteger.ZERO, summary.excluded()); assertEquals(summary.samples(), summary.attempts());
            Map<CandidateId, Object> observations = new LinkedHashMap<>();
            for (Observation observation : handle.observations()) observations.put(observation.key().candidateId(), observation.domain());
            assertEquals(summary.samples(), observations.size()); return new Result(summary, observations);
        }
    }
    @Test void bothOriginalMatricesRunIdenticallyWithOneAndFourWorkers(@TempDir Path dir) throws Exception {
        for (String family : List.of("telemetry", "pub")) {
            Path path = dir.resolve(family + ".json");
            try (InputStream in = CatalogPubRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/catalogpub/" + family + ".json")) {
                assertNotNull(in); Files.write(path, in.readAllBytes());
            }
            ScenarioSpec spec = ScenarioLoader.load(path).spec();
            String plugin = family.equals("telemetry") ? "telemetry-catalog" : "tester-pub";
            Result serial = run(plugin, spec, 1), parallel = run(plugin, spec, 4);
            Map<String, Long> expected = family.equals("telemetry") ? Map.of("VALID", 4L, "INVALID_QUERY", 4L) : Map.of("ADEQUATE", 19L, "DEFECT", 13L);
            assertEquals(expected, serial.summary().verdictsByName()); assertEquals(expected, parallel.summary().verdictsByName());
            assertEquals(expected.values().stream().mapToLong(Long::longValue).sum(), serial.summary().samples());
            assertEquals(serial.observations(), parallel.observations());
            for (Outcome outcome : List.of(Outcome.BROKEN, Outcome.TIMEOUT, Outcome.INFRA_FAIL, Outcome.SKIPPED, Outcome.CANCELLED)) {
                assertEquals(0, serial.summary().count(outcome)); assertEquals(0, parallel.summary().count(outcome));
            }
        }
    }
    @Test void simulationIsReproducibleAndMalformedOrOversizedWorkIsBroken() {
        var simulation = ScheduleBuilder.newSchedule().sheet("operation").verbs("FW_Combi(1)").row("simulate")
                .sheet("seed").verbs("FW_Combi(1)").rows("0", "1", "2").sheet("rounds").verbs("FW_Combi(1)").row("12").build();
        ScenarioSpec spec = ScenarioInterpreter.interpret(simulation, FileResolver.none()).spec();
        Result serial = run("tester-pub", spec, 1), parallel = run("tester-pub", spec, 4);
        assertEquals(serial.observations(), parallel.observations()); assertEquals(3, serial.summary().samples());
        assertEquals(0, serial.summary().count(Outcome.BROKEN));
        for (String value : List.of("--1", "x".repeat(16385))) {
            var malformed = ScheduleBuilder.newSchedule().sheet("quantity").verbs("FW_Combi(1)").row(value).build();
            assertEquals(1, run("tester-pub", ScenarioInterpreter.interpret(malformed, FileResolver.none()).spec(), 1).summary().count(Outcome.BROKEN));
        }
        var excessive = ScheduleBuilder.newSchedule().sheet("operation").verbs("FW_Combi(1)").row("simulate")
                .sheet("rounds").verbs("FW_Combi(1)").row("129").build();
        assertEquals(1, run("tester-pub", ScenarioInterpreter.interpret(excessive, FileResolver.none()).spec(), 1).summary().count(Outcome.BROKEN));
    }
}
