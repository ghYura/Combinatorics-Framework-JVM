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

import org.ghyura.cf.analysis.TextScoring;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.campaign.RunSummary;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shared facade (A18): what the commands do, through one session; two embedded sessions isolated from each other. */
class SessionTest {

    /** 4 x 2 metric-bearing candidates for the inline-metrics plugin. */
    static Path scenario(Path dir) throws IOException {
        return Files.writeString(dir.resolve("metrics.json"), "{\"sheets\": {\"FW_Seq\": [[\"P\", \"FW_Combi(1)\"], [\"Q\", \"FW_Combi(1)\"]], "
                + "\"P\": [[\"latency=10; throughput=800;\"], [\"latency=20; throughput=700;\"], [\"latency=30; throughput=600;\"], "
                + "[\"latency=40; throughput=500;\"]], \"Q\": [[\"mode=m0;\"], [\"mode=m1;\"]]}}", StandardCharsets.UTF_8);
    }

    /**
     * A scenario inside a legacy generator tree runs with that bundle's Core configuration (cross-check with the legacy
     * 2026-10-13: engine_demo's repeated pass gave the bundle 8 candidates and this build 32 without it): table and final-table
     * distinctification and the key strategy as its config/core.fw.properties sets them, every cell's whitespace and the
     * analyzer's best ten, all below an explicit setting; a scenario outside a tree keeps this build's defaults.
     */
    @Test
    void aScenarioInALegacyGeneratorTreeRunsWithThatBundlesCoreConfiguration(@TempDir Path dir) throws IOException {
        Path tree = dir.resolve("generator_trunk");
        Files.createDirectories(tree.resolve("config"));
        Files.createDirectories(tree.resolve("nested"));
        Files.writeString(tree.resolve("sut_paths.py"), "# tree marker, never executed\n");
        Files.writeString(tree.resolve("config/core.fw.properties"), "distinctify__fwX_Y__Tables=true\ndistinctify__fw_final_opt__Tables=true\n");
        Path scenario = Files.writeString(tree.resolve("nested/example.toml"), "[[slots]]\nsheet = \"A\"\nvalues = [\"x = 0\"]\n");
        org.ghyura.cf.config.RunConfig inTree = session().configurationFor(scenario).config();
        assertTrue(inTree.generationDistinctTables());
        assertTrue(inTree.assemblyDistinct());
        assertEquals(org.ghyura.cf.config.RunConfig.LogicalLabels.LEGACY_WINDOW, inTree.logicalLabels());
        assertTrue(inTree.renderPreserveWhitespace());
        assertEquals(10, inTree.analysisTopK());
        assertEquals(org.ghyura.cf.config.ConfigOrigin.SCENARIO, session().configurationFor(scenario).entries().stream()
                .filter(e -> e.key().equals("cf.generation.distinctTables")).findFirst().orElseThrow().origin());
        assertTrue(!session("cf.generation.distinctTables=false").configurationFor(scenario).config().generationDistinctTables(),
                "an explicit setting wins");
        Path outside = Files.writeString(dir.resolve("example.toml"), "[[slots]]\nsheet = \"A\"\nvalues = [\"x = 0\"]\n");
        org.ghyura.cf.config.RunConfig plain = session().configurationFor(outside).config();
        assertTrue(!plain.generationDistinctTables() && !plain.assemblyDistinct() && plain.analysisTopK() == 20);
    }

