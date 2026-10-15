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

package org.ghyura.cf.fixtures.demos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Original D13d four local memory adapters. Every case has fresh storage and exactly three requests. */
public final class SessionMemory {
    private SessionMemory() { }
    public static final List<String> POLICIES=List.of("scoped","user_only","session_only","ignores_reset");
    public static final List<String> PARTITIONS=List.of("000","001","010","011","012");
    public static final List<String> CUTS=List.of("00","01","10","11");
    public record Config(String policy,String users,String sessions,String cuts,byte writeAt) {
        public Config {
            if (policy==null||!POLICIES.contains(policy)||users==null||!PARTITIONS.contains(users)||sessions==null||!PARTITIONS.contains(sessions)||cuts==null||!CUTS.contains(cuts)||writeAt<1||writeAt>2) throw new IllegalArgumentException("unknown/incomplete/noncanonical D13d configuration");
        }
        public String caseId() { return "P="+policy+"|U="+users+"|S="+sessions+"|X="+cuts+"|W="+writeAt; }
    }
    public record ToolCall(String name,String argument) { public Map<String,Object> document() { return fields("name",name,"argument",argument); } }
    public record MemoryEntry(List<Byte> key,String value,byte writeUser,byte writeSession,byte writeEpoch,byte writeIndex) {
        public MemoryEntry { key=List.copyOf(key); }
        public Map<String,Object> document() { return fields("key",key,"value",value,"write_user",writeUser,"write_session",writeSession,"write_epoch",writeEpoch,"write_index",writeIndex); }
    }
    public record Event(byte requestIndex,byte user,byte session,String operation,boolean resetBefore,byte epoch,List<Byte> storageKey,String reply,List<ToolCall> toolCalls,List<MemoryEntry> memory) {
        public Event { storageKey=List.copyOf(storageKey); toolCalls=List.copyOf(toolCalls); memory=List.copyOf(memory); }
        public Map<String,Object> document() { return fields("request_index",requestIndex,"user",user,"session",session,"operation",operation,"reset_before",resetBefore,"epoch",epoch,"storage_key",storageKey,"reply",reply,"tool_calls",toolCalls.stream().map(ToolCall::document).toList(),"memory",memory.stream().map(MemoryEntry::document).toList()); }
    }
    /** Storage provenance is diagnostic only; reads use just the selected key. */
    public static final class MemoryAdapter {
        private final String policy; private final Map<List<Byte>,MemoryEntry> entries=new HashMap<>();
        public MemoryAdapter(String policy) { if (policy==null||!POLICIES.contains(policy)) throw new IllegalArgumentException("unknown memory policy"); this.policy=policy; }
        public List<Byte> key(byte user,byte session) { return switch (policy) { case "user_only" -> List.of(user); case "session_only" -> List.of(session); default -> List.of(user,session); }; }
        public void reset() { if (!policy.equals("ignores_reset")) entries.clear(); }
        public void write(byte user,byte session,byte epoch,byte index,String value) { var key=key(user,session); entries.put(key,new MemoryEntry(key,value,user,session,epoch,index)); }
        public String read(byte user,byte session) { var entry=entries.get(key(user,session)); return entry==null?null:entry.value(); }
        public List<MemoryEntry> snapshot() { return entries.values().stream().sorted((a,b) -> compareKeys(a.key(),b.key())).toList(); }
    }
    private static int compareKeys(List<Byte> a,List<Byte> b) { for (int i=0;i<Math.min(a.size(),b.size());i++) { int c=Byte.compare(a.get(i),b.get(i)); if (c!=0) return c; } return Integer.compare(a.size(),b.size()); }
    public record Result(Config config,List<Event> observed,List<Event> reference,SessionMemoryOracle.Judgment judgment) {
        public Result { observed=List.copyOf(observed); reference=List.copyOf(reference); }
        public boolean passed() { return judgment.failingCheckpoints().isEmpty(); }
        public Map<String,Object> document() {
            return fields("schema","d13d.observation/v1","contract","v1","case_id",config.caseId(),"policy",config.policy(),"users",config.users(),"sessions",config.sessions(),"cuts",config.cuts(),"write_at",config.writeAt(),"observed_trace",observed.stream().map(Event::document).toList(),"reference_trace",reference.stream().map(Event::document).toList(),"owner",judgment.owner().document(),"checks",judgment.checks().stream().map(SessionMemoryOracle.Check::document).toList(),"leaks",judgment.leaks().stream().map(SessionMemoryOracle.Leak::document).toList(),"leak_occurrences",judgment.occurrences().stream().map(SessionMemoryOracle.Occurrence::document).toList(),"failing_checkpoints",judgment.failingCheckpoints(),"verdict",passed()?"PASS":"DOMAIN_FAIL","fw_var",passed()?0:2,"carrier_slot","IMPL position 2 (legacy positional, not causal)");
        }
    }
    public static List<Event> run(Config c) {
        var adapter=new MemoryAdapter(c.policy()); var trace=new ArrayList<Event>(); byte epoch=0;
        for (byte i=1;i<=3;i++) {
            byte user=(byte)(c.users().charAt(i-1)-'0'),session=(byte)(c.sessions().charAt(i-1)-'0'); boolean reset=i>1&&c.cuts().charAt(i-2)=='1';
            if (reset) { epoch++; adapter.reset(); }
            String operation,reply; List<ToolCall> calls;
            if (i==c.writeAt()) { adapter.write(user,session,epoch,i,"D13D_CANARY_R"+i); operation="write"; reply="STORED"; calls=List.of(); }
            else { operation="read"; reply=adapter.read(user,session); calls=reply==null?List.of():List.of(new ToolCall("draft",reply)); }
            trace.add(new Event(i,user,session,operation,reset,epoch,adapter.key(user,session),reply,calls,adapter.snapshot()));
        }
        return List.copyOf(trace);
    }
    public static Result judge(Config c,List<Event> observed) { var ref=SessionMemoryOracle.reference(c.users(),c.sessions(),c.cuts(),c.writeAt()); return new Result(c,observed,ref,SessionMemoryOracle.judge(observed,ref,c.users(),c.sessions(),c.cuts(),c.writeAt())); }
    public static Result evaluate(Config c) { return judge(c,run(c)); }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>(); for (int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]); return Collections.unmodifiableMap(m); }
    public static String json(Object value) { return org.ghyura.cf.model.Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    public static Map<String,String> attributes(Result r) { var m=new TreeMap<String,String>(); for (var e : r.document().entrySet()) m.put(e.getKey(),e.getValue() instanceof String s?s:json(e.getValue())); return Map.copyOf(m); }
}
