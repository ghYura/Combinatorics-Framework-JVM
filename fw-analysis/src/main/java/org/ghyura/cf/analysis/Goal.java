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

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.util.Objects;

/**
 * One optimisation axis (legacy GoalSpec): a metric key, its direction and, for weighted scores, a finite weight at least
 * 0. The deviation of a value is what every comparison minimises: the value, its negation, or its distance to the target.
 *
 * @param key    the metric key
 * @param mode   minimise, maximise or reach a target
 * @param target the target value (used only by TARGET)
 * @param weight the weight in scores and balanced picks; 0 keeps the axis on the front only
 */
public record Goal(String key, Mode mode, double target, double weight) {

    public enum Mode {
        MINIMIZE("min"),
        MAXIMIZE("max"),
        TARGET("target");

        private final String token;

        Mode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    public Goal {
        if (key == null || key.isBlank()) {
            throw invalid("a goal needs a metric key");
        }
        Objects.requireNonNull(mode, "mode");
        if (!Double.isFinite(target)) {
            throw invalid("goal " + key + ": the target must be finite");
        }
        if (!Double.isFinite(weight) || weight < 0) {
            throw invalid("goal " + key + ": the weight must be finite and at least 0");
        }
    }

    public static Goal min(String key) {
        return new Goal(key, Mode.MINIMIZE, 0, 1);
    }

    public static Goal max(String key) {
        return new Goal(key, Mode.MAXIMIZE, 0, 1);
    }

    public static Goal target(String key, double target) {
        return new Goal(key, Mode.TARGET, target, 1);
    }

    /** The goal a cf.analysis.goals entry declares. */
    public static Goal of(RunConfig.AnalysisGoal g) {
        Mode mode = switch (g.mode()) {
            case MIN -> Mode.MINIMIZE;
            case MAX -> Mode.MAXIMIZE;
            case TARGET -> Mode.TARGET;
        };
        return new Goal(g.key(), mode, g.target(), g.weight());
    }

    /** What comparisons minimise: v, -v or |v - target|. */
    public double deviation(double v) {
        return switch (mode) {
            case MINIMIZE -> v;
            case MAXIMIZE -> -v;
            case TARGET -> Math.abs(v - target);
        };
    }

    /** The goal as cf.analysis.goals writes it. */
    public String token() {
        String w = weight == 1 ? "" : ":" + weight;
        return mode == Mode.TARGET ? key + ":target:" + target + w : key + ":" + mode.token() + w;
    }

    private static FrameworkException invalid(String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.ANALYSIS_INVALID_GOAL, message, SourceLocation.NONE));
    }
}
