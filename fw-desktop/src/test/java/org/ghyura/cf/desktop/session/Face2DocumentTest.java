// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
package org.ghyura.cf.desktop.session;

import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.RuleValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.ghyura.cf.desktop.session.SieveDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class Face2DocumentTest {
    @Test void noOpKeepsRuleIdentityGroupingMetadataAndExactJsonTypes() {
        var original = SieveDocument.parse("""
                {"version":1,"params":{"A":{"a":{"n":9007199254740993,"flag":true,"text":"2"}}},
                 "orders":{"A":["b","a"]},"constraints":[
                 {"id":"keep-tuples","desc":"authored description","polarity":"require","sheets":["B","A"],"pairs":[{"A":"a","B":"1"},{"A":"b","B":2}],"gate":{"adjacent":true,"within":1}},
                 {"id":"separate-require","polarity":"require","pairs":[{"A":"a","B":"1"}]},
                 {"id":"group","sets":{"A":["a","b"],"B":["1"]},"condition":{"sheet":"A","present":true}},
                 {"id":"formula","sheets":["A","B"],"when":"A.n > 2"},
                 {"id":"mapping","mapping":{"source":"A","target":"B","allow":{"a":["1"]}}},
                 {"id":"assertion","assert":{"all":[{"sheet":"A","present":true},{"sheet":"B","eq":"1"}]}}]}
                """, "test");
        var bridge = new Face2Document(original);
        assertEquals(original, bridge.apply(unchanged(7)));
        assertEquals(original.json(), bridge.apply(unchanged(7)).json());
    }

    @Test void editsKeepUntouchedLargeNumbersOrdersAndOtherRules() {
        var original = SieveDocument.parse("""
                {"version":1,"params":{"A":{"a":{"big":9007199254740993,"cost":1}}},"orders":{"B":"numeric"},
                 "constraints":[{"id":"pair","desc":"original","sheets":["B","A"],"pairs":[{"A":"a","B":"1"}]},
                                {"id":"other","sheets":["A","B"],"when":"A.value == B.value"}]}
                """, "test");
        var result = new Face2Document(original).apply("""
                {"version":1,"edits":[{"source":0,"patch":[{"path":["sides","B"],"value":["2"]}]},
                                      {"source":1,"patch":[]}],
                 "paramsPatch":[{"path":["A","a","cost"],"value":3}]}
                """);
        assertEquals(original.rules().get(1), result.rules().get(1));
        assertEquals("pair", text(result.rules().getFirst().entries().get("id")));
        assertEquals("original", text(result.rules().getFirst().entries().get("desc")));
        assertEquals(texts(List.of("B", "A")), result.rules().getFirst().entries().get("sheets"));
        assertEquals(original.root().entries().get("orders"), result.root().entries().get("orders"));
        assertTrue(result.json().contains("9007199254740993"));
        assertEquals(RuleValue.Int.of(3), map(map(map(result.root().entries().get("params")).get("A")).get("a")).get("cost"));
        assertEquals(new RuleValue.Text("2"), map(list(result.rules().getFirst().entries().get("pairs")).getFirst()).get("B"));
    }

    @Test void independentNewRequireRulesNeverRegroupIntoOneOrRule() {
        var result = new Face2Document(SieveDocument.empty()).apply("""
                {"version":1,"paramsPatch":[],"edits":[
                  {"bond":{"polarity":"require","sides":{"A":["a"],"B":["1"]}}},
                  {"bond":{"polarity":"require","sides":{"A":["b"],"B":["2"]}}}]}
                """);
        assertEquals(2, result.rules().size());
        assertEquals("face2-1", text(result.rules().get(0).entries().get("id")));
        assertEquals("face2-2", text(result.rules().get(1).entries().get("id")));
    }

    @Test void deletionKeepsRemainingTupleButSplittingARuleIsRefused() {
        var original = SieveDocument.parse("""
                {"version":1,"constraints":[{"id":"or-pairs","polarity":"require","pairs":[{"A":"a","B":"1"},{"A":"b","B":"2"}]}]}
                """, "test");
        var bridge = new Face2Document(original);
        var one = bridge.apply(unchanged(1));
        assertEquals(1, list(one.rules().getFirst().entries().get("pairs")).size());
        assertEquals("or-pairs", text(one.rules().getFirst().entries().get("id")));
        assertTrue(bridge.apply(unchanged(0)).rules().isEmpty());
        var problem = assertThrows(IllegalArgumentException.class, () -> bridge.apply("""
                {"version":1,"edits":[{"source":0,"patch":[]},{"source":1,"patch":[{"path":["gate"],"value":{"adjacent":true}}]}],"paramsPatch":[]}
                """));
        assertTrue(problem.getMessage().contains("splitting it could change its meaning"));
    }

    @Test void allAdvancedWebBondKindsUseTheJVMReader() {
        var result = new Face2Document(SieveDocument.empty()).apply("""
                {"version":1,"paramsPatch":[],"edits":[
                 {"bond":{"polarity":"forbid","sides":{"A":["a","b"],"B":["1"]},"gate":{"within":2},"condition":{"sheet":"A","present":true}}},
                 {"bond":{"polarity":"forbid","sides":{"A":[],"B":[]},"when":"A.value == B.value"}},
                 {"bond":{"polarity":"require","sides":{"A":[],"B":[]},"mapping":{"source":"A","target":"B","allow":{"a":["1"]}}}},
                 {"bond":{"polarity":"require","sides":{"A":[],"B":[]},"assert":{"any":[{"sheet":"A","eq":"a"},{"sheet":"B","eq":"2"}]}}}]}
                """);
        assertEquals(List.of("sets", "when", "mapping", "assert"), result.rules().stream().map(SieveDocument::kind).toList());
        assertEquals(object("within", RuleValue.Int.of(2)), result.rules().getFirst().entries().get("gate"));
        assertNotNull(result.rules().getFirst().entries().get("condition"));
    }

    @Test void badSourcesAndMalformedPatchesAreRejected() {
        var original = SieveDocument.parse("{\"version\":1,\"constraints\":[{\"pairs\":[{\"A\":\"a\",\"B\":\"1\"}]}]}", "test");
        var bridge = new Face2Document(original);
        for (String edits : List.of("[{\"source\":9,\"patch\":[]}]", "[{\"source\":0,\"patch\":[]},{\"source\":0,\"patch\":[]}]",
                "[{\"source\":0,\"patch\":[{\"path\":[\"unsupported\"],\"value\":1}]}]")) {
            assertThrows(IllegalArgumentException.class, () -> bridge.apply("{\"version\":1,\"edits\":" + edits + ",\"paramsPatch\":[]}"));
        }
    }

    @Test void assertionGateEditsAndDeclaredSheetOrderSurviveTheRoundTrip() {
        var original = SieveDocument.parse("""
                {"version":1,"constraints":[{"id":"authored","sheets":["B","A"],"gate":{"within":2},
                 "assert":{"all":[{"sheet":"A","eq":"a"},{"sheet":"B","eq":"1"}]}}]}
                """, "test");
        var edited = new Face2Document(original).apply("""
                {"version":1,"paramsPatch":[],"edits":[{"source":0,"patch":[{"path":["gate","within"],"value":0}]},
                 {"bond":{"polarity":"require","sides":{"A":[],"B":[]},"gate":{"adjacent":true},
                  "assert":{"any":[{"sheet":"A","eq":"b"},{"sheet":"B","eq":"2"}]}}}]}
                """);
        assertEquals(object("within", RuleValue.Int.of(0)), edited.rules().getFirst().entries().get("gate"));
        assertEquals(texts(List.of("B", "A")), edited.rules().getFirst().entries().get("sheets"));
        assertEquals("authored", text(edited.rules().getFirst().entries().get("id")));
        assertEquals(object("adjacent", new RuleValue.Bool(true)), edited.rules().get(1).entries().get("gate"));
        assertEquals(edited, new Face2Document(edited).apply(unchanged(2)));
    }

    @Test void originalWebTemplateIsByteIdenticalAndInjectedDataIsScriptSafe() throws Exception {
        try (var in = Face2Document.class.getResourceAsStream("/org/ghyura/cf/desktop/face2/editor.html")) {
            assertNotNull(in);
            assertEquals("4e5af253ae0ef3a72bba29ebff693b89ba79d58c4c06677919204aca38b5c513",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }
        String html = new Face2Document(SieveDocument.empty()).html(Map.of("__TITLE__", List.of("__TITLE__", "</script><img src=x onerror=alert(1)>")), Set.of("__TITLE__"), "./");
        assertTrue(html.contains("\\u003c/script\\u003e"));
        assertFalse(html.contains("</script><img"));
        assertFalse(html.contains("/*__DATA__*/null"));
        assertTrue(html.contains("<title>Face 2 (Sieve) · Combinatorics Framework</title>"));
        assertEquals(3, html.split("\"__TITLE__\"", -1).length - 1, "template replacement must not alter sheet names or values");
        assertTrue(html.contains("id=\"quick-add\""));
        assertTrue(html.contains("id=\"stagewrap\""));
        assertTrue(html.contains("id=\"fdlg\""));
        assertTrue(html.contains("id=\"mdlg\""));
        assertTrue(html.contains("jvmRevision"));
        assertTrue(html.getBytes(StandardCharsets.UTF_8).length > 121_540);
    }

    static String unchanged(int count) {
        var edits = java.util.stream.IntStream.range(0, count).mapToObj(i -> (RuleValue) object("source", RuleValue.Int.of(i),
                "patch", new RuleValue.ListValue(List.of()))).toList();
        return ConstraintBonds.pretty(object("version", RuleValue.Int.of(1), "revision", RuleValue.Int.of(0),
                "edits", new RuleValue.ListValue(edits), "paramsPatch", new RuleValue.ListValue(List.of())));
    }
}
