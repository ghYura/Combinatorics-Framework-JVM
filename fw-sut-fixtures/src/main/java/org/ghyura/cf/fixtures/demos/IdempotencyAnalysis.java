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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Original D1 per-policy tables, effect histograms and every explanatory witness/contrast group. */
public final class IdempotencyAnalysis {
    private IdempotencyAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    public static Idempotency.Config configuration(Observed row) { var f=row.fields(); return new Idempotency.Config(f.get("impl"),f.get("labels"),f.get("cuts"),f.get("control")); }
    public static Set<String> expectedIds() {
        var ids=new TreeSet<String>(); for (String p : Idempotency.POLICIES) { for (String l : Idempotency.PARTITIONS) for (String x : Idempotency.CUTS) ids.add(new Idempotency.Config(p,l,x,"none").caseId()); for (String c : Idempotency.CONTROLS.subList(1,5)) ids.add(new Idempotency.Config(p,"000","00",c).caseId()); } return Collections.unmodifiableSet(ids);
    }
    public static Map<String,List<String>> witnessGroups() {
        var groups=new LinkedHashMap<String,List<String>>(); groups.put("defective_policy_witnesses",List.of("volatile_transport|L=000|C=01|P=none","durable_transport|L=001|C=00|P=none","durable_order|L=000|C=00|P=new_operation_same_order","durable_payload|L=000|C=00|P=new_order_equal_payload")); groups.put("positive_control",Idempotency.CONTROLS.stream().map(c -> "durable_operation|L=000|C=00|P="+c).toList()); groups.put("contrast_1_single_retry",Idempotency.POLICIES.stream().map(p -> p+"|L=000|C=00|P=none").toList()); groups.put("contrast_2_restart_only",Idempotency.POLICIES.stream().map(p -> p+"|L=000|C=01|P=none").toList()); groups.put("contrast_3_new_transport_identity",Idempotency.POLICIES.stream().map(p -> p+"|L=001|C=00|P=none").toList()); groups.put("contrast_4_peer_controls",Idempotency.POLICIES.stream().flatMap(p -> Idempotency.CONTROLS.subList(1,5).stream().map(c -> p+"|L=000|C=00|P="+c)).toList()); return Collections.unmodifiableMap(groups);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Map<String,String> f,String key) { Object value=Json.parse(f.get(key),"observed D1 "+key); if (!(value instanceof Map<?,?>)) throw new IllegalArgumentException("observed D1 object required: "+key); return (Map<String,Object>)value; }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> deliveries(Map<String,Object> observation) { return (List<Map<String,Object>>)observation.get("deliveries"); }
    private static void tally(Map<String,Integer> m,String value) { m.merge(value,1,Integer::sum); }
    @SuppressWarnings("unchecked") private static Map<String,Object> brief(Observed row) {
        var f=row.fields(); var o=object(f,"observation"); var checks=object(f,"checks"); var core=new ArrayList<List<Object>>();
        for (var d : deliveries(o)) { var req=(Map<String,Object>)d.get("request"); var reply=(Map<String,Object>)d.get("response"); core.add(java.util.Arrays.asList(req.get("transport_id"),d.get("epoch"),reply.get("status"),reply.get("receipt_id"),reply.get("order_id")+"/"+reply.get("operation_id"),d.get("effects_after"))); }
        var m=new TreeMap<String,Object>(); m.put("case_id",row.id()); m.put("verdict",f.get("verdict")); m.put("core",core); m.put("failing_sensors",IdempotencyOracle.SENSORS.stream().filter(k -> Boolean.FALSE.equals(checks.get(k))).toList());
        if (o.get("peer")!=null) { var p=(Map<String,Object>)o.get("peer"); var req=(Map<String,Object>)p.get("request"); var payload=(Map<String,Object>)req.get("payload"); m.put("peer",Map.of("request",req.get("transport_id")+" "+req.get("order_id")+"/"+req.get("operation_id")+" amount="+payload.get("amount_minor"),"response",p.get("response"),"effect_delta",p.get("effect_delta"))); }
        return m;
    }
    /** Completeness/identity/domain checks precede recorded-value aggregation; no SUT/reference fallback. */
    @SuppressWarnings("unchecked") public static Map<String,Object> analyse(List<Observed> observations) {
        var expected=expectedIds(); var bank=new TreeMap<String,Observed>(); var totals=new TreeMap<String,Integer>(); var table=new TreeMap<String,Map<String,List<Integer>>>(); var controls=new TreeMap<String,List<String>>(); var b=new TreeMap<String,Integer>(); var e=new TreeMap<String,Integer>(); var observedB=new TreeMap<String,Integer>(); var observedE=new TreeMap<String,Integer>();
        for (String p : Idempotency.POLICIES) table.put(p,Map.of("core",new ArrayList<>(List.of(0,0)),"peer",new ArrayList<>(List.of(0,0))));
        for (Observed row : observations) {
            var f=row.fields(); var c=configuration(row); String pop=c.control().equals("none")?"core":"peer",verdict=f.get("verdict");
            if (!c.caseId().equals(row.id())||!pop.equals(f.get("population"))||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null||!("PASS".equals(verdict)||"DOMAIN_FAIL".equals(verdict))) throw new IllegalArgumentException("unknown/duplicate/conflicting/non-domain D1 support");
            tally(totals,verdict); var cell=table.get(c.policy()).get(pop); int col=verdict.equals("PASS")?0:1; cell.set(col,cell.get(col)+1);
            if (pop.equals("core")&&(c.policy().equals("volatile_transport")||c.policy().equals("durable_transport"))) {
                var o=object(f,"observation"); var multiplicity=(Map<String,Object>)o.get("pre_peer_multiplicity"); String effects=String.valueOf(multiplicity.get("O1/OP1"));
                if (c.policy().equals("durable_transport")) tally(observedB,effects);
                else { tally(observedE,effects); var labels=new TreeSet<Character>(); var joint=new TreeSet<String>(); byte epoch=0; for (int i=0;i<3;i++) { if (i>0&&c.cuts().charAt(i-1)=='1') epoch++; labels.add(c.labels().charAt(i)); joint.add(c.labels().charAt(i)+"/"+epoch); } tally(b,Integer.toString(labels.size())); tally(e,Integer.toString(joint.size())); }
            }
        }
        if (!bank.keySet().equals(expected)) throw new IllegalArgumentException("complete original 120-case D1 support required");
        for (String p : Idempotency.POLICIES) controls.put(p,Idempotency.CONTROLS.subList(1,5).stream().map(c -> bank.get(p+"|L=000|C=00|P="+c).fields().get("verdict").equals("PASS")?"pass":"fail").toList());
        var witnesses=new TreeMap<String,Object>(); witnesses.put("schema","d1.witnesses/v1"); witnesses.put("note","Explanatory witnesses chosen before the run and looked up in the complete campaign; not held-out discovery and not a certified minimum suite."); witnessGroups().forEach((k,ids) -> witnesses.put(k,ids.stream().map(id -> brief(bank.get(id))).toList()));
        return (Map<String,Object>)ToolCallOrder.freezeDocument(Idempotency.fields("totals",totals,"verdict_table",table,"control_table",controls,"histograms",Map.of("B",b,"E",e,"observed_durable_transport_effects",observedB,"observed_volatile_transport_effects",observedE),"witnesses",witnesses));
    }
}
