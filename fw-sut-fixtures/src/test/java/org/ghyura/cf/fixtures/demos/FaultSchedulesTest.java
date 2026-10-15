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

class FaultSchedulesTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d15-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private List<FaultSchedulesAnalysis.Observed> observations(){return FaultSchedulesAnalysis.configurations(false).stream().map(c -> new FaultSchedulesAnalysis.Observed(FaultSchedules.attributes(FaultSchedules.evaluate(c)))).toList();}
    private FaultSchedules.Result run(String policy,List<Byte> failed,int cut,String kind,String retry){return FaultSchedules.evaluate(new FaultSchedules.Config(policy,failed,(byte)cut,kind,retry));}
    @Test void allThreeHundredTwelveCasesEveryAttemptCheckpointJournalPayloadAndSevenChecksMatchIndependentPredictions()throws Exception{
        var bank=new TreeMap<String,Map<String,String>>();for(var row:observations())assertNull(bank.put(row.id(),row.fields()));assertEquals(312,bank.size());var cases=golden().getElementsByTagName("case");assertEquals(312,cases.getLength());for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=bank.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(20,fields.getLength());assertEquals(20,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}}assertEquals(216,bank.values().stream().filter(f -> f.get("verdict").equals("PASS")).count());
    }
    @Test void durableJournalBeforeAckVolatilePendingLossFullDoubleReplayAndKeySortedMergePreserveOriginalControls()throws Exception{
        var rec=FaultSchedules.record("o1:a1");var durableLog=new FaultSchedules.Journal();var durable=new FaultSchedules.Replica("durable",durableLog);assertFalse(durable.put(rec));assertEquals(List.of(rec),durableLog.records());assertTrue(durable.put(rec));assertEquals(List.of(rec),durable.effects());
        var volatileLog=new FaultSchedules.Journal();var volatileReplica=new FaultSchedules.Replica("volatile_ack",volatileLog);volatileReplica.put(rec);assertTrue(volatileLog.records().isEmpty());assertEquals(List.of("o1:a1"),volatileReplica.pending());assertTrue(new FaultSchedules.Replica("volatile_ack",volatileLog).effects().isEmpty());assertEquals(1,volatileReplica.flush());assertEquals(List.of(rec),volatileLog.records());assertEquals(List.of(rec),volatileReplica.effects());assertEquals(0,volatileReplica.flush());
        var three=List.of(rec,FaultSchedules.record("o2:a1"),FaultSchedules.record("o3:a1"));var replay=new FaultSchedules.Replica("replay_twice",new FaultSchedules.Journal(three));assertEquals(List.of("o1","o2","o3","o1","o2","o3"),replay.effects().stream().map(FaultSchedules.Record::op).toList());assertEquals(3,replay.accepted().size());assertEquals(0,replay.merge(three));assertEquals(6,replay.effects().size());assertThrows(IllegalArgumentException.class,() -> replay.merge(List.of(three.get(1),three.get(0))));assertThrows(IllegalArgumentException.class,() -> replay.merge(List.of(rec,rec)));assertThrows(IllegalArgumentException.class,() -> new FaultSchedules.Record("o1:a1","o1",2));
        try(var c=new FaultSchedules.Cluster("durable")){c.attempt("o1","o1:a1","prefix");var before=c.snapshot("prefix");c.crash((byte)0);assertThrows(IllegalArgumentException.class,() -> c.crash((byte)0));c.start((byte)0);assertThrows(IllegalArgumentException.class,() -> c.start((byte)0));var after=c.snapshot("reopened");assertNotEquals(before,after);assertEquals(0,FaultSchedulesOracle.integer(FaultSchedulesOracle.object(FaultSchedulesOracle.list(before.get("nodes")).getFirst()).get("generation")));assertThrows(UnsupportedOperationException.class,() -> FaultSchedulesOracle.list(before.get("nodes")).clear());}
        assertTrue(run("durable",List.of((byte)0,(byte)1),1,"crash","stable").passed());assertFalse(run("durable",List.of((byte)0,(byte)1),1,"partition","fresh").passed());assertTrue(run("replay_twice",List.of((byte)0),0,"crash","stable").passed());assertFalse(run("volatile_ack",List.of((byte)0,(byte)1,(byte)2),3,"crash","stable").passed());
        assertEquals(Json.parse(golden().getElementsByTagName("controls").item(0).getTextContent(),"golden"),Json.parse(FaultSchedules.json(FaultSchedulesAnalysis.controls()),"controls"));for(String id:FaultSchedulesAnalysis.restartIds()){var row=observations().stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();var l=FaultSchedulesOracle.object(FaultSchedulesAnalysis.value(row,"local_lifecycle"));assertEquals(6,FaultSchedulesOracle.integer(l.get("replica_starts")));assertEquals(3,FaultSchedulesOracle.integer(l.get("crash_transitions")));}
        assertThrows(IllegalArgumentException.class,() -> FaultSchedulesPlugin.parseAtoms("impl(\"durable\");\n",List.of("fail(0);"),"cut(1);","fault(\"crash\");","retry(\"stable\");"));assertThrows(IllegalArgumentException.class,() -> FaultSchedulesPlugin.parseAtoms("impl(\"durable\");",List.of("fail(0);","fail(0);"),"cut(1);","fault(\"crash\");","retry(\"stable\");"));assertThrows(IllegalArgumentException.class,() -> FaultSchedulesPlugin.parseAtoms("impl(\"durable\");",List.of("fail(3);"),"cut(1);","fault(\"crash\");","retry(\"stable\");"));
    }
    private Map<String,Object> mutable(FaultSchedules.Result r){return FaultSchedulesOracle.object(Json.parse(FaultSchedules.json(r.document()),"mutable evidence"));}
    private Map<String,Object> judge(Map<String,Object> r){return FaultSchedulesOracle.judge(FaultSchedulesOracle.list(r.get("attempts")),FaultSchedulesOracle.list(r.get("trace")),FaultSchedulesOracle.list(r.get("final_payloads")));}
    @Test void policyBlindOracleRefusesMalformedEvidenceAndRejectsUniformWrongVacuousAndTamperedPayloads(){
        var wrong=run("replay_twice",List.of((byte)0,(byte)1,(byte)2),3,"crash","stable");assertFalse(wrong.passed());var checks=FaultSchedulesOracle.object(wrong.document().get("checks"));assertEquals(true,checks.get("replicas_agree"));assertEquals(false,checks.get("matches_fault_free_reference"));
        var good=run("durable",List.of((byte)0),1,"crash","stable");var doc=mutable(good);doc.put("policy","invented");doc.put("checks",Map.of());doc.put("verdict","DOMAIN_FAIL");assertEquals("PASS",judge(doc).get("verdict"));var payload=FaultSchedulesOracle.object(FaultSchedulesOracle.list(doc.get("final_payloads")).getFirst());var rec=FaultSchedulesOracle.object(FaultSchedulesOracle.list(payload.get("wal")).getFirst());rec.put("delta",2);var tampered=judge(doc);assertEquals("DOMAIN_FAIL",tampered.get("verdict"));assertEquals(false,FaultSchedulesOracle.object(tampered.get("checks")).get("final_wal_matches_accepted"));
        var empty=mutable(good);empty.put("attempts",List.of());var nodes=new ArrayList<Map<String,Object>>();var payloads=new ArrayList<Map<String,Object>>();for(int n=0;n<3;n++){nodes.add(FaultSchedules.fields("node",n,"running",true,"reachable",true,"generation",0,"accepted",List.of(),"wal",List.of(),"effects",List.of(),"counts",Map.of("o1",0,"o2",0,"o3",0),"value",0));payloads.add(FaultSchedules.fields("node",n,"accepted",List.of(),"wal",List.of()));}empty.put("trace",List.of(FaultSchedules.fields("checkpoint","repaired","nodes",nodes),FaultSchedules.fields("checkpoint","final","nodes",nodes)));empty.put("final_payloads",payloads);assertEquals("DOMAIN_FAIL",judge(empty).get("verdict"));
        var malformed=mutable(good);var trace=FaultSchedulesOracle.list(malformed.get("trace"));trace.removeIf(t -> FaultSchedulesOracle.object(t).get("checkpoint").equals("repaired"));assertThrows(IllegalArgumentException.class,() -> judge(malformed));var bad=mutable(good);var first=FaultSchedulesOracle.object(FaultSchedulesOracle.list(FaultSchedulesOracle.object(FaultSchedulesOracle.list(bad.get("trace")).getLast()).get("nodes")).getFirst());first.put("value",111.0);assertThrows(IllegalArgumentException.class,() -> judge(bad));first.put("value",111);first.put("effects",List.of());assertThrows(IllegalArgumentException.class,() -> judge(bad));
        var duplicate=mutable(good);var t=new ArrayList<Object>(FaultSchedulesOracle.list(duplicate.get("trace")));duplicate.put("trace",t);t.add(0,t.getFirst());assertThrows(IllegalArgumentException.class,() -> judge(duplicate));
    }
    @Test void completeRecordedTalliesMechanismsStrataAndFiveWitnessesMatchWithoutExecutingOrRepair()throws Exception{
        var rows=observations();var want=Json.parse(golden().getElementsByTagName("analysis").item(0).getTextContent(),"golden");assertEquals(want,Json.parse(FaultSchedules.json(FaultSchedulesAnalysis.analyse(rows)),"actual"));assertEquals(24,FaultSchedulesAnalysis.rejectedIds().size());assertThrows(IllegalArgumentException.class,() -> FaultSchedulesAnalysis.analyse(rows.subList(1,rows.size())));var duplicate=new ArrayList<>(rows);duplicate.add(rows.getFirst());assertThrows(IllegalArgumentException.class,() -> FaultSchedulesAnalysis.analyse(duplicate));var changed=new ArrayList<>(rows);int i=0;while(!rows.get(i).id().equals("P=replay_twice|F=012|C=3|K=crash|R=stable"))i++;var f=new TreeMap<>(rows.get(i).fields());f.put("verdict","PASS");var checks=FaultSchedulesOracle.object(Json.parse(f.get("checks"),"checks"));checks.put("matches_fault_free_reference",true);f.put("checks",FaultSchedules.json(checks));var trace=FaultSchedulesOracle.list(Json.parse(f.get("trace"),"trace"));FaultSchedulesOracle.object(FaultSchedulesOracle.list(FaultSchedulesOracle.object(trace.getLast()).get("nodes")).getFirst()).put("value",42);f.put("trace",FaultSchedules.json(trace));changed.set(i,new FaultSchedulesAnalysis.Observed(f));var actual=FaultSchedulesAnalysis.analyse(changed);assertEquals(Map.of("PASS",217,"DOMAIN_FAIL",95),actual.get("totals"));var witnesses=FaultSchedulesOracle.list(actual.get("witnesses"));var last=FaultSchedulesOracle.object(witnesses.getLast());assertEquals("PASS",last.get("verdict"));assertEquals(42,FaultSchedulesOracle.integer(FaultSchedulesOracle.object(FaultSchedulesOracle.list(last.get("final")).getFirst()).get("value")));assertNotEquals(want,Json.parse(FaultSchedules.json(actual),"changed"));f.put("verdict","BROKEN");changed.set(i,new FaultSchedulesAnalysis.Observed(f));assertThrows(IllegalArgumentException.class,() -> FaultSchedulesAnalysis.analyse(changed));
    }
}
