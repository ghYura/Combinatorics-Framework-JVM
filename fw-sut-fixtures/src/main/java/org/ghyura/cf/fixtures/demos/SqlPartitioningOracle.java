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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Independent policy-blind fixture model and multiplicity oracle;no SUT query/predicate/recombiner calls. */
public final class SqlPartitioningOracle {
    private SqlPartitioningOracle() { }
    public static String not(String a) { return switch(a){case "T" -> "F";case "F" -> "T";case "U" -> "U";default -> throw new IllegalArgumentException("invalid truth value");}; }
    public static String and(String a,String b) { not(a);not(b);return a.equals("F")||b.equals("F")?"F":a.equals("U")||b.equals("U")?"U":"T"; }
    public static String or(String a,String b) { not(a);not(b);return a.equals("T")||b.equals("T")?"T":a.equals("U")||b.equals("U")?"U":"F"; }
    public static String truth(SqlPartitioning.Item i,String p) { String gt=i.value()==null?"U":i.value()>0?"T":"F",eq=i.value()==null?"U":i.value()==1?"T":"F",flag=i.flag()==null?"U":i.flag()?"T":"F";return switch(p){case "gt0" -> gt;case "eq1" -> eq;case "flag" -> flag;case "and" -> and(gt,flag);case "or" -> or(eq,flag);case "is_null" -> i.value()==null?"T":"F";default -> throw new IllegalArgumentException("unknown model predicate");}; }
    public static Map<String,List<List<Integer>>> expected(String query,String predicate) { if(!SqlPartitioning.QUERIES.contains(query)||!SqlPartitioning.PREDICATES.contains(predicate))throw new IllegalArgumentException("unknown model family");var rows=new TreeMap<String,List<List<Integer>>>();SqlPartitioning.BRANCHES.forEach(b -> rows.put(b,new ArrayList<>()));for(var item:SqlPartitioning.ITEMS){if(query.equals("filtered")&&!item.group().equals("a"))continue;int copies=0;if(query.equals("scan")||query.equals("filtered"))copies=1;else{for(var tag:SqlPartitioning.TAGS)if(tag.itemId()==item.id())copies++;if(query.equals("left_join")&&copies==0)copies=1;}String tv=truth(item,predicate);for(int k=0;k<copies;k++){var row=Collections.singletonList(item.value()==null?null:item.value().intValue());rows.get("base").add(row);if(tv.equals("T"))rows.get("true").add(row);if(not(tv).equals("T"))rows.get("false").add(row);if(tv.equals("U"))rows.get("unknown").add(row);}}rows.replaceAll((k,v) -> canonical(v));return Collections.unmodifiableMap(rows); }
    public static List<List<Integer>> canonical(List<?> rows) { var out=new ArrayList<List<Integer>>();for(Object raw:rows){if(!(raw instanceof List<?> row)||row.size()!=1)throw new IllegalArgumentException("one-column row required");Object value=row.getFirst();Integer n=null;if(value!=null){if(!(value instanceof Byte||value instanceof Integer||value instanceof Long)||((Number)value).longValue()<Integer.MIN_VALUE||((Number)value).longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("NULL or strict integral row value required");n=((Number)value).intValue();}out.add(Collections.singletonList(n));}out.sort(Comparator.comparing((List<Integer> row) -> row.getFirst(),Comparator.nullsFirst(Comparator.naturalOrder())));return List.copyOf(out); }
    public static List<Map<String,Object>> bag(List<?> rows) { var canon=canonical(rows);var out=new ArrayList<Map<String,Object>>();for(var row:canon){if(!out.isEmpty()&&out.getLast().get("row").equals(row)){var previous=out.removeLast();out.add(SqlPartitioning.fields("row",row,"count",(Integer)previous.get("count")+1));}else out.add(SqlPartitioning.fields("row",row,"count",1));}return List.copyOf(out); }
    /** All four query bags must equal independent expected bags;then base and recombined bags must agree. */
    public static Map<String,Object> judge(String query,String predicate,Map<String,? extends List<?>> observed,List<?> combined) { if(!observed.keySet().equals(new java.util.HashSet<>(SqlPartitioning.BRANCHES)))throw new IllegalArgumentException("all four query observations required");var want=expected(query,predicate);var checks=new TreeMap<String,Boolean>();for(String b:SqlPartitioning.BRANCHES)checks.put(b,bag(observed.get(b)).equals(bag(want.get(b))));boolean tlp=bag(observed.get("base")).equals(bag(combined)),sets=new java.util.HashSet<>(canonical(observed.get("base"))).equals(new java.util.HashSet<>(canonical(combined)));return SqlPartitioning.fields("query_checks",checks,"tlp_ok",tlp,"set_equal",sets,"verdict",tlp&&checks.values().stream().allMatch(Boolean.TRUE::equals)?"PASS":"DOMAIN_FAIL"); }
}
