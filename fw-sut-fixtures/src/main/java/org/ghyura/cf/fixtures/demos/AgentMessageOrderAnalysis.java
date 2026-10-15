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

/** Complete recorded-value strata/mechanisms/witnesses and independent schedule exploration. */
public final class AgentMessageOrderAnalysis {
    private AgentMessageOrderAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    public static List<String> words() { var words=new ArrayList<String>();for(int i=0;i<16;i++){var s=new StringBuilder();for(int j=3;j>=0;j--)s.append((i&(1<<j))==0?'A':'B');words.add(s.toString());}return List.copyOf(words); }
    private static boolean twoEach(String s) { return s.chars().filter(c -> c=='A').count()==2; }
    private static byte expression(String s) { boolean a=s.charAt(0)=='A',b=s.charAt(1)=='A',c=s.charAt(2)=='A',d=s.charAt(3)=='A';return (byte)((a!=b?1:0)+(b!=c&&a!=b?1:0)+(c!=d&&a!=c&&b!=c?1:0)); }
    private static byte prefixPreemptions(String s) { byte n=0;var counts=new TreeMap<Character,Byte>();counts.put('A',(byte)0);counts.put('B',(byte)0);for(int i=0;i<4;i++){char a=s.charAt(i);counts.put(a,(byte)(counts.get(a)+1));if(i<3&&a!=s.charAt(i+1)&&counts.get(a)==1)n++;}return n; }
    private static byte switches(String s) { byte n=0;for(int i=1;i<4;i++)if(s.charAt(i)!=s.charAt(i-1))n++;return n; }
    public static boolean included(String mode,byte cap,String word) { return twoEach(word)&&(mode.equals("independent")||word.charAt(0)=='A')&&expression(word)<=cap; }
    public static Set<String> expectedIds() { var ids=new TreeSet<String>();for(String p:AgentMessageOrder.POLICIES)for(String m:AgentMessageOrder.MODES)for(byte k=0;k<=2;k++)for(String s:words())if(included(m,k,s))ids.add(new AgentMessageOrder.Config(p,m,k,s).caseId());return Collections.unmodifiableSet(ids); }
    public static Map<String,String> witnesses() { var m=new LinkedHashMap<String,String>();m.put("trust_offer_double_allocation","P=trust_offer|M=independent|K=2|S=ABAB");m.put("overwrite_capacity_passes_promises_fail","P=overwrite_owner|M=independent|K=2|S=ABAB");m.put("compare_version_stale_refusal","P=compare_version|M=independent|K=2|S=ABAB");m.put("overwrite_withdraws_earlier_B_promise","P=overwrite_owner|M=after_A_offer|K=1|S=ABBA");m.put("serial_busy_control","P=trust_offer|M=independent|K=0|S=AABB");return Collections.unmodifiableMap(m); }
    public static AgentMessageOrder.Config configuration(Observed row) { var f=row.fields();return new AgentMessageOrder.Config(f.get("policy"),f.get("mode"),Byte.parseByte(f.get("cap")),f.get("schedule")); }
    private static Object value(Observed row,String key) { String v=row.fields().get(key);if(v==null)throw new IllegalArgumentException("missing observed D13c "+key);return Json.parse(v,"observed D13c "+key); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> objects(Observed row,String key) { Object v=value(row,key);if(!(v instanceof List<?> xs)||xs.stream().anyMatch(x -> !(x instanceof Map<?,?>)))throw new IllegalArgumentException("observed D13c event list required");return (List<Map<String,Object>>)v; }
    @SuppressWarnings("unchecked") private static boolean status(Map<String,Object> event,String status) { return status.equals(((Map<String,Object>)event.get("response")).get("status")); }
    private static void tally(Map<String,Integer> c,String key) { c.merge(key,1,Integer::sum); }
    public static Map<String,Object> analyse(List<Observed> observations) {
        var bank=new TreeMap<String,Observed>();var totals=new TreeMap<String,Integer>();var policies=new TreeMap<String,Map<String,Integer>>();var caps=new TreeMap<String,Map<String,Integer>>();var strata=new TreeMap<String,Map<String,Integer>>();var denominators=new TreeMap<String,Integer>();var schedules=new TreeMap<String,TreeSet<String>>();int trust=0,overwrite=0,stale=0,busy=0;boolean useful=true;
        for(String p:AgentMessageOrder.POLICIES){policies.put(p,new TreeMap<>());for(String m:AgentMessageOrder.MODES)for(byte k=0;k<=2;k++)strata.put(p+"|"+m+"|K="+k,new TreeMap<>());}for(byte k=0;k<=2;k++)caps.put(Byte.toString(k),new TreeMap<>());for(String m:AgentMessageOrder.MODES)for(byte k=0;k<=2;k++){denominators.put(m+"|K="+k,0);schedules.put(m+"|K="+k,new TreeSet<>());}var expected=expectedIds();
        for(var row:observations){var c=configuration(row);String v=row.fields().get("verdict");if(!c.caseId().equals(row.id())||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null||!("PASS".equals(v)||"DOMAIN_FAIL".equals(v)))throw new IllegalArgumentException("unknown/duplicate/conflicting/non-domain D13c support");tally(totals,v);tally(policies.get(c.policy()),v);tally(caps.get(Byte.toString(c.cap())),v);tally(strata.get(c.policy()+"|"+c.mode()+"|K="+c.cap()),v);String stratum=c.mode()+"|K="+c.cap();denominators.merge(stratum,1,Integer::sum);schedules.get(stratum).add(c.schedule());if(v.equals("DOMAIN_FAIL")&&c.policy().equals("trust_offer"))trust++;if(v.equals("DOMAIN_FAIL")&&c.policy().equals("overwrite_owner"))overwrite++;var trace=objects(row,"observed_trace");if(trace.stream().anyMatch(e -> status(e,"STALE")))stale++;if(trace.stream().anyMatch(e -> status(e,"BUSY")))busy++;useful&=objects(row,"reference_trace").stream().filter(e -> status(e,"GRANTED")).count()==1;}
        if(!bank.keySet().equals(expected))throw new IllegalArgumentException("complete original 54-case D13c support required");var witnesses=new ArrayList<Map<String,Object>>();for(var w:witnesses().entrySet()){var row=bank.get(w.getValue());var out=new TreeMap<String,Object>();out.put("label",w.getKey());for(String k:List.of("case_id","schedule","verdict"))out.put(k,row.fields().get(k));for(String k:List.of("observed_trace","checks","failing_checkpoints"))out.put(k,value(row,k));witnesses.add(out);}var scheduleLists=new TreeMap<String,Object>();schedules.forEach((k,v) -> scheduleLists.put(k,new ArrayList<>(v)));
        return AgentMessageOrder.freeze(AgentMessageOrder.fields("totals",totals,"by_policy",policies,"by_cap",caps,"strata",strata,"denominators",denominators,"schedules",scheduleLists,"mechanisms",AgentMessageOrder.fields("trust_offer_fail",trust,"overwrite_owner_fail",overwrite,"records_with_STALE",stale,"records_with_BUSY",busy,"reference_grants_exactly_one",useful),"witnesses",witnesses));
    }
    private static Map<String,Object> explore(String word,String mode) { var counts=new TreeMap<String,Byte>();counts.put("A",(byte)0);counts.put("B",(byte)0);for(byte i=0;i<4;i++){String a=Character.toString(word.charAt(i));var ready=new ArrayList<String>();for(String next:List.of("A","B"))if(counts.get(next)<2&&!(next.equals("B")&&mode.equals("after_A_offer")&&counts.get("B")==0&&counts.get("A")==0))ready.add(next);if(!ready.contains(a))return AgentMessageOrder.fields("status","disabled","at",(byte)(i+1),"agent",a,"enabled",ready);counts.put(a,(byte)(counts.get(a)+1));}return Map.of("status","feasible"); }
    public static Map<String,Object> exploration() {
        var enabled=new TreeMap<String,Object>();var feasible=new TreeMap<String,List<String>>();var excluded=new TreeMap<String,Object>();var sets=new TreeMap<String,Object>();var wordCerts=new TreeMap<String,Object>();var examples=new TreeMap<String,Object>();boolean same=true;
        for(String mode:AgentMessageOrder.MODES){var cases=new TreeMap<String,Object>();var legal=new ArrayList<String>();for(String w:words()){var result=explore(w,mode);cases.put(w,result);if(result.get("status").equals("feasible"))legal.add(w);else if(mode.equals("after_A_offer")&&twoEach(w))excluded.put(w,result);}enabled.put(mode,cases);feasible.put(mode,legal);var caps=new TreeMap<String,Object>();for(byte k=0;k<=2;k++){var kept=new ArrayList<String>();for(String w:legal)if(prefixPreemptions(w)<=k)kept.add(w);caps.put(Byte.toString(k),kept);}sets.put(mode,caps);}
        for(String w:words()){same&=expression(w)==prefixPreemptions(w);wordCerts.put(w,AgentMessageOrder.fields("two_each",twoEach(w),"prefix_preemptions",prefixPreemptions(w),"expression",expression(w),"context_switches",switches(w)));}for(String w:List.of("AABB","ABBA","ABAB"))examples.put(w,List.of(switches(w),prefixPreemptions(w)));
        var hits=new TreeMap<String,Integer>();for(String rule:List.of("two_each","causal_ready","preemption_cap"))hits.put(rule,0);int overlap=0,removals=0;int[] sequential=new int[4];var population=new ArrayList<String>();
        for(String p:AgentMessageOrder.POLICIES)for(String m:AgentMessageOrder.MODES)for(byte k=0;k<=2;k++)for(String w:words()){boolean t=twoEach(w),c=m.equals("independent")||w.charAt(0)=='A',b=expression(w)<=k;int n=(t?0:1)+(c?0:1)+(b?0:1);sequential[0]++;if(!t)hits.merge("two_each",1,Integer::sum);if(!c)hits.merge("causal_ready",1,Integer::sum);if(!b)hits.merge("preemption_cap",1,Integer::sum);if(t)sequential[1]++;if(t&&c)sequential[2]++;if(t&&c&&b){sequential[3]++;population.add(new AgentMessageOrder.Config(p,m,k,w).caseId());}if(n>0)removals++;if(n>1)overlap++;}Collections.sort(population);
        var two=words().stream().filter(AgentMessageOrderAnalysis::twoEach).toList();return AgentMessageOrder.freeze(AgentMessageOrder.fields("enabledness",enabled,"feasible",feasible,"independent_feasible_equals_two_each",feasible.get("independent").equals(two),"after_A_offer_feasible_equals_first_letter_A",feasible.get("after_A_offer").equals(two.stream().filter(w -> w.charAt(0)=='A').toList()),"after_A_offer_excluded",excluded,"words",wordCerts,"expression_equals_prefix_count_on_two_each",same,"expression_equals_prefix_count_on_all_16",same,"contract_examples",examples,"schedules_by_mode_cap",sets,"truth_table",AgentMessageOrder.fields("raw",288,"per_rule",hits,"overlap",overlap,"unique_removals",removals,"sequential",java.util.Arrays.stream(sequential).boxed().toList()),"population",population));
    }
}
