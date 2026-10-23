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

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review HG08 and decisions F12-F22: every deliberate difference from the legacy behaviour is recorded in the
 * shipped compatibility-deltas.xml, bound to a named test that exists and to the legacy line it differs from.
 * A test reference must be a test method of the named module; an evidence line must hold the text it quotes
 * (checked when the legacy checkout is beside the project, skipped visibly otherwise).
 */
class CompatibilityDeltasTest {

    private static List<Element> deltas() throws Exception {
        try (InputStream in = CompatibilityDeltasTest.class.getResourceAsStream("/org/ghyura/cf/config/compatibility-deltas.xml")) {
            assertNotNull(in, "compatibility-deltas.xml is not shipped by fw-config");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(in);
            NodeList nodes = document.getElementsByTagName("delta");
            List<Element> list = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                list.add((Element) nodes.item(i));
            }
            assertEquals(Integer.parseInt(document.getDocumentElement().getAttribute("deltas")), list.size());
            return list;
        }
    }

    private static List<Element> children(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        List<Element> list = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            list.add((Element) nodes.item(i));
        }
        return list;
    }

    @Test
    void everyDeltaIsBoundToTestMethodsThatExistInTheNamedModules() throws Exception {
        List<Element> deltas = deltas();
        assertTrue(deltas.size() >= 16, "deltas: " + deltas.size());
        Set<String> ids = new HashSet<>();
        List<String> missing = new ArrayList<>();
        for (Element delta : deltas) {
            assertTrue(ids.add(delta.getAttribute("id")), "twice: " + delta.getAttribute("id"));
            assertFalse(children(delta, "legacy").getFirst().getTextContent().isBlank(), delta.getAttribute("id"));
            assertFalse(children(delta, "now").getFirst().getTextContent().isBlank(), delta.getAttribute("id"));
            List<Element> tests = children(delta, "test");
            assertFalse(tests.isEmpty(), delta.getAttribute("id") + " names no test");
            for (Element test : tests) {
                Path source = Reactor.root().resolve(test.getAttribute("module")).resolve("src/test/java")
                        .resolve(test.getAttribute("class").replace('.', '/') + ".java");
                String method = test.getAttribute("method");
                String text = Files.isRegularFile(source) ? Files.readString(source, StandardCharsets.UTF_8) : "";
                boolean annotated = Pattern.compile("@(?:Test|ParameterizedTest)\\s+(?:@[^\\n]+\\s+)*void " + Pattern.quote(method)
                        + "\\(").matcher(text).find();
                if (!annotated) {
                    missing.add(delta.getAttribute("id") + ": " + test.getAttribute("class") + "#" + method);
                }
            }
        }
        assertEquals(List.of(), missing, "test references that are not test methods of their module");
    }

    @Test
    void everyLegacyEvidenceLineHoldsTheTextItQuotes() throws Exception {
        Path checkout = Reactor.companion(CompanionRepositories.Kind.QA, "Core_trunk");
        List<String> stale = new ArrayList<>();
        int checked = 0;
        for (Element delta : deltas()) {
            for (Element evidence : children(delta, "evidence")) {
                Path file = checkout.resolve(evidence.getAttribute("path"));
                int line = Integer.parseInt(evidence.getAttribute("line"));
                List<String> lines = Files.isRegularFile(file)
                        ? new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).lines().toList() : List.of();
                checked++;
                if (line < 1 || line > lines.size() || !lines.get(line - 1).contains(evidence.getAttribute("contains"))) {
                    stale.add(delta.getAttribute("id") + ": " + evidence.getAttribute("path") + ":" + line + " does not hold '"
                            + evidence.getAttribute("contains") + "'");
                }
            }
        }
        assertTrue(checked >= 25, "evidence lines: " + checked);
        assertEquals(List.of(), stale);
    }
}
