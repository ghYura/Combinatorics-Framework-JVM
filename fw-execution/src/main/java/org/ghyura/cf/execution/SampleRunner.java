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

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.spi.CancellationSource;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.store.MemoryBudget;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs one attempt of one sample under the cooperative deadline (architecture D07). The attempt runs on a thread of its
 * own; at the deadline, or when the run is cancelled, the sample's cancellation fires and its thread is interrupted, and
 * the attempt has {@code graceMillis} to stop. An attempt that stops is TIMEOUT (EXEC_DEADLINE) or CANCELLED; one that
 * does not is reported TIMEOUT with EXEC_UNCOOPERATIVE and its thread is left running, because one JVM cannot stop it:
 * nothing here kills a thread. Whatever such a thread does later is ignored: the observation was decided when its time
 * ran out, so a late result can never replace it.
 *
 * <p>Each attempt reserves twice its output bound from the run's budget for its capture buffers until its thread ends
 * (RESOURCE_LIMIT when that does not fit). With a serial gate ({@code cf.execution.serialize}) only one attempt executes at
 * a time; compilation and preparation stay parallel, and the deadline starts once the gate is taken.</p>
 */
final class SampleRunner {

    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(20);

    private final SutTarget target;
    private final ExecutionPolicy policy;
    private final String policyDigest;
    private final String planDigest;
    private final MemoryBudget budget;
    private final AtomicLong threads = new AtomicLong();
    private final List<Thread> abandoned = new ArrayList<>();

    SampleRunner(SutTarget target, ExecutionPolicy policy, String planDigest, MemoryBudget budget) {
        this.target = target;
        this.policy = policy;
        this.policyDigest = policy.digest(target.id(), target.version());
        this.planDigest = planDigest;
        this.budget = budget;
    }

    String policyDigest() {
        return policyDigest;
    }

    Observation attempt(SutCandidate candidate, SampleKey key, Cancellation run) {
        return attempt(candidate, key, run, null);
    }

    Observation attempt(SutCandidate candidate, SampleKey key, Cancellation run, Semaphore serial) {
        String sourceDigest;
        try {
            sourceDigest = target.sourceDigest(candidate);
        } catch (RuntimeException e) {
            return failure(key, candidate.displayId(), Outcome.BROKEN, ErrorCode.ASSEMBLY_RENDER_FAILED,
                    "the candidate could not be rendered: " + Failures.describe(e), 0, "", new Capture(1));
        }
        if (run.isCancelled()) {
            return failure(key, candidate.displayId(), Outcome.CANCELLED, ErrorCode.EXEC_CANCELLED, "the run was cancelled before the sample started", 0,
                    sourceDigest, new Capture(1));
        }
        long preparing = System.nanoTime();
        SutTarget.Prepared prepared;
        try {
            prepared = target.prepare(candidate);
        } catch (RuntimeException | LinkageError e) {
            prepared = SutTarget.Prepared.failed(new AttemptResult.Failure(Outcome.INFRA_FAIL, ErrorCode.EXEC_COMPILER_UNAVAILABLE,
                    "preparing the candidate failed: " + Failures.describe(e)));
        }
        if (prepared.failure() != null) {
            AttemptResult.Failure f = prepared.failure();
            return failure(key, candidate.displayId(), f.outcome(), f.code(), f.message(), System.nanoTime() - preparing, sourceDigest,
                    new Capture(1));
        }
        SutTarget.Prepared ready = prepared;
        if (serial != null && !acquire(serial, run)) {
            return failure(key, candidate.displayId(), Outcome.CANCELLED, ErrorCode.EXEC_CANCELLED, "the run was cancelled before the sample started", 0,
                    sourceDigest, new Capture(1));
        }
        try {
            return run(candidate, key, run, ready, sourceDigest);
        } finally {
            if (serial != null) {
                serial.release();
            }
        }
    }

