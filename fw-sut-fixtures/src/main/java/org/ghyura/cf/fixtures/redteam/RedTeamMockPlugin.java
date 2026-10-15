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

import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.DomainObservation;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Native original redteam-v1 matrix with local deterministic victim and independent response oracle. */
public final class RedTeamMockPlugin implements SutPlugin {
    public static final String ID="local-redteam-mock-v1";
    @Override public String id(){return ID;}
    @Override public String version(){return "redteam-v1@0bc3e5a/java-1";}
    @Override public Map<Integer,String> verdictNames(){return Map.of(0,"PASS",1,"BREACH");}
    @Override public SutSession open(ExecutionContext context){
        return new SutSession(){
            @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx){
                ctx.cancellation().throwIfCancelled();try(var lease=ctx.reserve(65536,"local redteam prompt,response,oracle and metrics")){
                    if(lease.bytes()!=65536)throw new IllegalStateException("invalid redteam reservation");
                    var sheets=RedTeamMatrix.slots().stream().map(RedTeamMatrix.Slot::sheet).toList();
                    if(!candidate.selections().stream().map(s->s.sheet()).toList().equals(sheets))throw new IllegalArgumentException("original redteam-v1 columns required");
                    var values=candidate.selections().stream().map(s->{if(!s.inCandidate())throw new IllegalArgumentException("mandatory redteam selection absent");return s.values();}).toList();
                    var coordinate=RedTeamMatrix.coordinate(values);var response=RedTeamMock.generate(coordinate.prompt());
                    var oracle=RedTeamOracle.check(response.text());var metrics=new TreeMap<String,Double>();
                    metrics.put("n_chars",(double)coordinate.prompt().codePointCount(0,coordinate.prompt().length()));
                    metrics.put("cost_tokens",(double)response.costTokens());metrics.put("latency_ms",response.latencyMillis());
                    oracle.fields().forEach((key,value)->metrics.put(key,((Number)value).doubleValue()));
                    var attributes=new TreeMap<String,String>();attributes.put("candidate_id",coordinate.candidateId());attributes.put("combo_id",Integer.toString(coordinate.comboId()));
                    attributes.put("labels",RedTeamMatrix.json(coordinate.labels()));attributes.put("prompt",coordinate.prompt());attributes.put("prompt_sha256",RedTeamMatrix.promptDigest(coordinate.prompt()));
                    attributes.put("response",response.text());attributes.put("oracle",RedTeamMatrix.json(oracle.fields()));attributes.put("outcome",oracle.classification());
                    attributes.put("provider","mock");attributes.put("model",RedTeamMock.MODEL);attributes.put("FW_VAR",Integer.toString(oracle.breach()));
                    return new DomainObservation(oracle.breach(),metrics,attributes);
                }
            }
            @Override public void close(){ }
        };
    }
}
