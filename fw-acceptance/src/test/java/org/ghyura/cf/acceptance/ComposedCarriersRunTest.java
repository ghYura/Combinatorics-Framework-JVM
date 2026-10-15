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
import org.ghyura.cf.fixtures.demos.ComposedCarriers;
import org.ghyura.cf.fixtures.demos.ComposedCarriersPlugin;
import org.ghyura.cf.fixtures.demos.ComposedCarriersAnalysis;
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

class ComposedCarriersRunTest {
    private Element golden() throws Exception {var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);return f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d13b-golden.xml").toFile()).getDocumentElement();}
    private Path original(Path dir)throws Exception {Path p=dir.resolve("d13b.toml");try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13b/spec.toml")){assertNotNull(in);Files.write(p,in.readAllBytes());}return p;}
    private org.ghyura.cf.config.EffectiveConfig config(int workers){var overrides=new ArrayList<ConfigResolver.Override>();ComposedCarriersPlugin.SETTINGS.forEach((k,v) -> overrides.add(new ConfigResolver.Override(k,v,"D13b")));overrides.add(new ConfigResolver.Override("cf.execution.sut",ComposedCarriersPlugin.ID,"D13b"));overrides.add(new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D13b"));return ConfigResolver.resolve(null,Map.of(),overrides);}
    private Map<String,Map<String,String>> run(Path spec,int workers)throws Exception {try(var run=RunService.prepare(ScenarioLoader.load(spec).spec(),config(workers))){var s=run.await();assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString());assertEquals(BigInteger.valueOf(108),s.generated());assertEquals(BigInteger.ZERO,s.excluded());assertEquals(108,s.samples());assertEquals(80,s.count(Outcome.PASS));assertEquals(28,s.count(Outcome.DOMAIN_FAIL));var bank=new TreeMap<String,Map<String,String>>();for(var o:run.finals()){assertTrue(o.outcome()==Outcome.PASS||o.outcome()==Outcome.DOMAIN_FAIL);var f=o.domain().orElseThrow().attributes();assertEquals(o.outcome().name(),f.get("verdict"));assertNull(bank.put(f.get("case_id"),f));}return bank;}}
    @Test void unchangedComposedScenarioRetainsEveryOriginalTreeTraceAndEffectInSerialAndParallelWithFullAnalysis(@TempDir Path dir)throws Exception {
        var g=golden();var spec=original(dir);var serial=run(spec,1);assertEquals(serial,run(spec,4));assertEquals(ComposedCarriersAnalysis.expectedIds(),serial.keySet());var cases=g.getElementsByTagName("case");for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);var actual=serial.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(fields.getLength(),actual.size());for(int k=0;k<fields.getLength();k++){var f=(Element)fields.item(k);String key=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key);}}
        assertEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(ComposedCarriers.json(ComposedCarriersAnalysis.analyse(serial.values().stream().map(ComposedCarriersAnalysis.Observed::new).toList())),"actual"));
    }
    @SuppressWarnings("unchecked") @Test void existingGeneratorMatchesEveryDictionaryCodeWholePairRewriteAndBothBracesWithOriginalSettings(@TempDir Path dir)throws Exception {
        var predicted=(Map<String,Object>)Json.parse(golden().getElementsByTagName("construction").item(0).getTextContent(),"golden");var spec=ScenarioLoader.load(original(dir)).spec();var config=config(1).config();var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan();var budget=MemoryBudget.of(config);var options=GenerationOptions.of(config);assertEquals(16,spec.sheetCount());assertEquals(0,plan.virtualSheetCount());assertTrue(options.distinctTables());assertTrue(config.assemblyDistinct());assertEquals("WARN",options.replace().pattern().name());assertEquals("SUMMARY",options.replace().diagnostics().name());var settings=new java.util.Properties();try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13b/run.properties")){assertNotNull(in);settings.load(in);}ComposedCarriersPlugin.SETTINGS.forEach((k,v) -> assertEquals(v,settings.getProperty(k)));
        try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
            assertEquals(ComposedCarriersPlugin.SHEETS,assembly.columnSheets());assertEquals(0,assembly.rules().size());var codes=new TreeMap<String,Object>();for(var sheet:spec.sheets())codes.put(sheet.name(),Arrays.stream(dictionary.labelsOf(sheet.key())).boxed().toList());assertEquals(predicted.get("predicted_codes"),Json.parse(ComposedCarriers.json(codes),"codes"));var expectedModel=(Map<String,Object>)predicted.get("model");var roots=new ArrayList<String>();var structures=new ArrayList<Object>();
            for(String name:List.of("CHUNKS","INNER","ROOT")){var data=new ArrayList<List<Integer>>();try(var cur=generated.result().sheet(name).orElseThrow().rows().open(Cancellation.NONE)){while(cur.hasNext()){var labels=Arrays.stream(cur.next().keys().labels()).boxed().toList();data.add(labels);if(name.equals("ROOT")){String text=String.join("",labels.stream().map(x -> dictionary.value(x).text()).toList());roots.add(text);structures.add(ComposedCarriers.decodeRoot(text).document());}}}assertEquals(expectedModel.get(name),Json.parse(ComposedCarriers.json(data),"actual "+name));}assertEquals(predicted.get("ROOT_text"),roots);assertEquals(predicted.get("ROOT_structure"),Json.parse(ComposedCarriers.json(structures),"structures"));
            var summaries=generated.result().diagnostics().stream().filter(x -> x.code().name().equals("GEN_REPLACEMENT_SUMMARY")).toList();assertEquals(1,summaries.size());String message=summaries.getFirst().message();assertTrue(message.contains("rows seen=2 rewritten=2 dropped=0"),message);assertTrue(message.contains("\"\\[\"=2"),message);assertTrue(message.contains("\"\\]\"=2"),message);assertEquals(1,generated.result().diagnostics().size());
            var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(200));assertTrue(count.exact());assertEquals(BigInteger.valueOf(108),count.generated());assertEquals(BigInteger.valueOf(108),count.executable());assertEquals(BigInteger.ZERO,count.removedEarly());assertEquals(BigInteger.ZERO,count.removedLate());assertEquals(BigInteger.ZERO,count.unselected());assertEquals(BigInteger.ONE,assembly.perMandatoryRow());
        }
        assertEquals(0,budget.reservedBytes());
    }
}
