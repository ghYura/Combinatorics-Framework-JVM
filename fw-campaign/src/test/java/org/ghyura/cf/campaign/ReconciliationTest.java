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
import org.ghyura.cf.model.RunId;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GEN:reconciliation: each stage invariant fails exactly when its totals do not add up, and names the figures. */
class ReconciliationTest {

    /** A run's totals, consistent by default: 10 generated, 2 excluded, 1 unselected, 7 executed once each. */
    private static final class Totals {
        RunState state = RunState.COMPLETED;
        boolean exact = true;
        long generated = 10;
        long excluded = 2;
        long unselected = 1;
        long executable = 7;
        long admitted = 7;
        long samples = 7;
        long attempts = 8;
        long retried = 1;
        Map<Outcome, Long> outcomes = new EnumMap<>(Map.of(Outcome.PASS, 5L, Outcome.DOMAIN_FAIL, 1L, Outcome.BROKEN, 1L));
        Map<Integer, Long> verdicts = Map.of(0, 5L, 3, 1L);
        long deliveries = 8;
        long listenerFailures;
        long restored;
        long admittedSamples = 7;
        boolean budgetExhausted;
        RunSummary.Stop stop = new RunSummary.Stop(StopPolicy.Reason.EXHAUSTED, 7, 7, "every executable candidate was admitted");
        List<RunSummary.Axis> axes = List.of(new RunSummary.Axis("OS", 3, 3, List.of()), new RunSummary.Axis("DB", 2, 1, List.of("pg")));

        RunSummary summary() {
            RunSummary.Execution x = new RunSummary.Execution(List.of(""), "local", "all", 0, Map.of(), 0, 0, 0, deliveries, 0,
                    listenerFailures, 0, 0, 0, 1 << 20, budgetExhausted, "full", admittedSamples, restored, axes);
            return new RunSummary(new RunId("r"), state, "mem", "s", "p", "generated-java", "", "d", exact, BigInteger.valueOf(generated),
                    BigInteger.valueOf(excluded), BigInteger.valueOf(unselected), BigInteger.valueOf(executable), admitted, samples, attempts,
                    retried, outcomes, verdicts, Map.of(), 0, List.of(), 0, 0, 1, List.of(), x, Optional.empty(), Optional.empty(), stop);
        }
    }

    private static Reconciliation.Check check(RunSummary s, String id) {
        return s.reconciliation().checks().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void consistentTotalsHoldAndOnlyTheNoticesSpeak() {
        RunSummary s = new Totals().summary();
        Reconciliation r = s.reconciliation();
        assertTrue(r.consistent(), r.failures().toString());
        assertEquals(List.of("no-broken", "every-value-exercised"), r.failures().stream().map(Reconciliation.Check::id).toList());
        assertEquals("4 exercised; not exercised: DB 1 of 2 (pg)", check(s, "every-value-exercised").actual());
        assertEquals(Reconciliation.Status.NOT_APPLICABLE, check(s, "analysis-read-every-final").status());
        assertEquals("post_sieve_le_core", check(s, "partition").legacy());
    }

    @Test
    void everyBrokenSumIsNamedByItsOwnCheck() {
        Totals t = new Totals();
        t.excluded = 3;
        assertEquals(Reconciliation.Status.FAILS, check(t.summary(), "partition").status());

        t = new Totals();
        t.admitted = 6;
        assertEquals(Reconciliation.Status.FAILS, check(t.summary(), "completed-admitted-all").status());
        t.state = RunState.CANCELLED;
        assertEquals(Reconciliation.Status.NOT_APPLICABLE, check(t.summary(), "completed-admitted-all").status(), "a cancelled run stops early");
        t = new Totals();
        t.admitted = 4;
        t.stop = new RunSummary.Stop(StopPolicy.Reason.CANDIDATE_LIMIT, 4, 4, "the first 4 candidates");
        assertEquals(Reconciliation.Status.NOT_APPLICABLE, check(t.summary(), "completed-admitted-all").status());
        assertEquals(Reconciliation.Status.HOLDS, check(t.summary(), "stop-boundary").status());
        t.stop = new RunSummary.Stop(StopPolicy.Reason.CANDIDATE_LIMIT, 3, 4, "the first 3 candidates");
        assertEquals(Reconciliation.Status.FAILS, check(t.summary(), "stop-boundary").status(), "a limit admits exactly its candidates");
        t.stop = new RunSummary.Stop(StopPolicy.Reason.ERROR_BUDGET, 3, 4, "2 errors");
        assertEquals(Reconciliation.Status.HOLDS, check(t.summary(), "stop-boundary").status(), "one more was admitted before the decision");

        t = new Totals();
        t.admittedSamples = 8;
        assertEquals(Reconciliation.Status.FAILS, check(t.summary(), "one-final-per-sample").status());

        t = new Totals();
        t.outcomes = new EnumMap<>(Map.of(Outcome.PASS, 5L, Outcome.DOMAIN_FAIL, 1L));
        RunSummary missing = t.summary();
        assertEquals(Reconciliation.Status.FAILS, check(missing, "outcomes-add-up").status());
        assertFalse(missing.reconciliation().consistent());

        t = new Totals();
        t.verdicts = Map.of(0, 5L);
        assertEquals(Reconciliation.Status.FAILS, check(t.summary(), "verdicts-recorded").status());

        t = new Totals();
        t.attempts = 7;
        assertEquals(Reconciliation.Status.FAILS, check(t.summary(), "attempts-cover-retries").status(), "a retried sample has two attempts");

        t = new Totals();
        t.deliveries = 6;
        t.listenerFailures = 1;
        assertEquals(Reconciliation.Status.FAILS, check(t.summary(), "every-commit-delivered").status());
        t.restored = 1;
        assertEquals(Reconciliation.Status.HOLDS, check(t.summary(), "every-commit-delivered").status(), "a restored attempt was delivered before");
        assertEquals(Reconciliation.Status.FAILS, check(t.summary(), "listener-took-all").status());

        t = new Totals();
        t.exact = false;
        RunSummary cut = t.summary();
        assertEquals(Reconciliation.Status.NOT_APPLICABLE, check(cut, "partition").status());
        assertTrue(check(cut, "partition").expected().contains("scan limit"));
    }
}
