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
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class FinanceGatewayTest {
    private static final class World {
        final FinanceEcosystem eco=FinanceSimulation.deterministic();
        final FinanceGateway gateway=new FinanceGateway(eco,"test-secret","/toBank7",()->1000);int nonce;
        FinanceGateway.Request signed(String method,String path,byte[] body,String nonce) {
            path="/toBank7"+path;String signature=FinanceSecurity.sign("test-secret",method,path,body,"1000",nonce);
            return new FinanceGateway.Request(method,path,body,Map.of("X-Timestamp","1000","X-Nonce",nonce,"X-Signature",signature),Map.of());
        }
        FinanceGateway.Response call(String method,String path,Object body) { return gateway.dispatch(signed(method,path,FinanceSecurity.canonicalBytes(body),"nonce-"+(++nonce))); }
        Map<?,?> ok(String method,String path,Object body) { var response=call(method,path,body);assertEquals(200,response.status(),response.body().toString());return (Map<?,?>)response.body(); }
        String client(String name) { return (String)ok("POST","/v1/clients",fields("display_name",name,"email",name+"@x.test","kyc_status","VERIFIED")).get("client_id"); }
        String account(String bank,String client,Object amount) { return (String)ok("POST","/v1/banks/"+bank+"/accounts",fields("client_id",client,"initial_deposit",amount)).get("account_id"); }
    }
    @Test void allTwentyRoutesDriveSharedWorldCardsTransfersAuditAndLiveTime() {
        var w=new World();String a=w.client("A"),b=w.client("B");
        assertEquals(a,w.ok("GET","/v1/clients/"+a,true).get("client_id"));
        assertEquals("PENDING",w.ok("PATCH","/v1/clients/"+a+"/kyc",Map.of("status","PENDING")).get("kyc_status"));
        w.ok("PATCH","/v1/clients/"+a+"/kyc",Map.of("status","VERIFIED"));
        String source=w.account("AURUM",a,5000L),target=w.account("NORD",b,"0");
        assertEquals("5000.00",w.ok("GET","/v1/accounts/"+source+"/balance",List.of()).get("balance"));
        var card=w.ok("POST","/v1/accounts/"+source+"/cards",Map.of());String cid=(String)card.get("card_id");
        assertEquals(fields("card_id","crd_0000000000000001","account_id",source,"client_id",a,"bank_code","AURUM","brand","VISA","pan_masked","VISA •••• 1111","expiry","2030-12","status","ACTIVE","spending_limit",null),card);
        assertEquals(card,w.ok("GET","/v1/cards/"+cid,Map.of()));assertEquals(Map.of("cards",List.of(card)),w.ok("GET","/v1/accounts/"+source+"/cards",Map.of()));
        var blocked=w.ok("PATCH","/v1/cards/"+cid,fields("status","BLOCKED","spending_limit",100.5));assertEquals("BLOCKED",blocked.get("status"));assertEquals("100.50",blocked.get("spending_limit"));
        assertEquals("CLOSED",w.ok("DELETE","/v1/cards/"+cid,Map.of()).get("status"));
        var closed=w.call("PATCH","/v1/cards/"+cid,Map.of("spending_limit","1"));assertEquals(409,closed.status());assertEquals(fields("code","INVALID_STATE","message","card is closed","details",Map.of("card_id",cid)),closed.body());
        var created=w.ok("POST","/v1/transfers",fields("source_account_id",source,"target_account_id",target,"amount",10.5,"idempotency_key","first"));
        var t=(Map<?,?>)created.get("transfer");String reference=(String)t.get("reference");var tokens=(Map<?,?>)created.get("confirmation_tokens");assertEquals(Set.of("SENDER","RECEIVER"),tokens.keySet());
        assertEquals("PARTIALLY_CONFIRMED",w.ok("POST","/v1/transfers/"+reference+"/confirm",fields("party","SENDER","token",tokens.get("SENDER"))).get("status"));
        assertEquals("SETTLED",w.ok("POST","/v1/transfers/"+reference+"/confirm",fields("party","RECEIVER","token",tokens.get("RECEIVER"))).get("status"));
        assertEquals("SETTLED",w.ok("GET","/v1/transfers/"+reference,Map.of()).get("status"));
        assertEquals("4989.50",w.eco.balance(source).get("balance"));assertEquals("10.50",w.eco.balance(target).get("balance"));
        assertEquals(Map.of("positions",Map.of("AURUM",Map.of("USD","10.50"),"NORD",Map.of("USD","-10.50"))),w.ok("GET","/v1/network/positions",Map.of()));
        assertEquals(2,((List<?>)w.ok("GET","/v1/network/banks",Map.of()).get("banks")).size());
        assertTrue(((String)w.ok("GET","/v1/audit",Map.of()).get("trace")).contains("transfer.settled"));
        assertEquals(3600,w.ok("GET","/v1/time-config",Map.of()).get("confirmation_ttl_seconds"));
        w.ok("PUT","/v1/time-config",fields("values",Map.of("confirmation_ttl_seconds",5),"accept_sync","false"));
        assertTrue(w.eco.time().acceptSync());assertEquals(5,w.ok("GET","/v1/time-config",Map.of()).get("confirmation_ttl_seconds"));
        var missing=w.call("POST","/v1/banks/AURUM/accounts",Map.of());assertEquals(400,missing.status());assertEquals(Map.of("code","VALIDATION_ERROR","message","missing required field: client_id"),missing.body());
        var badEnum=w.call("PATCH","/v1/clients/"+a+"/kyc",Map.of("status","verified"));assertEquals(400,badEnum.status());
        assertEquals(fields("code","VALIDATION_ERROR","message","status must be one of ['PENDING', 'VERIFIED', 'REJECTED']","details",fields("field","status","allowed",List.of("PENDING","VERIFIED","REJECTED"))),badEnum.body());
        assertEquals(Map.of("ok",true,"service","gateway","banks",List.of("AURUM","NORD")),w.gateway.dispatch(new FinanceGateway.Request("GET","/toBank7/health",new byte[0],Map.of(),Map.of())).body());
    }
    @Test void authenticationConsumesBeforeJsonFailuresAndSignsRawMountedBodyWhileHealthBypasses() {
        var w=new World();var noAuth=new FinanceGateway.Request("GET","/toBank7/health",new byte[0],Map.of(),Map.of());assertEquals(200,w.gateway.dispatch(noAuth).status());assertEquals(0,w.gateway.seenNonceCount());
        assertEquals(404,w.gateway.dispatch(new FinanceGateway.Request("GET","/toBank7/missing",new byte[0],Map.of(),Map.of())).status());
        assertEquals(404,w.gateway.dispatch(new FinanceGateway.Request("GET","/toBank7/v1/cards/",new byte[0],Map.of(),Map.of())).status());
        assertEquals(405,w.gateway.dispatch(new FinanceGateway.Request("DELETE","/toBank7/v1/time-config",new byte[0],Map.of(),Map.of())).status());assertEquals(0,w.gateway.seenNonceCount());
        assertEquals(new FinanceGateway.Response(401,Map.of("code","UNAUTHORIZED","message","missing_auth_headers")),w.gateway.dispatch(new FinanceGateway.Request("GET","/toBank7/v1/time-config",new byte[0],Map.of(),Map.of())));
        var broken=w.signed("POST","/v1/clients","{bad".getBytes(StandardCharsets.UTF_8),"broken");
        assertEquals(new FinanceGateway.Response(400,Map.of("code","VALIDATION_ERROR","message","request body is not valid JSON")),w.gateway.dispatch(broken));
        assertEquals(new FinanceGateway.Response(401,Map.of("code","UNAUTHORIZED","message","nonce_replayed")),w.gateway.dispatch(broken));
        var good=w.signed("GET","/v1/time-config","true".getBytes(StandardCharsets.UTF_8),"good");
        var changed=new FinanceGateway.Request(good.method(),good.path(),"false".getBytes(StandardCharsets.UTF_8),good.headers(),Map.of());
        assertEquals(new FinanceGateway.Response(401,Map.of("code","UNAUTHORIZED","message","bad_signature")),w.gateway.dispatch(changed));assertEquals(1,w.gateway.seenNonceCount());
        assertEquals(200,w.gateway.dispatch(good).status());assertEquals(2,w.gateway.seenNonceCount());
        var unmountedSignature=FinanceSecurity.sign("test-secret","GET","/v1/time-config",new byte[0],"1000","unmounted");
        assertEquals(401,w.gateway.dispatch(new FinanceGateway.Request("GET","/toBank7/v1/time-config",new byte[0],Map.of("x-timestamp","1000","x-nonce","unmounted","x-signature",unmountedSignature),Map.of())).status());
        var badQuery=w.signed("GET","/v1/audit",new byte[0],"query-error");
        assertThrows(IllegalArgumentException.class,()->w.gateway.dispatch(new FinanceGateway.Request(badQuery.method(),badQuery.path(),badQuery.body(),badQuery.headers(),Map.of("since","1__2"))));
        assertEquals(3,w.gateway.seenNonceCount());
        var goodQuery=w.signed("GET","/v1/audit",new byte[0],"query-ok");
        assertEquals(new FinanceGateway.Response(200,Map.of("trace","")),w.gateway.dispatch(new FinanceGateway.Request(goodQuery.method(),goodQuery.path(),goodQuery.body(),goodQuery.headers(),Map.of("since","1_000"))));
    }
    @Test void allElevenIndependentReviewTruthinessControlsKeepStringFalseAndNullActorBypasses() throws Exception {
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try(var in=getClass().getResourceAsStream("gateway-review-golden.xml")) {
            assertNotNull(in);var doc=factory.newDocumentBuilder().parse(in).getDocumentElement();var rows=(List<?>)Json.parse(doc.getElementsByTagName("review-truthiness").item(0).getTextContent(),"independent truthiness");assertEquals(11,rows.size());
            for(var item:rows) {
                var gold=(Map<?,?>)item;var w=new World();String a=w.client("A"),b=w.client("B"),source=w.account("AURUM",a,"5000"),target=w.account("NORD",b,"0");
                var created=w.ok("POST","/v1/transfers",fields("source_account_id",source,"target_account_id",target,"amount","2500","idempotency_key","review"));
                String reference=(String)((Map<?,?>)created.get("transfer")).get("reference");var tokens=(Map<?,?>)created.get("confirmation_tokens");
                for(String p:List.of("SENDER","RECEIVER"))w.ok("POST","/v1/transfers/"+reference+"/confirm",fields("party",p,"token",tokens.get(p)));
                assertEquals("AWAITING_REVIEW",w.eco.transfer(reference).status().name());
                assertEquals(gold.get("expected_status"),w.ok("POST","/v1/transfers/"+reference+"/review",fields("approve",gold.get("approve"))).get("status"));
                assertEquals(gold.get("expected_source"),w.eco.balance(source).get("balance"));assertEquals(gold.get("expected_target"),w.eco.balance(target).get("balance"));assertEquals("0.00",w.eco.balance(source).get("hold"));
            }
        }
    }
    @Test void sourceJsonValidityAndSourceLookupOrderSeparateSourceErrorsFromRefusals() {
        var w=new World();var invalid=new FinanceGateway.Response(400,Map.of("code","VALIDATION_ERROR","message","request body is not valid JSON"));
        // json.loads accepts only ASCII digits,whereas Character.isDigit would admit this Arabic-Indic one.
        assertEquals(invalid,w.gateway.dispatch(w.signed("POST","/v1/clients","{\"display_name\":١}".getBytes(StandardCharsets.UTF_8),"arabic")));
        assertEquals(invalid,w.gateway.dispatch(w.signed("POST","/v1/clients","[1,]".getBytes(StandardCharsets.UTF_8),"trailing")));
        int before=w.gateway.seenNonceCount();var refused=List.of("{\"a\":1,\"a\":2}","NaN","[-Infinity]","[1e400]","123456789012345678901234567890","[".repeat(300)+"]".repeat(300));
        for(String body:refused) {
            var request=w.signed("POST","/v1/clients",body.getBytes(StandardCharsets.UTF_8),"refused-"+body.length()+body.charAt(1));
            assertThrows(IllegalArgumentException.class,()->w.gateway.dispatch(request),body);
        }
        assertEquals(before+refused.size(),w.gateway.seenNonceCount());
        // target_bank is tested before required fields:an unhashable value raises,a falsy one reads them,a number is never remote.
        assertThrows(IllegalArgumentException.class,()->w.call("POST","/v1/transfers",Map.of("target_bank",List.of(1))));
        assertEquals(new FinanceGateway.Response(400,Map.of("code","VALIDATION_ERROR","message","missing required field: source_account_id")),w.call("POST","/v1/transfers",Map.of("target_bank",List.of())));
        String a=w.client("A"),b=w.client("B"),source=w.account("AURUM",a,"100"),target=w.account("NORD",b,"0");
        assertEquals(200,w.call("POST","/v1/transfers",fields("target_bank",5,"source_account_id",source,"target_account_id",target,"amount","10","idempotency_key","numeric-bank")).status());
        // Card issue and update find the account or card before they read the body.
        assertEquals(new FinanceGateway.Response(404,fields("code","NOT_FOUND","message","card not found","details",Map.of("card_id","crd_missing"))),w.call("PATCH","/v1/cards/crd_missing",List.of("status")));
        assertEquals(new FinanceGateway.Response(404,fields("code","NOT_FOUND","message","account not found","details",Map.of("account_id","acc_missing"))),w.call("POST","/v1/accounts/acc_missing/cards",List.of()));
        assertThrows(IllegalArgumentException.class,()->new FinanceGateway.Request("GET","/health",new byte[0],Map.of("X-Nonce","a","x-nonce","b"),Map.of()));
    }
    /** Body text as the source tests send it, signed now: the source's own tests/test_gateway_http.py. */
    private static FinanceGateway.Response signedNow(FinanceGateway gateway,String method,String path,Object payload) {
        byte[] body=payload==null?new byte[0]:Json.write(payload).getBytes(StandardCharsets.UTF_8);String ts=Long.toString(System.currentTimeMillis()/1000),nonce=UUID.randomUUID().toString();
        return gateway.dispatch(new FinanceGateway.Request(method,path,body,Map.of("x-timestamp",ts,"x-nonce",nonce,"x-signature",FinanceSecurity.sign("test-secret",method,path,body,ts,nonce),"content-type","application/json"),Map.of()));
    }
    private static Map<?,?> json(FinanceGateway.Response response) { return (Map<?,?>)response.body(); }
    @Test void theSourceGatewayHttpTestsPassOverInJvmDispatch() {
        var gateway=new FinanceGateway(FinanceEcosystem.defaults(),"test-secret");byte[] body=Json.write(fields("display_name","X","email","x@x.com")).getBytes(StandardCharsets.UTF_8);
        assertEquals(401,gateway.dispatch(new FinanceGateway.Request("POST","/v1/clients",body,Map.of("content-type","application/json"),Map.of())).status());
        String now=Long.toString(System.currentTimeMillis()/1000);
        assertEquals(401,gateway.dispatch(new FinanceGateway.Request("POST","/v1/clients",body,Map.of("x-timestamp",now,"x-nonce","n","x-signature","deadbeef","content-type","application/json"),Map.of())).status());
        // test_full_signed_flow
        var alice=json(signedNow(gateway,"POST","/v1/clients",fields("display_name","Alice","email","a@x.com","kyc_status","VERIFIED")));
        var bob=json(signedNow(gateway,"POST","/v1/clients",fields("display_name","Bob","email","b@x.com","kyc_status","VERIFIED")));
        var a=json(signedNow(gateway,"POST","/v1/banks/AURUM/accounts",fields("client_id",alice.get("client_id"),"currency","USD","initial_deposit","300.00")));
        var b=json(signedNow(gateway,"POST","/v1/banks/NORD/accounts",fields("client_id",bob.get("client_id"),"currency","USD")));
        var card=json(signedNow(gateway,"POST","/v1/accounts/"+a.get("account_id")+"/cards",fields("brand","VISA","spending_limit","200")));assertEquals("ACTIVE",card.get("status"));
        assertEquals("BLOCKED",json(signedNow(gateway,"PATCH","/v1/cards/"+card.get("card_id"),fields("status","BLOCKED"))).get("status"));
        assertEquals("CLOSED",json(signedNow(gateway,"DELETE","/v1/cards/"+card.get("card_id"),null)).get("status"));
        var created=json(signedNow(gateway,"POST","/v1/transfers",fields("source_account_id",a.get("account_id"),"target_account_id",b.get("account_id"),"amount","100.00","idempotency_key",UUID.randomUUID().toString())));
        var transfer=(Map<?,?>)created.get("transfer");String reference=(String)transfer.get("reference");var tokens=(Map<?,?>)created.get("confirmation_tokens");
        assertEquals("AWAITING_CONFIRMATION",transfer.get("status"));assertEquals(3600,((Number)transfer.get("ttl_seconds")).intValue());assertTrue(transfer.containsKey("expires_at"));
        signedNow(gateway,"POST","/v1/transfers/"+reference+"/confirm",fields("party","SENDER","token",tokens.get("SENDER")));
        assertEquals("SETTLED",json(signedNow(gateway,"POST","/v1/transfers/"+reference+"/confirm",fields("party","RECEIVER","token",tokens.get("RECEIVER")))).get("status"));
        assertEquals("100.00",json(signedNow(gateway,"GET","/v1/accounts/"+b.get("account_id")+"/balance",null)).get("balance"));
        assertEquals("100.00",((Map<?,?>)((Map<?,?>)json(signedNow(gateway,"GET","/v1/network/positions",null)).get("positions")).get("AURUM")).get("USD"));
        // test_invalid_enum_returns_400, test_missing_required_field_returns_400, test_bad_card_expiry_returns_400
        for(var response:List.of(signedNow(gateway,"POST","/v1/clients",fields("display_name","X","email","x@x.com","kyc_status","BOGUS")),signedNow(gateway,"POST","/v1/banks/AURUM/accounts",fields("currency","USD")))) {
            assertEquals(400,response.status());assertEquals("VALIDATION_ERROR",json(response).get("code"));
        }
        var c=json(signedNow(gateway,"POST","/v1/clients",fields("display_name","A","email","a@x.com","kyc_status","VERIFIED")));
        var account=json(signedNow(gateway,"POST","/v1/banks/AURUM/accounts",fields("client_id",c.get("client_id"),"currency","USD")));
        var expiry=signedNow(gateway,"POST","/v1/accounts/"+account.get("account_id")+"/cards",fields("expiry","13-2030"));assertEquals(400,expiry.status());assertEquals("VALIDATION_ERROR",json(expiry).get("code"));
        // test_time_config_get_put_and_live_replay_window
        var timed=new FinanceGateway(FinanceEcosystem.defaults(),"test-secret");var got=signedNow(timed,"GET","/v1/time-config",null);
        assertEquals(200,got.status());assertTrue(json(got).containsKey("confirmation_ttl_seconds"));assertEquals(true,json(got).get("accept_time_sync"));
        var put=signedNow(timed,"PUT","/v1/time-config",fields("values",fields("replay_window_seconds",1)));
        assertEquals(200,put.status());assertEquals(true,json(put).get("applied"));assertEquals(1,((Number)((Map<?,?>)json(put).get("time_config")).get("replay_window_seconds")).intValue());
        String stale=Long.toString(System.currentTimeMillis()/1000-100),nonce=UUID.randomUUID().toString();
        assertEquals(401,timed.dispatch(new FinanceGateway.Request("GET","/v1/network/banks",new byte[0],Map.of("x-timestamp",stale,"x-nonce",nonce,"x-signature",FinanceSecurity.sign("test-secret","GET","/v1/network/banks",new byte[0],stale,nonce)),Map.of())).status());
        // test_sanctioned_transfer_blocked_over_http
        var eco=FinanceEcosystem.defaults();var sanctioned=new FinanceGateway(eco,"test-secret");
        var alice2=json(signedNow(sanctioned,"POST","/v1/clients",fields("display_name","Alice","email","a@x.com","kyc_status","VERIFIED")));
        var bob2=json(signedNow(sanctioned,"POST","/v1/clients",fields("display_name","Bob","email","b@x.com","kyc_status","VERIFIED")));
        var a2=json(signedNow(sanctioned,"POST","/v1/banks/AURUM/accounts",fields("client_id",alice2.get("client_id"),"currency","USD","initial_deposit","300.00")));
        var b2=json(signedNow(sanctioned,"POST","/v1/banks/NORD/accounts",fields("client_id",bob2.get("client_id"),"currency","USD")));
        eco.network().sanctions().client((String)bob2.get("client_id"));
        var blocked=json(signedNow(sanctioned,"POST","/v1/transfers",fields("source_account_id",a2.get("account_id"),"target_account_id",b2.get("account_id"),"amount","10.00","idempotency_key",UUID.randomUUID().toString())));
        assertEquals("BLOCKED",((Map<?,?>)blocked.get("transfer")).get("status"));
    }
}
