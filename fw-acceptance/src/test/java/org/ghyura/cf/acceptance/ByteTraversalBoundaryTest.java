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

package org.ghyura.cf.acceptance;

import org.ghyura.cf.generation.*;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.*;
import org.ghyura.cf.model.key.*;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.plan.*;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reader -> planner -> dictionary -> enumeration -> wire round-trip -> rendered business values. */
class ByteTraversalBoundaryTest {
    private static final PlanOptions PLAN = new PlanOptions(true, "FW_VIRTUAL_", true, true, false, true);
    private static final RenderOptions RENDER = RenderOptions.defaults();

    private ScenarioSpec load(Path dir, Map<String, Object> sheets) throws Exception {
        Path file = dir.resolve("boundary.json");
        Files.writeString(file, Json.write(Map.of("sheets", sheets)));
        return ScenarioLoader.load(file).spec();
    }

    private void strictRefusesBeforeAllocating(ScenarioSpec spec, SeqPlan plan) {
        long maximum = Math.max(spec.valueCount(), spec.sheetCount() + plan.virtualSheetCount());
        if (maximum <= 255) return;
        MemoryBudget budget = new MemoryBudget(64L << 20);
        FrameworkException failure = assertThrows(FrameworkException.class,
                () -> KeyDictionary.build(spec, (int) plan.virtualSheetCount(), KeyEncodingPolicy.SIGNED_BYTE, budget));
        assertEquals(ErrorCode.KEY_CENSUS_OVERFLOW, failure.primary().code());
        assertEquals("255", failure.primary().attributes().get("capacity"));
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void allByteValuesAndNeighboursSurviveCellEnumerationBeyond127And255(@TempDir Path dir) throws Exception {
        for (int count : new int[]{125, 126, 127, 128, 254, 255, 256, 258}) {
            Map<String, Object> sheets = new LinkedHashMap<>();
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < count; i++) values.add(List.of(Byte.MIN_VALUE - 1 + i));
            sheets.put("A", values);
            sheets.put("FW_Seq", List.of(List.of("A", "FW_Combi(1)")));
            ScenarioSpec spec = load(dir, sheets);
            SeqPlan plan = SeqPlanner.plan(spec, PLAN).plan();
            strictRefusesBeforeAllocating(spec, plan);
            MemoryBudget budget = new MemoryBudget(64L << 20);
            try (KeyDictionary dictionary = KeyDictionary.build(spec, 0, KeyEncodingPolicy.AUTO_PRESERVE, budget);
                 Generation.Result generated = Generation.run(plan, dictionary, GenerationOptions.defaults(), budget, Cancellation.NONE);
                 Assembly assembly = Assembly.of(plan, generated.result(), dictionary, GenerationOptions.defaults(), AssemblyOptions.defaults(), RENDER, 1L, budget, Cancellation.NONE);
                 CandidateRenderer renderer = CandidateRenderer.of(assembly, plan, dictionary, RENDER, budget)) {
                KeyEncoding expected = count <= 255 ? KeyEncoding.SIGNED_BYTE : KeyEncoding.PACKED_LEGACY16;
                int first = count <= 126 || count > 255 ? 2 : 128 - count;
                assertEquals(first, dictionary.firstLabel());
                assertEquals(expected, dictionary.encoding());
                assertEquals(BigInteger.valueOf(count), assembly.generated());
                var cursor = assembly.candidates(Cancellation.NONE);
                Set<String> ids = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    assertTrue(cursor.hasNext());
                    Candidate candidate = cursor.next();
                    assertEquals(BigInteger.valueOf(i), candidate.ordinal());
                    assertTrue(ids.add(candidate.id().hex()), "no identity alias at " + i);
                    assertEquals(expected, candidate.keys(0).encoding());
                    assertArrayEquals(new int[]{first + i}, KeyWire.decode(KeyWire.encode(candidate.keys(0))).labels());
                    assertEquals(expected.bytesPerKey(), candidate.keys(0).payloadLength());
                    String expectedText = Integer.toString(Byte.MIN_VALUE - 1 + i);
                    assertEquals(expectedText, dictionary.value(first + i).text());
                    assertEquals(expectedText, renderer.renderText(candidate));
                    assertEquals(i == 0 ? SelectionState.BASELINE_REFERENCE : SelectionState.PRESENT, candidate.states().getFirst());
                }
                assertFalse(cursor.hasNext());
            }
            assertEquals(0, budget.reservedBytes());
        }
    }

    @Test
    void dataSheetTraversalAndValueLookupRemainDistinctAcrossSignedAndUnsignedByteEdges(@TempDir Path dir) throws Exception {
        for (int count : new int[]{63, 64, 127, 128, 255, 256, 257}) {
            Map<String, Object> sheets = new LinkedHashMap<>();
            List<Object> seq = new ArrayList<>();
            StringBuilder expectedSource = new StringBuilder();
            for (int i = 0; i < count; i++) {
                String name = "S" + i, value = "v" + i + ";";
                sheets.put(name, List.of(List.of(value)));
                seq.add(List.of(name, "FW_Combi(1)"));
                expectedSource.append(value);
            }
            sheets.put("FW_Seq", seq);
            ScenarioSpec spec = load(dir, sheets);
            SeqPlan plan = SeqPlanner.plan(spec, PLAN).plan();
            strictRefusesBeforeAllocating(spec, plan);
            MemoryBudget budget = new MemoryBudget(64L << 20);
            try (KeyDictionary dictionary = KeyDictionary.build(spec, 0, KeyEncodingPolicy.AUTO_PRESERVE, budget);
                 Generation.Result generated = Generation.run(plan, dictionary, GenerationOptions.defaults(), budget, Cancellation.NONE);
                 Assembly assembly = Assembly.of(plan, generated.result(), dictionary, GenerationOptions.defaults(), AssemblyOptions.defaults(), RENDER, 1L, budget, Cancellation.NONE);
                 CandidateRenderer renderer = CandidateRenderer.of(assembly, plan, dictionary, RENDER, budget)) {
                assertEquals(count <= 255 ? KeyEncoding.SIGNED_BYTE : KeyEncoding.PACKED_LEGACY16, dictionary.encoding());
                assertEquals(BigInteger.ONE, assembly.generated());
                Candidate candidate = assembly.candidates(Cancellation.NONE).next();
                assertEquals(count, candidate.rows().size());
                for (int i = 0; i < count; i++) {
                    int sheet = i + 1;
                    int first = count <= 63 || count > 255 ? count + 1 : 128 - count;
                    int label = first + i;
                    assertEquals(sheet, spec.sheets().get(i).key());
                    assertEquals(sheet, dictionary.sheetOfLabel(label));
                    assertArrayEquals(new int[]{label}, dictionary.rowOf(sheet).labels());
                    assertArrayEquals(new int[]{label}, candidate.labels(i));
                    assertEquals(SelectionState.BASELINE_REFERENCE, candidate.states().get(i));
                }
                assertEquals(expectedSource.toString(), renderer.renderText(candidate));
            }
            assertEquals(0, budget.reservedBytes());
        }
    }

    @Test
    void virtualSheetsUseTheNegativeRangeBeforeWideningOrTraversal(@TempDir Path dir) throws Exception {
        for (int virtual : new int[]{126, 127, 254, 255, 256}) {
            Map<String, Object> sheets = new LinkedHashMap<>();
            sheets.put("A", List.of(List.of("kept")));
            List<Object> seq = new ArrayList<>();
            seq.add(List.of("A", "FW_Combi(1)"));
            for (int i = 0; i < virtual; i++) seq.add(List.of("V" + i, "FW_Permut()"));
            sheets.put("FW_Seq", seq);
            ScenarioSpec spec = load(dir, sheets);
            SeqPlan plan = SeqPlanner.plan(spec, PLAN).plan();
            assertEquals(virtual, plan.virtualSheetCount());
            strictRefusesBeforeAllocating(spec, plan);
            MemoryBudget budget = new MemoryBudget(64L << 20);
            try (KeyDictionary dictionary = KeyDictionary.build(spec, virtual, KeyEncodingPolicy.AUTO_PRESERVE, budget);
                 Generation.Result generated = Generation.run(plan, dictionary, GenerationOptions.defaults(), budget, Cancellation.NONE);
                 Assembly assembly = Assembly.of(plan, generated.result(), dictionary, GenerationOptions.defaults(), AssemblyOptions.defaults(), RENDER, 1L, budget, Cancellation.NONE);
                 CandidateRenderer renderer = CandidateRenderer.of(assembly, plan, dictionary, RENDER, budget)) {
                assertEquals(virtual <= 254 ? KeyEncoding.SIGNED_BYTE : KeyEncoding.PACKED_LEGACY16, dictionary.encoding());
                assertEquals(virtual <= 254 ? 127 : virtual + 1, dictionary.census().max());
                assertEquals(virtual <= 126 || virtual > 254 ? 1 : 127 - virtual, dictionary.sheetLabel(1));
                assertEquals(BigInteger.ONE, assembly.generated());
                Candidate candidate = assembly.candidates(Cancellation.NONE).next();
                assertEquals(1, candidate.rows().size(), "empty virtual sheets have role NONE and add no candidate columns");
                assertArrayEquals(new int[]{2}, candidate.labels(0));
                for (int i = 0; i < virtual; i++) {
                    var sheet = generated.result().sheet("V" + i).orElseThrow();
                    assertEquals(i + 2, sheet.key());
                    assertEquals(BigInteger.ONE, sheet.rows().size());
                    assertEquals(0, sheet.rows().at(BigInteger.ZERO).keys().size());
                }
                assertEquals("kept", renderer.renderText(candidate), "empty virtual rows must not replace data or become numeric sentinels");
            }
            assertEquals(0, budget.reservedBytes());
        }
    }

    @Test
    void nativeReaderDistinguishesMissingCellsFromNumericLegacySentinelValues(@TempDir Path dir) throws Exception {
        List<Integer> numbers = List.of(-32768, -128, -1, 0, 127, 128, 32767, 32768);
        List<Object> rows = new ArrayList<>();
        rows.add(Collections.singletonList(null));
        rows.add(List.of());
        for (int n : numbers) rows.add(List.of(n));
        ScenarioSpec spec = load(dir, Map.of("A", rows, "FW_Seq", List.of(List.of("A", "FW_Combi(1)"))));
        SeqPlan plan = SeqPlanner.plan(spec, PLAN).plan();
        MemoryBudget budget = new MemoryBudget(64L << 20);
        try (KeyDictionary dictionary = KeyDictionary.build(spec, 0, KeyEncodingPolicy.AUTO_PRESERVE, budget);
             Generation.Result generated = Generation.run(plan, dictionary, GenerationOptions.defaults(), budget, Cancellation.NONE);
             Assembly assembly = Assembly.of(plan, generated.result(), dictionary, GenerationOptions.defaults(), AssemblyOptions.defaults(), RENDER, 1L, budget, Cancellation.NONE);
             CandidateRenderer renderer = CandidateRenderer.of(assembly, plan, dictionary, RENDER, budget)) {
            assertEquals(KeyEncoding.SIGNED_BYTE, dictionary.encoding(), "keys fit a byte even when the business values do not");
            assertEquals(BigInteger.valueOf(numbers.size()), assembly.generated());
            var cursor = assembly.candidates(Cancellation.NONE);
            for (int n : numbers) assertEquals(Integer.toString(n), renderer.renderText(cursor.next()));
            assertFalse(cursor.hasNext());
        }
        assertEquals(0, budget.reservedBytes());
    }
}
