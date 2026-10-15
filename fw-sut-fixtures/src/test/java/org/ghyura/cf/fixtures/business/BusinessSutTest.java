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

package org.ghyura.cf.fixtures.business;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.ghyura.cf.fixtures.business.FixtureValues.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original Python functions generated these immutable goldens, independently of the Java port (784 scenarios). */
class BusinessSutTest {
    private record Case(String id, Map<String, Object> input, Object expected) { }
    private static final Map<String, String> HASHES = Map.of(
            "pricing", "2bcdd62bdf713d702740dd7129e8dc5ff3385bafe2378adde5e579057dbea8be",
            "security", "b2a85094500ca4a6b2ee6a8ef9a01593b9d28d3d8691b2876020ef9f9b53b1f5",
            "billing", "72d9b505bbe785fded13b512582cc7e94aa6d7e86ea2fa435b6d3cf050892f45",
            "fulfilment", "470c757032693bf323cac17e548d6488137f50a59c5d488c6b0614f5474fd26b");

    private static Element root(String family) throws Exception {
        try (InputStream in = BusinessSutTest.class.getResourceAsStream(family + "-golden.xml")) {
            assertNotNull(in, family);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(in).getDocumentElement();
        }
    }
    private static List<Case> cases(String family, int count) throws Exception {
        Element root = root(family); assertEquals(count, Integer.parseInt(root.getAttribute("cases")));
        NodeList nodes = root.getElementsByTagName("case"); assertEquals(count, nodes.getLength());
        List<Case> cases = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            cases.add(new Case(node.getAttribute("id"), object(Json.parse(node.getElementsByTagName("input").item(0).getTextContent(), family)),
                    Json.parse(node.getElementsByTagName("expected").item(0).getTextContent(), family)));
        }
        return cases;
    }
    private static List<?> list(Object value) { assertInstanceOf(List.class, value); return (List<?>) value; }

    private static void equalJson(Object expected, Object actual, String where) {
        if (expected instanceof Number n && actual instanceof Number a) {
            assertEquals(0, new BigDecimal(n.toString()).compareTo(new BigDecimal(a.toString())), where + ": " + n + " / " + a);
        } else if (expected instanceof Map<?, ?> map && actual instanceof Map<?, ?> result) {
            assertEquals(map.keySet(), result.keySet(), where);
            for (Object key : map.keySet()) equalJson(map.get(key), result.get(key), where + "/" + key);
        } else if (expected instanceof List<?> items && actual instanceof List<?> result) {
            assertEquals(items.size(), result.size(), where);
            for (int i = 0; i < items.size(); i++) equalJson(items.get(i), result.get(i), where + "/" + i);
        } else assertEquals(expected, actual, where);
    }

    @Test void goldensIdentifyUnchangedPinnedSources() throws Exception {
        Path sourceRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (sourceRoot != null && !Files.isDirectory(sourceRoot.resolve("SUT"))) sourceRoot = sourceRoot.getParent();
        for (String family : HASHES.keySet()) {
            Element root = root(family);
            assertEquals("8d5aa2d48a605521f783d1787a42fc9a5ebfc7a9", root.getAttribute("commit"));
            assertEquals(HASHES.get(family), root.getAttribute("sha256"));
            if (sourceRoot != null) assertEquals(HASHES.get(family), Canonical.sha256Hex(
                    Files.readAllBytes(sourceRoot.resolve("SUT").resolve(root.getAttribute("source")))));
        }
    }

    @Test void pricingMatchesPythonAcrossOrdersRegionsCouponsAndIdempotency() throws Exception {
        for (Case c : cases("pricing", 184)) {
            PricingSut sut = new PricingSut(); Map<String, Object> request = object(c.input().get("request"));
            List<Object> responses = new ArrayList<>(); responses.add(sut.checkout(pricing(request)));
            if (c.input().containsKey("other_key")) {
                responses.add(sut.checkout(pricing(request))); responses.add(sut.checkout(pricing(object(c.input().get("other_key")))));
            }
            Map<String, Object> actual = map("responses", responses);
            if (c.input().containsKey("other_key")) actual.put("canonical", PricingSut.canonicalPrice(strings(request.get("coupons")), text(request, "region", "us")));
            equalJson(c.expected(), actual, c.id());
        }
    }
    private static PricingSut.Request pricing(Map<String, Object> request) {
        return new PricingSut.Request(text(request, "scenario_id", "sample"), text(request, "idem_key", "key"),
                text(request, "mode", "strict"), text(request, "region", "us"), strings(request.get("coupons")), strings(request.get("stages")));
    }

    @Test void securityMatchesPythonIncludingConfusablesRotationAndPoison() throws Exception {
        for (Case c : cases("security", 147)) {
            SecurePipelineSut sut = new SecurePipelineSut(); List<Object> replies = new ArrayList<>();
            Map<String, Object> request = object(c.input().get("request")); String scenario = text(request, "scenario_id", "sample");
            if (bool(c.input(), "rotate", false)) replies.add(sut.rotateKey(scenario));
            if (bool(c.input(), "poison", false)) replies.add(sut.poisonCache(scenario));
            replies.add(sut.process(new SecurePipelineSut.Request(scenario, text(request, "mode", "strict"),
                    strings(request.get("order")), strings(request.get("features")), text(request, "text", ""))));
            equalJson(c.expected(), replies, c.id());
        }
    }

    @Test void billingMatchesPythonAfterEveryActionIncludingRejectionsAndReplay() throws Exception {
        for (Case c : cases("billing", 285)) {
            Map<String, Object> config = object(c.input().get("config"));
            BillingSut sut = new BillingSut(text(config, "plan", "basic"), integer(config, "cycle_days", 30).intValueExact(),
                    text(config, "proration", "credit"), text(config, "rounding", "half_up"), integer(config, "trial_days", 0).longValueExact(),
                    bool(config, "dunning", false));
            List<Object> returned = new ArrayList<>(), states = new ArrayList<>();
            for (Object action : list(c.input().get("actions"))) {
                try { returned.add(BusinessActions.billing(sut, object(action))); }
                catch (IllegalArgumentException | ArithmeticException error) { returned.add(map("error", error.getMessage())); }
                states.add(sut.snapshot());
            }
            equalJson(c.expected(), map("returns", returned, "states", states, "final", sut.snapshot()), c.id());
        }
    }

    @Test void fulfilmentMatchesPythonAfterEveryDeliveryLossRestartAndTimeout() throws Exception {
        for (Case c : cases("fulfilment", 168)) {
            Map<String, Object> config = object(c.input().get("config"));
            OrderFulfilmentSut sut = new OrderFulfilmentSut(integer(config, "timeout", 3).intValueExact(),
                    text(config, "compensate_order", "release_first"), bool(config, "retry", true));
            List<Object> returned = new ArrayList<>(), states = new ArrayList<>();
            for (Object action : list(c.input().get("actions"))) {
                returned.add(BusinessActions.fulfilment(sut, object(action))); states.add(sut.snapshot());
            }
            equalJson(c.expected(), map("returns", returned, "states", states, "final", sut.snapshot()), c.id());
        }
    }

    @Test void binaryRoundingAndDomainStateIsolationArePreserved() {
        // CPython round(2.675, 2)=2.67: using BigDecimal.valueOf would change this behavior.
        assertEquals(2.67, round2(2.675)); assertEquals(2.69, round2(2.685));
        PricingSut pricing = new PricingSut();
        List<String> stages = List.of("discount", "tax", "round");
        pricing.checkout(new PricingSut.Request("first", "key", "strict", "us", List.of(), stages));
        assertEquals(false, pricing.checkout(new PricingSut.Request("second", "key", "strict", "us", List.of(), stages)).get("duplicate"));
        SecurePipelineSut security = new SecurePipelineSut(); security.rotateKey("first");
        assertEquals(1, security.process(new SecurePipelineSut.Request("second", "strict", List.of("normalize", "redact", "tag"),
                List.of(), "SECRET-A")).get("key_version"));
        OrderFulfilmentSut fulfilment = new OrderFulfilmentSut(); fulfilment.submit("one", "widget", java.math.BigInteger.ONE,
                new java.math.BigInteger("9876543210987654321098765432109876543210")); fulfilment.drain(200);
        assertEquals(new java.math.BigInteger("9876543210987654321098765432109876543210"), fulfilment.balance());
    }
}
