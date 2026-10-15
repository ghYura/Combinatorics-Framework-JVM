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

class ComposedCarriersTest {
    private Element golden() throws Exception {var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d13b-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private List<ComposedCarriersAnalysis.Observed> observations() {var rows=new ArrayList<ComposedCarriersAnalysis.Observed>();for(String p:ComposedCarriers.POLICIES)for(String c:ComposedCarriers.CARRIERS)for(String o:ComposedCarriers.ORDERS)for(String e:ComposedCarriers.ENCODINGS)for(String m:ComposedCarriers.MARKERS)rows.add(new ComposedCarriersAnalysis.Observed(ComposedCarriers.attributes(ComposedCarriers.evaluate(new ComposedCarriers.Config(p,c,o,e,m)))));return rows;}
    @Test void allOneHundredEightOriginalTreesTracesDecodedContentActionsAndEffectsMatchEveryFrozenField() throws Exception {
        var bank=new TreeMap<String,Map<String,String>>();for(var row:observations())assertNull(bank.put(row.id(),row.fields()));assertEquals(108,bank.size());var cases=golden().getElementsByTagName("case");assertEquals(108,cases.getLength());
        for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=bank.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(23,fields.getLength());assertEquals(fields.getLength(),actual.size());for(int k=0;k<fields.getLength();k++){var f=(Element)fields.item(k);String key=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key);}}
        assertEquals(80,bank.values().stream().filter(f -> f.get("verdict").equals("PASS")).count());
    }
    @SuppressWarnings("unchecked") private Map<String,Object> mutable(Map<String,Object> tree){return (Map<String,Object>)Json.parse(ComposedCarriers.json(tree),"mutable test tree");}
    @SuppressWarnings("unchecked") @Test void independentOracleRejectsLostRelabelledDecodedContentMissingTrustedWorkAndForbiddenEffectsWhileSetupIsStrict() {
        var c=new ComposedCarriers.Config("preserve_origin","retrieved_page","MF","base64","emit_canary");var tree=ComposedCarriers.tree(c);var actual=ComposedCarriers.process(c.policy(),tree);assertEquals(new ComposedCarriersOracle.Checks(true,true,true),ComposedCarriersOracle.judge(tree,c.carrier(),c.order(),c.encoding(),c.marker(),actual));
        var lost=actual.decodedLeaves().stream().filter(x -> !x.get("id").equals("marker")).toList();var lossy=new ComposedCarriers.Observation(actual.trace(),lost,String.join("\n",lost.stream().map(x -> (String)x.get("text")).toList()),actual.actions(),actual.outbox(),true,false);assertFalse(ComposedCarriersOracle.judge(tree,c.carrier(),c.order(),c.encoding(),c.marker(),lossy).contentOk());
        var denied=new ComposedCarriers.Observation(actual.trace(),actual.decodedLeaves(),actual.rendered(),List.of(),List.of(),false,false);assertEquals(new ComposedCarriersOracle.Checks(true,false,false),ComposedCarriersOracle.judge(tree,c.carrier(),c.order(),c.encoding(),c.marker(),denied));
        var forbidden=new ComposedCarriers.Observation(actual.trace(),actual.decodedLeaves(),actual.rendered(),actual.actions(),List.of(ComposedCarriers.CANARY),true,true);assertFalse(ComposedCarriersOracle.judge(tree,c.carrier(),c.order(),c.encoding(),c.marker(),forbidden).effectsOk());
        var swapped=actual.decodedLeaves().stream().map(x -> x.get("id").equals("marker")?Map.<String,Object>of("id","marker","text",ComposedCarriers.CANARY+"|SET_FLAG"):x).toList();var altered=new ComposedCarriers.Observation(actual.trace(),swapped,String.join("\n",swapped.stream().map(x -> (String)x.get("text")).toList()),actual.actions(),List.of(),true,false);assertFalse(ComposedCarriersOracle.judge(tree,c.carrier(),c.order(),c.encoding(),c.marker(),altered).contentOk());
        var relabelled=mutable(tree);var children=(List<Map<String,Object>>)relabelled.get("children");((List<Map<String,Object>>)children.get(1).get("children")).getFirst().put("origin","trusted");assertFalse(ComposedCarriersOracle.judge(relabelled,c.carrier(),c.order(),c.encoding(),c.marker(),actual).contentOk());assertFalse(ComposedCarriersOracle.judge(tree,"tool_result",c.order(),c.encoding(),c.marker(),actual).contentOk());assertFalse(ComposedCarriersOracle.judge(tree,c.carrier(),"FM",c.encoding(),c.marker(),actual).contentOk());
        var exact=mutable(tree);((List<Map<String,Object>>)exact.get("children")).get(1).put("origin","trusted");((List<Map<String,Object>>)exact.get("children")).get(1).put("kind","tool_result");((List<Map<String,Object>>)((List<Map<String,Object>>)exact.get("children")).get(1).get("children")).getFirst().put("text","MARK_READY ");((List<Map<String,Object>>)((List<Map<String,Object>>)exact.get("children")).get(1).get("children")).getFirst().put("encoding","plain");assertEquals(1,ComposedCarriers.process("wrapper_trust",exact).actions().size());
        for(String carrier:ComposedCarriers.CARRIERS)for(String order:ComposedCarriers.ORDERS){String root=ComposedCarriers.rootText(carrier,order);assertEquals(carrier,ComposedCarriers.decodeRoot(root).carrier());assertEquals(order,ComposedCarriers.decodeRoot(root).order());assertThrows(IllegalArgumentException.class,() -> ComposedCarriers.decodeRoot(root.replace("filler","marker")));assertThrows(IllegalArgumentException.class,() -> ComposedCarriers.decodeRoot(root.replace("trusted_task()","trusted_note()")));assertThrows(IllegalArgumentException.class,() -> ComposedCarriers.decodeRoot(root+"finish()"));}
        assertThrows(IllegalArgumentException.class,() -> ComposedCarriersPlugin.parseAtoms("impl(\"preserve_origin\")","encoding(\"plain\")\n","marker(\"reference\")\n",ComposedCarriers.rootText("tool_result","MF")));assertThrows(IllegalArgumentException.class,() -> ComposedCarriers.decode("42","json"));for(String b:List.of("%%%%","YQ","YQ="," YQ==","/w=="))assertThrows(IllegalArgumentException.class,() -> ComposedCarriers.decode(b,"base64"));assertThrows(UnsupportedOperationException.class,() -> tree.put("origin","untrusted"));assertEquals(1,ComposedCarriers.evaluate(c).observation().actions().size());
    }
    @SuppressWarnings("unchecked") @Test void allOriginalFiveAxesThirtyCrossGroupsEffectsBenignPromotionsAndFiveWitnessesUseRecordedValuesWithoutRepair() throws Exception {
        var rows=observations();var expected=Json.parse(golden().getElementsByTagName("analysis").item(0).getTextContent(),"golden");assertEquals(expected,Json.parse(ComposedCarriers.json(ComposedCarriersAnalysis.analyse(rows)),"actual"));
        assertThrows(IllegalArgumentException.class,() -> ComposedCarriersAnalysis.analyse(rows.subList(1,rows.size())));var doubled=new ArrayList<>(rows);doubled.add(rows.getFirst());assertThrows(IllegalArgumentException.class,() -> ComposedCarriersAnalysis.analyse(doubled));
        var changed=new ArrayList<>(rows);int index=java.util.stream.IntStream.range(0,rows.size()).filter(i -> rows.get(i).id().equals(ComposedCarriersAnalysis.witnesses().get("benign_marker_pass"))).findFirst().orElseThrow();var f=new TreeMap<>(rows.get(index).fields());f.put("verdict","DOMAIN_FAIL");f.put("actions",ComposedCarriers.json(List.of(Map.of("name","MARK_READY","leaf","task"),Map.of("name","SET_FLAG","leaf","marker"))));f.put("flag","true");f.put("rendered","recorded mutation");changed.set(index,new ComposedCarriersAnalysis.Observed(f));var analysis=ComposedCarriersAnalysis.analyse(changed);assertEquals(13,((List<?>)analysis.get("benign_promotions")).size());assertEquals(false,analysis.get("order_neutral"));assertNotEquals(expected,Json.parse(ComposedCarriers.json(analysis),"changed"));var witness=((List<Map<String,Object>>)analysis.get("witnesses")).getLast();assertEquals("forbidden_flag",witness.get("effect"));assertEquals("recorded mutation",witness.get("rendered"));assertEquals(true,witness.get("flag"));
        for(var patch:List.of(Map.of("verdict","BROKEN"),Map.of("case_id","unknown"),Map.of("order","FM"))){var bad=new ArrayList<>(rows);var fields=new TreeMap<>(rows.getFirst().fields());fields.putAll(patch);bad.set(0,new ComposedCarriersAnalysis.Observed(fields));assertThrows(IllegalArgumentException.class,() -> ComposedCarriersAnalysis.analyse(bad));}
    }
}
