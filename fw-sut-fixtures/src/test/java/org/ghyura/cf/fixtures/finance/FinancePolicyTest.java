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
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
import static org.junit.jupiter.api.Assertions.*;
class FinancePolicyTest {
 @Test void all98IndependentScriptsMatch762DecisionsRestrictionsDirectoryAndAudit()throws Exception{var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);try(var in=getClass().getResourceAsStream("policy-golden.xml")){assertNotNull(in);var rows=f.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("case");assertEquals(98,rows.getLength());long count=0;for(int i=0;i<rows.getLength();i++){var row=(Map<?,?>)Json.parse(rows.item(i).getTextContent(),"independent policy");var actual=FinancePolicyScenario.run((Map<?,?>)row.get("request"));assertEquals(row.get("expected"),Json.parse(Json.write(actual),"actual policy"),"script "+i);count+=((List<?>)actual.get("results")).size();}assertEquals(762,count);}}
 @Test void identityLifecycleAndLiveTimeKeepOrderedErrorsPartialChangesAndAudit(){var bus=new Bus(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));var audit=new Audit(bus);var sequence=new AtomicInteger();var identity=new FinanceIdentity(bus,p->p+"_"+String.format(Locale.ROOT,"%016x",sequence.incrementAndGet()));var c=identity.register("\u00a0 Alice \u0085"," A@Example.Test ",FinanceIdentity.Kyc.PENDING,Set.of(),"RETAIL",false);assertEquals("Alice",c.name());assertEquals("a@example.test",c.view().get("email"));assertEquals(List.of("CUSTOMER"),c.view().get("roles"));assertEquals("VALIDATION_ERROR",assertThrows(ServiceError.class,()->identity.update(c.id(),"Bob","invalid")).code());assertEquals("Bob",c.name());assertEquals("a@example.test",c.view().get("email"));assertEquals(1,audit.records().size());identity.update(c.id(),null," B@Example.Test ");identity.setKyc(c.id(),FinanceIdentity.Kyc.VERIFIED);identity.flag(c.id(),"manual");identity.clearFlag(c.id());identity.setSanctioned(c.id(),true,null);identity.setSanctioned(c.id(),false,"clear");identity.grant(c.id(),"COMPLIANCE");assertTrue(c.hasRole("COMPLIANCE"));assertEquals(List.of("COMPLIANCE","CUSTOMER"),c.view().get("roles"));assertTrue(c.verified());assertFalse(c.flagged());assertNull(c.flagReason());identity.delete(c.id());assertNull(identity.tryGet(c.id()));assertEquals(404,assertThrows(ServiceError.class,()->identity.get(c.id())).status());assertEquals(List.of("client.registered","client.kyc_changed","client.flagged","client.flag_cleared","client.sanctioned","client.unsanctioned","client.deleted"),audit.records().stream().map(Event::type).toList());assertEquals(7,audit.lastSeq());assertEquals(1,audit.ofType("client.flagged").size());assertEquals("  #007 [identity ] client.deleted               client_id="+c.id(),audit.render(6));
  var time=new FinanceTime();var updates=new LinkedHashMap<String,Object>();updates.put("risk_window_seconds","+1");updates.put("unknown","2");var error=assertThrows(ServiceError.class,()->time.update(updates));assertEquals("unknown time unit 'unknown'",error.getMessage());assertEquals(1,time.seconds("risk_window_seconds"));assertThrows(ServiceError.class,()->time.update(Map.of("risk_window_seconds",0)));assertThrows(ServiceError.class,()->time.update(Map.of("risk_window_seconds","1.0")));assertEquals(1,time.seconds("risk_window_seconds"));time.acceptSync(false);assertFalse(time.acceptSync());assertNull(time.view().get("path"));
  var order=new ArrayList<String>();var nested=new Bus();var trace=new Audit(nested);nested.subscribe("outer",e->{order.add("specific");nested.subscribe("outer",v->order.add("late"));nested.publish("inner","test");});nested.subscribe("*",e->order.add(e.type()));nested.publish("outer","test");assertEquals(List.of("specific","inner","outer"),order);assertEquals(List.of(2L,1L),trace.records().stream().map(Event::seq).toList());
 }
 @Test void moneyRoundingNegativeZeroCurrencyAndAdmissionAreExplicit(){assertEquals("1.01",Money.of("1.005"," usd ").asString());assertEquals("1.01",Money.of("__1__.0_05__","USD").asString());assertThrows(IllegalArgumentException.class,()->Money.of("1e999999999","USD"));assertThrows(IllegalArgumentException.class,()->Money.of("1e-999999999","USD"));assertEquals("-1.01",Money.of("-1.005","USD").asString());assertEquals("-0.00",Money.of("-0.001","USD").asString());assertEquals("0.00",Money.of("-0.001","USD").negate().asString());assertEquals("-0.00",Money.of("-0.001","USD").add(Money.of("-0.001","USD")).asString());assertEquals("0.00",Money.of("-0.001","USD").add(Money.zero("USD")).asString());assertEquals("-0.00",Money.of("-0.001","USD").subtract(Money.zero("USD")).asString());assertEquals("SSA",Money.zero("ßa").currency());assertTrue(Money.of("1","USD").positive());assertTrue(Money.zero("USD").zero());assertEquals("3.00 USD",Money.of("1","USD").add(Money.of("2","USD")).toString());assertEquals("VALIDATION_ERROR",assertThrows(ServiceError.class,()->Money.of("x","USD")).code());assertEquals("currency mismatch: USD vs EUR",assertThrows(ServiceError.class,()->Money.zero("USD").add(Money.zero("EUR"))).getMessage());assertThrows(ServiceError.class,()->Money.zero("US1"));assertThrows(IllegalArgumentException.class,()->Money.of("1000000000000.01","USD"));assertThrows(IllegalArgumentException.class,()->FinancePolicyScenario.run(Map.of("profile","VERIFIED","amount","1","count",65L,"step",0L,"window",60L)));assertThrows(IllegalArgumentException.class,()->FinancePolicyScenario.run(Map.of("profile","VERIFIED","amount","1","count",1L,"step",Double.NaN,"window",60L)));assertThrows(IllegalArgumentException.class,()->text(new String(new char[]{(char)0xd800})));var bounded=new Bus();for(int i=0;i<MAX_EVENTS;i++)bounded.publish("x","test");assertThrows(IllegalArgumentException.class,()->bounded.publish("x","test"));assertEquals(FinanceRisk.Decision.ALLOW,FinanceRisk.combine(List.of()));assertEquals(FinanceRisk.Decision.BLOCK,FinanceRisk.combine(List.of(new FinanceRisk.Result("a",FinanceRisk.Decision.REVIEW,List.of(),Map.of()),new FinanceRisk.Result("b",FinanceRisk.Decision.BLOCK,List.of(),Map.of()))));}
}
