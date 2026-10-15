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

import org.ghyura.cf.model.SourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * What FW_Seq says about one sheet.
 *
 * @param key            sheet key: 1..S for data sheets, S+1.. for virtual sheets
 * @param virtual        generated for a reference that named no data sheet; it has no values
 * @param role           mandatory, optional, excluded, or none (no values)
 * @param heading        flagged FW_Heading (treated like FW_Exclude by the legacy parser)
 * @param lastInQueue    flagged FW_LastInQueue while mandatory
 * @param reuse          flagged FW_Reuse, or reached by a nested joiner placeholder
 * @param reuseTableOnly flagged FW_ReuseTableOnly, or reached by a nested joiner placeholder
 * @param concatenator   the text after {@code FW_Concatenator=}, or null
 * @param directives     the directives of the last FW_Seq row that had any for this sheet
 * @param promoted       the single combination verb was repeated (the legacy auto-promotion)
 * @param valueCount     how many values the sheet has
 */
public record SheetPlan(String name, int key, boolean virtual, SheetRole role, boolean heading, boolean lastInQueue,
                        boolean reuse, boolean reuseTableOnly, String concatenator, List<Directive> directives,
                        boolean promoted, int valueCount, SourceLocation location) {

    public SheetPlan {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(location, "location");
        directives = List.copyOf(directives);
    }

    /** A sheet takes part in the generation only when FW_Seq gave it at least one directive. */
    public boolean active() {
        return !directives.isEmpty();
    }
}
