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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** D14c's tiny local auction inputs, transformations and original three solvers. */
public final class SolverRelations {
    private SolverRelations() { }
    public static final List<String> POLICIES=List.of("exact_dp","greedy_value","min_only_dp"), INSTANCES=List.of("bundle_trap","tie","disjoint","overlap"), EDITS=List.of("none","dominated");
    public static final List<Byte> GOODS=List.of((byte)0,(byte)1,(byte)2);
    public record Config(String policy,String instance,List<Byte> permutation,String edit) {
        public Config { if(policy==null||instance==null||edit==null||!POLICIES.contains(policy)||!INSTANCES.contains(instance)||!EDITS.contains(edit))throw new IllegalArgumentException("unknown D14c configuration");permutation=List.copyOf(permutation);if(permutation.size()!=3||!new TreeSet<>(permutation).equals(new TreeSet<>(GOODS)))throw new IllegalArgumentException("three distinct goods required"); }
        public String caseId() { return "P="+policy+"|I="+instance+"|R="+permutation.stream().map(Object::toString).collect(java.util.stream.Collectors.joining())+"|E="+edit; }
    }
    public record Bid(String id,List<Byte> items,int value) {
        public Bid { items=List.copyOf(items);if(id==null||id.isBlank()||!items.equals(new ArrayList<>(new TreeSet<>(items)))||!GOODS.containsAll(items))throw new IllegalArgumentException("malformed bid"); }
        public Map<String,Object> document() { return fields("id",id,"items",items,"value",value); }
    }
    public record Answer(List<String> selected,int reportedValue) {
        public Answer { selected=List.copyOf(selected); }
        public Map<String,Object> document() { return fields("selected",selected,"reported_value",reportedValue); }
    }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> value) { return (Map<String,Object>)ToolCallOrder.freezeDocument(value); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    private static Bid bid(String id,int value,int... goods) { var items=new ArrayList<Byte>();for(int g:goods)items.add((byte)g);return new Bid(id,items,value); }
    public static List<Bid> baseBids(String name) { return switch(name) {
        case "bundle_trap" -> List.of(bid("b0",7,0,1),bid("b1",4,0),bid("b2",4,1),bid("b3",2,2));
        case "tie" -> List.of(bid("b0",8,0,1),bid("b1",4,0),bid("b2",4,1),bid("b3",2,2));
        case "disjoint" -> List.of(bid("b0",5,0),bid("b1",4,1),bid("b2",3,2),bid("b3",6,0,1,2));
        case "overlap" -> List.of(bid("b0",6,0,1),bid("b1",5,1,2),bid("b2",4,0,2),bid("b3",2,2));
        default -> throw new IllegalArgumentException("unknown bid instance"); }; }
    public static List<Bid> relabel(List<Bid> bids,List<Byte> permutation) { if(permutation.size()!=3||!new TreeSet<>(permutation).equals(new TreeSet<>(GOODS)))throw new IllegalArgumentException("invalid goods bijection");return bids.stream().map(b -> new Bid(b.id(),b.items().stream().map(g -> permutation.get(g)).sorted().toList(),b.value())).toList(); }
    public static List<Bid> edit(List<Bid> bids,String edit) { if("none".equals(edit))return List.copyOf(bids);if(!"dominated".equals(edit)||bids.isEmpty()||!bids.getFirst().id().equals("b0")||bids.getFirst().items().isEmpty()||bids.getFirst().value()<2)throw new IllegalArgumentException("dominated edit requires nonempty b0 and value at least two");var out=new ArrayList<>(bids);var first=bids.getFirst();out.add(new Bid("b4",first.items(),first.value()-1));return List.copyOf(out); }
    private static byte mask(List<Byte> items,boolean firstOnly) { byte mask=0;for(int i=0;i<items.size()&&(!firstOnly||i==0);i++)mask|=(byte)(1<<items.get(i));return mask; }
    private static int compareIds(List<String> a,List<String> b) { for(int i=0;i<Math.min(a.size(),b.size());i++){int c=a.get(i).compareTo(b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size()); }
    private static int compare(Answer a,Answer b) { int c=Integer.compare(a.reportedValue(),b.reportedValue());return c==0?compareIds(a.selected(),b.selected()):c; }
    /** DP reads only previous iteration states; it never enumerates subsets or calls the oracle. */
    public static Answer solve(String policy,List<Bid> bids) {
        if("greedy_value".equals(policy)){var used=new TreeSet<Byte>();var ids=new TreeSet<String>();int value=0;for(var b:bids.stream().sorted(Comparator.comparingInt(Bid::value).reversed().thenComparing(Bid::id)).toList())if(Collections.disjoint(used,b.items())){used.addAll(b.items());ids.add(b.id());value=Math.addExact(value,b.value());}return new Answer(new ArrayList<>(ids),value);}
        if(!"exact_dp".equals(policy)&&!"min_only_dp".equals(policy))throw new IllegalArgumentException("unknown solver policy");var best=new TreeMap<Byte,Answer>();best.put((byte)0,new Answer(List.of(),0));
        for(var bid:bids){byte mask=mask(bid.items(),policy.equals("min_only_dp"));var next=new TreeMap<>(best);for(var state:best.entrySet()){if((state.getKey()&mask)!=0)continue;var ids=new ArrayList<>(state.getValue().selected());ids.add(bid.id());Collections.sort(ids);var candidate=new Answer(ids,Math.addExact(state.getValue().reportedValue(),bid.value()));byte occupied=(byte)(state.getKey()|mask);var old=next.get(occupied);if(old==null||compare(candidate,old)>0)next.put(occupied,candidate);}best=next;}
        return best.values().stream().max(SolverRelations::compare).orElseThrow();
    }
    public record Result(Config config,Map<String,Object> variants,Map<String,Object> relations,Map<String,Object> subsetChecks) {
        public Result { variants=freeze(variants);relations=freeze(relations);subsetChecks=freeze(subsetChecks); }
        @SuppressWarnings("unchecked") public boolean passed() { return variants.values().stream().allMatch(v -> {var x=(Map<String,Object>)v;return Boolean.TRUE.equals(x.get("feasible"))&&Boolean.TRUE.equals(x.get("value_correct"))&&Boolean.TRUE.equals(x.get("optimal"));})&&relations.values().stream().allMatch(Boolean.TRUE::equals); }
        public Map<String,Object> document() { return fields("schema","d14c.observation/v1","contract","v1","id",config.caseId(),"policy",config.policy(),"instance",config.instance(),"permutation",config.permutation(),"edit",config.edit(),"variants",variants,"relations",relations,"verdict",passed()?"PASS":"DOMAIN_FAIL","fw_var",passed()?0:2,"solver_calls",(byte)3,"oracle_subset_checks",subsetChecks,"carrier_slot","IMPL position 2 (legacy positional, not causal)"); }
    }
    public static Result evaluate(Config c) { var base=baseBids(c.instance());var relabeled=relabel(base,c.permutation());var inputs=Map.of("base",base,"relabeled",relabeled,"edited",edit(relabeled,c.edit()));var variants=new TreeMap<String,Object>();var counts=new TreeMap<String,Object>();for(String k:List.of("base","relabeled","edited")){var bids=inputs.get(k);var judged=SolverRelationsOracle.observe(bids,solve(c.policy(),bids).document());variants.put(k,judged.document());counts.put(k,judged.subsetChecks());}return new Result(c,variants,SolverRelationsOracle.relations(variants),counts); }
    public static Map<String,String> attributes(Result r) { var out=new TreeMap<String,String>();r.document().forEach((k,v) -> out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
