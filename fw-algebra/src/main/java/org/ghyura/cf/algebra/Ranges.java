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

package org.ghyura.cf.algebra;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic work splitting (audit CB01): a rank range is cut into contiguous half-open parts, the first
 * {@code total mod parts} parts one rank longer, never more parts than ranks. The same input always gives the
 * same parts, so a split run produces exactly the tuples of an unsplit one, in the same order part by part.
 */
public final class Ranges {

    private Ranges() {
    }

    /** One half-open part {@code [from, to)}. */
    public record Range(BigInteger from, BigInteger to) {
        public Range {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (from.signum() < 0 || from.compareTo(to) > 0) {
                throw new IllegalArgumentException("not a range: [" + from + ", " + to + ")");
            }
        }

        public BigInteger length() {
            return to.subtract(from);
        }
    }

    /**
     * @param total how many ranks there are
     * @param parts how many parts are wanted, at least 1
     * @return at most {@code parts} non-empty ranges that cover {@code [0, total)} in order; none when total is 0
     */
    public static List<Range> split(BigInteger total, int parts) {
        Objects.requireNonNull(total, "total");
        if (parts < 1) {
            throw new IllegalArgumentException("parts must be >= 1: " + parts);
        }
        if (total.signum() < 0) {
            throw new IllegalArgumentException("a negative total: " + total);
        }
        if (total.signum() == 0) {
            return List.of();
        }
        int count = total.min(BigInteger.valueOf(parts)).intValueExact();
        BigInteger[] qr = total.divideAndRemainder(BigInteger.valueOf(count));
        int longer = qr[1].intValueExact();
        List<Range> ranges = new ArrayList<>(count);
        BigInteger start = BigInteger.ZERO;
        for (int i = 0; i < count; i++) {
            BigInteger end = start.add(qr[0]).add(i < longer ? BigInteger.ONE : BigInteger.ZERO);
            ranges.add(new Range(start, end));
            start = end;
        }
        return List.copyOf(ranges);
    }
}
