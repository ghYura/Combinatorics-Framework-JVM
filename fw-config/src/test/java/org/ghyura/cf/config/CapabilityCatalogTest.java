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

package org.ghyura.cf.config;

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityCatalogTest {

    private static final List<Capability> MAPPING = List.of(
            new Capability("X1", "replaced thing", List.of("fw-a"), CapabilityAction.REPLACE, "do it in Java"),
            new Capability("X2", "ported thing", List.of("fw-b", "fw-c"), CapabilityAction.PORT_REQUIRED, "port each family"),
            new Capability("X3", "impossible thing", List.of("fw-d"), CapabilityAction.INCOMPATIBLE, "no OS isolation in one JVM"));

    @Test
    void theShippedMappingHasAllEighteenCapabilitiesInArchitectureOrder() {
        CapabilityCatalog catalog = CapabilityCatalog.load();
        List<String> ids = catalog.entries().stream().map(e -> e.capability().id()).toList();
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= 18; i++) {
            expected.add(String.format("C%02d", i));
        }
        assertEquals(expected, ids);
        Capability c01 = catalog.entry("C01").capability();
        assertEquals(CapabilityAction.REPLACE, c01.action());
        assertEquals(List.of("fw-input", "fw-store"), c01.targetModules());
        assertEquals("Core DB dictionaries and FW metadata", c01.legacy());
        assertEquals(List.of("fw-execution"), catalog.entry("C08").capability().targetModules());
    }

    @Test
    void withoutAnyModuleClaimNothingIsAdvertisedAsImplemented() {
        CapabilityCatalog catalog = CapabilityCatalog.load();
        Map<CapabilityStatus, Integer> counts = catalog.counts();
        assertEquals(0, counts.get(CapabilityStatus.IMPLEMENTED));
        assertEquals(0, counts.get(CapabilityStatus.PARTIAL));
        assertEquals(1, counts.get(CapabilityStatus.INCOMPATIBLE));
        assertEquals(17, counts.get(CapabilityStatus.NOT_IMPLEMENTED));
        assertEquals(CapabilityStatus.INCOMPATIBLE, catalog.entry("C08").status());
        assertTrue(catalog.entry("C08").evidence().contains("OS isolation"));
        assertEquals(CapabilityStatus.NOT_IMPLEMENTED, catalog.entry("C09").status());
    }

    @Test
    void claimsDecideTheStatusAndCarryTheirEvidence() {
        CapabilityCatalog catalog = CapabilityCatalog.of(MAPPING, List.of(
                new CapabilityClaim("X1", CapabilityStatus.IMPLEMENTED, "fw-a/FooGateTest#everything"),
                new CapabilityClaim("X2", CapabilityStatus.PARTIAL, "tutor fixture only")));
        assertEquals(CapabilityStatus.IMPLEMENTED, catalog.entry("X1").status());
        assertEquals("fw-a/FooGateTest#everything", catalog.entry("X1").evidence());
        assertEquals(CapabilityStatus.PARTIAL, catalog.entry("X2").status());
        assertEquals(CapabilityStatus.INCOMPATIBLE, catalog.entry("X3").status());
        assertEquals("no OS isolation in one JVM", catalog.entry("X3").evidence());
    }

    @Test
    void anImplementedOrPartialClaimMustNameItsEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new CapabilityClaim("X1", CapabilityStatus.IMPLEMENTED, ""));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityClaim("X1", CapabilityStatus.PARTIAL, "  "));
        assertEquals(CapabilityStatus.NOT_IMPLEMENTED,
                new CapabilityClaim("X1", CapabilityStatus.NOT_IMPLEMENTED, "").status());
    }

    @Test
    void contradictoryOrUnknownClaimsAreRejectedAsInternalErrors() {
        assertInternal(() -> CapabilityCatalog.of(MAPPING,
                List.of(new CapabilityClaim("X9", CapabilityStatus.IMPLEMENTED, "e"))));
        assertInternal(() -> CapabilityCatalog.of(MAPPING, List.of(
                new CapabilityClaim("X1", CapabilityStatus.IMPLEMENTED, "e"),
                new CapabilityClaim("X1", CapabilityStatus.PARTIAL, "e"))));
        assertInternal(() -> CapabilityCatalog.of(MAPPING,
                List.of(new CapabilityClaim("X3", CapabilityStatus.IMPLEMENTED, "we emulated a sandbox"))));
        assertInternal(() -> CapabilityCatalog.of(List.of(MAPPING.get(0), MAPPING.get(0)), List.of()));
    }

    @Test
    void anIncompatibleCapabilityMayBeClaimedIncompatibleAndNothingElse() {
        CapabilityCatalog catalog = CapabilityCatalog.of(MAPPING,
                List.of(new CapabilityClaim("X3", CapabilityStatus.INCOMPATIBLE, "")));
        assertEquals(CapabilityStatus.INCOMPATIBLE, catalog.entry("X3").status());
    }

    @Test
    void xmlIsWellFormedAndCarriesCountsStatusesAndReasons() throws Exception {
        CapabilityCatalog catalog = CapabilityCatalog.of(MAPPING,
                List.of(new CapabilityClaim("X1", CapabilityStatus.IMPLEMENTED, "A & B <gate>")));
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(catalog.toXml().getBytes(StandardCharsets.UTF_8)));
        Element root = doc.getDocumentElement();
        assertEquals("1", root.getAttribute("implemented"));
        assertEquals("0", root.getAttribute("partial"));
        assertEquals("1", root.getAttribute("notImplemented"));
        assertEquals("1", root.getAttribute("incompatible"));
        Element x1 = (Element) root.getElementsByTagName("capability").item(0);
        assertEquals("X1", x1.getAttribute("id"));
        assertEquals("IMPLEMENTED", x1.getAttribute("status"));
        assertEquals("A & B <gate>", x1.getElementsByTagName("evidence").item(0).getTextContent());
        Element x2 = (Element) root.getElementsByTagName("capability").item(1);
        assertEquals("fw-b fw-c", x2.getAttribute("targets"));
        assertEquals(0, x2.getElementsByTagName("evidence").getLength());
    }

    @Test
    void textReportEndsWithAnHonestSummary() {
        String text = CapabilityCatalog.load().toText();
        assertTrue(text.contains("C08  INCOMPATIBLE"), text);
        assertTrue(text.contains("reason: "), text);
        assertTrue(text.endsWith("0 implemented, 0 partial, 17 not implemented, 1 incompatible by design\n"), text);
    }

    private static void assertInternal(org.junit.jupiter.api.function.Executable action) {
        FrameworkException e = assertThrows(FrameworkException.class, action);
        assertEquals(ErrorCode.INTERNAL_ERROR, e.code());
    }
}
