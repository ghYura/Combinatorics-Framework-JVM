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

/** Original six structural columns; ROOT's eleven values are composed by the shared generator. */
public final class ComposedCarriersPlugin implements SutPlugin {
    public static final String ID="demo-d13b-composed-carriers";
    public static final List<String> SHEETS=List.of("HEAD","IMPL","ENCODING","MARKER","ROOT","TAIL");
    public static final Map<String,String> SETTINGS=Map.of("cf.generation.distinctTables","true","cf.assembly.distinct","true","cf.generation.replace.pattern","warn","cf.generation.replace.unmatched","ignore","cf.generation.replace.unparseable","warn","cf.generation.replace.identity","allow","cf.generation.replace.diagnostics","summary");
    @Override public String id() { return ID; }
    @Override public String version() { return "15-d13b@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    private static String call(String text,String name) { var p=Pattern.compile(name+"\\(\"([a-z0-9_]+)\"\\)\\n").matcher(text); if(!p.matches()) throw new IllegalArgumentException("original "+name+" call/newline required"); return p.group(1); }
    public static ComposedCarriers.Config parseAtoms(String impl,String encoding,String marker,String root) { var parsed=ComposedCarriers.decodeRoot(root); return new ComposedCarriers.Config(call(impl,"impl"),parsed.carrier(),parsed.order(),call(encoding,"encoding"),call(marker,"marker")); }
    private static String one(SutCandidate c,String sheet) { var s=c.selection(sheet).orElseThrow(() -> new IllegalArgumentException("missing "+sheet)); if(!s.inCandidate()||s.values().size()!=1) throw new IllegalArgumentException("one original "+sheet+" atom required"); return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate c,ExecutionContext ctx) {
            ctx.cancellation().throwIfCancelled(); try(var owned=ctx.reserve(65536,"D13b composed context/tree/content/trace, local effects and independent oracle")) {
                if(owned.bytes()!=65536) throw new IllegalStateException("invalid D13b reservation"); if(!c.selections().stream().map(s -> s.sheet()).toList().equals(SHEETS)) throw new IllegalArgumentException("six original D13b structural columns required"); one(c,"HEAD"); if(!one(c,"TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n")) throw new IllegalArgumentException("original D13b finish atom required"); var root=c.selection("ROOT").orElseThrow(); if(!root.inCandidate()||root.values().isEmpty()) throw new IllegalArgumentException("composed ROOT required");
                var result=ComposedCarriers.evaluate(parseAtoms(one(c,"IMPL"),one(c,"ENCODING"),one(c,"MARKER"),String.join("",root.values()))); return new DomainObservation(result.passed()?0:2,Map.of("actions",(double)result.observation().actions().size(),"outbox",(double)result.observation().outbox().size(),"ready",result.observation().ready()?1.0:0.0,"flag",result.observation().flag()?1.0:0.0),ComposedCarriers.attributes(result));
            }
        }
        @Override public void close() { }
    }; }
}
