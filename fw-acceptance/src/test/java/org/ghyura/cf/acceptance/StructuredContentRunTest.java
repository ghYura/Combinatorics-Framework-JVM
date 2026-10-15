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
import org.ghyura.cf.model.Json;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StructuredContentRunTest {
 private static Path scenario(Path directory)throws Exception{Path p=directory.resolve("assistant.toml");try(var in=StructuredContentRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/assistant/structured/spec.toml")){assertNotNull(in);Files.write(p,in.readAllBytes());}return p;}
 private static org.ghyura.cf.config.EffectiveConfig config(int workers){return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","local-structured-assistant","local helper"),new ConfigResolver.Override("cf.execution.classpath",Reactor.root().resolve("fw-sut-fixtures/target/classes").toString(),"explicit addon"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"worker invariance"),new ConfigResolver.Override("cf.generation.distinctTables","true","control profile"),new ConfigResolver.Override("cf.assembly.distinct","true","control profile")));}
 @Test void all13StructuredRequestsKeepSourceResponsesUsageAndIdentityAcrossWorkers(@TempDir Path work)throws Exception{
  var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var cases=f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/assistant/structured-chat-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var expected=new ArrayList<Map<?,?>>();for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)Json.parse(cases.item(i).getTextContent(),"golden"));Map<String,Object> serial=null;
  for(int workers:List.of(1,4)){var actual=new TreeMap<String,Object>();var seen=new HashSet<Integer>();try(var run=RunService.prepare(ScenarioLoader.load(scenario(work)).spec(),config(workers))){var summary=run.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());assertEquals(13,summary.samples());assertTrue(summary.reconciliation().consistent());
   for(var o:run.finals()){assertEquals("PASS",o.outcome().name(),o.failureMessage());var d=o.domain().orElseThrow();var request=(Map<?,?>)Json.parse(d.attributes().get("request"),"request");int index=-1;for(int i=0;i<expected.size();i++){var row=expected.get(i);var wanted=(Map<?,?>)row.get("request");if(Objects.equals(request.get("messages"),wanted.get("messages"))&&Objects.equals(request.get("max_tokens"),wanted.get("max_tokens"))){index=i;break;}}assertTrue(index>=0);assertTrue(seen.add(index));var want=expected.get(index);assertEquals(want.get("response"),d.attributes().get("response"));assertEquals("0",d.attributes().get("FW_VAR"));assertEquals(7,d.attributes().size());assertEquals(5,d.metrics().size());var response=(Map<?,?>)Json.parse(d.attributes().get("response_json"),"completion");assertEquals(want.get("usage"),response.get("usage"));assertEquals("chatcmpl-"+o.key().candidateId().hex().substring(0,12),response.get("id"));assertEquals(0,((Number)response.get("created")).longValue());assertEquals(want.get("sources"),Json.parse(d.attributes().get("sources"),"sources"));var usage=(Map<?,?>)want.get("usage");for(String k:List.of("prompt_tokens","completion_tokens","total_tokens"))assertEquals(((Number)usage.get(k)).doubleValue(),d.metrics().get(k));assertEquals(3.,d.metrics().get("document_count"));assertEquals(0.,d.metrics().get("source_count"));actual.put(o.key().candidateId().hex(),Map.of("fields",d.attributes(),"metrics",d.metrics(),"outcome",o.outcome()));
   }}assertEquals(13,seen.size());if(serial==null)serial=actual;else assertEquals(serial,actual);
  }
 }
 @Test void unsupportedStructuredRequestNeverCreatesSuccessfulDomainData(@TempDir Path work)throws Exception{
  Path p=scenario(work);String text=Files.readString(p);String prefix="{\"max_tokens\":";assertTrue(text.contains(prefix));Files.writeString(p,text.replaceFirst(java.util.regex.Pattern.quote(prefix),"{\"unsupported\":1,\"max_tokens\":"));int broken=0;try(var run=RunService.prepare(ScenarioLoader.load(p).spec(),config(1))){assertEquals(RunState.COMPLETED,run.await().state());for(var o:run.finals())if(o.outcome().name().equals("BROKEN")){broken++;assertTrue(o.domain().isEmpty());}else assertEquals("PASS",o.outcome().name());}assertEquals(1,broken);
 }
}
