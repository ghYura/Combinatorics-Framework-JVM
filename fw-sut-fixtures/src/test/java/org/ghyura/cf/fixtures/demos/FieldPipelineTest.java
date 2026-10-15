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
class FieldPipelineTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d5a-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private Object parsed(String value){return Json.parse(value,"test");}
    @Test void allCasesMatchIndependentRecordsAndTraces()throws Exception{
        var g=golden();var cases=g.getElementsByTagName("case");assertEquals(72,cases.getLength());var configs=FieldPipelineAnalysis.configurations().stream().collect(java.util.stream.Collectors.toMap(FieldPipeline.Config::caseId,c->c));int pass=0;for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var result=FieldPipeline.evaluate(configs.get(c.getAttribute("id")));var actual=FieldPipeline.attributes(result);assertEquals(16,actual.size());var fields=c.getElementsByTagName("field");for(int k=0;k<fields.getLength();k++){var f=(Element)fields.item(k);String key=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?parsed(f.getTextContent()):f.getTextContent(),f.getAttribute("kind").equals("json")?parsed(actual.get(key)):actual.get(key),c.getAttribute("id")+" "+key);}if(result.passed())pass++;}assertEquals(32,pass);
    }
    @Test void policyBlindExactRecordComparisonKeepsDiagnosticTraceDifferences(){
        var tree=new FieldPipeline.Tree("x",List.of("A","M"));var input=new TreeMap<String,Object>(Map.of("x",2,"y",5,"z",99));var ref=FieldPipelineOracle.evaluate(input,tree);assertEquals(Map.of("x",6,"y",5,"z",99),ref.record());assertEquals(Map.of("x",5,"y",5,"z",99),FieldPipeline.adapt(input,tree,"reverse_pair").record());assertEquals(Map.of("x",2,"y",12,"z",99),FieldPipeline.adapt(input,tree,"wrong_field").record());assertEquals(Map.of("x",2,"y",5,"z",99),input);input.put("x",10);assertEquals(6,ref.record().get("x"));assertThrows(UnsupportedOperationException.class,()->ref.trace().getFirst().put("value",9));
        var benign=FieldPipeline.evaluate(new FieldPipeline.Config("reverse_pair",new FieldPipeline.Tree("x",List.of("A","S"))));assertTrue(benign.passed());assertEquals(false,benign.document().get("traces_equal"));assertTrue(FieldPipelineOracle.sameRecord(Map.of("x",1,"y",2L),Map.of("x",1L,"y",2)));for(var bad:List.of(Map.of("x",true,"y",2),Map.of("x",1.0,"y",2),Map.of("x",1,"y",2,"z",0),Map.of("x",1)))assertFalse(FieldPipelineOracle.sameRecord(bad,Map.of("x",1,"y",2)));assertFalse(FieldPipelineOracle.sameRecord(List.of(1,2),Map.of("x",1,"y",2)));assertThrows(ArithmeticException.class,()->FieldPipeline.adapt(Map.of("x",Integer.MAX_VALUE,"y",5),tree,"correct"));assertThrows(ArithmeticException.class,()->FieldPipelineOracle.evaluate(Map.of("x",Integer.MAX_VALUE,"y",5),tree));
        var good=List.of("push_op(\"A\")\n","push_op(\"M\")\n","bind(\"x\")\n");assertEquals(tree,FieldPipelinePlugin.parseAtoms("impl(\"correct\")\n",good).tree());for(var bad:List.of(good.subList(0,2),List.of(good.get(2),good.get(0),good.get(1)),List.of(good.get(0),good.get(0),good.get(2)),List.of(good.get(0).strip(),good.get(1),good.get(2)),List.of(good.get(0),good.get(1),"bind(\"z\")\n")))assertThrows(IllegalArgumentException.class,()->FieldPipelinePlugin.parseAtoms("impl(\"correct\")\n",bad));assertThrows(IllegalArgumentException.class,()->FieldPipelinePlugin.parseAtoms("impl(\"unknown\")\n",good));
    }
    @Test void recordedAnalysisRequiresCompleteSupportWithoutRepair()throws Exception{
        var rows=new ArrayList<>(FieldPipelineAnalysis.configurations().stream().map(FieldPipeline::evaluate).map(FieldPipeline::attributes).map(FieldPipelineAnalysis.Observed::new).toList());var summary=FieldPipelineAnalysis.analyse(rows);assertEquals(parsed(golden().getElementsByTagName("analysis").item(0).getTextContent()),parsed(FieldPipeline.json(summary)));assertThrows(IllegalArgumentException.class,()->FieldPipelineAnalysis.analyse(rows.subList(1,rows.size())));var duplicate=new ArrayList<>(rows);duplicate.add(rows.getFirst());assertThrows(IllegalArgumentException.class,()->FieldPipelineAnalysis.analyse(duplicate));
        int idx=java.util.stream.IntStream.range(0,rows.size()).filter(i->rows.get(i).id().equals("A|reverse_pair|OPS=AM|FIELD=x")).findFirst().orElseThrow();var changed=new TreeMap<>(rows.get(idx).fields());changed.put("verdict","PASS");changed.put("observed","{\"x\":777,\"y\":5}");rows.set(idx,new FieldPipelineAnalysis.Observed(changed));var revised=FieldPipelineAnalysis.analyse(rows);assertEquals(parsed("{\"PASS\":33,\"DOMAIN_FAIL\":39}"),parsed(FieldPipeline.json(revised.get("totals"))));assertTrue(FieldPipeline.json(revised.get("witnesses")).contains("777"));changed.put("policy","correct");rows.set(idx,new FieldPipelineAnalysis.Observed(changed));assertThrows(IllegalArgumentException.class,()->FieldPipelineAnalysis.analyse(rows));changed.put("policy","reverse_pair");changed.put("verdict","BROKEN");rows.set(idx,new FieldPipelineAnalysis.Observed(changed));assertThrows(IllegalArgumentException.class,()->FieldPipelineAnalysis.analyse(rows));
    }
}
