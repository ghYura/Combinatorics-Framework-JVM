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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** D13a original offline counts and witness timelines from actual observations; no SUT/oracle rerun or repair.
 * Public retained API data are caller-owned under the 0064 admission contract. */
public final class ToolCallAnalysis {
    private ToolCallAnalysis() { }
    public static final List<String> WITNESSES=List.of("P=sticky_approval|O=ASR|N=1|F=0|X=1","P=guarded|O=ASR|N=1|F=0|X=1","P=success_only_taint|O=ARS|N=1|F=1|X=none","P=no_dedup|O=ASR|N=2|F=0|X=none","P=guarded|O=ASR|N=2|F=1|X=3");
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String caseId() { return fields.get("case_id"); } }
    public static List<String> expectedIds() {
        var ids=new TreeSet<String>(); for (var p : ToolCallOrder.POLICIES) for (var o : ToolCallOrder.ORDERS) for (byte n=1;n<=2;n++) for (boolean f : List.of(false,true)) for (int x=-1;x<=3;x++) ids.add(new ToolCallOrder.Config(p,o,n,f,x<0?null:(byte)x).caseId()); return List.copyOf(ids);
    }
    private static String field(Observed r,String key) { String v=r.fields().get(key); if (v==null) throw new IllegalArgumentException("missing observed field "+key); return v; }
    public static ToolCallOrder.Config configuration(Observed r) {
        String n=field(r,"send_repetitions"),fail=field(r,"fail_read"),x=field(r,"revoke_cut");
        if (!n.matches("[12]")||!List.of("true","false").contains(fail)||!x.equals("null")&&!x.matches("[0-3]")) throw new IllegalArgumentException("invalid observed configuration");
        var c=new ToolCallOrder.Config(field(r,"policy"),field(r,"order"),Byte.parseByte(n),Boolean.parseBoolean(fail),x.equals("null")?null:Byte.valueOf(x));
        if (!c.caseId().equals(r.caseId())) throw new IllegalArgumentException("conflicting case identity"); return c;
    }
    private static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?> m)) throw new IllegalArgumentException("observed object required"); var out=new TreeMap<String,Object>(); for (var e : m.entrySet()) { if (!(e.getKey() instanceof String k)) throw new IllegalArgumentException("string key required"); out.put(k,e.getValue()); } return out;
    }
    private static List<?> array(Object value) { if (!(value instanceof List<?> list)) throw new IllegalArgumentException("observed array required"); return list; }
    private static Object parsed(Observed r,String key) { return Json.parse(field(r,key),r.caseId()+"/"+key); }
    private static Object required(Map<String,Object> m,String key) { if (!m.containsKey(key)) throw new IllegalArgumentException("missing event field "+key); return m.get(key); }
    private static boolean flag(Map<String,Object> m,String key) { if (!(required(m,key) instanceof Boolean b)) throw new IllegalArgumentException("boolean state flag required"); return b; }
    private static String text(Map<String,Object> m,String key) { if (!(required(m,key) instanceof String s)) throw new IllegalArgumentException("string event field required"); return s; }
    private static int number(Map<String,Object> m,String key) { if (!(required(m,key) instanceof Long n)||n<0||n>Integer.MAX_VALUE) throw new IllegalArgumentException("nonnegative event count required"); return Math.toIntExact(n); }
    private static void increment(Map<String,Integer> m,String key) { m.merge(key,1,Math::addExact); }
    private static void tally(Map<String,Map<String,Integer>> groups,String key,String verdict) { increment(groups.computeIfAbsent(key,ignored -> new TreeMap<>()),verdict); }
    private static List<Object> timeline(List<?> trace) {
        boolean approved=false,tainted=false; var rows=new ArrayList<Object>();
        for (var value : trace) {
            var e=object(value); String kind=text(e,"event"); if (kind.equals("approve")) approved=true; else if (kind.equals("revoke")) approved=false; if (kind.equals("read_chunk")) tainted=true;
            String label=kind+(kind.equals("revoke")?"("+number(e,"cut")+")":e.containsKey("request")?"#"+number(e,"request"):"");
            if (kind.equals("send_result")) { Object payload=required(e,"payload"); if (payload!=null&&!(payload instanceof String)) throw new IllegalArgumentException("string or null payload required"); label+=" -> "+text(e,"action")+"("+text(e,"reason")+") payload="+(payload==null?"None":payload); }
            var local=object(required(e,"state")); rows.add(ToolCallOrder.fields("event",label,"local_state",ToolCallOrder.fields("approved",flag(local,"approved"),"tainted",flag(local,"tainted"),"sent_once",flag(local,"sent_once")),"buffer",text(local,"buffer"),"prefix_evidence",ToolCallOrder.fields("approved",approved,"tainted",tainted),"outbox_size",number(e,"outbox_size")));
        }
        return List.copyOf(rows);
    }
    public static Map<String,Object> analyse(List<Observed> observations) {
        var rows=new TreeMap<String,Observed>(); var expected=new TreeSet<>(expectedIds());
        for (var r : observations) {
            if (r.caseId()==null||!expected.contains(r.caseId())) throw new IllegalArgumentException("unknown identity "+r.caseId()); configuration(r);
            if (!List.of("PASS","DOMAIN_FAIL").contains(field(r,"verdict"))) throw new IllegalArgumentException("non-domain verdict"); if (rows.putIfAbsent(r.caseId(),r)!=null) throw new IllegalArgumentException("duplicate observation "+r.caseId());
        }
        if (!rows.keySet().equals(expected)) throw new IllegalArgumentException("exactly one observation for each of 480 cases required");
        var groups=new TreeMap<String,Map<String,Map<String,Integer>>>(); for (var g : List.of("by_policy","by_policy_repetitions","by_policy_read_failure","by_policy_revoke_cut","by_policy_order")) groups.put(g,new TreeMap<>());
        var violations=new TreeMap<String,Map<String,Integer>>(); for (var p : ToolCallOrder.POLICIES) violations.put(p,new TreeMap<>());
        int requests=0,effects=0,emits=0;
        for (var r : rows.values()) {
            var c=configuration(r); String p=c.policy(),v=field(r,"verdict"),prefix="('"+p+"', ";
            tally(groups.get("by_policy"),p,v); tally(groups.get("by_policy_repetitions"),prefix+c.repetitions()+")",v); tally(groups.get("by_policy_read_failure"),prefix+(c.failRead()?"True":"False")+")",v);
            tally(groups.get("by_policy_revoke_cut"),prefix+(c.revokeCut()==null?"'none'":c.revokeCut())+")",v); tally(groups.get("by_policy_order"),prefix+"'"+c.order()+"')",v);
            var sends=array(parsed(r,"send_results")); requests=Math.addExact(requests,sends.size()); effects=Math.addExact(effects,array(parsed(r,"outbox")).size());
            for (var s : sends) if (text(object(s),"action").equals("emit")) emits=Math.addExact(emits,1);
            for (var violation : array(parsed(r,"violations"))) increment(violations.get(p),text(object(violation),"kind"));
        }
        var timelines=new TreeMap<String,List<Object>>(); for (var w : WITNESSES) timelines.put(w,timeline(array(parsed(rows.get(w),"trace"))));
        var doc=new TreeMap<String,Object>(); doc.put("schema","d13a.analysis/v1"); doc.put("source","observed traces of all 480 cases"); doc.putAll(groups); doc.put("violation_kinds",violations); doc.put("counts",ToolCallOrder.fields("framework_attempts",rows.size(),"send_requests",requests,"outbox_effects",effects,"emit_responses",emits)); doc.put("timelines",timelines);
        // Freeze the closed-domain document; use the framework codec, not a new serializer/parser.
        return objectFrozen(doc);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> objectFrozen(Map<String,Object> doc) { return (Map<String,Object>)ToolCallOrder.freezeDocument(doc); }
}
