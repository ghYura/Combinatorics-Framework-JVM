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

package org.ghyura.cf.campaign;

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.spi.DomainObservation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AN-10: the stop owner's decisions, made in delivery (candidate) order on each sample's final attempt. */
class StopPolicyTest {

    private static final RunId RUN = new RunId("r");

    private static RunConfig config(String... settings) {
        return ConfigResolver.resolve(null, Map.of(), java.util.Arrays.stream(settings)
                .map(s -> new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test")).toList()).config();
    }

    private static CandidateId id(int c) {
        return new CandidateId(Character.toString('a' + c).repeat(64));
    }

    private static Observation seen(int candidate, int repeat, int attempt, Outcome outcome, Map<String, Double> metrics) {
        SampleKey key = new SampleKey(RUN, id(candidate), attempt, repeat, "");
        boolean verdict = outcome.isDomainVerdict();
        return new Observation(key, candidate + "_0_0", outcome, verdict ? Optional.of(new DomainObservation(outcome == Outcome.PASS ? 0 : 2,
                metrics, Map.of())) : Optional.empty(), 1, "s", "p", verdict ? Optional.empty() : Optional.of(outcome == Outcome.BROKEN
                ? ErrorCode.EXEC_COMPILE_ERROR : ErrorCode.EXEC_DEADLINE), "", "", "", false);
    }

    @Test
    void errorsCountOnTheFinalAttemptOfVerdictSamplesAndDecideAtTheirCandidate() {
        StopPolicy p = new StopPolicy(config("cf.run.stop.errors=2"), o -> o.key().repeatIndex() > 0);
        assertTrue(p.active());
        p.observe(1, seen(0, 0, 0, Outcome.TIMEOUT, Map.of()));
        p.observe(1, seen(0, 0, 1, Outcome.PASS, Map.of()));
        p.observe(1, seen(0, 1, 0, Outcome.BROKEN, Map.of()));
        p.observe(2, seen(1, 0, 0, Outcome.BROKEN, Map.of()));
        assertNull(p.decided(), "a retried TIMEOUT and a measurement are not errors; candidate 1's sample is counted when it is final");
        p.observe(3, seen(2, 0, 0, Outcome.INFRA_FAIL, Map.of()));
        assertNull(p.decided(), "candidate 2's slot may still be retried");
        p.observe(4, seen(3, 0, 0, Outcome.PASS, Map.of()));
        assertEquals(StopPolicy.Reason.ERROR_BUDGET, p.decided());
        assertEquals(3, p.boundary(), "the third candidate in candidate order decided it");
        assertTrue(p.stopsAdmission());
        p.delivered();
        assertEquals(3, p.boundary(), "the first decision stays");
    }

    @Test
    void aTargetIsReachedOnlyByAPassSampleAndTheLimitByAdmission() {
        StopPolicy target = new StopPolicy(config("cf.run.stop.target=latency<=10"), o -> false);
        target.observe(1, seen(0, 0, 0, Outcome.DOMAIN_FAIL, Map.of("latency", 5.0)));
        target.observe(2, seen(1, 0, 0, Outcome.PASS, Map.of("latency", 12.0)));
        target.observe(3, seen(2, 0, 0, Outcome.PASS, Map.of("latency", 10.0)));
        assertNull(target.decided());
        target.delivered();
        assertEquals(StopPolicy.Reason.TARGET_REACHED, target.decided());
        assertEquals(3, target.boundary());

        StopPolicy limit = new StopPolicy(config("cf.run.stop.candidates=2"), o -> false);
        assertFalse(limit.stopsAdmission());
        limit.admit();
        assertFalse(limit.stopsAdmission());
        limit.admit();
        assertTrue(limit.stopsAdmission());
        assertEquals(StopPolicy.Reason.CANDIDATE_LIMIT, limit.decided());
        assertEquals(2, limit.boundary());

        StopPolicy restored = new StopPolicy(config("cf.run.stop.errors=1"), o -> false);
        restored.restored(List.of(seen(0, 0, 0, Outcome.TIMEOUT, Map.of()), seen(0, 0, 1, Outcome.BROKEN, Map.of())));
        assertEquals(StopPolicy.Reason.ERROR_BUDGET, restored.decided(), "a checkpoint that already used up the budget ends admission at once");
        assertEquals(-1, restored.boundary());
        assertFalse(new StopPolicy(config(), o -> false).active());
    }

    @Test
    void aTargetSettingIsAMetricAComparisonAndAFiniteNumber() {
        assertEquals("coverage_line>=0.9", config("cf.run.stop.target= coverage_line >= 0.9 ").stopTarget().toString());
        for (String bad : List.of("latency", "latency=3", "latency>=x", "latency>=NaN", "1x>=2", ">=3", "a>=1<=2")) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> config("cf.run.stop.target=" + bad), bad);
            assertEquals(ErrorCode.CONFIG_INVALID_VALUE, e.diagnostics().getFirst().code(), bad);
        }
    }
}
