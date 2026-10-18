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
import org.w3c.dom.Element;
import org.ghyura.cf.artifacts.CampaignCheckpointCodec;
import org.ghyura.cf.campaign.Campaign;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.input.ScenarioLoader;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The run command (work package M04) through the application: reports, exit codes and the refusals before running. */
class RunCommandTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /** Generated Java: X's first value passes, its second is FW_VAR = FW_EXIT_CODE of sheet X (key 2). */
    private static Path scenario(Path dir) throws IOException {
        Path file = dir.resolve("mini.json");
        Files.writeString(file, "{\"sheets\": {\"FW_Seq\": [[\"HEAD\", \"FW_Combi(1)\"], [\"X\", \"FW_Combi(1)\"], [\"TAIL\", \"FW_Combi(1)\"]], "
                + "\"HEAD\": [[\"public class M { public static int FW_VAR = -1; public static void main(String[] a) {\"]], "
                + "\"X\": [[\"FW_VAR = 0;\"], [\"FW_VAR = FW_EXIT_CODE;\"]], \"TAIL\": [[\"} }\"]], "
                + "\"FW_CUSTOM_VAR\": [[2, \"CARRIED_BY_X\"]]}}", StandardCharsets.UTF_8);
        return file;
    }

    private static Element root(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    @Test
    void aCompletedRunExitsZeroWithItsDomainFailuresReported(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir);
        Result text = run("run", "--scenario", scenario.toString(), "--run-id", "cli-1");
        assertEquals(0, text.code(), text.err());
        assertTrue(text.out().contains("run: cli-1"), text.out());
        assertTrue(text.out().contains("generated 2, excluded 0, left out by covering selection 0, executable 2"), text.out());
        assertTrue(text.out().contains("PASS 1, DOMAIN_FAIL 1, BROKEN 0"), text.out());
        assertTrue(text.out().contains("2 CARRIED_BY_X: 1"), text.out());
        assertTrue(text.out().contains("reconciled  every total adds up: "), text.out());
        Path report = dir.resolve("report.xml");
        Result xml = run("run", "--scenario", scenario.toString(), "--format", "xml", "--out", report.toString());
        assertEquals(0, xml.code(), xml.err());
        Element root = root(xml.out());
        assertEquals("OK", root.getAttribute("status"));
        Element runElement = (Element) root.getElementsByTagName("run").item(0);
        assertEquals("COMPLETED", runElement.getAttribute("state"));
        assertEquals("generated-java", runElement.getAttribute("sut"));
        assertEquals("2", ((Element) root.getElementsByTagName("candidates").item(0)).getAttribute("executable"));
        Element reconciliation = (Element) root.getElementsByTagName("reconciliation").item(0);
        assertEquals("true", reconciliation.getAttribute("consistent"), xml.out());
        assertEquals("0", reconciliation.getAttribute("fails"), xml.out());
        assertEquals(3, reconciliation.getElementsByTagName("axis").getLength(), "HEAD, X and TAIL");
        assertEquals(xml.out(), Files.readString(report), "--out writes the same report");
    }

    /** Profiles trading latency against throughput: p1..p4 are the front, p5..p8 dominated; Q carries no metric. */
    private static Path profiles(Path dir) throws IOException {
        Path file = dir.resolve("profiles.json");
        StringBuilder p = new StringBuilder();
        int[][] profiles = {{10, 100}, {20, 200}, {30, 300}, {40, 400}, {50, 150}, {60, 250}, {70, 350}, {80, 120}};
        for (int[] x : profiles) {
            p.append(p.isEmpty() ? "" : ", ").append("[\"latency=").append(x[0]).append("; throughput=").append(x[1]).append(";\"]");
        }
        Files.writeString(file, "{\"sheets\": {\"FW_Seq\": [[\"P\", \"FW_Combi(1)\"], [\"Q\", \"FW_Combi(1)\"]], \"P\": [" + p
                + "], \"Q\": [[\"mode=a;\"], [\"mode=b;\"]]}}", StandardCharsets.UTF_8);
        return file;
    }

    /** A batch run reaches the same analysis as the desktop: export its metrics corpus, then analyze it. */
    @Test
    void aRunsMetricsExportIsWhatAnalyzeReads(@TempDir Path dir) throws Exception {
        Path scenario = profiles(dir);
        Path checkpoint = dir.resolve("run.cp.xml");
        assertEquals(0, run("run", "--scenario", scenario.toString(), "--sut", "inline-metrics", "--run-id", "kv", "--checkpoint",
                checkpoint.toString()).code());
        Path metrics = dir.resolve("run-metrics.kv");
        Result exported = run("export", "--input", checkpoint.toString(), "--output", metrics.toString(), "--kind", "metrics");
        assertEquals(0, exported.code(), exported.err());
        List<String> lines = Files.readAllLines(metrics);
        assertEquals(16, lines.size(), "one line per sample: " + lines);
        assertTrue(lines.stream().allMatch(l -> l.startsWith("candidate_id=") && l.contains(" run_id=kv ") && l.endsWith(" FW_VAR=0")), lines.toString());
        Result analyzed = run("analyze", "--results", metrics.toString());
        assertEquals(0, analyzed.code(), analyzed.err());
        assertTrue(analyzed.out().contains("(16 samples)"), analyzed.out());
        assertFalse(analyzed.err().contains("carry no FW_VAR"), "every sample carries its verdict: " + analyzed.err());
    }

    @Test
    void aCheckpointedRunIsExportedAndResumedButNotAsAnotherRun(@TempDir Path dir) throws Exception {
        Path scenario = profiles(dir);
        Path checkpoint = dir.resolve("run.cp.xml");
        Result first = run("run", "--scenario", scenario.toString(), "--sut", "inline-metrics", "--run-id", "cp1", "--checkpoint",
                checkpoint.toString());
        assertEquals(0, first.code(), first.err());
        assertTrue(Files.readString(checkpoint).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<cf-checkpoint schema=\"1\" "));

        Path rows = dir.resolve("results_v2.csv");
        Result exported = run("export", "--input", checkpoint.toString(), "--output", rows.toString(), "--kind", "results");
        assertEquals(0, exported.code(), exported.err());
        List<String> lines = Files.readAllLines(rows);
        assertEquals(17, lines.size(), "a header and the 16 attempts");
        assertTrue(lines.get(0).startsWith("run_id,candidate_id,attempt,outcome,verdict_code,"), lines.get(0));
        assertTrue(lines.get(1).startsWith("cp1,") && lines.get(1).contains(",1,PASS,0,"), lines.get(1));

        // resuming a finished run runs nothing and reports the same outcomes
        Result resumed = run("resume", "--checkpoint", checkpoint.toString(), "--format", "xml");
        assertEquals(0, resumed.code(), resumed.err());
        Element outcome = (Element) root(resumed.out()).getElementsByTagName("outcome").item(0);
        assertEquals("PASS", outcome.getAttribute("name"));
        assertEquals("16", outcome.getAttribute("count"));
        assertEquals("16", ((Element) root(resumed.out()).getElementsByTagName("samples").item(0)).getAttribute("attempts"));
        assertEquals("true", ((Element) root(resumed.out()).getElementsByTagName("reconciliation").item(0)).getAttribute("consistent"),
                "a resumed run reconciles with its restored attempts: " + resumed.out());

        Result other = run("resume", "--checkpoint", checkpoint.toString(), "--set", "cf.keys.encoding=PACKED_LEGACY16");
        assertEquals(3, other.code(), "VALIDATION_FAILED");
        assertTrue(other.err().contains("CHECKPOINT_MISMATCH") && other.err().contains("key dictionary (labels, encoding, window)")
                && other.err().contains("configuration"), other.err());
        Files.writeString(checkpoint, Files.readString(checkpoint).replace("cp1", "cp2"));
        Result altered = run("resume", "--checkpoint", checkpoint.toString());
        assertEquals(3, altered.code());
        assertTrue(altered.err().contains("CHECKPOINT_CORRUPT"), altered.err());
    }

    @Test
    void anImportedSeedOutputWritesTheSeedAndIterationsRunACampaign(@TempDir Path dir) throws Exception {
        Path scenario = profiles(dir);
        Path seed = dir.resolve("seeds.json");
        Path legacy = dir.resolve("fw.properties");
        Files.writeString(legacy, "fw.analyzer.seedOutputPath=" + seed + "\n", StandardCharsets.UTF_8);
        Result single = run("run", "--scenario", scenario.toString(), "--legacy-config", legacy.toString(), "--sut", "inline-metrics",
                "--set", "cf.analysis.enabled=true", "--set", "cf.analysis.minimize=latency", "--set", "cf.analysis.maximize=throughput");
        assertEquals(0, single.code(), single.err());
        assertTrue(Files.readString(seed).contains("\"schemaVersion\": 1,"), "the legacy key is mapped to cf.campaign.seed.output");

        Result campaign = run("run", "--scenario", scenario.toString(), "--sut", "inline-metrics", "--run-id", "cli", "--set",
                "cf.campaign.iterations=2", "--set", "cf.analysis.minimize=latency", "--set", "cf.analysis.maximize=throughput",
                "--format", "xml");
        assertEquals(0, campaign.code(), campaign.err());
        Element root = root(campaign.out());
        Element lineage = (Element) root.getElementsByTagName("campaign").item(0);
        assertEquals("COMPLETED", lineage.getAttribute("status"));
        assertEquals(2, lineage.getElementsByTagName("iteration").getLength());
        Element second = (Element) lineage.getElementsByTagName("iteration").item(1);
        assertEquals("cli-it2", second.getAttribute("runId"));
        assertEquals("BIASED", second.getAttribute("planStatus"));
        assertEquals("10", second.getAttribute("executable"), "p1..p5 with both modes");
    }

    @Test
    void anUnknownSutIsRefusedBeforeRunning(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir);
        Result unknown = run("run", "--scenario", scenario.toString(), "--sut", "no-such-plugin");
        assertEquals(3, unknown.code());
        assertTrue(unknown.err().contains("CONFIG_SUT_UNKNOWN"), unknown.err());
    }

    @Test
    void aCampaignCheckpointResumesWithItsCompleteReportsAndRejectsConfigurationDrift(@TempDir Path dir) throws Exception {
        Path scenario = profiles(dir);
        Path checkpoint = dir.resolve("campaign.cp.xml");
        Result first = run("run", "--scenario", scenario.toString(), "--sut", "inline-metrics", "--run-id", "resume-campaign",
                "--checkpoint", checkpoint.toString(), "--set", "cf.campaign.iterations=3", "--set", "cf.campaign.strategy=noop",
                "--set", "cf.analysis.minimize=latency", "--set", "cf.analysis.maximize=throughput", "--format", "xml");
        assertEquals(0, first.code(), first.err());
        assertTrue(Files.readString(checkpoint).contains("<cf-campaign-checkpoint schema=\"1\""));
        String saved = Files.readString(checkpoint);
        Result resumed = run("resume", "--checkpoint", checkpoint.toString(), "--format", "xml");
        assertEquals(0, resumed.code(), resumed.err());
        assertEquals(first.out().substring(first.out().indexOf("  <campaign"), first.out().indexOf("  </campaign>")),
                resumed.out().substring(resumed.out().indexOf("  <campaign"), resumed.out().indexOf("  </campaign>")),
                "completed runs, analysis, seeds and elapsed times are restored rather than rerun");
        assertEquals(saved, Files.readString(checkpoint));
        Result mismatch = run("resume", "--checkpoint", checkpoint.toString(), "--set", "cf.random.seed=91");
        assertEquals(3, mismatch.code(), mismatch.err());
        assertTrue(mismatch.err().contains("CHECKPOINT_MISMATCH"), mismatch.err());
        assertEquals(saved, Files.readString(checkpoint));
        Path blocked = Files.writeString(dir.resolve("blocked"), "file");
        Result failedWrite = run("run", "--scenario", scenario.toString(), "--sut", "inline-metrics", "--checkpoint",
                blocked.resolve("campaign.xml").toString(), "--set", "cf.campaign.iterations=2", "--set", "cf.campaign.strategy=noop",
                "--format", "xml");
        assertEquals(1, failedWrite.code(), "the checkpoint failure fails the command while preserving completed iterations");
        assertTrue(failedWrite.out().contains("CHECKPOINT_WRITE_FAILED"));
        assertEquals("COMPLETED", ((Element) root(failedWrite.out()).getElementsByTagName("campaign").item(0)).getAttribute("status"));
    }

    @Test
    void aggregateOnlyReportsItsRetentionAndAttachmentOmissionReason(@TempDir Path dir) throws Exception {
        Result aggregate = run("run", "--scenario", scenario(dir).toString(), "--set", "cf.results.retention=aggregate-only",
                "--set", "cf.results.attach=all", "--format", "xml");
        assertEquals(0, aggregate.code(), aggregate.err());
        assertTrue(aggregate.out().contains("retention=\"aggregate-only\""));
        assertTrue(aggregate.out().contains("omissionReason=\"aggregate-only retention\""));
    }

    @Test
    void aCampaignPausedInsideAnIterationResumesThroughTheCliFromItsAtomicFile(@TempDir Path dir) throws Exception {
        Path scenario = profiles(dir);
        Path file = dir.resolve("paused-campaign.xml");
        var base = ScenarioLoader.load(scenario).spec();
        List<ConfigResolver.Override> overrides = List.of("cf.execution.sut=inline-metrics", "cf.run.id=file-campaign",
                "cf.campaign.iterations=3", "cf.campaign.strategy=bias", "cf.execution.workers=1", "cf.pipeline.ordinal.window=2",
                "cf.analysis.minimize=latency", "cf.analysis.maximize=throughput", "cf.checkpoint.enabled=true", "cf.artifacts.enabled=true",
                "cf.checkpoint.file=" + file).stream().map(s -> new ConfigResolver.Override(s.substring(0, s.indexOf('=')),
                        s.substring(s.indexOf('=') + 1), "test")).toList();
        EffectiveConfig effective = ConfigResolver.resolve(null, Map.of(), overrides);
        Campaign.Result expected = Campaign.of(base, effective).run();
        Campaign campaign = Campaign.of(base, effective);
        AtomicBoolean saved = new AtomicBoolean();
        campaign.checkpointTo(scenario.toAbsolutePath().toString(), cp -> {
            CampaignCheckpointCodec.write(file, cp);
            if (cp.currentRun().isPresent()) saved.set(true);
        });
        campaign.observe((i, p) -> { if (i == 2 && p.committed() == 3) campaign.pause(); });
        CompletableFuture<Campaign.Result> future = CompletableFuture.supplyAsync(campaign::run);
        try {
            for (int i = 0; i < 1000 && !saved.get() && !future.isDone(); i++) Thread.sleep(10);
            assertTrue(saved.get(), "the drained pause was atomically written");
            assertEquals(RunState.PAUSED, campaign.state());
            assertEquals(2, CampaignCheckpointCodec.read(file).iteration());
        } finally { campaign.cancel(); }
        assertEquals(Campaign.Status.CANCELLED, future.get(10, TimeUnit.SECONDS).status());
        Result resumed = run("resume", "--checkpoint", file.toString(), "--format", "xml");
        assertEquals(0, resumed.code(), resumed.err());
        assertEquals(3, root(resumed.out()).getElementsByTagName("iteration").getLength());
        Campaign.Result restored = Campaign.resume(base, effective, CampaignCheckpointCodec.read(file)).run();
        assertEquals(expected.canonicalLineage(), restored.canonicalLineage());
    }

    @Test
    void aLegacyHandOverRunsItsJavaCandidatesAndAFailedExportLeavesTheResultsIntact(@TempDir Path dir) throws Exception {
        Path out = Files.createDirectories(dir.resolve("out"));
        Files.writeString(out.resolve("1_0_0.java"), "public class A { public static int FW_CUSTOM_VAR = -1; "
                + "public static void main(String[] a) { FW_CUSTOM_VAR = 0; } }", StandardCharsets.UTF_8);
        Files.writeString(out.resolve("2_0_0.java"), "public class B { public static int FW_CUSTOM_VAR = -1; "
                + "public static void main(String[] a) { FW_CUSTOM_VAR = a.length == 1 ? 2 : 9; } }", StandardCharsets.UTF_8);
        Path manifest = dir.resolve("manifest.json");
        Files.writeString(manifest, "{\"protocol\": \"bundle.handoff/v2\", \"run_id\": \"handed-over\", \"language\": \"java\", "
                + "\"candidate_transport\": \"loose-files\", \"candidate_count\": 2, \"id_format\": \"<combi_id>_0_0\", "
                + "\"sources\": [{\"kind\": \"loose-files\", \"path\": \"out\"}], \"result_target\": {\"host\": \"h\", "
                + "\"port\": 5432, \"database\": \"d\"}, \"result_schema_mode\": \"placeholders=6\", \"verdict_mode\": "
                + "\"FW_CUSTOM_VAR\", \"custom_verdicts\": [{\"code\": 2, \"message\": \"LOST\"}], \"arguments\": [\"x\"]}",
                StandardCharsets.UTF_8);
        Result r = run("run", "--legacy-handoff", manifest.toString(), "--format", "xml", "--out", dir.toString());
        assertEquals(1, r.code(), "the report could not be written to a directory: the command fails, the run did not");
        Element report = root(r.out());
        assertEquals("FAILED", report.getAttribute("exitName"));
        Element run = (Element) report.getElementsByTagName("run").item(0);
        assertEquals("handed-over", run.getAttribute("id"), "the manifest's run id");
        assertEquals("COMPLETED", run.getAttribute("state"));
        assertTrue(r.out().contains("<verdict code=\"2\" name=\"LOST\" count=\"1\"/>"), r.out());
        assertTrue(r.out().contains("<outcome name=\"PASS\" count=\"1\"/>"), r.out());
        assertTrue(r.out().contains("RESULT_EXPORT_FAILED"), r.out());
        Result both = run("run", "--legacy-handoff", manifest.toString(), "--scenario", manifest.toString());
        assertEquals(2, both.code(), both.err());
    }

    @Test
    void theStopPolicyEndsAdmissionAndAnErrorBudgetFailsTheCommand(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir);
        Result limited = run("run", "--scenario", scenario.toString(), "--set", "cf.run.stop.candidates=1", "--format", "xml");
        assertEquals(0, limited.code(), limited.err());
        Element stop = (Element) root(limited.out()).getElementsByTagName("stop").item(0);
        assertEquals("CANDIDATE_LIMIT", stop.getAttribute("reason"));
        assertEquals("1", stop.getAttribute("admitted"));
        Path broken = Files.writeString(dir.resolve("broken.json"), "{\"sheets\": {\"FW_Seq\": [[\"HEAD\", \"FW_Combi(1)\"], [\"X\", "
                + "\"FW_Combi(1)\"], [\"TAIL\", \"FW_Combi(1)\"]], \"HEAD\": [[\"public class M { public static int FW_VAR = -1; "
                + "public static void main(String[] a) {\"]], \"X\": [[\"FW_VAR = ;\"], [\"FW_VAR = 0;\"]], \"TAIL\": [[\"} }\"]]}}",
                StandardCharsets.UTF_8);
        Result budget = run("run", "--scenario", broken.toString(), "--set", "cf.run.stop.errors=1");
        assertEquals(1, budget.code(), "FAILED: " + budget.err());
        assertTrue(budget.err().contains("RUN_ERROR_BUDGET_EXHAUSTED"), budget.err());
        assertTrue(budget.out().contains("stopped     ERROR_BUDGET: 1 verdict samples ended BROKEN, TIMEOUT or INFRA_FAIL"), budget.out());
        Result xmlBudget = run("run", "--scenario", broken.toString(), "--set", "cf.run.stop.errors=1", "--format", "xml");
        assertEquals(1, xmlBudget.code());
        assertEquals("FAILED", root(xmlBudget.out()).getAttribute("status"), "command status must agree with its exit code");
        assertEquals("COMPLETED", ((Element) root(xmlBudget.out()).getElementsByTagName("run").item(0)).getAttribute("state"),
                "the completed observations remain results");
    }

    @Test
    void lineCoverageIsAMetricOfEveryVerdictSampleAndAStopTargetCanUseIt(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("lines.json"), "{\"sheets\": {\"FW_Seq\": [[\"HEAD\", \"FW_Combi(1)\"], [\"X\", "
                + "\"FW_Combi(1)\"], [\"TAIL\", \"FW_Combi(1)\"]], \"HEAD\": [[\"public class M {\\n public static int FW_VAR = -1;\\n "
                + "public static void main(String[] a) {\\n\"]], \"X\": [[\"if (a.length > 0) {\\n FW_VAR = 1;\\n }\\n FW_VAR = 0;\\n\"], "
                + "[\"FW_VAR = 0;\\n\"]], \"TAIL\": [[\"}\\n}\\n\"]]}}", StandardCharsets.UTF_8);
        // the sources keep their lines (by default rendering joins every value onto one line)
        Result full = run("run", "--scenario", scenario.toString(), "--set", "cf.render.preserveWhitespace=true", "--set",
                "cf.execution.coverage=lines", "--set", "cf.run.stop.target=coverage_line>=1", "--format", "xml");
        assertEquals(0, full.code(), full.err());
        Element stop = (Element) root(full.out()).getElementsByTagName("stop").item(0);
        assertEquals("TARGET_REACHED", stop.getAttribute("reason"), full.out());
        assertEquals("2", stop.getAttribute("boundary"), "the first candidate leaves a branch unrun; the second runs every line");
        assertTrue(stop.getAttribute("detail").startsWith("a PASS sample reported coverage_line = 1.0"), stop.getAttribute("detail"));
        Result text = run("run", "--scenario", scenario.toString(), "--set", "cf.render.preserveWhitespace=true", "--set",
                "cf.execution.coverage=lines");
        assertTrue(text.out().contains("compiler    2 sources compiled in memory"), "plain bytecode is not cached apart: " + text.out());

        Result plugin = run("run", "--scenario", profiles(dir).toString(), "--sut", "inline-metrics", "--set", "cf.execution.coverage=lines");
        assertEquals(4, plugin.code(), plugin.err());
        assertTrue(plugin.err().contains("UNSUPPORTED_CAPABILITY") && plugin.err().contains("cf.execution.coverage=lines"), plugin.err());
    }
}