    private static boolean acquire(Semaphore serial, Cancellation run) {
        try {
            while (!serial.tryAcquire(20, TimeUnit.MILLISECONDS)) {
                if (run.isCancelled()) {
                    return false;
                }
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Observation run(SutCandidate candidate, SampleKey key, Cancellation run, SutTarget.Prepared ready, String sourceDigest) {
        MemoryBudget.Lease output = budget.reserve(2L * policy.outputMaxBytes(), "output capture");
        CancellationSource own = new CancellationSource();
        Cancellation token = () -> own.isCancelled() || run.isCancelled();
        long start = System.nanoTime();
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(policy.timeoutMillis());
        Capture capture = new Capture(policy.outputMaxBytes());
        SampleContext context = new SampleContext(key, token, deadline, policy.seed(), planDigest, capture, budget);
        CompletableFuture<AttemptResult> done = new CompletableFuture<>();
        Thread thread = Thread.ofPlatform().daemon().name("cf-sample-" + threads.incrementAndGet()).unstarted(() -> {
            OutputRouter.bind(capture);
            AttemptResult completed;
            try {
                completed = target.attempt(candidate, ready, context);
            } catch (Throwable t) {
                completed = Failures.classify(t);
            } finally {
                OutputRouter.unbind();
                context.releaseAll();
                output.close();
            }
            // A completed attempt includes cleanup: its caller may immediately release the run's resources.
            done.complete(completed);
        });
        try {
            thread.start();
        } catch (RuntimeException | Error e) {
            output.close();
            throw e;
        }

        Outcome stop = null;
        AttemptResult result = null;
        try {
            while (result == null && stop == null) {
                long left = deadline - System.nanoTime();
                if (done.isDone()) {
                    result = done.join();
                } else if (left <= 0) {
                    stop = Outcome.TIMEOUT;
                } else if (run.isCancelled()) {
                    stop = Outcome.CANCELLED;
                } else {
                    try {
                        result = done.get(Math.min(left, POLL_NANOS), TimeUnit.NANOSECONDS);
                    } catch (TimeoutException e) {
                        // keep waiting until the deadline or a cancellation
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop = Outcome.CANCELLED;
        } catch (ExecutionException e) {
            result = Failures.classify(e.getCause());
        }
        if (stop == null) {
            if (result instanceof AttemptResult.Failure f && run.isCancelled() && f.outcome() != Outcome.SKIPPED) {
                // the sample saw the run's cancellation first and gave up: it was cancelled, not broken
                return failure(key, candidate.displayId(), Outcome.CANCELLED, ErrorCode.EXEC_CANCELLED, "the run was cancelled while the sample ran ("
                        + f.code() + ": " + f.message() + ")", System.nanoTime() - start, sourceDigest, capture);
            }
            return fromResult(key, candidate.displayId(), result, System.nanoTime() - start, sourceDigest, capture);
        }
        own.cancel();
        thread.interrupt();
        long decided = System.nanoTime() - start;
        boolean stopped = awaitQuietly(done, TimeUnit.MILLISECONDS.toNanos(policy.graceMillis()));
        if (!stopped) {
            synchronized (abandoned) {
                abandoned.add(thread);
            }
        }
        if (stop == Outcome.TIMEOUT) {
            return stopped
                    ? failure(key, candidate.displayId(), Outcome.TIMEOUT, ErrorCode.EXEC_DEADLINE, "the deadline of " + policy.timeoutMillis()
                    + " ms passed; the sample stopped when asked", decided, sourceDigest, capture)
                    : failure(key, candidate.displayId(), Outcome.TIMEOUT, ErrorCode.EXEC_UNCOOPERATIVE, "the deadline of " + policy.timeoutMillis()
                    + " ms passed and the sample did not stop within " + policy.graceMillis() + " ms; its thread " + thread.getName()
                    + " was interrupted and is still running (not stopped: one JVM cannot stop it)", decided, sourceDigest, capture);
        }
        return failure(key, candidate.displayId(), Outcome.CANCELLED, ErrorCode.EXEC_CANCELLED, stopped ? "the run was cancelled while the sample ran"
                : "the run was cancelled while the sample ran; its thread " + thread.getName() + " did not stop within "
                + policy.graceMillis() + " ms and is still running", decided, sourceDigest, capture);
    }

    private static boolean awaitQuietly(CompletableFuture<AttemptResult> done, long nanos) {
        try {
            done.get(nanos, TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return done.isDone();
        } catch (ExecutionException e) {
            return true;
        }
    }

    private Observation fromResult(SampleKey key, String display, AttemptResult result, long elapsed, String sourceDigest,
                                   Capture capture) {
        return switch (result) {
            case AttemptResult.Verdict v -> new Observation(key, display, v.outcome(), Optional.of(target.harvestsMetricLines()
                    ? MetricLine.harvest(v.observation(), capture.out()) : v.observation()), elapsed, sourceDigest, policyDigest,
                    Optional.empty(), "", capture.out(), capture.err(), capture.truncated());
            case AttemptResult.Failure f -> failure(key, display, f.outcome(), f.code(), f.message(), elapsed, sourceDigest, capture);
        };
    }

    private Observation failure(SampleKey key, String display, Outcome outcome, ErrorCode code, String message, long elapsed,
                                String sourceDigest, Capture capture) {
        return new Observation(key, display, outcome, Optional.empty(), elapsed, sourceDigest, policyDigest, Optional.of(code), message,
                capture.out(), capture.err(), capture.truncated());
    }

    /** Observation for a sample that was admitted but never started (the run stopped first). */
    Observation notStarted(SampleKey key, String display, Outcome outcome, ErrorCode code, String message) {
        return failure(key, display, outcome, code, message, 0, "", new Capture(1));
    }

    /** Threads of samples that ignored their deadline or the cancellation and were still running when last seen. */
    List<String> stillRunning() {
        synchronized (abandoned) {
            return abandoned.stream().filter(Thread::isAlive).map(Thread::getName).toList();
        }
    }

    long abandonedCount() {
        synchronized (abandoned) {
            return abandoned.size();
        }
    }
}
