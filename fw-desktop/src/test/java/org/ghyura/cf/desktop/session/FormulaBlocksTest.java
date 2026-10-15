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

import org.ghyura.cf.desktop.session.FormulaBlocks.Binary;
import org.ghyura.cf.desktop.session.FormulaBlocks.Block;
import org.ghyura.cf.desktop.session.FormulaBlocks.Kind;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.Condition;
import org.ghyura.cf.model.rule.ExprParser;
import org.ghyura.cf.model.rule.RuleValue;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GEN:operator-authoring-ui: the legacy block formula builder and condition builder against what their own JavaScript gave
 * (handoff devtools/blocks/blocks_goldens.mjs over a copy of constraints/editor.py): compileT of seeded random trees,
 * newNode, paramsOf, opsOf, every palette drop and every move of 40 trees with the tree it leaves, defaultVal, and the
 * params row's parse, display and cleanParams; then this port's way back from text to blocks.
 */
class FormulaBlocksTest {

    private static List<Map<String, RuleValue>> goldens() throws IOException {
        List<Map<String, RuleValue>> out = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                FormulaBlocksTest.class.getResourceAsStream("blocks-goldens.jsonl")), StandardCharsets.UTF_8))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                out.add(((RuleValue.MapValue) ConstraintBonds.parse(line, "blocks-goldens.jsonl")).entries());
            }
        }
        return out;
    }

    private static String text(RuleValue v) {
        return v instanceof RuleValue.Text t ? t.value() : null;
    }

    private static List<RuleValue> list(RuleValue v) {
        return ((RuleValue.ListValue) v).items();
    }

    /** A tree as the page held it: {t, op, a, b}, {t, x}, {t, sheet, param}, {t, val}; null for an empty slot. */
    static Block block(RuleValue v) {
        if (!(v instanceof RuleValue.MapValue m)) {
            return null;
        }
        Map<String, RuleValue> n = m.entries();
        return switch (text(n.get("t"))) {
            case "cmp" -> new Binary(Kind.CMP, text(n.get("op")), block(n.get("a")), block(n.get("b")));
            case "logic" -> new Binary(Kind.LOGIC, text(n.get("op")), block(n.get("a")), block(n.get("b")));
            case "arith" -> new Binary(Kind.ARITH, text(n.get("op")), block(n.get("a")), block(n.get("b")));
            case "not" -> new FormulaBlocks.Not(block(n.get("x")));
            case "param" -> new FormulaBlocks.Param(text(n.get("sheet")), text(n.get("param")));
            case "num" -> new FormulaBlocks.Num(text(n.get("val")));
            default -> throw new IllegalArgumentException(n.toString());
        };
    }

    private static Kind kind(String t) {
        return Kind.valueOf(t.toUpperCase(java.util.Locale.ROOT));
    }

    private static List<String> sheetNames(RuleValue sheets) {
        return list(sheets).stream().map(s -> text(list(s).getFirst())).toList();
    }

    private static Map<String, List<String>> sheetValues(RuleValue sheets) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (RuleValue s : list(sheets)) {
            out.put(text(list(s).getFirst()), list(list(s).get(1)).stream().map(FormulaBlocksTest::text).toList());
        }
        return out;
    }

    private static final RuleValue PARAMS = ConstraintBonds.parse("{\"MODE\":{\"a\":{\"tier\":2,\"n\":1},\"b\":{\"tier\":1,\"weight\":0.5}},"
            + "\"PAYLOAD\":{\"2\":{\"0\":7,\"size\":4},\"10\":{\"big\":3},\"x\":{\"unicode\":1}},\"Charge\":{}}", "params");
    private static final List<String> SHEETS = List.of("MODE", "PAYLOAD", "Charge");

    @Test
    void theBlocksCompileDropAndMoveAsTheLegacyPageDid() throws IOException {
        Map<String, Integer> seen = new LinkedHashMap<>();
        int okDrops = 0;
        int okMoves = 0;
        for (Map<String, RuleValue> g : goldens()) {
            String c = text(g.get("case"));
            seen.merge(c, 1, Integer::sum);
            switch (c) {
                case "compile" -> assertEquals(text(g.get("out")), FormulaBlocks.compile(block(g.get("tree"))), g.toString());
                case "drops" -> {
                    Block root = block(g.get("tree"));
                    List<String> paths = new ArrayList<>();
                    for (RuleValue t : list(g.get("targets"))) {
                        String path = text(list(t).getFirst());
                        paths.add(path);
                        assertTrue(FormulaBlocks.isSlot(root, path), path);
                        assertEquals(text(list(t).get(1)), FormulaBlocks.slot(root, path) == FormulaBlocks.Type.BOOL ? "bool" : "arith",
                                "the slot type the page rendered at '" + path + "'");
                    }
                    assertEquals(paths, FormulaBlocks.slots(root), "the slots in the order the page rendered them");
                    for (RuleValue d : list(g.get("drops"))) {
                        List<RuleValue> drop = list(d);
                        String path = text(drop.getFirst());
                        Kind k = kind(text(drop.get(1)));
                        boolean ok = ((RuleValue.Bool) drop.get(2)).value();
                        assertEquals(ok, FormulaBlocks.fits(root, path, k), k + " at '" + path + "' of " + g.get("tree"));
                        if (ok) {
                            okDrops++;
                            Block after = FormulaBlocks.with(root, path, FormulaBlocks.newNode(k, SHEETS, s -> FormulaBlocks.paramsOf(s, PARAMS)));
                            assertEquals(text(drop.get(3)), FormulaBlocks.compile(after));
                        }
                    }
                    for (RuleValue mv : list(g.get("moves"))) {
                        List<RuleValue> move = list(mv);
                        String from = text(move.getFirst());
                        String to = text(move.get(1));
                        boolean ok = ((RuleValue.Bool) move.get(2)).value();
                        assertEquals(ok, FormulaBlocks.canMove(root, from, to), "move '" + from + "' to '" + to + "' of " + g.get("tree"));
                        if (ok) {
                            okMoves++;
                            assertEquals(block(move.get(3)), FormulaBlocks.move(root, from, to));
                        }
                    }
                }
                case "newNode" -> {
                    RuleValue params = g.get("params");
                    assertEquals(block(g.get("out")), FormulaBlocks.newNode(kind(text(g.get("kind"))), sheetNames(g.get("sheets")),
                            s -> FormulaBlocks.paramsOf(s, params)));
                }
                case "paramsOf" -> assertEquals(list(g.get("out")).stream().map(FormulaBlocksTest::text).toList(),
                        FormulaBlocks.paramsOf(text(g.get("sheet")), g.get("params")));
                case "opsOf" -> assertEquals(list(g.get("out")).stream().map(o -> list(o).stream().map(FormulaBlocksTest::text).toList()).toList(),
                        FormulaBlocks.ops(kind(text(g.get("kind")))).stream().map(o -> List.of(o.value(), o.label())).toList());
                case "defaultVal" -> assertEquals(ConstraintBonds.pythonJson(g.get("out"), false, false),
                        ConstraintBonds.pythonJson(ConditionBlocks.defaultOperand(text(g.get("sheet")), Condition.Op.ofToken(text(g.get("op"))),
                                sheetValues(g.get("sheets"))), false, false), g.toString());
                case "paramsRow" -> assertEquals(ConstraintBonds.pythonJson(ConstraintBonds.parse(text(g.get("out")), "out"), false, false),
                        ConstraintBonds.pythonJson(new RuleValue.MapValue(ConstraintEditing.parseRow(text(g.get("text")))), false, false),
                        text(g.get("text")));
                case "paramsShow" -> assertEquals(text(g.get("out")), ConstraintEditing.of("{\"version\": 1, \"params\": {\"S\": {\"v\": "
                        + text(g.get("json")) + "}}, \"constraints\": []}", "t").paramsText("S", "v"), text(g.get("json")));
                case "cleanParams" -> assertEquals(ConstraintBonds.pythonJson(ConstraintBonds.parse(text(g.get("out")), "out"), false, false),
                        ConstraintBonds.pythonJson(ConstraintEditing.cleanParams(ConstraintBonds.parse(text(g.get("params")), "in")), false, false));
                default -> throw new AssertionError("unknown case " + c);
            }
        }
        assertEquals(Map.of("compile", 300, "drops", 40, "newNode", 36, "paramsOf", 9, "opsOf", 3, "defaultVal", 102, "paramsRow", 32,
                "paramsShow", 5, "cleanParams", 3), seen);
        assertTrue(okDrops > 300 && okMoves > 50, okDrops + " drops, " + okMoves + " moves were allowed");
    }

    private static final Set<String> CANONICAL = Set.of("0", "1", "-1", "2.5", "10", "-0.25");

    private static boolean canonical(Block b) {
        return switch (b) {
            case null -> true;
            case FormulaBlocks.Num n -> CANONICAL.contains(n.val());
            case Binary x -> canonical(x.a()) && canonical(x.b());
            case FormulaBlocks.Not x -> canonical(x.x());
            case FormulaBlocks.Param p -> p.param().matches("[A-Za-z_][A-Za-z0-9_]*");
        };
    }

    @Test
    void aCompiledFormulaOpensAsTheSameBlocksAndTextsWithoutABlockStayText() throws IOException {
        int roundTrips = 0;
        for (Map<String, RuleValue> g : goldens()) {
            if (text(g.get("case")).equals("compile") && g.get("out") instanceof RuleValue.Text out && canonical(block(g.get("tree")))) {
                assertEquals(block(g.get("tree")), FormulaBlocks.parse(out.value()), out.value());
                roundTrips++;
            }
        }
        assertTrue(roundTrips > 40, roundTrips + " round trips");
        assertNull(FormulaBlocks.parse("  "), "an empty text is an empty root slot");
        assertEquals(FormulaBlocks.parse("((a.x > 1) and (b.y < 2)) and (c.z == 3)"), FormulaBlocks.parse("a.x > 1 and b.y < 2 and c.z == 3"),
                "a longer and/or chain nests to the left");
        assertEquals("not (((MODE.tier * PAYLOAD.size) >= -1))", FormulaBlocks.compile(FormulaBlocks.parse("not MODE.tier * PAYLOAD.size >= -1")));
        assertEquals("((MODE.n - -2.5) != 0)", FormulaBlocks.compile(FormulaBlocks.parse("MODE.n - -2.5 != 0")));
        Map<String, String> refused = Map.of(
                "MODE.value == 'a'", "the text 'a' (the number block holds numbers only)",
                "abs(MODE.n) > 1", "the function abs()",
                "MODE.n / 2 > 1", "the operator '/' (the arithmetic block has + - *)",
                "1 < MODE.n < 3", "a comparison chain (compare blocks take two numbers each)",
                "MODE.n", "the number MODE.n where a condition belongs",
                "MODE.n > 1 and 2", "the number 2 where a condition belongs",
                "MODE.n in [1, 2]", "the operator 'in'",
                "(MODE.n > 1) > 0", "the condition (MODE.n > 1) where a number belongs",
                "MODE > 1", "the bare name MODE (a parameter is written sheet.param)",
                "MODE.n > True", "the value True");
        refused.forEach((formula, why) -> assertEquals(why, assertThrows(FormulaBlocks.NotBlocks.class, () -> FormulaBlocks.parse(formula)).getMessage(),
                formula));
        assertThrows(ExprParser.SyntaxError.class, () -> FormulaBlocks.parse("MODE.n >"));
        assertThrows(ExprParser.SyntaxError.class, () -> FormulaBlocks.parse("(MODE.n > 007)"), "Python 3 refuses 007, which a legacy number block let through");
        assertThrows(ExprParser.SyntaxError.class, () -> FormulaBlocks.parse("(PAYLOAD.0 > 1)"),
                "nor a parameter named 0, which the legacy picker offered from the params");
    }

    @Test
    void conditionTreesNestAtAnyDepthAndStayTreesTheSieveReads() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("channel", List.of("web", "mobile"));
        values.put("payment", List.of("card", "wallet"));
        values.put("dup", List.of("dup"));
        RuleValue leaf = ConditionBlocks.newLeaf(values);
        assertEquals("{\"sheet\": \"channel\", \"eq\": \"web\"}", ConstraintBonds.pythonJson(leaf, false, false), "the legacy first term");
        ConditionBlocks.Added first = ConditionBlocks.add(null, List.of(), leaf);
        assertEquals(List.of(), first.path());
        ConditionBlocks.Added second = ConditionBlocks.add(first.root(), List.of(),
                ConditionBlocks.leaf("payment", Condition.Op.IN, ConditionBlocks.defaultOperand("payment", Condition.Op.IN, values)));
        assertEquals(List.of(1), second.path());
        RuleValue root = ConditionBlocks.negate(second.root(), List.of(1));
        root = ConditionBlocks.add(root, List.of(1, 0), ConditionBlocks.leaf("dup", Condition.Op.PRESENT, new RuleValue.Bool(true))).root();
        root = ConditionBlocks.join(root, List.of(1, 0), "any");
        assertEquals("{\"all\": [{\"sheet\": \"channel\", \"eq\": \"web\"}, {\"not\": {\"any\": [{\"sheet\": \"payment\", \"in\": [\"card\"]}, "
                + "{\"sheet\": \"dup\", \"present\": true}]}}]}", ConstraintBonds.pythonJson(root, false, false));
        assertEquals("(channel=web) and (not ((payment∈{card}) or (dup present)))", ConstraintBonds.describeCondition(root));
        assertEquals(List.of(List.of(), List.of(0), List.of(1), List.of(1, 0), List.of(1, 0, 0), List.of(1, 0, 1)), ConditionBlocks.paths(root));
        assertEquals("dup present", ConditionBlocks.label(ConditionBlocks.at(root, List.of(1, 0, 1))));
        RuleValue swapped = ConditionBlocks.shift(root, List.of(1, 0, 1), -1);
        assertEquals("dup present", ConditionBlocks.label(ConditionBlocks.at(swapped, List.of(1, 0, 0))));
        assertEquals(swapped, ConditionBlocks.shift(swapped, List.of(1, 0, 0), -1), "the first part stays first");
        RuleValue fewer = ConditionBlocks.remove(root, List.of(1, 0, 0));
        assertEquals("{\"all\": [{\"sheet\": \"channel\", \"eq\": \"web\"}, {\"not\": {\"sheet\": \"dup\", \"present\": true}}]}",
                ConstraintBonds.pythonJson(fewer, false, false), "an any left with one part becomes that part");
        RuleValue unwrapped = ConditionBlocks.negate(fewer, List.of(1));
        assertEquals("{\"all\": [{\"sheet\": \"channel\", \"eq\": \"web\"}, {\"sheet\": \"dup\", \"present\": true}]}",
                ConstraintBonds.pythonJson(unwrapped, false, false));
        assertEquals("{\"sheet\": \"channel\", \"eq\": \"web\"}", ConstraintBonds.pythonJson(ConditionBlocks.remove(fewer, List.of(1, 0)), false, false),
                "removing the part of a not removes the not, and the all left with one part becomes it");
        assertNull(ConditionBlocks.remove(leaf, List.of()));
        RuleValue counted = ConditionBlocks.leaf("payment", Condition.Op.COUNT_GE, ConditionBlocks.defaultOperand("payment", Condition.Op.COUNT_GE, values));
        assertEquals(new ConditionBlocks.Leaf("payment", Condition.Op.COUNT_GE, new RuleValue.Int(java.math.BigInteger.ONE)), ConditionBlocks.leaf(counted));
        assertEquals("[\"wallet\"]", ConstraintBonds.pythonJson(ConditionBlocks.defaultOperand("channel", Condition.Op.HAS_ANY,
                Map.of("channel", List.of("wallet"))), false, false), "operators the legacy builder never offered take an operand of their kind");
        assertEquals("|payment|≥1", ConditionBlocks.label(counted));
    }
}
