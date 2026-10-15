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

/** Bounded coordinator scripts using real local ledger/network ports and a controlled policy gate. */
public final class FinanceClearingScenario {
    private FinanceClearingScenario() { }
    public static final Set<String> WORKFLOWS = Set.of("happy", "sender_reject", "late_confirmation", "expire_partial",
            "review_denied", "wrong_token_repeat", "idempotent_changed_body", "review_actor_controls");
    public static Map<String,Object> run(Map<?,?> request) {
        if (!request.keySet().equals(Set.of("cross_bank", "gate", "amount", "workflow"))
                || !(request.get("cross_bank") instanceof Boolean cross)
                || !(request.get("gate") instanceof String decision)
                || !(request.get("amount") instanceof String amount)
                || !(request.get("workflow") instanceof String workflow) || !WORKFLOWS.contains(workflow))
            throw new IllegalArgumentException("unsupported bounded clearing profile");
        var gateDecision = FinanceRisk.Decision.valueOf(decision);
        var money = Money.of(amount, "USD");
        if (money.amount().abs().compareTo(new java.math.BigDecimal("5000.00")) > 0)
            throw new IllegalArgumentException("clearing profile amount exceeds5000");
        var bus = new Bus(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var audit = new Audit(bus);
        var clients = new AtomicInteger();
        var identity = new FinanceIdentity(bus, prefix -> prefix + "_" + String.format(Locale.ROOT,"%016x",clients.incrementAndGet()));
        var sender = identity.register("sender", "a@example.test", FinanceIdentity.Kyc.VERIFIED, Set.of("CUSTOMER"), "retail", false);
        var receiver = identity.register("receiver", "b@example.test", FinanceIdentity.Kyc.VERIFIED, Set.of("CUSTOMER"), "retail", false);
        var officer = identity.register("officer", "o@example.test", FinanceIdentity.Kyc.VERIFIED, Set.of("COMPLIANCE"), "retail", false);
        var counters = new HashMap<String,AtomicInteger>();
        java.util.function.Function<String,String> ids = prefix -> prefix + "_" + String.format(Locale.ROOT,"%016x",counters.computeIfAbsent(prefix,k -> new AtomicInteger()).incrementAndGet());
        var sourceLedger = new FinanceLedger("AURUM", bus, ids, () -> 1);
        var targetLedger = cross ? new FinanceLedger("NORD", bus, ids, () -> 1) : sourceLedger;
        var source = sourceLedger.open(sender.id(), "USD", "5000.00");
        var target = targetLedger.open(receiver.id(), "USD", "0.00");
        var network = new FinanceNetwork(identity,bus);
        network.register(new FinanceNetwork.BankInfo("AURUM","Aurum","US",false),new FinanceSettlement.LedgerPort(sourceLedger));
        if (cross) network.register(new FinanceNetwork.BankInfo("NORD","Nord","EU",false),new FinanceSettlement.LedgerPort(targetLedger));
        var time = new FinanceTime(); time.update(Map.of("confirmation_ttl_seconds",10));
        double[] now = {0}; var tokens = new AtomicInteger();
        var gate = new FinanceRisk.Result("fixture",gateDecision,gateDecision == FinanceRisk.Decision.ALLOW ? List.of() : List.of("controlled_"+decision.toLowerCase(Locale.ROOT)),Map.of());
        var clearing = new FinanceClearing(List.of(context -> gate),
                context -> context.crossBank() ? network.executor(context) : new FinanceSettlement.Internal(sourceLedger),
                bus,"clearing:AURUM",identity,time,List.of(),() -> now[0],() -> "TRF-00000001",() -> "token-"+tokens.incrementAndGet());
        var results = new ArrayList<Map<String,Object>>(); var errors = new ArrayList<Map<String,Object>>();
        var transfer = clearing.initiate(new FinanceClearing.Request("same",money,sender.id(),source.id(),"AURUM",receiver.id(),target.id(),cross ? "NORD" : "AURUM"));
        results.add(fields("transfer",transfer.view())); String reference = transfer.reference();
        class Actions {
            void call(java.util.function.Supplier<Map<String,Object>> action) {
                try { results.add(action.get()); }
                catch (ServiceError error) { errors.add(error.view()); results.add(fields("error",error.view(),"http_status",error.status())); }
            }
            void confirm(FinanceClearing.Party party,String token,String response) {
                call(() -> fields("transfer",clearing.confirm(reference,party,token.equals("valid") ? transfer.tokens().getOrDefault(party,"bad") : "wrong",response).view()));
            }
            void review(boolean approve,String actor) { call(() -> fields("transfer",clearing.review(reference,approve,actor).view())); }
        }
        var actions = new Actions(); var a = FinanceClearing.Party.SENDER; var b = FinanceClearing.Party.RECEIVER;
        if (workflow.equals("sender_reject")) actions.confirm(a,"valid","reject");
        else if (workflow.equals("expire_partial")) {
            actions.confirm(a,"valid","CONFIRM"); now[0]=10;
            results.add(fields("expired",clearing.expireDue(now[0]).stream().map(FinanceClearing.Transfer::reference).toList()));
            actions.confirm(b,"valid","CONFIRM");
        } else {
            if (workflow.equals("wrong_token_repeat")) { actions.confirm(a,"wrong","CONFIRM"); actions.confirm(a,"valid","wat"); }
            if (workflow.equals("late_confirmation")) now[0]=11;
            if (workflow.equals("idempotent_changed_body")) {
                var repeated = clearing.initiate(new FinanceClearing.Request("same",Money.of("400","USD"),sender.id(),source.id(),"AURUM",officer.id(),"changed-target","CHANGED"));
                results.add(fields("transfer",repeated.view(),"same_reference",repeated == transfer));
            }
            actions.confirm(a,"valid","CONFIRM");
            if (workflow.equals("wrong_token_repeat")) {
                actions.confirm(a,"valid","CONFIRM"); actions.confirm(a,"wrong","CONFIRM"); actions.confirm(a,"valid","REJECT");
            }
            actions.confirm(b,"valid","CONFIRM");
            if (workflow.equals("review_denied")) actions.review(false,officer.id());
            else if (workflow.equals("review_actor_controls")) {
                actions.review(true,sender.id()); actions.review(true,"missing"); actions.review(true,null);
            } else if (transfer.status() == FinanceClearing.Status.AWAITING_REVIEW) actions.review(true,null);
            if (workflow.equals("wrong_token_repeat")) actions.confirm(b,"valid","CONFIRM");
        }
        sourceLedger.assertBalanced(); targetLedger.assertBalanced(); network.assertBalanced();
        return fields("results",results,"transfers",clearing.transfers().stream().map(FinanceClearing.Transfer::view).toList(),
                "balances",fields("source",source.balance().asString(),"target",target.balance().asString(),"hold",source.hold().asString(),"available",source.available().asString()),
                "events",audit.records().stream().filter(e -> e.type().startsWith("transfer.")).map(e -> fields("type",e.type(),"data",e.data())).toList(),
                "errors",errors,"now",(long)now[0]);
    }
}
