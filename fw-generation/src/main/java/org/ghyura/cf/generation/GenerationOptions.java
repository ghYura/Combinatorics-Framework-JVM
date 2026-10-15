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

import java.util.Objects;

/**
 * The switches that decide which rows the stages produce and in which order (supplement to revision 2, decisions
 * G6 and G7), and how FW_ReplaceRE rewrites are policed (legacy core.replace.*, work package M03).
 *
 * @param library        the generator library of the first pass and of the ungrouped later passes
 *                       (cf.generation.library; legacy core.threading.parallelizeSubCombosIfPossible: true, as each
 *                       shipped Core configuration sets it, is {@link LegacyLibrary#PARALLEL}, false or absent
 *                       {@link LegacyLibrary#COMBINATORICSLIB3}). Grouped passes always used combinatoricslib3 and
 *                       the FW_Permut duplicate marker always the parallel multiset generator, whatever this says.
 * @param distinctTables remove repeated rows from a sheet's table after every combination directive and joiner and
 *                       from its final table, keeping the first (cf.generation.distinctTables; legacy
 *                       distinctify__fwX_Y__Tables)
 * @param replace        the FW_ReplaceRE policies (cf.generation.replace.*; legacy core.replace.*)
 */
public record GenerationOptions(LegacyLibrary library, boolean distinctTables, ReplacePolicies replace) {

    /**
     * The FW_ReplaceRE policies of the legacy Core (AppConfig: core.replace.patternPolicy, unmatchedPolicy,
     * unparseablePolicy, identityPolicy, diagnostics); {@link #LEGACY} is the historical behaviour.
     */
    public record ReplacePolicies(RunConfig.ReplacePattern pattern, RunConfig.ReplaceUnmatched unmatched,
                                  RunConfig.ReplaceUnparseable unparseable, RunConfig.ReplaceIdentity identity,
                                  RunConfig.ReplaceDiagnostics diagnostics) {
        public static final ReplacePolicies LEGACY = new ReplacePolicies(RunConfig.ReplacePattern.PERMISSIVE,
                RunConfig.ReplaceUnmatched.IGNORE, RunConfig.ReplaceUnparseable.WARN, RunConfig.ReplaceIdentity.ALLOW,
                RunConfig.ReplaceDiagnostics.OFF);

        public ReplacePolicies {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(unmatched, "unmatched");
            Objects.requireNonNull(unparseable, "unparseable");
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(diagnostics, "diagnostics");
        }

        String canonical() {
            return pattern.token() + "," + unmatched.token() + "," + unparseable.token() + "," + identity.token() + "," + diagnostics.token();
        }
    }

    public GenerationOptions {
        Objects.requireNonNull(library, "library");
        Objects.requireNonNull(replace, "replace");
    }

    /** The given library and de-duplication with the legacy replacement policies. */
    public GenerationOptions(LegacyLibrary library, boolean distinctTables) {
        this(library, distinctTables, ReplacePolicies.LEGACY);
    }

    /** This build's defaults: the parallel library's order, no de-duplication, the legacy replacement policies. */
    public static GenerationOptions defaults() {
        return new GenerationOptions(LegacyLibrary.PARALLEL, false);
    }

    /** The options a resolved configuration asks for. */
    public static GenerationOptions of(RunConfig config) {
        Objects.requireNonNull(config, "config");
        LegacyLibrary library = switch (config.generationLibrary()) {
            case PARALLEL -> LegacyLibrary.PARALLEL;
            case COMBINATORICSLIB3 -> LegacyLibrary.COMBINATORICSLIB3;
        };
        return new GenerationOptions(library, config.generationDistinctTables(), new ReplacePolicies(config.replacePattern(),
                config.replaceUnmatched(), config.replaceUnparseable(), config.replaceIdentity(), config.replaceDiagnostics()));
    }

    /** A canonical text of every option, for identities. */
    public String canonical() {
        return "library=" + library + ";distinctTables=" + distinctTables + ";replace=" + replace.canonical();
    }
}
