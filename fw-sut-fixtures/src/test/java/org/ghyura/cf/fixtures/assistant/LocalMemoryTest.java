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
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalMemoryTest {
 private static final Clock CLOCK=Clock.fixed(Instant.ofEpochSecond(1000),ZoneOffset.UTC);
 private static Object normalized(Object v){return Json.parse(Json.write(v),"observed");}
 @Test void allSeedAndIngestIdsTagsTokensRankedScoresAndSnippetsMatchIndependentPredictions()throws Exception{
  var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("memory-golden.xml")){assertNotNull(in);var root=f.newDocumentBuilder().parse(in).getDocumentElement();var docs=root.getElementsByTagName("document");assertEquals(9,docs.getLength());var incoming=new ArrayList<LocalMemory.Input>();var wanted=new ArrayList<Object>();
   for(int i=0;i<docs.getLength();i++){var row=(Map<?,?>)Json.parse(docs.item(i).getTextContent(),"golden");var raw=(Map<?,?>)row.get("input");incoming.add(new LocalMemory.Input((String)raw.get("title"),(String)raw.get("text"),((List<?>)raw.get("tags")).stream().map(String.class::cast).toList()));wanted.add(row.get("stored"));}
   var memory=new LocalMemory(CLOCK);assertEquals(6,memory.addDocuments(incoming).size());assertEquals(wanted,normalized(memory.documents().stream().map(LocalMemory.Document::fields).toList()));assertEquals(0,memory.addDocuments(incoming).size());var cases=root.getElementsByTagName("case");assertEquals(19,cases.getLength());for(int i=0;i<cases.getLength();i++){var row=(Map<?,?>)Json.parse(cases.item(i).getTextContent(),"golden");String query=(String)row.get("query");assertEquals(row.get("tokens"),LocalMemory.tokenize(query));assertEquals(row.get("matches"),normalized(memory.search(query,5).stream().map(LocalMemory.Match::fields).toList()),query);}
  }
 }
 @Test void snapshotsRestoreAndSourceFallbackAndFailedMutationsPreserveGoodData(@TempDir Path work)throws Exception{
  var memory=new LocalMemory(CLOCK);memory.addDocuments(List.of(new LocalMemory.Input(" note "," alpha beta ",List.of(" tag "))));Path file=work.resolve("memory.json");memory.save(file);assertEquals(memory.documents(),LocalMemory.load(file,CLOCK).documents());byte[] good=Files.readAllBytes(file);assertThrows(java.io.IOException.class,()->memory.save(work));assertArrayEquals(good,Files.readAllBytes(file));Files.writeString(file,"{broken");assertEquals(3,LocalMemory.load(file,CLOCK).count());Files.write(file,new byte[]{(byte)0xc0,(byte)0x80});assertEquals(3,LocalMemory.load(file,CLOCK).count());Files.writeString(file,"[]");assertEquals(3,LocalMemory.load(file,CLOCK).count());
  while(memory.count()<256){var batch=new ArrayList<LocalMemory.Input>();for(int i=0,n=Math.min(64,256-memory.count());i<n;i++)batch.add(new LocalMemory.Input("doc"+(memory.count()+i),"body",List.of()));memory.addDocuments(batch);}var before=memory.documents();assertThrows(IllegalArgumentException.class,()->memory.addDocuments(List.of(new LocalMemory.Input("overflow","body",List.of()))));assertEquals(before,memory.documents());assertThrows(IllegalArgumentException.class,()->memory.search("x".repeat(12001),5));assertThrows(IllegalArgumentException.class,()->memory.search("note",21));assertThrows(IllegalArgumentException.class,()->new LocalMemory.Input("title","x".repeat(12001),List.of()));assertThrows(UnsupportedOperationException.class,()->memory.documents().clear());
 }
}
