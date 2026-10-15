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

import org.ghyura.cf.algebra.Counting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code plan --scenario} end to end, in-process (work package M02): the generator library and distinct tables reach
 * generation from the command line, the native configuration and an imported legacy configuration (decisions G6,
 * G7, C1), exact sizes beyond any machine word, explicit budget failures, and pending legacy settings reported rather
 * than refused (R-RUN-GATE).
 */
class PlanCommandTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** Sheet A with values a, b, c (labels 2, 3, 4): FW_Combi(3) then FW_Permut() makes the six orders of a b c. */
    private static Path permutations(Path dir) throws IOException {
        return write(dir, "scenario.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(3)\", \"FW_Permut()\"]], "
                + "\"A\": [[\"a\"], [\"b\"], [\"c\"]]}}");
    }

    private static final String PARALLEL_FIRST = "first: [2, 3, 4] [2, 4, 3] [3, 2, 4] [3, 4, 2] [4, 2, 3] ...";
    private static final String LIB3_FIRST = "first: [2, 3, 4] [2, 4, 3] [4, 2, 3] [4, 3, 2] [3, 4, 2] ...";

    private static Element rootOf(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return document.getDocumentElement();
    }

    @Test
    void planPrintsEveryTableWithItsExactSizeAndFirstRowsInTheConfiguredOrder(@TempDir Path dir) throws IOException {
        Path scenario = permutations(dir);
        Result parallel = run("plan", "--scenario", scenario.toString());
        assertEquals(0, parallel.code(), parallel.err());
        assertTrue(parallel.out().contains("cf.generation.library PARALLEL, cf.generation.distinctTables false"), parallel.out());
        assertTrue(parallel.out().contains("6 rows, computed (no rows held)"), parallel.out());
        assertTrue(parallel.out().contains(PARALLEL_FIRST), parallel.out());
        Result lib3 = run("plan", "--scenario", scenario.toString(), "--set", "cf.generation.library=COMBINATORICSLIB3");
        assertEquals(0, lib3.code(), lib3.err());
        assertTrue(lib3.out().contains(LIB3_FIRST), lib3.out());
    }

    @Test
    void anImportedLegacyConfigurationWithoutTheSwitchPlansInCombinatoricslib3OrderUntilANativeSettingOverridesIt(@TempDir Path dir)
            throws Exception {
        Path scenario = permutations(dir);
        Path absent = write(dir, "fw.properties", "seq.autoPromoteSingleVerb=true\n");
        Result legacy = run("plan", "--scenario", scenario.toString(), "--legacy-config", absent.toString());
        assertEquals(0, legacy.code(), legacy.err());
        assertTrue(legacy.out().contains(LIB3_FIRST), legacy.out());

        Path parallelFile = write(dir, "core.fw.properties", "core.threading.parallelizeSubCombosIfPossible=true\n");
        assertTrue(run("plan", "--scenario", scenario.toString(), "--legacy-config", parallelFile.toString()).out().contains(PARALLEL_FIRST));

        Result overridden = run("plan", "--scenario", scenario.toString(), "--legacy-config", absent.toString(), "--set",
                "cf.generation.library=PARALLEL", "--format", "xml");
        assertEquals(0, overridden.code(), overridden.err());
        Element root = rootOf(overridden.out());
        Element generation = (Element) root.getElementsByTagName("generation").item(0);
        assertEquals("PARALLEL", generation.getAttribute("library"));
        assertTrue(overridden.out().contains("<overridden key=\"cf.generation.library\" value=\"COMBINATORICSLIB3\" origin=\"LEGACY\" by=\"OVERRIDE\"/>"),
                overridden.out());
        Element table = (Element) generation.getElementsByTagName("table").item(0);
        assertEquals("6", table.getAttribute("rows"));
        assertEquals("computed", table.getAttribute("held"));
        assertEquals("[3, 2, 4]", table.getElementsByTagName("row").item(2).getTextContent());
    }

    @Test
    void distinctTablesCanBeSwitchedOnFromTheCommandLine(@TempDir Path dir) throws IOException {
        Path scenario = write(dir, "scenario.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(2)\", \"FW_Combi(1)\"]], "
                + "\"A\": [[\"a\"], [\"b\"], [\"c\"]]}}");
        Result plain = run("plan", "--scenario", scenario.toString());
        assertTrue(plain.out().contains("6 rows, computed"), plain.out());
        Result distinct = run("plan", "--scenario", scenario.toString(), "--set", "cf.generation.distinctTables=true");
        assertEquals(0, distinct.code(), distinct.err());
        assertTrue(distinct.out().contains("3 rows, stored ("), distinct.out());
        assertTrue(distinct.out().contains("first: [2] [3] [4]\n"), distinct.out());
    }

    @Test
    void aTableBeyondAnyMachineWordIsSizedExactlyWithoutBeingBuilt(@TempDir Path dir) throws IOException {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            values.append(i == 0 ? "" : ", ").append("[\"v").append(i).append("\"]");
        }
        // a lone FW_Permut() is promoted (the legacy rule), so each of the 25! rows is permuted once more
        Path scenario = write(dir, "scenario.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Permut()\"]], \"A\": [" + values + "]}}");
        Result r = run("plan", "--scenario", scenario.toString());
        assertEquals(0, r.code(), r.err());
        String size = Counting.factorial(25).pow(2).toString();
        assertTrue(r.out().contains(size + " rows, computed (no rows held)"), r.out());
    }

    @Test
    void aStoredTableThatDoesNotFitTheBudgetEndsWithResourceLimit(@TempDir Path dir) throws IOException {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            values.append(i == 0 ? "" : ", ").append("[\"v").append(i).append("\"]");
        }
        Path scenario = write(dir, "scenario.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(2)\", \"FW_Group\", \"FW_Combi(1)\"]], "
                + "\"A\": [" + values + "]}}");
        // 499500 grouped units need about 50 MB; the budget still holds every configured sub-budget
        Result r = run("plan", "--scenario", scenario.toString(), "--set", "cf.memory.budget.bytes=40000000");
        assertEquals(5, r.code(), r.err());
        assertEquals("", r.out());
        assertTrue(r.err().contains("RESOURCE_LIMIT") && r.err().contains("FW_Group units"), r.err());
    }

    @Test
    void planAppliesAnImportedLegacyConfiguration(@TempDir Path dir) throws IOException {
        Path scenario = permutations(dir);
        Path legacy = write(dir, "fw.properties", "core.threading.parallelizeSubCombosIfPossible=true\n"
                + "fw.analyzer.seedOutputPath=seeds.json\n");
        Result r = run("plan", "--scenario", scenario.toString(), "--legacy-config", legacy.toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains(PARALLEL_FIRST), r.out());
        assertFalse((r.out() + r.err()).contains("CONFIG_LEGACY_PENDING"), "no catalog setting is pending since M07: " + r.out());
    }

    /** The parcel-dispatch lesson (as in CandidatesCommandTest) with its one rule in the companion sidecar. */
    private static Path tutor(Path dir) throws IOException {
        write(dir, "tutor.constraints.json", "{\"version\": 1, \"constraints\": [{\"id\": \"wallet_requires_member\", \"polarity\": "
                + "\"forbid\", \"pairs\": [{\"payment\": \"wallet\", \"account\": \"guest\"}]}]}");
        return write(dir, "tutor.json", "{\"sheets\": {\"FW_Seq\": [[\"channel\", \"FW_Combi(1)\"], [\"account\", \"FW_Combi(1)\"], "
                + "[\"payment\", \"FW_Combi(1)\"], [\"steps\", \"FW_Permut()\"], [\"duplicate_submit\", \"FW_Optional\", \"FW_Combi(1)\"], "
                + "[\"carrier_timeout\", \"FW_Optional\", \"FW_Combi(1)\"]], "
                + "\"channel\": [[\"web\"], [\"mobile\"]], \"account\": [[\"guest\"], [\"member\"]], \"payment\": [[\"card\"], [\"wallet\"]], "
                + "\"steps\": [[\"reserve\"], [\"charge\"], [\"dispatch\"]], \"duplicate_submit\": [[\"dup\"]], "
                + "\"carrier_timeout\": [[\"timeout\"]]}}");
    }

    @Test
    void everyCountCarriesHowSureItIsWithItsFormulaAndRowLengths(@TempDir Path dir) throws Exception {
        Result perm = run("plan", "--scenario", permutations(dir).toString());
        assertTrue(perm.out().contains("EXACT: C(3,3) × 3!; rows by keys 3:6\n"), perm.out());

        Path scenario = tutor(dir);
        Result r = run("plan", "--scenario", scenario.toString(), "--set", "cf.plan.autoPromoteSingleVerb=false");
        assertEquals(0, r.code(), r.err());
        for (String line : new String[]{"EXACT: 3!; rows by keys 3:6\n",
                "  mandatory     48 (EXACT: |channel| × |account| × |payment| × |steps| = 2 × 2 × 2 × 6)\n",
                "  per row       4 (EXACT: 1 absent branch + 2 of size 1 + 1 of size 2)\n",
                "  generated     192 (EXACT: 48 × 4)\n",
                "  executable    144 (EXACT: 192 − 48 − 0 − 0 (generated − removed early − unselected − removed late, counted))\n",
                "  attempts      144 (EXACT: = samples: one attempt each)\n", "(run class S: ",
                "  duration-ms   ~1152000 in [0, 1152000] (ESTIMATED: ⌈144 / 4⌉ × (30000 + 2000))\n"}) {
            assertTrue(r.out().contains(line), line + " in " + r.out());
        }

        Result scanned = run("plan", "--scenario", scenario.toString(), "--set", "cf.plan.autoPromoteSingleVerb=false", "--scan", "10");
        assertTrue(scanned.out().contains("  executable    [0, 192] (BOUNDED: [0, generated])\n                because 1 rule(s) can remove "
                + "from none to all of these candidates; the plan counts them exactly only up to --scan 10 candidates\n"), scanned.out());

        Result repeated = run("plan", "--scenario", scenario.toString(), "--set", "cf.plan.autoPromoteSingleVerb=false", "--set",
                "cf.execution.repeat.count=3", "--set", "cf.execution.repeat.scope=metrics", "--set", "cf.execution.retry.maxAttempts=2",
                "--set", "cf.run.stop.candidates=100", "--set", "cf.run.stop.errors=5");
        for (String line : new String[]{"  admitted      [0, 100] (BOUNDED: min(executable, 100) or fewer)\n",
                "                because cf.run.stop.errors can end admission earlier\n",
                "  samples       [0, 300] (BOUNDED: admitted × 3 (K=3, local/metrics))\n",
                "  verdicts      [0, 100] (BOUNDED: admitted × 1 (K=3, local/metrics))\n",
                "  measurements  [0, 200] (BOUNDED: admitted × 2 (K=3, local/metrics))\n",
                "  attempts      [0, 600] (BOUNDED: [verdicts, samples × 2])\n"}) {
            assertTrue(repeated.out().contains(line), line + " in " + repeated.out());
        }

        Result xml = run("plan", "--scenario", scenario.toString(), "--set", "cf.plan.autoPromoteSingleVerb=false", "--format", "xml");
        assertEquals(0, xml.code(), xml.err());
        Element root = rootOf(xml.out());
        Element steps = (Element) root.getElementsByTagName("table").item(3);
        assertEquals("3!", steps.getAttribute("formula"));
        assertEquals("EXACT", steps.getAttribute("mode"));
        Element length = (Element) steps.getElementsByTagName("length").item(0);
        assertEquals("3", length.getAttribute("keys"));
        assertEquals("6", length.getAttribute("rows"));
        Element cardinality = (Element) root.getElementsByTagName("cardinality").item(0);
        Element executable = (Element) cardinality.getElementsByTagName("estimate").item(3);
        assertEquals("executable", executable.getAttribute("name"));
        assertEquals("EXACT", executable.getAttribute("mode"));
        assertEquals("144", executable.getAttribute("value"));
        Element resources = (Element) root.getElementsByTagName("resources").item(0);
        assertEquals("S", resources.getAttribute("runClass"));
        Element results = (Element) resources.getElementsByTagName("estimate").item(1);
        assertEquals("result-bytes", results.getAttribute("name"));
        assertEquals("ESTIMATED", results.getAttribute("mode"), "an assumed cost stays an estimate however exact the count is");
        assertEquals(1, results.getElementsByTagName("assumption").getLength());
        assertTrue(Long.parseLong(resources.getAttribute("assemblyBytes")) > 0, "the memory reserved is reported apart, exactly");
    }

    @Test
    void aCountThePlanCannotKnowStaysUnknownAndNeverBecomesZero(@TempDir Path dir) throws IOException {
        Result r = run("plan", "--scenario", tutor(dir).toString(), "--set", "cf.plan.autoPromoteSingleVerb=false", "--set",
                "cf.coverage.strength=2", "--set", "cf.coverage.hardCap=10");
        assertEquals(0, r.code(), "a plan explains; it does not fail on a candidate space it cannot assemble: " + r.err());
        for (String line : new String[]{"  executable    unknown (UNKNOWN: the candidate space could not be assembled)\n"
                + "                because covering selection works on the 48 mandatory rows, more than cf.coverage.hardCap=10\n",
                "  samples       unknown (UNKNOWN: admitted × 1 (K=1, local/all))\n",
                "  measurements  0 (EXACT: admitted × 0 (K=1, local/all))\n", "(run class X: ",
                "  duration-ms   unknown (UNKNOWN: ⌈attempts / workers⌉ × (timeout + grace))\n"}) {
            assertTrue(r.out().contains(line), line + " in " + r.out());
        }
        Result budgeted = run("plan", "--scenario", tutor(dir).toString(), "--set", "cf.plan.autoPromoteSingleVerb=false", "--set",
                "cf.coverage.strength=2", "--set", "cf.coverage.hardCap=10", "--set", "cf.run.budget.millis=60000");
        assertTrue(budgeted.out().contains("  duration-ms   ~62000 in [0, 62000] (ESTIMATED: cf.run.budget.millis + grace)\n"), budgeted.out());
    }

    private static Element estimate(Element root, String name) {
        var estimates = root.getElementsByTagName("estimate");
        for (int i = 0; i < estimates.getLength(); i++) {
            Element e = (Element) estimates.item(i);
            if (name.equals(e.getAttribute("name"))) return e;
        }
        throw new AssertionError("missing estimate " + name);
    }

    @Test
    void sourceSamplingCannotWalkPastTheScanLimitToFindASurvivor(@TempDir Path dir) throws Exception {
        Path scenario = write(dir, "filtered.json", """
                {"constraints":[{"id":"none","assert":{"sheet":"P","present":false},"polarity":"require"}],
                 "slots":[{"sheet":"P","values":["a","b"],"verb":"FW_PermutR(40)"}]}
                """);
        Result r = run("plan", "--scenario", scenario.toString(), "--scan", "1", "--set",
                "cf.plan.autoPromoteSingleVerb=false", "--format", "xml");
        assertEquals(0, r.code(), r.err());
        Element root = rootOf(r.out());
        assertEquals(BigInteger.ONE.shiftLeft(40).toString(), estimate(root, "generated").getAttribute("value"));
        assertEquals("BOUNDED", estimate(root, "executable").getAttribute("mode"));
        Element source = estimate(root, "source-bytes");
        assertEquals("UNKNOWN", source.getAttribute("mode"));
        assertTrue(source.getTextContent().contains("could exceed --scan 1"), source.getTextContent());
    }

    @Test
    void serializedDurationUsesOneWorkerAndStatesItsModelAssumptions(@TempDir Path dir) throws Exception {
        Result r = run("plan", "--scenario", permutations(dir).toString(), "--set", "cf.execution.serialize=true", "--format", "xml");
        assertEquals(0, r.code(), r.err());
        Element duration = estimate(rootOf(r.out()), "duration-ms");
        assertEquals("ESTIMATED", duration.getAttribute("mode"));
        assertEquals("192000", duration.getAttribute("value"));
        assertEquals("⌈6 / 1⌉ × (30000 + 2000)", duration.getAttribute("formula"));
        assertTrue(duration.getTextContent().contains("not a wall-clock upper bound"), duration.getTextContent());
    }

    @Test
    void expensiveOptionalHistogramsDoNotPreventExactSizingOrPreview(@TempDir Path dir) throws Exception {
        Path scenario = write(dir, "large-subsets.json", """
                {"sheets":{"FW_Seq":[["A","FW_PermutR(8192)","FW_Subsets"]],"A":[["a"]]}}
                """);
        Result r = run("plan", "--scenario", scenario.toString(), "--scan", "1", "--format", "xml");
        assertEquals(0, r.code(), r.err());
        Element table = (Element) rootOf(r.out()).getElementsByTagName("table").item(0);
        assertEquals("EXACT", table.getAttribute("mode"));
        assertEquals(BigInteger.ONE.shiftLeft(8192).toString(), table.getAttribute("rows"));
        assertEquals("[]", table.getElementsByTagName("row").item(0).getTextContent());
        Element lengths = (Element) table.getElementsByTagName("lengths").item(0);
        assertEquals("UNKNOWN", lengths.getAttribute("mode"));
        assertTrue(lengths.getAttribute("reason").contains("optional metadata allowance"), lengths.getAttribute("reason"));
    }

    @Test
    void theDependencyGraphIsReportedAndWrittenAsDotOrJson(@TempDir Path dir) throws IOException {
        Path scenario = write(dir, "joined.json", "{\"sheets\": {\"FW_Seq\": [[\"L\", \"FW_Exclude\", \"FW_Combi(1)\"], [\"R\", "
                + "\"FW_Exclude\", \"FW_Combi(1)\"], [\"LR\", \"FW_(,,L,,R,,,,1:N)\"], [\"IDLE\", \"FW_Exclude\", \"FW_Combi(1)\"]], "
                + "\"L\": [[\"l1\"], [\"l2\"]], \"R\": [[\"r1\"]], \"LR\": [[\"x\"]], \"IDLE\": [[\"i\"]]}}");
        Path dot = dir.resolve("joined.dot");
        Result r = run("plan", "--scenario", scenario.toString(), "--graph", dot.toString());
        assertEquals(0, r.code(), r.err());
        String hash = Files.readString(dot).isEmpty() ? "" : r.out().replaceAll("(?s).*\\(sha256:([0-9a-f]{64})\\)\\n.*", "sha256:$1");
        assertTrue(r.out().contains("\n  graph         5 nodes, 3 edges, " + hash + "; 0 errors, 1 warnings\n    ⚠ [unused_advanced_node] slot "
                + "'IDLE' is FW_Exclude'd (set aside from the cartesian) but never consumed by a brace operand — it is unreachable and "
                + "contributes nothing\n"), r.out());
        assertTrue(r.out().endsWith("  ✓ dependency graph -> " + dot + " (" + hash + ")\n"), r.out());
        assertEquals("""
                digraph fwseq {
                  rankdir=LR;
                  "IDLE" [shape=ellipse, label="IDLE\\n[excluded]"];
                  "L" [shape=ellipse, label="L\\n[excluded]"];
                  "LR" [shape=ellipse, label="LR\\n[join-result]"];
                  "R" [shape=ellipse, label="R\\n[excluded]"];
                  "brace#0.1" [shape=box, label="brace#0.1"];
                  "LR" -> "brace#0.1" [label="produces"];
                  "brace#0.1" -> "L" [label="brace_operand"];
                  "brace#0.1" -> "R" [label="brace_operand"];
                }
                """, Files.readString(dot));
        Path json = dir.resolve("joined.graph.json");
        Result xml = run("plan", "--scenario", scenario.toString(), "--graph", json.toString(), "--format", "xml");
        assertEquals(0, xml.code(), xml.err());
        assertTrue(Files.readString(json).startsWith("{\n  \"nodes\": [\n    {\n      \"id\": \"IDLE\",\n      \"kind\": \"slot\",\n"),
                Files.readString(json));
        assertTrue(xml.out().contains("<dependency-graph hash=\"" + hash + "\" nodes=\"5\" edges=\"3\">\n      <issue level=\"warning\" "
                + "code=\"unused_advanced_node\" nodes=\"IDLE\">"), xml.out());
    }
}
