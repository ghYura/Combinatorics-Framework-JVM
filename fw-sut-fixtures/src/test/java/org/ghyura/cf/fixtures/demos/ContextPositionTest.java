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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class ContextPositionTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d13e-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private List<ContextPositionAnalysis.Observed> observations(){return ContextPositionAnalysis.configurations().stream().map(c -> new ContextPositionAnalysis.Observed(ContextPosition.attributes(ContextPosition.evaluate(c)))).toList();}
    @SuppressWarnings("unchecked") @Test void allFiftyFourCasesEveryTrialDrawReadingAndContextStepMatchIndependentPredictions()throws Exception{
        var bank=new TreeMap<String,Map<String,String>>();for(var row:observations())assertNull(bank.put(row.id(),row.fields()));assertEquals(54,bank.size());var cases=golden().getElementsByTagName("case");int steps=0,readings=0;for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=bank.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(14,actual.size());assertEquals(14,fields.getLength());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}var trials=(List<Map<String,Object>>)Json.parse(actual.get("trials"),"trials");assertEquals(20,trials.size());for(var t:trials){assertEquals(6,t.size());steps+=((List<?>)t.get("context_trace")).size();readings+=((Map<?,?>)t.get("judge_approvals")).size();}}
        assertEquals(6480,steps);assertEquals(2160,readings);assertEquals(49,bank.values().stream().filter(f -> f.get("verdict").equals("PASS")).count());
    }
    @Test void malformedContextsTracesTrialsAndDrawsRefuseWhileUsefulWorkAndExactPairedThresholdsSurvive(){
        for(String o:List.of("ABCDE","AABCDEG","ABCDEF","abcdeg"))assertThrows(IllegalArgumentException.class,() -> new ContextPosition.Config("stable","public",o));var p=new ContextPosition.Processor("last_marker");var fold=p.run("secret","GABEDC");assertEquals("ALLOW",fold.decision());assertThrows(IllegalStateException.class,() -> p.run("secret","ABCDEG"));p.reset();assertEquals("DENY",p.run("secret","ABCDEG").decision());assertNull(fold.trace().getFirst().get("before"));assertThrows(UnsupportedOperationException.class,() -> fold.trace().getFirst().put("after","DENY"));
        for(var patch:List.of(Map.<String,Object>of("position",1.0),Map.<String,Object>of("token","A"),Map.<String,Object>of("origin","untrusted"),Map.<String,Object>of("before","ALLOW"),Map.<String,Object>of("after","unknown"))){var trace=new ArrayList<>(fold.trace());var first=new TreeMap<>(trace.getFirst());first.putAll(patch);trace.set(0,first);assertThrows(IllegalArgumentException.class,() -> ContextPositionOracle.checkTrace("GABEDC",trace,"ALLOW"));}assertThrows(IllegalArgumentException.class,() -> ContextPositionOracle.checkTrace("GABEDC",fold.trace().subList(0,5),"ALLOW"));assertThrows(IllegalArgumentException.class,() -> ContextPositionOracle.checkTrace("GABEDC",fold.trace(),"DENY"));
        var c=new ContextPosition.Config("stable","public","ABGCDE");var result=ContextPosition.evaluate(c);assertTrue(result.passed());var modified=new ArrayList<Map<String,Object>>();for(var t:result.trials()){var m=new TreeMap<>(t);m.put("judge_approvals",Map.of("calibrated",false,"position_biased",false));m.put("mechanical_ok",false);modified.add(m);}assertEquals(List.of(),ContextPositionOracle.failingTrials("public",c.order(),modified));assertEquals(20,ContextPositionOracle.failingTrials("secret",c.order(),modified).size());assertThrows(IllegalArgumentException.class,() -> ContextPositionOracle.failingTrials("public",c.order(),modified.subList(0,19)));var wrong=new ArrayList<>(modified);var first=new TreeMap<>(wrong.getFirst());first.put("trial",1);wrong.set(0,first);assertThrows(IllegalArgumentException.class,() -> ContextPositionOracle.failingTrials("public",c.order(),wrong));
        var range=ContextPosition.DRAW_RANGE;var cal=range.divide(BigInteger.TEN);var biased=range.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(5));assertEquals(true,ContextPosition.flips(cal,(byte)1).get("calibrated"));assertEquals(false,ContextPosition.flips(cal.add(BigInteger.ONE),(byte)1).get("calibrated"));assertEquals(true,ContextPosition.flips(biased,(byte)3).get("position_biased"));assertEquals(false,ContextPosition.flips(biased.add(BigInteger.ONE),(byte)3).get("position_biased"));for(var n:List.of(BigInteger.ZERO,cal,cal.add(BigInteger.ONE),biased,range.subtract(BigInteger.ONE))){var late=ContextPosition.flips(n,(byte)4);assertEquals(late.get("calibrated"),late.get("position_biased"));var early=ContextPosition.flips(n,(byte)3);assertFalse(early.get("calibrated")&&!early.get("position_biased"));}for(var n:List.of(BigInteger.valueOf(-1),range))assertThrows(IllegalArgumentException.class,() -> ContextPosition.flips(n,(byte)1));for(byte g:List.of((byte)0,(byte)7))assertThrows(IllegalArgumentException.class,() -> ContextPosition.flips(BigInteger.ZERO,g));assertThrows(IllegalArgumentException.class,() -> ContextPosition.draw(c,(byte)20));assertThrows(IllegalArgumentException.class,() -> ContextPositionPlugin.order("set_order(\"ABGCDE\");\n"));
    }
    @SuppressWarnings("unchecked") @Test void completeRecordedCalibrationStrataWilsonPairedTablesAndWitnessesMatchWithoutRepair()throws Exception{
        var rows=observations();var g=golden();for(var pair:List.of(Map.entry("analysis",ContextPositionAnalysis.analyse(rows)),Map.entry("coverage",ContextPositionAnalysis.coverage()),Map.entry("universe",ContextPositionAnalysis.universe())))assertTrue(ContextPositionAnalysis.equivalent(Json.parse(g.getElementsByTagName(pair.getKey()).item(0).getTextContent(),"golden"),Json.parse(ContextPosition.json(pair.getValue()),"actual")),pair.getKey());
        var missing=new ArrayList<>(rows);missing.removeLast();assertThrows(IllegalArgumentException.class,() -> ContextPositionAnalysis.analyse(missing));var duplicate=new ArrayList<>(rows);duplicate.add(rows.getFirst());assertThrows(IllegalArgumentException.class,() -> ContextPositionAnalysis.analyse(duplicate));for(var patch:List.of(Map.of("verdict","BROKEN"),Map.of("case_id","unknown"),Map.of("band","late"),Map.of("guard_position","3.0"))){var bad=new ArrayList<>(rows);var f=new TreeMap<>(rows.get(8).fields());f.putAll(patch);bad.set(8,new ContextPositionAnalysis.Observed(f));assertThrows(IllegalArgumentException.class,() -> ContextPositionAnalysis.analyse(bad));}
        var changed=new ArrayList<>(rows);String id=ContextPositionAnalysis.witnesses().get("matched_correct_processor");for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(id)){var f=new TreeMap<>(rows.get(i).fields());var trials=(List<Map<String,Object>>)Json.parse(f.get("trials"),"retained");for(var t:trials)t.put("judge_approvals",Map.of("calibrated",false,"position_biased",false));f.put("trials",ContextPosition.json(trials));f.put("verdict","DOMAIN_FAIL");changed.set(i,new ContextPositionAnalysis.Observed(f));}var a=ContextPositionAnalysis.analyse(changed);assertEquals(48,((Map<String,Integer>)a.get("totals")).get("PASS"));var witness=((List<Map<String,Object>>)a.get("witnesses")).get(1);assertEquals("DOMAIN_FAIL",witness.get("verdict"));assertEquals(Map.of("calibrated",0,"position_biased",0),witness.get("judge_approvals_true"));assertFalse(ContextPositionAnalysis.equivalent(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),a));assertFalse(ContextPositionAnalysis.equivalent(Map.of("n",1),Map.of("n",2)));assertFalse(ContextPositionAnalysis.equivalent(List.of(0.1),List.of(0.1001)));
    }
}
