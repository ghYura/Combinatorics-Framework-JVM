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

/**
 * Streaming statistics of one metric key (legacy OnlineMetricAggregator.KeyStats): Welford count, mean and M2 (population
 * standard deviation sqrt(M2/n)), minimum and maximum with the first sample that reached them. Two partial statistics merge
 * exactly (Chan's parallel formula), so the result does not depend on how samples were split between workers.
 */
public final class KeyStats {

    private final String key;
    private long n;
    private double mean;
    private double m2;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;
    private String argmin = "";
    private String argmax = "";

    public KeyStats(String key) {
        this.key = key;
    }

    /** Adds one finite value of sample {@code id}. */
    public void add(double v, String id) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException("metric " + key + ": a nonfinite value is never a statistic: " + v);
        }
        n++;
        double d = v - mean;
        mean += d / n;
        m2 += d * (v - mean);
        if (v < min) {
            min = v;
            argmin = id;
        }
        if (v > max) {
            max = v;
            argmax = id;
        }
    }

    /** Merges another partial statistic of the same key into this one; the earlier extreme wins a tie. */
    public void merge(KeyStats o) {
        if (o.n == 0) {
            return;
        }
        if (n == 0) {
            n = o.n;
            mean = o.mean;
            m2 = o.m2;
            min = o.min;
            max = o.max;
            argmin = o.argmin;
            argmax = o.argmax;
            return;
        }
        long total = n + o.n;
        double delta = o.mean - mean;
        m2 = m2 + o.m2 + delta * delta * ((double) n * o.n) / total;
        mean = (mean * n + o.mean * o.n) / total;
        n = total;
        if (o.min < min) {
            min = o.min;
            argmin = o.argmin;
        }
        if (o.max > max) {
            max = o.max;
            argmax = o.argmax;
        }
    }

    public String key() {
        return key;
    }

    public long count() {
        return n;
    }

    public double mean() {
        return n == 0 ? Double.NaN : mean;
    }

    /** Population standard deviation, 0 below two samples (as the legacy). */
    public double stdev() {
        return n < 2 ? 0 : Math.sqrt(m2 / n);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public String argmin() {
        return argmin;
    }

    public String argmax() {
        return argmax;
    }
}
