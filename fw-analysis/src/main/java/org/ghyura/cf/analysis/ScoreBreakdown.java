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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The heuristic score of one logical line (legacy AnalyzerCore.LineResult): the total, its decision against the
 * thresholds ({@link #OPT} at or above the opt threshold, {@link #WATCH} at or above the watch threshold, else
 * {@link #REJECT}), the named contributions in the order they were made (legacy names such as DominantType, FreqTokens,
 * ReqToken:status, MissOptMetric[latency]; OptArgmin[k] and OptFmin[k] record a goal's Brent optimum, not a
 * contribution), notes and the matched rule names. A heuristic decision never decides whether a sample may win an
 * optimization; that stays with verdicts and goals.
 */
public record ScoreBreakdown(int lineNo, String originalLine, String raw, TextFeatures features, double score, String decision,
                             Map<String, Double> contributions, List<String> notes, List<String> matchedRules) {

    public static final String OPT = "opt";
    public static final String WATCH = "watch";
    public static final String REJECT = "reject";

    public ScoreBreakdown {
        contributions = Collections.unmodifiableMap(new LinkedHashMap<>(contributions));
        notes = List.copyOf(notes);
        matchedRules = List.copyOf(matchedRules);
    }
}
