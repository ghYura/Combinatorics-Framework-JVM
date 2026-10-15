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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Independent name-based AST interpreter and policy-blind four-number oracle. */
public final class LanguageProcessorsOracle {
    private LanguageProcessorsOracle() { }
    private static int integer(Object value) { if(!(value instanceof Byte||value instanceof Integer||value instanceof Long)||((Number)value).longValue()<Integer.MIN_VALUE||((Number)value).longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("strict reference integer required");return ((Number)value).intValue(); }
    private static Map<?,?> object(Object value) { if(!(value instanceof Map<?,?> m))throw new IllegalArgumentException("reference object required");return m; }
    private static int value(Object expression,Map<String,Integer> env) { var n=object(expression);if(n.keySet().equals(Set.of("var"))){if(!(n.get("var") instanceof String name)||!env.containsKey(name))throw new IllegalArgumentException("unbound reference variable");return env.get(name);}if(!n.keySet().equals(Set.of("op","left","right"))||!(n.get("op") instanceof String op))throw new IllegalArgumentException("invalid reference node");int left=value(n.get("left"),env),right=value(n.get("right"),env);return switch(op){case "+" -> Math.addExact(left,right);case "-" -> Math.subtractExact(left,right);case "*" -> Math.multiplyExact(left,right);default -> throw new IllegalArgumentException("unknown reference operator");}; }
    /** No policy, compiler, bytecode, VM or shared arithmetic helper is used. */
    public static int interpret(Map<?,?> program) { if(!program.keySet().equals(Set.of("bindings","expr"))||!(program.get("bindings") instanceof List<?> bindings))throw new IllegalArgumentException("invalid reference program");var env=new TreeMap<String,Integer>();for(Object raw:bindings){var b=object(raw);if(!b.keySet().equals(Set.of("name","value"))||!(b.get("name") instanceof String n))throw new IllegalArgumentException("invalid reference binding");env.put(n,integer(b.get("value")));}return value(program.get("expr"),env); }
    /** Reference agreement is an integrity precondition; diagnostics cannot change the verdict. */
    public static Map<String,Object> judge(Map<String,Object> observations) { var original=object(observations.get("original"));var transformed=object(observations.get("transformed"));int a=integer(original.get("reference_value")),b=integer(transformed.get("reference_value")),x=integer(original.get("vm_value")),y=integer(transformed.get("vm_value"));if(a!=b)throw new IllegalArgumentException("reference values must agree before sensors");return Map.of("original_differential",x==a,"transformed_differential",y==b,"metamorphic",x==y); }
}
