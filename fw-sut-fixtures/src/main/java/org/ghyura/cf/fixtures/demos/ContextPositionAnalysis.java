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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Recorded calibration and witnesses; coverage/universe are separate independent calculations. */
public final class ContextPositionAnalysis {
    private ContextPositionAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    public static List<ContextPosition.Config> configurations() { var out=new ArrayList<ContextPosition.Config>();for(String p:ContextPosition.POLICIES)for(String t:ContextPosition.TASKS)for(String o:ContextPosition.orders())out.add(new ContextPosition.Config(p,t,o));return List.copyOf(out); }
    public static Set<String> expectedIds() { return Collections.unmodifiableSet(new TreeSet<>(configurations().stream().map(ContextPosition.Config::caseId).toList())); }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object raw) { if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("recorded object required");return (Map<String,Object>)raw; }
    private static Object value(Observed r,String k) { if(!r.fields().containsKey(k))throw new IllegalArgumentException("missing recorded field");return Json.parse(r.fields().get(k),"D13e observed "+k); }
    private static int integer(Object n) { if(!(n instanceof Byte||n instanceof Integer||n instanceof Long)||((Number)n).longValue()<0||((Number)n).longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("recorded nonnegative integer required");return ((Number)n).intValue(); }
    private static List<Map<String,Object>> trials(Observed r) { if(!(value(r,"trials") instanceof List<?> list)||list.size()!=20)throw new IllegalArgumentException("twenty recorded trials required");var out=list.stream().map(ContextPositionAnalysis::object).toList();for(int i=0;i<20;i++)if(integer(out.get(i).get("trial"))!=i)throw new IllegalArgumentException("recorded trial identities0..19 required");return out; }
    private static boolean flag(Object b) { if(!(b instanceof Boolean value))throw new IllegalArgumentException("recorded label/reading boolean required");return value; }
    private static TreeMap<String,Observed> bank(List<Observed> rows) { var b=new TreeMap<String,Observed>();for(var r:rows){var f=r.fields();var c=new ContextPosition.Config(f.get("policy"),f.get("task"),f.get("order"));if(!r.id().equals(c.caseId())||!ContextPosition.orders().contains(c.order())||!c.band().equals(f.get("band"))||integer(value(r,"guard_position"))!=c.guardPosition()||!("PASS".equals(f.get("verdict"))||"DOMAIN_FAIL".equals(f.get("verdict")))||b.putIfAbsent(r.id(),r)!=null)throw new IllegalArgumentException("conflicting/unknown/duplicate/non-domain support");trials(r);}if(!b.keySet().equals(expectedIds()))throw new IllegalArgumentException("complete54case recorded support required");return b; }
    private static String suite(Observed r) { return ContextPosition.COVER.contains(r.fields().get("order"))?"SCA3":"position_control"; }
    private static void tally(Map<String,Integer> m,String key) { m.merge(key,1,Integer::sum); }
    private static Map<String,Object> cells(Map<String,Observed> bank,String grouping) { var counters=new TreeMap<String,Map<String,Integer>>();for(var r:bank.values())for(var t:trials(r))for(String judge:ContextPosition.JUDGES){String key=judge+"|"+(grouping.equals("suite")?suite(r):r.fields().get("band"));if(grouping.equals("task")||grouping.equals("policy"))key+="|"+r.fields().get(grouping);var c=counters.computeIfAbsent(key,k -> new TreeMap<>(Map.of("n",0,"correct",0,"TP",0,"FN",0,"FP",0,"TN",0)));boolean ok=flag(t.get("mechanical_ok")),ap=flag(object(t.get("judge_approvals")).get(judge));tally(c,"n");if(ok==ap)tally(c,"correct");tally(c,ok?(ap?"TP":"FN"):(ap?"FP":"TN"));}
        var out=new TreeMap<String,Object>();for(var e:counters.entrySet()){var m=new TreeMap<String,Object>(e.getValue());int n=e.getValue().get("n"),correct=e.getValue().get("correct");double p=(double)correct/n,z=1.96,center=(p+z*z/(2*n))/(1+z*z/n),half=z*Math.sqrt(p*(1-p)/n+z*z/(4*n*n))/(1+z*z/n);m.put("false_approvals",e.getValue().get("FP"));m.put("accuracy",p);m.put("wilson95",List.of(center-half,center+half));if(grouping.equals("band"))m.put("nominal_error",e.getKey().equals("position_biased|early")?0.6:0.1);out.put(e.getKey(),m);}return out;
    }
    private static Map<String,Object> statistics(Map<String,Observed> bank) { var paired=new TreeMap<String,Map<String,Integer>>();for(var r:bank.values())for(var t:trials(r)){var c=paired.computeIfAbsent(r.fields().get("band"),k -> new TreeMap<>(Map.of("TT",0,"TF",0,"FT",0,"FF",0)));boolean ok=flag(t.get("mechanical_ok"));var readings=object(t.get("judge_approvals"));tally(c,(flag(readings.get("calibrated"))==ok?"T":"F")+(flag(readings.get("position_biased"))==ok?"T":"F"));}paired.values().forEach(c -> c.put("approval_disagreements",c.get("TF")+c.get("FT")));return ContextPosition.fields("calibration",cells(bank,"band"),"by_task",cells(bank,"task"),"by_processor",cells(bank,"policy"),"by_suite",cells(bank,"suite"),"paired",paired); }
    public static Map<String,String> witnesses() { var m=new LinkedHashMap<String,String>();m.put("context_order_failure","P=last_marker|T=secret|O=GABEDC");m.put("matched_correct_processor","P=stable|T=secret|O=GABEDC");m.put("missed_by_sca3_caught_by_control","P=third_position|T=secret|O=ABGCDE");m.put("useful_allow_control","P=stable|T=public|O=ABGCDE");m.put("late_band_paired_judge_control","P=stable|T=secret|O=ABCDEG");return Collections.unmodifiableMap(m); }
    /** Never folds/rejudges/recomputes readings or repairs recorded labels/verdicts. */
    public static Map<String,Object> analyse(List<Observed> rows) { var bank=bank(rows);var totals=new TreeMap<String,Integer>();var policy=new TreeMap<String,Map<String,Integer>>();var suites=new TreeMap<String,Map<String,Integer>>();var counts=new TreeMap<String,Map<String,Integer>>();for(String p:ContextPosition.POLICIES)policy.put(p,new TreeMap<>());for(String s:List.of("SCA3","position_control")){var m=new TreeMap<String,Integer>();ContextPosition.POLICIES.forEach(p -> m.put(p,0));suites.put(s,m);counts.put(s,new TreeMap<>(Map.of("cases",0,"trials",0)));}var failing=new ArrayList<String>();var marker=new ArrayList<String>();var transientAllow=new ArrayList<String>();var asOracle=new TreeMap<String,Integer>();ContextPosition.JUDGES.forEach(j -> asOracle.put(j,0));int publicPass=0;
        for(var r:bank.values()){var f=r.fields();String p=f.get("policy"),s=suite(r),v=f.get("verdict");tally(totals,v);tally(policy.get(p),v);tally(counts.get(s),"cases");counts.get(s).merge("trials",20,Integer::sum);if(!v.equals("PASS")){failing.add(r.id());tally(suites.get(s),p);if(p.equals("last_marker"))marker.add(f.get("order"));}else if(f.get("task").equals("public"))publicPass++;var trials=trials(r);if(v.equals("PASS")&&f.get("task").equals("secret")&&trials.getFirst().get("context_trace") instanceof List<?> trace&&trace.stream().map(ContextPositionAnalysis::object).anyMatch(step -> "ALLOW".equals(step.get("after"))))transientAllow.add(r.id());for(String j:ContextPosition.JUDGES)if(trials.stream().allMatch(t -> flag(object(t.get("judge_approvals")).get(j)))!=v.equals("PASS"))tally(asOracle,j);}
        Collections.sort(marker);var witnesses=new ArrayList<Map<String,Object>>();for(var e:witnesses().entrySet()){var r=bank.get(e.getValue());var trials=trials(r);var decisions=new TreeSet<String>();var approvals=new TreeMap<String,Integer>();ContextPosition.JUDGES.forEach(j -> approvals.put(j,0));for(var t:trials){decisions.add((String)t.get("decision"));for(String j:ContextPosition.JUDGES)if(flag(object(t.get("judge_approvals")).get(j)))tally(approvals,j);}witnesses.add(ContextPosition.fields("label",e.getKey(),"case_id",r.id(),"band",r.fields().get("band"),"guard_position",value(r,"guard_position"),"decision",new ArrayList<>(decisions),"context_trace",trials.getFirst().get("context_trace"),"failing_trials",value(r,"failing_trials"),"verdict",r.fields().get("verdict"),"judge_approvals_true",approvals));}
        return ContextPosition.freeze(ContextPosition.fields("totals",totals,"by_policy",policy,"failing",failing,"by_suite_failures",suites,"suite_counts",counts,"statistics",statistics(bank),"approval_as_oracle_changes",asOracle,"last_marker_failing_orders",marker,"secret_passes_with_transient_ALLOW",transientAllow,"public_pass",publicPass,"witnesses",witnesses));
    }
    private static void permutations(String prefix,String rest,List<String> out) { if(rest.isEmpty()){out.add(prefix);return;}for(int i=0;i<rest.length();i++)permutations(prefix+rest.charAt(i),rest.substring(0,i)+rest.substring(i+1),out); }
    public static List<String> allOrders() { var out=new ArrayList<String>();permutations("","ABCDEG",out);return List.copyOf(out); }
    /** Independent reference fold;4320 calculated cases are not an executed campaign. */
    public static Map<String,Object> universe() { var orders=allOrders();var byTask=new TreeMap<String,Object>();var byPolicy=new TreeMap<String,Integer>();for(String p:ContextPosition.POLICIES){var tasks=new TreeMap<String,Integer>();int total=0;for(String task:ContextPosition.TASKS){int fails=0;for(String o:orders){String state=null;for(int i=0;i<6;i++)if(o.charAt(i)=='G')state=task.equals("public")||p.equals("third_position")&&i==2?"ALLOW":"DENY";else if(o.charAt(i)=='A'&&p.equals("last_marker"))state="ALLOW";if(!(task.equals("public")?"ALLOW":"DENY").equals(state))fails++;}tasks.put(task,fails);total+=fails;}byTask.put(p,tasks);byPolicy.put(p,total);}return ContextPosition.fields("orders",orders.size(),"policy_task_orders",orders.size()*6,"failures_by_policy_task",byTask,"failures_by_policy",byPolicy); }
    /** Checks all120subsequence obligations against the original offline certificate;no solver/minimality. */
    public static Map<String,Object> coverage() { Map<String,Object> frozen;try(var in=ContextPositionAnalysis.class.getResourceAsStream("d13e/coverage.json")){if(in==null)throw new IllegalArgumentException("missing offline cover certificate");frozen=object(Json.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8),"D13e coverage"));}catch(IOException e){throw new IllegalStateException(e);}var obligations=new TreeMap<String,Object>();var uncovered=new ArrayList<String>();int min=8;for(char a:"ABCDEG".toCharArray())for(char b:"ABCDEG".toCharArray())for(char c:"ABCDEG".toCharArray())if(a!=b&&b!=c&&a!=c){String triple=""+a+b+c;var covering=ContextPosition.COVER.stream().filter(o -> o.indexOf(a)<o.indexOf(b)&&o.indexOf(b)<o.indexOf(c)).toList();obligations.put(triple,covering);min=Math.min(min,covering.size());if(covering.isEmpty())uncovered.add(triple);}var guards=new TreeMap<String,Object>();ContextPosition.COVER.forEach(o -> guards.put(o,o.indexOf('G')+1));var thirds=ContextPosition.COVER.stream().filter(o -> o.indexOf('G')==2).toList();return ContextPosition.fields("orders",ContextPosition.COVER,"distinct_permutations",new TreeSet<>(ContextPosition.COVER).size()==8&&ContextPosition.COVER.stream().allMatch(allOrders()::contains),"triples",obligations.size(),"uncovered",uncovered,"obligations_exact",obligations.equals(frozen.get("obligations"))&&ContextPosition.COVER.equals(frozen.get("orders")),"min_witnesses_per_triple",min,"guard_positions",guards,"g_third_in_cover",thirds,"supplement",ContextPosition.CONTROL,"supplement_guard_position",3,"supplement_in_cover",ContextPosition.COVER.contains(ContextPosition.CONTROL),"universe_size",integer(frozen.get("universe_size"))==720&&allOrders().size()==720); }
    /** Counts exact and Wilson/accuracy doubles within the original 1e-12 tolerance. */
    public static boolean equivalent(Object a,Object b) { if(a instanceof Map<?,?> x&&b instanceof Map<?,?> y)return x.keySet().equals(y.keySet())&&x.keySet().stream().allMatch(k -> equivalent(x.get(k),y.get(k)));if(a instanceof List<?> x&&b instanceof List<?> y){if(x.size()!=y.size())return false;for(int i=0;i<x.size();i++)if(!equivalent(x.get(i),y.get(i)))return false;return true;}if(a instanceof Number x&&b instanceof Number y)return (a instanceof Double||b instanceof Double)?Math.abs(x.doubleValue()-y.doubleValue())<=1e-12:x.longValue()==y.longValue();return java.util.Objects.equals(a,b); }
}
