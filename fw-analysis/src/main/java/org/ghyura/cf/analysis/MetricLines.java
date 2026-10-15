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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metric-bearing text lines as the legacy analyzer read them. {@link #keyValues} is the KvLineParser (key, ':' or '=', a
 * value up to white space or a separator; keys lower-cased); {@link #sample} reads a corpus line as RepeatAggregator did:
 * the identity tokens candidate_id, repeat_idx, env_id, source_ref and run_id, the verdict FW_VAR, and every other numeric
 * pair as a metric.
 */
public final class MetricLines {

    private static final Pattern KV_PAIR = Pattern.compile("([A-Za-zА-Яа-я_][\\w.\\-]*)\\s*[:=]\\s*([^\\s;|,\\]\\)\\}]+)",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> NON_METRIC = Set.of("candidate_id", "repeat_idx", "env_id", "source_ref", "run_id", "fw_var",
            "fw_custom_var", "app");

    private MetricLines() {
    }

    /** One line of a metrics corpus or analyzer input. */
    public record Line(String candidateId, int repeat, String environment, Integer verdict, Map<String, Double> metrics,
                       Map<String, String> attributes) {
    }

    /** The key=value (or key: value) pairs of a line, keys lower-cased, the last of a repeated key kept. */
    public static Map<String, String> keyValues(String line) {
        Map<String, String> kv = new LinkedHashMap<>();
        if (line == null || line.isEmpty()) {
            return kv;
        }
        Matcher m = KV_PAIR.matcher(line);
        while (m.find()) {
            kv.put(m.group(1).trim().toLowerCase(Locale.ROOT), m.group(2).trim());
        }
        return kv;
    }

    /**
     * A corpus line: its identity (candidate_id, else {@code line N}), repeat index and environment, the explicit FW_VAR
     * verdict if any, numeric metrics and the other pairs as attributes.
     *
     * @throws IllegalArgumentException conflicting FW_VAR tokens, a malformed repeat_idx or FW_VAR (as the legacy reader)
     */
    public static Line sample(String line, int lineNo) {
        String candidate = null;
        int repeat = 0;
        String env = "";
        Integer verdict = null;
        for (String token : line.trim().split("\\s+")) {
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq);
            String value = token.substring(eq + 1);
            switch (key) {
                case "candidate_id" -> candidate = value;
                case "env_id" -> env = value;
                case "repeat_idx" -> {
                    try {
                        repeat = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("line " + lineNo + ": repeat_idx '" + value + "' is not an integer");
                    }
                    if (repeat < 0) {
                        throw new IllegalArgumentException("line " + lineNo + ": repeat_idx must be at least 0");
                    }
                }
                case "FW_VAR" -> {
                    int v;
                    try {
                        v = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("line " + lineNo + ": FW_VAR '" + value + "' is not an integer");
                    }
                    if (verdict != null && verdict != v) {
                        throw new IllegalArgumentException("line " + lineNo + ": conflicting FW_VAR tokens");
                    }
                    verdict = v;
                }
                default -> {
                }
            }
        }
        Map<String, Double> metrics = new LinkedHashMap<>();
        Map<String, String> attributes = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : keyValues(line).entrySet()) {
            if (NON_METRIC.contains(e.getKey())) {
                if (e.getKey().equals("app")) {
                    attributes.put("app", e.getValue());
                }
                continue;
            }
            OptionalDouble v = Numbers.parse(e.getValue());
            if (v.isPresent()) {
                metrics.put(e.getKey(), v.getAsDouble());
            } else {
                attributes.put(e.getKey(), e.getValue());
            }
        }
        return new Line(candidate == null || candidate.isEmpty() ? "line " + lineNo : candidate, repeat, env, verdict, metrics, attributes);
    }
}
