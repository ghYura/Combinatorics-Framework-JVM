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

import java.util.List;
import java.util.Locale;

/**
 * Which metric keys without a declared goal become goals, and how (legacy DiscoveryPolicy and AutoAnalysisPlanner's
 * lexical hints): in exploratory mode a discovered key gets the forced direction, or the one its name suggests
 * (minimising fragments win over maximising ones; minimise when none matches), with the given weight.
 *
 * @param include whether undeclared keys become goals at all
 * @param forced  the direction of every discovered goal, or null to infer it from the key
 * @param weight  their weight, finite and at least 0
 */
public record Discovery(boolean include, Goal.Mode forced, double weight) {

    /** Key fragments that imply minimising, checked first (legacy MIN_HINTS). */
    static final List<String> MIN_HINTS = List.of("cost", "latency", "delay", "wait", "queue", "miss", "drop", "error", "fail", "loss",
            "overhead", "lag", "duration", "time", "size", "bytes", "kb", "mb", "gb", "memory", "ram", "footprint", "regret", "penalty",
            "attempt", "retry");
    /** Key fragments that imply maximising (legacy MAX_HINTS). */
    static final List<String> MAX_HINTS = List.of("throughput", "qps", "rps", "tps", "ops", "bandwidth", "speed", "score", "quality",
            "accuracy", "precision", "recall", "f1", "uptime", "available", "success", "hit", "win", "yield", "coverage", "efficiency",
            "fitness", "reward", "utility");

    public static final Discovery DECLARED_ONLY = new Discovery(false, null, 0);

    public Discovery {
        if (!Double.isFinite(weight) || weight < 0) {
            throw new IllegalArgumentException("a discovery weight is finite and at least 0: " + weight);
        }
    }

    /** The goal a discovered key gets, or null when nothing is discovered. */
    public Goal goalFor(String key) {
        if (!include) {
            return null;
        }
        return new Goal(key, forced != null ? forced : inferMode(key), 0, weight);
    }

    /** The direction a key's name suggests. */
    public static Goal.Mode inferMode(String key) {
        String lc = key.toLowerCase(Locale.ROOT);
        for (String h : MIN_HINTS) {
            if (lc.contains(h)) {
                return Goal.Mode.MINIMIZE;
            }
        }
        for (String h : MAX_HINTS) {
            if (lc.contains(h)) {
                return Goal.Mode.MAXIMIZE;
            }
        }
        return Goal.Mode.MINIMIZE;
    }
}
