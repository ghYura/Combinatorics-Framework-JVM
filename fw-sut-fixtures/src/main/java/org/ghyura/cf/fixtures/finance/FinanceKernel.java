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
import java.time.*;
import java.util.*;
import java.util.function.*;

/** Bounded local money, typed service errors and synchronous audit events. */
public final class FinanceKernel {
 private FinanceKernel(){}
 public static final int MAX_CLIENTS=256, MAX_EVENTS=4096;
 public static final MathContext DECIMAL=new MathContext(28,RoundingMode.HALF_EVEN);
 public static LinkedHashMap<String,Object> fields(Object... pairs){var m=new LinkedHashMap<String,Object>();if(pairs.length%2!=0)throw new IllegalArgumentException("field pairs required");for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
 public static String text(String s){Objects.requireNonNull(s);if(s.length()>2048)throw new IllegalArgumentException("finance text exceeds2048 UTF16 units");for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException("invalid Unicode");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("invalid Unicode");}return s;}
 public static String strip(String s){text(s);int a=0,b=s.length();while(a<b&&space(s.codePointAt(a)))a+=Character.charCount(s.codePointAt(a));while(b>a&&space(s.codePointBefore(b)))b-=Character.charCount(s.codePointBefore(b));return s.substring(a,b);}
 private static boolean space(int c){return Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0x85;}
 public static class ServiceError extends RuntimeException {
  private static final long serialVersionUID=1L;
  private final String code;private final int status;private final transient Map<String,Object> details;
  public ServiceError(String code,int status,String message,Map<String,Object> details){super(message);this.code=code;this.status=status;this.details=Collections.unmodifiableMap(new LinkedHashMap<>(details));}
  public String code(){return code;}public int status(){return status;}
  public Map<String,Object> view(){var m=fields("code",code,"message",getMessage());if(!details.isEmpty())m.put("details",details);return m;}
 }
 public static ServiceError validation(String message,Object...details){return new ServiceError("VALIDATION_ERROR",400,message,fields(details));}
 public static ServiceError notFound(String message,Object...details){return new ServiceError("NOT_FOUND",404,message,fields(details));}
 public record Money(BigDecimal amount,String currency,boolean negativeZero) implements Comparable<Money>{
  public Money{Objects.requireNonNull(amount);currency=normalizeCurrency(currency);if(amount.scale()!=2||amount.abs().compareTo(new BigDecimal("1000000000000.00"))>0)throw new IllegalArgumentException("money exceeds bounded two-decimal domain");negativeZero=negativeZero&&amount.signum()==0;}
  public static Money of(Object value,String currency){String v;if(value instanceof String s)v=text(s);else if(value instanceof Long||value instanceof Integer||value instanceof BigDecimal)v=value.toString();else throw validation("amount is not a valid money amount","field","amount");if(v.length()>128)throw new IllegalArgumentException("money literal exceeds128 units");BigDecimal a;try{a=new BigDecimal(strip(v).replace("_",""));if(Math.abs((long)a.scale())>256||a.abs().compareTo(new BigDecimal("1000000000001"))>0)throw new IllegalArgumentException("money magnitude or decimal exponent outside admission");a=a.setScale(2,RoundingMode.HALF_UP);}catch(NumberFormatException|ArithmeticException e){throw validation("amount is not a valid money amount","field","amount");}return new Money(a,currency,strip(v).replace("_","").startsWith("-")&&a.signum()==0);}
  public static Money zero(String currency){return new Money(BigDecimal.ZERO.setScale(2),currency,false);}
  @Override public boolean equals(Object other){return other instanceof Money m&&currency.equals(m.currency)&&amount.compareTo(m.amount)==0;}
  @Override public int hashCode(){return Objects.hash(amount.stripTrailingZeros(),currency);}
  public String asString(){return(negativeZero?"-":"")+amount.toPlainString();}
  private void same(Money m){if(!currency.equals(m.currency))throw validation("currency mismatch: "+currency+" vs "+m.currency,"field","currency");}
  public Money add(Money m){same(m);return new Money(amount.add(m.amount).setScale(2),currency,negativeZero&&m.negativeZero);}
  public Money subtract(Money m){same(m);return new Money(amount.subtract(m.amount).setScale(2),currency,negativeZero&&m.amount.signum()==0&&!m.negativeZero);}
  public Money negate(){return new Money(amount.negate(),currency,false);}
  public boolean positive(){return amount.signum()>0;}public boolean zero(){return amount.signum()==0;}
  @Override public int compareTo(Money m){same(m);return amount.compareTo(m.amount);}
  @Override public String toString(){return asString()+" "+currency;}
 }
 public static String normalizeCurrency(String value){String code=strip(value==null?"":value).toUpperCase(Locale.ROOT);if(code.codePointCount(0,code.length())!=3||!code.codePoints().allMatch(Character::isLetter))throw validation("currency must be a 3-letter ISO code","field","currency");return code;}
 public record Event(long seq,String type,String source,Map<String,Object> data,Instant timestamp){public Map<String,Object> view(){return fields("seq",seq,"type",type,"source",source,"data",new LinkedHashMap<>(data));}}
 public static final class Bus {
  private final Map<String,List<Consumer<Event>>> handlers=new LinkedHashMap<>();private final List<Consumer<Event>> wildcard=new ArrayList<>();private final Clock clock;private long seq;private int listeners;
  public Bus(){this(Clock.systemUTC());}public Bus(Clock clock){this.clock=Objects.requireNonNull(clock);}
  public void subscribe(String type,Consumer<Event> handler){text(type);Objects.requireNonNull(handler);if(listeners>=64)throw new IllegalArgumentException("event subscriber bound exceeded");listeners++;if(type.equals("*"))wildcard.add(handler);else handlers.computeIfAbsent(type,k->new ArrayList<>()).add(handler);}
  public Instant now(){return clock.instant();}
  public Event publish(String type,String source,Object...data){if(data.length>32)throw new IllegalArgumentException("event field bound exceeded");if(seq>=MAX_EVENTS)throw new IllegalArgumentException("event bound exceeded");var event=new Event(++seq,text(type),text(source),fields(data),clock.instant());var targets=new ArrayList<>(handlers.getOrDefault(type,List.of()));targets.addAll(wildcard);for(var handler:targets)handler.accept(event);return event;}
 }
 public static final class Audit {
  private final List<Event> records=new ArrayList<>();public Audit(Bus bus){bus.subscribe("*",records::add);}public List<Event> records(){return List.copyOf(records);}public long lastSeq(){return records.isEmpty()?0:records.getLast().seq();}public List<Event> ofType(String type){return records.stream().filter(e->e.type().equals(type)).toList();}
  public String render(long since){var out=new ArrayList<String>();for(var e:records){if(e.seq()<=since)continue;var d=new ArrayList<String>();for(var a:e.data().entrySet())d.add(a.getKey()+"="+pythonScalar(a.getValue()));out.add(String.format(Locale.ROOT,"  #%03d [%-9s] %-28s %s",e.seq(),e.source(),e.type(),String.join("  ",d)));}return String.join("\n",out);}
  private static String pythonScalar(Object v){if(v==null)return"None";if(v instanceof Boolean b)return b?"True":"False";return v.toString();}
 }
 public static Function<String,String> randomIds(){return prefix->prefix+"_"+UUID.randomUUID().toString().replace("-","").substring(0,16);}
}
