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

package org.ghyura.cf.execution;

import org.ghyura.cf.config.CapabilityClaim;
import org.ghyura.cf.config.CapabilityProvider;
import org.ghyura.cf.config.CapabilityStatus;

import java.util.List;

/**
 * What execution can prove today (M05): the legacy executor's dynamic Java (C06) as in-memory compilation, typed plugins and
 * the explicit import of legacy hand-overs, and the hand-over between generation and execution (C03) as candidates passed
 * in memory.
 */
public final class ExecutionCapabilities implements CapabilityProvider {

    static final String C06_EVIDENCE = "M04: generated candidates compile in memory with the JDK compiler (bytecode cache, a fresh class "
            + "loader per sample, VM-stopping calls refused) and run through main with FW_VAR or FW_CUSTOM_VAR read exactly; typed "
            + "SUT plugins through ServiceLoader; seven outcomes with cooperative deadlines (an uncooperative sample is reported and "
            + "left running, never killed); repeats with fresh state and retries of TIMEOUT and INFRA_FAIL. M05: repeats over "
            + "environments (local, disperse, nested), the metrics repeat scope, deterministic ordinal windows with ordered "
            + "delivery, byte-bounded admission, one sample at a time on request, the app= metrics line, and the explicit, "
            + "fail-closed import of legacy bundle.handoff/v2 loose-files hand-overs (run --legacy-handoff). Python candidates "
            + "(C07) and sandboxes (C08) stay outside, refused by name.";
    static final String C03_EVIDENCE = "M04: assembled candidates go from generation to execution in memory, with their selections and "
            + "rendered sources; no COPY, JDBC, handshake file, shard or gRPC hop exists. RunServiceTest proves generated execution; "
            + "LegacyHandoffTest proves explicit bundle.handoff/v2 loose-files import and rejection of unsupported handovers. "
            + "0111: candidates --export writes the rendered candidates as a bundle.handoff/v2 hand-over, one file per candidate or "
            + "compressed shards in the legacy ShardSink format, with the legacy manifest (HandoffExportTest); an exported "
            + "hand-over runs again through run --legacy-handoff with the scenario's outcomes (CandidatesExportTest). gRPC stays "
            + "out: it is a network hop.";

    @Override
    public List<CapabilityClaim> claims() {
        return List.of(new CapabilityClaim("C06", CapabilityStatus.IMPLEMENTED, C06_EVIDENCE),
                new CapabilityClaim("C03", CapabilityStatus.IMPLEMENTED, C03_EVIDENCE));
    }
}
