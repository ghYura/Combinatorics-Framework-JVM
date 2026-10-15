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
import org.ghyura.cf.model.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;

/**
 * Source peering: the AVAILABLE/ON_HOLD/UNAVAILABLE peer link, the one-file-per-node registry and an in-JVM network of
 * bank nodes standing in for HTTP. A frozen node fails every probe and call, as a SIGSTOPped process times out.
 */
public final class FinancePeering {
    private FinancePeering() { }
    public enum PeerState { AVAILABLE, ON_HOLD, UNAVAILABLE }
    /** One peer reply: its status and the JSON it carried, or {@link Text} when the body was not JSON. */
    public record Reply(int status, Object data) { }
    /** A non-JSON response body, such as the server's plain "Internal Server Error". */
    public record Text(String value) { }
    /** The transport failed (the source's httpx.HTTPError): the node is unknown or frozen. */
    public static final class Unreachable extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public Unreachable(String message) { super(message); }
    }
    /** An injected caller replaces the transport, as the source's caller hook does; whatever it throws propagates unchanged. */
    @FunctionalInterface public interface Caller { Reply call(String method, String path, byte[] body); }
    /** The node side of the in-JVM network. */
    @FunctionalInterface public interface Endpoint { FinanceGateway.Response dispatch(FinanceGateway.Request request); }

    /** Base URLs to in-JVM nodes. Bounded: 16 nodes. */
    public static final class Network {
        private final Map<String, Endpoint> nodes = new LinkedHashMap<>();
        private final Set<String> frozen = new HashSet<>();
        public synchronized void attach(String baseUrl, Endpoint endpoint) {
            if (nodes.size() >= 16 && !nodes.containsKey(baseUrl)) throw new IllegalArgumentException("in-JVM network admits 16 nodes");
            nodes.put(text(baseUrl), Objects.requireNonNull(endpoint));
        }
        public synchronized void detach(String baseUrl) { nodes.remove(baseUrl); frozen.remove(baseUrl); }
        /** SIGSTOP: the node keeps its state but answers nothing. */
        public synchronized void freeze(String baseUrl) { frozen.add(baseUrl); }
        /** SIGCONT. */
        public synchronized void resume(String baseUrl) { frozen.remove(baseUrl); }
        public FinanceGateway.Response exchange(String method, String baseUrl, String path, byte[] body, Map<String, String> headers, Map<String, String> query) {
            Endpoint endpoint;
            synchronized (this) {
                endpoint = nodes.get(baseUrl);
                if (endpoint == null) throw new Unreachable("connection refused: " + baseUrl);
                if (frozen.contains(baseUrl)) throw new Unreachable("timed out: " + baseUrl);
            }
            return endpoint.dispatch(new FinanceGateway.Request(method, path, body, headers, query));
        }
    }

    /** Source PeerLink: a reachability state machine over pings, with explicit timing overrides, the live time config or the defaults. */
    public static final class PeerLink {
        private static final Map<String, Integer> DEFAULTS = Map.of("peer_ping_interval_seconds", 5, "peer_reconnect_delay_seconds", 2, "peer_unavailable_after_seconds", 30);
        private final String alias, bankCode, baseUrl, secret;
        private final Integer pingInterval, reconnectDelay, unavailableAfter;
        private final FinanceTime time;
        private final BooleanSupplier health;
        private final Caller caller;
        private final Network network;
        private final DoubleSupplier seconds;
        private final Supplier<String> nonces;
        private PeerState state = PeerState.ON_HOLD;
        private Double firstFailure;
        private double lastPing;

        private PeerLink(Builder b) {
            alias = text(b.alias); bankCode = text(b.bankCode); secret = text(b.secret);
            String url = text(b.baseUrl); int end = url.length(); while (end > 0 && url.charAt(end - 1) == '/') end--; baseUrl = url.substring(0, end);
            pingInterval = b.pingInterval; reconnectDelay = b.reconnectDelay; unavailableAfter = b.unavailableAfter; time = b.time;
            health = b.health; caller = b.caller; network = b.network; seconds = Objects.requireNonNull(b.seconds); nonces = Objects.requireNonNull(b.nonces);
        }
        public static Builder builder(String alias, String bankCode, String baseUrl, String secret) { return new Builder(alias, bankCode, baseUrl, secret); }
        public static final class Builder {
            private final String alias, bankCode, baseUrl, secret;
            private Integer pingInterval, reconnectDelay, unavailableAfter;
            private FinanceTime time; private BooleanSupplier health; private Caller caller; private Network network;
            private DoubleSupplier seconds = () -> System.currentTimeMillis() / 1000.0;
            private Supplier<String> nonces = () -> UUID.randomUUID().toString().replace("-", "");
            private Builder(String alias, String bankCode, String baseUrl, String secret) { this.alias = alias; this.bankCode = bankCode; this.baseUrl = baseUrl; this.secret = secret; }
            public Builder pingInterval(int s) { pingInterval = s; return this; }
            public Builder reconnectDelay(int s) { reconnectDelay = s; return this; }
            public Builder unavailableAfter(int s) { unavailableAfter = s; return this; }
            public Builder time(FinanceTime t) { time = t; return this; }
            public Builder health(BooleanSupplier h) { health = h; return this; }
            public Builder caller(Caller c) { caller = c; return this; }
            public Builder network(Network n) { network = n; return this; }
            public Builder seconds(DoubleSupplier s) { seconds = s; return this; }
            public Builder nonces(Supplier<String> n) { nonces = n; return this; }
            public PeerLink build() { return new PeerLink(this); }
        }
        public String alias() { return alias; }
        public String bankCode() { return bankCode; }
        public String baseUrl() { return baseUrl; }
        public PeerState state() { return state; }
        /** Sets the state directly, as the source tests do. */
        public void state(PeerState value) { state = Objects.requireNonNull(value); }
        /** Marks the last ping time, as the source tests do. */
        public void lastPing(double value) { lastPing = value; }
        private int timing(Integer explicit, String field) { return explicit != null ? explicit : time != null ? time.seconds(field) : DEFAULTS.get(field); }
        public int pingInterval() { return timing(pingInterval, "peer_ping_interval_seconds"); }
        public int reconnectDelay() { return timing(reconnectDelay, "peer_reconnect_delay_seconds"); }
        public int unavailableAfter() { return timing(unavailableAfter, "peer_unavailable_after_seconds"); }
        private double now() { double n = seconds.getAsDouble(); if (!Double.isFinite(n) || Math.abs(n) > 1e12) throw new IllegalArgumentException("finite bounded seconds required"); return n; }
        private boolean probe() {
            if (health != null) return health.getAsBoolean();
            if (network == null) throw new IllegalStateException("no health check and no in-JVM network: HTTP is outside this port");
            try { return network.exchange("GET", baseUrl, "/health", new byte[0], Map.of(), Map.of()).status() == 200; }
            catch (Unreachable e) { return false; }
        }
        public void recordOk() { state = PeerState.AVAILABLE; firstFailure = null; }
        public void recordFailure(double now) {
            if (firstFailure == null) firstFailure = now;
            state = now - firstFailure >= unavailableAfter() ? PeerState.UNAVAILABLE : PeerState.ON_HOLD;
        }
        public PeerState ping() {
            lastPing = now();
            if (probe()) recordOk(); else recordFailure(now());
            return state;
        }
        /** The state, re-pinging first when the last probe is at least the reconnect delay old. */
        public PeerState stateNow() {
            if (now() - lastPing >= reconnectDelay()) ping();
            return state;
        }
        public void ensureAvailable() {
            var current = stateNow();
            if (current == PeerState.AVAILABLE) return;
            throw new FinanceClearing.PeerUnavailable("peer bank " + bankCode + " is " + current.name(), current == PeerState.ON_HOLD, bankCode);
        }
        /** Source call: a 2xx reply records success; another status raises its typed error and leaves the state alone. */
        public Map<String, Object> call(String method, String path, Map<String, ?> payload) {
            byte[] body = dumps(payload).getBytes(StandardCharsets.UTF_8);
            Reply reply;
            if (caller != null) reply = caller.call(method, path, body);
            else if (network == null) throw new IllegalStateException("no caller and no in-JVM network: HTTP is outside this port");
            else {
                String ts = Long.toString((long) now()), nonce = text(nonces.get());
                var headers = new LinkedHashMap<String, String>();
                headers.put("x-timestamp", ts); headers.put("x-nonce", nonce);
                headers.put("x-signature", FinanceSecurity.sign(secret, method, path, body, ts, nonce)); headers.put("content-type", "application/json");
                try { reply = reply(network.exchange(method, baseUrl, path, body, headers, Map.of())); }
                catch (Unreachable e) {
                    recordFailure(now());
                    throw new FinanceClearing.PeerUnavailable("peer bank " + bankCode + " unreachable", state == PeerState.ON_HOLD, bankCode);
                }
            }
            if (reply.status() >= 200 && reply.status() < 300) {
                recordOk();
                if (reply.data() instanceof Map<?, ?> map) { var out = new LinkedHashMap<String, Object>(); map.forEach((k, v) -> out.put((String) k, v)); return out; }
                return fields("ok", true);
            }
            Object data = reply.data();
            String code = data instanceof Map<?, ?> m && m.containsKey("code") ? PythonValues.str(m.get("code")) : "UPSTREAM_ERROR";
            String message = data instanceof Map<?, ?> m ? (m.containsKey("message") ? PythonValues.str(m.get("message")) : PythonValues.str(data))
                    : data instanceof Text t ? t.value() : PythonValues.str(data);
            throw new ServiceError(code, reply.status(), message, Map.of());
        }
    }
    /** An in-JVM response as the source client sees it: JSON data, or the text of a non-JSON body. */
    static Reply reply(FinanceGateway.Response response) { return new Reply(response.status(), response.body()); }

    /** Source HttpBankPort: only the beneficiary leg is used in the sender-initiated model. */
    public record PeerBankPort(PeerLink link) implements FinanceSettlement.BankPort {
        private void post(String path, String account, Money money, String reference) {
            link.ensureAvailable();
            link.call("POST", path, fields("account_id", account, "amount", money.asString(), "currency", money.currency(), "reference", reference));
        }
        @Override public void inbound(String account, Money money, String reference) { post("/interbank/inbound", account, money, reference); }
        @Override public void hold(String account, Money money, String reference) { post("/interbank/hold", account, money, reference); }
        @Override public void release(String account, Money money, String reference) { post("/interbank/release", account, money, reference); }
        @Override public void outbound(String account, Money money, String reference) { post("/interbank/outbound", account, money, reference); }
    }

    /** Source shared registry: one {alias}.json per node in one directory, read in sorted order. Bounded: 64 files of 4 KiB. */
    public static final class Registry {
        private final Path directory;
        private final DoubleSupplier seconds;
        private final long pid;
        public Registry(Path directory, DoubleSupplier seconds, long pid) { this.directory = Objects.requireNonNull(directory); this.seconds = Objects.requireNonNull(seconds); this.pid = pid; }
        public Path directory() { return directory; }
        public void register(String alias, String bankCode, String baseUrl) {
            try {
                Files.createDirectories(directory);
                var entry = fields("alias", text(alias), "bank_code", text(bankCode), "base_url", text(baseUrl), "pid", pid, "ts", seconds.getAsDouble());
                Path path = directory.resolve(alias + ".json"), tmp = directory.resolve(alias + ".json." + pid + ".tmp");
                Files.writeString(tmp, dumps(entry), StandardCharsets.UTF_8);
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        /** Skips undecodable JSON and entries without an alias; a non-object entry raises, as the source's TypeError does. */
        public Map<String, Map<String, Object>> read() {
            var nodes = new LinkedHashMap<String, Map<String, Object>>();
            List<Path> files;
            try (var stream = Files.list(directory)) { files = stream.sorted().toList(); }
            catch (NoSuchFileException e) { return nodes; }
            catch (IOException e) { throw new UncheckedIOException(e); }
            if (files.size() > 64) throw new IllegalArgumentException("registry admits 64 files");
            for (Path file : files) {
                if (!file.getFileName().toString().endsWith(".json")) continue;
                String raw;
                try {
                    if (Files.size(file) > 4096) throw new IllegalArgumentException("registry entry exceeds 4KiB");
                    raw = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
                } catch (NoSuchFileException e) { continue; }
                catch (CharacterCodingException e) { throw new IllegalArgumentException("registry entry is not UTF-8", e); }
                catch (IOException e) { throw new UncheckedIOException(e); }
                Object entry;
                try { entry = FinanceGateway.sourceJson(raw); } catch (FinanceGateway.InvalidJson e) { continue; }
                if (!(entry instanceof Map<?, ?> map)) throw new IllegalArgumentException("registry entry is not an object (source TypeError)");
                if (!map.containsKey("alias")) continue;
                if (!(map.get("alias") instanceof String alias)) throw new IllegalArgumentException("registry alias must be a string");
                var copy = new LinkedHashMap<String, Object>(); map.forEach((k, v) -> copy.put((String) k, v)); nodes.put(alias, copy);
            }
            return nodes;
        }
        public void deregister(String alias) {
            try { Files.deleteIfExists(directory.resolve(alias + ".json")); } catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }

    /** Source json.dumps defaults: ", " and ": " separators, insertion order, ASCII escapes, NaN/Infinity allowed. */
    public static String dumps(Object value) {
        var out = new StringBuilder(); dumps(out, value, 0); return out.toString();
    }
    private static void dumps(StringBuilder out, Object value, int depth) {
        if (depth > 64 || out.length() > 65536) throw new IllegalArgumentException("peer JSON bound exceeded");
        if (value == null) out.append("null");
        else if (value instanceof Boolean b) out.append(b ? "true" : "false");
        else if (value instanceof Double d) out.append(Double.isNaN(d) ? "NaN" : Double.isInfinite(d) ? (d > 0 ? "Infinity" : "-Infinity") : PythonValues.str(d));
        else if (value instanceof Long || value instanceof Integer) out.append(value);
        else if (value instanceof String s) {
            out.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t"); case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f");
                    default -> { if (c < 0x20 || c > 0x7e) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c)); else out.append(c); }
                }
            }
            out.append('"');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) { if (i > 0) out.append(", "); dumps(out, list.get(i), depth + 1); }
            out.append(']');
        } else if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            for (var e : map.entrySet()) {
                if (!(e.getKey() instanceof String k)) throw new IllegalArgumentException("JSON string keys required");
                if (!first) out.append(", "); dumps(out, k, depth + 1); out.append(": "); dumps(out, e.getValue(), depth + 1); first = false;
            }
            out.append('}');
        } else throw new IllegalArgumentException("JSON value required");
    }
}
