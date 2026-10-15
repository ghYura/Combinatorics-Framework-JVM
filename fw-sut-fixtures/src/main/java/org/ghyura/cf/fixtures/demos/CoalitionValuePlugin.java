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


package org.ghyura.cf.fixtures.demos;

import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;
import java.util.Map;

/** Original FW_Subsets includes the present empty coalition; equal values retain distinct identities. */
public final class CoalitionValuePlugin implements SutPlugin {
    public static final String ID = "demo-d11-coalition-value";
    @Override public String id() { return ID; }
    @Override public String version() { return "12-d11@0bc3e5a/java-1"; }
    @Override public Map<Integer, String> verdictNames() { return Map.of(0, "PASS", 2, "DOMAIN_FAIL"); }
    private static void one(SutCandidate candidate, String name) {
        var s = candidate.selection(name).orElseThrow(() -> new IllegalArgumentException("missing " + name));
        if (!s.inCandidate() || s.values().size() != 1) throw new IllegalArgumentException("one " + name + " value required");
    }
    private static String player(String text) {
        String prefix = "enable(\"", suffix = "\")\n";
        if (!text.startsWith(prefix) || !text.endsWith(suffix)) throw new IllegalArgumentException("malformed D11 player atom");
        return text.substring(prefix.length(), text.length() - suffix.length());
    }
    @Override public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) {
                ctx.cancellation().throwIfCancelled();
                try (var memory = ctx.reserve(65536, "D11 coalition value and oracle")) {
                    if (memory.bytes() != 65536) throw new IllegalStateException("invalid coalition reservation");
                    one(candidate, "HEAD"); one(candidate, "TAIL");
                    var selected = candidate.selection("COALITION").orElseThrow(() -> new IllegalArgumentException("missing COALITION"));
                    if (!selected.inCandidate()) throw new IllegalArgumentException("absent COALITION is not the empty subset");
                    var r = CoalitionValues.evaluate(selected.values().stream().map(CoalitionValuePlugin::player).toList());
                    return new DomainObservation(r.passed() ? 0 : 2, Map.of("value", r.observed().value().doubleValue(),
                            "standalone_value", r.observed().standalone().doubleValue()), Map.of(
                            "case", "C=" + r.mask(), "mask", r.mask(), "members", String.join("", r.members()),
                            "value", r.observed().value().toString(), "standalone_value", r.observed().standalone().toString(),
                            "interaction_bonuses", r.observed().bonuses().toString(), "reference", r.reference().toString(),
                            "oracle_agrees", Boolean.toString(r.passed())));
                }
            }
            @Override public void close() { }
        };
    }
}
