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

/** Original D3 A/B billing service, including unchecked refunds and restart cache loss. */
public final class WorkflowOrder {
    private WorkflowOrder() { }
    public static final List<String> POLICIES=List.of("correct","refund_unchecked","restart_cache");
    public record Config(String phase,String policy,String core,byte restart,byte reconcile) {
        public Config { if(!List.of("A","B").contains(phase)||!POLICIES.contains(policy)||core==null||core.length()!=3||core.chars().anyMatch(c -> "CFN".indexOf(c)<0)||restart<0||restart>1||reconcile<0||reconcile>1||phase.equals("B")&&(restart!=0||reconcile!=0)||phase.equals("A")&&core.chars().distinct().count()!=3)throw new IllegalArgumentException("original D3 A/B coordinates required"); }
        public String caseId() { return phase+"|"+policy+"|OPS="+core+"|S="+restart+"|Q="+reconcile; }
        public String order() { return core+(restart==1?"S":"")+(reconcile==1?"Q":""); }
    }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> value) { return (Map<String,Object>)ToolCallOrder.freezeDocument(value); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    public static final class Ledger {
        private int epoch;private final List<Byte> captures=new ArrayList<>(List.of((byte)0));private final List<Integer> refunds=new ArrayList<>(List.of(0));
        private int value() { return Math.multiplyExact(100,Math.subtractExact(captures.stream().mapToInt(Byte::intValue).sum(),refunds.stream().mapToInt(Integer::intValue).reduce(0,Math::addExact))); }
    }
    /** Events and policy only; repeated refunds are counters, so overflow refuses rather than wrapping. */
    public static final class Service {
        private final String policy;private final Ledger ledger;private int balance;
        public Service(String policy,Ledger ledger) { this(policy,ledger,true); }
        private Service(String policy,Ledger ledger,boolean reload) { if(!POLICIES.contains(policy))throw new IllegalArgumentException("unknown D3 policy");this.policy=policy;this.ledger=ledger;balance=reload?ledger.value():0; }
        public Service apply(String op) {
            var d=ledger;int e=d.epoch;
            switch(op) {
                case "C" -> { if(d.captures.get(e)==0){d.captures.set(e,(byte)1);balance=Math.addExact(balance,100);} }
                case "F" -> { if(policy.equals("refund_unchecked")){d.refunds.set(e,Math.incrementExact(d.refunds.get(e)));balance=Math.subtractExact(balance,100);}else if(d.captures.get(e)==1&&d.refunds.get(e)==0){d.refunds.set(e,1);balance=Math.subtractExact(balance,100);} }
                case "N" -> { d.epoch=Math.incrementExact(e);d.captures.add((byte)0);d.refunds.add(0); }
                case "S" -> { return new Service(policy,d,!policy.equals("restart_cache")); }
                case "Q" -> balance=d.value();
                default -> throw new IllegalArgumentException("unknown billing event");
            }return this;
        }
        public Map<String,Object> snapshot() { return freeze(fields("epoch",ledger.epoch,"captures",ledger.captures,"refunds",ledger.refunds,"balance",balance)); }
    }
    public record Result(Config config,Map<String,Object> document) { public Result { document=freeze(document); } public boolean passed() { return document.get("verdict").equals("PASS"); } }
    public static Result evaluate(Config c) {
        var service=new Service(c.policy(),new Ledger());var reference=WorkflowOrderOracle.initial();var cps=new ArrayList<Map<String,Object>>();var ops=new ArrayList<String>();var failing=new ArrayList<Byte>();String order=c.order();
        for(byte k=0;k<order.length();k++){String op=order.substring(k,k+1);service=service.apply(op);var observed=service.snapshot();reference=WorkflowOrderOracle.transition(reference,op);var check=WorkflowOrderOracle.check(observed,reference);var cp=new TreeMap<>(check);cp.putAll(fields("index",k+1,"op",op,"observed",observed,"reference",reference));cps.add(freeze(cp));ops.add(op);if(!Boolean.TRUE.equals(check.get("ok")))failing.add((byte)(k+1));}
        boolean finalOk=Boolean.TRUE.equals(cps.getLast().get("ok"));return new Result(c,fields("schema","d3.observation/v1","contract","v1","case_id",c.caseId(),"phase",c.phase(),"policy",c.policy(),"ops",ops,"S",c.restart(),"Q",c.reconcile(),"checkpoints",cps,"failing_checkpoints",failing,"verdict",failing.isEmpty()?"PASS":"DOMAIN_FAIL","final_only_verdict",finalOk?"PASS":"DOMAIN_FAIL","hidden_by_final_only",!failing.isEmpty()&&finalOk,"fw_var",failing.isEmpty()?0:2,"carrier","IMPL position 2 (legacy positional, not causal)"));
    }
    public static Map<String,String> attributes(Result r) { var out=new TreeMap<String,String>();r.document().forEach((k,v) -> out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
