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

/** Original twelve-sheet D13d scenario. Embedded foreign fragments are provenance, never executed. */
public final class SessionMemoryPlugin implements SutPlugin {
    public static final String ID="demo-d13d-session-memory";
    public static final List<String> SHEETS=List.of("HEAD","IMPL","WRITE_AT","U1","U2","U3","S1","S2","S3","R12","R23","TAIL");
    @Override public String id() { return ID; }
    @Override public String version() { return "17-d13d@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    public static SessionMemory.Config parseAtoms(List<String> atoms) {
        if (atoms.size()!=10) throw new IllegalArgumentException("ten ordered D13d atoms required");
        var impl=Pattern.compile("impl\\(\"([a-z_]+)\"\\);").matcher(atoms.get(0)); var write=Pattern.compile("write_at\\(([12])\\);").matcher(atoms.get(1)); if (!impl.matches()||!write.matches()) throw new IllegalArgumentException("impl then write_at required");
        var users=new StringBuilder(); var sessions=new StringBuilder(); var cuts=new StringBuilder();
        for (int i=1;i<=3;i++) {
            var u=Pattern.compile("user_at\\("+i+",([0-2])\\);").matcher(atoms.get(i+1)); var s=Pattern.compile("session_at\\("+i+",([0-2])\\);").matcher(atoms.get(i+4)); if (!u.matches()||!s.matches()) throw new IllegalArgumentException("users1..3 then sessions1..3 required"); users.append(u.group(1)); sessions.append(s.group(1));
        }
        for (int i=1;i<=2;i++) { var cut=Pattern.compile("reset_cut\\("+i+",([01])\\);").matcher(atoms.get(i+7)); if (!cut.matches()) throw new IllegalArgumentException("reset cuts1..2 required"); cuts.append(cut.group(1)); }
        return new SessionMemory.Config(impl.group(1),users.toString(),sessions.toString(),cuts.toString(),Byte.parseByte(write.group(1)));
    }
    private static String one(SutCandidate c,String name) { var s=c.selection(name).orElseThrow(() -> new IllegalArgumentException("missing "+name)); if (!s.inCandidate()||s.values().size()!=1) throw new IllegalArgumentException("one "+name+" required"); return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate c,ExecutionContext ctx) {
            ctx.cancellation().throwIfCancelled(); try (var owned=ctx.reserve(65536,"D13d fresh memory, three-request trace, independent scope/epoch/response/tool checks")) {
                if (owned.bytes()!=65536) throw new IllegalStateException("invalid D13d reservation");
                if (!c.selections().stream().map(s -> s.sheet()).toList().equals(SHEETS)) throw new IllegalArgumentException("original D13d sheet order required"); one(c,"HEAD");
                if (!one(c,"TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n")) throw new IllegalArgumentException("unknown finish atom"); var atoms=new ArrayList<String>(); for (String name : SHEETS) if (!name.equals("HEAD")&&!name.equals("TAIL")) atoms.add(one(c,name));
                var r=SessionMemory.evaluate(parseAtoms(atoms)); return new DomainObservation(r.passed()?0:2,Map.of("leak_checkpoints",(double)r.judgment().leaks().size(),"leak_occurrences",(double)r.judgment().occurrences().size()),SessionMemory.attributes(r));
            }
        }
        @Override public void close() { }
    }; }
}
