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

package org.ghyura.cf.results;

import org.ghyura.cf.config.CapabilityClaim;
import org.ghyura.cf.config.CapabilityProvider;
import org.ghyura.cf.config.CapabilityStatus;

import java.util.List;

/** What the result repository can prove today (M07): the legacy results database (C09) as an in-memory repository. */
public final class ResultCapabilities implements CapabilityProvider {

    static final String C09_EVIDENCE = "M04: one immutable observation per five-field sample key (identical replay harmless, a "
            + "conflicting one an error), every attempt kept under full retention, the highest attempt final per slot, exact separate totals, commits "
            + "reserved from the memory budget and bounded by cf.results.maxSamples. M05: the latest attempt per repeat and "
            + "environment, measurements counted apart from verdict samples, candidate-source attachments (failed, passed, all) "
            + "within cf.results.maxAttachmentBytes, the legacy metrics corpus. M07: every committed attempt exported as a legacy "
            + "results_v2 row (export --kind results), the final samples as a metrics table, quiescent checkpoints and exact resume. "
            + "M07 follow-up: aggregate-only retention folds exact final-slot and retry totals, retains replay protection through "
            + "delivery and releases observations afterward. Distinct verdict counters and active attempts consume the memory budget. "
            + "Attachments are omitted; analysis, seeds, campaigns and exact checkpoints require full retention. Historical "
            + "queries and arbitrary late replay are unavailable in aggregate mode.";

    @Override
    public List<CapabilityClaim> claims() {
        return List.of(new CapabilityClaim("C09", CapabilityStatus.IMPLEMENTED, C09_EVIDENCE));
    }
}
