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
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.results.ResultRepository;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.spi.CancellationSource;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.store.MemoryBudget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes candidates against a target with {@code workers} threads (architecture D07, rules on backpressure and windows).
 *
 * <ul>
 *   <li><b>Samples.</b> The calling thread admits candidates from a lazy iterator; candidate number c (its ordinal, in
 *       admission order) becomes K repeats spread over the environments by the repeat policy (legacy BundleControlPlane):
 *       local pins them to environment c mod E, disperse sends repeat r to (c + r) mod E, nested runs K repeats in each
 *       environment. Every repeat has fresh SUT state.</li>
 *   <li><b>Attempts.</b> A TIMEOUT or INFRA_FAIL attempt is retried while attempts remain; a verdict, BROKEN, SKIPPED or
 *       CANCELLED is final. Under the metrics repeat scope repeat 0 is the verdict sample and the other repeats are
 *       measurements, run after it on the same worker and skipped (SKIPPED, EXEC_NOT_ATTEMPTED) when it gave no verdict.</li>
 *   <li><b>Admission.</b> Work items wait in a queue bounded by items and by the bytes they hold; each item reserves its
 *       estimate from the run budget until its samples are final; an item larger than the byte bound is RESOURCE_LIMIT.
 *       Candidate c is admitted only while c &lt; d + window, d being the oldest candidate not delivered yet, which bounds
 *       what waits for delivery.</li>
 *   <li><b>Commits before publishing.</b> Every attempt is committed to the result repository (and its attachment kept)
 *       before anyone hears of it. One delivery thread hands the committed observations to the listener in candidate
 *       order and, within a candidate, by repeat, environment and attempt, so the sequence does not depend on the
 *       workers. A listener that throws gets the same observation again, up to the policy's attempts, then the failure is
 *       counted; counters come from the repository, never from deliveries.</li>
 *   <li><b>Lifecycle.</b> Pausing stops admission; samples already admitted finish and the drain callback fires when none
 *       is left. Cancelling (or interrupting the calling thread) stops admission, makes running samples stop
 *       cooperatively and commits admitted samples that never started as CANCELLED. A commit or producer failure stops the
 *       run the same way and is rethrown when the workers are done; nothing waits forever on a full or empty queue.</li>
 * </ul>
 */
public final class ExecutionEngine {

    /** Hears about every committed attempt, on the delivery thread, after the commit, in candidate order. */
    @FunctionalInterface
    public interface Listener {
        void committed(Observation observation);
    }

    /** Receives the zero-based admission ordinal, including candidates whose restored samples are not delivered again. */
    @FunctionalInterface
    public interface OrderedListener {
        void committed(long ordinal, Observation observation);
    }

    /** What the engine did besides the committed observations. */
    public record Report(long admittedCandidates, long admittedSamples, long attemptsRun, long abandonedThreads,
                         List<String> stillRunning, long listenerFailures, boolean cancelled, long deliveries, long listenerRetries,
                         long peakQueuedBytes, long peakWindow, String firstListenerFailure) {
        public Report {
            stillRunning = List.copyOf(stillRunning);
        }
    }

    /** One sample of a candidate: its repeat index and environment. */
    record Slot(int repeat, String environment) {
    }

    /** The candidate an admitted work item belongs to, and what its items committed so far. */
    private static final class Admitted {
        final long ordinal;
        final SutCandidate candidate;
        int itemsLeft;
        final List<Observation> committed = new ArrayList<>();

        Admitted(long ordinal, SutCandidate candidate, int items) {
            this.ordinal = ordinal;
            this.candidate = candidate;
            this.itemsLeft = items;
        }
    }

    /** What a worker executes: samples of one candidate, in order, holding their reservation until they are final. */
    private record Item(Admitted owner, List<Slot> slots, long bytes, MemoryBudget.Lease lease) {
    }

    private static final Item END = new Item(null, List.of(), 0, null);

    private final SutTarget target;
    private final ExecutionPolicy policy;
    private final ResultRepository results;
    private final SampleRunner runner;
    private final MemoryBudget budget;
    private final RunId run;
    private final Semaphore serial;
    private final AtomicBoolean paused = new AtomicBoolean();
    private final Object flow = new Object();
    private final TreeMap<Long, Admitted> done = new TreeMap<>();
    private volatile Runnable whenDrained;
    private long inFlightItems;
    private long queuedBytes;
    private long nextToDeliver;
    private long admittedCandidates;
    private final AtomicLong restoredSamplesRevisited = new AtomicLong();

