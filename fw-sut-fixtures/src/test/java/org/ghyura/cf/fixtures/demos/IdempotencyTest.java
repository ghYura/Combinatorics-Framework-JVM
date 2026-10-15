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
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class IdempotencyTest {
    private Element golden() throws Exception { var f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d1-golden.xml")) { assertNotNull(in); return f.newDocumentBuilder().parse(in).getDocumentElement(); } }
    private Map<String,String> fields(Element e) { var m=new TreeMap<String,String>(); var fs=e.getElementsByTagName("field"); for (int i=0;i<fs.getLength();i++) { var f=(Element)fs.item(i); m.put(f.getAttribute("name"),f.getTextContent()); } return m; }


    private Idempotency.Config config(String p,String l,String x,String c) { return new Idempotency.Config(p,l,x,c); }
    private Idempotency.Observation withPeer(Idempotency.Observation o,Idempotency.Peer p,List<Idempotency.Effect> post) { return new Idempotency.Observation(o.deliveries(),o.prePeerLedger(),o.prePeerMultiplicity(),o.firstReceipt(),p,post); }
    @Test void allOneHundredTwentyOriginalObservationsMatchEveryFrozenDeliveryLedgerSensorAndVerdict() throws Exception {
        var cases=golden().getElementsByTagName("case"); assertEquals(120,cases.getLength()); int pass=0,fail=0;
        for (int i=0;i<cases.getLength();i++) { var c=(Element)cases.item(i); var f=fields(c); var result=Idempotency.evaluate(IdempotencyAnalysis.configuration(new IdempotencyAnalysis.Observed(f))); var actual=Idempotency.attributes(result); assertEquals(f.keySet(),actual.keySet()); var fs=c.getElementsByTagName("field"); for (int k=0;k<fs.getLength();k++) { var e=(Element)fs.item(k); String key=e.getAttribute("name"); assertEquals(e.getAttribute("kind").equals("json")?Json.parse(e.getTextContent(),"golden"):e.getTextContent(),e.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key); } if (result.passed()) pass++; else fail++; }
        assertEquals(77,pass); assertEquals(43,fail);
    }
    @Test void restartCacheLifetimesStoredReceiptIdentityFullPayloadConflictsAndPolicyBlindSensorsPreserveOriginalContracts() {
        for (String policy : List.of("volatile_transport","durable_transport")) { var state=new Idempotency.DurableState(); var svc=new Idempotency.Service(policy,state); svc.handle(Idempotency.request("T0","O1","OP1",(byte)100)); svc=new Idempotency.Service(policy,state); assertEquals(1,svc.ledgerView().size()); var response=svc.handle(Idempotency.request("T0","O1","OP1",(byte)100)); assertEquals(policy.equals("volatile_transport")?"APPLIED":"REPLAY",response.status()); assertEquals(policy.equals("volatile_transport")?2:1,svc.ledgerView().size()); }
        var state=new Idempotency.DurableState(); var service=new Idempotency.Service("durable_operation",state); var first=service.handle(Idempotency.request("T0","O1","OP1",(byte)100)); var before=service.ledgerView(); var conflict=service.handle(Idempotency.request("T1","O1","OP1",(byte)101)); assertEquals("CONFLICT",conflict.status()); assertNull(conflict.receiptId()); assertEquals(before,service.ledgerView()); var replay=service.handle(Idempotency.request("T2","O1","OP1",(byte)100)); assertEquals((byte)1,replay.receiptId()); assertEquals(first.orderId(),replay.orderId()); assertEquals("REPLAY",replay.status()); assertEquals("APPLIED",service.handle(Idempotency.request("T3","O1","OP2",(byte)100)).status()); assertEquals("APPLIED",service.handle(Idempotency.request("T4","O2","OP1",(byte)100)).status());
        var payloadService=new Idempotency.Service("durable_payload",new Idempotency.DurableState()); payloadService.handle(Idempotency.request("T0","O1","OP1",(byte)100)); var changedCurrency=new Idempotency.Request("T1","O2","OP1",Map.of("amount_minor",(byte)100,"currency","ALT")); assertEquals("APPLIED",payloadService.handle(changedCurrency).status(),"complete payload key includes currency"); var extra=new Idempotency.Request("T2","O3","OP1",Map.of("amount_minor",(byte)100,"currency","TST","extra","field")); assertEquals("APPLIED",payloadService.handle(extra).status(),"complete payload key includes every field");
        var order=Idempotency.evaluate(config("durable_order","000","00","new_operation_same_order")); assertEquals("REPLAY",order.observation().peer().response().status()); assertEquals("OP1",order.observation().peer().response().operationId()); assertEquals(false,order.checks().get("receipt_identity")); assertEquals(false,order.checks().get("peer_independence"));
        var good=config("durable_operation","000","00","new_order_equal_payload"); var o=Idempotency.observe(good); var peer=o.peer(); var forged=new Idempotency.Peer(false,peer.request(),new Idempotency.Response("REPLAY",(byte)1,"O2","OP1"),(byte)0); var wrong=Idempotency.judge(good,withPeer(o,forged,o.prePeerLedger())); assertFalse(wrong.passed()); assertEquals(false,wrong.checks().get("receipt_identity")); assertEquals(false,wrong.checks().get("peer_independence"));
        var conflictCase=config("durable_operation","000","00","conflicting_retry"); var co=Idempotency.observe(conflictCase); var entry=co.postPeerLedger().getFirst(); var overwritten=new Idempotency.Effect(entry.receiptId(),entry.orderId(),entry.operationId(),Map.of("amount_minor",(byte)101,"currency","TST")); var overwrite=Idempotency.judge(conflictCase,withPeer(co,co.peer(),List.of(overwritten))); assertFalse(overwrite.passed()); assertEquals(false,overwrite.checks().get("conflict_handling"));
        var restart=Idempotency.evaluate(config("durable_operation","012","11","none")); assertTrue(restart.passed()); assertEquals(List.of((byte)0,(byte)1,(byte)2),restart.observation().deliveries().stream().map(Idempotency.Delivery::epoch).toList()); assertEquals(List.of((byte)1,(byte)1,(byte)1),restart.observation().deliveries().stream().map(d -> d.response().receiptId()).toList()); assertEquals(IdempotencyOracle.NA,restart.checks().get("retry_dedup")); assertEquals(Idempotency.evaluate(good),Idempotency.evaluate(good),"fresh cases share no state");
        int raw=0; for (String p : Idempotency.POLICIES) for (int l2=0;l2<2;l2++) for (int l3=0;l3<3;l3++) for (String x : Idempotency.CUTS) for (String c : Idempotency.CONTROLS) { var result=Idempotency.evaluate(config(p,"0"+l2+l3,x,c)); assertEquals(true,result.checks().get("sensors_agree"),result.config().caseId()); raw++; } assertEquals(600,raw,"runtime preserves original raw-domain acceptance; bonds filter the run");
        var atoms=List.of("IMPL = \"durable_operation\"","L = [0]","L.append(0)","L.append(0)","CUT1 = 0","CUT2 = 0","CONTROL = \"new_order_equal_payload\""); assertEquals(good,IdempotencyPlugin.parseAtoms(atoms));
        for (var edit : List.of(Map.entry(0,"IMPL = \"unknown\""),Map.entry(1,"L = [1]"),Map.entry(2,"L.append(2)"),Map.entry(3,"L.append(3)"),Map.entry(4,"CUT2 = 0"),Map.entry(6,"CONTROL = \"unknown\""),Map.entry(0,"IMPL = \"durable_operation\"\n"))) { var bad=new ArrayList<>(atoms); bad.set(edit.getKey(),edit.getValue()); assertThrows(IllegalArgumentException.class,() -> IdempotencyPlugin.parseAtoms(bad)); }
        assertThrows(IllegalArgumentException.class,() -> IdempotencyPlugin.parseAtoms(atoms.subList(0,6))); assertThrows(IllegalArgumentException.class,() -> config("durable_operation","003","00","none")); assertThrows(IllegalArgumentException.class,() -> new Idempotency.Service("unknown",state));
    }
    @SuppressWarnings("unchecked") @Test void allOriginalObservedTablesHistogramsAndFortyFourWitnessEntriesUseRecordedValuesWithoutRepair() throws Exception {
        var g=golden(); var cases=g.getElementsByTagName("case"); var rows=new ArrayList<IdempotencyAnalysis.Observed>(); for (int i=0;i<cases.getLength();i++) rows.add(new IdempotencyAnalysis.Observed(fields((Element)cases.item(i)))); var original=IdempotencyAnalysis.analyse(rows); assertEquals(Json.parse(g.getElementsByTagName("analysis").item(0).getTextContent(),"golden"),Json.parse(Idempotency.json(original),"actual")); assertEquals(120,IdempotencyAnalysis.expectedIds().size()); assertEquals(44,IdempotencyAnalysis.witnessGroups().values().stream().mapToInt(List::size).sum());
        var missing=new ArrayList<>(rows); missing.removeLast(); assertThrows(IllegalArgumentException.class,() -> IdempotencyAnalysis.analyse(missing)); var duplicate=new ArrayList<>(rows); duplicate.set(1,rows.getFirst()); assertThrows(IllegalArgumentException.class,() -> IdempotencyAnalysis.analyse(duplicate));
        for (var edit : List.of(Map.entry("case_id","unknown"),Map.entry("impl","unknown"),Map.entry("verdict","BROKEN"),Map.entry("labels","002"),Map.entry("cuts","01"),Map.entry("population","peer"))) { var bad=new ArrayList<>(rows); var f=new TreeMap<>(rows.getFirst().fields()); f.put(edit.getKey(),edit.getValue()); bad.set(0,new IdempotencyAnalysis.Observed(f)); assertThrows(IllegalArgumentException.class,() -> IdempotencyAnalysis.analyse(bad)); }
        String id=IdempotencyAnalysis.witnessGroups().get("defective_policy_witnesses").getFirst(); int index=java.util.stream.IntStream.range(0,rows.size()).filter(i -> rows.get(i).id().equals(id)).findFirst().orElseThrow(); var altered=new ArrayList<>(rows); var f=new TreeMap<>(rows.get(index).fields()); f.put("verdict","PASS"); var observation=(Map<String,Object>)Json.parse(f.get("observation"),"recorded"); ((Map<String,Object>)observation.get("pre_peer_multiplicity")).put("O1/OP1",7); var deliveries=(List<Map<String,Object>>)observation.get("deliveries"); ((Map<String,Object>)deliveries.getFirst().get("response")).put("status","MEASURED"); f.put("observation",Idempotency.json(observation)); var checks=(Map<String,Object>)Json.parse(f.get("checks"),"recorded"); checks.put("receipt_identity",false); f.put("checks",Idempotency.json(checks)); altered.set(index,new IdempotencyAnalysis.Observed(f)); var changed=IdempotencyAnalysis.analyse(altered); for (String key : List.of("totals","verdict_table","histograms","witnesses")) assertNotEquals(original.get(key),changed.get(key),key+" must use recorded values");
    }
}
