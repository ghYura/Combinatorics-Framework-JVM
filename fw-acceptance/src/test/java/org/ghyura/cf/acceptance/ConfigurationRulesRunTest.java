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
import org.ghyura.cf.constraints.Coverage;
import org.ghyura.cf.fixtures.demos.ConfigurationRules;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.plan.PlanOptions;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationRulesRunTest {
    private Path original(Path dir,String phase)throws Exception{Path target=dir.resolve(phase+".toml");try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d4/"+phase+"/spec.toml")){assertNotNull(in);Files.write(target,in.readAllBytes());}return target;}
    private org.ghyura.cf.config.EffectiveConfig config(int workers){return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","demo-d4-configuration-rules","D4"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D4"),new ConfigResolver.Override("cf.generation.distinctTables","true","original Core settings"),new ConfigResolver.Override("cf.assembly.distinct","true","original Core settings")));}
    @Test void bothOriginalScenariosHaveIdenticalSerialParallelObservationsAndExactFaultDistributions(@TempDir Path dir)throws Exception{
        for(String phase:List.of("main","controls")){
            var loaded=ScenarioLoader.load(original(dir,phase));assertEquals(phase.equals("main")?6:0,loaded.spec().constraints().rules().size());Map<String,Object> first=null;
            for(int workers:List.of(1,4))try(var run=RunService.prepare(loaded.spec(),config(workers))){
                var summary=run.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());
                assertEquals(BigInteger.valueOf(phase.equals("main")?288:18),summary.generated());assertEquals(BigInteger.valueOf(phase.equals("main")?222:0),summary.excluded());assertEquals(phase.equals("main")?66:18,summary.samples());assertEquals(phase.equals("main")?48:17,summary.count(Outcome.PASS));assertEquals(phase.equals("main")?18:1,summary.count(Outcome.DOMAIN_FAIL));assertEquals(0,summary.count(Outcome.BROKEN));
                Map<String,Object> observations=new TreeMap<>();Map<String,Long> classes=new TreeMap<>();
                for(var observation:run.finals()){var attributes=observation.domain().orElseThrow().attributes();observations.put(attributes.get("case"),observation.domain());classes.merge(attributes.get("failure_classes"),1L,Long::sum);}
                assertEquals(summary.samples(),observations.size());assertEquals(phase.equals("main")?Map.of("",48L,"wrong_headers",18L):Map.of("",17L,"wrong_acceptance",1L),classes);
                if(first==null)first=observations;else assertEquals(first,observations);
            }
        }
    }
    @Test void theRealRuleEnginePreservesAllMandatoryTruthCountsAndDefersOnlyR6(@TempDir Path dir)throws Exception{
        var spec=ScenarioLoader.load(original(dir,"main")).spec();var config=config(1).config();var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan();var budget=MemoryBudget.of(config);var options=GenerationOptions.of(config);
        try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);
            var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);
            var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)){
            assertEquals(BigInteger.valueOf(144),assembly.mandatoryRows());assertEquals(BigInteger.TWO,assembly.perMandatoryRow());assertEquals(6,assembly.rules().size());
            for(int i=0;i<6;i++){assertEquals("R"+(i+1),assembly.rules().name(i));assertEquals(i<5,assembly.rules().mandatoryOnly(i));}
            int[] matched=new int[5],survivors=new int[6];var rows=assembly.assessed(Cancellation.NONE);
            while(rows.hasNext()){var row=rows.next();if(!row.candidate().absentBranch())continue;var hit=row.violations();for(int i=0;i<5;i++)if(hit.get(i))matched[i]++;for(int k=0;k<=5;k++)if(hit.nextSetBit(0)<0||hit.nextSetBit(0)>=k)survivors[k]++;}
            assertArrayEquals(new int[]{36,48,36,48,24},matched);assertArrayEquals(new int[]{144,108,72,63,51,36},survivors);
            var counts=assembly.count(Cancellation.NONE,BigInteger.valueOf(1000));assertTrue(counts.exact());assertEquals(BigInteger.valueOf(216),counts.removedEarly());assertEquals(BigInteger.valueOf(6),counts.removedLate());assertEquals(BigInteger.valueOf(66),counts.executable());
        }
        assertEquals(0,budget.reservedBytes());
    }
    @Test void existingSetCoverSelectsTheOriginalNineRowsAndObservedVerdictsProveWrongSieveMasking(@TempDir Path dir)throws Exception{
        var spec=ScenarioLoader.load(original(dir,"main")).spec();List<ConfigurationRules.Observed> observations=new ArrayList<>();
        try(var run=RunService.prepare(spec,config(4))){assertEquals(RunState.COMPLETED,run.await().state());for(var o:run.finals())observations.add(ConfigurationRules.observed(o.domain().orElseThrow().attributes(),o.outcome().name()));}
        Map<String,ConfigurationRules.Config> bank=new TreeMap<>();observations.forEach(o->bank.put(o.config().key(),o.config()));var keys=new ArrayList<>(bank.keySet());var levels=new ArrayList<List<String>>();for(int i=0;i<6;i++){final int column=i;levels.add(bank.values().stream().map(c->c.levels().get(column)).distinct().sorted().toList());}
        List<int[]> tuples=new ArrayList<>();for(var c:bank.values()){int[] row=new int[6];for(int i=0;i<6;i++)row[i]=levels.get(i).indexOf(c.levels().get(i));tuples.add(row);}
        var coverage=Coverage.select(tuples,levels.stream().mapToInt(List::size).toArray(),2,Coverage.Method.SET_COVER,Cancellation.NONE);assertEquals(60,coverage.feasible());assertEquals(60,coverage.covered());assertEquals(9,coverage.kept().length);
        var selected=Arrays.stream(coverage.kept()).mapToObj(keys::get).toList();var certificate=ConfigurationRules.certify(observations,selected);assertEquals(certificate.obligations(),certificate.covered());
        assertEquals(54,certificate.wrongSieveRemoved());assertEquals(12,certificate.wrongSieveRemaining());assertEquals(0,certificate.wrongSieveRemainingFailures());assertEquals(18,certificate.wrongSieveHiddenFailures());assertTrue(certificate.detections().get("correct").isEmpty());assertFalse(certificate.detections().get("drops_gzip").isEmpty());assertTrue(certificate.detections().get("ignores_debug_rule").isEmpty());
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        try(var in=Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d4-golden.xml"))){var root=factory.newDocumentBuilder().parse(in).getDocumentElement();var rows=root.getElementsByTagName("pairwise-row");List<String> original=new ArrayList<>();for(int i=0;i<rows.getLength();i++)original.add(((org.w3c.dom.Element)rows.item(i)).getAttribute("key"));assertEquals(original.stream().sorted().toList(),selected);var expected=root.getElementsByTagName("obligation");java.util.Set<String> obligations=new java.util.TreeSet<>();for(int i=0;i<expected.getLength();i++)obligations.add(((org.w3c.dom.Element)expected.item(i)).getAttribute("id"));assertEquals(obligations,certificate.obligations());}
    }
}
