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
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.config.ConfigEntry;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.spi.SutPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The one way to run a scenario, shared by the command line, the desktop and embedding: prepare a run from a scenario and
 * an effective configuration, then start it through its {@link RunHandle}. Preparing executes nothing; it refuses pending
 * legacy settings (R-RUN-GATE) and an unknown SUT before anything runs.
 */
public final class RunService {

    private RunService() {
    }

    /**
     * @throws org.ghyura.cf.model.FrameworkException CONFIG_LEGACY_PENDING for each pending legacy setting, CONFIG_SUT_UNKNOWN or
     *                                                CONFIG_SUT_AMBIGUOUS when cf.execution.sut names no single plugin
     */
    public static RunHandle prepare(ScenarioSpec scenario, EffectiveConfig effective) {
        return prepare(scenario, effective, null);
    }

    /**
     * Prepares the rest of a run from one of its checkpoints (decision M07-D4): once planned, the run must have the
     * checkpoint's identity (scenario, plan, key dictionary, policy, SUT and configuration); its committed observations are
     * restored and only the samples without a final observation run, a cancelled one as its next attempt.
     *
     * @throws FrameworkException CHECKPOINT_MISMATCH when the checkpoint names another run; the identity of the plan is
     *                            compared when the run starts, and a mismatch there ends it FAILED with nothing run
     */
    public static RunHandle resume(ScenarioSpec scenario, EffectiveConfig effective, Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (effective.config().resultsRetention() == RunConfig.ResultsRetention.AGGREGATE_ONLY) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT,
                    "checkpoint resume needs full retention", SourceLocation.NONE));
        }
        if (!checkpoint.runId().equals(effective.config().runId())) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CHECKPOINT_MISMATCH, "the checkpoint belongs to run '"
                    + checkpoint.runId() + "', not to '" + effective.config().runId() + "'; nothing was resumed", SourceLocation.NONE));
        }
        return prepare(scenario, effective, checkpoint);
    }

    private static RunHandle prepare(ScenarioSpec scenario, EffectiveConfig effective, Checkpoint restore) {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(effective, "effective");
        PendingSettings.refuseIfAny(effective);
        RunConfig config = effective.config();
        RunId id = new RunId(config.runId());
        SutResolver resolver = new SutResolver(config.executionClasspath());
        try {
            SutPlugin plugin = config.executionSut().equals(RunConfig.GENERATED_JAVA) ? null : resolver.plugin(config.executionSut());
            RunHandle handle = new RunHandle(id, scenario, null, config, resolver, plugin);
            handle.identity(configuration(effective), restore);
            return handle;
        } catch (RuntimeException e) {
            resolver.close();
            throw e;
        }
    }

    /** The effective configuration a checkpoint records: every key with its raw value, secrets left out. */
    public static Map<String, String> configuration(EffectiveConfig effective) {
        Map<String, String> out = new TreeMap<>();
        for (ConfigEntry e : effective.entries()) {
            if (!e.secret()) {
                out.put(e.key(), e.rawValue());
            }
        }
        return out;
    }

    /** The declared SUT version without creating a session or executing a candidate. */
    static String sutVersion(EffectiveConfig effective) {
        RunConfig config = effective.config();
        if (config.executionSut().equals(RunConfig.GENERATED_JAVA)) return "jdk-" + Runtime.version().feature();
        SutResolver resolver = new SutResolver(config.executionClasspath());
        try { return resolver.plugin(config.executionSut()).version(); }
        finally { resolver.close(); }
    }

    /**
     * Prepares a run of candidates that were not generated here, such as an imported legacy hand-over, under the same
     * gate: pending legacy settings and an unknown SUT are refused before anything runs.
     */
    public static RunHandle prepare(ImportedCandidates imported, EffectiveConfig effective) {
        Objects.requireNonNull(imported, "imported");
        Objects.requireNonNull(effective, "effective");
        PendingSettings.refuseIfAny(effective);
        RunConfig config = effective.config();
        RunId id = new RunId(config.runId());
        SutResolver resolver = new SutResolver(config.executionClasspath());
        try {
            SutPlugin plugin = config.executionSut().equals(RunConfig.GENERATED_JAVA) ? null : resolver.plugin(config.executionSut());
            return new RunHandle(id, null, imported, config, resolver, plugin);
        } catch (RuntimeException e) {
            resolver.close();
            throw e;
        }
    }
}
