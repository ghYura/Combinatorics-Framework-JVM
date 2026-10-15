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

/** Four original sheets; OPS is the six actual event atoms composed by the native permutation verb. */
public final class WorkflowCoveragePlugin implements SutPlugin {
    public static final String ID="demo-d3c-workflow-coverage";
    public static final List<String> SHEETS=List.of("HEAD","IMPL","OPS","TAIL");
    @Override public String id() { return ID; }
    @Override public String version() { return "03-d3-phase-c@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    private static String call(String text,String name) { var m=Pattern.compile(name+"\\(\"([A-Za-z_]+)\"\\)\n").matcher(text);if(!m.matches())throw new IllegalArgumentException("original "+name+" atom including LF required");return m.group(1); }
    public static WorkflowCoverage.Config parseAtoms(String impl,List<String> ops) { return new WorkflowCoverage.Config(call(impl,"start"),ops.stream().map(s -> call(s,"step")).collect(java.util.stream.Collectors.joining())); }
    private static String one(SutCandidate c,String sheet) { var s=c.selection(sheet).orElseThrow(() -> new IllegalArgumentException("missing "+sheet));if(!s.inCandidate()||s.values().size()!=1)throw new IllegalArgumentException("one original "+sheet+" atom required");return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx) { ctx.cancellation().throwIfCancelled();try(var owned=ctx.reserve(65536,"D3C six immutable billing checkpoints and independent accounting oracle")){if(owned.bytes()!=65536)throw new IllegalStateException("invalid D3C reservation");if(!candidate.selections().stream().map(s -> s.sheet()).toList().equals(SHEETS))throw new IllegalArgumentException("four original D3C columns required");one(candidate,"HEAD");if(!one(candidate,"TAIL").equals("_verdict = finish(\"C\")\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))throw new IllegalArgumentException("original finish C atom required");var ops=candidate.selection("OPS").orElseThrow();if(!ops.inCandidate()||ops.values().size()!=6)throw new IllegalArgumentException("six original OPS atoms required");var result=WorkflowCoverage.evaluate(parseAtoms(one(candidate,"IMPL"),ops.values()));return new DomainObservation(result.passed()?0:2,Map.of("checkpoints",6.0),WorkflowCoverage.attributes(result));}}
        @Override public void close() { }
    }; }
}
