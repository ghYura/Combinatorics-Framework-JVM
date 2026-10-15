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

/** Five original columns with nine frozen full context orders. */
public final class ContextPositionPlugin implements SutPlugin {
    public static final String ID="demo-d13e-context-position";
    public static final List<String> SHEETS=List.of("HEAD","IMPL","TASK","ORDER","TAIL");
    @Override public String id() { return ID; }
    @Override public String version() { return "18-d13e@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    private static String call(String text,String name) { var m=Pattern.compile(name+"\\(\"([A-Za-z0-9_]+)\"\\);").matcher(text);if(!m.matches())throw new IllegalArgumentException("original "+name+" semicolon atom required");return m.group(1); }
    public static String order(String atom) { return call(atom,"set_order"); }
    public static ContextPosition.Config parseAtoms(String impl,String task,String order) { return new ContextPosition.Config(call(impl,"impl"),call(task,"task"),order(order)); }
    private static String one(SutCandidate c,String sheet) { var s=c.selection(sheet).orElseThrow(() -> new IllegalArgumentException("missing "+sheet));if(!s.inCandidate()||s.values().size()!=1)throw new IllegalArgumentException("one original "+sheet+" atom required");return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx) { ctx.cancellation().throwIfCancelled();try(var owned=ctx.reserve(65536,"D13e twenty local context folds and paired diagnostic readings")){if(owned.bytes()!=65536)throw new IllegalStateException("invalid D13e reservation");if(!candidate.selections().stream().map(s -> s.sheet()).toList().equals(SHEETS))throw new IllegalArgumentException("five original D13e columns required");one(candidate,"HEAD");if(!one(candidate,"TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))throw new IllegalArgumentException("original finish atom required");var result=ContextPosition.evaluate(parseAtoms(one(candidate,"IMPL"),one(candidate,"TASK"),one(candidate,"ORDER")));return new DomainObservation(result.passed()?0:2,Map.of("trials",20.0,"judge_readings",40.0),ContextPosition.attributes(result));}}
        @Override public void close() { }
    }; }
}
