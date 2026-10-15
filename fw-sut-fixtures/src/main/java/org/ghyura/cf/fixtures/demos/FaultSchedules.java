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

import org.ghyura.cf.model.Json;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Three local replicas with separately retained journals; no process, filesystem or OS durability claim. */
public final class FaultSchedules {
    private FaultSchedules() { }
    public static final List<String> POLICIES=List.of("durable","volatile_ack","replay_twice");
    public static final List<String> OPS=List.of("o1","o2","o3");
    public record Config(String policy,List<Byte> failed,byte cut,String kind,String retry) {
        public Config {
            failed=List.copyOf(failed);
            if(!POLICIES.contains(policy)||failed.isEmpty()||failed.size()>3||new TreeSet<>(failed).size()!=failed.size()||failed.stream().anyMatch(n -> n<0||n>2)||cut<0||cut>3||!List.of("crash","partition").contains(kind)||!List.of("stable","fresh").contains(retry))throw new IllegalArgumentException("invalid D15 coordinate");
        }
        public List<Byte> canonicalFailed() { return failed.stream().sorted().toList(); }
        public String caseId() { return "P="+policy+"|F="+canonicalFailed().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining())+"|C="+cut+"|K="+kind+"|R="+retry; }
        public boolean forbidden() { return kind.equals("partition")&&failed.size()==3; }
    }
    public record Record(String key,String op,int delta) {
        public Record { if(key==null||!key.matches("o[123]:a[12]")||!key.substring(0,2).equals(op)||delta!=switch(op){case "o1" -> 1;case "o2" -> 10;case "o3" -> 100;default -> throw new IllegalArgumentException("unknown operation");})throw new IllegalArgumentException("inconsistent journal record"); }
        public Map<String,Object> document() { return fields("key",key,"op",op,"delta",delta); }
    }
    public static Record record(String key) { if(key==null||!key.matches("o[123]:a[12]"))throw new IllegalArgumentException("invalid record key");String op=key.substring(0,2);return new Record(key,op,switch(op){case "o1" -> 1;case "o2" -> 10;default -> 100;}); }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> value) { return (Map<String,Object>)ToolCallOrder.freezeDocument(value); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    /** Exact original compact sorted-key ASCII representation for journal and semantic digests. */
    public static String canonical(Object value) {
        if(value==null)return "null";
        if(value instanceof String s)return Json.write(s).stripTrailing();
        if(value instanceof Boolean||value instanceof Byte||value instanceof Integer||value instanceof Long)return value.toString();
        if(value instanceof List<?> list)return "["+list.stream().map(FaultSchedules::canonical).collect(java.util.stream.Collectors.joining(","))+"]";
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((k,v) -> {if(!(k instanceof String s))throw new IllegalArgumentException("string key required");sorted.put(s,v);});return "{"+sorted.entrySet().stream().map(e -> canonical(e.getKey())+":"+canonical(e.getValue())).collect(java.util.stream.Collectors.joining(","))+"}";}
        throw new IllegalArgumentException("strict canonical journal value required");
    }
    public static String digest(String text) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.US_ASCII)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
    public static final class Journal {
        private final List<Record> records=new ArrayList<>();
        public Journal() { }
        public Journal(List<Record> initial) { records.addAll(List.copyOf(initial)); }
        private void append(List<Record> added) { records.addAll(added); }
        public List<Record> records() { return List.copyOf(records); }
        public Map<String,Object> evidence() { var lines=records.stream().map(r -> canonical(r.document())).toList();return fields("lines",lines,"sha256",digest(lines.stream().map(l -> l+"\n").collect(java.util.stream.Collectors.joining()))); }
    }
    /** Worker semantics know only policy, received records and retained journal, never a schedule or oracle. */
    public static final class Replica {
        private final String policy;private final Journal journal;
        private final TreeMap<String,Record> accepted=new TreeMap<>();private final List<Record> effects=new ArrayList<>(),pending=new ArrayList<>();
        public Replica(String policy,Journal journal) {
            if(!POLICIES.contains(policy))throw new IllegalArgumentException("unknown replica policy");this.policy=policy;this.journal=journal;
            for(var r:journal.records()){var old=accepted.putIfAbsent(r.key(),r);if(old!=null&&!old.equals(r))throw new IllegalArgumentException("conflicting journal payloads");}
            for(int pass=0;pass<(policy.equals("replay_twice")?2:1);pass++)effects.addAll(journal.records());
        }
        public boolean put(Record record) {
            var old=accepted.get(record.key());if(old!=null){if(!old.equals(record))throw new IllegalArgumentException("conflicting accepted payload");return true;}
            if(!policy.equals("volatile_ack"))journal.append(List.of(record));
            accepted.put(record.key(),record);effects.add(record);if(policy.equals("volatile_ack"))pending.add(record);return false;
        }
        public int flush() { int count=pending.size();journal.append(pending);pending.clear();return count; }
        public int merge(List<Record> records) {
            var keys=records.stream().map(Record::key).toList();if(!keys.equals(keys.stream().distinct().sorted().toList()))throw new IllegalArgumentException("merge must be key-sorted and unique");int added=0;for(var r:records)if(!put(r))added++;flush();return added;
        }
        public List<Record> accepted() { return List.copyOf(accepted.values()); }
        public List<Record> effects() { return List.copyOf(effects); }
        public List<String> pending() { return pending.stream().map(Record::key).toList(); }
    }
    private static final class Node { final byte id;final Journal journal=new Journal();byte generation=-1;Replica replica;Node(byte id){this.id=id;} }
    /** Deterministic coordinator: reachable writes in node order, two replies ACK, partial writes retained. */
    public static final class Cluster implements AutoCloseable {
        private final String policy;private final List<Node> nodes=new ArrayList<>();private final TreeSet<Byte> blocked=new TreeSet<>();private final List<Map<String,Object>> events=new ArrayList<>();
        public Cluster(String policy) { if(!POLICIES.contains(policy))throw new IllegalArgumentException("unknown cluster policy");this.policy=policy;for(byte n=0;n<3;n++){nodes.add(new Node(n));start(n);} }
        public void start(byte id) { var n=node(id);if(n.replica!=null)throw new IllegalArgumentException("replica already running");n.generation++;n.replica=new Replica(policy,n.journal);events.add(fields("event","start","node",id,"generation",n.generation,"loaded_effects",n.replica.effects.size(),"wal_sha256",n.journal.evidence().get("sha256"))); }
        private Node node(byte id) { if(id<0||id>2)throw new IllegalArgumentException("invalid node");return nodes.get(id); }
        public void crash(byte id) { var n=node(id);if(n.replica==null)throw new IllegalArgumentException("replica already down");n.replica=null;events.add(fields("event","crash","node",id,"generation",n.generation,"wal_sha256",n.journal.evidence().get("sha256"))); }
        public void gate(List<Byte> failed) { blocked.clear();blocked.addAll(failed); }
        public void ungate() { blocked.clear(); }
        public Map<String,Object> attempt(String op,String key,String phase) { var r=record(key);if(!r.op().equals(op))throw new IllegalArgumentException("attempt/key mismatch");var targets=new ArrayList<Byte>();for(var n:nodes)if(n.replica!=null&&!blocked.contains(n.id)){n.replica.put(r);targets.add(n.id);}return freeze(fields("op",op,"key",key,"phase",phase,"targets",targets,"status",targets.size()>=2?"ACK":"TIMEOUT")); }
        public void repair() { var union=new TreeMap<String,Record>();for(var n:nodes){if(n.replica==null)throw new IllegalArgumentException("repair needs all replicas");for(var r:n.replica.accepted()){var old=union.putIfAbsent(r.key(),r);if(old!=null&&!old.equals(r))throw new IllegalArgumentException("conflicting replica payloads");}}var records=List.copyOf(union.values());for(var n:nodes)n.replica.merge(records); }
        public void flush() { for(var n:nodes){if(n.replica==null)throw new IllegalArgumentException("flush needs all replicas");n.replica.flush();} }
        public Map<String,Object> snapshot(String label) {
            var states=new ArrayList<Map<String,Object>>();for(var n:nodes){var live=n.replica;Map<String,Integer> counts=null;Integer value=null;if(live!=null){counts=new TreeMap<>();for(String op:OPS)counts.put(op,(int)live.effects.stream().filter(r -> r.op().equals(op)).count());value=live.effects.stream().mapToInt(Record::delta).sum();}states.add(fields("node",n.id,"running",live!=null,"reachable",live!=null&&!blocked.contains(n.id),"generation",n.generation,"accepted",live==null?null:live.accepted.keySet().stream().toList(),"wal",n.journal.records.stream().map(Record::key).toList(),"effects",live==null?null:live.effects.stream().map(Record::op).toList(),"counts",counts,"value",value));}return freeze(fields("checkpoint",label,"nodes",states));
        }
        public List<Map<String,Object>> payloads() { var out=new ArrayList<Map<String,Object>>();for(var n:nodes){if(n.replica==null)throw new IllegalArgumentException("payloads need running replicas");out.add(freeze(fields("node",n.id,"accepted",n.replica.accepted().stream().map(Record::document).toList(),"wal",n.journal.records().stream().map(Record::document).toList())));}return List.copyOf(out); }
        public Map<String,Object> evidence() { var out=new TreeMap<String,Object>();nodes.forEach(n -> out.put("n"+n.id,n.journal.evidence()));return freeze(out); }
        public Map<String,Object> lifecycle() { return freeze(fields("boundary","in-JVM replica/journal state transitions","events",events,"replica_starts",(int)events.stream().filter(e -> e.get("event").equals("start")).count(),"crash_transitions",(int)events.stream().filter(e -> e.get("event").equals("crash")).count())); }
        @Override public void close() { for(var n:nodes)if(n.replica!=null){n.replica=null;events.add(fields("event","exit","node",n.id,"generation",n.generation,"flushed",false));} }
    }
    public record Result(Config config,Map<String,Object> document) { public Result { document=freeze(document); }public boolean passed(){return document.get("verdict").equals("PASS");} }
    public static Result evaluate(Config c) {
        var attempts=new ArrayList<Map<String,Object>>();var trace=new ArrayList<Map<String,Object>>();var cluster=new Cluster(c.policy());List<Map<String,Object>> payloads;Map<String,Object> evidence;
        try(cluster){
            trace.add(cluster.snapshot("initial"));for(int k=1;k<=c.cut();k++)attempts.add(cluster.attempt("o"+k,"o"+k+":a1","prefix"));trace.add(cluster.snapshot("prefix"));cluster.gate(c.canonicalFailed());if(c.kind().equals("crash"))for(byte n:c.canonicalFailed())cluster.crash(n);trace.add(cluster.snapshot("faulted"));
            boolean timedOut=false;if(c.cut()<3){var window=cluster.attempt("o"+(c.cut()+1),"o"+(c.cut()+1)+":a1","window");attempts.add(window);timedOut=window.get("status").equals("TIMEOUT");trace.add(cluster.snapshot("window"));}
            if(c.kind().equals("crash"))for(byte n:c.canonicalFailed())cluster.start(n);cluster.ungate();trace.add(cluster.snapshot("reopened"));cluster.repair();trace.add(cluster.snapshot("repaired"));
            if(timedOut){attempts.add(cluster.attempt("o"+(c.cut()+1),"o"+(c.cut()+1)+(c.retry().equals("stable")?":a1":":a2"),"retry"));trace.add(cluster.snapshot("retry"));}
            for(int k=c.cut()+2;k<=3;k++)attempts.add(cluster.attempt("o"+k,"o"+k+":a1","suffix"));cluster.flush();trace.add(cluster.snapshot("final"));payloads=cluster.payloads();evidence=cluster.evidence();
        }
        var judged=FaultSchedulesOracle.judge(attempts,trace,payloads);var semantic=new TreeMap<>(judged);semantic.putAll(fields("id",c.caseId(),"policy",c.policy(),"failed",c.canonicalFailed(),"cut",c.cut(),"kind",c.kind(),"retry",c.retry(),"attempts",attempts,"trace",trace));var doc=new TreeMap<>(semantic);doc.putAll(fields("schema","d15.observation/v1","contract","v1","semantic_sha256",digest(canonical(semantic)),"fail_rendered",c.failed(),"fw_var",judged.get("verdict").equals("PASS")?0:2,"carrier_slot","IMPL position 2 (legacy positional, not causal)","wal_evidence",evidence,"final_payloads",payloads,"local_lifecycle",cluster.lifecycle()));return new Result(c,doc);
    }
    /** Three useful fault-free controls outside the 312-case denominator. */
    public static Map<String,Object> control(String policy) { var attempts=new ArrayList<Map<String,Object>>();Map<String,Object> finalState,evidence;try(var cluster=new Cluster(policy)){for(int k=1;k<=3;k++)attempts.add(cluster.attempt("o"+k,"o"+k+":a1","prefix"));cluster.flush();finalState=cluster.snapshot("final");evidence=cluster.evidence();}return freeze(fields("policy",policy,"attempts",attempts,"final",finalState,"wal_evidence",evidence,"ok",FaultSchedulesOracle.faultFree(attempts,finalState))); }
    public static Map<String,String> attributes(Result result) { var out=new TreeMap<String,String>();result.document().forEach((k,v) -> out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
