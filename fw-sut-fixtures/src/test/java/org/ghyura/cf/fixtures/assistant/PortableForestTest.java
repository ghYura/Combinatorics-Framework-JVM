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

package org.ghyura.cf.fixtures.assistant;
import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PortableForestTest {
 @Test void all3916IndependentSourceModelPredictionsProbabilitiesAndFloat32BoundariesMatchExactly()throws Exception{
  var model=PortableForest.bundled();assertEquals(4,model.featureCount());assertEquals(100,model.treeCount());assertEquals(1274,model.nodeCount());assertEquals(List.of(0,1),model.classes());assertEquals("3fb05548af9b73c384eaab8297b0e101f964c11f1da5ab8b69c4506551e1b0e0",model.sourceDigest());var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("forest-golden.xml")){assertNotNull(in);var root=f.newDocumentBuilder().parse(in).getDocumentElement();assertEquals(model.digest(),root.getAttribute("asset_sha256"));var cases=root.getElementsByTagName("case");assertEquals(3916,cases.getLength());int zero=0,one=0;for(int i=0;i<cases.getLength();i++){var want=(Map<?,?>)Json.parse(cases.item(i).getTextContent(),"independent neutral source model");var features=((List<?>)want.get("features")).stream().map(x->((Number)x).doubleValue()).toList();var actual=model.infer(features);assertEquals(((Number)want.get("prediction")).intValue(),actual.prediction());assertEquals(Double.doubleToRawLongBits(((Number)want.get("confidence")).doubleValue()),Double.doubleToRawLongBits(actual.confidence()),"confidence "+i);var probabilities=(List<?>)want.get("probabilities");for(int c=0;c<2;c++)assertEquals(Double.doubleToRawLongBits(((Number)probabilities.get(c)).doubleValue()),Double.doubleToRawLongBits(actual.probabilities().get(c)),"probability "+i+"/"+c);assertEquals(List.of(0,1),actual.classes());assertEquals(new LocalAssistant.Prediction(actual.prediction(),actual.confidence()),model.predict(features));if(actual.prediction()==0)zero++;else one++;}assertEquals(403,zero);assertEquals(3513,one);}
 }
 @Test void neutralSnapshotsAndFittedDimensionBoundsRejectMalformedModelsWithoutLosingTheGoodArtifact(@TempDir Path work)throws Exception{
  var model=PortableForest.bundled();Path p=work.resolve("forest.json");model.save(p);var loaded=PortableForest.load(p);assertEquals(model.digest(),loaded.digest());assertEquals(model.infer(List.of(0.,0.,0.,0.)),loaded.infer(List.of(0.,0.,0.,0.)));byte[] good=Files.readAllBytes(p);assertThrows(IllegalArgumentException.class,()->model.infer(List.of(1.)));assertThrows(IllegalArgumentException.class,()->model.infer(List.of(Double.NaN,0.,0.,0.)));assertThrows(IllegalArgumentException.class,()->model.infer(List.of(Double.POSITIVE_INFINITY,0.,0.,0.)));assertThrows(IllegalArgumentException.class,()->model.infer(List.of(Double.MAX_VALUE,0.,0.,0.)));assertThrows(UnsupportedOperationException.class,()->model.classes().add(7));
  Path bad=work.resolve("bad.json");String text=Files.readString(p);assertTrue(text.contains("\"left\":1"));Files.writeString(bad,text.replaceFirst("\"left\":1", "\"left\":0"));assertThrows(IllegalArgumentException.class,()->PortableForest.load(bad));Files.write(bad,new byte[]{(byte)0xc3,0x28});assertThrows(java.nio.charset.CharacterCodingException.class,()->PortableForest.load(bad));Files.write(bad,new byte[PortableForest.MAX_BYTES+1]);assertThrows(IllegalArgumentException.class,()->PortableForest.load(bad));assertArrayEquals(good,Files.readAllBytes(p));
  var assistant=new LocalAssistant(new LocalMemory(java.time.Clock.fixed(java.time.Instant.EPOCH,java.time.ZoneOffset.UTC)),model,java.time.Clock.fixed(java.time.Instant.EPOCH,java.time.ZoneOffset.UTC),()->"source-model-chat");var prediction=model.infer(List.of(0.,0.,0.,0.));var completion=assistant.complete(new LocalAssistant.Request(List.of(new LocalAssistant.Message("user","predict features [0, 0, 0, 0]")),null,200));assertEquals(String.format(Locale.ROOT,"Surrogate prediction: class %d with confidence %.3f.",prediction.prediction(),prediction.confidence()),completion.text());
 }
}
