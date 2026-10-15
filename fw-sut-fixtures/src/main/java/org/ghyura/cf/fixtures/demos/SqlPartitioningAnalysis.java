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

/** Full recorded D14b bag evidence and independent nullable truth tables. */
public final class SqlPartitioningAnalysis {
    private SqlPartitioningAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("id"); } }
    public static List<SqlPartitioning.Config> configurations() { var out=new ArrayList<SqlPartitioning.Config>();for(String p:SqlPartitioning.POLICIES)for(String q:SqlPartitioning.QUERIES)for(String f:SqlPartitioning.PREDICATES)out.add(new SqlPartitioning.Config(p,q,f));return List.copyOf(out); }
    public static Set<String> expectedIds() { return Collections.unmodifiableSet(new TreeSet<>(configurations().stream().map(SqlPartitioning.Config::caseId).toList())); }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object raw) { if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("recorded D14b object required");return (Map<String,Object>)raw; }
    private static Object value(Observed r,String k) { if(!r.fields().containsKey(k))throw new IllegalArgumentException("missing D14b record field");return Json.parse(r.fields().get(k),"observed D14b "+k); }
    private static List<?> rows(Object raw) { if(!(raw instanceof List<?> list))throw new IllegalArgumentException("recorded rows required");return list; }
    private static TreeMap<String,Observed> bank(List<Observed> records) { var out=new TreeMap<String,Observed>();for(var r:records){var f=r.fields();var c=new SqlPartitioning.Config(f.get("policy"),f.get("query"),f.get("predicate"));if(!c.caseId().equals(r.id())||!("PASS".equals(f.get("verdict"))||"DOMAIN_FAIL".equals(f.get("verdict")))||out.putIfAbsent(r.id(),r)!=null)throw new IllegalArgumentException("unknown/duplicate/conflicting/non-domain D14b support");}if(!out.keySet().equals(expectedIds()))throw new IllegalArgumentException("complete72case D14b support required");return out; }
    private static void tally(Map<String,Integer> m,String k) { m.merge(k,1,Integer::sum); }
    public static Map<String,String> witnesses() { var out=new LinkedHashMap<String,String>();out.put("omit_unknown_sets_agree_bags_differ","P=omit_unknown|Q=scan|F=flag");out.put("union_all_keeps_unknown_multiplicity","P=union_all|Q=scan|F=flag");out.put("dedup_union_destroys_join_multiplicity","P=dedup_union|Q=inner_join|F=eq1");out.put("omit_unknown_no_unknown_rows_passes","P=omit_unknown|Q=filtered|F=or");out.put("dedup_union_distinct_rows_passes","P=dedup_union|Q=filtered|F=is_null");return Collections.unmodifiableMap(out); }
    /** Uses recorded rows/bags/flags/verdicts;never queries,recombines,rejudges or repairs. */
    public static Map<String,Object> analyse(List<Observed> records) { var bank=bank(records);var totals=new TreeMap<String,Integer>();var policy=new TreeMap<String,Map<String,Integer>>();var strata=new TreeMap<String,Map<String,Map<String,Integer>>>();var passes=new TreeMap<String,List<String>>();for(String p:SqlPartitioning.POLICIES){policy.put(p,new TreeMap<>());var q=new TreeMap<String,Map<String,Integer>>();SqlPartitioning.QUERIES.forEach(name -> q.put(name,new TreeMap<>()));strata.put(p,q);if(!p.equals("union_all"))passes.put(p,new ArrayList<>());}var hidden=new ArrayList<String>();var unknownEmpty=new ArrayList<String>();var distinct=new ArrayList<String>();var rowCounts=new TreeMap<String,Integer>();
        for(var r:bank.values()){var f=r.fields();String p=f.get("policy"),q=f.get("query"),v=f.get("verdict"),family=q+"|"+f.get("predicate");tally(totals,v);tally(policy.get(p),v);tally(strata.get(p).get(q),v);if(v.equals("PASS")&&passes.containsKey(p))passes.get(p).add(family);if(!v.equals("PASS")&&Boolean.TRUE.equals(value(r,"set_equal")))hidden.add(r.id());if(p.equals("union_all")){var qr=object(value(r,"query_rows"));var base=rows(qr.get("base"));Integer old=rowCounts.putIfAbsent(q,base.size());if(old!=null&&old!=base.size())throw new IllegalArgumentException("conflicting observed base family cardinality");if(rows(qr.get("unknown")).isEmpty())unknownEmpty.add(family);if(new java.util.HashSet<>(base).size()==base.size())distinct.add(family);}}
        passes.values().forEach(Collections::sort);Collections.sort(unknownEmpty);Collections.sort(distinct);var scan=bank.get("P=union_all|Q=scan|F=flag");var branches=object(value(scan,"query_rows"));var flat=new TreeMap<String,Object>();for(String b:List.of("true","false","unknown"))flat.put(b,rows(branches.get(b)).stream().map(x -> rows(x).getFirst()).toList());var om=bank.get("P=omit_unknown|Q=scan|F=flag");var controls=SqlPartitioning.fields("scan_bag",object(value(bank.get("P=union_all|Q=scan|F=gt0"),"query_bags")).get("base"),"scan_flag_branches",flat,"omit_unknown_scan_flag",SqlPartitioning.fields("set_equal",value(om,"set_equal"),"tlp_ok",value(om,"tlp_ok")),"unknown_empty_families",unknownEmpty,"distinct_base_families",distinct);
        var witnesses=new ArrayList<Map<String,Object>>();for(var e:witnesses().entrySet()){var r=bank.get(e.getValue());var m=new TreeMap<String,Object>();m.put("label",e.getKey());m.put("case_id",r.id());for(String k:List.of("query_rows","query_bags","combined_rows","combined_bag","tlp_ok","set_equal","query_checks"))m.put(k,value(r,k));m.put("verdict",r.fields().get("verdict"));witnesses.add(m);}return SqlPartitioning.freeze(SqlPartitioning.fields("totals",totals,"by_policy",policy,"strata",strata,"failures_with_equal_sets",hidden,"passes",passes,"controls",controls,"row_counts",rowCounts,"counts",Map.of("cases",bank.size(),"families",24,"data_selects",bank.size()*4),"witnesses",witnesses));
    }
    public static Map<String,Object> kleene() { var not=new TreeMap<String,Object>();var and=new TreeMap<String,Object>();var or=new TreeMap<String,Object>();for(String a:List.of("T","F","U")){not.put(full(a),full(SqlPartitioningOracle.not(a)));for(String b:List.of("T","F","U")){and.put(full(a)+"|"+full(b),full(SqlPartitioningOracle.and(a,b)));or.put(full(a)+"|"+full(b),full(SqlPartitioningOracle.or(a,b)));}}return SqlPartitioning.fields("not",not,"and",and,"or",or); }
    private static String full(String s) { return switch(s){case "T" -> "TRUE";case "F" -> "FALSE";default -> "UNKNOWN";}; }
    public static Map<String,Object> truth() { var out=new TreeMap<String,Object>();for(String p:SqlPartitioning.PREDICATES){var table=new TreeMap<String,Object>();for(var item:SqlPartitioning.ITEMS)table.put(Byte.toString(item.id()),full(SqlPartitioningOracle.truth(item,p)));out.put(p,table);}return out; }
    public static Map<String,Object> fixture() { return SqlPartitioning.fields("items",SqlPartitioning.ITEMS.stream().map(i -> SqlPartitioning.fields("id",i.id(),"grp",i.group(),"val",i.value(),"flag",i.flag())).toList(),"tags",SqlPartitioning.TAGS.stream().map(t -> SqlPartitioning.fields("item_id",t.itemId(),"tag",t.tag())).toList()); }
}
