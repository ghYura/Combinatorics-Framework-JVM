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

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/**
 * The combinatorial operators of the FW_Seq verbs, in the order and with the edge cases of the legacy library
 * that produced them ({@link LegacyLibrary}). Every result is a {@link RankedTuples} of positions into the source.
 *
 * <p>Edge cases that the two libraries answer differently, read from the libraries themselves:</p>
 * <ul>
 *   <li>combinations of an empty source with k=0: one empty tuple (parallel), none (combinatoricslib3);</li>
 *   <li>combinations with repetition of an empty source: none for any k (parallel), one empty tuple for any k
 *       (combinatoricslib3);</li>
 *   <li>permutations of an empty source: one empty tuple (parallel), none (combinatoricslib3);</li>
 *   <li>permutations with repetition of an empty source: one empty tuple for k=0 and none otherwise (parallel),
 *       none for any k (combinatoricslib3).</li>
 * </ul>
 * Combinations with k above the source size are empty here (approved in the supplement to revision 2); the
 * parallel library threw. The legacy Core never asked for them: SheetWorker.buildGenerator returned the subsets
 * of an empty list first, one empty row, which generation reproduces. A negative size stays an error.
 */
public final class Operators {

    private Operators() {
    }

    /** FW_Combi(k): k-combinations without repetition. */
    public static RankedTuples combinations(int n, int k, LegacyLibrary library) {
        if (n == 0 && library == LegacyLibrary.COMBINATORICSLIB3) {
            return FixedTuples.NONE;
        }
        return new Combinations(n, k);
    }

    /** FW_CombiR(k): k-combinations with repetition. */
    public static RankedTuples combinationsWithRepetition(int n, int k, LegacyLibrary library) {
        if (n == 0) {
            return library == LegacyLibrary.PARALLEL ? FixedTuples.NONE : FixedTuples.ONE_EMPTY;
        }
        return new MultiCombinations(n, k);
    }

    /**
     * FW_Permut: every permutation of the source, distinct by position. combinatoricslib3 3.3.3 used the
     * adjacent-swap order and, when the source repeats an element (by equality), only the distinct arrangements;
     * the parallel library used the lexicographic order of positions.
     *
     * @param classOfPosition the equality classes of the source (see {@link MultisetPermutations#classesOf}); only
     *                        combinatoricslib3 looks at them
     */
    public static RankedTuples permutations(int[] classOfPosition, LegacyLibrary library) {
        int n = classOfPosition.length;
        if (library == LegacyLibrary.PARALLEL) {
            return new LexicographicPermutations(n);
        }
        if (n == 0) {
            return FixedTuples.NONE;
        }
        for (int i = 0; i < n; i++) {
            if (classOfPosition[i] != i) {
                return new MultisetPermutations(classOfPosition);
            }
        }
        return new AdjacentSwapPermutations(n);
    }

    /** FW_Permut with PermutationGenerator.TreatDuplicatesAs.IDENTICAL: the distinct arrangements of the multiset. */
    public static RankedTuples distinctArrangements(int[] classOfPosition) {
        return new MultisetPermutations(classOfPosition);
    }

    /** FW_PermutR(k): k-tuples with repetition. */
    public static RankedTuples permutationsWithRepetition(int n, int k, LegacyLibrary library) {
        if (n == 0 && library == LegacyLibrary.COMBINATORICSLIB3) {
            return FixedTuples.NONE;
        }
        return new RepetitionPermutations(n, k, library == LegacyLibrary.PARALLEL
                ? RepetitionPermutations.Fastest.LAST : RepetitionPermutations.Fastest.FIRST);
    }

    /** FW_Subsets: all subsets, in binary order. */
    public static RankedTuples subsets(int n) {
        return new Subsets(n);
    }

    /**
     * FW_Subsets_MODE(sizes): the combinations of every selected size, smallest size first, as
     * CombinatorialGenerator.subsets built them from one combination generator per size.
     */
    public static RankedTuples subsetsOfSizes(int n, SortedSet<Integer> sizes, LegacyLibrary library) {
        List<RankedTuples> parts = new ArrayList<>();
        for (int size : sizes) {
            if (size >= 0 && size <= n) {
                parts.add(combinations(n, size, library));
            }
        }
        return new Concatenation(parts);
    }

    /** FW_Cartes: the product of the sources, the last varying fastest. */
    public static RankedTuples cartesian(int... sizes) {
        return new CartesianProduct(sizes);
    }
}
