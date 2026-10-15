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

import org.ghyura.cf.constraints.CandidateView;
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.key.SelectionState;
import org.ghyura.cf.model.row.Row;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * One assembled candidate: for every column of the assembly, the row it holds and that row's state (B07): a present
 * row (possibly empty), a mandatory row equal to the baseline row (the legacy NULLIF-compressed cell, which resolves
 * to the baseline's keys), or an absent optional sheet.
 *
 * @param ordinal     the position in the generated space (absent block first, then each optional size)
 * @param mandatory   the rank of the mandatory row in the mandatory product (after de-duplication and cut-off)
 * @param size        the number of optional sheets combined; 0 for the absent branch
 * @param optional    the rank inside that size's optional rows; -1 for the absent branch
 * @param rows        per column, the row, or null when the column's optional sheet is absent
 * @param states      per column, the state
 * @param display     the legacy display id: finalId_0_0, or finalId_optId_size
 * @param coordinates the exact combinatorial coordinates (original ranks in the sheets' tables), the identity's input
 */
public record Candidate(BigInteger ordinal, BigInteger mandatory, int size, BigInteger optional, List<Row> rows,
                        List<SelectionState> states, String display, String coordinates, String context) implements CandidateView {

    public Candidate {
        Objects.requireNonNull(ordinal, "ordinal");
        rows = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(rows));
        states = List.copyOf(states);
    }

    /** The keys of column {@code c}, or null when its sheet is absent. */
    public KeyRow keys(int column) {
        Row row = rows.get(column);
        return row == null ? null : row.keys();
    }

    @Override
    public int[] labels(int column) {
        Row row = rows.get(column);
        return row == null ? null : row.keys().labels();
    }

    /** Whether no optional sheet is present. */
    public boolean absentBranch() {
        return size == 0;
    }

    /**
     * The candidate's identity (D05): SHA-256 of the assembly context (schema, plan, dictionary and label window,
     * generation, assembly and rendering policy, seed) and the exact coordinates. Selection (rules, covering) is not part
     * of it: it decides which candidates run, not what a candidate is.
     */
    public CandidateId id() {
        return new CandidateId(Canonical.sha256Hex(context + "\n" + coordinates));
    }
}
