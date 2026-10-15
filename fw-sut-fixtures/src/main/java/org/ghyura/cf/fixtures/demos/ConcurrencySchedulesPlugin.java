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

/** Both original D6 scenarios. HEAD records provenance; embedded foreign fragments are never executed. */
public final class ConcurrencySchedulesPlugin implements SutPlugin {
    public static final String ID="demo-d6-concurrency-schedules";
    @Override public String id() { return ID; }
    @Override public String version() { return "06-d6@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    public static ConcurrencySchedules.Config parseAtoms(String campaign,List<String> atoms) {
        String policy=null; Byte cap=null; var schedule=new StringBuilder();
        for (int i=0;i<atoms.size();i++) {
            String atom=atoms.get(i); var impl=Pattern.compile("impl\\(\"([a-z_]+)\"\\);").matcher(atom);
            if (impl.matches()) { if (i!=0||policy!=null) throw new IllegalArgumentException("impl once, first"); policy=impl.group(1); continue; }
            if (policy==null) throw new IllegalArgumentException("step/cap before impl");
            var bound=Pattern.compile("cap\\(([0-2])\\);").matcher(atom); var step=Pattern.compile("set_step\\(([1-4]),\"([ABPC])\"\\);").matcher(atom);
            if (bound.matches()) { if (cap!=null||!schedule.isEmpty()) throw new IllegalArgumentException("cap once before steps"); cap=Byte.valueOf(bound.group(1)); }
            else if (step.matches()) { if (Integer.parseInt(step.group(1))!=schedule.length()+1) throw new IllegalArgumentException("steps 1..4 in order"); schedule.append(step.group(2)); }
            else throw new IllegalArgumentException("unknown raw D6 atom");
        }
        return new ConcurrencySchedules.Config(campaign,policy,schedule.toString(),cap);
    }
    private static String one(SutCandidate c,String name) { var s=c.selection(name).orElseThrow(() -> new IllegalArgumentException("missing "+name)); if (!s.inCandidate()||s.values().size()!=1) throw new IllegalArgumentException("one "+name+" required"); return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate c,ExecutionContext ctx) {
            ctx.cancellation().throwIfCancelled(); try (var owned=ctx.reserve(65536,"D6 fresh machine, ordered checkpoints and independent reference")) {
                if (owned.bytes()!=65536) throw new IllegalStateException("invalid D6 reservation"); one(c,"HEAD"); String tail=one(c,"TAIL");
                String campaign=tail.equals("_verdict = finish(\"counter\")\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n")?"counter":tail.equals("_verdict = finish(\"queue\")\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n")?"queue":null;
                if (campaign==null) throw new IllegalArgumentException("unknown finish atom");
                var names=campaign.equals("counter")?List.of("HEAD","IMPL","CAP","S1","S2","S3","S4","TAIL"):List.of("HEAD","IMPL","S1","S2","S3","S4","TAIL");
                if (!c.selections().stream().map(s -> s.sheet()).toList().equals(names)) throw new IllegalArgumentException("original D6 sheet order required");
                var atoms=new ArrayList<String>(); for (String name : names) if (!name.equals("HEAD")&&!name.equals("TAIL")) atoms.add(one(c,name));
                var r=ConcurrencySchedules.evaluate(parseAtoms(campaign,atoms)); return new DomainObservation(r.passed()?0:2,Map.of("preemptions",(double)ConcurrencySchedulesOracle.preemptions(r.config().schedule()),"context_switches",(double)ConcurrencySchedulesOracle.contextSwitches(r.config().schedule()),"failing_checkpoints",(double)r.failingCheckpoints().size()),ConcurrencySchedules.attributes(r));
            }
        }
        @Override public void close() { }
    }; }
}
