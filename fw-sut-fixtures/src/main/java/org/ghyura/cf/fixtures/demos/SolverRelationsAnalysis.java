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

/** Full recorded-value D14c analysis and independent proofs on all actual transformed inputs. */
public final class SolverRelationsAnalysis {
    private SolverRelationsAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("id"); } }
    public static List<List<Byte>> permutations() { var out=new ArrayList<List<Byte>>();for(byte a=0;a<3;a++)for(byte b=0;b<3;b++)for(byte c=0;c<3;c++)if(a!=b&&a!=c&&b!=c)out.add(List.of(a,b,c));return List.copyOf(out); }
    public static Set<String> expectedIds() { var ids=new TreeSet<String>();for(String p:SolverRelations.POLICIES)for(String i:SolverRelations.INSTANCES)for(var r:permutations())for(String e:SolverRelations.EDITS)ids.add(new SolverRelations.Config(p,i,r,e).caseId());return Collections.unmodifiableSet(ids); }
    public static Map<String,String> witnesses() { var out=new LinkedHashMap<String,String>();out.put("tie_exact_dp_three_bids","P=exact_dp|I=tie|R=210|E=dominated");out.put("tie_greedy_two_bids_also_optimal","P=greedy_value|I=tie|R=210|E=dominated");out.put("greedy_gap_6_on_disjoint","P=greedy_value|I=disjoint|R=012|E=dominated");out.put("min_only_symmetry_failure","P=min_only_dp|I=disjoint|R=102|E=none");out.put("min_only_infeasible_with_invariant_outputs","P=min_only_dp|I=bundle_trap|R=012|E=dominated");return Collections.unmodifiableMap(out); }
    private static Object value(Observed row,String key) { String text=row.fields().get(key);if(text==null)throw new IllegalArgumentException("missing observed D14c "+key);return Json.parse(text,"observed D14c "+key); }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object v) { if(!(v instanceof Map<?,?>))throw new IllegalArgumentException("observed D14c object required");return (Map<String,Object>)v; }
    private static SolverRelations.Config configuration(Observed row) { Object r=value(row,"permutation");if(!(r instanceof List<?> list)||list.size()!=3)throw new IllegalArgumentException("observed goods permutation required");var permutation=new ArrayList<Byte>();for(Object n:list){if(!(n instanceof Number number)||!(n instanceof Byte||n instanceof Integer||n instanceof Long)||number.longValue()<0||number.longValue()>2)throw new IllegalArgumentException("observed good outside contract");permutation.add(number.byteValue());}return new SolverRelations.Config(row.fields().get("policy"),row.fields().get("instance"),permutation,row.fields().get("edit")); }
    private static void tally(Map<String,Integer> map,String key) { map.merge(key,1,Integer::sum); }
    private static TreeMap<String,Observed> bank(List<Observed> rows) { var bank=new TreeMap<String,Observed>();var expected=expectedIds();for(var row:rows){var c=configuration(row);String v=row.fields().get("verdict");if(!c.caseId().equals(row.id())||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null||!("PASS".equals(v)||"DOMAIN_FAIL".equals(v)))throw new IllegalArgumentException("unknown/duplicate/conflicting/non-domain D14c support");}if(!bank.keySet().equals(expected))throw new IllegalArgumentException("complete original 144-case D14c support required");return bank; }
    /** Reads recorded verdicts, variants, relation flags and counts; never solves or rejudges a record. */
    public static Map<String,Object> analyse(List<Observed> rows) {
        var bank=bank(rows);var outcomes=new TreeMap<String,Integer>();var policy=new TreeMap<String,Map<String,Integer>>();var cross=new TreeMap<String,Map<String,Integer>>();var gaps=new TreeMap<String,TreeSet<Long>>();for(String p:SolverRelations.POLICIES){policy.put(p,new TreeMap<>());for(String i:SolverRelations.INSTANCES)cross.put(p+"|"+i,new TreeMap<>());}for(String i:SolverRelations.INSTANCES)gaps.put(i,new TreeSet<>());int equalButFail=0,infeasible=0,relabel=0,edit=0,checks=0;
        for(var row:bank.values()){String p=row.fields().get("policy"),i=row.fields().get("instance"),v=row.fields().get("verdict");tally(outcomes,v);tally(policy.get(p),v);tally(cross.get(p+"|"+i),v);var relations=object(value(row,"relations"));boolean r=Boolean.TRUE.equals(relations.get("solver_relabel")),e=Boolean.TRUE.equals(relations.get("solver_edit"));if(!r)relabel++;if(!e)edit++;if(!v.equals("PASS")&&r&&e)equalButFail++;for(Object variant:object(value(row,"variants")).values()){var x=object(variant);if(!Boolean.TRUE.equals(x.get("feasible")))infeasible++;if(p.equals("greedy_value")){Object gap=x.get("gap");if(!(gap instanceof Number n))throw new IllegalArgumentException("recorded greedy gap required");gaps.get(i).add(n.longValue());}}for(Object count:object(value(row,"oracle_subset_checks")).values())checks=Math.addExact(checks,((Number)count).intValue());}
        var gapLists=new TreeMap<String,Object>();gaps.forEach((k,v) -> gapLists.put(k,new ArrayList<>(v)));var witnesses=new ArrayList<Map<String,Object>>();for(var entry:witnesses().entrySet()){var row=bank.get(entry.getValue());var brief=new TreeMap<String,Object>();brief.put("label",entry.getKey());brief.put("case_id",row.id());brief.put("verdict",row.fields().get("verdict"));brief.put("relations",value(row,"relations"));var variants=new TreeMap<String,Object>();for(var variant:object(value(row,"variants")).entrySet()){var reduced=new TreeMap<String,Object>();var original=object(variant.getValue());for(String key:List.of("selected","reported_value","actual_value","feasible","optimum","optimal","gap"))reduced.put(key,original.get(key));variants.put(variant.getKey(),reduced);}brief.put("variants",variants);witnesses.add(brief);}
        return SolverRelations.freeze(SolverRelations.fields("outcomes",outcomes,"by_policy",policy,"by_policy_instance",cross,"greedy_gaps",gapLists,"output_relations_hold_but_fail",equalButFail,"infeasible_results",infeasible,"solver_relabel_failures",relabel,"solver_edit_failures",edit,"subset_checks",checks,"witnesses",witnesses));
    }
    public static Map<String,Object> controls(List<Observed> rows) { var bank=bank(rows);boolean greedy=true,min=true;for(var row:bank.values()){var variants=object(value(row,"variants"));if(row.fields().get("policy").equals("greedy_value"))greedy&=variants.values().stream().map(SolverRelationsAnalysis::object).allMatch(x -> Boolean.TRUE.equals(x.get("feasible")));if(row.fields().get("policy").equals("min_only_dp"))min&=!Boolean.TRUE.equals(object(variants.get("base")).get("optimal"));}var exact=object(object(value(bank.get("P=exact_dp|I=tie|R=012|E=none"),"variants")).get("base"));var gr=object(object(value(bank.get("P=greedy_value|I=tie|R=012|E=none"),"variants")).get("base"));return SolverRelations.freeze(SolverRelations.fields("greedy_all_feasible",greedy,"min_only_fail_at_base",min,"tie_selections",Map.of("exact_dp",exact.get("selected"),"greedy_value",gr.get("selected")),"tie_optima",exact.get("optimal_allocations"))); }
    /** On actual transformed inputs, exhaustive feasible ID/value pairs prove relabel and dominance. */
    public static Map<String,Object> transformationProofs() {
        boolean relabel=true,dominance=true;for(String name:SolverRelations.INSTANCES)for(var permutation:permutations()){var base=SolverRelations.baseBids(name);var renamed=SolverRelations.relabel(base,permutation);var a=SolverRelationsOracle.enumerateSubsets(base);var b=SolverRelationsOracle.enumerateSubsets(renamed);relabel&=new java.util.HashSet<>(a.feasibleAllocations()).equals(new java.util.HashSet<>(b.feasibleAllocations()));var edited=SolverRelations.edit(renamed,"dominated");var c=SolverRelationsOracle.enumerateSubsets(edited);dominance&=b.optimum()==c.optimum();var feasible=new java.util.HashSet<>(b.feasibleAllocations());for(var allocation:c.feasibleAllocations())if(allocation.ids().contains("b4")){var swapped=new ArrayList<>(allocation.ids());swapped.remove("b4");swapped.add("b0");Collections.sort(swapped);dominance&=!allocation.ids().contains("b0")&&feasible.contains(new SolverRelationsOracle.Allocation(swapped,allocation.value()+1));}}return Map.of("relabel_bijection_on_all_24_inputs",relabel,"dominated_swap_on_all_24_edited_inputs",dominance);
    }
}
