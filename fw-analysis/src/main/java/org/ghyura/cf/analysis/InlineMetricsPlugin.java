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

import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The legacy analyzer's inline executor (LineExecutor.Inline) as a typed SUT plugin, {@code cf.execution.sut=inline-metrics}:
 * a candidate whose rendered text already carries its metrics as key=value pairs is its own observation. The metrics are
 * the numeric pairs of the text (keys lower-cased, units scaled, as the legacy analyzer read them); an FW_VAR pair is the
 * verdict, and a text without one is a pass marked {@code verdict=missing}, the legacy inline mode's permissive reading.
 */
public final class InlineMetricsPlugin implements SutPlugin {

    @Override
    public String id() {
        return "inline-metrics";
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override
            public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) {
                MetricLines.Line line = MetricLines.sample(candidate.source(), 1);
                Map<String, String> attributes = new LinkedHashMap<>(line.attributes());
                if (line.verdict() == null) {
                    attributes.put("verdict", "missing");
                }
                return new DomainObservation(line.verdict() == null ? 0 : line.verdict(), line.metrics(), attributes);
            }

            @Override
            public void close() {
            }
        };
    }
}
