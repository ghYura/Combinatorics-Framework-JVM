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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;

/**
 * The source on_hold_recover.py flow and node controls on two in-JVM nodes in one temporary registry directory; the output
 * holds observations, never expected answers. Account IDs appear as $alice and $bob.
 */
public final class FinancePeeringScenario {
    private FinancePeeringScenario() { }
    private static final String SECRET = "demo-secret";
    /** One deterministic world: a shared clock in seconds, separate ID ranges per node, counted nonces. */
    private static final class World {
        double now = 1000; int nonce;
        final FinancePeering.Network network = new FinancePeering.Network();
        final FinancePeering.Registry registry;
        World(Path directory) { registry = new FinancePeering.Registry(directory, () -> now, 4242); }
        Function<FinanceTime, FinanceEcosystem> ecosystems(int base) {
            var counters = new HashMap<String, AtomicInteger>(); var refs = new AtomicInteger(); var tokens = new AtomicInteger();
            return time -> new FinanceEcosystem(time, Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC), () -> now,
                    prefix -> prefix + "_" + String.format(Locale.ROOT, "%016x", (long) base + counters.computeIfAbsent(prefix, k -> new AtomicInteger()).incrementAndGet()),
                    () -> 1, () -> String.format(Locale.ROOT, "TRF-%08X", base + refs.incrementAndGet()), () -> "token-" + base + "-" + tokens.incrementAndGet());
        }
        FinanceNode node(String alias, String bank, int port, int base) {
            return new FinanceNode(alias, bank, bank + " Bank", "127.0.0.1", port, SECRET, "XX", null, registry, network, () -> now, () -> "peer-nonce-" + (++nonce), ecosystems(base));
        }
        FinanceGateway.Request signed(String method, String path, byte[] body, double at, String signature) {
            String ts = Long.toString((long) at), n = "client-nonce-" + (++nonce);
            return new FinanceGateway.Request(method, path, body, Map.of("x-timestamp", ts, "x-nonce", n,
                    "x-signature", signature != null ? signature : FinanceSecurity.sign(SECRET, method, path, body, ts, n), "content-type", "application/json"), Map.of());
        }
        FinanceGateway.Response send(FinanceNode via, FinanceGateway.Request request) {
            return network.exchange(request.method(), via.baseUrl(), request.path(), request.body(), request.headers(), request.query());
        }
        FinanceGateway.Response call(FinanceNode via, String method, String path, Object payload) {
            byte[] body = payload == null ? new byte[0] : FinancePeering.dumps(payload).getBytes(StandardCharsets.UTF_8);
            return send(via, signed(method, path, body, now, null));
        }
        Map<?, ?> ok(FinanceNode via, String method, String path, Object payload) {
            var response = call(via, method, path, payload);
            if (response.status() != 200) throw new IllegalStateException(method + " " + path + " -> " + response.status() + " " + response.body());
            return (Map<?, ?>) response.body();
        }
        Object health(FinanceNode node) { return network.exchange("GET", node.baseUrl(), "/health", new byte[0], Map.of(), Map.of()).body(); }
    }
    public static Map<String, Object> run(Map<?, ?> request) {
        if (!request.keySet().equals(Set.of("scenario")) || !"source_multinode".equals(request.get("scenario")))
            throw new IllegalArgumentException("only source_multinode profile supported");
        Path directory;
        try { directory = Files.createTempDirectory("cf-finance-peering-"); } catch (IOException e) { throw new UncheckedIOException(e); }
        try { return run(new World(directory)); } finally { delete(directory); }
    }
    private static Map<String, Object> run(World w) {
        var node1 = w.node("toBank1", "AURUM", 8001, 0x10000000); var node2 = w.node("toBank2", "NORD", 8002, 0x20000000);
        var discovery = new LinkedHashMap<String, Object>();
        discovery.put("health_before", w.health(node1));
        node1.refreshPeers(); discovery.put("peers_after_refresh", node1.peerStates());
        node1.heartbeat(); node2.heartbeat(); discovery.put("peers_after_heartbeat", node1.peerStates());
        discovery.put("remote_bank", node1.ecosystem().network().bank("NORD").view());

        var alice = w.ok(node1, "POST", "/toBank1/v1/clients", fields("display_name", "Alice", "email", "a@x.com", "kyc_status", "VERIFIED"));
        String aid = (String) w.ok(node1, "POST", "/toBank1/v1/banks/AURUM/accounts", fields("client_id", alice.get("client_id"), "currency", "USD", "initial_deposit", "500.00")).get("account_id");
        var bob = w.ok(node2, "POST", "/toBank2/v1/clients", fields("display_name", "Bob", "email", "b@x.com", "kyc_status", "VERIFIED"));
        String bid = (String) w.ok(node2, "POST", "/toBank2/v1/banks/NORD/accounts", fields("client_id", bob.get("client_id"), "currency", "USD")).get("account_id");
        Function<String, String> transfer = amount -> {
            var created = w.ok(node1, "POST", "/toBank1/v1/transfers", fields("source_account_id", aid, "target_bank", "NORD", "target_account_id", bid, "amount", amount, "idempotency_key", "key-" + amount));
            String ref = (String) ((Map<?, ?>) created.get("transfer")).get("reference"); var tokens = (Map<?, ?>) created.get("confirmation_tokens");
            for (String party : List.of("SENDER", "RECEIVER")) w.ok(node1, "POST", "/toBank1/v1/transfers/" + ref + "/confirm", fields("party", party, "token", tokens.get(party)));
            return ref;
        };
        Function<String, Map<?, ?>> status = ref -> w.ok(node1, "GET", "/toBank1/v1/transfers/" + ref, null);
        Function<String, Map<?, ?>> balance = account -> account.equals(aid) ? w.ok(node1, "GET", "/toBank1/v1/accounts/" + aid + "/balance", null)
                : w.ok(node2, "GET", "/toBank2/v1/accounts/" + bid + "/balance", null);

        var recovery = new ArrayList<Map<String, Object>>();
        String first = transfer.apply("120.00");
        recovery.add(fields("step", 1, "transfer", "120.00", "status", status.apply(first).get("status"), "alice", balance.apply(aid).get("balance"), "bob", balance.apply(bid).get("balance")));
        w.network.freeze(node2.baseUrl()); w.now += 6; node1.tick();
        Object seen = ((Map<?, ?>) w.health(node1)).get("peers");
        String held = transfer.apply("50.00"); var st = status.apply(held); var bal = balance.apply(aid);
        recovery.add(fields("step", 2, "toBank2", "frozen", "peers_seen_by_toBank1", seen, "transfer", "50.00", "status", st.get("status"), "failure", st.get("failure"),
                "alice_balance", bal.get("balance"), "alice_hold", bal.get("hold"), "alice_available", bal.get("available")));
        w.network.resume(node2.baseUrl()); w.now += 2; node1.tick();
        bal = balance.apply(aid);
        recovery.add(fields("step", 3, "toBank2", "resumed", "status", status.apply(held).get("status"), "alice", bal.get("balance"), "alice_hold", bal.get("hold"),
                "bob", balance.apply(bid).get("balance"), "positions_at_toBank1", node1.ecosystem().network().positions()));

        var proxy = new ArrayList<Map<String, Object>>();
        var proxied = w.call(node1, "GET", "/toBank2/v1/accounts/" + bid + "/balance", null);
        proxy.add(fields("path", "/toBank2/v1/accounts/$bob/balance", "status", proxied.status(), "balance", ((Map<?, ?>) proxied.body()).get("balance")));
        var unknown = w.call(node1, "GET", "/toBank9/v1/network/banks", null);
        proxy.add(fields("path", "/toBank9/v1/network/banks", "status", unknown.status(), "reply", unknown.body()));
        w.network.freeze(node2.baseUrl()); w.now += 2;
        var frozen = w.call(node1, "GET", "/toBank2/v1/network/banks", null);
        proxy.add(fields("toBank2", "frozen", "path", "/toBank2/v1/network/banks", "status", frozen.status(), "reply", frozen.body()));
        w.network.resume(node2.baseUrl()); w.now += 2; node1.tick();

        var propagation = new ArrayList<Map<String, Object>>();
        var put = w.ok(node1, "PUT", "/toBank1/v1/time-config", fields("values", fields("risk_window_seconds", 12), "propagate", "connected"));
        propagation.add(fields("node", "toBank1", "propagated", put.get("propagated"), "toBank2_risk_window", node2.time().seconds("risk_window_seconds"),
                "toBank2_file_risk_window", FinanceTime.load(node2.time().path()).seconds("risk_window_seconds")));
        w.ok(node2, "PUT", "/toBank2/v1/time-config", fields("accept_sync", false));
        propagation.add(fields("node", "toBank2", "toBank2_file_accept", FinanceTime.load(node2.time().path()).acceptSync()));
        put = w.ok(node1, "PUT", "/toBank1/v1/time-config", fields("values", fields("risk_window_seconds", 13), "propagate", "connected"));
        propagation.add(fields("node", "toBank1", "propagated", put.get("propagated"), "toBank2_risk_window", node2.time().seconds("risk_window_seconds")));
        w.network.freeze(node2.baseUrl());
        put = w.ok(node1, "PUT", "/toBank1/v1/time-config", fields("values", fields("risk_window_seconds", 14), "propagate", "connected"));
        propagation.add(fields("node", "toBank1", "toBank2", "frozen", "propagated", put.get("propagated")));
        w.network.resume(node2.baseUrl()); w.now += 2; node1.tick();

        var inbound = new ArrayList<Map<String, Object>>();
        String before = (String) balance.apply(bid).get("balance");
        byte[] credit = FinancePeering.dumps(fields("account_id", bid, "amount", "5.00", "currency", "USD", "reference", "MANUAL-1")).getBytes(StandardCharsets.UTF_8);
        var once = w.signed("POST", "/interbank/inbound", credit, w.now, null);
        inbound.add(observed(w.send(node2, once)));
        inbound.add(observed(w.send(node2, once)));
        inbound.get(1).put("bob_balance_delta", Money.of(balance.apply(bid).get("balance"), "USD").subtract(Money.of(before, "USD")).asString());
        inbound.add(observed(w.send(node2, w.signed("POST", "/interbank/inbound", credit, w.now, "deadbeef"))));
        byte[] missing = FinancePeering.dumps(fields("account_id", "acc_missing", "amount", "1.00", "currency", "USD", "reference", "MANUAL-2")).getBytes(StandardCharsets.UTF_8);
        inbound.add(observed(w.send(node2, w.signed("POST", "/interbank/inbound", missing, w.now, null))));
        node2.ecosystem().network().sanctions().client((String) bob.get("client_id"));
        inbound.add(observed(w.send(node2, w.signed("POST", "/interbank/inbound", credit, w.now, null))));
        w.ok(node2, "PUT", "/toBank2/v1/time-config", fields("values", fields("replay_window_seconds", 1)));
        byte[] empty = FinancePeering.dumps(fields("values", fields())).getBytes(StandardCharsets.UTF_8);
        var stale = w.send(node2, w.signed("POST", "/interbank/time-config", empty, w.now - 100, null));
        var staleGateway = w.send(node2, w.signed("GET", "/toBank2/v1/network/banks", new byte[0], w.now - 100, null));
        inbound.add(fields("live_replay_window", node2.time().seconds("replay_window_seconds"), "signed_seconds_ago", 100, "interbank_status", stale.status(), "mounted_gateway_status", staleGateway.status()));

        var files = new LinkedHashMap<String, Object>();
        for (var node : List.of(node1, node2)) {
            try { files.put(node.alias(), Files.readString(node.time().path(), StandardCharsets.UTF_8)); } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        var registered = List.copyOf(w.registry.read().keySet()); node1.close(); node2.close();
        files.put("registry_before_close", registered); files.put("registry_after_close", List.copyOf(w.registry.read().keySet()));
        var out = fields("discovery", discovery, "recovery", recovery, "proxy", proxy, "propagation", propagation, "inbound", inbound, "files", files);
        return replaceIds(out, aid, bid);
    }
    private static Map<String, Object> observed(FinanceGateway.Response response) {
        Object body = response.body() instanceof FinancePeering.Text text ? "text:" + text.value() : response.body();
        return fields("status", response.status(), "reply", body);
    }
    @SuppressWarnings("unchecked")
    private static <T> T replaceIds(T value, String alice, String bob) {
        if (value instanceof String s) return (T) (s.equals(alice) ? "$alice" : s.equals(bob) ? "$bob" : s);
        if (value instanceof List<?> list) return (T) list.stream().map(v -> replaceIds(v, alice, bob)).toList();
        if (value instanceof Map<?, ?> map) { var out = new LinkedHashMap<String, Object>(); map.forEach((k, v) -> out.put((String) k, replaceIds(v, alice, bob))); return (T) out; }
        return value;
    }
    private static void delete(Path directory) {
        try (var walk = Files.walk(directory)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
