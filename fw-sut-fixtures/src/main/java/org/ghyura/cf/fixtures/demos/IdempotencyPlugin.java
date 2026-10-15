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

/** Exact original D1 assignment/list atoms; embedded foreign HEAD is provenance only. */
public final class IdempotencyPlugin implements SutPlugin {
    public static final String ID="demo-d1-idempotency";
    public static final List<String> SHEETS=List.of("HEAD","IMPL","L1","L2","L3","CUT1","CUT2","CONTROL","TAIL");
    @Override public String id() { return ID; }
    @Override public String version() { return "01-d1@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    public static Idempotency.Config parseAtoms(List<String> atoms) {
        if (atoms.size()!=7||!atoms.get(1).equals("L = [0]")) throw new IllegalArgumentException("seven ordered D1 assignment/list atoms required");
        var impl=Pattern.compile("IMPL = \"([a-z_]+)\"").matcher(atoms.get(0)); var l2=Pattern.compile("L\\.append\\(([01])\\)").matcher(atoms.get(2)); var l3=Pattern.compile("L\\.append\\(([012])\\)").matcher(atoms.get(3)); var c1=Pattern.compile("CUT1 = ([01])").matcher(atoms.get(4)); var c2=Pattern.compile("CUT2 = ([01])").matcher(atoms.get(5)); var control=Pattern.compile("CONTROL = \"([a-z_]+)\"").matcher(atoms.get(6));
        if (!impl.matches()||!l2.matches()||!l3.matches()||!c1.matches()||!c2.matches()||!control.matches()) throw new IllegalArgumentException("original D1 IMPL/L1/L2/L3/CUT1/CUT2/CONTROL order required");
        return new Idempotency.Config(impl.group(1),"0"+l2.group(1)+l3.group(1),c1.group(1)+c2.group(1),control.group(1));
    }
    private static String one(SutCandidate c,String name) { var s=c.selection(name).orElseThrow(() -> new IllegalArgumentException("missing "+name)); if (!s.inCandidate()||s.values().size()!=1) throw new IllegalArgumentException("one "+name+" required"); return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate c,ExecutionContext ctx) {
            ctx.cancellation().throwIfCancelled(); try (var owned=ctx.reserve(65536,"D1 fresh service/durable state, deliveries/ledgers and independent operation oracle")) {
                if (owned.bytes()!=65536) throw new IllegalStateException("invalid D1 reservation"); if (!c.selections().stream().map(s -> s.sheet()).toList().equals(SHEETS)) throw new IllegalArgumentException("original D1 sheet order required"); one(c,"HEAD");
                if (!one(c,"TAIL").equals("_verdict = d1.emit(IMPL, L, [CUT1, CUT2], CONTROL)\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n")) throw new IllegalArgumentException("unknown D1 finish atom"); var atoms=new ArrayList<String>(); for (String name : SHEETS) if (!name.equals("HEAD")&&!name.equals("TAIL")) atoms.add(one(c,name)); var result=Idempotency.evaluate(parseAtoms(atoms));
                return new DomainObservation(result.passed()?0:2,Map.of("core_effects",(double)result.observation().prePeerLedger().size(),"peer_effect_delta",result.observation().peer()==null?0.0:(double)result.observation().peer().effectDelta()),Idempotency.attributes(result));
            }
        }
        @Override public void close() { }
    }; }
}
