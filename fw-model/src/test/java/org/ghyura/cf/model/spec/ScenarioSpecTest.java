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

package org.ghyura.cf.model.spec;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioSpecTest {

    private static final SourceLocation HERE = SourceLocation.of("a.json", 3, 5);
    private static final SourceLocation THERE = SourceLocation.ofPath("b.xlsx", "Sheet1!B2");

    private static LocatedText at(String text, SourceLocation where) {
        return new LocatedText(text, where);
    }

    /** A small scenario that touches every part of the canonical text. */
    private static ScenarioSpec sample(String source, SourceLocation where) {
        return new ScenarioSpec(source, List.of(
                new SheetSpec("A", 1, List.of(
                        ValueCell.of("x", ValueKind.TEXT, where),
                        ValueCell.of("", ValueKind.EMPTY, where).asOptional(),
                        ValueCell.of("raw  y", ValueKind.TEXT, where).refinedTo("raw y")), where),
                new SheetSpec("B", 2, List.of(ValueCell.of("FW_VAR = 2", ValueKind.EXIT_CODE_VAR, where)), where),
                new SheetSpec("Empty", 3, List.of(), where)),
                new ControlSpec(List.of(new SheetDecoration("A", "pre", "end", where)), List.of(at("code", where)),
                        List.of(at("a b", where)), List.of(new CustomVar(2, "d", where))),
                true,
                List.of(new SeqRow(0, List.of(at("A", where), at("FW_Combi(1)", where))), new SeqRow(2, List.of(at("B", where)))));
    }

    private static final String EXPECTED = "cf.scenario/1\n"
            + "SHEET\t1\tA\n"
            + "VALUE\tt\t-\t-\tx\n"
            + "VALUE\te\tO\t-\t\n"
            + "VALUE\tt\t-\tR\traw y\n"
            + "SHEET\t2\tB\n"
            + "VALUE\tx\t-\t-\tFW_VAR = 2\n"
            + "SHEET\t3\tEmpty\n"
            + "SHEETNAME\tA\tpre\tend\n"
            + "RUNME\tcode\n"
            + "ARG\ta b\n"
            + "CUSTOM\t2\td\n"
            + "SEQ\tpresent\n"
            + "ROW\t0\tA\tFW_Combi(1)\n"
            + "ROW\t2\tB\n";

    @Test
    void theCanonicalTextIsExactlyThisForASmallScenario() {
        assertEquals(EXPECTED, sample("a.json", HERE).canonicalText());
    }

    @Test
    void locationsAndTheSourceNameAreNotPartOfTheIdentity() {
        ScenarioSpec first = sample("a.json", HERE);
        ScenarioSpec second = sample("other.xlsx", THERE);
        assertNotEquals(first, second, "the two specs really differ in source and locations, or this test proves nothing");
        assertEquals(first.canonicalText(), second.canonicalText());
        assertEquals(first.contentDigest(), second.contentDigest());
    }

    @Test
    void everyLogicalFieldChangesTheIdentity() {
        ScenarioSpec base = sample("a.json", HERE);
        List<SheetSpec> sheets = base.sheets();
        SheetSpec a = sheets.get(0);
        SheetSpec b = sheets.get(1);
        SheetSpec empty = sheets.get(2);
        ControlSpec controls = base.controls();
        List<SeqRow> rows = base.sequence();

        Map<String, ScenarioSpec> variants = new LinkedHashMap<>();
        variants.put("value text", withSheets(base, List.of(sheetWith(a, 0, ValueCell.of("x2", ValueKind.TEXT, HERE)), b, empty)));
        variants.put("value kind", withSheets(base, List.of(sheetWith(a, 0, ValueCell.of("x", ValueKind.FORMULA, HERE)), b, empty)));
        variants.put("optional flag", withSheets(base, List.of(sheetWith(a, 0, ValueCell.of("x", ValueKind.TEXT, HERE).asOptional()), b, empty)));
        variants.put("refined flag", withSheets(base, List.of(sheetWith(a, 2, ValueCell.of("raw y", ValueKind.TEXT, HERE)), b, empty)));
        variants.put("sheet name", withSheets(base, List.of(new SheetSpec("A2", 1, a.cells(), HERE), b, empty)));
        variants.put("sheet order", withSheets(base, List.of(new SheetSpec("B", 1, b.cells(), HERE), new SheetSpec("A", 2, a.cells(), HERE), empty)));
        variants.put("extra sheet", withSheets(base, List.of(a, b, empty, new SheetSpec("More", 4, List.of(), HERE))));
        variants.put("value dropped", withSheets(base, List.of(new SheetSpec("A", 1, a.cells().subList(0, 2), HERE), b, empty)));
        variants.put("decoration prefix", withControls(base, new ControlSpec(List.of(new SheetDecoration("A", "pre2", "end", HERE)),
                controls.runMeFirstOnce(), controls.arguments(), controls.customVars())));
        variants.put("decoration ending", withControls(base, new ControlSpec(List.of(new SheetDecoration("A", "pre", "end2", HERE)),
                controls.runMeFirstOnce(), controls.arguments(), controls.customVars())));
        variants.put("decoration sheet", withControls(base, new ControlSpec(List.of(new SheetDecoration("B", "pre", "end", HERE)),
                controls.runMeFirstOnce(), controls.arguments(), controls.customVars())));
        variants.put("run once", withControls(base, new ControlSpec(controls.sheetNames(), List.of(at("code2", HERE)),
                controls.arguments(), controls.customVars())));
        variants.put("arguments", withControls(base, new ControlSpec(controls.sheetNames(), controls.runMeFirstOnce(),
                List.of(at("a  b", HERE)), controls.customVars())));
        variants.put("custom index", withControls(base, new ControlSpec(controls.sheetNames(), controls.runMeFirstOnce(),
                controls.arguments(), List.of(new CustomVar(3, "d", HERE)))));
        variants.put("custom declaration", withControls(base, new ControlSpec(controls.sheetNames(), controls.runMeFirstOnce(),
                controls.arguments(), List.of(new CustomVar(2, "e", HERE)))));
        variants.put("sequence cell", withSequence(base, true, List.of(new SeqRow(0, List.of(at("A", HERE), at("FW_Combi(2)", HERE))), rows.get(1))));
        variants.put("sequence ordinal", withSequence(base, true, List.of(new SeqRow(1, rows.get(0).cells()), rows.get(1))));
        variants.put("sequence row dropped", withSequence(base, true, List.of(rows.get(0))));
        variants.put("sequence sheet absent", withSequence(base, false, List.of()));
        variants.put("sequence sheet present but empty", withSequence(base, true, List.of()));

        Set<String> digests = new HashSet<>();
        digests.add(base.contentDigest());
        for (Map.Entry<String, ScenarioSpec> variant : variants.entrySet()) {
            assertNotEquals(base.canonicalText(), variant.getValue().canonicalText(), variant.getKey());
            assertTrue(digests.add(variant.getValue().contentDigest()), "digest of '" + variant.getKey() + "' repeats another variant");
        }
        assertEquals(variants.size() + 1, digests.size());
    }

    private static SheetSpec sheetWith(SheetSpec sheet, int index, ValueCell replacement) {
        List<ValueCell> cells = new ArrayList<>(sheet.cells());
        cells.set(index, replacement);
        return new SheetSpec(sheet.name(), sheet.key(), cells, sheet.location());
    }

    private static ScenarioSpec withSheets(ScenarioSpec base, List<SheetSpec> sheets) {
        return new ScenarioSpec(base.source(), sheets, base.controls(), base.hasSequenceSheet(), base.sequence());
    }

    private static ScenarioSpec withControls(ScenarioSpec base, ControlSpec controls) {
        return new ScenarioSpec(base.source(), base.sheets(), controls, base.hasSequenceSheet(), base.sequence());
    }

    private static ScenarioSpec withSequence(ScenarioSpec base, boolean present, List<SeqRow> rows) {
        return new ScenarioSpec(base.source(), base.sheets(), base.controls(), present, rows);
    }

    @Test
    void theDigestIsTheSha256OfTheCanonicalTextComputedIndependently() throws Exception {
        ScenarioSpec spec = sample("a.json", HERE);
        StringBuilder expected = new StringBuilder();
        for (byte octet : MessageDigest.getInstance("SHA-256").digest(EXPECTED.getBytes(StandardCharsets.UTF_8))) {
            expected.append(String.format("%02x", octet & 0xFF));
        }
        assertEquals(expected.toString(), spec.contentDigest());
        assertEquals(64, spec.contentDigest().length());
    }

    @Test
    void aValueCannotForgeLinesOrFieldsOfTheCanonicalText() {
        String evil = "x\nSHEET\t9\tevil\rVALUE\tt\t-\t-\ty\\";
        ScenarioSpec spec = new ScenarioSpec("s", List.of(new SheetSpec("A", 1,
                List.of(ValueCell.of(evil, ValueKind.TEXT, HERE)), HERE)), ControlSpec.EMPTY, false, List.of());
        List<String> lines = spec.canonicalText().lines().toList();
        assertEquals(4, lines.size(), lines.toString());
        assertEquals("VALUE\tt\t-\t-\tx\\nSHEET\\t9\\tevil\\rVALUE\\tt\\t-\\t-\\ty\\\\", lines.get(2));
        assertEquals("SEQ\tabsent", lines.get(3));
    }

    /** Review finding BM01: equal specs but for one cell holding U+D800 versus '?' had one content digest. */
    @Test
    void unpairedSurrogatesKeepTheirOwnIdentityInEveryTextField() {
        String high = String.valueOf((char) 0xD800);
        String low = String.valueOf((char) 0xDC00);
        String pair = new String(Character.toChars(0x1F600));
        Set<String> digests = new HashSet<>();
        Set<String> texts = new HashSet<>();
        for (String value : List.of(high, low, "?", "�", "\\ud800", pair, high + "?", "")) {
            ScenarioSpec spec = new ScenarioSpec("review", List.of(new SheetSpec("A", 1,
                    List.of(ValueCell.of(value, ValueKind.TEXT, HERE)), HERE)),
                    new ControlSpec(List.of(new SheetDecoration("A", value, value, HERE)), List.of(at(value, HERE)),
                            List.of(at(value, HERE)), List.of(new CustomVar(1, value, HERE))),
                    true, List.of(new SeqRow(0, List.of(at("A", HERE), at(value, HERE)))));
            assertTrue(texts.add(spec.canonicalText()), () -> "canonical text repeats for " + Canonical.escape(value));
            assertTrue(digests.add(spec.contentDigest()), () -> "digest repeats for " + Canonical.escape(value));
        }
        // the reviewer's exact pair, value only
        ScenarioSpec lone = new ScenarioSpec("review", List.of(new SheetSpec("A", 1,
                List.of(ValueCell.of(high, ValueKind.TEXT, SourceLocation.NONE)), SourceLocation.NONE)),
                ControlSpec.EMPTY, false, List.of());
        ScenarioSpec question = new ScenarioSpec("review", List.of(new SheetSpec("A", 1,
                List.of(ValueCell.of("?", ValueKind.TEXT, SourceLocation.NONE)), SourceLocation.NONE)),
                ControlSpec.EMPTY, false, List.of());
        assertNotEquals(lone.contentDigest(), question.contentDigest());
        assertNotEquals("7f28d2d45655b28bb8b321f9c56fa2f4663cac3310f3654aafa75b4cc29f8922", lone.contentDigest(),
                "the colliding digest the review reproduced belongs to '?' only");
        assertEquals("7f28d2d45655b28bb8b321f9c56fa2f4663cac3310f3654aafa75b4cc29f8922", question.contentDigest());
    }

    @Test
    void aSpecRefusesWhatWouldMakeItAmbiguous() {
        SheetSpec one = new SheetSpec("A", 1, List.of(), HERE);
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSpec("s", List.of(new SheetSpec("A", 2, List.of(), HERE)),
                ControlSpec.EMPTY, false, List.of()), "keys start at 1");
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSpec("s", List.of(one, new SheetSpec("B", 1, List.of(), HERE)),
                ControlSpec.EMPTY, false, List.of()), "keys are consecutive");
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSpec("s", List.of(one, new SheetSpec("B", 3, List.of(), HERE)),
                ControlSpec.EMPTY, false, List.of()), "no gap in the keys");
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSpec("s", List.of(new SheetSpec("FW_Seq", 1, List.of(), HERE)),
                ControlSpec.EMPTY, false, List.of()), "a control sheet name is not a data sheet");
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSpec("s", List.of(one, new SheetSpec("a", 2, List.of(), HERE)),
                ControlSpec.EMPTY, false, List.of()), "names are unique ignoring case");
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSpec("s", List.of(one), ControlSpec.EMPTY, false,
                List.of(new SeqRow(0, List.of(at("A", HERE))))), "rows need a FW_Seq sheet");
        SeqRow row0 = new SeqRow(0, List.of(at("A", HERE)));
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSpec("s", List.of(one), ControlSpec.EMPTY, true, List.of(row0, row0)),
                "ordinals strictly increase");
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSpec("s", List.of(one), ControlSpec.EMPTY, true,
                List.of(new SeqRow(2, List.of(at("A", HERE))), row0)), "ordinals strictly increase");
        new ScenarioSpec("s", List.of(one), ControlSpec.EMPTY, true, List.of(row0, new SeqRow(5, List.of(at("A", HERE)))));
    }

    @Test
    void theElementsRefuseWhatTheyCannotRepresent() {
        assertThrows(IllegalArgumentException.class, () -> new SheetSpec(" ", 1, List.of(), HERE));
        assertThrows(IllegalArgumentException.class, () -> new SheetSpec("A", 0, List.of(), HERE));
        assertThrows(IllegalArgumentException.class, () -> new SeqRow(-1, List.of(at("A", HERE))));
        assertThrows(IllegalArgumentException.class, () -> new SeqRow(0, List.of()));
        assertThrows(NullPointerException.class, () -> new ValueCell(null, ValueKind.TEXT, false, false, HERE));
        assertThrows(NullPointerException.class, () -> new ValueCell("x", null, false, false, HERE));
        assertThrows(NullPointerException.class, () -> new SheetDecoration("A", null, "", HERE));
        assertThrows(NullPointerException.class, () -> new CustomVar(1, null, HERE));
    }

    @Test
    void lookupsAndCountsFollowTheSheetsAndValues() {
        ScenarioSpec spec = sample("a.json", HERE);
        assertEquals(3, spec.sheetCount());
        assertEquals(4, spec.valueCount());
        assertEquals("B", spec.sheet("B").orElseThrow().name());
        assertTrue(spec.sheet("b").isEmpty(), "lookup by exact name");
        assertEquals("Empty", spec.sheetByKey(3).orElseThrow().name());
        assertTrue(spec.sheetByKey(0).isEmpty());
        assertTrue(spec.sheetByKey(4).isEmpty());
    }

    @Test
    void theExitCodeCellsOfASheetAreCountedByTheirKindOnly() {
        ScenarioSpec spec = sample("a.json", HERE);
        assertEquals(0, spec.sheet("A").orElseThrow().exitCodeVarCount());
        assertEquals(1, spec.sheet("B").orElseThrow().exitCodeVarCount());
        SheetSpec two = new SheetSpec("C", 1, List.of(ValueCell.of("FW_VAR = 1", ValueKind.EXIT_CODE_VAR, HERE),
                ValueCell.of("FW_VAR = 1", ValueKind.TEXT, HERE), ValueCell.of("FW_VAR = 1", ValueKind.EXIT_CODE_VAR, HERE)), HERE);
        assertEquals(2, two.exitCodeVarCount());
    }

    @Test
    void optionalAndRefinedAreSeparateImmutableFacts() {
        ValueCell plain = ValueCell.of("a  b", ValueKind.FILE, HERE);
        ValueCell optional = plain.asOptional();
        ValueCell refined = plain.refinedTo("a b");
        assertFalse(plain.optional());
        assertFalse(plain.refined());
        assertTrue(optional.optional());
        assertFalse(optional.refined());
        assertEquals("a  b", optional.text());
        assertTrue(refined.refined());
        assertFalse(refined.optional());
        assertEquals("a b", refined.text());
        assertEquals(ValueKind.FILE, refined.kind());
        assertTrue(optional.refinedTo("q").optional(), "refining keeps the optional flag");
        assertTrue(refined.asOptional().refined(), "marking optional keeps the refined flag");
        assertEquals("a  b", plain.text(), "the original is untouched");
    }

    @Test
    void theKindCodesUsedInTheCanonicalTextAreDistinct() {
        Set<Character> codes = new HashSet<>();
        for (ValueKind kind : ValueKind.values()) {
            assertTrue(codes.add(kind.code()), kind.name());
        }
        assertEquals(ValueKind.values().length, codes.size());
    }
}
