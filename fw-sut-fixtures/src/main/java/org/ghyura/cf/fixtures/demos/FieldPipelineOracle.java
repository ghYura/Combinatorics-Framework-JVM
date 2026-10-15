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
import java.util.Map;
import java.util.TreeMap;

/** Policy-blind reference owns its fold/arithmetic. Traces are diagnostic, never a verdict sensor. */
public final class FieldPipelineOracle {
    private FieldPipelineOracle() { }
    private static boolean integral(Object value) { return value instanceof Byte||value instanceof Integer||value instanceof Long; }
    public static FieldPipeline.Fold evaluate(Map<String,Object> record,FieldPipeline.Tree tree) {
        var expected=new TreeMap<String,Object>();record.forEach((key,value)->{if(key==null||!integral(value))throw new IllegalArgumentException("integral reference input required");expected.put(key,Math.toIntExact(((Number)value).longValue()));});String field=tree.field();if(!expected.containsKey(field))throw new IllegalArgumentException("reference field absent");int value=(Integer)expected.get(field);var trace=new ArrayList<Map<String,Object>>();
        for(String op:tree.ops()){switch(op){case "A":value=Math.addExact(value,1);break;case "M":value=Math.multiplyExact(value,2);break;case "S":value=Math.subtractExact(value,3);break;case "N":value=Math.subtractExact(0,value);break;default:throw new IllegalArgumentException("unknown reference operation");}expected.put(field,value);trace.add(FieldPipeline.fields("op",op,"field",field,"value",value));}return new FieldPipeline.Fold(expected,trace);
    }
    /** Exact key set and integer values. Boolean/floating/missing/extra outputs fail the original comparison. */
    public static boolean sameRecord(Object actual,Object expected) {
        if(!(actual instanceof Map<?,?> a)||!(expected instanceof Map<?,?> e)||!a.keySet().equals(e.keySet()))return false;
        for(Object key:e.keySet()){Object av=a.get(key),ev=e.get(key);if(!(key instanceof String)||!integral(av)||!integral(ev)||((Number)av).longValue()!=((Number)ev).longValue())return false;}return true;
    }
}
