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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SuggestionsTest {

    @Test
    void editDistanceMatchesHandCountedValues() {
        assertEquals(0, Suggestions.editDistance("run", "run"));
        assertEquals(1, Suggestions.editDistance("run", "ruin"));   // one insertion
        assertEquals(1, Suggestions.editDistance("plan", "plam"));  // one substitution
        assertEquals(3, Suggestions.editDistance("kitten", "sitting")); // the classic example
        assertEquals(5, Suggestions.editDistance("", "hello"));
        assertEquals(5, Suggestions.editDistance("hello", ""));
    }

    @Test
    void closestPicksTheNearestWithinTheLimitAndNothingBeyondIt() {
        List<String> commands = List.of("run", "validate", "plan", "analyze", "resume", "export", "desktop", "capabilities");
        assertEquals("validate", Suggestions.closest("validat", commands, 2));
        assertEquals("capabilities", Suggestions.closest("capabilites", commands, 2));
        assertEquals("plan", Suggestions.closest("plna", commands, 2));
        assertNull(Suggestions.closest("zzzzzzzz", commands, 2));
        assertNull(Suggestions.closest("anything", List.of(), 2));
    }

    @Test
    void equallyCloseCandidatesResolveToTheFirstOne() {
        assertEquals("aab", Suggestions.closest("aaa", List.of("aab", "aac"), 1));
        assertEquals("aac", Suggestions.closest("aaa", List.of("aac", "aab"), 1));
    }
}
