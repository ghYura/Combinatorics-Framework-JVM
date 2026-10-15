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
import org.ghyura.cf.fixtures.demos.SessionMemory;
import org.ghyura.cf.fixtures.demos.SessionMemoryPlugin;
import org.ghyura.cf.fixtures.demos.SessionMemoryAnalysis;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.plan.PlanOptions;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
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

class SessionMemoryRunTest {
    private Element golden() throws Exception { var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); try (var in=Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d13d-golden.xml"))) { return f.newDocumentBuilder().parse(in).getDocumentElement(); } }
    private Path original(Path dir) throws Exception { Path spec=dir.resolve("d13d.toml"); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13d/spec.toml")) { assertNotNull(in); Files.write(spec,in.readAllBytes()); } return spec; }
    private org.ghyura.cf.config.EffectiveConfig config(int workers) { return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",SessionMemoryPlugin.ID,"D13d"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D13d"))); }

    private Map<String,Map<String,String>> run(Path spec,int workers) throws Exception {
        try (var run=RunService.prepare(ScenarioLoader.load(spec).spec(),config(workers))) {
            var s=run.await(); assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString()); assertEquals(BigInteger.valueOf(1152),s.generated()); assertEquals(BigInteger.valueOf(352),s.excluded()); assertEquals(800,s.samples()); assertEquals(718,s.count(Outcome.PASS)); assertEquals(82,s.count(Outcome.DOMAIN_FAIL));
            var bank=new TreeMap<String,Map<String,String>>(); for (var o : run.finals()) { assertTrue(o.outcome()==Outcome.PASS||o.outcome()==Outcome.DOMAIN_FAIL); var f=o.domain().orElseThrow().attributes(); assertEquals(o.outcome().name(),f.get("verdict")); assertNull(bank.put(f.get("case_id"),f)); } assertEquals(800,bank.size()); return bank;
        }
    }
    @Test void unchangedScenarioRetainsEveryOriginalObservationInSerialAndParallelWithFullObservedAnalysis(@TempDir Path dir) throws Exception {
        var g=golden(); var spec=original(dir); var serial=run(spec,1); assertEquals(serial,run(spec,4)); assertEquals(SessionMemoryAnalysis.expectedIds(),serial.keySet()); var cases=g.getElementsByTagName("case");
        for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); var actual=serial.get(c.getAttribute("id")); assertNotNull(actual); var fs=c.getElementsByTagName("field"); assertEquals(fs.getLength(),actual.size()); for (int k=0;k<fs.getLength();k++) { var e=(Element)fs.item(k); String key=e.getAttribute("name"); assertEquals(e.getAttribute("kind").equals("json")?Json.parse(e.getTextContent(),"golden"):e.getTextContent(),e.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(SessionMemory.json(SessionMemoryAnalysis.analyse(serial.values().stream().map(SessionMemoryAnalysis.Observed::new).toList())),"actual"));
    }
    @Test void existingRuleEngineMatchesEveryOriginalRawBondTruthAndExactSurvivorOverlapCertificate(@TempDir Path dir) throws Exception {
        var truth=(Map<?,?>)Json.parse(golden().getElementsByTagName("raw-bond-truth").item(0).getTextContent(),"golden"); var seen=new TreeMap<String,Object>(); var kept=new java.util.TreeSet<String>(); var spec=ScenarioLoader.load(original(dir)).spec(); assertEquals(2,spec.constraints().rules().size()); var config=config(1).config(); var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan(); var budget=MemoryBudget.of(config); var options=GenerationOptions.of(config);
        try (var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);
             var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);
             var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
            assertEquals(BigInteger.valueOf(1152),assembly.mandatoryRows()); assertEquals(BigInteger.ONE,assembly.perMandatoryRow()); assertEquals(2,assembly.rules().size()); assertEquals("user_rgs",assembly.rules().name(0)); assertEquals("session_rgs",assembly.rules().name(1)); assertTrue(assembly.rules().mandatoryOnly(0)); assertTrue(assembly.rules().mandatoryOnly(1));
            int[] hits=new int[2],survivors=new int[3]; int overlap=0; var rows=assembly.assessed(Cancellation.NONE);
            while (rows.hasNext()) {
                var row=rows.next(); var hit=row.violations(); for (int k=0;k<2;k++) if (hit.get(k)) hits[k]++; if (hit.cardinality()>1) overlap++; for (int k=0;k<=2;k++) if (hit.nextSetBit(0)<0||hit.nextSetBit(0)>=k) survivors[k]++;
                var atoms=new java.util.ArrayList<String>(); var names=assembly.columnSheets(); for (int k=0;k<names.size();k++) if (!names.get(k).equals("HEAD")&&!names.get(k).equals("TAIL")) atoms.add(dictionary.value(row.candidate().labels(k)[0]).text());
                String p=atoms.get(0).substring(6,atoms.get(0).length()-3),u="",s="",x=""; for (int i=2;i<5;i++) u+=atoms.get(i).charAt(10); for (int i=5;i<8;i++) s+=atoms.get(i).charAt(13); for (int i=8;i<10;i++) x+=atoms.get(i).charAt(12); String id="P="+p+"|U="+u+"|S="+s+"|X="+x+"|W="+atoms.get(1).charAt(9);
                assertNull(seen.put(id,Map.of("user_rgs",!hit.get(0),"session_rgs",!hit.get(1)))); if (hit.isEmpty()) kept.add(SessionMemoryPlugin.parseAtoms(atoms).caseId());
            }
            assertEquals(truth,seen); assertArrayEquals(new int[]{192,192},hits); assertArrayEquals(new int[]{1152,960,800},survivors); assertEquals(32,overlap); assertEquals(SessionMemoryAnalysis.expectedIds(),kept);
            var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(1200)); assertTrue(count.exact()); assertEquals(BigInteger.valueOf(352),count.removedEarly()); assertEquals(BigInteger.ZERO,count.removedLate()); assertEquals(BigInteger.ZERO,count.unselected()); assertEquals(BigInteger.valueOf(800),count.executable()); assertEquals(BigInteger.valueOf(32),count.overlap());
        }
        assertEquals(0,budget.reservedBytes());
    }
}
