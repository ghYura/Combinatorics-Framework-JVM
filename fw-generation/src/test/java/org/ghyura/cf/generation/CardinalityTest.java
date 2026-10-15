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

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GEN:cardinality-resource-planning: a count keeps how sure it is through every step, and an unknown never becomes zero. */
class CardinalityTest {

    private static BigInteger n(long v) {
        return BigInteger.valueOf(v);
    }

    @Test
    void confidenceSurvivesScalingCapsAndEarlierEnds() {
        Cardinality exact = Cardinality.exact(n(24), "C(4,2) × 4");
        assertEquals("24 (EXACT: C(4,2) × 4)", exact.describe());
        Cardinality tripled = exact.times(n(3), "× 3");
        assertEquals(Cardinality.Mode.EXACT, tripled.mode());
        assertEquals(n(72), tripled.value());

        Cardinality range = Cardinality.bounded(n(0), n(30), "[0, generated]", "rules not counted");
        assertEquals("[0, 30] (BOUNDED: [0, generated])", range.describe());
        Cardinality scaled = range.times(n(2), "× 2");
        assertEquals(Cardinality.Mode.BOUNDED, scaled.mode(), "a range stays a range");
        assertEquals(n(60), scaled.upper());
        assertEquals(scaled.reasons(), range.reasons(), "the reasons travel with it");

        Cardinality unknown = Cardinality.unknown("not assembled", "covering selection refused");
        Cardinality stillUnknown = unknown.times(n(5), "× 5");
        assertEquals(Cardinality.Mode.UNKNOWN, stillUnknown.mode(), "an unknown count never becomes a number");
        assertNull(stillUnknown.value());
        assertEquals("0 (EXACT: × 0)", unknown.times(BigInteger.ZERO, "× 0").describe(), "an exact factor of 0 proves 0");

        Cardinality capped = unknown.atMost(n(100), "min(executable, 100)", "cf.run.stop.candidates");
        assertEquals("[0, 100] (BOUNDED: min(executable, 100))", capped.describe(), "a cap bounds even an unknown count");
        assertEquals(n(10), Cardinality.exact(n(10), "x").atMost(n(100), "min", "cap").value());
        Cardinality earlier = Cardinality.exact(n(10), "x").orFewer("x or fewer", "cf.run.stop.errors");
        assertEquals("[0, 10] (BOUNDED: x or fewer)", earlier.describe(), "a stop policy turns an exact count into a range");
        assertEquals(Cardinality.Mode.UNKNOWN, unknown.orFewer("u", "r").mode());

        Cardinality guess = Cardinality.estimated(n(3456), n(3456), n(25_000_000), "attempts × bytes", java.util.List.of(),
                java.util.List.of("no output"));
        assertEquals("~3456 in [3456, 25000000] (ESTIMATED: attempts × bytes)", guess.describe());
        assertFalse(guess.reasons().contains("no output"));
    }

    @Test
    void anEstimateIsWellFormedAndWritesItsReasonsAndAssumptions() {
        assertThrows(IllegalArgumentException.class, () -> new Cardinality(Cardinality.Mode.UNKNOWN, n(1), null, null, "", java.util.List.of(),
                java.util.List.of()), "an unknown count has no figure");
        assertThrows(IllegalArgumentException.class, () -> Cardinality.bounded(n(5), n(4), "inverted"));
        StringBuilder xml = new StringBuilder();
        Cardinality.bounded(n(0), n(7), "a < b", "why").appendXml(xml, "", "executable");
        assertEquals("<estimate name=\"executable\" mode=\"BOUNDED\" value=\"7\" lower=\"0\" upper=\"7\" formula=\"a &lt; b\">\n"
                + "  <reason>why</reason>\n</estimate>\n", xml.toString());
        xml.setLength(0);
        Cardinality.unknown("u").appendXml(xml, "", "samples");
        assertTrue(xml.toString().startsWith("<estimate name=\"samples\" mode=\"UNKNOWN\" formula=\"u\"/>"), xml.toString());
    }
}
