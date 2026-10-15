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

package org.ghyura.cf.acceptance;

import org.ghyura.cf.model.Json;
import org.w3c.dom.Element;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Independent preproduction records; only fields actually emitted by the original runtime are observations. */
final class LocalProposalProof {
    private LocalProposalProof(){ }
    private static Element xml(Path path)throws Exception {
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }
    private static Map<?,?> json(String value){return (Map<?,?>)Json.parse(value,"local task proof");}
    private static String child(Element row,String name){return row.getElementsByTagName(name).item(0).getTextContent();}
    private static List<?> values(Map<?,?> slots,String key){return (List<?>)slots.get(key);}
    private static String atom(Map<?,?> slots,String key){return (String)values(slots,key).getFirst();}
    private static String match(String text,String regex){var m=Pattern.compile(regex).matcher(text);assertTrue(m.find(),text);return m.group(1);}
    private static String joined(Object value,String delimiter){return String.join(delimiter,((List<?>)value).stream().map(Object::toString).toList());}
    private static String guardPart(String value){
        assertTrue(value.startsWith("parts.append(")&&value.endsWith(")\n"));
        String literal=value.substring(13,value.length()-2);
        return literal.startsWith("\"")?(String)Json.parse(literal,"literal"):literal.substring(1,literal.length()-1);
    }
    static Map<String,Object> verify(Path path,String family,int providerCalls)throws Exception {
        var proof=xml(path);assertEquals(family,proof.getAttribute("task"));assertEquals(Integer.toString(providerCalls),proof.getAttribute("provider-calls"));
        int n=switch(family){case "quote"->648;case "parser"->216;case "guard"->128;default->throw new IllegalArgumentException();};
        int mandatory=family.equals("quote")?648:family.equals("parser")?8:2,multiplier=family.equals("quote")?1:family.equals("parser")?27:64;
        assertEquals(Integer.toString(n),proof.getAttribute("observations"));
        assertEquals(json(Json.write(Map.of("raw",n,"early_excluded",0,"late_excluded",0,"unselected",0,"executable",n,"mandatory_rows",mandatory,"optional_multiplier",multiplier))),json(child(proof,"stage-counts")));
        var golden=xml(Reactor.root().resolve("fw-input/src/test/resources/org/ghyura/cf/input/local-proposals-golden.xml"));
        var cases=golden.getElementsByTagName("case");var expected=new TreeMap<String,Map<?,?>>();
        for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);if(c.getAttribute("family").equals(family))assertNull(expected.put(c.getAttribute("id"),json(c.getTextContent())));}
        var observed=new TreeMap<String,Object>();var partitions=new TreeMap<Long,Long>();var totals=new TreeMap<String,Long>();
        var rows=proof.getElementsByTagName("observation");assertEquals(n,rows.getLength());
        for(int i=0;i<rows.getLength();i++) {
            var row=(Element)rows.item(i);var recipe=json(child(row,"recipe"));var slots=(Map<?,?>)recipe.get("values");String id;String stdout=child(row,"stdout");var fields=json(child(row,"fields"));
            if(family.equals("quote")) {
                var order=values(slots,"OPS").stream().map(Object::toString).map(s->s.contains("applyDiscount")?"discount":s.contains("applyTax")?"tax":"round10").toList();
                var flags=values(slots,"FLAGS").stream().map(Object::toString).map(s->s.contains("/ 10")?"loyalty":"coupon").toList();
                id=String.join("|",match(atom(slots,"QTY"),"qty = (-?\\d+);"),match(atom(slots,"DISCOUNT"),"discount = (-?\\d+);"),match(atom(slots,"TAX"),"taxPct = (-?\\d+);"),String.join(",",order),String.join(",",flags));
                var want=expected.get(id);assertNotNull(want,id);
                assertEquals("FW_VAR="+want.get("fw_var")+" total="+want.get("total")+" qty="+want.get("qty")+" discount="+want.get("discount")+" taxPct="+want.get("taxPct")+"\n",stdout,id);
            } else if(family.equals("parser")) {
                var keys=new ArrayList<String>();for(String key:List.of("ACTION","TARGET","AMOUNT"))keys.add(match(atom(slots,key),"= \"([^\"]*)\";"));
                for(int k=1;k<=3;k++)keys.add(values(slots,"SURPRISE"+k).isEmpty()?"absent":match(atom(slots,"SURPRISE"+k),"toks.add\\(\"([^\"]*)\"\\);"));
                id=String.join("|",keys);var want=expected.get(id);assertNotNull(want,id);
                assertEquals("FW_VAR="+want.get("fw_var")+" want=["+joined(want.get("expected"),"|")+"] got=["+joined(want.get("observed"),", ")+"] toks=["+joined(want.get("tokens"),", ")+"]\n",stdout,id);
            } else {
                var keys=new ArrayList<String>();keys.add(guardPart(atom(slots,"QUERY")));
                for(int k=1;k<=3;k++)keys.add(values(slots,"INJ"+k).isEmpty()?"absent":guardPart(atom(slots,"INJ"+k)));
                id=String.join("|",keys);var want=expected.get(id);assertNotNull(want,id);
                assertEquals(want.get("text"),fields.get("text"),id);assertEquals(want.get("parts"),Json.parse((String)fields.get("parts"),"parts"),id);
                for(String key:List.of("allowed","malicious"))assertEquals(want.get(key).toString(),fields.get(key),id);
                assertEquals(want.get("fw_var").toString(),fields.get("FW_VAR"),id);assertEquals("",stdout);
            }
            assertEquals(expected.get(id).get("verdict"),row.getAttribute("outcome"),id);
            assertNull(observed.put(id,Map.of("candidate",row.getAttribute("candidate"),"outcome",row.getAttribute("outcome"),"recipe",recipe,"stdout",stdout,"fields",fields)),id);
            partitions.merge((Long)recipe.get("optional_size"),1L,Long::sum);totals.merge(row.getAttribute("outcome"),1L,Long::sum);
        }
        assertEquals(expected.keySet(),observed.keySet());assertEquals(json(child(golden,"counts")).get(family),totals);
        if(family.equals("parser"))assertEquals(Map.of(0L,8L,1L,48L,2L,96L,3L,64L),partitions);
        if(family.equals("guard"))assertEquals(Map.of(0L,2L,1L,18L,2L,54L,3L,54L),partitions);
        return Map.of("digest",proof.getAttribute("frozen-digest"),"run",proof.getAttribute("run-id"),"observations",observed);
    }
}
