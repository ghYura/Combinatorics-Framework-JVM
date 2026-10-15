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

/** One complete evolving source simulation; domain statuses remain observed data. */
public final class FinanceEcosystemPlugin implements SutPlugin {
    public static final String ID="finance-stack-ecosystem";
    @Override public String id() { return ID; }
    @Override public String version() { return "finance-stack@8d5aa2d/java-ecosystem-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"COMPLETED"); }
    @Override public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx) {
                ctx.cancellation().throwIfCancelled();
                try(var lease=ctx.reserve(8L*1024*1024,"bounded eight-persona world,thirteen evolving finance phases")) {
                    if(lease.bytes()!=8L*1024*1024)throw new IllegalStateException("invalid ecosystem reservation");
                    if(candidate.selections().size()!=1)throw new IllegalArgumentException("one mandatory ECOSYSTEM selection required");
                    var selected=candidate.selections().getFirst();
                    if(!selected.sheet().equals("ECOSYSTEM")||!selected.inCandidate()||selected.values().size()!=1)
                        throw new IllegalArgumentException("one mandatory ECOSYSTEM JSON atom required");
                    String source=selected.values().getFirst();
                    if(source.length()>65536||source.getBytes(StandardCharsets.UTF_8).length>65536)
                        throw new IllegalArgumentException("ecosystem profile exceeds64KiB");
                    if(!(Json.parse(source,"source ecosystem profile") instanceof Map<?,?> request)
                            ||!request.keySet().equals(Set.of("scenario"))||!"source_simulation".equals(request.get("scenario")))
                        throw new IllegalArgumentException("only source_simulation ecosystem profile supported");
                    var result=FinanceSimulation.run();ctx.cancellation().throwIfCancelled();
                    var attributes=new LinkedHashMap<String,String>();attributes.put("request",Json.write(request));
                    result.forEach((key,value)->attributes.put(key,Json.write(value)));attributes.put("FW_VAR","0");
                    var transfers=(List<?>)result.get("transfers");var metrics=new TreeMap<String,Double>();
                    metrics.put("phases",(double)((List<?>)result.get("phases")).size());metrics.put("transfers",(double)transfers.size());
                    for(String status:List.of("SETTLED","BLOCKED"))metrics.put(status.equals("SETTLED")?"settled_count":"blocked_count",
                        (double)transfers.stream().filter(v->status.equals(((Map<?,?>)v).get("status"))).count());
                    return new DomainObservation(0,metrics,attributes);
                }
            }
            @Override public void close() { }
        };
    }
}
