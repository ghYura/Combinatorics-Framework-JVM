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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Original policy-free oracle: logical operations, requests, actual replies and ledger projections only. */
public final class IdempotencyOracle {
    private IdempotencyOracle() { }
    public static final String NA="not_applicable";
    public static final List<String> SENSORS=List.of("core_at_most_once","receipt_identity","retry_dedup","peer_independence","conflict_handling","contract_valid","sensors_agree");
    private static List<String> operation(String order,String op) { return List.of(order,op); }
    private static List<String> operation(Idempotency.Request r) { return operation(r.orderId(),r.operationId()); }
    private static List<String> operation(Idempotency.Response r) { return operation(r.orderId(),r.operationId()); }
    private static List<String> operation(Idempotency.Effect e) { return operation(e.orderId(),e.operationId()); }
    public record Reference(List<Idempotency.Response> responses,List<List<Idempotency.Effect>> ledgers) { public Reference { responses=List.copyOf(responses); ledgers=ledgers.stream().map(List::copyOf).toList(); } }
    /** Separate ledger keyed by logical operation; no service policy/cache/restart state is read. */
    public static Reference normativeTrace(List<Idempotency.Request> requests) {
        var ledger=new ArrayList<Idempotency.Effect>(); var first=new HashMap<List<String>,Idempotency.Effect>(); var replies=new ArrayList<Idempotency.Response>(); var snapshots=new ArrayList<List<Idempotency.Effect>>();
        for (var request : requests) {
            var op=operation(request); var old=first.get(op);
            if (old==null) { if (ledger.size()>=Byte.MAX_VALUE) throw new IllegalStateException("D1 byte receipt domain exhausted"); var effect=new Idempotency.Effect((byte)(ledger.size()+1),request.orderId(),request.operationId(),request.payload()); ledger.add(effect); first.put(op,effect); replies.add(new Idempotency.Response("APPLIED",effect.receiptId(),effect.orderId(),effect.operationId())); }
            else if (old.payload().equals(request.payload())) replies.add(new Idempotency.Response("REPLAY",old.receiptId(),old.orderId(),old.operationId()));
            else replies.add(new Idempotency.Response("CONFLICT",null,request.orderId(),request.operationId()));
            snapshots.add(List.copyOf(ledger));
        }
        return new Reference(replies,snapshots);
    }
    private static boolean ownedReceipt(Idempotency.Request request,Idempotency.Response response,List<Idempotency.Effect> ledger) { Byte id=response.receiptId(); return id==null||(id>=1&&id<=ledger.size()&&operation(ledger.get(id-1)).equals(operation(request))&&operation(request).equals(operation(response))); }
    public static String peerKind(List<Idempotency.Request> core,Idempotency.Request peer) { var earlier=new HashMap<List<String>,Map<String,Object>>(); for (var r : core) earlier.put(operation(r),r.payload()); var payload=earlier.get(operation(peer)); return payload==null?"new_operation":payload.equals(peer.payload())?"retry":"conflict"; }
    public static Map<String,Object> judge(Idempotency.Observation observation) {
        var core=observation.deliveries(); if (core.isEmpty()) throw new IllegalArgumentException("D1 core deliveries required"); var pre=observation.prePeerLedger(); var post=observation.postPeerLedger(); var peer=observation.peer(); var coreRequests=core.stream().map(Idempotency.Delivery::request).toList(); var requests=new ArrayList<>(coreRequests); if (peer!=null) requests.add(peer.request()); var reference=normativeTrace(requests);
        var seen=new HashSet<List<String>>(); boolean atMostOnce=true,identity=true;
        for (var delivery : core) { seen.add(operation(delivery.request())); if (delivery.effectsAfter()!=seen.size()) atMostOnce=false; identity&=ownedReceipt(delivery.request(),delivery.response(),pre); }
        var counts=new HashMap<List<String>,Integer>(); for (var effect : pre) counts.merge(operation(effect),1,Integer::sum); if (pre.size()!=seen.size()||counts.values().stream().anyMatch(n -> n!=1)) atMostOnce=false;
        if (peer!=null) identity&=ownedReceipt(peer.request(),peer.response(),post);
        var checks=new TreeMap<String,Object>(); checks.put("core_at_most_once",atMostOnce); checks.put("receipt_identity",identity); checks.put("retry_dedup",NA); checks.put("peer_independence",NA); checks.put("conflict_handling",NA);
        if (peer!=null) {
            String kind=peerKind(coreRequests,peer.request()); var reply=peer.response(); var request=peer.request(); boolean priorKept=post.size()>=pre.size()&&post.subList(0,pre.size()).equals(pre);
            switch (kind) {
                case "retry" -> checks.put("retry_dedup","REPLAY".equals(reply.status())&&Objects.equals(reply.receiptId(),core.getFirst().response().receiptId())&&operation(reply).equals(operation(request))&&peer.effectDelta()==0&&post.equals(pre));
                case "new_operation" -> { boolean added=post.size()==pre.size()+1; var effect=added?post.getLast():null; checks.put("peer_independence","APPLIED".equals(reply.status())&&operation(reply).equals(operation(request))&&reply.receiptId()!=null&&reply.receiptId()==pre.size()+1&&peer.effectDelta()==1&&priorKept&&added&&operation(effect).equals(operation(request))&&effect.payload().equals(request.payload())&&effect.receiptId()==pre.size()+1); }
                default -> checks.put("conflict_handling","CONFLICT".equals(reply.status())&&reply.receiptId()==null&&peer.effectDelta()==0&&priorKept&&post.equals(pre));
            }
        }
        var replies=new ArrayList<>(core.stream().map(Idempotency.Delivery::response).toList()); if (peer!=null) replies.add(peer.response()); boolean valid=replies.equals(reference.responses())&&pre.equals(reference.ledgers().get(core.size()-1))&&post.equals(reference.ledgers().getLast()); for (int i=0;i<core.size();i++) valid&=core.get(i).effectsAfter()==reference.ledgers().get(i).size(); if (peer!=null) valid&=peer.effectDelta()==reference.ledgers().getLast().size()-pre.size();
        boolean applicable=checks.values().stream().filter(v -> !NA.equals(v)).allMatch(Boolean.TRUE::equals); checks.put("contract_valid",valid); checks.put("sensors_agree",valid==applicable); return Collections.unmodifiableMap(checks);
    }
}
