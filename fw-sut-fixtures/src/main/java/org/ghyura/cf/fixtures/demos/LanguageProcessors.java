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
import java.util.Set;
import java.util.TreeMap;

/** D14a's local AST builder, three original compiler policies and policy-blind stack VM. */
public final class LanguageProcessors {
    private LanguageProcessors() { }
    public static final List<String> POLICIES=List.of("faithful","reverse_sub","alias_dead_temp"), ORDERS=List.of("XY","YX"), SHAPES=List.of("L","R"), OPERATORS=List.of("+","-","*");
    public static final List<Byte> CONSTANTS=List.of((byte)-1,(byte)2);
    public record Config(String policy,String order,String shape,byte x,byte y,String opA,String opB) {
        public Config { if(!POLICIES.contains(policy)||!ORDERS.contains(order)||!SHAPES.contains(shape)||!CONSTANTS.contains(x)||!CONSTANTS.contains(y)||!OPERATORS.contains(opA)||!OPERATORS.contains(opB))throw new IllegalArgumentException("unknown D14a coordinate"); }
        public String caseId() { return "P="+policy+"|O="+order+"|H="+shape+"|X="+x+"|Y="+y+"|A="+opA+"|B="+opB; }
    }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> value) { return (Map<String,Object>)ToolCallOrder.freezeDocument(value); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    static int integer(Object value) { if(!(value instanceof Byte||value instanceof Integer||value instanceof Long)||((Number)value).longValue()<Integer.MIN_VALUE||((Number)value).longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("strict local integer required");return ((Number)value).intValue(); }
    private static String name(Object value) { if(!(value instanceof String s))throw new IllegalArgumentException("string name required");return s; }
    private static Map<?,?> object(Object value) { if(!(value instanceof Map<?,?> m))throw new IllegalArgumentException("AST object required");return m; }
    public static Map<String,Object> expression(String shape,String a,String b) { if(!SHAPES.contains(shape)||!OPERATORS.contains(a)||!OPERATORS.contains(b))throw new IllegalArgumentException("unknown expression coordinate");var x=fields("var","x");var y=fields("var","y");return shape.equals("L")?fields("op",b,"left",fields("op",a,"left",x,"right",y),"right",x):fields("op",a,"left",x,"right",fields("op",b,"left",y,"right",x)); }
    public static Map<String,Map<String,Object>> programs(Config c) { var bindings=new ArrayList<Map<String,Object>>();for(char n:c.order().toLowerCase(java.util.Locale.ROOT).toCharArray())bindings.add(fields("name",String.valueOf(n),"value",n=='x'?c.x():c.y()));var transformed=List.of(bindings.getFirst(),fields("name","z","value",7),bindings.getLast());var expr=expression(c.shape(),c.opA(),c.opB());return Map.of("original",freeze(fields("bindings",bindings,"expr",expr)),"transformed",freeze(fields("bindings",transformed,"expr",expr))); }
    private static void emit(Object value,String policy,List<List<Object>> code) { var node=object(value);if(node.keySet().equals(Set.of("var"))){code.add(List.of("LOAD",name(node.get("var"))));return;}if(!node.keySet().equals(Set.of("op","left","right")))throw new IllegalArgumentException("invalid expression node");String op=name(node.get("op"));String instruction=switch(op){case "+" -> "ADD";case "-" -> "SUB";case "*" -> "MUL";default -> throw new IllegalArgumentException("unknown binary operator");};boolean reverse=policy.equals("reverse_sub")&&op.equals("-");emit(node.get(reverse?"right":"left"),policy,code);emit(node.get(reverse?"left":"right"),policy,code);code.add(List.of(instruction)); }
    /** Emission never evaluates the AST, calls an interpreter/oracle or sees an expected value. */
    public static List<List<Object>> compile(Map<?,?> program,String policy) { if(!POLICIES.contains(policy)||!program.keySet().equals(Set.of("bindings","expr"))||!(program.get("bindings") instanceof List<?> bindings))throw new IllegalArgumentException("malformed compiler input");var code=new ArrayList<List<Object>>();for(Object v:bindings){var binding=object(v);if(!binding.keySet().equals(Set.of("name","value")))throw new IllegalArgumentException("malformed binding");String n=name(binding.get("name"));code.add(List.of("PUSH",integer(binding.get("value"))));code.add(List.of("STORE",policy.equals("alias_dead_temp")&&n.equals("z")?"x":n));}emit(program.get("expr"),policy,code);code.add(List.of("RETURN"));return List.copyOf(code); }
    public record Execution(int value,List<Map<String,Object>> trace) { public Execution { trace=trace.stream().map(LanguageProcessors::freeze).toList(); } }
    /** Fresh stack/locals, left then right arithmetic and immutable post-instruction snapshots. */
    public static Execution execute(List<?> code) { if(code.isEmpty()||!List.of("RETURN").equals(code.getLast()))throw new IllegalArgumentException("final RETURN required");var stack=new ArrayList<Integer>();var locals=new TreeMap<String,Integer>();var trace=new ArrayList<Map<String,Object>>();Integer returned=null;
        for(int ip=0;ip<code.size();ip++){if(!(code.get(ip) instanceof List<?> ins)||ins.isEmpty()||!(ins.getFirst() instanceof String op))throw new IllegalArgumentException("malformed instruction");int arity=switch(op){case "PUSH","STORE","LOAD" -> 1;case "ADD","SUB","MUL","RETURN" -> 0;default -> throw new IllegalArgumentException("unknown opcode");};if(ins.size()!=arity+1)throw new IllegalArgumentException("instruction arity");switch(op){
            case "PUSH" -> stack.add(integer(ins.get(1)));
            case "STORE" -> {String n=name(ins.get(1));if(stack.isEmpty())throw new IllegalArgumentException("STORE underflow");locals.put(n,stack.removeLast());}
            case "LOAD" -> {String n=name(ins.get(1));if(!locals.containsKey(n))throw new IllegalArgumentException("unbound VM local");stack.add(locals.get(n));}
            case "ADD","SUB","MUL" -> {if(stack.size()<2)throw new IllegalArgumentException("arithmetic underflow");int right=stack.removeLast(),left=stack.removeLast();stack.add(switch(op){case "ADD" -> Math.addExact(left,right);case "SUB" -> Math.subtractExact(left,right);default -> Math.multiplyExact(left,right);});}
            case "RETURN" -> {if(ip!=code.size()-1||stack.size()!=1)throw new IllegalArgumentException("invalid RETURN");returned=stack.removeLast();}
            default -> throw new IllegalArgumentException("unknown opcode");
        }trace.add(freeze(fields("ip",ip,"instruction",ins,"stack",stack,"locals",locals,"returned",returned)));}if(returned==null)throw new IllegalArgumentException("missing result");return new Execution(returned,trace);
    }
    public record Result(Config config,Map<String,Object> observations,Map<String,Object> checks) {
        public Result { observations=freeze(observations);checks=freeze(checks); }
        public boolean passed() { return checks.size()==3&&checks.values().stream().allMatch(Boolean.TRUE::equals); }
        public Map<String,Object> document() { return fields("schema","d14a.observation/v1","contract","v1","case_id",config.caseId(),"policy",config.policy(),"order",config.order(),"shape",config.shape(),"x",config.x(),"y",config.y(),"op_a",config.opA(),"op_b",config.opB(),"declared",config.order().toLowerCase(java.util.Locale.ROOT).chars().mapToObj(n -> String.valueOf((char)n)).toList(),"observations",observations,"checks",checks,"verdict",passed()?"PASS":"DOMAIN_FAIL","fw_var",passed()?0:2,"carrier_slot","IMPL position 2 (legacy positional, not causal)"); }
    }
    public static Result evaluate(Config c) { var observations=new TreeMap<String,Object>();var programs=programs(c);for(String kind:List.of("original","transformed")){var p=programs.get(kind);int reference=LanguageProcessorsOracle.interpret(p);var code=compile(p,c.policy());var execution=execute(code);observations.put(kind,freeze(fields("program",p,"reference_value",reference,"bytecode",code,"vm_value",execution.value(),"vm_trace",execution.trace())));}return new Result(c,observations,LanguageProcessorsOracle.judge(observations)); }
    public static Map<String,String> attributes(Result r) { var out=new TreeMap<String,String>();r.document().forEach((k,v) -> out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
