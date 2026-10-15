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

class ToolCallOrderTest {
    private Element golden() throws Exception {
        var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13a-golden.xml")) { assertNotNull(in); return f.newDocumentBuilder().parse(in).getDocumentElement(); }
    }
    private Map<String,String> fields(Element c) { var out=new TreeMap<String,String>(); var ns=c.getElementsByTagName("field"); for (int i=0;i<ns.getLength();i++) { var e=(Element)ns.item(i); out.put(e.getAttribute("name"),e.getTextContent()); } return out; }
    private List<ToolCallAnalysis.Observed> observations() throws Exception { var out=new ArrayList<ToolCallAnalysis.Observed>(); var cases=golden().getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) out.add(new ToolCallAnalysis.Observed(fields((Element)cases.item(i)))); return out; }
    private ToolCallOrder.Config config(String p,String o,int n,boolean f,Integer x) { return new ToolCallOrder.Config(p,o,(byte)n,f,x==null?null:x.byteValue()); }
    @Test void all480OriginalEventsSnapshotsDecisionsEffectsAndIndependentReferencesMatchFrozenPredictions() throws Exception {
        var cases=golden().getElementsByTagName("case"); assertEquals(480,cases.getLength()); int pass=0,fail=0;
        for (int i=0;i<cases.getLength();i++) {
            var c=(Element)cases.item(i); var f=fields(c); var r=ToolCallOrder.evaluate(ToolCallAnalysis.configuration(new ToolCallAnalysis.Observed(f))); var actual=ToolCallOrder.attributes(r); assertEquals(f.keySet(),actual.keySet()); var fs=c.getElementsByTagName("field");
            for (int k=0;k<fs.getLength();k++) { var e=(Element)fs.item(k); String key=e.getAttribute("name"); assertEquals(e.getAttribute("kind").equals("json")?Json.parse(e.getTextContent(),"golden"):e.getTextContent(),e.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key); }
            if (r.passed()) pass++; else fail++;
        }
        assertEquals(454,pass); assertEquals(26,fail);
    }
    @Test void everyObservedAxisViolationCountRequestEffectAttemptAndFiveWitnessTimelineMatchesPredictions() throws Exception {
        var g=golden(); var analysis=ToolCallAnalysis.analyse(observations()); assertEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(ToolCallOrder.json(analysis),"analysis"));
        assertEquals(480,ToolCallAnalysis.expectedIds().size()); assertEquals(Map.of("framework_attempts",480,"send_requests",720,"outbox_effects",90,"emit_responses",90),analysis.get("counts"));
        assertEquals(5,((Map<?,?>)analysis.get("timelines")).size()); assertEquals(Map.of("guarded",Map.of(),"sticky_approval",Map.of("unapproved_emit",4),"success_only_taint",Map.of("tainted_emit",14),"no_dedup",Map.of("duplicate_emit",8)),analysis.get("violation_kinds"));
    }
    @SuppressWarnings("unchecked") @Test void partialReadRevocationDuplicatesDenyAllPrefixRulesSetupAndObservedSupportAreHandledWithoutFallback() throws Exception {
        var chunks=new ArrayList<String>(); assertThrows(ToolCallOrder.ControlledReadError.class,() -> ToolCallOrder.readTool(chunks::add,true)); assertEquals(List.of(ToolCallOrder.CANARY),chunks);
        var partial=ToolCallOrder.evaluate(config("guarded","ARS",1,true,null)); assertTrue(partial.passed()); assertEquals(List.of("approve","read_start","read_chunk","read_error"),partial.observed().trace().subList(0,4).stream().map(ToolCallOrder.Event::name).toList()); assertEquals("tainted",partial.observed().sends().getFirst().reason());
        var missed=ToolCallOrder.evaluate(config("success_only_taint","ARS",1,true,null)); assertFalse(missed.passed()); assertEquals(ToolCallOrder.CANARY,missed.observed().outbox().getFirst().payload()); assertEquals(List.of(new ToolCallOrderOracle.Violation((byte)1,"tainted_emit")),missed.violations());
        assertTrue(ToolCallOrder.evaluate(config("guarded","ASR",1,false,0)).passed()); assertEquals("emit",ToolCallOrder.run(config("guarded","ASR",1,false,0)).sends().getFirst().action()); assertEquals("block",ToolCallOrder.run(config("guarded","ASR",1,false,1)).sends().getFirst().action());
        var sticky=ToolCallOrder.evaluate(config("sticky_approval","ASR",1,false,1)); assertEquals(List.of(new ToolCallOrderOracle.Violation((byte)1,"unapproved_emit")),sticky.violations());
        var repeated=ToolCallOrder.evaluate(config("no_dedup","ASR",2,false,null)); assertEquals(2,repeated.observed().outbox().size()); assertEquals(List.of(new ToolCallOrderOracle.Violation((byte)2,"duplicate_emit")),repeated.violations());
        var late=config("guarded","ASR",2,true,3); var safe=ToolCallOrder.evaluate(late); assertTrue(safe.passed()); assertEquals(List.of("emit","dedup"),safe.observed().sends().stream().map(ToolCallOrder.Send::action).toList()); assertEquals(new ToolCallOrder.State(false,true,true,ToolCallOrder.CANARY),safe.observed().finalState()); assertTrue(safe.violations().isEmpty());
        var deceptive=sticky.observed().trace().stream().map(e -> new ToolCallOrder.Event(e.name(),e.cut(),e.request(),e.messageId(),e.action(),e.reason(),e.payload(),new ToolCallOrder.State(true,false,false,"PUBLIC"),e.outboxSize())).toList(); assertEquals(sticky.violations(),ToolCallOrderOracle.violations(deceptive),"oracle ignores controller snapshots");
        var eligible=config("guarded","ASR",1,false,null); var actual=ToolCallOrder.run(eligible); var denied=new ToolCallOrder.Execution(actual.trace(),List.of(new ToolCallOrder.Send((byte)1,"block","no_approval",null)),List.of(),actual.finalState()); var denial=ToolCallOrder.judge(eligible,denied); assertFalse(denial.passed()); assertFalse(denial.decisionsOk()); assertFalse(denial.outboxOk());
        var atoms=List.of("impl(\"guarded\")\n","repetitions(1)\n","read_mode(0)\n","plan(\"A\")\n","plan(\"R\")\n","plan(\"S\")\n"); assertEquals(config("guarded","ARS",1,false,null),ToolCallOrderPlugin.parseAtoms(atoms)); var afterPlan=new ArrayList<>(atoms); afterPlan.add("set_revoke(0)\n"); assertEquals(config("guarded","ARS",1,false,0),ToolCallOrderPlugin.parseAtoms(afterPlan));
        for (String bad : List.of("repetitions(3)\n","read_mode(2)\n","set_revoke(4)\n","plan(\"Z\")\n","plan(\"A\")\n","impl(\"guarded\")\n","read_mode(0)\n","repetitions(1)\n","set_revoke(0)")) { var edited=new ArrayList<>(atoms); edited.add(bad); assertThrows(IllegalArgumentException.class,() -> ToolCallOrderPlugin.parseAtoms(edited)); }
        var cutTwice=new ArrayList<>(afterPlan); cutTwice.add("set_revoke(1)\n"); assertThrows(IllegalArgumentException.class,() -> ToolCallOrderPlugin.parseAtoms(cutTwice)); assertThrows(IllegalArgumentException.class,() -> ToolCallOrderPlugin.parseAtoms(atoms.subList(1,atoms.size()))); assertThrows(IllegalArgumentException.class,() -> ToolCallOrderPlugin.parseAtoms(atoms.subList(0,5))); assertThrows(IllegalArgumentException.class,() -> config("unknown","ARS",1,false,null));
        var rows=observations(); var missing=new ArrayList<>(rows); missing.removeLast(); assertThrows(IllegalArgumentException.class,() -> ToolCallAnalysis.analyse(missing)); var duplicate=new ArrayList<>(rows); duplicate.set(1,rows.getFirst()); assertThrows(IllegalArgumentException.class,() -> ToolCallAnalysis.analyse(duplicate));
        for (var change : List.of(Map.entry("case_id","unknown"),Map.entry("policy","no_dedup"),Map.entry("verdict","BROKEN"),Map.entry("fail_read","yes"),Map.entry("send_repetitions","3"))) { var edited=new ArrayList<>(rows); var f=new TreeMap<>(rows.getFirst().fields()); f.put(change.getKey(),change.getValue()); edited.set(0,new ToolCallAnalysis.Observed(f)); assertThrows(IllegalArgumentException.class,() -> ToolCallAnalysis.analyse(edited)); }
        var changed=new ArrayList<>(rows); String witness=ToolCallAnalysis.WITNESSES.getFirst(); int index=java.util.stream.IntStream.range(0,rows.size()).filter(i -> rows.get(i).caseId().equals(witness)).findFirst().orElseThrow(); var fields=new TreeMap<>(rows.get(index).fields()); var trace=(List<Object>)Json.parse(fields.get("trace"),"test"); var event=(Map<String,Object>)trace.getFirst(); var local=(Map<String,Object>)event.get("state"); local.put("approved",false); fields.put("trace",ToolCallOrder.json(trace)); var box=(List<Object>)Json.parse(fields.get("outbox"),"test"); box.add(Map.of("message_id","observed-extra","payload","PUBLIC")); fields.put("outbox",ToolCallOrder.json(box)); changed.set(index,new ToolCallAnalysis.Observed(fields)); var altered=ToolCallAnalysis.analyse(changed); assertEquals(91,((Map<?,?>)altered.get("counts")).get("outbox_effects")); assertNotEquals(ToolCallAnalysis.analyse(rows).get("timelines"),altered.get("timelines")); var first=(Map<?,?>)((List<?>)((Map<?,?>)altered.get("timelines")).get(witness)).getFirst(); assertEquals(false,((Map<?,?>)first.get("local_state")).get("approved")); assertEquals(true,((Map<?,?>)first.get("prefix_evidence")).get("approved"));
    }
}
