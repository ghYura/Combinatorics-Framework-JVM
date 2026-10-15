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

/** Parses the unchanged D8b raw selection cells, including their original trailing newline. */
public final class DependencyDagsPlugin implements SutPlugin {
    public static final String ID="demo-d8b-dependency-dags";
    private static final List<String> EDGES=List.of("AB","AC","AD","BC","BD","CD");
    private static final List<String> SHEETS=List.of("HEAD","IMPL","EDGE_AB","EDGE_AC","EDGE_AD","EDGE_BC","EDGE_BD","EDGE_CD","EDIT","TAIL");
    public record Parsed(String policy,int graph,int edit){}
    @Override public String id(){return ID;}
    @Override public String version(){return "09-d8b@0bc3e5a/java-1";}
    @Override public Map<Integer,String> verdictNames(){return Map.of(0,"PASS",2,"DOMAIN_FAIL");}
    private static String one(SutCandidate c,String name){var s=c.selection(name).orElseThrow(()->new IllegalArgumentException("missing "+name));if(!s.inCandidate()||s.values().size()!=1)throw new IllegalArgumentException("one "+name+" required");return s.values().getFirst();}
    public static Parsed parseAtoms(String policyAtom,List<String> edgeAtoms,String editAtom){
        String policy=DependencyDags.POLICIES.stream().filter(p->policyAtom.equals("impl(\""+p+"\")\n")).findFirst().orElseThrow(()->new IllegalArgumentException("unknown or malformed D8b policy atom"));
        if(edgeAtoms.size()!=6)throw new IllegalArgumentException("six ordered edge atoms required");int graph=0;
        for(int k=0;k<6;k++){String edge=EDGES.get(k),prefix="edge(\""+edge.charAt(0)+"\",\""+edge.charAt(1)+"\",";String atom=edgeAtoms.get(k);graph<<=1;if(atom.equals(prefix+"1)\n"))graph|=1;else if(!atom.equals(prefix+"0)\n"))throw new IllegalArgumentException("malformed or out-of-order edge "+edge);}
        int edit=-1;for(int v=0;v<4;v++)if(editAtom.equals("edit(\""+"ABCD".charAt(v)+"\")\n"))edit=v;if(edit<0)throw new IllegalArgumentException("unknown or malformed edit atom");return new Parsed(policy,graph,edit);
    }
    @Override public SutSession open(ExecutionContext context){return new SutSession(){
        @Override public DomainObservation evaluate(SutCandidate c,ExecutionContext ctx){
            ctx.cancellation().throwIfCancelled();try(var memory=ctx.reserve(65536,"D8b cache traces and independent recursive reference")){
                if(memory.bytes()!=65536)throw new IllegalStateException("invalid D8b reservation");one(c,"HEAD");
                if(!c.selections().stream().map(s->s.sheet()).toList().equals(SHEETS))throw new IllegalArgumentException("D8b requires the original ten-sheet order");
                if(!one(c,"TAIL").equals("_verdict = finish()\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))throw new IllegalArgumentException("unknown D8b finish atom");
                var parsed=parseAtoms(one(c,"IMPL"),EDGES.stream().map(e->one(c,"EDGE_"+e)).toList(),one(c,"EDIT"));
                var r=DependencyDags.evaluate(parsed.policy(),parsed.graph(),parsed.edit());
                return new DomainObservation(r.passed()?0:2,Map.of("evaluations",(double)r.trace().updates().size(),"mismatched_nodes",(double)r.mismatched().size()),DependencyDags.attributes(r));
            }
        }
        @Override public void close(){}
    };}
}
