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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Original D10 sheets/raw newline atoms; HEAD is provenance, never executed. */
public final class DiagnosticSensorPlugin implements SutPlugin {
    public static final String ID="demo-d10-diagnostic-sensors";
    public record Parsed(String mask,String fault) { }
    @Override public String id() { return ID; }
    @Override public String version() { return "11-d10@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    public static Parsed parseAtoms(List<String> sensors,String fault) {
        var selected=new ArrayList<Integer>();
        for (var atom : sensors) {
            var m=java.util.regex.Pattern.compile("select\\(([0-7])\\)\n").matcher(atom);
            if (!m.matches()) throw new IllegalArgumentException("unknown sensor atom"); selected.add(Integer.valueOf(m.group(1)));
        }
        String mask=DiagnosticSensors.mask(selected); var m=java.util.regex.Pattern.compile("fault\\(\"(F0[1-9]|F1[0-2])\"\\)\n").matcher(fault);
        if (!m.matches()) throw new IllegalArgumentException("one campaign fault atom required; H0 is control only"); return new Parsed(mask,m.group(1));
    }
    private static String one(SutCandidate c,String name) {
        var s=c.selection(name).orElseThrow(() -> new IllegalArgumentException("missing "+name));
        if (!s.inCandidate()||s.values().size()!=1) throw new IllegalArgumentException("one "+name+" required"); return s.values().getFirst();
    }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate c,ExecutionContext ctx) {
            ctx.cancellation().throwIfCancelled(); try (var owned=ctx.reserve(65536,"D10 fresh plant/probes, literal reference and full observation")) {
                if (owned.bytes()!=65536) throw new IllegalStateException("invalid D10 reservation");
                if (!c.selections().stream().map(s -> s.sheet()).toList().equals(List.of("HEAD","SENSORS","FAULT","TAIL"))) throw new IllegalArgumentException("original D10 sheet order required");
                one(c,"HEAD"); if (!one(c,"TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n")) throw new IllegalArgumentException("unknown finish atom");
                var sensors=c.selection("SENSORS").orElseThrow(); if (!sensors.inCandidate()) throw new IllegalArgumentException("sensor subset must be present, including empty selection");
                var parsed=parseAtoms(sensors.values(),one(c,"FAULT")); var r=DiagnosticSensors.evaluate(parsed.mask(),parsed.fault());
                return new DomainObservation(r.passed()?0:2,Map.of("sensor_cost",(double)r.selected().size()),DiagnosticSensors.attributes(r));
            }
        }
        @Override public void close() { }
    }; }
}
