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


package org.ghyura.cf.generation;

import org.ghyura.cf.algebra.Counting;
import org.ghyura.cf.algebra.LegacyLibrary;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.spi.CancellationSource;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generation stays lazy and budgeted (supplement to revision 2): tables whose rows follow from their inputs hold no
 * rows, even beyond Long.MAX_VALUE rows; what has to be stored is reserved before it is allocated, a reservation
 * that does not fit fails with RESOURCE_LIMIT, and every reservation comes back on close, on failure and on
 * cancellation.
 */
class BoundedGenerationTest {

    private static final GenerationOptions PARALLEL = new GenerationOptions(LegacyLibrary.PARALLEL, false);

    private static Scenario values(int count, String... directives) {
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = "v" + i;
        }
        String[] cells = new String[directives.length + 1];
        cells[0] = "A";
        System.arraycopy(directives, 0, cells, 1, directives.length);
        return Scenario.of().sheet("A", names).seq(cells).options(Scenario.UNPROMOTED);
    }

    @Test
    void aFirstPassLongerThanAnyMachineWordIsComputedRowByRowWithExactIds() {
        Scenario s = values(25, "FW_Permut()");
        MemoryBudget budget = new MemoryBudget(Scenario.BIG);
        try (Scenario.Run run = s.run(PARALLEL, budget, Cancellation.NONE)) {
            RowSource table = run.table("A");
            BigInteger size = Counting.factorial(25);
            assertEquals(size, table.size());
            assertTrue(size.bitLength() > 63);
            assertFalse(table.stored());
            assertEquals(0, table.reservedBytes());
            assertEquals(run.dictionary().reservedBytes(), budget.reservedBytes(), "nothing but the dictionary is reserved");
            Row last = table.at(size.subtract(BigInteger.ONE));
            assertEquals(size, last.id(), "the last row's id is 25!, beyond 2^63");
            assertEquals("v24 v23 v22 v21 v20 v19 v18 v17 v16 v15 v14 v13 v12 v11 v10 v9 v8 v7 v6 v5 v4 v3 v2 v1 v0", run.text(last));
            assertEquals("v0 v1 v2 v3 v4 v5 v6 v7 v8 v9 v10 v11 v12 v13 v14 v15 v16 v17 v18 v19 v20 v21 v22 v24 v23",
                    run.text(table.at(BigInteger.ONE)));
        }
    }

    @Test
    void aLaterPassOverRowsOfOneLengthFindsItsSourceRowByDivisionAndHoldsNothing() {
        Scenario s = values(2000, "FW_Combi(2)", "FW_Permut()");
        try (Scenario.Run run = s.run(PARALLEL)) {
            RowSource table = run.table("A");
            assertEquals(BigInteger.valueOf(2 * 1_999_000L), table.size());
            assertFalse(table.stored());
            assertEquals(0, table.reservedBytes());
            assertEquals("1<-1 v0 v1", describe(run, table.at(BigInteger.ZERO)));
            assertEquals("2<-1 v1 v0", describe(run, table.at(BigInteger.ONE)));
            assertEquals("3998000<-1999000 v1999 v1998", describe(run, table.at(table.size().subtract(BigInteger.ONE))));
            assertCursorsAgreeWithRandomAccess(table, new Random(7));
        }
    }

    @Test
    void aLaterPassOverRowsOfMixedLengthsReservesItsRunningTotalsAndReturnsThemOnClose() {
        Scenario s = values(8, "FW_Combi(all)", "FW_Permut()");
        MemoryBudget budget = new MemoryBudget(Scenario.BIG);
        Scenario.Run run = s.run(PARALLEL, budget, Cancellation.NONE);
        try {
            RowSource table = run.table("A");
            long expected = 0;
            for (int m = 1; m <= 8; m++) {
                expected += Counting.binomial(8, m).multiply(Counting.factorial(m)).longValueExact();
            }
            assertEquals(BigInteger.valueOf(expected), table.size());
            assertFalse(table.stored());
            assertTrue(table.reservedBytes() > 0, "one running total per source row, reserved");
            assertEquals(run.dictionary().reservedBytes() + table.reservedBytes(), budget.reservedBytes());
            assertEquals("109600<-255 v7 v6 v5 v4 v3 v2 v1 v0", describe(run, table.at(table.size().subtract(BigInteger.ONE))),
                    "the last of the 255 source rows is all eight values, its last permutation reversed");
            assertCursorsAgreeWithRandomAccess(table, new Random(11));
            run.result().close();
            assertEquals(run.dictionary().reservedBytes(), budget.reservedBytes(), "closing the result returns its reservations");
        } finally {
            run.close();
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void joinedRowsAreComputedAndAgreeWithTheirCursor() {
        Scenario s = Scenario.of().sheet("A", "a0", "a1", "a2", "a3").sheet("B", "b0", "b1", "b2").sheet("R", "r")
                .seq("A", "FW_Combi(all)", "FW_Combi(size)").seq("B", "FW_Subsets", "FW_Combi(size)")
                .seq("J", "FW_(,,A,R,B,,,R,1:1)").seq("K", "FW_(,,A,R,B,,,R,M:N)");
        try (Scenario.Run run = s.run(PARALLEL)) {
            for (String sheet : List.of("J", "K")) {
                RowSource table = run.table(sheet);
                assertFalse(table.stored(), sheet);
                assertCursorsAgreeWithRandomAccess(table, new Random(sheet.hashCode()));
            }
        }
    }

    private static String describe(Scenario.Run run, Row row) {
        return row.id() + "<-" + row.parent() + " " + run.text(row);
    }

    /** Every cursor over a random range returns exactly the rows random access returns. */
    private static void assertCursorsAgreeWithRandomAccess(RowSource table, Random random) {
        long size = table.size().longValueExact();
        for (int trial = 0; trial < 20; trial++) {
            long from = (long) (random.nextDouble() * size);
            long to = Math.min(size, from + random.nextInt(40));
            try (RowCursor cursor = table.open(BigInteger.valueOf(from), BigInteger.valueOf(to), Cancellation.NONE)) {
                for (long rank = from; rank < to; rank++) {
                    assertTrue(cursor.hasNext());
                    assertEquals(BigInteger.valueOf(rank), cursor.position());
                    assertEquals(table.at(BigInteger.valueOf(rank)), cursor.next(), "rank " + rank);
                }
                assertFalse(cursor.hasNext());
            }
        }
    }

    // ---- budgets --------------------------------------------------------------------------------------------------

    @Test
    void aGroupedPassThatDoesNotFitFailsExplicitlyAndReleasesEveryReservation() {
        Scenario s = values(200, "FW_Combi(2)", "FW_Group", "FW_Combi(1)");
        MemoryBudget budget = new MemoryBudget(KeyDictionary.retainedBytes(s.spec()) + (1L << 20));
        FrameworkException e = assertThrows(FrameworkException.class, () -> s.run(PARALLEL, budget, Cancellation.NONE));
        Diagnostic d = e.diagnostics().getFirst();
        assertEquals(ErrorCode.RESOURCE_LIMIT, d.code());
        assertTrue(d.attributes().get("purpose").contains("FW_Group units"), d.toString());
        assertEquals(0, budget.reservedBytes(), "the failed generation returned everything it had reserved");
    }

    @Test
    void distinctTablesStoreTheirRowsWithinTheBudgetOrFailExplicitly() {
        Scenario s = values(2000, "FW_Combi(2)", "FW_Permut()");
        GenerationOptions distinct = new GenerationOptions(LegacyLibrary.PARALLEL, true);
        MemoryBudget small = new MemoryBudget(KeyDictionary.retainedBytes(s.spec()) + (8L << 20));
        FrameworkException e = assertThrows(FrameworkException.class, () -> s.run(distinct, small, Cancellation.NONE));
        assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
        assertEquals(0, small.reservedBytes());

        Scenario fits = values(60, "FW_Combi(2)", "FW_Permut()");
        MemoryBudget budget = new MemoryBudget(Scenario.BIG);
        try (Scenario.Run run = fits.run(distinct, budget, Cancellation.NONE)) {
            RowSource table = run.table("A");
            assertTrue(table.stored());
            assertEquals(BigInteger.valueOf(60 * 59), table.size());
            assertEquals(run.dictionary().reservedBytes() + table.reservedBytes(), budget.reservedBytes());
        }
        assertEquals(0, budget.reservedBytes());
    }

    // ---- cancellation ---------------------------------------------------------------------------------------------

    @Test
    void identityChainsKeepTheirRowsLazyAndDoNotCopyProvenUniqueAssemblyFactors() {
        String[] verbs = new String[128];
        Arrays.fill(verbs, "FW_Combi(1)");
        Scenario s = values(3, verbs);
        MemoryBudget budget = new MemoryBudget(KeyDictionary.retainedBytes(s.spec()) + 16_384);
        try (Scenario.Run run = s.run(new GenerationOptions(LegacyLibrary.PARALLEL, true), budget, Cancellation.NONE)) {
            assertEquals(List.of("v0", "v1", "v2"), run.rows("A"));
            assertEquals(big(379), run.table("A").at(BigInteger.ZERO).id(), "all authored stages still advance IDs");
            assertEquals(big(376), run.table("A").at(BigInteger.ZERO).parent());
            assertEquals(run.dictionary().reservedBytes(), budget.reservedBytes());
            long peak = budget.highWaterBytes();
            RunConfig config = ConfigResolver.resolve(null, Map.of(), List.of(
                    new ConfigResolver.Override("cf.assembly.distinct", "true", "--set"))).config();
            try (Assembly assembly = run.assemble(AssemblyOptions.of(config), RenderOptions.of(config))) {
                assertEquals(big(3), assembly.generated());
                assertEquals(peak, budget.highWaterBytes(), "proven uniqueness needs no temporary row copy or index");
            }
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void materializationReleasesObsoleteStagesWithoutChangingFirstOccurrenceIds() {
        String[] verbs = new String[64];
        Arrays.fill(verbs, "FW_Permut()");
        Scenario s = values(3, verbs);
        MemoryBudget budget = new MemoryBudget(KeyDictionary.retainedBytes(s.spec()) + 16_384);
        try (Scenario.Run run = s.run(new GenerationOptions(LegacyLibrary.PARALLEL, true), budget, Cancellation.NONE)) {
            assertEquals(big(6), run.table("A").size());
            assertEquals(6, run.rows("A").stream().distinct().count());
            assertEquals(big(36L * 62 + 1), run.table("A").at(BigInteger.ZERO).id());
            assertEquals(big(36L * 61 + 1), run.table("A").at(BigInteger.ZERO).parent());
            assertEquals(5_632, run.table("A").reservedBytes());
            assertEquals(run.dictionary().reservedBytes() + 5_632, budget.reservedBytes(),
                    "only the final stored stage remains");
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void groupedDuplicatesAreDiscardedWhileProducedButTheirIdsStillAdvance() {
        Scenario s = values(3, "FW_Combi(1)", "FW_Group FW_ReplaceRE(\"[234]\", \"2\")",
                "FW_PermutR(8)", "FW_Combi(size)");
        MemoryBudget budget = new MemoryBudget(KeyDictionary.retainedBytes(s.spec()) + 16_384);
        try (Scenario.Run run = s.run(new GenerationOptions(LegacyLibrary.PARALLEL, true), budget, Cancellation.NONE)) {
            assertEquals(List.of("v0 v0 v0 v0 v0 v0 v0 v0"), run.rows("A"));
            Row row = run.table("A").at(BigInteger.ZERO);
            assertEquals(big(6562), row.id(), "the Group emitted 3^8 raw rows before the last authored stage");
            assertEquals(BigInteger.ONE, row.parent(), "the first duplicate occurrence remains the parent");
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void pruningKeepsSharedOperandsAndTheirComputedDependenciesReadable() {
        Scenario s = Scenario.of().sheet("A", "a", "b", "c").sheet("B", "x", "y")
                .seq("A", "FW_Reuse", "FW_Permut()", "FW_Permut()", "FW_Permut()")
                .seq("B", "FW_Reuse", "FW_Combi(1)")
                .seq("J", "FW_(,,A,,B,,,,M:N)", "FW_Group", "FW_Combi(1)")
                .seq("K", "FW_(,,A,,B,,,,M:N)").options(Scenario.UNPROMOTED);
        MemoryBudget budget = new MemoryBudget(1L << 20);
        try (Scenario.Run run = s.run(new GenerationOptions(LegacyLibrary.PARALLEL, true), budget, Cancellation.NONE)) {
            assertEquals(big(6), run.table("A").size());
            assertEquals(big(12), run.table("J").size());
            assertEquals(big(12), run.table("K").size());
            assertEquals(run.rows("J").stream().sorted().toList(), run.rows("K").stream().sorted().toList());
            assertEquals(6, run.rows("A").size(), "completed operands remain part of the generation result");
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void expansionDoesNotCopyItsAncestorsFormulaDuringConstruction() {
        AtomicInteger formulas = new AtomicInteger();
        RowSource source = new RowSource() {
            @Override public BigInteger size() { return big(3); }
            @Override public Row at(BigInteger rank) {
                return new Row(rank.add(BigInteger.ONE), BigInteger.ZERO, KeyRow.of(KeyEncoding.SIGNED_BYTE, rank.intValue() + 1));
            }
            @Override public int uniformLength() { return 1; }
            @Override public String formula() { formulas.incrementAndGet(); return "S + T"; }
            @Override public boolean idsAscending() { return true; }
            @Override public boolean stored() { return false; }
            @Override public long reservedBytes() { return 0; }
        };
        RowExpansion expansion = new RowExpansion(values(3, "FW_Combi(1)").plan().sheet("A").orElseThrow()
                .directives().getFirst(), LegacyLibrary.PARALLEL, null, null);
        MemoryBudget budget = new MemoryBudget(1024);
        try (ExpandedRows first = ExpandedRows.of(source, expansion, KeyEncoding.SIGNED_BYTE, BigInteger.ZERO,
                budget, "first", Cancellation.NONE);
             ExpandedRows second = ExpandedRows.of(first, expansion, KeyEncoding.SIGNED_BYTE, big(3),
                     budget, "second", Cancellation.NONE)) {
            assertEquals(0, formulas.get(), "constructing descendants must not expand/copy ancestor formula strings");
            String step = expansion.formulaForLength(1);
            assertEquals("((S + T) × " + step + ") × " + step, second.formula());
            assertEquals(1, formulas.get());
            assertEquals(big(6), second.at(big(2)).id());
        }
        assertEquals(0, budget.reservedBytes());
    }

    private static BigInteger big(long value) {
        return BigInteger.valueOf(value);
    }

    @Test
    void cancellationStopsTheGenerationWithRunCancelledAndReleasesEveryReservation() {
        CancellationSource before = new CancellationSource();
        before.cancel();
        MemoryBudget budget = new MemoryBudget(Scenario.BIG);
        FrameworkException early = assertThrows(FrameworkException.class,
                () -> values(5, "FW_Combi(2)", "FW_Permut()").run(PARALLEL, budget, before));
        assertEquals(ErrorCode.RUN_CANCELLED, early.diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());

        // the token turns cancelled while a distinct table is being stored, after some thousands of rows
        AtomicInteger polls = new AtomicInteger();
        Cancellation later = () -> polls.incrementAndGet() > 40;
        FrameworkException during = assertThrows(FrameworkException.class, () -> values(400, "FW_Combi(2)", "FW_Permut()")
                .run(new GenerationOptions(LegacyLibrary.PARALLEL, true), budget, later));
        assertEquals(ErrorCode.RUN_CANCELLED, during.diagnostics().getFirst().code());
        assertTrue(polls.get() > 40);
        assertEquals(0, budget.reservedBytes(), "the partly stored table was released");
    }

    // ---- configuration --------------------------------------------------------------------------------------------

    @Test
    void theOptionsComeFromTheConfiguredGenerationSettings() {
        assertEquals(new GenerationOptions(LegacyLibrary.PARALLEL, false), GenerationOptions.of(RunConfig.defaults()));
        assertEquals(GenerationOptions.defaults(), GenerationOptions.of(RunConfig.defaults()));
        RunConfig config = ConfigResolver.resolve(null, Map.of("CF_GENERATION_LIBRARY", "COMBINATORICSLIB3"),
                List.of(new ConfigResolver.Override("cf.generation.distinctTables", "true", "--set"))).config();
        assertEquals(new GenerationOptions(LegacyLibrary.COMBINATORICSLIB3, true), GenerationOptions.of(config));
    }
}
