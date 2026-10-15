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
import org.ghyura.cf.input.InputFormat;
import org.ghyura.cf.input.ScenarioInterpreter;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.input.FileResolver;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.input.WorkbookFormats;
import org.ghyura.cf.input.builder.Flag;
import org.ghyura.cf.input.builder.ScheduleBuilder;
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A15 (the tutor vertical slice) and the A03 candidate-sequence case: SUT/combination_thinking_tutor run through the
 * whole pipeline (input, plan, generation, assembly with the wallet rule, execution, immutable results, summary), once
 * with the typed Java port as a plugin and once as generated Java compiled in memory, and the lesson's candidates written
 * in five input forms.
 */
class TutorRunTest {

    /** The lesson's numbers (handoff: 192 raw, 48 excluded, 144 executed; 18, 120, 2, 4), also in its scenarios.json. */
    private static final Map<String, Long> EXPECTED = Map.of("PASS", 18L, "ORDER_VIOLATION", 120L, "DOUBLE_CHARGE", 2L, "DISPATCH_LOST", 4L);

    private static Path fixture(Path dir, String name) throws IOException {
        try (InputStream in = TutorRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/tutor/" + name)) {
            assertNotNull(in, name);
            Path file = dir.resolve(name);
            Files.write(file, in.readAllBytes());
            return file;
        }
    }

