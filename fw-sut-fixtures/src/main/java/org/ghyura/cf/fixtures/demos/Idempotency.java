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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Original D1 local order service. Restart replaces only its process object; cases share no state. */
public final class Idempotency {
    private Idempotency() { }
    public static final List<String> POLICIES=List.of("volatile_transport","durable_transport","durable_order","durable_payload","durable_operation");
    public static final List<String> CONTROLS=List.of("none","retry_fresh_transport","new_order_equal_payload","new_operation_same_order","conflicting_retry");
    public static final List<String> PARTITIONS=List.of("000","001","010","011","012");
    public static final List<String> CUTS=List.of("00","01","10","11");
    /** Runtime accepts the original raw domain; population bonds belong to the existing rule engine. */
    public record Config(String policy,String labels,String cuts,String control) {
        public Config { if (policy==null||!POLICIES.contains(policy)||labels==null||!labels.matches("0[01][012]")||cuts==null||!CUTS.contains(cuts)||control==null||!CONTROLS.contains(control)) throw new IllegalArgumentException("unknown/incomplete D1 configuration"); }
        public String caseId() { return policy+"|L="+labels+"|C="+cuts+"|P="+control; }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> payload(Map<String,Object> p) { return (Map<String,Object>)ToolCallOrder.freezeDocument(p); }
    public record Request(String transportId,String orderId,String operationId,Map<String,Object> payload) {
        public Request { payload=Idempotency.payload(payload); }
        public Map<String,Object> document() { return fields("transport_id",transportId,"order_id",orderId,"operation_id",operationId,"payload",payload); }
    }
    public record Response(String status,Byte receiptId,String orderId,String operationId) { public Map<String,Object> document() { return fields("status",status,"receipt_id",receiptId,"order_id",orderId,"operation_id",operationId); } }
    public record Effect(byte receiptId,String orderId,String operationId,Map<String,Object> payload) {
        public Effect { payload=Idempotency.payload(payload); }
        public Map<String,Object> document() { return fields("receipt_id",receiptId,"order_id",orderId,"operation_id",operationId,"payload",payload); }
    }
    public record Delivery(byte index,byte label,boolean restartBefore,byte epoch,Request request,Response response,byte effectsAfter) { public Map<String,Object> document() { return fields("index",index,"label",label,"restart_before",restartBefore,"epoch",epoch,"request",request.document(),"response",response.document(),"effects_after",effectsAfter); } }
    public record Peer(boolean restartBefore,Request request,Response response,byte effectDelta) { public Map<String,Object> document() { return fields("restart_before",restartBefore,"request",request.document(),"response",response.document(),"effect_delta",effectDelta); } }
    public record Observation(List<Delivery> deliveries,List<Effect> prePeerLedger,Map<String,Byte> prePeerMultiplicity,Byte firstReceipt,Peer peer,List<Effect> postPeerLedger) {
        public Observation { deliveries=List.copyOf(deliveries); prePeerLedger=List.copyOf(prePeerLedger); prePeerMultiplicity=Map.copyOf(prePeerMultiplicity); postPeerLedger=List.copyOf(postPeerLedger); }
        public Map<String,Object> document() { return fields("deliveries",deliveries.stream().map(Delivery::document).toList(),"pre_peer_ledger",prePeerLedger.stream().map(Effect::document).toList(),"pre_peer_multiplicity",prePeerMultiplicity,"first_receipt",firstReceipt,"peer",peer==null?null:peer.document(),"post_peer_ledger",postPeerLedger.stream().map(Effect::document).toList()); }
    }
    /** Per-case backing state is local JVM memory, retained across that case's simulated restarts. */
    public static final class DurableState {
        private final List<Effect> ledger=new ArrayList<>(); private final Map<Object,Effect> cache=new HashMap<>();
        public List<Effect> ledgerView() { return List.copyOf(ledger); }
    }
    public static final class Service {
        private final String policy; private final DurableState durable; private final Map<Object,Effect> cache;
        public Service(String policy,DurableState durable) { if (policy==null||!POLICIES.contains(policy)) throw new IllegalArgumentException("unknown D1 cache policy"); this.policy=policy; this.durable=java.util.Objects.requireNonNull(durable); cache=policy.equals("volatile_transport")?new HashMap<>():durable.cache; }
        private Object key(Request r) { return switch (policy) { case "volatile_transport","durable_transport" -> List.of("transport",r.transportId()); case "durable_order" -> List.of("order",r.orderId()); case "durable_payload" -> List.of("payload",r.payload()); default -> List.of("operation",r.orderId(),r.operationId()); }; }
        public Response handle(Request r) {
            Object key=key(r); var entry=cache.get(key);
            if (entry==null) { if (durable.ledger.size()>=Byte.MAX_VALUE) throw new IllegalStateException("D1 byte receipt domain exhausted"); var effect=new Effect((byte)(durable.ledger.size()+1),r.orderId(),r.operationId(),r.payload()); durable.ledger.add(effect); cache.put(key,effect); return new Response("APPLIED",effect.receiptId(),r.orderId(),r.operationId()); }
            if (!entry.payload().equals(r.payload())) return new Response("CONFLICT",null,r.orderId(),r.operationId());
            return new Response("REPLAY",entry.receiptId(),entry.orderId(),entry.operationId());
        }
        public List<Effect> ledgerView() { return durable.ledgerView(); }
    }
    public record Result(Config config,Observation observation,Map<String,Object> checks) {
        public Result { checks=Collections.unmodifiableMap(new TreeMap<>(checks)); }
        public boolean passed() { return Boolean.TRUE.equals(checks.get("contract_valid")); }
        public Map<String,Object> document() { return fields("schema","d1.observation/v1","contract","v1","case_id",config.caseId(),"impl",config.policy(),"labels",config.labels(),"cuts",config.cuts(),"control",config.control(),"population",config.control().equals("none")?"core":"peer","observation",observation.document(),"checks",checks,"verdict",passed()?"PASS":"DOMAIN_FAIL","fw_var",passed()?0:2,"carrier","IMPL position 2 (legacy positional, not causal)"); }
    }
    public static Request request(String transport,String order,String operation,byte amount) { return new Request(transport,order,operation,Map.of("amount_minor",amount,"currency","TST")); }
    public static Observation observe(Config c) {
        var durable=new DurableState(); var service=new Service(c.policy(),durable); var deliveries=new ArrayList<Delivery>(); byte epoch=0;
        for (byte i=0;i<3;i++) { byte label=(byte)(c.labels().charAt(i)-'0'); boolean restart=i>0&&c.cuts().charAt(i-1)=='1'; if (restart) { service=new Service(c.policy(),durable); epoch++; } var request=request("T"+label,"O1","OP1",(byte)100); var response=service.handle(request); deliveries.add(new Delivery((byte)(i+1),label,restart,epoch,request,response,(byte)durable.ledger.size())); }
        var pre=service.ledgerView(); Peer peer=null;
        if (!c.control().equals("none")) { var req=switch (c.control()) { case "retry_fresh_transport" -> request("T3","O1","OP1",(byte)100); case "new_order_equal_payload" -> request("T3","O2","OP1",(byte)100); case "new_operation_same_order" -> request("T3","O1","OP2",(byte)100); default -> request("T3","O1","OP1",(byte)101); }; byte before=(byte)durable.ledger.size(); var response=service.handle(req); peer=new Peer(false,req,response,(byte)(durable.ledger.size()-before)); }
        var multiplicity=new TreeMap<String,Byte>(); for (var effect : pre) multiplicity.merge(effect.orderId()+"/"+effect.operationId(),(byte)1,(a,b) -> (byte)(a+b));
        return new Observation(deliveries,pre,multiplicity,deliveries.getFirst().response().receiptId(),peer,service.ledgerView());
    }
    public static Result judge(Config c,Observation o) { return new Result(c,o,IdempotencyOracle.judge(o)); }
    public static Result evaluate(Config c) { return judge(c,observe(c)); }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>(); for (int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]); return Collections.unmodifiableMap(m); }
    public static String json(Object value) { return org.ghyura.cf.model.Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    public static Map<String,String> attributes(Result r) { var m=new TreeMap<String,String>(); for (var e : r.document().entrySet()) m.put(e.getKey(),e.getValue() instanceof String s?s:json(e.getValue())); return Map.copyOf(m); }
}
