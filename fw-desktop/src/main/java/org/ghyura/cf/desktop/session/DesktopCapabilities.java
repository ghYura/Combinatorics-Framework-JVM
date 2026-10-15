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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.config.CapabilityClaim;
import org.ghyura.cf.config.CapabilityProvider;
import org.ghyura.cf.config.CapabilityStatus;

import java.util.List;

/** Desktop evidence discoverable by headless commands without loading the windowing toolkit. */
public final class DesktopCapabilities implements CapabilityProvider {

    @Override
    public List<CapabilityClaim> claims() {
        return List.of(new CapabilityClaim("C05", CapabilityStatus.IMPLEMENTED,
                "M09: Swing authoring, control sheets, plan/candidate previews, sieve, run/campaign controls, results, analysis "
                        + "and export use the shared Session facade. Constraint formulas, nested blocks, graphs, companion repair "
                        + "and mapping-entry editing through validated JSON are available; DesktopTest and SessionTest exercise these workflows. "
                        + "The scenario library is available; its release-gate entry is made fresh by an application the port does not carry (author scope). "
                        + "Real-display acceptance is recorded separately from headless tests."));
    }
}
