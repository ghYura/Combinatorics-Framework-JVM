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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D13e local context processors and paired, labelled noise surrogates. */
public final class ContextPosition {
    private ContextPosition() { }
    public static final List<String> POLICIES=List.of("stable","last_marker","third_position"), TASKS=List.of("public","secret"), JUDGES=List.of("calibrated","position_biased");
    public static final List<String> COVER=List.of("ABCDEG","CBAGED","DCBEGA","DGEBCA","EACGDB","EBDAGC","GABEDC","GCDAEB");
    public static final String CONTROL="ABGCDE";
    public static final byte TRIALS=20;
    public static final BigInteger DRAW_RANGE=BigInteger.ONE.shiftLeft(64);
    public static List<String> orders() { var out=new ArrayList<>(COVER);out.add(CONTROL);return List.copyOf(out); }
    public record Config(String policy,String task,String order) {
        public Config { if(!POLICIES.contains(policy)||!TASKS.contains(task)||order==null||!order.chars().sorted().mapToObj(n -> String.valueOf((char)n)).collect(java.util.stream.Collectors.joining()).equals("ABCDEG"))throw new IllegalArgumentException("invalid D13e coordinate"); }
        public String caseId() { return "P="+policy+"|T="+task+"|O="+order; }
        public byte guardPosition() { return (byte)(order.indexOf('G')+1); }
        public String band() { return guardPosition()<=3?"early":"late"; }
    }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> value) { return (Map<String,Object>)ToolCallOrder.freezeDocument(value); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    public record Fold(String decision,List<Map<String,Object>> trace) { public Fold { trace=trace.stream().map(ContextPosition::freeze).toList(); } }
    /** Knows guard content, but not the mechanical oracle, noise channels or verdict. */
    public static final class Processor {
        private final String policy;
        private String decision;
        private final List<Map<String,Object>> trace=new ArrayList<>();
        public Processor(String policy) { if(!POLICIES.contains(policy))throw new IllegalArgumentException("unknown processor policy");this.policy=policy; }
        public void reset() { decision=null;trace.clear(); }
        public Fold run(String task,String order) { var c=new Config(policy,task,order);if(decision!=null||!trace.isEmpty())throw new IllegalStateException("processor not reset before trial");for(byte position=1;position<=6;position++){String token=order.substring(position-1,position),before=decision;if(token.equals("G"))decision=c.task().equals("public")||policy.equals("third_position")&&position==3?"ALLOW":"DENY";else if(token.equals("A")&&policy.equals("last_marker"))decision="ALLOW";trace.add(freeze(fields("position",position,"token",token,"origin",token.equals("G")?"trusted":"untrusted","before",before,"after",decision)));}return new Fold(decision,trace); }
    }
    public static String draw(Config c,byte trial) { if(trial<0||trial>=TRIALS)throw new IllegalArgumentException("trial outside0..19");try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(("d13e|"+c.policy()+"|"+c.task()+"|"+c.order()+"|"+trial).getBytes(StandardCharsets.US_ASCII));return HexFormat.of().formatHex(hash,0,8);}catch(NoSuchAlgorithmException e){throw new IllegalStateException("JDK SHA-256 unavailable",e);} }
    /** Exact unsigned64-bit inequalities; channels intentionally share a draw. */
    public static Map<String,Boolean> flips(BigInteger n,byte guard) { if(n==null||n.signum()<0||n.compareTo(DRAW_RANGE)>=0||guard<1||guard>6)throw new IllegalArgumentException("invalid draw/guard position");boolean calibrated=n.multiply(BigInteger.TEN).compareTo(DRAW_RANGE)<0;boolean biased=guard<=3?n.multiply(BigInteger.valueOf(5)).compareTo(DRAW_RANGE.multiply(BigInteger.valueOf(3)))<0:calibrated;return Map.of("calibrated",calibrated,"position_biased",biased); }
    public static Map<String,Boolean> approvals(boolean mechanical,Map<String,Boolean> flips) { return Map.of("calibrated",mechanical!=flips.get("calibrated"),"position_biased",mechanical!=flips.get("position_biased")); }
    public record Result(Config config,List<Map<String,Object>> trials,List<Byte> failing) {
        public Result { trials=trials.stream().map(ContextPosition::freeze).toList();failing=List.copyOf(failing); }
        public boolean passed() { return failing.isEmpty(); }
        public Map<String,Object> document() { return fields("schema","d13e.observation/v1","contract","v1","case_id",config.caseId(),"policy",config.policy(),"task",config.task(),"order",config.order(),"guard_position",config.guardPosition(),"band",config.band(),"expected_decision",ContextPositionOracle.truth(config.task()),"trials",trials,"failing_trials",failing,"verdict",passed()?"PASS":"DOMAIN_FAIL","fw_var",passed()?0:2,"carrier_slot","IMPL position 2 (legacy positional, not causal)"); }
    }
    public static Result evaluate(Config c) { var proc=new Processor(c.policy());var trials=new ArrayList<Map<String,Object>>();for(byte t=0;t<TRIALS;t++){proc.reset();var fold=proc.run(c.task(),c.order());ContextPositionOracle.checkTrace(c.order(),fold.trace(),fold.decision());boolean ok=ContextPositionOracle.mechanicalOk(c.task(),fold.decision());String hx=draw(c,t);trials.add(freeze(fields("trial",t,"draw_hex",hx,"decision",fold.decision(),"context_trace",fold.trace(),"mechanical_ok",ok,"judge_approvals",approvals(ok,flips(new BigInteger(hx,16),c.guardPosition())))));}return new Result(c,trials,ContextPositionOracle.failingTrials(c.task(),c.order(),trials)); }
    public static Map<String,String> attributes(Result r) { var out=new TreeMap<String,String>();r.document().forEach((k,v) -> out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
