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

package org.ghyura.cf.model.rule;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One line of a legacy compiled bonds file (reader.constraints.bondsFile, read by the Reader's OptionalBondFilter):
 * {@code F|R} | gate flag | sheets | label tuples. Its labels are the legacy key labels, which this build numbers
 * identically. It is evaluated with the Reader filter's own rule: the bond applies only when every sheet it names is
 * present with at least one key; it holds when some tuple has each of its labels among its sheet's keys; with the gate
 * flag 0 a forbid bond never fires and a require bond always removes the candidate.
 *
 * @param forbid  F (a holding tuple removes the candidate) or R (a candidate where no tuple holds is removed)
 * @param gateOk  the gate flag the Python compiler precomputed from the sheets' positions
 * @param sheets  the sheets the tuples' labels belong to, in order
 * @param tuples  label tuples aligned with {@code sheets}
 */
public record LegacyBond(boolean forbid, boolean gateOk, List<String> sheets, List<List<Integer>> tuples,
                         SourceLocation location) {
    public LegacyBond {
        sheets = List.copyOf(sheets);
        List<List<Integer>> copy = new ArrayList<>();
        for (List<Integer> t : tuples) {
            copy.add(List.copyOf(t));
        }
        tuples = List.copyOf(copy);
        Objects.requireNonNull(location, "location");
        if (sheets.isEmpty()) {
            throw new IllegalArgumentException("a bond names at least one sheet");
        }
    }

    String canonical() {
        StringBuilder out = new StringBuilder("bond\t").append(forbid ? 'F' : 'R').append('\t').append(gateOk ? '1' : '0').append('\t');
        sheets.forEach(s -> out.append(Canonical.escape(s)).append(','));
        out.append('\t').append(tuples);
        return out.toString();
    }
}
