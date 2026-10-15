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

package org.ghyura.cf.results;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.math.BigInteger;

/**
 * Campaign state at an iteration barrier or a drained pause. Completed iteration JSON includes the full run summaries;
 * the next seed and derivation inputs belong to the current iteration, whose optional run checkpoint supplies its attempts.
 * An empty status means continuation; a terminal status preserves the completed campaign without replaying its work.
 * The configuration covers the random seed: current strategies and run samplers derive randomness from that seed, with no
 * mutable campaign RNG. The codec is in fw-artifacts so neither the controller nor embedding requires disk transport.
 */
public record CampaignCheckpoint(String scenarioPath, String baseScenarioDigest, Map<String, String> configuration,
                                 String strategy, int iterationsRequested, int stopWhenStable, double explorationFloor,
                                 int minWinnerSupport, List<String> completedIterations, String inputSeedDigest,
                                 String nextSeedJson, String fullSpace, List<String> previousFront, int stableTransitions,
                                 int iteration, String childScenarioDigest, Optional<Checkpoint> currentRun,
                                 String status, String reason, List<String> diagnostics) {
    public CampaignCheckpoint {
        Objects.requireNonNull(scenarioPath, "scenarioPath");
        Objects.requireNonNull(baseScenarioDigest, "baseScenarioDigest");
        configuration = Collections.unmodifiableMap(new TreeMap<>(configuration));
        Objects.requireNonNull(strategy, "strategy");
        completedIterations = List.copyOf(completedIterations);
        Objects.requireNonNull(inputSeedDigest, "inputSeedDigest");
        Objects.requireNonNull(nextSeedJson, "nextSeedJson");
        Objects.requireNonNull(fullSpace, "fullSpace");
        previousFront = List.copyOf(previousFront);
        Objects.requireNonNull(childScenarioDigest, "childScenarioDigest");
        Objects.requireNonNull(currentRun, "currentRun");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        diagnostics = List.copyOf(diagnostics);
        if (iterationsRequested < 1 || iteration < 1 || iteration > iterationsRequested + 1 || stopWhenStable < 0
                || minWinnerSupport < 1 || stableTransitions < 0 || !Double.isFinite(explorationFloor)
                || explorationFloor < 0 || explorationFloor > 1 || completedIterations.size() != iteration - 1) {
            throw new IllegalArgumentException("invalid campaign checkpoint position or parameters");
        }
        if (currentRun.isPresent() && (!status.isEmpty() || !childScenarioDigest.equals(currentRun.get().scenarioDigest()))) {
            throw new IllegalArgumentException("a current run must identify this continuing iteration's scenario");
        }
        if (status.isEmpty() && iteration > iterationsRequested) throw new IllegalArgumentException("no iteration left to continue");
        if (!fullSpace.isEmpty() && new BigInteger(fullSpace).signum() < 0) throw new IllegalArgumentException("negative full space");
    }

    public String configDigest() { return Checkpoint.configDigest(configuration); }

    /** Adds the drained current run to a frozen pre-iteration snapshot. */
    public CampaignCheckpoint withCurrentRun(Checkpoint run) {
        return new CampaignCheckpoint(scenarioPath, baseScenarioDigest, configuration, strategy, iterationsRequested, stopWhenStable,
                explorationFloor, minWinnerSupport, completedIterations, inputSeedDigest, nextSeedJson, fullSpace, previousFront,
                stableTransitions, iteration, childScenarioDigest, Optional.of(run), status, reason, diagnostics);
    }
}
