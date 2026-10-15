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

/** Exact case support and recorded summaries/witnesses; never reruns or rejudges the billing service. */
public final class WorkflowOrderAnalysis {
    private WorkflowOrderAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    public static List<WorkflowOrder.Config> configurations(String phase) { if(!List.of("A","B").contains(phase))throw new IllegalArgumentException("A/B phase required");var out=new ArrayList<WorkflowOrder.Config>();for(String p:WorkflowOrder.POLICIES)for(char a:"CFN".toCharArray())for(char b:"CFN".toCharArray())for(char c:"CFN".toCharArray()){if(phase.equals("A")&&(a==b||a==c||b==c))continue;for(byte s=0;s<=(phase.equals("A")?1:0);s++)for(byte q=0;q<=(phase.equals("A")?1:0);q++)out.add(new WorkflowOrder.Config(phase,p,""+a+b+c,s,q));}return List.copyOf(out); }
    public static Set<String> expectedIds(String phase) { return Collections.unmodifiableSet(new TreeSet<>(configurations(phase).stream().map(WorkflowOrder.Config::caseId).toList())); }
    public static Object value(Observed row,String key) { String s=row.fields().get(key);if(s==null)throw new IllegalArgumentException("missing observed D3 "+key);return Json.parse(s,"observed D3 "+key); }
    private static List<String> strings(Object x) { return WorkflowOrderOracle.list(x).stream().map(v -> {if(!(v instanceof String s))throw new IllegalArgumentException("text list required");return s;}).toList(); }
    public static WorkflowOrder.Config configuration(Observed row) { var ops=strings(value(row,"ops"));if(ops.size()<3||ops.size()>5||ops.stream().anyMatch(s -> s.length()!=1))throw new IllegalArgumentException("three core events and optional tails required");int s=WorkflowOrderOracle.integer(value(row,"S")),q=WorkflowOrderOracle.integer(value(row,"Q"));if(s<0||s>1||q<0||q>1)throw new IllegalArgumentException("Boolean S/Q coordinates required");var c=new WorkflowOrder.Config(row.fields().get("phase"),row.fields().get("policy"),String.join("",ops.subList(0,3)),(byte)s,(byte)q);if(!c.order().equals(String.join("",ops)))throw new IllegalArgumentException("conflicting recorded operation order");return c; }
    public static Map<String,String> witnesses(String phase) { var m=new LinkedHashMap<String,String>();if(phase.equals("A")){m.put("refund_before_charge","A|refund_unchecked|OPS=FCN|S=0|Q=0");m.put("restart_breaks_reconcile_heals","A|restart_cache|OPS=CNF|S=1|Q=1");m.put("restart_without_reconcile","A|restart_cache|OPS=CNF|S=1|Q=0");m.put("positive_control","A|correct|OPS=CNF|S=1|Q=1");}else if(phase.equals("B"))m.put("duplicate_refund","B|refund_unchecked|OPS=CFF|S=0|Q=0");else throw new IllegalArgumentException("A/B phase required");return Collections.unmodifiableMap(m); }
    private static void tally(Map<String,Integer> map,String verdict) { if(!List.of("PASS","DOMAIN_FAIL").contains(verdict))throw new IllegalArgumentException("domain verdict required");map.merge(verdict,1,Integer::sum); }
    public static Map<String,Object> analyse(String phase,List<Observed> observations) {
        var expected=expectedIds(phase);var bank=new TreeMap<String,Observed>();var totals=new TreeMap<String,Integer>();var byPolicy=new TreeMap<String,Map<String,Integer>>();WorkflowOrder.POLICIES.forEach(p -> byPolicy.put(p,new TreeMap<>()));var finalOnly=new TreeMap<String,Integer>();var hidden=new TreeSet<String>();
        for(var row:observations){var c=configuration(row);if(!c.phase().equals(phase)||!c.caseId().equals(row.id())||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null)throw new IllegalArgumentException("unknown/duplicate/conflicting D3 observation");String verdict=row.fields().get("verdict");tally(totals,verdict);tally(byPolicy.get(c.policy()),verdict);tally(finalOnly,row.fields().get("final_only_verdict"));Object hide=value(row,"hidden_by_final_only");if(!(hide instanceof Boolean))throw new IllegalArgumentException("Boolean hidden indicator required");if(Boolean.TRUE.equals(hide))hidden.add(row.id());}
        if(!bank.keySet().equals(expected))throw new IllegalArgumentException("complete original D3 phase support required");var witnesses=new ArrayList<Map<String,Object>>();
        for(var e:witnesses(phase).entrySet()){var row=bank.get(e.getValue());var brief=new ArrayList<Map<String,Object>>();for(Object x:WorkflowOrderOracle.list(value(row,"checkpoints"))){var cp=WorkflowOrderOracle.object(x);brief.add(WorkflowOrder.fields("k",cp.get("index"),"op",cp.get("op"),"observed",cp.get("observed"),"reference",cp.get("reference"),"ok",cp.get("ok")));}witnesses.add(WorkflowOrder.fields("label",e.getKey(),"case_id",row.id(),"ops",value(row,"ops"),"checkpoints",brief,"failing_checkpoints",value(row,"failing_checkpoints"),"verdict",row.fields().get("verdict"),"final_only_verdict",row.fields().get("final_only_verdict")));}
        return WorkflowOrder.freeze(WorkflowOrder.fields("totals",totals,"by_policy",byPolicy,"hidden_by_final_only",hidden.size(),"hidden_cases",new ArrayList<>(hidden),"final_only",finalOnly,"witnesses",witnesses));
    }
    /** Separate sizing/equivalence model; no campaign generation or expected verdict lookup. */
    public static Map<String,Object> alternate() { var multisets=new ArrayList<String>();var distinct=new TreeSet<String>();String alphabet="CFN";for(int i=0;i<3;i++)for(int j=i;j<3;j++)for(int k=j;k<3;k++){String m=""+alphabet.charAt(i)+alphabet.charAt(j)+alphabet.charAt(k);multisets.add(m);for(int a=0;a<3;a++)for(int b=0;b<3;b++)for(int c=0;c<3;c++)if(a!=b&&a!=c&&b!=c)distinct.add(""+m.charAt(a)+m.charAt(b)+m.charAt(c));}var product=new TreeSet<>(configurations("B").stream().map(WorkflowOrder.Config::core).toList());return WorkflowOrder.fields("multisets",multisets,"distinct_orders",new ArrayList<>(distinct),"equals_B_product",distinct.equals(product),"original_role","planned only","native_role","additional executed equivalence proof"); }
}
