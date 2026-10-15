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

package org.ghyura.cf.fixtures.business;

import org.ghyura.cf.model.Json;
import org.ghyura.cf.spi.SheetSelection;
import org.ghyura.cf.spi.SutCandidate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Domain-port helpers; preserve Python's binary-float rounding and ordered, nullable dictionaries. */
public final class FixtureValues {
    private FixtureValues() {}

    public static double round2(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite price");
        double rounded = new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
        return rounded == 0 ? Math.copySign(0, value) : rounded;
    }

    public static Map<String, Object> map(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Uneven dictionary entries");
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    public static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected list");
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text)) throw new IllegalArgumentException("Expected text item");
            result.add(text);
        }
        return List.copyOf(result);
    }

    public static String one(SutCandidate candidate, String sheet, String fallback) {
        return candidate.selection(sheet).filter(SheetSelection::inCandidate)
                .map(SheetSelection::values).map(values -> {
                    if (values.size() > 1) throw new IllegalArgumentException("Sheet " + sheet + " requires one value");
                    return values.isEmpty() ? fallback : values.getFirst();
                }).orElse(fallback);
    }

    public static List<String> selected(SutCandidate candidate, String sheet) {
        return candidate.selection(sheet).filter(SheetSelection::inCandidate).map(SheetSelection::values).orElse(List.of());
    }

    public static String json(Object value) { return Json.write(value); }

    /** Python orders Unicode scalar values; Java's natural UTF-16 ordering differs for supplementary characters. */
    public static List<String> unicodeSorted(List<String> values) {
        return values.stream().sorted((left, right) -> {
            int a = 0, b = 0;
            while (a < left.length() && b < right.length()) {
                int x = left.codePointAt(a), y = right.codePointAt(b);
                if (x != y) return Integer.compare(x, y);
                a += Character.charCount(x); b += Character.charCount(y);
            }
            return Integer.compare(left.length() - a, right.length() - b);
        }).toList();
    }

    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (!(key instanceof String text)) throw new IllegalArgumentException("Expected text key");
            result.put(text, item);
        });
        return result;
    }

    public static String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String text)) throw new IllegalArgumentException("Expected text for " + key);
        return text;
    }

    public static java.math.BigInteger integer(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        if (value == null) return java.math.BigInteger.valueOf(fallback);
        if (value instanceof java.math.BigInteger n) return n;
        if (value instanceof java.math.BigDecimal n) return n.toBigIntegerExact();
        if (value instanceof Number || value instanceof String) return new java.math.BigInteger(value.toString());
        throw new IllegalArgumentException("Expected integer for " + key);
    }

    public static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException("Expected boolean for " + key);
        return flag;
    }
}
