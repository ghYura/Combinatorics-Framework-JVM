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

package org.ghyura.cf.execution;

import org.ghyura.cf.spi.DomainObservation;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The legacy metrics line of a generated candidate (audit EX07): the first line of its standard output that, stripped,
 * starts with {@code app=} and contains {@code FW_VAR=} (MainWatch.firstAppMetricLine, py_executor.collect_kv). Its
 * {@code key=value} tokens become typed metrics when the value is a finite decimal number and attributes otherwise; the
 * line itself is kept verbatim for the legacy corpus. What the candidate reported itself is never replaced, and the
 * {@code FW_VAR} token is not a metric: the verdict is read from the field.
 */
final class MetricLine {

    static final String LINE_ATTRIBUTE = org.ghyura.cf.results.LegacyMetricsCorpus.LINE_ATTRIBUTE;

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

    private MetricLine() {
    }

    /** The first metrics line of the output, stripped, or null. */
    static String find(String stdout) {
        for (String raw : stdout.split("\n", -1)) {
            String line = raw.strip();
            if (line.startsWith("app=") && line.contains("FW_VAR=")) {
                return line;
            }
        }
        return null;
    }

    /** The observation with the line's metrics and attributes added; unchanged when the output has no metrics line. */
    static DomainObservation harvest(DomainObservation observation, String stdout) {
        String line = find(stdout);
        if (line == null) {
            return observation;
        }
        Map<String, Double> metrics = new TreeMap<>(observation.metrics());
        Map<String, String> attributes = new TreeMap<>(observation.attributes());
        attributes.putIfAbsent(LINE_ATTRIBUTE, line);
        for (String token : line.split("\\s+")) {
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq);
            String value = token.substring(eq + 1);
            if (key.equals("FW_VAR") || key.equals("FW_CUSTOM_VAR") || metrics.containsKey(key) || attributes.containsKey(key)) {
                continue;
            }
            if (DECIMAL.matcher(value).matches() && Double.isFinite(Double.parseDouble(value))) {
                metrics.put(key, Double.parseDouble(value));
            } else {
                attributes.put(key, value);
            }
        }
        return new DomainObservation(observation.verdictCode(), metrics, attributes);
    }
}
