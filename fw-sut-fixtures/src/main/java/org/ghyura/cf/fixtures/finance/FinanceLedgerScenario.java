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
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
/** Strict bounded operation scripts over the source ledger API, with deterministic local seams. */
public final class FinanceLedgerScenario {
 private FinanceLedgerScenario(){}
 private static String string(Map<?,?> m,String key){if(!(m.get(key) instanceof String s))throw new IllegalArgumentException("string required: "+key);return text(s);}
 public static Map<String,Object> run(Map<?,?> request){if(!request.keySet().equals(Set.of("bank","currency","initial","operations")))throw new IllegalArgumentException("exact ledger script fields required");String bank=string(request,"bank"),currency=string(request,"currency");if(!(request.get("operations")instanceof List<?> operations)||operations.size()>64)throw new IllegalArgumentException("at most64 ledger operations required");var bus=new Bus(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));var audit=new Audit(bus);var sequence=new AtomicInteger();var digits=new AtomicInteger();var ledger=new FinanceLedger(bank,bus,p->p+"_"+String.format(Locale.ROOT,"%016x",sequence.incrementAndGet()),()->digits.getAndIncrement()%10);var accounts=new LinkedHashMap<String,FinanceLedger.Account>();accounts.put("source",ledger.open("cli_source",currency,request.get("initial")));accounts.put("target",ledger.open("cli_target",currency,"5"));var aliases=new HashMap<String,String>();var results=new ArrayList<Map<String,Object>>();int index=0;
  for(var value:operations){if(!(value instanceof Map<?,?> q))throw new IllegalArgumentException("operation object required");String op=string(q,"op");Set<String> allowed=switch(op){case "fund","hold","release","outbound","inbound"->Set.of("op","who","amount");case "internal"->Set.of("op","who","amount","target");case "status"->Set.of("op","who","status");case "close"->Set.of("op","who");case "card"->Set.of("op","who","name","brand","limit","expiry");case "card_update"->Set.of("op","who","name","status","limit");case "card_delete"->Set.of("op","who","name");default->throw new IllegalArgumentException("unsupported ledger operation");};if(!allowed.containsAll(q.keySet()))throw new IllegalArgumentException("unsupported ledger operation fields");String who=q.containsKey("who")?string(q,"who"):"source";var a=accounts.get(who);if(a==null)throw new IllegalArgumentException("unknown account alias");String ref="op:"+index++;Object result=null;try{switch(op){
   case "fund"->result=ledger.fund(a.id(),q.get("amount"),ref).view();case "hold"->ledger.hold(a.id(),Money.of(q.get("amount"),currency),ref);case "release"->ledger.release(a.id(),Money.of(q.get("amount"),currency),ref);case "outbound"->ledger.outbound(a.id(),Money.of(q.get("amount"),currency),ref);case "inbound"->ledger.inbound(a.id(),Money.of(q.get("amount"),currency),ref);
   case "internal"->{String target=q.containsKey("target")?string(q,"target"):"target";var to=accounts.get(target);if(to==null)throw new IllegalArgumentException("unknown target alias");ledger.internal(a.id(),to.id(),Money.of(q.get("amount"),currency),ref);}
   case "status"->result=ledger.status(a.id(),FinanceLedger.AccountStatus.valueOf(string(q,"status"))).view();case "close"->result=ledger.close(a.id()).view();
   case "card"->{String alias=string(q,"name");var c=ledger.issue(a.id(),FinanceLedger.Brand.valueOf(string(q,"brand")),q.get("limit"),q.containsKey("expiry")?string(q,"expiry"):"2030-12");aliases.put(alias,c.id());result=c.view();}
   case "card_update","card_delete"->{String alias=string(q,"name"),id=aliases.get(alias);if(id==null)throw new IllegalArgumentException("unknown card alias");result=(op.equals("card_delete")?ledger.deleteCard(id):ledger.updateCard(id,q.containsKey("status")?FinanceLedger.CardStatus.valueOf(string(q,"status")):null,q.get("limit"))).view();}
   default->throw new IllegalArgumentException("unsupported ledger operation");}
   results.add(fields("op",op,"value",result));
  }catch(ServiceError error){results.add(fields("op",op,"error",error.view(),"http_status",error.status()));}}
  ledger.assertBalanced();var cards=ledger.accounts(null).stream().flatMap(a->ledger.cards(a.id()).stream()).toList();return fields("results",results,"accounts",ledger.accounts(null).stream().map(FinanceLedger.Account::view).toList(),"cards",cards.stream().map(FinanceLedger.Card::view).toList(),"card_tokens",cards.stream().map(c->fields("card_id",c.id(),"pan_token",c.panToken(),"pan_last4",c.last4())).toList(),"entries",ledger.entries().stream().map(FinanceLedger.Entry::view).toList(),"debit_totals",ledger.debitTotals(),"credit_totals",ledger.creditTotals(),"events",audit.records().stream().map(Event::view).toList());
 }
}
