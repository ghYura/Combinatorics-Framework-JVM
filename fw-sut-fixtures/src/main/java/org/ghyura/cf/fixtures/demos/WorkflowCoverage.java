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

/** D3 phase C: durable billing ledger and separate cached view, including faulty recovery. */
public final class WorkflowCoverage {
    private WorkflowCoverage() { }
    public static final String EVENTS="CFNQSV";
    public static final List<String> POLICIES=List.of("correct","late_restart");
    public record Config(String policy,String order) {
        public Config { if(!POLICIES.contains(policy)||order==null||!order.chars().sorted().collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString().equals(EVENTS))throw new IllegalArgumentException("original D3C policy and exact CFNQSV permutation required"); }
        public String caseId() { return "C|"+policy+"|OPS="+order; }
    }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> value) { return (Map<String,Object>)ToolCallOrder.freezeDocument(value); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    public static final class Durable {
        private int epoch;private final List<Byte> captures=new ArrayList<>(List.of((byte)0)),refunds=new ArrayList<>(List.of((byte)0));private boolean refundAttempted;
        private int value() { return Math.multiplyExact(100,captures.stream().mapToInt(Byte::intValue).sum()-refunds.stream().mapToInt(Byte::intValue).sum()); }
    }
    /** Receives events only; knows no schedule, oracle, expected result or coverage data. */
    public static final class Service {
        private final String policy;private final Durable durable;private int balance;
        public Service(String policy,Durable durable) { this(policy,durable,durable.value()); }
        private Service(String policy,Durable durable,int balance) { if(!POLICIES.contains(policy))throw new IllegalArgumentException("unknown D3C policy");this.policy=policy;this.durable=durable;this.balance=balance; }
        public Service apply(String op) {
            var d=durable;int e=d.epoch;
            switch(op) {
                case "C" -> { if(d.captures.get(e)==0){d.captures.set(e,(byte)1);balance=Math.addExact(balance,100);} }
                case "F" -> { d.refundAttempted=true;if(d.captures.get(e)==1&&d.refunds.get(e)==0){d.refunds.set(e,(byte)1);balance=Math.subtractExact(balance,100);} }
                case "N" -> { d.epoch=Math.incrementExact(e);d.captures.add((byte)0);d.refunds.add((byte)0);d.refundAttempted=false; }
                case "S" -> { boolean faulty=policy.equals("late_restart")&&e==1&&d.captures.equals(List.of((byte)1,(byte)0))&&d.refunds.equals(List.of((byte)0,(byte)0))&&d.refundAttempted;return new Service(policy,d,faulty?0:d.value()); }
                case "Q" -> balance=d.value();
                case "V" -> { }
                default -> throw new IllegalArgumentException("unknown billing event");
            }return this;
        }
        public Map<String,Object> snapshot() { return freeze(fields("epoch",durable.epoch,"captures",durable.captures,"refunds",durable.refunds,"balance",balance,"refund_attempted",durable.refundAttempted)); }
    }
    public record Result(Config config,Map<String,Object> document) { public Result { document=freeze(document); } public boolean passed() { return document.get("verdict").equals("PASS"); } }
    public static Result evaluate(Config c) {
        var service=new Service(c.policy(),new Durable());var reference=WorkflowCoverageOracle.initial();var cps=new ArrayList<Map<String,Object>>();var ops=new ArrayList<String>();var failing=new ArrayList<Byte>();
        for(byte k=0;k<6;k++){String op=c.order().substring(k,k+1);service=service.apply(op);var observed=service.snapshot();reference=WorkflowCoverageOracle.transition(reference,op);var check=WorkflowCoverageOracle.check(observed,reference);var cp=new TreeMap<>(check);cp.putAll(fields("index",k+1,"op",op,"observed",observed,"reference",reference));cps.add(freeze(cp));ops.add(op);if(!Boolean.TRUE.equals(check.get("ok")))failing.add((byte)(k+1));}
        boolean finalOk=Boolean.TRUE.equals(cps.getLast().get("ok"));return new Result(c,fields("schema","d3c.observation/v1","contract","phase-c","case_id",c.caseId(),"policy",c.policy(),"ops",ops,"checkpoints",cps,"failing_checkpoints",failing,"verdict",failing.isEmpty()?"PASS":"DOMAIN_FAIL","final_only_verdict",finalOk?"PASS":"DOMAIN_FAIL","hidden_by_final_only",!failing.isEmpty()&&finalOk,"fw_var",failing.isEmpty()?0:2,"carrier","IMPL position 2 (legacy positional, not causal)"));
    }
    public static Map<String,String> attributes(Result r) { var out=new TreeMap<String,String>();r.document().forEach((k,v) -> out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
