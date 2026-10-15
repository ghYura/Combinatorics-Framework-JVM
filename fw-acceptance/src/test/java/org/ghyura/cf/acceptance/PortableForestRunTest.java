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
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.ScenarioLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PortableForestRunTest {
 static Path scenario(Path work)throws Exception{Path p=work.resolve("forest.toml");try(var in=PortableForestRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/assistant/forest/spec.toml")){assertNotNull(in);Files.write(p,in.readAllBytes());}return p;}
 static org.ghyura.cf.config.EffectiveConfig config(int workers){return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","local-surrogate-forest","fitted source model"),new ConfigResolver.Override("cf.execution.classpath",Reactor.root().resolve("fw-sut-fixtures/target/classes").toString(),"explicit addon"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"worker invariance")));}
 static Map<String,Map<?,?>> expected()throws Exception{var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var rows=f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/assistant/forest-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var result=new TreeMap<String,Map<?,?>>();for(int i=0;i<rows.getLength();i++){var row=(Map<?,?>)Json.parse(rows.item(i).getTextContent(),"preregistered");if(row.get("kind").equals("native-cartesian"))result.put(key((List<?>)row.get("features")),row);}assertEquals(625,result.size());return result;}
 static String key(List<?> vector){return vector.stream().map(x->Double.toHexString(((Number)x).doubleValue())).collect(java.util.stream.Collectors.joining("/"));}
 static void check(Map<?,?> fields,Map<String,Double> metrics,Map<?,?> want){assertEquals(5,fields.size());assertEquals(7,metrics.size());assertEquals("0",fields.get("FW_VAR"));assertEquals("66b1156aaf3de578a97c64123318250d9fcd7a37070a89dea45b598e23e087a0",fields.get("model_asset_sha256"));assertEquals("3fb05548af9b73c384eaab8297b0e101f964c11f1da5ab8b69c4506551e1b0e0",fields.get("source_model_sha256"));var actual=(Map<?,?>)Json.parse((String)fields.get("prediction_json"),"actual probabilities");assertEquals(4,actual.size());assertEquals(want.get("prediction"),actual.get("prediction"));assertEquals(want.get("classes"),actual.get("classes"));assertEquals(((Number)want.get("confidence")).doubleValue(),((Number)actual.get("confidence")).doubleValue());assertEquals(((Number)want.get("confidence")).doubleValue(),metrics.get("confidence"));assertEquals(((Number)want.get("prediction")).doubleValue(),metrics.get("prediction"));var probabilities=(List<?>)want.get("probabilities");var observed=(List<?>)actual.get("probabilities");for(int c=0;c<2;c++){double expected=((Number)probabilities.get(c)).doubleValue();assertEquals(expected,((Number)observed.get(c)).doubleValue());assertEquals(expected,metrics.get("probability"+c));}assertEquals(100.,metrics.get("tree_count"));assertEquals(1274.,metrics.get("node_count"));assertEquals(4.,metrics.get("feature_count"));}
 @Test void all625NativeFittedModelVectorsKeepIndependentProbabilitiesAndProvenanceAcrossWorkers(@TempDir Path work)throws Exception{
  var expected=expected();var spec=ScenarioLoader.load(scenario(work)).spec();Map<String,Object> serial=null;for(int workers:List.of(1,4)){var actual=new TreeMap<String,Object>();var seen=new HashSet<String>();try(var run=RunService.prepare(spec,config(workers))){var summary=run.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());assertEquals(625,summary.samples());assertTrue(summary.reconciliation().consistent());for(var o:run.finals()){assertEquals("PASS",o.outcome().name(),o.failureMessage());var d=o.domain().orElseThrow();String key=key((List<?>)Json.parse(d.attributes().get("features"),"features"));assertTrue(seen.add(key));var want=expected.get(key);assertNotNull(want);check(d.attributes(),d.metrics(),want);assertNull(actual.put(o.key().candidateId().hex(),Map.of("metrics",d.metrics(),"fields",d.attributes())));}}assertEquals(expected.keySet(),seen);if(serial==null)serial=actual;else assertEquals(serial,actual);}
 }
 @Test void nonfiniteNativeFeatureIsBrokenAndCannotPretendToBeAnOracleClass(@TempDir Path work)throws Exception{
  Path p=scenario(work);String text=Files.readString(p);assertTrue(text.contains("0.0"));Files.writeString(p,text.replaceFirst(java.util.regex.Pattern.quote("0.0"),"NaN"));int broken=0,pass=0;try(var run=RunService.prepare(ScenarioLoader.load(p).spec(),config(1))){assertEquals(RunState.COMPLETED,run.await().state());for(var o:run.finals())if(o.outcome().name().equals("BROKEN")){broken++;assertTrue(o.domain().isEmpty());assertTrue(o.failureMessage().contains("finite features required"));}else{assertEquals("PASS",o.outcome().name());pass++;}}assertEquals(125,broken);assertEquals(500,pass);
 }
}
