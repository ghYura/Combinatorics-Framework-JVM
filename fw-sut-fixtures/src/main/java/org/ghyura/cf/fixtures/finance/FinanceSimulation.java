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

/** All thirteen source simulation phases share one seeded eight-persona/eight-account world. */
public final class FinanceSimulation {
    private FinanceSimulation() { }
    public static FinanceEcosystem deterministic() {
        var counters=new HashMap<String,AtomicInteger>();var refs=new AtomicInteger();var tokens=new AtomicInteger();
        var eco=new FinanceEcosystem(new FinanceTime(),Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),()->0,
                prefix->prefix+"_"+String.format(Locale.ROOT,"%016x",counters.computeIfAbsent(prefix,k->new AtomicInteger()).incrementAndGet()),
                ()->1,()->String.format(Locale.ROOT,"TRF-%08X",refs.incrementAndGet()),()->"token-"+tokens.incrementAndGet());
        eco.addBank("AURUM","Aurum Bank","US",false,null);eco.addBank("NORD","Nord Credit","EU",false,null);return eco;
    }
    public static Map<String,Object> run() {
        var eco=deterministic();var people=new LinkedHashMap<String,String>();var accounts=new LinkedHashMap<String,String>();
        String[][] directory={{"alice","Alice Stone","alice@x.com","RETAIL"},{"bob","Bob Rivera","bob@x.com","RETAIL"},
                {"carol","Carol Vance","carol@x.com","PREMIER"},{"dave","Dave Unchecked","dave@x.com","RETAIL"},
                {"mallory","Mallory Black","mallory@x.com","RETAIL"},{"walter","Walter Whale","walter@x.com","PRIVATE"},
                {"eve","Eve Burst","eve@x.com","RETAIL"},{"officer","Olivia Officer","olivia@bank.com","RETAIL"}};
        for(var row:directory)people.put(row[0],eco.identity().register(row[1],row[2],row[0].equals("dave")?FinanceIdentity.Kyc.PENDING:FinanceIdentity.Kyc.VERIFIED,
                row[0].equals("officer")?Set.of("CUSTOMER","COMPLIANCE"):Set.of("CUSTOMER"),row[3],false).id());
        String[][] opening={{"alice","alice","AURUM","1000"},{"bob","bob","NORD","0"},{"carol_aurum","carol","AURUM","300"},
                {"carol_nord","carol","NORD","0"},{"dave","dave","AURUM","200"},{"mallory","mallory","AURUM","200"},
                {"walter","walter","AURUM","9000"},{"eve","eve","AURUM","2000"}};
        for(var row:opening)accounts.put(row[0],eco.open(row[2],people.get(row[1]),"USD",row[3]).id());
        var phases=new ArrayList<Map<String,Object>>();
        class Actions {
            FinanceClearing.Transfer initiate(String source,String target,String amount,String key) {
                return eco.initiate(accounts.get(source),accounts.get(target),amount,key);
            }
            FinanceClearing.Transfer settle(FinanceClearing.Transfer transfer) {
                if(!transfer.tokens().isEmpty())for(var party:FinanceClearing.Party.values())eco.confirm(transfer.reference(),party,transfer.tokens().get(party),"CONFIRM");
                return eco.transfer(transfer.reference());
            }
            String balance(String name,String field) { return (String)eco.balance(accounts.get(name)).get(field); }
        }
        var a=new Actions();var ledger=eco.bank("AURUM").ledger();
        var card=ledger.issue(accounts.get("alice"),FinanceLedger.Brand.VISA,"500","2030-12");
        ledger.updateCard(card.id(),null,"750");ledger.updateCard(card.id(),FinanceLedger.CardStatus.BLOCKED,null);ledger.deleteCard(card.id());
        phases.add(fields("phase",1,"name","card-crud","card",fields("brand","VISA","status",card.status().name(),"spending_limit",card.spendingLimit().asString())));
        var transfer=a.settle(a.initiate("alice","carol_aurum","150","intra-1"));
        phases.add(fields("phase",2,"name","intra","status",transfer.status().name(),"alice",a.balance("alice","balance"),"carol_aurum",a.balance("carol_aurum","balance")));
        transfer=a.settle(a.initiate("alice","bob","200","cross-1"));
        phases.add(fields("phase",3,"name","cross","status",transfer.status().name(),"bob",a.balance("bob","balance"),"positions",fields("AURUM",((Map<?,?>)eco.network().positions().get("AURUM")).get("USD"),"NORD",((Map<?,?>)eco.network().positions().get("NORD")).get("USD"))));
        transfer=a.settle(a.initiate("carol_aurum","carol_nord","75","self-1"));
        phases.add(fields("phase",4,"name","self-cross","status",transfer.status().name(),"carol_aurum",a.balance("carol_aurum","balance"),"carol_nord",a.balance("carol_nord","balance")));
        transfer=a.initiate("alice","carol_aurum","60","reject-1");String before=a.balance("alice","available");
        eco.confirm(transfer.reference(),FinanceClearing.Party.RECEIVER,transfer.tokens().get(FinanceClearing.Party.RECEIVER),"REJECT");
        phases.add(fields("phase",5,"name","receiver-reject","status",transfer.status().name(),"before_available",before,"after_available",a.balance("alice","available")));
        transfer=a.initiate("bob","alice","500","nsf-1");phases.add(fields("phase",6,"name","insufficient","status",transfer.status().name(),"reason",transfer.failure()));
        transfer=a.initiate("dave","alice","20","kyc-1");phases.add(fields("phase",7,"name","pending-kyc","status",transfer.status().name(),"reason",((List<?>)transfer.view().get("reasons")).getFirst()));
        eco.network().sanctions().client(people.get("mallory"));var senderListed=a.initiate("mallory","alice","10","san-1");var receiverListed=a.initiate("alice","mallory","10","san-2");
        phases.add(fields("phase",8,"name","client-sanctions","statuses",List.of(senderListed.status().name(),receiverListed.status().name()),"reasons",List.of(((List<?>)senderListed.view().get("reasons")).getFirst(),((List<?>)receiverListed.view().get("reasons")).getFirst())));
        eco.network().sanctioned("NORD",true);transfer=a.initiate("alice","bob","10","san-3");eco.network().sanctioned("NORD",false);
        phases.add(fields("phase",9,"name","bank-sanctions","status",transfer.status().name(),"reason",((List<?>)transfer.view().get("reasons")).getFirst(),"final_bank_sanctioned",eco.network().bank("NORD").sanctioned()));
        var denied=a.settle(a.initiate("walter","bob","2500","rev-deny"));String awaiting=denied.status().name();eco.review(denied.reference(),false,people.get("officer"));
        var approved=a.settle(a.initiate("walter","bob","2500","rev-ok"));eco.review(approved.reference(),true,people.get("officer"));
        phases.add(fields("phase",10,"name","reviews","after_both",awaiting,"denied",denied.status().name(),"approved",approved.status().name(),"walter",a.balance("walter","balance"),"bob",a.balance("bob","balance")));
        var velocity=new ArrayList<String>();for(int i=1;i<=10;i++){transfer=a.initiate("eve","alice","100","vel-"+i);if(!transfer.tokens().isEmpty())a.settle(transfer);velocity.add(transfer.status().name());}
        phases.add(fields("phase",11,"name","velocity","statuses",velocity,"eve_flagged",eco.identity().get(people.get("eve")).flagged(),"eve_hold",a.balance("eve","hold"),"eve",a.balance("eve","balance"),"alice",a.balance("alice","balance")));
        var first=a.initiate("alice","carol_aurum","25","dup-key");var replay=a.initiate("alice","carol_aurum","25","dup-key");
        phases.add(fields("phase",12,"name","idempotency","same_reference",first.reference().equals(replay.reference()),"status",first.status().name(),"alice_hold",a.balance("alice","hold")));
        ledger.status(accounts.get("carol_aurum"),FinanceLedger.AccountStatus.FROZEN);transfer=a.initiate("carol_aurum","alice","10","frozen-1");
        phases.add(fields("phase",13,"name","frozen-source","status",transfer.status().name(),"reason",transfer.failure(),"carol_status",ledger.get(accounts.get("carol_aurum")).status().name()));
        eco.assertBalanced();
        var all=eco.banks().stream().flatMap(bank->bank.clearing().transfers().stream()).sorted(Comparator.comparing(FinanceClearing.Transfer::reference)).toList();
        var rows=new ArrayList<Map<String,Object>>();var statuses=new LinkedHashMap<String,Integer>();
        for(var item:all) {
            var gate=(Map<?,?>)((List<?>)item.view().get("gate_results")).get(1);var metadata=(Map<?,?>)gate.get("metadata");
            String source=accounts.entrySet().stream().filter(e->e.getValue().equals(item.context().sourceAccount())).findFirst().orElseThrow().getKey();
            String target=accounts.entrySet().stream().filter(e->e.getValue().equals(item.context().targetAccount())).findFirst().orElseThrow().getKey();
            rows.add(fields("reference",item.reference(),"key",item.idempotencyKey(),"source",source,"target",target,"amount",item.context().amount().asString(),"status",item.status().name(),"reasons",item.view().get("reasons"),"risk",fields("decision",gate.get("decision"),"score",metadata.get("score"),"window_count",metadata.get("window_count"),"window_sum",metadata.get("window_sum"))));
            statuses.merge(item.status().name(),1,Integer::sum);
        }
        var balances=new LinkedHashMap<String,String>();var holds=new LinkedHashMap<String,String>();
        accounts.forEach((name,id)->{balances.put(name,(String)eco.balance(id).get("balance"));holds.put(name,(String)eco.balance(id).get("hold"));});
        var totals=new LinkedHashMap<String,String>();for(var bank:eco.banks())totals.put(bank.code(),String.format(Locale.ROOT,"%.2f",bank.ledger().debitTotals().get("USD")));
        return fields("phases",phases,"transfers",rows,"final",fields("balances",balances,"holds",holds,"positions",eco.network().positions(),"statuses",statuses,"journal_totals",totals,"account_count",accounts.size(),"client_count",people.size(),"transfer_count",all.size()));
    }
}
