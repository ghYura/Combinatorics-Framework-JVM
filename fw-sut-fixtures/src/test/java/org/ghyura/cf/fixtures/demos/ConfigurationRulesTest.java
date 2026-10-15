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

package org.ghyura.cf.fixtures.demos;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationRulesTest {
    private Element golden() throws Exception {
        var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d4-golden.xml")){assertNotNull(in);return f.newDocumentBuilder().parse(in).getDocumentElement();}
    }
    @Test void everyOriginalMainAndControlCaseMatchesIndependentAcceptanceHeadersPayloadAndFailureClasses() throws Exception {
        var cases=golden().getElementsByTagName("case");assertEquals(84,cases.getLength());Map<String,Integer> totals=new TreeMap<>();
        for(int i=0;i<cases.getLength();i++){
            var c=(Element)cases.item(i);var cfg=new ConfigurationRules.Config(c.getAttribute("env"),c.getAttribute("mode"),c.getAttribute("transport"),List.of(c.getAttribute("features").split("\\+")),Integer.parseInt(c.getAttribute("workers")),c.getAttribute("debug").equals("1"));
            var r=ConfigurationRules.evaluate(c.getAttribute("phase"),c.getAttribute("policy"),cfg);var j=r.judgment();
            assertEquals(c.getAttribute("id"),r.caseId());assertEquals(c.getAttribute("accepted"),Boolean.toString(r.observed().accepted()));assertEquals(c.getAttribute("violations"),String.join(" ",r.observed().violations()));assertEquals(c.getAttribute("expected-violations"),String.join(" ",j.expectedViolations()));assertEquals(c.getAttribute("failure-classes"),String.join(" ",j.failureClasses()));assertEquals(c.getAttribute("verdict"),j.passed()?"PASS":"DOMAIN_FAIL");
            totals.merge(r.phase()+"|"+(j.passed()?"PASS":"DOMAIN_FAIL"),1,Integer::sum);
            if(r.observed().accepted()){
                var e=r.observed().envelope();var expected=(Element)c.getElementsByTagName("envelope").item(0);assertEquals(expected.getAttribute("url"),e.url());assertEquals(Integer.parseInt(expected.getAttribute("workers")),e.workers());Map<String,String> headers=new TreeMap<>();var hs=expected.getElementsByTagName("header");for(int n=0;n<hs.getLength();n++){var h=(Element)hs.item(n);headers.put(h.getAttribute("name"),h.getAttribute("value"));}assertEquals(headers,e.headers());
                byte[] body=Base64.getDecoder().decode(e.bodyBase64());if(e.headers().get("Content-Encoding").equals("gzip"))try(var zip=new GZIPInputStream(new ByteArrayInputStream(body))){body=zip.readAllBytes();}assertArrayEquals(ConfigurationRules.originalPayload(),body);
                var transform=c.getElementsByTagName("transformation");if(transform.getLength()>0){var t=(Element)transform.item(0);for(String name:List.of("url","workers","headers","payload"))assertEquals(t.getAttribute(name),j.transformation().get(name).toString());}else assertTrue(j.transformation().isEmpty());
            }else{assertNull(r.observed().envelope());assertTrue(j.transformation().isEmpty());assertTrue(j.acceptanceOk(),"legitimate rejection passes");}
        }
        assertEquals(Map.of("main|PASS",48,"main|DOMAIN_FAIL",18,"controls|PASS",17,"controls|DOMAIN_FAIL",1),totals);
    }
    @Test void thePolicyBlindOracleSeparatesAcceptanceViolationIdsAndAllEnvelopeFailures() {
        var legal=new ConfigurationRules.Config("dev","batch","https",List.of("cache","gzip"),1,false);var correct=ConfigurationRules.adapt(legal,"correct");var env=correct.envelope();
        assertEquals(List.of("wrong_rejection"),ConfigurationRules.judge(legal,new ConfigurationRules.Adapted(false,List.of(),null)).failureClasses());
        var wrong=new ConfigurationRules.Envelope("http://elsewhere.invalid/live",2,Map.of("Content-Encoding","identity"),Base64.getEncoder().encodeToString(new byte[]{0}));
        assertEquals(List.of("wrong_url","wrong_workers","wrong_headers","wrong_payload"),ConfigurationRules.judge(legal,new ConfigurationRules.Adapted(true,List.of(),wrong)).failureClasses());
        var brokenGzip=new ConfigurationRules.Envelope(env.url(),env.workers(),env.headers(),Base64.getEncoder().encodeToString(new byte[]{0}));
        assertEquals(List.of("wrong_payload"),ConfigurationRules.judge(legal,new ConfigurationRules.Adapted(true,List.of(),brokenGzip)).failureClasses());
        var forbidden=new ConfigurationRules.Config("prod","batch","http",List.of("audit","gzip"),2,false);
        assertEquals(List.of("wrong_acceptance"),ConfigurationRules.judge(forbidden,correct).failureClasses());
        assertEquals(List.of("wrong_violation_ids"),ConfigurationRules.judge(forbidden,new ConfigurationRules.Adapted(false,List.of("R6"),null)).failureClasses());
        var multiple=new ConfigurationRules.Config("prod","batch","http",List.of("cache","gzip"),1,true);assertEquals(List.of("R1","R4","R6"),ConfigurationRules.violations(multiple));assertEquals(List.of("R1","R4"),ConfigurationRules.adapt(multiple,"ignores_debug_rule").violations());
    }
    @Test void repeatedIncompleteAndMalformedAtomsAreSetupFailuresAndProofNeverRecomputesObservedVerdicts() throws Exception {
        String valid="env(\"dev\");mode(\"batch\");transport(\"https\");feature(\"cache\");feature(\"gzip\");workers(1);";
        assertFalse(ConfigurationRulesPlugin.parseAtoms(List.of(valid)).debug());assertTrue(ConfigurationRulesPlugin.parseAtoms(List.of(valid+"debug();")).debug());
        for(String bad:List.of(valid+"env(\"prod\");",valid+"debug();debug();",valid.replace("feature(\"gzip\");",""),valid.replace("feature(\"gzip\");","feature(\"cache\");"),valid+"\n",valid.replace("workers(1)","workers(3)")))assertThrows(IllegalArgumentException.class,()->ConfigurationRulesPlugin.parseAtoms(List.of(bad)));
        assertThrows(IllegalArgumentException.class,()->ConfigurationRulesPlugin.parseAtoms(List.of("feature(\"audit\");feature(\"gzip\");")));
        var root=golden();var cases=root.getElementsByTagName("case");var observations=new java.util.ArrayList<ConfigurationRules.Observed>();
        for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);if(!c.getAttribute("phase").equals("main"))continue;var cfg=new ConfigurationRules.Config(c.getAttribute("env"),c.getAttribute("mode"),c.getAttribute("transport"),List.of(c.getAttribute("features").split("\\+")),Integer.parseInt(c.getAttribute("workers")),c.getAttribute("debug").equals("1"));observations.add(new ConfigurationRules.Observed(c.getAttribute("id"),c.getAttribute("policy"),cfg,c.getAttribute("verdict")));}
        var rows=root.getElementsByTagName("pairwise-row");var selected=new java.util.ArrayList<String>();for(int i=0;i<rows.getLength();i++)selected.add(((Element)rows.item(i)).getAttribute("key"));var proof=ConfigurationRules.certify(observations,selected);
        assertEquals(60,proof.obligations().size());assertEquals(proof.obligations(),proof.covered());assertEquals(54,proof.wrongSieveRemoved());assertEquals(12,proof.wrongSieveRemaining());assertEquals(0,proof.wrongSieveRemainingFailures());assertEquals(18,proof.wrongSieveHiddenFailures());
        assertThrows(IllegalArgumentException.class,()->ConfigurationRules.certify(observations.subList(0,65),selected));var duplicate=new java.util.ArrayList<>(observations);duplicate.set(65,observations.getFirst());assertThrows(IllegalArgumentException.class,()->ConfigurationRules.certify(duplicate,selected));
        var changed=new java.util.ArrayList<>(observations);var first=changed.getFirst();changed.set(0,new ConfigurationRules.Observed(first.caseId(),first.policy(),first.config(),"DOMAIN_FAIL"));assertNotEquals(proof.wrongSieveHiddenFailures()+proof.wrongSieveRemainingFailures(),ConfigurationRules.certify(changed,selected).wrongSieveHiddenFailures()+ConfigurationRules.certify(changed,selected).wrongSieveRemainingFailures());
    }
}
