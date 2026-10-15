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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CoalitionValuesTest {
    private static Element golden() throws Exception {
        try(var in=CoalitionValuesTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/demos/d11-golden.xml")) {
            assertNotNull(in);var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            return f.newDocumentBuilder().parse(in).getDocumentElement();
        }
    }
    private static BigInteger integer(Element e,String a) { return new BigInteger(e.getAttribute(a)); }
    private static CoalitionAllocation.Fraction q(Element e,String n,String d) {return new CoalitionAllocation.Fraction(integer(e,n),integer(e,d));}
    private static List<CoalitionAllocation.Observed> observations(Element root) {
        List<CoalitionAllocation.Observed> r=new ArrayList<>();var cs=root.getElementsByTagName("case");
        for(int i=0;i<cs.getLength();i++) {var c=(Element)cs.item(i);r.add(new CoalitionAllocation.Observed(c.getAttribute("id"),c.getAttribute("mask"),"PASS",integer(c,"value")));}return r;
    }
    private static List<int[]> orders(Element root) {
        List<int[]> r=new ArrayList<>();var ps=root.getElementsByTagName("permutation");
        for(int i=0;i<ps.getLength();i++)r.add(((Element)ps.item(i)).getAttribute("order").chars().map(c->c-'A').toArray());return r;
    }
    @Test void all64OriginalCoalitionsIncludingEmptyAndNullPlayerMatchBothIndependentValueRoutes() throws Exception {
        var cases=golden().getElementsByTagName("case");assertEquals(64,cases.getLength());
        for(int i=0;i<cases.getLength();i++) {
            var c=(Element)cases.item(i);String members=c.getAttribute("members");
            var r=CoalitionValues.evaluate(members.isEmpty()?List.of():List.of(members.split(" ")));
            assertEquals(c.getAttribute("mask"),r.mask());assertTrue(r.passed());assertEquals(r.observed(),r.reference());
            assertEquals(integer(c,"value"),r.observed().value());assertEquals(integer(c,"standalone"),r.observed().standalone());
            assertEquals(Map.of("AB",integer(c,"AB"),"ACD",integer(c,"ACD"),"BCD",integer(c,"BCD")),r.observed().bonuses());
        }
        assertEquals(BigInteger.ZERO,CoalitionValues.evaluate(List.of()).observed().value());
        assertEquals(CoalitionValues.evaluate(List.of("A")).observed(),CoalitionValues.evaluate(List.of("A","F")).observed());
    }
    @Test void all192WeightedMarginals720PermutationCertificatesAnd64DividendsMatchIndependentPredictions() throws Exception {
        var root=golden();var a=CoalitionAllocation.allocate(observations(root),orders(root).iterator());
        assertEquals(192,a.marginals().size());assertEquals(720,a.permutations().size());assertEquals(64,a.dividends().size());assertEquals(BigInteger.valueOf(32),a.grandValue());
        var ms=root.getElementsByTagName("marginal");
        for(int i=0;i<ms.getLength();i++) {
            var m=(Element)ms.item(i);assertEquals(new CoalitionAllocation.Marginal(m.getAttribute("player"),m.getAttribute("coalition"),q(m,"n","d"),integer(m,"value")),a.marginals().get(i));
        }
        var ps=root.getElementsByTagName("permutation");
        for(int i=0;i<ps.getLength();i++) {
            var m=(Element)ps.item(i);assertEquals(new CoalitionAllocation.Permutation(m.getAttribute("order"),Arrays.stream(m.getAttribute("marginals").split(" ")).map(BigInteger::new).toList()),a.permutations().get(i));
        }
        var ds=root.getElementsByTagName("dividend");for(int i=0;i<ds.getLength();i++){var d=(Element)ds.item(i);assertEquals(integer(d,"value"),a.dividends().get(d.getAttribute("mask")));}
        var players=root.getElementsByTagName("player");
        for(int i=0;i<players.getLength();i++) {
            var c=(Element)players.item(i);String p=c.getAttribute("id");assertEquals(q(c,"n","d"),a.shapley().get(p));assertEquals(q(c,"n","d"),a.dividendSplit().get(p));
            assertEquals(integer(c,"permutation-sum"),a.permutationSums().get(p));assertEquals(integer(c,"standalone"),a.standalone().get(p));
            assertEquals(integer(c,"leave-one-out"),a.leaveOneOut().get(p));assertEquals(q(c,"uniform-n","uniform-d"),a.uniformSubsetMarginals().get(p));
        }
        assertTrue(a.checks().values().stream().allMatch(Boolean::booleanValue));
    }
    @Test void invalidPlayersAndObservationsAreRejectedAndAlteredObservedValuesChangeTheAllocation() throws Exception {
        for(var members:List.of(List.of("B","A"),List.of("A","A"),List.of("G")))assertThrows(IllegalArgumentException.class,()->CoalitionValues.evaluate(members));
        var root=golden();var good=observations(root);var orders=orders(root);
        assertThrows(IllegalArgumentException.class,()->CoalitionAllocation.allocate(good.subList(0,63),orders.iterator()));
        var duplicate=new ArrayList<>(good);duplicate.set(63,good.getFirst());assertThrows(IllegalArgumentException.class,()->CoalitionAllocation.allocate(duplicate,orders.iterator()));
        var broken=new ArrayList<>(good);var o=good.getFirst();broken.set(0,new CoalitionAllocation.Observed(o.caseId(),o.mask(),"DOMAIN_FAIL",o.value()));
        assertThrows(IllegalArgumentException.class,()->CoalitionAllocation.allocate(broken,orders.iterator()));
        broken.set(0,new CoalitionAllocation.Observed("C=000001",o.mask(),"PASS",o.value()));assertThrows(IllegalArgumentException.class,()->CoalitionAllocation.allocate(broken,orders.iterator()));
        assertThrows(IllegalArgumentException.class,()->CoalitionAllocation.allocate(good,orders.subList(0,719).iterator()));
        var repeated=new ArrayList<>(orders);repeated.set(719,orders.getFirst());assertThrows(IllegalArgumentException.class,()->CoalitionAllocation.allocate(good,repeated.iterator()));
        var altered=new ArrayList<>(good);o=good.get(12);altered.set(12,new CoalitionAllocation.Observed(o.caseId(),o.mask(),"PASS",o.value().add(BigInteger.ONE)));
        var original=CoalitionAllocation.allocate(good,orders.iterator());var changed=CoalitionAllocation.allocate(altered,orders.iterator());
        assertNotEquals(original.shapley(),changed.shapley());assertTrue(changed.checks().get("three_certificates_agree"));assertTrue(changed.checks().get("efficiency"));
    }
}
