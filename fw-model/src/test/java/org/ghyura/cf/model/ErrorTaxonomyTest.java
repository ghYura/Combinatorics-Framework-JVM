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

package org.ghyura.cf.model;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The error taxonomy is frozen at M00: this build fails when the enum drifts from the reviewed XML contract.
 * It is a change detector, not independent evidence of any behavior.
 */
class ErrorTaxonomyTest {

    @Test
    void theEnumMatchesTheFrozenContractFile() throws IOException {
        String frozen;
        try (InputStream in = ErrorTaxonomy.class.getResourceAsStream(ErrorTaxonomy.RESOURCE)) {
            assertNotNull(in, "missing " + ErrorTaxonomy.RESOURCE);
            frozen = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(withoutComments(frozen), withoutComments(ErrorTaxonomy.toXml()),
                "ErrorCode changed: review the change, then regenerate error-taxonomy.xml");
    }

    @Test
    void everyCodeAppearsExactlyOnceInTheRendering() {
        String xml = ErrorTaxonomy.toXml();
        for (ErrorCode code : ErrorCode.values()) {
            String marker = "<code name=\"" + code.name() + "\"";
            assertEquals(xml.indexOf(marker), xml.lastIndexOf(marker), code.name());
            assertTrue(xml.contains(marker), code.name());
        }
    }

    /**
     * Round trip: the frozen file, read back as XML, names every code of the enum exactly once, under its own
     * category, with its severity and description, and names nothing else (supplement to revision 2).
     */
    @Test
    void theFrozenFileReadsBackAsExactlyTheEnum() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document;
        try (InputStream in = ErrorTaxonomy.class.getResourceAsStream(ErrorTaxonomy.RESOURCE)) {
            assertNotNull(in, "missing " + ErrorTaxonomy.RESOURCE);
            document = factory.newDocumentBuilder().parse(in);
        }
        Map<String, String> read = new LinkedHashMap<>();
        NodeList codes = document.getElementsByTagName("code");
        for (int i = 0; i < codes.getLength(); i++) {
            Element code = (Element) codes.item(i);
            Element category = (Element) code.getParentNode();
            String previous = read.put(code.getAttribute("name"), category.getAttribute("name") + "|"
                    + code.getAttribute("severity") + "|" + code.getTextContent());
            assertEquals(null, previous, "named twice: " + code.getAttribute("name"));
        }
        Map<String, String> expected = new LinkedHashMap<>();
        for (ErrorCode code : ErrorCode.values()) {
            expected.put(code.name(), code.category().name() + "|" + code.defaultSeverity().name() + "|" + code.description());
        }
        assertEquals(expected, read);
        assertEquals(ErrorCode.values().length, codes.getLength());
    }

    /** The license header is a comment; the contract is everything else. */
    private static String withoutComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->\\s*", "").replace("\r\n", "\n");
    }
}
