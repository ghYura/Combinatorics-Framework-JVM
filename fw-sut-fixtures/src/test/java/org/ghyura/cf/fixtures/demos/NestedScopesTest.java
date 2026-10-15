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
class NestedScopesTest {
    private Element golden()throws Exception { var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d5b-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();} }
    private Object parsed(String value) { return Json.parse(value,"golden"); }
    private Map<String,String> fields(Element c) { var out=new TreeMap<String,String>();var nodes=c.getElementsByTagName("field");for(int i=0;i<nodes.getLength();i++){var f=(Element)nodes.item(i);out.put(f.getAttribute("name"),f.getTextContent());}return out; }
    private List<String> atoms(Object fragments) { return ((List<?>)fragments).stream().map(x->{var a=(List<?>)x;return a.get(0)+"("+(a.size()==1?"":"\""+a.get(1)+"\"")+");\n";}).toList(); }
    private NestedScopes.Result actual(Element c) { var f=fields(c);return NestedScopes.evaluate(NestedScopesPlugin.parseAtoms(c.getAttribute("phase"),"impl(\""+f.get("policy")+"\");\n",atoms(parsed(f.get("fragments"))))); }
    private List<NestedScopesAnalysis.Observed> rows(String phase)throws Exception { var out=new ArrayList<NestedScopesAnalysis.Observed>();var cases=golden().getElementsByTagName("case");for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);if(c.getAttribute("phase").equals(phase))out.add(new NestedScopesAnalysis.Observed(NestedScopes.attributes(actual(c))));}return out; }
    @Test void allRecordsTreesFragmentsAndTracesMatchIndependentModels()throws Exception { var cases=golden().getElementsByTagName("case");assertEquals(27,cases.getLength());int pass=0,trees=0;for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var result=actual(c);var actual=NestedScopes.attributes(result);assertEquals(c.getAttribute("phase").equals("B1")?10:12,actual.size());var nodes=c.getElementsByTagName("field");for(int k=0;k<nodes.getLength();k++){var f=(Element)nodes.item(k);String key=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?parsed(f.getTextContent()):f.getTextContent(),f.getAttribute("kind").equals("json")?parsed(actual.get(key)):actual.get(key),c.getAttribute("id")+" "+key);}if(result.passed())pass++;trees+=result.config().trees().size();}assertEquals(9,pass);assertEquals(48,trees); }
    @Test void independentStackRestoresEnclosingFieldsAndEveryExecutionStartsFresh() {
        var tree=NestedScopes.fromDocument(parsed("[{\"scope\":\"x\",\"children\":[{\"scope\":\"y\",\"children\":[\"A\",\"N\"]}]},\"S\",{\"scope\":\"x\",\"children\":[\"A\"]},\"N\"]"));assertEquals(Map.of("x",0,"y",-6),NestedScopes.evaluate(tree,"correct").record());assertEquals(Map.of("x",5,"y",5),NestedScopes.evaluate(tree,"flatten_scope").record());assertEquals(Map.of("x",-3,"y",-9),NestedScopes.evaluate(tree,"leak_scope").record());assertEquals(NestedScopes.evaluate(tree,"correct"),NestedScopesOracle.evaluate(tree));for(String p:NestedScopes.POLICIES)assertEquals(NestedScopes.evaluate(tree,p),NestedScopes.evaluate(tree,p));var another=List.<NestedScopes.Node>of(new NestedScopes.Scope("y",List.of(new NestedScopes.Op("A"),new NestedScopes.Scope("x",List.of(new NestedScopes.Op("M"))),new NestedScopes.Op("S"))),new NestedScopes.Op("N"));assertEquals(Map.of("x",-4,"y",3),NestedScopesOracle.evaluate(another).record());assertEquals(Map.of("x",-4,"y",3),NestedScopes.evaluate(another,"correct").record());assertEquals(Map.of("x",-3,"y",5),NestedScopes.evaluate(another,"flatten_scope").record());assertEquals(Map.of("x",-1,"y",6),NestedScopes.evaluate(another,"leak_scope").record());assertFalse(NestedScopesOracle.sameRecord(Map.of("x",true,"y",2),Map.of("x",1,"y",2)));assertFalse(NestedScopesOracle.sameRecord(Map.of("x",1.0,"y",2),Map.of("x",1,"y",2)));assertFalse(NestedScopesOracle.sameRecord(Map.of("x",1,"y",2,"z",3),Map.of("x",1,"y",2)));var fold=NestedScopesOracle.evaluate(tree);assertThrows(UnsupportedOperationException.class,()->fold.record().put("x",999));assertThrows(UnsupportedOperationException.class,()->fold.trace().getFirst().put("value",999));var overflow=java.util.Collections.<NestedScopes.Node>nCopies(32,new NestedScopes.Op("M"));assertThrows(ArithmeticException.class,()->NestedScopes.evaluate(overflow,"correct"));assertThrows(ArithmeticException.class,()->NestedScopesOracle.evaluate(overflow)); }
    @Test void builderRejectsOutOfOrderUnclosedPoisonAndDuplicateBundleFragments()throws Exception {
        var c=(Element)golden().getElementsByTagName("case").item(0);var good=atoms(parsed(fields(c).get("fragments")));for(var bad:List.of(List.of("op(\"A\");\n"),List.of("begin_pipeline();\n","close_scope();\n"),List.of("begin_pipeline();\n","open_scope(\"x\");\n","end_pipeline();\n"),List.of("begin_pipeline();\n","begin_pipeline();\n"),List.of("begin_pipeline();\n","poison_tmp();\n"),List.of("begin_pipeline();\n","op(\"Q\");\n"),List.of("begin_pipeline();\n","open_scope(\"z\");\n"),List.of("begin_bundle();\n","seal_bundle();\n"),List.of("begin_bundle();\n","end_bundle();\n"),List.of("begin_pipeline();\n","end_pipeline();\n","begin_pipeline();\n"),List.of("begin_pipeline();\n","op(\"A\");\n","end_pipeline();\n"),List.of("begin_pipeline(\"x\");\n"),List.of(good.getFirst().strip()),good.subList(0,good.size()-1)))assertThrows(IllegalArgumentException.class,()->NestedScopesPlugin.parseAtoms("B1","impl(\"correct\");\n",bad));var duplicates=new ArrayList<String>();duplicates.add("begin_bundle();\n");for(int i=0;i<8;i++)duplicates.addAll(good);duplicates.add("seal_bundle();\n");duplicates.add("end_bundle();\n");assertThrows(IllegalArgumentException.class,()->NestedScopesPlugin.parseAtoms("B2","impl(\"correct\");\n",duplicates));assertThrows(IllegalArgumentException.class,()->NestedScopesPlugin.parseAtoms("B1","impl(\"correct\")\n",good));assertThrows(IllegalArgumentException.class,()->NestedScopes.fromDocument(List.of(Map.of("scope","z","children",List.of("A"))))); }
    @Test void recordedSupportSummariesAndWitnessesAreReadWithoutRejudging()throws Exception {
        var g=golden();for(String phase:List.of("B1","B2")){var rows=new ArrayList<>(rows(phase));var predicted=g.getElementsByTagName("analyse");for(int i=0;i<predicted.getLength();i++)if(((Element)predicted.item(i)).getAttribute("phase").equals(phase))assertEquals(parsed(predicted.item(i).getTextContent()),parsed(NestedScopes.json(NestedScopesAnalysis.analyse(phase,rows))));assertThrows(IllegalArgumentException.class,()->NestedScopesAnalysis.analyse(phase,rows.subList(1,rows.size())));var duplicate=new ArrayList<>(rows);duplicate.add(rows.getFirst());assertThrows(IllegalArgumentException.class,()->NestedScopesAnalysis.analyse(phase,duplicate));int idx=java.util.stream.IntStream.range(0,rows.size()).filter(i->rows.get(i).fields().get("policy").equals(phase.equals("B1")?"flatten_scope":"correct")&&rows.get(i).id().endsWith(phase.equals("B1")?"ZIP=A|CAT=A|TAIL=N":"BUNDLE=all8")).findFirst().orElseThrow();var changed=new TreeMap<>(rows.get(idx).fields());changed.put("verdict",phase.equals("B1")?"PASS":"DOMAIN_FAIL");if(phase.equals("B1")){var result=new TreeMap<>(NestedScopesAnalysis.object(parsed(changed.get("result"))));result.put("observed",Map.of("x",777,"y",888));changed.put("result",NestedScopes.json(result));}else changed.put("failing_trees",NestedScopes.json(List.of("ZIP=A|CAT=A|TAIL=N")));rows.set(idx,new NestedScopesAnalysis.Observed(changed));var analysis=NestedScopesAnalysis.analyse(phase,rows);assertEquals(parsed(phase.equals("B1")?"{\"PASS\":9,\"DOMAIN_FAIL\":15}":"{\"DOMAIN_FAIL\":3}"),parsed(NestedScopes.json(analysis.get("totals"))));assertTrue(NestedScopes.json(analysis.get("witnesses")).contains(phase.equals("B1")?"777":"ZIP=A|CAT=A|TAIL=N"));changed.put("policy","leak_scope");rows.set(idx,new NestedScopesAnalysis.Observed(changed));assertThrows(IllegalArgumentException.class,()->NestedScopesAnalysis.analyse(phase,rows));}
    }
}
