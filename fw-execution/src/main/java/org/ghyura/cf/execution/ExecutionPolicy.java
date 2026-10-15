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

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Canonical;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * How every sample of a run executes: the cooperative deadline and grace, repeats and environments, attempts, the output
 * bound, the verdict field of generated candidates, the seed, the worker count, admission bounds, listener deliveries and
 * attachments.
 *
 * @param timeoutMillis    cooperative deadline per attempt ({@code cf.execution.timeout.millis})
 * @param graceMillis      how long a sample may take to stop after its deadline or a cancellation
 * @param repeats          K: repeats per candidate, each with fresh state ({@code cf.execution.repeat.count})
 * @param maxAttempts      attempts per sample, the first included; TIMEOUT and INFRA_FAIL are retried
 * @param outputMaxBytes   captured output kept per attempt
 * @param verdict          the static int field a generated candidate leaves its verdict in
 * @param seed             the run seed every sample's randomness derives from
 * @param workers          samples executed at the same time
 * @param queueItems       work items admitted ahead of the workers ({@code cf.pipeline.queue.maxItems})
 * @param queueBytes       bytes of admitted, unfinished work items ({@code cf.pipeline.queue.maxBytes})
 * @param window           candidates admitted beyond the oldest one not yet delivered ({@code cf.pipeline.ordinal.window})
 * @param environments     the environments, never empty ({@code cf.execution.environments}; '' is the default one)
 * @param repeatPolicy     how repeats meet environments ({@code cf.execution.repeat.policy})
 * @param repeatScope      whether repeats other than 0 are measurements ({@code cf.execution.repeat.scope})
 * @param serialize        one sample executes at a time ({@code cf.execution.serialize})
 * @param listenerAttempts deliveries of one observation to a failing listener ({@code cf.pipeline.listener.maxAttempts})
 * @param attach           whose candidate source is attached ({@code cf.results.attach})
 */
public record ExecutionPolicy(long timeoutMillis, long graceMillis, int repeats, int maxAttempts, int outputMaxBytes,
                              RunConfig.VerdictField verdict, long seed, int workers, int queueItems, long queueBytes, int window,
                              List<String> environments, RunConfig.RepeatPolicy repeatPolicy, RunConfig.RepeatScope repeatScope,
                              boolean serialize, int listenerAttempts, RunConfig.Attach attach) {

    public ExecutionPolicy {
        if (timeoutMillis < 1 || graceMillis < 1 || repeats < 1 || maxAttempts < 1 || outputMaxBytes < 1 || workers < 1
                || queueItems < 1 || queueBytes < 1 || window < 1 || listenerAttempts < 1) {
            throw new IllegalArgumentException("execution limits must be positive");
        }
        environments = List.copyOf(environments);
        if (environments.isEmpty() || new HashSet<>(environments).size() != environments.size()) {
            throw new IllegalArgumentException("the environments must be distinct and at least one: " + environments);
        }
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(repeatPolicy, "repeatPolicy");
        Objects.requireNonNull(repeatScope, "repeatScope");
        Objects.requireNonNull(attach, "attach");
    }

    /** One default environment, every repeat a verdict sample, no attachment, the default admission bounds. */
    public ExecutionPolicy(long timeoutMillis, long graceMillis, int repeats, int maxAttempts, int outputMaxBytes,
                           RunConfig.VerdictField verdict, long seed, int workers, int queueItems) {
        this(timeoutMillis, graceMillis, repeats, maxAttempts, outputMaxBytes, verdict, seed, workers, queueItems, 1L << 24, 256,
                List.of(""), RunConfig.RepeatPolicy.LOCAL, RunConfig.RepeatScope.ALL, false, 3, RunConfig.Attach.NONE);
    }

    public static ExecutionPolicy of(RunConfig config) {
        return new ExecutionPolicy(config.timeoutMillis(), config.cancelGraceMillis(), config.repeatCount(), config.retryMaxAttempts(),
                config.outputMaxBytes(), config.executionVerdict(), config.randomSeed(), config.workers(), config.queueMaxItems(),
                config.queueMaxBytes(), config.ordinalWindow(), config.environments(), config.repeatPolicy(), config.repeatScope(),
                config.serializeSamples(), config.listenerMaxAttempts(), config.resultsAttach());
    }

    public ExecutionPolicy withEnvironments(List<String> envs, RunConfig.RepeatPolicy policy) {
        return new ExecutionPolicy(timeoutMillis, graceMillis, repeats, maxAttempts, outputMaxBytes, verdict, seed, workers, queueItems,
                queueBytes, window, envs, policy, repeatScope, serialize, listenerAttempts, attach);
    }

    public ExecutionPolicy withScope(RunConfig.RepeatScope scope) {
        return new ExecutionPolicy(timeoutMillis, graceMillis, repeats, maxAttempts, outputMaxBytes, verdict, seed, workers, queueItems,
                queueBytes, window, environments, repeatPolicy, scope, serialize, listenerAttempts, attach);
    }

    public ExecutionPolicy withAdmission(int items, long bytes, int candidateWindow) {
        return new ExecutionPolicy(timeoutMillis, graceMillis, repeats, maxAttempts, outputMaxBytes, verdict, seed, workers, items,
                bytes, candidateWindow, environments, repeatPolicy, repeatScope, serialize, listenerAttempts, attach);
    }

    public ExecutionPolicy withDelivery(int attempts, RunConfig.Attach attachments, boolean serialized) {
        return new ExecutionPolicy(timeoutMillis, graceMillis, repeats, maxAttempts, outputMaxBytes, verdict, seed, workers, queueItems,
                queueBytes, window, environments, repeatPolicy, repeatScope, serialized, attempts, attachments);
    }

    /**
     * SHA-256 of what decides an attempt's outcome: deadline, grace, output bound, verdict field and the SUT's id and
     * version. Repeats, environments, attempts, workers and admission decide how many samples run and where, not what one
     * of them gives.
     */
    public String digest(String sutId, String sutVersion) {
        return Canonical.sha256Hex("execution-policy/1\ntimeoutMillis=" + timeoutMillis + "\ngraceMillis=" + graceMillis
                + "\noutputMaxBytes=" + outputMaxBytes + "\nverdict=" + verdict.token() + "\nsut=" + Canonical.escape(sutId)
                + "\nversion=" + Canonical.escape(sutVersion) + "\n");
    }
}
