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

import org.ghyura.cf.model.Outcome;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One report that checks every stage invariant of a run (the legacy bundle/invariants.py, over the run's own totals
 * instead of database rows): generation, filtering and selection, admission, execution, retries, outcomes, delivery,
 * aggregation and the values the candidates exercised. A {@link Kind#CONSISTENCY} check states how the totals must add
 * up, so its failure is a defect of the framework, never a property of the scenario; a {@link Kind#NOTICE} names what a
 * reader should look at (broken samples, values no candidate held). A check that cannot be decided says why.
 */
public record Reconciliation(List<Check> checks) {

    public Reconciliation {
        checks = List.copyOf(checks);
    }

    /** Whether a failure is a framework defect or a signal about the run. */
    public enum Kind { CONSISTENCY, NOTICE }

    /** What a check found. */
    public enum Status { HOLDS, FAILS, NOT_APPLICABLE }

    /**
     * One invariant.
     *
     * @param id       a stable name
     * @param legacy   the legacy invariant it corresponds to, or empty
     * @param stage    generation, filtering, admission, execution, retries, outcomes, delivery, aggregation or values
     * @param rule     what must hold, in words
     * @param expected the expected figure (or why the check does not apply)
     * @param actual   the figure found
     */
    public record Check(String id, String legacy, String stage, Kind kind, String rule, String expected, String actual, Status status) {
        public Check {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(status, "status");
        }
    }

    /** Whether no consistency check fails. */
    public boolean consistent() {
        return checks.stream().noneMatch(c -> c.kind() == Kind.CONSISTENCY && c.status() == Status.FAILS);
    }

    public long count(Status status) {
        return checks.stream().filter(c -> c.status() == status).count();
    }

    /** The checks that fail, consistency first. */
    public List<Check> failures() {
        List<Check> out = new ArrayList<>();
        checks.stream().filter(c -> c.status() == Status.FAILS && c.kind() == Kind.CONSISTENCY).forEach(out::add);
        checks.stream().filter(c -> c.status() == Status.FAILS && c.kind() == Kind.NOTICE).forEach(out::add);
        return out;
    }

    /** Reconciles a finished run. */
    public static Reconciliation of(RunSummary s) {
        List<Check> c = new ArrayList<>();
        RunSummary.Execution x = s.execution();
        long finals = s.samples() + x.measurements();
        boolean exact = s.countsExact();
        boolean failed = s.state() == RunState.FAILED;
        boolean whole = s.state() == RunState.COMPLETED && !x.budgetExhausted();

        c.add(check("generated", "core_count_positive", "generation", Kind.NOTICE, "the scenario generated candidates",
                "more than 0", s.generated().toString(), s.generated().signum() > 0));
        if (exact) {
            BigInteger accounted = s.excluded().add(s.unselected()).add(s.executable());
            c.add(check("partition", "post_sieve_le_core", "filtering", Kind.CONSISTENCY,
                    "every generated candidate is excluded by a rule, left out by covering selection or executable",
                    "generated " + s.generated(), "excluded " + s.excluded() + " + unselected " + s.unselected() + " + executable "
                            + s.executable() + " = " + accounted, accounted.equals(s.generated())));
            c.add(check("admitted-within-executable", "reader_emitted_eq_expected", "admission", Kind.CONSISTENCY,
                    "no more candidates are admitted than are executable", "at most " + s.executable(), Long.toString(s.admitted()),
                    BigInteger.valueOf(s.admitted()).compareTo(s.executable()) <= 0));
        } else {
            c.add(notApplicable("partition", "post_sieve_le_core", "filtering", Kind.CONSISTENCY,
                    "every generated candidate is excluded by a rule, left out by covering selection or executable",
                    "the counting walk was cut at its scan limit, so the counts are not exact"));
            c.add(notApplicable("admitted-within-executable", "reader_emitted_eq_expected", "admission", Kind.CONSISTENCY,
                    "no more candidates are admitted than are executable", "the executable count is not exact"));
        }
        String admitAll = "a run whose candidates ran out admitted every executable candidate";
        RunSummary.Stop stop = s.stop();
        if (exact && whole && stop.reason() == StopPolicy.Reason.EXHAUSTED) {
            c.add(check("completed-admitted-all", "reader_emitted_eq_expected", "admission", Kind.CONSISTENCY, admitAll,
                    s.executable().toString(), Long.toString(s.admitted()), BigInteger.valueOf(s.admitted()).equals(s.executable())));
        } else {
            c.add(notApplicable("completed-admitted-all", "reader_emitted_eq_expected", "admission", Kind.CONSISTENCY, admitAll,
                    !exact ? "the executable count is not exact" : "admission ended by " + stop.reason() + " (" + stop.detail() + ")"));
        }
        switch (stop.reason()) {
            case CANDIDATE_LIMIT -> c.add(check("stop-boundary", "", "admission", Kind.CONSISTENCY,
                    "the candidate limit admitted exactly the first candidates in candidate order", "admitted " + stop.boundary(),
                    "admitted " + s.admitted(), s.admitted() == stop.boundary()));
            case ERROR_BUDGET, TARGET_REACHED -> c.add(check("stop-boundary", "", "admission", Kind.CONSISTENCY,
                    "the candidate that decided the stop was admitted, and the candidates after it only before the decision was known",
                    "a boundary within the " + s.admitted() + " admitted candidates (or one restored from a checkpoint)",
                    "boundary " + stop.boundary(), stop.boundary() == -1 || (stop.boundary() >= 1 && stop.boundary() <= s.admitted())));
            default -> {
            }
        }
        String oneFinal = "every admitted sample has exactly one final observation (verdict samples and measurements)";
        if (failed) {
            c.add(notApplicable("one-final-per-sample", "results_v2_attempted_matches_processed", "execution", Kind.CONSISTENCY, oneFinal,
                    "the run failed, so samples may lack their final observation"));
        } else {
            c.add(check("one-final-per-sample", "results_v2_attempted_matches_processed", "execution", Kind.CONSISTENCY, oneFinal,
                    "admitted samples " + x.admittedSamples(), "samples " + s.samples() + " + measurements " + x.measurements() + " = "
                            + finals, finals == x.admittedSamples()));
        }
        long outcomeSum = s.outcomes().values().stream().mapToLong(Long::longValue).sum();
        long measuredSum = x.measurementOutcomes().values().stream().mapToLong(Long::longValue).sum();
        c.add(check("outcomes-add-up", "executor_processed_eq_sum", "outcomes", Kind.CONSISTENCY,
                "the final outcomes add up to the samples, and the measurement outcomes to the measurements",
                "samples " + s.samples() + ", measurements " + x.measurements(), "outcomes " + outcomeSum + ", measurement outcomes "
                        + measuredSum, outcomeSum == s.samples() && measuredSum == x.measurements()));
        long verdictSum = s.verdicts().values().stream().mapToLong(Long::longValue).sum();
        long domain = s.count(Outcome.PASS) + s.count(Outcome.DOMAIN_FAIL);
        c.add(check("verdicts-recorded", "executor_inserted_matches_policy", "outcomes", Kind.CONSISTENCY,
                "every PASS and DOMAIN_FAIL sample has its verdict code, and nothing else has one",
                "PASS + DOMAIN_FAIL " + domain, "verdict codes " + verdictSum, verdictSum == domain));
        c.add(check("attempts-cover-retries", "results_v2_write_counts_consistent", "retries", Kind.CONSISTENCY,
                "attempts are one per final observation plus at least one earlier attempt for every retried sample",
                "at least " + finals + " + " + s.retried() + " retried = " + (finals + s.retried()), "attempts " + s.attempts(),
                s.attempts() >= finals + s.retried() && s.retried() <= s.samples()));
        long delivered = x.deliveries() + x.listenerFailures() + x.restoredAttempts();
        String everyCommit = "every committed attempt was delivered to the run's listener once, or counted as never taken "
                + "(attempts restored from a checkpoint were delivered before)";
        if (failed) {
            c.add(notApplicable("every-commit-delivered", "results_db_eq_inserted", "delivery", Kind.CONSISTENCY, everyCommit,
                    "the run failed, so delivery may have stopped early"));
        } else {
            c.add(check("every-commit-delivered", "results_db_eq_inserted", "delivery", Kind.CONSISTENCY, everyCommit,
                    "attempts " + s.attempts(), "delivered " + x.deliveries() + " + never taken " + x.listenerFailures() + " + restored "
                            + x.restoredAttempts() + " = " + delivered, delivered == s.attempts()));
        }
        c.add(check("no-broken", "executor_broken_zero", "outcomes", Kind.NOTICE, "no sample is BROKEN (not compiled, no verdict, malformed)",
                "0", Long.toString(s.count(Outcome.BROKEN)), s.count(Outcome.BROKEN) == 0));
        c.add(check("no-timeout", "executor_timeout_zero", "outcomes", Kind.NOTICE, "no sample ran out of its deadline", "0",
                Long.toString(s.count(Outcome.TIMEOUT)), s.count(Outcome.TIMEOUT) == 0));
        c.add(check("no-infra-failure", "executor_infra_fail_zero", "outcomes", Kind.NOTICE, "no sample failed for the framework or its "
                + "environment", "0", Long.toString(s.count(Outcome.INFRA_FAIL)), s.count(Outcome.INFRA_FAIL) == 0));
        c.add(check("no-abandoned-thread", "", "execution", Kind.NOTICE, "every sample stopped when asked", "0", Long.toString(s.abandoned()),
                s.abandoned() == 0));
        c.add(check("listener-took-all", "", "delivery", Kind.NOTICE, "the run's listener took every observation", "0",
                Long.toString(x.listenerFailures()), x.listenerFailures() == 0));
        if (s.analysis().isPresent()) {
            var a = s.analysis().get();
            long excludedSum = a.excluded().values().stream().mapToLong(Long::longValue).sum();
            c.add(check("analysis-read-every-final", "analyzer_input_matches_metrics", "aggregation", Kind.CONSISTENCY,
                    "the analysis read every final observation", "final observations " + finals, "analysed samples " + a.samples(),
                    a.samples() == finals));
            c.add(check("analysis-partition", "", "aggregation", Kind.CONSISTENCY,
                    "every analysed sample is eligible or excluded for one reason", "analysed " + a.samples(), "eligible " + a.eligible()
                            + " + excluded " + excludedSum + " = " + (a.eligible() + excludedSum), a.eligible() + excludedSum == a.samples()));
        } else {
            c.add(notApplicable("analysis-read-every-final", "analyzer_input_matches_metrics", "aggregation", Kind.CONSISTENCY,
                    "the analysis read every final observation", "the run was not analysed (cf.analysis.enabled)"));
        }
        if (x.axes().isEmpty()) {
            c.add(notApplicable("every-value-exercised", "subset_axis_coverage", "values", Kind.NOTICE,
                    "every value of every axis was held by an admitted candidate", "the candidates were not assembled from a scenario"));
        } else {
            List<String> missed = new ArrayList<>();
            int declared = 0;
            int exercised = 0;
            for (RunSummary.Axis axis : x.axes()) {
                declared += axis.declared();
                exercised += axis.exercised();
                if (axis.exercised() < axis.declared()) {
                    missed.add(axis.sheet() + " " + (axis.declared() - axis.exercised()) + " of " + axis.declared() + " (" + String.join(", ",
                            axis.unexercised()) + (axis.declared() - axis.exercised() > axis.unexercised().size() ? ", ..." : "") + ")");
                }
            }
            c.add(check("every-value-exercised", "subset_axis_coverage, no_axis_value_fully_excluded", "values", Kind.NOTICE,
                    "every value of every axis was held by an admitted candidate (a value no candidate held was excluded by the rules, "
                            + "left out by selection, never generated or not reached)", declared + " values",
                    exercised + " exercised" + (missed.isEmpty() ? "" : "; not exercised: " + String.join("; ", missed)), missed.isEmpty()));
        }
        return new Reconciliation(c);
    }

    private static Check check(String id, String legacy, String stage, Kind kind, String rule, String expected, String actual, boolean holds) {
        return new Check(id, legacy, stage, kind, rule, expected, actual, holds ? Status.HOLDS : Status.FAILS);
    }

    private static Check notApplicable(String id, String legacy, String stage, Kind kind, String rule, String why) {
        return new Check(id, legacy, stage, kind, rule, why, "", Status.NOT_APPLICABLE);
    }
}
