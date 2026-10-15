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

package org.ghyura.cf.artifacts;

import org.ghyura.cf.config.CapabilityClaim;
import org.ghyura.cf.config.CapabilityProvider;
import org.ghyura.cf.config.CapabilityStatus;

import java.util.List;

/** What the optional artifacts can prove today (M07): the legacy file checkpoints and reports (C18). */
public final class ArtifactCapabilities implements CapabilityProvider {

    static final String C18_EVIDENCE = "M07: quiescent checkpoints (run --checkpoint PATH: at a drained pause and at the end of a run, "
            + "XML cf-checkpoint schema 1 with a payload SHA-256, replaced only once complete so a failed write keeps the last good "
            + "one); resume --checkpoint FILE validates scenario, plan, key dictionary, policy, SUT and configuration, restores every "
            + "committed observation and runs only the missing samples, a cancelled one as its next attempt; export --kind report, "
            + "results (legacy results_v2 rows) or csv from a checkpoint; the run report (run --out); the campaign seed "
            + "(cf.campaign.seed.output, BundleSeed schema 1). M07 follow-up: campaign barriers and drained iteration pauses "
            + "write cf-campaign-checkpoint schema 1 with payload SHA-256 and atomic replacement. Resume restores complete prior "
            + "reports, lineage, seed and derivation state and continues a paused child run; the CLI dispatches both checkpoint roots. "
            + "A cancellation preserves the last resumable barrier or pause.";

    @Override
    public List<CapabilityClaim> claims() {
        return List.of(new CapabilityClaim("C18", CapabilityStatus.IMPLEMENTED, C18_EVIDENCE));
    }
}
