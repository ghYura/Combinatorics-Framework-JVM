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
import java.util.function.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class FinanceClearingTest {
    @Test void all192IndependentScriptsKeepCompleteClearingStatesErrorsEventsAndBalances() throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try (var in = getClass().getResourceAsStream("clearing-golden.xml")) {
            assertNotNull(in);
            var rows = factory.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("case");
            assertEquals(192,rows.getLength());
            for (int i=0;i<rows.getLength();i++) {
                var row = (Map<?,?>)Json.parse(rows.item(i).getTextContent(),"independent clearing");
                assertEquals(row.get("expected"),Json.parse(Json.write(FinanceClearingScenario.run((Map<?,?>)row.get("request"))),"actual clearing"),"script "+i);
            }
        }
    }
    private static final class World {
        final Bus bus = new Bus(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        final Audit audit = new Audit(bus);
        final AtomicInteger identifiers = new AtomicInteger(), references = new AtomicInteger(), tokens = new AtomicInteger();
        final Function<String,String> ids = prefix -> prefix+"_"+String.format(Locale.ROOT,"%016x",identifiers.incrementAndGet());
        final FinanceIdentity identity = new FinanceIdentity(bus,ids);
        final FinanceLedger ledger = new FinanceLedger("AURUM",bus,ids,()->1);
        final FinanceLedger other = new FinanceLedger("NORD",bus,ids,()->2);
        final FinanceNetwork network = new FinanceNetwork(identity,bus);
        final FinanceTime time = new FinanceTime();
        final double[] now = {0};
        final FinanceIdentity.Client sender = identity.register("A","a@x.test",FinanceIdentity.Kyc.VERIFIED,Set.of("CUSTOMER"),"retail",false);
        final FinanceIdentity.Client receiver = identity.register("B","b@x.test",FinanceIdentity.Kyc.VERIFIED,Set.of("CUSTOMER"),"retail",false);
        final FinanceLedger.Account source = ledger.open(sender.id(),"USD","5000");
        final FinanceLedger.Account target = ledger.open(receiver.id(),"USD","0");
        final FinanceLedger.Account foreign = other.open(receiver.id(),"USD","0");
        World() {
            time.update(Map.of("confirmation_ttl_seconds",10));
            network.register(new FinanceNetwork.BankInfo("AURUM","Aurum","US",false),new FinanceSettlement.LedgerPort(ledger));
            network.register(new FinanceNetwork.BankInfo("NORD","Nord","EU",false),new FinanceSettlement.LedgerPort(other));
        }
        FinanceClearing coordinator(Function<FinanceRisk.Context,FinanceSettlement.Executor> router,List<Consumer<FinanceClearing.Transfer>> hooks) {
            return new FinanceClearing(List.of(),router,bus,"clearing:AURUM",identity,time,hooks,()->now[0],
                    ()->String.format(Locale.ROOT,"TRF-%08X",references.incrementAndGet()),()->"token-"+tokens.incrementAndGet());
        }
        FinanceClearing.Request request(String key, boolean cross) {
            return new FinanceClearing.Request(key,Money.of("100","USD"),sender.id(),source.id(),"AURUM",receiver.id(),cross?foreign.id():target.id(),cross?"NORD":"AURUM");
        }
        void both(FinanceClearing clearing,FinanceClearing.Transfer transfer) {
            for (var party : FinanceClearing.Party.values()) clearing.confirm(transfer.reference(),party,transfer.tokens().get(party));
        }
    }
    @Test void retryablePeerRetainsHoldThenSettlesWhileDownPeersAndExpiryRelease() {
        var w = new World(); var delegate = new FinanceSettlement.Internal(w.ledger);
        boolean[] available = {false}, retryable = {true};
        var clearing = w.coordinator(context -> new FinanceSettlement.Executor() {
            public void hold(FinanceRisk.Context c) { delegate.hold(c); }
            public Map<String,Object> settle(FinanceRisk.Context c) {
                if (!available[0]) throw new FinanceClearing.PeerUnavailable("offline",retryable[0],"NORD");
                return delegate.settle(c);
            }
            public void release(FinanceRisk.Context c) { delegate.release(c); }
        },List.of());
        var first=clearing.initiate(w.request("first",false));w.both(clearing,first);
        assertEquals(FinanceClearing.Status.ON_HOLD,first.status());assertEquals("100.00",w.source.hold().asString());
        assertTrue(clearing.retryOnHold().isEmpty());available[0]=true;
        assertEquals(List.of(first),clearing.retryOnHold());assertEquals(FinanceClearing.Status.SETTLED,first.status());
        assertEquals("4900.00",w.source.balance().asString());assertEquals("0.00",w.source.hold().asString());assertTrue(clearing.retryOnHold().isEmpty());
        available[0]=false;retryable[0]=false;
        var down=clearing.initiate(w.request("down",false));w.both(clearing,down);
        assertEquals(FinanceClearing.Status.FAILED,down.status());assertEquals("PEER_UNAVAILABLE",down.failure());
        assertEquals("0.00",w.source.hold().asString());
        retryable[0]=true;
        var expires=clearing.initiate(w.request("expires",false));w.both(clearing,expires);
        assertEquals(List.of(expires),clearing.expireDue(10));assertEquals(FinanceClearing.Status.EXPIRED,expires.status());
        assertEquals("0.00",w.source.hold().asString());assertTrue(clearing.retryOnHold().isEmpty());
    }
    @Test void failedSourceLegAndSettledHookKeepCommittedMoneyAndSourceFailureLifecycle() {
        var w = new World();var base = new FinanceSettlement.LedgerPort(w.ledger);
        w.network.register(new FinanceNetwork.BankInfo("AURUM","Aurum","US",false),new FinanceSettlement.BankPort() {
            public void hold(String a,Money m,String r) { base.hold(a,m,r); }
            public void release(String a,Money m,String r) { base.release(a,m,r); }
            public void outbound(String a,Money m,String r) { throw new ServiceError("SERVICE_ERROR",400,"injected source-leg failure",Map.of()); }
            public void inbound(String a,Money m,String r) { base.inbound(a,m,r); }
        });
        var clearing=w.coordinator(w.network::executor,List.of());var transfer=clearing.initiate(w.request("cross",true));w.both(clearing,transfer);
        assertEquals(FinanceClearing.Status.FAILED,transfer.status());assertEquals("SERVICE_ERROR",transfer.failure());
        assertEquals("5000.00",w.source.balance().asString());assertEquals("100.00",w.foreign.balance().asString());
        assertEquals("0.00",w.source.hold().asString());assertTrue(w.network.settlements().isEmpty());assertTrue(w.network.positions().isEmpty());
        w.ledger.assertBalanced();w.other.assertBalanced();w.network.assertBalanced();
        var hookWorld=new World();var withHook=hookWorld.coordinator(context->new FinanceSettlement.Internal(hookWorld.ledger),
                List.of(t->{throw new ServiceError("SERVICE_ERROR",400,"hook failed",Map.of());}));
        var committed=withHook.initiate(hookWorld.request("hook",false));hookWorld.both(withHook,committed);
        assertEquals(FinanceClearing.Status.FAILED,committed.status());
        assertEquals(Map.of("mode","internal","bank","AURUM"),committed.view().get("settlement"));
        assertEquals("4900.00",hookWorld.source.balance().asString());assertEquals("100.00",hookWorld.target.balance().asString());
        assertEquals(1,hookWorld.audit.ofType("transfer.settled").size());assertEquals(1,hookWorld.audit.ofType("transfer.failed").size());
        assertTrue(withHook.retryOnHold().isEmpty());
    }
    @Test void routerFailuresStayInitiatedAndHoldErrorsKeepPartialSideEffectsWhileSafeReleaseSwallowsErrors() {
        var w = new World();var routerError=new ServiceError("UPSTREAM_ERROR",502,"missing bank",Map.of());
        var clearing=w.coordinator(context->{throw routerError;},List.of());
        assertSame(routerError,assertThrows(ServiceError.class,()->clearing.initiate(w.request("router",false))));
        var saved=clearing.transfers().getFirst();assertEquals(FinanceClearing.Status.INITIATED,saved.status());
        assertSame(saved,clearing.initiate(w.request("router",true)));assertEquals(List.of(saved),clearing.expireDue(10));
        var delegate=new FinanceSettlement.Internal(w.ledger);
        var holdError=w.coordinator(context->new FinanceSettlement.Executor() {
            public void hold(FinanceRisk.Context c) { delegate.hold(c);throw new ServiceError("SERVICE_ERROR",400,"hold callback failed",Map.of()); }
            public Map<String,Object> settle(FinanceRisk.Context c) { return delegate.settle(c); }
            public void release(FinanceRisk.Context c) { throw new IllegalStateException("release callback failed"); }
        },List.of());
        var rejected=holdError.initiate(w.request("hold",false));assertEquals(FinanceClearing.Status.REJECTED,rejected.status());
        assertEquals("100.00",w.source.hold().asString());assertEquals("SERVICE_ERROR",rejected.failure());
        assertTrue(holdError.expireDue(100).isEmpty());
        var releaseError=w.coordinator(context->new FinanceSettlement.Executor() {
            public void hold(FinanceRisk.Context c) { delegate.hold(c); }
            public Map<String,Object> settle(FinanceRisk.Context c) { return delegate.settle(c); }
            public void release(FinanceRisk.Context c) { throw new IllegalStateException("release failed"); }
        },List.of());
        var waiting=releaseError.initiate(w.request("release",false));
        assertEquals(FinanceClearing.Status.REJECTED,releaseError.confirm(waiting.reference(),FinanceClearing.Party.SENDER,waiting.tokens().get(FinanceClearing.Party.SENDER),"REJECT").status());
        assertEquals("200.00",w.source.hold().asString());
        assertThrows(IllegalArgumentException.class,()->releaseError.expireDue(Double.NaN));
    }
}
