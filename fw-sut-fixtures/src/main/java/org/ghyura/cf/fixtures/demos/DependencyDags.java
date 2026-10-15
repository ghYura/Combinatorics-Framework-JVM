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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Original four-node D8b catalogue. Cache updates and policy-blind recursive references are separate routes. */
public final class DependencyDags {
    public static final List<String> POLICIES=List.of("closure_forward","direct_only","closure_reverse");
    private static final String IDS="ABCD";
    private static final List<Integer> BASE=List.of(1,2,4,8);
    private static final List<Edge> SLOTS=List.of(new Edge(0,1),new Edge(0,2),new Edge(0,3),new Edge(1,2),new Edge(1,3),new Edge(2,3));
    private DependencyDags(){}
    public record Edge(int from,int to){}
    public record Read(String node,int value){}
    public record Update(String node,int base,List<Read> parentReads,int previous,int value,List<Integer> cache){
        public Update{parentReads=List.copyOf(parentReads);cache=List.copyOf(cache);}
    }
    public record Trace(List<Integer> before,List<Integer> postEdit,List<String> dirty,List<String> order,List<Update> updates,List<Integer> after){
        public Trace{before=List.copyOf(before);postEdit=List.copyOf(postEdit);dirty=List.copyOf(dirty);order=List.copyOf(order);updates=List.copyOf(updates);after=List.copyOf(after);}
    }
    public record Reference(List<Integer> before,List<Integer> after,List<List<Integer>> paths,List<Integer> delta,List<String> affected){
        public Reference{before=List.copyOf(before);after=List.copyOf(after);paths=paths.stream().map(List::copyOf).toList();delta=List.copyOf(delta);affected=List.copyOf(affected);}
    }
    public record Result(String policy,int graph,int edit,Trace trace,Reference reference,List<String> mismatched){
        public Result{mismatched=List.copyOf(mismatched);}
        public String caseId(){return policy+"|G="+code(graph)+"|U="+node(edit);}
        public boolean passed(){return trace.before().equals(reference.before())&&mismatched.isEmpty();}
    }
    public static String code(int graph){checkGraph(graph);return String.format(java.util.Locale.ROOT,"%6s",Integer.toBinaryString(graph)).replace(' ','0');}
    public static int graph(String bits){if(bits==null||!bits.matches("[01]{6}"))throw new IllegalArgumentException("six binary graph bits required");return Integer.parseInt(bits,2);}
    private static void checkGraph(int graph){if(graph<0||graph>=64)throw new IllegalArgumentException("D8b requires one of 64 forward graphs");}
    private static String node(int n){if(n<0||n>=4)throw new IllegalArgumentException("D8b node must be A/B/C/D");return IDS.substring(n,n+1);}
    private static List<Edge> edges(int graph){checkGraph(graph);List<Edge> out=new ArrayList<>();for(int k=0;k<6;k++)if((graph&(1<<(5-k)))!=0)out.add(SLOTS.get(k));return List.copyOf(out);}
    private static List<Integer> ints(int[] a){return java.util.Arrays.stream(a).boxed().toList();}
    private static List<String> names(List<Integer> a){return a.stream().map(DependencyDags::node).toList();}

