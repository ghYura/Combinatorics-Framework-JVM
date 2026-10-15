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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Small Python value boundary shared by these ports; Unicode digit tables come from the pinned oracle runtime. */
public final class PythonValues {
    private PythonValues() { }
    public static final class PythonError extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String type;
        public PythonError(String type, String message) { super(message); this.type = type; }
        public String type() { return type; }
    }
    public static Map<String, Object> resource(String name) {
        try (InputStream in = PythonValues.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("Missing fixture data " + name);
            return object(freeze(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), name)));
        } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static Object freeze(Object value) {
        if (value instanceof List<?> items) return items.stream().map(PythonValues::freeze).toList();
        if (value instanceof Map<?, ?> entries) {
            Map<String, Object> result = new LinkedHashMap<>();
            entries.forEach((k, v) -> result.put((String) k, freeze(v))); return Collections.unmodifiableMap(result);
        }
        return value;
    }
    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> entries)) throw new IllegalArgumentException("Expected object");
        Map<String, Object> result = new LinkedHashMap<>();
        entries.forEach((k, v) -> result.put((String) k, v)); return Collections.unmodifiableMap(result);
    }
    private static final Map<String, Object> CHARACTERS = resource("python-characters.json");
    public static boolean isDigit(int cp) {
        for (Object value : (List<?>) CHARACTERS.get("digits")) {
            List<?> range = (List<?>) value;
            if (cp >= ((Number) range.get(0)).intValue() && cp <= ((Number) range.get(1)).intValue()) return true;
        }
        return false;
    }
    private static int decimal(int cp) {
        for (Object value : (List<?>) CHARACTERS.get("decimalZero")) {
            int zero = ((Number) value).intValue(); if (cp >= zero && cp < zero + 10) return cp - zero;
        }
        return -1;
    }
    public static boolean truth(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s) return !s.isEmpty();
        if (v instanceof List<?> l) return !l.isEmpty();
        if (v instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }
    public static String str(Object v) {
        if (v == null) return "None";
        if (v instanceof Boolean b) return b ? "True" : "False";
        if (v instanceof List<?> l) return l.stream().map(PythonValues::repr).collect(Collectors.joining(", ", "[", "]"));
        if (v instanceof Map<?, ?> m) return m.entrySet().stream().map(e -> repr(e.getKey()) + ": " + repr(e.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
        if (v instanceof Double || v instanceof Float || v instanceof BigDecimal) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d)) return "nan";
            if (Double.isInfinite(d)) return d < 0 ? "-inf" : "inf";
            if (d == 0) return Double.doubleToRawLongBits(d) < 0 ? "-0.0" : "0.0";
            BigDecimal n = BigDecimal.valueOf(d).stripTrailingZeros();
            int exp = n.precision() - n.scale() - 1;
            if (exp < -4 || exp >= 16) return n.movePointLeft(exp).toPlainString() + "e" + (exp < 0 ? "-" : "+")
                    + (Math.abs(exp) < 10 ? "0" : "") + Math.abs(exp);
            return n.toPlainString() + (n.scale() <= 0 ? ".0" : "");
        }
        return v.toString();
    }
    public static String repr(Object v) {
        if (!(v instanceof String s)) return str(v);
        char quote = s.indexOf('\'') >= 0 && s.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder b = new StringBuilder().append(quote);
        s.codePoints().forEach(cp -> {
            if (cp == quote || cp == '\\') b.append('\\').appendCodePoint(cp);
            else if (cp == '\n') b.append("\\n"); else if (cp == '\r') b.append("\\r"); else if (cp == '\t') b.append("\\t");
            else if (cp != 32 && (Character.isISOControl(cp) || Character.isSpaceChar(cp)
                    || List.of(Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE, Character.UNASSIGNED)
                    .contains((byte) Character.getType(cp)))) {
                b.append(cp <= 255 ? String.format(java.util.Locale.ROOT, "\\x%02x", cp)
                        : cp <= 65535 ? String.format(java.util.Locale.ROOT, "\\u%04x", cp)
                        : String.format(java.util.Locale.ROOT, "\\U%08x", cp));
            } else b.appendCodePoint(cp);
        });
        return b.append(quote).toString();
    }
    /** int(str(value)), including Unicode decimal digits, sign and single inter-digit underscores. */
    public static BigInteger integer(Object v) {
        String raw = str(v);
        String s = raw.strip();
        int start = 0, end = s.length();
        while (start < end && Character.isSpaceChar(s.codePointAt(start))) start += Character.charCount(s.codePointAt(start));
        while (end > start && Character.isSpaceChar(s.codePointBefore(end))) end -= Character.charCount(s.codePointBefore(end));
        s = s.substring(start, end);
        StringBuilder normalized = new StringBuilder(); boolean previousDigit = false; int count = 0;
        for (int i = 0; i < s.length();) {
            int cp = s.codePointAt(i), at = i; i += Character.charCount(cp);
            if (at == 0 && (cp == '+' || cp == '-')) { normalized.appendCodePoint(cp); continue; }
            int digit = decimal(cp);
            if (digit >= 0) { normalized.append(digit); previousDigit = true; count++; }
            else if (cp == '_' && previousDigit && i < s.length() && decimal(s.codePointAt(i)) >= 0) previousDigit = false;
            else throw invalidInteger(raw);
        }
        if (!previousDigit) throw invalidInteger(raw);
        if (count > 4300) throw new PythonError("ValueError", "Exceeds the limit (4300 digits) for integer string conversion: value has "
                + count + " digits; use sys.set_int_max_str_digits() to increase the limit");
        return new BigInteger(normalized.toString());
    }
    private static PythonError invalidInteger(String raw) {
        return new PythonError("ValueError", "invalid literal for int() with base 10: " + repr(raw));
    }
    public static String json(Object value) { return json(value, false); }
    public static String json(Object value, boolean ascii) {
        if (value instanceof Map<?, ?> m) return m.entrySet().stream().map(e -> json(e.getKey(), ascii) + ": " + json(e.getValue(), ascii))
                .collect(Collectors.joining(", ", "{", "}"));
        if (value instanceof List<?> l) return l.stream().map(v -> json(v, ascii)).collect(Collectors.joining(", ", "[", "]"));
        String text = Json.write(value).stripTrailing();
        if (!ascii) return text;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i); if (c > 127) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c)); else out.append(c);
        }
        return out.toString();
    }
}
