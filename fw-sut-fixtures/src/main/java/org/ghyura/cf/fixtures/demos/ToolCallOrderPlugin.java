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
import java.util.regex.Pattern;

/** Original seven-sheet D13a scenario. HEAD is source provenance; no fragment is executed. */
public final class ToolCallOrderPlugin implements SutPlugin {
    public static final String ID="demo-d13a-tool-call-order";
    @Override public String id() { return ID; }
    @Override public String version() { return "14-d13a@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    public static ToolCallOrder.Config parseAtoms(List<String> atoms) {
        String policy=null; Byte repetitions=null,cut=null; Boolean fail=null; boolean cutSet=false; var order=new StringBuilder();
        for (int i=0;i<atoms.size();i++) {
            String atom=atoms.get(i); var impl=Pattern.compile("impl\\(\"([a-z_]+)\"\\)\n").matcher(atom);
            if (impl.matches()) { if (i!=0||policy!=null||!ToolCallOrder.POLICIES.contains(impl.group(1))) throw new IllegalArgumentException("impl must follow begin once with known policy"); policy=impl.group(1); continue; }
            if (policy==null) throw new IllegalArgumentException("configuration/plan before impl");
            var rep=Pattern.compile("repetitions\\(([12])\\)\n").matcher(atom); var read=Pattern.compile("read_mode\\(([01])\\)\n").matcher(atom); var revoke=Pattern.compile("set_revoke\\(([0-3])\\)\n").matcher(atom); var plan=Pattern.compile("plan\\(\"([ARS])\"\\)\n").matcher(atom);
            if (rep.matches()) { if (repetitions!=null) throw new IllegalArgumentException("repeated repetitions"); repetitions=Byte.valueOf(rep.group(1)); }
            else if (read.matches()) { if (fail!=null) throw new IllegalArgumentException("repeated read mode"); fail=read.group(1).equals("1"); }
            else if (revoke.matches()) { if (cutSet) throw new IllegalArgumentException("repeated revoke cut"); cutSet=true; cut=Byte.valueOf(revoke.group(1)); }
            else if (plan.matches()) { if (order.indexOf(plan.group(1))>=0) throw new IllegalArgumentException("repeated operation"); order.append(plan.group(1)); }
            else throw new IllegalArgumentException("unknown raw atom");
        }
        if (policy==null||repetitions==null||fail==null) throw new IllegalArgumentException("incomplete configuration"); return new ToolCallOrder.Config(policy,order.toString(),repetitions,fail,cut);
    }
    private static String one(SutCandidate c,String name) {
        var s=c.selection(name).orElseThrow(() -> new IllegalArgumentException("missing "+name)); if (!s.inCandidate()||s.values().size()!=1) throw new IllegalArgumentException("one "+name+" required"); return s.values().getFirst();
    }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate c,ExecutionContext ctx) {
            ctx.cancellation().throwIfCancelled(); try (var owned=ctx.reserve(65536,"D13a fresh local orchestrator, trace, independent prefix/reference observation")) {
                if (owned.bytes()!=65536) throw new IllegalStateException("invalid D13a reservation");
                if (!c.selections().stream().map(s -> s.sheet()).toList().equals(List.of("HEAD","IMPL","REPETITIONS","READ_MODE","REVOKE","ORDER","TAIL"))) throw new IllegalArgumentException("original D13a sheet order required");
                one(c,"HEAD"); if (!one(c,"TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n")) throw new IllegalArgumentException("unknown finish atom");
                var atoms=new ArrayList<>(List.of(one(c,"IMPL"),one(c,"REPETITIONS"),one(c,"READ_MODE"))); var revoke=c.selection("REVOKE").orElseThrow(); if (revoke.inCandidate()) atoms.add(one(c,"REVOKE"));
                var order=c.selection("ORDER").orElseThrow(); if (!order.inCandidate()||order.values().size()!=3) throw new IllegalArgumentException("three plan operations required"); atoms.addAll(order.values());
                var r=ToolCallOrder.evaluate(parseAtoms(atoms)); return new DomainObservation(r.passed()?0:2,Map.of("send_requests",(double)r.observed().sends().size(),"outbox_effects",(double)r.observed().outbox().size()),ToolCallOrder.attributes(r));
            }
        }
        @Override public void close() { }
    }; }
}
