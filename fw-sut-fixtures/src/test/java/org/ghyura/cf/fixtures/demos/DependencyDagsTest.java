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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.*;

class DependencyDagsTest {
    private Element golden()throws Exception{
        var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d8b-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}
    }
    @Test void all768CasesMatchEveryIndependentCacheTraceAndRecursiveReferenceField()throws Exception{
        var cases=golden().getElementsByTagName("case");assertEquals(768,cases.getLength());Map<String,Integer> counts=new HashMap<>();
        for(int i=0;i<cases.getLength();i++){
            var c=(Element)cases.item(i);String id=c.getAttribute("id");String[] identity=id.split("\\|");
            var r=DependencyDags.evaluate(identity[0],DependencyDags.graph(identity[1].substring(2)),"ABCD".indexOf(identity[2].substring(2)));
            assertEquals(id,r.caseId());var attrs=DependencyDags.attributes(r);assertEquals(c.getAttribute("verdict"),attrs.get("verdict"),id);
            var fields=c.getElementsByTagName("field");for(int j=0;j<fields.getLength();j++){var field=(Element)fields.item(j);assertEquals(field.getTextContent(),attrs.get(field.getAttribute("name")),id+" "+field.getAttribute("name"));}
            assertEquals(r.trace().before(),r.trace().postEdit(),"editing base never updates cache");assertTrue(r.trace().updates().size()<=4);
            for(int v=0;v<4;v++)assertEquals(r.reference().delta().get(v),r.reference().after().get(v)-r.reference().before().get(v));
            counts.merge(r.policy()+"/"+attrs.get("verdict"),1,Integer::sum);
        }
        assertEquals(Map.of("closure_forward/PASS",256,"direct_only/PASS",228,"direct_only/DOMAIN_FAIL",28,"closure_reverse/PASS",120,"closure_reverse/DOMAIN_FAIL",136),counts);
    }
    @Test void completeOrderSetsAndAll31RedundantEdgeCertificatesMatchIndependentPredictions()throws Exception{
        var gold=golden();var graphNodes=gold.getElementsByTagName("graph");Set<String> graphs=new TreeSet<>();for(int i=0;i<graphNodes.getLength();i++)graphs.add(((Element)graphNodes.item(i)).getAttribute("bits"));
        String[] words=((Element)graphNodes.item(0)).getAttribute("orders").split(" ");
        var tuples=java.util.Arrays.stream(words).map(w->w.chars().map(c->c-'A').toArray()).toList();
        var proof=DependencyDags.topology(graphs,tuples.iterator());assertEquals(64,proof.graphs().size());assertEquals(315,proof.graphOrderPairs());assertEquals(31,proof.additions().size());
        for(int i=0;i<graphNodes.getLength();i++){var g=(Element)graphNodes.item(i);assertEquals(g.getAttribute("bits"),proof.graphs().get(i).bits());assertEquals(g.getAttribute("orders"),String.join(" ",proof.graphs().get(i).orders()));}
        var adds=gold.getElementsByTagName("addition");assertEquals(31,adds.getLength());
        for(int i=0;i<adds.getLength();i++){var a=(Element)adds.item(i);var got=proof.additions().get(i);assertEquals(a.getAttribute("before"),got.before());assertEquals(a.getAttribute("after"),got.after());assertEquals(a.getAttribute("edge"),List.of(got.edge().from(),got.edge().to()).toString());assertEquals(a.getAttribute("orders-before"),String.join(" ",got.beforeOrders()));assertEquals(a.getAttribute("orders-after"),String.join(" ",got.afterOrders()));assertTrue(got.equalSets());assertEquals(a.getAttribute("minimum-before"),got.beforeOrders().getFirst());assertEquals(a.getAttribute("minimum-after"),got.afterOrders().getFirst());}
        assertThrows(IllegalArgumentException.class,()->DependencyDags.topology(Set.of("000000"),tuples.iterator()));
        assertThrows(IllegalArgumentException.class,()->DependencyDags.topology(graphs,tuples.subList(0,23).iterator()));
        assertThrows(IllegalArgumentException.class,()->DependencyDags.topology(graphs,List.of(new int[]{0,0,2,3}).iterator()));
        var duplicated=new ArrayList<>(tuples);duplicated.add(tuples.getFirst());assertThrows(IllegalArgumentException.class,()->DependencyDags.topology(graphs,duplicated.iterator()));
        // Redundant-edge equivalence is about order validity, not additive values.
        assertNotEquals(DependencyDags.reference(DependencyDags.graph("100010"),0).before(),DependencyDags.reference(DependencyDags.graph("101010"),0).before());
    }
    @Test void originalWitnessesKeepTheTwoFaultMechanismsAndMalformedAtomsAreSetupErrors(){
        int chain=DependencyDags.graph("100100");var direct=DependencyDags.evaluate("direct_only",chain,0);assertEquals(List.of("A","B"),direct.trace().dirty());assertEquals(List.of("C"),direct.mismatched());
        var reverse=DependencyDags.evaluate("closure_reverse",chain,0);assertEquals(List.of("C","B","A"),reverse.trace().order());assertEquals(reverse.reference().affected(),reverse.trace().dirty());assertFalse(reverse.passed());
        assertTrue(DependencyDags.evaluate("closure_forward",chain,0).passed());assertTrue(DependencyDags.evaluate("direct_only",DependencyDags.graph("110100"),0).passed());
        var complete=DependencyDags.evaluate("closure_forward",63,0);assertEquals(4,complete.reference().paths().get(0).get(3));assertEquals(40,complete.reference().delta().get(3));
        List<String> atoms=List.of("edge(\"A\",\"B\",0)\n","edge(\"A\",\"C\",0)\n","edge(\"A\",\"D\",0)\n","edge(\"B\",\"C\",0)\n","edge(\"B\",\"D\",0)\n","edge(\"C\",\"D\",0)\n");
        assertEquals(new DependencyDagsPlugin.Parsed("closure_forward",0,0),DependencyDagsPlugin.parseAtoms("impl(\"closure_forward\")\n",atoms,"edit(\"A\")\n"));
        for(String bad:List.of("edge(\"A\",\"C\",0)\n","edge(\"A\",\"B\",2)\n","edge(\"B\",\"A\",1)\n","edge(\"A\",\"B\",0)","edge(\"A\",\"B\",0)\nedge(\"A\",\"B\",0)\n")){var changed=new ArrayList<>(atoms);changed.set(0,bad);assertThrows(IllegalArgumentException.class,()->DependencyDagsPlugin.parseAtoms("impl(\"closure_forward\")\n",changed,"edit(\"A\")\n"));}
        assertThrows(IllegalArgumentException.class,()->DependencyDagsPlugin.parseAtoms("impl(\"bad\")\n",atoms,"edit(\"A\")\n"));
        assertThrows(IllegalArgumentException.class,()->DependencyDagsPlugin.parseAtoms("impl(\"closure_forward\")\n",atoms,"edit(\"E\")\n"));
        assertThrows(IllegalArgumentException.class,()->DependencyDagsPlugin.parseAtoms("impl(\"closure_forward\")\n",atoms.subList(0,5),"edit(\"A\")\n"));
        assertThrows(IllegalArgumentException.class,()->DependencyDags.evaluate("bad",0,0));assertThrows(IllegalArgumentException.class,()->DependencyDags.reference(64,0));assertThrows(IllegalArgumentException.class,()->DependencyDags.graph("000002"));
    }
}
