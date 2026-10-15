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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D5 A record adapter; operations, field and policy only, with both deliberate faults retained. */
public final class FieldPipeline {
    private FieldPipeline() { }
    public static final List<String> POLICIES=List.of("correct","reverse_pair","wrong_field"),OPS=List.of("A","M","S","N"),FIELDS=List.of("x","y");
    public static final Map<String,Object> INPUT=Map.of("x",2,"y",5);
    public record Tree(String field,List<String> ops) {
        public Tree { ops=List.copyOf(ops);if(!FIELDS.contains(field)||ops.size()!=2||ops.get(0).equals(ops.get(1))||!OPS.containsAll(ops))throw new IllegalArgumentException("two distinct known operations and x/y binding required"); }
        public Map<String,Object> document() { return fields("field",field,"ops",ops); }
    }
    public record Config(String policy,Tree tree) {
        public Config { if(!POLICIES.contains(policy)||tree==null)throw new IllegalArgumentException("known D5 A policy/tree required"); }
        public String caseId() { return "A|"+policy+"|OPS="+String.join("",tree.ops())+"|FIELD="+tree.field(); }
    }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> value) { return (Map<String,Object>)ToolCallOrder.freezeDocument(value); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    public record Fold(Map<String,Object> record,List<Map<String,Object>> trace) { public Fold { record=freeze(record);trace=trace.stream().map(FieldPipeline::freeze).toList(); } }
    /** Copies the input and preserves all other fields; checked integers refuse overflow. */
    public static Fold adapt(Map<String,Object> record,Tree tree,String policy) {
        if(!POLICIES.contains(policy))throw new IllegalArgumentException("unknown adapter policy");var out=new TreeMap<String,Object>();record.forEach((k,v)->{if(k==null||!(v instanceof Byte||v instanceof Integer||v instanceof Long))throw new IllegalArgumentException("integer input record required");out.put(k,Math.toIntExact(((Number)v).longValue()));});String field=policy.equals("wrong_field")?(tree.field().equals("x")?"y":"x"):tree.field();if(!out.containsKey(field))throw new IllegalArgumentException("bound field absent from input");var ops=new ArrayList<>(tree.ops());if(policy.equals("reverse_pair"))Collections.reverse(ops);var trace=new ArrayList<Map<String,Object>>();int value=(Integer)out.get(field);
        for(String op:ops){value=switch(op){case "A"->Math.incrementExact(value);case "M"->Math.multiplyExact(2,value);case "S"->Math.subtractExact(value,3);case "N"->Math.negateExact(value);default->throw new IllegalArgumentException("unknown operation");};out.put(field,value);trace.add(fields("op",op,"field",field,"value",value));}return new Fold(out,trace);
    }
    public record Result(Config config,Map<String,Object> document) { public Result { document=freeze(document); } public boolean passed() { return document.get("verdict").equals("PASS"); } }
    public static Result evaluate(Config c) {
        var actual=adapt(INPUT,c.tree(),c.policy());var reference=FieldPipelineOracle.evaluate(INPUT,c.tree());boolean ok=FieldPipelineOracle.sameRecord(actual.record(),reference.record());var fragments=new ArrayList<List<String>>();c.tree().ops().forEach(op->fragments.add(List.of("push_op",op)));fragments.add(List.of("bind",c.tree().field()));return new Result(c,fields("schema","d5a.observation/v1","contract","A","case_id",c.caseId(),"phase","A","policy",c.policy(),"tree",c.tree().document(),"fragments",fragments,"input",INPUT,"expected",reference.record(),"observed",actual.record(),"sut_trace",actual.trace(),"reference_trace",reference.trace(),"traces_equal",actual.trace().equals(reference.trace()),"verdict",ok?"PASS":"DOMAIN_FAIL","fw_var",ok?0:2,"carrier","IMPL position 2 (legacy positional, not causal)"));
    }
    public static Map<String,String> attributes(Result r) { var out=new TreeMap<String,String>();r.document().forEach((k,v)->out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
