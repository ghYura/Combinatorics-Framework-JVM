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

import java.util.*;
import java.util.function.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;

/** Source bank composition: shared directory/network/bus, local ledger/risk/coordinator. */
public final class FinanceBank {
    private final FinanceNetwork.BankInfo info;
    private final FinanceLedger ledger;
    private final FinanceRisk risk;
    private final FinanceClearing clearing;
    public FinanceBank(FinanceNetwork.BankInfo info,FinanceIdentity identity,FinanceNetwork network,Bus bus,
                       FinanceRisk.Config config,FinanceTime time,DoubleSupplier seconds,
                       Function<String,String> ids,IntSupplier digits,Supplier<String> references,Supplier<String> tokens) {
        this.info=Objects.requireNonNull(info);
        ledger=new FinanceLedger(info.code(),bus,ids,digits);
        network.register(info,new FinanceSettlement.LedgerPort(ledger));
        risk=new FinanceRisk(identity,bus,config,time,info.code(),seconds);
        var internal=new FinanceSettlement.Internal(ledger);
        clearing=new FinanceClearing(List.of(network::screen,risk::evaluate),
                context->context.crossBank()?network.executor(context):internal,bus,"clearing:"+info.code(),identity,time,
                List.of(transfer->risk.recordSettlement(transfer.context().sourceClient(),transfer.context().amount().asString())),
                seconds,references,tokens);
    }
    public String code() { return info.code(); }
    public FinanceLedger ledger() { return ledger; }
    public FinanceRisk risk() { return risk; }
    public FinanceClearing clearing() { return clearing; }
    public FinanceLedger.Account open(String client,String currency,Object deposit) { return ledger.open(client,currency,deposit); }
    public FinanceLedger.Card issueCard(String account,FinanceLedger.Brand brand,Object limit,String expiry) {
        return ledger.issue(account,brand,limit,expiry);
    }
    public Map<String,Object> view() { var result=new LinkedHashMap<>(info.view());result.put("accounts",ledger.accounts(null).size());return result; }
}
