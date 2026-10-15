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

import java.math.BigInteger;
import java.util.Objects;

/**
 * The counts of a candidate space, each reported separately (supplement, M03 priority 1): generated, removed by the
 * rules that read only mandatory sheets (each with every candidate of its mandatory row), left out by covering
 * selection, removed by the rules that read optional sheets, and executable. Per-rule counts count a candidate once per
 * rule it violates; {@code overlap} counts the candidates more than one rule removed. Without a scan only the generated
 * count is known.
 */
public record AssemblyCounts(boolean exact, BigInteger generated, BigInteger removedEarly, BigInteger unselected,
                             BigInteger removedLate, BigInteger executable, BigInteger[] perRule, BigInteger overlap) {

    public AssemblyCounts {
        Objects.requireNonNull(generated, "generated");
        perRule = perRule == null ? null : perRule.clone();
    }

    static AssemblyCounts exact(BigInteger generated, BigInteger early, BigInteger unselected, BigInteger late, BigInteger executable,
                                BigInteger[] perRule, BigInteger overlap) {
        return new AssemblyCounts(true, generated, early, unselected, late, executable, perRule, overlap);
    }

    static AssemblyCounts unknown(BigInteger generated) {
        return new AssemblyCounts(false, generated, null, null, null, null, null, null);
    }

    /** Candidates the rules removed, early and late. */
    public BigInteger excluded() {
        return exact ? removedEarly.add(removedLate) : null;
    }

    /** Candidates covering selection kept (before the late rules). */
    public BigInteger selected() {
        return exact ? generated.subtract(removedEarly).subtract(unselected) : null;
    }

    @Override
    public BigInteger[] perRule() {
        return perRule == null ? null : perRule.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AssemblyCounts c && exact == c.exact && generated.equals(c.generated) && Objects.equals(removedEarly, c.removedEarly)
                && Objects.equals(unselected, c.unselected) && Objects.equals(removedLate, c.removedLate)
                && Objects.equals(executable, c.executable) && java.util.Arrays.equals(perRule, c.perRule) && Objects.equals(overlap, c.overlap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exact, generated, removedEarly, unselected, removedLate, executable, java.util.Arrays.hashCode(perRule), overlap);
    }

    @Override
    public String toString() {
        return exact ? "generated " + generated + ", removed early " + removedEarly + ", unselected " + unselected + ", removed late "
                + removedLate + ", executable " + executable : "generated " + generated + " (other counts need a scan)";
    }
}
