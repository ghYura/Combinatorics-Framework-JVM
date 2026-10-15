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

/** Seven original columns; FAIL is the actual native FW_Subsets composition, not a reduced axis. */
public final class FaultSchedulesPlugin implements SutPlugin {
    public static final String ID="demo-d15-fault-schedules";
    public static final List<String> SHEETS=List.of("HEAD","IMPL","FAIL","CUT","KIND","RETRY","TAIL");
    @Override public String id(){return ID;}
    @Override public String version(){return "22-d15@0bc3e5a/java-1";}
    @Override public Map<Integer,String> verdictNames(){return Map.of(0,"PASS",2,"DOMAIN_FAIL");}
    private static String call(String text,String name){var m=Pattern.compile(name+"\\(\"([a-z_]+)\"\\);").matcher(text);if(!m.matches())throw new IllegalArgumentException("original "+name+" atom required");return m.group(1);}
    private static byte number(String text,String name){var m=Pattern.compile(name+"\\(([0-3])\\);").matcher(text);if(!m.matches())throw new IllegalArgumentException("original "+name+" numeric atom required");return Byte.parseByte(m.group(1));}
    public static FaultSchedules.Config parseAtoms(String impl,List<String> failed,String cut,String kind,String retry){var nodes=new ArrayList<Byte>();for(String atom:failed)nodes.add(number(atom,"fail"));return new FaultSchedules.Config(call(impl,"impl"),nodes,number(cut,"cut"),call(kind,"fault"),call(retry,"retry"));}
    private static String one(SutCandidate c,String sheet){var s=c.selection(sheet).orElseThrow(() -> new IllegalArgumentException("missing "+sheet));if(!s.inCandidate()||s.values().size()!=1)throw new IllegalArgumentException("one original "+sheet+" atom required");return s.values().getFirst();}
    @Override public SutSession open(ExecutionContext context){return new SutSession(){
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx){ctx.cancellation().throwIfCancelled();try(var owned=ctx.reserve(65536,"D15 three replicas/journals/checkpoints/independent observed oracle")){if(owned.bytes()!=65536)throw new IllegalStateException("invalid D15 reservation");if(!candidate.selections().stream().map(s -> s.sheet()).toList().equals(SHEETS))throw new IllegalArgumentException("seven original D15 columns required");one(candidate,"HEAD");if(!one(candidate,"TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))throw new IllegalArgumentException("original finish atom required");var failed=candidate.selection("FAIL").orElseThrow();if(!failed.inCandidate())throw new IllegalArgumentException("FAIL must be composed");var result=FaultSchedules.evaluate(parseAtoms(one(candidate,"IMPL"),failed.values(),one(candidate,"CUT"),one(candidate,"KIND"),one(candidate,"RETRY")));return new DomainObservation(result.passed()?0:2,Map.of("client_attempts",(double)((List<?>)result.document().get("attempts")).size()),FaultSchedules.attributes(result));}}
        @Override public void close(){}
    };}
}
