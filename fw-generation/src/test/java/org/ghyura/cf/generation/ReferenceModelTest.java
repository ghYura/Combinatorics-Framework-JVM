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

import org.ghyura.cf.algebra.LegacyLibrary;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generated scenarios run through the lazy, budgeted generation and through {@link LegacyModel}, an independent
 * list-based transliteration of the legacy loop; every final table must agree row by row (id, parent, keys), and so
 * must the consumed operands, for both generator libraries and with and without distinct tables. The scenarios mix
 * every verb and count, subset modes, Cartesian products, separators, FW_Group with literal, joined and unparseable
 * replacements, joiners of every multiplicity with optional wrappers, nested and grouped nested operands, FW_Reuse,
 * and joiners in the middle of a sheet's directives (which reshuffle row ids).
 */
class ReferenceModelTest {

    private static final String[] FIRST = {"FW_Combi(1)", "FW_Combi(2)", "FW_Combi(0)", "FW_Combi(all)", "FW_Combi(size)", "FW_Combi(5)",
            "FW_CombiR(2)", "FW_Permut()", "FW_Permut", "FW_Permut(PermutationGenerator.TreatDuplicatesAs.IDENTICAL)", "FW_PermutR(2)",
            "FW_Subsets", "FW_Subsets_EXACT(1)", "FW_Subsets_RANGE(1,2)", "FW_Subsets_AFTER(1)", "FW_Cartes(X)", "FW_Cartes_first(X)"};
    private static final String[] LATER = {"FW_Combi(1)", "FW_Combi(2)", "FW_Combi(all)", "FW_Combi(size)", "FW_CombiR(2)", "FW_Permut()",
            "FW_Permut", "FW_PermutR(2)", "FW_Subsets", "FW_Cartes(X)", "FW_Cartes_first(X)", "FW_Separator(S)", "FW_Group",
            "FW_Group FW_ReplaceRE(\"1\", \"2\")", "FW_Group FW_ReplaceRE(\"7\", \"x\")", "FW_Group FW_ReplaceRE(\"8\", \"\"+S+\"\")"};
    private static final String[] MULTIPLICITIES = {"1:1", "1:N", "M:1", "M:M", "M:N"};

    private static Scenario generate(Random random) {
        Scenario s = Scenario.of();
        int dataSheets = 2 + random.nextInt(3);
        for (int i = 1; i <= dataSheets; i++) {
            int count = random.nextInt(6) == 0 ? 0 : 1 + random.nextInt(3);
            String[] values = new String[count];
            for (int v = 0; v < count; v++) {
                values[v] = "d" + i + "_" + v;
            }
            s.sheet("D" + i, values);
        }
        s.sheet("S", "s").sheet("X", "x0", "x1");
        if (random.nextInt(4) == 0) {
            s.options(Scenario.UNPROMOTED);
        }
        for (int i = 1; i <= dataSheets; i++) {
            List<String> cells = new ArrayList<>(List.of("D" + i));
            if (random.nextInt(3) == 0) {
                cells.add("FW_Reuse");
            }
            cells.add(FIRST[random.nextInt(FIRST.length)]);
            int later = random.nextInt(4);
            for (int k = 0; k < later; k++) {
                if (i > 1 && random.nextInt(6) == 0) {
                    cells.add(joiner(random, i - 1, false));
                } else {
                    cells.add(LATER[random.nextInt(LATER.length)]);
                }
            }
            s.seq(cells.toArray(String[]::new));
        }
        if (random.nextInt(5) == 0) {
            // a sheet FW_Seq names but no data sheet has: virtual, with an empty list of values
            s.seq("V1", FIRST[random.nextInt(FIRST.length)]);
        }
        int joiners = random.nextInt(3);
        for (int j = 1; j <= joiners; j++) {
            List<String> cells = new ArrayList<>(List.of("J" + j));
            cells.add(joiner(random, dataSheets, j > 1 && random.nextInt(3) == 0));
            if (random.nextInt(3) == 0) {
                cells.add(LATER[random.nextInt(LATER.length)]);
            }
            s.seq(cells.toArray(String[]::new));
        }
        return s;
    }

    private static String joiner(Random random, int sheets, boolean nested) {
        String a = nested ? (random.nextBoolean() ? "FW_()" : "FW_()G") : "D" + (1 + random.nextInt(sheets));
        String b = "D" + (1 + random.nextInt(sheets));
        String start = random.nextInt(3) == 0 ? "S" : "";
        String relation = random.nextBoolean() ? "S" : "";
        String end = random.nextInt(3) == 0 ? "S" : "";
        String separator = random.nextBoolean() ? "S" : "";
        return "FW_(" + start + ",," + a + "," + relation + "," + b + ",," + end + "," + separator + ","
                + MULTIPLICITIES[random.nextInt(MULTIPLICITIES.length)] + ")";
    }

