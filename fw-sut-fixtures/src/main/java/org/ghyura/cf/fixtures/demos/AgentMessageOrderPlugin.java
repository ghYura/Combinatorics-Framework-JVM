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

/** Nine unchanged original structural atoms; shared constraints filter the 288 raw schedules. */
public final class AgentMessageOrderPlugin implements SutPlugin {
    public static final String ID="demo-d13c-agent-message-order";
    public static final List<String> SHEETS=List.of("HEAD","IMPL","MODE","CAP","S1","S2","S3","S4","TAIL");
    @Override public String id() { return ID; }
    @Override public String version() { return "16-d13c@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    private static String call(String text,String name) { var m=Pattern.compile(name+"\\(\"([a-zA-Z_]+)\"\\);").matcher(text);if(!m.matches())throw new IllegalArgumentException("original "+name+" semicolon atom required");return m.group(1); }
    public static AgentMessageOrder.Config parseAtoms(Map<String,String> atoms) { var m=Pattern.compile("cap\\(([012])\\);").matcher(atoms.get("CAP"));if(!m.matches())throw new IllegalArgumentException("original cap atom required");StringBuilder word=new StringBuilder();for(int i=1;i<=4;i++){var step=Pattern.compile("set_step\\("+i+",\"([AB])\"\\);").matcher(atoms.get("S"+i));if(!step.matches())throw new IllegalArgumentException("ordered original step required");word.append(step.group(1));}return new AgentMessageOrder.Config(call(atoms.get("IMPL"),"impl"),call(atoms.get("MODE"),"mode"),Byte.parseByte(m.group(1)),word.toString()); }
    private static String one(SutCandidate c,String sheet) { var s=c.selection(sheet).orElseThrow(() -> new IllegalArgumentException("missing "+sheet));if(!s.inCandidate()||s.values().size()!=1)throw new IllegalArgumentException("one original "+sheet+" atom required");return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx) { ctx.cancellation().throwIfCancelled();try(var owned=ctx.reserve(65536,"D13c local coordinator/agent trace and independent reference/ledger")){if(owned.bytes()!=65536)throw new IllegalStateException("invalid D13c reservation");if(!candidate.selections().stream().map(s -> s.sheet()).toList().equals(SHEETS))throw new IllegalArgumentException("nine original D13c sheets required");var atoms=new java.util.TreeMap<String,String>();for(String sheet:SHEETS)atoms.put(sheet,one(candidate,sheet));if(!atoms.get("TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))throw new IllegalArgumentException("original finish atom required");var result=AgentMessageOrder.evaluate(parseAtoms(atoms));return new DomainObservation(result.passed()?0:2,Map.of("preemptions",(double)AgentMessageOrder.preemptions(result.config().schedule()),"context_switches",(double)AgentMessageOrder.contextSwitches(result.config().schedule())),AgentMessageOrder.attributes(result));}}
        @Override public void close() { }
    }; }
}
