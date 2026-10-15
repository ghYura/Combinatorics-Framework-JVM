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

package org.ghyura.cf.plan;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.spec.SheetDecoration;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The parsed and validated FW_Seq: for every real and virtual sheet its flags and directives, plus the few
 * facts the legacy parser kept next to them. Immutable. Everything a joiner or directive refers to has
 * been resolved to a sheet before a plan exists, so an unknown reference cannot reach execution.
 *
 * @param sheets          data sheets in key order (1..S), then virtual sheets (S+1..)
 * @param headlessTargets row ordinal of a headless FW_Seq row to the key of the sheet generated as its target
 * @param firstOperands   keys of the first operand table of every joiner met, in the order they were read
 * @param secondOperands  keys of the second operand table of every joiner met, in the order they were read
 * @param decorations     the FW_SheetNames rows, plus (when enabled) an empty one for each unlisted sheet
 * @param options         the options the plan was made with; they are part of its identity
 */
public record SeqPlan(List<SheetPlan> sheets, Map<Integer, Integer> headlessTargets, List<Integer> firstOperands,
                      List<Integer> secondOperands, List<SheetDecoration> decorations, PlanOptions options) {

    public SeqPlan {
        Objects.requireNonNull(options, "options");
        sheets = List.copyOf(sheets);
        headlessTargets = Map.copyOf(headlessTargets);
        firstOperands = List.copyOf(firstOperands);
        secondOperands = List.copyOf(secondOperands);
        decorations = List.copyOf(decorations);
    }

    public Optional<SheetPlan> sheet(String name) {
        for (SheetPlan sheet : sheets) {
            if (sheet.name().equals(name)) {
                return Optional.of(sheet);
            }
        }
        return Optional.empty();
    }

    public Optional<SheetPlan> sheetByKey(int key) {
        return key >= 1 && key <= sheets.size() ? Optional.of(sheets.get(key - 1)) : Optional.empty();
    }

    public long virtualSheetCount() {
        return sheets.stream().filter(SheetPlan::virtual).count();
    }

    /** The sheets that FW_Seq gave directives to, in key order. */
    public List<SheetPlan> activeSheets() {
        return sheets.stream().filter(SheetPlan::active).toList();
    }

    /**
     * A deterministic text of the plan's content, independent of where anything was written. Every field is
     * escaped or a number or a fixed token, fields are separated by tabs and records by line breaks, so two
     * plans that differ in any semantic field differ here; directives contribute their typed
     * {@link Directive#identity()}. Version 2 replaced the readable directive forms of version 1, which could
     * not tell some replacement templates apart (review finding IP01).
     */
    public String canonicalText() {
        StringBuilder out = new StringBuilder(1024);
        out.append("cf.plan/2\n");
        out.append("OPT\t").append(options.autoGenerateMissingSheets()).append('\t')
                .append(Canonical.escape(options.virtualSheetPrefix())).append('\t').append(options.headlessRowsGetTarget())
                .append('\t').append(options.nestedJoinersEnabled()).append('\t').append(options.autoPromoteSingleVerb())
                .append('\t').append(options.autoGenerateSheetNames()).append('\n');
        for (SheetPlan sheet : sheets) {
            out.append("SHEET\t").append(sheet.key()).append('\t').append(Canonical.escape(sheet.name())).append('\t')
                    .append(sheet.virtual() ? 'V' : 'D').append('\t').append(sheet.role()).append('\t')
                    .append(sheet.heading() ? 'H' : '-').append(sheet.lastInQueue() ? 'L' : '-')
                    .append(sheet.reuse() ? 'U' : '-').append(sheet.reuseTableOnly() ? 'T' : '-').append(sheet.promoted() ? 'P' : '-')
                    .append('\t').append(sheet.concatenator() == null ? "-" : "=" + Canonical.escape(sheet.concatenator()))
                    .append('\t').append(sheet.valueCount()).append('\n');
            for (Directive directive : sheet.directives()) {
                out.append("DIR\t").append(directive.identity()).append('\n');
            }
        }
        headlessTargets.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.append("HEADLESS\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n'));
        out.append("FIRST\t").append(firstOperands).append('\n');
        out.append("SECOND\t").append(secondOperands).append('\n');
        for (SheetDecoration d : decorations) {
            out.append("DECOR\t").append(Canonical.escape(d.sheet())).append('\t').append(Canonical.escape(d.prefix()))
                    .append('\t').append(Canonical.escape(d.ending())).append('\n');
        }
        return out.toString();
    }

    public String contentDigest() {
        return Canonical.sha256Hex(canonicalText());
    }
}