    /** Stateful SUT: each dirty node is evaluated once from its parents' current cached values. */
    public static Trace update(String policy,int graph,int edit){
        if(!POLICIES.contains(policy))throw new IllegalArgumentException("unknown D8b policy");node(edit);var edges=edges(graph);
        int[] base={1,2,4,8},values=new int[4];
        for(int v=0;v<4;v++){values[v]=base[v];for(var e:edges)if(e.to()==v)values[v]=Math.addExact(values[v],values[e.from()]);}
        var before=ints(values);base[edit]+=10;var postEdit=ints(values);
        Set<Integer> dirty=new TreeSet<>();dirty.add(edit);
        if(policy.equals("direct_only")){for(var e:edges)if(e.from()==edit)dirty.add(e.to());}
        else{List<Integer> stack=new ArrayList<>();stack.add(edit);while(!stack.isEmpty()){int u=stack.removeLast();for(var e:edges)if(e.from()==u&&dirty.add(e.to()))stack.add(e.to());}}
        var sorted=new ArrayList<>(dirty);var order=new ArrayList<>(sorted);if(policy.equals("closure_reverse"))Collections.reverse(order);
        List<Update> updates=new ArrayList<>();
        for(int v:order){List<Read> reads=new ArrayList<>();int fresh=base[v];for(var e:edges)if(e.to()==v){reads.add(new Read(node(e.from()),values[e.from()]));fresh=Math.addExact(fresh,values[e.from()]);}int previous=values[v];values[v]=fresh;updates.add(new Update(node(v),base[v],reads,previous,fresh,ints(values)));}
        return new Trace(before,postEdit,names(sorted),names(order),updates,ints(values));
    }
    /** Independent reference: recursive fresh values and recursive successor path counts, no cache or policy input. */
    public static Reference reference(int graph,int edit){
        var edges=edges(graph);node(edit);int[] base={1,2,4,8},changed={1,2,4,8};changed[edit]+=10;
        Integer[] memoBefore=new Integer[4],memoAfter=new Integer[4];List<Integer> before=new ArrayList<>(),after=new ArrayList<>();
        for(int v=0;v<4;v++){before.add(fresh(v,base,edges,memoBefore));after.add(fresh(v,changed,edges,memoAfter));}
        Integer[][] memo=new Integer[4][4];List<List<Integer>> paths=new ArrayList<>();
        for(int u=0;u<4;u++){List<Integer> row=new ArrayList<>();for(int v=0;v<4;v++)row.add(paths(u,v,edges,memo));paths.add(row);}
        List<Integer> delta=new ArrayList<>();List<String> affected=new ArrayList<>();for(int v=0;v<4;v++){int count=paths.get(edit).get(v);delta.add(Math.multiplyExact(10,count));if(count!=0)affected.add(node(v));}
        return new Reference(before,after,paths,delta,affected);
    }
    private static int fresh(int v,int[] base,List<Edge> edges,Integer[] memo){if(memo[v]==null){int value=base[v];for(var e:edges)if(e.to()==v)value=Math.addExact(value,fresh(e.from(),base,edges,memo));memo[v]=value;}return memo[v];}
    private static int paths(int u,int v,List<Edge> edges,Integer[][] memo){if(memo[u][v]==null){int n=u==v?1:0;if(u!=v)for(var e:edges)if(e.from()==u)n=Math.addExact(n,paths(e.to(),v,edges,memo));memo[u][v]=n;}return memo[u][v];}
    public static Result evaluate(String policy,int graph,int edit){var trace=update(policy,graph,edit);var ref=reference(graph,edit);List<String> wrong=new ArrayList<>();for(int v=0;v<4;v++)if(!trace.after().get(v).equals(ref.after().get(v)))wrong.add(node(v));return new Result(policy,graph,edit,trace,ref,wrong);}
    private static String updates(List<Update> updates){List<String> out=new ArrayList<>();for(var u:updates)out.add(u.node()+"(base="+u.base()+",reads="+String.join(",",u.parentReads().stream().map(r->r.node()+"="+r.value()).toList())+",previous="+u.previous()+",new="+u.value()+",cache="+u.cache()+")");return String.join(";",out);}
    /** Lossless typed observation fields replace original Python stdout/base64 transport; no interpreter executes. */
    public static Map<String,String> attributes(Result r){
        Map<String,String> a=new TreeMap<>();a.put("schema","d8b.observation/v1");a.put("contract","v1");a.put("case",r.caseId());a.put("policy",r.policy());a.put("bits",code(r.graph()).chars().map(c->c-'0').boxed().toList().toString());a.put("edges",edges(r.graph()).stream().map(e->List.of(e.from(),e.to())).toList().toString());a.put("edit",node(r.edit()));
        a.put("before_inputs",BASE.toString());List<Integer> changed=new ArrayList<>(BASE);changed.set(r.edit(),changed.get(r.edit())+10);a.put("after_inputs",changed.toString());a.put("before_values",r.trace().before().toString());a.put("before_reference",r.reference().before().toString());a.put("post_edit_cache",r.trace().postEdit().toString());a.put("path_counts",r.reference().paths().toString());a.put("reference",r.reference().after().toString());a.put("expected_delta",r.reference().delta().toString());a.put("affected",r.reference().affected().toString());a.put("dirty",r.trace().dirty().toString());a.put("evaluation_order",r.trace().order().toString());a.put("updates",updates(r.trace().updates()));a.put("after_values",r.trace().after().toString());a.put("mismatched_nodes",r.mismatched().toString());a.put("verdict",r.passed()?"PASS":"DOMAIN_FAIL");a.put("fw_var",r.passed()?"0":"2");a.put("carrier","IMPL position 2 (legacy positional, not causal)");return Map.copyOf(a);
    }

