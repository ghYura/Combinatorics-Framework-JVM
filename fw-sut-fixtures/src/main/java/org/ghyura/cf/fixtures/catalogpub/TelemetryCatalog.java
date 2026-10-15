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

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.ghyura.cf.fixtures.business.FixtureValues.map;
import static org.ghyura.cf.fixtures.catalogpub.PythonValues.*;

/** Original catalog's pure dispatcher. No server, transport or database is created. */
public final class TelemetryCatalog {
    private TelemetryCatalog() { }
    public record Request(String method, String path, Map<String, ?> body) {
        public Request { if (body != null) body = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(body)); }
    }
    public record Response(int status, String contentType, String body) { }
    private static final Pattern ROUTE = Pattern.compile("^/api/catalog/lookup/([^/?]+)/?$");
    private static final String JSON = "application/json; charset=utf-8";
    private static Response jsonResponse(int status, Object value) { return new Response(status, JSON, json(value)); }
    public static Response handle(Request request) {
        String path = request.path().replace("\r", "").replace("\n", "").replace("\t", "");
        int leading = 0; while (leading < path.length() && path.charAt(leading) <= 32) leading++; path = path.substring(leading);
        path = path.replaceFirst("^[A-Za-z][A-Za-z0-9+.-]*:", "");
        if (path.startsWith("//")) {
            int end = 2; while (end < path.length() && "/?#".indexOf(path.charAt(end)) < 0) end++;
            validateAuthority(path.substring(2, end)); path = path.substring(end);
        }
        int fragment = path.indexOf('#'); if (fragment >= 0) path = path.substring(0, fragment);
        int queryAt = path.indexOf('?'); String query = queryAt < 0 ? "" : path.substring(queryAt + 1);
        if (queryAt >= 0) path = path.substring(0, queryAt);
        if (path.equals("/health")) return new Response(200, "text/plain; charset=utf-8", "ok");
        if (path.equals("/whoami")) return jsonResponse(200, map("service", "telemetry_catalog", "params", TelemetryRules.QUERY_PARAMS,
                "families", TelemetryRules.FAMILIES, "rules", TelemetryRules.RULES));
        Matcher match = ROUTE.matcher(path);
        if (!match.matches()) return new Response(404, JSON, json(map("error", "not found", "path", path), true));
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('='); if (eq < 0 || eq == pair.length() - 1) continue;
            String key = unquote(pair.substring(0, eq).replace('+', ' '));
            String value = unquote(pair.substring(eq + 1).replace('+', ' '));
            if (!parsed.containsKey(key)) parsed.put(key, List.of(value));
        }
        if (request.body() != null && !request.body().isEmpty()) parsed.putAll(request.body());
        Map<String, Object> params = TelemetryRules.parseQuery(unquote(match.group(1)), parsed);
        List<String> broken = TelemetryRules.validate(params);
        if (!broken.isEmpty()) {
            Map<String, Object> detail = new LinkedHashMap<>(); broken.forEach(rule -> detail.put(rule, TelemetryRules.RULES.get(rule)));
            return jsonResponse(400, map("error", "invalid query", "valid", false, "broken", broken, "detail", detail));
        }
        List<Map<String, Object>> records = records(params);
        if ("markup".equals(params.get("responseFormat"))) {
            StringBuilder body = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><catalog locator=\"")
                    .append(escape(str(params.getOrDefault("locator", "")))).append("\" count=\"").append(records.size()).append("\">");
            for (Map<String, Object> record : records) {
                body.append("<dataset id=\"").append(record.get("id")).append("\">");
                for (String field : List.of("label", "family", "signal", "accessTier", "quality")) body.append('<').append(field).append('>')
                        .append(escape(str(record.get(field)))).append("</").append(field).append('>');
                body.append("</dataset>");
            }
            return new Response(200, "application/xml; charset=utf-8", body.append("</catalog>").toString());
        }
        return jsonResponse(200, map("locator", params.get("locator"), "count", records.size(), "valid", true, "records", records));
    }
    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static List<Map<String, Object>> records(Map<String, ?> p) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> signals = TelemetryRules.asList(p.get("SignalClassList")), access = TelemetryRules.asList(p.get("AccessTier"));
        for (Object value : (List<?>) TelemetryRules.DATA.get("records")) {
            Map<String, Object> r = object(value);
            if (truth(p.get("DatasetFamily")) && !r.get("family").equals(p.get("DatasetFamily"))) continue;
            if (!signals.isEmpty() && !signals.contains(r.get("signal"))) continue;
            if (!access.isEmpty() && !access.contains(r.get("accessTier"))) continue;
            if (truth(p.get("AssetKind")) && !r.get("assetKind").equals(p.get("AssetKind"))) continue;
            result.add(r);
        }
        int start = bound(TelemetryRules.nullableInt(p.get("offsetStart")), result.size(), 0);
        int end = bound(TelemetryRules.nullableInt(p.get("offsetEnd")), result.size(), result.size());
        return List.copyOf(result.subList(start, Math.max(start, end)));
    }
    private static int bound(BigInteger value, int size, int fallback) {
        if (value == null) return fallback;
        if (value.signum() < 0) value = value.add(BigInteger.valueOf(size));
        return value.max(BigInteger.ZERO).min(BigInteger.valueOf(size)).intValueExact();
    }
    public static String unquote(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length();) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (i + 2 < value.length() && value.charAt(i) == '%' && hex(value.charAt(i + 1)) >= 0 && hex(value.charAt(i + 2)) >= 0) {
                bytes.write(hex(value.charAt(i + 1)) * 16 + hex(value.charAt(i + 2))); i += 3;
            }
            if (bytes.size() > 0) result.append(decodeUtf8(bytes.toByteArray())); else result.append(value.charAt(i++));
        }
        return result.toString();
    }
    private static int hex(char c) { return c <= 127 ? Character.digit(c, 16) : -1; }
    /** Python replaces each invalid leading byte, and consumes valid prefixes of truncated sequences once. */
    private static String decodeUtf8(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < bytes.length;) {
            int first = bytes[i] & 255;
            if (first < 128) { result.append((char) first); i++; continue; }
            int length = first >= 194 && first <= 223 ? 2 : first >= 224 && first <= 239 ? 3 : first >= 240 && first <= 244 ? 4 : 0;
            if (length == 0) { result.append('\ufffd'); i++; continue; }
            int code = first & (127 >>> length), consumed = 1;
            while (consumed < length && i + consumed < bytes.length) {
                int next = bytes[i + consumed] & 255;
                if (next < 128 || next > 191 || consumed == 1 && (first == 224 && next < 160 || first == 237 && next >= 160
                        || first == 240 && next < 144 || first == 244 && next >= 144)) break;
                code = (code << 6) | (next & 63); consumed++;
            }
            if (consumed == length) result.appendCodePoint(code); else result.append('\ufffd'); i += consumed;
        }
        return result.toString();
    }
    private static void validateAuthority(String authority) {
        if (authority.contains("[") || authority.contains("]")) {
            String hostPort = authority.substring(authority.lastIndexOf('@') + 1);
            int close = hostPort.indexOf(']');
            if (!hostPort.startsWith("[") || close < 0 || close + 1 < hostPort.length() && hostPort.charAt(close + 1) != ':')
                throw new PythonError("ValueError", "Invalid IPv6 URL");
            String host = hostPort.substring(1, close);
            if (host.startsWith("v")) {
                if (!host.matches("v[0-9A-Fa-f]+\\..+")) throw new PythonError("ValueError", "IPvFuture address is invalid");
            } else {
                if (host.matches("[0-9]+(?:\\.[0-9]+){3}")) throw new PythonError("ValueError", "An IPv4 address cannot be in brackets");
                try { java.net.URI.create("http://[" + host + "]/"); }
                catch (IllegalArgumentException e) { throw new PythonError("ValueError", repr(host) + " does not appear to be an IPv4 or IPv6 address"); }
            }
        }
        String stripped = authority.replace("@", "").replace(":", "").replace("#", "").replace("?", "");
        String normalized = java.text.Normalizer.normalize(stripped, java.text.Normalizer.Form.NFKC);
        if (!normalized.equals(stripped) && normalized.chars().anyMatch(c -> "/?#@:".indexOf(c) >= 0))
            throw new PythonError("ValueError", "netloc '" + authority + "' contains invalid characters under NFKC normalization");
    }
}
