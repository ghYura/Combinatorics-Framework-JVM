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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Original native96attack space, two observed local versions and separate measured-time response oracles. */
public final class RedTeamRegressionPlugin implements SutPlugin {
    public static final String ID="local-redteam-regression-v2";
    @Override public String id(){return ID;}
    @Override public String version(){return "redteam2@0bc3e5a/java-bounded-1/"+RedTeamRegression.ledgerDigest();}
    @Override public Map<Integer,String> verdictNames(){return Map.of(0,"PASS",1,"BREACH");}
    @Override public SutSession open(ExecutionContext context){return new SutSession(){
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx){
            ctx.cancellation().throwIfCancelled();try(var lease=ctx.reserve(8L*1024*1024,"redteam2 ledger replay,memory retrieval,two bounded local victims and observed version records")){
                if(lease.bytes()!=8L*1024*1024)throw new IllegalStateException("invalid regression reservation");
                if(!candidate.selections().stream().map(s->s.sheet()).toList().equals(List.of("FAMILY","VARIANT","OBFUSCATION","LOCALE")))throw new IllegalArgumentException("original four redteam2 sheets required");
                var values=candidate.selections().stream().map(s->{if(!s.inCandidate())throw new IllegalArgumentException("mandatory redteam2 selection absent");return s.values();}).toList();var coordinate=RedTeamRegression.coordinate(values);var records=new ArrayList<Map<String,Object>>();var metrics=new TreeMap<String,Double>();int aggregate=0,severity=0;double slowest=0;
                for(String version:List.of("app2","app3")){
                    ctx.cancellation().throwIfCancelled();var observed=RedTeamRegression.evaluate(coordinate,version);var attack=observed.attack();var completion=observed.completion();var verdict=observed.verdict();int breach=verdict.breach()?1:0;aggregate|=breach;severity=Math.max(severity,verdict.severity());double latency=new BigDecimal(observed.elapsedMillis()).setScale(1,RoundingMode.HALF_EVEN).doubleValue();slowest=Math.max(slowest,latency);
                    metrics.put(version+".breach",(double)breach);metrics.put(version+".severity",(double)verdict.severity());metrics.put(version+".latency_ms",latency);
                    var row=new TreeMap<String,Object>();row.put("version",version);row.put("uid",attack.uid());row.put("query",attack.query());row.put("system",attack.system());row.put("response",completion.text());row.put("sources",completion.sources().stream().map(s->s.fields()).toList());row.put("usage",completion.usage().fields());row.put("document_count",observed.documentCount());row.put("arithmetic_disposition",completion.arithmeticDisposition().name());row.put("breach",breach);row.put("severity",verdict.severity());row.put("FW_VAR",breach);row.put("elapsed_ms_raw",observed.elapsedMillis());row.put("latency_ms",latency);row.put("timed_out",false);records.add(Collections.unmodifiableMap(row));
                }
                metrics.put("breach",(double)aggregate);metrics.put("severity",(double)severity);metrics.put("latency_ms",slowest);
                return new DomainObservation(aggregate,metrics,new TreeMap<>(Map.of("combo_id",Integer.toString(coordinate.comboId()),"labels",Json.write(coordinate.labels()),"versions",Json.write(records),"uid_ledger_sha256",RedTeamRegression.ledgerDigest(),"FW_VAR",Integer.toString(aggregate),"timing_scope","observed local completion; original HTTP/CPU-DoS latency not equivalent")));
            }
        }
        @Override public void close(){ }
    };}
}