    /** At most this many rows in any table of a compared scenario: the list model holds every row. */
    private static final BigInteger CAP = BigInteger.valueOf(20_000);
    /** A probe or comparison run stops (RUN_CANCELLED) after this many cancellation polls, about 256 rows each. */
    private static final int POLLS = 4_000;

    /** A token that turns cancelled after a number of polls: a work limit for generated scenarios. */
    private static Cancellation workLimit() {
        AtomicInteger polls = new AtomicInteger();
        return () -> polls.incrementAndGet() > POLLS;
    }

    /** Whether the scenario plans and every table stays small enough to hold in the list model. */
    private static boolean comparable(Scenario scenario) {
        try (Scenario.Run probe = scenario.run(new GenerationOptions(LegacyLibrary.PARALLEL, false), new MemoryBudget(64L << 20), workLimit())) {
            for (SheetPlan sheet : probe.plan().activeSheets()) {
                if (probe.table(sheet.name()).size().compareTo(CAP) > 0) {
                    return false;
                }
            }
            return true;
        } catch (FrameworkException e) {
            return false;
        }
    }

    @Test
    void theLazyGenerationAgreesWithTheListModelOnGeneratedScenarios() {
        Random random = new Random(20261002L);
        int compared = 0;
        int skipped = 0;
        long rows = 0;
        int repeatedIds = 0;
        int storedTables = 0;
        int computedTables = 0;
        long unparseable = 0;
        for (int n = 0; n < 300; n++) {
            Scenario scenario = generate(random);
            if (!comparable(scenario)) {
                skipped++;
                continue;
            }
            for (GenerationOptions options : List.of(new GenerationOptions(LegacyLibrary.PARALLEL, false),
                    new GenerationOptions(LegacyLibrary.COMBINATORICSLIB3, false), new GenerationOptions(LegacyLibrary.PARALLEL, true),
                    new GenerationOptions(LegacyLibrary.COMBINATORICSLIB3, true))) {
                Scenario.Run run = scenario.run(options, new MemoryBudget(64L << 20), workLimit());
                try (run) {
                    LegacyModel model = new LegacyModel(run.plan(), run.dictionary(), options);
                    Map<String, List<LegacyModel.R>> expected = model.run();
                    for (SheetPlan sheet : run.plan().activeSheets()) {
                        String where = "scenario " + n + " " + options + " sheet " + sheet.name() + " " + sheet.directives().stream()
                                .map(d -> d.source()).toList();
                        List<String> want = new ArrayList<>();
                        for (LegacyModel.R r : expected.get(sheet.name())) {
                            want.add(r.id() + "<-" + r.parent() + " " + Arrays.toString(r.labels()));
                        }
                        List<String> got = new ArrayList<>();
                        for (Row r : Scenario.all(run.table(sheet.name()))) {
                            got.add(r.id() + "<-" + r.parent() + " " + Arrays.toString(r.keys().labels()));
                        }
                        assertEquals(want, got, where);
                        assertEquals(model.consumedBy.get(sheet.name()), run.consumedBy(sheet.name()), where);
                        // the row lengths are known from the operators' parameters (or counted while stored), never by reading rows
                        SortedMap<Integer, BigInteger> census = new TreeMap<>();
                        for (LegacyModel.R r : expected.get(sheet.name())) {
                            census.merge(r.labels().length, BigInteger.ONE, BigInteger::add);
                        }
                        assertEquals(census, run.table(sheet.name()).lengths(), where + " rows by length");
                        assertFalse(run.table(sheet.name()).formula().isBlank(), where);
                        rows += got.size();
                        if (expected.get(sheet.name()).stream().map(LegacyModel.R::id).distinct().count() < got.size()) {
                            repeatedIds++;
                        }
                        if (run.table(sheet.name()).stored()) {
                            storedTables++;
                        } else if (!got.isEmpty()) {
                            computedTables++;
                        }
                    }
                    unparseable += model.unparseable;
                    compared++;
                }
            }
        }
        assertTrue(compared >= 800, "most generated scenarios must be comparable: compared " + compared + " runs, skipped "
                + skipped + " scenarios");
        assertTrue(rows > 50_000, "the scenarios must produce rows to compare: " + rows);
        // the generated scenarios reach the paths that matter, not only the easy ones
        assertTrue(repeatedIds > 0, "a joiner after a swap repeats ids, which the later passes read by id");
        assertTrue(storedTables > 0 && computedTables > 0, "stored " + storedTables + ", computed " + computedTables);
        assertTrue(unparseable > 0, "grouped rows rewritten into text are dropped");
    }
}
