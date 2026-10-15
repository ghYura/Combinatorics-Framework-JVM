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

import org.ghyura.cf.fixtures.catalogpub.PythonValues;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;

/**
 * Source BankNode and its node app over in-JVM requests: its own bank's gateway under /{alias}, health, interbank inbound
 * and time-config routes, a proxy to discovered peers, and live time units persisted to the node's properties file.
 */
public final class FinanceNode implements AutoCloseable {
    /** settings.replay_window_seconds: the interbank routes read it once and ignore the live time config. */
    public static final int SETTINGS_REPLAY_WINDOW = 300;
    /** An exception the source does not handle; its node app answers 500 with a plain-text body. */
    static final class SourceCrash extends RuntimeException {
        private static final long serialVersionUID = 1L;
        SourceCrash(String message) { super(message); }
    }
    private static final FinancePeering.Text SERVER_ERROR = new FinancePeering.Text("Internal Server Error");
    private final String alias, bankCode, baseUrl, secret;
    private final FinanceTime time;
    private final FinanceEcosystem eco;
    private final FinanceBank bank;
    private final FinanceGateway gateway;
    private final FinancePeering.Registry registry;
    private final FinancePeering.Network network;
    private final DoubleSupplier seconds;
    private final Supplier<String> nonces;
    private final Map<String, FinancePeering.PeerLink> peers = new LinkedHashMap<>();
    private RuntimeException lastHeartbeatError;

    /** Source BankNode: loads or creates the time file (default registry/time_{alias}.properties), adds one bank, saves on every time change and registers. */
    public FinanceNode(String alias, String bankCode, String bankName, String host, int port, String secret, String jurisdiction, Path timeConfig,
                       FinancePeering.Registry registry, FinancePeering.Network network, DoubleSupplier seconds, Supplier<String> nonces,
                       Function<FinanceTime, FinanceEcosystem> ecosystems) {
        this.alias = text(alias); this.bankCode = text(bankCode); this.secret = text(secret); this.registry = Objects.requireNonNull(registry);
        this.network = Objects.requireNonNull(network); this.seconds = Objects.requireNonNull(seconds); this.nonces = Objects.requireNonNull(nonces);
        if (alias.isEmpty() || alias.contains("/")) throw new IllegalArgumentException("node alias must be one nonempty path segment");
        baseUrl = "http://" + text(host) + ":" + port;
        time = FinanceTime.load(timeConfig != null ? timeConfig : registry.directory().resolve("time_" + alias + ".properties"));
        eco = ecosystems.apply(time);
        bank = eco.addBank(bankCode, bankName, jurisdiction, false, null);
        eco.onTimeChange(time::save);
        eco.propagator(this::propagateTimeConfig);
        gateway = new FinanceGateway(eco, secret, "/" + alias, seconds);
        registry.register(alias, bankCode, baseUrl);
        network.attach(baseUrl, this::dispatch);
    }
    public String alias() { return alias; }
    public String baseUrl() { return baseUrl; }
    public FinanceEcosystem ecosystem() { return eco; }
    public FinanceBank bank() { return bank; }
    public FinanceTime time() { return time; }
    public Map<String, FinancePeering.PeerLink> peers() { return Collections.unmodifiableMap(peers); }
    /** The atexit hook: deregister; leaving the network also stops the in-JVM server. */
    @Override public void close() { registry.deregister(alias); network.detach(baseUrl); }

    public void refreshPeers() {
        for (var e : registry.read().entrySet()) {
            String peer = e.getKey(); var entry = e.getValue();
            if (peer.equals(alias) || peers.containsKey(peer)) continue;
            String code = entryText(entry, "bank_code"), url = entryText(entry, "base_url");
            var link = FinancePeering.PeerLink.builder(peer, code, url, secret).time(time).network(network).seconds(seconds).nonces(nonces).build();
            peers.put(peer, link);
            if (eco.banks().stream().noneMatch(b -> b.code().equals(code)) && !eco.remote(code))
                eco.addRemoteBank(code, code + " (peer)", new FinancePeering.PeerBankPort(link), "XX", false);
        }
    }
    private static String entryText(Map<String, Object> entry, String key) {
        if (!entry.containsKey(key)) throw new SourceCrash("KeyError: '" + key + "'");
        if (!(entry.get(key) instanceof String value)) throw new IllegalArgumentException("registry " + key + " must be a string");
        return text(value);
    }
    public void heartbeat() {
        refreshPeers();
        for (var link : peers.values()) link.ping();
        eco.retryOnHold();
    }
    /** One turn of the source heartbeat loop, which swallows every exception; the last one stays observable. */
    public void tick() {
        try { heartbeat(); lastHeartbeatError = null; } catch (RuntimeException e) { lastHeartbeatError = e; }
    }
    public RuntimeException lastHeartbeatError() { return lastHeartbeatError; }
    public Map<String, String> peerStates() {
        var out = new LinkedHashMap<String, String>(); peers.forEach((k, v) -> out.put(k, v.state().name())); return out;
    }
    /** Pushes new units to every peer; each peer's own accept_time_sync decides. */
    Map<String, Object> propagateTimeConfig(Map<String, ?> values) {
        var results = new LinkedHashMap<String, Object>();
        for (var e : peers.entrySet()) {
            try {
                var reply = e.getValue().call("POST", "/interbank/time-config", fields("values", values));
                results.put(e.getKey(), PythonValues.truth(reply.get("applied")) ? "applied" : reply.containsKey("reason") ? reply.get("reason") : "declined");
            } catch (ServiceError x) { results.put(e.getKey(), x.code()); }
        }
        return results;
    }

