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

class SessionMemoryTest {
    private Element golden() throws Exception { var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13d-golden.xml")) { assertNotNull(in); return f.newDocumentBuilder().parse(in).getDocumentElement(); } }
    private Map<String,String> fields(Element e) { var m=new TreeMap<String,String>(); var fs=e.getElementsByTagName("field"); for (int i=0;i<fs.getLength();i++) { var f=(Element)fs.item(i); m.put(f.getAttribute("name"),f.getTextContent()); } return m; }

    private SessionMemory.Config config(String p,String u,String s,String x,int w) { return new SessionMemory.Config(p,u,s,x,(byte)w); }
    private SessionMemory.Event replace(SessionMemory.Event e,String reply,List<SessionMemory.ToolCall> calls) { return new SessionMemory.Event(e.requestIndex(),(byte)9,(byte)9,e.operation(),e.resetBefore(),(byte)9,List.of((byte)9),reply,calls,List.of()); }
    @Test void allEightHundredOriginalObservationsMatchEveryFrozenTraceOwnerCheckLeakAndVerdict() throws Exception {
        var cases=golden().getElementsByTagName("case"); assertEquals(800,cases.getLength()); int pass=0,fail=0;
        for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); var f=fields(c); var result=SessionMemory.evaluate(SessionMemoryAnalysis.configuration(new SessionMemoryAnalysis.Observed(f))); var actual=SessionMemory.attributes(result); assertEquals(f.keySet(),actual.keySet()); var fs=c.getElementsByTagName("field"); for (int k=0;k<fs.getLength();k++) { var e=(Element)fs.item(k); String key=e.getAttribute("name"); assertEquals(e.getAttribute("kind").equals("json")?Json.parse(e.getTextContent(),"golden"):e.getTextContent(),e.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key); } if (result.passed()) pass++; else fail++; }
        assertEquals(718,pass); assertEquals(82,fail);
    }
    @Test void inputOwnedIsolationToolChannelsResetTimingUsefulRetentionAndStrictAtomsPreserveOriginalContracts() {
        var before=SessionMemory.evaluate(config("scoped","000","000","10",2)); assertTrue(before.passed()); assertEquals("D13D_CANARY_R2",before.observed().get(2).reply()); assertEquals((byte)1,before.judgment().owner().epoch());
        var after=SessionMemory.evaluate(config("scoped","000","000","01",1)); assertTrue(after.passed()); assertNull(after.observed().get(2).reply());
        var expired=SessionMemory.evaluate(config("ignores_reset","000","000","10",1)); assertFalse(expired.passed()); assertEquals(List.of((byte)2,(byte)3),expired.judgment().failingCheckpoints()); assertEquals((byte)1,expired.observed().get(1).epoch()); assertEquals((byte)0,expired.observed().get(1).memory().getFirst().writeEpoch());
        var cross=config("session_only","010","000","00",1); var trace=new ArrayList<>(SessionMemory.run(cross)); var e=trace.get(1); trace.set(1,replace(e,null,List.of(new SessionMemory.ToolCall("draft","wrapped D13D_CANARY_R1")))); var judge=SessionMemory.judge(cross,trace); assertFalse(judge.passed()); assertEquals("cross_user",judge.judgment().leaks().getFirst().kind()); assertEquals("tool_argument",judge.judgment().occurrences().getFirst().channel()); assertEquals((byte)0,judge.judgment().owner().user(),"spoofed observed provenance cannot own the secret");
        var safe=config("scoped","000","000","00",1); var erased=new ArrayList<>(SessionMemory.run(safe)); for (int i=1;i<3;i++) erased.set(i,replace(erased.get(i),null,List.of())); var erasedResult=SessionMemory.judge(safe,erased); assertFalse(erasedResult.passed()); assertTrue(erasedResult.judgment().leaks().isEmpty(),"erase-all still fails response/tool retention checks");
        var early=config("scoped","000","000","00",2); var injected=new ArrayList<>(SessionMemory.run(early)); injected.set(0,replace(injected.getFirst(),"D13D_CANARY_R2",List.of())); assertFalse(SessionMemory.judge(early,injected).judgment().checks().getFirst().isolationOk());
        var wrong=new ArrayList<>(SessionMemory.run(safe)); wrong.set(1,replace(wrong.get(1),"D13D_CANARY_R2",List.of())); assertEquals("expired",SessionMemory.judge(safe,wrong).judgment().leaks().getFirst().kind());
        var priority=config("session_only","010","011","10",1); var priorityTrace=new ArrayList<>(SessionMemory.run(priority)); priorityTrace.set(1,replace(priorityTrace.get(1),"D13D_CANARY_R1",List.of())); assertEquals("cross_user",SessionMemory.judge(priority,priorityTrace).judgment().leaks().getFirst().kind());
        var adapter=new SessionMemory.MemoryAdapter("user_only"); adapter.write((byte)0,(byte)0,(byte)7,(byte)1,"value"); assertEquals("value",adapter.read((byte)0,(byte)2),"read uses key, not stored session/epoch"); adapter.reset(); assertNull(adapter.read((byte)0,(byte)0)); assertEquals(before,SessionMemory.evaluate(before.config()),"fresh case state");
        var atoms=List.of("impl(\"scoped\");","write_at(2);","user_at(1,0);","user_at(2,0);","user_at(3,0);","session_at(1,0);","session_at(2,0);","session_at(3,0);","reset_cut(1,1);","reset_cut(2,0);"); assertEquals(before.config(),SessionMemoryPlugin.parseAtoms(atoms));
        for (var edit : List.of(Map.entry(0,"impl(\"unknown\");"),Map.entry(1,"write_at(3);"),Map.entry(2,"user_at(1,1);"),Map.entry(4,"user_at(3,2);"),Map.entry(5,"session_at(2,0);"),Map.entry(9,"reset_cut(2,2);"),Map.entry(0,"impl(\"scoped\");\n"))) { var bad=new ArrayList<>(atoms); bad.set(edit.getKey(),edit.getValue()); assertThrows(IllegalArgumentException.class,() -> SessionMemoryPlugin.parseAtoms(bad)); }
        assertThrows(IllegalArgumentException.class,() -> SessionMemoryPlugin.parseAtoms(atoms.subList(0,9))); assertThrows(IllegalArgumentException.class,() -> config("scoped","002","000","00",1)); assertThrows(IllegalArgumentException.class,() -> SessionMemory.judge(safe,List.of()));
    }
    @SuppressWarnings("unchecked") @Test void fullObservedAnalysisMatchesOriginalAndRejectsIncompleteSupportWithoutRepair() throws Exception {
        var g=golden(); var cases=g.getElementsByTagName("case"); var rows=new ArrayList<SessionMemoryAnalysis.Observed>(); for (int i=0;i<cases.getLength();i++) rows.add(new SessionMemoryAnalysis.Observed(fields((Element)cases.item(i))));
        var original=SessionMemoryAnalysis.analyse(rows); assertEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(SessionMemory.json(original),"actual")); assertEquals(800,SessionMemoryAnalysis.expectedIds().size());
        var missing=new ArrayList<>(rows); missing.removeLast(); assertThrows(IllegalArgumentException.class,() -> SessionMemoryAnalysis.analyse(missing)); var duplicate=new ArrayList<>(rows); duplicate.set(1,rows.getFirst()); assertThrows(IllegalArgumentException.class,() -> SessionMemoryAnalysis.analyse(duplicate));
        for (var edit : List.of(Map.entry("case_id","unknown"),Map.entry("policy","unknown"),Map.entry("verdict","BROKEN"),Map.entry("users","002"),Map.entry("cuts","01"),Map.entry("write_at","3"))) { var bad=new ArrayList<>(rows); var f=new TreeMap<>(rows.getFirst().fields()); f.put(edit.getKey(),edit.getValue()); bad.set(0,new SessionMemoryAnalysis.Observed(f)); assertThrows(IllegalArgumentException.class,() -> SessionMemoryAnalysis.analyse(bad)); }
        String id=SessionMemoryAnalysis.witnesses().values().iterator().next(); int index=java.util.stream.IntStream.range(0,rows.size()).filter(i -> rows.get(i).id().equals(id)).findFirst().orElseThrow(); var altered=new ArrayList<>(rows); var f=new TreeMap<>(rows.get(index).fields()); f.put("verdict","PASS"); f.put("leaks","[]"); f.put("leak_occurrences","[]"); var trace=(List<Map<String,Object>>)Json.parse(f.get("observed_trace"),"recorded"); trace.getFirst().put("measured_memory","changed"); f.put("observed_trace",SessionMemory.json(trace)); altered.set(index,new SessionMemoryAnalysis.Observed(f)); var changed=SessionMemoryAnalysis.analyse(altered); for (String key : List.of("totals","leak_checkpoints","occurrences","witnesses")) assertNotEquals(original.get(key),changed.get(key),key+" must use recorded values");
    }
}
