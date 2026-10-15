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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exact allocation from observed values. The caller supplies the framework's six-player permutation cursor. */
public final class CoalitionAllocation {
    public record Fraction(BigInteger numerator, BigInteger denominator) {
        public Fraction {
            if (denominator.signum() == 0) throw new IllegalArgumentException("zero denominator");
            if (denominator.signum() < 0) { numerator = numerator.negate(); denominator = denominator.negate(); }
            BigInteger gcd = numerator.gcd(denominator); numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
        }
        public static Fraction of(long n, long d) { return new Fraction(BigInteger.valueOf(n), BigInteger.valueOf(d)); }
        public Fraction add(Fraction other) { return new Fraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)), denominator.multiply(other.denominator)); }
        public Fraction multiply(BigInteger n) { return new Fraction(numerator.multiply(n), denominator); }
        @Override public String toString() { return numerator + "/" + denominator; }
    }
    public record Observed(String caseId, String mask, String verdict, BigInteger value) { }
    public record Marginal(String player, String coalition, Fraction weight, BigInteger marginal) { }
    public record Permutation(String order, List<BigInteger> marginals) {
        public Permutation { marginals = List.copyOf(marginals); }
    }
    public record Allocation(Map<String, Fraction> shapley, List<Marginal> marginals, List<Permutation> permutations,
                             Map<String, BigInteger> permutationSums, Map<String, BigInteger> dividends, Map<String, Fraction> dividendSplit,
                             Map<String, BigInteger> standalone, Map<String, BigInteger> leaveOneOut, Map<String, Fraction> uniformSubsetMarginals,
                             BigInteger grandValue, Map<String, Boolean> checks) {
        public Allocation {
            shapley = frozen(shapley); marginals = List.copyOf(marginals); permutations = List.copyOf(permutations);
            permutationSums = frozen(permutationSums); dividends = frozen(dividends); dividendSplit = frozen(dividendSplit);
            standalone = frozen(standalone); leaveOneOut = frozen(leaveOneOut); uniformSubsetMarginals = frozen(uniformSubsetMarginals); checks = frozen(checks);
        }
    }
    private CoalitionAllocation() { }
    private static <V> Map<String, V> frozen(Map<String, V> m) { return Collections.unmodifiableMap(new TreeMap<>(m)); }
    private static String mask(int n) { return String.format(java.util.Locale.ROOT, "%6s", Integer.toBinaryString(n)).replace(' ', '0'); }
    private static int bit(int player) { return 1 << (5 - player); }
    private static final Fraction ZERO = Fraction.of(0, 1), ONE = Fraction.of(1, 1);

    /** Rejects incomplete/conflicting observations; never fills a value from the SUT, oracle or expected-data table. */
    public static Allocation allocate(List<Observed> observations, Iterator<int[]> playerOrders) {
        if (observations.size() > 64) throw new IllegalArgumentException("extra or duplicate coalition observations");
        BigInteger[] values = new BigInteger[64];
        for (Observed o : observations) {
            if (o.mask() == null || !o.mask().matches("[01]{6}") || !("C=" + o.mask()).equals(o.caseId())) throw new IllegalArgumentException("unknown or mismatched coalition identity");
            if (!"PASS".equals(o.verdict())) throw new IllegalArgumentException("non-PASS coalition observation");
            int n = Integer.parseInt(o.mask(), 2);
            if (values[n] != null) throw new IllegalArgumentException("duplicate coalition observation");
            values[n] = java.util.Objects.requireNonNull(o.value(), "observed coalition value");
        }
        for (int n = 0; n < 64; n++) if (values[n] == null) throw new IllegalArgumentException("missing coalition " + mask(n));
        int[] factorial = {1, 1, 2, 6, 24, 120, 720};
        List<Marginal> marginals = new ArrayList<>(); Map<String, Fraction> phi = new TreeMap<>(), weights = new TreeMap<>(), uniform = new TreeMap<>();
        Map<String, BigInteger> sums = new TreeMap<>(), standalone = new TreeMap<>(), leaveOneOut = new TreeMap<>();
        for (int p = 0; p < 6; p++) {
            String player = CoalitionValues.PLAYERS.get(p); Fraction allocation = ZERO, weightSum = ZERO; BigInteger unweighted = BigInteger.ZERO;
            for (int n = 0; n < 64; n++) if ((n & bit(p)) == 0) {
                int k = Integer.bitCount(n); Fraction w = Fraction.of((long) factorial[k] * factorial[5 - k], 720);
                BigInteger m = values[n | bit(p)].subtract(values[n]); marginals.add(new Marginal(player, mask(n), w, m));
                allocation = allocation.add(w.multiply(m)); weightSum = weightSum.add(w); unweighted = unweighted.add(m);
            }
            phi.put(player, allocation); weights.put(player, weightSum); uniform.put(player, new Fraction(unweighted, BigInteger.valueOf(32)));
            sums.put(player, BigInteger.ZERO); standalone.put(player, values[bit(p)]); leaveOneOut.put(player, values[63].subtract(values[63 ^ bit(p)]));
        }
        List<Permutation> permutations = new ArrayList<>(); var seen = new HashSet<String>();
        while (playerOrders.hasNext()) {
            int[] order = playerOrders.next(); if (order.length != 6) throw new IllegalArgumentException("six-player order required");
            int membership = 0; StringBuilder written = new StringBuilder(); List<BigInteger> inc = new ArrayList<>();
            for (int p : order) {
                if (p < 0 || p > 5 || (membership & bit(p)) != 0) throw new IllegalArgumentException("invalid player permutation");
                BigInteger m = values[membership | bit(p)].subtract(values[membership]); membership |= bit(p);
                String player = CoalitionValues.PLAYERS.get(p); written.append(player); inc.add(m); sums.put(player, sums.get(player).add(m));
            }
            if (!seen.add(written.toString()) || seen.size() > 720) throw new IllegalArgumentException("duplicate player permutation");
            permutations.add(new Permutation(written.toString(), inc));
        }
        if (seen.size() != 720) throw new IllegalArgumentException("all 720 player permutations required");
        // Proper-subset subtraction is the source's dividend route, separate from weighted/permutation marginals.
        BigInteger[] d = new BigInteger[64];
        for (int size = 0; size <= 6; size++) for (int n = 0; n < 64; n++) if (Integer.bitCount(n) == size) {
            BigInteger proper = BigInteger.ZERO;
            for (int k = 0; k < 64; k++) if (k != n && (k & n) == k) proper = proper.add(d[k]);
            d[n] = values[n].subtract(proper);
        }
        Map<String, BigInteger> dividends = new TreeMap<>(); for (int n = 0; n < 64; n++) dividends.put(mask(n), d[n]);
        Map<String, Fraction> split = new TreeMap<>(), permutationMeans = new TreeMap<>();
        for (int p = 0; p < 6; p++) {
            Fraction share = ZERO; for (int n = 1; n < 64; n++) if ((n & bit(p)) != 0) share = share.add(new Fraction(d[n], BigInteger.valueOf(Integer.bitCount(n))));
            String player = CoalitionValues.PLAYERS.get(p); split.put(player, share); permutationMeans.put(player, new Fraction(sums.get(player), BigInteger.valueOf(720)));
        }
        boolean symmetryAB = true, symmetryCD = true, constantE = true, nullF = true;
        for (int n = 0; n < 64; n++) {
            if ((n & (bit(0) | bit(1))) == 0) symmetryAB &= values[n | bit(0)].equals(values[n | bit(1)]);
            if ((n & (bit(2) | bit(3))) == 0) symmetryCD &= values[n | bit(2)].equals(values[n | bit(3)]);
            if ((n & bit(4)) == 0) constantE &= values[n | bit(4)].subtract(values[n]).equals(BigInteger.valueOf(4));
            if ((n & bit(5)) == 0) nullF &= values[n | bit(5)].equals(values[n]);
        }
        Map<String, Boolean> checks = Map.of("weights_sum_to_one", weights.values().stream().allMatch(ONE::equals),
                "permutations_telescope", permutations.stream().allMatch(o -> o.marginals().stream().reduce(BigInteger.ZERO, BigInteger::add).equals(values[63])),
                "three_certificates_agree", phi.equals(split) && phi.equals(permutationMeans),
                "efficiency", phi.values().stream().reduce(ZERO, Fraction::add).equals(new Fraction(values[63], BigInteger.ONE)),
                "symmetry_AB", symmetryAB, "symmetry_CD", symmetryCD, "E_constant_4", constantE, "F_null", nullF);
        return new Allocation(phi, marginals, permutations, sums, dividends, split, standalone, leaveOneOut, uniform, values[63], checks);
    }
}
