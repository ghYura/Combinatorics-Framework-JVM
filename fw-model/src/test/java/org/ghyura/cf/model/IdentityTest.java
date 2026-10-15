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

package org.ghyura.cf.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The five-field sample identity and the three id types it is built from (D05, gate A08 foundations). */
class IdentityTest {

    private static final String HEX_A = "a".repeat(64);
    private static final String HEX_B = "0123456789abcdef".repeat(4);
    private static final RunId RUN = new RunId("run-2026.10_01");
    private static final CandidateId CAND_A = new CandidateId(HEX_A);
    private static final CandidateId CAND_B = new CandidateId(HEX_B);

    @Test
    void runIdAcceptsOnlyAsciiTokens() {
        assertEquals("run-2026.10_01", RUN.toString());
        assertEquals(64, new RunId("x".repeat(64)).value().length());
        for (String bad : new String[]{"", " ", "a b", "a/b", "a:b", "x".repeat(65), "run\n", "б"}) {
            assertThrows(IllegalArgumentException.class, () -> new RunId(bad), "'" + bad + "'");
        }
        assertThrows(NullPointerException.class, () -> new RunId(null));
    }

    @Test
    void candidateIdIsExactly64LowercaseHexCharacters() {
        assertEquals(HEX_B, CAND_B.hex());
        for (String bad : new String[]{"", "a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64), "a".repeat(63) + "-"}) {
            assertThrows(IllegalArgumentException.class, () -> new CandidateId(bad), "'" + bad + "'");
        }
        assertThrows(NullPointerException.class, () -> new CandidateId(null));
    }

    @Test
    void aK1SampleUsesRepeatZeroAndTheEmptyDefaultEnvironment() {
        SampleSlot slot = SampleSlot.first(RUN, CAND_A);
        assertEquals(0, slot.repeatIndex());
        assertEquals("", slot.environmentId());
        SampleKey first = slot.attempt(0);
        assertEquals(new SampleKey(RUN, CAND_A, 0, 0, ""), first);
    }

    @Test
    void sampleKeyUniquenessUsesAllFiveFields() {
        Set<SampleKey> keys = new HashSet<>();
        keys.add(new SampleKey(RUN, CAND_A, 0, 0, ""));
        keys.add(new SampleKey(new RunId("other"), CAND_A, 0, 0, ""));
        keys.add(new SampleKey(RUN, CAND_B, 0, 0, ""));
        keys.add(new SampleKey(RUN, CAND_A, 1, 0, ""));
        keys.add(new SampleKey(RUN, CAND_A, 0, 1, ""));
        keys.add(new SampleKey(RUN, CAND_A, 0, 0, "gpu-1"));
        assertEquals(6, keys.size());
        assertTrue(keys.contains(new SampleKey(RUN, CAND_A, 0, 0, "")));
    }

    @Test
    void attemptsOfOneSlotShareTheSlotAndOnlyTheAttemptDiffers() {
        SampleKey a0 = new SampleKey(RUN, CAND_A, 0, 2, "env.1");
        SampleKey a1 = a0.nextAttempt();
        assertEquals(1, a1.attempt());
        assertEquals(a0.slot(), a1.slot());
        assertNotEquals(a0, a1);
        assertEquals(a1, a0.slot().attempt(1));
        assertEquals(List.of(2, 2), List.of(a0.repeatIndex(), a1.repeatIndex()));
    }

    @Test
    void differentRepeatsOrEnvironmentsAreDifferentSlots() {
        SampleSlot base = new SampleSlot(RUN, CAND_A, 0, "");
        assertNotEquals(base, new SampleSlot(RUN, CAND_A, 1, ""));
        assertNotEquals(base, new SampleSlot(RUN, CAND_A, 0, "e"));
        assertNotEquals(base, new SampleSlot(RUN, CAND_B, 0, ""));
    }

    @Test
    void sampleKeyRejectsNegativeCountersAndBadEnvironments() {
        assertThrows(IllegalArgumentException.class, () -> new SampleKey(RUN, CAND_A, -1, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new SampleKey(RUN, CAND_A, 0, -1, ""));
        assertThrows(IllegalArgumentException.class, () -> new SampleKey(RUN, CAND_A, 0, 0, "has space"));
        assertThrows(IllegalArgumentException.class, () -> new SampleKey(RUN, CAND_A, 0, 0, "e".repeat(65)));
        assertThrows(IllegalArgumentException.class, () -> new SampleSlot(RUN, CAND_A, -1, ""));
        assertThrows(IllegalArgumentException.class, () -> new SampleSlot(RUN, CAND_A, 0, "bad env"));
        assertThrows(ArithmeticException.class,
                () -> new SampleKey(RUN, CAND_A, Integer.MAX_VALUE, 0, "").nextAttempt());
    }

    @Test
    void onlyPassAndDomainFailAreDomainVerdicts() {
        Set<Outcome> verdicts = new HashSet<>();
        for (Outcome o : Outcome.values()) {
            if (o.isDomainVerdict()) {
                verdicts.add(o);
            }
        }
        assertEquals(Set.of(Outcome.PASS, Outcome.DOMAIN_FAIL), verdicts);
        assertEquals(7, Outcome.values().length);
        assertFalse(Outcome.TIMEOUT.isDomainVerdict());
        assertFalse(Outcome.INFRA_FAIL.isDomainVerdict());
        assertFalse(Outcome.BROKEN.isDomainVerdict());
        assertFalse(Outcome.SKIPPED.isDomainVerdict());
        assertFalse(Outcome.CANCELLED.isDomainVerdict());
    }
}
