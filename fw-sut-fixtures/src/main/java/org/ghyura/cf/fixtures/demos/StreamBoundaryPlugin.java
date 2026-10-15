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

/** Original ten-sheet D2 scenario. Its inlined Python source is provenance data, never an executable program. */
public final class StreamBoundaryPlugin implements SutPlugin {
    public static final String ID = "demo-d2-stream-boundaries";
    @Override public String id() { return ID; }
    @Override public String version() { return "02-d2@0bc3e5a/java-1"; }
    @Override public Map<Integer, String> verdictNames() { return Map.of(0, "PASS", 2, "DOMAIN_FAIL"); }

    private static String one(SutCandidate candidate, String sheet) {
        var selection = candidate.selection(sheet).orElseThrow(() -> new IllegalArgumentException("missing " + sheet));
        if (!selection.inCandidate() || selection.values().size() != 1) throw new IllegalArgumentException("one " + sheet + " value required");
        return selection.values().getFirst();
    }

    private static String assigned(SutCandidate candidate, String sheet) {
        String text = one(candidate, sheet), prefix = sheet + " = \"", suffix = "\"";
        if (!text.startsWith(prefix) || !text.endsWith(suffix)) throw new IllegalArgumentException("malformed D2 catalogue entry");
        return text.substring(prefix.length(), text.length() - suffix.length());
    }

    @Override public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) {
                ctx.cancellation().throwIfCancelled();
                try (var memory = ctx.reserve(65536, "D2 decoder state and observation")) {
                    if (memory.bytes() != 65536) throw new IllegalStateException("invalid decoder reservation");
                    one(candidate, "HEAD"); one(candidate, "TAIL");
                    String adapter = assigned(candidate, "IMPL"), payload = assigned(candidate, "PAYLOAD"), errors = assigned(candidate, "ERRORS");
                    int mask = 0;
                    for (int i = 1; i <= 5; i++) {
                        String text = one(candidate, "CUT" + i);
                        if (text.equals("CUT" + i + " = 1")) mask |= 1 << (i - 1);
                        else if (!text.equals("CUT" + i + " = 0")) throw new IllegalArgumentException("binary cut bit required");
                    }
                    var r = StreamBoundaries.evaluate(adapter, payload, errors, mask);
                    var p = StreamBoundaries.payload(payload);
                    String caseId = adapter + "|P=" + payload + "|E=" + errors + "|M=" + String.format(java.util.Locale.ROOT, "%02x", mask);
                    var attrs = new java.util.LinkedHashMap<String, String>();
                    attrs.put("case", caseId); attrs.put("adapter", adapter); attrs.put("payload", payload);
                    attrs.put("payload_hex", p.hex()); attrs.put("encoding", p.encoding()); attrs.put("errors", errors);
                    attrs.put("mask", Integer.toString(mask)); attrs.put("chunks_hex", String.join(" ", r.chunksHex()));
                    attrs.put("failure_class", r.failureClass());
                    attrs.put("reference_accepted", Boolean.toString(r.reference().accepted()));
                    attrs.put("reference_text", r.reference().accepted() ? r.reference().text() : "");
                    attrs.put("reference_error", r.reference().accepted() ? "" : r.reference().error());
                    attrs.put("observed_accepted", Boolean.toString(r.observed().accepted()));
                    attrs.put("observed_text", r.observed().accepted() ? r.observed().text() : "");
                    attrs.put("observed_error", r.observed().accepted() ? "" : r.observed().error());
                    return new DomainObservation(r.passed() ? 0 : 2,
                            Map.of("chunk_count", (double) r.chunksHex().size(), "accepted", r.observed().accepted() ? 1.0 : 0.0), attrs);
                }
            }
            @Override public void close() { }
        };
    }
}
