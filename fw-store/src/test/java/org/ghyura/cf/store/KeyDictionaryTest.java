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

package org.ghyura.cf.store;

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.key.KeyCensus;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyEncodingPolicy;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.CustomVar;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetDecoration;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyDictionaryTest {

    private static final SourceLocation HERE = SourceLocation.of("scenario.json");
    private static final long BIG = 1L << 30;

    /** One data sheet S1, S2, ... per list; the values are the given texts, all plain. */
    @SafeVarargs
    private static ScenarioSpec spec(List<String>... sheets) {
        List<List<String>> lists = new ArrayList<>();
        for (List<String> sheet : sheets) {
            lists.add(sheet);
        }
        return specOf(lists);
    }

    private static ScenarioSpec specOf(List<List<String>> sheets) {
        List<SheetSpec> list = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            List<ValueCell> cells = new ArrayList<>();
            for (String text : sheets.get(i)) {
                cells.add(ValueCell.of(text, ValueKind.TEXT, HERE));
            }
            list.add(new SheetSpec("S" + (i + 1), i + 1, cells, HERE));
        }
        return new ScenarioSpec("scenario.json", list, ControlSpec.EMPTY, false, List.of());
    }

    private static ScenarioSpec sized(int sheets, int valuesInFirst) {
        List<List<String>> lists = new ArrayList<>();
        for (int i = 0; i < sheets; i++) {
            lists.add(i == 0 ? Collections.nCopies(valuesInFirst, "v") : List.of());
        }
        return specOf(lists);
    }

    private static KeyDictionary build(ScenarioSpec spec, int virtual, KeyEncodingPolicy policy) {
        return KeyDictionary.build(spec, virtual, policy, new MemoryBudget(BIG));
    }

    private static String hex(byte[] octets) {
        StringBuilder out = new StringBuilder();
        for (byte b : octets) {
            out.append(out.isEmpty() ? "" : " ").append(String.format("%02X", b & 0xFF));
        }
        return out.toString();
    }

    // ---- labels -----------------------------------------------------------------------------------------------

    @Test
    void valuesAreLabelledFromSPlusOneInSheetOrderAcrossSheetsAndRepeatsKeepTheirOwnLabel() {
        // S = 3 sheets, C = 5 values: A has x y, B is empty, C has x z x
        try (KeyDictionary dictionary = build(spec(List.of("x", "y"), List.of(), List.of("x", "z", "x")), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertEquals(3, dictionary.sheetCount());
            assertEquals(5, dictionary.valueCount());
            assertEquals(4, dictionary.firstLabel());
            assertEquals(8, dictionary.lastLabel());
            assertArrayEquals(new int[]{4, 5}, dictionary.labelsOf(1));
            assertArrayEquals(new int[]{}, dictionary.labelsOf(2));
            assertArrayEquals(new int[]{6, 7, 8}, dictionary.labelsOf(3));
            assertEquals("x", dictionary.value(4).text());
            assertEquals("x", dictionary.value(6).text(), "the same text under another label is another key");
            assertEquals("x", dictionary.value(8).text());
            assertEquals("z", dictionary.value(7).text());
            assertEquals(List.of(1, 1, 3, 3, 3), List.of(4, 5, 6, 7, 8).stream().map(dictionary::sheetOfLabel).toList());
            assertFalse(dictionary.contains(3));
            assertTrue(dictionary.contains(4));
            assertTrue(dictionary.contains(8));
            assertFalse(dictionary.contains(9));
        }
    }

    @Test
    void aValueIsTheSpecsOwnObjectWithItsKindFlagsAndLocation() {
        ScenarioSpec spec = spec(List.of("a", "b"));
        try (KeyDictionary dictionary = build(spec, 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertSame(spec.sheets().getFirst().cells().get(1), dictionary.value(3));
        }
    }

    @Test
    void sheetOfLabelSkipsEmptySheetsAtTheStartMiddleAndEnd() {
        try (KeyDictionary dictionary = build(spec(List.of(), List.of("a"), List.of(), List.of(), List.of("b", "c"), List.of()), 0,
                KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertEquals(7, dictionary.firstLabel());
            assertEquals(2, dictionary.sheetOfLabel(7));
            assertEquals(5, dictionary.sheetOfLabel(8));
            assertEquals(5, dictionary.sheetOfLabel(9));
            assertArrayEquals(new int[]{7}, dictionary.labelsOf(2));
            assertArrayEquals(new int[]{}, dictionary.labelsOf(6));
        }
    }

    @Test
    void aLabelThatNamesNoValueIsRefusedWithTheLabelInTheDiagnostic() {
        try (KeyDictionary dictionary = build(spec(List.of("a", "b")), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            for (int label : new int[]{Integer.MIN_VALUE, -5, 0, 1, 4, 5, 128, Integer.MAX_VALUE}) {
                FrameworkException e = assertThrows(FrameworkException.class, () -> dictionary.value(label), "label " + label);
                assertEquals(ErrorCode.KEY_LABEL_OUT_OF_RANGE, e.code());
                assertEquals(Integer.toString(label), e.primary().attributes().get("label"));
                assertTrue(e.primary().message().contains("2..3"), e.primary().message());
            }
            assertEquals(ErrorCode.KEY_LABEL_OUT_OF_RANGE, assertThrows(FrameworkException.class, () -> dictionary.sheetOfLabel(9)).code());
        }
        try (KeyDictionary empty = build(spec(List.of()), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> empty.value(2));
            assertTrue(e.primary().message().contains("no values"), e.primary().message());
        }
    }

    @Test
    void aSheetKeyOutsideOneToSIsAProgrammingError() {
        try (KeyDictionary dictionary = build(spec(List.of("a"), List.of("b")), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertThrows(IllegalArgumentException.class, () -> dictionary.labelsOf(0));
            assertThrows(IllegalArgumentException.class, () -> dictionary.labelsOf(3));
        }
    }

    @Test
    void aScenarioWithoutSheetsOrValuesGivesAnEmptyDictionary() {
        try (KeyDictionary dictionary = build(spec(), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertEquals(0, dictionary.valueCount());
            assertEquals(1, dictionary.firstLabel());
            assertEquals(0, dictionary.lastLabel());
            assertEquals(0, dictionary.census().count());
            assertEquals(KeyEncoding.SIGNED_BYTE, dictionary.encoding());
        }
    }

    // ---- the census and the encoding --------------------------------------------------------------------------

    @Test
    void theFullSignedByteRangeIsUsedBeforeWideningOrRefusing() {
        // Preserve 2..127 for 126 values; larger inputs use the rest of the signed window before widening.
        try (KeyDictionary fits = build(sized(1, 126), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertEquals(KeyEncoding.SIGNED_BYTE, fits.encoding());
            assertEquals(127, fits.census().max());
            assertEquals(1, fits.census().min());
        }
        for (KeyEncodingPolicy policy : List.of(KeyEncodingPolicy.AUTO_PRESERVE, KeyEncodingPolicy.SIGNED_BYTE)) {
            try (KeyDictionary full = build(sized(1, 255), 0, policy)) {
                assertEquals(KeyEncoding.SIGNED_BYTE, full.encoding());
                assertEquals(-127, full.firstLabel());
                assertEquals(127, full.lastLabel());
                assertEquals(255, full.rowOf(1).payloadLength());
                for (int label = -127; label <= 127; label++) {
                    assertEquals("v", full.value(label).text());
                    assertEquals(1, full.sheetOfLabel(label));
                }
                assertFalse(full.contains(-128));
            }
        }
        try (KeyDictionary widened = build(sized(1, 256), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertEquals(KeyEncoding.PACKED_LEGACY16, widened.encoding());
        }
        FrameworkException strict = assertThrows(FrameworkException.class, () -> build(sized(1, 256), 0, KeyEncodingPolicy.SIGNED_BYTE));
        assertEquals(ErrorCode.KEY_CENSUS_OVERFLOW, strict.code());
        assertEquals("255", strict.primary().attributes().get("capacity"));
        assertEquals(KeyEncoding.PACKED_LEGACY16, build(sized(1, 5), 0, KeyEncodingPolicy.PACKED_LEGACY16).encoding());
    }

    @Test
    void thePackedEncodingAlsoUsesItsNegativeRangeBeforeRefusing() {
        try (KeyDictionary last = build(sized(1, 32_766), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertEquals(KeyEncoding.PACKED_LEGACY16, last.encoding());
            assertEquals(32_767, last.census().max());
        }
        try (KeyDictionary full = build(sized(1, 65_535), 0, KeyEncodingPolicy.PACKED_LEGACY16)) {
            assertEquals(-32_767, full.firstLabel());
            assertEquals(32_767, full.lastLabel());
            assertEquals("v", full.value(-128).text(), "-128 is ordinary data in the wider encoding");
        }
        for (KeyEncodingPolicy policy : KeyEncodingPolicy.values()) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> build(sized(1, 65_536), 0, policy), policy.name());
            assertEquals(ErrorCode.KEY_CENSUS_OVERFLOW, e.code(), policy.name());
        }
    }

    @Test
    void virtualSheetsTakeSheetKeysAndCountInTheCensus() {
        // 2 data sheets, no values: the sheet keys alone decide
        try (KeyDictionary one = build(sized(2, 0), 125, KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertEquals(127, one.census().max(), "sheet keys 1..127");
            assertEquals(KeyEncoding.SIGNED_BYTE, one.encoding());
            assertEquals(125, one.virtualSheetCount());
        }
        try (KeyDictionary full = build(sized(2, 0), 253, KeyEncodingPolicy.SIGNED_BYTE)) {
            assertEquals(-127, full.sheetLabel(1));
            assertEquals(127, full.sheetLabel(255));
            assertEquals(KeyEncoding.SIGNED_BYTE, full.encoding());
            assertEquals(255, full.census().count(), "data and virtual sheets share one sheet namespace");
        }
        try (KeyDictionary wide = build(sized(2, 0), 254, KeyEncodingPolicy.AUTO_PRESERVE)) {
            assertEquals(KeyEncoding.PACKED_LEGACY16, wide.encoding());
        }
    }

    @Test
    void theCensusCountsSheetKeysAndValueLabelsTogether() {
        try (KeyDictionary dictionary = build(spec(List.of("a", "b"), List.of("c")), 2, KeyEncodingPolicy.AUTO_PRESERVE)) {
            // sheet keys 1..4 (two data, two virtual) and value labels 3..5
            assertEquals(4 + 3, dictionary.census().count());
            assertEquals(1, dictionary.census().min());
            assertEquals(5, dictionary.census().max());
        }
    }

    @Test
    void rowsOfTheDictionaryUseItsEncoding() {
        try (KeyDictionary small = build(spec(List.of("a", "b")), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            KeyRow row = small.rowOf(1);
            assertEquals(KeyEncoding.SIGNED_BYTE, row.encoding());
            assertArrayEquals(new int[]{2, 3}, row.labels());
            assertEquals("02 03", hex(row.payload()));
        }
        try (KeyDictionary wide = build(sized(1, 256), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            KeyRow row = wide.rowOf(1);
            assertEquals(KeyEncoding.PACKED_LEGACY16, row.encoding());
            assertEquals(256, row.size());
            assertEquals("00 02 00 03", hex(row.payload()).substring(0, 11));
            assertEquals("01 01", hex(row.payload()).substring(hex(row.payload()).length() - 5), "label 257 is two big-endian octets");
        }
    }

    // ---- the memory budget ------------------------------------------------------------------------------------

    @Test
    void theDictionaryReservesBeforeItBuildsAndReturnsTheReservationOnClose() {
        MemoryBudget budget = new MemoryBudget(BIG);
        KeyDictionary dictionary = KeyDictionary.build(spec(List.of("a", "b"), List.of("c")), 0, KeyEncodingPolicy.AUTO_PRESERVE, budget);
        assertTrue(dictionary.reservedBytes() > 0);
        assertEquals(dictionary.reservedBytes(), budget.reservedBytes());
        dictionary.close();
        assertEquals(0, budget.reservedBytes());
        dictionary.close();
        assertEquals(0, budget.reservedBytes(), "closing twice returns it once");
        assertThrows(IllegalStateException.class, () -> dictionary.value(3));
        assertThrows(IllegalStateException.class, () -> dictionary.labelsOf(1));
    }

    @Test
    void theReservationIsAtLeastTwoBytesPerCharacterOfEveryValueAndGrowsWithTheText() {
        MemoryBudget budget = new MemoryBudget(BIG);
        long shortText;
        long longText;
        try (KeyDictionary a = KeyDictionary.build(spec(Collections.nCopies(1_000, "0123456789")), 0, KeyEncodingPolicy.AUTO_PRESERVE, budget)) {
            shortText = a.reservedBytes();
            assertTrue(shortText >= 2L * 1_000 * 10, "conservative: two bytes per character even for Latin-1 text");
        }
        try (KeyDictionary b = KeyDictionary.build(spec(Collections.nCopies(1_000, "0123456789".repeat(100))), 0,
                KeyEncodingPolicy.AUTO_PRESERVE, budget)) {
            longText = b.reservedBytes();
            assertTrue(longText >= 2L * 1_000 * 1_000);
        }
        assertTrue(longText > shortText);
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void aBudgetTooSmallForTheDictionaryIsAResourceLimitAndKeepsNothing() {
        MemoryBudget budget = new MemoryBudget(1_000);
        FrameworkException e = assertThrows(FrameworkException.class, () -> KeyDictionary.build(spec(Collections.nCopies(100, "text")), 0,
                KeyEncodingPolicy.AUTO_PRESERVE, budget));
        assertEquals(ErrorCode.RESOURCE_LIMIT, e.code());
        assertTrue(e.primary().message().contains("key dictionary"), e.primary().message());
        assertEquals(0, budget.reservedBytes());
    }

    /**
     * Review finding BM05: the dictionary kept the whole ScenarioSpec but reserved only for its values, so a
     * 1,000,000-character FW_RunMeFirstOnce payload was kept under a 178-byte reservation. Every retained part
     * now grows the reservation by at least two bytes per character, and a budget that fits the small scenario
     * refuses the grown one with nothing reserved.
     */
    @Test
    void theReservationGrowsWithEveryPartOfTheScenarioTheDictionaryKeeps() {
        String big = "x".repeat(1_000_000);
        SourceLocation far = SourceLocation.ofPath("scenario.json", big);
        ValueCell x = ValueCell.of("x", ValueKind.TEXT, HERE);
        ScenarioSpec base = new ScenarioSpec("s", List.of(new SheetSpec("A", 1, List.of(x), HERE)), ControlSpec.EMPTY,
                true, List.of(new SeqRow(0, List.of(new LocatedText("A", HERE)))));
        Map<String, ScenarioSpec> grown = new LinkedHashMap<>();
        grown.put("run-once code", withControls(base, new ControlSpec(List.of(), List.of(new LocatedText(big, HERE)), List.of(), List.of())));
        grown.put("argument", withControls(base, new ControlSpec(List.of(), List.of(), List.of(new LocatedText(big, HERE)), List.of())));
        grown.put("custom variable", withControls(base, new ControlSpec(List.of(), List.of(), List.of(), List.of(new CustomVar(1, big, HERE)))));
        grown.put("sheet decoration", withControls(base, new ControlSpec(List.of(new SheetDecoration("A", big, "", HERE)),
                List.of(), List.of(), List.of())));
        grown.put("sheet name", new ScenarioSpec("s", List.of(new SheetSpec("A" + big, 1, List.of(x), HERE)), ControlSpec.EMPTY,
                true, base.sequence()));
        grown.put("sequence text", new ScenarioSpec("s", base.sheets(), ControlSpec.EMPTY, true,
                List.of(new SeqRow(0, List.of(new LocatedText("A", HERE), new LocatedText(big, HERE))))));
        grown.put("sequence location", new ScenarioSpec("s", base.sheets(), ControlSpec.EMPTY, true,
                List.of(new SeqRow(0, List.of(new LocatedText("A", far))))));
        grown.put("value location", new ScenarioSpec("s", List.of(new SheetSpec("A", 1, List.of(ValueCell.of("x", ValueKind.TEXT, far)),
                HERE)), ControlSpec.EMPTY, true, base.sequence()));
        grown.put("sheet location", new ScenarioSpec("s", List.of(new SheetSpec("A", 1, List.of(x), far)), ControlSpec.EMPTY,
                true, base.sequence()));
        grown.put("source name", new ScenarioSpec(big, base.sheets(), ControlSpec.EMPTY, true, base.sequence()));

        long small = KeyDictionary.retainedBytes(base);
        MemoryBudget budget = new MemoryBudget(2 * small);
        try (KeyDictionary fits = KeyDictionary.build(base, 0, KeyEncodingPolicy.AUTO_PRESERVE, budget)) {
            assertEquals(small, fits.reservedBytes());
            assertSame(base, fits.spec(), "the dictionary keeps the spec its lease covers");
        }
        for (Map.Entry<String, ScenarioSpec> part : grown.entrySet()) {
            long bytes = KeyDictionary.retainedBytes(part.getValue());
            assertTrue(bytes >= small + 2L * big.length(), part.getKey() + ": " + bytes + " < " + small + " + 2 per character");
            FrameworkException refused = assertThrows(FrameworkException.class, () -> KeyDictionary.build(part.getValue(), 0,
                    KeyEncodingPolicy.AUTO_PRESERVE, budget), part.getKey());
            assertEquals(ErrorCode.RESOURCE_LIMIT, refused.code(), part.getKey());
            assertEquals(0, budget.reservedBytes(), part.getKey() + ": a refusal reserves nothing");
            try (KeyDictionary kept = KeyDictionary.build(part.getValue(), 0, KeyEncodingPolicy.AUTO_PRESERVE, new MemoryBudget(bytes))) {
                assertEquals(bytes, kept.reservedBytes(), part.getKey() + ": the lease is the whole estimate");
            }
        }
        // the reviewer's reproduction: a 178-byte budget no longer admits the scenario with the large control payload
        MemoryBudget tiny = new MemoryBudget(178);
        assertThrows(FrameworkException.class, () -> KeyDictionary.build(grown.get("run-once code"), 0, KeyEncodingPolicy.AUTO_PRESERVE, tiny));
        assertEquals(0, tiny.reservedBytes());
    }

    private static ScenarioSpec withControls(ScenarioSpec base, ControlSpec controls) {
        return new ScenarioSpec(base.source(), base.sheets(), controls, base.hasSequenceSheet(), base.sequence());
    }

    /** Review finding BM04: census() returned the mutable census, so a caller could contradict the fixed encoding. */
    @Test
    void theDictionaryHandsOutNothingThatCouldChangeItsMetadata() throws Exception {
        try (KeyDictionary dictionary = build(spec(List.of("a", "b")), 0, KeyEncodingPolicy.AUTO_PRESERVE)) {
            KeyCensus.Snapshot census = dictionary.census();
            assertEquals(new KeyCensus.Snapshot(3, 1, 3), census);
            int[] labels = dictionary.labelsOf(1);
            labels[0] = 99;
            assertArrayEquals(new int[]{2, 3}, dictionary.labelsOf(1), "labelsOf returns a copy");
            assertEquals(census, dictionary.census());
            assertTrue(dictionary.census().fits(dictionary.encoding()));
            // every public accessor returns a primitive, an immutable value or a fresh copy
            Set<Class<?>> immutable = Set.of(String.class, KeyRow.class, KeyDictionary.class);
            for (Method method : KeyDictionary.class.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                Class<?> type = method.getReturnType();
                boolean ok = type.isPrimitive() || type.isRecord() || type.isEnum() || immutable.contains(type)
                        || type == int[].class && method.getName().equals("labelsOf");
                assertTrue(ok, "public " + method.getName() + " returns " + type.getName());
            }
        }
    }

    @Test
    void aCensusFailureHappensBeforeAnythingIsReserved() {
        MemoryBudget budget = new MemoryBudget(BIG);
        assertThrows(FrameworkException.class, () -> KeyDictionary.build(sized(1, 256), 0, KeyEncodingPolicy.SIGNED_BYTE, budget));
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void theArgumentsAreChecked() {
        MemoryBudget budget = new MemoryBudget(BIG);
        assertThrows(IllegalArgumentException.class, () -> KeyDictionary.build(spec(List.of("a")), -1, KeyEncodingPolicy.AUTO_PRESERVE, budget));
        assertThrows(NullPointerException.class, () -> KeyDictionary.build(null, 0, KeyEncodingPolicy.AUTO_PRESERVE, budget));
        assertThrows(NullPointerException.class, () -> KeyDictionary.build(spec(List.of("a")), 0, null, budget));
        assertThrows(NullPointerException.class, () -> KeyDictionary.build(spec(List.of("a")), 0, KeyEncodingPolicy.AUTO_PRESERVE, null));
    }
}
