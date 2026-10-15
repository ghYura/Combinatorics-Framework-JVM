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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.model.SampleSlot;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.store.MemoryBudget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The run's committed observations (architecture D03, A08). Full retention keeps every attempt for the run's life;
 * aggregate retention keeps active attempts through delivery, then retains only exact counters. Historical queries and
 * arbitrary late replays require full retention. Distinct verdict counters are charged to the memory budget.
 *
 * <ul>
 *   <li>A commit of an observation equal to the committed one is an identical replay and changes nothing; a different
 *       one under the same key is RESULT_CONFLICT and the committed one stays.</li>
 *   <li>Every attempt is kept; the final observation of a slot (run, candidate, repeat, environment) is its highest
 *       attempt.</li>
 *   <li>Totals count attempts, slots, final outcomes and final verdict codes separately and exactly.</li>
 *   <li>Every commit is reserved from the run's memory budget before it is stored, and the repository refuses more than
 *       {@code cf.results.maxSamples} observations with RESOURCE_LIMIT.</li>
 *   <li>Under the metrics repeat scope a slot with repeat index 0 is the verdict sample and the other repeats are
 *       measurements; totals count the two apart (legacy repeatScope=metrics: one verdict per candidate, K measurements).</li>
 *   <li>Attachments (candidate sources) are kept beside the observations within {@code cf.results.maxAttachmentBytes};
 *       one that does not fit is omitted and counted, and its observation is untouched.</li>
 * </ul>
 *
 * Commits are serialized; readers get snapshots. Counters and listeners are updated by the caller only after
 * {@link #commit} returned, so nothing is published before it is stored.
 */
public final class ResultRepository implements AutoCloseable {

    /** What a commit did. */
    public enum CommitResult {
        INSERTED,
        IDENTICAL_REPLAY
    }

    /** Whether every repeat is a verdict sample ({@code ALL}) or repeat 0 alone, the others measurements ({@code METRICS}). */
    public enum Scope {
        ALL,
        METRICS
    }

    /** Aggregate retention keeps replay/conflict protection through delivery, then drops the slot's history. */
    public enum Retention { FULL, AGGREGATE_ONLY }

    /** A kept attachment: the candidate source (or another text) of one committed attempt. */
    public record Attachment(SampleKey key, String name, String text) {
        public Attachment {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(text, "text");
        }

        /** Its size in UTF-8 bytes, what cf.results.maxAttachmentBytes counts. */
        public long bytes() {
            return utf8Length(text);
        }
    }

    private static final long CHUNK = 64 * 1024;

    private final RunId run;
    private final long maxSamples;
    private final MemoryBudget budget;
    private final List<MemoryBudget.Lease> leases = new ArrayList<>();
    private final Map<SampleKey, Observation> byKey = new HashMap<>();
    private final Map<SampleSlot, Observation> latest = new LinkedHashMap<>();
    private final List<Observation> commitOrder = new ArrayList<>();
    private final Scope scope;
    private final Retention retention;
    private final Map<SampleSlot, List<MemoryBudget.Lease>> activeLeases = new HashMap<>();
    private final Map<SampleSlot, List<SampleKey>> activeKeys = new HashMap<>();
    private final java.util.Set<SampleSlot> folded = new java.util.HashSet<>();
    private final EnumMap<Outcome, Long> foldedOutcomes = new EnumMap<>(Outcome.class);
    private final EnumMap<Outcome, Long> foldedMeasurements = new EnumMap<>(Outcome.class);
    private final TreeMap<Integer, Long> foldedVerdicts = new TreeMap<>();
    private long aggregateAttempts;
    private long aggregateSamples;
    private long aggregateRetried;
    private long aggregateMeasurements;
    private Totals closedTotals;
    private final long maxAttachmentBytes;
    private final Map<SampleKey, List<Attachment>> attachments = new HashMap<>();
    private long attachmentBytes;
    private long attachmentsKept;
    private long attachmentsOmitted;
    private long attachmentBytesOmitted;
    private long reserved;
    private long used;
    private boolean closed;

    public ResultRepository(RunId run, long maxSamples, MemoryBudget budget) {
        this(run, maxSamples, budget, Scope.ALL, 0);
    }

    /**
     * @param scope              whether repeats other than 0 are measurements
     * @param maxAttachmentBytes the UTF-8 bytes of attachments kept at most; 0 keeps none
     */
    public ResultRepository(RunId run, long maxSamples, MemoryBudget budget, Scope scope, long maxAttachmentBytes) {
        this(run, maxSamples, budget, scope, maxAttachmentBytes, Retention.FULL);
    }

    public ResultRepository(RunId run, long maxSamples, MemoryBudget budget, Scope scope, long maxAttachmentBytes, Retention retention) {
        this.run = Objects.requireNonNull(run, "run");
        if (maxSamples < 1) {
            throw new IllegalArgumentException("maxSamples must be positive: " + maxSamples);
        }
        if (maxAttachmentBytes < 0) {
            throw new IllegalArgumentException("maxAttachmentBytes must not be negative: " + maxAttachmentBytes);
        }
        this.maxSamples = maxSamples;
        this.budget = Objects.requireNonNull(budget, "budget");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.retention = Objects.requireNonNull(retention, "retention");
        this.maxAttachmentBytes = maxAttachmentBytes;
        if (retention == Retention.AGGREGATE_ONLY) {
            MemoryBudget.Lease lease = budget.reserve(1024, "aggregate counters");
            leases.add(lease); reserved = lease.bytes();
        }
    }

    public Retention retention() { return retention; }

    /** Whether the slot of this key is a measurement rather than a verdict sample. */
    public boolean isMeasurement(SampleKey key) {
        return scope == Scope.METRICS && key.repeatIndex() > 0;
    }

    public RunId run() {
        return run;
    }

    /**
     * Stores an observation, or recognises it as a replay of the stored one.
     *
     * @throws FrameworkException RESULT_CONFLICT for a different observation under a committed key, RESOURCE_LIMIT when
     *                            the capacity or the memory budget is exhausted
     */
    public synchronized CommitResult commit(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        if (closed) {
            throw new IllegalStateException("the result repository of run " + run + " is closed");
        }
        SampleKey key = observation.key();
        if (!key.runId().equals(run)) {
            throw new IllegalArgumentException("observation of run " + key.runId() + " offered to run " + run);
        }
        Observation existing = byKey.get(key);
        if (existing != null) {
            if (existing.equals(observation)) {
                return CommitResult.IDENTICAL_REPLAY;
            }
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_CONFLICT, "sample " + describe(key) + " is committed as "
                    + existing.outcome() + "; a different observation (" + observation.outcome() + ") under the same key is refused",
                    SourceLocation.NONE, "candidate", key.candidateId().hex(), "attempt", Integer.toString(key.attempt()),
                    "repeat", Integer.toString(key.repeatIndex())));
        }
        if (byKey.size() >= maxSamples) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, "the run already holds cf.results.maxSamples = "
                    + maxSamples + " observations; no further sample is admitted", SourceLocation.NONE, "limit", Long.toString(maxSamples)));
        }
        if (retention == Retention.AGGREGATE_ONLY) {
            if (folded.contains(key.slot())) throw new IllegalStateException("the sample slot is already final: " + key.slot());
            if (!isMeasurement(key) && observation.verdictCode().isPresent()) {
                int code = observation.verdictCode().get();
                if (!foldedVerdicts.containsKey(code)) {
                    MemoryBudget.Lease counter = budget.reserve(160, "aggregate verdict histogram");
                    leases.add(counter); reserved += counter.bytes(); foldedVerdicts.put(code, 0L);
                }
            }
            MemoryBudget.Lease lease = budget.reserve(estimate(observation) + 256, "aggregate active samples");
            activeLeases.computeIfAbsent(key.slot(), s -> new ArrayList<>()).add(lease);
            activeKeys.computeIfAbsent(key.slot(), s -> new ArrayList<>()).add(key);
            reserved += lease.bytes(); aggregateAttempts = Math.incrementExact(aggregateAttempts);
        } else reserveFor(estimate(observation), "results");
        byKey.put(key, observation);
        if (retention == Retention.FULL) commitOrder.add(observation);
        latest.merge(key.slot(), observation, (a, b) -> b.key().attempt() > a.key().attempt() ? b : a);
        return CommitResult.INSERTED;
    }

    /**
     * Folds a slot after its retry loop. The engine finalizes each logical slot once; arbitrary late replay is a
     * historical operation unavailable in aggregate mode. Memory stays charged until ordered delivery finishes.
     */
    public synchronized void slotFinal(SampleSlot slot) {
        if (retention == Retention.FULL || folded.contains(slot)) return;
        Observation last = latest.get(slot);
        if (last == null) return;
        if (!isMeasurement(last.key()) && last.verdictCode().isPresent()) {
            int code = last.verdictCode().get();
            foldedVerdicts.merge(code, 1L, Math::addExact);
        }
        if (isMeasurement(last.key())) {
            aggregateMeasurements = Math.incrementExact(aggregateMeasurements);
            foldedMeasurements.merge(last.outcome(), 1L, Math::addExact);
        } else {
            aggregateSamples = Math.incrementExact(aggregateSamples);
            foldedOutcomes.merge(last.outcome(), 1L, Math::addExact);
            if (last.key().attempt() > 0) aggregateRetried = Math.incrementExact(aggregateRetried);
        }
        latest.remove(slot); folded.add(slot);
    }

    /** Drops a finalized slot after listeners have received its attempts; duplicate calls are harmless. */
    public synchronized void slotDelivered(SampleSlot slot) {
        if (retention == Retention.FULL) return;
        slotFinal(slot);
        List<SampleKey> keys = activeKeys.remove(slot);
        if (keys != null) keys.forEach(byKey::remove);
        List<MemoryBudget.Lease> held = activeLeases.remove(slot);
        if (held != null) for (MemoryBudget.Lease lease : held) { reserved -= lease.bytes(); lease.close(); }
        folded.remove(slot);
    }

    /**
     * Keeps an attachment of a committed attempt when it fits {@code cf.results.maxAttachmentBytes}; otherwise counts it as
     * omitted. Attaching the same text under the same name again changes nothing.
     *
     * @return whether the attachment is kept
     * @throws FrameworkException RESULT_CONFLICT for a different text under a name already attached to the key,
     *                            RESOURCE_LIMIT when the memory budget is exhausted
     */
    public synchronized boolean attach(SampleKey key, String name, String text) {
        Objects.requireNonNull(key, "key");
        if (closed) {
            throw new IllegalStateException("the result repository of run " + run + " is closed");
        }
        if (!byKey.containsKey(key)) {
            throw new IllegalArgumentException("no committed observation for " + describe(key) + " to attach to");
        }
        Attachment attachment = new Attachment(key, name, text);
        if (retention == Retention.AGGREGATE_ONLY) {
            attachmentsOmitted = Math.incrementExact(attachmentsOmitted);
            attachmentBytesOmitted = Math.addExact(attachmentBytesOmitted, attachment.bytes());
            return false;
        }
        List<Attachment> kept = attachments.getOrDefault(key, List.of());
        for (Attachment a : kept) {
            if (a.name().equals(name)) {
                if (a.text().equals(text)) {
                    return true;
                }
                throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_CONFLICT, "sample " + describe(key) + " already has an "
                        + "attachment '" + name + "' with a different text; the kept one stays", SourceLocation.NONE, "attachment", name));
            }
        }
        long bytes = attachment.bytes();
        if (bytes > maxAttachmentBytes - attachmentBytes) {
            attachmentsOmitted++;
            attachmentBytesOmitted += bytes;
            return false;
        }
        reserveFor(64 + 2L * (name.length() + text.length()), "attachments");
        attachments.computeIfAbsent(key, k -> new ArrayList<>()).add(attachment);
        attachmentBytes += bytes;
        attachmentsKept++;
        return true;
    }

    /** The attachments kept for one committed attempt, in the order they were attached. */
    public synchronized List<Attachment> attachments(SampleKey key) {
        return List.copyOf(attachments.getOrDefault(key, List.of()));
    }

    /** Every kept attachment, in the commit order of the observations they belong to. */
    public synchronized List<Attachment> allAttachments() {
        List<Attachment> all = new ArrayList<>();
        for (Observation o : commitOrder) {
            all.addAll(attachments.getOrDefault(o.key(), List.of()));
        }
        return List.copyOf(all);
    }

    /** Attachments kept and omitted, with their UTF-8 bytes. */
    public synchronized AttachmentTotals attachmentTotals() {
        return new AttachmentTotals(attachmentsKept, attachmentBytes, attachmentsOmitted, attachmentBytesOmitted, maxAttachmentBytes);
    }

    private void reserveFor(long bytes, String purpose) {
        while (used + bytes > reserved) {
            long chunk = Math.max(CHUNK, bytes);
            leases.add(budget.reserve(chunk, purpose));
            reserved += chunk;
        }
        used += bytes;
    }

    public synchronized Optional<Observation> find(SampleKey key) {
        return Optional.ofNullable(byKey.get(key));
    }

    /** The final (highest) attempt of a slot. */
    public synchronized Optional<Observation> latest(SampleSlot slot) {
        return Optional.ofNullable(latest.get(slot));
    }

    /** Every committed observation, in commit order. */
    public synchronized List<Observation> all() {
        return List.copyOf(commitOrder);
    }

    /** Reference slots a quiescent checkpoint copies, without first allocating those copies or totals maps. */
    public synchronized long checkpointReferences() {
        return commitOrder.size() + attachmentsKept;
    }

    /** The final observation of every slot, ordered by candidate id, repeat and environment (not by scheduling). */
    public synchronized List<Observation> finals() {
        if (retention == Retention.AGGREGATE_ONLY) return List.of();
        List<Observation> list = new ArrayList<>(latest.values());
        list.sort(Comparator.comparing((Observation o) -> o.key().candidateId().hex())
                .thenComparingInt(o -> o.key().repeatIndex()).thenComparing(o -> o.key().environmentId()));
        return list;
    }

    public synchronized Totals totals() {
        if (closedTotals != null) return closedTotals;
        EnumMap<Outcome, Long> outcomes = new EnumMap<>(Outcome.class);
        for (Outcome o : Outcome.values()) {
            outcomes.put(o, foldedOutcomes.getOrDefault(o, 0L));
        }
        TreeMap<Integer, Long> verdicts = new TreeMap<>(foldedVerdicts);
        verdicts.values().removeIf(count -> count == 0);
        EnumMap<Outcome, Long> measured = new EnumMap<>(Outcome.class);
        measured.putAll(foldedMeasurements);
        long retried = aggregateRetried;
        long measurements = aggregateMeasurements;
        long samples = aggregateSamples;
        for (Observation o : latest.values()) {
            if (isMeasurement(o.key())) {
                measurements++;
                measured.merge(o.outcome(), 1L, Long::sum);
                continue;
            }
            samples++;
            outcomes.merge(o.outcome(), 1L, Long::sum);
            o.verdictCode().ifPresent(v -> verdicts.merge(v, 1L, Long::sum));
            if (o.key().attempt() > 0) {
                retried++;
            }
        }
        return new Totals(retention == Retention.FULL ? commitOrder.size() : aggregateAttempts,
                samples, retried, outcomes, verdicts, measurements, measured);
    }

    /** Bytes this repository holds against the memory budget. */
    public synchronized long reservedBytes() {
        return reserved;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closedTotals = totals(); closed = true;
        for (MemoryBudget.Lease lease : leases) {
            lease.close();
        }
        leases.clear();
        for (List<MemoryBudget.Lease> held : activeLeases.values()) held.forEach(MemoryBudget.Lease::close);
        activeLeases.clear(); activeKeys.clear(); folded.clear();
        if (retention == Retention.AGGREGATE_ONLY) { byKey.clear(); latest.clear(); }
        reserved = 0;
        attachments.clear();
    }

    /** A conservative size: object headers, the key, the strings at two bytes per character, the maps. */
    static long estimate(Observation o) {
        long bytes = 320;
        bytes += 2L * (o.sourceDigest().length() + o.policyDigest().length() + o.failureMessage().length() + o.stdout().length()
                + o.stderr().length() + o.key().environmentId().length());
        if (o.domain().isPresent()) {
            bytes += 96 + 96L * (o.domain().get().metrics().size() + o.domain().get().attributes().size());
            for (Map.Entry<String, String> e : o.domain().get().attributes().entrySet()) {
                bytes += 2L * (e.getKey().length() + e.getValue().length());
            }
            for (String name : o.domain().get().metrics().keySet()) {
                bytes += 2L * name.length();
            }
        }
        return bytes;
    }

    private static long utf8Length(String text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                bytes++;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    private static String describe(SampleKey key) {
        return "(" + key.runId() + ", " + key.candidateId().hex().substring(0, 12) + "…, attempt " + key.attempt() + ", repeat "
                + key.repeatIndex() + ", environment '" + key.environmentId() + "')";
    }

    /**
     * Exact totals: every committed attempt, the verdict samples (slots), the verdict samples whose final attempt is a
     * retry, the final outcome of each verdict sample and the final verdict codes; measurements (metrics repeat scope)
     * and their final outcomes apart.
     */
    public record Totals(long attempts, long samples, long retried, Map<Outcome, Long> outcomes, Map<Integer, Long> verdicts,
                         long measurements, Map<Outcome, Long> measurementOutcomes) {

        public Totals {
            outcomes = java.util.Collections.unmodifiableMap(new EnumMap<>(outcomes));
            verdicts = java.util.Collections.unmodifiableMap(new TreeMap<>(verdicts));
            measurementOutcomes = java.util.Collections.unmodifiableMap(measurementOutcomes.isEmpty()
                    ? new EnumMap<>(Outcome.class) : new EnumMap<>(measurementOutcomes));
        }

        public Totals(long attempts, long samples, long retried, Map<Outcome, Long> outcomes, Map<Integer, Long> verdicts) {
            this(attempts, samples, retried, outcomes, verdicts, 0, Map.of());
        }

        public long count(Outcome outcome) {
            return outcomes.getOrDefault(outcome, 0L);
        }
    }

    /** Attachments kept and omitted, their UTF-8 bytes, and the bound they were kept within. */
    public record AttachmentTotals(long kept, long keptBytes, long omitted, long omittedBytes, long maxBytes) {
    }
}