    /** Restored sample slots encountered again by this engine, including cancelled slots retried on resume. */
    public long restoredSamplesRevisited() {
        return restoredSamplesRevisited.get();
    }
    private boolean admissionOver;
    private boolean workersOver;
    private long peakQueuedBytes;
    private long peakWindow;

    public ExecutionEngine(SutTarget target, ExecutionPolicy policy, ResultRepository results, MemoryBudget budget, String planDigest) {
        this.target = Objects.requireNonNull(target, "target");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.results = Objects.requireNonNull(results, "results");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.runner = new SampleRunner(target, policy, planDigest, budget);
        this.run = results.run();
        this.serial = policy.serialize() ? new Semaphore(1, true) : null;
    }

    /** SHA-256 of the execution policy every observation of this engine carries. */
    public String policyDigest() {
        return runner.policyDigest();
    }

    /** Stops admitting candidates until {@link #resume()}; admitted samples still run. Idempotent. */
    public void pause() {
        pause(null);
    }

    /** Stops admission and calls {@code drained} once no admitted sample is left unfinished (at once if none is). */
    public void pause(Runnable drained) {
        boolean idle;
        synchronized (flow) {
            whenDrained = drained;
            paused.set(true);
            idle = inFlightItems == 0;
        }
        if (idle) {
            fireDrained();
        }
    }

    /** Admits candidates again. Idempotent. */
    public void resume() {
        synchronized (flow) {
            whenDrained = null;
            paused.set(false);
            flow.notifyAll();
        }
    }

    public boolean paused() {
        return paused.get();
    }

    /**
     * The samples of candidate {@code ordinal} grouped into work items, each executed in order by one worker: every sample
     * its own item when every repeat is a verdict sample; one item per environment group (repeat 0 first) under the
     * metrics scope.
     */
    static List<List<Slot>> plan(long ordinal, int repeats, List<String> envs, RunConfig.RepeatPolicy repeatPolicy,
                                 RunConfig.RepeatScope scope) {
        int e = envs.size();
        List<List<Slot>> groups = new ArrayList<>();
        switch (repeatPolicy) {
            case LOCAL -> {
                String env = envs.get((int) Math.floorMod(ordinal, (long) e));
                List<Slot> slots = new ArrayList<>();
                for (int r = 0; r < repeats; r++) {
                    slots.add(new Slot(r, env));
                }
                groups.add(slots);
            }
            case DISPERSE -> {
                List<Slot> slots = new ArrayList<>();
                for (int r = 0; r < repeats; r++) {
                    slots.add(new Slot(r, envs.get((int) Math.floorMod(ordinal + r, (long) e))));
                }
                groups.add(slots);
            }
            case NESTED -> {
                for (String env : envs) {
                    List<Slot> slots = new ArrayList<>();
                    for (int r = 0; r < repeats; r++) {
                        slots.add(new Slot(r, env));
                    }
                    groups.add(slots);
                }
            }
        }
        if (scope == RunConfig.RepeatScope.METRICS) {
            return groups;
        }
        List<List<Slot>> items = new ArrayList<>();
        for (List<Slot> group : groups) {
            for (Slot slot : group) {
                items.add(List.of(slot));
            }
        }
        return items;
    }

    /**
     * Runs every candidate the iterator yields (the iterator is used on the calling thread only) and returns when every
     * admitted sample has its final observation committed and delivered.
     *
     * @throws FrameworkException the first commit, admission or producer failure, after the workers stopped
     */
    public Report run(Iterator<? extends SutCandidate> candidates, Cancellation cancellation, Listener listener) {
        return runOrdered(candidates, cancellation, (ordinal, observation) -> {
            if (listener != null) listener.committed(observation);
        });
    }

