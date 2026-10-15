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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.rule.RuleValue;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SieveDocumentTest {
    @Test void roundTripPreservesEveryRuleKindIdsTypesGatesAndOrder() {
        String input = """
                {"version":1,"params":{"A":{"a":{"cost":9007199254740993,"label":"2","active":true}}},
                 "orders":{"A":["a","b"],"B":"numeric"},"constraints":[
                 {"id":"pair-original","desc":"Keep this description","sheets":["B","A"],
                  "pairs":[{"A":"a","B":2},{"A":"b","B":true}],"gate":{"adjacent":true,"within":0},
                  "condition":{"any":[{"sheet":"A","present":true},{"not":{"sheet":"B","eq":"2"}}]}},
                 {"id":"sets-original","sets":{"A":["a","b"],"B":["1","2"]}},
                 {"id":"formula-original","sheets":["A","B"],"when":"A.cost > B.pos","polarity":"require"},
                 {"id":"map-original","mapping":{"source":"A","target":"B","allow":{"a":["1",2],"b":[]}}},
                 {"id":"assert-original","assert":{"all":[{"sheet":"A","present":true},{"sheet":"B","in":["1","2"]}]}}
                 ]}
                """;
        SieveDocument doc = SieveDocument.parse(input, "test");
        assertEquals(ConstraintBonds.parse(input, "test"), doc.root());
        assertEquals(doc, SieveDocument.parse(doc.json(), "roundtrip"));
        assertEquals("pair-original", SieveDocument.text(doc.rules().getFirst().entries().get("id")));
        assertEquals(doc, doc.replace(0, doc.rules().getFirst()));
        assertEquals(doc, doc.move(0, 4).move(4, 0));
        assertEquals(4, doc.remove(2).rules().size());
        assertEquals(List.of("B", "A"), SieveDocument.sheets(doc.rules().getFirst()).stream().toList());
        assertTrue(doc.parameterNames("A").containsAll(List.of("cost", "value", "pos")));
        assertEquals(doc.root(), ConstraintBonds.parse(doc.json(), "execution"));
        ConstraintSidecarReader.read(InputSource.ofText("execution", doc.json()));
    }

    @Test void conflictChecksDistinguishTypesAssertionsAndCombinedGates() {
        var doc = SieveDocument.parse("""
                {"version":1,"constraints":[
                 {"id":"number","polarity":"forbid","pairs":[{"A":"a","B":2}]},
                 {"id":"text","polarity":"require","pairs":[{"A":"a","B":"2"}]},
                 {"id":"one","polarity":"forbid","assert":{"sheet":"A","eq":"a"}},
                 {"id":"two","polarity":"require","assert":{"sheet":"A","eq":"b"}},
                 {"id":"gate-one","polarity":"forbid","pairs":[{"A":"a","B":"1"}],"gate":{"adjacent":true,"within":0}},
                 {"id":"gate-two","polarity":"require","pairs":[{"A":"a","B":"1"}],"gate":{"adjacent":true,"within":1}},
                 {"id":"map-one","polarity":"forbid","mapping":{"source":"A","target":"B","allow":{"a":["1"]}}},
                 {"id":"map-two","polarity":"require","mapping":{"source":"A","target":"B","allow":{"a":["1"]}}}
                ]}
                """, "test");
        assertTrue(doc.contradictions().isEmpty());
        var opposite = new java.util.LinkedHashMap<>(doc.rules().getFirst().entries()); opposite.put("id", new RuleValue.Text("opposite")); opposite.put("polarity", new RuleValue.Text("require"));
        assertEquals(List.of("number ↔ opposite"), doc.replace(-1, new RuleValue.MapValue(opposite)).contradictions());
    }

    @Test void externalEditsAndMalformedJsonCannotSilentlyReplaceAFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("example.constraints.json");
        String initial = SieveDocument.empty().json();
        String stamp = SieveFiles.write(file, initial, "");
        Files.writeString(file, "externally changed");
        assertThrows(IllegalStateException.class, () -> SieveFiles.write(file, initial, stamp));
        assertEquals("externally changed", Files.readString(file));
        assertThrows(FrameworkException.class, () -> SieveDocument.parse("{\"version\":1,\"nodes\":[],\"edges\":[]}", "Gemini graph"));
        assertThrows(FrameworkException.class, () -> SieveDocument.parse("{\"version\":1,\"version\":1,\"constraints\":[]}", "duplicate"));
        Path link = dir.resolve("link.constraints.json"); Files.createSymbolicLink(link, file);
        assertThrows(IllegalStateException.class, () -> SieveFiles.write(link, initial, SieveFiles.fingerprint(file)));
        assertTrue(Files.isSymbolicLink(link));
    }

    @Test void brokenExpressionCanBeRepairedButExecutionValidationRefusesIt(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("small.json"), """
                {"sheets":{"FW_Seq":[["A","FW_Combi(1)"],["B","FW_Combi(1)"]],"A":[["a"],["b"]],"B":[["1"],["2"]]}}
                """);
        Path file = ConstraintSidecarReader.companionOf(scenario);
        String broken = """
                {"version":1,"constraints":[{"id":"repair-me","sheets":["A","B"],"when":"A.value >"}]}
                """;
        Files.writeString(file, broken);
        Session session = Session.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.plan.autoPromoteSingleVerb", "false", "test")));
        var opened = session.openSieve(scenario);
        assertEquals("repair-me", SieveDocument.text(opened.document().rules().getFirst().entries().get("id")));
        assertThrows(FrameworkException.class, () -> session.validateSieve(scenario, opened.document().json(), Cancellation.NONE));
        assertEquals(broken, Files.readString(file));
        String fixed = broken.replace("A.value >", "A.value == B.value");
        assertEquals(0, session.validateSieve(scenario, fixed, Cancellation.NONE).page().visited().intValueExact());
        String stamp = SieveFiles.write(file, fixed, opened.fingerprint());
        assertEquals(stamp, session.openSieve(scenario).fingerprint());
    }
}
