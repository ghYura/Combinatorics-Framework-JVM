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
import org.ghyura.cf.fixtures.demos.Idempotency;
import org.ghyura.cf.fixtures.demos.IdempotencyPlugin;
import org.ghyura.cf.fixtures.demos.IdempotencyAnalysis;
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

class IdempotencyRunTest {
    private Element golden() throws Exception { var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); try (var in=Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d1-golden.xml"))) { return f.newDocumentBuilder().parse(in).getDocumentElement(); } }
    private Path original(Path dir) throws Exception { Path spec=dir.resolve("d1.toml"); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d1/spec.toml")) { assertNotNull(in); Files.write(spec,in.readAllBytes()); } return spec; }
    private org.ghyura.cf.config.EffectiveConfig config(int workers) { return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",IdempotencyPlugin.ID,"D1"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D1"))); }

    private Map<String,Map<String,String>> run(Path spec,int workers) throws Exception {
        try (var run=RunService.prepare(ScenarioLoader.load(spec).spec(),config(workers))) {
            var s=run.await(); assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString()); assertEquals(BigInteger.valueOf(600),s.generated()); assertEquals(BigInteger.valueOf(480),s.excluded()); assertEquals(120,s.samples()); assertEquals(77,s.count(Outcome.PASS)); assertEquals(43,s.count(Outcome.DOMAIN_FAIL));
            var bank=new TreeMap<String,Map<String,String>>(); for (var o : run.finals()) { assertTrue(o.outcome()==Outcome.PASS||o.outcome()==Outcome.DOMAIN_FAIL); var f=o.domain().orElseThrow().attributes(); assertEquals(o.outcome().name(),f.get("verdict")); assertNull(bank.put(f.get("case_id"),f)); } assertEquals(120,bank.size()); return bank;
        }
    }
    @Test void unchangedScenarioRetainsEveryOriginalDeliveryLedgerAndSensorInSerialAndParallelWithFullAnalysis(@TempDir Path dir) throws Exception {
        var g=golden(); var spec=original(dir); var serial=run(spec,1); assertEquals(serial,run(spec,4)); assertEquals(IdempotencyAnalysis.expectedIds(),serial.keySet()); var cases=g.getElementsByTagName("case");
        for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); var actual=serial.get(c.getAttribute("id")); assertNotNull(actual); var fs=c.getElementsByTagName("field"); assertEquals(fs.getLength(),actual.size()); for (int k=0;k<fs.getLength();k++) { var e=(Element)fs.item(k); String key=e.getAttribute("name"); assertEquals(e.getAttribute("kind").equals("json")?Json.parse(e.getTextContent(),"golden"):e.getTextContent(),e.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(Idempotency.json(IdempotencyAnalysis.analyse(serial.values().stream().map(IdempotencyAnalysis.Observed::new).toList())),"actual"));
    }
    @Test void existingRuleEngineMatchesAllSixHundredRawBondTruthsAndExactPopulationCertificate(@TempDir Path dir) throws Exception {
        var truth=(Map<?,?>)Json.parse(golden().getElementsByTagName("raw-bond-truth").item(0).getTextContent(),"golden"); var seen=new TreeMap<String,Object>(); var kept=new java.util.TreeSet<String>(); var spec=ScenarioLoader.load(original(dir)).spec(); assertEquals(2,spec.constraints().rules().size()); var config=config(1).config(); var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan(); var budget=MemoryBudget.of(config); var options=GenerationOptions.of(config);
        try (var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);
             var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);
             var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
            assertEquals(BigInteger.valueOf(600),assembly.mandatoryRows()); assertEquals(BigInteger.ONE,assembly.perMandatoryRow()); assertEquals(2,assembly.rules().size()); assertEquals("canonical_identity",assembly.rules().name(0)); assertEquals("peer_baseline",assembly.rules().name(1)); assertTrue(assembly.rules().mandatoryOnly(0)); assertTrue(assembly.rules().mandatoryOnly(1));
            int[] hits=new int[2],survivors=new int[3]; int overlap=0; var rows=assembly.assessed(Cancellation.NONE);
            while (rows.hasNext()) {
                var row=rows.next(); var hit=row.violations(); for (int k=0;k<2;k++) if (hit.get(k)) hits[k]++; if (hit.cardinality()>1) overlap++; for (int k=0;k<=2;k++) if (hit.nextSetBit(0)<0||hit.nextSetBit(0)>=k) survivors[k]++;
                var atoms=new java.util.ArrayList<String>(); var names=assembly.columnSheets(); for (int k=0;k<names.size();k++) if (!names.get(k).equals("HEAD")&&!names.get(k).equals("TAIL")) atoms.add(dictionary.value(row.candidate().labels(k)[0]).text());
                String policy=atoms.get(0).substring(8,atoms.get(0).length()-1),labels="0"+atoms.get(2).charAt(9)+atoms.get(3).charAt(9),cuts=""+atoms.get(4).charAt(7)+atoms.get(5).charAt(7),control=atoms.get(6).substring(11,atoms.get(6).length()-1); String id=new Idempotency.Config(policy,labels,cuts,control).caseId();
                assertNull(seen.put(id,Map.of("canonical_identity",!hit.get(0),"peer_baseline",!hit.get(1)))); if (hit.isEmpty()) kept.add(IdempotencyPlugin.parseAtoms(atoms).caseId());
            }
            assertEquals(truth,seen); assertArrayEquals(new int[]{100,460},hits); assertArrayEquals(new int[]{600,500,120},survivors); assertEquals(80,overlap); assertEquals(IdempotencyAnalysis.expectedIds(),kept);
            var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(1200)); assertTrue(count.exact()); assertEquals(BigInteger.valueOf(480),count.removedEarly()); assertEquals(BigInteger.ZERO,count.removedLate()); assertEquals(BigInteger.ZERO,count.unselected()); assertEquals(BigInteger.valueOf(120),count.executable()); assertEquals(BigInteger.valueOf(80),count.overlap());
        }
        assertEquals(0,budget.reservedBytes());
    }
}
