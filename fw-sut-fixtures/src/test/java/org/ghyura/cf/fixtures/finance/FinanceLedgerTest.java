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
class FinanceLedgerTest {
 @Test void all72IndependentScriptsMatch336OperationsAccountsCardsJournalAndAudit()throws Exception{var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("ledger-golden.xml")){assertNotNull(in);var rows=f.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("case");assertEquals(72,rows.getLength());int operations=0;for(int i=0;i<rows.getLength();i++){var row=(Map<?,?>)Json.parse(rows.item(i).getTextContent(),"independent ledger");var actual=FinanceLedgerScenario.run((Map<?,?>)row.get("request"));assertEquals(row.get("expected"),Json.parse(Json.write(actual),"actual ledger"),"script "+i);operations+=((List<?>)actual.get("results")).size();}assertEquals(336,operations);}}
 @Test void sourceFailedMutationCurrencyExpiryAndPrivateStateAdmissionStayObservable(){assertEquals(Money.zero("USD"),Money.of("-0.001","USD"));assertEquals(Money.zero("USD").hashCode(),Money.of("-0.001","USD").hashCode());assertEquals("-0.00",Money.of("\u00a0__-0.001__\u00a0","USD").asString());var bus=new Bus(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));var audit=new Audit(bus);var sequence=new AtomicInteger();var digits=new AtomicInteger();var ledger=new FinanceLedger("AURUM",bus,p->p+"_"+String.format(Locale.ROOT,"%016x",sequence.incrementAndGet()),()->digits.getAndIncrement()%10);assertThrows(ServiceError.class,()->ledger.open("orphan","USD","invalid"));assertEquals(1,ledger.accounts(null).size());assertEquals(1,audit.ofType("account.opened").size());var orphan=ledger.accounts(null).getFirst();assertEquals("0.00",orphan.balance().asString());assertEquals(orphan,ledger.tryAccount(orphan.id()));assertEquals(List.of(orphan),ledger.accounts("orphan"));assertNull(ledger.tryAccount("missing"));assertEquals(404,assertThrows(ServiceError.class,()->ledger.get("missing")).status());assertEquals("1970-01-01T00:00:00.000001+00:00",FinanceLedger.iso(Instant.EPOCH.plusNanos(1000)));assertEquals(Instant.EPOCH,orphan.createdAt());assertEquals(Instant.EPOCH,orphan.updatedAt());
  var a=ledger.open("customer","USD","10");assertEquals("currency mismatch: account USD vs EUR",assertThrows(ServiceError.class,()->ledger.fund(a.id(),Money.of("1","EUR"),"mismatch")).getMessage());assertEquals("currency mismatch: USD vs EUR",assertThrows(ServiceError.class,()->ledger.release(a.id(),Money.of("1","EUR"),"mismatch")).getMessage());assertThrows(ServiceError.class,()->ledger.issue(a.id(),FinanceLedger.Brand.VISA,null,"2030-13"));assertThrows(ServiceError.class,()->ledger.issue(a.id(),FinanceLedger.Brand.VISA,null,"2030-12\r\n"));assertEquals(0,digits.get());var c=ledger.issue(a.id(),FinanceLedger.Brand.AMEX,"0","٢٠٣٠-12\n");assertEquals(15,digits.get());assertEquals("0.00",c.view().get("spending_limit"));assertEquals("1234",c.last4());assertEquals(16,c.panToken().length());assertEquals(Instant.EPOCH,c.createdAt());assertEquals(Instant.EPOCH,c.updatedAt());assertThrows(ServiceError.class,()->ledger.updateCard(c.id(),FinanceLedger.CardStatus.CLOSED,"invalid"));assertEquals(FinanceLedger.CardStatus.CLOSED,c.status());assertEquals("0.00",c.spendingLimit().asString());assertEquals(0,audit.ofType("card.updated").size());assertThrows(ServiceError.class,()->ledger.updateCard(c.id(),FinanceLedger.CardStatus.ACTIVE,null));ledger.deleteCard(c.id());ledger.deleteCard(c.id());assertEquals(2,audit.ofType("card.closed").size());assertEquals(List.of(c),ledger.cards(a.id()));assertEquals(404,assertThrows(ServiceError.class,()->ledger.card("missing")).status());ledger.assertBalanced();assertEquals(ledger.debitTotals(),ledger.creditTotals());assertEquals("10.00",ledger.balance(a.id()).get("available"));assertThrows(IllegalArgumentException.class,()->FinanceLedgerScenario.run(Map.of("bank","AURUM","currency","USD","initial","1","operations",Collections.nCopies(65,Map.of("op","close")))));assertThrows(IllegalArgumentException.class,()->FinanceLedgerScenario.run(Map.of("bank","AURUM","currency","USD","initial","1","operations",List.of(Map.of("op","foreign")))));assertThrows(UnsupportedOperationException.class,()->ledger.entries().clear());}
}
