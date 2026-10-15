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

package org.ghyura.cf.input;

import org.ghyura.cf.input.proposal.StubProposalProvider;
import org.ghyura.cf.input.proposal.ScriptedProposalProvider;
import org.ghyura.cf.model.proposal.ProposalProvider;
import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class LocalProposalProvidersTest {
    private static Map<?, ?> golden(String tag) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try (var in=LocalProposalProvidersTest.class.getResourceAsStream("local-proposals-golden.xml")) {
            assertNotNull(in);
            return (Map<?,?>)Json.parse(factory.newDocumentBuilder().parse(in).getDocumentElement()
                    .getElementsByTagName(tag).item(0).getTextContent(),"independent original bank predictions");
        }
    }
    private static ProposalProvider.Request request(String slot,String role,String context,int count) {
        return new ProposalProvider.Request(slot,role,context,count,17);
    }
    @Test void originalScenarioRolesAndCountsMatchAllFrozenProposals() throws Exception {
        var provider=new StubProposalProvider();
        var all=golden("proposals");
        for(var family:all.values())for(var entry:((Map<?,?>)family).entrySet()) {
            var expected=(List<?>)entry.getValue();
            assertEquals(expected,provider.propose(request("slot",(String)entry.getKey(),"attack money quantity context",expected.size())));
        }
        var banks=golden("banks");
        for(var entry:banks.entrySet()) {
            String role=entry.getKey().equals("interjection")?"surprise":entry.getKey().equals("injection")?"inject":(String)entry.getKey();
            assertEquals(entry.getValue(),provider.propose(request("slot",role,"",99)));
        }
    }
    @Test void rolePriorityContextFallbackZeroAndMissingBanksPreserveSourceSemantics() {
        var provider=new StubProposalProvider();
        assertEquals(List.of("1","0","-1"),provider.propose(request("slot","qty","injection attack",3)));
        assertEquals(List.of("0","100"),provider.propose(request("slot","discount","quantity",2)));
        assertEquals(List.of("0","10","200"),provider.propose(request("slot","taxPct","money",3)));
        assertEquals(List.of("transfer","delete","grant"),provider.propose(request("slot","unknown","COMMAND",99)));
        assertEquals(List.of("0","1","-1","2147483647"),provider.propose(request("slot","unknown","",99)));
        assertEquals(List.of(),provider.propose(request("slot","qty","",0)));
        assertThrows(IllegalArgumentException.class,()->request("slot","qty","",-1));
        assertThrows(IllegalArgumentException.class,()->request(" ","qty","",1));
        assertThrows(UnsupportedOperationException.class,()->provider.propose(request("slot","qty","",2)).add("7"));
    }
    @Test void scriptedResponsesPreserveDuplicatesEmptyOrderImmutabilityAndContentIdentity() {
        var values=new ArrayList<>(List.of("","same","same","tail"));
        var data=new LinkedHashMap<String,List<String>>();data.put("B",List.of("z"));data.put("A",values);
        var provider=new ScriptedProposalProvider("script","1",data,10000);
        values.set(0,"mutated");data.clear();
        assertEquals(List.of("","same","same","tail"),provider.propose(request("A","ignored","ignored",4)));
        assertThrows(UnsupportedOperationException.class,()->provider.propose(request("A","","",2)).set(0,"other"));
        var reversed=new LinkedHashMap<String,List<String>>();reversed.put("A",List.of("","same","same","tail"));reversed.put("B",List.of("z"));
        assertEquals(provider.identity(),new ScriptedProposalProvider("script","1",reversed,10000).identity());
        assertNotEquals(provider.identity(),new ScriptedProposalProvider("script","2",reversed,10000).identity());
        reversed.put("A",List.of("same","","same","tail"));
        assertNotEquals(provider.identity().contentDigest(),new ScriptedProposalProvider("script","1",reversed,10000).identity().contentDigest());
    }
    @Test void missingExhaustedMalformedAndOverBudgetScriptRefuseWithoutFallback() {
        var provider=new ScriptedProposalProvider("script","1",Map.of("A",List.of("")),10000);
        assertThrows(IllegalArgumentException.class,()->provider.propose(request("B","quantity","",1)));
        assertThrows(IllegalArgumentException.class,()->provider.propose(request("A","quantity","",2)));
        assertEquals(List.of(),provider.propose(request("A","quantity","",0)));
        assertThrows(IllegalArgumentException.class,()->new ScriptedProposalProvider("script","1",Map.of(),0));
        assertThrows(IllegalArgumentException.class,()->new ScriptedProposalProvider("script","1",Map.of("A",List.of("large")),256));
        assertThrows(IllegalArgumentException.class,()->new ScriptedProposalProvider("script","1",Map.of(" ",List.of("x")),10000));
        assertThrows(IllegalArgumentException.class,()->new ProposalProvider.Identity("script","1","not-a-digest"));
    }
}
