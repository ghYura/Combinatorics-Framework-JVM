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

/** Complete recorded-value D14a tallies, diagnostic witnesses and independent reference controls. */
public final class LanguageProcessorsAnalysis {
    private LanguageProcessorsAnalysis() { }
    public static final List<String> CHECKS=List.of("original_differential","transformed_differential","metamorphic");
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    public static List<LanguageProcessors.Config> configurations() { var out=new ArrayList<LanguageProcessors.Config>();for(String p:LanguageProcessors.POLICIES)for(String o:LanguageProcessors.ORDERS)for(String h:LanguageProcessors.SHAPES)for(byte x:LanguageProcessors.CONSTANTS)for(byte y:LanguageProcessors.CONSTANTS)for(String a:LanguageProcessors.OPERATORS)for(String b:LanguageProcessors.OPERATORS)out.add(new LanguageProcessors.Config(p,o,h,x,y,a,b));return List.copyOf(out); }
    public static Set<String> expectedIds() { return Collections.unmodifiableSet(new TreeSet<>(configurations().stream().map(LanguageProcessors.Config::caseId).toList())); }
    public static Map<String,String> witnesses() { var out=new LinkedHashMap<String,String>();out.put("reverse_sub_equal_but_wrong","P=reverse_sub|O=XY|H=L|X=-1|Y=2|A=-|B=-");out.put("faithful_subtraction_control","P=faithful|O=XY|H=L|X=-1|Y=2|A=-|B=-");out.put("alias_dead_temp_xy_corrupted","P=alias_dead_temp|O=XY|H=L|X=-1|Y=2|A=+|B=*");out.put("alias_dead_temp_yx_restored","P=alias_dead_temp|O=YX|H=L|X=-1|Y=2|A=+|B=*");out.put("faithful_grouping_control","P=faithful|O=XY|H=R|X=-1|Y=2|A=+|B=*");return Collections.unmodifiableMap(out); }
    private static Object value(Observed row,String key) { String text=row.fields().get(key);if(text==null)throw new IllegalArgumentException("missing observed D14a "+key);return Json.parse(text,"observed D14a "+key); }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object v) { if(!(v instanceof Map<?,?>))throw new IllegalArgumentException("observed D14a object required");return (Map<String,Object>)v; }
    private static byte constant(Object v) { int n=LanguageProcessors.integer(v);if(n!= -1&&n!=2)throw new IllegalArgumentException("original D14a constant required");return (byte)n; }
    private static LanguageProcessors.Config configuration(Observed r) { var f=r.fields();return new LanguageProcessors.Config(f.get("policy"),f.get("order"),f.get("shape"),constant(value(r,"x")),constant(value(r,"y")),f.get("op_a"),f.get("op_b")); }
    private static TreeMap<String,Observed> bank(List<Observed> rows) { var bank=new TreeMap<String,Observed>();var expected=expectedIds();for(var r:rows){var c=configuration(r);String v=r.fields().get("verdict");if(!c.caseId().equals(r.id())||!expected.contains(r.id())||bank.putIfAbsent(r.id(),r)!=null||!("PASS".equals(v)||"DOMAIN_FAIL".equals(v)))throw new IllegalArgumentException("unknown/duplicate/conflicting/non-domain D14a support");}if(!bank.keySet().equals(expected))throw new IllegalArgumentException("complete original432case D14a support required");return bank; }
    private static boolean flag(Map<String,Object> checks,String key) { if(!(checks.get(key) instanceof Boolean b))throw new IllegalArgumentException("recorded sensor boolean required");return b; }
    private static void tally(Map<String,Integer> m,String v) { m.merge(v,1,Integer::sum); }
    private static Map<String,Object> variant(Observed r,String key) { return object(object(value(r,"observations")).get(key)); }
    private static Map<String,Object> brief(Map<String,Object> v,boolean transformed) { var m=new TreeMap<String,Object>();m.put("reference",v.get("reference_value"));m.put("vm",v.get("vm_value"));m.put("bytecode",v.get("bytecode"));if(transformed){if(!(v.get("vm_trace") instanceof List<?> steps)||steps.isEmpty())throw new IllegalArgumentException("recorded VM trace required");m.put("final_locals",object(steps.getLast()).get("locals"));}return m; }
    /** Uses recorded verdict/check/bytecode/trace fields, without compiling, executing or repairing. */
    public static Map<String,Object> analyse(List<Observed> rows) { var bank=bank(rows);var outcomes=new TreeMap<String,Integer>();var byPolicy=new TreeMap<String,Map<String,Integer>>();var cross=new TreeMap<String,Map<String,Integer>>();var failures=new TreeMap<String,Map<String,Integer>>();for(String p:LanguageProcessors.POLICIES){byPolicy.put(p,new TreeMap<>());var sensors=new TreeMap<String,Integer>();CHECKS.forEach(k -> sensors.put(k,0));failures.put(p,sensors);for(String o:LanguageProcessors.ORDERS)for(String h:LanguageProcessors.SHAPES)cross.put(p+"|"+o+"|"+h,new TreeMap<>());}int wrong=0,corrupt=0;
        for(var r:bank.values()){String p=r.fields().get("policy"),v=r.fields().get("verdict");tally(outcomes,v);tally(byPolicy.get(p),v);tally(cross.get(p+"|"+r.fields().get("order")+"|"+r.fields().get("shape")),v);var checks=object(value(r,"checks"));for(String k:CHECKS)if(!flag(checks,k))failures.get(p).merge(k,1,Integer::sum);if(flag(checks,"metamorphic")&&!flag(checks,"original_differential"))wrong++;if(flag(checks,"original_differential")&&!flag(checks,"transformed_differential"))corrupt++;}
        var witnesses=new ArrayList<Map<String,Object>>();for(var entry:witnesses().entrySet()){var r=bank.get(entry.getValue());witnesses.add(LanguageProcessors.fields("label",entry.getKey(),"case_id",r.id(),"original",brief(variant(r,"original"),false),"transformed",brief(variant(r,"transformed"),true),"checks",value(r,"checks"),"verdict",r.fields().get("verdict")));}return LanguageProcessors.freeze(LanguageProcessors.fields("outcomes",outcomes,"by_policy",byPolicy,"by_policy_order_shape",cross,"failed_checks",failures,"wrong_agreements",wrong,"original_correct_transformed_corrupted",corrupt,"witnesses",witnesses));
    }
    public static Map<String,Object> controls(List<Observed> rows) { var bank=bank(rows);var passes=new TreeMap<String,Integer>();LanguageProcessors.ORDERS.forEach(o -> passes.put(o,0));int cancelling=0;
        for(var r:bank.values())if(r.fields().get("policy").equals("alias_dead_temp")&&r.fields().get("verdict").equals("PASS")){String order=r.fields().get("order");passes.merge(order,1,Integer::sum);if(order.equals("XY")){var p=object(variant(r,"original").get("program"));if(!(p.get("bindings") instanceof List<?> bindings))throw new IllegalArgumentException("recorded bindings required");var values=new TreeSet<Integer>();for(int x:List.of(-1,2,7,11)){var changed=new ArrayList<Map<String,Object>>();for(Object raw:bindings){var b=new TreeMap<>(object(raw));if(b.get("name").equals("x"))b.put("value",x);changed.add(b);}values.add(LanguageProcessorsOracle.interpret(LanguageProcessors.fields("bindings",changed,"expr",p.get("expr"))));}if(values.size()==1)cancelling++;}}
        var literals=new TreeMap<String,Object>();for(var pair:List.of(List.of("L","+","*","L(+,*)"),List.of("R","+","*","R(+,*)"),List.of("L","-","-","L(-,-)"))){var id=new LanguageProcessors.Config("faithful","XY",pair.get(0),(byte)-1,(byte)2,pair.get(1),pair.get(2)).caseId();literals.put(pair.get(3),variant(bank.get(id),"original").get("reference_value"));}return LanguageProcessors.freeze(LanguageProcessors.fields("literal_controls",literals,"alias_passes",passes,"alias_xy_cancelling_passes",cancelling));
    }
    private static List<String> leaves(Object raw) { var node=object(raw);if(node.containsKey("var"))return List.of((String)node.get("var"));var out=new ArrayList<>(leaves(node.get("left")));out.addAll(leaves(node.get("right")));return List.copyOf(out); }
    /** Structural/reference facts come from the actual retained programs and recorded reference values. */
    public static Map<String,Object> proofs(List<Observed> rows) { var bank=bank(rows);boolean agreement=true,order=true,third=true;for(var r:bank.values()){var a=variant(r,"original");var b=variant(r,"transformed");agreement&=a.get("reference_value").equals(b.get("reference_value"));for(var v:List.of(a,b))third&=leaves(object(v.get("program")).get("expr")).equals(List.of("x","y","x"));var c=configuration(r);var other=new LanguageProcessors.Config(c.policy(),c.order().equals("XY")?"YX":"XY",c.shape(),c.x(),c.y(),c.opA(),c.opB());order&=a.get("reference_value").equals(variant(bank.get(other.caseId()),"original").get("reference_value"));}return Map.of("reference_agreement",agreement,"reference_order_invariant",order,"third_leaf_is_x",third); }
}
