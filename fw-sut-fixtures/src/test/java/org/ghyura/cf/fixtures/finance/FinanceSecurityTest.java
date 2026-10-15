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
import static org.junit.jupiter.api.Assertions.*;

class FinanceSecurityTest {
    private org.w3c.dom.Element controls() throws Exception {
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try(var in=getClass().getResourceAsStream("gateway-golden.xml")){assertNotNull(in);return factory.newDocumentBuilder().parse(in).getDocumentElement();}
    }
    @Test void all72IndependentCanonicalJsonAndHmacVectorsKeepExactBytesAndMountedPaths() throws Exception {
        var rows=controls().getElementsByTagName("vector");assertEquals(72,rows.getLength());
        for(int i=0;i<rows.getLength();i++) {
            var row=(Map<?,?>)Json.parseBinary64(rows.item(i).getTextContent(),"independent signature");
            byte[] body=FinanceSecurity.canonicalBytes(row.get("payload"));assertEquals(row.get("body_utf8"),new String(body,StandardCharsets.UTF_8),"canonical "+i);
            assertEquals(row.get("digest"),FinanceSecurity.digest(body));
            String secret=(String)row.get("secret"),method=(String)row.get("method"),path=(String)row.get("path"),ts=(String)row.get("timestamp"),nonce=(String)row.get("nonce");
            assertEquals(row.get("signature"),FinanceSecurity.sign(secret,method,path,body,ts,nonce),"signature "+i);
            assertEquals(new FinanceSecurity.Verification(true,"ok"),FinanceSecurity.verify(secret,method,path,body,ts,nonce,(String)row.get("signature"),300,null,()->1000));
            assertEquals("bad_signature",FinanceSecurity.verify(secret,method,path+"/different",body,ts,nonce,(String)row.get("signature"),300,null,()->1000).reason());
        }
    }
    @Test void orderedAuthenticationPrecedenceKeepsInclusiveSkewAndPermanentNonceConsumption() throws Exception {
        var script=controls().getElementsByTagName("verification-script").item(0);var rows=(List<?>)Json.parse(script.getTextContent(),"verification precedence");assertEquals(13,rows.size());
        var seen=new LinkedHashSet<String>();byte[] body="{}".getBytes(StandardCharsets.UTF_8);
        for(var item:rows) {
            var row=(Map<?,?>)item;String ts=(String)row.get("timestamp"),nonce=(String)row.get("nonce"),kind=(String)row.get("signature");
            String signature=kind.equals("wrong")?"wrong":FinanceSecurity.sign("test-secret","POST","/v1/transfers",body,ts,nonce);
            if(kind.equals("uppercase-valid"))signature=signature.toUpperCase(Locale.ROOT);
            var actual=FinanceSecurity.verify("test-secret","POST","/v1/transfers",body,ts,nonce,signature,300,seen,()->1000);var expected=(List<?>)row.get("expected");
            assertEquals(expected.get(0),actual.ok(),String.valueOf(row.get("id")));assertEquals(expected.get(1),actual.reason(),String.valueOf(row.get("id")));
        }
        assertEquals(Set.of("n1","n2","n3","n4","n5"),seen);
        String later=FinanceSecurity.sign("test-secret","POST","/v1/transfers",body,"2000","n3");
        assertEquals("nonce_replayed",FinanceSecurity.verify("test-secret","POST","/v1/transfers",body,"2000","n3",later,300,seen,()->2000).reason());
        assertEquals("ok",FinanceSecurity.verify("test-secret","POST","/v1/transfers",body,"2000","n3",later,300,null,()->2000).reason());
        assertThrows(IllegalArgumentException.class,()->FinanceSecurity.canonicalBytes(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->FinanceSecurity.digest(new byte[65537]));
        assertThrows(IllegalArgumentException.class,()->FinanceSecurity.canonicalBytes(Map.of(1,"invalid key")));
    }
}
