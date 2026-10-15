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

import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrencySchedulesTest {
    private Element golden() throws Exception { var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d6-golden.xml")) { assertNotNull(in); return f.newDocumentBuilder().parse(in).getDocumentElement(); } }
    private Map<String,String> fields(Element e) { var m=new TreeMap<String,String>(); var fs=e.getElementsByTagName("field"); for (int i=0;i<fs.getLength();i++) { var f=(Element)fs.item(i); m.put(f.getAttribute("name"),f.getTextContent()); } return m; }
    private ConcurrencySchedules.Config counter(String policy,int cap,String w) { return new ConcurrencySchedules.Config("counter",policy,w,(byte)cap); }
    private ConcurrencySchedules.Config queue(String policy,String w) { return new ConcurrencySchedules.Config("queue",policy,w,null); }
    @Test void allThirtyOriginalObservationsMatchEveryFrozenCheckpointLocalReferenceAndVerdict() throws Exception {
        var cases=golden().getElementsByTagName("case"); assertEquals(30,cases.getLength()); int pass=0,fail=0;
        for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); var f=fields(c); var r=ConcurrencySchedules.evaluate(ConcurrencyScheduleAnalysis.configuration(new ConcurrencyScheduleAnalysis.Observed(f))); var actual=ConcurrencySchedules.attributes(r); assertEquals(f.keySet(),actual.keySet()); var fs=c.getElementsByTagName("field"); for (int k=0;k<fs.getLength();k++) { var e=(Element)fs.item(k); String key=e.getAttribute("name"); assertEquals(e.getAttribute("kind").equals("json")?Json.parse(e.getTextContent(),"golden"):e.getTextContent(),e.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key); } if (r.passed()) pass++; else fail++; }
        assertEquals(23,pass); assertEquals(7,fail);
    }
    @SuppressWarnings("unchecked") @Test void prefixCompletionEnablednessDefectsAndMalformedSetupPreserveOriginalContracts() throws Exception {
        var exploration=(Map<String,Object>)Json.parse(golden().getElementsByTagName("exploration").item(0).getTextContent(),"golden"); var words=(Map<String,Object>)((Map<String,Object>)exploration.get("counter")).get("words");
        for (String w : ConcurrencyScheduleAnalysis.words("counter")) { var expected=(Map<String,Object>)words.get(w); assertEquals(expected.get("prefix_preemptions"),Json.parse(ConcurrencySchedules.json(ConcurrencySchedulesOracle.preemptions(w)),"actual")); assertEquals(expected.get("context_switches"),Json.parse(ConcurrencySchedules.json(ConcurrencySchedulesOracle.contextSwitches(w)),"actual")); }
        assertEquals(0,ConcurrencySchedulesOracle.preemptions("AABB")); assertEquals(1,ConcurrencySchedulesOracle.contextSwitches("AABB")); assertEquals(2,ConcurrencySchedulesOracle.preemptions("ABAB")); assertEquals(3,ConcurrencySchedulesOracle.contextSwitches("ABAB")); assertEquals(List.of("A1","B1","A2","B2"),ConcurrencySchedulesOracle.localSteps("ABAB"));
        assertEquals(List.of((byte)4),ConcurrencySchedules.evaluate(counter("split_rw",2,"ABAB")).failingCheckpoints()); assertTrue(ConcurrencySchedules.evaluate(counter("atomic_commit",2,"ABAB")).passed()); assertTrue(ConcurrencySchedules.evaluate(counter("split_rw",0,"AABB")).passed());
        var stale=ConcurrencySchedules.evaluate(queue("stale_empty","PCCP")); assertEquals(List.of((byte)3,(byte)4),stale.failingCheckpoints()); var pop=(ConcurrencySchedules.QueueStep)stale.observed().get(2); assertEquals(List.of("item"),pop.items()); assertFalse(pop.publishedNonempty()); assertEquals("EMPTY",pop.popResult()); assertTrue(((ConcurrencySchedules.QueueStep)stale.observed().get(3)).publishedNonempty()); assertTrue(ConcurrencySchedules.evaluate(queue("actual_queue","PCCP")).passed());
        for (String w : List.of("CPPC","CPCP","CCPP")) { var ref=ConcurrencySchedulesOracle.queueReference(w); assertEquals((byte)1,ref.firstDisabledStep()); assertTrue(ref.trace().isEmpty()); assertThrows(IllegalArgumentException.class,() -> ConcurrencySchedules.evaluate(queue("stale_empty",w))); }
        assertEquals(counter("atomic_commit",0,"AABB"),ConcurrencySchedulesPlugin.parseAtoms("counter",List.of("impl(\"atomic_commit\");","cap(0);","set_step(1,\"A\");","set_step(2,\"A\");","set_step(3,\"B\");","set_step(4,\"B\");")));
        for (var atoms : List.of(List.of("impl(\"split_rw\");","set_step(2,\"A\");"),List.of("impl(\"split_rw\");","cap(0);","cap(1);"),List.of("impl(\"split_rw\");"),List.of("set_step(1,\"A\");"),List.of("impl(\"split_rw\");","set_step(1,\"A\");","cap(0);"))) assertThrows(IllegalArgumentException.class,() -> ConcurrencySchedulesPlugin.parseAtoms("counter",atoms));
        assertThrows(IllegalArgumentException.class,() -> counter("unknown",0,"AABB")); assertThrows(IllegalArgumentException.class,() -> counter("split_rw",0,"ABAB")); assertThrows(IllegalArgumentException.class,() -> counter("split_rw",2,"AAAB")); assertThrows(IllegalArgumentException.class,() -> new ConcurrencySchedules.Config("counter","split_rw","AABB",null)); assertThrows(IllegalArgumentException.class,() -> new ConcurrencySchedules.Config("queue","stale_empty","PCCP",(byte)0));
        var safe=counter("atomic_commit",2,"ABAB"); var changed=new ArrayList<>(ConcurrencySchedules.run(safe)); changed.set(3,new ConcurrencySchedules.CounterStep("B","write",(byte)1,Map.of("A",(byte)0,"B",(byte)0))); assertFalse(ConcurrencySchedules.judge(safe,changed).passed(),"oracle must use measured value, not policy label");
    }
    @SuppressWarnings("unchecked") @Test void fullObservedSummariesAndWitnessesMatchOriginalAndRejectIncompleteSupportWithoutRepair() throws Exception {
        var g=golden(); var expected=(Map<String,Object>)Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"); var cases=g.getElementsByTagName("case");
        for (String campaign : List.of("counter","queue")) {
            var rows=new ArrayList<ConcurrencyScheduleAnalysis.Observed>(); for (int i=0;i<cases.getLength();i++) { var f=fields((Element)cases.item(i)); if (f.get("campaign").equals(campaign)) rows.add(new ConcurrencyScheduleAnalysis.Observed(f)); }
            var original=ConcurrencyScheduleAnalysis.analyse(campaign,rows); assertEquals(expected.get(campaign),Json.parse(ConcurrencySchedules.json(original),"actual")); assertEquals(campaign.equals("counter")?24:6,ConcurrencyScheduleAnalysis.expectedIds(campaign).size());
            var missing=new ArrayList<>(rows); missing.removeLast(); assertThrows(IllegalArgumentException.class,() -> ConcurrencyScheduleAnalysis.analyse(campaign,missing)); var duplicate=new ArrayList<>(rows); duplicate.set(1,rows.getFirst()); assertThrows(IllegalArgumentException.class,() -> ConcurrencyScheduleAnalysis.analyse(campaign,duplicate));
            for (var edit : List.of(Map.entry("case_id","unknown"),Map.entry("policy","unknown"),Map.entry("verdict","BROKEN"),Map.entry("schedule","XXXX"))) { var changed=new ArrayList<>(rows); var f=new TreeMap<>(rows.getFirst().fields()); f.put(edit.getKey(),edit.getValue()); changed.set(0,new ConcurrencyScheduleAnalysis.Observed(f)); assertThrows(IllegalArgumentException.class,() -> ConcurrencyScheduleAnalysis.analyse(campaign,changed)); }
            String id=ConcurrencyScheduleAnalysis.witnesses(campaign).values().iterator().next(); int index=java.util.stream.IntStream.range(0,rows.size()).filter(i -> rows.get(i).id().equals(id)).findFirst().orElseThrow(); var altered=new ArrayList<>(rows); var f=new TreeMap<>(rows.get(index).fields()); f.put("verdict","PASS"); var trace=(List<Object>)Json.parse(f.get("observed_trace"),"observed"); ((Map<String,Object>)trace.getFirst()).put("measured_local","changed"); f.put("observed_trace",ConcurrencySchedules.json(trace)); altered.set(index,new ConcurrencyScheduleAnalysis.Observed(f)); var changed=ConcurrencyScheduleAnalysis.analyse(campaign,altered); assertNotEquals(original.get("totals"),changed.get("totals")); assertNotEquals(original.get("witnesses"),changed.get("witnesses"));
        }
    }
}
