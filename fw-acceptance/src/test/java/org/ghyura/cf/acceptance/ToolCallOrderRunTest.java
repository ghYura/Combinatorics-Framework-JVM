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
import org.ghyura.cf.fixtures.demos.ToolCallOrder;
import org.ghyura.cf.fixtures.demos.ToolCallOrderPlugin;
import org.ghyura.cf.fixtures.demos.ToolCallAnalysis;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Json;
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

class ToolCallOrderRunTest {
    private Element golden() throws Exception {
        var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try (var in=Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d13a-golden.xml"))) { return f.newDocumentBuilder().parse(in).getDocumentElement(); }
    }
    private Map<String,Map<String,String>> run(Path dir,int workers) throws Exception {
        Path spec=dir.resolve("d13a.toml"); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13a/spec.toml")) { assertNotNull(in); Files.write(spec,in.readAllBytes()); }
        var config=ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",ToolCallOrderPlugin.ID,"test"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"test")));
        try (var run=RunService.prepare(ScenarioLoader.load(spec).spec(),config)) {
            var s=run.await(); assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString()); assertEquals(BigInteger.valueOf(480),s.generated()); assertEquals(BigInteger.ZERO,s.excluded()); assertEquals(480,s.samples()); assertEquals(454,s.count(Outcome.PASS)); assertEquals(26,s.count(Outcome.DOMAIN_FAIL));
            var rows=new TreeMap<String,Map<String,String>>(); for (var o : run.finals()) { assertTrue(o.outcome()==Outcome.PASS||o.outcome()==Outcome.DOMAIN_FAIL); var a=o.domain().orElseThrow().attributes(); assertEquals(o.outcome().name(),a.get("verdict")); assertNull(rows.put(a.get("case_id"),a)); } assertEquals(480,rows.size()); return rows;
        }
    }
    @Test void unchangedOriginalScenarioProducesAll480IndependentTracesInSerialAndParallelIncludingAbsentRevocation(@TempDir Path dir) throws Exception {
        var serial=run(dir,1); assertEquals(serial,run(dir,4)); var cases=golden().getElementsByTagName("case");
        for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); var actual=serial.get(c.getAttribute("id")); assertNotNull(actual); var fs=c.getElementsByTagName("field"); assertEquals(fs.getLength(),actual.size()); for (int k=0;k<fs.getLength();k++) { var e=(Element)fs.item(k); String key=e.getAttribute("name"); assertEquals(e.getAttribute("kind").equals("json")?Json.parse(e.getTextContent(),"golden"):e.getTextContent(),e.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(96,serial.values().stream().filter(a -> a.get("revoke_cut").equals("null")).count()); for (String cut : List.of("0","1","2","3")) assertEquals(96,serial.values().stream().filter(a -> a.get("revoke_cut").equals(cut)).count()); assertEquals(96,serial.keySet().stream().map(id -> id.substring(0,id.lastIndexOf("|X="))).distinct().count());
    }
    @Test void completeAnalysisConsumesActualSharedRunEventsWithAllAxisCountsAndFivePrefixWitnessTimelines(@TempDir Path dir) throws Exception {
        var rows=run(dir,4); var analysis=ToolCallAnalysis.analyse(rows.values().stream().map(ToolCallAnalysis.Observed::new).toList()); assertEquals(Json.parse(golden().getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(ToolCallOrder.json(analysis),"actual"));
        assertEquals(Map.of("framework_attempts",480,"send_requests",720,"outbox_effects",90,"emit_responses",90),analysis.get("counts")); assertEquals(Map.of("PASS",120),((Map<?,?>)analysis.get("by_policy")).get("guarded")); assertEquals(Map.of("PASS",116,"DOMAIN_FAIL",4),((Map<?,?>)analysis.get("by_policy")).get("sticky_approval")); assertEquals(Map.of("PASS",106,"DOMAIN_FAIL",14),((Map<?,?>)analysis.get("by_policy")).get("success_only_taint")); assertEquals(Map.of("PASS",112,"DOMAIN_FAIL",8),((Map<?,?>)analysis.get("by_policy")).get("no_dedup"));
    }
}
