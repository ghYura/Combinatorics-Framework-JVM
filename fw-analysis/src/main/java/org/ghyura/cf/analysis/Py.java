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


package org.ghyura.cf.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The few CPython string, number and JSON rules the operator reports were written with, so a ported report prints what
 * the legacy one printed: {@code str.splitlines}, {@code str.isspace}, the {@code \w} of a str pattern, code point order,
 * {@code int()} and {@code float()} acceptance, {@code round}, {@code repr} of a float, {@code %.Nf} and
 * {@code json.dumps(indent=2, sort_keys=True, ensure_ascii=False)}.
 */
final class Py {

    private Py() {
    }

    /** {@code str.isspace()} for one code point. */
    static boolean isSpace(int cp) {
        return (cp >= 0x09 && cp <= 0x0D) || (cp >= 0x1C && cp <= 0x20) || cp == 0x85 || cp == 0xA0 || cp == 0x1680
                || (cp >= 0x2000 && cp <= 0x200A) || cp == 0x2028 || cp == 0x2029 || cp == 0x202F || cp == 0x205F || cp == 0x3000;
    }

    /** {@code not s.strip()}. */
    static boolean isBlank(String s) {
        return s.codePoints().allMatch(Py::isSpace);
    }

    /** {@code \w} of a str pattern: alphanumeric (letters and every numeric category) or the underscore; marks are not. */
    static boolean isWord(int cp) {
        if (cp == '_' || Character.isLetter(cp)) {
            return true;
        }
        int type = Character.getType(cp);
        return type == Character.DECIMAL_DIGIT_NUMBER || type == Character.LETTER_NUMBER || type == Character.OTHER_NUMBER;
    }

