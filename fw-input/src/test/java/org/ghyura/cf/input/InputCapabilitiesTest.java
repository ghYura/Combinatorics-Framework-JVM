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

package org.ghyura.cf.input;

import org.ghyura.cf.config.CapabilityClaim;
import org.ghyura.cf.config.CapabilityProvider;
import org.ghyura.cf.config.CapabilityStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputCapabilitiesTest {

    @Test
    void theModuleClaimsC01AndPartialC12AndNamesTheirEvidenceAndRemainingScope() {
        List<CapabilityClaim> claims = new InputCapabilities().claims();
        assertEquals(2, claims.size());
        CapabilityClaim claim = claims.getFirst();
        assertEquals("C01", claim.capabilityId());
        assertEquals(CapabilityStatus.IMPLEMENTED, claim.status());
        assertTrue(claim.evidence().contains("CorpusPlanTest") && claim.evidence().contains("ScenarioParityTest"), claim.evidence());
        assertTrue(claim.evidence().contains("RunServiceTest") && claim.evidence().contains("SessionTest"), claim.evidence());
        assertTrue(claim.evidence().contains("0064") && claim.evidence().contains("per-operation"), claim.evidence());
        CapabilityClaim local = claims.getLast();
        assertEquals("C12", local.capabilityId());
        assertEquals(CapabilityStatus.PARTIAL, local.status());
        assertTrue(local.evidence().contains("LocalTaskSnapshotTest") && local.evidence().contains("LocalTaskCheckpointTest"), local.evidence());
        assertTrue(local.evidence().contains("remain unfinished") && local.evidence().contains("no implicit"), local.evidence());
    }

    @Test
    void theClaimIsFoundThroughTheServiceLoaderLikeEveryOtherModulesClaim() {
        List<CapabilityProvider> providers = new ArrayList<>();
        ServiceLoader.load(CapabilityProvider.class).forEach(providers::add);
        assertTrue(providers.stream().anyMatch(p -> p instanceof InputCapabilities), providers.toString());
    }
}
