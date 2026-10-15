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
/** Shared-run adapter for bounded source ledger operation scripts, including recorded service errors. */
public final class FinanceLedgerPlugin implements SutPlugin {
 public static final String ID="finance-stack-ledger";
 @Override public String id(){return ID;}@Override public String version(){return "finance-stack@8d5aa2d/java-ledger-1";}@Override public Map<Integer,String> verdictNames(){return Map.of(0,"COMPLETED");}
 @Override public SutSession open(ExecutionContext context){return new SutSession(){@Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx){ctx.cancellation().throwIfCancelled();try(var lease=ctx.reserve(8L*1024*1024,"bounded source ledger/accounts/cards/journal/audit operation script")){if(lease.bytes()!=8L*1024*1024)throw new IllegalStateException("invalid ledger reservation");if(candidate.selections().size()!=1)throw new IllegalArgumentException("one mandatory LEDGER selection required");var selected=candidate.selections().getFirst();if(!selected.sheet().equals("LEDGER")||!selected.inCandidate()||selected.values().size()!=1)throw new IllegalArgumentException("one mandatory LEDGER JSON atom required");String source=selected.values().getFirst();if(source.length()>65536||source.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("ledger request exceeds64KiB");if(!(Json.parse(source,"finance ledger request")instanceof Map<?,?> request))throw new IllegalArgumentException("object ledger request required");var result=FinanceLedgerScenario.run(request);ctx.cancellation().throwIfCancelled();var rows=(List<?>)result.get("results");var metrics=new TreeMap<String,Double>();metrics.put("operations",(double)rows.size());metrics.put("error_count",(double)rows.stream().filter(v->((Map<?,?>)v).containsKey("error")).count());for(String k:List.of("accounts","cards","entries","events"))metrics.put(switch(k){case "accounts"->"account_count";case "cards"->"card_count";case "entries"->"entry_count";case "events"->"event_count";default->throw new IllegalArgumentException();},(double)((List<?>)result.get(k)).size());for(String k:List.of("debit_totals","credit_totals"))metrics.put(k.equals("debit_totals")?"debit_total":"credit_total",((Map<?,?>)result.get(k)).values().stream().map(Number.class::cast).mapToDouble(Number::doubleValue).sum());var fields=new TreeMap<String,String>();fields.put("request",Json.write(request));for(String k:List.of("results","accounts","cards","card_tokens","entries","debit_totals","credit_totals","events"))fields.put(k,Json.write(result.get(k)));fields.put("FW_VAR","0");return new DomainObservation(0,metrics,fields);}}@Override public void close(){}};}
}
