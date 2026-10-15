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
import org.ghyura.cf.fixtures.demos.WorkflowOrder;
import org.ghyura.cf.fixtures.demos.WorkflowOrderPlugin;
import org.ghyura.cf.fixtures.demos.WorkflowOrderAnalysis;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowOrderRunTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);return f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d3-golden.xml").toFile()).getDocumentElement();}
    private Path original(Path dir,String variant)throws Exception{var p=dir.resolve("d3-"+variant+".toml");try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d3/"+variant+"/spec.toml")){assertNotNull(in);Files.write(p,in.readAllBytes());}return p;}
    private org.ghyura.cf.config.EffectiveConfig config(int workers){return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",WorkflowOrderPlugin.ID,"D3"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D3"),new ConfigResolver.Override("cf.generation.distinctTables","true","original D3 Core distinctify")));}
    private Map<String,Map<String,String>> run(Path path,String phase,int workers)throws Exception{try(var run=RunService.prepare(ScenarioLoader.load(path).spec(),config(workers))){var s=run.await();assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString());assertEquals(BigInteger.valueOf(phase.equals("A")?72:81),s.generated());assertEquals(BigInteger.ZERO,s.excluded());assertEquals(phase.equals("A")?72:81,s.samples());assertEquals(phase.equals("A")?48:66,s.count(Outcome.PASS));assertEquals(phase.equals("A")?24:15,s.count(Outcome.DOMAIN_FAIL));var bank=new TreeMap<String,Map<String,String>>();for(var o:run.finals()){assertTrue(o.outcome()==Outcome.PASS||o.outcome()==Outcome.DOMAIN_FAIL);var f=o.domain().orElseThrow().attributes();assertEquals(o.outcome().name(),f.get("verdict"));assertNull(bank.put(f.get("case_id"),f));}return bank;}}
    @Test void unchangedScenariosMatchSerialParallelAndAlternateChains(@TempDir Path dir)throws Exception{
        var g=golden();Map<String,Map<String,String>> bBank=null;
        for(String phase:List.of("A","B")){var path=original(dir,phase);var serial=run(path,phase,1);assertEquals(serial,run(path,phase,4));if(phase.equals("B"))bBank=serial;assertEquals(WorkflowOrderAnalysis.expectedIds(phase),serial.keySet());var cases=g.getElementsByTagName("case");for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);if(!c.getAttribute("phase").equals(phase))continue;var actual=serial.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(15,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}}var analyses=g.getElementsByTagName("analysis");for(int i=0;i<analyses.getLength();i++)if(((Element)analyses.item(i)).getAttribute("phase").equals(phase))assertEquals(Json.parse(analyses.item(i).getTextContent(),"golden"),Json.parse(WorkflowOrder.json(WorkflowOrderAnalysis.analyse(phase,serial.values().stream().map(WorkflowOrderAnalysis.Observed::new).toList())),"actual"));}
        assertEquals(bBank,run(original(dir,"B-alt"),"B",4));
    }
    @SuppressWarnings("unchecked") @Test void nativeOptionalTailsRepeatedWordsAndAlternateMultisetsKeepExactPopulation(@TempDir Path dir)throws Exception{
        var g=golden();for(String variant:List.of("A","B","B-alt")){String phase=variant.equals("A")?"A":"B";var nodes=g.getElementsByTagName("construction");Map<String,Object> predicted=null;for(int i=0;i<nodes.getLength();i++)if(((Element)nodes.item(i)).getAttribute("variant").equals(variant))predicted=(Map<String,Object>)Json.parse(nodes.item(i).getTextContent(),"golden");assertNotNull(predicted);var spec=ScenarioLoader.load(original(dir,variant)).spec();assertEquals(phase.equals("A")?6:4,spec.sheetCount());assertEquals(0,spec.constraints().rules().size());var cfg=config(1).config();var plan=SeqPlanner.plan(spec,PlanOptions.of(cfg)).plan();var budget=MemoryBudget.of(cfg);var options=GenerationOptions.of(cfg);
            try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),cfg.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(cfg),RenderOptions.of(cfg),cfg.randomSeed(),budget,Cancellation.NONE)){
                assertEquals(phase.equals("A")?WorkflowOrderPlugin.SHEETS_A:WorkflowOrderPlugin.SHEETS_B,assembly.columnSheets());assertEquals(0,assembly.rules().size());assertEquals(0,plan.virtualSheetCount());var codes=new TreeMap<String,Object>();for(var sheet:spec.sheets())codes.put(sheet.name(),Arrays.stream(dictionary.labelsOf(sheet.key())).boxed().toList());assertEquals(predicted.get("predicted_codes"),Json.parse(WorkflowOrder.json(codes),"actual codes"));var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(100));assertTrue(count.exact());assertEquals(BigInteger.valueOf(phase.equals("A")?72:81),count.generated());assertEquals(count.generated(),count.executable());assertEquals(BigInteger.ZERO,count.removedEarly());assertEquals(BigInteger.ZERO,count.removedLate());assertEquals(BigInteger.ZERO,count.unselected());assertEquals(BigInteger.valueOf(phase.equals("A")?4:1),assembly.perMandatoryRow());assertEquals(BigInteger.valueOf(phase.equals("A")?18:81),assembly.mandatoryRows());
                var words=new TreeSet<String>();var rows=assembly.assessed(Cancellation.NONE);int n=0;while(rows.hasNext()){var row=rows.next();var labels=row.candidate().labels(2);assertEquals(3,labels.length);words.add(WorkflowOrderPlugin.parseAtoms(phase,"start(\"correct\")\n",Arrays.stream(labels).mapToObj(x -> dictionary.value(x).text()).toList(),List.of()).core());n++;}assertEquals(phase.equals("A")?72:81,n);assertEquals(new TreeSet<>(WorkflowOrderAnalysis.configurations(phase).stream().map(WorkflowOrder.Config::core).toList()),words);
            }assertEquals(0,budget.reservedBytes());}
    }
}
