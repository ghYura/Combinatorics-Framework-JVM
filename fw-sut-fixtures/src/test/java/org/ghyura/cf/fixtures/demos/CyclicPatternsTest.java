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
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class CyclicPatternsTest {
    private Element golden() throws Exception {
        var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d12-golden.xml")) { assertNotNull(in); return f.newDocumentBuilder().parse(in).getDocumentElement(); }
    }
    private Map<String,String> fields(Element c) {
        var fields=new TreeMap<String,String>(); var nodes=c.getElementsByTagName("field");
        for (int i=0;i<nodes.getLength();i++) { var n=(Element)nodes.item(i); fields.put(n.getAttribute("name"),n.getTextContent()); } return fields;
    }
    private List<CyclicAnalysis.Observed> observations(String campaign) throws Exception {
        var cases=golden().getElementsByTagName("case"); var reps=CyclicAnalysis.representatives(); var result=new ArrayList<CyclicAnalysis.Observed>();
        for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); var fields=fields(c); String w=fields.get("word"); if (campaign.equals("words")||reps.contains(w)) result.add(new CyclicAnalysis.Observed(c.getAttribute("id"),w,campaign,"PASS",fields)); } return result;
    }
    private String join(List<?> items) { return items.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" ")); }
    @Test void all729DirectedRingScoresAndIndependentRotationReferencesMatchEveryFrozenField() throws Exception {
        var cases=golden().getElementsByTagName("case"); assertEquals(729,cases.getLength());
        for (int i=0;i<cases.getLength();i++) {
            var c=(Element)cases.item(i); var expected=fields(c); var r=CyclicPatterns.evaluate("words",expected.get("word")); assertTrue(r.passed()); assertEquals(c.getAttribute("id"),r.caseId()); var a=CyclicPatterns.attributes(r);
            for (var f : expected.entrySet()) assertEquals(f.getValue(),a.get(f.getKey()),r.word()+" "+f.getKey());
            for (String k : CyclicPatterns.COMPARED) assertEquals(expected.get(k),a.get("reference_"+k));
        }
        assertEquals(25,CyclicPatterns.sut("AAAAAB").core().totalCost()); assertEquals(25,CyclicPatterns.sut("BAAAAA").core().totalCost());
        assertEquals(0,CyclicPatterns.sut("ABCABC").core().totalCost()); assertEquals(18,CyclicPatterns.sut("ACBACB").core().totalCost());
    }
    @Test void allOrbitsBurnsideReflectionPopulationsRankingAndMassesMatchIndependentPredictions() throws Exception {
        var g=golden(); var r=CyclicAnalysis.analyse(observations("words"),observations("classes")); var partition=(Element)g.getElementsByTagName("partition").item(0);
        assertEquals(((Element)g.getElementsByTagName("catalogue").item(0)).getAttribute("representatives"),join(CyclicAnalysis.representatives()));
        assertTrue(r.disjointComplete()); assertEquals(partition.getAttribute("burnside-fixed-counts"),join(r.burnside())); assertEquals(54,r.selfReflectionClasses()); assertEquals(38,r.mirrorPairs()); assertEquals(Map.of(1,3,2,3,3,8,6,116),r.orbitSizeHistogram());
        var orbits=g.getElementsByTagName("orbit"); assertEquals(130,r.orbits().size());
        for (int i=0;i<orbits.getLength();i++) {
            var o=(Element)orbits.item(i); var got=r.orbits().get(i); assertEquals(o.getAttribute("representative"),got.representative()); assertEquals(o.getAttribute("members"),join(got.members())); assertEquals(o.getAttribute("size"),Integer.toString(got.size()));
            assertEquals(o.getAttribute("period"),Integer.toString(got.period())); assertEquals(o.getAttribute("stabilizer-size"),Integer.toString(got.stabilizerSize())); assertEquals(o.getAttribute("reflected-representative"),got.reflectedRepresentative()); assertEquals(o.getAttribute("total-cost"),got.totalCost().toString());
        }
        var populations=g.getElementsByTagName("population");
        for (int i=0;i<populations.getLength();i++) {
            var p=(Element)populations.item(i); var got=r.populations().get(p.getAttribute("name")); assertEquals(p.getAttribute("label"),got.label()); assertEquals(p.getAttribute("mean"),got.mean().toString()); var hist=new TreeMap<BigInteger,Integer>(); var bins=p.getElementsByTagName("bin");
            for (int k=0;k<bins.getLength();k++) { var b=(Element)bins.item(k); hist.put(new BigInteger(b.getAttribute("cost")),Integer.valueOf(b.getAttribute("count"))); } assertEquals(hist,got.histogram());
        }
        var ranking=(Element)g.getElementsByTagName("ranking").item(0); assertEquals(ranking.getAttribute("order"),join(r.ranking())); assertEquals(ranking.getAttribute("best-classes"),join(r.bestClasses())); assertEquals(ranking.getAttribute("best-labelled-words"),join(r.bestLabelledWords()));
        assertEquals(ranking.getAttribute("best-class-mass"),r.masses().get("best_class_of_classes").toString()); assertEquals(ranking.getAttribute("best-word-mass"),r.masses().get("best_words_of_words").toString()); assertEquals(130,r.representativesMatched()); assertTrue(r.mismatches().isEmpty()); assertTrue(r.weightedHistogramEqualsWords());
        var wordsOnly=CyclicAnalysis.analyse(observations("words")); assertFalse(wordsOnly.hasClasses()); assertEquals(r.orbits(),wordsOnly.orbits()); assertEquals(Map.of("words",r.populations().get("words")),wordsOnly.populations()); assertTrue(wordsOnly.ranking().isEmpty());
    }
    @Test void malformedSetupAndBadSupportAreRefusedAndChangedObservedCostsAndCrossMismatchesAreRetained() throws Exception {
        var words=observations("words"); var classes=observations("classes"); var missing=new ArrayList<>(words); missing.removeLast(); assertThrows(IllegalArgumentException.class,() -> CyclicAnalysis.analyse(missing));
        var duplicate=new ArrayList<>(words); duplicate.set(1,words.getFirst()); assertThrows(IllegalArgumentException.class,() -> CyclicAnalysis.analyse(duplicate));
        for (var bad : List.of(new CyclicAnalysis.Observed("W=AAAAAA","AAAAAB","words","PASS",words.getFirst().fields()),new CyclicAnalysis.Observed("W=AAAAAA","AAAAAA","classes","PASS",words.getFirst().fields()),new CyclicAnalysis.Observed("W=AAAAAA","AAAAAA","words","DOMAIN_FAIL",words.getFirst().fields()))) {
            var edited=new ArrayList<>(words); edited.set(0,bad); assertThrows(IllegalArgumentException.class,() -> CyclicAnalysis.analyse(edited));
        }
        assertThrows(IllegalArgumentException.class,() -> CyclicAnalysis.analyse(words,List.of()));
        var nonconstant=new ArrayList<>(words); var old=nonconstant.get(1); var changed=new TreeMap<>(old.fields()); changed.put("total_cost","999"); nonconstant.set(1,new CyclicAnalysis.Observed(old.caseId(),old.word(),old.campaign(),old.verdict(),changed)); assertThrows(IllegalArgumentException.class,() -> CyclicAnalysis.analyse(nonconstant));
        BigInteger huge=BigInteger.TEN.pow(40); var changedWords=new ArrayList<>(words); var changedClasses=new ArrayList<>(classes);
        for (var list : List.of(changedWords,changedClasses)) for (int i=0;i<list.size();i++) if (CyclicAnalysis.rotations(list.get(i).word()).getFirst().equals("ABCABC")) {
            var o=list.get(i); var f=new TreeMap<>(o.fields()); f.put("total_cost",huge.toString()); list.set(i,new CyclicAnalysis.Observed(o.caseId(),o.word(),o.campaign(),o.verdict(),f));
        }
        var altered=CyclicAnalysis.analyse(changedWords,changedClasses); assertNotEquals(List.of("ABCABC"),altered.bestClasses()); assertEquals(new Fraction(BigInteger.valueOf(14).multiply(BigInteger.valueOf(729)).add(huge.multiply(BigInteger.valueOf(3))),BigInteger.valueOf(729)),altered.populations().get("words").mean()); assertTrue(altered.weightedHistogramEqualsWords());
        var mismatched=new ArrayList<>(classes); old=mismatched.getFirst(); changed=new TreeMap<>(old.fields()); changed.put("edge_costs","[0, 0, 0, 0, 0, 0]"); mismatched.set(0,new CyclicAnalysis.Observed(old.caseId(),old.word(),old.campaign(),old.verdict(),changed)); var cross=CyclicAnalysis.analyse(words,mismatched); assertEquals(List.of("AAAAAA"),cross.mismatches()); assertEquals(129,cross.representativesMatched());
        List<String> atoms=java.util.stream.IntStream.range(0,6).mapToObj(k -> "place("+k+",\"A\")\n").toList(); assertEquals("AAAAAA",CyclicPatternPlugin.parseAtoms("words",atoms).word());
        for (String bad : List.of("place(1,\"A\")\n","place(0,\"D\")\n","place(0,\"A\")","place(0,\"A\")\nplace(0,\"A\")\n")) { var edited=new ArrayList<>(atoms); edited.set(0,bad); assertThrows(IllegalArgumentException.class,() -> CyclicPatternPlugin.parseAtoms("words",edited)); }
        assertThrows(IllegalArgumentException.class,() -> CyclicPatternPlugin.parseAtoms("words",atoms.subList(0,5)));
        for (String bad : List.of("pattern(\"BAAAAA\")\n","pattern(\"ABC\")\n","pattern(\"AAAAAA\")")) assertThrows(IllegalArgumentException.class,() -> CyclicPatternPlugin.parseAtoms("classes",List.of(bad)));
        assertThrows(IllegalArgumentException.class,() -> CyclicPatterns.evaluate("classes","BAAAAA")); assertThrows(IllegalArgumentException.class,() -> CyclicPatterns.evaluate("words","AAAAAD"));
        var score=CyclicPatterns.sut("ABCABC"); var c=score.core(); var badCore=new CyclicPatterns.Core(c.counts(),c.edgeCounts(),c.transitionCost(),c.balancePenalty(),99,c.representative(),c.orbitSize(),c.period(),c.stabilizerSize()); assertEquals(List.of("total_cost"),CyclicPatterns.judge("words","ABCABC",new CyclicPatterns.Score(badCore,score.edgeCosts())).mismatched());
    }
}
