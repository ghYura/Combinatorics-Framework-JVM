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
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The candidates command (work package M03) end to end through the application, with companion and explicit sidecars. */
class CandidatesCommandTest {

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

    /** The parcel-dispatch lesson as a workbook in the legacy Core's JSON schedule format. */
    private static Path tutor(Path dir) throws IOException {
        return write(dir, "tutor.json", "{\"sheets\": {\"FW_Seq\": [[\"channel\", \"FW_Combi(1)\"], [\"account\", \"FW_Combi(1)\"], "
                + "[\"payment\", \"FW_Combi(1)\"], [\"steps\", \"FW_Permut()\"], [\"duplicate_submit\", \"FW_Optional\", \"FW_Combi(1)\"], "
                + "[\"carrier_timeout\", \"FW_Optional\", \"FW_Combi(1)\"]], "
                + "\"channel\": [[\"web\"], [\"mobile\"]], \"account\": [[\"guest\"], [\"member\"]], \"payment\": [[\"card\"], [\"wallet\"]], "
                + "\"steps\": [[\"reserve\"], [\"charge\"], [\"dispatch\"]], \"duplicate_submit\": [[\"dup\"]], "
                + "\"carrier_timeout\": [[\"timeout\"]]}}");
    }

    private static final String RULE = "{\"version\": 1, \"constraints\": [{\"id\": \"wallet_requires_member\", \"polarity\": \"forbid\", "
            + "\"pairs\": [{\"payment\": \"wallet\", \"account\": \"guest\"}]}]}";

