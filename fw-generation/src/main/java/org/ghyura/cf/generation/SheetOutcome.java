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

import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.plan.SheetRole;

import java.util.Objects;

/**
 * What one sheet's stages produced: its final table (the legacy fw2_ table when its last directive wrote one,
 * otherwise fw_), and whether a joiner consumed it. A consumed sheet takes no part in the final product (the legacy
 * handler removed it from mapTable2combs); its rows stay readable to later joiners only with FW_Reuse. The table is
 * valid until the generation result is closed.
 *
 * @param consumedBy the sheet whose joiner consumed this one, or null
 */
public record SheetOutcome(String sheet, int key, SheetRole role, RowSource rows, String consumedBy) {

    public SheetOutcome {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(rows, "rows");
    }

    /** Whether the sheet produced no rows. */
    public boolean empty() {
        return rows.size().signum() == 0;
    }
}
