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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The features of one logical input line as the legacy analyzer extracted them (AnalyzerCore.LineFeatures,
 * OptimizationAnalyzer.extractFeatures): the trimmed text, word tokens, numbers, key/value pairs (the KvLineParser of
 * {@link MetricLines#keyValues}), delimiter counts, placeholders, character-class ratios over the UTF-16 length,
 * Shannon entropy over code points, the compressed D/L/S/P signature, 32-character prefix and suffix and the line type
 * (empty, comment, kv_numeric, kv, templated, numeric, text or mixed). The payload is the line's own text: nothing is
 * executed and no dynamic evaluation is offered.
 */
public record TextFeatures(int lineNo, String raw, String originalLine, String text, int length, List<String> tokens,
                           List<String> tokenLowers, List<Double> numbers, Map<String, String> keyValues,
                           Map<String, Integer> delimiters, int placeholderCount, double entropy, double alphaRatio,
                           double digitRatio, double spaceRatio, double punctRatio, double uppercaseRatio,
                           double uniqueTokenRatio, String signature, String prefix, String suffix, String lineType) {

    static final Pattern NUMBER = Pattern.compile("(?<!\\w)[-+]?(?:\\d+(?:[.,]\\d+)?|\\d*\\.\\d+)(?:[eE][-+]?\\d+)?");
    static final Pattern TOKEN = Pattern.compile("\\b\\w+\\b", Pattern.UNICODE_CHARACTER_CLASS);
    static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}|\\{\\{[^}]+}}|\\[[^\\]]+]|<[^>]+>");
    static final Pattern COMMENT = Pattern.compile("^\\s*(#|//)");
    private static final String DELIMITERS = ":=,;|/\\()[]{}<>";

    public TextFeatures {
        tokens = List.copyOf(tokens);
        tokenLowers = List.copyOf(tokenLowers);
        numbers = List.copyOf(numbers);
        keyValues = Collections.unmodifiableMap(new LinkedHashMap<>(keyValues));
        delimiters = Collections.unmodifiableMap(new LinkedHashMap<>(delimiters));
    }

    /** The features of a line; {@code raw} is the analysed payload, {@code originalLine} the input it came from. */
    public static TextFeatures extract(int lineNo, String raw, String originalLine) {
        String text = raw == null ? "" : raw.replace("\r", "").trim();
        List<String> tokens = TOKEN.matcher(text).results().map(MatchResult::group).toList();
        List<String> lowers = tokens.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        List<Double> numbers = new ArrayList<>();
        Matcher nm = NUMBER.matcher(text);
        while (nm.find()) {
            numbers.add(safeDouble(nm.group()));
        }
        Map<String, String> kv = MetricLines.keyValues(text);
        Map<String, Integer> delimiters = new LinkedHashMap<>();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (DELIMITERS.indexOf(ch) >= 0) {
                delimiters.merge(String.valueOf(ch), 1, Integer::sum);
            }
        }
        int placeholders = (int) PLACEHOLDER.matcher(text).results().count();
        CharStats cs = CharStats.of(text);
        int length = text.length();
        int total = Math.max(1, length);
        double upper = cs.alpha == 0 ? 0.0 : cs.upper / (double) cs.alpha;
        double unique = lowers.isEmpty() ? 0.0 : new HashSet<>(lowers).size() / (double) lowers.size();
        String prefix = text.length() <= 32 ? text : text.substring(0, 32);
        String suffix = text.length() <= 32 ? text : text.substring(text.length() - 32);
        return new TextFeatures(lineNo, raw, originalLine == null ? raw : originalLine, text, length, tokens, lowers, numbers, kv,
                delimiters, placeholders, cs.entropy, cs.alpha / (double) total, cs.digit / (double) total, cs.space / (double) total,
                cs.punct / (double) total, upper, unique, signature(text), prefix, suffix, lineType(text, tokens, kv, numbers, placeholders));
    }

    /** Runs of digits, letters, white space and other characters as D, L, S and P (legacy compressSignature). */
    public static String signature(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char cur = Character.isDigit(ch) ? 'D' : Character.isLetter(ch) ? 'L' : Character.isWhitespace(ch) ? 'S' : 'P';
            if (cur != prev) {
                sb.append(cur);
                prev = cur;
            }
        }
        return sb.toString();
    }

    /** A number as the profile counts it: an integral value without decimals, otherwise three decimals (legacy canonicalNumber). */
    public static String canonicalNumber(double n) {
        if (Math.abs(n - Math.rint(n)) < 1e-9) {
            return String.valueOf(Math.round(n));
        }
        return String.format(Locale.ROOT, "%.3f", n);
    }

    /** The legacy safeDouble: a decimal comma is a point, anything unreadable is 0. */
    private static double safeDouble(String s) {
        String t = s.replace(',', '.').trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String lineType(String line, List<String> tokens, Map<String, String> kv, List<Double> numbers, int placeholders) {
        String t = line.trim();
        if (t.isEmpty()) {
            return "empty";
        }
        if (COMMENT.matcher(t).lookingAt()) {
            return "comment";
        }
        if (kv.size() >= 2 && !numbers.isEmpty()) {
            return "kv_numeric";
        }
        if (kv.size() >= 2) {
            return "kv";
        }
        if (placeholders > 0) {
            return "templated";
        }
        if (numbers.size() >= 3 && tokens.size() <= 10) {
            return "numeric";
        }
        if (tokens.size() >= 10) {
            return "text";
        }
        return "mixed";
    }

    /** One pass over the code points: class counts and the Shannon entropy in bits (legacy CharStats, same summation order). */
    private static final class CharStats {
        long alpha;
        long digit;
        long space;
        long punct;
        long upper;
        int total;
        double entropy;

        static CharStats of(String text) {
            CharStats s = new CharStats();
            if (text.isEmpty()) {
                return s;
            }
            int[] ascii = new int[128];
            Map<Integer, Integer> ext = null;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                if (Character.isDigit(cp)) s.digit++;
                if (Character.isLetter(cp)) s.alpha++;
                if (Character.isWhitespace(cp)) s.space++;
                if (Character.isUpperCase(cp)) s.upper++;
                if (!Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp)) s.punct++;
                if (cp < 128) {
                    ascii[cp]++;
                } else {
                    if (ext == null) ext = new HashMap<>();
                    ext.merge(cp, 1, Integer::sum);
                }
                i += Character.charCount(cp);
                s.total++;
            }
            double inv = 1.0 / s.total;
            double invLog2 = 1.0 / Math.log(2);
            double e = 0.0;
            for (int c : ascii) {
                if (c > 0) {
                    double p = c * inv;
                    e -= p * Math.log(p) * invLog2;
                }
            }
            if (ext != null) {
                for (int c : ext.values()) {
                    double p = c * inv;
                    e -= p * Math.log(p) * invLog2;
                }
            }
            s.entropy = e;
            return s;
        }
    }
}
