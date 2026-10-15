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
import java.math.*;
import java.util.*;
import java.util.function.DoubleSupplier;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
/** Original risk gate, with an injected seconds clock for repeatable boundary cases. */
public final class FinanceRisk {
 public enum Decision {ALLOW,REVIEW,BLOCK}
 public record Context(String reference,Money amount,String sourceClient,String targetClient,String sourceAccount,String targetAccount,String sourceBank,String targetBank){public Context{Objects.requireNonNull(amount);text(reference);text(sourceClient);text(targetClient);text(sourceAccount);text(targetAccount);text(sourceBank);text(targetBank);}public boolean crossBank(){return !sourceBank.equals(targetBank);}}
 public record Result(String gate,Decision decision,List<String> reasons,Map<String,Object> metadata){public Result{reasons=List.copyOf(reasons);metadata=Collections.unmodifiableMap(new LinkedHashMap<>(metadata));}public Map<String,Object> view(){return fields("gate",gate,"decision",decision.name(),"reasons",reasons,"metadata",metadata);}}
 public static Decision combine(List<Result> results){var worst=Decision.ALLOW;for(var r:results)if(r.decision().ordinal()>worst.ordinal())worst=r.decision();return worst;}
 public record Config(BigDecimal review,BigDecimal block,int countReview,int countBlock,BigDecimal sumReview,BigDecimal sumBlock){
  public Config{for(var a:List.of(review,block,sumReview,sumBlock))if(a.scale()!=2||a.abs().compareTo(new BigDecimal("1000000000000.00"))>0)throw new IllegalArgumentException("bounded two-decimal risk threshold required");if(block.signum()<=0||review.signum()<0||sumReview.signum()<0||sumBlock.signum()<0||countReview<0||countBlock<0||countReview>4096||countBlock>4096)throw new IllegalArgumentException("invalid bounded risk thresholds");}
  public static Config defaults(){return new Config(new BigDecimal("1000.00"),new BigDecimal("10000.00"),8,12,new BigDecimal("5000.00"),new BigDecimal("20000.00"));}
 }
 private record Attempt(double seconds,BigDecimal amount){}
 private final FinanceIdentity identity;private final Bus bus;private final Config config;private final FinanceTime time;private final DoubleSupplier clock;private final String name;private final Map<String,List<Attempt>> attempts=new LinkedHashMap<>();
 public FinanceRisk(FinanceIdentity identity,Bus bus,Config config,FinanceTime time,String bank,DoubleSupplier clock){this.identity=Objects.requireNonNull(identity);this.bus=Objects.requireNonNull(bus);this.config=Objects.requireNonNull(config);this.time=Objects.requireNonNull(time);this.clock=Objects.requireNonNull(clock);name=bank.isEmpty()?"risk":"risk:"+text(bank);}
 private double now(){double n=clock.getAsDouble();if(!Double.isFinite(n)||Math.abs(n)>1e12)throw new IllegalArgumentException("finite bounded seconds clock required");return n;}
 private List<Attempt> recent(String id,double now){var kept=new ArrayList<Attempt>();double cutoff=now-time.seconds("risk_window_seconds");for(var a:attempts.getOrDefault(id,List.of()))if(a.seconds()>=cutoff)kept.add(a);if(!attempts.containsKey(id)&&attempts.size()>=MAX_CLIENTS)throw new IllegalArgumentException("risk client bound exceeded");attempts.put(id,kept);return kept;}
 private void autoflag(String id,String reason){var c=identity.tryGet(id);if(c!=null&&!c.flagged())identity.flag(id,reason);}
 public Result evaluate(Context ctx){double now=now();var recent=recent(ctx.sourceClient(),now);if(attempts.values().stream().mapToInt(List::size).sum()>=4096)throw new IllegalArgumentException("risk attempt bound exceeded");int count=recent.size()+1;var amount=ctx.amount().amount();var sum=recent.stream().map(Attempt::amount).reduce(BigDecimal.ZERO,BigDecimal::add).add(amount);var reasons=new ArrayList<String>();var decision=Decision.ALLOW;var c=identity.tryGet(ctx.sourceClient());if(c==null){decision=Decision.BLOCK;reasons.add("unknown_client");}else{if(c.flagged()){decision=Decision.BLOCK;reasons.add("client_flagged:"+c.flagReason());}if(!c.verified()){decision=Decision.BLOCK;reasons.add("kyc_"+c.kyc().name().toLowerCase(Locale.ROOT));}}
  if(amount.compareTo(config.block())>=0){decision=Decision.BLOCK;reasons.add("amount_over_hard_limit");}else if(amount.compareTo(config.review())>=0){if(decision==Decision.ALLOW)decision=Decision.REVIEW;reasons.add("large_amount");}
  if(count>config.countBlock()||sum.compareTo(config.sumBlock())>0){decision=Decision.BLOCK;reasons.add("velocity_hard_limit");autoflag(ctx.sourceClient(),"velocity_hard_limit");}else if(count>config.countReview()||sum.compareTo(config.sumReview())>0){if(decision==Decision.ALLOW)decision=Decision.REVIEW;reasons.add("velocity_soft_limit");autoflag(ctx.sourceClient(),"velocity_soft_limit");}
  // Capture this evaluation's original clock sample; an injected clock is called once per evaluation.
  recent.add(new Attempt(now,amount));long score=score(amount,count,c!=null&&c.flagged());bus.publish("risk.evaluated",name,"reference",ctx.reference(),"decision",decision.name(),"score",score,"reasons",reasons.isEmpty()?"clean":String.join(",",reasons));return new Result(name,decision,reasons,fields("score",score,"window_count",count,"window_sum",sum.setScale(2).toPlainString()));
 }
 private long score(BigDecimal amount,int count,boolean flagged){long score=Math.min(60,amount.divide(config.block(),DECIMAL).multiply(BigDecimal.valueOf(60),DECIMAL).toBigInteger().longValueExact());score+=Math.min(30,(long)(count-1)*8);if(flagged)score+=40;return Math.min(99,score);}
 public Map<String,Object> restrictions(String id){var recent=recent(id,now());var c=identity.tryGet(id);var sum=recent.stream().map(Attempt::amount).reduce(BigDecimal.ZERO,BigDecimal::add);return fields("client_id",id,"flagged",c!=null&&c.flagged(),"flag_reason",c==null?null:c.flagReason(),"kyc_status",c==null?"UNKNOWN":c.kyc().name(),"sanctioned",c!=null&&c.sanctioned(),"window_count",recent.size(),"window_sum",sum.setScale(2).toPlainString(),"thresholds",fields("review_amount",config.review().toPlainString(),"block_amount",config.block().toPlainString(),"velocity_count_review",config.countReview(),"velocity_count_block",config.countBlock()));}
 public void recordSettlement(String client,String amount){bus.publish("risk.settlement_recorded",name,"client_id",text(client),"amount",text(amount));}
}
