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
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A19: every feature of the architect's traceability matrix (copied unmodified and pinned by hash) has exactly one
 * developer mapping with an honest status, and every Java file and test method a mapping cites exists in the reactor.
 */
class FeatureTraceabilityTest {

    /** SHA-256 of handoff/feature-traceability.xml (architecture revision 2) as copied into the test resources. */
    private static final String TRACEABILITY_SHA256 = "30f3f0d785868fd81b8a019aa74a5e31e2a2a2c6d50a79119be5571e15bfa13a";
    private static final Set<String> STATUSES = Set.of("implemented", "partial", "replaced", "incompatible", "not-implemented");

    @Test
    void theCopiedMatrixIsTheArchitectsFileAndEveryFeatureHasOneHonestMapping() throws Exception {
        String copied = text("/feature-traceability-handoff.xml");
        String withoutNotice = copied.replaceAll("(?s)<!--.*?-->\\s*", "");
        assertEquals(TRACEABILITY_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(withoutNotice.getBytes(StandardCharsets.UTF_8))), "a new matrix needs a deliberate update of the mapping");
        List<String> features = new ArrayList<>();
        for (Element f : children(parse(copied).getDocumentElement(), "feature")) {
            features.add(f.getAttribute("id"));
        }
        Map<String, Element> mapped = new LinkedHashMap<>();
        for (Element f : children(parse(text("/feature-mapping.xml")).getDocumentElement(), "feature")) {
            assertTrue(mapped.put(f.getAttribute("id"), f) == null, "mapped twice: " + f.getAttribute("id"));
        }
        assertEquals(57, features.size());
        assertEquals(features, List.copyOf(mapped.keySet()), "one mapping per feature, in the matrix's order");
        for (Element f : mapped.values()) {
            String id = f.getAttribute("id");
            String status = f.getAttribute("status");
            assertTrue(STATUSES.contains(status), id + ": " + status);
            assertFalse(children(f, "summary").isEmpty() || children(f, "summary").getFirst().getTextContent().isBlank(), id + " has no summary");
            boolean built = !status.equals("not-implemented");
            assertEquals(built, !children(f, "test").isEmpty(), id + ": tests exactly when something is built");
            assertEquals(built, !children(f, "source").isEmpty(), id + ": sources exactly when something is built");
            boolean incomplete = Set.of("partial", "incompatible", "not-implemented").contains(status);
            assertEquals(incomplete, !children(f, "open").isEmpty(), id + ": what is missing is named exactly when something is");
        }
    }

    @Test
    void everyCitedSourceAndTestMethodExistsInTheReactor() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Element f : children(parse(text("/feature-mapping.xml")).getDocumentElement(), "feature")) {
            for (Element source : children(f, "source")) {
                if (!Files.isRegularFile(Reactor.root().resolve(source.getAttribute("path")))) {
                    missing.add(f.getAttribute("id") + ": " + source.getAttribute("path"));
                }
            }
            for (Element test : children(f, "test")) {
                Path file = Reactor.root().resolve(test.getAttribute("module")).resolve("src/test/java")
                        .resolve(test.getAttribute("class").replace('.', '/') + ".java");
                String text = Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
                if (!Pattern.compile("@(?:Test|ParameterizedTest)(?:\\([^\\n]*\\))?\\s+(?:@[^\\n]+\\s+)*void " + Pattern.quote(test.getAttribute("method")) + "\\(")
                        .matcher(text).find()) {
                    missing.add(f.getAttribute("id") + ": " + test.getAttribute("class") + "#" + test.getAttribute("method"));
                }
            }
        }
        assertEquals(List.of(), missing, "cited files and test methods that do not exist");
    }

    private static String text(String resource) throws Exception {
        try (InputStream in = FeatureTraceabilityTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) out.add(e);
        }
        return out;
    }
}
