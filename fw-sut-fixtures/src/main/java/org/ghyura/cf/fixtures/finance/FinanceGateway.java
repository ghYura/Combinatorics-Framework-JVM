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

import org.ghyura.cf.model.Json;
import org.ghyura.cf.fixtures.assistant.LocalContent;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.DoubleSupplier;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;

/** Source gateway routes over local immutable request bytes;HTTP transport is outside this adapter. */
public final class FinanceGateway {
    public record Request(String method,String path,byte[] body,Map<String,String> headers,Map<String,String> query) {
        public Request {
            method=text(method).toUpperCase(Locale.ROOT);path=text(path);Objects.requireNonNull(body);
            if(body.length>FinanceSecurity.MAX_BYTES||headers.size()>16||query.size()>16)throw new IllegalArgumentException("gateway request bounds exceeded");
            body=body.clone();var normalized=new LinkedHashMap<String,String>();
            // Names differing only in case would otherwise keep whichever the caller's map iterates last.
            headers.forEach((k,v)->{if(normalized.put(text(k).toLowerCase(Locale.ROOT),text(v))!=null)throw new IllegalArgumentException("gateway header repeated ignoring case");});
            headers=Map.copyOf(normalized);query=Map.copyOf(query);query.forEach((k,v)->{text(k);text(v);});
            if(!path.startsWith("/")||path.contains("?"))throw new IllegalArgumentException("gateway path and query must be separate");
        }
        @Override public byte[] body() { return body.clone(); }
    }
    public record Response(int status,Object body) { }
    private static final class Missing extends RuntimeException {
        private static final long serialVersionUID=1L;Missing(String field){super(field);}
    }
    private final FinanceEcosystem eco;private final String secret,mount;private final DoubleSupplier seconds;
    private final Set<String> seen=new LinkedHashSet<>();
    public FinanceGateway(FinanceEcosystem eco,String secret) { this(eco,secret,"",()->System.currentTimeMillis()/1000.0); }
    public FinanceGateway(FinanceEcosystem eco,String secret,String mount,DoubleSupplier seconds) {
        this.eco=Objects.requireNonNull(eco);this.secret=text(secret==null||secret.isEmpty()?"change-me":secret);this.mount=text(mount);this.seconds=Objects.requireNonNull(seconds);
        if(!mount.isEmpty()&&(!mount.startsWith("/")||mount.endsWith("/")))throw new IllegalArgumentException("empty or leading-slash mount required");
    }
    public int seenNonceCount() { return seen.size(); }
    private record Route(String kind,String allowed,String id) { }
    private Route route(String path) {
        if(!mount.isEmpty()){if(!path.startsWith(mount+"/"))return null;path=path.substring(mount.length());}
        if(path.equals("/health"))return new Route("health","GET","");
        String[] p=path.substring(1).split("/",-1);if(p.length<2||!p[0].equals("v1"))return null;
        if(Arrays.stream(p).anyMatch(String::isEmpty))return null;
        if(p.length==2)return switch(p[1]) {
            case "clients"->new Route("clients","POST","");case "transfers"->new Route("transfers","POST","");
            case "audit"->new Route("audit","GET","");case "time-config"->new Route("time","GET,PUT","");default->null;
        };
        if(p.length==3&&p[1].equals("clients"))return new Route("client","GET",p[2]);
        if(p.length==3&&p[1].equals("cards"))return new Route("card","GET,PATCH,DELETE",p[2]);
        if(p.length==3&&p[1].equals("transfers"))return new Route("transfer","GET",p[2]);
        if(p.length==3&&p[1].equals("network"))return switch(p[2]) {case "banks"->new Route("banks","GET","");case "positions"->new Route("positions","GET","");default->null;};
        if(p.length==4&&p[1].equals("clients")&&p[3].equals("kyc"))return new Route("kyc","PATCH",p[2]);
        if(p.length==4&&p[1].equals("banks")&&p[3].equals("accounts"))return new Route("open","POST",p[2]);
        if(p.length==4&&p[1].equals("accounts"))return switch(p[3]) {case "balance"->new Route("balance","GET",p[2]);case "cards"->new Route("account-cards","GET,POST",p[2]);default->null;};
        if(p.length==4&&p[1].equals("transfers"))return switch(p[3]) {case "confirm"->new Route("confirm","POST",p[2]);case "review"->new Route("review","POST",p[2]);default->null;};
        return null;
    }
    public Response dispatch(Request request) {
        var route=route(request.path());if(route==null)return new Response(404,fields("detail","Not Found"));
        if(!Arrays.asList(route.allowed().split(",")).contains(request.method()))return new Response(405,fields("detail","Method Not Allowed"));
        if(route.kind().equals("health"))return new Response(200,fields("ok",true,"service","gateway","banks",eco.banks().stream().map(FinanceBank::code).toList()));
        try {
            byte[] bytes=request.body();var verified=FinanceSecurity.verify(secret,request.method(),request.path(),bytes,
                    request.headers().get("x-timestamp"),request.headers().get("x-nonce"),request.headers().get("x-signature"),eco.time().seconds("replay_window_seconds"),seen,seconds);
            if(!verified.ok())throw new ServiceError("UNAUTHORIZED",401,verified.reason(),Map.of());
            Object parsed=Map.of();if(bytes.length>0) {
                String raw;try{raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
                catch(CharacterCodingException e){throw new IllegalArgumentException("gateway requires valid UTF8 body",e);}
                try{parsed=sourceJson(raw);}catch(InvalidJson e){throw validation("request body is not valid JSON");}
            }
            return new Response(200,handle(route,request,parsed));
        } catch(ServiceError e){return new Response(e.status(),e.view());}
        catch(Missing e){return new Response(400,fields("code","VALIDATION_ERROR","message","missing required field: "+e.getMessage()));}
    }
    private static Map<?,?> object(Object value) { if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("object gateway carrier required for this source route");return map; }
    private static Object required(Map<?,?> body,String key) { if(!body.containsKey(key))throw new Missing(key);return body.get(key); }
    private static Object optional(Map<?,?> body,String key,Object fallback) { return body.containsKey(key)?body.get(key):fallback; }
    private static String string(Object value) { if(!(value instanceof String s))throw new IllegalArgumentException("source string carrier required");return text(s); }
    private static Object money(Object value) { return value instanceof Double?LocalContent.text(value):value; }
    public static boolean truth(Object value) {
        if(value==null)return false;if(value instanceof Boolean b)return b;if(value instanceof Number n)return n.doubleValue()!=0;
        if(value instanceof String s)return !s.isEmpty();if(value instanceof List<?> l)return !l.isEmpty();if(value instanceof Map<?,?> m)return !m.isEmpty();
        throw new IllegalArgumentException("finite JSON truthiness carrier required");
    }
    private static <T extends Enum<T>> T enumeration(Class<T> type,Object value,String field) {
        var allowed=Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        if(value instanceof String s)for(var e:type.getEnumConstants())if(e.name().equals(s))return e;
        throw validation(field+" must be one of ['"+String.join("', '",allowed)+"']","field",field,"allowed",allowed);
    }
    private FinanceBank cardBank(String id) {
        for(var bank:eco.banks())try{bank.ledger().card(id);return bank;}catch(ServiceError e){if(!e.code().equals("NOT_FOUND"))throw e;}
        throw notFound("card not found","card_id",id);
    }
    private static long since(String value) {
        String clean=strip(value);if(!clean.matches("[+-]?[0-9]+(?:_[0-9]+)*"))throw new IllegalArgumentException("signed64 ASCII decimal since required");
        return Long.parseLong(clean.replace("_",""));
    }
    /** Source set membership:an unhashable list/object raises TypeError,and other non-string scalars are never remote codes. */
    private boolean remote(Object bank) {
        if(bank instanceof List<?>||bank instanceof Map<?,?>)throw new IllegalArgumentException("unhashable target_bank raises in source membership");
        return bank instanceof String s&&eco.remote(text(s));
    }
    /** Where the source's json.loads raises JSONDecodeError. */
    static final class InvalidJson extends RuntimeException {
        private static final long serialVersionUID=1L;
        InvalidJson() { super("source JSONDecodeError",null,false,false); }
    }
    /** Parses as the source's json.loads decides validity:JSON it parses but this parser does not admit is refused,never reported invalid. */
    static Object sourceJson(String raw) {
        if(SourceJson.verdict(raw)==0)throw new InvalidJson();
        try{return Json.parseBinary64(raw,"local authenticated gateway");}
        catch(RuntimeException e){throw new IllegalArgumentException("source JSON outside gateway admission:unique keys,signed64 integers,finite binary64,256 levels",e);}
    }
    /** Python json.loads grammar:ASCII numbers of any width,NaN/Infinity,repeated keys and strict strings. */
    private static final class SourceJson {
        private final String s;private int i;private boolean deep;
        private SourceJson(String s) { this.s=s; }
        /** 1 when the source parses the text,0 when it raises JSONDecodeError,-1 past the 256 levels Json admits. */
        static int verdict(String s) {
            if(s.startsWith("﻿"))return 0;
            var p=new SourceJson(s);boolean ok=p.value(0)&&p.space()==s.length();return p.deep?-1:ok?1:0;
        }
        private int space() { while(i<s.length()&&" \t\n\r".indexOf(s.charAt(i))>=0)i++;return i; }
        private boolean at(char c) { return i<s.length()&&s.charAt(i)==c; }
        private boolean digit(int k) { return k<s.length()&&s.charAt(k)>='0'&&s.charAt(k)<='9'; }
        private boolean word(String w) { if(!s.startsWith(w,i))return false;i+=w.length();return true; }
        private boolean value(int depth) {
            if(depth>256){deep=true;return false;}
            space();if(i>=s.length())return false;
            return switch(s.charAt(i)) {
                case '{'->members(depth,'}',true);case '['->members(depth,']',false);case '"'->string();
                case 'n'->word("null");case 't'->word("true");case 'f'->word("false");case 'N'->word("NaN");case 'I'->word("Infinity");
                default->word("-Infinity")||number();
            };
        }
        private boolean members(int depth,char close,boolean object) {
            i++;space();if(at(close)){i++;return true;}
            while(true) {
                if(object){space();if(!at('"')||!string())return false;space();if(!at(':'))return false;i++;}
                if(!value(depth+1))return false;space();
                if(at(close)){i++;return true;}if(!at(','))return false;i++;
            }
        }
        private boolean string() {
            i++;
            while(i<s.length()) {
                char c=s.charAt(i++);if(c=='"')return true;if(c<0x20)return false;if(c!='\\')continue;
                if(i>=s.length())return false;char e=s.charAt(i++);
                if(e=='u'){if(i+4>s.length())return false;for(int k=i;k<i+4;k++)if("0123456789abcdefABCDEF".indexOf(s.charAt(k))<0)return false;i+=4;}
                else if("\"\\/bfnrt".indexOf(e)<0)return false;
            }
            return false;
        }
        /** Python's scanner ends a number before a '.' or exponent that has no digits. */
        private boolean number() {
            if(at('-'))i++;
            if(at('0'))i++;else if(digit(i)){while(digit(i))i++;}else return false;
            if(at('.')&&digit(i+1)){i++;while(digit(i))i++;}
            if(at('e')||at('E')){int e=i++;if(at('+')||at('-'))i++;if(digit(i)){while(digit(i))i++;}else i=e;}
            return true;
        }
    }
    private Object handle(Route route,Request request,Object parsed) {
        String id=route.id();switch(route.kind()) {
            case "client":return eco.identity().get(id).view();case "balance":return eco.balance(id);
            case "card":if(request.method().equals("GET"))return cardBank(id).ledger().card(id).view();else if(request.method().equals("DELETE"))return cardBank(id).ledger().deleteCard(id).view();break;
            case "account-cards":if(request.method().equals("GET"))return fields("cards",eco.accountBank(id).ledger().cards(id).stream().map(FinanceLedger.Card::view).toList());break;
            case "transfer":return eco.transfer(id).view();case "banks":return fields("banks",eco.network().banks().stream().map(FinanceNetwork.BankInfo::view).toList());
            case "positions":return fields("positions",eco.network().positions());
            case "audit":return fields("trace",eco.audit().render(since(request.query().getOrDefault("since","0"))));
            case "time":if(request.method().equals("GET"))return eco.time().view();break;default:break;
        }
        // Source card issue/update locate the account or card before reading the body;the other handlers read the body first.
        FinanceBank located=switch(route.kind()){case "account-cards"->eco.accountBank(id);case "card"->cardBank(id);default->null;};
        Map<?,?> body=object(parsed);
        return switch(route.kind()) {
            case "clients"->eco.identity().register(string(optional(body,"display_name","")),string(optional(body,"email","")),enumeration(FinanceIdentity.Kyc.class,optional(body,"kyc_status","PENDING"),"kyc_status"),Set.of("CUSTOMER"),string(optional(body,"segment","RETAIL")),false).view();
            case "kyc"->eco.identity().setKyc(id,enumeration(FinanceIdentity.Kyc.class,required(body,"status"),"status")).view();
            case "open"->eco.open(id,string(required(body,"client_id")),string(optional(body,"currency","USD")),money(optional(body,"initial_deposit","0.00"))).view();
            case "account-cards"->located.ledger().issue(id,enumeration(FinanceLedger.Brand.class,optional(body,"brand","VISA"),"brand"),money(body.get("spending_limit")),string(optional(body,"expiry","2030-12"))).view();
            case "card"->located.ledger().updateCard(id,body.containsKey("status")?enumeration(FinanceLedger.CardStatus.class,body.get("status"),"status"):null,money(body.get("spending_limit"))).view();
            case "transfers"->{
                // The source tests target_bank before it reads any required field.
                Object bank=body.get("target_bank");boolean remote=truth(bank)&&remote(bank);
                String source=string(required(body,"source_account_id")),target=string(required(body,"target_account_id"));Object amount=money(required(body,"amount"));String key=string(required(body,"idempotency_key"));
                var t=remote?eco.initiateRemote(source,(String)bank,target,amount,key):eco.initiate(source,target,amount,key);
                var tokens=new LinkedHashMap<String,String>();t.tokens().forEach((p,token)->tokens.put(p.name(),token));yield fields("transfer",t.view(),"confirmation_tokens",tokens);
            }
            // The source Party values are SENDER/RECEIVER;A/B is only their display label.
            case "confirm"->eco.confirm(id,enumeration(FinanceClearing.Party.class,required(body,"party"),"party"),string(required(body,"token")),string(optional(body,"decision","CONFIRM"))).view();
            case "review"->eco.review(id,truth(required(body,"approve")),body.get("actor_client_id")==null?null:string(body.get("actor_client_id"))).view();
            case "time"->{
                Map<String,Object> values=null;if(body.get("values")!=null){values=new LinkedHashMap<>();for(var e:object(body.get("values")).entrySet())values.put(string(e.getKey()),e.getValue() instanceof Double?LocalContent.text(e.getValue()):e.getValue());}
                yield eco.updateTime(values,body.get("accept_sync")==null?null:truth(body.get("accept_sync")),string(optional(body,"propagate","self")),false);
            }
            default->throw new IllegalStateException("unhandled admitted source route");
        };
    }
}
