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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Builds actual native ROOT/BUNDLE fragments; original semicolon/LF and builder lifecycle are strict. */
public final class NestedScopesPlugin implements SutPlugin {
    public static final String ID="demo-d5b-nested-scopes";
    public static final List<String> B1=List.of("HEAD","IMPL","ROOT","TAIL"),B2=List.of("HEAD","IMPL","BUNDLE","TAIL");
    @Override public String id() { return ID; }
    @Override public String version() { return "05-d5-B1-B2@0bc3e5a/java-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"PASS",2,"DOMAIN_FAIL"); }
    public static String phase(List<String> sheets) { if(sheets.equals(B1))return "B1";if(sheets.equals(B2))return "B2";throw new IllegalArgumentException("four original structural columns required"); }
    private record Frame(String field,List<NestedScopes.Node> nodes) { }
    public static NestedScopes.Config parseAtoms(String phase,String impl,List<String> fragments) {
        var m=Pattern.compile("impl\\(\"([a-z_]+)\"\\);\n").matcher(impl);if(!m.matches()||!NestedScopes.POLICIES.contains(m.group(1)))throw new IllegalArgumentException("original interpreter policy fragment required");if(fragments.size()>128)throw new IllegalArgumentException("bounded fragment limit128");var stack=new ArrayDeque<Frame>();var trees=new ArrayList<List<NestedScopes.Node>>();var calls=new ArrayList<List<String>>();byte bundle=0;
        for(String atom:fragments){var call=Pattern.compile("([a-z_]+)\\((?:\"([A-Za-z]+)\")?\\);\n").matcher(atom);if(!call.matches())throw new IllegalArgumentException("one original semicolon/LF call required");String name=call.group(1),arg=call.group(2);calls.add(arg==null?List.of(name):List.of(name,arg));if((name.equals("op")||name.equals("open_scope"))!=(arg!=null))throw new IllegalArgumentException("fragment arity mismatch");switch(name){
            case "begin_bundle"->{need(bundle==0&&stack.isEmpty()&&trees.isEmpty());bundle=1;}
            case "seal_bundle"->{need(bundle==1&&stack.isEmpty()&&!trees.isEmpty());bundle=2;}
            case "end_bundle"->{need(bundle==2&&stack.isEmpty());bundle=3;}
            case "begin_pipeline"->{need(stack.isEmpty()&&(bundle==0&&trees.isEmpty()||bundle==1));stack.push(new Frame(null,new ArrayList<>()));}
            case "open_scope"->{need(!stack.isEmpty()&&stack.size()<=64);if(!List.of("x","y").contains(arg))throw new IllegalArgumentException("known scope field required");stack.push(new Frame(arg,new ArrayList<>()));}
            case "op"->{need(!stack.isEmpty());stack.peek().nodes().add(new NestedScopes.Op(arg));}
            case "close_scope"->{need(stack.size()>1);var scope=stack.pop();stack.peek().nodes().add(new NestedScopes.Scope(scope.field(),scope.nodes()));}
            case "end_pipeline"->{need(stack.size()==1);trees.add(NestedScopes.tree(stack.pop().nodes()));}
            case "poison_tmp"->throw new IllegalArgumentException("TMP rewrite marker reached candidate");
            default->throw new IllegalArgumentException("unknown fragment");
        }}need(stack.isEmpty()&&(phase.equals("B1")?bundle==0:phase.equals("B2")&&bundle==3));return new NestedScopes.Config(phase,m.group(1),trees,calls);
    }
    private static void need(boolean condition) { if(!condition)throw new IllegalArgumentException("fragment builder out of order or unclosed"); }
    private static String one(SutCandidate c,String sheet) { var s=c.selection(sheet).orElseThrow();if(!s.inCandidate()||s.values().size()!=1)throw new IllegalArgumentException("one original atom required");return s.values().getFirst(); }
    @Override public SutSession open(ExecutionContext context) { return new SutSession(){
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx) { ctx.cancellation().throwIfCancelled();try(var lease=ctx.reserve(65536,"D5 B immutable nested trees, fresh results and independent scope oracle")){if(lease.bytes()!=65536)throw new IllegalStateException("invalid reservation");String phase=phase(candidate.selections().stream().map(s->s.sheet()).toList());one(candidate,"HEAD");if(!one(candidate,"TAIL").equals("_verdict = finish(\""+phase+"\")\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))throw new IllegalArgumentException("original finish fragment required");var structural=candidate.selection(phase.equals("B1")?"ROOT":"BUNDLE").orElseThrow();if(!structural.inCandidate())throw new IllegalArgumentException("native structural row required");var result=NestedScopes.evaluate(parseAtoms(phase,one(candidate,"IMPL"),structural.values()));return new DomainObservation(result.passed()?0:2,Map.of("trees",(double)result.config().trees().size()),NestedScopes.attributes(result));}}
        @Override public void close() { }
    }; }
}
