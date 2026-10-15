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
import java.time.Instant;
import java.util.function.Function;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
/** Shared client directory, including source ordering of partial updates. */
public final class FinanceIdentity {
 public enum Kyc {PENDING,VERIFIED,REJECTED}
 public static final class Client {
  private final String id;private String name,email,segment,reason;private Kyc kyc;private boolean sanctioned,flagged;private final Set<String> roles;private Instant createdAt,updatedAt;
  private Client(String id,String name,String email,Kyc kyc,Set<String> roles,String segment,boolean sanctioned){this.id=id;this.name=name;this.email=email;this.kyc=kyc;this.roles=roles;this.segment=segment;this.sanctioned=sanctioned;}
  public Instant createdAt(){return createdAt;}public Instant updatedAt(){return updatedAt;}public String id(){return id;}public String name(){return name;}public Kyc kyc(){return kyc;}public boolean verified(){return kyc==Kyc.VERIFIED;}public boolean sanctioned(){return sanctioned;}public boolean flagged(){return flagged;}public String flagReason(){return reason;}public boolean hasRole(String role){return roles.contains(role);}
  public Map<String,Object> view(){return fields("client_id",id,"display_name",name,"email",email,"kyc_status",kyc.name(),"sanctioned",sanctioned,"flagged",flagged,"flag_reason",reason,"roles",roles.stream().sorted(FinanceIdentity::codepointOrder).toList(),"segment",segment);}
 }
 private static int codepointOrder(String a,String b){int i=0,j=0;while(i<a.length()&&j<b.length()){int x=a.codePointAt(i),y=b.codePointAt(j);if(x!=y)return Integer.compare(x,y);i+=Character.charCount(x);j+=Character.charCount(y);}return Integer.compare(a.length()-i,b.length()-j);}
 private final Bus bus;private final Function<String,String> ids;private final Map<String,Client> clients=new LinkedHashMap<>();
 public FinanceIdentity(Bus bus){this(bus,randomIds());}public FinanceIdentity(Bus bus,Function<String,String> ids){this.bus=Objects.requireNonNull(bus);this.ids=Objects.requireNonNull(ids);}
 public Client register(String name,String email,Kyc kyc,Set<String> roles,String segment,boolean sanctioned){if(strip(name).isEmpty())throw validation("display_name is required","field","display_name");text(email);if(!email.contains("@"))throw validation("email is invalid","field","email");text(segment);Objects.requireNonNull(kyc);var adopted=new TreeSet<String>();if(roles==null||roles.isEmpty())adopted.add("CUSTOMER");else {if(roles.size()>32)throw new IllegalArgumentException("role bound exceeded");roles.forEach(v->adopted.add(text(v)));}if(clients.size()>=MAX_CLIENTS)throw new IllegalArgumentException("client bound exceeded");String id=Objects.requireNonNull(ids.apply("cli"));if(!id.matches("cli_[0-9a-f]{16}")||clients.containsKey(id))throw new IllegalArgumentException("invalid or duplicate injected client ID");var c=new Client(id,strip(name),strip(email).toLowerCase(Locale.ROOT),kyc,adopted,segment,sanctioned);c.createdAt=bus.now();c.updatedAt=bus.now();clients.put(id,c);bus.publish("client.registered","identity","client_id",id,"name",c.name,"kyc",kyc.name(),"segment",segment);return c;}
 public Client get(String id){var c=clients.get(id);if(c==null)throw notFound("client not found","client_id",id);return c;}public Client tryGet(String id){return clients.get(id);}public List<Client> list(){return List.copyOf(clients.values());}
 public Client update(String id,String name,String email){var c=get(id);if(name!=null){if(strip(name).isEmpty())throw validation("display_name is required","field","display_name");c.name=strip(name);}if(email!=null){text(email);if(!email.contains("@"))throw validation("email is invalid","field","email");c.email=strip(email).toLowerCase(Locale.ROOT);}c.updatedAt=bus.now();return c;}
 public Client setKyc(String id,Kyc kyc){var c=get(id);c.kyc=Objects.requireNonNull(kyc);c.updatedAt=bus.now();bus.publish("client.kyc_changed","identity","client_id",id,"kyc",kyc.name());return c;}
 public Client flag(String id,String reason){var c=get(id);c.reason=text(reason);c.flagged=true;c.updatedAt=bus.now();bus.publish("client.flagged","identity","client_id",id,"reason",reason);return c;}
 public Client clearFlag(String id){var c=get(id);c.flagged=false;c.reason=null;c.updatedAt=bus.now();bus.publish("client.flag_cleared","identity","client_id",id);return c;}
 public Client setSanctioned(String id,boolean value,String reason){var c=get(id);if(reason!=null)text(reason);c.sanctioned=value;c.updatedAt=bus.now();bus.publish(value?"client.sanctioned":"client.unsanctioned","identity","client_id",id,"reason",reason);return c;}
 public Client grant(String id,String role){var c=get(id);text(role);if(!c.roles.contains(role)&&c.roles.size()>=32)throw new IllegalArgumentException("role bound exceeded");c.roles.add(role);c.updatedAt=bus.now();return c;}
 public void delete(String id){get(id);clients.remove(id);bus.publish("client.deleted","identity","client_id",id);}
}
