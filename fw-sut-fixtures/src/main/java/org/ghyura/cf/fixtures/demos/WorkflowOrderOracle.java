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

/** Independent policy-blind reference machine and complete snapshot accounting checks. */
public final class WorkflowOrderOracle {
    private WorkflowOrderOracle() { }
    public static final List<String> FIELDS=List.of("epoch","captures","refunds","balance");
    public static int integer(Object x) { if(!(x instanceof Byte||x instanceof Integer||x instanceof Long))throw new IllegalArgumentException("integral billing value required");return Math.toIntExact(((Number)x).longValue()); }
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Object x) { if(!(x instanceof Map<?,?> m)||m.keySet().stream().anyMatch(k -> !(k instanceof String)))throw new IllegalArgumentException("billing object required");return (Map<String,Object>)x; }
    public static List<?> list(Object x) { if(!(x instanceof List<?> xs))throw new IllegalArgumentException("billing list required");return xs; }
    private static List<Integer> counts(Object x) { return list(x).stream().map(WorkflowOrderOracle::integer).toList(); }
    private static Map<String,Object> normal(Map<String,Object> s) { if(!s.keySet().equals(java.util.Set.copyOf(FIELDS)))throw new IllegalArgumentException("complete four-field billing snapshot required");return WorkflowOrder.fields("epoch",integer(s.get("epoch")),"captures",counts(s.get("captures")),"refunds",counts(s.get("refunds")),"balance",integer(s.get("balance"))); }
    public static Map<String,Object> initial() { return WorkflowOrder.freeze(WorkflowOrder.fields("epoch",0,"captures",List.of(0),"refunds",List.of(0),"balance",0)); }
    /** Own arrays and arithmetic; never calls Service, Ledger.value, or any SUT transition. */
    public static Map<String,Object> transition(Map<String,Object> prior,String op) {
        var s=normal(prior);if(!invariants(s).values().stream().allMatch(Boolean.TRUE::equals))throw new IllegalArgumentException("invalid reference state");int e=integer(s.get("epoch")),balance=integer(s.get("balance"));var captured=new ArrayList<>(counts(s.get("captures")));var refunded=new ArrayList<>(counts(s.get("refunds")));
        switch(op){case "C" -> { if(captured.get(e)==0){captured.set(e,1);balance=Math.addExact(balance,100);} }case "F" -> { if(captured.get(e)==1&&refunded.get(e)==0){refunded.set(e,1);balance=Math.subtractExact(balance,100);} }case "N" -> { e=Math.incrementExact(e);captured.add(0);refunded.add(0); }case "S","Q" -> { int sum=0;for(int i=0;i<captured.size();i++)sum=Math.addExact(sum,captured.get(i)-refunded.get(i));balance=Math.multiplyExact(100,sum); }default -> throw new IllegalArgumentException("unknown reference event");}
        return WorkflowOrder.freeze(WorkflowOrder.fields("epoch",e,"captures",captured,"refunds",refunded,"balance",balance));
    }
    public static Map<String,Boolean> invariants(Map<String,Object> input) {
        var s=normal(input);var c=counts(s.get("captures"));var r=counts(s.get("refunds"));int e=integer(s.get("epoch"));boolean domain=e>=0&&c.stream().allMatch(n -> n==0||n==1)&&r.stream().allMatch(n -> n==0||n==1),le=c.size()==r.size();for(int i=0;i<Math.min(c.size(),r.size());i++)le&=r.get(i)<=c.get(i);long value=100L*(c.stream().mapToLong(Integer::longValue).sum()-r.stream().mapToLong(Integer::longValue).sum());return Map.of("count_domain",domain,"refunds_le_captures",le,"list_lengths",c.size()==(long)e+1&&r.size()==(long)e+1,"ledger_balance",integer(s.get("balance"))==value);
    }
    public static Map<String,Object> check(Map<String,Object> observed,Map<String,Object> reference) {
        var expected=normal(reference);if(!invariants(expected).values().stream().allMatch(Boolean.TRUE::equals))throw new IllegalArgumentException("reference violates accounting invariant");var actual=normal(observed);var mismatched=FIELDS.stream().filter(k -> !java.util.Objects.equals(actual.get(k),expected.get(k))).toList();var inv=invariants(actual);var out=new TreeMap<String,Object>();out.put("mismatched_fields",mismatched);out.put("invariants",inv);out.put("ok",mismatched.isEmpty()&&inv.values().stream().allMatch(Boolean.TRUE::equals));return WorkflowOrder.freeze(out);
    }
}
