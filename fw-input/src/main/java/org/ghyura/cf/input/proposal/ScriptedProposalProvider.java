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

package org.ghyura.cf.input.proposal;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.proposal.ProposalProvider;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;

/** Explicit slot response data; neither an interpreter nor a fallback to another provider. */
public final class ScriptedProposalProvider implements ProposalProvider {
    private final Identity identity;
    private final Map<String, List<String>> responses;
    public ScriptedProposalProvider(String id, String version, Map<String, List<String>> responses, long maxBytes) {
        Objects.requireNonNull(responses);
        if (maxBytes < 0) throw new IllegalArgumentException("negative script byte allowance");
        long estimate = 256;
        if (estimate > maxBytes) throw new IllegalArgumentException("script byte allowance exceeded");
        for (var entry : responses.entrySet()) {
            String slot = Objects.requireNonNull(entry.getKey());
            if (slot.isBlank()) throw new IllegalArgumentException("blank script slot");
            estimate = admit(estimate, slot, maxBytes);
            for (String value : Objects.requireNonNull(entry.getValue()))
                estimate = admit(estimate, Objects.requireNonNull(value), maxBytes);
        }
        Map<String,List<String>> copied = new TreeMap<>();
        responses.forEach((key, values) -> copied.put(key, List.copyOf(values)));
        this.responses = Map.copyOf(copied);
        identity = new Identity(id, version, Canonical.sha256Hex(Json.write(copied)));
    }
    private static long admit(long prior, String text, long limit) {
        long bytes = Math.addExact(64L, Math.multiplyExact(32L, text.length()));
        if (prior > limit || bytes > limit-prior) throw new IllegalArgumentException("script byte allowance exceeded");
        return prior+bytes;
    }
    @Override public Identity identity() { return identity; }
    @Override public List<String> propose(Request request) {
        Objects.requireNonNull(request);
        List<String> values = responses.get(request.slot());
        if (values == null) throw new IllegalArgumentException("no scripted response for slot: " + request.slot());
        if (request.count() > values.size()) throw new IllegalArgumentException("scripted response exhausted: " + request.slot());
        return List.copyOf(values.subList(0, request.count()));
    }
}
