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

/** Both unchanged D12 layouts; selected raw atoms include their original newline. */
public final class CyclicPatternPlugin implements SutPlugin {
    public static final String ID="demo-d12-cyclic-patterns";
    private static final List<String> WORD_SHEETS=List.of("HEAD","P0","P1","P2","P3","P4","P5","TAIL"), CLASS_SHEETS=List.of("HEAD","PATTERN","TAIL");
    public record Parsed(String campaign,String word) { }
    @Override public String id() { return ID; }
    @Override public String version() { return "13-d12@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    private static String one(SutCandidate c,String name) {
        var s=c.selection(name).orElseThrow(() -> new IllegalArgumentException("missing "+name));
        if (!s.inCandidate()||s.values().size()!=1) throw new IllegalArgumentException("one "+name+" required"); return s.values().getFirst();
    }
    public static Parsed parseAtoms(String campaign,List<String> atoms) {
        if (campaign.equals("words")) {
            if (atoms.size()!=6) throw new IllegalArgumentException("six ordered positions required"); StringBuilder word=new StringBuilder();
            for (int k=0;k<6;k++) {
                char letter=0; for (char c : "ABC".toCharArray()) if (atoms.get(k).equals("place("+k+",\""+c+"\")\n")) letter=c;
                if (letter==0) throw new IllegalArgumentException("invalid or out-of-order position "+k); word.append(letter);
            }
            return new Parsed(campaign,word.toString());
        }
        if (!campaign.equals("classes")||atoms.size()!=1) throw new IllegalArgumentException("one canonical class pattern required");
        var m=java.util.regex.Pattern.compile("pattern\\(\"([ABC]{6})\"\\)\n").matcher(atoms.getFirst());
        if (!m.matches()||!CyclicPatterns.representative(m.group(1)).equals(m.group(1))) throw new IllegalArgumentException("malformed or non-canonical class pattern");
        return new Parsed(campaign,m.group(1));
    }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate c,ExecutionContext ctx) {
            ctx.cancellation().throwIfCancelled(); try (var owned=ctx.reserve(65536,"D12 directed ring, independent reference and full observation")) {
                if (owned.bytes()!=65536) throw new IllegalStateException("invalid D12 reservation");
                var sheets=c.selections().stream().map(s -> s.sheet()).toList(); String campaign;
                if (sheets.equals(WORD_SHEETS)) campaign="words"; else if (sheets.equals(CLASS_SHEETS)) campaign="classes"; else throw new IllegalArgumentException("original D12 sheet order required");
                one(c,"HEAD"); if (!one(c,"TAIL").equals("_verdict = finish(\""+campaign+"\")\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n")) throw new IllegalArgumentException("unknown or conflicting D12 finish atom");
                var atoms=campaign.equals("classes")?List.of(one(c,"PATTERN")):WORD_SHEETS.subList(1,7).stream().map(s -> one(c,s)).toList();
                var parsed=parseAtoms(campaign,atoms); var r=CyclicPatterns.evaluate(parsed.campaign(),parsed.word());
                return new DomainObservation(r.passed()?0:2,Map.of("total_cost",(double)r.observed().core().totalCost()),CyclicPatterns.attributes(r));
            }
        }
        @Override public void close() { }
    }; }
}
