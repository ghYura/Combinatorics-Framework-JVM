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
import org.ghyura.cf.algebra.Operators;
import org.ghyura.cf.algebra.RankedTuples;
import org.ghyura.cf.algebra.TupleCursor;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.constraints.Coverage;
import org.ghyura.cf.model.key.SelectionState;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.plan.SheetRole;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lazy assembly against a list model of the legacy final assembly and Reader, written independently of it: every
 * table read into lists, the mandatory product by nested loops, each fw_opt{k} table built subset by subset, de-duplication
 * by searching earlier rows, the legacy ids by counting rows as the tables were filled, the source by concatenating
 * strings. Compared on generated scenarios, candidate by candidate: order, display id, states and bytes.
 */
class AssemblyModelTest {

    private static final String[] VERBS = {"FW_Combi(1)", "FW_Combi(2)", "FW_Permut()", "FW_Subsets", "FW_CombiR(2)", "FW_Combi(5)"};

    /** The model's candidate: display id, states and source. */
    record Expected(String display, List<SelectionState> states, String source) {
    }

    @Test
    void theLazyAssemblyAgreesWithAListModelOfTheLegacyAssembler() {
        Random random = new Random(20261002);
        int compared = 0;
        int withOptional = 0;
        int withDistinct = 0;
        for (int scenario = 0; scenario < 300; scenario++) {
            Scenario s = Scenario.of().options(Scenario.UNPROMOTED);
            int sheets = 1 + random.nextInt(5);
            for (int i = 0; i < sheets; i++) {
                String name = "S" + i;
                int values = random.nextInt(4);
                String[] texts = new String[values];
                for (int v = 0; v < values; v++) {
                    texts[v] = random.nextInt(5) == 0 ? "dup" : name.toLowerCase() + "v" + v;
                }
                s.sheet(name, texts);
                List<String> row = new ArrayList<>(List.of(name));
                if (random.nextInt(3) == 0) {
                    row.add("FW_Optional");
                }
                row.add(VERBS[random.nextInt(VERBS.length)]);
                if (random.nextInt(4) == 0) {
                    row.add(VERBS[random.nextInt(3)]);
                }
                s.seq(row.toArray(new String[0]));
                s.names(name, random.nextBoolean() ? name + "=" : "", random.nextBoolean() ? ";" : "|");
            }
            boolean distinct = random.nextInt(3) == 0;
            boolean absent = random.nextInt(4) != 0;
            RunConfig.SizeSet sizes = random.nextBoolean() ? RunConfig.SizeSet.all() : RunConfig.SizeSet.of(List.of(1 + random.nextInt(2)));
            LegacyLibrary library = random.nextBoolean() ? LegacyLibrary.PARALLEL : LegacyLibrary.COMBINATORICSLIB3;
            String concatenator = random.nextInt(4) == 0 ? "," : "";
            AssemblyOptions options = new AssemblyOptions(RunConfig.EmptyStagePolicy.PRESERVE_EMPTY, distinct, Long.MAX_VALUE,
                    RunConfig.OptionalMode.AUTO, absent, sizes, RunConfig.SizeSet.all(), Long.MAX_VALUE, library, 0,
                    Coverage.Method.GREEDY, 0, 2_000_000L);
            RenderOptions render = new RenderOptions(concatenator, RunConfig.SheetConcatenator.APPLY, true, List.of(), List.of(),
                    List.of(), List.of(), false);
            try (Scenario.Run run = s.run(GenerationOptions.defaults()); Assembly a = run.assemble(options, render)) {
                List<Expected> expected = model(run, options, render);
                List<Candidate> actual = run.candidates(a);
                List<String> sources = run.sources(a, render);
                assertEquals(expected.size(), actual.size(), "scenario " + scenario + " " + run.plan().canonicalText());
                for (int i = 0; i < expected.size(); i++) {
                    assertEquals(expected.get(i), new Expected(actual.get(i).display(), actual.get(i).states(), sources.get(i)),
                            "scenario " + scenario + " candidate " + i + "\n" + run.plan().canonicalText());
                }
                assertEquals(BigInteger.valueOf(expected.size()), a.generated());
                compared += expected.size();
                withOptional += a.optionalSizes().isEmpty() ? 0 : 1;
                withDistinct += distinct ? 1 : 0;
            }
        }
        assertTrue(compared > 3000 && withOptional > 50 && withDistinct > 50, compared + " candidates, " + withOptional
                + " scenarios with optional sheets, " + withDistinct + " de-duplicated");
    }