    /** Like {@link #run}, with the admission ordinal on every delivered attempt. */
    public Report runOrdered(Iterator<? extends SutCandidate> candidates, Cancellation cancellation, OrderedListener listener) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(cancellation, "cancellation");
        OrderedListener hear = listener == null ? (ordinal, o) -> { } : listener;
        CancellationSource failed = new CancellationSource();
        CancellationSource interrupted = new CancellationSource();
        Cancellation cancelled = () -> cancellation.isCancelled() || interrupted.isCancelled();
        Cancellation stop = () -> cancelled.isCancelled() || failed.isCancelled();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        AtomicLong attempts = new AtomicLong();
        AtomicLong samples = new AtomicLong();
        Delivery delivery = new Delivery(hear);
        BlockingQueue<Item> queue = new ArrayBlockingQueue<>(policy.queueItems());
        OutputRouter.acquire();
        List<Thread> workers = new ArrayList<>();
        Thread deliverer = Thread.ofPlatform().name("cf-delivery").unstarted(delivery);
        try {
            deliverer.start();
            for (int w = 0; w < policy.workers(); w++) {
                Thread worker = Thread.ofPlatform().name("cf-worker-" + (w + 1)).unstarted(() -> work(queue, stop, cancelled, failure,
                        failed, attempts));
                workers.add(worker);
                worker.start();
            }
            admit(candidates, queue, stop, failure, failed, interrupted, samples);
        } catch (RuntimeException e) {
            failure.compareAndSet(null, e);
            failed.cancel();
        } finally {
            synchronized (flow) {
                admissionOver = true;
                flow.notifyAll();
            }
            for (int w = 0; w < workers.size(); w++) {
                putEnd(queue);
            }
            boolean wasInterrupted = joinAll(workers, interrupted);
            synchronized (flow) {
                workersOver = true;
                flow.notifyAll();
            }
            wasInterrupted |= joinAll(List.of(deliverer), interrupted);
            OutputRouter.release();
            if (wasInterrupted || interrupted.isCancelled()) {
                Thread.currentThread().interrupt();
            }
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        long admitted;
        synchronized (flow) {
            admitted = admittedCandidates;
        }
        return new Report(admitted, samples.get(), attempts.get(), runner.abandonedCount(), runner.stillRunning(), delivery.failures,
                cancelled.isCancelled(), delivery.deliveries, delivery.retries, peakQueuedBytes(), peakWindow(), delivery.firstFailure);
    }

    private void admit(Iterator<? extends SutCandidate> candidates, BlockingQueue<Item> queue, Cancellation stop,
                       AtomicReference<RuntimeException> failure, CancellationSource failed, CancellationSource interrupted,
                       AtomicLong samples) {
        long ordinal = 0;
        while (!stop.isCancelled()) {
            if (!awaitWindow(ordinal, stop, interrupted)) {
                return;
            }
            if (!candidates.hasNext()) {
                return;
            }
            SutCandidate candidate = candidates.next();
            List<List<Slot>> items = plan(ordinal, policy.repeats(), policy.environments(), policy.repeatPolicy(), policy.repeatScope());
            Admitted owner = new Admitted(ordinal, candidate, items.size());
            long bytes = itemBytes(candidate);
            synchronized (flow) {
                admittedCandidates = ordinal + 1;
                peakWindow = Math.max(peakWindow, ordinal + 1 - nextToDeliver);
            }
            int offered = 0;
            for (List<Slot> slots : items) {
                Item item = reserve(owner, slots, bytes, stop, interrupted);
                if (item == null || !offer(queue, item, stop, interrupted)) {
                    if (item != null) {
                        finishItem(item);
                    }
                    abandon(owner, items.size() - offered - (item != null ? 1 : 0));
                    return;
                }
                offered++;
                samples.addAndGet(slots.size());
            }
            ordinal++;
        }
    }

    /** Items never queued because admission stopped still count down, so their candidate can be delivered. */
    private void abandon(Admitted owner, int notQueued) {
        if (notQueued <= 0) {
            return;
        }
        synchronized (flow) {
            owner.itemsLeft -= notQueued;
            if (owner.itemsLeft == 0) {
                done.put(owner.ordinal, owner);
            }
            flow.notifyAll();
        }
    }

    private long itemBytes(SutCandidate candidate) {
        return Math.max(1, candidate.footprintBytes());
    }

