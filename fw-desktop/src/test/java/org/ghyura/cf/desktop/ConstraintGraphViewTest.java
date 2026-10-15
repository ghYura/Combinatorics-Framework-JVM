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

package org.ghyura.cf.desktop;

import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.RuleValue;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The constraint graph as the legacy editor's canvas drew it, and the parts of a rule as a tree. */
class ConstraintGraphViewTest {

    private static final String SIDECAR = """
            {"version": 1, "params": {}, "constraints": [
             {"id": "p1", "polarity": "forbid", "sheets": ["MODE", "PAYLOAD"], "pairs": [{"MODE": "strict", "PAYLOAD": "unicode"}],
              "gate": {"adjacent": true}},
             {"id": "p2", "polarity": "require", "sheets": ["MODE", "PAYLOAD"], "pairs": [{"MODE": "strict", "PAYLOAD": "unicode"}],
              "gate": {"adjacent": true}},
             {"id": "t1", "polarity": "require", "sheets": ["F", "MODE", "PAYLOAD"], "pairs": [{"MODE": "legacy", "PAYLOAD": "ascii", "F": "f1"}]},
             {"id": "s1", "polarity": "forbid", "sets": {"MODE": ["strict", "legacy"], "F": ["f2", "f3"]}},
             {"id": "w1", "polarity": "forbid", "sheets": ["MODE", "PAYLOAD"], "when": "MODE.tier == 2 and PAYLOAD.unicode == 1"},
             {"id": "c1", "polarity": "forbid", "sheets": ["MODE", "F"], "pairs": [{"MODE": "legacy", "F": "f3"}],
              "condition": {"all": [{"sheet": "PAYLOAD", "eq": "ascii"}, {"not": {"sheet": "F", "in": ["f1", "f2"]}}]}},
             {"id": "a1", "assert": {"any": [{"sheet": "MODE", "eq": "strict"}, {"sheet": "F", "present": false}]}}]}""";

    private static Map<String, List<String>> sheets() {
        Map<String, List<String>> sheets = new LinkedHashMap<>();
        sheets.put("MODE", List.of("strict", "legacy"));
        sheets.put("PAYLOAD", List.of("ascii", "unicode"));
        sheets.put("F", List.of("f1", "f2", "f3"));
        return sheets;
    }

    @Test
    void pairsAreThreadsTuplesAndSetsAreHubsAndConditionalRulesAreLeftToTheList() throws Exception {
        List<ConstraintBonds.Bond> bonds = ConstraintBonds.read(ConstraintBonds.parse(SIDECAR, "sidecar.json")).bonds();
        ConstraintGraphView.Layout layout = ConstraintGraphView.layout(sheets(), bonds);
        assertEquals(List.of("MODE", "PAYLOAD", "F"), List.copyOf(layout.headers().keySet()), "a column per sheet, in the scenario's order");
        assertTrue(layout.nodes().get("MODE").get("strict").getX() < layout.nodes().get("PAYLOAD").get("ascii").getX());
        assertEquals(List.of(0, 1, 2, 3, 5), layout.bonds().stream().map(ConstraintGraphView.Drawn::index).toList(),
                "the when and assert rules name sheets, not values, and are not drawn");
        ConstraintGraphView.Drawn p1 = layout.bonds().get(0);
        assertTrue(p1.gated() && p1.conflicting() && p1.hub() == null && p1.points().size() == 2, p1.toString());
        assertTrue(layout.bonds().get(1).conflicting(), "a require of the same pair under the same gate contradicts the forbid");
        assertNotNull(layout.bonds().get(2).hub(), "an n-ary tuple meets at a hub");
        assertEquals(3, layout.bonds().get(2).points().size());
        assertEquals(4, layout.bonds().get(3).points().size(), "a set of two values by two is four arms");
        assertNull(layout.bonds().get(4).hub());
        assertTrue(!layout.bonds().get(2).conflicting() && !layout.bonds().get(4).gated());
        var box = layout.nodes().get("F").get("f3");

        BufferedImage image = new BufferedImage(layout.size().width, layout.size().height, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            ConstraintGraphView view = new ConstraintGraphView();
            view.show(sheets(), Set.of("F"), bonds);
            view.setSize(view.getPreferredSize());
            view.paint(image.createGraphics());
            assertEquals("Sheet F, value f3", view.valueAt(box.getCenterX(), box.getCenterY()), "a value's whole text is its tooltip, in plain words");
        });
        var mid = layout.bonds().get(0).points();
        int rgb = image.getRGB((int) ((mid.get(0).getX() + mid.get(1).getX()) / 2), (int) ((mid.get(0).getY() + mid.get(1).getY()) / 2));
        assertTrue(((rgb >> 16) & 0xFF) > 150 || ((rgb >> 8) & 0xFF) > 120, "a bond is drawn between its values: " + Integer.toHexString(rgb));
    }

    @Test
    void aRuleShowsItsGateWhenNestingMappingSetsAndPairsAsATree() {
        List<RuleValue> rules = ((RuleValue.ListValue) ((RuleValue.MapValue) ConstraintBonds.parse(SIDECAR, "sidecar.json")).entries()
                .get("constraints")).items();
        assertEquals("""
                c1 (forbid)
                  sheets: MODE, F
                  condition
                    all of
                      PAYLOAD=ascii
                      not
                        F∈{f1, f2}
                  pairs: 1
                    MODE=legacy, F=f3
                """, ConditionTree.dump(ConditionTree.of((RuleValue.MapValue) rules.get(5))));
        assertEquals("""
                a1 (require)
                  assert
                    any of
                      MODE=strict
                      F absent
                """, ConditionTree.dump(ConditionTree.of((RuleValue.MapValue) rules.get(6))));
        assertEquals("""
                w1 (forbid)
                  sheets: MODE, PAYLOAD
                  when: MODE.tier == 2 and PAYLOAD.unicode == 1
                """, ConditionTree.dump(ConditionTree.of((RuleValue.MapValue) rules.get(4))));
        assertEquals("""
                s1 (forbid)
                  sets (every combination)
                    MODE ∈ {strict, legacy}
                    F ∈ {f2, f3}
                """, ConditionTree.dump(ConditionTree.of((RuleValue.MapValue) rules.get(3))));
        assertEquals("""
                p1 (forbid)
                  sheets: MODE, PAYLOAD
                  gate: {"adjacent": true}
                  pairs: 1
                    MODE=strict, PAYLOAD=unicode
                """, ConditionTree.dump(ConditionTree.of((RuleValue.MapValue) rules.get(0))));
    }
}
