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

import java.util.List;

/**
 * What the input side of the framework can prove today (work package M01), published through
 * {@link java.util.ServiceLoader}. A claim names the tests that justify it; anything not proven stays
 * NOT_IMPLEMENTED in the capability report.
 */
public final class InputCapabilities implements CapabilityProvider {

    /** C01 is claimed once for input, planning, dictionaries and their ownership by a run. */
    static final String C01_EVIDENCE = "M01: scenarios in XLSX, JSON, YAML, TOML and Java-builder form are read into one normalized, "
            + "source-located spec (fw-input), FW_Seq is parsed with every reference resolved before anything runs (fw-plan), and the "
            + "key dictionary with census, encoding choice and memory budget exists (fw-store); proven on the 32 reference workbooks "
            + "by CorpusPlanTest and ScenarioParityTest. RunHandle owns the dictionary and rendering lifecycle and keeps sheet maps, "
            + "exit codes and templates in its immutable scenario; RunServiceTest proves generated execution and lifecycle cleanup, "
            + "and SessionTest proves that concurrent sessions keep their policies and seeds separate. This replaces the core DB "
            + "metadata. 0064 closes the delivered per-operation A10 admission contract, with caller-owned retained data and "
            + "arbitrary SUT allocations outside a global heap quota.";

    @Override
    public List<CapabilityClaim> claims() {
        return List.of(new CapabilityClaim("C01", CapabilityStatus.IMPLEMENTED, C01_EVIDENCE),
                new CapabilityClaim("C12", CapabilityStatus.PARTIAL,
                        "Immutable typed tasks, exact local Stub banks and ordered scripted proposals freeze with provider/version/digest/seed provenance; "
                        + "bounded checksummed snapshots lower through SpecReader and replay without a provider. LocalProposalProvidersTest, "
                        + "LocalTaskSnapshotTest and LocalTaskCheckpointTest prove priority, ordering, identity and resume refusal. "
                        + "The explicit fixture addon supplies original quote/parser/guard recipes: 992 native outcomes and original emitted fields "
                        + "match independent predictions in PackagedApplicationIT. Broader red-team matrices, task families, local model math "
                        + "and library workflows remain unfinished; no implicit remote/native inference fallback."));
    }
}
