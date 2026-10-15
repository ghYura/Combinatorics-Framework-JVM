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

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TiesPaginationTest {
    private static Element golden() throws Exception {
        try (var in = TiesPaginationTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/demos/d8-golden.xml")) {
            assertNotNull(in);var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            return f.newDocumentBuilder().parse(in).getDocumentElement();
        }
    }
    private static String cursor(List<Integer> c) { return c==null?"null":String.join(" ",c.stream().map(Object::toString).toList()); }
    @Test void all450OriginalPageTracesAndOutcomesMatchIndependentPredictions() throws Exception {
        var cases=golden().getElementsByTagName("case");assertEquals(450,cases.getLength());int pass=0;
        for(int i=0;i<cases.getLength();i++) {
            var c=(Element)cases.item(i);String id=c.getAttribute("id");
            var r=TiesPagination.evaluate(c.getAttribute("policy"),c.getAttribute("ranks"),c.getAttribute("direction"));
            assertEquals(c.getAttribute("collected"),String.join("",r.collected()),id);assertEquals(c.getAttribute("expected"),String.join("",r.expected()),id);
            assertEquals(Boolean.parseBoolean(c.getAttribute("passed")),r.passed(),id);
            r.obligations().forEach((k,v)->assertEquals(Boolean.parseBoolean(c.getAttribute(k)),v,id+"/"+k));
            assertTrue(r.comparatorLaws().stream().allMatch(m->m.values().stream().allMatch(Boolean::booleanValue)),id);
            var pages=c.getElementsByTagName("page");assertEquals(pages.getLength(),r.pages().size(),id);
            for(int n=0;n<pages.getLength();n++) {
                var p=(Element)pages.item(n);var actual=r.pages().get(n);
                assertEquals(Integer.parseInt(p.getAttribute("request")),actual.request(),id);
                assertEquals(p.getAttribute("cursor-in"),cursor(actual.cursorIn()),id);assertEquals(p.getAttribute("cursor-out"),cursor(actual.cursorOut()),id);
                assertEquals(p.getAttribute("offset"),actual.offset()==null?"null":actual.offset().toString(),id);
                assertEquals(p.getAttribute("full-order"),String.join("",actual.fullOrder()),id);assertEquals(p.getAttribute("ids"),String.join("",actual.ids()),id);
                assertEquals(p.getAttribute("comparisons"),String.join(" ",actual.comparisons().stream().flatMap(List::stream).map(Object::toString).toList()),id);
            }
            if(r.passed())pass++;
        }
        assertEquals(330,pass);
    }
    @Test void all150ReversedTieAlternativesRetainPrimaryOrderAndRejectExactly102UnstableOnes() throws Exception {
        var cases=golden().getElementsByTagName("alternative");assertEquals(150,cases.getLength());int rejected=0;
        for(int i=0;i<cases.getLength();i++) {
            var c=(Element)cases.item(i);String ranks=c.getAttribute("ranks"),direction=c.getAttribute("direction");
            var alternative=TiesPagination.reversedTieOrder(ranks,direction);
            assertEquals(c.getAttribute("alternative"),String.join("",alternative));assertEquals(c.getAttribute("stable"),String.join("",TiesPagination.expectedOrder(ranks,direction)));
            var obligations=TiesPagination.obligations(ranks,direction,alternative,true);
            assertTrue(obligations.get("multiset_ok"));assertTrue(obligations.get("primary_order_ok"));
            assertEquals(Boolean.parseBoolean(c.getAttribute("stable-ok")),obligations.get("stable_ties_ok"));
            if(!obligations.get("stable_ties_ok"))rejected++;
        }
        assertEquals(102,rejected);
    }
    @Test void malformedRanksAreSetupFailuresAndAnUnlawfulComparatorIsDetected() {
        for(String ranks:List.of("0123x","013","1111","0022","0124",""))assertThrows(IllegalArgumentException.class,()->TiesPagination.evaluate("stable_cursor",ranks,"asc"));
        assertThrows(IllegalArgumentException.class,()->TiesPagination.evaluate("unknown","0000","asc"));
        assertThrows(IllegalArgumentException.class,()->TiesPagination.evaluate("stable_cursor","0000","unknown"));
        var bad=List.of(List.of(0,-1,0,0),List.of(-1,0,0,0),List.of(0,0,0,0),List.of(0,0,0,0));
        assertFalse(TiesPagination.comparatorLaws(bad,"0000","asc").get("antisymmetric"));
        assertEquals(List.of("AB","BA",""),TiesPagination.evaluate("alternating_ties","0000","asc").pages().stream().map(p->String.join("",p.ids())).toList());
        assertEquals(List.of("DA",""),TiesPagination.evaluate("score_only_cursor","1110","asc").pages().stream().map(p->String.join("",p.ids())).toList());
    }
}
