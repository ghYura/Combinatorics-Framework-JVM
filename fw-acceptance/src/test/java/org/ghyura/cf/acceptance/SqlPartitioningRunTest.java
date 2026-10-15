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
import org.ghyura.cf.fixtures.demos.SqlPartitioning;
import org.ghyura.cf.fixtures.demos.SqlPartitioningPlugin;
import org.ghyura.cf.fixtures.demos.SqlPartitioningAnalysis;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class SqlPartitioningRunTest {
    private Element golden()throws Exception{var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);return f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d14b-golden.xml").toFile()).getDocumentElement();}
    private Path original(Path dir)throws Exception{var p=dir.resolve("d14b.toml");try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d14b/spec.toml")){assertNotNull(in);Files.write(p,in.readAllBytes());}return p;}
    private org.ghyura.cf.config.EffectiveConfig config(int workers){return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",SqlPartitioningPlugin.ID,"D14b"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D14b")));}
    private Map<String,Map<String,String>> run(Path path,int workers)throws Exception{try(var run=RunService.prepare(ScenarioLoader.load(path).spec(),config(workers))){var s=run.await();assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString());assertEquals(BigInteger.valueOf(72),s.generated());assertEquals(BigInteger.ZERO,s.excluded());assertEquals(72,s.samples());assertEquals(36,s.count(Outcome.PASS));assertEquals(36,s.count(Outcome.DOMAIN_FAIL));var bank=new TreeMap<String,Map<String,String>>();for(var o:run.finals()){assertTrue(o.outcome()==Outcome.PASS||o.outcome()==Outcome.DOMAIN_FAIL);var f=o.domain().orElseThrow().attributes();assertEquals(o.outcome().name(),f.get("verdict"));assertNull(bank.put(f.get("id"),f));}return bank;}}
    @Test void unchangedScenarioRetainsEveryQueryRowBagAndUnionRelationInSerialAndParallel(@TempDir Path dir)throws Exception{
        var g=golden();var path=original(dir);var serial=run(path,1);assertEquals(serial,run(path,4));assertEquals(SqlPartitioningAnalysis.expectedIds(),serial.keySet());var cases=g.getElementsByTagName("case");for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=serial.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(17,actual.size());for(int j=0;j<fields.getLength();j++){var f=(Element)fields.item(j);String k=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(k),"actual"):actual.get(k),c.getAttribute("id")+" "+k);}}
        var rows=serial.values().stream().map(SqlPartitioningAnalysis.Observed::new).toList();for(var pair:List.of(Map.entry("analysis",SqlPartitioningAnalysis.analyse(rows)),Map.entry("kleene",SqlPartitioningAnalysis.kleene()),Map.entry("truth",SqlPartitioningAnalysis.truth()),Map.entry("fixture",SqlPartitioningAnalysis.fixture())))assertEquals(Json.parse(g.getElementsByTagName(pair.getKey()).item(0).getTextContent(),"golden"),Json.parse(SqlPartitioning.json(pair.getValue()),"actual"));
    }
    @SuppressWarnings("unchecked") @Test void existingGeneratorRetainsFiveOriginalSheetsDictionaryAndExactUnfilteredPopulation(@TempDir Path dir)throws Exception{
        var predicted=(Map<String,Object>)Json.parse(golden().getElementsByTagName("construction").item(0).getTextContent(),"golden");var spec=ScenarioLoader.load(original(dir)).spec();assertEquals(5,spec.sheetCount());assertEquals(0,spec.constraints().rules().size());var cfg=config(1).config();var plan=SeqPlanner.plan(spec,PlanOptions.of(cfg)).plan();var budget=MemoryBudget.of(cfg);var options=GenerationOptions.of(cfg);
        try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),cfg.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(cfg),RenderOptions.of(cfg),cfg.randomSeed(),budget,Cancellation.NONE)){
            assertEquals(SqlPartitioningPlugin.SHEETS,assembly.columnSheets());assertEquals(0,assembly.rules().size());assertEquals(0,plan.virtualSheetCount());var codes=new TreeMap<String,Object>();for(var sheet:spec.sheets())codes.put(sheet.name(),Arrays.stream(dictionary.labelsOf(sheet.key())).boxed().toList());assertEquals(predicted.get("predicted_codes"),Json.parse(SqlPartitioning.json(codes),"actual codes"));var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(100));assertTrue(count.exact());assertEquals(BigInteger.valueOf(72),count.generated());assertEquals(BigInteger.valueOf(72),count.executable());assertEquals(BigInteger.ZERO,count.removedEarly());assertEquals(BigInteger.ZERO,count.removedLate());assertEquals(BigInteger.ZERO,count.unselected());assertEquals(BigInteger.ONE,assembly.perMandatoryRow());
        }assertEquals(0,budget.reservedBytes());
    }
}
