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
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class LocalArithmeticTest {
 @Test void all728IndependentNumericExtractionPrecedenceAndErrorCasesMatch()throws Exception{
  var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("arithmetic-golden.xml")){assertNotNull(in);var cases=f.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("case");assertEquals(728,cases.getLength());int values=0,empty=0,refused=0;
   for(int i=0;i<cases.getLength();i++){String raw=cases.item(i).getTextContent();var row=(Map<?,?>)Json.parse(raw,"preregistered arithmetic");var observed=LocalArithmetic.evaluate((String)row.get("text"));assertEquals(row.get("expression"),observed.expression().orElse(null),row.get("id").toString());
    if(row.get("value")==null)assertTrue(observed.value().isEmpty(),row.toString());else{double wanted=((Number)row.get("value")).doubleValue();if(wanted==0&&raw.contains("\"value\": -0.0"))wanted=-0.;double actual=observed.value().orElseThrow();if(Double.doubleToLongBits(wanted)!=Double.doubleToLongBits(actual)){assertTrue(((String)row.get("text")).contains("**"),row.toString());assertEquals(wanted,actual,2*Math.ulp(wanted),row.toString());}values++;}
    if(observed.disposition()==LocalArithmetic.Disposition.RESOURCE_REFUSED)refused++;else if(observed.disposition()==LocalArithmetic.Disposition.NO_RESULT)empty++;
   }assertEquals(678,values);assertEquals(48,empty);assertEquals(2,refused);
  }
 }
 @Test void resourceRefusalsAndExactIntermediateRatiosAndPythonFloatModuloStayDistinct(){
  assertEquals(LocalArithmetic.Disposition.RESOURCE_REFUSED,LocalArithmetic.evaluate("calculate 9**9**6").disposition());assertEquals(LocalArithmetic.Disposition.RESOURCE_REFUSED,LocalArithmetic.evaluate("x".repeat(12001)).disposition());assertEquals(LocalArithmetic.Disposition.NO_RESULT,LocalArithmetic.evaluate("1/0").disposition());assertEquals(1,LocalArithmetic.evaluate("2**2000/2**2000").value().orElseThrow());assertEquals(2,LocalArithmetic.evaluate("(2**1024)/(2**1024)+1").value().orElseThrow());assertEquals(-1,LocalArithmetic.evaluate("1.0//0.1-10").value().orElseThrow());assertEquals(Double.doubleToRawLongBits(-0.),Double.doubleToRawLongBits(LocalArithmetic.evaluate("0.0%-2.0").value().orElseThrow()));assertEquals(5,LocalArithmetic.evaluate("(2\n+3)").value().orElseThrow());assertTrue(LocalArithmetic.evaluate("2\n+3").value().isEmpty());assertTrue(LocalArithmetic.evaluate("02+3").value().isEmpty());assertTrue(LocalArithmetic.evaluate("2\u00a0+3").value().isEmpty());assertThrows(NullPointerException.class,()->LocalArithmetic.evaluate(null));
 }
 @Test void floatingPowerOverflowComplexIntermediatesAndNonfiniteExponentsMatchIndependentControls()throws Exception{
  var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("power-golden.xml")){assertNotNull(in);var cases=f.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("case");assertEquals(10,cases.getLength());int values=0,refused=0;for(int i=0;i<cases.getLength();i++){var expected=(Map<?,?>)Json.parse(cases.item(i).getTextContent(),"independent power controls");var actual=LocalArithmetic.evaluate((String)expected.get("text"));if(expected.get("value")==null){assertEquals(LocalArithmetic.Disposition.NO_RESULT,actual.disposition());assertTrue(actual.value().isEmpty());refused++;}else{assertEquals(((Number)expected.get("value")).doubleValue(),actual.value().orElseThrow(),expected.toString());values++;}}assertEquals(6,values);assertEquals(4,refused);}
 }

}
