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

import org.ghyura.cf.algebra.Concatenation;
import org.ghyura.cf.algebra.FixedTuples;
import org.ghyura.cf.algebra.LegacyLibrary;
import org.ghyura.cf.algebra.MultisetPermutations;
import org.ghyura.cf.algebra.Operators;
import org.ghyura.cf.algebra.RankedTuples;
import org.ghyura.cf.plan.Directive;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What an ungrouped later pass makes of one row of the previous table (SheetWorker.runSubsequentPass and
 * consumeSubsequentPassChunk at the pinned commit): the verb applied to the row's labels once per size of
 * resolveM (size: the row length; all: 1 to the row length; otherwise max(1, m)), FW_Permut ignoring its size,
 * FW_Subsets always taking every subset, FW_Cartes pairing the row with the other sheet's original values; the
 * separator between the elements of every result, where an empty result is no row. An empty source row makes nothing.
 */
final class RowExpansion {

    private final Directive directive;
    private final LegacyLibrary library;
    private final int[] other;
    private final Integer separator;

    RowExpansion(Directive directive, LegacyLibrary library, int[] other, Integer separator) {
        this.directive = directive;
        this.library = library;
        this.other = other == null ? new int[0] : other.clone();
        this.separator = separator;
    }

    /** The tuples of one source row and how many leading ones are skipped (the empty subset, when separated). */
    record Plan(RankedTuples tuples, BigInteger skip) {
        BigInteger count() {
            return tuples.size().subtract(skip).max(BigInteger.ZERO);
        }
    }

    Plan plan(int[] in) {
        if (in.length == 0) {
            return new Plan(FixedTuples.NONE, BigInteger.ZERO);
        }
        RankedTuples tuples = switch (directive) {
            case Directive.Combinatoric c -> {
                if (c.verb() == Directive.Verb.PERMUT) {
                    yield Operators.permutations(MultisetPermutations.classesOf(Arrays.stream(in).boxed().toList()), library);
                }
                List<RankedTuples> parts = new ArrayList<>();
                for (int m : sizes(c, in.length)) {
                    parts.add(SheetStages.verb(c.verb(), in.length, m, library));
                }
                yield parts.size() == 1 ? parts.getFirst() : new Concatenation(parts);
            }
            case Directive.Subsets s -> Operators.subsets(in.length);
            case Directive.Cartes c -> c.first() ? Operators.cartesian(other.length, in.length) : Operators.cartesian(in.length, other.length);
            default -> throw new IllegalStateException("not a combination directive: " + directive);
        };
        // only the empty subset (rank 0 in binary order) and the guard's one empty row are empty tuples; with a
        // separator an empty tuple is no row
        boolean skipEmpty = separator != null && tuples.size().signum() > 0
                && (directive instanceof Directive.Subsets || tuples == FixedTuples.ONE_EMPTY);
        return new Plan(tuples, skipEmpty ? BigInteger.ONE : BigInteger.ZERO);
    }

    /** The labels of the result with tuple {@code tuple} for source row {@code in}, separated when a separator is set. */
    int[] labels(int[] in, int[] tuple) {
        int[] labels = new int[tuple.length];
        boolean cartesFirst = directive instanceof Directive.Cartes c && c.first();
        for (int i = 0; i < tuple.length; i++) {
            if (directive instanceof Directive.Cartes) {
                boolean fromOther = cartesFirst == (i == 0);
                labels[i] = fromOther ? other[tuple[i]] : in[tuple[i]];
            } else {
                labels[i] = in[tuple[i]];
            }
        }
        if (separator == null || labels.length < 2) {
            return labels;
        }
        int[] out = new int[labels.length * 2 - 1];
        for (int i = 0; i < labels.length; i++) {
            out[i * 2] = labels[i];
            if (i < labels.length - 1) {
                out[i * 2 + 1] = separator;
            }
        }
        return out;
    }

    /**
     * How many results any source row of {@code length} labels has, or null when it depends on the labels themselves
     * (combinatoricslib3 permuted a row with a repeated label as a multiset).
     */
    BigInteger countForLength(int length) {
        if (length == 0) {
            return BigInteger.ZERO;
        }
        if (directive instanceof Directive.Combinatoric c && c.verb() == Directive.Verb.PERMUT && library == LegacyLibrary.COMBINATORICSLIB3) {
            return null;
        }
        int[] distinct = new int[length];
        for (int i = 0; i < length; i++) {
            distinct[i] = i;
        }
        return plan(distinct).count();
    }

    /**
     * How many results of each number of keys any source row of {@code length} labels has, or null when the count
     * depends on the labels themselves; computed from the operator's parameters, not by enumerating results.
     */
    SortedMap<Integer, BigInteger> lengthsForLength(int length) {
        SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
        if (length == 0) {
            return lengths;
        }
        if (countForLength(length) == null) {
            return null;
        }
        int[] distinct = new int[length];
        for (int i = 0; i < length; i++) {
            distinct[i] = i;
        }
        Plan plan = plan(distinct);
        SortedMap<Integer, BigInteger> tuples = new TreeMap<>(plan.tuples().lengths());
        if (plan.skip().signum() > 0) {
            // the skipped leading tuple is the empty one
            tuples.computeIfPresent(0, (k, v) -> v.subtract(plan.skip()).signum() > 0 ? v.subtract(plan.skip()) : null);
        }
        tuples.forEach((keys, count) -> lengths.merge(separator == null || keys < 2 ? keys : keys * 2 - 1, count, BigInteger::add));
        return lengths;
    }

    /** The operator of one source row as a formula over its length {@code L}, for example {@code C(L,2)}. */
    String formulaForLength(int length) {
        if (length == 0 || countForLength(length) == null) {
            return directive.normalized() + " of the row";
        }
        int[] distinct = new int[length];
        for (int i = 0; i < length; i++) {
            distinct[i] = i;
        }
        Plan plan = plan(distinct);
        return plan.tuples().formula() + (plan.skip().signum() > 0 ? " - " + plan.skip() : "");
    }

    /** The directive, normalized. */
    String describe() {
        return directive.normalized();
    }

    /** The number of keys of every result of a source row of {@code length} labels, or -1 when they differ. */
    int outputLength(int length) {
        int keys = switch (directive) {
            case Directive.Combinatoric c -> switch (c.verb()) {
                case PERMUT -> length;
                default -> {
                    List<Integer> sizes = sizes(c, length);
                    yield sizes.size() != 1 ? -1 : c.verb() == Directive.Verb.COMBI && sizes.getFirst() > length ? 0 : sizes.getFirst();
                }
            };
            case Directive.Cartes c -> 2;
            default -> -1;
        };
        if (keys < 0 || separator == null || keys < 2) {
            return keys;
        }
        return keys * 2 - 1;
    }

    /** SheetWorker.resolveM and the m loop of a later pass. */
    static List<Integer> sizes(Directive.Combinatoric c, int length) {
        return switch (c.count().kind()) {
            case SIZE -> List.of(length);
            case ALL -> {
                List<Integer> all = new ArrayList<>();
                for (int m = 1; m <= length; m++) {
                    all.add(m);
                }
                yield all;
            }
            case EXACT -> List.of(Math.max(1, c.count().value()));
            case DEFAULT -> List.of(1);
        };
    }
}
