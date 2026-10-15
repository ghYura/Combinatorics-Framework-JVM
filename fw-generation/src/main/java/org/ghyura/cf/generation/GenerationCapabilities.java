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


package org.ghyura.cf.generation;

import org.ghyura.cf.config.CapabilityClaim;
import org.ghyura.cf.config.CapabilityProvider;
import org.ghyura.cf.config.CapabilityStatus;

import java.util.List;

/**
 * What generation can prove today, published through {@link java.util.ServiceLoader}: the legacy SQL intermediate,
 * final and optional tables (C02) replaced by in-memory stages and the lazy candidate assembly (M02, M03), and the
 * Python constraint layer and covering selection (part of C04, M03).
 */
public final class GenerationCapabilities implements CapabilityProvider {

    static final String C02_EVIDENCE = "M02 and M03: every sheet's stages (passes, FW_Group rewrites with the legacy policies, separators, "
            + "joiners of every multiplicity with nested and grouped operands, consumption, FW_Reuse, distinct tables) and the final "
            + "assembly (mandatory product with baseline and cut-off, optional subsets and sizes, de-duplication with legacy ids, the "
            + "empty-stage policy) run in memory, lazily or budgeted, in either legacy order; StageSemanticsTest, ReferenceModelTest, "
            + "AssemblyTest, AssemblyModelTest (a list model of the legacy assembler), BoundedGenerationTest.";
    static final String C04_EVIDENCE = "M03: the constraint sidecar (pairs, sets, when-predicates in Python semantics, mappings, "
            + "assertions, conditions, gates, orders) and legacy bonds files, evaluated as the legacy sieve and Reader filter did "
            + "(golden files from the legacy code) and greedy t-wise covering selection (golden from fwgen's reducers); fwgen spec "
            + "documents with aliases, option expansion, constraints, orders and coverage choices compile exactly as fwgen compiled "
            + "them (fw-input SpecReader, goldens and the 159-spec corpus, D57); every run reconciles its stage counts (D58); plan "
            + "gives every count with how sure it is (EXACT, BOUNDED, ESTIMATED, UNKNOWN), its formula and the tables' rows by "
            + "length from the operators' parameters, with the run class and resource estimates kept apart from reserved memory "
            + "(D59); decompose splits sources into byte-preserving pieces and the legacy decomposition spec, Java at the parser's "
            + "member boundaries and Python at its top-level statements as the legacy ast decomposition did (D62); benchmark times the "
            + "generator, sieve, executor and analyzer stages at the legacy profiles in this JVM and records the conditions that "
            + "make the figures comparable (D70).";

    @Override
    public List<CapabilityClaim> claims() {
        return List.of(new CapabilityClaim("C02", CapabilityStatus.IMPLEMENTED, C02_EVIDENCE),
                new CapabilityClaim("C04", CapabilityStatus.IMPLEMENTED, C04_EVIDENCE));
    }
}
