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

package org.ghyura.cf.analysis;

import org.ghyura.cf.config.ConfigResolver;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AN-12 autoplan against goldens of the unchanged legacy AutoAnalysisPlanner (handoff/outbox/devtools/AutoPlanGoldens.java). */
class AutoPlanTest {

    @Test
    void fileSamplingBoundsLongLinesAndStopsAtThePrefix(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var budget = new org.ghyura.cf.store.MemoryBudget(4L << 20);
        var file = dir.resolve("sample.data");
        java.nio.file.Files.writeString(file, "latency=3\r\nlatency=1\rlatency=2\n");
        assertEquals(AutoPlan.plan(List.of("latency=3", "latency=1", "latency=2")), AutoPlan.read(file, budget));
        java.nio.file.Files.writeString(file, "latency=1\n".repeat(500) + "x".repeat(100_000));
        assertEquals(500, AutoPlan.read(file, budget).sampledLines(), "unused suffix must not consume the sampling budget");
        java.nio.file.Files.writeString(file, "x".repeat(100_000));
        var refusal = org.junit.jupiter.api.Assertions.assertThrows(org.ghyura.cf.model.FrameworkException.class,
                () -> AutoPlan.read(file, budget));
        assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT, refusal.diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());
        java.nio.file.Files.write(file, new byte[]{(byte) 0xc3, (byte) 0x28});
        var encoding = org.junit.jupiter.api.Assertions.assertThrows(org.ghyura.cf.model.FrameworkException.class,
                () -> AutoPlan.read(file, budget));
        assertEquals(org.ghyura.cf.model.ErrorCode.INPUT_MALFORMED, encoding.diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void suggestionsMatchTheOriginalPlannerAndAreExplicitSettingsThatNeverEnableExecution() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        List<Element> cases = new ArrayList<>();
        try (var in = getClass().getResourceAsStream("autoplan-goldens.xml")) {
            assertNotNull(in);
            var root = factory.newDocumentBuilder().parse(in).getDocumentElement();
            for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && e.getTagName().equals("case")) cases.add(e);
            }
        }
        assertEquals(5, cases.size());
        boolean legacyWouldExecute = false;
        for (Element c : cases) {
            String id = c.getAttribute("id");
            String corpus = new String(Base64.getDecoder().decode(child(c, "corpus").get(0).getTextContent().strip()), StandardCharsets.UTF_8);
            AutoPlan.Plan plan = AutoPlan.plan(corpus.lines().toList());
            assertEquals(Integer.parseInt(c.getAttribute("sampled")), plan.sampledLines(), id);
            assertEquals(Integer.parseInt(c.getAttribute("nonEmpty")), plan.nonEmptyLines(), id);
            assertEquals(Integer.parseInt(c.getAttribute("shellish")), plan.shellishLines(), id);
            assertEquals(Integer.parseInt(c.getAttribute("pythonEligible")), plan.pythonEligibleLines(), id);
            legacyWouldExecute |= Boolean.parseBoolean(c.getAttribute("recommendExecute"));
            List<String> coverage = new ArrayList<>();
            for (Element e : child(c, "coverage")) coverage.add(e.getAttribute("key") + "=" + e.getAttribute("count"));
            List<String> ours = new ArrayList<>();
            plan.coverage().forEach((k, v) -> ours.add(k + "=" + v));
            assertEquals(coverage, ours, id + ": counts, most seen first, ties in first-seen order");
            List<Element> goals = child(c, "goal");
            assertEquals(goals.size(), plan.goals().size(), id);
            for (int i = 0; i < goals.size(); i++) {
                Element g = goals.get(i);
                AutoPlan.Recommendation r = plan.goals().get(i);
                String at = id + " goal " + i;
                assertEquals(g.getAttribute("key"), r.key(), at);
                assertEquals(g.getAttribute("mode"), r.mode() == Goal.Mode.MAXIMIZE ? "maximize" : "minimize", at);
                assertEquals(Double.parseDouble(g.getAttribute("target")), r.target(), at);
                assertEquals(Double.parseDouble(g.getAttribute("weight")), r.weight(), at);
                assertEquals(Integer.parseInt(g.getAttribute("support")), r.support(), at);
                assertEquals(Double.parseDouble(g.getAttribute("median")), r.median(), at);
                assertEquals(Double.parseDouble(g.getAttribute("min")), r.min(), at);
                assertEquals(Double.parseDouble(g.getAttribute("max")), r.max(), at);
                assertEquals(g.getAttribute("reason"), r.reason(), at);
            }
            if (!plan.goals().isEmpty()) {
                RuleSet rules = RuleSet.parse(plan.rulesJson(), id);
                assertEquals(plan.goals().size(), rules.goals().size(), id + ": the suggestion is valid manual rules");
                var effective = ConfigResolver.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.analysis.goals", plan.goalsSetting(), id)));
                assertEquals(plan.goals().size(), effective.config().analysisGoals().size(), id + ": and a valid cf.analysis.goals");
            }
            assertFalse(plan.toText().contains("execute each line"), id);
        }
        assertTrue(legacyWouldExecute, "the legacy planner recommended executing shell-like lines; this plan only reports them");
    }

    private static List<Element> child(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) out.add(e);
        }
        return out;
    }
}
