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

package org.ghyura.cf.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict JSON reader and a deterministic writer for imported and exported documents (legacy hand-overs, seeds): objects
 * become ordered maps, arrays lists, integers {@link Long}, other numbers {@link BigDecimal}. Stricter than the legacy
 * readers in two respects, both failing closed: a key repeated in one object and a control character inside a string are
 * refused instead of silently kept. The writer emits maps in their order, two-space indentation and '\n' line ends.
 */
public final class Json {

    private final String text;
    private final String origin;
    private int pos;
    private final boolean binary64;

    private Json(String text, String origin, boolean binary64) {
        this.text = text;
        this.origin = origin;
        this.binary64 = binary64;
    }

    /** @throws FrameworkException INPUT_MALFORMED, located at the line where reading stopped */
    public static Object parse(String text, String origin) {
        return read(text, origin, false);
    }

    /** Reads fractional/exponent literals as finite binary64, preserving floating kind and negative zero. */
    public static Object parseBinary64(String text, String origin) {
        return read(text, origin, true);
    }

    private static Object read(String text, String origin, boolean binary64) {
        Json json = new Json(text, origin, binary64);
        Object value = json.value(0);
        json.skipWhitespace();
        if (json.pos != text.length()) {
            throw json.error("trailing content after the document");
        }
        return value;
    }

    private Object value(int depth) {
        if (depth > 256) {
            throw error("nested deeper than 256 levels");
        }
        skipWhitespace();
        if (pos >= text.length()) {
            throw error("unexpected end of the document");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object(int depth) {
        pos++;
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected a quoted key");
            }
            String key = string();
            skipWhitespace();
            expect(':');
            Object value = value(depth + 1);
            if (map.containsKey(key)) {
                throw error("the key \"" + key + "\" appears twice in one object");
            }
            map.put(key, value);
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("expected ',' or '}'");
            }
        }
    }

    private List<Object> array(int depth) {
        pos++;
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value(depth + 1));
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("expected ',' or ']'");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw error("a control character inside a string");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("unterminated escape");
            }
            char e = text.charAt(pos++);
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length() || !text.substring(pos, pos + 4).matches("[0-9A-Fa-f]{4}")) {
                        throw error("a \\u escape needs four hexadecimal digits");
                    }
                    sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw error("invalid escape \\" + e);
            }
        }
    }

    private Object number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        int digits = pos;
        while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
            pos++;
        }
        if (pos == digits || (text.charAt(digits) == '0' && pos - digits > 1)) {
            throw error("invalid number");
        }
        boolean integral = true;
        if (pos < text.length() && text.charAt(pos) == '.') {
            integral = false;
            pos++;
            int fraction = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (pos == fraction) {
                throw error("invalid number");
            }
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            integral = false;
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            int exponent = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (pos == exponent) {
                throw error("invalid number");
            }
        }
        String token = text.substring(start, pos);
        if (!integral) {
            if (binary64) {
                double value = Double.parseDouble(token);
                if (!Double.isFinite(value)) {
                    throw error("number is outside finite binary64 admission");
                }
                return Double.valueOf(value);
            }
            return new BigDecimal(token);
        }
        BigInteger value = new BigInteger(token);
        if (value.bitLength() > 63) {
            throw error("the integer " + token + " is out of range");
        }
        return Long.valueOf(value.longValue());
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw error("invalid literal");
        }
        pos += word.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= text.length()) {
            throw error("unexpected end of the document");
        }
        return text.charAt(pos++);
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("expected '" + c + "'");
        }
    }

    private FrameworkException error(String why) {
        int line = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, origin + " is not valid JSON: " + why,
                SourceLocation.of(origin, line)));
    }

    /**
     * Writes a value: a Map (string keys, in its order), a List, a String, a finite Number, a Boolean or null.
     *
     * @throws IllegalArgumentException for another type or a nonfinite number
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, "");
        return out.append('\n').toString();
    }

    private static void write(StringBuilder out, Object value, String indent) {
        switch (value) {
            case null -> out.append("null");
            case String s -> quote(out, s);
            case Boolean b -> out.append(b);
            case Double d -> {
                if (!Double.isFinite(d)) {
                    throw new IllegalArgumentException("JSON has no " + d);
                }
                out.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d.doubleValue()) + ".0" : Double.toString(d));
            }
            case Float f -> write(out, f.doubleValue(), indent);
            case Number n -> out.append(n);
            case java.util.Map<?, ?> m -> {
                if (m.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                int k = 0;
                for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                    out.append(indent).append("  ");
                    quote(out, String.valueOf(e.getKey()));
                    out.append(": ");
                    write(out, e.getValue(), indent + "  ");
                    out.append(++k < m.size() ? ",\n" : "\n");
                }
                out.append(indent).append('}');
            }
            case java.util.List<?> l -> {
                if (l.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append("[\n");
                for (int k = 0; k < l.size(); k++) {
                    out.append(indent).append("  ");
                    write(out, l.get(k), indent + "  ");
                    out.append(k + 1 < l.size() ? ",\n" : "\n");
                }
                out.append(indent).append(']');
            }
            default -> throw new IllegalArgumentException("not a JSON value: " + value.getClass().getName());
        }
    }

    private static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
