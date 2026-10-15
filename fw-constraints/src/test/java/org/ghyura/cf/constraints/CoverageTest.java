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

package org.ghyura.cf.constraints;

import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Greedy t-wise selection worked out by hand. */
class CoverageTest {

    private static List<int[]> product(int... levels) {
        List<int[]> rows = new ArrayList<>();
        rows.add(new int[levels.length]);
        for (int f = 0; f < levels.length; f++) {
            List<int[]> next = new ArrayList<>();
            for (int[] r : rows) {
                for (int l = 0; l < levels[f]; l++) {
                    int[] x = r.clone();
                    x[f] = l;
                    next.add(x);
                }
            }
            rows = next;
        }
        return rows;
    }

    @Test
    void streamingGreedyAndSetCoverChooseDifferentlyOnATwoByTwo() {
        List<int[]> rows = product(2, 2);   // [0,0] [0,1] [1,0] [1,1]
        Coverage.Selection greedy = Coverage.select(rows, new int[]{2, 2}, 1, Coverage.Method.GREEDY, Cancellation.NONE);
        assertArrayEquals(new int[]{0, 1, 2}, greedy.kept(), "[1,1] brings nothing new after the first three");
        Coverage.Selection cover = Coverage.select(rows, new int[]{2, 2}, 1, Coverage.Method.SET_COVER, Cancellation.NONE);
        assertArrayEquals(new int[]{0, 3}, cover.kept(), "every row covers two tuples; the lowest index wins, then [1,1]");
        assertEquals(4, cover.universe());
        assertEquals(4, cover.feasible());
        assertEquals(4, cover.covered());
        assertArrayEquals(new int[]{0, 1, 2, 3}, Coverage.select(rows, new int[]{2, 2}, 2, Coverage.Method.GREEDY, Cancellation.NONE).kept());
    }

    @Test
    void tuplesNoAllowedRowContainsAreInfeasibleNotUncovered() {
        List<int[]> rows = new ArrayList<>(product(2, 2));
        rows.remove(3);                     // a rule forbade [1,1]
        Coverage.Selection s = Coverage.select(rows, new int[]{2, 2}, 2, Coverage.Method.SET_COVER, Cancellation.NONE);
        assertEquals(4, s.universe());
        assertEquals(3, s.feasible());
        assertEquals(1, s.infeasible(), "the pair (1,1) is infeasible");
        assertEquals(3, s.covered());
    }

    @Test
    void theBudgetPicksTheStrongestStrengthThatFitsAndStrengthOneOtherwise() {
        List<int[]> rows = product(3, 3, 3);   // 27 rows
        Coverage.Selection fits = Coverage.forBudget(rows, new int[]{3, 3, 3}, 27, Coverage.Method.GREEDY, Cancellation.NONE);
        assertEquals(0, fits.strength(), "the whole product fits");
        Coverage.Selection t2 = Coverage.forBudget(rows, new int[]{3, 3, 3}, 12, Coverage.Method.SET_COVER, Cancellation.NONE);
        assertEquals(2, t2.strength());
        Coverage.Selection tiny = Coverage.forBudget(rows, new int[]{3, 3, 3}, 1, Coverage.Method.GREEDY, Cancellation.NONE);
        assertEquals(1, tiny.strength(), "strength 1 even above the budget, as the legacy returned it");
    }

    @Test
    void selectionPollsCancellation() {
        AtomicInteger polls = new AtomicInteger();
        Cancellation cancelled = () -> polls.incrementAndGet() > 1;
        assertThrows(org.ghyura.cf.model.FrameworkException.class,
                () -> Coverage.select(product(40, 40), new int[]{40, 40}, 2, Coverage.Method.SET_COVER, cancelled));
    }
}
