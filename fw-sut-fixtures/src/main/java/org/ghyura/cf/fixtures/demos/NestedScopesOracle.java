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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Independent policy-blind explicit work stack; does not call the recursive SUT or its arithmetic. */
public final class NestedScopesOracle {
    private NestedScopesOracle() { }
    private record Frame(NestedScopes.Node node,String field) { }
    private static void push(ArrayDeque<Frame> work,List<NestedScopes.Node> nodes,String field) { for(int i=nodes.size()-1;i>=0;i--)work.push(new Frame(nodes.get(i),field)); }
    public static NestedScopes.Fold evaluate(List<NestedScopes.Node> input) {
        var nodes=NestedScopes.tree(input);var record=new TreeMap<String,Object>(Map.of("x",2,"y",5));var trace=new ArrayList<Map<String,Object>>();var work=new ArrayDeque<Frame>();push(work,nodes,"x");while(!work.isEmpty()){var frame=work.pop();if(frame.node()instanceof NestedScopes.Scope scope)push(work,scope.children(),scope.field());else{String op=((NestedScopes.Op)frame.node()).code();int value=(Integer)record.get(frame.field());if(op.equals("A"))value=Math.addExact(value,1);else if(op.equals("M"))value=Math.multiplyExact(value,2);else if(op.equals("S"))value=Math.subtractExact(value,3);else if(op.equals("N"))value=Math.subtractExact(0,value);else throw new IllegalArgumentException("unknown reference operation");record.put(frame.field(),value);trace.add(NestedScopes.fields("op",op,"field",frame.field(),"value",value));}}return new NestedScopes.Fold(record,trace);
    }
    public static boolean sameRecord(Object observed,Object expected) { if(!(observed instanceof Map<?,?> a)||!(expected instanceof Map<?,?> e)||!a.keySet().equals(e.keySet()))return false;for(Object key:e.keySet()){Object av=a.get(key),ev=e.get(key);if(!(key instanceof String)||!(av instanceof Byte||av instanceof Integer||av instanceof Long)||!(ev instanceof Byte||ev instanceof Integer||ev instanceof Long)||((Number)av).longValue()!=((Number)ev).longValue())return false;}return true; }
}
