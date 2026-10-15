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

package org.ghyura.cf.fixtures.finance;

import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class FinanceNodeTest {
    @Test void theOnHoldRecoveryExampleAndNodeControlsMatchTheIndependentProjection() throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var in = getClass().getResourceAsStream("peering-native-golden.xml")) {
            assertNotNull(in);
            var gold = (Map<?, ?>) Json.parse(factory.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("predictions").item(0).getTextContent(), "projection");
            var observed = (Map<?, ?>) Json.parse(Json.write(FinancePeeringScenario.run(Map.of("scenario", "source_multinode"))), "observed");
            for (var key : gold.keySet()) assertEquals(gold.get(key), observed.get(key), String.valueOf(key));
            assertEquals(gold.keySet(), observed.keySet());
        }
        assertThrows(IllegalArgumentException.class, () -> FinancePeeringScenario.run(Map.of("scenario", "unsupported")));
    }
    @Test void theNodeAppRoutesAndSourceCrashesAsStarletteDoes(@TempDir Path work) {
        double[] now = {1000}; var network = new FinancePeering.Network(); var registry = new FinancePeering.Registry(work.resolve("registry"), () -> now[0], 7);
        var node = new FinanceNode("toBank1", "AURUM", "Aurum Bank", "127.0.0.1", 8001, "s", "XX", null, registry, network, () -> now[0], () -> "n", time -> new FinanceEcosystem());
        java.util.function.BiFunction<String, String, FinanceGateway.Response> unsigned = (method, path) -> network.exchange(method, node.baseUrl(), path, new byte[0], Map.of(), Map.of());
        assertEquals(new FinanceGateway.Response(405, Map.of("detail", "Method Not Allowed")), unsigned.apply("POST", "/health"));
        // A wrong method on an interbank route falls through to the proxy route, which knows no node "interbank".
        assertEquals(new FinanceGateway.Response(404, Map.of("code", "NOT_FOUND", "message", "unknown bank node 'interbank'")), unsigned.apply("GET", "/interbank/inbound"));
        assertEquals(new FinanceGateway.Response(404, Map.of("detail", "Not Found")), unsigned.apply("GET", "/toBank2"));
        assertEquals(401, unsigned.apply("POST", "/interbank/inbound").status());
        java.util.function.Function<String, FinanceGateway.Response> signedInbound = body -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8); String ts = "1000", nonce = "same";
            return network.exchange("POST", node.baseUrl(), "/interbank/inbound", bytes, Map.of("x-timestamp", ts, "x-nonce", nonce, "x-signature", FinanceSecurity.sign("s", "POST", "/interbank/inbound", bytes, ts, nonce)), Map.of());
        };
        var crash = new FinanceGateway.Response(500, new FinancePeering.Text("Internal Server Error"));
        assertEquals(crash, signedInbound.apply("{\"amount\": \"1.00\"}"));
        assertEquals(crash, signedInbound.apply("{bad"));
        assertEquals(crash, signedInbound.apply("[1]"));
        assertEquals(404, signedInbound.apply("{\"account_id\": \"acc_none\"}").status());
        // An entry without bank_code breaks discovery: the proxy answers 500 and the heartbeat loop swallows it.
        registry.register("toBank3", "ZED", "http://127.0.0.1:8003");
        try { Files.writeString(work.resolve("registry/toBank4.json"), "{\"alias\": \"toBank4\"}"); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        assertEquals(crash, unsigned.apply("GET", "/toBank3/v1/network/banks"));
        node.tick(); assertNotNull(node.lastHeartbeatError());
        node.close(); assertEquals(List.of("toBank3", "toBank4"), List.copyOf(registry.read().keySet()));
        assertThrows(FinancePeering.Unreachable.class, () -> unsigned.apply("GET", "/health"));
    }
}
