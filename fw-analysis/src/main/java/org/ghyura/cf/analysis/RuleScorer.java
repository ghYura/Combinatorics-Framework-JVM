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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scores lines against a frozen profile, its synthesized rules and the manual rules, contribution by contribution as the
 * legacy scoreLine did: dominant type and signature (10 x confidence), frequent tokens with enough support, frequent keys
 * and frequent numbers (5, 10 and 5 x confidence shared over the line's tokens, keys and numbers), length and entropy
 * inside the 10th-90th percentile band (5 x confidence each), then the manual rules (required tokens and keys +15 or -15,
 * forbidden -50, token weights, expected line types and signatures +20 or -10, length limits -20, required regexes on the
 * raw text +20 or -20, forbidden -50, numeric ranges +10 or -10) and the goals: per goal with a positive weight and more than
 * one value in the profile, a missing metric costs half the weight, a degenerate 5th-95th band is skipped, and otherwise
 * the value normalized into the band and clamped earns weight x (1 - norm), weight x norm or weight x (1 - |norm - target|)
 * for minimize, maximize and target (the Brent argmin as the target when it converged). Immutable and thread-safe.
 */
public final class RuleScorer {

    private final RuleSet rules;
    private final CorpusProfile profile;
    private final CorpusProfile.Synthesized synthesized;
    private final double optThreshold;
    private final double watchThreshold;

    public RuleScorer(RuleSet rules, CorpusProfile profile, CorpusProfile.Synthesized synthesized, double optThreshold, double watchThreshold) {
        this.rules = rules;
        this.profile = profile;
        this.synthesized = synthesized;
        this.optThreshold = optThreshold;
        this.watchThreshold = watchThreshold;
    }

    /** A goal or range metric of a line: its length, its entropy or the numeric value of its key (legacy extractMetric). */
    static Double metric(TextFeatures f, String key) {
        if ("length".equals(key)) {
            return (double) f.length();
        }
        if ("entropy".equals(key)) {
            return f.entropy();
        }
        String v = f.keyValues().get(key);
        if (v == null) {
            return null;
        }
        OptionalDouble d = Numbers.parse(v);
        return d.isPresent() ? d.getAsDouble() : null;
    }

    public ScoreBreakdown score(TextFeatures f) {
        if ("empty".equals(f.lineType())) {
            return new ScoreBreakdown(f.lineNo(), f.originalLine(), f.raw(), f, 0.0, ScoreBreakdown.REJECT, Map.of(), List.of("Empty line"),
                    List.of());
        }
        CorpusProfile.Synthesized r = synthesized;
        double total = 0.0;
        Map<String, Double> b = new LinkedHashMap<>();
        List<String> n = new ArrayList<>();
        List<String> mr = new ArrayList<>();
        double conf = r.confidence();
        if (f.lineType().equals(r.dominantLineType())) {
            double x = 10.0 * conf;
            total += x;
            b.put("DominantType", x);
            mr.add("DominantType");
        }
        if (f.signature().equals(r.dominantSignature())) {
            double x = 10.0 * conf;
            total += x;
            b.put("DominantSignature", x);
            mr.add("DominantSignature");
        }
        double tok = 0.0;
        int lowersSize = Math.max(1, f.tokenLowers().size());
        for (String t : f.tokenLowers()) {
            if (r.topTokens().contains(t) && r.tokenSupport().getOrDefault(t, 0) >= r.tokenDfCutoff()) tok += (5.0 * conf / lowersSize);
        }
        if (tok > 0) {
            total += tok;
            b.put("FreqTokens", tok);
            mr.add("FreqTokens");
        }
        double key = 0.0;
        int kvSize = Math.max(1, f.keyValues().size());
        for (String k : f.keyValues().keySet()) {
            if (r.topKeys().contains(k)) key += (10.0 * conf / kvSize);
        }
        if (key > 0) {
            total += key;
            b.put("FreqKeys", key);
            mr.add("FreqKeys");
        }
        double num = 0.0;
        int numSize = Math.max(1, f.numbers().size());
        for (Double nm : f.numbers()) {
            if (r.topNumbers().contains(TextFeatures.canonicalNumber(nm))) num += (5.0 * conf / numSize);
        }
        if (num > 0) {
            total += num;
            b.put("FreqNumbers", num);
            mr.add("FreqNumbers");
        }
        if (f.length() >= r.lengthP10() && f.length() <= r.lengthP90()) {
            double x = 5.0 * conf;
            total += x;
            b.put("LengthInIQR", x);
            mr.add("LengthInIQR");
        }
        if (f.entropy() >= r.entropyP10() && f.entropy() <= r.entropyP90()) {
            double x = 5.0 * conf;
            total += x;
            b.put("EntropyInIQR", x);
            mr.add("EntropyInIQR");
        }
        if (!rules.requiredTokens().isEmpty() || !rules.forbiddenTokens().isEmpty() || !rules.tokenWeights().isEmpty()) {
            Set<String> toks = new HashSet<>(f.tokenLowers());
            for (String req : rules.requiredTokens()) {
                if (toks.contains(req)) {
                    total += 15.0;
                    b.put("ReqToken:" + req, 15.0);
                    mr.add("ReqToken");
                } else {
                    total += -15.0;
                    b.put("MissReqToken:" + req, -15.0);
                    n.add("Missing required token: " + req);
                }
            }
            for (String fb : rules.forbiddenTokens()) {
                if (toks.contains(fb)) {
                    total += -50.0;
                    b.put("ForbidToken:" + fb, -50.0);
                    n.add("Contains forbidden token: " + fb);
                }
            }
            for (Map.Entry<String, Double> e : rules.tokenWeights().entrySet()) {
                if (toks.contains(e.getKey())) {
                    total += e.getValue();
                    b.put("TokenWeight:" + e.getKey(), e.getValue());
                    mr.add("TokenWeight");
                }
            }
        }
        if (!rules.requiredKeys().isEmpty() || !rules.forbiddenKeys().isEmpty()) {
            Set<String> keys = f.keyValues().keySet();
            for (String req : rules.requiredKeys()) {
                if (keys.contains(req)) {
                    total += 15.0;
                    b.put("ReqKey:" + req, 15.0);
                    mr.add("ReqKey");
                } else {
                    total += -15.0;
                    b.put("MissReqKey:" + req, -15.0);
                    n.add("Missing required key: " + req);
                }
            }
            for (String fb : rules.forbiddenKeys()) {
                if (keys.contains(fb)) {
                    total += -50.0;
                    b.put("ForbidKey:" + fb, -50.0);
                    n.add("Contains forbidden key: " + fb);
                }
            }
        }
        if (!rules.expectedLineTypes().isEmpty()) {
            if (rules.expectedLineTypes().contains(f.lineType())) {
                total += 20.0;
                b.put("ExpType", 20.0);
                mr.add("ExpType");
            } else {
                total += -10.0;
                b.put("WrongType", -10.0);
                n.add("Unexpected type: " + f.lineType());
            }
        }
        if (!rules.expectedSignatures().isEmpty()) {
            if (rules.expectedSignatures().contains(f.signature())) {
                total += 20.0;
                b.put("ExpSignature", 20.0);
                mr.add("ExpSignature");
            } else {
                total += -10.0;
                b.put("WrongSignature", -10.0);
                n.add("Unexpected signature: " + f.signature());
            }
        }
        if (rules.minLength() != null && f.length() < rules.minLength()) {
            total += -20.0;
            b.put("TooShort", -20.0);
            n.add("Length " + f.length() + " < " + rules.minLength());
        }
        if (rules.maxLength() != null && f.length() > rules.maxLength()) {
            total += -20.0;
            b.put("TooLong", -20.0);
            n.add("Length " + f.length() + " > " + rules.maxLength());
        }
        for (Pattern pat : rules.requiredRegexes()) {
            if (pat.matcher(f.raw()).find()) {
                total += 20.0;
                b.put("ReqRegex:" + pat.pattern(), 20.0);
                mr.add("ReqRegex");
            } else {
                total += -20.0;
                b.put("MissRegex:" + pat.pattern(), -20.0);
                n.add("Misses regex: " + pat.pattern());
            }
        }
        for (Pattern pat : rules.forbiddenRegexes()) {
            if (pat.matcher(f.raw()).find()) {
                total += -50.0;
                b.put("ForbidRegex:" + pat.pattern(), -50.0);
                n.add("Matches forbid regex: " + pat.pattern());
            }
        }
        for (Map.Entry<String, RuleSet.Range> e : rules.numericRanges().entrySet()) {
            String k = e.getKey();
            RuleSet.Range range = e.getValue();
            Double val = metric(f, k);
            if (val != null) {
                if (val >= range.low() && val <= range.high()) {
                    total += 10.0;
                    b.put("Range:" + k, 10.0);
                    mr.add("NumRange");
                } else {
                    total += -10.0;
                    b.put("OutRange:" + k, -10.0);
                    n.add(k + "=" + val + " out of [" + range.low() + "," + range.high() + "]");
                }
            }
        }
        n.addAll(rules.notes());
        for (RuleSet.Goal goal : rules.goals()) {
            String optKey = goal.key();
            double weight = goal.weight();
            CorpusProfile.MetricBand band = profile.metrics().get(optKey);
            int count = band == null ? 0 : band.count();
            if (weight <= 0 || count <= 1) {
                continue;
            }
            Double val = metric(f, optKey);
            if (val == null) {
                double x = -weight * 0.5;
                total += x;
                b.put("MissOptMetric[" + optKey + "]", x);
                continue;
            }
            double lo = band.low();
            double hi = band.high();
            if (hi <= lo) {
                continue;
            }
            double norm = clamp((val - lo) / (hi - lo));
            double bonus;
            String tag;
            if (goal.optimumValid()) {
                double tNorm = clamp((goal.argmin() - lo) / (hi - lo));
                bonus = (1.0 - Math.abs(norm - tNorm)) * weight;
                tag = "OptBrent[" + optKey + "]";
                b.put("OptArgmin[" + optKey + "]", goal.argmin());
                b.put("OptFmin[" + optKey + "]", goal.optimum());
            } else if ("minimize".equals(goal.mode())) {
                bonus = (1.0 - norm) * weight;
                tag = "OptTarget[" + optKey + "]";
            } else if ("maximize".equals(goal.mode())) {
                bonus = norm * weight;
                tag = "OptTarget[" + optKey + "]";
            } else {
                double tNorm = clamp((goal.target() - lo) / (hi - lo));
                bonus = (1.0 - Math.abs(norm - tNorm)) * weight;
                tag = "OptTarget[" + optKey + "]";
            }
            total += bonus;
            b.put(tag, bonus);
            mr.add(goal.optimumValid() ? "OptBrent" : "OptTarget");
        }
        String decision = total >= optThreshold ? ScoreBreakdown.OPT : (total >= watchThreshold ? ScoreBreakdown.WATCH : ScoreBreakdown.REJECT);
        return new ScoreBreakdown(f.lineNo(), f.originalLine(), f.raw(), f, total, decision, b, n, mr);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
