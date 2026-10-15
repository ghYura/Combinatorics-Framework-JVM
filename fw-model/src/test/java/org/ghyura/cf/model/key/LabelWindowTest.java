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

package org.ghyura.cf.model.key;

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Decision F2: the legacy 16-bit tier's label window. */
class LabelWindowTest {

    private static final SourceLocation WHERE = SourceLocation.of("w.json");

    /** The legacy algorithm as written (DataTypeDispatcher.lengthPrefix and uniformBestBase): a prefix table, bases scanned down. */
    private static int legacyBestBase(int n, int[] prefix) {
        int lo = LabelWindow.MIN;
        int hi = LabelWindow.MAX - n + 1;
        int best = hi;
        long bestCost = Long.MAX_VALUE;
        for (int base = hi; base >= lo; base--) {
            long cost = prefix[base + n - LabelWindow.MIN] - prefix[base - LabelWindow.MIN];
            if (cost < bestCost) {
                bestCost = cost;
                best = base;
            }
        }
        return best;
    }

    @Test
    void theWindowIsTheLegacyCheapestOneWithTheHighestBaseOnTies() {
        assertEquals(9, LabelWindow.bestBase(1), "one label: the first one-character label met scanning down from 32767");
        assertEquals(0, LabelWindow.bestBase(10), "0..9");
        assertEquals(-999, LabelWindow.bestBase(32767), "-100..-999 cost four characters, less than 10000.. at five");
        assertEquals(-7232, LabelWindow.bestBase(40000));
        assertEquals(-32766, LabelWindow.bestBase(65534), "leaving out -32767 (six characters) is cheaper than leaving out 32767");
        assertEquals(-32767, LabelWindow.bestBase(65535), "the whole range");
        int[] prefix = new int[LabelWindow.CAPACITY + 1];
        for (int i = 0; i < LabelWindow.CAPACITY; i++) {
            prefix[i + 1] = prefix[i] + LabelWindow.chars(LabelWindow.MIN + i);
        }
        Random random = new Random(7);
        for (int i = 0; i < 150; i++) {
            int n = 1 + random.nextInt(LabelWindow.CAPACITY);
            assertEquals(legacyBestBase(n, prefix), LabelWindow.bestBase(n), "n=" + n);
        }
        assertEquals(1, LabelWindow.chars(0));
        assertEquals(2, LabelWindow.chars(-1));
        assertEquals(6, LabelWindow.chars(-32767));
        assertEquals(5, LabelWindow.chars(32767));
    }

    @Test
    void preserveKeepsTheLegacyNumberingAndLegacyWindowMovesOnlyWhenItDoesNotFit() {
        LabelWindow preserve = LabelWindow.place(LabelWindow.Mode.PRESERVE, LabelWindow.Scope.ALL, 3, 0, 50_000, WHERE);
        assertEquals(4, preserve.firstLabel(), "preserve never moves; the encoding census refuses what does not fit");
        assertFalse(preserve.shifted());
        LabelWindow fits = LabelWindow.place(LabelWindow.Mode.LEGACY_WINDOW, LabelWindow.Scope.ALL, 3, 0, 32_764, WHERE);
        assertEquals(4, fits.firstLabel(), "S+C = 32767 still fits");
        assertFalse(fits.shifted());
        LabelWindow moved = LabelWindow.place(LabelWindow.Mode.LEGACY_WINDOW, LabelWindow.Scope.ALL, 3, 0, 40_000, WHERE);
        assertEquals(-7232, moved.firstLabel());
        assertTrue(moved.shifted());
        assertEquals(32767, moved.lastLabel(40_000));
        assertEquals("labels=legacy-window;scope=all;first=-7232", moved.canonical());
    }

    @Test
    void theGateCountsAsTheScopeSaysAndRefusesWhatTheLegacyRefused() {
        FrameworkException all = assertThrows(FrameworkException.class,
                () -> LabelWindow.place(LabelWindow.Mode.LEGACY_WINDOW, LabelWindow.Scope.ALL, 10, 5, 65_525, WHERE));
        assertEquals(ErrorCode.KEY_CENSUS_OVERFLOW, all.diagnostics().getFirst().code(), "10 + 65525 + 5 = 65540 keys");
        LabelWindow cells = LabelWindow.place(LabelWindow.Mode.LEGACY_WINDOW, LabelWindow.Scope.CELLS, 10, 5, 65_525, WHERE);
        assertEquals(LabelWindow.bestBase(65_525), cells.firstLabel(), "scope cells counts only the values");
        assertThrows(FrameworkException.class,
                () -> LabelWindow.place(LabelWindow.Mode.LEGACY_WINDOW, LabelWindow.Scope.CELLS, 1, 0, 65_536, WHERE));
        assertThrows(FrameworkException.class,
                () -> LabelWindow.place(LabelWindow.Mode.LEGACY_WINDOW, LabelWindow.Scope.CELLS, 32_000, 768, 1, WHERE),
                "sheets plus virtual sheets above 32767");
    }
}
