// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
package org.ghyura.cf.desktop.session;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.ghyura.cf.generation.Cardinality;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.RuleValue;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.ghyura.cf.desktop.session.SieveDocument.object;

/** A private loopback connection between the original Face 2 page and the existing Session API. */
public final class Face2Bridge implements AutoCloseable {
    private final Session session;
    private final Path scenario;
    private Face2Document document;
    private final Map<String, List<String>> values;
    private final Set<String> optional;
    private final int scan;
    private final Function<SieveDocument, CompletableFuture<Void>> accept;
    private final String route = "/face2/" + UUID.randomUUID() + "/";
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    private long revision;
    private HttpServer server;
    private ExecutorService executor;
    private String origin;

    public Face2Bridge(Session session, Path scenario, SieveDocument snapshot, Map<String, List<String>> values,
                       Set<String> optional, int scan, Function<SieveDocument, CompletableFuture<Void>> accept) {
        this.session = session; this.scenario = scenario; this.document = new Face2Document(snapshot);
        this.values = values; this.optional = optional; this.scan = scan; this.accept = accept;
        if (scan < 1 || scan > 1_000_000) throw new IllegalArgumentException("Scan must be between 1 and 1,000,000.");
    }

    /** Call off the event thread. No Python, npm, external server or downloaded assets are needed. */
    public URI start() throws IOException {
        synchronized (lifecycle) {
            if (closed) throw new IllegalStateException("Face 2 is closed.");
            if (server != null) return URI.create(origin + route);
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
            origin = "http://127.0.0.1:" + server.getAddress().getPort();
            executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon(true).name("cf-face2-", 0).factory());
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
            return URI.create(origin + route);
        }
    }

    String route() { return route; }

    /** Kept separate from socket startup so the real HTTP handler can also be verified in restricted environments. */
    synchronized void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String host = exchange.getRequestHeaders().getFirst("Host");
            String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
            if (closed) { send(exchange, 410, "text/plain", "This Face 2 session is closed. Open it again from the JVM."); return; }
            String expectedOrigin = origin == null ? "http://127.0.0.1:" + exchange.getLocalAddress().getPort() : origin;
            if (!path.startsWith(route) || !URI.create(expectedOrigin).getAuthority().equals(host)
                    || requestOrigin != null && !expectedOrigin.equals(requestOrigin)) {
                send(exchange, 403, "text/plain", "This request does not belong to this Face 2 session."); return;
            }
            String action = path.substring(route.length());
            if (action.isEmpty() && exchange.getRequestMethod().equals("GET")) {
                send(exchange, 200, "text/html", document.html(values, optional, "./", revision)); return;
            }
            if (!Set.of("submit", "impact", "export").contains(action)) { send(exchange, 404, "text/plain", "Not found."); return; }
            if (!exchange.getRequestMethod().equals("POST")) { send(exchange, 405, "text/plain", "Use POST."); return; }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                send(exchange, 415, "text/plain", "Expected application/json."); return;
            }
            byte[] bytes = exchange.getRequestBody().readNBytes(SieveDocument.MAX_BYTES + 1);
            if (bytes.length > SieveDocument.MAX_BYTES) { send(exchange, 413, "text/plain", "Face 2 is limited to 16 MiB."); return; }
            try {
                String body = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                if (!RuleValue.Int.of(revision).equals(SieveDocument.map(ConstraintBonds.parse(body, "Face 2 request")).get("revision"))) {
                    send(exchange, 409, "text/plain", "This browser page is out of date. Reload Face 2 before editing again."); return;
                }
                SieveDocument next = document.apply(body);
                switch (action) {
                    case "export" -> send(exchange, 200, "application/json", next.json());
                    case "impact" -> send(exchange, 200, "application/json", impact(next));
                    case "submit" -> {
                        session.validateSieve(scenario, next.json(), () -> closed);
                        if (closed) throw new IllegalStateException("Face 2 was closed; reopen it from the JVM.");
                        accept.apply(next).join(); // Completes only after the workbench's normal validated save.
                        document = new Face2Document(next); revision++;
                        send(exchange, 200, "application/json", "{\"ok\":true,\"rules\":" + next.rules().size() + "}");
                    }
                    default -> throw new IllegalStateException(action);
                }
            } catch (java.nio.charset.CharacterCodingException failure) {
                send(exchange, 422, "text/plain", "Face 2 expects valid UTF-8.");
            } catch (RuntimeException failure) {
                Throwable cause = failure;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                send(exchange, cause instanceof IllegalStateException ? 409 : 422, "text/plain", String.valueOf(cause.getMessage()));
            }
        }
    }

    private String impact(SieveDocument next) {
        var preview = session.preview(scenario, next.json(), new CandidatePreview.Request(BigInteger.valueOf(scan), BigInteger.ZERO,
                1, false, true), () -> closed);
        var count = preview.estimates().candidates().stream().filter(s -> s.name().equals("executable"))
                .findFirst().orElseThrow().count();
        if (count.mode() == Cardinality.Mode.EXACT) return ConstraintBonds.pretty(object("exact", new RuleValue.Bool(true),
                "total", new RuleValue.Text(preview.generated().toString()), "kept", new RuleValue.Text(count.value().toString())));
        return ConstraintBonds.pretty(object("exact", new RuleValue.Bool(false), "note", new RuleValue.Text(
                "Framework count is bounded or unknown at scan limit " + scan + ". Use Check impact in the JVM for details.")));
    }

    private static void send(HttpExchange exchange, int status, String type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type + "; charset=utf-8"); headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff"); headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
    }

    @Override public void close() {
        // Never take the request lock: a handler may be awaiting the event thread that closes us.
        synchronized (lifecycle) {
            closed = true;
            if (server != null) { server.stop(0); server = null; }
            if (executor != null) { executor.shutdownNow(); executor = null; }
        }
    }
}
