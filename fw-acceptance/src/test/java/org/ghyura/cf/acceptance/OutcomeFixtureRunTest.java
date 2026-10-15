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
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A07 through the whole pipeline with the typed fixture of fw-sut-fixtures (OutcomeFixturePlugin): every outcome
 * from candidates of one sheet, each value a behaviour. The compiled-Java side of the gate is ExecutionEngineTest.
 */
class OutcomeFixtureRunTest {

    private static final List<String> BEHAVIOURS = List.of("pass", "fail:7", "fail:300", "print", "none", "throw", "nonfinite",
            "oracle", "sleep", "spin:600", "reserve", "skip", "flaky");

    private static ScenarioSpec scenario(Path dir, List<String> behaviours) throws IOException {
        StringBuilder values = new StringBuilder();
        for (String b : behaviours) {
            values.append(values.isEmpty() ? "" : ", ").append("[\"").append(b).append("\"]");
        }
        Path file = dir.resolve("outcomes.json");
        Files.writeString(file, "{\"sheets\": {\"FW_Seq\": [[\"behaviour\", \"FW_Combi(1)\"]], \"behaviour\": [" + values + "]}}",
                StandardCharsets.UTF_8);
        return ScenarioLoader.load(file, ScenarioLoader.ConstraintFiles.NONE).spec();
    }

    private static EffectiveConfig config(String... settings) {
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        for (String s : settings) {
            overrides.add(new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test"));
        }
        return ConfigResolver.resolve(null, Map.of(), overrides);
    }

    /** The display id of the n-th value of the single mandatory sheet (1-based, absent optional branch). */
    private static String display(int n) {
        return n + "_0_0";
    }

    @Test
    void theFixturePluginYieldsEveryOutcomeWithItsCode(@TempDir Path dir) throws IOException {
        ScenarioSpec spec = scenario(dir, BEHAVIOURS);
        Map<String, Observation> finals = new HashMap<>();
        RunSummary s;
        try (RunHandle run = RunService.prepare(spec, config("cf.execution.sut=outcome-fixture", "cf.execution.timeout.millis=300",
                "cf.execution.cancel.grace.millis=150", "cf.execution.retry.maxAttempts=2", "cf.execution.workers=4"))) {
            s = run.await();
            for (Observation o : run.finals()) {
                finals.put(BEHAVIOURS.get(Integer.parseInt(o.display().substring(0, o.display().indexOf('_'))) - 1), o);
            }
        }
        assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
        assertEquals(13, s.samples());
        record Expect(Outcome outcome, Optional<ErrorCode> code, Optional<Integer> verdict) {
        }
        Map<String, Expect> expected = Map.ofEntries(
                Map.entry("pass", new Expect(Outcome.PASS, Optional.empty(), Optional.of(0))),
                Map.entry("fail:7", new Expect(Outcome.DOMAIN_FAIL, Optional.empty(), Optional.of(7))),
                Map.entry("fail:300", new Expect(Outcome.DOMAIN_FAIL, Optional.empty(), Optional.of(300))),
                Map.entry("print", new Expect(Outcome.PASS, Optional.empty(), Optional.of(0))),
                Map.entry("none", new Expect(Outcome.BROKEN, Optional.of(ErrorCode.EXEC_NO_VERDICT), Optional.empty())),
                Map.entry("throw", new Expect(Outcome.BROKEN, Optional.of(ErrorCode.EXEC_SUT_EXCEPTION), Optional.empty())),
                Map.entry("nonfinite", new Expect(Outcome.BROKEN, Optional.of(ErrorCode.EXEC_SUT_EXCEPTION), Optional.empty())),
                Map.entry("oracle", new Expect(Outcome.BROKEN, Optional.of(ErrorCode.EXEC_ORACLE_REFUSED), Optional.empty())),
                Map.entry("sleep", new Expect(Outcome.TIMEOUT, Optional.of(ErrorCode.EXEC_DEADLINE), Optional.empty())),
                Map.entry("spin:600", new Expect(Outcome.TIMEOUT, Optional.of(ErrorCode.EXEC_UNCOOPERATIVE), Optional.empty())),
                Map.entry("reserve", new Expect(Outcome.INFRA_FAIL, Optional.of(ErrorCode.RESOURCE_LIMIT), Optional.empty())),
                Map.entry("skip", new Expect(Outcome.SKIPPED, Optional.of(ErrorCode.EXEC_NOT_ATTEMPTED), Optional.empty())),
                Map.entry("flaky", new Expect(Outcome.PASS, Optional.empty(), Optional.of(0))));
        expected.forEach((behaviour, e) -> {
            Observation o = finals.get(behaviour);
            assertEquals(e.outcome(), o.outcome(), behaviour + ": " + o.failureMessage());
            assertEquals(e.code(), o.failure(), behaviour);
            assertEquals(e.verdict(), o.verdictCode(), behaviour);
        });
        assertEquals(1, finals.get("flaky").key().attempt(), "INFRA_FAIL on the first attempt, PASS on the retry");
        assertEquals(1, finals.get("sleep").key().attempt(), "a TIMEOUT is retried too, and timed out again");
        assertEquals(0, finals.get("skip").key().attempt(), "a skip is final");
        assertEquals("printed by " + display(4) + "\n", finals.get("print").stdout());
        assertEquals("to the capture", finals.get("print").stderr());
        assertEquals("CUSTOM_ABOVE_127", s.verdictNames().get(300));
        assertEquals(2, s.abandoned(), "the spinning sample ignored both deadlines; its threads were left running, not stopped");
        assertTrue(s.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.EXEC_ABANDONED_THREADS));
        assertEquals(3, s.count(Outcome.PASS));
        assertEquals(2, s.count(Outcome.DOMAIN_FAIL));
        assertEquals(4, s.count(Outcome.BROKEN));
        assertEquals(2, s.count(Outcome.TIMEOUT));
        assertEquals(1, s.count(Outcome.INFRA_FAIL));
        assertEquals(1, s.count(Outcome.SKIPPED));
    }

    @Test
    void cancellingARunningRunAndAPluginThatCannotOpenGiveTheLastTwoOutcomes(@TempDir Path dir) throws Exception {
        List<String> sleepers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            sleepers.add("sleep");
        }
        try (RunHandle run = RunService.prepare(scenario(dir, sleepers), config("cf.execution.sut=outcome-fixture",
                "cf.execution.timeout.millis=60000", "cf.execution.workers=2"))) {
            run.start();
            // executing: RUNNING, or DRAINING once every candidate is admitted (M05 lifecycle)
            while (run.state() != RunState.RUNNING && run.state() != RunState.DRAINING) {
                Thread.sleep(5);
            }
            Thread.sleep(100);
            run.cancel();
            RunSummary s = run.await();
            assertEquals(RunState.CANCELLED, s.state());
            assertEquals(s.samples(), s.count(Outcome.CANCELLED), "every admitted sample ended CANCELLED, none claims a verdict");
            assertTrue(s.samples() >= 2 && s.admitted() <= 12, s.toString());
        }
        try (RunHandle run = RunService.prepare(scenario(dir, List.of("pass")), config("cf.execution.sut=outcome-fixture-unopenable"))) {
            RunSummary s = run.await();
            assertEquals(1, s.count(Outcome.INFRA_FAIL));
            assertEquals(ErrorCode.EXEC_SESSION_FAILED, run.finals().getFirst().failure().orElseThrow());
        }
    }
}
