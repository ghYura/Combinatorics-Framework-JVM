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

package org.ghyura.cf.fixtures.assistant;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.spi.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Shared-run adapter: a candidate owns its request and fresh bounded local memory. */
public final class LocalAssistantPlugin implements SutPlugin {
    public static final String ID="local-cpu-assistant";
    private final boolean structured;
    public LocalAssistantPlugin(){this(false);}
    public LocalAssistantPlugin(boolean structured){this.structured=structured;}
    @Override public String id(){return structured?StructuredContentPlugin.ID:ID;}
    @Override public String version(){return structured?"legacy-app2-app3@8d5aa2d/java-content-1":"legacy-app2-app3@8d5aa2d/java-text-1";}
    @Override public Map<Integer,String> verdictNames(){return Map.of(0,"COMPLETED");}
    @Override public SutSession open(ExecutionContext context){return new SutSession(){
        @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx){
            ctx.cancellation().throwIfCancelled();try(var lease=ctx.reserve(8L*1024*1024,"bounded local assistant request,memory,indexes,arithmetic and completion")){
                if(lease.bytes()!=8L*1024*1024)throw new IllegalStateException("invalid assistant reservation");
                if(candidate.selections().size()!=1||!candidate.selections().getFirst().sheet().equals("CHAT")||!candidate.selections().getFirst().inCandidate()||candidate.selections().getFirst().values().size()!=1)throw new IllegalArgumentException("one mandatory CHAT JSON atom required");
                String source=candidate.selections().getFirst().values().getFirst();if(source.length()>65536||source.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("assistant request exceeds64KiB UTF8");
                Object parsed=structured?Json.parseBinary64(source,"native structured CHAT request"):Json.parse(source,"native CHAT request");if(!(parsed instanceof Map<?,?> request)||!request.keySet().stream().allMatch(k->Set.of("messages","max_tokens","model","documents").contains(k)))throw new IllegalArgumentException("unsupported assistant request fields");
                var clock=Clock.fixed(Instant.EPOCH,ZoneOffset.UTC);var memory=new LocalMemory(clock);
                if(request.containsKey("documents")){var docs=((List<?>)request.get("documents")).stream().map(v->{var d=(Map<?,?>)v;if(!d.keySet().stream().allMatch(k->Set.of("title","text","tags").contains(k)))throw new IllegalArgumentException("unsupported document fields");return new LocalMemory.Input((String)d.get("title"),(String)d.get("text"),d.containsKey("tags")?((List<?>)d.get("tags")).stream().map(String.class::cast).toList():List.of());}).toList();memory.addDocuments(docs);}
                var messages=((List<?>)request.get("messages")).stream().map(v->{var m=(Map<?,?>)v;if(!m.keySet().equals(Set.of("role","content")))throw new IllegalArgumentException("text message role/content required");return new LocalAssistant.Message((String)m.get("role"),structured?LocalContent.text(m.get("content")):(String)m.get("content"));}).toList();
                Integer limit=null;if(request.get("max_tokens")!=null){if(!(request.get("max_tokens")instanceof Long n)||n<1||n>Integer.MAX_VALUE)throw new IllegalArgumentException("positive integral max_tokens required");limit=Math.toIntExact(n);}
                var assistant=new LocalAssistant(memory,v->{if(v.size()>256)throw new IllegalArgumentException("too many features: max is 256");return new LocalAssistant.Prediction(-1,0);},clock,()->"chatcmpl-"+candidate.id().hex().substring(0,12));var c=assistant.complete(new LocalAssistant.Request(messages,(String)request.get("model"),limit));ctx.cancellation().throwIfCancelled();
                var metrics=new TreeMap<String,Double>();metrics.put("prompt_tokens",(double)c.usage().promptTokens());metrics.put("completion_tokens",(double)c.usage().completionTokens());metrics.put("total_tokens",(double)c.usage().totalTokens());metrics.put("source_count",(double)c.sources().size());metrics.put("document_count",(double)memory.count());
                var fields=new TreeMap<String,String>();fields.put("request",structured?source:Json.write(request));fields.put("response",c.text());fields.put("response_json",Json.write(c.fields()));fields.put("sources",Json.write(c.sources().stream().map(LocalMemory.Match::fields).toList()));fields.put("arithmetic_disposition",c.arithmeticDisposition().name());fields.put("model",c.model());fields.put("FW_VAR","0");return new DomainObservation(0,metrics,fields);
            }
        }
        @Override public void close(){ }
    };}
}