    /** The node app: the mounted gateway first, then health, the interbank routes and the proxy, as Starlette orders them. */
    public FinanceGateway.Response dispatch(FinanceGateway.Request request) {
        try { return route(request); }
        catch (SourceCrash e) { return new FinanceGateway.Response(500, SERVER_ERROR); }
    }
    private FinanceGateway.Response route(FinanceGateway.Request request) {
        String path = request.path(), method = request.method();
        if (path.startsWith("/" + alias + "/")) return gateway.dispatch(request);
        if (path.equals("/health"))
            return method.equals("GET") ? new FinanceGateway.Response(200, fields("ok", true, "alias", alias, "bank", bankCode, "peers", peerStates()))
                    : new FinanceGateway.Response(405, fields("detail", "Method Not Allowed"));
        if (method.equals("POST") && path.equals("/interbank/inbound")) return handled(() -> inbound(request));
        if (method.equals("POST") && path.equals("/interbank/time-config")) return handled(() -> timeConfig(request));
        int slash = path.indexOf('/', 1);
        if (path.startsWith("/") && slash > 1) {
            if (!List.of("GET", "POST", "PATCH", "PUT", "DELETE").contains(method)) return new FinanceGateway.Response(405, fields("detail", "Method Not Allowed"));
            return proxy(path.substring(1, slash), path.substring(slash + 1), request);
        }
        return new FinanceGateway.Response(404, fields("detail", "Not Found"));
    }
    private static FinanceGateway.Response handled(Supplier<Object> handler) {
        try { return new FinanceGateway.Response(200, handler.get()); } catch (ServiceError e) { return new FinanceGateway.Response(e.status(), e.view()); }
    }
    private Object verify(FinanceGateway.Request request) {
        byte[] raw = request.body(); var headers = request.headers();
        var verified = FinanceSecurity.verify(secret, request.method(), request.path(), raw, headers.get("x-timestamp"), headers.get("x-nonce"), headers.get("x-signature"),
                SETTINGS_REPLAY_WINDOW, null, seconds);
        if (!verified.ok()) throw new ServiceError("UNAUTHORIZED", 401, verified.reason(), Map.of());
        if (raw.length == 0) return Map.of();
        String body;
        try { body = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString(); }
        catch (CharacterCodingException e) { throw new SourceCrash("UnicodeDecodeError"); }
        try { return FinanceGateway.sourceJson(body); } catch (FinanceGateway.InvalidJson e) { throw new SourceCrash("JSONDecodeError"); }
    }
    private static Object key(Object body, String key) {
        if (!(body instanceof Map<?, ?> map)) throw new SourceCrash("TypeError: body[" + key + "]");
        if (!map.containsKey(key)) throw new SourceCrash("KeyError: '" + key + "'");
        return map.get(key);
    }
    private static String string(Object value) { if (!(value instanceof String s)) throw new IllegalArgumentException("source string carrier required"); return text(s); }
    private Object inbound(FinanceGateway.Request request) {
        Object body = verify(request);
        var account = bank.ledger().get(string(key(body, "account_id")));
        var client = eco.identity().tryGet(account.client());
        var hits = client == null ? List.<String>of() : eco.network().sanctions().screen(client);
        if (!hits.isEmpty()) throw new ServiceError("POLICY_BLOCKED", 403, "beneficiary is sanctioned", fields("reasons", hits));
        String target = string(key(body, "account_id"));
        Object amount = key(body, "amount"), currency = key(body, "currency");
        var money = Money.of(amount instanceof Double ? PythonValues.str(amount) : amount, PythonValues.truth(currency) ? PythonValues.str(currency) : "");
        bank.ledger().inbound(target, money, string(key(body, "reference")));
        return fields("ok", true, "credited", target, "bank", bankCode);
    }
    private Object timeConfig(FinanceGateway.Request request) {
        Object body = verify(request);
        if (!(body instanceof Map<?, ?> map)) throw new SourceCrash("AttributeError: get");
        Object values = map.get("values");
        if (!time.acceptSync()) return eco.updateTime(null, null, "self", true);
        if (!PythonValues.truth(values)) return eco.updateTime(null, null, "self", true);
        if (!(values instanceof Map<?, ?> units)) throw new SourceCrash("AttributeError: items");
        var copy = new LinkedHashMap<String, Object>(); units.forEach((k, v) -> copy.put((String) k, v));
        return eco.updateTime(copy, null, "self", true);
    }
    private FinanceGateway.Response proxy(String prefix, String rest, FinanceGateway.Request request) {
        refreshPeers();
        var link = peers.get(prefix);
        if (link == null) return new FinanceGateway.Response(404, fields("code", "NOT_FOUND", "message", "unknown bank node '" + prefix + "'"));
        if (link.stateNow() != FinancePeering.PeerState.AVAILABLE)
            return new FinanceGateway.Response(503, fields("code", "PEER_UNAVAILABLE", "message", "peer '" + prefix + "' is " + link.state().name(), "peer_state", link.state().name()));
        var headers = new LinkedHashMap<String, String>();
        request.headers().forEach((k, v) -> { if (List.of("x-timestamp", "x-nonce", "x-signature", "content-type").contains(k)) headers.put(k, v); });
        try { return network.exchange(request.method(), link.baseUrl(), "/" + prefix + "/" + rest, request.body(), headers, request.query()); }
        catch (FinancePeering.Unreachable e) {
            link.recordFailure(seconds.getAsDouble());
            return new FinanceGateway.Response(503, fields("code", "PEER_UNAVAILABLE", "message", "peer '" + prefix + "' unreachable"));
        }
    }
}
