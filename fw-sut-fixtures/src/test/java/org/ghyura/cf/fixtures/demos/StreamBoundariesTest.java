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


package org.ghyura.cf.fixtures.demos;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Predictions are frozen from the unchanged original runtime/codecs, never computed by this port. */
class StreamBoundariesTest {
    private static void outcome(Element c, String tag, StreamBoundaries.Outcome actual) {
        var e = (Element) c.getElementsByTagName(tag).item(0);
        assertEquals(Boolean.parseBoolean(e.getAttribute("accepted")), actual.accepted(), c.getAttribute("id"));
        assertEquals(actual.accepted() ? e.getTextContent() : null, actual.text(), c.getAttribute("id"));
        assertEquals(actual.accepted() ? null : e.getAttribute("error"), actual.error(), c.getAttribute("id"));
    }

    @Test void all480OriginalCodecOutcomesChunksAndFailureClassesMatchIndependentPredictions() throws Exception {
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d2-golden.xml")) {
            assertNotNull(in);var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            var cases=f.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("case");
            assertEquals(480,cases.getLength());int passes=0;Map<String,Long> classes=new HashMap<>();
            for(int i=0;i<cases.getLength();i++) {
                var c=(Element)cases.item(i);
                var r=StreamBoundaries.evaluate(c.getAttribute("adapter"),c.getAttribute("payload"),c.getAttribute("errors"),Integer.parseInt(c.getAttribute("mask")));
                assertEquals(c.getAttribute("chunks"),String.join(" ",r.chunksHex()),c.getAttribute("id"));
                outcome(c,"reference",r.reference());outcome(c,"observed",r.observed());
                assertEquals(c.getAttribute("failure-class"),r.failureClass(),c.getAttribute("id"));
                classes.merge(r.failureClass(),1L,Long::sum);if(r.passed())passes++;
            }
            assertEquals(332,passes);
            assertEquals(Map.of("none",332L,"unexpected_rejection",47L,"unexpected_acceptance",20L,"wrong_text",81L),classes);
        }
    }

    @Test void invalidCutsAndConfigurationAreSetupFailuresAndTheOracleIsAdapterBlind() {
        for(int mask:new int[]{-1,32,4,8,16})
            assertThrows(IllegalArgumentException.class,()->StreamBoundaries.evaluate("incremental","ascii","strict",mask));
        assertThrows(IllegalArgumentException.class,()->StreamBoundaries.evaluate("other","ascii","strict",0));
        assertThrows(IllegalArgumentException.class,()->StreamBoundaries.evaluate("incremental","other","strict",0));
        assertThrows(IllegalArgumentException.class,()->StreamBoundaries.evaluate("incremental","ascii","ignore",0));
        var p=StreamBoundaries.payload("euro");
        assertThrows(IllegalStateException.class,()->StreamBoundaries.reference(new StreamBoundaries.Payload(p.encoding(),p.hex(),"wrong literal",false),"strict"));
        var correct=StreamBoundaries.evaluate("incremental","truncated_utf8","strict",0);
        var broken=StreamBoundaries.evaluate("no_final","truncated_utf8","strict",0);
        assertEquals(correct.reference(),broken.reference());assertTrue(correct.passed());assertEquals("unexpected_acceptance",broken.failureClass());
        assertEquals("A",broken.observed().text());
    }
}
