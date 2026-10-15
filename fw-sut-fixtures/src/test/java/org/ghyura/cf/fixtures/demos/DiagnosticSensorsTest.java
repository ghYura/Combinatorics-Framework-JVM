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

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticSensorsTest {
    private Element golden() throws Exception {
        var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d10-golden.xml")) { assertNotNull(in); return f.newDocumentBuilder().parse(in).getDocumentElement(); }
    }
    private Map<String,String> fields(Element c) {
        var out=new TreeMap<String,String>(); var nodes=c.getElementsByTagName("field"); for (int i=0;i<nodes.getLength();i++) { var n=(Element)nodes.item(i); out.put(n.getAttribute("name"),n.getTextContent()); } return out;
    }
    private String join(List<?> values) { return values.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" ")); }
    private List<SensorDiagnosis.Observed> observations() throws Exception {
        var out=new ArrayList<SensorDiagnosis.Observed>(); var cases=golden().getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var f=fields((Element)cases.item(i)); out.add(new SensorDiagnosis.Observed(f.get("case_id"),f.get("mask"),f.get("fault"),f.get("verdict"),SensorDiagnosis.parseReadings(f.get("readings")))); } return out;
    }
    @Test void all3072ProbeReadingsLatentBitsAndLiteralReferencesMatchIndependentPredictions() throws Exception {
        var cases=golden().getElementsByTagName("case"); assertEquals(3072,cases.getLength());
        for (int i=0;i<cases.getLength();i++) { var f=fields((Element)cases.item(i)); var r=DiagnosticSensors.evaluate(f.get("mask"),f.get("fault")); assertTrue(r.passed()); assertEquals(f,DiagnosticSensors.attributes(r),r.caseId()); }
        var empty=DiagnosticSensors.evaluate("00000000","F01"); assertEquals(List.of(),empty.observed().readings()); assertEquals(List.of((byte)1,(byte)0,(byte)0,(byte)0),empty.observed().latentBits()); assertEquals("",DiagnosticSensors.attributes(empty).get("signature"));
        var all=DiagnosticSensors.selected("11111111"); assertEquals(java.util.Collections.nCopies(8,(byte)0),DiagnosticSensors.observe("H0",all).readings()); assertEquals(DiagnosticSensors.reference("H0",all),DiagnosticSensors.observe("H0",all).readings());
        var correct=DiagnosticSensors.observe("F01",all); assertTrue(DiagnosticSensors.judge("11111111","F01",new DiagnosticSensors.Reading(List.of((byte)0,(byte)0,(byte)0,(byte)0),correct.readings())).passed(),"latent bits are diagnostic; original verdict compares readings only");
        var wrong=new ArrayList<>(correct.readings()); wrong.set(0,(byte)0); assertFalse(DiagnosticSensors.judge("11111111","F01",new DiagnosticSensors.Reading(correct.latentBits(),wrong)).passed());
    }
    private void tree(Element e,SensorDiagnosis.Node n) {
        assertEquals(e.getAttribute("cost"),Integer.toString(n.cost()));
        if (n.sensor()==null) { assertEquals(e.getAttribute("class"),Integer.toString(n.diagnosis())); assertFalse(e.hasAttribute("sensor")); }
        else { assertEquals(e.getAttribute("sensor"),Integer.toString(n.sensor())); var branches=e.getChildNodes(); int count=0; for (int i=0;i<branches.getLength();i++) if (branches.item(i) instanceof Element b) { assertEquals("branch",b.getTagName()); tree((Element)b.getElementsByTagName("node").item(0),b.getAttribute("reading").equals("0")?n.zero():n.one()); count++; } assertEquals(2,count); }
    }
    @Test void everyClassSuiteDistanceMinimumCostTieAdaptiveBeliefPathAndNoiseControlMatchesPredictions() throws Exception {
        var g=golden(); var r=SensorDiagnosis.analyse(observations()); var classes=g.getElementsByTagName("class"); assertEquals(7,r.classes().size());
        for (int i=0;i<7;i++) { var e=(Element)classes.item(i); var c=r.classes().get(i); assertEquals(e.getAttribute("index"),Integer.toString(c.index())); assertEquals(e.getAttribute("code"),Integer.toString(c.code())); assertEquals(e.getAttribute("signature"),join(c.signature())); assertEquals(e.getAttribute("members"),join(c.members())); assertEquals(i==0?SensorDiagnosis.HEALTHY_SOURCE:"inferred from observed full-mask signatures",c.source()); }
        var suites=g.getElementsByTagName("suite"); assertEquals(256,r.suites().size());
        for (int i=0;i<256;i++) {
            var e=(Element)suites.item(i); var s=r.suites().get(i); assertEquals(e.getAttribute("mask"),s.mask()); assertEquals(e.getAttribute("cost"),Integer.toString(s.cost())); assertEquals(e.getAttribute("min_distance"),Integer.toString(s.minDistance()));
            for (var goal : List.of("detect","separate","erasure","error")) assertEquals(e.getAttribute(goal),Boolean.toString(s.meets(goal)),s.mask()+goal);
            var words=e.getElementsByTagName("signature"); assertEquals(7,s.signatures().size()); for (int k=0;k<7;k++) assertEquals(words.item(k).getTextContent(),s.signatures().get(k));
            var ds=e.getElementsByTagName("distance"); assertEquals(21,s.distances().size()); for (int k=0;k<21;k++) { var d=(Element)ds.item(k); var a=s.distances().get(k); assertEquals(d.getAttribute("classes"),a.first()+" "+a.second()); assertEquals(d.getAttribute("value"),Integer.toString(a.value())); }
        }
        var winners=g.getElementsByTagName("winner"); for (int i=0;i<winners.getLength();i++) { var e=(Element)winners.item(i); var w=r.winners().get(e.getAttribute("goal")); assertEquals(e.getAttribute("mask"),w.mask()); assertEquals(e.getAttribute("cost"),Integer.toString(w.cost())); assertEquals(e.getAttribute("feasible_count"),Integer.toString(w.feasibleCount())); assertEquals(e.getAttribute("minimum_cost_masks"),join(w.minimumCostMasks())); }
        var beliefs=g.getElementsByTagName("belief"); assertEquals(127,r.adaptive().dp().size()); for (int i=0;i<127;i++) { var e=(Element)beliefs.item(i); var b=r.adaptive().dp().get(i); assertEquals(e.getAttribute("classes"),join(b.classes())); assertEquals(e.getAttribute("cost"),Integer.toString(b.cost())); assertEquals(e.getAttribute("sensor"),b.sensor()==null?"none":b.sensor().toString()); }
        tree((Element)((Element)g.getElementsByTagName("adaptive").item(0)).getElementsByTagName("node").item(0),r.adaptive().tree()); var paths=g.getElementsByTagName("path"); assertEquals(7,r.adaptive().paths().size());
        for (int i=0;i<7;i++) { var e=(Element)paths.item(i); var p=r.adaptive().paths().get(i); assertEquals(e.getAttribute("class"),Integer.toString(p.diagnosis())); assertEquals(e.getAttribute("cost"),Integer.toString(p.cost())); var steps=e.getElementsByTagName("step"); assertEquals(steps.getLength(),p.steps().size()); for (int k=0;k<steps.getLength();k++) { var s=(Element)steps.item(k); assertEquals(s.getAttribute("sensor"),Integer.toString(p.steps().get(k).sensor())); assertEquals(s.getAttribute("reading"),Byte.toString(p.steps().get(k).reading())); } }
        for (String goal : List.of("erasure","error")) { var controls=((Element)g.getElementsByTagName(goal).item(0)).getElementsByTagName("control"); var rows=r.noise().get(goal); assertEquals(controls.getLength(),rows.size()); for (int i=0;i<rows.size();i++) { var e=(Element)controls.item(i); var n=rows.get(i); assertEquals(e.getAttribute("class"),Integer.toString(n.diagnosis())); assertEquals(e.getAttribute("position"),Integer.toString(n.position())); assertEquals(e.getAttribute("observed"),n.observed()); assertEquals(e.getAttribute("decoded"),join(n.decoded())); } }
        assertEquals(49,r.noise().get("erasure").size()); assertEquals(56,r.noise().get("error").size()); assertEquals(0,r.unquotientedLabelMinDistance());
        for (int i=0;i<12;i++) { var fault=DiagnosticSensors.LABELS.get(i); var hit=r.identifyExamples().get(fault); assertEquals(i/2+1,hit.diagnosis()); assertEquals(DiagnosticSensors.LABELS.subList(i/2*2,i/2*2+2),hit.members()); assertEquals(hit,SensorDiagnosis.identify(r.classes().get(hit.diagnosis()).signature(),r.classes())); }
        assertNull(SensorDiagnosis.identify(List.of((byte)1,(byte)1,(byte)0,(byte)0,(byte)0,(byte)0,(byte)0,(byte)0),r.classes()));
    }
    @Test void malformedSetupAndBadSupportAreRefusedAndConsistentlyChangedObservedReadingsChangeDiagnosis() throws Exception {
        assertEquals(new DiagnosticSensorPlugin.Parsed("00000000","F01"),DiagnosticSensorPlugin.parseAtoms(List.of(),"fault(\"F01\")\n"));
        for (var atoms : List.of(List.of("select(8)\n"),List.of("select(0)"),List.of("select(0)\n","select(0)\n"),List.of("select(1)\n","select(0)\n"))) assertThrows(IllegalArgumentException.class,() -> DiagnosticSensorPlugin.parseAtoms(atoms,"fault(\"F01\")\n"));
        for (var bad : List.of("fault(\"H0\")\n","fault(\"F13\")\n","fault(\"F01\")","fault(\"F01\")\nfault(\"F02\")\n")) assertThrows(IllegalArgumentException.class,() -> DiagnosticSensorPlugin.parseAtoms(List.of(),bad));
        assertThrows(IllegalArgumentException.class,() -> DiagnosticSensors.evaluate("11111111","H0")); assertThrows(IllegalArgumentException.class,() -> DiagnosticSensors.observe("F13",List.of())); assertThrows(IllegalArgumentException.class,() -> DiagnosticSensors.selected("0000000")); assertThrows(IllegalArgumentException.class,() -> DiagnosticSensors.observe("F01",List.of(1,0)));
        var rows=observations(); var missing=new ArrayList<>(rows); missing.removeLast(); assertThrows(IllegalArgumentException.class,() -> SensorDiagnosis.analyse(missing)); var duplicate=new ArrayList<>(rows); duplicate.set(1,rows.getFirst()); assertThrows(IllegalArgumentException.class,() -> SensorDiagnosis.analyse(duplicate));
        var old=rows.get(12); for (var bad : List.of(new SensorDiagnosis.Observed(old.caseId(),old.mask(),"F13","PASS",old.readings()),new SensorDiagnosis.Observed("S=11111111|H=F01",old.mask(),old.fault(),"PASS",old.readings()),new SensorDiagnosis.Observed(old.caseId(),old.mask(),old.fault(),"DOMAIN_FAIL",old.readings()),new SensorDiagnosis.Observed(old.caseId(),old.mask(),old.fault(),"PASS",List.of((byte)2)),new SensorDiagnosis.Observed(old.caseId(),old.mask(),old.fault(),"PASS",List.of()),new SensorDiagnosis.Observed(old.caseId(),old.mask(),old.fault(),"PASS",List.of((byte)(1-old.readings().getFirst()))))) { var edited=new ArrayList<>(rows); edited.set(12,bad); assertThrows(IllegalArgumentException.class,() -> SensorDiagnosis.analyse(edited)); }
        var changed=new ArrayList<SensorDiagnosis.Observed>(); var full=new TreeMap<String,List<Byte>>(); for (var o : rows) if (o.mask().equals("11111111")) full.put(o.fault(),o.readings().reversed());
        for (var o : rows) changed.add(new SensorDiagnosis.Observed(o.caseId(),o.mask(),o.fault(),o.verdict(),DiagnosticSensors.selected(o.mask()).stream().map(full.get(o.fault())::get).toList()));
        var original=SensorDiagnosis.analyse(rows); var altered=SensorDiagnosis.analyse(changed); assertNotEquals(original.winners().get("detect"),altered.winners().get("detect")); assertNotEquals(original.adaptive(),altered.adaptive()); assertEquals(full.get("F01"),altered.classes().get(1).signature()); assertEquals(3072,changed.size());
        var collapsed=rows.stream().map(o -> new SensorDiagnosis.Observed(o.caseId(),o.mask(),o.fault(),o.verdict(),java.util.Collections.nCopies(o.readings().size(),(byte)0))).toList(); assertThrows(IllegalArgumentException.class,() -> SensorDiagnosis.analyse(collapsed));
    }
}
