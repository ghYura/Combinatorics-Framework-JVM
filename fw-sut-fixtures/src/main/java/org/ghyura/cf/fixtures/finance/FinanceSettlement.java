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
import java.util.Map;
/** Local transport and settlement seams shared by the original network and clearing lifecycle. */
public final class FinanceSettlement {
 private FinanceSettlement(){}
 public interface BankPort {
  void hold(String account,FinanceKernel.Money amount,String reference);
  void release(String account,FinanceKernel.Money amount,String reference);
  void outbound(String account,FinanceKernel.Money amount,String reference);
  void inbound(String account,FinanceKernel.Money amount,String reference);
 }
 public interface Executor {
  void hold(FinanceRisk.Context context);
  Map<String,Object> settle(FinanceRisk.Context context);
  void release(FinanceRisk.Context context);
 }
 public record LedgerPort(FinanceLedger ledger) implements BankPort {
  public LedgerPort{java.util.Objects.requireNonNull(ledger);}
  @Override public void hold(String a,FinanceKernel.Money m,String r){ledger.hold(a,m,r);}
  @Override public void release(String a,FinanceKernel.Money m,String r){ledger.release(a,m,r);}
  @Override public void outbound(String a,FinanceKernel.Money m,String r){ledger.outbound(a,m,r);}
  @Override public void inbound(String a,FinanceKernel.Money m,String r){ledger.inbound(a,m,r);}
 }
 public record Internal(FinanceLedger ledger) implements Executor {
  public Internal{java.util.Objects.requireNonNull(ledger);}
  @Override public void hold(FinanceRisk.Context c){ledger.hold(c.sourceAccount(),c.amount(),c.reference());}
  @Override public Map<String,Object> settle(FinanceRisk.Context c){ledger.internal(c.sourceAccount(),c.targetAccount(),c.amount(),c.reference());return FinanceKernel.fields("mode","internal","bank",c.sourceBank());}
  @Override public void release(FinanceRisk.Context c){ledger.release(c.sourceAccount(),c.amount(),c.reference());}
 }
}
