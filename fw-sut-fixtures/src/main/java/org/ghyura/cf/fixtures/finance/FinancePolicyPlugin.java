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

package org.ghyura.cf.fixtures.finance;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.spi.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Native shared-run finance policy scripts. Completion is the verdict; gate decisions are observed data. */
public final class FinancePolicyPlugin implements SutPlugin {
 public static final String ID="finance-stack-policy";
 @Override public String id(){return ID;}@Override public String version(){return "finance-stack@8d5aa2d/java-policy-1";}@Override public Map<Integer,String> verdictNames(){return Map.of(0,"COMPLETED");}
 @Override public SutSession open(ExecutionContext context){return new SutSession(){@Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx){ctx.cancellation().throwIfCancelled();try(var lease=ctx.reserve(8L*1024*1024,"bounded finance policy script,client directory,attempts and audit")){if(lease.bytes()!=8L*1024*1024)throw new IllegalStateException("invalid policy reservation");if(candidate.selections().size()!=1)throw new IllegalArgumentException("one mandatory POLICY selection required");var selected=candidate.selections().getFirst();if(!selected.sheet().equals("POLICY")||!selected.inCandidate()||selected.values().size()!=1)throw new IllegalArgumentException("one mandatory POLICY JSON atom required");String source=selected.values().getFirst();if(source.length()>65536||source.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("policy request exceeds64KiB");if(!(Json.parse(source,"finance policy request")instanceof Map<?,?> request))throw new IllegalArgumentException("object policy request required");var result=FinancePolicyScenario.run(request);ctx.cancellation().throwIfCancelled();var rows=(List<?>)result.get("results");var last=(Map<?,?>)rows.getLast();var metadata=(Map<?,?>)last.get("metadata");var restrictions=(Map<?,?>)result.get("restrictions");var metrics=new TreeMap<String,Double>();for(String name:List.of("ALLOW","REVIEW","BLOCK"))metrics.put(name.toLowerCase(Locale.ROOT)+"_count",(double)rows.stream().filter(v->name.equals(((Map<?,?>)v).get("decision"))).count());metrics.put("evaluations",(double)rows.size());metrics.put("event_count",(double)((List<?>)result.get("events")).size());metrics.put("window_count",((Number)restrictions.get("window_count")).doubleValue());metrics.put("window_sum",Double.valueOf((String)restrictions.get("window_sum")));metrics.put("score",((Number)metadata.get("score")).doubleValue());var fields=new TreeMap<String,String>();fields.put("request",Json.write(request));for(String k:List.of("results","restrictions","clients","events"))fields.put(k,Json.write(result.get(k)));fields.put("FW_VAR","0");return new DomainObservation(0,metrics,fields);}}@Override public void close(){}};}
}
