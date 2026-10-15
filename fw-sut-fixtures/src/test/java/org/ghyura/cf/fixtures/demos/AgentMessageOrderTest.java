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

class AgentMessageOrderTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d13c-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private AgentMessageOrder.Config config(String p,String m,int k,String s){return new AgentMessageOrder.Config(p,m,(byte)k,s);}
    private List<AgentMessageOrderAnalysis.Observed> observations(){var rows=new ArrayList<AgentMessageOrderAnalysis.Observed>();for(String p:AgentMessageOrder.POLICIES)for(String m:AgentMessageOrder.MODES)for(byte k=0;k<=2;k++)for(String w:AgentMessageOrderAnalysis.words())if(AgentMessageOrderAnalysis.included(m,k,w))rows.add(new AgentMessageOrderAnalysis.Observed(AgentMessageOrder.attributes(AgentMessageOrder.evaluate(config(p,m,k,w)))));return rows;}
    @Test void allFiftyFourOriginalTracesPromisesPrefixChecksAndEveryFrozenFieldMatch()throws Exception{
        var bank=new TreeMap<String,Map<String,String>>();for(var row:observations())assertNull(bank.put(row.id(),row.fields()));assertEquals(54,bank.size());var cases=golden().getElementsByTagName("case");for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=bank.get(c.getAttribute("id"));var fields=c.getElementsByTagName("field");assertEquals(19,fields.getLength());assertEquals(19,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}}
        assertEquals(36,bank.values().stream().filter(f -> f.get("verdict").equals("PASS")).count());var overwrite=AgentMessageOrder.evaluate(config("overwrite_owner","after_A_offer",1,"ABBA"));assertEquals(List.of(4L),Json.parse(AgentMessageOrder.json(overwrite.judgement().failingCheckpoints()),"indices"));assertEquals(Map.of("A","R:A"),overwrite.observedTrace().getLast().get("allocations"));assertEquals(Map.of("A","R:A","B","R:B"),overwrite.observedTrace().getLast().get("issued_grants"));
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> mutable(List<Map<String,Object>> trace){return (List<Map<String,Object>>)Json.parse(AgentMessageOrder.json(trace),"mutable observed trace");}
    @SuppressWarnings("unchecked") @Test void independentReferenceRequiresUsefulGrantIntegrityPrecedesSensorsAndMalformedSetupNeverBecomesDomainFail(){
        var c=config("compare_version","independent",2,"ABAB");var good=AgentMessageOrder.evaluate(c);var denied=mutable(good.observedTrace());for(var e:denied){var response=(Map<?,?>)e.get("response");if(response.containsKey("status"))e.put("response",AgentMessageOrder.fields("status","BUSY","ticket",null));e.put("allocations",Map.of());e.put("issued_grants",Map.of());e.put("version",0);}var deny=AgentMessageOrder.judge(c,denied);assertFalse(deny.passed());for(var check:deny.judgement().checks()){assertEquals(true,check.get("capacity_ok"));assertEquals(true,check.get("exclusive_promises_ok"));assertEquals(true,check.get("commitments_ok"));}assertEquals(false,((Map<?,?>)deny.document().get("all_checks")).get("reference_ok"));
        var corrupt=mutable(good.observedTrace());corrupt.getLast().put("issued_grants",Map.of());var broken=AgentMessageOrderOracle.judge(corrupt,good.referenceTrace());assertFalse(broken.integrityOk());assertNull(broken.checks());assertNull(broken.failingCheckpoints());assertEquals(List.of(4),broken.integrityFailures());assertThrows(IllegalStateException.class,() -> AgentMessageOrder.judge(c,corrupt));
        var wrong=mutable(good.observedTrace());wrong.get(2).put("response",AgentMessageOrder.fields("status","GRANTED","ticket","wrong"));assertFalse(AgentMessageOrderOracle.judge(wrong,good.referenceTrace()).integrityOk());assertFalse(AgentMessageOrderOracle.judge(good.observedTrace().subList(0,3),good.referenceTrace()).integrityOk());
        for(var invalid:List.of(config("compare_version","after_A_offer",2,"BAAB"),config("compare_version","independent",1,"ABAB"),config("compare_version","independent",2,"AAAA")))assertThrows(IllegalArgumentException.class,() -> AgentMessageOrder.evaluate(invalid));assertEquals(0,AgentMessageOrder.preemptions("AABB"));assertEquals(1,AgentMessageOrder.preemptions("ABBA"));assertEquals(2,AgentMessageOrder.preemptions("ABAB"));assertEquals(3,AgentMessageOrder.contextSwitches("ABAB"));
        var coordinator=new AgentMessageOrder.Coordinator("compare_version");var valid=new TreeMap<String,Object>(AgentMessageOrder.fields("id","A:commit","agent","A","kind","commit","resource","R","offer_version",(byte)0,"offer_available",true));var before=coordinator.snapshot();for(var patch:List.of(Map.of("resource","other"),Map.of("id","wrong"),Map.of("offer_version",0.0),Map.of("offer_available","true"),Map.of("extra",1))){var req=new TreeMap<>(valid);req.putAll(patch);assertThrows(IllegalArgumentException.class,() -> coordinator.deliver(req));assertEquals(before,coordinator.snapshot());}assertEquals("GRANTED",coordinator.deliver(valid).get("status"));assertEquals(before.get("version"),good.observedTrace().getFirst().get("version"));
        assertThrows(UnsupportedOperationException.class,() -> ((Map<String,Object>)good.observedTrace().getFirst().get("offers")).put("C",Map.of()));
    }
    @SuppressWarnings("unchecked") @Test void allOriginalStrataDenominatorsMechanismsWitnessesAndEnablednessCertificatesUseRecordedValuesWithoutRepair()throws Exception{
        var rows=observations();var g=golden();var analysis=AgentMessageOrderAnalysis.analyse(rows);assertEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(AgentMessageOrder.json(analysis),"actual"));assertEquals(Json.parse(g.getElementsByTagName("exploration").item(0).getTextContent(),"golden"),Json.parse(AgentMessageOrder.json(AgentMessageOrderAnalysis.exploration()),"actual"));
        var incomplete=new ArrayList<>(rows);incomplete.removeLast();assertThrows(IllegalArgumentException.class,() -> AgentMessageOrderAnalysis.analyse(incomplete));var duplicate=new ArrayList<>(rows);duplicate.add(rows.getFirst());assertThrows(IllegalArgumentException.class,() -> AgentMessageOrderAnalysis.analyse(duplicate));for(var patch:List.of(Map.of("verdict","BROKEN"),Map.of("case_id","unknown"),Map.of("mode","after_A_offer"))){var bad=new ArrayList<>(rows);var f=new TreeMap<>(rows.getFirst().fields());f.putAll(patch);bad.set(0,new AgentMessageOrderAnalysis.Observed(f));assertThrows(IllegalArgumentException.class,() -> AgentMessageOrderAnalysis.analyse(bad));}
        String id=AgentMessageOrderAnalysis.witnesses().get("trust_offer_double_allocation");var changed=new ArrayList<>(rows);for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(id)){var f=new TreeMap<>(rows.get(i).fields());f.put("observed_trace","[]");f.put("checks","[]");f.put("failing_checkpoints","[]");f.put("verdict","PASS");changed.set(i,new AgentMessageOrderAnalysis.Observed(f));}var recorded=AgentMessageOrderAnalysis.analyse(changed);assertEquals(37,((Map<String,Integer>)recorded.get("totals")).get("PASS"));var witness=((List<Map<String,Object>>)recorded.get("witnesses")).getFirst();assertEquals(List.of(),witness.get("observed_trace"));assertEquals("PASS",witness.get("verdict"));
    }
}
