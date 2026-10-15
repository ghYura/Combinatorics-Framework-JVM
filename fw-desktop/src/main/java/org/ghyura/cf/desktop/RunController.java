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

package org.ghyura.cf.desktop;

import org.ghyura.cf.campaign.Progress;
import org.ghyura.cf.campaign.ProgressListener;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.campaign.RunSummary;
import org.ghyura.cf.desktop.session.RunReport;
import org.ghyura.cf.artifacts.CheckpointCodec;
import org.ghyura.cf.artifacts.ResultsExport;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Run control without Swing: prepares a run through the session, starts it on the run's own thread, and keeps only the
 * latest progress (coalesced: the engine never waits for a view). Pause, resume and cancel go to the run handle, which
 * ignores what its state does not allow. A view polls {@link #view()} on its own thread.
 */
final class RunController implements AutoCloseable {

    private final java.util.function.Supplier<Session> session;
    private final AtomicReference<Session.PreparedRun> current = new AtomicReference<>();
    private final AtomicReference<Progress> latest = new AtomicReference<>();
    private final AtomicReference<String> report = new AtomicReference<>("");
    private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<Checkpoint> checkpoint = new AtomicReference<>();
    private boolean closed;

    RunController(Session session) {
        this(() -> session);
    }

    RunController(java.util.function.Supplier<Session> session) {
        this.session = session;
    }

    /** Also tells the listener every progress of this and later runs, on the run's threads. */
    void observe(ProgressListener listener) {
        listeners.add(listener);
    }

    /** What a view shows: the run's state (null before the first run), committed samples and the final report. */
    record View(RunState state, long committed, String report) {
    }

    /**
     * Prepares and starts a run of the scenario; the future completes with its summary.
     *
     * @throws IllegalStateException while a previous run has not ended
     * @throws org.ghyura.cf.model.FrameworkException when the scenario cannot be read or the run cannot be prepared
     */
    synchronized CompletableFuture<RunSummary> start(Path scenario) {
        if (closed) throw new IllegalStateException("the run controller is closed");
        Session.PreparedRun previous = current.get();
        if (previous != null && !previous.handle().state().terminal()) {
            throw new IllegalStateException("a run is still going; cancel it or wait for it");
        }
        Session.PreparedRun run = session.get().prepareRun(scenario);
        if (previous != null) {
            previous.close();
        }
        current.set(run);
        latest.set(null);
        report.set("");
        checkpoint.set(null);
        try {
            run.handle().checkpointTo(scenario.toAbsolutePath().toString(), checkpoint::set);
        } catch (FrameworkException e) {
            // aggregate-only retention keeps no exact checkpoint: the run goes on, there is just nothing to export
        }
        run.handle().observe(p -> {
            latest.set(p);
            for (ProgressListener l : listeners) {
                l.onProgress(p);
            }
        });
        return run.handle().start().toCompletableFuture().whenComplete((summary, failure) -> {
            String finished = failure != null ? Background.describe(failure) : text(summary, run);
            synchronized (this) {
                if (current.get() == run) report.set(finished);
            }
        });
    }

    private static String text(RunSummary summary, Session.PreparedRun run) {
        StringBuilder text = new StringBuilder(RunReport.toText(summary));
        for (Diagnostic d : run.findings()) {
            text.append(d).append('\n');
        }
        for (Diagnostic d : summary.diagnostics()) {
            text.append(d).append('\n');
        }
        return text.toString();
    }

    void pause() {
        Session.PreparedRun run = current.get();
        if (run != null) run.handle().pause();
    }

    void resume() {
        Session.PreparedRun run = current.get();
        if (run != null) run.handle().resume();
    }

    void cancel() {
        Session.PreparedRun run = current.get();
        if (run != null) run.handle().cancel();
    }

    View view() {
        Session.PreparedRun run = current.get();
        Progress p = latest.get();
        return new View(run == null ? null : run.handle().state(), p == null ? 0 : p.committed(), report.get());
    }

    /**
     * Writes the last run's final checkpoint as the {@code export} command's kinds do: report (XML summary), results (legacy
     * results_v2 rows), csv (final samples with their metrics), or checkpoint (the artifact {@code resume} reads).
     *
     * @throws FrameworkException RESULT_EXPORT_FAILED when there is no ended run to export or the file cannot be written
     */
    @SuppressWarnings("try")
    synchronized void export(String kind, Path target) {
        Session.PreparedRun run = current.get();
        Checkpoint c = checkpoint.get();
        if (run == null || !run.handle().state().terminal() || c == null) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "there is no ended run to export (or its retention "
                    + "keeps no exact results)", SourceLocation.of(target.toString())));
        }
        MemoryBudget budget = session.get().analysisBudget();
        try (MemoryBudget.Lease retained = budget.reserve(MemorySizes.retained(c), "checkpoint retained during export")) {
            if (kind.equals("checkpoint")) {
                CheckpointCodec.write(target, c, budget);
                return;
            }
            byte[] bytes = ResultsExport.encode(c, kind, budget);
            AtomicFiles.write(target, bytes);
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the " + kind + " export cannot be written: "
                    + e.getMessage(), SourceLocation.of(target.toString())));
        }
    }

    /** Cancels a run still going, waits for it within its deadlines and releases it. */
    @Override
    public void close() {
        Session.PreparedRun run;
        synchronized (this) {
            closed = true;
            run = current.getAndSet(null);
        }
        if (run != null) {
            run.close();
        }
    }
}
