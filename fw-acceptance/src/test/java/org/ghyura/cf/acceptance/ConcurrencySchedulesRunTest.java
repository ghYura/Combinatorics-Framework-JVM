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
import org.ghyura.cf.fixtures.demos.ConcurrencySchedules;
import org.ghyura.cf.fixtures.demos.ConcurrencySchedulesPlugin;
import org.ghyura.cf.fixtures.demos.ConcurrencyScheduleAnalysis;
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

class ConcurrencySchedulesRunTest {
    private Element golden() throws Exception { var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); try (var in=Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d6-golden.xml"))) { return f.newDocumentBuilder().parse(in).getDocumentElement(); } }
    private Path original(Path dir,String campaign) throws Exception { Path spec=dir.resolve(campaign+".toml"); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d6/"+campaign+"/spec.toml")) { assertNotNull(in); Files.write(spec,in.readAllBytes()); } return spec; }
    private org.ghyura.cf.config.EffectiveConfig config(int workers) { return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",ConcurrencySchedulesPlugin.ID,"D6"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D6"))); }
    private Map<String,Map<String,String>> run(Path spec,String campaign,int workers) throws Exception {
        try (var run=RunService.prepare(ScenarioLoader.load(spec).spec(),config(workers))) {
            var s=run.await(); boolean counter=campaign.equals("counter"); assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString()); assertEquals(BigInteger.valueOf(counter?96:32),s.generated()); assertEquals(BigInteger.valueOf(counter?72:26),s.excluded()); assertEquals(counter?24:6,s.samples()); assertEquals(counter?18:5,s.count(Outcome.PASS)); assertEquals(counter?6:1,s.count(Outcome.DOMAIN_FAIL));
            var bank=new TreeMap<String,Map<String,String>>(); for (var o : run.finals()) { assertTrue(o.outcome()==Outcome.PASS||o.outcome()==Outcome.DOMAIN_FAIL); var f=o.domain().orElseThrow().attributes(); assertEquals(o.outcome().name(),f.get("verdict")); assertNull(bank.put(f.get("case_id"),f)); } assertEquals(counter?24:6,bank.size()); return bank;
        }
    }
    @SuppressWarnings("unchecked") @Test void bothUnchangedScenariosRetainEveryOriginalObservationInSerialAndParallelWithFullObservedAnalysis(@TempDir Path dir) throws Exception {
        var g=golden(); var expected=(Map<String,Object>)Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"); var cases=g.getElementsByTagName("case");
        for (String campaign : List.of("counter","queue")) {
            var spec=original(dir,campaign); var serial=run(spec,campaign,1); assertEquals(serial,run(spec,campaign,4)); assertEquals(ConcurrencyScheduleAnalysis.expectedIds(campaign),serial.keySet());
            for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); if (!c.getAttribute("id").startsWith(campaign+"|")) continue; var actual=serial.get(c.getAttribute("id")); assertNotNull(actual); var fs=c.getElementsByTagName("field"); assertEquals(fs.getLength(),actual.size()); for (int k=0;k<fs.getLength();k++) { var e=(Element)fs.item(k); String key=e.getAttribute("name"); assertEquals(e.getAttribute("kind").equals("json")?Json.parse(e.getTextContent(),"golden"):e.getTextContent(),e.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key); } }
            assertEquals(expected.get(campaign),Json.parse(ConcurrencySchedules.json(ConcurrencyScheduleAnalysis.analyse(campaign,serial.values().stream().map(ConcurrencyScheduleAnalysis.Observed::new).toList())),"actual"));
        }
    }
    @Test void existingRuleEngineAssessesAllRawProductsWithExactBondOverlapSurvivorExclusionAndReservationCertificates(@TempDir Path dir) throws Exception {
        for (String campaign : List.of("counter","queue")) {
            boolean counter=campaign.equals("counter"); var spec=ScenarioLoader.load(original(dir,campaign)).spec(); assertEquals(2,spec.constraints().rules().size()); var config=config(1).config(); var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan(); var budget=MemoryBudget.of(config); var options=GenerationOptions.of(config);
            try (var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);
                 var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);
                 var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
                assertEquals(BigInteger.valueOf(counter?96:32),assembly.mandatoryRows()); assertEquals(BigInteger.ONE,assembly.perMandatoryRow()); assertEquals(2,assembly.rules().size()); assertEquals("two_each",assembly.rules().name(0)); assertEquals(counter?"preemption_cap":"producer_first",assembly.rules().name(1)); assertTrue(assembly.rules().mandatoryOnly(0)); assertTrue(assembly.rules().mandatoryOnly(1));
                int[] hits=new int[2],survivors=new int[3]; int overlap=0; var kept=new java.util.TreeSet<String>(); var rows=assembly.assessed(Cancellation.NONE);
                while (rows.hasNext()) {
                    var row=rows.next(); var hit=row.violations(); for (int k=0;k<2;k++) if (hit.get(k)) hits[k]++; if (hit.cardinality()>1) overlap++; for (int k=0;k<=2;k++) if (hit.nextSetBit(0)<0||hit.nextSetBit(0)>=k) survivors[k]++;
                    var atoms=new java.util.ArrayList<String>(); var names=assembly.columnSheets(); for (int k=0;k<names.size();k++) if (!names.get(k).equals("HEAD")&&!names.get(k).equals("TAIL")) atoms.add(dictionary.value(row.candidate().labels(k)[0]).text());
                    String w=""; for (String a : atoms) if (a.startsWith("set_step")) w+=a.charAt(12); boolean ownTwo=w.chars().filter(t -> t==(counter?'A':'P')).count()!=2; assertEquals(ownTwo,hit.get(0));
                    if (counter) { int cap=Integer.parseInt(atoms.get(1).substring(4,5)); int pre=0; for (int i=0;i<3;i++) { char t=w.charAt(i); long done=w.substring(0,i+1).chars().filter(x -> x==t).count(); if (t!=w.charAt(i+1)&&done<2) pre++; } assertEquals(pre>cap,hit.get(1)); } else assertEquals(w.charAt(0)!='P',hit.get(1));
                    if (hit.isEmpty()) kept.add(ConcurrencySchedulesPlugin.parseAtoms(campaign,atoms).caseId());
                }
                assertArrayEquals(counter?new int[]{60,28}:new int[]{20,16},hits); assertArrayEquals(counter?new int[]{96,36,24}:new int[]{32,12,6},survivors); assertEquals(counter?16:10,overlap); assertEquals(ConcurrencyScheduleAnalysis.expectedIds(campaign),kept);
                var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(150)); assertTrue(count.exact()); assertEquals(BigInteger.valueOf(counter?72:26),count.removedEarly()); assertEquals(BigInteger.ZERO,count.removedLate()); assertEquals(BigInteger.ZERO,count.unselected()); assertEquals(BigInteger.valueOf(counter?24:6),count.executable()); assertEquals(BigInteger.valueOf(counter?16:10),count.overlap());
            }
            assertEquals(0,budget.reservedBytes());
        }
    }
}
