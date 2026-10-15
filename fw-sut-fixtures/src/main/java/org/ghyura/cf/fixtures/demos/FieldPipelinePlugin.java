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
import java.util.regex.Pattern;

/** Four original final columns. The excluded FIELD reaches OPS through its third, grouped fragment. */
public final class FieldPipelinePlugin implements SutPlugin {
    public static final String ID="demo-d5a-field-pipeline";
    public static final List<String> SHEETS=List.of("HEAD","IMPL","OPS","TAIL");
    @Override public String id() { return ID; }
    @Override public String version() { return "05-d5-A@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    private static String call(String value,String name) { var m=Pattern.compile(name+"\\(\"([A-Za-z_]+)\"\\)\n").matcher(value);if(!m.matches())throw new IllegalArgumentException("original "+name+" fragment including LF required");return m.group(1); }
    public static FieldPipeline.Config parseAtoms(String impl,List<String> fragments) { if(fragments.size()!=3)throw new IllegalArgumentException("two push operations then bind required");return new FieldPipeline.Config(call(impl,"impl"),new FieldPipeline.Tree(call(fragments.get(2),"bind"),List.of(call(fragments.get(0),"push_op"),call(fragments.get(1),"push_op")))); }
    private static String one(SutCandidate c,String sheet) { var s=c.selection(sheet).orElseThrow();if(!s.inCandidate()||s.values().size()!=1)throw new IllegalArgumentException("one original "+sheet+" atom required");return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx) { ctx.cancellation().throwIfCancelled();try(var owned=ctx.reserve(65536,"D5 A immutable records/traces and independent record oracle")){if(owned.bytes()!=65536)throw new IllegalStateException("invalid D5 A reservation");if(!candidate.selections().stream().map(s->s.sheet()).toList().equals(SHEETS))throw new IllegalArgumentException("original four D5 A columns required");one(candidate,"HEAD");if(!one(candidate,"TAIL").equals("_verdict = finish(\"A\")\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))throw new IllegalArgumentException("original finish A fragment required");var ops=candidate.selection("OPS").orElseThrow();if(!ops.inCandidate())throw new IllegalArgumentException("grouped OPS required");var result=FieldPipeline.evaluate(parseAtoms(one(candidate,"IMPL"),ops.values()));return new DomainObservation(result.passed()?0:2,Map.of("operations",2.0),FieldPipeline.attributes(result));}}
        @Override public void close() { }
    }; }
}
