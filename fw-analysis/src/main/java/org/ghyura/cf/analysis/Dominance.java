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

import java.util.ArrayList;
import java.util.List;

/**
 * Pareto dominance over goal deviations (legacy Dominance): a dominates b when it is no worse on every comparable axis and
 * strictly better on one; an axis is skipped for the pair when its goal is null or either value is NaN (a missing metric).
 */
public final class Dominance {

    private Dominance() {
    }

    public static boolean dominates(double[] a, double[] b, Goal[] goals) {
        boolean strict = false;
        int len = Math.min(Math.min(a.length, b.length), goals.length);
        for (int i = 0; i < len; i++) {
            Goal g = goals[i];
            if (g == null || Double.isNaN(a[i]) || Double.isNaN(b[i])) {
                continue;
            }
            double diff = g.deviation(a[i]) - g.deviation(b[i]);
            if (diff > 0) {
                return false;
            }
            if (diff < 0) {
                strict = true;
            }
        }
        return strict;
    }

    /**
     * Indexes of the vectors no other vector dominates, in input order. Every pair is compared: the legacy batch front
     * skipped points already found dominated, which is the same front only while dominance is transitive, and a skipped
     * missing value makes it not transitive (delta D43).
     */
    public static List<Integer> front(double[][] vectors, Goal[] goals) {
        int n = vectors.length;
        boolean[] dominated = new boolean[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && dominates(vectors[j], vectors[i], goals)) {
                    dominated[i] = true;
                    break;
                }
            }
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!dominated[i]) {
                out.add(i);
            }
        }
        return out;
    }
}
