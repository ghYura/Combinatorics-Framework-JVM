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

package org.ghyura.cf.fixtures.redteam;

import org.ghyura.cf.spi.*;
import org.ghyura.cf.model.Json;
import java.util.*;

/** Cheap source pair scores; oracle/frontier/routing analysis operates on observations. */
public final class AttackDefensePlugin implements SutPlugin {
    public static final String ID="local-attack-defense";
    @Override public String id(){return ID;}
    @Override public String version(){return "attack-defense@0bc3e5a/java-bounded-1";}
    @Override public Map<Integer,String> verdictNames(){return Map.of(0,"PASS",1,"BREACH");}
    @Override public SutSession open(ExecutionContext context){return new SutSession(){
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx){ctx.cancellation().throwIfCancelled();try(var lease=ctx.reserve(256L*1024,"bounded source attack/guard score")){
            if(lease.bytes()!=256L*1024)throw new IllegalStateException("invalid attack reservation");if(!candidate.selections().stream().map(s->s.sheet()).toList().equals(List.of("GUARD","TECHNIQUE","OBFUSCATION","ROLE","LANG","ORDER")))throw new IllegalArgumentException("six original sheets required");
            var values=candidate.selections().stream().map(s->{if(!s.inCandidate())throw new IllegalArgumentException("mandatory source dimension absent");return s.values();}).toList();var c=AttackDefenseMatrix.coordinate(values);var score=AttackDefenseMatrix.score(c);int breach=score.severity()>0?1:0;
            return new DomainObservation(breach,Map.of("severity",(double)score.severity(),"breach",(double)breach,"cost_tokens",(double)score.cost()),Map.of("combo_id",Integer.toString(c.comboId()),"labels",Json.write(c.labels()),"FW_VAR",Integer.toString(breach)));
        }}
        @Override public void close(){ }
    };}
}
