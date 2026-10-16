// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
package org.ghyura.cf.desktop.session;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Exercises the actual HTTP handler and workbench callback without needing a socket. */
public final class Face2TestClient {
    private Face2TestClient() { }
    public record Response(int status, String text) { }

    public static Response request(Face2Bridge bridge, String action, String body) throws IOException {
        Exchange exchange = exchange(bridge, action, action.isEmpty() ? "GET" : "POST", body);
        bridge.handle(exchange);
        return new Response(exchange.status, exchange.text());
    }

    static Exchange exchange(Face2Bridge bridge, String action, String method, String body) {
        return new Exchange(bridge.route() + action, method, body);
    }

    static final class Exchange extends HttpExchange {
        final Headers headers = new Headers(), response = new Headers();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final String path, method, body;
        int status = -1;
        Exchange(String path, String method, String body) {
            this.path = path; this.method = method; this.body = body;
            headers.set("Host", "127.0.0.1:8123"); headers.set("Content-Type", "application/json");
        }
        String text() { return out.toString(StandardCharsets.UTF_8); }
        @Override public Headers getRequestHeaders() { return headers; }
        @Override public Headers getResponseHeaders() { return response; }
        @Override public URI getRequestURI() { return URI.create(path); }
        @Override public String getRequestMethod() { return method; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
        @Override public OutputStream getResponseBody() { return out; }
        @Override public void sendResponseHeaders(int code, long length) { status = code; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 9000); }
        @Override public int getResponseCode() { return status; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 8123); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream input, OutputStream output) { }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
