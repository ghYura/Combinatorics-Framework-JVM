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
/** Native original sanctions gate; registry/settlement APIs remain reusable in the same addon. */
public final class FinanceNetworkPlugin implements SutPlugin {
 public static final String ID="finance-stack-sanctions";
 @Override public String id(){return ID;}@Override public String version(){return "finance-stack@8d5aa2d/java-network-1";}@Override public Map<Integer,String> verdictNames(){return Map.of(0,"COMPLETED");}
 @Override public SutSession open(ExecutionContext context){return new SutSession(){@Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx){ctx.cancellation().throwIfCancelled();try(var lease=ctx.reserve(8L*1024*1024,"bounded sanctions profile,client directory,registry and gate result")){if(lease.bytes()!=8L*1024*1024)throw new IllegalStateException("invalid sanctions reservation");if(candidate.selections().size()!=1)throw new IllegalArgumentException("one mandatory SANCTIONS selection required");var selected=candidate.selections().getFirst();if(!selected.sheet().equals("SANCTIONS")||!selected.inCandidate()||selected.values().size()!=1)throw new IllegalArgumentException("one mandatory SANCTIONS JSON atom required");String source=selected.values().getFirst();if(source.length()>65536||source.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("sanctions request exceeds64KiB");if(!(Json.parse(source,"source sanctions request")instanceof Map<?,?> request))throw new IllegalArgumentException("object sanctions profile required");var result=FinanceSanctionsScenario.run(request);ctx.cancellation().throwIfCancelled();var metrics=new TreeMap<String,Double>();metrics.put("reason_count",(double)((List<?>)result.get("reasons")).size());metrics.put("is_blocked",result.get("decision").equals("BLOCK")?1.:0.);return new DomainObservation(0,metrics,Map.of("request",Json.write(request),"gate_result",Json.write(result),"FW_VAR","0"));}}@Override public void close(){}};}
}
