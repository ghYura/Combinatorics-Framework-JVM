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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class SqlPartitioningTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("d14b-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private List<SqlPartitioningAnalysis.Observed> observations(){return SqlPartitioningAnalysis.configurations().stream().map(c -> new SqlPartitioningAnalysis.Observed(SqlPartitioning.attributes(SqlPartitioning.evaluate(c)))).toList();}
    @Test void allSeventyTwoCasesEveryQueryRowBagSqlAndFaultyRecombinationMatchIndependentPredictions()throws Exception{
        var bank=new TreeMap<String,Map<String,String>>();for(var row:observations())assertNull(bank.put(row.id(),row.fields()));assertEquals(72,bank.size());var cases=golden().getElementsByTagName("case");assertEquals(72,cases.getLength());for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=bank.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(17,fields.getLength());assertEquals(17,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}}assertEquals(36,bank.values().stream().filter(f -> f.get("verdict").equals("PASS")).count());
    }
    @Test void malformedRowsRefuseAndFourUsefulQueryChecksPreventVacuousOrWrongBagsEvenWhenSetsAgree(){
        var rows=SqlPartitioning.queryRows("scan","flag");var omitted=SqlPartitioningOracle.judge("scan","flag",rows,SqlPartitioning.recombine("omit_unknown",rows));assertEquals(true,omitted.get("set_equal"));assertEquals(false,omitted.get("tlp_ok"));assertEquals("DOMAIN_FAIL",omitted.get("verdict"));var denied=Map.<String,List<?>>of("base",List.of(),"true",List.of(),"false",List.of(),"unknown",List.of());var judged=SqlPartitioningOracle.judge("scan","flag",denied,List.of());assertEquals(true,judged.get("tlp_ok"));assertEquals("DOMAIN_FAIL",judged.get("verdict"));var wrong=new TreeMap<String,List<?>>(rows);wrong.put("true",List.of(List.of(99)));assertEquals("DOMAIN_FAIL",SqlPartitioningOracle.judge("scan","flag",wrong,rows.get("base")).get("verdict"));
        for(List<?> bad:List.<List<?>>of(List.of(List.of()),List.of(List.of(1,2)),List.of(List.of(1.0)),List.of(List.of(true)),List.of(List.of("1")),List.of(1),List.of(List.of(2147483648L))))assertThrows(IllegalArgumentException.class,() -> SqlPartitioningOracle.bag(bad));var missing=new TreeMap<String,List<?>>(rows);missing.remove("unknown");assertThrows(IllegalArgumentException.class,() -> SqlPartitioningOracle.judge("scan","flag",missing,List.of()));assertThrows(IllegalArgumentException.class,() -> SqlPartitioning.queryRows("unknown","flag"));assertThrows(IllegalArgumentException.class,() -> SqlPartitioning.recombine("unknown",rows));assertThrows(IllegalArgumentException.class,() -> SqlPartitioningPlugin.parseAtoms("impl(\"union_all\");\n","query(\"scan\");","predicate(\"flag\");"));
        var nul=Collections.singletonList((Integer)null);assertEquals(List.of(Map.of("row",nul,"count",2),Map.of("row",List.of(1),"count",2)),SqlPartitioningOracle.bag(List.of(List.of(1),nul,List.of(1),nul)));assertThrows(UnsupportedOperationException.class,() -> rows.get("base").add(List.of((byte)3)));assertThrows(UnsupportedOperationException.class,() -> rows.get("base").getFirst().set(0,(byte)3));assertEquals("U",SqlPartitioningOracle.not("U"));assertEquals("F",SqlPartitioningOracle.and("F","U"));assertEquals("T",SqlPartitioningOracle.or("T","U"));
    }
    @SuppressWarnings("unchecked") @Test void completeRecordedStrataHiddenSetFailuresControlsAndFiveWitnessesMatchWithoutQueryingOrRepair()throws Exception{
        var rows=observations();var g=golden();for(var pair:List.of(Map.entry("analysis",SqlPartitioningAnalysis.analyse(rows)),Map.entry("kleene",SqlPartitioningAnalysis.kleene()),Map.entry("truth",SqlPartitioningAnalysis.truth()),Map.entry("fixture",SqlPartitioningAnalysis.fixture())))assertEquals(Json.parse(g.getElementsByTagName(pair.getKey()).item(0).getTextContent(),"golden"),Json.parse(SqlPartitioning.json(pair.getValue()),"actual"),pair.getKey());
        var missing=new ArrayList<>(rows);missing.removeLast();assertThrows(IllegalArgumentException.class,() -> SqlPartitioningAnalysis.analyse(missing));var duplicate=new ArrayList<>(rows);duplicate.add(rows.getFirst());assertThrows(IllegalArgumentException.class,() -> SqlPartitioningAnalysis.analyse(duplicate));for(var patch:List.of(Map.of("verdict","BROKEN"),Map.of("id","unknown"),Map.of("query","filtered"))){var bad=new ArrayList<>(rows);var f=new TreeMap<>(rows.getFirst().fields());f.putAll(patch);bad.set(0,new SqlPartitioningAnalysis.Observed(f));assertThrows(IllegalArgumentException.class,() -> SqlPartitioningAnalysis.analyse(bad));}
        var changed=new ArrayList<>(rows);String id=SqlPartitioningAnalysis.witnesses().get("omit_unknown_sets_agree_bags_differ");for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(id)){var f=new TreeMap<>(rows.get(i).fields());f.put("verdict","PASS");f.put("combined_bag","[{\"row\":[99],\"count\":42}]");changed.set(i,new SqlPartitioningAnalysis.Observed(f));}var a=SqlPartitioningAnalysis.analyse(changed);assertEquals(37,((Map<String,Integer>)a.get("totals")).get("PASS"));assertEquals(22,((List<?>)a.get("failures_with_equal_sets")).size());var witness=((List<Map<String,Object>>)a.get("witnesses")).getFirst();assertEquals("PASS",witness.get("verdict"));assertEquals(List.of(Map.of("row",List.of(99L),"count",42L)),witness.get("combined_bag"));assertNotEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(SqlPartitioning.json(a),"changed"));
    }
}
