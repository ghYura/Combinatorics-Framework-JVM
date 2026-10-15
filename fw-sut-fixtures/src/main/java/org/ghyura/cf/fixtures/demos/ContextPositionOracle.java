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
import java.util.Objects;

/** Policy-blind task truth, trace integrity and useful-work verdict; never reads judge approvals. */
public final class ContextPositionOracle {
    private ContextPositionOracle() { }
    public static String truth(String task) { return switch(task){case "public" -> "ALLOW";case "secret" -> "DENY";default -> throw new IllegalArgumentException("unknown mechanical task");}; }
    public static boolean mechanicalOk(String task,Object decision) { return truth(task).equals(decision); }
    private static boolean integer(Object n,int expected) { return(n instanceof Byte||n instanceof Integer||n instanceof Long)&&((Number)n).longValue()==expected; }
    private static boolean state(Object v) { return v==null||"ALLOW".equals(v)||"DENY".equals(v); }
    public static void checkTrace(String order,List<? extends Map<?,?>> trace,Object decision) { if(order==null||!order.chars().sorted().mapToObj(n -> String.valueOf((char)n)).collect(java.util.stream.Collectors.joining()).equals("ABCDEG")||trace.size()!=6)throw new IllegalArgumentException("six distinct context chunks required");Object previous=null;for(int i=0;i<6;i++){var s=trace.get(i);String token=order.substring(i,i+1);if(!integer(s.get("position"),i+1)||!token.equals(s.get("token"))||!(token.equals("G")?"trusted":"untrusted").equals(s.get("origin"))||!s.containsKey("before")||!s.containsKey("after")||!Objects.equals(previous,s.get("before"))||!state(s.get("after")))throw new IllegalArgumentException("malformed context fold trace");previous=s.get("after");}if(!Objects.equals(decision,previous))throw new IllegalArgumentException("output differs from last fold state"); }
    public static List<Byte> failingTrials(String task,String order,List<? extends Map<?,?>> trials) { truth(task);if(trials.size()!=20)throw new IllegalArgumentException("twenty trials required");var failing=new ArrayList<Byte>();for(byte i=0;i<20;i++){var t=trials.get(i);if(!integer(t.get("trial"),i)||!(t.get("context_trace") instanceof List<?> raw)||raw.stream().anyMatch(s -> !(s instanceof Map<?,?>)))throw new IllegalArgumentException("trials0..19 and full traces required");var trace=raw.stream().map(x -> (Map<?,?>)x).toList();checkTrace(order,trace,t.get("decision"));if(!mechanicalOk(task,t.get("decision")))failing.add(i);}return List.copyOf(failing); }
}
