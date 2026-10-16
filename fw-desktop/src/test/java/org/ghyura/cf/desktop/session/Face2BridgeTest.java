// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
package org.ghyura.cf.desktop.session;

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.ghyura.cf.desktop.session.Face2TestClient.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Face2BridgeTest {
    static final String PAIR = """
            {"version":1,"revision":0,"paramsPatch":[],"edits":[{"bond":{"polarity":"forbid","sides":{"A":["a"],"B":["1"]}}}]}
            """;

    @Test void servedWebEditsReachActualPreviewAndSavedScenario(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir); Session session = session();
        AtomicInteger saves = new AtomicInteger(); AtomicReference<String> fingerprint = new AtomicReference<>("");
        try (var bridge = new Face2Bridge(session, scenario, SieveDocument.empty(), values(), Set.of(), 100, next -> {
            session.validateSieve(scenario, next.json(), Cancellation.NONE);
            fingerprint.set(SieveFiles.write(ConstraintSidecarReader.companionOf(scenario), next.json(), fingerprint.get()));
            saves.incrementAndGet(); return CompletableFuture.completedFuture(null);
        })) {
            var page = exchange(bridge, "", "GET", ""); bridge.handle(page);
            assertEquals(200, page.status); assertTrue(page.text().contains("Face 2 (Sieve)"));
            assertTrue(page.text().contains("id=\"stagewrap\"")); assertTrue(page.text().contains("Transport adapter only"));
            var impact = exchange(bridge, "impact", "POST", PAIR); bridge.handle(impact);
            assertEquals(200, impact.status); assertEquals("3", field(impact.text(), "kept")); assertEquals("4", field(impact.text(), "total"));
            assertFalse(Files.exists(ConstraintSidecarReader.companionOf(scenario)), "preview must not save");
            var submit = exchange(bridge, "submit", "POST", PAIR); bridge.handle(submit);
            assertEquals(200, submit.status, submit.text()); assertEquals(1, saves.get());
            assertEquals(1, session.openSieve(scenario).document().rules().size());
            assertEquals(BigInteger.valueOf(3), survivors(session, scenario));
            var stale = exchange(bridge, "submit", "POST", PAIR); bridge.handle(stale);
            assertEquals(409, stale.status); assertEquals(1, saves.get());
            var again = exchange(bridge, "", "GET", ""); bridge.handle(again);
            assertTrue(again.text().contains("\"jvmRevision\": 1"));
            var remove = exchange(bridge, "submit", "POST", "{\"version\":1,\"revision\":1,\"edits\":[],\"paramsPatch\":[]}");
            bridge.handle(remove); assertEquals(200, remove.status, remove.text());
            assertEquals(BigInteger.valueOf(4), survivors(session, scenario)); assertEquals(2, saves.get());
        }
    }

    @Test void invalidRulesAndRefusedSavesAreNeverAcknowledged(@TempDir Path dir) throws Exception {
        AtomicInteger saves = new AtomicInteger();
        try (var bridge = new Face2Bridge(session(), scenario(dir), SieveDocument.empty(), values(), Set.of(), 100, next -> {
            saves.incrementAndGet(); return CompletableFuture.failedFuture(new IllegalStateException("JVM document changed"));
        })) {
            String invalid = """
                    {"version":1,"revision":0,"paramsPatch":[],"edits":[{"bond":{"polarity":"forbid","sides":{"A":[],"B":[]},"when":"A.value >"}}]}
                    """;
            var bad = exchange(bridge, "submit", "POST", invalid); bridge.handle(bad);
            assertEquals(422, bad.status); assertEquals(0, saves.get());
            var conflict = exchange(bridge, "submit", "POST", PAIR); bridge.handle(conflict);
            assertEquals(409, conflict.status); assertTrue(conflict.text().contains("JVM document changed"));
            assertFalse(Files.exists(ConstraintSidecarReader.companionOf(dir.resolve("small.json"))));
        }
    }

    @Test void boundedImpactIsNotReportedAsExactAndIndependentRequiresRemainIndependent(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir); Session session = session();
        try (var bridge = new Face2Bridge(session, scenario, SieveDocument.empty(), values(), Set.of(), 1,
                next -> CompletableFuture.completedFuture(null))) {
            var impact = exchange(bridge, "impact", "POST", PAIR); bridge.handle(impact);
            assertEquals(200, impact.status, impact.text()); assertTrue(impact.text().contains("\"exact\": false"));
            assertTrue(impact.text().contains("scan limit 1"));
        }
        String requires = """
                {"version":1,"revision":0,"paramsPatch":[],"edits":[
                 {"bond":{"polarity":"require","sides":{"A":["a"],"B":["1"]}}},
                 {"bond":{"polarity":"require","sides":{"A":["b"],"B":["2"]}}}]}
                """;
        try (var bridge = new Face2Bridge(session, scenario, SieveDocument.empty(), values(), Set.of(), 100,
                next -> CompletableFuture.completedFuture(null))) {
            var impact = exchange(bridge, "impact", "POST", requires); bridge.handle(impact);
            assertEquals(200, impact.status, impact.text()); assertEquals("0", field(impact.text(), "kept"));
        }
    }

    @Test void endpointEnforcesTokenOriginMethodJsonAndLifecycle(@TempDir Path dir) throws Exception {
        var bridge = new Face2Bridge(session(), scenario(dir), SieveDocument.empty(), values(), Set.of(), 100,
                next -> CompletableFuture.completedFuture(null));
        try {
            var external = exchange(bridge, "submit", "POST", PAIR); external.headers.set("Origin", "https://example.com");
            bridge.handle(external); assertEquals(403, external.status);
            var host = exchange(bridge, "submit", "POST", PAIR); host.headers.set("Host", "example.com");
            bridge.handle(host); assertEquals(403, host.status);
            var token = new Exchange("/face2/wrong/submit", "POST", PAIR); bridge.handle(token); assertEquals(403, token.status);
            var method = exchange(bridge, "submit", "GET", ""); bridge.handle(method); assertEquals(405, method.status);
            var type = exchange(bridge, "submit", "POST", PAIR); type.headers.set("Content-Type", "text/plain");
            bridge.handle(type); assertEquals(415, type.status);
            var unknown = exchange(bridge, "missing", "POST", PAIR); bridge.handle(unknown); assertEquals(404, unknown.status);
        } finally { bridge.close(); }
        var closed = exchange(bridge, "", "GET", ""); bridge.handle(closed); assertEquals(410, closed.status);
    }

    @Test void liveLoopbackServesThePackagedPage(@TempDir Path dir) throws Exception {
        try (var bridge = new Face2Bridge(session(), scenario(dir), SieveDocument.empty(), values(), Set.of(), 100,
                next -> CompletableFuture.completedFuture(null))) {
            URI uri;
            try { uri = bridge.start(); }
            catch (java.net.SocketException e) {
                assumeTrue(!String.valueOf(e.getMessage()).contains("Operation not permitted"), "Environment forbids loopback sockets; HTTP handler tested without sockets.");
                throw e;
            }
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode()); assertTrue(response.body().contains("id=\"quick-add\""));
            }
        }
    }

    private static String field(String json, String name) { return SieveDocument.text(SieveDocument.map(ConstraintBonds.parse(json, "response")).get(name)); }
    static Session session() { return Session.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.plan.autoPromoteSingleVerb", "false", "test"))); }
    static Map<String, List<String>> values() { return Map.of("A", List.of("a", "b"), "B", List.of("1", "2")); }
    static Path scenario(Path dir) throws Exception { return Files.writeString(dir.resolve("small.json"), """
            {"sheets":{"FW_Seq":[["A","FW_Combi(1)"],["B","FW_Combi(1)"]],"A":[["a"],["b"]],"B":[["1"],["2"]]}}
            """); }
    private static BigInteger survivors(Session session, Path scenario) {
        return session.preview(scenario, new CandidatePreview.Request(BigInteger.valueOf(100), BigInteger.ZERO, 10, true, true), Cancellation.NONE)
                .estimates().candidates().stream().filter(s -> s.name().equals("executable")).findFirst().orElseThrow().count().value();
    }

}
