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

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.SampleSlot;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The one owner of a run's stop decisions (AN-10): the candidate limit, the error budget and the metric target of
 * cf.run.stop.*, next to the time budget, cancellation and the end of the candidates. Every observation-based decision is
 * made in delivery order, which is candidate order, so the candidate that decides it (the boundary) does not depend on
 * workers or timing; the candidate limit is applied at admission, so its candidates are exactly the first ones. A decision
 * ends admission and lets the candidates already admitted finish; the summary states the reason, the boundary and how
 * many candidates were admitted before the decision was known.
 *
 * <p>Not thread-safe by itself: {@link #admit()} runs on the admission thread, {@link #observe} on the delivery thread;
 * the decision is published through a volatile field.</p>
 */
public final class StopPolicy {

    /** Why admission ended; one per run. */
    public enum Reason {
        /** Every executable candidate was admitted. */
        EXHAUSTED,
        /** cf.run.stop.candidates candidates were admitted. */
        CANDIDATE_LIMIT,
        /** cf.run.stop.errors verdict samples ended BROKEN, TIMEOUT or INFRA_FAIL. */
        ERROR_BUDGET,
        /** A PASS verdict sample reached cf.run.stop.target. */
        TARGET_REACHED,
        /** cf.run.budget.millis passed; running samples were cancelled. */
        TIME_BUDGET,
        /** The run was cancelled. */
        CANCELLED,
        /** The run failed. */
        FAILED
    }

    private final long candidateLimit;
    private final long errorBudget;
    private final RunConfig.StopTarget target;
    private final Predicate<Observation> measurement;

    private long admitted;
    private long errors;
    private long pendingBoundary;
    private SampleSlot pendingSlot;
    private Observation pending;
    private volatile Reason decided;
    private volatile long boundary = -1;
    private volatile String detail = "";

    /**
     * @param measurement whether an observation is a measurement sample (never counted: only verdict samples decide)
     */
    StopPolicy(RunConfig config, Predicate<Observation> measurement) {
        this.candidateLimit = config.stopCandidates();
        this.errorBudget = config.stopErrors();
        this.target = config.stopTarget();
        this.measurement = Objects.requireNonNull(measurement, "measurement");
    }

    /** Whether any cf.run.stop.* setting is in force. */
    public boolean active() {
        return candidateLimit > 0 || errorBudget > 0 || !target.isNone();
    }

    /**
     * Whether admission must end before the next candidate, which exists; called by the admission thread. The candidate
     * limit is decided here, so the admitted candidates are exactly the first ones.
     */
    boolean stopsAdmission() {
        if (decided != null) {
            return true;
        }
        if (candidateLimit > 0 && admitted >= candidateLimit) {
            decide(Reason.CANDIDATE_LIMIT, admitted, "the first " + candidateLimit + " candidates in candidate order were admitted "
                    + "(cf.run.stop.candidates)");
            return true;
        }
        return false;
    }

    /** How many candidates were admitted. */
    long admitted() {
        return admitted;
    }

    /** One more candidate was admitted. */
    void admit() {
        admitted++;
    }

    /**
     * Seeds the counts from the final observations a checkpoint restored (they are not delivered again); a budget or
     * target they already reach ends admission at once.
     */
    void restored(List<Observation> observations) {
        java.util.Map<SampleSlot, Observation> last = new java.util.LinkedHashMap<>();
        for (Observation o : observations) {
            Observation before = last.get(o.slot());
            if (before == null || before.key().attempt() < o.key().attempt()) {
                last.put(o.slot(), o);
            }
        }
        for (Observation o : last.values()) {
            if (o.outcome() != Outcome.CANCELLED) {
                count(o, -1);
            }
        }
    }

    /** One delivered observation, in delivery order; a slot's last attempt is its final observation. */
    void observe(long candidateBoundary, Observation o) {
        if (pending != null && !pendingSlot.equals(o.slot())) {
            count(pending, pendingBoundary);
        }
        pending = o;
        pendingSlot = o.slot();
        pendingBoundary = candidateBoundary;
    }

    /** The end of delivery: the last slot is final. */
    void delivered() {
        if (pending != null) {
            count(pending, pendingBoundary);
            pending = null;
        }
    }

    private void count(Observation o, long candidateBoundary) {
        if (measurement.test(o) || decided != null) {
            return;
        }
        if (errorBudget > 0 && (o.outcome() == Outcome.BROKEN || o.outcome() == Outcome.TIMEOUT || o.outcome() == Outcome.INFRA_FAIL)
                && ++errors >= errorBudget) {
            decide(Reason.ERROR_BUDGET, candidateBoundary, errors + " verdict samples ended BROKEN, TIMEOUT or INFRA_FAIL "
                    + "(cf.run.stop.errors = " + errorBudget + ")");
            return;
        }
        if (!target.isNone() && o.outcome() == Outcome.PASS) {
            Double value = o.domain().map(d -> d.metrics().get(target.key())).orElse(null);
            if (value != null && target.reachedBy(value)) {
                decide(Reason.TARGET_REACHED, candidateBoundary, "a PASS sample reported " + target.key() + " = " + value + " (cf.run.stop.target "
                        + target + ")");
            }
        }
    }

    private synchronized void decide(Reason reason, long at, String why) {
        if (decided == null) {
            boundary = at;
            detail = why;
            decided = reason;
        }
    }

    /** The decision so far, or null. */
    Reason decided() {
        return decided;
    }

    /** Candidates in candidate order up to the one that decided (-1 when restored observations decided, unknown order). */
    long boundary() {
        return boundary;
    }

    String detail() {
        return detail;
    }
}
