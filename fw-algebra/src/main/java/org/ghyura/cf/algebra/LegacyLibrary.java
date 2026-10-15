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

package org.ghyura.cf.algebra;

/**
 * The two generator libraries the legacy Core used, whose orders and empty-input results differ. The Core chose
 * per pass: the first and the ungrouped later passes used {@link #PARALLEL} when
 * core.threading.parallelizeSubCombosIfPossible was true (each shipped Core configuration sets it) and
 * {@link #COMBINATORICSLIB3} otherwise; a grouped pass (after FW_Group) always used {@link #COMBINATORICSLIB3}; the
 * FW_Permut duplicate marker always used the parallel multiset generator. The orders were read from the libraries
 * themselves (fixtures legacy-orders.xml).
 */
public enum LegacyLibrary {
    /** org.ghYura.combinatorics3parallel 3.4.2-SNAPSHOT: element-by-index generators. */
    PARALLEL,
    /** org.paukov combinatoricslib3 3.3.3. */
    COMBINATORICSLIB3
}
