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
import java.util.Arrays;
import java.util.SortedMap;

/**
 * The distinct arrangements of a multiset, in lexicographic order of its classes (the parallel library's
 * MultiPermutationGenerator, and combinatoricslib3 3.3.3 simple() when the input repeats an element). The input
 * is described by the class of each position, classes numbered by first occurrence; each tuple gives, per
 * place, the first position of the class placed there. For classes (0,1,1,2), that is b,a,a,c, the order starts
 * (0,1,1,3), (0,1,3,1), (0,3,1,1), (1,0,1,3). Multinomial-many tuples; one empty tuple for no positions.
 */
public final class MultisetPermutations implements RankedTuples {

    private final int n;
    private final int[] counts;
    private final int[] representative;
    private final int[] classOfPosition;
    private final BigInteger size;

    /** @param classOfPosition the class of each position, numbered 0, 1, 2, ... in order of first occurrence */
    public MultisetPermutations(int[] classOfPosition) {
        this.classOfPosition = classOfPosition.clone();
        this.n = classOfPosition.length;
        int classes = 0;
        for (int i = 0; i < n; i++) {
            int c = classOfPosition[i];
            if (c < 0 || c > classes) {
                throw new IllegalArgumentException("classes must be numbered by first occurrence: " + Arrays.toString(classOfPosition));
            }
            if (c == classes) {
                classes++;
            }
        }
        this.counts = new int[classes];
        this.representative = new int[classes];
        Arrays.fill(representative, -1);
        for (int i = 0; i < n; i++) {
            int c = classOfPosition[i];
            counts[c]++;
            if (representative[c] < 0) {
                representative[c] = i;
            }
        }
        this.size = Counting.multinomial(counts);
    }

    /** Numbers equal elements by first occurrence: the classes of a list as {@link #MultisetPermutations} expects them. */
    public static int[] classesOf(java.util.List<?> elements) {
        java.util.List<Object> seen = new java.util.ArrayList<>();
        int[] classes = new int[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            int found = -1;
            for (int j = 0; j < seen.size(); j++) {
                if (java.util.Objects.equals(seen.get(j), elements.get(i))) {
                    found = j;
                    break;
                }
            }
            if (found < 0) {
                found = seen.size();
                seen.add(elements.get(i));
            }
            classes[i] = found;
        }
        return classes;
    }

    @Override
    public BigInteger size() {
        return size;
    }

    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        return RankedTuples.lengthOf(n, size);
    }

    @Override
    public String formula() {
        StringBuilder repeated = new StringBuilder();
        for (int c : counts) {
            if (c > 1) {
                repeated.append(repeated.isEmpty() ? "" : "·").append(c).append('!');
            }
        }
        return repeated.isEmpty() ? n + "!" : n + "!/(" + repeated + ")";
    }

    @Override
    public int[] get(BigInteger rank) {
        RankedTuples.checkRank(rank, size);
        int[] left = counts.clone();
        int[] tuple = new int[n];
        BigInteger remaining = rank;
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < left.length; c++) {
                if (left[c] == 0) {
                    continue;
                }
                left[c]--;
                BigInteger block = Counting.multinomial(left);
                if (remaining.compareTo(block) < 0) {
                    tuple[i] = representative[c];
                    break;
                }
                remaining = remaining.subtract(block);
                left[c]++;
            }
        }
        return tuple;
    }

    @Override
    public BigInteger rankOf(int[] tuple) {
        int[] classes = classesOfTuple(tuple);
        int[] left = counts.clone();
        BigInteger rank = BigInteger.ZERO;
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < classes[i]; c++) {
                if (left[c] == 0) {
                    continue;
                }
                left[c]--;
                rank = rank.add(Counting.multinomial(left));
                left[c]++;
            }
            left[classes[i]]--;
        }
        return rank;
    }

    @Override
    public int[] successor(int[] tuple, BigInteger rank) {
        int[] classes = classesOfTuple(tuple);
        int i = n - 2;
        while (i >= 0 && classes[i] >= classes[i + 1]) {
            i--;
        }
        if (i < 0) {
            return null;
        }
        int j = n - 1;
        while (classes[j] <= classes[i]) {
            j--;
        }
        LexicographicPermutations.swap(classes, i, j);
        for (int a = i + 1, b = n - 1; a < b; a++, b--) {
            LexicographicPermutations.swap(classes, a, b);
        }
        int[] next = new int[n];
        for (int p = 0; p < n; p++) {
            next[p] = representative[classes[p]];
        }
        return next;
    }

    private int[] classesOfTuple(int[] tuple) {
        if (tuple.length != n) {
            throw RankedTuples.notMember(tuple, "an arrangement has " + n + " places");
        }
        int[] used = new int[counts.length];
        int[] classes = new int[n];
        for (int i = 0; i < n; i++) {
            int p = tuple[i];
            if (p < 0 || p >= n || representative[classOfPosition[p]] != p) {
                throw RankedTuples.notMember(tuple, "each place holds the first position of a class");
            }
            classes[i] = classOfPosition[p];
            if (++used[classes[i]] > counts[classes[i]]) {
                throw RankedTuples.notMember(tuple, "a class is used more often than it occurs");
            }
        }
        return classes;
    }
}
