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

class LanguageProcessorsTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d14a-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private List<LanguageProcessorsAnalysis.Observed> observations(){return LanguageProcessorsAnalysis.configurations().stream().map(c -> new LanguageProcessorsAnalysis.Observed(LanguageProcessors.attributes(LanguageProcessors.evaluate(c)))).toList();}
    @SuppressWarnings("unchecked") @Test void allFourHundredThirtyTwoCasesAndEveryProgramBytecodeAndVmTransitionMatchIndependentFrozenPredictions()throws Exception{
        var bank=new TreeMap<String,Map<String,String>>();for(var row:observations())assertNull(bank.put(row.id(),row.fields()));assertEquals(432,bank.size());var cases=golden().getElementsByTagName("case");assertEquals(432,cases.getLength());int steps=0;for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=bank.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(16,fields.getLength());assertEquals(16,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}var variants=(Map<String,Map<String,Object>>)Json.parse(actual.get("observations"),"actual");assertEquals(2,variants.size());for(var v:variants.values()){assertEquals(5,v.size());steps+=((List<?>)v.get("vm_trace")).size();for(Object step:(List<?>)v.get("vm_trace"))assertEquals(5,((Map<?,?>)step).size());}}
        assertEquals(9504,steps);assertEquals(316,bank.values().stream().filter(f -> f.get("verdict").equals("PASS")).count());assertEquals(116,bank.values().stream().filter(f -> f.get("verdict").equals("DOMAIN_FAIL")).count());
    }
    @SuppressWarnings("unchecked") @Test void malformedProgramsBytecodeAndReferenceSetupRefuseBeforeSensorsWhileEqualWrongNumbersFailAndDiagnosticsStayNoncausal(){
        var c=new LanguageProcessors.Config("faithful","XY","L",(byte)-1,(byte)2,"+","*");var p=LanguageProcessors.programs(c).get("original");assertEquals(-1,LanguageProcessorsOracle.interpret(p));assertEquals(-1,LanguageProcessors.execute(LanguageProcessors.compile(p,"faithful")).value());
        for(var bad:List.of(Map.<String,Object>of("bindings","bad","expr",Map.of("var","x")),Map.<String,Object>of("bindings",List.of(Map.of("name","x","value",true)),"expr",Map.of("var","x")),Map.<String,Object>of("bindings",List.of(Map.of("name",3,"value",2)),"expr",Map.of("var","x")),Map.<String,Object>of("bindings",List.of(),"expr",Map.of("var","x","extra",1)),Map.<String,Object>of("bindings",List.of(),"expr",Map.of("op","/","left",Map.of("var","x"),"right",Map.of("var","x"))),Map.<String,Object>of("bindings",List.of(),"expr",Map.of("value",1)))){assertThrows(IllegalArgumentException.class,() -> LanguageProcessorsOracle.interpret(bad));assertThrows(IllegalArgumentException.class,() -> LanguageProcessors.compile(bad,"faithful"));}
        assertThrows(IllegalArgumentException.class,() -> LanguageProcessorsOracle.interpret(Map.of("bindings",List.of(),"expr",Map.of("var","missing"))));assertThrows(IllegalArgumentException.class,() -> LanguageProcessors.compile(p,"unknown"));assertEquals(2,LanguageProcessorsOracle.interpret(Map.of("bindings",List.of(Map.of("name","x","value",1),Map.of("name","x","value",2)),"expr",Map.of("var","x"))));
        for(List<?> code:List.<List<?>>of(List.of(),List.of(List.of("PUSH",1)),List.of(List.of(),List.of("RETURN")),List.of("PUSH",List.of("RETURN")),List.of(List.of("BOGUS"),List.of("RETURN")),List.of(List.of("PUSH",true),List.of("RETURN")),List.of(List.of("PUSH",1.0),List.of("RETURN")),List.of(List.of("PUSH",1,2),List.of("RETURN")),List.of(List.of("STORE","x"),List.of("RETURN")),List.of(List.of("LOAD","x"),List.of("RETURN")),List.of(List.of("LOAD",1),List.of("RETURN")),List.of(List.of("ADD"),List.of("RETURN")),List.of(List.of("PUSH",1),List.of("SUB"),List.of("RETURN")),List.of(List.of("RETURN")),List.of(List.of("PUSH",1),List.of("PUSH",2),List.of("RETURN")),List.of(List.of("PUSH",1),List.of("RETURN"),List.of("RETURN")),List.of(List.of("PUSH",1),List.of("ADD",2),List.of("RETURN"))))assertThrows(IllegalArgumentException.class,() -> LanguageProcessors.execute(code));
        assertThrows(ArithmeticException.class,() -> LanguageProcessors.execute(List.of(List.of("PUSH",Integer.MAX_VALUE),List.of("PUSH",1),List.of("ADD"),List.of("RETURN"))));var execution=LanguageProcessors.execute(LanguageProcessors.compile(p,"faithful"));assertNull(execution.trace().getFirst().get("returned"));assertEquals(-1,execution.trace().getLast().get("returned"));assertEquals(List.of( -1 ),execution.trace().getFirst().get("stack"));assertEquals(Map.of(),execution.trace().getFirst().get("locals"));assertThrows(UnsupportedOperationException.class,() -> ((List<Object>)execution.trace().getFirst().get("stack")).add(9));assertThrows(UnsupportedOperationException.class,() -> execution.trace().getFirst().put("returned",99));assertThrows(IllegalArgumentException.class,() -> LanguageProcessors.execute(List.of(List.of("LOAD","x"),List.of("RETURN"))));
        var wrong=Map.<String,Object>of("original",Map.of("reference_value",1,"vm_value",2),"transformed",Map.of("reference_value",1,"vm_value",2));assertEquals(Map.of("original_differential",false,"transformed_differential",false,"metamorphic",true),LanguageProcessorsOracle.judge(wrong));assertFalse(new LanguageProcessors.Result(c,wrong,LanguageProcessorsOracle.judge(wrong)).passed());for(Object n:List.of(true,1.0,"1",2147483648L)){var bad=Map.<String,Object>of("original",Map.of("reference_value",n,"vm_value",1),"transformed",Map.of("reference_value",1,"vm_value",1));assertThrows(IllegalArgumentException.class,() -> LanguageProcessorsOracle.judge(bad));}assertThrows(IllegalArgumentException.class,() -> LanguageProcessorsOracle.judge(Map.of("original",Map.of("reference_value",1,"vm_value",1),"transformed",Map.of("reference_value",2,"vm_value",2))));assertThrows(IllegalArgumentException.class,() -> LanguageProcessorsOracle.judge(Map.of("original",Map.of("reference_value",1),"transformed",Map.of("reference_value",1,"vm_value",1))));var diagnostic=Map.<String,Object>of("original",Map.of("reference_value",1,"vm_value",1,"bytecode","corrupted","vm_trace","changed"),"transformed",Map.of("reference_value",1,"vm_value",1));assertTrue(LanguageProcessorsOracle.judge(diagnostic).values().stream().allMatch(Boolean.TRUE::equals));
        for(var atoms:List.of(List.of("declare(\"x\");","declare(\"x\");"),List.of("declare(\"x\");"),List.of("declare(\"x\");","declare(\"z\");"),List.of("declare(\"x\");\n","declare(\"y\");")))assertThrows(IllegalArgumentException.class,() -> LanguageProcessorsPlugin.declarationOrder(atoms));assertEquals("YX",LanguageProcessorsPlugin.declarationOrder(List.of("declare(\"y\");","declare(\"x\");")));
    }
    @SuppressWarnings("unchecked") @Test void fullOriginalTalliesDeclarationControlsAndFiveWitnessesReadRecordedValuesWithoutRecompilingOrRepair()throws Exception{
        var rows=observations();var g=golden();for(var pair:List.of(Map.entry("analysis",LanguageProcessorsAnalysis.analyse(rows)),Map.entry("controls",LanguageProcessorsAnalysis.controls(rows)),Map.entry("proofs",LanguageProcessorsAnalysis.proofs(rows))))assertEquals(Json.parse(g.getElementsByTagName(pair.getKey()).item(0).getTextContent(),"golden"),Json.parse(LanguageProcessors.json(pair.getValue()),"actual"));
        var missing=new ArrayList<>(rows);missing.removeLast();assertThrows(IllegalArgumentException.class,() -> LanguageProcessorsAnalysis.analyse(missing));var dup=new ArrayList<>(rows);dup.add(rows.getFirst());assertThrows(IllegalArgumentException.class,() -> LanguageProcessorsAnalysis.analyse(dup));for(var patch:List.of(Map.of("verdict","BROKEN"),Map.of("case_id","unknown"),Map.of("order","YX"),Map.of("x","2.0"))){var bad=new ArrayList<>(rows);var f=new TreeMap<>(rows.getFirst().fields());f.putAll(patch);bad.set(0,new LanguageProcessorsAnalysis.Observed(f));assertThrows(IllegalArgumentException.class,() -> LanguageProcessorsAnalysis.analyse(bad));}
        String id=LanguageProcessorsAnalysis.witnesses().get("reverse_sub_equal_but_wrong");var changed=new ArrayList<>(rows);for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(id)){var f=new TreeMap<>(rows.get(i).fields());var variants=(Map<String,Object>)Json.parse(f.get("observations"),"mutate retained diagnostics");((Map<String,Object>)variants.get("original")).put("bytecode",List.of(List.of("DIAGNOSTIC_CHANGE")));f.put("observations",LanguageProcessors.json(variants));f.put("verdict","PASS");changed.set(i,new LanguageProcessorsAnalysis.Observed(f));}var analysis=LanguageProcessorsAnalysis.analyse(changed);assertEquals(317,((Map<String,Integer>)analysis.get("outcomes")).get("PASS"));assertEquals(64,analysis.get("wrong_agreements"));var witness=((List<Map<String,Object>>)analysis.get("witnesses")).getFirst();assertEquals("PASS",witness.get("verdict"));assertEquals(List.of(List.of("DIAGNOSTIC_CHANGE")),((Map<String,Object>)witness.get("original")).get("bytecode"));assertNotEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(LanguageProcessors.json(analysis),"changed"));
    }
}
