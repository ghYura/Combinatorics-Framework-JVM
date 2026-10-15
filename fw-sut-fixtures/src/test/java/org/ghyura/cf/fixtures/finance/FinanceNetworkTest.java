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
class FinanceNetworkTest {
 @Test void all268IndependentSanctionsProfilesKeepOrderedHitsAndBankListFlagEquivalence()throws Exception{var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("network-golden.xml")){assertNotNull(in);var rows=f.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("case");assertEquals(268,rows.getLength());for(int i=0;i<rows.getLength();i++){var row=(Map<?,?>)Json.parse(rows.item(i).getTextContent(),"independent sanctions");assertEquals(row.get("expected"),Json.parse(Json.write(FinanceSanctionsScenario.run((Map<?,?>)row.get("request"))),"actual sanctions"),"profile "+i);}}}
 @Test void registryRoutingPositionsAndBeneficiaryFirstFailurePreserveSourceBehavior(){var bus=new Bus(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));var audit=new Audit(bus);var identity=new FinanceIdentity(bus);var unicode=identity.register("Unicode","u@example.test",FinanceIdentity.Kyc.VERIFIED,Set.of("x\ue000","x\ud800\udc00"),"RETAIL",false);assertEquals(List.of("x\ue000","x\ud800\udc00"),unicode.view().get("roles"));var network=new FinanceNetwork(identity,bus);var ids=new AtomicInteger();var source=new FinanceLedger("AURUM",bus,p->p+"_"+String.format(Locale.ROOT,"%016x",ids.incrementAndGet()),()->0);var target=new FinanceLedger("NORD",bus,p->p+"_"+String.format(Locale.ROOT,"%016x",ids.incrementAndGet()),()->0);var a=source.open("sender","USD","500");var b=target.open("receiver","USD","0");network.register(new FinanceNetwork.BankInfo("AURUM","Aurum Bank","US",false),new FinanceSettlement.LedgerPort(source));network.register(new FinanceNetwork.BankInfo("NORD","Nord Credit","EU",false),new FinanceSettlement.LedgerPort(target));assertTrue(network.member("AURUM"));assertFalse(network.member("unknown"));assertNull(network.tryBank("unknown"));assertEquals(404,assertThrows(ServiceError.class,()->network.bank("unknown")).status());assertEquals(List.of("AURUM","NORD"),network.banks().stream().map(FinanceNetwork.BankInfo::code).toList());var context=new FinanceRisk.Context("TRF-00000001",Money.of("100","USD"),"sender","receiver",a.id(),b.id(),"AURUM","NORD");assertEquals(FinanceRisk.Decision.ALLOW,network.screen(context).decision());assertEquals(List.of(),network.sanctions().screen(null));network.sanctioned("NORD",true);assertEquals(List.of("target_bank_sanctioned:NORD"),network.screen(context).reasons());network.sanctioned("NORD",false);
  var executor=network.executor(context);executor.hold(context);assertEquals("100.00",a.hold().asString());assertEquals(Map.of("mode","interbank","source_bank","AURUM","target_bank","NORD"),executor.settle(context));assertEquals("400.00",a.balance().asString());assertEquals("100.00",b.balance().asString());assertEquals(Map.of("AURUM",Map.of("USD","100.00"),"NORD",Map.of("USD","-100.00")),network.positions());assertEquals(1,network.settlements().size());assertEquals("1970-01-01T00:00:00+00:00",network.settlements().getFirst().view().get("at"));source.assertBalanced();target.assertBalanced();network.assertBalanced();var events=audit.records();int inbound=-1,outbound=-1,settled=-1;for(int i=0;i<events.size();i++){var e=events.get(i);if(e.type().equals("ledger.posted")){if(e.source().equals("ledger:NORD"))inbound=i;else outbound=i;}if(e.type().equals("interbank.settled"))settled=i;}assertTrue(inbound>=0&&inbound<outbound&&outbound<settled);
  var broken=new FinanceSettlement.BankPort(){@Override public void hold(String x,Money m,String r){source.hold(x,m,r);}@Override public void release(String x,Money m,String r){source.release(x,m,r);}@Override public void inbound(String x,Money m,String r){source.inbound(x,m,r);}@Override public void outbound(String x,Money m,String r){throw new ServiceError("SERVICE_ERROR",400,"source ledger unavailable mid-settlement",Map.of());}};network.register(new FinanceNetwork.BankInfo("AURUM","Aurum Bank","US",false),broken);var failing=network.executor(context);failing.hold(context);var error=assertThrows(ServiceError.class,()->failing.settle(context));assertEquals("SERVICE_ERROR",error.code());assertEquals("400.00",a.balance().asString());assertEquals("200.00",b.balance().asString());assertEquals("100.00",a.hold().asString());assertEquals(1,network.settlements().size());failing.release(context);assertEquals("0.00",a.hold().asString());assertEquals(Map.of("AURUM",Map.of("USD","100.00"),"NORD",Map.of("USD","-100.00")),network.positions());source.assertBalanced();target.assertBalanced();network.assertBalanced();assertEquals("600.00",a.balance().add(b.balance()).asString());
  var missing=new FinanceRisk.Context("TRF-00000002",Money.of("1","USD"),"sender","receiver",a.id(),b.id(),"AURUM","unknown");assertEquals("beneficiary bank is unreachable",assertThrows(ServiceError.class,()->network.executor(missing)).getMessage());network.record("AURUM","AURUM",Money.of("1","USD"),"same-bank");network.assertBalanced();assertThrows(IllegalArgumentException.class,()->FinanceSanctionsScenario.run(Map.of("unsupported",true)));assertThrows(UnsupportedOperationException.class,()->network.settlements().clear());}
}
