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

package org.ghyura.cf.fixtures.catalogpub;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import static org.ghyura.cf.fixtures.business.FixtureValues.map;
import static org.ghyura.cf.fixtures.business.FixtureValues.strings;
import static org.ghyura.cf.fixtures.catalogpub.PythonValues.object;
import static org.junit.jupiter.api.Assertions.*;

/** Expectations were captured from unchanged original Python sources before the Java implementation. */
class CatalogPubTest {
    private record Case(String id, Map<String, Object> input, Object expected) { }
    private static Element root(String family) throws Exception {
        try (InputStream in = CatalogPubTest.class.getResourceAsStream(family + "-golden.xml")) {
            assertNotNull(in); DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(in).getDocumentElement();
        }
    }
    private static List<Case> cases(String family, int count) throws Exception {
        Element root = root(family); var entries = root.getElementsByTagName("case");
        assertEquals(count, entries.getLength()); assertEquals(Integer.toString(count), root.getAttribute("cases"));
        List<Case> result = new ArrayList<>();
        for (int i = 0; i < entries.getLength(); i++) {
            Element node = (Element) entries.item(i);
            result.add(new Case(node.getAttribute("id"), object(Json.parse(node.getElementsByTagName("input").item(0).getTextContent(), family)),
                    Json.parse(node.getElementsByTagName("expected").item(0).getTextContent(), family)));
        }
        return result;
    }
    private static void equal(Object expected, Object actual, String path) {
        if (expected instanceof Map<?, ?> m && m.size() == 1 && m.containsKey("$integer")) assertEquals(new BigInteger((String) m.get("$integer")), actual, path);
        else if (expected instanceof Number n && actual instanceof Number a) assertEquals(0, new BigDecimal(n.toString()).compareTo(new BigDecimal(a.toString())), path + ": " + n + " / " + a);
        else if (expected instanceof Map<?, ?> e && actual instanceof Map<?, ?> a) {
            assertEquals(e.keySet(), a.keySet(), path); e.forEach((k, v) -> equal(v, a.get(k), path + "/" + k));
        } else if (expected instanceof List<?> e && actual instanceof List<?> a) {
            assertEquals(e.size(), a.size(), path); for (int i = 0; i < e.size(); i++) equal(e.get(i), a.get(i), path + "/" + i);
        } else assertEquals(expected, actual, path);
    }
    private static Object outcome(Supplier<Object> call) {
        try { return map("value", call.get()); }
        catch (PythonValues.PythonError e) { return map("error", e.type(), "message", e.getMessage()); }
    }
    @Test void goldenProvenanceMatchesPinnedReferences() throws Exception {
        Path workspace = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (workspace != null && !Files.isDirectory(workspace.resolve("SUT"))) workspace = workspace.getParent();
        for (String family : List.of("telemetry", "random", "pub", "matrices")) {
            Element root = root(family); assertEquals("8d5aa2d48a605521f783d1787a42fc9a5ebfc7a9", root.getAttribute("commit"));
            var sources = root.getElementsByTagName("source"); assertEquals(4, sources.getLength());
            if (workspace != null) for (int i = 0; i < sources.getLength(); i++) {
                Element source = (Element) sources.item(i);
                assertEquals(source.getAttribute("sha256"), Canonical.sha256Hex(Files.readAllBytes(workspace.resolve("SUT").resolve(source.getAttribute("path")))));
            }
        }
    }
    @Test void telemetryRulesQueriesAndDispatcherMatchOriginalIncludingFailures() throws Exception {
        for (Case c : cases("telemetry", 252)) {
            Map<String, Object> p = c.input();
            Object actual = outcome(() -> switch ((String) p.get("op")) {
                case "unquote" -> TelemetryCatalog.unquote((String) p.get("value"));
                case "quote" -> TelemetryRules.quote((String) p.get("value"), "/");
                case "helpers" -> Arrays.asList(TelemetryRules.asList(p.get("value")), TelemetryRules.nullableInt(p.get("value")), TelemetryRules.present(map("v", p.get("value")), "v"));
                case "validate" -> TelemetryRules.validate(object(p.get("params")));
                case "assemble" -> List.of(TelemetryRules.assembleQuery(object(p.get("params"))), TelemetryRules.assembleUrl((String) p.get("base"), p.get("locator"), object(p.get("params"))));
                case "parse" -> TelemetryRules.parseQuery(p.get("locator"), object(p.get("query")));
                case "handle" -> {
                    TelemetryCatalog.Response r = TelemetryCatalog.handle(new TelemetryCatalog.Request((String) p.get("method"), (String) p.get("path"), p.get("body") == null ? null : object(p.get("body"))));
                    yield List.of(r.status(), r.contentType(), r.body());
                }
                default -> throw new AssertionError(p);
            });
            equal(c.expected(), actual, c.id());
        }
    }
    private static String bits(double d) { return Long.toHexString(Double.doubleToRawLongBits(d)); }
    @Test void randomDrawsMatchCPythonAcrossIntegerSeedsAndWordBoundaries() throws Exception {
        for (Case c : cases("random", 6)) {
            PythonRandom r = new PythonRandom(new BigInteger((String) c.input().get("seed")));
            Map<String, Object> actual = map("random", IntStream.range(0, 12).mapToObj(i -> bits(r.random())).toList());
            actual.put("bits", ((List<?>) c.input().get("bits")).stream().map(k -> r.getrandbits(((Number) k).intValue()).toString()).toList());
            actual.put("below", strings(c.input().get("bounds")).stream().map(n -> r.randbelow(new BigInteger(n)).toString()).toList());
            actual.put("choice", IntStream.range(0, 12).mapToObj(i -> r.choice(IntStream.range(0, 9).boxed().toList())).toList());
            actual.put("uniform", IntStream.range(0, 6).mapToObj(i -> bits(r.uniform(.5, 3))).toList());
            List<Integer> shuffle = new ArrayList<>(IntStream.range(0, 10).boxed().toList()); r.shuffle(shuffle); actual.put("shuffle", shuffle);
            actual.put("weighted", IntStream.range(0, 12).mapToObj(i -> r.weightedChoice(List.of(0, 1, 2, 3), List.of(1.05, .65, .41, .266))).toList());
            equal(c.expected(), actual, c.id());
        }
    }
    private static Pub.Order order(Map<String, Object> p) {
        return new Pub.Order(strings(p.getOrDefault("actions", List.of("enter", "sit", "order"))), p.getOrDefault("quantity", "1"),
                (String) p.getOrDefault("target", "beer"), strings(p.getOrDefault("garnish", List.of())), Set.copyOf(strings(p.getOrDefault("events", List.of()))));
    }
    @Test void pubOracleDefectsStrictModeAndStatefulTranscriptsMatchOriginal() throws Exception {
        Set<String> deviations = new java.util.HashSet<>();
        for (Case c : cases("pub", 924)) {
            Map<String, Object> p = c.input();
            Object actual = outcome(() -> {
                if (p.get("op").equals("adjudicate")) return Pub.adjudicate(order(object(p.get("order")))).toMap();
                BigInteger seed = new BigInteger((String) p.get("seed"));
                if (p.get("op").equals("serve")) {
                    Pub.Response result = new Pub(BigInteger.valueOf(99), .6, List.of(new Pub.Barman("Zero", 0)), List.of())
                            .serve(order(object(p.get("order"))), (String) p.get("barman"), seed, (Boolean) p.get("strict"));
                    if (result.deviation() != null) deviations.add(result.deviation()); return result.toMap();
                }
                if (p.get("op").equals("simulate")) {
                    Pub pub = new Pub(seed, ((Number) p.get("reliability")).doubleValue(), List.of(), List.of());
                    return List.of(pub.simulate(7, false), pub.simulate(5, false));
                }
                Pub pub = new Pub(BigInteger.valueOf(91));
                Pub.Order order = new Pub.Order(List.of("enter", "sit", "order"), "2", "beer", List.of("lime"), Set.of("happy_hour"));
                return List.of(pub.serve(order, null, seed, false).toMap(), pub.serve(null, null, null, false).toMap(),
                        pub.serve(order, null, seed, false).toMap(), pub.serve(null, null, null, false).toMap());
            });
            equal(c.expected(), actual, c.id());
        }
        assertEquals(11, deviations.size());
    }
}
