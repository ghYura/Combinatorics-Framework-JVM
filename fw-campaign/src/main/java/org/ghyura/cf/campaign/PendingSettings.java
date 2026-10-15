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

import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;

import java.util.ArrayList;
import java.util.List;

/**
 * Decision R-RUN-GATE: a run refuses to start while an imported legacy setting that changes behaviour is still pending
 * (its semantics belong to a later work package). Each one is named with the package that implements it and the remedy;
 * validation and planning only report them. Retired and inert settings are not pending.
 */
final class PendingSettings {

    private PendingSettings() {
    }

    static void refuseIfAny(EffectiveConfig effective) {
        LegacyImport legacy = effective.legacyImport().orElse(null);
        if (legacy == null || legacy.pending().isEmpty()) {
            return;
        }
        List<Diagnostic> refusals = new ArrayList<>();
        for (LegacyImport.Pending p : legacy.pending()) {
            refusals.add(Diagnostic.error(ErrorCode.CONFIG_LEGACY_PENDING, "the run refuses the legacy setting '" + p.legacyKey()
                            + "': its behaviour arrives with work package " + p.owner() + " (" + p.reason() + "). Remedy: remove it from "
                            + "the imported file, or run validate or plan, which only report it", p.where(), "key", p.legacyKey(),
                    "owner", p.owner()));
        }
        throw new FrameworkException(refusals);
    }
}
