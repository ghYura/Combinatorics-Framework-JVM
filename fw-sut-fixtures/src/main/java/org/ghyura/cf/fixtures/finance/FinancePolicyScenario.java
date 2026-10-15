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
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
/** Deterministic source policy script, reusable by the native adapter and local callers. */
public final class FinancePolicyScenario {
 private FinancePolicyScenario(){}
 public static Map<String,Object> run(Map<?,?> request){
  if(!request.keySet().equals(Set.of("profile","amount","count","step","window")))throw new IllegalArgumentException("exact policy script fields required");String profile=(String)request.get("profile");if(!Set.of("VERIFIED","PENDING","REJECTED","flagged","unknown").contains(profile))throw new IllegalArgumentException("unsupported identity profile");if(!(request.get("count") instanceof Long n)||n<1||n>64)throw new IllegalArgumentException("script count must be1..64");if(!(request.get("window") instanceof Long window)||window<1||window>Integer.MAX_VALUE)throw new IllegalArgumentException("positive integral risk window required");if(!(request.get("step") instanceof Number step))throw new IllegalArgumentException("numeric seconds step required");double delta=step.doubleValue();if(!Double.isFinite(delta)||delta<0||delta>1e9)throw new IllegalArgumentException("seconds step outside bounds");Money amount=Money.of(request.get("amount"),"USD");var bus=new Bus(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));var audit=new Audit(bus);var identity=new FinanceIdentity(bus,prefix->prefix+"_0000000000000001");String client="missing";
  if(!profile.equals("unknown")){var kyc=profile.equals("PENDING")?FinanceIdentity.Kyc.PENDING:profile.equals("REJECTED")?FinanceIdentity.Kyc.REJECTED:FinanceIdentity.Kyc.VERIFIED;var c=identity.register(" Alice "," Alice@Example.Test ",kyc,null,"RETAIL",false);client=c.id();if(profile.equals("flagged"))identity.flag(client,"manual");}
  var time=new FinanceTime();time.update(Map.of("risk_window_seconds",window));double[] now={0};var risk=new FinanceRisk(identity,bus,FinanceRisk.Config.defaults(),time,"AURUM",()->now[0]);var results=new ArrayList<Map<String,Object>>();for(int i=0;i<n;i++){now[0]=i*delta;results.add(risk.evaluate(new FinanceRisk.Context("TRF-"+i,amount,client,"receiver","source-account","target-account","AURUM","NORD")).view());}return fields("results",results,"restrictions",risk.restrictions(client),"clients",identity.list().stream().map(FinanceIdentity.Client::view).toList(),"events",audit.records().stream().map(Event::view).toList());
 }
}
