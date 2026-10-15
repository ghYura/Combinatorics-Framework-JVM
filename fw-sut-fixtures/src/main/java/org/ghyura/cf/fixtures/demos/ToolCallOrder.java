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
import java.util.function.Consumer;

/** D13a inert local tools and four orchestrators. Each candidate owns fresh state and an in-memory outbox. */
public final class ToolCallOrder {
    private ToolCallOrder() { }
    public static final List<String> POLICIES=List.of("guarded","sticky_approval","success_only_taint","no_dedup");
    public static final List<String> ORDERS=List.of("ARS","ASR","RAS","RSA","SAR","SRA");
    public static final String CANARY="D13A_LOCAL_CANARY", MESSAGE_ID="notice-1";
    public record Config(String policy,String order,byte repetitions,boolean failRead,Byte revokeCut) {
        public Config {
            if (!POLICIES.contains(policy)||!ORDERS.contains(order)||repetitions<1||repetitions>2||revokeCut!=null&&(revokeCut<0||revokeCut>3)) throw new IllegalArgumentException("unknown or incomplete D13a configuration/plan");
        }
        public String caseId() { return "P="+policy+"|O="+order+"|N="+repetitions+"|F="+(failRead?1:0)+"|X="+(revokeCut==null?"none":revokeCut); }
    }
    public record State(boolean approved,boolean tainted,boolean sentOnce,String buffer) {
        public Map<String,Object> document() { return fields("approved",approved,"tainted",tainted,"sent_once",sentOnce,"buffer",buffer); }
    }
    public record Event(String name,Byte cut,Byte request,String messageId,String action,String reason,String payload,State state,int outboxSize) {
        public Map<String,Object> document() {
            var m=new TreeMap<String,Object>(); m.put("event",name); m.put("state",state.document()); m.put("outbox_size",outboxSize);
            if (name.equals("revoke")) m.put("cut",cut);
            if (name.equals("read_chunk")) m.put("payload",payload);
            if (request!=null) { m.put("request",request); m.put("message_id",messageId); }
            if (name.equals("send_result")) { m.put("action",action); m.put("reason",reason); m.put("payload",payload); }
            return Collections.unmodifiableMap(m);
        }
    }
    public record PublicSend(byte request,String action,String payload) { public Map<String,Object> document() { return fields("request",request,"action",action,"payload",payload); } }
    public record Send(byte request,String action,String reason,String payload) {
        public PublicSend publicResult() { return new PublicSend(request,action,payload); }
        public Map<String,Object> document() { return fields("request",request,"action",action,"reason",reason,"payload",payload); }
    }
    public record Delivery(String messageId,String payload) { public Map<String,Object> document() { return fields("message_id",messageId,"payload",payload); } }
    public record Execution(List<Event> trace,List<Send> sends,List<Delivery> outbox,State finalState) {
        public Execution { trace=List.copyOf(trace); sends=List.copyOf(sends); outbox=List.copyOf(outbox); }
    }
    public record Result(Config config,Execution observed,ToolCallOrderOracle.Reference reference,List<ToolCallOrderOracle.Violation> violations,boolean decisionsOk,boolean outboxOk) {
        public Result { violations=List.copyOf(violations); }
        public boolean passed() { return decisionsOk&&outboxOk&&violations.isEmpty(); }
        public Map<String,Object> document() {
            return fields("schema","d13a.observation/v1","contract","v1","case_id",config.caseId(),"policy",config.policy(),"order",config.order(),"send_repetitions",config.repetitions(),"fail_read",config.failRead(),"revoke_cut",config.revokeCut(),
                "trace",observed.trace().stream().map(Event::document).toList(),"send_results",observed.sends().stream().map(Send::document).toList(),"outbox",observed.outbox().stream().map(Delivery::document).toList(),"final_state",observed.finalState().document(),
                "reference_sends",reference.sends().stream().map(PublicSend::document).toList(),"reference_outbox",reference.outbox().stream().map(Delivery::document).toList(),"decisions_ok",decisionsOk,"outbox_ok",outboxOk,"violations",violations.stream().map(ToolCallOrderOracle.Violation::document).toList(),"verdict",passed()?"PASS":"DOMAIN_FAIL","fw_var",passed()?0:2,"carrier","IMPL position 2 (legacy positional, not causal)");
        }
    }
    public static final class ControlledReadError extends RuntimeException {
        private static final long serialVersionUID=1L;
        ControlledReadError() { super("read stub: controlled failure after delivering its chunk"); }
    }
    public static void readTool(Consumer<String> onChunk,boolean fail) { onChunk.accept(CANARY); if (fail) throw new ControlledReadError(); }
    private static final class Orchestrator {
        final String policy; boolean approved,tainted,sentOnce; String buffer="PUBLIC";
        final List<Event> trace=new ArrayList<>(); final List<Send> sends=new ArrayList<>(); final List<Delivery> outbox=new ArrayList<>();
        Orchestrator(String policy) { this.policy=policy; }
        State state() { return new State(approved,tainted,sentOnce,buffer); }
        void event(String name,Byte cut,Byte request,String action,String reason,String payload) { trace.add(new Event(name,cut,request,request==null?null:MESSAGE_ID,action,reason,payload,state(),outbox.size())); }
        void event(String name) { event(name,null,null,null,null,null); }
        void approve() { approved=true; event("approve"); }
        void revoke(byte cut) { if (!policy.equals("sticky_approval")) approved=false; event("revoke",cut,null,null,null,null); }
        void read(boolean fail) {
            event("read_start");
            try { readTool(chunk -> { buffer=chunk; if (!policy.equals("success_only_taint")) tainted=true; event("read_chunk",null,null,null,null,chunk); },fail); }
            catch (ControlledReadError expected) { event("read_error"); return; }
            tainted=true; event("read_ok");
        }
        void send(byte request) {
            event("send_request",null,request,null,null,null); String action,reason;
            if (!approved) { action="block"; reason="no_approval"; }
            else if (tainted) { action="block"; reason="tainted"; }
            else if (sentOnce&&!policy.equals("no_dedup")) { action="dedup"; reason="duplicate"; }
            else { action="emit"; reason="allowed"; }
            String payload=action.equals("emit")?buffer:null;
            if (action.equals("emit")) { outbox.add(new Delivery(MESSAGE_ID,payload)); sentOnce=true; }
            sends.add(new Send(request,action,reason,payload)); event("send_result",null,request,action,reason,payload);
        }
        Execution run(Config c) {
            if (c.revokeCut()!=null&&c.revokeCut()==0) revoke((byte)0);
            for (int k=0;k<3;k++) {
                switch (c.order().charAt(k)) { case 'A' -> approve(); case 'R' -> read(c.failRead()); case 'S' -> { for (byte q=1;q<=c.repetitions();q++) send(q); } default -> throw new IllegalArgumentException(); }
                if (c.revokeCut()!=null&&c.revokeCut()==k+1) revoke((byte)(k+1));
            }
            return new Execution(trace,sends,outbox,state());
        }
    }
    public static Execution run(Config c) { return new Orchestrator(c.policy()).run(c); }
    public static Result judge(Config c,Execution observed) {
        var ref=ToolCallOrderOracle.reference(c.order(),c.repetitions(),c.revokeCut()); return new Result(c,observed,ref,ToolCallOrderOracle.violations(observed.trace()),observed.sends().stream().map(Send::publicResult).toList().equals(ref.sends()),observed.outbox().equals(ref.outbox()));
    }
    public static Result evaluate(Config c) { return judge(c,run(c)); }
    static Map<String,Object> fields(Object... pairs) {
        var m=new TreeMap<String,Object>(); for (int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]); return Collections.unmodifiableMap(m);
    }
    static Object freezeDocument(Object value) {
        if (value instanceof Map<?,?> map) { var m=new TreeMap<String,Object>(); for (var e : map.entrySet()) m.put((String)e.getKey(),freezeDocument(e.getValue())); return Collections.unmodifiableMap(m); }
        if (value instanceof List<?> list) return list.stream().map(ToolCallOrder::freezeDocument).toList(); return value;
    }
    /** Existing framework JSON codec; stable field order, preserving null payloads and optional cut. */
    public static String json(Object value) { return Json.write(freezeDocument(value)).stripTrailing(); }
    public static Map<String,String> attributes(Result r) {
        var m=new TreeMap<String,String>(); for (var e : r.document().entrySet()) m.put(e.getKey(),e.getValue() instanceof String s?s:json(e.getValue())); return Map.copyOf(m);
    }
}
