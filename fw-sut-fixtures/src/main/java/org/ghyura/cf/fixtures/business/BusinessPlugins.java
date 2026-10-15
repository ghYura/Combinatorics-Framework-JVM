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

import org.ghyura.cf.model.Json;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.MemoryReservation;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.ghyura.cf.fixtures.business.FixtureValues.*;

/** ServiceLoader adapters for four business domains. All mutable domain state is confined to one sample. */
public final class BusinessPlugins {
    private BusinessPlugins() { }
    private static final int MAX_ACTIONS = 512;
    private static final int MAX_WORK = 4096;
    private static final int MAX_CHARACTERS = 65536;

    public abstract static class Base implements SutPlugin {
        @Override public String version() { return id() + "@8d5aa2d/java-1"; }
        protected abstract DomainObservation run(SutCandidate candidate, ExecutionContext context);
        @Override public SutSession open(ExecutionContext context) {
            return new SutSession() {
                @Override public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) {
                    ctx.cancellation().throwIfCancelled();
                    long characters = candidate.selections().stream().flatMap(s -> s.values().stream()).mapToLong(String::length).sum();
                    if (characters > MAX_CHARACTERS) throw new IllegalArgumentException("Business fixture text exceeds " + MAX_CHARACTERS);
                    // Before parsing/action execution, reserve for their bounded worst-case mutable state and returned JSON.
                    MemoryReservation memory = ctx.reserve(65536L + characters * 128 + MAX_WORK * 8192L, id() + " domain state");
                    try { return run(candidate, ctx); } finally { memory.close(); }
                }
                @Override public void close() { }
            };
        }
    }

    public static final class Pricing extends Base {
        @Override public String id() { return "bundle-pricing"; }
        @Override public Map<Integer, String> verdictNames() {
            return Map.of(0, "PASS", 1, "PRICE_MISMATCH", 2, "DOUBLE_CHARGE");
        }
        @Override protected DomainObservation run(SutCandidate c, ExecutionContext ctx) {
            String mode = one(c, "mode", "strict"), region = one(c, "region", "us");
            List<String> coupons = selected(c, "coupons"), stages = selected(c, "stages");
            if (stages.isEmpty()) stages = List.of("discount", "tax", "round");
            PricingSut sut = new PricingSut();
            PricingSut.Request request = new PricingSut.Request("sample", "key", mode, region, coupons, stages);
            Map<String, Object> first = sut.checkout(request); requireOk(first);
            Map<String, Object> last = first;
            boolean duplicate = c.selection("duplicate_submit").filter(s -> s.inCandidate()).isPresent();
            if (duplicate) { ctx.cancellation().throwIfCancelled(); last = sut.checkout(request); }
            double expected = PricingSut.canonicalPrice(coupons, region);
            int verdict = Double.compare((Double) first.get("price"), expected) == 0 ? 0 : 1;
            if (duplicate && !first.get("ledger_total").equals(last.get("ledger_total"))) verdict = 2;
            return new DomainObservation(verdict, Map.of("price", (Double) first.get("price"), "ledger_total", (Double) last.get("ledger_total"),
                    "expected_price", expected), Map.of("response", json(last), "interaction", json(c.selections().stream().map(Object::toString).toList())));
        }
    }

    public static final class Security extends Base {
        @Override public String id() { return "bundle-secure-pipeline"; }
        @Override public Map<Integer, String> verdictNames() {
            return Map.of(0, "PASS", 1, "REDACTION_LEAK", 2, "STALE_KEY", 3, "CACHE_POISON");
        }
        @Override protected DomainObservation run(SutCandidate c, ExecutionContext ctx) {
            SecurePipelineSut sut = new SecurePipelineSut();
            boolean rotate = c.selection("rotate_key").filter(s -> s.inCandidate()).isPresent();
            boolean poison = c.selection("poison_cache").filter(s -> s.inCandidate()).isPresent();
            if (rotate) sut.rotateKey("sample"); if (poison) sut.poisonCache("sample");
            List<String> order = selected(c, "order"), features = selected(c, "features");
            if (order.isEmpty()) order = List.of("normalize", "redact", "tag");
            String text = one(c, "text", "note SECRET-ABC end");
            Map<String, Object> result = sut.process(new SecurePipelineSut.Request("sample", one(c, "mode", "strict"), order, features, text));
            requireOk(result);
            String actualText = (String) result.get("text");
            int verdict = actualText.matches("(?s).*SECRET-[A-Za-z0-9]+.*") ? 1 : 0;
            if ((Integer) result.get("key_version") != (rotate ? 2 : 1)) verdict = 2;
            if (!result.get("digest").equals(SecurePipelineSut.digest(actualText, (Integer) result.get("key_version")))) verdict = 3;
            ctx.cancellation().throwIfCancelled();
            return new DomainObservation(verdict, Map.of("key_version", ((Integer) result.get("key_version")).doubleValue()),
                    Map.of("response", json(result)));
        }
    }

    public static final class Billing extends Base {
        @Override public String id() { return "billing-engine"; }
        @Override public Map<Integer, String> verdictNames() { return Map.of(0, "PASS", 1, "INVARIANT_VIOLATION"); }
        @Override protected DomainObservation run(SutCandidate c, ExecutionContext ctx) {
            BillingSut sut = new BillingSut(one(c, "plan", "basic"), Integer.parseInt(one(c, "cycle_days", "30")),
                    one(c, "proration", "credit"), one(c, "rounding", "half_up"), Long.parseLong(one(c, "trial_days", "0")), false);
            List<Map<String, Object>> actions = actions(c, false);
            for (Map<String, Object> action : actions) {
                String op = text(action, "op", "");
                if (op.equals("tick")) {
                    int days = integer(action, "days", 1).intValueExact();
                    for (int i = 0; i < days; i++) { ctx.cancellation().throwIfCancelled(); sut.tick(1); }
                } else { ctx.cancellation().throwIfCancelled(); BusinessActions.billing(sut, action); }
            }
            List<String> bad = sut.audit();
            return new DomainObservation(bad.isEmpty() ? 0 : 1, Map.of("violations", (double) bad.size()),
                    Map.of("audit", String.join(",", bad), "state", json(sut.snapshot())));
        }
    }

    public static final class Fulfilment extends Base {
        @Override public String id() { return "order-fulfilment"; }
        @Override public Map<Integer, String> verdictNames() { return Map.of(0, "PASS", 1, "INVARIANT_VIOLATION"); }
        @Override protected DomainObservation run(SutCandidate c, ExecutionContext ctx) {
            String retry = one(c, "retry", "true");
            if (!retry.equals("true") && !retry.equals("false")) throw new IllegalArgumentException("retry requires true or false");
            OrderFulfilmentSut sut = new OrderFulfilmentSut(Integer.parseInt(one(c, "timeout", "3")),
                    one(c, "compensate_order", "release_first"), Boolean.parseBoolean(retry));
            for (Map<String, Object> action : actions(c, true)) {
                String op = text(action, "op", "");
                if (op.equals("tick")) {
                    int n = integer(action, "n", 1).intValueExact();
                    for (int i = 0; i < n; i++) { ctx.cancellation().throwIfCancelled(); sut.tick(1); }
                } else if (op.equals("drain")) {
                    int limit = integer(action, "limit", 200).intValueExact();
                    for (int i = 0; i < limit && sut.inflight() > 0; i++) { ctx.cancellation().throwIfCancelled(); sut.deliver(0); }
                } else { ctx.cancellation().throwIfCancelled(); BusinessActions.fulfilment(sut, action); }
            }
            List<String> bad = sut.audit();
            return new DomainObservation(bad.isEmpty() ? 0 : 1, Map.of("violations", (double) bad.size(), "inflight", (double) sut.inflight()),
                    Map.of("audit", String.join(",", bad), "state", json(sut.snapshot())));
        }
    }

    private static void requireOk(Map<String, Object> response) {
        if (!Boolean.TRUE.equals(response.get("ok"))) throw new IllegalArgumentException((String) response.get("error"));
    }

    private static List<Map<String, Object>> actions(SutCandidate candidate, boolean fulfilment) {
        List<String> values = selected(candidate, "actions");
        if (values.size() > MAX_ACTIONS) throw new IllegalArgumentException("Too many business actions");
        List<Map<String, Object>> actions = new ArrayList<>();
        long work = 0;
        int submitted = 0;
        for (String value : values) {
            Map<String, Object> action = object(Json.parse(value, "actions"));
            String op = text(action, "op", "");
            int steps = switch (op) {
                case "tick" -> integer(action, fulfilment ? "n" : "days", 1).intValueExact();
                case "drain" -> integer(action, "limit", 200).intValueExact();
                default -> 1;
            };
            if (steps > MAX_WORK) throw new IllegalArgumentException("Business action exceeds work limit");
            if (op.equals("submit")) submitted++;
            // A fulfilment tick can retry every order, rather than only doing one operation.
            int weight = fulfilment && op.equals("tick") ? Math.max(1, submitted) : 1;
            work = Math.addExact(work, (long) Math.max(0, steps) * weight);
            if (work > MAX_WORK) throw new IllegalArgumentException("Business sequence exceeds work limit");
            actions.add(action);
        }
        return List.copyOf(actions);
    }
}
