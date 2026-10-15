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

package org.ghyura.cf.plan;

/**
 * Where a sheet's values go in the final assembly, decided by the first FW_Exclude/FW_Heading or FW_Optional
 * flag that reaches the sheet while it is still mandatory (the legacy parser moved the sheet's values between
 * maps and a later flag found nothing left to move).
 */
public enum SheetRole {
    /** The sheet has no values (an empty or a virtual sheet), so there is nothing to place. */
    NONE,
    /** The values are combined into every candidate. */
    MANDATORY,
    /** The values form an optional axis: a candidate may or may not include them. */
    OPTIONAL,
    /** The values only feed other tables (joiners, headings); they are not a column of their own. */
    EXCLUDED
}
