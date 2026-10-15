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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Complete recorded population and diagnostic witnesses; never reruns replicas or repairs observations. */
public final class FaultSchedulesAnalysis {
    private FaultSchedulesAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); }public String id(){return fields.get("id");} }
    public static List<FaultSchedules.Config> configurations(boolean includeForbidden) { var out=new ArrayList<FaultSchedules.Config>();for(String p:FaultSchedules.POLICIES)for(int size=1;size<=3;size++)for(int mask=1;mask<8;mask++){if(Integer.bitCount(mask)!=size)continue;var f=new ArrayList<Byte>();for(byte n=0;n<3;n++)if((mask&(1<<n))!=0)f.add(n);for(byte c=0;c<4;c++)for(String k:List.of("crash","partition"))for(String retry:List.of("stable","fresh")){var cfg=new FaultSchedules.Config(p,f,c,k,retry);if(includeForbidden||!cfg.forbidden())out.add(cfg);}}return List.copyOf(out); }
    public static Set<String> expectedIds(){return Collections.unmodifiableSet(new TreeSet<>(configurations(false).stream().map(FaultSchedules.Config::caseId).toList()));}
    public static List<String> rejectedIds(){return configurations(true).stream().filter(FaultSchedules.Config::forbidden).map(FaultSchedules.Config::caseId).sorted().toList();}
    static Object value(Observed r,String name){String raw=r.fields().get(name);if(raw==null)throw new IllegalArgumentException("missing D15 record field "+name);return Json.parse(raw,"recorded D15 "+name);}
    private static List<Byte> failed(Observed r){var out=new ArrayList<Byte>();for(Object n:FaultSchedulesOracle.list(value(r,"failed"))){int v=FaultSchedulesOracle.integer(n);if(v<0||v>2)throw new IllegalArgumentException("invalid recorded node");out.add((byte)v);}return List.copyOf(out);}
    private static TreeMap<String,Observed> bank(List<Observed> records){var out=new TreeMap<String,Observed>();for(var r:records){var f=r.fields();int cut=FaultSchedulesOracle.integer(value(r,"cut"));if(cut<0||cut>3)throw new IllegalArgumentException("invalid recorded cut");var cfg=new FaultSchedules.Config(f.get("policy"),failed(r),(byte)cut,f.get("kind"),f.get("retry"));if(cfg.forbidden()||!cfg.caseId().equals(r.id())||!failed(r).equals(cfg.canonicalFailed())||!List.of("PASS","DOMAIN_FAIL").contains(f.get("verdict"))||out.putIfAbsent(r.id(),r)!=null)throw new IllegalArgumentException("unknown/duplicate/conflicting/non-domain D15 support");}if(!out.keySet().equals(expectedIds()))throw new IllegalArgumentException("complete312case D15 support required");return out;}
    private static boolean check(Observed r,String k){var checks=FaultSchedulesOracle.object(value(r,"checks"));if(!checks.keySet().equals(new TreeSet<>(FaultSchedulesOracle.CHECKS))||!(checks.get(k)instanceof Boolean b))throw new IllegalArgumentException("all seven recorded checks required");return b;}
    private static void tally(Map<String,Integer> m,String k){m.merge(k,1,Integer::sum);}
    public static List<String> restartIds(){return List.of("P=replay_twice|F=012|C=2|K=crash|R=fresh","P=volatile_ack|F=012|C=3|K=crash|R=stable");}
    public static Map<String,Object> analyse(List<Observed> records){var bank=bank(records);var totals=new TreeMap<String,Integer>();var policies=new TreeMap<String,Map<String,Integer>>();var strata=new TreeMap<String,Map<String,Map<String,Map<String,Integer>>>>();var failedChecks=new TreeMap<String,Map<String,Integer>>();var fresh=new TreeMap<String,Integer>();var lost=new ArrayList<String>();var agreeWrong=new ArrayList<String>();int attempts=0,starts=0,crashes=0;
        for(String p:FaultSchedules.POLICIES){policies.put(p,new TreeMap<>());fresh.put(p,0);var kinds=new TreeMap<String,Map<String,Map<String,Integer>>>();for(String k:List.of("crash","partition")){var retries=new TreeMap<String,Map<String,Integer>>();for(String retry:List.of("stable","fresh"))retries.put(retry,new TreeMap<>());kinds.put(k,retries);}strata.put(p,kinds);var checks=new TreeMap<String,Integer>();FaultSchedulesOracle.CHECKS.forEach(k -> checks.put(k,0));failedChecks.put(p,checks);}
        for(var row:bank.values()){var f=row.fields();String p=f.get("policy"),kind=f.get("kind"),retry=f.get("retry"),v=f.get("verdict");tally(totals,v);tally(policies.get(p),v);tally(strata.get(p).get(kind).get(retry),v);for(String k:FaultSchedulesOracle.CHECKS)if(!check(row,k))failedChecks.get(p).merge(k,1,Integer::sum);if(!v.equals("PASS")&&retry.equals("fresh")&&failed(row).size()==2&&FaultSchedulesOracle.integer(value(row,"cut"))<3)fresh.merge(p,1,Integer::sum);if(!check(row,"acknowledged_survive_repair"))lost.add(row.id());if(check(row,"replicas_agree")&&!check(row,"matches_fault_free_reference"))agreeWrong.add(row.id());attempts=Math.addExact(attempts,FaultSchedulesOracle.list(value(row,"attempts")).size());var lifecycle=FaultSchedulesOracle.object(value(row,"local_lifecycle"));starts=Math.addExact(starts,FaultSchedulesOracle.integer(lifecycle.get("replica_starts")));crashes=Math.addExact(crashes,FaultSchedulesOracle.integer(lifecycle.get("crash_transitions")));}
        var witnesses=new ArrayList<Map<String,Object>>();for(var e:List.of(Map.entry("durable_stable_partial_quorum_converges","P=durable|F=01|C=1|K=crash|R=stable"),Map.entry("durable_fresh_key_duplicates_uncertain_op","P=durable|F=01|C=1|K=partition|R=fresh"),Map.entry("volatile_ack_loses_acknowledged_work","P=volatile_ack|F=012|C=2|K=crash|R=stable"),Map.entry("replay_twice_single_node_double_effects","P=replay_twice|F=0|C=3|K=crash|R=stable"),Map.entry("replay_twice_uniform_wrong_state_agrees","P=replay_twice|F=012|C=3|K=crash|R=stable"))){var row=bank.get(e.getValue());var trace=FaultSchedulesOracle.list(value(row,"trace"));var finalNodes=FaultSchedulesOracle.list(FaultSchedulesOracle.object(trace.getLast()).get("nodes"));var finalView=new ArrayList<Map<String,Object>>();for(Object raw:finalNodes){var n=FaultSchedulesOracle.object(raw);var view=new TreeMap<String,Object>();for(String k:List.of("node","generation","accepted","wal","effects","value"))view.put(k,n.get(k));finalView.add(view);}witnesses.add(FaultSchedules.fields("label",e.getKey(),"case_id",row.id(),"attempts",value(row,"attempts"),"final",finalView,"checks",value(row,"checks"),"verdict",row.fields().get("verdict")));}
        return FaultSchedules.freeze(FaultSchedules.fields("totals",totals,"by_policy",policies,"strata",strata,"failed_checks",failedChecks,"counts",FaultSchedules.fields("attempts",attempts,"replica_starts",starts,"crash_transitions",crashes),"rejected_ids",rejectedIds(),"mechanisms",FaultSchedules.fields("fresh_key_duplicates",fresh,"lost_acknowledged",lost,"replicas_agree_but_wrong",agreeWrong),"witnesses",witnesses));
    }
    public static List<Map<String,Object>> controls(){return FaultSchedules.POLICIES.stream().map(FaultSchedules::control).toList();}
}
