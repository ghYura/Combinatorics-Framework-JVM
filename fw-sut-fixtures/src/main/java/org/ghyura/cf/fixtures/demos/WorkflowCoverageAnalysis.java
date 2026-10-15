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

/** Offline coverage certificates and detection measurements from complete recorded observations. */
public final class WorkflowCoverageAnalysis {
    private WorkflowCoverageAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    private static void permutations(String prefix,String rest,Set<String> out) { if(rest.isEmpty()){out.add(prefix);return;}for(int i=0;i<rest.length();i++)permutations(prefix+rest.charAt(i),rest.substring(0,i)+rest.substring(i+1),out); }
    public static List<WorkflowCoverage.Config> configurations() { var orders=new TreeSet<String>();permutations("",WorkflowCoverage.EVENTS,orders);var out=new ArrayList<WorkflowCoverage.Config>();for(String p:WorkflowCoverage.POLICIES)for(String o:orders)out.add(new WorkflowCoverage.Config(p,o));return List.copyOf(out); }
    public static Set<String> expectedIds() { return Collections.unmodifiableSet(new TreeSet<>(configurations().stream().map(WorkflowCoverage.Config::caseId).toList())); }
    public static Object value(Observed row,String key) { String s=row.fields().get(key);if(s==null)throw new IllegalArgumentException("missing observed D3C "+key);return Json.parse(s,"observed D3C "+key); }
    private static List<String> strings(Object x) { return WorkflowCoverageOracle.list(x).stream().map(v -> {if(!(v instanceof String s))throw new IllegalArgumentException("text list required");return s;}).toList(); }
    public static Map<String,Object> coverageAsset() { try(var in=WorkflowCoverageAnalysis.class.getResourceAsStream("d3c/coverage.json")){if(in==null)throw new IllegalStateException("missing D3C coverage asset");return WorkflowCoverageOracle.object(Json.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8),"D3C certificates"));}catch(IOException e){throw new IllegalStateException("cannot read D3C certificates",e);} }
    public static List<String> catalogueRows() { return strings(WorkflowCoverageOracle.object(WorkflowCoverageOracle.object(coverageAsset().get("suites")).get("SCA3")).get("rows")); }
    public static Map<String,Object> catalogue() { return WorkflowCoverage.fields("rows",catalogueRows(),"planned_candidates",20,"executed",false); }
    public static Set<String> obligations(String row,String family) {
        var out=new TreeSet<String>();if(family.equals("SCA2")||family.equals("SCA3")){for(int i=0;i<row.length();i++)for(int j=i+1;j<row.length();j++){if(family.equals("SCA2"))out.add(""+row.charAt(i)+row.charAt(j));else for(int k=j+1;k<row.length();k++)out.add(""+row.charAt(i)+row.charAt(j)+row.charAt(k));}}else if(family.equals("ADJ2")||family.equals("PROJ")){String seq=family.equals("PROJ")?row.chars().filter(c -> "CFQS".indexOf(c)>=0).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString():row;for(int i=0;i<seq.length()-1;i++)out.add(seq.substring(i,i+2));}else throw new IllegalArgumentException("unknown coverage family");return Collections.unmodifiableSet(out);
    }
    private static Set<String> required(String family) { String events=family.equals("PROJ")?"CFQS":WorkflowCoverage.EVENTS;var orders=new TreeSet<String>();permutations("",events,orders);var out=new TreeSet<String>();for(String row:orders)out.addAll(obligations(row,family));return out; }
    /** Checks coverage and row witnesses; never reads predicted_fault_orders or uses a solver. */
    public static Map<String,Object> measureSuites(Map<String,Observed> bank,Map<String,Object> suites) {
        var sizes=Map.of("SCA2",2,"SCA3",10,"ADJ2",9,"PROJ",5,"SCA3_counterexample",10);if(!suites.keySet().equals(sizes.keySet()))throw new IllegalArgumentException("five original coverage suites required");var out=new TreeMap<String,Object>();
        for(var entry:suites.entrySet()){String name=entry.getKey();var s=WorkflowCoverageOracle.object(entry.getValue());String family=(String)s.get("family");if(!family.equals(name.equals("SCA3_counterexample")?"SCA3":name))throw new IllegalArgumentException("conflicting coverage family");var rows=strings(s.get("rows"));if(rows.size()!=sizes.get(name)||new TreeSet<>(rows).size()!=rows.size()||WorkflowCoverageOracle.integer(s.get("size"))!=rows.size())throw new IllegalArgumentException("invalid suite size");var need=required(family);var specified=strings(s.get("required_obligations"));if(specified.size()!=need.size()||!new TreeSet<>(specified).equals(need))throw new IllegalArgumentException("conflicting obligations");var covered=new TreeSet<String>();for(String row:rows){new WorkflowCoverage.Config("correct",row);covered.addAll(obligations(row,family));}var cert=WorkflowCoverageOracle.object(s.get("certificate"));if(!cert.keySet().equals(need)||!covered.containsAll(need))throw new IllegalArgumentException("incomplete coverage certificate");for(var c:cert.entrySet())if(!(c.getValue()instanceof String row)||!rows.contains(row)||!obligations(row,family).contains(c.getKey()))throw new IllegalArgumentException("invalid certificate witness");var detected=new TreeSet<String>();var correctFail=new ArrayList<String>();for(String row:rows){var late=bank.get("C|late_restart|OPS="+row);var good=bank.get("C|correct|OPS="+row);if(late==null||good==null)throw new IllegalArgumentException("missing coverage observations");if(late.fields().get("verdict").equals("DOMAIN_FAIL"))detected.add(row);if(!good.fields().get("verdict").equals("PASS"))correctFail.add(row);}covered.retainAll(need);out.put(name,WorkflowCoverage.fields("size",rows.size(),"obligations",need.size(),"covered",covered.size(),"certificate_entries_valid",true,"measured_late_restart_detections",new ArrayList<>(detected),"correct_failures",correctFail));}return WorkflowCoverage.freeze(out);
    }
    public static Map<String,String> witnesses() { var m=new LinkedHashMap<String,String>();m.put("late_restart_heals","C|late_restart|OPS=CNFSQV");m.put("late_restart_persists","C|late_restart|OPS=QCNFSV");m.put("late_restart_nontrigger","C|late_restart|OPS=CFNSQV");m.put("positive_control","C|correct|OPS=CNFSQV");return Collections.unmodifiableMap(m); }
    private static void tally(Map<String,Integer> map,String verdict) { if(!List.of("PASS","DOMAIN_FAIL").contains(verdict))throw new IllegalArgumentException("domain verdict required");map.merge(verdict,1,Integer::sum); }
    /** Complete known coordinate-matched support; no SUT/reference execution, rejudging, or repairs. */
    public static Map<String,Object> analyse(List<Observed> observations) {
        var expected=expectedIds();var bank=new TreeMap<String,Observed>();var totals=new TreeMap<String,Integer>();var byPolicy=new TreeMap<String,Map<String,Integer>>();WorkflowCoverage.POLICIES.forEach(p -> byPolicy.put(p,new TreeMap<>()));var finalOnly=new TreeMap<String,Integer>();var faults=new TreeSet<String>();var hidden=new TreeSet<String>();
        for(var row:observations){String order=String.join("",strings(value(row,"ops")));var c=new WorkflowCoverage.Config(row.fields().get("policy"),order);if(!c.caseId().equals(row.id())||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null)throw new IllegalArgumentException("unknown/duplicate/conflicting D3C observation");String verdict=row.fields().get("verdict");tally(totals,verdict);tally(byPolicy.get(c.policy()),verdict);tally(finalOnly,row.fields().get("final_only_verdict"));Object hide=value(row,"hidden_by_final_only");if(!(hide instanceof Boolean))throw new IllegalArgumentException("Boolean hidden indicator required");if(Boolean.TRUE.equals(hide))hidden.add(order);if(verdict.equals("DOMAIN_FAIL"))faults.add(order);}
        if(!bank.keySet().equals(expected))throw new IllegalArgumentException("complete original 1440-case D3C support required");var measured=measureSuites(bank,WorkflowCoverageOracle.object(coverageAsset().get("suites")));var witnesses=new ArrayList<Map<String,Object>>();
        for(var e:witnesses().entrySet()){var row=bank.get(e.getValue());var brief=new ArrayList<Map<String,Object>>();for(Object x:WorkflowCoverageOracle.list(value(row,"checkpoints"))){var cp=WorkflowCoverageOracle.object(x);brief.add(WorkflowCoverage.fields("k",cp.get("index"),"op",cp.get("op"),"observed",cp.get("observed"),"ok",cp.get("ok")));}witnesses.add(WorkflowCoverage.fields("label",e.getKey(),"case_id",row.id(),"checkpoints",brief,"failing_checkpoints",value(row,"failing_checkpoints"),"verdict",row.fields().get("verdict"),"final_only_verdict",row.fields().get("final_only_verdict")));}
        return WorkflowCoverage.freeze(WorkflowCoverage.fields("totals",totals,"by_policy",byPolicy,"final_only",finalOnly,"fault_orders",new ArrayList<>(faults),"hidden_orders",new ArrayList<>(hidden),"suites",measured,"witnesses",witnesses));
    }
}
