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

import org.ghyura.cf.config.CapabilityClaim;
import org.ghyura.cf.config.CapabilityProvider;
import org.ghyura.cf.config.CapabilityStatus;

import java.util.List;

/**
 * What the analysis can prove today (M06): the legacy analyzer's algorithms (C10) and its numerical optimisation without the
 * Python/SciPy subprocess (C11).
 */
public final class AnalysisCapabilities implements CapabilityProvider {

    static final String C10_EVIDENCE = "M06: goals with direction, target and weight; discovery by key name; metric statistics with "
            + "mergeable Welford state and deterministic weighted reservoirs; exact Pareto fronts (no top-1000 prefilter) with the "
            + "NSGA-II, NSGA-III, SPEA2 and MOEA/D rankers; balanced picks; top-K by a frozen-profile score; repeat medians with "
            + "exact order-statistic intervals; eligibility that never lets failures win; the analyze command and the run "
            + "analysis. M09: budgeted JaCoCo XML, coverage.py JSON and gcov summary imports through MetricCorpus and analyze, "
            + "with per-line provenance; the heuristic textual scorer (logical lines, a frozen corpus profile, synthesized and manual "
            + "rules, named contributions, opt/watch/reject decisions) and the score command, matching legacy analyzer goldens. "
            + "fw-desktop: the Swing analysis and text-scorer views (result table, legacy filter, breakdown, summary, CSV export, the "
            + "legacy optimization charts) over the shared session; the legacy Auto-Analysis plan as explicit suggested goals; optional "
            + "in-memory line instrumentation of generated Java candidates (cf.execution.coverage=lines: coverage_line of the "
            + "candidate's own executable lines per verdict sample, probes per attempt, no agent or subprocess).";
    static final String C11_EVIDENCE = "M06: Brent, Newton-Raphson, gradient descent, Nelder-Mead, simulated annealing, Simpson, "
            + "trapezoid and Runge-Kutta in Java with convergence metadata, a typed objective callback and a declared expression "
            + "language; Python objectives are refused, never run.";

    @Override
    public List<CapabilityClaim> claims() {
        return List.of(new CapabilityClaim("C10", CapabilityStatus.IMPLEMENTED, C10_EVIDENCE),
                new CapabilityClaim("C11", CapabilityStatus.IMPLEMENTED, C11_EVIDENCE));
    }
}
