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

package org.ghyura.cf.spi;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * What the system under test reported about one sample: its domain verdict code (exact, any int, 0 by convention
 * meaning success), named measurements and attributes. It is not an outcome: the executor and the plugin's
 * {@link Oracle} classify it.
 *
 * @param verdictCode the domain verdict, kept exactly (custom codes above 127 included)
 * @param metrics     finite measurements by name; NaN and infinities are refused, never coerced
 * @param attributes  further named facts (a reason, a carrier), as text
 */
public record DomainObservation(int verdictCode, Map<String, Double> metrics, Map<String, String> attributes) {

    public DomainObservation {
        metrics = Map.copyOf(new TreeMap<>(Objects.requireNonNull(metrics, "metrics")));
        attributes = Map.copyOf(new TreeMap<>(Objects.requireNonNull(attributes, "attributes")));
        for (Map.Entry<String, Double> e : metrics.entrySet()) {
            if (!Double.isFinite(e.getValue())) {
                throw new IllegalArgumentException("metric '" + e.getKey() + "' is not finite: " + e.getValue());
            }
        }
    }

    /** An observation with a verdict and nothing else. */
    public static DomainObservation of(int verdictCode) {
        return new DomainObservation(verdictCode, Map.of(), Map.of());
    }
}
