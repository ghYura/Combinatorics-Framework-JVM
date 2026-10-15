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


package org.ghyura.cf.fixtures.demos;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D11's six-module SUT and separate multilinear indicator oracle. No frozen case result is a runtime input. */
public final class CoalitionValues {
    public static final List<String> PLAYERS = List.of("A", "B", "C", "D", "E", "F");
    public record Value(BigInteger standalone, Map<String, BigInteger> bonuses, BigInteger value) {
        public Value { bonuses = Collections.unmodifiableMap(new TreeMap<>(bonuses)); }
    }
    public record Result(String mask, List<String> members, Value observed, Value reference, boolean passed) {
        public Result { members = List.copyOf(members); }
    }
    private CoalitionValues() { }
    public static Result evaluate(List<String> members) {
        int previous = -1;
        for (String p : members) {
            int position = PLAYERS.indexOf(p);
            if (position <= previous || position < 0) throw new IllegalArgumentException("unknown, repeated or out-of-order player");
            previous = position;
        }
        var base = Map.of("A", 2, "B", 2, "C", 1, "D", 1, "E", 4, "F", 0);
        BigInteger standalone = members.stream().map(p -> BigInteger.valueOf(base.get(p))).reduce(BigInteger.ZERO, BigInteger::add);
        Map<String, BigInteger> bonuses = Map.of("AB", BigInteger.valueOf(members.containsAll(List.of("A", "B")) ? 12 : 0),
                "ACD", BigInteger.valueOf(members.containsAll(List.of("A", "C", "D")) ? 5 : 0),
                "BCD", BigInteger.valueOf(members.containsAll(List.of("B", "C", "D")) ? 5 : 0));
        Value observed = new Value(standalone, bonuses, standalone.add(bonuses.values().stream().reduce(BigInteger.ZERO, BigInteger::add)));
        StringBuilder mask = new StringBuilder(); for (String p : PLAYERS) mask.append(members.contains(p) ? '1' : '0');
        Value reference = reference(mask.toString());
        return new Result(mask.toString(), members, observed, reference, observed.equals(reference));
    }
    /** Indicator terms are intentionally separate from the SUT's named membership/bonus rules. */
    public static Value reference(String mask) {
        if (mask == null || !mask.matches("[01]{6}")) throw new IllegalArgumentException("six membership flags required");
        int[][] indices = {{0}, {1}, {2}, {3}, {4}, {5}, {0, 1}, {0, 2, 3}, {1, 2, 3}};
        int[] coefficients = {2, 2, 1, 1, 4, 0, 12, 5, 5}; String[] names = {"AB", "ACD", "BCD"};
        BigInteger standalone = BigInteger.ZERO; Map<String, BigInteger> bonuses = new TreeMap<>();
        for (int t = 0; t < indices.length; t++) {
            int product = 1; for (int i : indices[t]) product *= mask.charAt(i) - '0';
            BigInteger term = BigInteger.valueOf((long) coefficients[t] * product);
            if (indices[t].length == 1) standalone = standalone.add(term); else bonuses.put(names[t - 6], term);
        }
        return new Value(standalone, bonuses, standalone.add(bonuses.values().stream().reduce(BigInteger.ZERO, BigInteger::add)));
    }
}
