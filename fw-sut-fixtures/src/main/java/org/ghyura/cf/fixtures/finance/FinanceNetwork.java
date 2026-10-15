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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
/** Original in-process registry, sanctions screening and non-atomic correspondent settlement. */
public final class FinanceNetwork {
 public static final int MAX_BANKS=16,MAX_SETTLEMENTS=4096;
 public static final class BankInfo {
  private final String code,name,jurisdiction;private boolean sanctioned;
  public BankInfo(String code,String name,String jurisdiction,boolean sanctioned){this.code=text(code);this.name=text(name);this.jurisdiction=text(jurisdiction);this.sanctioned=sanctioned;}
  public String code(){return code;}public boolean sanctioned(){return sanctioned;}public Map<String,Object> view(){return fields("bank_code",code,"name",name,"jurisdiction",jurisdiction,"sanctioned",sanctioned);}
 }
 public static final class Sanctions {
  private final Set<String> clients=new LinkedHashSet<>(),banks=new LinkedHashSet<>(),names=new LinkedHashSet<>();
  private static void add(Set<String> values,String value,int max){text(value);if(!values.contains(value)&&values.size()>=max)throw new IllegalArgumentException("sanctions set bound exceeded");values.add(value);}
  public void client(String id){add(clients,id,256);}public void bank(String code){add(banks,code,MAX_BANKS);}public void name(String value){add(names,strip(value).toLowerCase(Locale.ROOT),256);}
  public List<String> screen(FinanceIdentity.Client client){if(client==null)return List.of();var hits=new ArrayList<String>();if(client.sanctioned())hits.add("ofac_flag");if(clients.contains(client.id()))hits.add("listed_id");if(names.contains(strip(client.name()).toLowerCase(Locale.ROOT)))hits.add("name_match");return List.copyOf(hits);}
  public boolean bankSanctioned(String code,boolean registryFlag){return registryFlag||banks.contains(code);}
 }
 public record Settlement(String reference,String sourceBank,String targetBank,Money amount,Instant at){public Map<String,Object> view(){return fields("reference",reference,"source_bank",sourceBank,"target_bank",targetBank,"amount",amount.asString(),"currency",amount.currency(),"at",FinanceLedger.iso(at));}}
 private final FinanceIdentity identity;private final Bus bus;private final Sanctions sanctions=new Sanctions();private final Map<String,BankInfo> banks=new LinkedHashMap<>();private final Map<String,FinanceSettlement.BankPort> ports=new LinkedHashMap<>();private final Map<String,Map<String,BigDecimal>> positions=new LinkedHashMap<>();private final List<Settlement> settlements=new ArrayList<>();
 public FinanceNetwork(FinanceIdentity identity,Bus bus){this.identity=Objects.requireNonNull(identity);this.bus=Objects.requireNonNull(bus);}public Sanctions sanctions(){return sanctions;}
 public void register(BankInfo info,FinanceSettlement.BankPort port){Objects.requireNonNull(info);Objects.requireNonNull(port);if(!banks.containsKey(info.code)&&banks.size()>=MAX_BANKS)throw new IllegalArgumentException("bank registry bound exceeded");banks.put(info.code,info);ports.put(info.code,port);bus.publish("bank.registered","network","bank",info.code,"jurisdiction",info.jurisdiction,"sanctioned",info.sanctioned);}
 public BankInfo tryBank(String code){return banks.get(code);}public BankInfo bank(String code){var info=tryBank(code);if(info==null)throw notFound("bank not registered","bank_code",code);return info;}public boolean member(String code){return banks.containsKey(code);}public List<BankInfo> banks(){return List.copyOf(banks.values());}
 public BankInfo sanctioned(String code,boolean value){var info=bank(code);info.sanctioned=value;bus.publish(value?"bank.sanctioned":"bank.unsanctioned","network","bank",code);return info;}
 public FinanceRisk.Result screen(FinanceRisk.Context context){var reasons=new ArrayList<String>();for(var party:List.of(Map.entry("sender",context.sourceClient()),Map.entry("receiver",context.targetClient())))for(String hit:sanctions.screen(identity.tryGet(party.getValue())))reasons.add(party.getKey()+"_"+hit);for(var side:List.of(Map.entry("source",context.sourceBank()),Map.entry("target",context.targetBank()))){var info=tryBank(side.getValue());if(sanctions.bankSanctioned(side.getValue(),info!=null&&info.sanctioned))reasons.add(side.getKey()+"_bank_sanctioned:"+side.getValue());}bus.publish("sanctions.screened","network","reference",context.reference(),"result",reasons.isEmpty()?"clear":String.join(",",reasons));return new FinanceRisk.Result("sanctions",reasons.isEmpty()?FinanceRisk.Decision.ALLOW:FinanceRisk.Decision.BLOCK,reasons,Map.of());}
 public FinanceSettlement.Executor executor(FinanceRisk.Context context){if(!member(context.sourceBank()))throw new ServiceError("UPSTREAM_ERROR",502,"originating bank is not a network member",fields("bank",context.sourceBank()));if(!member(context.targetBank()))throw new ServiceError("UPSTREAM_ERROR",502,"beneficiary bank is unreachable",fields("bank",context.targetBank()));var source=ports.get(context.sourceBank());var target=ports.get(context.targetBank());return new FinanceSettlement.Executor(){
  @Override public void hold(FinanceRisk.Context c){source.hold(c.sourceAccount(),c.amount(),c.reference());}
  @Override public Map<String,Object> settle(FinanceRisk.Context c){target.inbound(c.targetAccount(),c.amount(),c.reference());source.outbound(c.sourceAccount(),c.amount(),c.reference());record(c.sourceBank(),c.targetBank(),c.amount(),c.reference());return fields("mode","interbank","source_bank",c.sourceBank(),"target_bank",c.targetBank());}
  @Override public void release(FinanceRisk.Context c){source.release(c.sourceAccount(),c.amount(),c.reference());}
 };}
 public void record(String source,String target,Money amount,String reference){text(source);text(target);text(reference);Objects.requireNonNull(amount);if(settlements.size()>=MAX_SETTLEMENTS)throw new IllegalArgumentException("settlement bound exceeded");long unseen=java.util.stream.Stream.of(source,target).distinct().filter(v->!positions.containsKey(v)).count();if(positions.size()+unseen>MAX_BANKS)throw new IllegalArgumentException("position bank bound exceeded");var src=positions.computeIfAbsent(source,k->new LinkedHashMap<>());var dst=positions.computeIfAbsent(target,k->new LinkedHashMap<>());String currency=amount.currency();src.put(currency,src.getOrDefault(currency,BigDecimal.ZERO).add(amount.amount()).setScale(2));dst.put(currency,dst.getOrDefault(currency,BigDecimal.ZERO).subtract(amount.amount()).setScale(2));settlements.add(new Settlement(reference,source,target,amount,bus.now()));bus.publish("interbank.settled","network","reference",reference,"src",source,"dst",target,"amount",amount.toString());}
 public Map<String,Object> positions(){var out=new LinkedHashMap<String,Object>();positions.forEach((bank,currencies)->{var p=new LinkedHashMap<String,String>();currencies.forEach((c,a)->p.put(c,a.toPlainString()));out.put(bank,p);});return out;}
 public List<Settlement> settlements(){return List.copyOf(settlements);}public void assertBalanced(){var totals=new LinkedHashMap<String,BigDecimal>();for(var p:positions.values())p.forEach((c,a)->totals.merge(c,a,BigDecimal::add));for(var e:totals.entrySet())if(e.getValue().signum()!=0)throw new AssertionError("interbank positions unbalanced: "+e.getKey()+"="+e.getValue());}
}
