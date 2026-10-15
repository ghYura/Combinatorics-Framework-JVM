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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Seven policy-blind checks on received attempts, checkpoints and full payloads; no replica/model execution. */
public final class FaultSchedulesOracle {
    private FaultSchedulesOracle() { }
    public static final List<String> CHECKS=List.of("all_logical_ops_acknowledged","acknowledged_survive_repair","acknowledged_effects_once","accepted_logical_ops_once","replicas_agree","matches_fault_free_reference","final_wal_matches_accepted");
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object raw) { if(!(raw instanceof Map<?,?> map)||map.keySet().stream().anyMatch(k -> !(k instanceof String)))throw new IllegalArgumentException("object evidence required");return (Map<String,Object>)map; }
    static List<?> list(Object raw) { if(!(raw instanceof List<?> values))throw new IllegalArgumentException("list evidence required");return values; }
    static int integer(Object value) { if(!(value instanceof Byte||value instanceof Integer||value instanceof Long)||((Number)value).longValue()<Integer.MIN_VALUE||((Number)value).longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("strict bounded integral evidence required");return ((Number)value).intValue(); }
    private static List<String> keys(Object raw) { var out=new ArrayList<String>();for(var k:list(raw)){if(!(k instanceof String s)||!s.matches("o[123]:a[12]"))throw new IllegalArgumentException("invalid journal key");out.add(s);}return List.copyOf(out); }
    private static int delta(String op) { return switch(op){case "o1" -> 1;case "o2" -> 10;case "o3" -> 100;default -> throw new IllegalArgumentException("invalid observed operation");}; }
    private static Map<String,Integer> counts(Object raw) { var m=object(raw);if(!m.keySet().equals(Set.of("o1","o2","o3")))throw new IllegalArgumentException("all operation counts required");var out=new TreeMap<String,Integer>();for(String op:List.of("o1","o2","o3")){int n=integer(m.get(op));if(n<0)throw new IllegalArgumentException("negative effect count");out.put(op,n);}return out; }
    private static List<Map<String,Object>> nodes(Object raw) {
        var values=list(raw);if(values.size()!=3)throw new IllegalArgumentException("all three node states required");var out=new ArrayList<Map<String,Object>>();
        for(int i=0;i<3;i++){var n=object(values.get(i));if(!n.keySet().equals(Set.of("node","running","reachable","generation","accepted","wal","effects","counts","value"))||integer(n.get("node"))!=i||integer(n.get("generation"))<0||integer(n.get("generation"))>127||!(n.get("running")instanceof Boolean)||!(n.get("reachable")instanceof Boolean))throw new IllegalArgumentException("invalid node snapshot");keys(n.get("wal"));
            if(Boolean.TRUE.equals(n.get("running"))){var accepted=keys(n.get("accepted"));if(!accepted.equals(accepted.stream().distinct().sorted().toList()))throw new IllegalArgumentException("accepted keys must be sorted/unique");var got=counts(n.get("counts"));var actual=new TreeMap<String,Integer>(Map.of("o1",0,"o2",0,"o3",0));int value=0;for(Object op:list(n.get("effects"))){if(!(op instanceof String s))throw new IllegalArgumentException("string effect required");value=Math.addExact(value,delta(s));actual.merge(s,1,Integer::sum);}if(!got.equals(actual)||integer(n.get("value"))!=value)throw new IllegalArgumentException("snapshot counts/value disagree with effects");}
            else if(!Boolean.FALSE.equals(n.get("reachable"))||n.get("accepted")!=null||n.get("effects")!=null||n.get("counts")!=null||n.get("value")!=null)throw new IllegalArgumentException("down-node live fields must be null");out.add(n);
        }return List.copyOf(out);
    }
    private static List<String> acknowledged(List<?> attempts,boolean early) { var out=new TreeSet<String>();for(Object raw:attempts){var a=object(raw);if(!a.keySet().equals(Set.of("op","key","phase","targets","status"))||!(a.get("op")instanceof String op)||!(a.get("phase")instanceof String phase)||!List.of("prefix","window","retry","suffix").contains(phase)||!List.of("ACK","TIMEOUT").contains(a.get("status"))||!keys(List.of(a.get("key"))).getFirst().startsWith(op+":"))throw new IllegalArgumentException("invalid client attempt");delta(op);var targets=new ArrayList<Integer>();for(Object n:list(a.get("targets"))){int v=integer(n);if(v<0||v>2)throw new IllegalArgumentException("invalid attempt node");targets.add(v);}if(!targets.equals(targets.stream().distinct().sorted().toList())||a.get("status").equals("ACK")!=(targets.size()>=2))throw new IllegalArgumentException("invalid quorum observation");if(a.get("status").equals("ACK")&&(!early||phase.equals("prefix")||phase.equals("window")))out.add(op);}return List.copyOf(out); }
    private static List<Map<String,Object>> records(Object raw) { var out=new ArrayList<Map<String,Object>>();for(Object value:list(raw)){var m=object(value);if(!m.keySet().equals(Set.of("key","op","delta"))||!(m.get("op")instanceof String op))throw new IllegalArgumentException("full journal payload required");keys(List.of(m.get("key")));delta(op);integer(m.get("delta"));out.add(m);}return List.copyOf(out); }
    private static boolean payloadsMatch(List<?> payloads,List<Map<String,Object>> finalNodes) {
        if(payloads.size()!=3)throw new IllegalArgumentException("all final payload sets required");boolean ok=true;
        for(int i=0;i<3;i++){var p=object(payloads.get(i));if(!p.keySet().equals(Set.of("node","accepted","wal"))||integer(p.get("node"))!=i)throw new IllegalArgumentException("invalid payload node");var accepted=records(p.get("accepted"));var wal=records(p.get("wal"));var a=new TreeMap<String,Object>();var w=new TreeMap<String,Object>();for(var rec:accepted){Object key=rec.get("key");ok&=a.put((String)key,rec)==null;ok&=key.toString().startsWith(rec.get("op")+":")&&integer(rec.get("delta"))==delta((String)rec.get("op"));}for(var rec:wal){Object key=rec.get("key");ok&=w.put((String)key,rec)==null;ok&=key.toString().startsWith(rec.get("op")+":")&&integer(rec.get("delta"))==delta((String)rec.get("op"));}var n=finalNodes.get(i);ok&=a.equals(w)&&new ArrayList<>(a.keySet()).equals(keys(n.get("accepted")))&&wal.stream().map(m -> m.get("key")).toList().equals(keys(n.get("wal")));}return ok;
    }
    public static Map<String,Object> judge(List<?> attempts,List<?> trace,List<?> payloads) {
        var ack=acknowledged(attempts,false);var before=acknowledged(attempts,true);var by=new TreeMap<String,List<Map<String,Object>>>();String last=null;
        for(Object raw:trace){var t=object(raw);if(!t.keySet().equals(Set.of("checkpoint","nodes"))||!(t.get("checkpoint")instanceof String name)||!List.of("initial","prefix","faulted","window","reopened","repaired","retry","final").contains(name)||by.putIfAbsent(name,nodes(t.get("nodes")))!=null)throw new IllegalArgumentException("invalid/duplicate checkpoint");last=name;}
        if(!"final".equals(last)||!by.containsKey("repaired"))throw new IllegalArgumentException("repaired/final checkpoints required");var finalNodes=by.get("final");var repaired=by.get("repaired");for(var n:finalNodes)if(!Boolean.TRUE.equals(n.get("running")))throw new IllegalArgumentException("final node down");for(var n:repaired)if(!Boolean.TRUE.equals(n.get("running")))throw new IllegalArgumentException("repaired node down");
        var checks=new TreeMap<String,Boolean>();checks.put("all_logical_ops_acknowledged",ack.equals(List.of("o1","o2","o3")));checks.put("acknowledged_survive_repair",repaired.stream().allMatch(n -> before.stream().allMatch(op -> counts(n.get("counts")).get(op)>=1)));checks.put("acknowledged_effects_once",finalNodes.stream().allMatch(n -> ack.stream().allMatch(op -> counts(n.get("counts")).get(op)==1)));checks.put("accepted_logical_ops_once",finalNodes.stream().allMatch(n -> {var c=new TreeMap<String,Integer>();keys(n.get("accepted")).forEach(k -> c.merge(k.substring(0,2),1,Integer::sum));return c.equals(Map.of("o1",1,"o2",1,"o3",1));}));
        var first=finalNodes.getFirst();checks.put("replicas_agree",finalNodes.stream().allMatch(n -> n.get("accepted").equals(first.get("accepted"))&&counts(n.get("counts")).equals(counts(first.get("counts")))&&integer(n.get("value"))==integer(first.get("value"))));checks.put("matches_fault_free_reference",finalNodes.stream().allMatch(n -> counts(n.get("counts")).equals(Map.of("o1",1,"o2",1,"o3",1))&&integer(n.get("value"))==111));checks.put("final_wal_matches_accepted",payloadsMatch(payloads,finalNodes)&&finalNodes.stream().allMatch(n -> {var wal=keys(n.get("wal"));return new TreeSet<>(wal).size()==wal.size()&&wal.stream().sorted().toList().equals(keys(n.get("accepted")));}));
        return FaultSchedules.freeze(FaultSchedules.fields("acknowledged",ack,"checks",checks,"verdict",checks.values().stream().allMatch(Boolean.TRUE::equals)?"PASS":"DOMAIN_FAIL"));
    }
    static boolean faultFree(List<?> attempts,Map<String,Object> finalState) { var ack=acknowledged(attempts,false);return ack.equals(List.of("o1","o2","o3"))&&nodes(finalState.get("nodes")).stream().allMatch(n -> Boolean.TRUE.equals(n.get("running"))&&counts(n.get("counts")).equals(Map.of("o1",1,"o2",1,"o3",1))&&integer(n.get("value"))==111&&keys(n.get("wal")).equals(List.of("o1:a1","o2:a1","o3:a1"))); }
}
