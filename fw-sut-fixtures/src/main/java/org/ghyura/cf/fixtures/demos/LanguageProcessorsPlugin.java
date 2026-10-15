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

/** Nine original columns, with declaration order decoded from both actual FW_Permut atoms. */
public final class LanguageProcessorsPlugin implements SutPlugin {
    public static final String ID="demo-d14a-language-processors";
    public static final List<String> SHEETS=List.of("HEAD","IMPL","XVAL","YVAL","SHAPE","OP_A","OP_B","DECL","TAIL");
    @Override public String id() { return ID; }
    @Override public String version() { return "19-d14a@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    private static String call(String text,String name,boolean quoted) { var m=Pattern.compile(Pattern.quote(name)+"\\("+(quoted?"\"([^\"]+)\"":"(-?[0-9]+)")+"\\);").matcher(text);if(!m.matches())throw new IllegalArgumentException("original "+name+" semicolon atom required");return m.group(1); }
    public static String declarationOrder(List<String> atoms) { if(atoms.size()!=2)throw new IllegalArgumentException("two actual declarations required");String order=atoms.stream().map(s -> call(s,"declare",true)).collect(java.util.stream.Collectors.joining()).toUpperCase(java.util.Locale.ROOT);if(!LanguageProcessors.ORDERS.contains(order))throw new IllegalArgumentException("declare x and y exactly once");return order; }
    public static LanguageProcessors.Config parseAtoms(String impl,String x,String y,String shape,String a,String b,List<String> declarations) { return new LanguageProcessors.Config(call(impl,"impl",true),declarationOrder(declarations),call(shape,"shape",true),Byte.parseByte(call(x,"xval",false)),Byte.parseByte(call(y,"yval",false)),call(a,"op_a",true),call(b,"op_b",true)); }
    private static String one(SutCandidate c,String sheet) { var s=c.selection(sheet).orElseThrow(() -> new IllegalArgumentException("missing "+sheet));if(!s.inCandidate()||s.values().size()!=1)throw new IllegalArgumentException("one original "+sheet+" atom required");return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession() {
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx) { ctx.cancellation().throwIfCancelled();try(var owned=ctx.reserve(65536,"D14a two local AST/compiler/VM variants and independent numeric oracle")){if(owned.bytes()!=65536)throw new IllegalStateException("invalid D14a reservation");if(!candidate.selections().stream().map(s -> s.sheet()).toList().equals(SHEETS))throw new IllegalArgumentException("nine original D14a columns required");one(candidate,"HEAD");if(!one(candidate,"TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))throw new IllegalArgumentException("original finish atom required");var declarations=candidate.selection("DECL").orElseThrow();if(!declarations.inCandidate())throw new IllegalArgumentException("DECL must be composed");var result=LanguageProcessors.evaluate(parseAtoms(one(candidate,"IMPL"),one(candidate,"XVAL"),one(candidate,"YVAL"),one(candidate,"SHAPE"),one(candidate,"OP_A"),one(candidate,"OP_B"),declarations.values()));return new DomainObservation(result.passed()?0:2,Map.of("program_variants",2.0,"vm_executions",2.0,"reference_evaluations",2.0),LanguageProcessors.attributes(result));}}
        @Override public void close() { }
    }; }
}
