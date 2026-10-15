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

import org.ghyura.cf.algebra.LexicographicPermutations;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.fixtures.demos.DependencyDags;
import org.ghyura.cf.fixtures.demos.DependencyDagsPlugin;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.*;

class DependencyDagsRunTest {
    @Test void theUnchangedOriginalScenarioRunsAll768CasesWithExactIndependentSerialParallelTraces(@TempDir Path dir)throws Exception{
        Path spec=dir.resolve("spec.toml");try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d8b/spec.toml")){assertNotNull(in);Files.write(spec,in.readAllBytes());}
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);Element golden;
        try(var in=Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d8b-golden.xml"))){golden=factory.newDocumentBuilder().parse(in).getDocumentElement();}
        Map<String,Map<String,String>> expected=new TreeMap<>();var cases=golden.getElementsByTagName("case");
        for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);Map<String,String> attrs=new TreeMap<>();attrs.put("verdict",c.getAttribute("verdict"));var fields=c.getElementsByTagName("field");for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);attrs.put(f.getAttribute("name"),f.getTextContent());}expected.put(c.getAttribute("id"),attrs);}
        Map<String,Object> first=null;
        for(int workers:List.of(1,4)){
            var config=ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",DependencyDagsPlugin.ID,"test"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"test")));
            try(var run=RunService.prepare(ScenarioLoader.load(spec).spec(),config)){
                var s=run.await();assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString());assertEquals(BigInteger.valueOf(768),s.generated());assertEquals(BigInteger.ZERO,s.excluded());assertEquals(768,s.samples());assertEquals(604,s.count(Outcome.PASS));assertEquals(164,s.count(Outcome.DOMAIN_FAIL));assertEquals(0,s.count(Outcome.BROKEN));
                Map<String,Object> actual=new TreeMap<>();for(var o:run.finals()){var domain=o.domain().orElseThrow();var a=domain.attributes();assertNull(actual.put(a.get("case"),domain));var e=expected.get(a.get("case"));assertNotNull(e,a.toString());for(var field:e.entrySet())assertEquals(field.getValue(),a.get(field.getKey()),a.get("case")+" "+field.getKey());assertEquals(o.outcome().name(),a.get("verdict"));}
                assertEquals(expected.keySet(),actual.keySet());if(first==null)first=actual;else assertEquals(first,actual);
            }
        }
    }
    @Test void theExistingAlgebraPermutationsFeedCompleteTopologyCertificatesForEveryOriginalGraph(){
        var permutations=new LexicographicPermutations(4);var graphs=new TreeSet<String>();for(int g=0;g<64;g++)graphs.add(DependencyDags.code(g));
        var proof=DependencyDags.topology(graphs,java.util.stream.IntStream.range(0,24).mapToObj(i->permutations.get(BigInteger.valueOf(i))).iterator());
        assertEquals(315,proof.graphOrderPairs());assertEquals(31,proof.additions().size());assertTrue(proof.additions().stream().allMatch(DependencyDags.Addition::equalSets));
        var example=proof.additions().stream().filter(a->a.before().equals("100010")&&a.after().equals("101010")).findFirst().orElseThrow();
        assertTrue(example.beforeOrders().containsAll(List.of("ABCD","CABD")));assertEquals(example.beforeOrders(),example.afterOrders());
        assertTrue(proof.additions().stream().allMatch(a->a.beforeOrders().getFirst().equals(a.afterOrders().getFirst())));
    }
}
