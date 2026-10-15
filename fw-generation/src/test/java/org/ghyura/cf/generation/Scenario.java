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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.key.KeyEncodingPolicy;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetDecoration;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.ghyura.cf.plan.PlanOptions;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * A scenario for generation tests built through the real pipeline (spec, planner, key dictionary), so a test states
 * sheets, values and FW_Seq rows the way a workbook would and reads rows back as value names.
 */
final class Scenario {

    static final long BIG = 1L << 30;
    static final PlanOptions LEGACY = PlanOptions.legacyDefaults();
    /** The legacy defaults without the auto-promotion of a lone verb, for tests of a single pass. */
    static final PlanOptions UNPROMOTED = new PlanOptions(true, "FW_VIRTUAL_", true, true, false, true);

    private final List<SheetSpec> sheets = new ArrayList<>();
    private final List<SeqRow> rows = new ArrayList<>();
    private final List<SheetDecoration> decorations = new ArrayList<>();
    private ConstraintSet constraints = ConstraintSet.NONE;
    private PlanOptions options = LEGACY;

    static Scenario of() {
        return new Scenario();
    }

    /** A data sheet whose values are the given texts, in order. */
    Scenario sheet(String name, String... values) {
        List<ValueCell> cells = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            cells.add(ValueCell.of(values[i], ValueKind.TEXT, SourceLocation.ofPath("t.json", name + "!" + i)));
        }
        sheets.add(new SheetSpec(name, sheets.size() + 1, cells, SourceLocation.ofPath("t.json", name)));
        return this;
    }

    /** One FW_Seq row: the target sheet's name, then flags and directives. */
    Scenario seq(String... cells) {
        List<LocatedText> texts = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            texts.add(new LocatedText(cells[i], SourceLocation.ofPath("t.json", "FW_Seq!" + rows.size() + ":" + i)));
        }
        rows.add(new SeqRow(rows.size(), texts));
        return this;
    }

    Scenario options(PlanOptions planOptions) {
        this.options = planOptions;
        return this;
    }

    /** A FW_SheetNames row: the text a sheet's part of a candidate starts and ends with. */
    Scenario names(String sheet, String prefix, String ending) {
        decorations.add(new SheetDecoration(sheet, prefix, ending, SourceLocation.ofPath("t.json", "FW_SheetNames!" + sheet)));
        return this;
    }

    Scenario constraints(ConstraintSet layer) {
        this.constraints = layer;
        return this;
    }

    ScenarioSpec spec() {
        return new ScenarioSpec("t.json", sheets, new ControlSpec(decorations, List.of(), List.of(), List.of()), true, rows)
                .withConstraints(constraints);
    }

    SeqPlan plan() {
        return SeqPlanner.plan(spec(), options).plan();
    }

    Run run(GenerationOptions generation) {
        return run(generation, new MemoryBudget(BIG), Cancellation.NONE);
    }

    Run run(GenerationOptions generation, MemoryBudget budget, Cancellation cancellation) {
        ScenarioSpec spec = spec();
        SeqPlan plan = SeqPlanner.plan(spec, options).plan();
        KeyDictionary dictionary = KeyDictionary.build(spec, (int) plan.virtualSheetCount(), KeyEncodingPolicy.AUTO_PRESERVE, budget);
        try {
            Generation.Result result = Generation.run(plan, dictionary, generation, budget, cancellation);
            return new Run(plan, dictionary, result, budget, names(spec, dictionary));
        } catch (RuntimeException | Error e) {
            dictionary.close();
            throw e;
        }
    }

    /** Value names by label, read through the dictionary. */
    static Map<Integer, String> names(ScenarioSpec spec, KeyDictionary dictionary) {
        Map<Integer, String> names = new HashMap<>();
        for (SheetSpec sheet : spec.sheets()) {
            int[] labels = dictionary.labelsOf(sheet.key());
            for (int i = 0; i < labels.length; i++) {
                names.put(labels[i], sheet.cells().get(i).text());
            }
        }
        return names;
    }

    /** A finished generation: its tables, read back as names, valid until closed. */
    record Run(SeqPlan plan, KeyDictionary dictionary, Generation.Result result, MemoryBudget budget, Map<Integer, String> names)
            implements AutoCloseable {

        RowSource table(String sheet) {
            return result.result().sheet(sheet).orElseThrow(() -> new AssertionError("no outcome for " + sheet)).rows();
        }

        /** Every row of a sheet's final table as its value names separated by spaces. */
        List<String> rows(String sheet) {
            List<String> out = new ArrayList<>();
            for (Row row : all(table(sheet))) {
                out.add(text(row));
            }
            return out;
        }

        /** Every row as "id<-parent: names". */
        List<String> provenance(String sheet) {
            List<String> out = new ArrayList<>();
            for (Row row : all(table(sheet))) {
                out.add(row.id() + "<-" + row.parent() + ": " + text(row));
            }
            return out;
        }

        String text(Row row) {
            StringJoiner joiner = new StringJoiner(" ");
            for (int label : row.keys().labels()) {
                joiner.add(names.getOrDefault(label, "#" + label));
            }
            return joiner.toString();
        }

        List<ErrorCode> codes() {
            return result.result().diagnostics().stream().map(Diagnostic::code).toList();
        }

        String consumedBy(String sheet) {
            return result.result().sheet(sheet).orElseThrow().consumedBy();
        }

        /** The candidates of this run, with the given options. */
        Assembly assemble(AssemblyOptions assembly, RenderOptions render) {
            return Assembly.of(plan, result.result(), dictionary, GenerationOptions.defaults(), assembly, render, 1L, budget,
                    Cancellation.NONE);
        }

        /** Every candidate kept, rendered. */
        List<String> sources(Assembly assembly, RenderOptions render) {
            List<String> out = new ArrayList<>();
            try (CandidateRenderer renderer = CandidateRenderer.of(assembly, plan, dictionary, render, budget)) {
                java.util.Iterator<Candidate> it = assembly.candidates(Cancellation.NONE);
                while (it.hasNext()) {
                    out.add(renderer.renderText(it.next()));
                }
            }
            return out;
        }

        /** Every candidate kept. */
        List<Candidate> candidates(Assembly assembly) {
            List<Candidate> out = new ArrayList<>();
            assembly.candidates(Cancellation.NONE).forEachRemaining(out::add);
            return out;
        }

        @Override
        public void close() {
            result.close();
            dictionary.close();
        }
    }

    /** Every row of a table, through its cursor. */
    static List<Row> all(RowSource table) {
        List<Row> rows = new ArrayList<>();
        try (RowCursor cursor = table.open(Cancellation.NONE)) {
            while (cursor.hasNext()) {
                rows.add(cursor.next());
            }
        }
        return rows;
    }
}
