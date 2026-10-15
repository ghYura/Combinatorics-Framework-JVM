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

package org.ghyura.cf.fixtures.finance;

import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class FinanceEcosystemTest {
    @Test void allThirteenPhasesRetainIndependentTransferRiskAndFinalMoneyCertificates() throws Exception {
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try(var in=getClass().getResourceAsStream("ecosystem-golden.xml")) {
            assertNotNull(in);var document=factory.newDocumentBuilder().parse(in).getDocumentElement();
            var expected=Json.parse(document.getElementsByTagName("predictions").item(0).getTextContent(),"independent ecosystem");
            assertEquals(expected,Json.parse(Json.write(FinanceSimulation.run()),"actual evolving world"));
        }
    }
    private static final class World {
        final double[] now={0};final Map<String,AtomicInteger> counters=new HashMap<>();
        final AtomicInteger refs=new AtomicInteger(),tokens=new AtomicInteger();
        final FinanceEcosystem eco=new FinanceEcosystem(new FinanceTime(),Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),()->now[0],
            prefix->prefix+"_"+String.format(Locale.ROOT,"%016x",counters.computeIfAbsent(prefix,k->new AtomicInteger()).incrementAndGet()),
            ()->1,()->String.format(Locale.ROOT,"TRF-%08X",refs.incrementAndGet()),()->"token-"+tokens.incrementAndGet());
        final String sender,receiver,source,target;
        World() {
            eco.addBank("AURUM","Aurum Bank","US",false,null);eco.addBank("NORD","Nord Credit","EU",false,null);
            sender=eco.identity().register("A","a@x.test",FinanceIdentity.Kyc.VERIFIED,Set.of("CUSTOMER"),"RETAIL",false).id();
            receiver=eco.identity().register("B","b@x.test",FinanceIdentity.Kyc.VERIFIED,Set.of("CUSTOMER"),"RETAIL",false).id();
            source=eco.open("AURUM",sender,"USD","5000").id();target=eco.open("NORD",receiver,"USD","0").id();
        }
        FinanceClearing.Transfer initiate(String amount,String key) { return eco.initiate(source,target,amount,key); }
        void both(FinanceClearing.Transfer t) { for(var p:FinanceClearing.Party.values())eco.confirm(t.reference(),p,t.tokens().get(p),"CONFIRM"); }
    }
    @Test void accountAndCurrencyPrevalidationPrecedeIdempotencyAndCachedReplacementDoesNotFallback() {
        var w=new World();var first=w.initiate("100","key");
        assertSame(first,w.initiate("999","key"));assertEquals("100.00",first.context().amount().asString());
        assertEquals("NOT_FOUND",assertThrows(ServiceError.class,()->w.eco.initiate(w.source,"missing","999","key")).code());
        var foreign=w.eco.open("NORD",w.receiver,"EUR","0");
        assertEquals("VALIDATION_ERROR",assertThrows(ServiceError.class,()->w.eco.initiate(w.source,foreign.id(),"999","key")).code());
        assertEquals("VALIDATION_ERROR",assertThrows(ServiceError.class,()->w.initiate("bad","key")).code());
        assertEquals(1,w.eco.bank("AURUM").clearing().transfers().size());
        var directlyOpened=w.eco.bank("NORD").open(w.receiver,"USD","7");
        assertEquals("7.00",w.eco.balance(directlyOpened.id()).get("balance"));
        int count=w.eco.bank("NORD").ledger().accounts(null).size();
        assertEquals("VALIDATION_ERROR",assertThrows(ServiceError.class,()->w.eco.open("NORD",w.receiver,"USD","bad")).code());
        assertEquals(count+1,w.eco.bank("NORD").ledger().accounts(null).size());
        var old=w.eco.bank("AURUM");w.eco.addBank("AURUM","Replacement","US",false,null);
        assertEquals(2,w.eco.banks().size());assertEquals("5000.00",old.ledger().balance(w.source).get("balance"));
        assertEquals("NOT_FOUND",assertThrows(ServiceError.class,()->w.eco.balance(w.source)).code());
        assertEquals("NOT_FOUND",assertThrows(ServiceError.class,()->w.eco.transfer(first.reference())).code());
    }
    @Test void liveSharedTimeChangesAffectNewDeadlinesAndExistingRiskWindows() {
        var w=new World();var old=w.initiate("1","old");
        var changed=new LinkedHashMap<String,Object>();changed.put("confirmation_ttl_seconds",10);changed.put("risk_window_seconds",1);
        w.eco.updateTime(changed,null,"self",false);var next=w.initiate("1","next");
        assertEquals(List.of(next),w.eco.bank("AURUM").clearing().expireDue(10));
        assertEquals(FinanceClearing.Status.AWAITING_CONFIRMATION,old.status());
        w.now[0]=1;assertEquals(2,w.eco.bank("AURUM").risk().restrictions(w.sender).get("window_count"));
        w.now[0]=1.0001;assertEquals(0,w.eco.bank("AURUM").risk().restrictions(w.sender).get("window_count"));
        assertEquals(List.of(old),w.eco.bank("AURUM").clearing().expireDue(3600));
        assertEquals("0.00",w.eco.balance(w.source).get("hold"));
        w.eco.updateTime(Map.of(),false,"self",false);
        assertEquals(Map.of("applied",false,"reason","sync_declined","accept_time_sync",false),w.eco.updateTime(Map.of("risk_window_seconds",99),true,"connected",true));
        assertFalse(w.eco.time().acceptSync());assertEquals(1,w.eco.time().seconds("risk_window_seconds"));
        w.eco.updateTime(Map.of("risk_window_seconds",2),true,"self",false);
        assertTrue(w.eco.time().acceptSync());assertEquals(2,w.eco.time().seconds("risk_window_seconds"));
    }
    @Test void timeHooksRunBeforeEventsAndPropagationWhileInvalidUpdatesAndFailingHooksKeepPartialChanges() {
        var w=new World();var order=new ArrayList<String>();boolean[] added={false};
        w.eco.onTimeChange(()->{order.add("hook");if(!added[0]){added[0]=true;w.eco.onTimeChange(()->order.add("added-hook"));}});
        w.eco.bus().subscribe("time_config.updated",e->order.add("event"));
        w.eco.propagator(values->{order.add("propagate");return fields("NORD","applied");});
        var result=w.eco.updateTime(Map.of("risk_window_seconds",2),null,"connected",false);
        assertEquals(List.of("hook","added-hook","event","propagate"),order);assertEquals(Map.of("NORD","applied"),result.get("propagated"));
        order.clear();var partial=new LinkedHashMap<String,Object>();partial.put("risk_window_seconds",3);partial.put("unknown",7);
        assertThrows(ServiceError.class,()->w.eco.updateTime(partial,false,"connected",false));
        assertEquals(3,w.eco.time().seconds("risk_window_seconds"));assertTrue(w.eco.time().acceptSync());assertTrue(order.isEmpty());
        w.eco.onTimeChange(()->{throw new IllegalStateException("hook failure");});
        assertThrows(IllegalStateException.class,()->w.eco.updateTime(Map.of("risk_window_seconds",4),false,"connected",false));
        assertEquals(4,w.eco.time().seconds("risk_window_seconds"));assertFalse(w.eco.time().acceptSync());assertEquals(List.of("hook","added-hook"),order);
    }
    @Test void localRemotePortsRetainHoldsRetryAndSettlementHooksAndNullActorReviewBypass() {
        var w=new World();var remoteLedger=new FinanceLedger("PEER",w.eco.bus(),prefix->prefix+"_00000000000000ff",()->2);
        var beneficiary=remoteLedger.open("peer-customer","USD","0");var port=new FinanceSettlement.LedgerPort(remoteLedger);boolean[] online={false};
        w.eco.addRemoteBank("PEER","Peer Bank",new FinanceSettlement.BankPort() {
            public void hold(String a,Money m,String r){port.hold(a,m,r);}
            public void release(String a,Money m,String r){port.release(a,m,r);}
            public void outbound(String a,Money m,String r){port.outbound(a,m,r);}
            public void inbound(String a,Money m,String r){if(!online[0])throw new FinanceClearing.PeerUnavailable("offline",true,"PEER");port.inbound(a,m,r);}
        },"XX",false);
        assertTrue(w.eco.remote("PEER"));assertEquals(2,w.eco.banks().size());
        assertEquals("NOT_FOUND",assertThrows(ServiceError.class,()->w.eco.balance(beneficiary.id())).code());
        var t=w.eco.initiateRemote(w.source,"PEER",beneficiary.id(),"10","remote");w.both(t);
        assertEquals(FinanceClearing.Status.ON_HOLD,t.status());assertEquals("10.00",w.eco.balance(w.source).get("hold"));
        assertTrue(w.eco.retryOnHold().isEmpty());online[0]=true;assertEquals(List.of(t),w.eco.retryOnHold());
        assertEquals("4990.00",w.eco.balance(w.source).get("balance"));assertEquals("10.00",beneficiary.balance().asString());
        assertEquals(1,w.eco.audit().ofType("risk.settlement_recorded").size());w.eco.assertBalanced();remoteLedger.assertBalanced();
        var review=w.initiate("2500","review");w.both(review);assertEquals(FinanceClearing.Status.AWAITING_REVIEW,review.status());
        assertThrows(ServiceError.class,()->w.eco.review(review.reference(),true,w.sender));
        assertEquals(FinanceClearing.Status.SETTLED,w.eco.review(review.reference(),true,null).status());
        var negative=w.initiate("-10","negative");w.both(negative);
        assertEquals(FinanceClearing.Status.SETTLED,negative.status());assertEquals("2500.00",w.eco.balance(w.source).get("balance"));
        assertEquals("2490.00",w.eco.balance(w.target).get("balance"));
    }
}
