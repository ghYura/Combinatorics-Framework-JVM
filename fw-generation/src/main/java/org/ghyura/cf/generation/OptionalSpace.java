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

import org.ghyura.cf.algebra.Operators;
import org.ghyura.cf.algebra.RankedTuples;
import org.ghyura.cf.algebra.TupleCursor;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The optional rows of one combination size k (the legacy fw_opt{k} table): every k-subset of the optional columns, in
 * the order of the combination generator the legacy Core used, and for each subset the product of its columns' rows
 * (the first column slowest). A subset with an empty column contributes nothing (runOptionalInsert skipped it); a
 * subset whose product exceeds the row limit is left out with a warning; a product above the cut-off keeps its first
 * rows. Subsets are held in memory, reserved first.
 */
final class OptionalSpace {

    /** One kept subset. */
    record Subset(int[] factors, BigInteger rows, BigInteger offset, BigInteger legacyOffset, BigInteger legacyRows) {
    }

    final int size;
    final List<Subset> subsets;
    final BigInteger rows;
    final long skippedOverLimit;

    private OptionalSpace(int size, List<Subset> subsets, BigInteger rows, long skippedOverLimit) {
        this.size = size;
        this.subsets = List.copyOf(subsets);
        this.rows = rows;
        this.skippedOverLimit = skippedOverLimit;
    }

    /** The bytes one held subset of k columns is charged. */
    static long subsetBytes(int k) {
        return 160 + 4L * k + 4 * 48;
    }

    static OptionalSpace of(int k, List<Factor> optional, AssemblyOptions options, MemoryBudget budget, Arena arena,
                            List<Diagnostic> diagnostics, Cancellation cancellation) {
        int n = optional.size();
        RankedTuples combinations = Operators.combinations(n, k, options.library());
        BigInteger count = combinations.size();
        if (count.bitLength() > 31) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, "the " + n + " optional sheets form " + count
                    + " combinations of " + k + ", more than the assembly can hold", SourceLocation.NONE));
        }
        arena.own(budget.reserve(64 + count.longValue() * subsetBytes(k), "the optional combinations of size " + k));
        List<Subset> kept = new ArrayList<>();
        BigInteger offset = BigInteger.ZERO;
        BigInteger legacyOffset = BigInteger.ZERO;
        long skipped = 0;
        try (TupleCursor cursor = combinations.cursor(BigInteger.ZERO, count, cancellation)) {
            while (cursor.hasNext()) {
                int[] subset = cursor.next();
                BigInteger product = BigInteger.ONE;
                BigInteger legacy = BigInteger.ONE;
                boolean empty = false;
                for (int f : subset) {
                    Factor factor = optional.get(f);
                    if (factor.size().signum() == 0) {
                        empty = true;
                        break;
                    }
                    product = product.multiply(factor.size());
                    legacy = legacy.multiply(factor.originalSize());
                }
                if (empty) {
                    continue;
                }
                if (legacy.compareTo(BigInteger.valueOf(options.subsetRowLimit())) > 0) {
                    skipped++;
                    diagnostics.add(Diagnostic.warning(ErrorCode.ASSEMBLY_SUBSET_SKIPPED, "the optional combination " + names(optional, subset)
                            + " has " + legacy + " rows, more than cf.optional.subsetRowLimit=" + options.subsetRowLimit()
                            + ", and is left out as the legacy Core left it out", optional.get(subset[0]).where,
                            "subset", names(optional, subset)));
                    continue;
                }
                BigInteger inserted = Assembly.legacySubsetRows(legacy, options.mandatoryLimit());
                List<Factor> factors = new ArrayList<>();
                for (int f : subset) {
                    factors.add(optional.get(f));
                }
                BigInteger limited = Assembly.keptBelow(factors, inserted);
                if (limited.compareTo(product) < 0) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.ASSEMBLY_TRUNCATED, "the optional combination " + names(optional, subset)
                            + " keeps its first " + limited + " of " + product + " rows (cf.assembly.mandatoryLimit="
                            + options.mandatoryLimit() + ")", optional.get(subset[0]).where, "subset", names(optional, subset)));
                }
                kept.add(new Subset(subset, limited, offset, legacyOffset, inserted));
                offset = offset.add(limited);
                legacyOffset = legacyOffset.add(inserted);
            }
        }
        return new OptionalSpace(k, kept, offset, skipped);
    }

    static String names(List<Factor> optional, int[] subset) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < subset.length; i++) {
            out.append(i == 0 ? "" : ", ").append(optional.get(subset[i]).sheet);
        }
        return out.append(']').toString();
    }

    /** The subset holding local row {@code index}. */
    int subsetAt(BigInteger index) {
        int low = 0;
        int high = subsets.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (subsets.get(middle).offset().compareTo(index) <= 0) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }
}
