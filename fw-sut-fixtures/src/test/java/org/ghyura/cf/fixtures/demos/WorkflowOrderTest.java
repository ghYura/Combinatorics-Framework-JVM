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

class WorkflowOrderTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d3-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private List<WorkflowOrderAnalysis.Observed> observations(String phase){return WorkflowOrderAnalysis.configurations(phase).stream().map(c -> new WorkflowOrderAnalysis.Observed(WorkflowOrder.attributes(WorkflowOrder.evaluate(c)))).toList();}
    private WorkflowOrder.Result run(String phase,String policy,String core,int s,int q){return WorkflowOrder.evaluate(new WorkflowOrder.Config(phase,policy,core,(byte)s,(byte)q));}
    @Test void allCasesMatchIndependentSnapshots()throws Exception{
        var bank=new TreeMap<String,Map<String,String>>();for(String phase:List.of("A","B"))for(var row:observations(phase))assertNull(bank.put(row.id(),row.fields()));assertEquals(153,bank.size());var cases=golden().getElementsByTagName("case");assertEquals(153,cases.getLength());for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=bank.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(15,fields.getLength());assertEquals(15,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}}assertEquals(114,bank.values().stream().filter(f -> f.get("verdict").equals("PASS")).count());
    }
    @Test void durableLedgerRepeatedRefundAndRestartControls(){
        var ledger=new WorkflowOrder.Ledger();var service=new WorkflowOrder.Service("correct",ledger);var before=service.snapshot();service=service.apply("F").apply("C").apply("F").apply("C");assertEquals(0,WorkflowOrderOracle.integer(service.snapshot().get("balance")));assertEquals(List.of((byte)1),service.snapshot().get("captures"));assertEquals(List.of(1),service.snapshot().get("refunds"));service=service.apply("N").apply("C");assertEquals(100,WorkflowOrderOracle.integer(service.snapshot().get("balance")));assertEquals(List.of((byte)1,(byte)1),service.snapshot().get("captures"));assertEquals(List.of((byte)0),before.get("captures"));assertThrows(UnsupportedOperationException.class,() -> WorkflowOrderOracle.list(before.get("captures")).clear());
        var unchecked=new WorkflowOrder.Service("refund_unchecked",new WorkflowOrder.Ledger()).apply("F").apply("F");assertEquals(List.of(2),unchecked.snapshot().get("refunds"));assertEquals(-200,WorkflowOrderOracle.integer(unchecked.snapshot().get("balance")));for(int i=2;i<150;i++)unchecked=unchecked.apply("F");assertEquals(List.of(150),unchecked.snapshot().get("refunds"));assertEquals(-15000,WorkflowOrderOracle.integer(unchecked.snapshot().get("balance")));
        var faulty=new WorkflowOrder.Service("restart_cache",new WorkflowOrder.Ledger()).apply("C");var reopened=faulty.apply("S");assertNotSame(faulty,reopened);assertEquals(0,WorkflowOrderOracle.integer(reopened.snapshot().get("balance")));assertEquals(100,WorkflowOrderOracle.integer(reopened.apply("Q").snapshot().get("balance")));assertEquals(100,WorkflowOrderOracle.integer(new WorkflowOrder.Service("correct",new WorkflowOrder.Ledger()).apply("C").apply("S").snapshot().get("balance")));
        var heals=run("A","restart_cache","CNF",1,1);assertFalse(heals.passed());assertEquals("PASS",heals.document().get("final_only_verdict"));assertEquals(true,heals.document().get("hidden_by_final_only"));assertEquals(List.of((byte)4),heals.document().get("failing_checkpoints"));assertEquals(List.of((byte)1,(byte)2,(byte)3),run("B","refund_unchecked","FFC",0,0).document().get("failing_checkpoints"));assertTrue(run("B","correct","CCC",0,0).passed());assertTrue(run("B","restart_cache","NNN",0,0).passed());
        assertThrows(IllegalArgumentException.class,() -> new WorkflowOrder.Config("A","correct","CCC",(byte)0,(byte)0));assertThrows(IllegalArgumentException.class,() -> new WorkflowOrder.Config("B","correct","CFN",(byte)1,(byte)0));assertThrows(IllegalArgumentException.class,() -> new WorkflowOrder.Config("B","correct","CFX",(byte)0,(byte)0));assertThrows(IllegalArgumentException.class,() -> WorkflowOrderPlugin.parseAtoms("A","start(\"correct\");",List.of(),List.of()));assertThrows(IllegalArgumentException.class,() -> WorkflowOrderPlugin.parseAtoms("A","start(\"correct\")\n",List.of("step(\"C\")\n","step(\"F\")\n","step(\"N\")\n"),List.of("Q","S")));var current=service;assertThrows(IllegalArgumentException.class,() -> current.apply("V"));
    }
    @Test void policyBlindChecksRejectWrongAndMalformedSnapshots(){
        var initial=WorkflowOrderOracle.initial();var expected=WorkflowOrderOracle.transition(initial,"C");var wrong=new TreeMap<>(expected);wrong.put("balance",42);assertEquals(false,WorkflowOrderOracle.check(wrong,expected).get("ok"));assertEquals(List.of("balance"),WorkflowOrderOracle.check(wrong,expected).get("mismatched_fields"));wrong.put("balance",100);wrong.put("refunds",List.of(2));assertEquals(false,WorkflowOrderOracle.invariants(wrong).get("count_domain"));assertEquals(false,WorkflowOrderOracle.invariants(wrong).get("refunds_le_captures"));wrong.put("refunds",List.of());assertEquals(false,WorkflowOrderOracle.invariants(wrong).get("list_lengths"));assertEquals(false,WorkflowOrderOracle.check(wrong,expected).get("ok"));var bad=new TreeMap<>(initial);bad.put("epoch",0.0);assertThrows(IllegalArgumentException.class,() -> WorkflowOrderOracle.check(bad,initial));bad.put("epoch",0);bad.remove("captures");assertThrows(IllegalArgumentException.class,() -> WorkflowOrderOracle.check(bad,initial));var invalidReference=new TreeMap<>(initial);invalidReference.put("balance",1);assertThrows(IllegalArgumentException.class,() -> WorkflowOrderOracle.check(initial,invalidReference));assertThrows(IllegalArgumentException.class,() -> WorkflowOrderOracle.transition(initial,"V"));assertEquals(List.of(0),initial.get("captures"));
    }
    @Test void recordedSummariesAndWitnessesReadWithoutRepair()throws Exception{
        for(String phase:List.of("A","B")){var rows=observations(phase);var want=golden().getElementsByTagName("analysis");Element expected=null;for(int i=0;i<want.getLength();i++)if(((Element)want.item(i)).getAttribute("phase").equals(phase))expected=(Element)want.item(i);assertNotNull(expected);assertEquals(Json.parse(expected.getTextContent(),"golden"),Json.parse(WorkflowOrder.json(WorkflowOrderAnalysis.analyse(phase,rows)),"actual"));assertThrows(IllegalArgumentException.class,() -> WorkflowOrderAnalysis.analyse(phase,rows.subList(1,rows.size())));var duplicate=new ArrayList<>(rows);duplicate.add(rows.getFirst());assertThrows(IllegalArgumentException.class,() -> WorkflowOrderAnalysis.analyse(phase,duplicate));}
        assertEquals(Json.parse(golden().getElementsByTagName("alternate").item(0).getTextContent(),"golden"),Json.parse(WorkflowOrder.json(WorkflowOrderAnalysis.alternate()),"actual"));var rows=observations("A");var changed=new ArrayList<>(rows);int i=0;while(!rows.get(i).id().equals("A|restart_cache|OPS=CNF|S=1|Q=1"))i++;var f=new TreeMap<>(rows.get(i).fields());f.put("verdict","PASS");var cps=WorkflowOrderOracle.list(Json.parse(f.get("checkpoints"),"recorded"));WorkflowOrderOracle.object(WorkflowOrderOracle.object(cps.getFirst()).get("observed")).put("balance",42);f.put("checkpoints",WorkflowOrder.json(cps));changed.set(i,new WorkflowOrderAnalysis.Observed(f));var actual=WorkflowOrderAnalysis.analyse("A",changed);assertEquals(49,WorkflowOrderOracle.integer(WorkflowOrderOracle.object(actual.get("totals")).get("PASS")));var witness=WorkflowOrderOracle.object(WorkflowOrderOracle.list(actual.get("witnesses")).get(1));assertEquals("PASS",witness.get("verdict"));assertEquals(42,WorkflowOrderOracle.integer(WorkflowOrderOracle.object(WorkflowOrderOracle.object(WorkflowOrderOracle.list(witness.get("checkpoints")).getFirst()).get("observed")).get("balance")));var conflict=new ArrayList<>(rows);var bad=new TreeMap<>(rows.getFirst().fields());bad.put("S","256");conflict.set(0,new WorkflowOrderAnalysis.Observed(bad));assertThrows(IllegalArgumentException.class,() -> WorkflowOrderAnalysis.analyse("A",conflict));assertThrows(IllegalArgumentException.class,() -> WorkflowOrderAnalysis.analyse("B",rows));
    }
}
