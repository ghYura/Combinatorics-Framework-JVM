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
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Runs the stages of every sheet that has directives, in dependency order: a sheet whose joiner reads other sheets
 * runs after them; sheets that do not depend on each other run in sheet-key order. The legacy Core ran sheets on
 * threads and made a joiner wait for its operands; the tables are the same, and where the legacy result depended on
 * timing (two joiners consuming one table without FW_Reuse) this order decides, deterministically.
 *
 * <p>Tables are computed where their rows follow from their inputs and stored otherwise; everything stored is
 * reserved from the memory budget before it is allocated and released when the result is closed, or at once when
 * the generation fails or is cancelled.</p>
 */
public final class Generation {

    private Generation() {
    }

    /**
     * @throws org.ghyura.cf.model.FrameworkException RESOURCE_LIMIT when a stored table or index does not fit the
     *                                                budget, RUN_CANCELLED on cancellation, GEN_REPLACEMENT_INVALID
     */
    public static Result run(SeqPlan plan, KeyDictionary dictionary, GenerationOptions options, MemoryBudget budget,
                             Cancellation cancellation) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(dictionary, "dictionary");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(cancellation, "cancellation");
        Run run = new Run(plan, dictionary, options, budget, cancellation);
        try {
            for (SheetPlan sheet : order(plan)) {
                cancellation.throwIfCancelled();
                RowSource table = new SheetStages(sheet, run).run();
                run.tables.put(sheet.name(), table);
                if (table.size().signum() == 0) {
                    run.diagnostics.add(Diagnostic.warning(ErrorCode.GEN_EMPTY_TABLE, "sheet " + sheet.name() + " produced no rows; "
                            + "under the default empty-stage policy a mandatory sheet without rows leaves no candidates, where the "
                            + "legacy Core dropped the sheet", sheet.location(), "sheet", sheet.name()));
                }
            }
            List<SheetOutcome> outcomes = new ArrayList<>();
            for (SheetPlan sheet : plan.activeSheets()) {
                outcomes.add(new SheetOutcome(sheet.name(), sheet.key(), sheet.role(), run.tables.get(sheet.name()),
                        run.consumedBy.get(sheet.name())));
            }
            return new Result(new GenerationResult(outcomes, run.diagnostics), run.arena, budget);
        } catch (RuntimeException | Error e) {
            run.arena.close();
            throw e;
        }
    }

    /**
     * The generation's tables with their memory reservations: the reservations last until {@link #close}, which
     * returns them to the budget; the tables cannot be read afterwards.
     */
    public static final class Result implements AutoCloseable {
        private final GenerationResult result;
        private final Arena arena;
        private final long reservedAtEnd;

        private Result(GenerationResult result, Arena arena, MemoryBudget budget) {
            this.result = result;
            this.arena = arena;
            this.reservedAtEnd = budget.reservedBytes();
        }

        public GenerationResult result() {
            return result;
        }

        /** The bytes the budget had reserved when the generation finished (the run's other reservations included). */
        public long reservedBytesAtEnd() {
            return reservedAtEnd;
        }

        @Override
        public void close() {
            arena.close();
        }
    }

    /**
     * Active sheets in dependency order, ties by sheet key (Kahn's algorithm with a key-ordered queue): the order the stages
     * run in, shared with the authoring views that predict operand lifetimes.
     */
    public static List<SheetPlan> order(SeqPlan plan) {
        Map<String, SheetPlan> active = new LinkedHashMap<>();
        for (SheetPlan sheet : plan.activeSheets()) {
            active.put(sheet.name(), sheet);
        }
        Map<String, List<String>> readers = new HashMap<>();
        Map<String, Integer> waiting = new HashMap<>();
        for (SheetPlan sheet : active.values()) {
            int count = 0;
            for (Directive directive : sheet.directives()) {
                if (directive instanceof Directive.Joiner joiner) {
                    for (Directive.Operand operand : List.of(joiner.first(), joiner.second())) {
                        String name = operand.sheet();
                        if (name != null && active.containsKey(name) && !name.equals(sheet.name())) {
                            readers.computeIfAbsent(name, k -> new ArrayList<>()).add(sheet.name());
                            count++;
                        }
                    }
                }
            }
            waiting.put(sheet.name(), count);
        }
        PriorityQueue<SheetPlan> ready = new PriorityQueue<>((a, b) -> Integer.compare(a.key(), b.key()));
        for (SheetPlan sheet : active.values()) {
            if (waiting.get(sheet.name()) == 0) {
                ready.add(sheet);
            }
        }
        List<SheetPlan> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            SheetPlan next = ready.poll();
            order.add(next);
            for (String reader : readers.getOrDefault(next.name(), List.of())) {
                if (waiting.merge(reader, -1, Integer::sum) == 0) {
                    ready.add(active.get(reader));
                }
            }
        }
        if (order.size() != active.size()) {
            throw new IllegalStateException("joiners wait for each other in a circle; the planner reports that before generation");
        }
        return order;
    }

    /** The run's shared state, seen by every sheet's stages through {@link SheetStages.Context}. */
    private static final class Run implements SheetStages.Context {
        private final SeqPlan plan;
        private final KeyDictionary dictionary;
        private final GenerationOptions options;
        private final MemoryBudget budget;
        private final Cancellation cancellation;
        private final Arena arena = new Arena();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final Map<String, RowSource> tables = new LinkedHashMap<>();
        private final Map<String, String> consumedBy = new HashMap<>();

        Run(SeqPlan plan, KeyDictionary dictionary, GenerationOptions options, MemoryBudget budget, Cancellation cancellation) {
            this.plan = plan;
            this.dictionary = dictionary;
            this.options = options;
            this.budget = budget;
            this.cancellation = cancellation;
        }

        @Override
        public int[] values(String sheet) {
            return plan.sheet(sheet).filter(s -> !s.virtual()).map(s -> dictionary.labelsOf(s.key())).orElse(new int[0]);
        }

        @Override
        public RowSource operand(String sheet, String reader) {
            RowSource table = tables.get(sheet);
            if (table == null) {
                return RowSource.empty();
            }
            String consumer = consumedBy.get(sheet);
            boolean reuse = plan.sheet(sheet).map(SheetPlan::reuse).orElse(false);
            if (consumer != null && !reuse) {
                diagnostics.add(Diagnostic.warning(ErrorCode.GEN_OPERAND_CONSUMED, "the joiner of " + reader + " reads " + sheet
                        + ", whose rows the joiner of " + consumer + " consumed (no FW_Reuse), so it reads none",
                        plan.sheet(reader).map(SheetPlan::location).orElse(SourceLocation.NONE),
                        "sheet", sheet, "reader", reader, "consumer", consumer));
                return RowSource.empty();
            }
            return table;
        }

        @Override
        public void consume(String sheet, String reader) {
            consumedBy.putIfAbsent(sheet, reader);
        }

        @Override
        public KeyEncoding encoding() {
            return dictionary.encoding();
        }

        @Override
        public GenerationOptions options() {
            return options;
        }

        @Override
        public MemoryBudget budget() {
            return budget;
        }

        @Override
        public Cancellation cancellation() {
            return cancellation;
        }

        @Override
        public List<Diagnostic> diagnostics() {
            return diagnostics;
        }

        @Override
        public Arena arena() {
            return arena;
        }
    }
}
