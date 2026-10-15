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
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** A short explicit sequence of tuples: the empty sequence and the single empty tuple the legacy edge cases need. */
public final class FixedTuples implements RankedTuples {

    /** No tuple at all. */
    public static final FixedTuples NONE = new FixedTuples(List.of());
    /** Exactly one tuple, the empty one. */
    public static final FixedTuples ONE_EMPTY = new FixedTuples(List.of(new int[0]));

    private final List<int[]> tuples;

    private FixedTuples(List<int[]> tuples) {
        List<int[]> copy = new ArrayList<>();
        for (int[] t : tuples) {
            copy.add(t.clone());
        }
        this.tuples = List.copyOf(copy);
    }

    @Override
    public BigInteger size() {
        return BigInteger.valueOf(tuples.size());
    }

    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
        for (int[] tuple : tuples) {
            lengths.merge(tuple.length, BigInteger.ONE, BigInteger::add);
        }
        return lengths;
    }

    @Override
    public String formula() {
        return Integer.toString(tuples.size());
    }

    @Override
    public int[] get(BigInteger rank) {
        RankedTuples.checkRank(rank, size());
        return tuples.get(rank.intValueExact()).clone();
    }

    @Override
    public BigInteger rankOf(int[] tuple) {
        for (int i = 0; i < tuples.size(); i++) {
            if (Arrays.equals(tuples.get(i), tuple)) {
                return BigInteger.valueOf(i);
            }
        }
        throw RankedTuples.notMember(tuple, "it is not one of the " + tuples.size() + " fixed tuples");
    }
}
