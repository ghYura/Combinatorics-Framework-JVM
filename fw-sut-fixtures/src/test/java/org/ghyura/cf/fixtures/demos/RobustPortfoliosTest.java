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

package org.ghyura.cf.fixtures.demos;

import org.ghyura.cf.fixtures.demos.CoalitionAllocation.Fraction;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RobustPortfoliosTest {
    private Element golden() throws Exception {
        var f = DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d9-golden.xml")) { assertNotNull(in); return f.newDocumentBuilder().parse(in).getDocumentElement(); }
    }
    private List<PortfolioRanking.Observed> observations() throws Exception {
        var cases = golden().getElementsByTagName("case"); var observations = new ArrayList<PortfolioRanking.Observed>();
        for (int i = 0; i < cases.getLength(); i++) {
            var c = (Element)cases.item(i); var f = fields(c);
            observations.add(new PortfolioRanking.Observed(c.getAttribute("id"), f.get("design"), Integer.parseInt(f.get("demand")), Integer.parseInt(f.get("disruption")), c.getAttribute("verdict"), new BigInteger(f.get("total_cost"))));
        }
        return observations;
    }
    private Map<String, String> fields(Element c) {
        var fields = new java.util.TreeMap<String, String>(); var ns = c.getElementsByTagName("field");
        for (int i = 0; i < ns.getLength(); i++) { var n = (Element)ns.item(i); fields.put(n.getAttribute("name"), n.getTextContent()); }
        return fields;
    }
    private String join(List<?> values) { return values.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" ")); }
    @Test void all1024CostsAndIndependentReferencesMatchEveryFrozenField() throws Exception {
        var cases = golden().getElementsByTagName("case"); assertEquals(1024, cases.getLength());
        for (int i = 0; i < cases.getLength(); i++) {
            var c = (Element)cases.item(i); var f = fields(c); var r = RobustPortfolios.evaluate(f.get("design"), Integer.parseInt(f.get("demand")), Integer.parseInt(f.get("disruption")));
            assertEquals(c.getAttribute("id"), r.caseId()); assertTrue(r.passed()); var a = RobustPortfolios.attributes(r);
            for (var field : f.entrySet()) assertEquals(field.getValue(), a.get(field.getKey()), r.caseId() + " " + field.getKey());
            for (String field : RobustPortfolios.FIELDS) assertEquals(f.get(field), a.get("reference_" + field));
        }
        assertEquals(4, RobustPortfolios.evaluate("000000",0,0).observed().totalCost());
        var clamped = RobustPortfolios.evaluate("111111",3,1).observed(); assertEquals(-3, clamped.rawLoss()); assertEquals(0, clamped.residualLoss()); assertEquals(28, clamped.totalCost());
    }
    @Test void allRankingsMeansWeightsTiesAndJointSensitivityMatchIndependentPredictions() throws Exception {
        var g = golden(); var r = PortfolioRanking.rank(observations());
        var minimax = (Element)g.getElementsByTagName("minimax").item(0);
        assertEquals(minimax.getAttribute("ranking"), join(r.minimaxRanking())); assertEquals(minimax.getAttribute("primary-ties"), join(r.primaryTies()));
        var profiles = g.getElementsByTagName("profile");
        for (int i = 0; i < profiles.getLength(); i++) {
            var p = (Element)profiles.item(i); var actual = r.profiles().get(p.getAttribute("name"));
            assertEquals(p.getAttribute("demand-weights"), join(actual.demandWeights())); assertEquals(p.getAttribute("disruption-weights"), join(actual.disruptionWeights()));
            assertEquals(p.getAttribute("world-weights"), join(actual.worldWeights())); assertEquals(Fraction.of(1,1), actual.worldWeights().stream().reduce(Fraction.of(0,1),Fraction::add));
        }
        var expected = g.getElementsByTagName("expected-ranking");
        for (int i = 0; i < expected.getLength(); i++) { var p = (Element)expected.item(i); assertEquals(p.getAttribute("ranking"), join(r.expectedRankings().get(p.getAttribute("profile")))); }
        var summaries = g.getElementsByTagName("summary"); assertEquals(64, summaries.getLength());
        for (int i = 0; i < summaries.getLength(); i++) {
            var s = (Element)summaries.item(i); var got = r.summaries().get(i);
            assertEquals(s.getAttribute("design"), got.design()); assertEquals(s.getAttribute("worst-cost"), got.worstCost().toString()); assertEquals(s.getAttribute("worst-worlds"), join(got.worstWorlds()));
            var means = s.getElementsByTagName("mean"); for (int j = 0; j < means.getLength(); j++) { var m = (Element)means.item(j); assertEquals(m.getAttribute("value"), got.expected().get(m.getAttribute("profile")).toString()); }
        }
        var sensitivity = g.getElementsByTagName("sensitivity"); assertEquals(11, r.sensitivity().size());
        for (int i = 0; i < sensitivity.getLength(); i++) {
            var s = (Element)sensitivity.item(i); var got = r.sensitivity().get(i);
            assertEquals(s.getAttribute("alpha"), got.alpha().toString()); assertEquals(s.getAttribute("ranking"), join(got.ranking())); assertEquals(s.getAttribute("winner"), got.winner()); assertEquals(s.getAttribute("winner-expected"), got.winnerExpected().toString());
        }
    }
    @Test void invalidSetupAndIncompleteSupportAreRefusedAndChangedObservedCostsAreUsed() throws Exception {
        var valid = observations(); var missing = new ArrayList<>(valid); missing.removeFirst(); assertThrows(IllegalArgumentException.class, () -> PortfolioRanking.rank(missing));
        var duplicate = new ArrayList<>(valid); duplicate.set(1, valid.getFirst()); assertThrows(IllegalArgumentException.class, () -> PortfolioRanking.rank(duplicate));
        for (var bad : List.of(new PortfolioRanking.Observed("P=000000|D=0|X=0","000001",0,0,"PASS",BigInteger.ONE),
                new PortfolioRanking.Observed("P=000000|D=4|X=0","000000",4,0,"PASS",BigInteger.ONE),
                new PortfolioRanking.Observed("P=000000|D=0|X=0","000000",0,0,"DOMAIN_FAIL",BigInteger.ONE),
                new PortfolioRanking.Observed("P=000000|D=0|X=0","000000",0,0,"PASS",null))) {
            var edited = new ArrayList<>(valid); edited.set(0,bad); assertThrows(IllegalArgumentException.class, () -> PortfolioRanking.rank(edited));
        }
        var edited = new ArrayList<>(valid); BigInteger huge = BigInteger.TEN.pow(50);
        for (int i = 0; i < edited.size(); i++) if (edited.get(i).design().equals("110000")) {
            var o = edited.get(i); edited.set(i,new PortfolioRanking.Observed(o.caseId(),o.design(),o.demand(),o.disruption(),o.verdict(),huge));
        }
        var changed = PortfolioRanking.rank(edited); assertEquals("110001", changed.minimaxRanking().getFirst()); assertEquals(List.of("110001"), changed.primaryTies());
        assertEquals(huge, changed.summaries().get(48).worstCost()); assertEquals(new Fraction(huge,BigInteger.ONE), changed.summaries().get(48).expected().get("uniform"));
        assertEquals(new RobustPortfolioPlugin.Parsed("000000",0,0), RobustPortfolioPlugin.parseAtoms(List.of(),"demand(0)\n","disruption(0)\n"));
        for (var atoms : List.of(List.of("enable(\"bad\")\n"), List.of("enable(\"cache\")"), List.of("enable(\"cache\")\n","enable(\"cache\")\n"), List.of("enable(\"quota\")\n","enable(\"cache\")\n")))
            assertThrows(IllegalArgumentException.class, () -> RobustPortfolioPlugin.parseAtoms(atoms,"demand(0)\n","disruption(0)\n"));
        assertThrows(IllegalArgumentException.class, () -> RobustPortfolioPlugin.parseAtoms(List.of(),"demand(4)\n","disruption(0)\n"));
        assertThrows(IllegalArgumentException.class, () -> RobustPortfolioPlugin.parseAtoms(List.of(),"demand(0)\n","disruption(0)"));
        assertThrows(IllegalArgumentException.class, () -> RobustPortfolios.evaluate("000002",0,0));
        var c = RobustPortfolios.sut("000000",0,0); var badCost = new RobustPortfolios.Cost(c.fixedCost(),c.grossLoss(),c.reductions(),c.rawLoss(),c.residualLoss(),99);
        assertEquals(List.of("total_cost"), RobustPortfolios.judge("000000",0,0,badCost).mismatched());
    }
}
