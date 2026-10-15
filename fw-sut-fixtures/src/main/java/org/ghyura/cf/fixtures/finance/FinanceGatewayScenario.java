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

import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;

/** Bounded source security/gateway workflows;output contains observations,never expected answers. */
public final class FinanceGatewayScenario {
    private FinanceGatewayScenario() { }
    public static Map<String,Object> run(Map<?,?> request) {
        if(!request.keySet().equals(Set.of("scenario"))||!"source_gateway_security".equals(request.get("scenario")))
            throw new IllegalArgumentException("only source_gateway_security profile supported");
        var signatures=new ArrayList<Map<String,Object>>();
        var payloads=List.of(fields(),fields("z",1,"a",true),fields("nested",Arrays.asList(null,false,"quotes\"\n\\",-0.0,1e-7)),
            fields("\ue000","private","\ud800\udc00","astral","Ж","Київ"),fields("text","not a content carrier","a",fields("z",2,"a",1)),fields("x",1e20,"n",-123,"spaces"," : , \t "));
        for(String secret:List.of("test-secret","ключ"))for(String method:List.of("get","POST","patch"))for(String path:List.of("/v1/transfers","/toBank7/v1/time-config"))for(var payload:payloads) {
            byte[] bytes=FinanceSecurity.canonicalBytes(payload);String ts="1000",nonce="same-λ";
            signatures.add(fields("secret",secret,"method",method,"path",path,"body_utf8",new String(bytes,StandardCharsets.UTF_8),"timestamp",ts,"nonce",nonce,"digest",FinanceSecurity.digest(bytes),"signature",FinanceSecurity.sign(secret,method,path,bytes,ts,nonce)));
        }
        String[][] script={{"missing-precedes-everything",null,"n","wrong"},{"invalid-precedes-signature","bad","n","wrong"},
            {"past-outside","699","n","wrong"},{"future-outside","1301","n","wrong"},{"inclusive-past","700","n1","valid"},{"inclusive-future","1300","n2","valid"},
            {"bad-does-not-consume","1000","n3","wrong"},{"after-bad-consumes","1000","n3","valid"},{"same-nonce-replayed","1000","n3","valid"},{"bad-precedes-replay","1000","n3","wrong"},
            {"uppercase-bad","1000","n4","uppercase-valid"},{"signed-spaces-accepted"," +1_000 ","n4","valid"},{"signed-unicode-digits","١٠٠٠","n5","valid"}};
        var verification=new ArrayList<Map<String,Object>>();var seen=new LinkedHashSet<String>();byte[] bytes="{}".getBytes(StandardCharsets.UTF_8);
        for(var row:script) {
            String signature=row[3].equals("wrong")?"wrong":FinanceSecurity.sign("test-secret","POST","/v1/transfers",bytes,row[1],row[2]);
            if(row[3].equals("uppercase-valid"))signature=signature.toUpperCase(Locale.ROOT);
            var value=FinanceSecurity.verify("test-secret","POST","/v1/transfers",bytes,row[1],row[2],signature,300,seen,()->1000);
            verification.add(fields("id",row[0],"ok",value.ok(),"reason",value.reason()));
        }
        var reviews=new ArrayList<Map<String,Object>>();
        for(Object approve:Arrays.asList(null,false,0,"",List.of(),Map.of(),"false",true,1,List.of("false"),Map.of("x",false))) {
            var eco=FinanceSimulation.deterministic();var gateway=new FinanceGateway(eco,"test-secret","",()->1000);var nonce=new int[]{0};
            var a=eco.identity().register("A","a@x.test",FinanceIdentity.Kyc.VERIFIED,Set.of("CUSTOMER"),"RETAIL",false);
            var b=eco.identity().register("B","b@x.test",FinanceIdentity.Kyc.VERIFIED,Set.of("CUSTOMER"),"RETAIL",false);
            String source=eco.open("AURUM",a.id(),"USD","5000").id(),target=eco.open("NORD",b.id(),"USD","0").id();
            var created=(Map<?,?>)call(gateway,"POST","/v1/transfers",fields("source_account_id",source,"target_account_id",target,"amount","2500","idempotency_key","review"),nonce).body();
            String reference=(String)((Map<?,?>)created.get("transfer")).get("reference");var tokens=(Map<?,?>)created.get("confirmation_tokens");
            for(String party:List.of("SENDER","RECEIVER"))call(gateway,"POST","/v1/transfers/"+reference+"/confirm",fields("party",party,"token",tokens.get(party)),nonce);
            var reviewed=(Map<?,?>)call(gateway,"POST","/v1/transfers/"+reference+"/review",fields("approve",approve),nonce).body();
            reviews.add(fields("approve",approve,"status",reviewed.get("status"),"source",eco.balance(source).get("balance"),"target",eco.balance(target).get("balance")));
        }
        var eco=FinanceSimulation.deterministic();var gateway=new FinanceGateway(eco,"test-secret","",()->1000);var nonce=new int[]{0};
        var missing=gateway.dispatch(new FinanceGateway.Request("GET","/v1/time-config",new byte[0],Map.of(),Map.of()));
        var bad=gateway.dispatch(new FinanceGateway.Request("GET","/v1/time-config",new byte[0],Map.of("x-timestamp","1000","x-nonce","bad","x-signature","wrong"),Map.of()));
        var malformed=signed("POST","/v1/clients","{bad".getBytes(StandardCharsets.UTF_8),"malformed");var json=gateway.dispatch(malformed);var replay=gateway.dispatch(malformed);
        var field=call(gateway,"POST","/v1/banks/AURUM/accounts",Map.of(),nonce);var errors=new ArrayList<Map<String,Object>>();
        for(var error:List.of(missing,bad,replay,json,field)){var value=(Map<?,?>)error.body();errors.add(fields("status",error.status(),"code",value.get("code"),"message",value.get("message")));}
        return fields("signatures",signatures,"verification",verification,"reviews",reviews,"errors",errors);
    }
    private static FinanceGateway.Request signed(String method,String path,byte[] body,String nonce) {
        String signature=FinanceSecurity.sign("test-secret",method,path,body,"1000",nonce);
        return new FinanceGateway.Request(method,path,body,Map.of("x-timestamp","1000","x-nonce",nonce,"x-signature",signature),Map.of());
    }
    private static FinanceGateway.Response call(FinanceGateway gateway,String method,String path,Object body,int[] nonce) {
        return gateway.dispatch(signed(method,path,FinanceSecurity.canonicalBytes(body),"nonce-"+(++nonce[0])));
    }
}
