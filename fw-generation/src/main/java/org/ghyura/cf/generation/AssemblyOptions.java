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

import org.ghyura.cf.algebra.LegacyLibrary;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.constraints.Coverage;

import java.util.Objects;

/**
 * How candidates are assembled from the sheets' tables (work package M03): the empty-stage policy of the supplement,
 * the legacy final-table limits and de-duplication, the optional contract, and covering selection.
 *
 * @param emptyStage      what a mandatory sheet without rows does (cf.generation.emptyStagePolicy)
 * @param distinct        de-duplicate the mandatory product and the optional tables by content (cf.assembly.distinct)
 * @param mandatoryLimit  the legacy cut-off of the products (cf.assembly.mandatoryLimit); {@link Long#MAX_VALUE} means
 *                        none, the native default
 * @param optional        whether optional sheets are assembled (cf.optional.mode)
 * @param includeAbsent   whether each mandatory row also yields the candidate without optional sheets
 *                        (cf.optional.includeAbsent)
 * @param sizes           the optional combination sizes assembled (cf.optional.sizes)
 * @param producedSizes   the sizes the optional contract produces (cf.optional.producedSizes)
 * @param subsetRowLimit  an optional subset whose product of rows exceeds this is left out (cf.optional.subsetRowLimit)
 * @param library         the order of the optional subsets (the legacy Core combined optional sheets with the library of
 *                        core.threading.parallelizeSubCombosIfPossible, as its first passes; cf.generation.library)
 * @param coverageStrength t of covering selection, 0 for none (cf.coverage.strength)
 * @param coverageMethod  the greedy strategy (cf.coverage.method)
 * @param coverageBudget  the most mandatory rows a budgeted selection keeps, 0 for none (cf.coverage.budget)
 * @param coverageHardCap the largest mandatory product covering selection accepts (cf.coverage.hardCap)
 */
public record AssemblyOptions(RunConfig.EmptyStagePolicy emptyStage, boolean distinct, long mandatoryLimit,
                              RunConfig.OptionalMode optional, boolean includeAbsent, RunConfig.SizeSet sizes,
                              RunConfig.SizeSet producedSizes, long subsetRowLimit, LegacyLibrary library, int coverageStrength,
                              Coverage.Method coverageMethod, long coverageBudget, long coverageHardCap) {

    public AssemblyOptions {
        Objects.requireNonNull(emptyStage, "emptyStage");
        Objects.requireNonNull(optional, "optional");
        Objects.requireNonNull(sizes, "sizes");
        Objects.requireNonNull(producedSizes, "producedSizes");
        Objects.requireNonNull(library, "library");
        Objects.requireNonNull(coverageMethod, "coverageMethod");
        if (mandatoryLimit < 1 || subsetRowLimit < 0 || coverageStrength < 0 || coverageBudget < 0 || coverageHardCap < 1) {
            throw new IllegalArgumentException("assembly limits out of range");
        }
    }

    /** The native defaults: every row, every optional size with the absent branch, no reduction. */
    public static AssemblyOptions defaults() {
        return of(RunConfig.defaults());
    }

    public static AssemblyOptions of(RunConfig config) {
        Objects.requireNonNull(config, "config");
        return new AssemblyOptions(config.emptyStagePolicy(), config.assemblyDistinct(), config.assemblyMandatoryLimit(),
                config.optionalMode(), config.optionalIncludeAbsent(), config.optionalSizes(), config.optionalProducedSizes(),
                config.optionalSubsetRowLimit(), GenerationOptions.of(config).library(), config.coverageStrength(),
                config.coverageMethod() == RunConfig.CoverageMethod.GREEDY ? Coverage.Method.GREEDY : Coverage.Method.SET_COVER,
                config.coverageBudget(), config.coverageHardCap());
    }

    /** The same options with another empty-stage policy. */
    public AssemblyOptions withEmptyStage(RunConfig.EmptyStagePolicy policy) {
        return new AssemblyOptions(policy, distinct, mandatoryLimit, optional, includeAbsent, sizes, producedSizes, subsetRowLimit,
                library, coverageStrength, coverageMethod, coverageBudget, coverageHardCap);
    }

    /** Whether covering selection is requested. */
    public boolean coverage() {
        return coverageStrength > 0 || coverageBudget > 0;
    }

    /** A canonical text of the options that decide what a candidate is (selection options are left out: D05). */
    public String canonical() {
        return "emptyStage=" + emptyStage.token() + ";distinct=" + distinct + ";mandatoryLimit=" + mandatoryLimit + ";optional="
                + optional.token() + ";absent=" + includeAbsent + ";sizes=" + sizes.token() + ";produced=" + producedSizes.token()
                + ";subsetRowLimit=" + subsetRowLimit + ";optionalOrder=" + library;
    }
}
