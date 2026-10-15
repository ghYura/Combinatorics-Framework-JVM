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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

/** D5 B recursive scope interpreter with flattening and leaked current-field faults. */
public final class NestedScopes {
    private NestedScopes() { }
    public static final List<String> POLICIES=List.of("correct","flatten_scope","leak_scope");
    public sealed interface Node permits Op,Scope { }
    public record Op(String code) implements Node { public Op { if(!List.of("A","M","S","N").contains(code))throw new IllegalArgumentException("known operation required"); } }
    public record Scope(String field,List<Node> children) implements Node { public Scope { if(!List.of("x","y").contains(field))throw new IllegalArgumentException("x/y scope required");children=List.copyOf(children); } }
    public static Map<String,Object> fields(Object... pairs) { return FieldPipeline.fields(pairs); }
    public static Map<String,Object> freeze(Map<String,Object> value) { return FieldPipeline.freeze(value); }
    public static String json(Object value) { return FieldPipeline.json(value); }
    private static void validate(List<Node> nodes,int depth,int[] count) { if(depth>64)throw new IllegalArgumentException("scope depth exceeds bounded port64");for(var node:nodes){if(++count[0]>128)throw new IllegalArgumentException("tree exceeds bounded port128nodes");if(node instanceof Scope s)validate(s.children(),depth+1,count);else if(!(node instanceof Op))throw new IllegalArgumentException("operation/scope node required");} }
    public static List<Node> tree(List<Node> nodes) { var copy=List.copyOf(nodes);validate(copy,0,new int[1]);return copy; }
    public static List<Object> document(List<Node> nodes) { return tree(nodes).stream().map(n->n instanceof Op o?(Object)o.code():fields("scope",((Scope)n).field(),"children",document(((Scope)n).children()))).toList(); }
    public static List<Node> fromDocument(Object value) { return read(value,0,new int[1]); }
    private static List<Node> read(Object value,int depth,int[] count) { if(depth>64||!(value instanceof List<?> list))throw new IllegalArgumentException("bounded node list required");var out=new ArrayList<Node>();for(Object node:list){if(++count[0]>128)throw new IllegalArgumentException("tree exceeds128nodes");if(node instanceof String op)out.add(new Op(op));else if(node instanceof Map<?,?> s&&s.keySet().equals(Set.of("scope","children"))&&s.get("scope")instanceof String field)out.add(new Scope(field,read(s.get("children"),depth+1,count)));else throw new IllegalArgumentException("operation/scope document required");}return List.copyOf(out); }
    public static String treeId(List<Node> input) {
        var nodes=tree(input);if(nodes.size()!=4||!(nodes.get(0)instanceof Scope a)||!a.field().equals("x")||a.children().size()!=1||!(a.children().getFirst()instanceof Scope b)||!b.field().equals("y")||b.children().size()!=2||!(b.children().get(0)instanceof Op z)||!List.of("A","M").contains(z.code())||!b.children().get(1).equals(new Op("N"))||!nodes.get(1).equals(new Op("S"))||!(nodes.get(2)instanceof Scope cat)||!cat.field().equals("x")||cat.children().size()!=1||!(cat.children().getFirst()instanceof Op c)||!List.of("A","M").contains(c.code())||!(nodes.get(3)instanceof Op tail)||!List.of("N","S").contains(tail.code()))throw new IllegalArgumentException("tree outside original D5 B shape");return "ZIP="+z.code()+"|CAT="+c.code()+"|TAIL="+tail.code();
    }
    public record Config(String phase,String policy,List<List<Node>> trees,List<List<String>> fragments) {
        public Config { if(!List.of("B1","B2").contains(phase)||!POLICIES.contains(policy))throw new IllegalArgumentException("original phase/policy required");trees=trees.stream().map(NestedScopes::tree).toList();fragments=fragments.stream().map(List::copyOf).toList();if(trees.size()!=(phase.equals("B1")?1:8)||trees.stream().map(NestedScopes::treeId).distinct().count()!=trees.size())throw new IllegalArgumentException("B1one tree/B2eight distinct trees required"); }
        public String caseId() { return phase+"|"+policy+"|"+(phase.equals("B1")?treeId(trees.getFirst()):"BUNDLE=all8"); }
    }
    public record Fold(Map<String,Object> record,List<Map<String,Object>> trace) { public Fold { record=freeze(record);trace=trace.stream().map(NestedScopes::freeze).toList(); } }
    private static final class Interpreter {
        private final String mode;private String current="x";private final Map<String,Object> record=new TreeMap<>(Map.of("x",2,"y",5));private final List<Map<String,Object>> trace=new ArrayList<>();
        Interpreter(String mode) { this.mode=mode; }
        void walk(List<Node> nodes,String field) { for(var node:nodes){if(node instanceof Op o){String target=mode.equals("leak_scope")?current:field;int value=(Integer)record.get(target);value=switch(o.code()){case "A"->Math.incrementExact(value);case "M"->Math.multiplyExact(2,value);case "S"->Math.subtractExact(value,3);case "N"->Math.negateExact(value);default->throw new IllegalArgumentException("operation required");};record.put(target,value);trace.add(fields("op",o.code(),"field",target,"value",value));}else{var s=(Scope)node;if(mode.equals("flatten_scope"))walk(s.children(),field);else if(mode.equals("leak_scope")){current=s.field();walk(s.children(),current);}else walk(s.children(),s.field());}} }
    }
    /** Fresh record and current field for every tree, including each member of a bundle. */
    public static Fold evaluate(List<Node> nodes,String mode) { if(!POLICIES.contains(mode))throw new IllegalArgumentException("unknown interpreter policy");var source=tree(nodes);var interpreter=new Interpreter(mode);interpreter.walk(source,"x");return new Fold(interpreter.record,interpreter.trace); }
    public record Result(Config config,Map<String,Object> document) { public Result { document=freeze(document); } public boolean passed() { return document.get("verdict").equals("PASS"); } }
    public static Result evaluate(Config config) {
        var results=new TreeMap<String,Object>();var order=new ArrayList<String>();var failing=new ArrayList<String>();for(var tree:config.trees()){String id=treeId(tree);var observed=evaluate(tree,config.policy());var reference=NestedScopesOracle.evaluate(tree);boolean ok=NestedScopesOracle.sameRecord(observed.record(),reference.record());results.put(id,fields("tree_id",id,"tree",document(tree),"input",Map.of("x",2,"y",5),"expected",reference.record(),"observed",observed.record(),"sut_trace",observed.trace(),"reference_trace",reference.trace(),"verdict",ok?"PASS":"DOMAIN_FAIL"));order.add(id);if(!ok)failing.add(id);}var record=new TreeMap<>(fields("schema","d5b.observation/v1","contract","B","case_id",config.caseId(),"phase",config.phase(),"policy",config.policy(),"fragments",config.fragments(),"verdict",failing.isEmpty()?"PASS":"DOMAIN_FAIL","fw_var",failing.isEmpty()?0:2,"carrier","IMPL position 2 (legacy positional, not causal)"));if(config.phase().equals("B1"))record.put("result",results.get(order.getFirst()));else{record.put("generation_order",order);record.put("results",results);record.put("failing_trees",failing.stream().sorted().toList());}return new Result(config,record);
    }
    public static Map<String,String> attributes(Result result) { var out=new TreeMap<String,String>();result.document().forEach((k,v)->out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
