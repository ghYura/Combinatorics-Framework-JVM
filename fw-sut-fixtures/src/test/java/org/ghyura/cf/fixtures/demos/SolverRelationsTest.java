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

class SolverRelationsTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d14c-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private List<SolverRelationsAnalysis.Observed> observations(){var rows=new ArrayList<SolverRelationsAnalysis.Observed>();for(String p:SolverRelations.POLICIES)for(String i:SolverRelations.INSTANCES)for(var r:SolverRelationsAnalysis.permutations())for(String e:SolverRelations.EDITS)rows.add(new SolverRelationsAnalysis.Observed(SolverRelations.attributes(SolverRelations.evaluate(new SolverRelations.Config(p,i,r,e)))));return rows;}
    @SuppressWarnings("unchecked") @Test void allOneHundredFortyFourCasesAndThreeVariantsMatchEveryFrozenFieldIncludingTiedOptimaAndBothDefects()throws Exception{
        var bank=new TreeMap<String,Map<String,String>>();for(var row:observations())assertNull(bank.put(row.id(),row.fields()));assertEquals(144,bank.size());var cases=golden().getElementsByTagName("case");assertEquals(144,cases.getLength());for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=bank.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(14,fields.getLength());assertEquals(14,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}}
        assertEquals(72,bank.values().stream().filter(f -> f.get("verdict").equals("PASS")).count());assertEquals(8064,bank.values().stream().mapToInt(f -> ((Map<String,Number>)Json.parse(f.get("oracle_subset_checks"),"checks")).values().stream().mapToInt(Number::intValue).sum()).sum());var tie=SolverRelations.baseBids("tie");assertEquals(List.of("b1","b2","b3"),SolverRelations.solve("exact_dp",tie).selected());assertEquals(List.of("b0","b3"),SolverRelations.solve("greedy_value",tie).selected());assertEquals(List.of(List.of("b0","b3"),List.of("b1","b2","b3")),SolverRelationsOracle.enumerateSubsets(tie).optimalAllocations());
    }
    @Test void independentExhaustiveOracleRejectsMalformedResultsAndRequiresUsefulOptimalCorrectValueAndValidTransforms(){
        var bids=SolverRelations.baseBids("disjoint");var wrong=SolverRelationsOracle.observe(bids,Map.of("selected",List.of("b0","b1","b2"),"reported_value",13));assertEquals(true,wrong.document().get("feasible"));assertEquals(true,wrong.document().get("optimal"));assertEquals(false,wrong.document().get("value_correct"));assertEquals(12,wrong.document().get("actual_value"));var denied=SolverRelationsOracle.observe(bids,Map.of("selected",List.of(),"reported_value",0));assertEquals(true,denied.document().get("feasible"));assertEquals(false,denied.document().get("optimal"));assertEquals(12,denied.document().get("gap"));var variants=Map.<String,Object>of("base",denied.document(),"relabeled",denied.document(),"edited",denied.document());var result=new SolverRelations.Result(new SolverRelations.Config("exact_dp","disjoint",SolverRelations.GOODS,"none"),variants,SolverRelationsOracle.relations(variants),Map.of());assertFalse(result.passed());assertTrue(result.relations().values().stream().allMatch(Boolean.TRUE::equals));
        for(var bad:List.of(Map.<String,Object>of("selected",List.of("b9"),"reported_value",1),Map.<String,Object>of("selected",List.of("b1","b1"),"reported_value",8),Map.<String,Object>of("selected",List.of("b2","b1"),"reported_value",8),Map.<String,Object>of("selected",List.of("b1"),"reported_value",4.0),Map.<String,Object>of("selected","b1","reported_value",4),Map.<String,Object>of("selected",List.of("b1")),Map.<String,Object>of("reported_value",4)))assertThrows(IllegalArgumentException.class,() -> SolverRelationsOracle.observe(bids,bad));
        var empty=SolverRelationsOracle.enumerateSubsets(List.of());assertEquals(0,empty.optimum());assertEquals(1,empty.feasibleSubsets());assertEquals(List.of(List.of()),empty.optimalAllocations());assertEquals(1,empty.subsetChecks());for(String p:SolverRelations.POLICIES)assertEquals(new SolverRelations.Answer(List.of(),0),SolverRelations.solve(p,List.of()));
        for(var labels:List.of(List.of("label(0);","label(0);","label(2);"),List.of("label(0);","label(1);"),List.of("label(0);","label(1);","label(3);"),List.of("label(0);\n","label(1);","label(2);")))assertThrows(IllegalArgumentException.class,() -> SolverRelationsPlugin.permutation(labels));assertThrows(IllegalArgumentException.class,() -> SolverRelations.edit(List.of(new SolverRelations.Bid("b0",List.of(),5)),"dominated"));assertThrows(IllegalArgumentException.class,() -> SolverRelations.edit(List.of(new SolverRelations.Bid("b0",List.of((byte)0),1)),"dominated"));assertThrows(IllegalArgumentException.class,() -> SolverRelationsPlugin.parseAtoms("impl(\"unknown\");","instance(\"tie\");",List.of("label(0);","label(1);","label(2);"),"edit(\"none\");"));assertEquals(Map.of("relabel_bijection_on_all_24_inputs",true,"dominated_swap_on_all_24_edited_inputs",true),SolverRelationsAnalysis.transformationProofs());
        assertThrows(UnsupportedOperationException.class,() -> SolverRelations.baseBids("tie").getFirst().items().add((byte)2));
    }
    @SuppressWarnings("unchecked") @Test void completeOriginalTalliesControlsAndFiveWitnessesUseRecordedValuesWithoutSolvingOrRepair()throws Exception{
        var rows=observations();var g=golden();for(var pair:List.of(Map.entry("analysis",SolverRelationsAnalysis.analyse(rows)),Map.entry("controls",SolverRelationsAnalysis.controls(rows)),Map.entry("proofs",SolverRelationsAnalysis.transformationProofs())))assertEquals(Json.parse(g.getElementsByTagName(pair.getKey()).item(0).getTextContent(),"golden"),Json.parse(SolverRelations.json(pair.getValue()),"actual"));
        var missing=new ArrayList<>(rows);missing.removeLast();assertThrows(IllegalArgumentException.class,() -> SolverRelationsAnalysis.analyse(missing));var dup=new ArrayList<>(rows);dup.add(rows.getFirst());assertThrows(IllegalArgumentException.class,() -> SolverRelationsAnalysis.analyse(dup));for(var patch:List.of(Map.of("verdict","BROKEN"),Map.of("id","unknown"),Map.of("instance","tie"),Map.of("permutation","[0.0,1,2]"))){var bad=new ArrayList<>(rows);var f=new TreeMap<>(rows.getFirst().fields());f.putAll(patch);bad.set(0,new SolverRelationsAnalysis.Observed(f));assertThrows(IllegalArgumentException.class,() -> SolverRelationsAnalysis.analyse(bad));}
        String id=SolverRelationsAnalysis.witnesses().get("greedy_gap_6_on_disjoint");var changed=new ArrayList<>(rows);for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(id)){var f=new TreeMap<>(rows.get(i).fields());var variants=(Map<String,Object>)Json.parse(f.get("variants"),"mutate recorded variant");for(Object v:variants.values())((Map<String,Object>)v).put("gap",42);f.put("variants",SolverRelations.json(variants));f.put("verdict","PASS");changed.set(i,new SolverRelationsAnalysis.Observed(f));}var analysis=SolverRelationsAnalysis.analyse(changed);assertEquals(73,((Map<String,Integer>)analysis.get("outcomes")).get("PASS"));assertTrue(((List<Long>)((Map<String,Object>)analysis.get("greedy_gaps")).get("disjoint")).contains(42L));var witness=((List<Map<String,Object>>)analysis.get("witnesses")).get(2);assertEquals("PASS",witness.get("verdict"));assertEquals(42L,((Map<String,Object>)((Map<String,Object>)witness.get("variants")).get("base")).get("gap"));
    }
}
