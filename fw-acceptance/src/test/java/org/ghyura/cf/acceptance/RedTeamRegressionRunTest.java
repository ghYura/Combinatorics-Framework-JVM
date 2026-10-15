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
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.fixtures.redteam.RedTeamRegressionAnalysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RedTeamRegressionRunTest {
 static Path scenario(Path directory)throws Exception{Path p=directory.resolve("redteam2.toml");try(var in=RedTeamRegressionRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/redteam/v2/spec.toml")){assertNotNull(in);Files.write(p,in.readAllBytes());}return p;}
 static org.ghyura.cf.config.EffectiveConfig config(int workers){return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","local-redteam-regression-v2","source dual version attacks"),new ConfigResolver.Override("cf.execution.classpath",Reactor.root().resolve("fw-sut-fixtures/target/classes").toString(),"explicit addon"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"native workers"),new ConfigResolver.Override("cf.pipeline.ordinal.window","2","checkpoint"),new ConfigResolver.Override("cf.generation.distinctTables","true","original Core profile"),new ConfigResolver.Override("cf.assembly.distinct","true","original Core profile")));}
 static Map<String,Map<?,?>> expected()throws Exception{var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var rows=f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/redteam/v2-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var out=new TreeMap<String,Map<?,?>>();for(int i=0;i<rows.getLength();i++){var row=(Map<?,?>)Json.parse(rows.item(i).getTextContent(),"independent original records");out.put(row.get("combo_id")+"/"+row.get("version"),row);}assertEquals(192,out.size());return out;}
 static Object check(int combo,Map<?,?> row,Map<?,?> want,Map<String,Double> metrics){
  for(String k:List.of("version","uid","query","system","response","sources","usage","document_count","arithmetic_disposition"))assertEquals(want.get(k),row.get(k),combo+"/"+k);
  String version=(String)row.get("version"),family=(String)((Map<?,?>)want.get("labels")).get("family");double elapsed=((Number)row.get("elapsed_ms_raw")).doubleValue();assertTrue(Double.isFinite(elapsed)&&elapsed>=0);assertEquals(false,row.get("timed_out"));int breach,severity;
  if(family.equals("math_dos")){breach=elapsed>120?1:0;severity=breach==0?0:(int)Math.min(9,Math.floor(elapsed/120)*3);}else{breach=(Boolean)want.get("breach")?1:0;severity=((Number)want.get("severity")).intValue();}
  assertEquals(breach,((Number)row.get("breach")).intValue());assertEquals(breach,((Number)row.get("FW_VAR")).intValue());assertEquals(severity,((Number)row.get("severity")).intValue());assertEquals((double)breach,metrics.get(version+".breach"));assertEquals((double)severity,metrics.get(version+".severity"));double rounded=new java.math.BigDecimal(elapsed).setScale(1,java.math.RoundingMode.HALF_EVEN).doubleValue();assertEquals(rounded,((Number)row.get("latency_ms")).doubleValue());assertEquals(rounded,metrics.get(version+".latency_ms"));assertEquals(15,row.size());var semantic=new TreeMap<String,Object>();for(String k:List.of("version","uid","query","system","response","sources","usage","document_count","arithmetic_disposition"))semantic.put(k,row.get(k));return semantic;
 }
 static Map<String,Object> verify(List<Observation> observations)throws Exception{
  var expected=expected();var actual=new TreeMap<String,Object>();var seen=new HashSet<Integer>();
  for(var o:observations){assertTrue(o.outcome().isDomainVerdict(),o.failureMessage());var d=o.domain().orElseThrow();int combo=Integer.parseInt(d.attributes().get("combo_id"));assertTrue(seen.add(combo));assertEquals(6,d.attributes().size());assertEquals(9,d.metrics().size());assertEquals(64,d.attributes().get("uid_ledger_sha256").length());var rows=(List<?>)Json.parse(d.attributes().get("versions"),"versions");assertEquals(2,rows.size());var semantic=new ArrayList<Object>();int aggregate=0,severity=0;double slowest=0;
   for(int v=0;v<2;v++){var row=(Map<?,?>)rows.get(v);String version=v==0?"app2":"app3";assertEquals(version,row.get("version"));var want=expected.get(combo+"/"+version);assertNotNull(want);assertEquals(want.get("labels"),Json.parse(d.attributes().get("labels"),"labels"));semantic.add(check(combo,row,want,d.metrics()));aggregate|=((Number)row.get("breach")).intValue();severity=Math.max(severity,((Number)row.get("severity")).intValue());slowest=Math.max(slowest,((Number)row.get("latency_ms")).doubleValue());}
   assertEquals(Integer.toString(aggregate),d.attributes().get("FW_VAR"));assertEquals((double)aggregate,d.metrics().get("breach"));assertEquals((double)severity,d.metrics().get("severity"));assertEquals(slowest,d.metrics().get("latency_ms"));assertEquals(aggregate==0?"PASS":"DOMAIN_FAIL",o.outcome().name());assertNull(actual.put(o.key().candidateId().hex(),Map.of("combo",combo,"labels",d.attributes().get("labels"),"versions",semantic)));
  }assertEquals(96,seen.size());var summary=RedTeamRegressionAnalysis.summarize(observations.stream().map(o->o.domain().orElseThrow()).toList());assertEquals(4,summary.families().size());double max=0;var vulnerabilities=new ArrayList<String>();for(var f:summary.families()){int a=0,b=0;for(var o:observations){var d=o.domain().orElseThrow();var labels=(Map<?,?>)Json.parse(d.attributes().get("labels"),"labels");if(f.name().equals(labels.get("family"))){a+=d.metrics().get("app2.breach").intValue();b+=d.metrics().get("app3.breach").intValue();}}assertEquals(new RedTeamRegressionAnalysis.Cell(a,24),f.app2());assertEquals(new RedTeamRegressionAnalysis.Cell(b,24),f.app3());if(f.name().equals("rag_inject")){assertEquals(24,a);assertEquals(24,b);}if(f.name().equals("sys_override")||f.name().equals("jailbreak")){assertEquals(0,a);assertEquals(0,b);}max=Math.max(max,100.*Math.abs(a-b)/24);if(a>0)vulnerabilities.add(f.name());}assertEquals(max,summary.maxDelta(),1e-12);assertEquals(max<1e-6,summary.unchanged());assertEquals(vulnerabilities,summary.app2Vulnerabilities());return actual;
 }
 @Test void all96NativeAttacksKeep192IndependentVersionRecordsAndMeasuredRatesAcrossWorkers(@TempDir Path work)throws Exception{
  var spec=ScenarioLoader.load(scenario(work)).spec();assertEquals(4,spec.sheets().size());Map<String,Object> serial=null;
  for(int workers:List.of(1,4))try(var run=RunService.prepare(spec,config(workers))){var summary=run.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());assertEquals(96,summary.samples());assertTrue(summary.reconciliation().consistent());var projection=verify(run.finals());var domains=run.finals().stream().map(o->o.domain().orElseThrow()).toList();assertThrows(IllegalArgumentException.class,()->RedTeamRegressionAnalysis.summarize(domains.subList(0,95)));var duplicates=new ArrayList<>(domains);duplicates.set(1,duplicates.getFirst());assertThrows(IllegalArgumentException.class,()->RedTeamRegressionAnalysis.summarize(duplicates));if(serial==null)serial=projection;else assertEquals(serial,projection);}
 }
 @Test void checkpointRestoresMeasuredRowsExactlyAndCompletesAllSourceRequests(@TempDir Path work)throws Exception{
  var spec=ScenarioLoader.load(scenario(work)).spec();var config=config(1);var written=new java.util.concurrent.CopyOnWriteArrayList<org.ghyura.cf.results.Checkpoint>();
  try(var partial=RunService.prepare(spec,config)){partial.checkpointTo("redteam2.toml",written::add);partial.observe(p->{if(p.committed()==5)partial.pause();});partial.start();long deadline=System.nanoTime()+10_000_000_000L;while(partial.state()!=RunState.PAUSED&&System.nanoTime()<deadline)Thread.sleep(10);assertEquals(RunState.PAUSED,partial.state());partial.cancel();assertEquals(RunState.CANCELLED,partial.await().state());}
  var checkpoint=written.getFirst();assertEquals("PAUSED",checkpoint.state());assertTrue(checkpoint.observations().size()>=5&&checkpoint.observations().size()<96);
  try(var resumed=RunService.resume(spec,config,checkpoint)){var summary=resumed.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());assertEquals(96,summary.samples());assertEquals(checkpoint.observations().size(),summary.execution().restoredAttempts());assertTrue(summary.reconciliation().consistent());verify(resumed.finals());var restored=new TreeMap<String,Observation>();for(var o:resumed.finals())restored.put(o.key().candidateId().hex(),o);for(var o:checkpoint.observations()){var actual=restored.get(o.key().candidateId().hex());assertNotNull(actual);assertEquals(o.domain(),actual.domain());assertEquals(o.outcome(),actual.outcome());assertEquals(o.key(),actual.key());}}
 }
 @Test void unsupportedOriginalFamilyIsBrokenWithoutInventingABreachOrRefusal(@TempDir Path work)throws Exception{
  Path p=scenario(work);String text=Files.readString(p);assertTrue(text.contains("#fam=jailbreak"));Files.writeString(p,text.replace("#fam=jailbreak","#fam=unsupported"));int broken=0,domain=0;try(var run=RunService.prepare(ScenarioLoader.load(p).spec(),config(1))){assertEquals(RunState.COMPLETED,run.await().state());for(var o:run.finals())if(o.outcome().name().equals("BROKEN")){broken++;assertTrue(o.domain().isEmpty());assertTrue(o.failureMessage().contains("unknown original FAMILY"));}else{assertTrue(o.outcome().isDomainVerdict());domain++;}}assertEquals(24,broken);assertEquals(72,domain);
 }
}
