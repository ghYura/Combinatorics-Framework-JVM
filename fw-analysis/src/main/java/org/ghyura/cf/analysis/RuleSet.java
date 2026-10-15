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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The manual rules of the textual scorer: the legacy analyzer's manual JSON configuration (AnalyzerCore
 * OptimizationAnalyzer) with its field names: required_tokens, forbidden_tokens, token_weights, required_keys,
 * forbidden_keys, expected_line_types, expected_signatures, required_regexes, forbidden_regexes, notes, line_length {min,
 * max}, numeric_ranges {key: [low, high]} and optimizations [{key, mode, target_value, weight, objective, search_lo,
 * search_hi}] (or one optimization). Values are compared as the legacy analyzer compared them: tokens with the lower-cased
 * tokens of a line, keys with its lower-cased keys, numeric ranges by their key as written, goal keys and modes lower-cased.
 * Numbers and numeric text are read as Jackson read them and other fields are ignored. Unlike the legacy reader, which
 * silently coerced or dropped them, a known field of the wrong JSON type, non-numeric text where a number belongs, an invalid
 * regular expression, an unknown goal mode and an objective that does not compile are refused (delta D56); a null range
 * bound or length limit is an open one, and a goal without a key is skipped, as before.
 */
public record RuleSet(List<String> requiredTokens, List<String> forbiddenTokens, Map<String, Double> tokenWeights,
                      List<String> requiredKeys, List<String> forbiddenKeys, List<String> expectedLineTypes,
                      List<String> expectedSignatures, List<Pattern> requiredRegexes, List<Pattern> forbiddenRegexes,
                      List<String> notes, Integer minLength, Integer maxLength, Map<String, Range> numericRanges, List<Goal> goals) {

    /** No manual rule: profile-derived contributions only. */
    public static final RuleSet NONE = new RuleSet(List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), null, null, Map.of(), List.of());

    /** A numeric range, bounds included; an open bound is infinite. */
    public record Range(double low, double high) {
    }

    /**
     * A weighted goal over one metric. With an objective in x and a finite bracket, Brent's method (tolerance 1e-9, 200
     * iterations) finds its argmin once, and a converged argmin replaces the mode as the target.
     */
    public record Goal(String key, String mode, double target, double weight, String objective, double searchLow,
                       double searchHigh, double argmin, double optimum, boolean optimumValid) {

        /** @throws FrameworkException ANALYSIS_INVALID_EXPRESSION when an objective with a usable bracket does not compile */
        public static Goal of(String key, String mode, double target, double weight, String objective, double low, double high) {
            double x = Double.NaN;
            double fx = Double.NaN;
            boolean valid = false;
            if (objective != null && !objective.isBlank() && Double.isFinite(low) && Double.isFinite(high) && high > low) {
                DoubleUnaryOperator f = Expression.function(objective, "x");
                try {
                    NumericMethods.Min1D r = NumericMethods.brent(f, low, high, 1e-9, 200);
                    x = r.x();
                    fx = r.fx();
                    valid = r.converged();
                } catch (FrameworkException e) {
                    if (e.code() != ErrorCode.ANALYSIS_NONFINITE) {
                        throw e;
                    }
                }
            }
            return new Goal(key, mode, target, weight, objective, low, high, x, fx, valid);
        }
    }

    public RuleSet {
        requiredTokens = List.copyOf(requiredTokens);
        forbiddenTokens = List.copyOf(forbiddenTokens);
        tokenWeights = Collections.unmodifiableMap(new LinkedHashMap<>(tokenWeights));
        requiredKeys = List.copyOf(requiredKeys);
        forbiddenKeys = List.copyOf(forbiddenKeys);
        expectedLineTypes = List.copyOf(expectedLineTypes);
        expectedSignatures = List.copyOf(expectedSignatures);
        requiredRegexes = List.copyOf(requiredRegexes);
        forbiddenRegexes = List.copyOf(forbiddenRegexes);
        notes = List.copyOf(notes);
        numericRanges = Collections.unmodifiableMap(new LinkedHashMap<>(numericRanges));
        goals = List.copyOf(goals);
    }

    /** How many rules can contribute to one line's breakdown (for memory estimates). */
    int size() {
        return requiredTokens.size() + forbiddenTokens.size() + tokenWeights.size() + requiredKeys.size() + forbiddenKeys.size()
                + requiredRegexes.size() + forbiddenRegexes.size() + notes.size() + numericRanges.size() + 3 * goals.size() + 4;
    }

    /**
     * Reads a rules document (UTF-8 JSON), admitted to the budget while it is parsed.
     *
     * @throws FrameworkException INPUT_UNREADABLE, INPUT_MALFORMED, ANALYSIS_INVALID_GOAL, ANALYSIS_INVALID_EXPRESSION or
     *                            RESOURCE_LIMIT
     */
    public static RuleSet read(Path file, MemoryBudget budget) {
        try (AnalysisInput input = AnalysisInput.read(file, budget, 192)) {
            return parse(input.text, file.toString());
        }
    }

    /** Bounded UTF-8 source for a rules editor; the editor owns the returned text and may edit invalid JSON. */
    public static String readText(Path file, MemoryBudget budget) {
        try (AnalysisInput input = AnalysisInput.read(file, budget, 192)) {
            return input.text;
        }
    }

    /** @throws FrameworkException INPUT_MALFORMED, ANALYSIS_INVALID_GOAL or ANALYSIS_INVALID_EXPRESSION */
    public static RuleSet parse(String json, String origin) {
        if (!(Json.parse(json, origin) instanceof Map<?, ?> root)) {
            throw malformed(origin, "the rules document must be a JSON object");
        }
        List<Goal> goals = new ArrayList<>();
        if (root.containsKey("optimizations")) {
            if (!(root.get("optimizations") instanceof List<?> list)) {
                throw malformed(origin, "rules field 'optimizations' must be an array of objects");
            }
            for (Object item : list) {
                Goal g = goal(item, origin, "optimizations");
                if (g != null) goals.add(g);
            }
        } else if (root.containsKey("optimization")) {
            Goal g = goal(root.get("optimization"), origin, "optimization");
            if (g != null) goals.add(g);
        }
        Integer minLength = null;
        Integer maxLength = null;
        if (root.containsKey("line_length")) {
            if (!(root.get("line_length") instanceof Map<?, ?> band)) {
                throw malformed(origin, "rules field 'line_length' must be an object with min and max");
            }
            minLength = band.get("min") == null ? null : integer(band.get("min"), origin, "line_length.min");
            maxLength = band.get("max") == null ? null : integer(band.get("max"), origin, "line_length.max");
        }
        Map<String, Range> ranges = new LinkedHashMap<>();
        if (root.containsKey("numeric_ranges")) {
            if (!(root.get("numeric_ranges") instanceof Map<?, ?> map)) {
                throw malformed(origin, "rules field 'numeric_ranges' must be an object of [low, high] arrays");
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String field = "numeric_ranges." + e.getKey();
                if (!(e.getValue() instanceof List<?> bounds) || bounds.size() != 2) {
                    throw malformed(origin, "rules field '" + field + "' must be an array [low, high]");
                }
                ranges.put((String) e.getKey(), new Range(bound(bounds.get(0), Double.NEGATIVE_INFINITY, origin, field),
                        bound(bounds.get(1), Double.POSITIVE_INFINITY, origin, field)));
            }
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        if (root.containsKey("token_weights")) {
            if (!(root.get("token_weights") instanceof Map<?, ?> map)) {
                throw malformed(origin, "rules field 'token_weights' must be an object of numbers");
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                weights.put((String) e.getKey(), number(e.getValue(), origin, "token_weights." + e.getKey()));
            }
        }
        return new RuleSet(strings(root, "required_tokens", origin), strings(root, "forbidden_tokens", origin), weights,
                strings(root, "required_keys", origin), strings(root, "forbidden_keys", origin), strings(root, "expected_line_types", origin),
                strings(root, "expected_signatures", origin), patterns(root, "required_regexes", origin),
                patterns(root, "forbidden_regexes", origin), strings(root, "notes", origin), minLength, maxLength, ranges, goals);
    }

    private static Goal goal(Object item, String origin, String field) {
        if (!(item instanceof Map<?, ?> opt)) {
            throw malformed(origin, "rules field '" + field + "' must hold objects");
        }
        Object rawKey = opt.get("key");
        if (rawKey != null && !(rawKey instanceof String)) {
            throw malformed(origin, "rules field '" + field + ".key' must be text");
        }
        String key = rawKey == null ? "" : ((String) rawKey).toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return null;
        }
        Object rawMode = opt.containsKey("mode") ? opt.get("mode") : "minimize";
        if (!(rawMode instanceof String m)) {
            throw malformed(origin, "rules field '" + field + ".mode' must be text");
        }
        String mode = m.toLowerCase(Locale.ROOT);
        if (!mode.equals("minimize") && !mode.equals("maximize") && !mode.equals("target")) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.ANALYSIS_INVALID_GOAL, "the goal '" + key + "' has the mode '" + m
                    + "'; use minimize, maximize or target", SourceLocation.of(origin), "key", key));
        }
        Object objective = opt.get("objective");
        if (objective != null && !(objective instanceof String)) {
            throw malformed(origin, "rules field '" + field + ".objective' must be text");
        }
        return Goal.of(key, mode, optionalNumber(opt, "target_value", 0.0, origin, field), optionalNumber(opt, "weight", 30.0, origin, field),
                (String) objective, optionalNumber(opt, "search_lo", Double.NaN, origin, field),
                optionalNumber(opt, "search_hi", Double.NaN, origin, field));
    }

    private static double optionalNumber(Map<?, ?> map, String name, double absent, String origin, String field) {
        return map.containsKey(name) ? number(map.get(name), origin, field + "." + name) : absent;
    }

    /** A number, or numeric text read as Jackson's asDouble read it (trimmed, Double.parseDouble), finite. */
    private static double number(Object value, String origin, String field) {
        double d;
        if (value instanceof Long l) {
            d = l;
        } else if (value instanceof BigDecimal b) {
            d = b.doubleValue();
        } else if (value instanceof String s) {
            try {
                d = Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                throw malformed(origin, "rules field '" + field + "' must be a number, not '" + s + "'");
            }
        } else {
            throw malformed(origin, "rules field '" + field + "' must be a number");
        }
        if (!Double.isFinite(d)) {
            throw malformed(origin, "rules field '" + field + "' must be finite");
        }
        return d;
    }

    /** A length limit as the legacy safeInt read it: an integer, else a number truncated (saturating). */
    private static int integer(Object value, String origin, String field) {
        if (value instanceof String s) {
            String t = s.replace(',', '.').trim();
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException ignored) {
                // a decimal or out-of-range number is truncated below
            }
        }
        return (int) number(value instanceof String s ? s.replace(',', '.') : value, origin, field);
    }

    private static double bound(Object value, double open, String origin, String field) {
        if (value == null) {
            return open;
        }
        if (!(value instanceof Long) && !(value instanceof BigDecimal)) {
            throw malformed(origin, "rules field '" + field + "' bounds must be numbers or null");
        }
        return number(value, origin, field);
    }

    private static List<String> strings(Map<?, ?> root, String field, String origin) {
        if (!root.containsKey(field)) {
            return List.of();
        }
        if (!(root.get(field) instanceof List<?> list)) {
            throw malformed(origin, "rules field '" + field + "' must be an array");
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            switch (item) {
                case String s -> out.add(s);
                case Long l -> out.add(Long.toString(l));
                case BigDecimal b -> out.add(Double.toString(b.doubleValue()));
                case null, default -> throw malformed(origin, "rules field '" + field + "' must hold text");
            }
        }
        return out;
    }

    private static List<Pattern> patterns(Map<?, ?> root, String field, String origin) {
        List<Pattern> out = new ArrayList<>();
        for (String s : strings(root, field, origin)) {
            try {
                out.add(Pattern.compile(s));
            } catch (PatternSyntaxException e) {
                throw malformed(origin, "rules field '" + field + "' holds an invalid pattern: " + e.getDescription() + " in " + s);
            }
        }
        return out;
    }

    private static FrameworkException malformed(String origin, String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, message, SourceLocation.of(origin)));
    }
}
