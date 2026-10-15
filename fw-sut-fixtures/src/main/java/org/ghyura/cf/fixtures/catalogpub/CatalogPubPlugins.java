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

import org.ghyura.cf.model.Json;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.MemoryReservation;
import org.ghyura.cf.spi.SheetSelection;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.ghyura.cf.fixtures.business.FixtureValues.one;
import static org.ghyura.cf.fixtures.business.FixtureValues.selected;

/** Bounded in-JVM adapters. Seeds and mutable domain state belong to the candidate, independent of worker order. */
public final class CatalogPubPlugins {
    private CatalogPubPlugins() { }
    public abstract static class Base implements SutPlugin {
        @Override public String version() { return id() + "@8d5aa2d/cpython-3.14/java-1"; }
        protected abstract DomainObservation run(SutCandidate candidate, ExecutionContext context);
        @Override public SutSession open(ExecutionContext context) {
            return new SutSession() {
                @Override public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) {
                    ctx.cancellation().throwIfCancelled();
                    long values = candidate.selections().stream().mapToLong(s -> s.values().size()).sum();
                    long chars = candidate.selections().stream().flatMap(s -> s.values().stream()).mapToLong(String::length).sum();
                    if (values > 1024 || chars > 16384) throw new IllegalArgumentException("Catalog/pub fixture input exceeds 1024 values or 16384 characters");
                    // Includes bounded parse state, responses and at most 128 simulation rounds with copies of input lists.
                    MemoryReservation reservation = ctx.reserve(1048576L + chars * 1024 + values * 4096, id() + " fixture state");
                    try { return run(candidate, ctx); } finally { reservation.close(); }
                }
                @Override public void close() { }
            };
        }
    }
    public static final class Catalog extends Base {
        @Override public String id() { return "telemetry-catalog"; }
        @Override public Map<Integer, String> verdictNames() { return Map.of(0, "VALID", 1, "INVALID_QUERY", 2, "NOT_FOUND"); }
        @Override protected DomainObservation run(SutCandidate c, ExecutionContext ctx) {
            Map<String, Object> params = new LinkedHashMap<>(); params.put("accessKey", "k");
            for (String key : TelemetryRules.QUERY_PARAMS) if (c.selection(key).filter(SheetSelection::inCandidate).isPresent()) {
                params.put(key, Set.of("SignalClassList", "AccessTier", "sortBy").contains(key) ? selected(c, key) : one(c, key, ""));
            }
            String path = one(c, "path", TelemetryRules.assembleUrl("", one(c, "locator", "sample"), params));
            String body = one(c, "body", "");
            TelemetryCatalog.Response response = TelemetryCatalog.handle(new TelemetryCatalog.Request(one(c, "method", "GET"), path,
                    body.isEmpty() ? null : PythonValues.object(Json.parse(body, "catalog body"))));
            return new DomainObservation(response.status() == 200 ? 0 : response.status() == 400 ? 1 : 2,
                    Map.of("status", (double) response.status()), Map.of("response", response.body(), "content_type", response.contentType(), "request", path));
        }
    }
    public static final class TesterPub extends Base {
        @Override public String id() { return "tester-pub"; }
        @Override public Map<Integer, String> verdictNames() { return Map.of(0, "ADEQUATE", 1, "DEFECT"); }
        @Override protected DomainObservation run(SutCandidate c, ExecutionContext ctx) {
            BigInteger seed = new BigInteger(one(c, "seed", "1"));
            String barman = one(c, "barman", ""), competence = one(c, "competence", "");
            List<Pub.Barman> barmen = competence.isEmpty() ? List.of() : List.of(new Pub.Barman(barman.isEmpty() ? "Fixed" : barman, Double.parseDouble(competence)));
            Pub pub = new Pub(seed, Double.parseDouble(one(c, "reliability", "0.6")), barmen, List.of());
            String strictValue = one(c, "strict", "false");
            if (!Set.of("true", "false").contains(strictValue)) throw new IllegalArgumentException("strict requires true or false");
            boolean strict = Boolean.parseBoolean(strictValue);
            String operation = one(c, "operation", "serve");
            if (operation.equals("simulate")) {
                int rounds = Integer.parseInt(one(c, "rounds", "12"));
                if (rounds > 128) throw new IllegalArgumentException("Pub simulation exceeds 128 rounds");
                List<List<Object>> transcript = new ArrayList<>(); int defects = 0;
                for (int i = 0; i < rounds; i++) {
                    ctx.cancellation().throwIfCancelled(); List<Object> row = pub.simulate(1, strict).getFirst(); transcript.add(row);
                    if (!Boolean.TRUE.equals(PythonValues.object(row.get(2)).get("adequate"))) defects++;
                }
                return new DomainObservation(defects == 0 ? 0 : 1, Map.of("rounds", (double) transcript.size(), "defects", (double) defects), Map.of("transcript", PythonValues.json(transcript)));
            }
            if (!operation.equals("serve")) throw new IllegalArgumentException("Unknown pub operation " + operation);
            List<String> actions = c.selection("actions").filter(SheetSelection::inCandidate).map(SheetSelection::values).orElse(List.of("enter", "sit", "order"));
            Pub.Order order = new Pub.Order(actions, one(c, "quantity", "1"), one(c, "target", "beer"), selected(c, "garnish"), Set.copyOf(selected(c, "events")));
            Pub.Response response = pub.serve(order, barman.isEmpty() ? null : barman, seed, strict);
            return new DomainObservation(response.adequate() ? 0 : 1, Map.of("units", response.units().doubleValue(), "price", response.price()), Map.of("response", PythonValues.json(response.toMap())));
        }
    }
}
