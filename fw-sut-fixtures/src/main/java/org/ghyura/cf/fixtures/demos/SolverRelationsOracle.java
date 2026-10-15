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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Independent policy-blind exhaustive allocation oracle, using pairwise intersections and no DP/masks. */
public final class SolverRelationsOracle {
    private SolverRelationsOracle() { }
    private static Map<String,Object> fields(Object... pairs) { var out=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(out); }
    public record Allocation(List<String> ids,int value) { public Allocation { ids=List.copyOf(ids); } }
    public record Reference(int optimum,int feasibleSubsets,List<List<String>> optimalAllocations,byte subsetChecks,List<Allocation> feasibleAllocations) { }
    private static boolean disjoint(List<SolverRelations.Bid> bids) { for(int i=0;i<bids.size();i++)for(int k=i+1;k<bids.size();k++)for(byte item:bids.get(i).items())if(bids.get(k).items().contains(item))return false;return true; }
    private static void subsets(List<SolverRelations.Bid> bids,int index,ArrayList<SolverRelations.Bid> chosen,List<Allocation> feasible,byte[] checks) { if(index==bids.size()){checks[0]++;if(disjoint(chosen))feasible.add(new Allocation(chosen.stream().map(SolverRelations.Bid::id).toList(),chosen.stream().mapToInt(SolverRelations.Bid::value).sum()));return;}subsets(bids,index+1,chosen,feasible,checks);chosen.add(bids.get(index));subsets(bids,index+1,chosen,feasible,checks);chosen.removeLast(); }
    public static Reference enumerateSubsets(List<SolverRelations.Bid> bids) { if(bids.size()>5||bids.stream().map(SolverRelations.Bid::id).distinct().count()!=bids.size())throw new IllegalArgumentException("at most five unique local bids required");var feasible=new ArrayList<Allocation>();byte[] checks={0};subsets(bids,0,new ArrayList<>(),feasible,checks);int optimum=feasible.stream().mapToInt(Allocation::value).max().orElseThrow();var optimal=feasible.stream().filter(x -> x.value()==optimum).map(Allocation::ids).sorted(SolverRelationsOracle::compareIds).toList();return new Reference(optimum,feasible.size(),optimal,checks[0],List.copyOf(feasible)); }
    private static int compareIds(List<String> a,List<String> b) { for(int i=0;i<Math.min(a.size(),b.size());i++){int c=a.get(i).compareTo(b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size()); }
    public record Observation(Map<String,Object> document,byte subsetChecks) { public Observation { document=SolverRelations.freeze(document); } }
    public static Observation observe(List<SolverRelations.Bid> bids,Map<String,Object> answer) {
        Object selected=answer.get("selected"),reported=answer.get("reported_value");var ids=bids.stream().map(SolverRelations.Bid::id).toList();
        if(!(selected instanceof List<?> chosenIds)||chosenIds.stream().anyMatch(x -> !(x instanceof String))||!chosenIds.equals(new ArrayList<>(new TreeSet<>(chosenIds)))||!ids.containsAll(chosenIds)||!(reported instanceof Byte||reported instanceof Integer||reported instanceof Long)||((Number)reported).longValue()<Integer.MIN_VALUE||((Number)reported).longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("malformed solver result");
        var chosen=bids.stream().filter(x -> chosenIds.contains(x.id())).toList();boolean feasible=disjoint(chosen);int actual=chosen.stream().mapToInt(SolverRelations.Bid::value).sum();var reference=enumerateSubsets(bids);return new Observation(fields("bids",bids.stream().map(SolverRelations.Bid::document).toList(),"selected",chosenIds,"reported_value",((Number)reported).intValue(),"actual_value",actual,"feasible",feasible,"value_correct",((Number)reported).intValue()==actual,"optimum",reference.optimum(),"feasible_subsets",reference.feasibleSubsets(),"optimal_allocations",reference.optimalAllocations(),"optimal",feasible&&actual==reference.optimum(),"gap",feasible?reference.optimum()-actual:null),reference.subsetChecks());
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> relations(Map<String,Object> variants) { var a=(Map<String,Object>)variants.get("base");var b=(Map<String,Object>)variants.get("relabeled");var c=(Map<String,Object>)variants.get("edited");return fields("reference_relabel",a.get("optimum").equals(b.get("optimum")),"reference_edit",b.get("optimum").equals(c.get("optimum")),"solver_relabel",a.get("reported_value").equals(b.get("reported_value")),"solver_edit",b.get("reported_value").equals(c.get("reported_value"))); }
}
