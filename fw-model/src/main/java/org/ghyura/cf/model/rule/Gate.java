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

/**
 * The positional gate of a bond (legacy sieve {@code _gate_ok_multi}): which placements of the referenced sheets the
 * bond looks at. Positions are the columns of the candidate; every value of one sheet shares that sheet's column.
 *
 * @param adjacent the chosen positions must form a contiguous run ({@code max - min == count - 1})
 * @param within   when not negative, the chosen positions must fit a window of this width ({@code max - min <= within});
 *                 -1 when the gate has none. Adjacency wins when both are set, as in the legacy code.
 */
public record Gate(boolean adjacent, long within) {

    /** Any co-occurrence. */
    public static final Gate ANY = new Gate(false, -1);

    public Gate {
        if (within < -1) {
            throw new IllegalArgumentException("within is a non-negative width, or -1 for none: " + within);
        }
    }

    /** Whether the gate passes for these positions (an empty gate passes everything). */
    public boolean admits(int[] positions) {
        if (!adjacent && within < 0) {
            return true;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int p : positions) {
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        long span = (long) max - min;
        if (adjacent) {
            return span == positions.length - 1;
        }
        return span <= within;
    }

    public boolean any() {
        return !adjacent && within < 0;
    }

    String canonical() {
        return any() ? "any" : adjacent ? "adjacent" + (within >= 0 ? "+within=" + within : "") : "within=" + within;
    }
}
