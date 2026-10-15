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
import org.ghyura.cf.model.FrameworkException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalContentTest {
 @Test void all190IndependentContentFloatKindAndPythonContainerRenderingControlsMatch()throws Exception{
  var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("content-golden.xml")){assertNotNull(in);var rows=f.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("case");assertEquals(190,rows.getLength());for(int i=0;i<rows.getLength();i++){var row=(Map<?,?>)Json.parseBinary64(rows.item(i).getTextContent(),"independent source content");assertEquals(row.get("text"),LocalContent.text(row.get("content")),"content "+i);}}
 }
 @Test void defaultDecimalParsingStaysUnchangedAndMalformedOrExcessiveContentIsRefused(){
  assertInstanceOf(java.math.BigDecimal.class,Json.parse("1e0","default"));assertInstanceOf(java.math.BigDecimal.class,Json.parse("-0.0","default"));assertEquals(1.,Json.parseBinary64("1e0","floatkind"));assertEquals(Long.valueOf(1),Json.parseBinary64("1","integerkind"));assertEquals(Double.doubleToRawLongBits(-0.),Double.doubleToRawLongBits((Double)Json.parseBinary64("-0.0","signedzero")));assertEquals(Double.doubleToRawLongBits(-0.),Double.doubleToRawLongBits((Double)Json.parseBinary64("-1e-400","underflow")));assertThrows(FrameworkException.class,()->Json.parseBinary64("1e400","overflow"));assertThrows(FrameworkException.class,()->Json.parseBinary64("{\"a\":1,\"a\":2}","duplicate"));assertThrows(FrameworkException.class,()->Json.parseBinary64("[1,]","malformed"));
  assertThrows(IllegalArgumentException.class,()->LocalContent.text("x".repeat(LocalContent.MAX_CHARS+1)));assertThrows(IllegalArgumentException.class,()->LocalContent.text(Collections.nCopies(LocalContent.MAX_ATOMS+1,Map.of("type","ignored"))));var cycle=new ArrayList<Object>();cycle.add(cycle);assertThrows(IllegalArgumentException.class,()->LocalContent.text(cycle));assertThrows(IllegalArgumentException.class,()->LocalContent.text(Double.NaN));assertThrows(IllegalArgumentException.class,()->LocalContent.text(new Object()));assertThrows(IllegalArgumentException.class,()->LocalContent.text(new String(new char[]{(char)0xd800})));assertEquals("-0.0",LocalContent.text(-0.));assertEquals("5e-324",LocalContent.text(Double.MIN_VALUE));assertEquals("",LocalContent.text(List.of(Map.of("type","output_text","text","ignored"),Map.of("type","image_url","image_url",Map.of("url","invalid://no-fetch")))));
 }
}
