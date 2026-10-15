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

import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.fixtures.demos.CyclicAnalysis;
import org.ghyura.cf.fixtures.demos.CyclicPatternPlugin;
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
import static org.junit.jupiter.api.Assertions.*;

class CyclicPatternsRunTest {
    private Element golden() throws Exception {
        var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try (var in=Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d12-golden.xml"))) { return f.newDocumentBuilder().parse(in).getDocumentElement(); }
    }
    private Map<String,Map<String,String>> run(Path dir,String campaign,int workers) throws Exception {
        Path spec=dir.resolve(campaign+".toml"); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d12/"+campaign+"/spec.toml")) { assertNotNull(in); Files.write(spec,in.readAllBytes()); }
        var config=ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",CyclicPatternPlugin.ID,"test"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"test")));
        try (var run=RunService.prepare(ScenarioLoader.load(spec).spec(),config)) {
            int expected=campaign.equals("words")?729:130; var s=run.await(); assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString()); assertEquals(BigInteger.valueOf(expected),s.generated()); assertEquals(BigInteger.ZERO,s.excluded()); assertEquals(expected,s.samples()); assertEquals(expected,s.count(Outcome.PASS));
            var rows=new TreeMap<String,Map<String,String>>(); for (var o : run.finals()) { assertEquals(Outcome.PASS,o.outcome()); var a=o.domain().orElseThrow().attributes(); assertEquals(campaign,a.get("campaign")); assertEquals(o.outcome().name(),a.get("verdict")); assertNull(rows.put(a.get("word"),a)); } assertEquals(expected,rows.size()); return rows;
        }
    }
    private List<CyclicAnalysis.Observed> observations(Map<String,Map<String,String>> rows) {
        return rows.values().stream().map(a -> new CyclicAnalysis.Observed(a.get("case_id"),a.get("word"),a.get("campaign"),a.get("verdict"),a)).toList();
    }
    @Test void bothUnchangedOriginalScenariosMatchAllIndependentFieldsInSerialAndParallel(@TempDir Path dir) throws Exception {
        var g=golden(); var expected=new TreeMap<String,Map<String,String>>(); var cases=g.getElementsByTagName("case");
        for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); var fields=new TreeMap<String,String>(); var nodes=c.getElementsByTagName("field"); for (int j=0;j<nodes.getLength();j++) { var f=(Element)nodes.item(j); fields.put(f.getAttribute("name"),f.getTextContent()); } expected.put(fields.get("word"),fields); }
        var reps=List.of(((Element)g.getElementsByTagName("catalogue").item(0)).getAttribute("representatives").split(" "));
        for (String campaign : List.of("words","classes")) {
            Map<String,Map<String,String>> first=null;
            for (int workers : List.of(1,4)) {
                var actual=run(dir,campaign,workers); assertEquals(campaign.equals("words")?expected.keySet():new java.util.TreeSet<>(reps),actual.keySet());
                for (var entry : actual.entrySet()) { var a=entry.getValue(); assertEquals("W="+entry.getKey(),a.get("case_id")); for (var f : expected.get(entry.getKey()).entrySet()) assertEquals(f.getValue(),a.get(f.getKey()),campaign+" "+entry.getKey()+" "+f.getKey()); }
                if (first==null) first=actual; else assertEquals(first,actual);
            }
        }
    }
    @Test void fullObservedPopulationAnalysisCrossChecksBothSharedRuns(@TempDir Path dir) throws Exception {
        var words=observations(run(dir,"words",4)); var classes=observations(run(dir,"classes",4)); var r=CyclicAnalysis.analyse(words,classes); var g=golden();
        assertTrue(r.disjointComplete()); assertEquals(130,r.representativesMatched()); assertTrue(r.mismatches().isEmpty()); assertEquals(List.of(729,3,9,27,9,3),r.burnside()); assertTrue(r.weightedHistogramEqualsWords());
        var ranking=(Element)g.getElementsByTagName("ranking").item(0); assertEquals(ranking.getAttribute("order"),String.join(" ",r.ranking())); assertEquals(ranking.getAttribute("best-labelled-words"),String.join(" ",r.bestLabelledWords()));
        var populations=g.getElementsByTagName("population"); for (int i=0;i<populations.getLength();i++) { var p=(Element)populations.item(i); var actual=r.populations().get(p.getAttribute("name")); assertEquals(p.getAttribute("mean"),actual.mean().toString()); var hist=new TreeMap<BigInteger,Integer>(); var bins=p.getElementsByTagName("bin"); for (int j=0;j<bins.getLength();j++) { var b=(Element)bins.item(j); hist.put(new BigInteger(b.getAttribute("cost")),Integer.valueOf(b.getAttribute("count"))); } assertEquals(hist,actual.histogram()); }
        assertEquals(List.of("ABCABC"),r.bestClasses()); assertEquals(54,r.selfReflectionClasses()); assertEquals(38,r.mirrorPairs());
    }
}
