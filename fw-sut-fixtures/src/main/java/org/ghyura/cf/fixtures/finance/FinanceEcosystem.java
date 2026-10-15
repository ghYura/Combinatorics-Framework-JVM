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

import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import java.util.function.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;

/** Source single-process finance world with shared identity, routing, audit and live time units. */
public final class FinanceEcosystem {
    private final Bus bus;
    private final Audit audit;
    private final FinanceIdentity identity;
    private final FinanceNetwork network;
    private final FinanceTime time;
    private final Map<String,FinanceBank> banks=new LinkedHashMap<>();
    private final Map<String,String> accountBanks=new LinkedHashMap<>(),transferOwners=new LinkedHashMap<>();
    private final Set<String> remoteBanks=new LinkedHashSet<>();
    private final List<Runnable> timeHooks=new ArrayList<>();
    private Function<Map<String,?>,Map<String,Object>> timePropagator;
    private final DoubleSupplier seconds;
    private final Function<String,String> ids;
    private final IntSupplier digits;
    private final Supplier<String> references,tokens;
    public FinanceEcosystem() {
        this(new FinanceTime(),Clock.systemUTC(),()->System.currentTimeMillis()/1000.0,randomIds(),randomDigits(),
                ()->"TRF-"+HexFormat.of().withUpperCase().formatHex(random(4)),
                ()->Base64.getUrlEncoder().withoutPadding().encodeToString(random(18)));
    }
    public FinanceEcosystem(FinanceTime time,Clock clock,DoubleSupplier seconds,Function<String,String> ids,
                            IntSupplier digits,Supplier<String> references,Supplier<String> tokens) {
        this.time=Objects.requireNonNull(time);this.seconds=Objects.requireNonNull(seconds);this.ids=Objects.requireNonNull(ids);
        this.digits=Objects.requireNonNull(digits);this.references=Objects.requireNonNull(references);this.tokens=Objects.requireNonNull(tokens);
        bus=new Bus(clock);audit=new Audit(bus);identity=new FinanceIdentity(bus,ids);network=new FinanceNetwork(identity,bus);
    }
    private static byte[] random(int size) { var bytes=new byte[size];new SecureRandom().nextBytes(bytes);return bytes; }
    private static IntSupplier randomDigits() { var random=new Random();return()->random.nextInt(10); }
    public Bus bus() { return bus; }
    public Audit audit() { return audit; }
    public FinanceIdentity identity() { return identity; }
    public FinanceNetwork network() { return network; }
    public FinanceTime time() { return time; }
    public List<FinanceBank> banks() { return List.copyOf(banks.values()); }
    public FinanceBank addBank(String code,String name,String jurisdiction,boolean sanctioned,FinanceRisk.Config config) {
        if(!banks.containsKey(code)&&banks.size()>=16)throw new IllegalArgumentException("ecosystem bank bound exceeded");
        var bank=new FinanceBank(new FinanceNetwork.BankInfo(code,name,jurisdiction,sanctioned),identity,network,bus,
                config==null?FinanceRisk.Config.defaults():config,time,seconds,ids,digits,references,tokens);
        banks.put(code,bank);return bank;
    }
    public FinanceBank bank(String code) { var bank=banks.get(code);if(bank==null)throw notFound("bank not found","bank_code",code);return bank; }
    public FinanceNetwork.BankInfo addRemoteBank(String code,String name,FinanceSettlement.BankPort port,String jurisdiction,boolean sanctioned) {
        var info=new FinanceNetwork.BankInfo(code,name,jurisdiction,sanctioned);network.register(info,port);remoteBanks.add(code);return info;
    }
    public boolean remote(String code) { return remoteBanks.contains(code); }
    public FinanceLedger.Account open(String bankCode,String client,String currency,Object initial) {
        var bank=bank(bankCode);identity.get(client);var account=bank.open(client,currency,initial);
        accountBanks.put(account.id(),bankCode);return account;
    }
    private record Located(FinanceBank bank,FinanceLedger.Account account) { }
    private Located locate(String account) {
        var code=accountBanks.get(account);
        if(code!=null) { var bank=bank(code);return new Located(bank,bank.ledger().get(account)); }
        for(var bank:banks.values()) { var found=bank.ledger().tryAccount(account);if(found!=null)return new Located(bank,found); }
        throw notFound("account not found","account_id",account);
    }
    public Map<String,Object> balance(String account) { var found=locate(account);return found.bank().ledger().balance(account); }
    public FinanceBank accountBank(String account) { return locate(account).bank(); }
    public FinanceClearing.Transfer initiate(String source,String target,Object amount,String key) {
        var src=locate(source);var dst=locate(target);
        if(!src.account().currency().equals(dst.account().currency()))
            throw validation("cross-currency transfers are not supported in this simulator","field","currency");
        var money=Money.of(amount,src.account().currency());
        var transfer=src.bank().clearing().initiate(new FinanceClearing.Request(key,money,src.account().client(),source,src.bank().code(),
                dst.account().client(),target,dst.bank().code()));
        transferOwners.put(transfer.reference(),src.bank().code());return transfer;
    }
    public FinanceClearing.Transfer initiateRemote(String source,String targetBank,String targetAccount,Object amount,String key) {
        var src=locate(source);
        if(!network.member(targetBank))throw notFound("target bank is not known to this node","bank_code",targetBank);
        var money=Money.of(amount,src.account().currency());
        var transfer=src.bank().clearing().initiate(new FinanceClearing.Request(key,money,src.account().client(),source,src.bank().code(),
                "remote:"+targetBank+":"+targetAccount,targetAccount,targetBank));
        transferOwners.put(transfer.reference(),src.bank().code());return transfer;
    }
    private FinanceClearing coordinator(String reference) {
        String owner=transferOwners.get(reference);
        if(owner==null)throw notFound("transfer not found","reference",reference);
        return bank(owner).clearing();
    }
    public FinanceClearing.Transfer transfer(String reference) { return coordinator(reference).get(reference); }
    public FinanceClearing.Transfer confirm(String reference,FinanceClearing.Party party,String token,String decision) {
        return coordinator(reference).confirm(reference,party,token,decision);
    }
    public FinanceClearing.Transfer review(String reference,boolean approve,String actor) { return coordinator(reference).review(reference,approve,actor); }
    public List<FinanceClearing.Transfer> retryOnHold() {
        var resolved=new ArrayList<FinanceClearing.Transfer>();for(var bank:banks.values())resolved.addAll(bank.clearing().retryOnHold());return List.copyOf(resolved);
    }
    public void onTimeChange(Runnable hook) { if(timeHooks.size()>=16)throw new IllegalArgumentException("time hook bound exceeded");timeHooks.add(Objects.requireNonNull(hook)); }
    public void propagator(Function<Map<String,?>,Map<String,Object>> function) { timePropagator=function; }
    public Map<String,Object> updateTime(Map<String,?> values,Boolean acceptSync,String propagate,boolean incoming) {
        if(incoming&&!time.acceptSync()) {
            bus.publish("time_config.declined","ecosystem","reason","accept_time_sync_off");
            return fields("applied",false,"reason","sync_declined","accept_time_sync",false);
        }
        Map<String,Integer> changed=values==null||values.isEmpty()?Map.of():time.update(values);
        if(acceptSync!=null)time.acceptSync(acceptSync);
        for(int i=0;i<timeHooks.size();i++)timeHooks.get(i).run();
        bus.publish("time_config.updated","ecosystem","changed",changed.isEmpty()?"-":String.join(",",changed.keySet()),"incoming",incoming);
        var result=fields("applied",true,"changed",changed,"time_config",time.view());
        if(values!=null&&!values.isEmpty()&&"connected".equals(propagate)&&timePropagator!=null)result.put("propagated",timePropagator.apply(values));
        return result;
    }
    public void assertBalanced() { for(var bank:banks.values())bank.ledger().assertBalanced();network.assertBalanced(); }
    public static FinanceEcosystem defaults() {
        var result=new FinanceEcosystem();result.addBank("AURUM","Aurum Bank","US",false,null);result.addBank("NORD","Nord Credit","EU",false,null);return result;
    }
}
