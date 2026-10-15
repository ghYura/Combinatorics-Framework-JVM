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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.model.rule.RuleValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * How the legacy editor's page read and showed the text of a value parameter, in the browser's terms: String.trim,
 * Number(x) and String(x) of what JSON.parse gave it, and what JSON.stringify then handed back to the sidecar.
 */
final class JsText {

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:Infinity|(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");
    private static final Pattern RADIX = Pattern.compile("0(?:[xX][0-9a-fA-F]+|[oO][0-7]+|[bB][01]+)");
    private static final BigInteger SAFE = BigInteger.TWO.pow(53);

    private JsText() {
    }

    /** String.prototype.trim: white space and line terminators off both ends. */
    static String trim(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && space(s.charAt(start))) {
            start++;
        }
        while (end > start && space(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean space(char c) {
        return c == '\t' || c == '\u000b' || c == '\f' || c == '\n' || c == '\r' || c == ' ' || c == ' ' || c == '﻿'
                || Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    /**
     * Number(x) when it is a number, as JSON.stringify wrote it and the sidecar's reader took it: an integer below 1e21 an
     * int, another finite number a float, an infinity null; null when Number(x) is NaN (the text stays text).
     */
    static RuleValue number(String text) {
        String x = trim(text);
        double v;
        if (x.isEmpty()) {
            v = 0;
        } else if (RADIX.matcher(x).matches()) {
            int radix = switch (Character.toLowerCase(x.charAt(1))) {
                case 'x' -> 16;
                case 'o' -> 8;
                default -> 2;
            };
            v = new BigInteger(x.substring(2), radix).doubleValue();
        } else if (DECIMAL.matcher(x).matches()) {
            v = x.endsWith("Infinity") ? (x.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY) : Double.parseDouble(x);
        } else {
            return null;
        }
        if (Double.isInfinite(v)) {
            return RuleValue.Null.NULL;
        }
        if (v == Math.rint(v) && Math.abs(v) < 1e21) {
            return new RuleValue.Int(new BigDecimal(string(v)).toBigIntegerExact());
        }
        return new RuleValue.Real(v);
    }

    /** String(x) of a value as the page held it after JSON.parse. */
    static String string(RuleValue v) {
        return switch (v) {
            case RuleValue.Text t -> t.value();
            case RuleValue.Int i -> i.value().abs().compareTo(SAFE) <= 0 ? i.value().toString() : string(i.value().doubleValue());
            case RuleValue.Real r -> string(r.value());
            case RuleValue.Bool b -> String.valueOf(b.value());
            case RuleValue.Null n -> "null";
            case RuleValue.ListValue l -> String.join(",", l.items().stream().map(x -> x == RuleValue.Null.NULL ? "" : string(x)).toList());
            case RuleValue.MapValue m -> "[object Object]";
        };
    }

    /** Number.prototype.toString(): the shortest digits that read back, plain from 1e-6 up to below 1e21, else with an exponent. */
    static String string(double v) {
        if (Double.isNaN(v)) {
            return "NaN";
        }
        if (Double.isInfinite(v)) {
            return v > 0 ? "Infinity" : "-Infinity";
        }
        if (v == 0) {
            return "0";
        }
        BigDecimal shortest = new BigDecimal(Double.toString(Math.abs(v))).stripTrailingZeros();
        String s = shortest.unscaledValue().toString();
        int k = s.length();
        int n = k - shortest.scale();
        String sign = v < 0 ? "-" : "";
        if (k <= n && n <= 21) {
            return sign + s + "0".repeat(n - k);
        }
        if (0 < n && n <= 21) {
            return sign + s.substring(0, n) + "." + s.substring(n);
        }
        if (-6 < n && n <= 0) {
            return sign + "0." + "0".repeat(-n) + s;
        }
        String exponent = "e" + (n - 1 < 0 ? "-" : "+") + Math.abs(n - 1);
        return sign + (k == 1 ? s : s.charAt(0) + "." + s.substring(1)) + exponent;
    }
}