    private static EffectiveConfig config(String... settings) {
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        for (String s : settings) {
            overrides.add(new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test"));
        }
        return ConfigResolver.resolve(null, Map.of(), overrides);
    }

    private static RunSummary run(ScenarioSpec spec, EffectiveConfig config, List<Observation> into) {
        try (RunHandle handle = RunService.prepare(spec, config)) {
            RunSummary summary = handle.await();
            into.addAll(handle.observations());
            return summary;
        }
    }

    private static void assertLesson(RunSummary s) {
        assertEquals(RunState.COMPLETED, s.state(), s.diagnostics().toString());
        assertTrue(s.countsExact());
        assertEquals(BigInteger.valueOf(192), s.generated());
        assertEquals(BigInteger.valueOf(48), s.excluded());
        assertEquals(BigInteger.valueOf(144), s.executable());
        assertEquals(144, s.samples());
        assertEquals(144, s.attempts());
        assertEquals(18, s.count(Outcome.PASS));
        assertEquals(126, s.count(Outcome.DOMAIN_FAIL));
        for (Outcome o : List.of(Outcome.BROKEN, Outcome.TIMEOUT, Outcome.INFRA_FAIL, Outcome.SKIPPED, Outcome.CANCELLED)) {
            assertEquals(0, s.count(o), o.name());
        }
        assertEquals(EXPECTED, s.verdictsByName(), "the domain categories keep their names and canonical outcomes");
    }

    @Test
    void theTypedPluginAndTheGeneratedJavaGiveTheLessonAndAgreeOnEveryCandidate(@TempDir Path dir) throws IOException {
        Path typed = fixture(dir, "tutor-typed.json");
        fixture(dir, "tutor-typed.constraints.json");
        Path generated = fixture(dir, "tutor-generated.json");
        fixture(dir, "tutor-generated.constraints.json");

        List<Observation> typedObservations = new ArrayList<>();
        RunSummary t = run(ScenarioLoader.load(typed, ScenarioLoader.ConstraintFiles.NONE).spec(), config("cf.execution.sut=tutor"),
                typedObservations);
        assertLesson(t);
        assertEquals("tutor", t.sut());

        List<Observation> generatedObservations = new ArrayList<>();
        RunSummary g = run(ScenarioLoader.load(generated, ScenarioLoader.ConstraintFiles.NONE).spec(),
                config("cf.execution.verdict=FW_CUSTOM_VAR"), generatedObservations);
        assertLesson(g);
        assertEquals("generated-java", g.sut());
        assertEquals(144, g.compilations() + g.cacheHits());

        // case by case: the typed port's interaction line and the generated program's printed line name the same case
        Map<String, Integer> byCase = new HashMap<>();
        for (Observation o : typedObservations) {
            byCase.put(o.domain().orElseThrow().attributes().get("interaction"), o.verdictCode().orElseThrow());
        }
        assertEquals(144, byCase.size());
        for (Observation o : generatedObservations) {
            String line = o.stdout().strip();
            assertEquals(byCase.get(line), o.verdictCode().orElseThrow(), line);
        }
    }

    @Test
    void theFiveInputFormsGiveTheSameCandidateSequence(@TempDir Path dir) throws IOException {
        WorkbookData built = ScheduleBuilder.newSchedule()
                .sheet("channel").verbs("FW_Combi(1)").rows("web", "mobile")
                .sheet("account").verbs("FW_Combi(1)").rows("guest", "member")
                .sheet("payment").verbs("FW_Combi(1)").rows("card", "wallet")
                .sheet("steps").verbs("FW_Combi(3)", "FW_Permut()").rows("reserve", "charge", "dispatch")
                .sheet("duplicate_submit").flag(Flag.OPTIONAL).verbs("FW_Combi(1)").row("duplicate_submit")
                .sheet("carrier_timeout").flag(Flag.OPTIONAL).verbs("FW_Combi(1)").row("carrier_timeout")
                .build();
        Path rule = fixture(dir, "tutor-typed.constraints.json");
        Map<String, ScenarioSpec> specs = new LinkedHashMap<>();
        specs.put("builder", ScenarioInterpreter.interpret(built, FileResolver.none()).spec()
                .withConstraints(ConstraintSidecarReader.read(InputSource.read(rule))));
        for (InputFormat format : List.of(InputFormat.XLSX, InputFormat.JSON, InputFormat.YAML, InputFormat.TOML)) {
            Path sub = Files.createDirectory(dir.resolve(format.name().toLowerCase(java.util.Locale.ROOT)));
            Path file = sub.resolve("tutor." + format.name().toLowerCase(java.util.Locale.ROOT));
            Files.write(file, WorkbookFormats.writer(format).write(built));
            Files.copy(rule, sub.resolve("tutor.constraints.json"));
            specs.put(format.name(), ScenarioLoader.load(file, ScenarioLoader.ConstraintFiles.NONE).spec());
        }
        List<CandidateId> reference = null;
        for (Map.Entry<String, ScenarioSpec> e : specs.entrySet()) {
            List<Observation> observations = new ArrayList<>();
            RunSummary s = run(e.getValue(), config("cf.execution.sut=tutor", "cf.execution.workers=1"), observations);
            assertLesson(s);
            List<CandidateId> sequence = observations.stream().map(o -> o.key().candidateId()).toList();
            if (reference == null) {
                reference = sequence;
            } else {
                assertEquals(reference, sequence, e.getKey() + ": the same candidates, in the same order, with the same ids");
            }
        }
    }

    private static long number(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(m.find(), key);
        return Long.parseLong(m.group(1));
    }

    @Test
    void theExpectedNumbersAreTheLessonsOwn() throws IOException {
        Path lesson = Reactor.root().getParent().resolve("SUT/combination_thinking_tutor/scenarios.json");
        Assumptions.assumeTrue(Files.isRegularFile(lesson), "the SUT checkout is not beside the project");
        // the few numbers needed, by their unique keys; the growth block comes before the expected outcomes
        String text = Files.readString(lesson);
        Map<String, Long> numbers = new HashMap<>();
        String outcomes = text.substring(text.indexOf("\"expected_executable_outcomes\""));
        for (String key : List.of("raw_without_constraint", "final_candidates", "optional_multiplier")) {
            numbers.put("growth." + key, number(text, key));
        }
        for (String key : EXPECTED.keySet()) {
            numbers.put("expected_executable_outcomes." + key, number(outcomes, key));
        }
        assertEquals(192L, numbers.get("growth.raw_without_constraint"));
        assertEquals(144L, numbers.get("growth.final_candidates"));
        assertEquals(4L, numbers.get("growth.optional_multiplier"));
        for (Map.Entry<String, Long> e : EXPECTED.entrySet()) {
            assertEquals(e.getValue(), numbers.get("expected_executable_outcomes." + e.getKey()), e.getKey());
        }
    }
}
