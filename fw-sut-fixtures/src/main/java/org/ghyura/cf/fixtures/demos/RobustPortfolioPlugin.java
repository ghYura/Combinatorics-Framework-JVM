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
import java.util.List;
import java.util.Map;

/** Strict original raw cells; a present empty DESIGN is the valid empty portfolio. */
public final class RobustPortfolioPlugin implements SutPlugin {
    public static final String ID = "demo-d9-robust-portfolios";
    private static final List<String> SHEETS = List.of("HEAD", "DESIGN", "DEMAND", "DISRUPTION", "TAIL");
    public record Parsed(String design, int demand, int disruption) { }
    @Override public String id() { return ID; }
    @Override public String version() { return "10-d9@0bc3e5a/java-1"; }
    @Override public Map<Integer, String> verdictNames() { return Map.of(0, "PASS", 2, "DOMAIN_FAIL"); }
    private static String one(SutCandidate c, String name) {
        var s = c.selection(name).orElseThrow(() -> new IllegalArgumentException("missing " + name));
        if (!s.inCandidate() || s.values().size() != 1) throw new IllegalArgumentException("one " + name + " required");
        return s.values().getFirst();
    }
    private static int world(String atom, String name) {
        for (int i = 0; i < 4; i++) if (atom.equals(name + "(" + i + ")\n")) return i;
        throw new IllegalArgumentException("unknown or malformed " + name + " atom");
    }
    public static Parsed parseAtoms(List<String> modules, String demand, String disruption) {
        int previous = -1, bits = 0;
        for (String atom : modules) {
            int index = -1;
            for (int i = 0; i < 6; i++) if (atom.equals("enable(\"" + RobustPortfolios.MODULES.get(i) + "\")\n")) index = i;
            if (index <= previous) throw new IllegalArgumentException("unknown, repeated or out-of-order module");
            previous = index; bits |= 1 << (5 - index);
        }
        return new Parsed(RobustPortfolios.design(bits), world(demand, "demand"), world(disruption, "disruption"));
    }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate c, ExecutionContext ctx) {
            ctx.cancellation().throwIfCancelled();
            try (var owned = ctx.reserve(65536, "D9 SUT, literal oracle and complete cost observation")) {
                if (owned.bytes() != 65536) throw new IllegalStateException("invalid D9 reservation");
                if (!c.selections().stream().map(s -> s.sheet()).toList().equals(SHEETS)) throw new IllegalArgumentException("D9 requires original five-sheet order");
                one(c, "HEAD");
                if (!one(c, "TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n")) throw new IllegalArgumentException("unknown D9 finish atom");
                var selection = c.selection("DESIGN").orElseThrow();
                if (!selection.inCandidate()) throw new IllegalArgumentException("DESIGN must be present, possibly empty");
                var p = parseAtoms(selection.values(), one(c, "DEMAND"), one(c, "DISRUPTION"));
                var r = RobustPortfolios.evaluate(p.design(), p.demand(), p.disruption());
                return new DomainObservation(r.passed() ? 0 : 2, Map.of("total_cost", (double)r.observed().totalCost()), RobustPortfolios.attributes(r));
            }
        }
        @Override public void close() { }
    }; }
}
