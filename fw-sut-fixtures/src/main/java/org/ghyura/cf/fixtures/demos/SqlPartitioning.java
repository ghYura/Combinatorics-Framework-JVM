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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D14b's immutable local relational fixture, nullable predicates and original faulty recombiners. */
public final class SqlPartitioning {
    private SqlPartitioning() { }
    public static final List<String> POLICIES=List.of("union_all","omit_unknown","dedup_union"), QUERIES=List.of("scan","filtered","inner_join","left_join"), PREDICATES=List.of("gt0","eq1","flag","and","or","is_null"), BRANCHES=List.of("base","true","false","unknown");
    public record Item(byte id,String group,Byte value,Boolean flag) { }
    public record Tag(byte itemId,String tag) { }
    private static Item item(int id,String group,Integer value,Boolean flag) { return new Item((byte)id,group,value==null?null:value.byteValue(),flag); }
    public static final List<Item> ITEMS=List.of(item(1,"a",null,true),item(2,"a",0,false),item(3,"a",1,null),item(4,"b",1,true),item(5,"b",2,false),item(6,"b",null,null));
    public static final List<Tag> TAGS=List.of(new Tag((byte)1,"p"),new Tag((byte)1,"q"),new Tag((byte)3,"p"),new Tag((byte)4,"p"),new Tag((byte)4,"q"),new Tag((byte)5,"p"));
    public record Config(String policy,String query,String predicate) { public Config { if(!POLICIES.contains(policy)||!QUERIES.contains(query)||!PREDICATES.contains(predicate))throw new IllegalArgumentException("unknown D14b coordinate"); } public String caseId() { return "P="+policy+"|Q="+query+"|F="+predicate; } }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> value) { return (Map<String,Object>)ToolCallOrder.freezeDocument(value); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    private static Boolean and(Boolean a,Boolean b) { if(Boolean.FALSE.equals(a)||Boolean.FALSE.equals(b))return Boolean.FALSE;if(a==null||b==null)return null;return Boolean.TRUE; }
    private static Boolean or(Boolean a,Boolean b) { if(Boolean.TRUE.equals(a)||Boolean.TRUE.equals(b))return Boolean.TRUE;if(a==null||b==null)return null;return Boolean.FALSE; }
    /** WHERE keeps only TRUE;UNKNOWN is distinct from FALSE. */
    private static Boolean predicate(Item item,String name) { Boolean gt=item.value()==null?null:item.value()>0,eq=item.value()==null?null:item.value()==1;return switch(name){case "gt0" -> gt;case "eq1" -> eq;case "flag" -> item.flag();case "and" -> and(gt,item.flag());case "or" -> or(eq,item.flag());case "is_null" -> item.value()==null;default -> throw new IllegalArgumentException("unknown predicate");}; }
    private static List<List<Byte>> canonical(List<List<Byte>> rows) { return rows.stream().sorted(Comparator.comparing((List<Byte> row) -> row.getFirst(),Comparator.nullsFirst(Comparator.naturalOrder()))).toList(); }
    /** Local scan/filter/join and projection: no policy, expected rows, oracle or SQL-text execution. */
    public static Map<String,List<List<Byte>>> queryRows(String query,String predicate) { if(!QUERIES.contains(query)||!PREDICATES.contains(predicate))throw new IllegalArgumentException("unknown query family");var rows=new TreeMap<String,List<List<Byte>>>();BRANCHES.forEach(b -> rows.put(b,new ArrayList<>()));for(var item:ITEMS){if(query.equals("filtered")&&!item.group().equals("a"))continue;long multiplicity=query.endsWith("join")?TAGS.stream().filter(t -> t.itemId()==item.id()).count():1;if(query.equals("left_join"))multiplicity=Math.max(1,multiplicity);Boolean truth=predicate(item,predicate);String branch=truth==null?"unknown":truth?"true":"false";for(int i=0;i<multiplicity;i++){var row=Collections.singletonList(item.value());rows.get("base").add(row);rows.get(branch).add(row);}}rows.replaceAll((k,v) -> canonical(v));return Collections.unmodifiableMap(rows); }
    /** Recombines received branches only;knows no fixture,expected rows or verdict. */
    public static List<List<Byte>> recombine(String policy,Map<String,List<List<Byte>>> results) { if(!POLICIES.contains(policy))throw new IllegalArgumentException("unknown adapter policy");var out=new ArrayList<List<Byte>>();for(String b:policy.equals("omit_unknown")?List.of("true","false"):List.of("true","false","unknown"))out.addAll(results.get(b));return canonical(policy.equals("dedup_union")?new ArrayList<>(new LinkedHashSet<>(out)):out); }
    public static Map<String,String> sql(String query,String predicate) { if(!QUERIES.contains(query)||!PREDICATES.contains(predicate))throw new IllegalArgumentException("unknown SQL family");String join=switch(query){case "inner_join" -> " JOIN fixture.tags AS t ON t.item_id=i.id";case "left_join" -> " LEFT JOIN fixture.tags AS t ON t.item_id=i.id";default -> "";};String p=switch(predicate){case "gt0" -> "i.val > 0";case "eq1" -> "i.val = 1";case "flag" -> "i.flag";case "and" -> "(i.val > 0) AND i.flag";case "or" -> "(i.val = 1) OR i.flag";default -> "i.val IS NULL";};String base=query.equals("filtered")?"i.grp = 'a'":"TRUE",head="SELECT i.val FROM fixture.items AS i"+join+" WHERE ";return Map.of("base",head+base,"true",head+"("+base+") AND ("+p+")","false",head+"("+base+") AND NOT ("+p+")","unknown",head+"("+base+") AND (("+p+") IS NULL)"); }
    public record Result(Config config,Map<String,Object> document) { public Result { document=freeze(document); } public boolean passed() { return document.get("verdict").equals("PASS"); } }
    public static Result evaluate(Config c) { var rows=queryRows(c.query(),c.predicate());var combined=recombine(c.policy(),rows);var judged=SqlPartitioningOracle.judge(c.query(),c.predicate(),rows,combined);var bags=new TreeMap<String,Object>();rows.forEach((b,r) -> bags.put(b,SqlPartitioningOracle.bag(r)));var doc=new TreeMap<>(judged);doc.putAll(fields("schema","d14b.observation/v1","contract","v1","id",c.caseId(),"policy",c.policy(),"query",c.query(),"predicate",c.predicate(),"sql",sql(c.query(),c.predicate()),"query_rows",rows,"query_bags",bags,"combined_rows",combined,"combined_bag",SqlPartitioningOracle.bag(combined),"fw_var",judged.get("verdict").equals("PASS")?0:2,"carrier_slot","IMPL position 2 (legacy positional, not causal)"));return new Result(c,doc); }
    public static Map<String,String> attributes(Result r) { var out=new TreeMap<String,String>();r.document().forEach((k,v) -> out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