    /** Waits until the item's bytes fit the queue bound, then reserves them from the run budget. Null when stopped. */
    private Item reserve(Admitted owner, List<Slot> slots, long bytes, Cancellation stop, CancellationSource interrupted) {
        if (bytes > policy.queueBytes()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, "candidate " + owner.candidate.displayId()
                    + " needs about " + bytes + " bytes in the work queue, more than cf.pipeline.queue.maxBytes = " + policy.queueBytes()
                    + " allows for everything queued", SourceLocation.NONE, "requestedBytes", Long.toString(bytes), "limit",
                    Long.toString(policy.queueBytes())));
        }
        synchronized (flow) {
            while (paused.get() || queuedBytes + bytes > policy.queueBytes()) {
                if (stop.isCancelled() || !waitFlow(interrupted)) {
                    return null;
                }
            }
            MemoryBudget.Lease lease = budget.reserve(bytes, "work queue");
            queuedBytes += bytes;
            inFlightItems++;
            peakQueuedBytes = Math.max(peakQueuedBytes, queuedBytes);
            return new Item(owner, slots, bytes, lease);
        }
    }

    private boolean awaitWindow(long ordinal, Cancellation stop, CancellationSource interrupted) {
        synchronized (flow) {
            while (ordinal >= nextToDeliver + policy.window()) {
                if (stop.isCancelled() || !waitFlow(interrupted)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Waits on the flow monitor for a short while; false when the calling thread was interrupted (it then cancels). */
    private boolean waitFlow(CancellationSource interrupted) {
        try {
            flow.wait(20);
            return true;
        } catch (InterruptedException e) {
            interrupted.cancel();
            return false;
        }
    }

    private void work(BlockingQueue<Item> queue, Cancellation stop, Cancellation cancelled, AtomicReference<RuntimeException> failure,
                      CancellationSource failed, AtomicLong attempts) {
        while (true) {
            Item item;
            try {
                item = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            if (item == END) {
                return;
            }
            try {
                execute(item, stop, cancelled, failure, attempts);
            } catch (RuntimeException | Error e) {
                failure.compareAndSet(null, e instanceof RuntimeException re ? re : new IllegalStateException("a worker failed", e));
                failed.cancel();
            } finally {
                finishItem(item);
            }
        }
    }

    private void execute(Item item, Cancellation stop, Cancellation cancelled, AtomicReference<RuntimeException> failure,
                         AtomicLong attempts) {
        SutCandidate candidate = item.owner().candidate;
        Outcome verdictSample = null;
        for (Slot slot : item.slots()) {
            SampleKey key = new SampleKey(run, candidate.id(), 0, slot.repeat(), slot.environment());
            try {
            boolean measurement = results.isMeasurement(key);
            // a resumed run's repository holds the restored observations: a final one is kept, a cancelled one runs again
            Observation restored = results.latest(key.slot()).orElse(null);
            if (restored != null) {
                restoredSamplesRevisited.incrementAndGet();
                if (restored.outcome() != Outcome.CANCELLED) {
                    if (!measurement) {
                        verdictSample = restored.outcome();
                    }
                    continue;
                }
                key = restored.key().nextAttempt();
            }
            if (stop.isCancelled()) {
                try {
                    commit(item, runner.notStarted(key, candidate.displayId(), Outcome.CANCELLED, ErrorCode.EXEC_CANCELLED,
                            failure.get() == null ? "the run was cancelled before the sample started"
                                    : "the run stopped on a failure before the sample started"), false);
                } catch (RuntimeException e) {
                    // the run is failing already (a full or conflicting result history); its first failure is reported
                }
                continue;
            }
            if (measurement && verdictSample != null && verdictSample != Outcome.PASS && verdictSample != Outcome.DOMAIN_FAIL) {
                commit(item, runner.notStarted(key, candidate.displayId(), Outcome.SKIPPED, ErrorCode.EXEC_NOT_ATTEMPTED,
                        "the verdict sample (repeat 0) ended " + verdictSample + " without a verdict, so this measurement was not attempted"),
                        false);
                continue;
            }
            while (true) {
                Observation o = runner.attempt(candidate, key, cancelled, serial);
                attempts.incrementAndGet();
                commit(item, o, !measurement);
                boolean retry = (o.outcome() == Outcome.TIMEOUT || o.outcome() == Outcome.INFRA_FAIL)
                        && key.attempt() + 1 < policy.maxAttempts() && !stop.isCancelled();
                if (!retry) {
                    if (!measurement) {
                        verdictSample = o.outcome();
                    }
                    break;
                }
                key = key.nextAttempt();
            }
            } finally {
                results.slotFinal(key.slot());
            }
        }
    }

    /** Commits an attempt, keeps its attachment, then records it for delivery. */
    private void commit(Item item, Observation o, boolean attachable) {
        results.commit(o);
        if (attachable && attaches(o.outcome())) {
            String source;
            try {
                source = item.owner().candidate.source();
            } catch (RuntimeException e) {
                source = null;
            }
            if (source != null) {
                results.attach(o.key(), "source", source);
            }
        }
        synchronized (flow) {
            item.owner().committed.add(o);
        }
    }

    private boolean attaches(Outcome outcome) {
        return switch (policy.attach()) {
            case NONE -> false;
            case PASSED -> outcome == Outcome.PASS;
            case FAILED -> outcome == Outcome.DOMAIN_FAIL || outcome == Outcome.BROKEN || outcome == Outcome.TIMEOUT
                    || outcome == Outcome.INFRA_FAIL;
            case ALL -> outcome != Outcome.CANCELLED && outcome != Outcome.SKIPPED;
        };
    }

    private void finishItem(Item item) {
        boolean drained;
        synchronized (flow) {
            queuedBytes -= item.bytes();
            inFlightItems--;
            Admitted owner = item.owner();
            owner.itemsLeft--;
            if (owner.itemsLeft == 0) {
                done.put(owner.ordinal, owner);
            }
            drained = inFlightItems == 0 && paused.get();
            flow.notifyAll();
        }
        item.lease().close();
        if (drained) {
            fireDrained();
        }
    }

    private void fireDrained() {
        Runnable r = whenDrained;
        if (r != null && paused.get()) {
            r.run();
        }
    }

    /** The delivery thread: candidates in ordinal order; after the workers ended, whatever was committed but not delivered. */
    private final class Delivery implements Runnable {
        private final OrderedListener listener;
        volatile long deliveries;
        volatile long retries;
        volatile long failures;
        volatile String firstFailure = "";

        Delivery(OrderedListener listener) {
            this.listener = listener;
        }

        @Override
        public void run() {
            while (true) {
                List<Observation> batch;
                long ordinal;
                synchronized (flow) {
                    while (!done.containsKey(nextToDeliver) && !(workersOver || (admissionOver && nextToDeliver >= admittedCandidates
                            && inFlightItems == 0))) {
                        try {
                            flow.wait(20);
                        } catch (InterruptedException e) {
                            // the engine joins this thread; keep delivering what is committed
                        }
                    }
                    Admitted next = done.remove(nextToDeliver);
                    if (next == null) {
                        if (!workersOver && !(admissionOver && nextToDeliver >= admittedCandidates && inFlightItems == 0)) {
                            continue;
                        }
                        if (done.isEmpty()) {
                            return;
                        }
                        next = done.pollFirstEntry().getValue();
                    }
                    batch = new ArrayList<>(next.committed);
                    ordinal = next.ordinal;
                }
                batch.sort(Comparator.comparingInt((Observation o) -> o.key().repeatIndex()).thenComparing(o -> o.key().environmentId())
                        .thenComparingInt(o -> o.key().attempt()));
                for (Observation o : batch) {
                    deliver(ordinal, o);
                }
                for (Observation o : batch) results.slotDelivered(o.slot());
                synchronized (flow) {
                    nextToDeliver++;
                    flow.notifyAll();
                }
            }
        }

        private void deliver(long ordinal, Observation o) {
            for (int attempt = 1; ; attempt++) {
                try {
                    listener.committed(ordinal, o);
                    deliveries++;
                    return;
                } catch (RuntimeException e) {
                    if (attempt >= policy.listenerAttempts()) {
                        if (failures == 0) {
                            firstFailure = "listener failed " + attempt + " times on sample " + o.key().candidateId().hex().substring(0, 12)
                                    + "… repeat " + o.key().repeatIndex() + " environment '" + o.key().environmentId() + "' attempt "
                                    + o.key().attempt() + ": " + e;
                        }
                        failures++;
                        return;
                    }
                    retries++;
                }
            }
        }
    }

    private long peakQueuedBytes() {
        synchronized (flow) {
            return peakQueuedBytes;
        }
    }

    private long peakWindow() {
        synchronized (flow) {
            return peakWindow;
        }
    }

    private static boolean offer(BlockingQueue<Item> queue, Item item, Cancellation stop, CancellationSource interrupted) {
        try {
            while (!queue.offer(item, 20, TimeUnit.MILLISECONDS)) {
                if (stop.isCancelled()) {
                    return false;
                }
            }
            return true;
        } catch (InterruptedException e) {
            interrupted.cancel();
            return false;
        }
    }

    private static void putEnd(BlockingQueue<Item> queue) {
        boolean interrupted = false;
        while (true) {
            try {
                queue.put(END);
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Joins the threads; an interrupt of the caller meanwhile cancels the run and is reported back. */
    private static boolean joinAll(List<Thread> threads, CancellationSource interrupted) {
        boolean wasInterrupted = false;
        for (Thread t : threads) {
            while (true) {
                try {
                    t.join();
                    break;
                } catch (InterruptedException e) {
                    wasInterrupted = true;
                    interrupted.cancel();
                }
            }
        }
        return wasInterrupted;
    }

    /** The target this engine executes against. */
    public SutTarget target() {
        return target;
    }
}