    /** The legacy assembly, materialized. */
    private static List<Expected> model(Scenario.Run run, AssemblyOptions options, RenderOptions render) {
        // columns: mandatory and optional sheets with directives, not consumed, by sheet key; empty mandatory: no candidates
        record Column(String sheet, int key, boolean optional, List<Row> rows, List<Integer> originalRanks, String prefix, String ending) {
        }
        List<Column> columns = new ArrayList<>();
        boolean emptyMandatory = false;
        for (SheetOutcome o : run.result().result().sheets()) {
            if (o.consumedBy() != null || (o.role() != SheetRole.MANDATORY && o.role() != SheetRole.OPTIONAL)) {
                continue;
            }
            List<Row> all = Scenario.all(o.rows());
            List<Row> rows = new ArrayList<>();
            List<Integer> ranks = new ArrayList<>();
            for (int r = 0; r < all.size(); r++) {
                boolean seen = false;
                for (Row k : rows) {
                    seen |= options.distinct() && k.keys().equals(all.get(r).keys());
                }
                if (!seen) {
                    rows.add(all.get(r));
                    ranks.add(r);
                }
            }
            var decoration = run.plan().decorations().stream().filter(d -> d.sheet().equals(o.sheet())).findFirst().orElseThrow();
            columns.add(new Column(o.sheet(), o.key(), o.role() == SheetRole.OPTIONAL, rows, ranks, decoration.prefix(), decoration.ending()));
            emptyMandatory |= o.role() == SheetRole.MANDATORY && all.isEmpty();
        }
        columns.sort((x, y) -> Integer.compare(x.key(), y.key()));
        List<Column> mandatory = columns.stream().filter(c -> !c.optional()).toList();
        List<Column> optional = columns.stream().filter(Column::optional).toList();
        if (emptyMandatory) {
            return List.of();
        }
        // the mandatory product with its legacy ids (rank in the product of the original tables, plus one)
        List<int[]> product = new ArrayList<>();
        product.add(new int[0]);
        for (Column c : mandatory) {
            List<int[]> next = new ArrayList<>();
            for (int[] prefix : product) {
                for (int r = 0; r < c.rows().size(); r++) {
                    int[] t = java.util.Arrays.copyOf(prefix, prefix.length + 1);
                    t[prefix.length] = r;
                    next.add(t);
                }
            }
            product = next;
        }
        // fw_opt{k}: subsets in the library's order, empty sheets skipped, rows numbered as inserted
        int n = optional.size();
        List<List<int[]>> optionalRows = new ArrayList<>();   // per size: entries {subset..., -1, ranks...}
        List<List<String>> optionalIds = new ArrayList<>();
        List<Integer> sizesUsed = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            if (!options.sizes().contains(k, n)) {
                continue;
            }
            List<int[]> rows = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            long inserted = 0;
            RankedTuples subsets = Operators.combinations(n, k, options.library());
            try (TupleCursor cursor = subsets.cursor(BigInteger.ZERO, subsets.size(), Cancellation.NONE)) {
                while (cursor.hasNext()) {
                    int[] subset = cursor.next();
                    boolean empty = false;
                    long legacyRows = 1;
                    for (int f : subset) {
                        empty |= optional.get(f).rows().isEmpty();
                        legacyRows *= originalSize(run, optional.get(f).sheet());
                    }
                    if (empty) {
                        continue;
                    }
                    List<int[]> local = new ArrayList<>();
                    local.add(new int[0]);
                    for (int f : subset) {
                        List<int[]> next = new ArrayList<>();
                        for (int[] p : local) {
                            for (int r = 0; r < optional.get(f).rows().size(); r++) {
                                int[] t = java.util.Arrays.copyOf(p, p.length + 1);
                                t[p.length] = r;
                                next.add(t);
                            }
                        }
                        local = next;
                    }
                    for (int[] ranks : local) {
                        int[] entry = new int[subset.length * 2];
                        System.arraycopy(subset, 0, entry, 0, subset.length);
                        System.arraycopy(ranks, 0, entry, subset.length, ranks.length);
                        rows.add(entry);
                        long legacyRank = 0;
                        for (int i = 0; i < subset.length; i++) {
                            legacyRank = legacyRank * originalSize(run, optional.get(subset[i]).sheet())
                                    + optional.get(subset[i]).originalRanks().get(ranks[i]);
                        }
                        ids.add(Long.toString(inserted + legacyRank + 1));
                    }
                    inserted += legacyRows;
                }
            }
            optionalRows.add(rows);
            optionalIds.add(ids);
            sizesUsed.add(k);
        }
        List<Expected> out = new ArrayList<>();
        boolean enabled = n > 0;
        boolean withAbsent = !enabled || options.includeAbsent();
        int phases = (withAbsent ? 1 : 0) + optionalRows.size();
        for (int phase = 0; phase < phases; phase++) {
            boolean absentPhase = withAbsent && phase == 0;
            int sizeIndex = phase - (withAbsent ? 1 : 0);
            for (int[] m : product) {
                long finalRank = 0;
                for (int i = 0; i < mandatory.size(); i++) {
                    finalRank = finalRank * originalSize(run, mandatory.get(i).sheet()) + mandatory.get(i).originalRanks().get(m[i]);
                }
                List<int[]> branches = absentPhase ? List.of(new int[0]) : optionalRows.get(sizeIndex);
                for (int b = 0; b < branches.size(); b++) {
                    int[] branch = branches.get(b);
                    Row[] chosen = new Row[columns.size()];
                    SelectionState[] states = new SelectionState[columns.size()];
                    java.util.Arrays.fill(states, SelectionState.ABSENT);
                    for (int i = 0; i < mandatory.size(); i++) {
                        int c = columns.indexOf(mandatory.get(i));
                        chosen[c] = mandatory.get(i).rows().get(m[i]);
                        states[c] = chosen[c].keys().equals(mandatory.get(i).rows().getFirst().keys()) ? SelectionState.BASELINE_REFERENCE
                                : SelectionState.PRESENT;
                    }
                    int k = branch.length / 2;
                    for (int i = 0; i < k; i++) {
                        Column col = optional.get(branch[i]);
                        int c = columns.indexOf(col);
                        chosen[c] = col.rows().get(branch[k + i]);
                        states[c] = SelectionState.PRESENT;
                    }
                    StringBuilder source = new StringBuilder();
                    String lastSeparator = "";
                    for (int c = 0; c < columns.size(); c++) {
                        if (chosen[c] == null) {
                            continue;
                        }
                        source.append(columns.get(c).prefix());
                        for (int label : chosen[c].keys().labels()) {
                            source.append(run.dictionary().value(label).text());
                        }
                        lastSeparator = render.concatenator().isEmpty() ? columns.get(c).ending() : render.concatenator();
                        source.append(lastSeparator);
                    }
                    String text = source.substring(0, source.length() - lastSeparator.length());
                    String display = (finalRank + 1) + (absentPhase ? "_0_0" : "_" + optionalIds.get(sizeIndex).get(b) + "_" + sizesUsed.get(sizeIndex));
                    out.add(new Expected(display, List.of(states), text));
                }
            }
        }
        return out;
    }

    private static long originalSize(Scenario.Run run, String sheet) {
        return Scenario.all(run.table(sheet)).size();
    }
}
