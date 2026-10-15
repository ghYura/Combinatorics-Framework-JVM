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
import org.ghyura.cf.fixtures.demos.AgentMessageOrder;
import org.ghyura.cf.fixtures.demos.AgentMessageOrderPlugin;
import org.ghyura.cf.fixtures.demos.AgentMessageOrderAnalysis;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class AgentMessageOrderRunTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);return f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d13c-golden.xml").toFile()).getDocumentElement();}
    private Path original(Path dir)throws Exception{Path p=dir.resolve("spec.toml");for(String n:List.of("spec.toml","demo.constraints.json"))try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13c/"+n)){assertNotNull(in);Files.write(dir.resolve(n),in.readAllBytes());}return p;}
    private org.ghyura.cf.config.EffectiveConfig config(int workers){return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",AgentMessageOrderPlugin.ID,"D13c"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D13c")));}
    private Map<String,Map<String,String>> run(Path path,int workers)throws Exception{try(var run=RunService.prepare(ScenarioLoader.load(path).spec(),config(workers))){var s=run.await();assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString());assertEquals(BigInteger.valueOf(288),s.generated());assertEquals(BigInteger.valueOf(234),s.excluded());assertEquals(54,s.samples());assertEquals(36,s.count(Outcome.PASS));assertEquals(18,s.count(Outcome.DOMAIN_FAIL));var bank=new TreeMap<String,Map<String,String>>();for(var o:run.finals()){assertTrue(o.outcome()==Outcome.PASS||o.outcome()==Outcome.DOMAIN_FAIL);var f=o.domain().orElseThrow().attributes();assertEquals(o.outcome().name(),f.get("verdict"));assertNull(bank.put(f.get("case_id"),f));}return bank;}}
    @Test void unchangedScenarioProducesEveryOriginalObservationInSerialAndParallelWithCompleteRecordedAnalysis(@TempDir Path dir)throws Exception{
        var g=golden();var path=original(dir);var serial=run(path,1);assertEquals(serial,run(path,4));assertEquals(AgentMessageOrderAnalysis.expectedIds(),serial.keySet());var cases=g.getElementsByTagName("case");assertEquals(54,cases.getLength());for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=serial.get(c.getAttribute("id"));var fields=c.getElementsByTagName("field");assertEquals(19,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}}
        assertEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(AgentMessageOrder.json(AgentMessageOrderAnalysis.analyse(serial.values().stream().map(AgentMessageOrderAnalysis.Observed::new).toList())),"actual"));
    }
    @SuppressWarnings("unchecked") @Test void existingSieveMatchesAllTwoHundredEightyEightRawBondTruthsOverlapAndSequentialSurvivors(@TempDir Path dir)throws Exception{
        var g=golden();var predicted=(Map<String,Object>)Json.parse(g.getElementsByTagName("raw-bond-truth").item(0).getTextContent(),"golden");var spec=ScenarioLoader.load(original(dir)).spec();assertEquals(9,spec.sheetCount());assertEquals(3,spec.constraints().rules().size());var cfg=config(1).config();var plan=SeqPlanner.plan(spec,PlanOptions.of(cfg)).plan();var budget=MemoryBudget.of(cfg);var options=GenerationOptions.of(cfg);var truth=new TreeMap<String,Object>();var hits=new TreeMap<String,Integer>();int overlap=0,removed=0;int[] sequential=new int[4];
        try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),cfg.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(cfg),RenderOptions.of(cfg),cfg.randomSeed(),budget,Cancellation.NONE)){
            assertEquals(AgentMessageOrderPlugin.SHEETS,assembly.columnSheets());assertEquals(3,assembly.rules().size());for(int k=0;k<3;k++){assertTrue(assembly.rules().mandatoryOnly(k));hits.put(assembly.rules().name(k),0);}var rows=assembly.assessed(Cancellation.NONE);while(rows.hasNext()){var row=rows.next();var atoms=new TreeMap<String,String>();for(int k=0;k<assembly.columnSheets().size();k++){var labels=row.candidate().labels(k);assertEquals(1,labels.length);atoms.put(assembly.columnSheets().get(k),dictionary.value(labels[0]).text());}var violations=row.violations();var rules=new TreeMap<String,Boolean>();sequential[0]++;for(int k=0;k<3;k++){String name=assembly.rules().name(k);rules.put(name,!violations.get(k));if(violations.get(k))hits.merge(name,1,Integer::sum);if(violations.nextSetBit(0)<0||violations.nextSetBit(0)>k)sequential[k+1]++;}if(!violations.isEmpty())removed++;if(violations.cardinality()>1)overlap++;assertNull(truth.put(AgentMessageOrderPlugin.parseAtoms(atoms).caseId(),rules));}assertEquals(288,truth.size());assertEquals(predicted,Json.parse(AgentMessageOrder.json(truth),"actual truth"));assertEquals(Map.of("two_each",180,"causal_ready",72,"preemption_cap",84),hits);assertEquals(90,overlap);assertEquals(234,removed);assertArrayEquals(new int[]{288,108,81,54},sequential);var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(400));assertTrue(count.exact());assertEquals(BigInteger.valueOf(288),count.generated());assertEquals(BigInteger.valueOf(234),count.removedEarly());assertEquals(BigInteger.ZERO,count.removedLate());assertEquals(BigInteger.valueOf(54),count.executable());
        }assertEquals(0,budget.reservedBytes());
    }
}
