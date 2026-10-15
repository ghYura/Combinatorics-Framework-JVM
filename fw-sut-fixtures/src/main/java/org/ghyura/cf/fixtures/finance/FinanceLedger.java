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
import java.time.format.DateTimeFormatter;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
/** Source ledger and card semantics, within bounded local state and injected randomness. */
public final class FinanceLedger {
 public enum AccountStatus {ACTIVE,FROZEN,CLOSED}
 public enum CardStatus {ACTIVE,BLOCKED,EXPIRED,CLOSED}
 public enum Brand {VISA,MASTERCARD,AMEX}
 public static final class Account {
  private final String id,bank,client,currency;private AccountStatus status=AccountStatus.ACTIVE;private Money balance,hold;private final Instant createdAt;private Instant updatedAt;
  private Account(String id,String bank,String client,String currency,Instant created,Instant updated){this.id=id;this.bank=bank;this.client=client;this.currency=currency;balance=Money.zero(currency);hold=Money.zero(currency);createdAt=created;updatedAt=updated;}
  public String id(){return id;}public String client(){return client;}public String currency(){return currency;}public AccountStatus status(){return status;}public Money balance(){return balance;}public Money hold(){return hold;}public Money available(){return balance.subtract(hold);}public Instant createdAt(){return createdAt;}public Instant updatedAt(){return updatedAt;}
  public Map<String,Object> view(){return fields("account_id",id,"bank_code",bank,"client_id",client,"currency",currency,"status",status.name(),"balance",balance.asString(),"hold",hold.asString(),"available",available().asString());}
 }
 public static final class Card {
  private final String id,account,client,bank,last4,token,expiry;private final Brand brand;private CardStatus status=CardStatus.ACTIVE;private Money limit;private final Instant createdAt;private Instant updatedAt;
  private Card(String id,Account account,Brand brand,String last4,String token,String expiry,Money limit,Instant created,Instant updated){this.id=id;this.account=account.id;client=account.client;bank=account.bank;this.brand=brand;this.last4=last4;this.token=token;this.expiry=expiry;this.limit=limit;createdAt=created;updatedAt=updated;}
  public String id(){return id;}public String account(){return account;}public String last4(){return last4;}public String panToken(){return token;}public CardStatus status(){return status;}public Money spendingLimit(){return limit;}public Instant createdAt(){return createdAt;}public Instant updatedAt(){return updatedAt;}
  public Map<String,Object> view(){return fields("card_id",id,"account_id",account,"client_id",client,"bank_code",bank,"brand",brand.name(),"pan_masked",brand.name()+" •••• "+last4,"expiry",expiry,"status",status.name(),"spending_limit",limit==null?null:limit.asString());}
 }
 public record Entry(String id,String reference,String account,String direction,Money amount,String memo,Instant createdAt){public Map<String,Object> view(){return fields("entry_id",id,"reference",reference,"account",account,"direction",direction,"amount",amount.asString(),"currency",amount.currency(),"memo",memo,"created_at",iso(createdAt));}}
 public static String iso(Instant instant){var utc=instant.atOffset(ZoneOffset.UTC);int micro=instant.getNano()/1000;return DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss",Locale.ROOT).format(utc)+(micro==0?"":String.format(Locale.ROOT,".%06d",micro))+"+00:00";}
 private static final Pattern EXPIRY=Pattern.compile("\\A\\p{Nd}{4}-(0[1-9]|1[0-2])(?:\\n)?\\z");
 private final String bank,name;private final Bus bus;private final Function<String,String> ids;private final IntSupplier digits;private final Map<String,Account> accounts=new LinkedHashMap<>();private final Map<String,Card> cards=new LinkedHashMap<>();private final List<Entry> entries=new ArrayList<>();
 public FinanceLedger(String bank,Bus bus){this(bank,bus,randomIds(),randomDigits());}
 public FinanceLedger(String bank,Bus bus,Function<String,String> ids,IntSupplier digits){this.bank=text(bank);name="ledger:"+bank;this.bus=Objects.requireNonNull(bus);this.ids=Objects.requireNonNull(ids);this.digits=Objects.requireNonNull(digits);}
 private static IntSupplier randomDigits(){var random=new Random();return()->random.nextInt(10);}
 private String id(String prefix){String id=Objects.requireNonNull(ids.apply(prefix));if(!id.matches(prefix+"_[0-9a-f]{16}"))throw new IllegalArgumentException("invalid injected ledger ID");return id;}
 public String bank(){return bank;}public String nostro(){return "NOSTRO:"+bank;}
 public Account open(String client,String currency,Object initial){text(client);String normalized=normalizeCurrency(currency);if(accounts.size()>=256)throw new IllegalArgumentException("account bound exceeded");var a=new Account(id("acc"),bank,client,normalized,bus.now(),bus.now());if(accounts.putIfAbsent(a.id,a)!=null)throw new IllegalArgumentException("duplicate injected account ID");bus.publish("account.opened",name,"account_id",a.id,"client_id",client,"bank",bank);Money deposit=Money.of(initial,currency);if(deposit.positive())fund(a.id,deposit,"open:"+a.id);return a;}
 public Account get(String id){var a=accounts.get(id);if(a==null)throw notFound("account not found","account_id",id);return a;}public Account tryAccount(String id){return accounts.get(id);}public List<Account> accounts(String client){return accounts.values().stream().filter(a->client==null||a.client.equals(client)).toList();}
 public Account status(String id,AccountStatus status){var a=get(id);a.status=Objects.requireNonNull(status);a.updatedAt=bus.now();bus.publish("account.status_changed",name,"account_id",id,"status",status.name());return a;}
 public Account close(String id){var a=get(id);if(!a.balance.zero()||!a.hold.zero())throw new ServiceError("INVALID_STATE",409,"cannot close an account with non-zero balance or holds",fields("account_id",id));return status(id,AccountStatus.CLOSED);}
 public Map<String,Object> balance(String id){var a=get(id);return fields("account_id",id,"currency",a.currency,"balance",a.balance.asString(),"hold",a.hold.asString(),"available",a.available().asString());}
 private void active(Account a){if(a.status!=AccountStatus.ACTIVE)throw new ServiceError("INVALID_STATE",409,"account is "+a.status.name(),fields("account_id",a.id));}
 private void currency(Account a,Money money){if(!a.currency.equals(money.currency()))throw validation("currency mismatch: account "+a.currency+" vs "+money.currency(),"field","currency");}
 public Card issue(String accountId,Brand brand,Object spendingLimit,String expiry){var a=get(accountId);active(a);text(expiry);if(!EXPIRY.matcher(expiry).matches())throw validation("expiry must be in YYYY-MM format (month 01-12)","field","expiry");if(cards.size()>=256)throw new IllegalArgumentException("card bound exceeded");var pan=new StringBuilder(switch(Objects.requireNonNull(brand)){case VISA->"4";case MASTERCARD->"5";case AMEX->"3";});for(int i=0;i<15;i++){int d=digits.getAsInt();if(d<0||d>9)throw new IllegalArgumentException("injected PAN digit outside0..9");pan.append(d);}String number=pan.toString(),id=id("crd"),token;try{token=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(number.getBytes(StandardCharsets.UTF_8))).substring(0,16);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}Money limit=spendingLimit==null?null:Money.of(spendingLimit,a.currency);var c=new Card(id,a,brand,number.substring(number.length()-4),token,expiry,limit,bus.now(),bus.now());if(cards.putIfAbsent(id,c)!=null)throw new IllegalArgumentException("duplicate injected card ID");bus.publish("card.issued",name,"card_id",id,"account_id",a.id,"brand",brand.name());return c;}
 public Card card(String id){var c=cards.get(id);if(c==null)throw notFound("card not found","card_id",id);return c;}public List<Card> cards(String accountId){return cards.values().stream().filter(c->c.account.equals(accountId)).toList();}
 public Card updateCard(String id,CardStatus status,Object spendingLimit){var c=card(id);if(c.status==CardStatus.CLOSED)throw new ServiceError("INVALID_STATE",409,"card is closed",fields("card_id",id));if(status!=null)c.status=status;if(spendingLimit!=null)c.limit=Money.of(spendingLimit,get(c.account).currency);c.updatedAt=bus.now();bus.publish("card.updated",name,"card_id",id,"status",c.status.name());return c;}
 public Card deleteCard(String id){var c=card(id);c.status=CardStatus.CLOSED;c.updatedAt=bus.now();bus.publish("card.closed",name,"card_id",id);return c;}
 private void pair(String reference,String debit,String credit,Money money,String memo){if(entries.size()>4094)throw new IllegalArgumentException("journal entry bound exceeded");entries.add(new Entry(id("led"),text(reference),debit,"DEBIT",money,memo,bus.now()));entries.add(new Entry(id("led"),reference,credit,"CREDIT",money,memo,bus.now()));}
 public Account fund(String id,Object amount,String reference){var a=get(id);active(a);var money=amount instanceof Money m?m:Money.of(amount,a.currency);currency(a,money);a.balance=a.balance.add(money);a.updatedAt=bus.now();pair(reference,"EXTERNAL",id,money,"external funding");bus.publish("ledger.funded",name,"account_id",id,"amount",money.toString(),"reference",reference);return a;}
 public void hold(String id,Money money,String reference){var a=get(id);active(a);currency(a,money);if(a.available().compareTo(money)<0)throw new ServiceError("INSUFFICIENT_FUNDS",422,"insufficient available balance",fields("account_id",id,"available",a.available().asString(),"requested",money.asString()));a.hold=a.hold.add(money);a.updatedAt=bus.now();bus.publish("ledger.hold_placed",name,"account_id",id,"amount",money.toString(),"reference",text(reference));}
 public void release(String id,Money money,String reference){var a=get(id);var remaining=a.hold.subtract(money);a.hold=remaining.positive()?remaining:Money.zero(a.currency);a.updatedAt=bus.now();bus.publish("ledger.hold_released",name,"account_id",id,"amount",money.toString(),"reference",text(reference));}
 public void internal(String sourceId,String targetId,Money money,String reference){var source=get(sourceId);var target=get(targetId);currency(source,money);currency(target,money);if(target.status==AccountStatus.CLOSED)throw new ServiceError("INVALID_STATE",409,"target account is closed",fields("account_id",targetId));release(sourceId,money,reference);source.balance=source.balance.subtract(money);target.balance=target.balance.add(money);pair(reference,sourceId,targetId,money,"internal transfer");bus.publish("ledger.posted",name,"reference",reference,"debit",sourceId,"credit",targetId,"amount",money.toString());}
 public void outbound(String sourceId,Money money,String reference){var source=get(sourceId);currency(source,money);release(sourceId,money,reference);source.balance=source.balance.subtract(money);pair(reference,sourceId,nostro(),money,"outbound interbank");bus.publish("ledger.posted",name,"reference",reference,"debit",sourceId,"credit",nostro(),"amount",money.toString());}
 public void inbound(String targetId,Money money,String reference){var target=get(targetId);currency(target,money);if(target.status==AccountStatus.CLOSED)throw new ServiceError("INVALID_STATE",409,"beneficiary account is closed",fields("account_id",targetId));target.balance=target.balance.add(money);pair(reference,nostro(),targetId,money,"inbound interbank");bus.publish("ledger.posted",name,"reference",reference,"debit",nostro(),"credit",targetId,"amount",money.toString());}
 public List<Entry> entries(){return List.copyOf(entries);}private Map<String,Double> totals(String direction){var result=new LinkedHashMap<String,Double>();for(var e:entries)if(e.direction().equals(direction))result.merge(e.amount().currency(),e.amount().amount().doubleValue(),Double::sum);return result;}
 public Map<String,Double> debitTotals(){return totals("DEBIT");}public Map<String,Double> creditTotals(){return totals("CREDIT");}public void assertBalanced(){if(!debitTotals().equals(creditTotals()))throw new AssertionError("ledger "+bank+" unbalanced: debits="+debitTotals()+" credits="+creditTotals());}
}
