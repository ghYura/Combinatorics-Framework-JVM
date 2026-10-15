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

/** The source on-hold recovery and node controls on two in-JVM nodes; peer states and source errors are observations. */
public final class FinancePeeringPlugin implements SutPlugin {
    public static final String ID="finance-stack-peering";
    @Override public String id() { return ID; }
    @Override public String version() { return "finance-stack@8d5aa2d/java-peering-1"; }
    @Override public Map<Integer,String> verdictNames() { return Map.of(0,"COMPLETED"); }
    @Override public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx) {
                ctx.cancellation().throwIfCancelled();
                try(var lease=ctx.reserve(8L*1024*1024,"bounded in-JVM nodes, registry and time files")) {
                    if(lease.bytes()!=8L*1024*1024)throw new IllegalStateException("invalid peering reservation");
                    if(candidate.selections().size()!=1)throw new IllegalArgumentException("one mandatory PEERING selection required");
                    var selected=candidate.selections().getFirst();
                    if(!selected.sheet().equals("PEERING")||!selected.inCandidate()||selected.values().size()!=1)
                        throw new IllegalArgumentException("one mandatory PEERING JSON atom required");
                    String source=selected.values().getFirst();
                    if(source.length()>65536||source.getBytes(StandardCharsets.UTF_8).length>65536)
                        throw new IllegalArgumentException("peering profile exceeds64KiB");
                    if(!(Json.parse(source,"source peering profile") instanceof Map<?,?> request)
                            ||!request.keySet().equals(Set.of("scenario"))||!"source_multinode".equals(request.get("scenario")))
                        throw new IllegalArgumentException("only source_multinode peering profile supported");
                    var result=FinancePeeringScenario.run(request);ctx.cancellation().throwIfCancelled();
                    var attributes=new LinkedHashMap<String,String>();attributes.put("request",Json.write(request));
                    result.forEach((key,value)->attributes.put(key,Json.write(value)));attributes.put("FW_VAR","0");
                    var metrics=new TreeMap<String,Double>();
                    for(var key:Map.of("recovery","recovery_steps","proxy","proxy_cases","propagation","propagation_cases","inbound","inbound_cases").entrySet())metrics.put(key.getValue(),(double)((List<?>)result.get(key.getKey())).size());
                    return new DomainObservation(0,metrics,attributes);
                }
            }
            @Override public void close() { }
        };
    }
}
