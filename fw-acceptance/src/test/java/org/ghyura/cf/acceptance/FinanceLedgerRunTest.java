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
class FinanceLedgerRunTest {
 private static Path scenario(Path directory)throws Exception{Path p=directory.resolve("finance-ledger.toml");try(var in=FinanceLedgerRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/finance/ledger/spec.toml")){assertNotNull(in);Files.write(p,in.readAllBytes());}return p;}
 private static org.ghyura.cf.config.EffectiveConfig config(int workers){return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","finance-stack-ledger","local helper"),new ConfigResolver.Override("cf.execution.classpath",Reactor.root().resolve("fw-sut-fixtures/target/classes").toString(),"explicit addon"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"worker invariance"),new ConfigResolver.Override("cf.generation.distinctTables","true","control profile"),new ConfigResolver.Override("cf.assembly.distinct","true","control profile")));}
 @Test void all72LedgerScriptsKeep336OperationsFullStateJournalErrorsAndAuditAcrossWorkers(@TempDir Path work)throws Exception{
  var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var cases=f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/ledger-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var expected=new ArrayList<Map<?,?>>();for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)Json.parse(cases.item(i).getTextContent(),"golden"));Map<String,Object> serial=null;
  for(int workers:List.of(1,4)){var actual=new TreeMap<String,Object>();var seen=new HashSet<Integer>();try(var run=RunService.prepare(ScenarioLoader.load(scenario(work)).spec(),config(workers))){var summary=run.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());assertEquals(72,summary.samples());assertTrue(summary.reconciliation().consistent());
   for(var o:run.finals()){assertEquals("PASS",o.outcome().name(),o.failureMessage());var d=o.domain().orElseThrow();var request=(Map<?,?>)Json.parse(d.attributes().get("request"),"request");int index=-1;for(int i=0;i<expected.size();i++){var row=expected.get(i);var wanted=(Map<?,?>)row.get("request");if(Objects.equals(request,wanted)){index=i;break;}}assertTrue(index>=0);assertTrue(seen.add(index));var want=expected.get(index);assertEquals("0",d.attributes().get("FW_VAR"));assertEquals(10,d.attributes().size());assertEquals(8,d.metrics().size());var gold=(Map<?,?>)want.get("expected");for(String key:List.of("results","accounts","cards","card_tokens","entries","debit_totals","credit_totals","events"))assertEquals(gold.get(key),Json.parse(d.attributes().get(key),"observed "+key),"script "+index+" field "+key);var results=(List<?>)gold.get("results");assertEquals((double)results.size(),d.metrics().get("operations"));assertEquals((double)results.stream().filter(v->((Map<?,?>)v).containsKey("error")).count(),d.metrics().get("error_count"));for(String key:List.of("accounts","cards","entries","events"))assertEquals((double)((List<?>)gold.get(key)).size(),d.metrics().get(Map.of("accounts","account_count","cards","card_count","entries","entry_count","events","event_count").get(key)));for(String key:List.of("debit_totals","credit_totals"))assertEquals(((Map<?,?>)gold.get(key)).values().stream().map(Number.class::cast).mapToDouble(Number::doubleValue).sum(),d.metrics().get(key.equals("debit_totals")?"debit_total":"credit_total"));actual.put(o.key().candidateId().hex(),Map.of("fields",d.attributes(),"metrics",d.metrics(),"outcome",o.outcome()));
   }}assertEquals(72,seen.size());if(serial==null)serial=actual;else assertEquals(serial,actual);
  }
 }
 @Test void unsupportedFinanceLedgerRequestNeverCreatesSuccessfulDomainData(@TempDir Path work)throws Exception{
  Path p=scenario(work);String text=Files.readString(p);String prefix="{\"bank\":";assertTrue(text.contains(prefix));Files.writeString(p,text.replaceFirst(java.util.regex.Pattern.quote(prefix),"{\"unsupported\":1,\"bank\":"));int broken=0;try(var run=RunService.prepare(ScenarioLoader.load(p).spec(),config(1))){assertEquals(RunState.COMPLETED,run.await().state());for(var o:run.finals())if(o.outcome().name().equals("BROKEN")){broken++;assertTrue(o.domain().isEmpty());}else assertEquals("PASS",o.outcome().name());}assertEquals(1,broken);
 }
}