    static Session session(String... settings) {
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        for (String s : settings) {
            overrides.add(new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test"));
        }
        return Session.resolve(null, Map.of(), overrides);
    }

    /** The final observation of every sample without its attempt, time or output. */
    private static Map<String, String> projection(List<Observation> finals) {
        Map<String, String> out = new TreeMap<>();
        for (Observation o : finals) {
            out.put(o.key().candidateId().hex() + "/" + o.key().repeatIndex() + "/" + o.key().environmentId(), o.outcome() + " "
                    + o.domain().map(d -> d.verdictCode() + " " + d.metrics()).orElse("-") + " " + o.sourceDigest());
        }
        return out;
    }

    private static Map<String, String> alone(Session session, Path scenario) {
        try (Session.PreparedRun run = session.prepareRun(scenario)) {
            assertEquals(RunState.COMPLETED, run.handle().await().state());
            return projection(run.handle().finals());
        }
    }

    @Test
    void aScenarioAndItsConstraintFilesShareOneReadingBudget(@TempDir Path dir) throws Exception {
        for (String kind : List.of("companion", "explicit", "bonds")) {
            Path folder = Files.createDirectory(dir.resolve(kind));
            Path input = scenario(folder);
            Files.writeString(input, Files.readString(input) + " ".repeat(60_000));
            Path constraints = folder.resolve(kind.equals("companion") ? "metrics.constraints.json" : "extra.json");
            Files.writeString(constraints, (kind.equals("bonds") ? "" : "{\"version\":1,\"constraints\":[]}") + " ".repeat(60_000));
            List<String> settings = new ArrayList<>(List.of("cf.memory.budget.bytes=1048576", "cf.pipeline.queue.maxBytes=1024",
                    "cf.compiler.cache.maxBytes=1024", "cf.results.maxAttachmentBytes=1024", "cf.execution.output.maxBytes=1024"));
            if (!kind.equals("companion")) settings.add((kind.equals("bonds") ? "cf.constraints.bondsFile=" : "cf.constraints.file=") + constraints);
            Session limited = session(settings.toArray(String[]::new));
            FrameworkException refused = assertThrows(FrameworkException.class, () -> limited.validate(input), kind);
            assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT, refused.primary().code(), kind);
            assertTrue(refused.primary().message().contains(constraints.toString()), refused.toString());
            if (kind.equals("companion")) {
                assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT,
                        assertThrows(FrameworkException.class, () -> limited.openConstraints(input)).primary().code());
            }
            settings.set(0, "cf.memory.budget.bytes=2097152");
            assertEquals(2, session(settings.toArray(String[]::new)).validate(input).spec().sheetCount(), kind);
            if (kind.equals("companion")) {
                assertEquals(2, session(settings.toArray(String[]::new)).openConstraints(input).summary().spec().sheetCount());
            }
        }
    }

    @Test
    void constraintRepairKeepsFileFormatAndCompanionOwnershipChecks(@TempDir Path dir) throws Exception {
        Path input = scenario(dir);
        Path companion = dir.resolve("metrics.constraints.json");
        Session session = session();
        assertEquals(List.of(), session.openConstraints(input).editing().bonds());
        for (String malformed : List.of("[", "[]", "{\"version\":1,\"constraints\":[],\"unrecognized\":1}")) {
            Files.writeString(companion, malformed);
            assertThrows(FrameworkException.class, () -> session.openConstraints(input));
            assertEquals(malformed, Files.readString(companion), "refused input is preserved for repair outside the bond editor");
        }
        Files.write(companion, new byte[]{(byte) 0xc3, 0x28});
        assertEquals(org.ghyura.cf.model.ErrorCode.CONSTRAINT_UNREADABLE,
                assertThrows(FrameworkException.class, () -> session.openConstraints(input)).primary().code());
        Files.writeString(companion, "{\"version\":1,\"constraints\":[]}");
        assertEquals(org.ghyura.cf.model.ErrorCode.CONSTRAINT_AMBIGUOUS,
                assertThrows(FrameworkException.class,
                        () -> session("cf.constraints.file=" + companion).openConstraints(input)).primary().code());
        Path authored = Files.writeString(dir.resolve("authored.toml"), """
                [[slots]]
                sheet = "P"
                values = ["a", "b"]
                """);
        assertEquals(org.ghyura.cf.model.ErrorCode.CONSTRAINT_AMBIGUOUS,
                assertThrows(FrameworkException.class, () -> session.openConstraints(authored)).primary().code());
        Files.writeString(input, "{");
        assertThrows(FrameworkException.class, () -> session.openConstraints(input), "base scenario validation remains required");
    }

    @Test
    void referencedFilesAreAdmittedForSavedAndEditedScenarios(@TempDir Path dir) throws Exception {
        Path values = Files.writeString(dir.resolve("values.data"), "x".repeat(120_000));
        Path input = Files.writeString(dir.resolve("input.json"), "{\"sheets\":{\"FW_Seq\":[[\"P\",\"FW_Combi(1)\"]],\"P\":[[\"FW_File=values.data\"]]}}");
        Session limited = session("cf.memory.budget.bytes=1048576", "cf.pipeline.queue.maxBytes=1024",
                "cf.compiler.cache.maxBytes=1024", "cf.results.maxAttachmentBytes=1024", "cf.execution.output.maxBytes=1024");
        ScenarioDocument edited = ScenarioDocument.read(input);
        for (Runnable read : List.<Runnable>of(() -> limited.validate(input), () -> limited.validate(edited, input))) {
            FrameworkException refused = assertThrows(FrameworkException.class, read::run);
            assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT, refused.primary().code());
            assertTrue(refused.primary().message().contains(values.toString()), refused.toString());
        }
        assertEquals(1, session().validate(input).spec().valueCount());
    }

    @Test
    void unsavedConstraintPreviewsAndEditedCompanionsAreAdmittedBeforeParsing(@TempDir Path dir) throws Exception {
        Path input = scenario(dir);
        ScenarioDocument edited = ScenarioDocument.read(input);
        Session limited = session("cf.memory.budget.bytes=1048576", "cf.pipeline.queue.maxBytes=1024",
                "cf.compiler.cache.maxBytes=1024", "cf.results.maxAttachmentBytes=1024", "cf.execution.output.maxBytes=1024");
        String hugeDraft = "{invalid" + " ".repeat(200_000);
        FrameworkException draft = assertThrows(FrameworkException.class, () -> limited.withSidecar(input, hugeDraft));
        assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT, draft.primary().code(), "admission precedes malformed-JSON diagnostics");
        assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT,
                assertThrows(FrameworkException.class, () -> limited.constraintDraft(hugeDraft)).primary().code());
        Path companion = Files.writeString(dir.resolve("metrics.constraints.json"), hugeDraft);
        FrameworkException companionError = assertThrows(FrameworkException.class, () -> limited.validate(edited, input));
        assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT, companionError.primary().code());
        Files.delete(companion);
        Files.writeString(input, Files.readString(input) + " ".repeat(200_000));
        FrameworkException scenario = assertThrows(FrameworkException.class, () -> limited.withSidecar(input, "{\"constraints\":[]}"));
        assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT, scenario.primary().code(), "unsaved-sidecar preview admits the scenario too");
    }

    @Test
    void embeddingValidationAdmitsTheDocumentBeforeReadingIt(@TempDir Path dir) throws Exception {
        Path input = scenario(dir);
        Files.writeString(input, Files.readString(input) + " ".repeat(200_000));
        Session session = session("cf.memory.budget.bytes=1048576", "cf.pipeline.queue.maxBytes=1024",
                "cf.compiler.cache.maxBytes=1024", "cf.results.maxAttachmentBytes=1024", "cf.execution.output.maxBytes=1024");
        for (Runnable validate : List.<Runnable>of(() -> session.validate(input),
                () -> ScenarioValidation.run(input.toString(), session.configuration().config()),
                () -> ScenarioValidation.prepare(input.toString(), session.configuration().config(), (summary, dictionary, budget) -> summary))) {
            FrameworkException failure = assertThrows(FrameworkException.class, validate::run);
            assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT, failure.diagnostics().getFirst().code());
        }
    }

    @Test
    void aSessionValidatesRunsAnalysesAndScoresWithTheCommandsServices(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir);
        Session session = session("cf.execution.sut=inline-metrics", "cf.run.id=s1", "cf.execution.workers=2");
        ScenarioValidation.Summary summary = session.validate(scenario);
        assertEquals(2, summary.spec().sheetCount());
        assertTrue(summary.toText().contains("scenario: " + scenario), summary.toText());
        try (Session.PreparedRun run = session.prepareRun(scenario)) {
            RunSummary s = run.handle().await();
            assertEquals(RunState.COMPLETED, s.state());
            assertEquals(8, s.samples());
            assertTrue(RunReport.toText(s).contains("s1"), RunReport.toText(s));
        }
        assertThrows(FrameworkException.class, () -> session.validate(dir.resolve("missing.json")));

        Path corpus = Files.writeString(dir.resolve("results.kv"), "candidate_id=a latency=3 FW_VAR=0\ncandidate_id=b latency=1 FW_VAR=0\n"
                + "candidate_id=c latency=0 FW_VAR=1\n", StandardCharsets.UTF_8);
        Session.Analyzed analyzed = session("cf.analysis.goals=latency:min", "cf.analysis.discover=false").analyze(corpus, null);
        assertEquals(3, analyzed.samples());
        assertTrue(analyzed.report().toText().contains("b"), analyzed.report().toText());
        TextScoring.Report scored = session.score(corpus, (Path) null, TextScoring.Settings.LEGACY);
        assertEquals(3, scored.lines().size());
        assertTrue(scored.sha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void twoSessionsWithTheirOwnPoliciesAndSeedsRunConcurrentlyWithoutSeeingEachOther(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir);
        Session a = session("cf.execution.sut=inline-metrics", "cf.run.id=alpha", "cf.keys.encoding=SIGNED_BYTE", "cf.random.seed=1",
                "cf.execution.workers=2");
        Session b = session("cf.execution.sut=inline-metrics", "cf.run.id=beta", "cf.keys.encoding=PACKED_LEGACY16", "cf.random.seed=99",
                "cf.execution.repeat.count=3", "cf.execution.workers=3");
        Map<String, String> soloA = alone(a, scenario);
        Map<String, String> soloB = alone(b, scenario);
        assertEquals(8, soloA.size());
        assertEquals(24, soloB.size());
        assertNotEquals(soloA.keySet(), soloB.keySet(), "the key encodings give the sessions different candidate identities");
        for (int round = 0; round < 3; round++) {
            try (Session.PreparedRun ra = a.prepareRun(scenario); Session.PreparedRun rb = b.prepareRun(scenario)) {
                CompletableFuture<RunSummary> fa = ra.handle().start().toCompletableFuture();
                CompletableFuture<RunSummary> fb = rb.handle().start().toCompletableFuture();
                RunSummary sa = fa.join();
                RunSummary sb = fb.join();
                assertEquals(RunState.COMPLETED, sa.state());
                assertEquals(RunState.COMPLETED, sb.state());
                assertEquals("alpha", sa.run().value());
                assertEquals("beta", sb.run().value());
                assertEquals(soloA, projection(ra.handle().finals()), "round " + round);
                assertEquals(soloB, projection(rb.handle().finals()), "round " + round);
            }
        }
        assertEquals("SIGNED_BYTE", a.configuration().config().keyEncodingPolicy().toString());
        assertEquals(99L, b.configuration().config().randomSeed());
    }
}
