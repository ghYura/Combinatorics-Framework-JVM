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

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Observation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A quiescent checkpoint of a run (work package M07, decision M07-D4): what identifies the run, so that a resume refuses
 * anything else, and every committed observation and attachment. A run writes one when it is paused and drained, and when
 * it ends; never while a sample is in flight.
 *
 * @param runId            the run
 * @param state            the run's state when it was written: PAUSED, or how it ended
 * @param scenarioPath     the scenario file a resume reads again
 * @param scenarioDigest   the scenario's canonical digest
 * @param planDigest       the plan's digest
 * @param dictionaryDigest the key dictionary's identity (labels, encoding, window)
 * @param policyDigest     the execution policy's digest
 * @param plugin           the SUT plugin id, or generated-java
 * @param pluginVersion    its version
 * @param configuration    the effective configuration without secrets, key to raw value, in key order
 * @param observations     every committed attempt, in commit order
 * @param attachments      every kept attachment, in commit order
 */
public record Checkpoint(String runId, String state, String scenarioPath, String scenarioDigest, String planDigest, String dictionaryDigest,
                         String policyDigest, String plugin, String pluginVersion, Map<String, String> configuration,
                         List<Observation> observations, List<ResultRepository.Attachment> attachments) {

    public Checkpoint {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(scenarioPath, "scenarioPath");
        Objects.requireNonNull(scenarioDigest, "scenarioDigest");
        Objects.requireNonNull(planDigest, "planDigest");
        Objects.requireNonNull(dictionaryDigest, "dictionaryDigest");
        Objects.requireNonNull(policyDigest, "policyDigest");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        configuration = Collections.unmodifiableMap(new TreeMap<>(configuration));
        observations = List.copyOf(observations);
        attachments = List.copyOf(attachments);
    }

    /** SHA-256 of the recorded configuration. */
    public String configDigest() {
        return configDigest(configuration);
    }

    /** SHA-256 of a configuration, one {@code key=value} line per key in key order. */
    public static String configDigest(Map<String, String> configuration) {
        StringBuilder text = new StringBuilder("cf.checkpoint.config/1\n");
        new TreeMap<>(configuration).forEach((k, v) -> text.append(Canonical.escape(k)).append('=').append(Canonical.escape(v)).append('\n'));
        return Canonical.sha256Hex(text.toString());
    }

    /**
     * What differs between this checkpoint's identity and a run about to resume from it, in words; empty when nothing does.
     */
    public List<String> mismatches(String scenario, String plan, String dictionary, String policy, String pluginId, String version,
                                   String configurationDigest) {
        List<String> out = new java.util.ArrayList<>();
        differs(out, "scenario", scenarioDigest, scenario);
        differs(out, "plan", planDigest, plan);
        differs(out, "key dictionary (labels, encoding, window)", dictionaryDigest, dictionary);
        differs(out, "execution policy", policyDigest, policy);
        differs(out, "SUT", plugin + " " + pluginVersion, pluginId + " " + version);
        differs(out, "configuration", configDigest(), configurationDigest);
        return out;
    }

    private static void differs(List<String> out, String what, String recorded, String now) {
        if (!recorded.equals(now)) {
            out.add(what + " (checkpoint " + abbreviate(recorded) + ", now " + abbreviate(now) + ")");
        }
    }

    private static String abbreviate(String s) {
        return s.matches("[0-9a-f]{64}") ? s.substring(0, 12) + "…" : s;
    }
}