    /** {@code str.splitlines()}: every line boundary CPython knows, CR LF as one, no empty line after a final boundary. */
    static List<String> splitLines(String s) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int k = 0;
        while (k < s.length()) {
            char c = s.charAt(k);
            if (c == '\n' || c == '\r' || c == 0x0B || c == 0x0C || c == 0x1C || c == 0x1D || c == 0x1E || c == 0x85 || c == 0x2028
                    || c == 0x2029) {
                lines.add(s.substring(start, k));
                k += c == '\r' && k + 1 < s.length() && s.charAt(k + 1) == '\n' ? 2 : 1;
                start = k;
            } else {
                k++;
            }
        }
        if (start < s.length()) {
            lines.add(s.substring(start));
        }
        return lines;
    }

    /** String order by code point, as CPython sorts str. */
    static int compare(String a, String b) {
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

    /** {@code s[:n]} in code points. */
    static String head(String s, int n) {
        int count = s.codePointCount(0, s.length());
        return count <= n ? s : s.substring(0, s.offsetByCodePoints(0, n));
    }

    /** {@code "%-Ns" % s}: padded on the right to {@code width} code points, never cut. */
    static String left(String s, int width) {
        int pad = width - s.codePointCount(0, s.length());
        return pad > 0 ? s + " ".repeat(pad) : s;
    }

    /** {@code "%Ns" % s}: padded on the left to {@code width} code points. */
    static String right(String s, int width) {
        int pad = width - s.codePointCount(0, s.length());
        return pad > 0 ? " ".repeat(pad) + s : s;
    }

    /** {@code "%.Nf" % x}: the exact binary value rounded half to even, as C's printf does (Java's Formatter rounds half up). */
    static String fixed(double x, int digits) {
        String text = new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).toPlainString();
        return x < 0 || (x == 0 && 1 / x < 0) ? (text.startsWith("-") ? text : "-" + text) : text;
    }

    /** {@code round(x, n)}: the exact value rounded half to even at {@code n} decimals, read back as a double. */
    static double round(double x, int n) {
        return new BigDecimal(x).setScale(n, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** {@code repr(x)} of a finite float: the shortest digits, positional from 1e-4 up to 1e16, otherwise 'e' notation. */
    static String repr(double x) {
        if (x == 0) {
            return 1 / x < 0 ? "-0.0" : "0.0";
        }
        BigDecimal d = shortest(Math.abs(x));
        String digits = d.unscaledValue().toString();
        int point = digits.length() - d.scale();
        String sign = x < 0 ? "-" : "";
        if (point > -4 && point <= 16) {
            if (point <= 0) {
                return sign + "0." + "0".repeat(-point) + digits;
            }
            if (point >= digits.length()) {
                return sign + digits + "0".repeat(point - digits.length()) + ".0";
            }
            return sign + digits.substring(0, point) + "." + digits.substring(point);
        }
        int exponent = point - 1;
        String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return sign + mantissa + "e" + (exponent < 0 ? "-" : "+") + (Math.abs(exponent) < 10 ? "0" : "") + Math.abs(exponent);
    }

    /**
     * The shortest decimal that reads back as {@code x} (positive), the closest such: Java's {@code Double.toString} gives
     * it whenever it has two digits or more, but chooses among one- and two-digit decimals when one digit would do.
     */
    private static BigDecimal shortest(double x) {
        BigDecimal d = new BigDecimal(Double.toString(x)).stripTrailingZeros();
        if (d.precision() != 2) {
            return d;
        }
        BigDecimal exact = new BigDecimal(x);
        BigDecimal best = null;
        for (RoundingMode mode : new RoundingMode[]{RoundingMode.FLOOR, RoundingMode.CEILING}) {
            BigDecimal c = exact.round(new java.math.MathContext(1, mode));
            if (c.signum() > 0 && Double.parseDouble(c.toString()) == x) {
                int cmp = best == null ? -1 : exact.subtract(c).abs().compareTo(exact.subtract(best).abs());
                if (cmp < 0 || (cmp == 0 && c.unscaledValue().testBit(0) == false)) {
                    best = c;
                }
            }
        }
        return best == null ? d : best.stripTrailingZeros();
    }

    /**
     * {@code bytes.decode("utf-8", errors="replace")}: one U+FFFD for each maximal invalid subpart, as CPython replaces (an
     * encoded surrogate is three, where Java's decoder writes one).
     */
    static String decodeUtf8(byte[] b) {
        StringBuilder out = new StringBuilder(b.length);
        int i = 0;
        while (i < b.length) {
            int c = b[i] & 0xFF;
            if (c < 0x80) {
                out.append((char) c);
                i++;
                continue;
            }
            int need;
            int cp;
            int lo = 0x80;
            int hi = 0xBF;
            if (c >= 0xC2 && c <= 0xDF) {
                need = 1;
                cp = c & 0x1F;
            } else if (c >= 0xE0 && c <= 0xEF) {
                need = 2;
                cp = c & 0x0F;
                lo = c == 0xE0 ? 0xA0 : 0x80;
                hi = c == 0xED ? 0x9F : 0xBF;
            } else if (c >= 0xF0 && c <= 0xF4) {
                need = 3;
                cp = c & 0x07;
                lo = c == 0xF0 ? 0x90 : 0x80;
                hi = c == 0xF4 ? 0x8F : 0xBF;
            } else {
                out.append('\uFFFD');
                i++;
                continue;
            }
            int j = i + 1;
            boolean whole = true;
            for (int k = 0; k < need; k++, j++) {
                int d = j < b.length ? b[j] & 0xFF : -1;
                if (d < (k == 0 ? lo : 0x80) || d > (k == 0 ? hi : 0xBF)) {
                    whole = false;
                    break;
                }
                cp = (cp << 6) | (d & 0x3F);
            }
            if (whole) {
                out.appendCodePoint(cp);
            } else {
                out.append('\uFFFD');
            }
            i = j;
        }
        return out.toString();
    }

    /** What {@code int(s)} reads, or null when it raises ValueError: whitespace, a sign, Unicode decimal digits and single underscores between them. */
    static BigDecimal integer(String s) {
        String t = strip(s);
        int k = 0;
        boolean negative = false;
        if (!t.isEmpty() && (t.charAt(0) == '+' || t.charAt(0) == '-')) {
            negative = t.charAt(0) == '-';
            k = 1;
        }
        StringBuilder digits = new StringBuilder();
        boolean underscore = false;
        while (k < t.length()) {
            int cp = t.codePointAt(k);
            k += Character.charCount(cp);
            if (cp == '_') {
                if (digits.isEmpty() || underscore) {
                    return null;
                }
                underscore = true;
                continue;
            }
            if (Character.getType(cp) != Character.DECIMAL_DIGIT_NUMBER) {
                return null;
            }
            digits.append(Character.digit(cp, 10));
            underscore = false;
        }
        if (digits.isEmpty() || underscore) {
            return null;
        }
        BigDecimal value = new BigDecimal(digits.toString());
        return negative ? value.negate() : value;
    }

    /** Whether {@code float(s)} accepts {@code s}: Python's float grammar with Unicode digits and underscores. */
    static boolean isFloat(String s) {
        String t = strip(s);
        int k = 0;
        if (!t.isEmpty() && (t.charAt(0) == '+' || t.charAt(0) == '-')) {
            k = 1;
        }
        String rest = t.substring(k).toLowerCase(java.util.Locale.ROOT);
        if (rest.equals("inf") || rest.equals("infinity") || rest.equals("nan")) {
            return true;
        }
        int[] cps = t.substring(k).codePoints().toArray();
        int[] at = {0};
        boolean whole = digitPart(cps, at);
        boolean fraction = false;
        if (at[0] < cps.length && cps[at[0]] == '.') {
            at[0]++;
            fraction = digitPart(cps, at);
        }
        if (!whole && !fraction) {
            return false;
        }
        if (at[0] < cps.length && (cps[at[0]] == 'e' || cps[at[0]] == 'E')) {
            at[0]++;
            if (at[0] < cps.length && (cps[at[0]] == '+' || cps[at[0]] == '-')) {
                at[0]++;
            }
            if (!digitPart(cps, at)) {
                return false;
            }
        }
        return at[0] == cps.length;
    }

    /** {@code digit (["_"] digit)*} from {@code at[0]}; false (and nothing consumed) when no digit starts there. */
    private static boolean digitPart(int[] cps, int[] at) {
        int k = at[0];
        if (k >= cps.length || Character.getType(cps[k]) != Character.DECIMAL_DIGIT_NUMBER) {
            return false;
        }
        k++;
        while (k < cps.length) {
            if (Character.getType(cps[k]) == Character.DECIMAL_DIGIT_NUMBER) {
                k++;
            } else if (cps[k] == '_' && k + 1 < cps.length && Character.getType(cps[k + 1]) == Character.DECIMAL_DIGIT_NUMBER) {
                k += 2;
            } else {
                break;
            }
        }
        at[0] = k;
        return true;
    }

    /** {@code str.strip()}. */
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

    /**
     * {@code json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False)}: maps (string keys, sorted by code point),
     * collections, strings, booleans, null, integers and doubles (written as {@link #repr}).
     */
    static String json(Object value) {
        StringBuilder out = new StringBuilder();
        json(out, value, "");
        return out.toString();
    }

    private static void json(StringBuilder out, Object value, String indent) {
        switch (value) {
            case null -> out.append("null");
            case String s -> quote(out, s);
            case Boolean b -> out.append(b ? "true" : "false");
            case Double d -> out.append(repr(d));
            case Number n -> out.append(n);
            case Map<?, ?> m -> {
                if (m.isEmpty()) {
                    out.append("{}");
                    return;
                }
                TreeMap<String, Object> sorted = new TreeMap<>(Py::compare);
                m.forEach((k, v) -> sorted.put((String) k, v));
                String inner = indent + "  ";
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> e : sorted.entrySet()) {
                    out.append(first ? "\n" : ",\n").append(inner);
                    quote(out, e.getKey());
                    out.append(": ");
                    json(out, e.getValue(), inner);
                    first = false;
                }
                out.append('\n').append(indent).append('}');
            }
            case Collection<?> c -> {
                if (c.isEmpty()) {
                    out.append("[]");
                    return;
                }
                String inner = indent + "  ";
                out.append('[');
                boolean first = true;
                for (Object item : c) {
                    out.append(first ? "\n" : ",\n").append(inner);
                    json(out, item, inner);
                    first = false;
                }
                out.append('\n').append(indent).append(']');
            }
            default -> throw new IllegalArgumentException("no JSON form for " + value.getClass().getName());
        }
    }

    private static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
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
