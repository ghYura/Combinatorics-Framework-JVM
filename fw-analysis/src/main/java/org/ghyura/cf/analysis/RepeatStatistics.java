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

package org.ghyura.cf.analysis;

import java.util.Arrays;

/**
 * Statistics of repeated samples (legacy RepeatAggregator): the median and an exact distribution-free interval for it from
 * order statistics (binomial(n, 1/2) tails at most alpha/2 each). One sample is a point, never an interval; above 1024
 * samples the exact computation is not attempted and the range is reported as not valid.
 */
public final class RepeatStatistics {

    public static final double DEFAULT_ALPHA = 0.05;
    public static final int MAX_EXACT_N = 1024;

    /**
     * @param median   the median
     * @param low      the interval's lower bound
     * @param high     its upper bound
     * @param valid    whether the bounds are a confidence interval
     * @param n        the samples
     * @param method   order_statistic, point_k1, range_insufficient_n or exceeds_exact_bound
     * @param coverage the interval's exact coverage, NaN when not valid
     */
    public record Interval(double median, double low, double high, boolean valid, int n, String method, double coverage) {
    }

    private RepeatStatistics() {
    }

    public static double median(double[] sorted) {
        if (sorted.length == 0) {
            throw new IllegalArgumentException("a median needs a value");
        }
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    public static Interval interval(double[] values, double alpha) {
        if (!Double.isFinite(alpha) || alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException("alpha is finite and in (0, 1): " + alpha);
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("an interval needs a value");
        }
        double[] sorted = values.clone();
        for (double v : sorted) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("an interval needs finite values");
            }
        }
        Arrays.sort(sorted);
        int n = sorted.length;
        double med = median(sorted);
        if (n == 1) {
            return new Interval(med, sorted[0], sorted[0], false, 1, "point_k1", Double.NaN);
        }
        if (n > MAX_EXACT_N) {
            return new Interval(med, sorted[0], sorted[n - 1], false, n, "exceeds_exact_bound", Double.NaN);
        }
        double half = alpha / 2.0;
        double term = Math.pow(0.5, n);
        double cumulative = 0;
        int lower = 0;
        for (int i = 0; i < n; i++) {
            if (cumulative + term > half) {
                break;
            }
            cumulative += term;
            lower = i + 1;
            term = term * (n - i) / (i + 1);
        }
        if (lower == 0) {
            return new Interval(med, sorted[0], sorted[n - 1], false, n, "range_insufficient_n", Double.NaN);
        }
        int upper = n + 1 - lower;
        return new Interval(med, sorted[lower - 1], sorted[upper - 1], true, n, "order_statistic", 1.0 - 2.0 * cumulative);
    }

    /** Whether two candidates' valid intervals overlap on every goal (legacy noiseTied). */
    public static boolean tied(Interval[] a, Interval[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null || b[i] == null || !a[i].valid() || !b[i].valid()
                    || Math.max(a[i].low(), b[i].low()) > Math.min(a[i].high(), b[i].high())) {
                return false;
            }
        }
        return a.length > 0;
    }
}
