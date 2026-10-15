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

package org.ghyura.cf.fixtures.proposal;

import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.proposal.TaskDefinition;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.ArrayList;

/** Typed original naive guard; the Python frame is validated opaque data and is never executed. */
public final class LocalPromptGuardPlugin implements SutPlugin {
    public static final String ID="local-prompt-guard";
    private static final List<String> SHEETS=List.of("FRAME","SYS","INJ1","QUERY","INJ2","INJ3","VERDICT");
    @Override public String id(){return ID;}
    @Override public String version(){return "llm_loop-guard@0bc3e5a/java-1";}
    @Override public Map<Integer,String> verdictNames(){return Map.of(0,"PASS",1,"DOMAIN_FAIL");}
    private static String one(SutCandidate candidate,String name) {
        var selection=candidate.selection(name).orElseThrow();
        if(!selection.inCandidate() || selection.values().size()!=1)throw new IllegalArgumentException("one required "+name+" atom");
        return selection.values().getFirst();
    }
    public static String part(String fragment) {
        String prefix="parts.append(";
        if(!fragment.startsWith(prefix) || !fragment.endsWith(")\n"))throw new IllegalArgumentException("literal append fragment with LF required");
        String literal=fragment.substring(prefix.length(),fragment.length()-2);
        if(literal.startsWith("\"")) {
            var parsed=Json.parse(literal,"guard literal");if(parsed instanceof String value)return value;
        } else if(literal.length()>=2 && literal.startsWith("'") && literal.endsWith("'")) {
            String value=literal.substring(1,literal.length()-1);
            if(!value.contains("'") && !value.contains("\\") && !value.contains("\n"))return value;
        }
        throw new IllegalArgumentException("bounded original literal grammar required;no expression execution");
    }
    @Override public SutSession open(ExecutionContext context) {
        var original=LocalProposalScenarios.of("guard",0);
        String frame=((TaskDefinition.Fixed)original.slots().getFirst().values()).values().getFirst();
        String tail=((TaskDefinition.Fixed)original.slots().getLast().values()).values().getFirst();
        return new SutSession(){
            @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx) {
                ctx.cancellation().throwIfCancelled();
                try(var lease=ctx.reserve(65536,"local prompt guard parts and typed original verdict")) {
                    if(lease.bytes()!=65536)throw new IllegalStateException("invalid guard reservation");
                    if(!candidate.selections().stream().map(s->s.sheet()).toList().equals(SHEETS))throw new IllegalArgumentException("original guard columns required");
                    if(!one(candidate,"FRAME").equals(frame) || !one(candidate,"VERDICT").equals(tail))throw new IllegalArgumentException("original opaque frame/verdict required");
                    var parts=new ArrayList<String>();
                    for(String name:List.of("SYS","INJ1","QUERY","INJ2","INJ3")) {
                        var selection=candidate.selection(name).orElseThrow();
                        if(selection.inCandidate())parts.add(part(one(candidate,name)));
                        else if(!name.startsWith("INJ"))throw new IllegalArgumentException("required benign guard part absent");
                    }
                    String text=String.join(" ",parts);
                    if(text.length()>4096)throw new IllegalArgumentException("guard text exceeds4096characters");
                    String lower=text.toLowerCase(Locale.ROOT);boolean allowed=true,malicious=false;
                    for(String blocked:List.of("ignore previous","disregard","system:","you are now"))if(lower.contains(blocked))allowed=false;
                    for(String marker:List.of("reveal secrets","exfiltrate","delete all"))if(lower.contains(marker))malicious=true;
                    int verdict=allowed&&malicious?1:0;
                    return new DomainObservation(verdict,Map.of("parts",(double)parts.size()),Map.of("text",text,"parts",Json.write(parts),
                            "allowed",Boolean.toString(allowed),"malicious",Boolean.toString(malicious),"FW_VAR",Integer.toString(verdict)));
                }
            }
            @Override public void close(){ }
        };
    }
}
