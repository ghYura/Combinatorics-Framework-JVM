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

package org.ghyura.cf.input;

import org.ghyura.cf.model.rule.RuleValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The few Python text rules the fwgen spec compiler depends on, so that a compiled cell, a message or a sort order is
 * the one the legacy code produced: {@code str()}, {@code repr()}, {@code strip()}, the first of {@code splitlines()},
 * {@code int()}, truthiness, float repr and code point ordering.
 */
final class PyText {

    /** Python's {@code sorted()} order of strings: by code point, not by UTF-16 unit. */
    static final Comparator<String> CODE_POINT_ORDER = PyText::compareCodePoints;

    private PyText() {
    }

    /** A value that has no Python {@code str()} the legacy compiler could have produced from TOML or JSON. */
    static final class NotRenderable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotRenderable(String message) {
            super(message);
        }
    }

    /** Python {@code str(value)}. */
    static String str(DocTree n) {
        return switch (n) {
            case DocTree.Leaf l -> str(l.value());
            case DocTree.Arr a -> a.items().stream().map(PyText::repr).collect(Collectors.joining(", ", "[", "]"));
            case DocTree.Obj o -> o.fields().entrySet().stream().map(e -> repr(e.getKey()) + ": " + repr(e.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        };
    }

    /** Python {@code repr(value)}; a date or time inside a container has a repr the port does not reproduce. */
    static String repr(DocTree n) {
        if (n instanceof DocTree.Leaf l) {
            if (l.temporal() != DocTree.Temporal.NONE) {
                throw new NotRenderable("a TOML date or time inside a list or table is written by the legacy compiler as its "
                        + "Python repr, which is not reproduced; write it as a string");
            }
            return l.value() instanceof RuleValue.Text t ? repr(t.value()) : str(l.value());
        }
        return str(n);
    }

    /** Python {@code str()} of a plain value. */
    static String str(RuleValue v) {
        return switch (v) {
            case RuleValue.Text t -> t.value();
            case RuleValue.Int i -> i.value().toString();
            case RuleValue.Real r -> floatRepr(r.value());
            case RuleValue.Bool b -> b.value() ? "True" : "False";
            case RuleValue.ListValue l -> l.items().stream().map(PyText::repr).collect(Collectors.joining(", ", "[", "]"));
            case RuleValue.MapValue m -> m.entries().entrySet().stream().map(e -> repr(e.getKey()) + ": " + repr(e.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
            default -> "None";
        };
    }

    private static String repr(RuleValue v) {
        return v instanceof RuleValue.Text t ? repr(t.value()) : str(v);
    }

    /** Python's {@code repr(float)}: the shortest round-trip digits, exponent form below 1e-4 and from 1e16. */
    static String floatRepr(double d) {
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (Double.isInfinite(d)) {
            return d < 0 ? "-inf" : "inf";
        }
        if (d == 0) {
            return Double.doubleToRawLongBits(d) < 0 ? "-0.0" : "0.0";
        }
        BigDecimal n = new BigDecimal(Double.toString(d)).stripTrailingZeros();
        int exp = n.precision() - n.scale() - 1;
        if (exp < -4 || exp >= 16) {
            String digits = n.unscaledValue().abs().toString();
            String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
            return (n.signum() < 0 ? "-" : "") + mantissa + "e" + (exp < 0 ? "-" : "+") + (Math.abs(exp) < 10 ? "0" : "") + Math.abs(exp);
        }
        String plain = n.toPlainString();
        return plain.indexOf('.') >= 0 ? plain : plain + ".0";
    }

    /** Python's {@code repr(str)}: single quotes unless the text has one and no double quote. */
    static String repr(String s) {
        char quote = s.indexOf('\'') >= 0 && s.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder b = new StringBuilder(s.length() + 2).append(quote);
        s.codePoints().forEach(cp -> {
            if (cp == quote || cp == '\\') {
                b.append('\\').appendCodePoint(cp);
            } else if (cp == '\n') {
                b.append("\\n");
            } else if (cp == '\r') {
                b.append("\\r");
            } else if (cp == '\t') {
                b.append("\\t");
            } else if (!printable(cp)) {
                b.append(cp <= 0xFF ? String.format(Locale.ROOT, "\\x%02x", cp)
                        : cp <= 0xFFFF ? String.format(Locale.ROOT, "\\u%04x", cp) : String.format(Locale.ROOT, "\\U%08x", cp));
            } else {
                b.appendCodePoint(cp);
            }
        });
        return b.append(quote).toString();
    }

    /** Python {@code str(list_of_strings)}. */
    static String reprList(List<String> items) {
        return items.stream().map(PyText::repr).collect(Collectors.joining(", ", "[", "]"));
    }

    private static boolean printable(int cp) {
        if (cp == ' ') {
            return true;
        }
        int type = Character.getType(cp);
        return !(type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE || type == Character.PRIVATE_USE
                || type == Character.UNASSIGNED || type == Character.SPACE_SEPARATOR || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR);
    }

    /** Python's {@code str.isspace()} for one code point. */
    static boolean isSpace(int cp) {
        return (cp >= 0x09 && cp <= 0x0D) || (cp >= 0x1C && cp <= 0x20) || cp == 0x85 || cp == 0xA0 || cp == 0x1680
                || (cp >= 0x2000 && cp <= 0x200A) || cp == 0x2028 || cp == 0x2029 || cp == 0x202F || cp == 0x205F || cp == 0x3000;
    }

    /** Python's {@code str.strip()}. */
    static String strip(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isSpace(s.codePointAt(start))) {
            start += Character.charCount(s.codePointAt(start));
        }
        while (end > start && isSpace(s.codePointBefore(end))) {
            end -= Character.charCount(s.codePointBefore(end));
        }
        return s.substring(start, end);
    }

    /** Python's {@code s.rstrip(" \t")}. */
    static String rstripSpaceTab(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t')) {
            end--;
        }
        return s.substring(0, end);
    }

    /** fwgen's {@code _first_line}: the stripped first of Python's {@code splitlines()} of the stripped text. */
    static String firstLine(String text) {
        String s = strip(text);
        if (s.isEmpty()) {
            return "";
        }
        int k = 0;
        while (k < s.length() && !lineBoundary(s.charAt(k))) {
            k++;
        }
        return strip(s.substring(0, k));
    }

    private static boolean lineBoundary(char c) {
        return c == '\n' || c == '\r' || c == 0x0B || c == 0x0C || c == 0x1C || c == 0x1D || c == 0x1E || c == 0x85
                || c == 0x2028 || c == 0x2029;
    }

    /** Python truthiness of a document value. */
    static boolean truthy(DocTree n) {
        return switch (n) {
            case DocTree.Leaf l -> switch (l.value()) {
                case RuleValue.Text t -> !t.value().isEmpty() || l.temporal() != DocTree.Temporal.NONE;
                case RuleValue.Int i -> i.value().signum() != 0;
                case RuleValue.Real r -> r.value() != 0.0;
                case RuleValue.Bool b -> b.value();
                default -> false;
            };
            case DocTree.Arr a -> !a.items().isEmpty();
            case DocTree.Obj o -> !o.fields().isEmpty();
        };
    }

    /** A failed Python {@code int()}: its exception type and message. */
    static final class IntError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String type;

        IntError(String type, String message) {
            super(message);
            this.type = type;
        }

        String type() {
            return type;
        }
    }

    /** Python {@code int(value)} of a TOML or JSON value. */
    static BigInteger integer(DocTree n) {
        if (n instanceof DocTree.Leaf l && l.temporal() == DocTree.Temporal.NONE) {
            switch (l.value()) {
                case RuleValue.Int i -> {
                    return i.value();
                }
                case RuleValue.Bool b -> {
                    return b.value() ? BigInteger.ONE : BigInteger.ZERO;
                }
                case RuleValue.Real r -> {
                    if (Double.isNaN(r.value())) {
                        throw new IntError("ValueError", "cannot convert float NaN to integer");
                    }
                    if (Double.isInfinite(r.value())) {
                        throw new IntError("OverflowError", "cannot convert float infinity to integer");
                    }
                    return new BigDecimal(r.value()).toBigInteger();
                }
                case RuleValue.Text t -> {
                    return parseInt(t.value());
                }
                default -> {
                }
            }
        }
        String type = switch (n) {
            case DocTree.Arr a -> "list";
            case DocTree.Obj o -> "dict";
            case DocTree.Leaf l when l.temporal() == DocTree.Temporal.DATE -> "datetime.date";
            case DocTree.Leaf l when l.temporal() == DocTree.Temporal.TIME -> "datetime.time";
            case DocTree.Leaf l when l.temporal() == DocTree.Temporal.DATE_TIME -> "datetime.datetime";
            default -> "NoneType";
        };
        throw new IntError("TypeError", "int() argument must be a string, a bytes-like object or a real number, not '" + type + "'");
    }

    /** Python {@code int(text)}: surrounding whitespace, a sign, Unicode decimal digits and single inner underscores. */
    static BigInteger parseInt(String raw) {
        String s = strip(raw);
        StringBuilder normalized = new StringBuilder();
        boolean previousDigit = false;
        int digits = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int at = i;
            i += Character.charCount(cp);
            if (at == 0 && (cp == '+' || cp == '-')) {
                normalized.appendCodePoint(cp);
                continue;
            }
            int digit = Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER ? Character.digit(cp, 10) : -1;
            if (digit >= 0) {
                normalized.append(digit);
                previousDigit = true;
                digits++;
            } else if (cp == '_' && previousDigit && i < s.length() && Character.getType(s.codePointAt(i)) == Character.DECIMAL_DIGIT_NUMBER) {
                previousDigit = false;
            } else {
                throw new IntError("ValueError", "invalid literal for int() with base 10: " + repr(raw));
            }
        }
        if (digits == 0) {
            throw new IntError("ValueError", "invalid literal for int() with base 10: " + repr(raw));
        }
        return new BigInteger(normalized.toString());
    }

    /** Python's {@code sorted()} order of two strings. */
    static int compareCodePoints(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(j);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** Python {@code str(dict)} of string keys and string lists, for messages. */
    static String reprMap(Map<String, ?> map) {
        return map.entrySet().stream().map(e -> repr(e.getKey()) + ": " + (e.getValue() instanceof String s ? repr(s) : String.valueOf(e.getValue())))
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
