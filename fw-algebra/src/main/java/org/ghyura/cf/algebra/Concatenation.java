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
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Several sequences one after the other: how the legacy code produced subsets of chosen sizes (the combinations of
 * each matching size, smallest first) and the all/full argument (the verb for every size from 1 to n). A tuple
 * that occurs in several parts has the rank of its first occurrence.
 */
public final class Concatenation implements RankedTuples {

    private final List<RankedTuples> parts;
    private final BigInteger[] starts;
    private final BigInteger size;

    public Concatenation(List<? extends RankedTuples> parts) {
        this.parts = List.copyOf(parts);
        this.starts = new BigInteger[this.parts.size()];
        BigInteger total = BigInteger.ZERO;
        for (int i = 0; i < this.parts.size(); i++) {
            starts[i] = total;
            total = total.add(this.parts.get(i).size());
        }
        this.size = total;
    }

    public List<RankedTuples> parts() {
        return parts;
    }

    @Override
    public BigInteger size() {
        return size;
    }

    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
        for (RankedTuples part : parts) {
            part.lengths().forEach((length, count) -> lengths.merge(length, count, BigInteger::add));
        }
        return lengths;
    }

    @Override
    public String formula() {
        return parts.isEmpty() ? "0" : String.join(" + ", parts.stream().map(RankedTuples::formula).toList());
    }

    private int partOf(BigInteger rank) {
        int low = 0;
        int high = parts.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (starts[middle].compareTo(rank) <= 0) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        // skip empty parts that start at the same rank
        while (parts.get(low).size().signum() == 0 || rank.subtract(starts[low]).compareTo(parts.get(low).size()) >= 0) {
            low++;
        }
        return low;
    }

    @Override
    public int[] get(BigInteger rank) {
        RankedTuples.checkRank(rank, size);
        int part = partOf(rank);
        return parts.get(part).get(rank.subtract(starts[part]));
    }

    @Override
    public BigInteger rankOf(int[] tuple) {
        for (int i = 0; i < parts.size(); i++) {
            try {
                return starts[i].add(parts.get(i).rankOf(tuple));
            } catch (IllegalArgumentException notInThisPart) {
                // try the next part
            }
        }
        throw RankedTuples.notMember(tuple, "no part holds it");
    }

    @Override
    public int[] successor(int[] tuple, BigInteger rank) {
        int part = partOf(rank);
        int[] next = parts.get(part).successor(tuple, rank.subtract(starts[part]));
        if (next != null) {
            return next;
        }
        BigInteger following = rank.add(BigInteger.ONE);
        return following.compareTo(size) < 0 ? get(following) : null;
    }
}
