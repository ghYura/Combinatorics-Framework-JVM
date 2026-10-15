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
import org.ghyura.cf.fixtures.demos.DiagnosticSensorPlugin;
import org.ghyura.cf.fixtures.demos.SensorDiagnosis;
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

class DiagnosticSensorsRunTest {
    private Element golden() throws Exception {
        var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try (var in=Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d10-golden.xml"))) { return f.newDocumentBuilder().parse(in).getDocumentElement(); }
    }
    private Map<String,Map<String,String>> run(Path dir,int workers) throws Exception {
        Path spec=dir.resolve("d10.toml"); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d10/spec.toml")) { assertNotNull(in); Files.write(spec,in.readAllBytes()); }
        var config=ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",DiagnosticSensorPlugin.ID,"test"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"test"),new ConfigResolver.Override("cf.generation.distinctTables","true","original Subsets to Subsets distinctification")));
        try (var run=RunService.prepare(ScenarioLoader.load(spec).spec(),config)) {
            var s=run.await(); assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString()); assertEquals(BigInteger.valueOf(3072),s.generated()); assertEquals(BigInteger.ZERO,s.excluded()); assertEquals(3072,s.samples()); assertEquals(3072,s.count(Outcome.PASS));
            var rows=new TreeMap<String,Map<String,String>>(); for (var o : run.finals()) { assertEquals(Outcome.PASS,o.outcome()); var a=o.domain().orElseThrow().attributes(); assertEquals(o.outcome().name(),a.get("verdict")); assertNull(rows.put(a.get("case_id"),a)); } assertEquals(3072,rows.size()); return rows;
        }
    }
    private List<SensorDiagnosis.Observed> observations(Map<String,Map<String,String>> rows) { return rows.values().stream().map(a -> new SensorDiagnosis.Observed(a.get("case_id"),a.get("mask"),a.get("fault"),a.get("verdict"),SensorDiagnosis.parseReadings(a.get("readings")))).toList(); }
    @Test void unchangedOriginalScenarioRunsAll3072CasesWithEveryIndependentFieldInSerialAndParallel(@TempDir Path dir) throws Exception {
        var expected=new TreeMap<String,Map<String,String>>(); var cases=golden().getElementsByTagName("case");
        for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); var fields=new TreeMap<String,String>(); var ns=c.getElementsByTagName("field"); for (int j=0;j<ns.getLength();j++) { var f=(Element)ns.item(j); fields.put(f.getAttribute("name"),f.getTextContent()); } expected.put(c.getAttribute("id"),fields); }
        var serial=run(dir,1); assertEquals(expected,serial); assertEquals(serial,run(dir,4)); assertEquals("[]",serial.get("S=00000000|H=F01").get("readings")); assertTrue(serial.keySet().stream().noneMatch(id -> id.endsWith("H=H0")));
    }
    @Test void fullDiagnosisUsesActualSharedRunReadingsIncludingAllTiesBeliefsAndDecodingControls(@TempDir Path dir) throws Exception {
        var r=SensorDiagnosis.analyse(observations(run(dir,4))); var g=golden(); assertEquals(7,r.classes().size()); assertEquals(List.of("H0"),r.classes().getFirst().members()); assertEquals(SensorDiagnosis.HEALTHY_SOURCE,r.classes().getFirst().source()); assertEquals(256,r.suites().size()); assertEquals(5376,r.suites().stream().mapToInt(s -> s.distances().size()).sum()); assertEquals(0,r.unquotientedLabelMinDistance());
        var winners=g.getElementsByTagName("winner"); for (int i=0;i<winners.getLength();i++) { var e=(Element)winners.item(i); var w=r.winners().get(e.getAttribute("goal")); assertEquals(e.getAttribute("mask"),w.mask()); assertEquals(e.getAttribute("cost"),Integer.toString(w.cost())); assertEquals(e.getAttribute("feasible_count"),Integer.toString(w.feasibleCount())); assertEquals(e.getAttribute("minimum_cost_masks"),String.join(" ",w.minimumCostMasks())); }
        var beliefs=g.getElementsByTagName("belief"); assertEquals(127,r.adaptive().dp().size()); for (int i=0;i<127;i++) { var e=(Element)beliefs.item(i); var b=r.adaptive().dp().get(i); assertEquals(e.getAttribute("classes"),b.classes().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" "))); assertEquals(e.getAttribute("cost"),Integer.toString(b.cost())); assertEquals(e.getAttribute("sensor"),b.sensor()==null?"none":b.sensor().toString()); }
        assertEquals(3,r.adaptive().tree().cost()); assertEquals(0,r.adaptive().tree().sensor()); assertEquals(7,r.adaptive().paths().size());
        for (String goal : List.of("erasure","error")) { var controls=((Element)g.getElementsByTagName(goal).item(0)).getElementsByTagName("control"); var rows=r.noise().get(goal); assertEquals(controls.getLength(),rows.size()); for (int i=0;i<rows.size();i++) { var e=(Element)controls.item(i); var n=rows.get(i); assertEquals(e.getAttribute("class"),Integer.toString(n.diagnosis())); assertEquals(e.getAttribute("position"),Integer.toString(n.position())); assertEquals(e.getAttribute("observed"),n.observed()); assertEquals(List.of(n.diagnosis()),n.decoded()); } }
        assertEquals(List.of("F01","F02"),r.identifyExamples().get("F01").members());
    }
}
