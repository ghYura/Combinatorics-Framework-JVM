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
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Expected results come from the independent frozen architect derivation, not this port. */
class ReductionTreesTest {
    private static BigDecimal q(Element e, String n, String d) {
        return new BigDecimal(e.getAttribute(n)).divide(new BigDecimal(e.getAttribute(d)));
    }
    @Test void everyOriginalTreeVectorAndPolicyMatchesTheIndependentExactPrediction() throws Exception {
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d7-golden.xml")) {
            assertNotNull(in);var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            var root=f.newDocumentBuilder().parse(in).getDocumentElement();
            Map<String,List<Double>> vectors=new HashMap<>();Map<String,BigDecimal> budgets=new HashMap<>();
            var vs=root.getElementsByTagName("vector");
            for(int i=0;i<vs.getLength();i++) {
                var v=(Element)vs.item(i);vectors.put(v.getAttribute("id"),Arrays.stream(v.getAttribute("hex").split(" ")).map(Double::valueOf).toList());
                var bs=v.getElementsByTagName("budget");for(int j=0;j<bs.getLength();j++) {
                    var b=(Element)bs.item(j);budgets.put(v.getAttribute("id")+"/"+b.getAttribute("policy"),q(b,"n","d"));
                }
            }
            var cases=root.getElementsByTagName("case");assertEquals(168,cases.getLength());int pass=0;
            for(int i=0;i<cases.getLength();i++) {
                var c=(Element)cases.item(i);String policy=c.getAttribute("policy"),vector=c.getAttribute("vector"),tree=c.getAttribute("tree");
                var r=ReductionTrees.evaluate(policy,tree,vectors.get(vector),budgets.get(vector+"/"+policy));
                String at=policy+"/"+vector+"/"+tree;
                assertEquals(0,q(c,"result_n","result_d").compareTo(r.value()),at);
                assertEquals(0,q(c,"error_n","error_d").compareTo(r.error()),at);
                assertEquals(Boolean.parseBoolean(c.getAttribute("passed")),r.passed(),at);
                assertEquals(policy.equals("flat_fsum")?0:4,r.nodes().size(),at);
                if(r.passed())pass++;
            }
            assertEquals(156,pass);
        }
    }
    @Test void malformedTreesRemainSetupFailuresAndReferenceIsPolicyBlind() {
        var values=List.of(0x1p53,1.0,-0x1p53,1.0,1.0);
        assertEquals(0,BigDecimal.valueOf(3).compareTo(ReductionTrees.reference(values)));
        for(String tree:List.of("((0,1),2)","((((0,2),1),3),4)","((((0,1),2),3),4", "(((0,1),2),(3,4),5)","((((0,1),2),3),4)x","(((( 0,1),2),3),4)","((((0,0),2),3),4)"))
            assertThrows(IllegalArgumentException.class,()->ReductionTrees.evaluate("tree_binary64",tree,values,BigDecimal.ZERO));
        assertFalse(ReductionTrees.evaluate("tree_binary64","((((0,1),2),3),4)",values,BigDecimal.valueOf(0.25)).passed());
        assertTrue(ReductionTrees.evaluate("flat_fsum","((((0,1),2),3),4)",values,BigDecimal.ZERO).passed());
    }
}
