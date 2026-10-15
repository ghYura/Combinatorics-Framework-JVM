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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;

import java.math.BigInteger;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * What a finished run did, with every total kept apart (architecture: candidates, repeat samples, attempts, skipped and
 * excluded are separate figures).
 *
 * @param run             the run
 * @param state           COMPLETED, CANCELLED or FAILED
 * @param scenario        the scenario's source name
 * @param scenarioDigest  the scenario's canonical digest (empty when it never got that far)
 * @param planDigest      the plan's digest (empty when planning failed)
 * @param sut             the plugin id or generated-java
 * @param sutVersion      its version
 * @param policyDigest    the execution policy's digest
 * @param countsExact     whether the candidate counts below are exact (the walk stayed within its scan limit)
 * @param generated       candidates generated
 * @param excluded        candidates the rules removed (early and late)
 * @param unselected      candidates covering selection left out
 * @param executable      candidates left to execute
 * @param admitted        candidates admitted to execution (fewer than executable when the run stopped early)
 * @param samples         samples with a final observation (candidates x repeats that ran or were cancelled)
 * @param attempts        attempts committed, retries included
 * @param retried         samples whose final observation is a retry
 * @param outcomes        final outcome of each sample, every outcome listed
 * @param verdicts        final domain verdict codes and how often each occurred
 * @param verdictNames    the names of verdict codes, from the plugin or the FW_CUSTOM_VAR sheet
 * @param abandoned       samples whose thread did not stop when asked and was left running
 * @param stillRunning    names of such threads that were still alive at the end
 * @param compilations    generated sources compiled (cache misses); 0 for a typed plugin
 * @param cacheHits       compilations answered by the bytecode cache
 * @param elapsedMillis   wall time of the run
 * @param diagnostics     findings, warnings and, for a failed run, its errors
 * @param execution       how the samples were spread over environments and repeats, and what the run held (M05)
 * @param analysis        the analysis of the final observations, when cf.analysis.enabled asked for it (M06)
 * @param seed            the winners of a completed analysed run with their sheet selections, when a seed was wanted
 *                        (cf.campaign.seed.output set, or a campaign of more than one iteration; M07)
 * @param stop            why admission ended, once per run (AN-10)
 */
public record RunSummary(RunId run, RunState state, String scenario, String scenarioDigest, String planDigest, String sut,
                         String sutVersion, String policyDigest, boolean countsExact, BigInteger generated, BigInteger excluded,
                         BigInteger unselected, BigInteger executable, long admitted, long samples, long attempts, long retried,
                         Map<Outcome, Long> outcomes, Map<Integer, Long> verdicts, Map<Integer, String> verdictNames, long abandoned,
                         List<String> stillRunning, long compilations, long cacheHits, long elapsedMillis, List<Diagnostic> diagnostics,
                         Execution execution, java.util.Optional<org.ghyura.cf.analysis.AnalysisReport> analysis,
                         java.util.Optional<org.ghyura.cf.analysis.BundleSeed> seed, Stop stop) {

    public RunSummary {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(state, "state");
        EnumMap<Outcome, Long> all = new EnumMap<>(Outcome.class);
        for (Outcome o : Outcome.values()) {
            all.put(o, outcomes.getOrDefault(o, 0L));
        }
        outcomes = Collections.unmodifiableMap(all);
        verdicts = Collections.unmodifiableMap(new TreeMap<>(verdicts));
        verdictNames = Collections.unmodifiableMap(new TreeMap<>(verdictNames));
        stillRunning = List.copyOf(stillRunning);
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(stop, "stop");
    }

    /**
     * How the samples were spread and what the run held (work package M05).
     *
     * @param environments        the environments, '' being the default one
     * @param repeatPolicy        local, disperse or nested
     * @param repeatScope         all, or metrics (repeat 0 the verdict sample, the other repeats measurements)
     * @param measurements        measurement samples, counted apart from the verdict samples
     * @param measurementOutcomes their final outcomes, every outcome listed
     * @param attachmentsKept     candidate sources kept as attachments
     * @param attachmentBytes     their UTF-8 bytes
     * @param attachmentsOmitted  attachments omitted by retention or cf.results.maxAttachmentBytes
     * @param deliveries          committed observations delivered to the run's listener
     * @param listenerRetries     deliveries repeated after the listener threw
     * @param listenerFailures    observations the listener never took
     * @param peakQueuedBytes     the most bytes admitted work held at once
     * @param peakWindow          the most candidates admitted beyond the oldest one not yet delivered
     * @param budgetHighWater     the most bytes reserved from the run's memory budget at once
     * @param budgetBytes         that budget (cf.memory.budget.bytes)
     * @param budgetExhausted     whether the run's time budget (cf.run.budget.millis) ended it
     * @param retention           full (every attempt) or aggregate-only (exact totals without retained history)
     * @param admittedSamples     distinct admitted sample slots, measurements included; a restored slot counts once even
     *                            when a stop prevents this engine from revisiting it
     * @param restoredAttempts    committed attempts restored from a checkpoint (delivered by the run that made it)
     * @param axes                per data sheet the candidates read, how many of its values the admitted candidates held
     */
    public record Execution(List<String> environments, String repeatPolicy, String repeatScope, long measurements,
                            Map<Outcome, Long> measurementOutcomes, long attachmentsKept, long attachmentBytes, long attachmentsOmitted,
                            long deliveries, long listenerRetries, long listenerFailures, long peakQueuedBytes, long peakWindow,
                            long budgetHighWater, long budgetBytes, boolean budgetExhausted, String retention, long admittedSamples,
                            long restoredAttempts, List<Axis> axes) {

        public Execution {
            axes = List.copyOf(axes);
            environments = List.copyOf(environments);
            EnumMap<Outcome, Long> all = new EnumMap<>(Outcome.class);
            for (Outcome o : Outcome.values()) {
                all.put(o, measurementOutcomes.getOrDefault(o, 0L));
            }
            measurementOutcomes = Collections.unmodifiableMap(all);
        }
    }

    public long count(Outcome outcome) {
        return outcomes.get(outcome);
    }

    /** The final verdict counts by name (the code itself when it has none). */
    public Map<String, Long> verdictsByName() {
        Map<String, Long> named = new java.util.LinkedHashMap<>();
        verdicts.forEach((code, n) -> named.put(verdictNames.getOrDefault(code, Integer.toString(code)), n));
        return named;
    }

    /**
     * How many of a data sheet's values the admitted candidates held.
     *
     * @param sheet       the data sheet
     * @param declared    its values
     * @param exercised   how many of them an admitted candidate held
     * @param unexercised the first few values none held (shortened)
     */
    public record Axis(String sheet, int declared, int exercised, List<String> unexercised) {
        public Axis {
            Objects.requireNonNull(sheet, "sheet");
            unexercised = List.copyOf(unexercised);
        }
    }

    /** The run's stage invariants checked against its totals (GEN:reconciliation). */
    public Reconciliation reconciliation() {
        return Reconciliation.of(this);
    }

    /**
     * Why admission ended (AN-10).
     *
     * @param reason   the one reason
     * @param boundary for a stop-policy decision, the candidates in candidate order up to the one that decided it
     *                 (independent of workers and timing; -1 when restored observations decided it or for reasons that
     *                 are not decisions about candidates); for EXHAUSTED the admitted candidates
     * @param admitted the candidates admitted, including those admitted before the decision was known
     * @param detail   what decided it, in words
     */
    public record Stop(StopPolicy.Reason reason, long boundary, long admitted, String detail) {
        public Stop {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
