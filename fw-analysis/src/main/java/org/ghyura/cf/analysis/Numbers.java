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

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a metric value from text as the legacy analyzer did (AnalyzerCore.tryParseNumeric): a decimal comma is a point,
 * leading brackets and quotes are dropped, null, none and nan are no value, a unit suffix scales the number (GiB, MiB, KiB,
 * GB, MB, KB, ms, us, ns, %, B, s; the longest suffix first), and otherwise the first number in the text counts.
 * Infinite results are no value either: nothing nonfinite ever becomes a metric.
 */
public final class Numbers {

    private static final String[][] UNITS = {{"GiB", "1.073741824e9"}, {"MiB", "1.048576e6"}, {"KiB", "1024.0"}, {"GB", "1.0e9"},
            {"MB", "1.0e6"}, {"KB", "1.0e3"}, {"ms", "1.0e-3"}, {"μs", "1.0e-6"}, {"us", "1.0e-6"}, {"ns", "1.0e-9"}, {"%", "0.01"},
            {"B", "1.0"}, {"s", "1.0"}};
    private static final Pattern NUMBER = Pattern.compile("(?<!\\w)[-+]?(?:\\d+(?:[.,]\\d+)?|\\d*\\.\\d+)(?:[eE][-+]?\\d+)?");

    private Numbers() {
    }

    public static OptionalDouble parse(String value) {
        if (value == null) {
            return OptionalDouble.empty();
        }
        String s = value.replace(',', '.').trim();
        while (!s.isEmpty() && "([{\"'".indexOf(s.charAt(0)) >= 0) {
            s = s.substring(1);
        }
        if (s.isEmpty() || s.equalsIgnoreCase("null") || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("nan")) {
            return OptionalDouble.empty();
        }
        OptionalDouble direct = finite(s, 1);
        if (direct.isPresent() || isJavaNumber(s)) {
            return direct;
        }
        for (String[] unit : UNITS) {
            if (s.length() > unit[0].length() && s.endsWith(unit[0])) {
                OptionalDouble scaled = finite(s.substring(0, s.length() - unit[0].length()).trim(), Double.parseDouble(unit[1]));
                if (scaled.isPresent()) {
                    return scaled;
                }
            }
        }
        Matcher m = NUMBER.matcher(s);
        if (m.find()) {
            return finite(m.group(), 1);
        }
        return OptionalDouble.empty();
    }

    /** Whether Double.parseDouble reads the text (an infinite value then means no value, not a fallback). */
    private static boolean isJavaNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static OptionalDouble finite(String text, double scale) {
        try {
            double v = Double.parseDouble(text) * scale;
            return Double.isFinite(v) ? OptionalDouble.of(v) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
