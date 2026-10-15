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
/** Original sanctions gate with explicit independent name, ID, client and bank flags. */
public final class FinanceSanctionsScenario {
 private FinanceSanctionsScenario(){}
 public static Map<String,Object> run(Map<?,?> request){var keys=Set.of("sender_flag","sender_id","sender_name","receiver_flag","receiver_id","receiver_name","source_bank_flag","target_bank_flag","source_bank_list","target_bank_list");if(!request.keySet().equals(keys)||!request.values().stream().allMatch(Boolean.class::isInstance))throw new IllegalArgumentException("exact Boolean sanctions profile required");var bus=new Bus(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));var sequence=new AtomicInteger();var identity=new FinanceIdentity(bus,p->p+"_"+String.format(Locale.ROOT,"%016x",sequence.incrementAndGet()));var sender=identity.register("Alice","a@example.test",FinanceIdentity.Kyc.VERIFIED,null,"RETAIL",(boolean)request.get("sender_flag"));var receiver=identity.register("Bob","b@example.test",FinanceIdentity.Kyc.VERIFIED,null,"RETAIL",(boolean)request.get("receiver_flag"));var network=new FinanceNetwork(identity,bus);var dummy=new FinanceSettlement.LedgerPort(new FinanceLedger("unused",bus));network.register(new FinanceNetwork.BankInfo("AURUM","Aurum Bank","US",(boolean)request.get("source_bank_flag")),dummy);network.register(new FinanceNetwork.BankInfo("NORD","Nord Credit","EU",(boolean)request.get("target_bank_flag")),dummy);var list=network.sanctions();if((boolean)request.get("sender_id"))list.client(sender.id());if((boolean)request.get("receiver_id"))list.client(receiver.id());if((boolean)request.get("sender_name"))list.name(" ALICE ");if((boolean)request.get("receiver_name"))list.name(" bOb ");if((boolean)request.get("source_bank_list"))list.bank("AURUM");if((boolean)request.get("target_bank_list"))list.bank("NORD");return network.screen(new FinanceRisk.Context("TRF-00000001",Money.of("1","USD"),sender.id(),receiver.id(),"source","target","AURUM","NORD")).view();}
}
