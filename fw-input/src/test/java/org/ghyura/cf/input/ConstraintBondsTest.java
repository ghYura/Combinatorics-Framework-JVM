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

import org.ghyura.cf.model.rule.RuleValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The constraint editor's core against the unchanged legacy editor: editor-goldens.jsonl holds, per case, what
 * constraints/editor.py sidecar_to_bonds, bonds_to_sidecar and find_contradictions returned for a sidecar (made by
 * handoff/outbox/devtools/constraints/editor_goldens.py on a scratch copy of the reference tree).
 */
class ConstraintBondsTest {

    private static List<RuleValue.MapValue> goldens() throws IOException {
        List<RuleValue.MapValue> out = new ArrayList<>();
        try (InputStream in = ConstraintBondsTest.class.getResourceAsStream("editor-goldens.jsonl")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    out.add((RuleValue.MapValue) ConstraintBonds.parse(line, "editor-goldens.jsonl"));
                }
            }
        }
        return out;
    }

    private static String json(RuleValue v) {
        return ConstraintBonds.pythonJson(v, false, true);
    }

    @Test
    void readingWritingAndContradictionsMatchTheLegacyEditor() throws IOException {
        List<RuleValue.MapValue> cases = goldens();
        assertEquals(12, cases.size());
        for (RuleValue.MapValue golden : cases) {
            Map<String, RuleValue> g = golden.entries();
            String name = ((RuleValue.Text) g.get("case")).value();
            RuleValue input = g.get("input");
            ConstraintBonds.Bonds bonds = ConstraintBonds.read(input);
            assertEquals(json(g.get("bonds")), json(new RuleValue.ListValue(bonds.bonds().stream().<RuleValue>map(ConstraintBonds.Bond::toValue).toList())),
                    name + ": sidecar_to_bonds");
            RuleValue params = ((RuleValue.MapValue) input).entries().get("params");
            assertEquals(json(g.get("sidecar")), json(ConstraintBonds.write(bonds.bonds(), params, bonds.orders())), name + ": bonds_to_sidecar");
            List<RuleValue> conflicts = new ArrayList<>();
            for (ConstraintBonds.Contradiction c : ConstraintBonds.contradictions(bonds.bonds())) {
                List<RuleValue> sides = new ArrayList<>();
                for (Map.Entry<String, List<String>> e : c.sides()) {
                    sides.add(new RuleValue.ListValue(List.of(new RuleValue.Text(e.getKey()),
                            new RuleValue.ListValue(e.getValue().stream().<RuleValue>map(RuleValue.Text::new).toList()))));
                }
                conflicts.add(new RuleValue.ListValue(List.of(new RuleValue.ListValue(c.sheets().stream().<RuleValue>map(RuleValue.Text::new).toList()),
                        new RuleValue.ListValue(c.gate().stream().<RuleValue>map(x -> x instanceof BigInteger i ? new RuleValue.Int(i)
                                : new RuleValue.Text((String) x)).toList()), new RuleValue.ListValue(sides), new RuleValue.Text(c.condition()))));
            }
            assertEquals(json(g.get("contradictions")), json(new RuleValue.ListValue(conflicts)), name + ": find_contradictions");
        }
    }

    @Test
    void aWrittenSidecarIsWhatTheReaderAcceptsAndPrettyJsonRoundTrips() throws IOException {
        for (RuleValue.MapValue golden : goldens()) {
            String name = ((RuleValue.Text) golden.entries().get("case")).value();
            if (List.of("mapping", "when-and-condition").contains(name)) {
                // editor-only inputs that are not valid sidecars, refused as the strict legacy validation refuses them: a when
                // bond naming one sheet, and a mapping with the key "map" where the format has "allow"
                continue;
            }
            RuleValue written = golden.entries().get("sidecar");
            String text = ConstraintBonds.pretty(written);
            assertEquals(json(written), json(ConstraintBonds.parse(text, name)), name + ": pretty JSON parses back to the same values");
            assertTrue(text.startsWith("{\n  \"version\": 1,\n  \"params\": "), text);
            ConstraintSidecarReader.read(InputSource.ofText(name + ".constraints.json", text));
        }
        assertTrue(ConstraintBonds.pretty(ConstraintBonds.parse("{\"a\": [\"ж\"]}", "x")).contains("\"ж\""), "non-ASCII kept as written");
    }
}
