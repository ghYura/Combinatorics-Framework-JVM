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

package org.ghyura.cf.fixtures.tutor;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The Java port of tutor_sut.py against the Python module itself: golden/tutor-golden.xml was produced by running the
 * original (SUT checkout 8d5aa2d, its SHA-256 recorded in the file) on its 192 matrix cases and on malformed inputs.
 */
class TutorSutTest {

    private static Element golden() throws Exception {
        try (InputStream in = TutorSutTest.class.getResourceAsStream("/golden/tutor-golden.xml")) {
            assertNotNull(in, "golden/tutor-golden.xml");
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(in).getDocumentElement();
        }
    }

    private static List<String> split(String text) {
        return text.isEmpty() ? List.of() : Arrays.asList(text.split("\\|", -1));
    }

    @Test
    void everyGoldenCaseGivesThePythonResult() throws Exception {
        Element root = golden();
        assertEquals("e6693a62173f3d7549d475fdf0230f76cc1dea59a0ce683b28414dc1a7e61e1e", root.getAttribute("sha256"));
        NodeList cases = root.getElementsByTagName("case");
        assertEquals(198, cases.getLength());
        for (int i = 0; i < cases.getLength(); i++) {
            Element c = (Element) cases.item(i);
            List<String> steps = c.hasAttribute("steps-missing") ? null : split(c.getAttribute("steps"));
            TutorSut.Result r = TutorSut.evaluate(c.getAttribute("channel"), c.getAttribute("account"), c.getAttribute("payment"), steps,
                    split(c.getAttribute("stress")));
            String where = "case " + i + " " + c.getAttribute("channel") + "/" + c.getAttribute("steps");
            assertEquals(c.getAttribute("outcome"), r.outcome(), where);
            assertEquals(Integer.parseInt(c.getAttribute("verdict")), r.verdictCode(), where);
            assertEquals(Boolean.parseBoolean(c.getAttribute("passed")), r.passed(), where);
            assertEquals(c.getAttribute("reason"), r.reason(), where);
            assertEquals(c.getAttribute("interaction"), String.join(" ", r.interaction()), where);
            assertEquals(Integer.parseInt(c.getAttribute("charges")), r.charges(), where);
            assertEquals(Integer.parseInt(c.getAttribute("dispatches")), r.dispatches(), where);
        }
    }

    @Test
    void theMatrixSummaryIsThePythonOne() throws Exception {
        Element s = (Element) golden().getElementsByTagName("summary").item(0);
        Map<String, Object> summary = TutorSut.matrixSummary();
        assertEquals(Integer.parseInt(s.getAttribute("raw")), summary.get("raw_candidates"));
        assertEquals(Integer.parseInt(s.getAttribute("rejected")), summary.get("constraint_rejected"));
        assertEquals(Integer.parseInt(s.getAttribute("executable")), summary.get("executable_candidates"));
        assertEquals(Map.of("PASS", 18, "ORDER_VIOLATION", 120, "DOUBLE_CHARGE", 2, "DISPATCH_LOST", 4), summary.get("outcomes"));
        assertEquals(Integer.parseInt(s.getAttribute("PASS")), ((Map<?, ?>) summary.get("outcomes")).get("PASS"));
    }
}
