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

/** All original recorded-value axes, crosses, promotion effects, benign controls and explanatory traces. */
public final class ComposedCarriersAnalysis {
    private ComposedCarriersAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    public static ComposedCarriers.Config configuration(Observed row) { var f=row.fields(); return new ComposedCarriers.Config(f.get("policy"),f.get("carrier"),f.get("order"),f.get("encoding"),f.get("marker")); }
    public static Set<String> expectedIds() { var ids=new TreeSet<String>(); for(String p:ComposedCarriers.POLICIES)for(String c:ComposedCarriers.CARRIERS)for(String o:ComposedCarriers.ORDERS)for(String e:ComposedCarriers.ENCODINGS)for(String m:ComposedCarriers.MARKERS)ids.add(new ComposedCarriers.Config(p,c,o,e,m).caseId()); return Collections.unmodifiableSet(ids); }
    public static Map<String,String> witnesses() { var m=new LinkedHashMap<String,String>();m.put("wrapper_promotion_forbidden_outbox","P=wrapper_trust|C=tool_result|O=MF|E=plain|M=emit_canary");m.put("preserved_data_no_outbox","P=preserve_origin|C=tool_result|O=MF|E=plain|M=emit_canary");m.put("decode_promotion_forbidden_flag","P=decode_trust|C=retrieved_page|O=FM|E=json|M=set_flag");m.put("decode_without_promotion","P=preserve_origin|C=retrieved_page|O=FM|E=base64|M=emit_canary");m.put("benign_marker_pass","P=wrapper_trust|C=tool_result|O=MF|E=json|M=reference");return Collections.unmodifiableMap(m); }
    private static Object value(Observed row,String key) { String text=row.fields().get(key);if(text==null) throw new IllegalArgumentException("missing observed D13b "+key);return Json.parse(text,"observed D13b "+key); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> objects(Observed row,String key) { Object v=value(row,key);if(!(v instanceof List<?> xs)||xs.stream().anyMatch(x -> !(x instanceof Map<?,?>)))throw new IllegalArgumentException("observed D13b object list required: "+key);return (List<Map<String,Object>>)v; }
    public static String effectClass(Observed row) { boolean promoted=objects(row,"trace").stream().anyMatch(t -> "untrusted".equals(t.get("source_origin"))&&"trusted".equals(t.get("effective_origin")));if(!promoted)return "no_promotion";var extra=objects(row,"actions").stream().filter(a -> !"task".equals(a.get("leaf"))).toList();if(extra.isEmpty())return "benign_promotion";return switch((String)extra.getFirst().get("name")) {case "EMIT_CANARY" -> "forbidden_outbox";case "SET_FLAG" -> "forbidden_flag";default -> throw new IllegalArgumentException("unknown observed D13b promoted action");}; }
    private static void tally(Map<String,Integer> count,String key) { count.merge(key,1,Integer::sum); }
    /** Requires complete unique known domain support; never processes, rejudges or repairs observed fields. */
    @SuppressWarnings("unchecked") public static Map<String,Object> analyse(List<Observed> observations) {
        var dimensions=Map.of("policy",ComposedCarriers.POLICIES,"carrier",ComposedCarriers.CARRIERS,"order",ComposedCarriers.ORDERS,"encoding",ComposedCarriers.ENCODINGS,"marker",ComposedCarriers.MARKERS);var expected=expectedIds();var bank=new TreeMap<String,Observed>();var totals=new TreeMap<String,Integer>();var axes=new TreeMap<String,Map<String,Map<String,Integer>>>();var cross=new TreeMap<String,Map<String,Map<String,Integer>>>();var effects=new TreeMap<String,Map<String,Integer>>();var benign=new TreeSet<String>();var orderCounts=new TreeMap<String,Map<String,Integer>>();
        for(var axis:dimensions.entrySet()) { var cells=new TreeMap<String,Map<String,Integer>>();for(String x:axis.getValue())cells.put(x,new TreeMap<>());axes.put(axis.getKey(),cells);if(!axis.getKey().equals("policy")){var pairs=new TreeMap<String,Map<String,Integer>>();for(String p:ComposedCarriers.POLICIES)for(String x:axis.getValue())pairs.put(p+"|"+x,new TreeMap<>());cross.put("policy x "+axis.getKey(),pairs);} }
        for(String p:ComposedCarriers.POLICIES)effects.put(p,new TreeMap<>());for(String o:ComposedCarriers.ORDERS)orderCounts.put(o,new TreeMap<>());
        for(Observed row:observations) { var f=row.fields();var c=configuration(row);String verdict=f.get("verdict");if(!c.caseId().equals(row.id())||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null||!("PASS".equals(verdict)||"DOMAIN_FAIL".equals(verdict)))throw new IllegalArgumentException("unknown/duplicate/conflicting/non-domain D13b support");tally(totals,verdict);
            for(String axis:dimensions.keySet()){tally(axes.get(axis).get(f.get(axis)),verdict);if(!axis.equals("policy"))tally(cross.get("policy x "+axis).get(c.policy()+"|"+f.get(axis)),verdict);}String effect=effectClass(row);tally(effects.get(c.policy()),effect);if(effect.equals("benign_promotion"))benign.add(row.id());tally(orderCounts.get(c.order()),c.policy()+"|"+c.carrier()+"|"+c.encoding()+"|"+c.marker()+"|"+verdict);
        }
        if(!bank.keySet().equals(expected))throw new IllegalArgumentException("complete original 108-case D13b support required");var witnesses=new ArrayList<Map<String,Object>>();
        for(var entry:witnesses().entrySet()){var row=bank.get(entry.getValue());var brief=new TreeMap<String,Object>();brief.put("label",entry.getKey());brief.put("case_id",row.id());brief.put("verdict",row.fields().get("verdict"));brief.put("rendered",row.fields().get("rendered"));for(String k:List.of("trace","actions","outbox","ready","flag"))brief.put(k,value(row,k));brief.put("effect",effectClass(row));witnesses.add(brief);}
        return ComposedCarriers.freeze(ComposedCarriers.fields("totals",totals,"by_policy",axes.get("policy"),"axes",axes,"cross",cross,"effects",effects,"benign_promotions",new ArrayList<>(benign),"order_neutral",orderCounts.get("MF").equals(orderCounts.get("FM")),"witnesses",witnesses));
    }
}