    private static Element root(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    @Test
    void theCompanionSidecarIsReadAndTheTutorCountsAreReportedApart(@TempDir Path dir) throws Exception {
        Path scenario = tutor(dir);
        write(dir, "tutor.constraints.json", RULE);
        Result text = run("candidates", "--scenario", scenario.toString(), "--set", "cf.plan.autoPromoteSingleVerb=false", "--limit", "3");
        assertEquals(0, text.code(), text.err());
        assertTrue(text.out().contains("  generated     192\n"), text.out());
        assertTrue(text.out().contains("  removed       early 48, late 0 (overlap 0)\n"), text.out());
        assertTrue(text.out().contains("  executable    144\n"), text.out());
        assertTrue(text.out().contains("wallet_requires_member (early): removed 48"), text.out());
        assertTrue(text.out().contains("  first 3:\n"), text.out());

        Result xml = run("candidates", "--scenario", scenario.toString(), "--set", "cf.plan.autoPromoteSingleVerb=false", "--format", "xml");
        assertEquals(0, xml.code(), xml.err());
        Element candidates = (Element) root(xml.out()).getElementsByTagName("candidates").item(0);
        assertEquals("192", candidates.getAttribute("generated"));
        assertEquals("48", candidates.getAttribute("removedEarly"));
        assertEquals("144", candidates.getAttribute("executable"));
        NodeList shown = candidates.getElementsByTagName("candidate");
        assertEquals(10, shown.getLength(), "the default limit");
        assertTrue(((Element) shown.item(0)).getAttribute("id").matches("[0-9a-f]{64}"));
        assertEquals("1_0_0", ((Element) shown.item(0)).getAttribute("display"));
        assertTrue(xml.out().contains("CONSTRAINT_LOADED"), "the companion's reading is noted");
    }

    @Test
    void anExplicitSidecarBesideADifferentCompanionIsRefusedAndOneWithoutACompanionApplies(@TempDir Path dir) throws Exception {
        Path scenario = tutor(dir);
        Path explicit = write(dir, "other.json", RULE);
        Result alone = run("candidates", "--scenario", scenario.toString(), "--constraints", explicit.toString(), "--limit", "0",
                "--set", "cf.plan.autoPromoteSingleVerb=false");
        assertEquals(0, alone.code(), alone.err());
        assertTrue(alone.out().contains("  executable    144\n"), alone.out());
        write(dir, "tutor.constraints.json", "{\"version\": 1, \"constraints\": []}");
        Result both = run("candidates", "--scenario", scenario.toString(), "--constraints", explicit.toString());
        assertEquals(3, both.code(), "validation failed: " + both.err());
        assertTrue(both.err().contains("CONSTRAINT_AMBIGUOUS"), both.err());
    }

    @Test
    void invalidRulesAndValuesFailValidationWithTheirLocation(@TempDir Path dir) throws Exception {
        Path scenario = tutor(dir);
        write(dir, "tutor.constraints.json", "{\"version\": 1, \"constraints\": [{\"id\": \"x\", \"polarity\": \"Forbid\", "
                + "\"pairs\": [{\"payment\": \"wallet\", \"account\": \"guest\"}]}]}");
        Result bad = run("candidates", "--scenario", scenario.toString());
        assertEquals(3, bad.code(), bad.err());
        assertTrue(bad.err().contains("CONSTRAINT_INVALID") && bad.err().contains("tutor.constraints.json:1"), bad.err());
        write(dir, "tutor.constraints.json", "{\"version\": 1, \"constraints\": [{\"id\": \"x\", \"pairs\": "
                + "[{\"payment\": \"wallet\", \"nosuch\": \"guest\"}]}]}");
        Result unknown = run("candidates", "--scenario", scenario.toString());
        assertEquals(3, unknown.code(), unknown.err());
        assertTrue(unknown.err().contains("CONSTRAINT_UNKNOWN_SHEET"), unknown.err());
        Result limit = run("candidates", "--scenario", scenario.toString(), "--limit", "ten");
        assertEquals(2, limit.code(), limit.err());
        assertTrue(limit.err().contains("USAGE_INVALID_VALUE"), limit.err());
    }

    @Test
    void anEmptyRequiredSheetGivesZeroCandidatesWithItsReasonAndCoverageIsReportedAsGreedy(@TempDir Path dir) throws Exception {
        Path empty = write(dir, "empty.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(1)\"], [\"B\", \"FW_Cartes(E)\"]], "
                + "\"A\": [[\"a\"]], \"B\": [[\"b\"]], \"E\": []}}");
        Result r = run("candidates", "--scenario", empty.toString(), "--set", "cf.plan.autoPromoteSingleVerb=false");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("  generated     0\n"), r.out());
        assertTrue(r.err().contains("ASSEMBLY_EMPTY_FACTOR"), r.err());
        Result drop = run("candidates", "--scenario", empty.toString(), "--set", "cf.plan.autoPromoteSingleVerb=false",
                "--set", "cf.generation.emptyStagePolicy=LEGACY_DROP_WITH_DIAGNOSTIC");
        assertTrue(drop.out().contains("  generated     1\n"), drop.out());
        assertTrue(drop.err().contains("ASSEMBLY_SHEET_DROPPED"), drop.err());

        Path grid = write(dir, "grid.json", "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(1)\"], [\"B\", \"FW_Combi(1)\"], "
                + "[\"C\", \"FW_Combi(1)\"]], \"A\": [[\"a1\"], [\"a2\"], [\"a3\"]], \"B\": [[\"b1\"], [\"b2\"], [\"b3\"]], "
                + "\"C\": [[\"c1\"], [\"c2\"], [\"c3\"]]}}");
        Result covered = run("candidates", "--scenario", grid.toString(), "--set", "cf.plan.autoPromoteSingleVerb=false",
                "--set", "cf.coverage.strength=2", "--set", "cf.coverage.method=set-cover", "--format", "xml");
        assertEquals(0, covered.code(), covered.err());
        Element coverage = (Element) root(covered.out()).getElementsByTagName("coverage").item(0);
        assertEquals("not-claimed", coverage.getAttribute("minimum"));
        assertEquals("27", coverage.getAttribute("universe"));
        assertEquals(coverage.getAttribute("feasible"), coverage.getAttribute("covered"));
        assertFalse(covered.out().contains("optimal"));
    }
}
