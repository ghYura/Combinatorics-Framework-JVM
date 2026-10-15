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

/** Full original D8a scenario; inlined Python atoms are data and the request state belongs to one candidate. */
public final class TiesPaginationPlugin implements SutPlugin {
    public static final String ID = "demo-d8-ties-pagination";
    @Override public String id() { return ID; }
    @Override public String version() { return "08-d8a@0bc3e5a/java-1"; }
    @Override public Map<Integer, String> verdictNames() { return Map.of(0, "PASS", 2, "DOMAIN_FAIL"); }
    private static String one(SutCandidate candidate, String sheet) {
        var s = candidate.selection(sheet).orElseThrow(() -> new IllegalArgumentException("missing " + sheet));
        if (!s.inCandidate() || s.values().size() != 1) throw new IllegalArgumentException("one " + sheet + " value required");
        return s.values().getFirst();
    }
    private static String argument(String name, String text) {
        String prefix = name + "(\"", suffix = "\")\n";
        if (!text.startsWith(prefix) || !text.endsWith(suffix)) throw new IllegalArgumentException("malformed D8 catalogue entry");
        return text.substring(prefix.length(), text.length() - suffix.length());
    }
    @Override public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) {
                ctx.cancellation().throwIfCancelled();
                try (var memory = ctx.reserve(131072, "D8 page/comparison trace and oracle")) {
                    if (memory.bytes() != 131072) throw new IllegalStateException("invalid pagination reservation");
                    one(candidate, "HEAD"); one(candidate, "TAIL");
                    String policy = argument("impl", one(candidate, "IMPL")), ranks = argument("ranks", one(candidate, "RANKS")),
                            direction = argument("direction", one(candidate, "DIRECTION"));
                    var r = TiesPagination.evaluate(policy, ranks, direction);
                    var attributes = new java.util.LinkedHashMap<String, String>();
                    attributes.put("case", policy + "|D=" + direction + "|R=" + ranks); attributes.put("policy", policy);
                    attributes.put("ranks", ranks); attributes.put("direction", direction); attributes.put("pages", r.pages().toString());
                    attributes.put("collected", String.join("", r.collected())); attributes.put("expected", String.join("", r.expected()));
                    r.obligations().forEach((k, v) -> attributes.put(k, v.toString()));
                    attributes.put("comparator_laws", r.comparatorLaws().toString());
                    attributes.put("comparators_lawful", Boolean.toString(r.comparatorLaws().stream().allMatch(m -> m.values().stream().allMatch(Boolean::booleanValue))));
                    return new DomainObservation(r.passed() ? 0 : 2, Map.of("request_count", (double) r.pages().size(),
                            "collected_count", (double) r.collected().size()), attributes);
                }
            }
            @Override public void close() { }
        };
    }
}
