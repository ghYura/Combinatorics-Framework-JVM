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

package org.ghyura.cf.fixtures.redteam;

import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AttackDefenseTest {
    private org.w3c.dom.Element golden()throws Exception{var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("attack-defense-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}}
    private static Object json(Object o){return Json.parse(Json.write(o),"normalized observation");}
    private static Map<String,String> labels(Map<?,?> row){var result=new TreeMap<String,String>();((Map<?,?>)row.get("labels")).forEach((k,v)->result.put((String)k,(String)v));return result;}
    @Test void all10368SourceCoordinatesGuardScoresFeaturesCostsFrontiersAndFocusedNeedlesMatchIndependentControls()throws Exception{
        var root=golden();var cases=root.getElementsByTagName("case");assertEquals(10368,cases.getLength());var scored=new ArrayList<AttackDefenseMatrix.Scored>();int focused=0,focusedFull=0;
        for(int i=0;i<cases.getLength();i++){var want=(Map<?,?>)Json.parse(cases.item(i).getTextContent(),"preJava control");var values=new ArrayList<List<String>>();for(Object cell:(List<?>)want.get("values"))values.add(((List<?>)cell).stream().map(String.class::cast).toList());var c=AttackDefenseMatrix.coordinate(values);assertEquals(i,c.comboId());assertEquals(labels(want),c.labels());assertEquals(c,AttackDefenseMatrix.fromLabels(c.labels()));assertEquals(json(want.get("features")),json(AttackDefenseMatrix.features(c)));var score=AttackDefenseMatrix.score(c);assertEquals(((Number)want.get("cost_tokens")).intValue(),score.cost(),"cost:"+i);assertEquals(((Number)want.get("severity")).intValue(),score.severity(),"surrogate:"+i);assertEquals(((Number)want.get("oracle_severity")).intValue(),AttackDefenseMatrix.severity(c,true),"oracle:"+i);scored.add(score);
            if(c.labels().get("guard").equals("strict_refuse")&&c.labels().get("technique").equals("delimiter")&&c.labels().get("role").equals("dev")&&c.labels().get("lang").equals("de")&&c.labels().get("obfuscation").equals("unicode+homoglyph")){focused++;assertTrue(score.severity()>0);if(AttackDefenseMatrix.severity(c,true)>0)focusedFull++;}
        }
        var summary=new TreeMap<String,Object>(AttackDefenseMatrix.summarize(scored));summary.put("focused",Map.of("pairs",focused,"surrogate_breaches",focused,"oracle_breaches",focusedFull));assertEquals(Json.parse(root.getElementsByTagName("summary").item(0).getTextContent(),"source summary"),json(summary));
    }
    @Test void sourceSeededRoutingSweepAndDiscoveryMatchExactIndependentProbeAndReportedSets()throws Exception{
        var root=golden();var cases=root.getElementsByTagName("case");var routing=(Map<?,?>)Json.parse(root.getElementsByTagName("routing").item(0).getTextContent(),"independent routing");var predictions=(List<?>)routing.get("predictions");var rows=new ArrayList<AttackDefenseRouting.ModelRow>();
        for(int i=0;i<cases.getLength();i++){var row=(Map<?,?>)Json.parse(cases.item(i).getTextContent(),"source labels");var p=(Map<?,?>)predictions.get(i);assertEquals((long)i,p.get("combo_id"));rows.add(new AttackDefenseRouting.ModelRow(AttackDefenseMatrix.fromLabels(labels(row)),((Number)p.get("prediction")).intValue(),((Number)p.get("confidence")).doubleValue(),((Number)row.get("oracle_severity")).intValue()>0?1:0));}
        var router=new AttackDefenseRouting(rows,BigInteger.valueOf(7),.75);var wanted=(List<?>)routing.get("policies");int[] probes={0,1,2,3,5,3};for(int i=0;i<probes.length;i++)assertEquals(wanted.get(i),json(router.evaluate(probes[i],i==5)),"source continuous RNG policy:"+i);
        assertEquals(0L,((Map<?,?>)wanted.getFirst()).get("needles_found"));assertEquals(6L,((Map<?,?>)wanted.getLast()).get("needles_found"));assertNotEquals(100L,((Map<?,?>)wanted.getLast()).get("precision"));
    }
    @Test void sourceAdmissionAndStrictThresholdRemainExplicit(){
        assertThrows(IllegalArgumentException.class,()->AttackDefenseMatrix.coordinate(List.of()));assertThrows(IllegalArgumentException.class,()->AttackDefenseMatrix.fromLabels(Map.of()));assertThrows(IllegalArgumentException.class,()->AttackDefenseMatrix.costTokens("x".repeat(4097)));assertEquals(4,AttackDefenseMatrix.costTokens("#GUARD=x\nx\u00a0y\u0085z\u2028Q"));
        var c=AttackDefenseMatrix.fromLabels(Map.of("guard","paranoid","technique","direct","obfuscation","none","role","none","lang","en","order","cover>persuade>escalate"));assertThrows(IllegalArgumentException.class,()->new AttackDefenseRouting.ModelRow(c,2,.99,0));assertThrows(IllegalArgumentException.class,()->new AttackDefenseRouting.ModelRow(c,0,Double.NaN,0));assertThrows(IllegalArgumentException.class,()->new AttackDefenseRouting(List.of(),BigInteger.ONE,.75));
        var row=new AttackDefenseRouting.ModelRow(c,0,.75,0);var router=new AttackDefenseRouting(List.of(row),BigInteger.valueOf(7),.75);assertEquals(0,router.evaluate(0,false).get("oracle_calls"));assertEquals(1,router.evaluate(1,false).get("oracle_calls"));assertThrows(IllegalArgumentException.class,()->router.evaluate(13,false));assertThrows(IllegalArgumentException.class,()->new AttackDefenseRouting(List.of(row,row),BigInteger.ONE,.75));assertThrows(IllegalArgumentException.class,()->AttackDefenseMatrix.summarize(List.of(AttackDefenseMatrix.score(c),AttackDefenseMatrix.score(c))));
    }
}
