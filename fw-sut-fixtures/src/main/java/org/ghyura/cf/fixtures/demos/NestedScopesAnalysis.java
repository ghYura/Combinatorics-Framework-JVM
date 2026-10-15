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
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;

/** Reads full recorded support and witnesses without running or rejudging trees. */
public final class NestedScopesAnalysis {
    private NestedScopesAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    public static Set<String> treeIds() { var ids=new TreeSet<String>();for(String z:List.of("A","M"))for(String c:List.of("A","M"))for(String t:List.of("N","S"))ids.add("ZIP="+z+"|CAT="+c+"|TAIL="+t);return Collections.unmodifiableSet(ids); }
    public static Set<String> expectedIds(String phase) { if(!List.of("B1","B2").contains(phase))throw new IllegalArgumentException("B1/B2 required");var ids=new TreeSet<String>();for(String p:NestedScopes.POLICIES)for(String t:phase.equals("B1")?treeIds():Set.of("BUNDLE=all8"))ids.add(phase+"|"+p+"|"+t);return Collections.unmodifiableSet(ids); }
    public static Object value(Observed row,String key) { var v=row.fields().get(key);if(v==null)throw new IllegalArgumentException("missing observed "+key);return Json.parse(v,"recorded D5 B "+key); }
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Object v) { if(!(v instanceof Map<?,?> map)||map.keySet().stream().anyMatch(k->!(k instanceof String)))throw new IllegalArgumentException("recorded object required");return (Map<String,Object>)v; }
    private static String coordinate(Object value) { var res=object(value);if(!(res.get("tree_id")instanceof String id)||!id.equals(NestedScopes.treeId(NestedScopes.fromDocument(res.get("tree")))))throw new IllegalArgumentException("conflicting recorded tree coordinate");return id; }
    public static Map<String,String> witnesses(String phase) { var w=new LinkedHashMap<String,String>();if(phase.equals("B1"))for(String p:NestedScopes.POLICIES)w.put(p+"_AAN","B1|"+p+"|ZIP=A|CAT=A|TAIL=N");else if(phase.equals("B2"))w.put("correct_bundle","B2|correct|BUNDLE=all8");else throw new IllegalArgumentException("B1/B2 required");return Collections.unmodifiableMap(w); }
    public static Map<String,Object> analyse(String phase,List<Observed> rows) {
        var expected=expectedIds(phase);var bank=new TreeMap<String,Observed>();var totals=new TreeMap<String,Integer>();var by=new TreeMap<String,Map<String,Integer>>();NestedScopes.POLICIES.forEach(p->by.put(p,new TreeMap<>()));int evaluations=0;
        for(var row:rows){String policy=row.fields().get("policy"),verdict=row.fields().get("verdict");if(!phase.equals(row.fields().get("phase"))||!"B".equals(row.fields().get("contract"))||!"d5b.observation/v1".equals(row.fields().get("schema"))||!NestedScopes.POLICIES.contains(policy)||!List.of("PASS","DOMAIN_FAIL").contains(verdict)||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null)throw new IllegalArgumentException("unknown/duplicate/non-domain D5 B observation");if(phase.equals("B1")){String tid=coordinate(value(row,"result"));if(!row.id().equals("B1|"+policy+"|"+tid))throw new IllegalArgumentException("conflicting case coordinate");evaluations++;}else{if(!row.id().equals("B2|"+policy+"|BUNDLE=all8"))throw new IllegalArgumentException("conflicting bundle coordinate");var results=object(value(row,"results"));if(!results.keySet().equals(treeIds()))throw new IllegalArgumentException("eight recorded trees required");for(var e:results.entrySet())if(!e.getKey().equals(coordinate(e.getValue())))throw new IllegalArgumentException("conflicting bundle tree coordinate");Object order=value(row,"generation_order");if(!(order instanceof List<?> orderList)||orderList.size()!=8||!new java.util.HashSet<>(orderList).equals(treeIds()))throw new IllegalArgumentException("complete distinct generation order required");Object failing=value(row,"failing_trees");if(!(failing instanceof List<?> failedList)||!treeIds().containsAll(failedList)||new java.util.HashSet<>(failedList).size()!=failedList.size())throw new IllegalArgumentException("known distinct failing tree list required");evaluations+=results.size();}totals.merge(verdict,1,Integer::sum);by.get(policy).merge(verdict,1,Integer::sum);}
        if(!bank.keySet().equals(expected))throw new IllegalArgumentException("complete original D5 B support required");var witnesses=new ArrayList<Map<String,Object>>();for(var e:witnesses(phase).entrySet()){var row=bank.get(e.getValue());var brief=new TreeMap<>(NestedScopes.fields("label",e.getKey(),"case_id",row.id(),"verdict",row.fields().get("verdict")));if(phase.equals("B1")){var result=object(value(row,"result"));for(String key:List.of("tree","expected","observed","sut_trace"))brief.put(key,result.get(key));}else{brief.put("generation_order",value(row,"generation_order"));brief.put("failing_trees",value(row,"failing_trees"));}witnesses.add(brief);}return NestedScopes.freeze(NestedScopes.fields("totals",totals,"by_policy",by,"tree_evaluations",evaluations,"witnesses",witnesses));
    }
}
