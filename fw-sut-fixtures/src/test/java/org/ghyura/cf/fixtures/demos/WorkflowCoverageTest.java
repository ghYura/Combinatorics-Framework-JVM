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

class WorkflowCoverageTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d3c-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private List<WorkflowCoverageAnalysis.Observed> observations(){return WorkflowCoverageAnalysis.configurations().stream().map(c -> new WorkflowCoverageAnalysis.Observed(WorkflowCoverage.attributes(WorkflowCoverage.evaluate(c)))).toList();}
    private WorkflowCoverage.Result run(String policy,String order){return WorkflowCoverage.evaluate(new WorkflowCoverage.Config(policy,order));}
    @Test void allCasesMatchIndependentSnapshots()throws Exception{
        var bank=new TreeMap<String,Map<String,String>>();for(var row:observations())assertNull(bank.put(row.id(),row.fields()));assertEquals(1440,bank.size());var cases=golden().getElementsByTagName("case");assertEquals(1440,cases.getLength());for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=bank.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(12,fields.getLength());assertEquals(12,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}}assertEquals(1410,bank.values().stream().filter(f -> f.get("verdict").equals("PASS")).count());
    }
    @Test void durableRestartAndReadControlsPreserveEarlierFailures(){
        var d=new WorkflowCoverage.Durable();var service=new WorkflowCoverage.Service("late_restart",d);var before=service.snapshot();service=service.apply("C").apply("C");assertEquals(100,WorkflowCoverageOracle.integer(service.snapshot().get("balance")));service=service.apply("F").apply("F");assertEquals(0,WorkflowCoverageOracle.integer(service.snapshot().get("balance")));service=service.apply("N");assertEquals(false,service.snapshot().get("refund_attempted"));service=service.apply("F");assertEquals(true,service.snapshot().get("refund_attempted"));assertEquals(service.snapshot(),service.apply("V").snapshot());assertEquals(0,WorkflowCoverageOracle.integer(before.get("epoch")));assertEquals(List.of((byte)0),before.get("captures"));assertThrows(UnsupportedOperationException.class,() -> WorkflowCoverageOracle.list(before.get("captures")).clear());assertThrows(IllegalArgumentException.class,() -> new WorkflowCoverage.Service("invented",d));var current=service;assertThrows(IllegalArgumentException.class,() -> current.apply("?"));
        var faulty=new WorkflowCoverage.Service("late_restart",new WorkflowCoverage.Durable()).apply("C").apply("N").apply("F");assertEquals(100,WorkflowCoverageOracle.integer(faulty.snapshot().get("balance")));var reopened=faulty.apply("S");assertNotSame(faulty,reopened);assertEquals(0,WorkflowCoverageOracle.integer(reopened.snapshot().get("balance")));assertEquals(100,WorkflowCoverageOracle.integer(new WorkflowCoverage.Service("correct",new WorkflowCoverage.Durable()).apply("C").apply("N").apply("F").apply("S").snapshot().get("balance")));assertEquals(100,WorkflowCoverageOracle.integer(reopened.apply("Q").snapshot().get("balance")));
        var heals=run("late_restart","CNFSQV");assertFalse(heals.passed());assertEquals("PASS",heals.document().get("final_only_verdict"));assertEquals(true,heals.document().get("hidden_by_final_only"));assertEquals(List.of((byte)4),heals.document().get("failing_checkpoints"));assertEquals(List.of((byte)5,(byte)6),run("late_restart","QCNFSV").document().get("failing_checkpoints"));assertTrue(run("late_restart","CFNSQV").passed());
        assertEquals(new WorkflowCoverage.Config("correct","CFNQSV"),WorkflowCoveragePlugin.parseAtoms("start(\"correct\")\n",List.of("step(\"C\")\n","step(\"F\")\n","step(\"N\")\n","step(\"Q\")\n","step(\"S\")\n","step(\"V\")\n")));assertThrows(IllegalArgumentException.class,() -> WorkflowCoveragePlugin.parseAtoms("start(\"correct\");",List.of()));assertThrows(IllegalArgumentException.class,() -> new WorkflowCoverage.Config("correct","CFNQSS"));assertThrows(IllegalArgumentException.class,() -> new WorkflowCoverage.Config("correct","CFNQS"));
    }
    @Test void policyBlindChecksRejectWrongAndMalformedSnapshots(){
        var initial=WorkflowCoverageOracle.initial();var expected=WorkflowCoverageOracle.transition(initial,"C");var wrong=new TreeMap<>(expected);wrong.put("balance",42);assertEquals(false,WorkflowCoverageOracle.check(wrong,expected).get("ok"));assertEquals(List.of("balance"),WorkflowCoverageOracle.check(wrong,expected).get("mismatched_fields"));wrong.put("balance",100);wrong.put("captures",List.of(2));assertEquals(false,WorkflowCoverageOracle.invariants(wrong).get("count_domain"));assertEquals(false,WorkflowCoverageOracle.check(wrong,expected).get("ok"));wrong.put("captures",List.of(1));wrong.put("refund_attempted","false");assertEquals(false,WorkflowCoverageOracle.invariants(wrong).get("flag_boolean"));assertEquals(false,WorkflowCoverageOracle.check(wrong,expected).get("ok"));wrong.put("refund_attempted",false);wrong.put("refunds",List.of(1));assertEquals(false,WorkflowCoverageOracle.check(wrong,expected).get("ok"));var bad=new TreeMap<>(initial);bad.put("epoch",0.0);assertThrows(IllegalArgumentException.class,() -> WorkflowCoverageOracle.check(bad,initial));bad.put("epoch",0);bad.remove("captures");assertThrows(IllegalArgumentException.class,() -> WorkflowCoverageOracle.check(bad,initial));var invalidReference=new TreeMap<>(initial);invalidReference.put("balance",1);assertThrows(IllegalArgumentException.class,() -> WorkflowCoverageOracle.check(initial,invalidReference));assertThrows(IllegalArgumentException.class,() -> WorkflowCoverageOracle.transition(initial,"?"));assertEquals(List.of(0),initial.get("captures"));
    }
    @Test void recordedCoverageCertificatesMeasureWithoutRepair()throws Exception{
        var rows=observations();var analysis=WorkflowCoverageAnalysis.analyse(rows);assertEquals(Json.parse(golden().getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(WorkflowCoverage.json(analysis),"actual"));assertEquals(Json.parse(golden().getElementsByTagName("catalogue").item(0).getTextContent(),"golden"),Json.parse(WorkflowCoverage.json(WorkflowCoverageAnalysis.catalogue()),"actual"));var suites=WorkflowCoverageOracle.object(analysis.get("suites"));assertEquals(120,WorkflowCoverageOracle.integer(WorkflowCoverageOracle.object(suites.get("SCA3_counterexample")).get("covered")));assertEquals(List.of(),WorkflowCoverageOracle.object(suites.get("SCA3_counterexample")).get("measured_late_restart_detections"));assertThrows(IllegalArgumentException.class,() -> WorkflowCoverageAnalysis.analyse(rows.subList(1,rows.size())));var duplicate=new ArrayList<>(rows);duplicate.add(rows.getFirst());assertThrows(IllegalArgumentException.class,() -> WorkflowCoverageAnalysis.analyse(duplicate));
        var changed=new ArrayList<>(rows);int i=0;while(!rows.get(i).id().equals("C|late_restart|OPS=CNFSQV"))i++;var f=new TreeMap<>(rows.get(i).fields());f.put("verdict","PASS");var cps=WorkflowCoverageOracle.list(Json.parse(f.get("checkpoints"),"recorded"));WorkflowCoverageOracle.object(WorkflowCoverageOracle.object(cps.getFirst()).get("observed")).put("balance",42);f.put("checkpoints",WorkflowCoverage.json(cps));changed.set(i,new WorkflowCoverageAnalysis.Observed(f));var actual=WorkflowCoverageAnalysis.analyse(changed);assertEquals(1411,WorkflowCoverageOracle.integer(WorkflowCoverageOracle.object(actual.get("totals")).get("PASS")));var witness=WorkflowCoverageOracle.object(WorkflowCoverageOracle.list(actual.get("witnesses")).getFirst());assertEquals("PASS",witness.get("verdict"));assertEquals(42,WorkflowCoverageOracle.integer(WorkflowCoverageOracle.object(WorkflowCoverageOracle.object(WorkflowCoverageOracle.list(witness.get("checkpoints")).getFirst()).get("observed")).get("balance")));
        var asset=WorkflowCoverageOracle.object(Json.parse(WorkflowCoverage.json(WorkflowCoverageAnalysis.coverageAsset()),"mutable certificate"));var sourceSuites=WorkflowCoverageOracle.object(asset.get("suites"));var cert=WorkflowCoverageOracle.object(WorkflowCoverageOracle.object(sourceSuites.get("SCA2")).get("certificate"));cert.put("CF","VSQNFC");var bank=new TreeMap<String,WorkflowCoverageAnalysis.Observed>();rows.forEach(o -> bank.put(o.id(),o));assertThrows(IllegalArgumentException.class,() -> WorkflowCoverageAnalysis.measureSuites(bank,sourceSuites));
    }
}
