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

import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SheetPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which tables every joiner of a plan reads and what becomes of them (GEN:operator-authoring-ui; the legacy face1 grid's
 * operand lifetimes), predicted from the plan alone so an unsaved edit shows them: joiners in the order the stages run,
 * each operand as written and as resolved (FW_() and FW_()G name earlier joiner targets), its lifetime (FW_Reuse keeps its
 * rows for later joiners; FW_ReuseTableOnly or no flag lets its first joiner consume them), and the helper sheets a
 * full joiner names. A joiner that would read an operand an earlier joiner consumed is a GEN_OPERAND_CONSUMED warning
 * here, as the generation reports it when it runs: the Core reads an empty table and makes no rows, it does not refuse.
 */
public record OperandDependencies(List<Join> joins, List<Diagnostic> findings) {

    public OperandDependencies {
        joins = List.copyOf(joins);
        findings = List.copyOf(findings);
    }

    /** What happens to an operand's rows after a joiner read them. */
    public enum Lifetime {
        /** FW_Reuse: the rows stay for later joiners. */
        KEPT,
        /** FW_ReuseTableOnly (or a nested placeholder's target): the table stays, its first joiner consumes the rows. */
        TABLE_ONLY,
        /** No reuse flag: its first joiner consumes the rows. */
        CONSUMED
    }

    /**
     * One operand of a joiner.
     *
     * @param written    as the cell names it: a sheet, FW_() or FW_()G
     * @param sheet      the table it reads, or null when nothing resolves it (the plan reports that)
     * @param consumedBy the earlier joiner whose read left this operand empty, or null when it still has its rows
     */
    public record Operand(String written, String sheet, Lifetime lifetime, String consumedBy) {
        public boolean readsNothing() {
            return consumedBy != null;
        }
    }

    /**
     * One joiner, in the order the stages run.
     *
     * @param target  the sheet whose FW_Seq row holds it
     * @param helpers the start, relation, end and separator sheets it names, those it leaves empty left out
     */
    public record Join(String target, String multiplicity, Operand first, Operand second, List<String> helpers, String directive) {
        public Join {
            helpers = List.copyOf(helpers);
        }
    }

    /** The joiners of a plan and their operands' fate. */
    public static OperandDependencies of(SeqPlan plan) {
        List<Join> joins = new ArrayList<>();
        List<Diagnostic> findings = new ArrayList<>();
        Map<String, String> consumedBy = new HashMap<>();
        for (SheetPlan sheet : Generation.order(plan)) {
            for (Directive directive : sheet.directives()) {
                if (!(directive instanceof Directive.Joiner j)) {
                    continue;
                }
                Operand first = operand(plan, j.first(), consumedBy);
                Operand second = operand(plan, j.second(), consumedBy);
                for (Operand o : List.of(first, second)) {
                    if (o.readsNothing()) {
                        findings.add(Diagnostic.warning(ErrorCode.GEN_OPERAND_CONSUMED, "the joiner of " + sheet.name() + " would read "
                                + o.sheet() + ", whose rows the joiner of " + o.consumedBy() + " consumes (no FW_Reuse), so it would read "
                                + "none; add FW_Reuse to " + o.sheet() + " to keep its rows", j.location(), "sheet", o.sheet(), "reader",
                                sheet.name(), "consumer", o.consumedBy()));
                    }
                }
                // both reads come before the operands are consumed (the legacy cleanupOperand)
                for (Directive.Operand o : List.of(j.first(), j.second())) {
                    if (o.sheet() != null) {
                        consumedBy.putIfAbsent(o.sheet(), sheet.name());
                    }
                }
                List<String> helpers = new ArrayList<>();
                for (String h : List.of(j.start(), j.relation(), j.end(), j.separator())) {
                    if (!h.isEmpty()) {
                        helpers.add(h);
                    }
                }
                joins.add(new Join(sheet.name(), j.multiplicity().token(), first, second, helpers, j.source()));
            }
        }
        return new OperandDependencies(joins, findings);
    }

    private static Operand operand(SeqPlan plan, Directive.Operand o, Map<String, String> consumedBy) {
        String written = switch (o.kind()) {
            case SHEET -> o.sheet() == null ? "" : o.sheet();
            case NESTED -> "FW_()";
            case NESTED_GROUPED -> "FW_()G";
        };
        if (o.sheet() == null) {
            return new Operand(written, null, Lifetime.CONSUMED, null);
        }
        SheetPlan sheet = plan.sheet(o.sheet()).orElse(null);
        Lifetime lifetime = sheet == null ? Lifetime.CONSUMED : sheet.reuse() ? Lifetime.KEPT
                : sheet.reuseTableOnly() ? Lifetime.TABLE_ONLY : Lifetime.CONSUMED;
        String consumer = lifetime == Lifetime.KEPT ? null : consumedBy.get(o.sheet());
        return new Operand(written, o.sheet(), lifetime, consumer);
    }
}