    public record GraphOrders(String bits,List<String> orders){public GraphOrders{orders=List.copyOf(orders);}}
    public record Addition(String before,String after,Edge edge,List<String> beforeOrders,List<String> afterOrders){
        public Addition{beforeOrders=List.copyOf(beforeOrders);afterOrders=List.copyOf(afterOrders);}
        public boolean equalSets(){return beforeOrders.equals(afterOrders);}
    }
    public record Topology(List<GraphOrders> graphs,List<Addition> additions){
        public Topology{graphs=List.copyOf(graphs);additions=List.copyOf(additions);}
        public int graphOrderPairs(){return graphs.stream().mapToInt(g->g.orders().size()).sum();}
    }
    /** Caller supplies all 24 existing algebra permutations and owns retained inputs/results. Order proof only. */
    public static Topology topology(Set<String> observedGraphs,Iterator<int[]> permutations){
        Set<String> expected=new TreeSet<>();for(int g=0;g<64;g++)expected.add(code(g));if(!observedGraphs.equals(expected))throw new IllegalArgumentException("all 64 graph identities required");
        Set<String> orders=new TreeSet<>();while(permutations.hasNext()){int[] a=permutations.next();if(a==null||a.length!=4)throw new IllegalArgumentException("four-node permutation required");Set<Integer> seen=new HashSet<>();StringBuilder word=new StringBuilder();for(int v:a){node(v);if(!seen.add(v))throw new IllegalArgumentException("repeated node in permutation");word.append(node(v));}if(!orders.add(word.toString()))throw new IllegalArgumentException("duplicate permutation");}
        if(orders.size()!=24)throw new IllegalArgumentException("all 24 permutations required");
        Map<String,List<String>> bank=new TreeMap<>();List<GraphOrders> graphs=new ArrayList<>();
        for(String bits:expected){var edges=edges(graph(bits));var valid=orders.stream().filter(order->edges.stream().allMatch(e->order.indexOf(node(e.from()))<order.indexOf(node(e.to())))).toList();bank.put(bits,valid);graphs.add(new GraphOrders(bits,valid));}
        List<Addition> additions=new ArrayList<>();for(String bits:expected){int graph=graph(bits);var edges=edges(graph);for(int k=0;k<6;k++)if((graph&(1<<(5-k)))==0&&reachable(edges,SLOTS.get(k).from(),SLOTS.get(k).to())){String after=code(graph|(1<<(5-k)));additions.add(new Addition(bits,after,SLOTS.get(k),bank.get(bits),bank.get(after)));}}
        return new Topology(graphs,additions);
    }
    private static boolean reachable(List<Edge> edges,int u,int v){Set<Integer> seen=new HashSet<>();List<Integer> frontier=new ArrayList<>();frontier.add(u);while(!frontier.isEmpty()){int x=frontier.removeLast();for(var e:edges)if(e.from()==x&&seen.add(e.to()))frontier.add(e.to());}return seen.contains(v);}
}
