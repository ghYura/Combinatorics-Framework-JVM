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

import org.ghyura.cf.campaign.Campaign;
import org.ghyura.cf.campaign.Progress;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.Diagnostic;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Campaign control without Swing (AN-13 campaign feedback): prepares a campaign through the session, runs it on a thread of
 * its own, and keeps the latest progress of the running iteration (coalesced: the campaign never waits for a view) and
 * every iteration's lineage entry as it is recorded. Pause and resume go to the running iteration, cancel ends the
 * running iteration and starts no further one. A view polls {@link #view()} on its own thread.
 */
final class CampaignController implements AutoCloseable {

    private final java.util.function.Supplier<Session> session;
    private final AtomicReference<Session.PreparedCampaign> current = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Campaign.Result>> finished = new AtomicReference<>();
    private final AtomicReference<Progress> latest = new AtomicReference<>();
    private final AtomicInteger iteration = new AtomicInteger();
    private final List<Campaign.Iteration> recorded = new CopyOnWriteArrayList<>();
    private final List<Consumer<Campaign.Iteration>> listeners = new CopyOnWriteArrayList<>();
    private final List<java.util.function.BiConsumer<Integer, Progress>> progressListeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> report = new AtomicReference<>("");
    private final AtomicReference<Campaign.Result> ended = new AtomicReference<>();
    private boolean closed;

    CampaignController(Session session) {
        this(() -> session);
    }

    CampaignController(java.util.function.Supplier<Session> session) {
        this.session = session;
    }

    /**
     * What a view shows: whether the campaign is going, the running iteration (0 before the first) of how many, its run's
     * state and committed samples, how the campaign ended (null while it goes or when it failed to run), the iterations
     * recorded so far and the final report.
     */
    record View(boolean going, int iteration, int requested, String strategy, RunState state, long committed, Campaign.Status outcome,
                List<Campaign.Iteration> iterations, String report) {
        View {
            iterations = List.copyOf(iterations);
        }
    }

    /** Also tells the listener every progress of the running iteration of this and later campaigns, on the run's threads. */
    void observe(java.util.function.BiConsumer<Integer, Progress> listener) {
        progressListeners.add(listener);
    }

    /** Also tells the listener every recorded iteration of this and later campaigns, on the campaign's thread. */
    void observeIterations(Consumer<Campaign.Iteration> listener) {
        listeners.add(listener);
    }

    /**
     * Prepares and starts a campaign of the scenario; the future completes with its lineage.
     *
     * @throws IllegalStateException while a previous campaign has not ended
     * @throws org.ghyura.cf.model.FrameworkException when the scenario cannot be read or the campaign cannot be prepared
     */
    synchronized CompletableFuture<Campaign.Result> start(Path scenario) {
        if (closed) throw new IllegalStateException("the campaign controller is closed");
        CompletableFuture<Campaign.Result> previous = finished.get();
        if (previous != null && !previous.isDone()) {
            throw new IllegalStateException("a campaign is still going; cancel it or wait for it");
        }
        Session.PreparedCampaign prepared = session.get().prepareCampaign(scenario);
        current.set(prepared);
        latest.set(null);
        iteration.set(0);
        recorded.clear();
        report.set("");
        ended.set(null);
        Campaign campaign = prepared.campaign();
        campaign.observe((index, progress) -> {
            iteration.set(index);
            latest.set(progress);
            for (java.util.function.BiConsumer<Integer, Progress> l : progressListeners) {
                l.accept(index, progress);
            }
        });
        campaign.observeIterations(it -> {
            recorded.add(it);
            for (Consumer<Campaign.Iteration> l : listeners) {
                l.accept(it);
            }
        });
        CompletableFuture<Campaign.Result> future = new CompletableFuture<>();
        finished.set(future);
        Thread.ofVirtual().name("cf-desktop-campaign").start(() -> {
            try {
                Campaign.Result result = campaign.run();
                ended.set(result);
                report.set(text(result, prepared));
                future.complete(result);
            } catch (Throwable failure) {
                report.set("The campaign failed:\n" + Background.describe(failure));
                future.completeExceptionally(failure);
            }
        });
        return future;
    }

    /** The lineage as text: how it ended and why, one line per iteration, then the findings. */
    static String text(Campaign.Result result, Session.PreparedCampaign prepared) {
        StringBuilder text = new StringBuilder(1024);
        text.append("campaign ").append(result.status()).append(": ").append(result.reason()).append('\n');
        text.append("strategy ").append(result.strategy()).append(", ").append(result.iterationsRequested()).append(" iterations requested, ")
                .append("stop when stable ").append(result.stopWhenStable()).append('\n');
        for (Campaign.Iteration it : result.iterations()) {
            text.append(String.format(java.util.Locale.ROOT, "  %d  %s  %s: generated %s, executable %s, samples %d, winners %d, front %d, "
                            + "stable %d; %s%n", it.index(), it.runId(), it.state(), it.generated(), it.executable(), it.samples(), it.winners(),
                    it.frontIds().size(), it.stableTransitions(), it.derivation()));
        }
        for (Diagnostic d : prepared.findings()) {
            text.append(d).append('\n');
        }
        for (Diagnostic d : result.diagnostics()) {
            text.append(d).append('\n');
        }
        return text.toString();
    }

    void pause() {
        Session.PreparedCampaign c = current.get();
        if (c != null) c.campaign().pause();
    }

    void resume() {
        Session.PreparedCampaign c = current.get();
        if (c != null) c.campaign().resume();
    }

    void cancel() {
        Session.PreparedCampaign c = current.get();
        if (c != null) c.campaign().cancel();
    }

    View view() {
        Session.PreparedCampaign c = current.get();
        CompletableFuture<Campaign.Result> f = finished.get();
        Progress p = latest.get();
        RunConfig config = c == null ? null : c.configuration().config();
        return new View(f != null && !f.isDone(), iteration.get(), config == null ? 0 : config.campaignIterations(),
                config == null ? "" : config.campaignStrategy().token(), c == null ? null : c.campaign().state(), p == null ? 0 : p.committed(),
                ended.get() == null ? null : ended.get().status(), recorded, report.get());
    }

    /** Cancels a campaign still going and waits until it has ended within its runs' deadlines. */
    @Override
    public void close() {
        CompletableFuture<Campaign.Result> f;
        synchronized (this) {
            closed = true;
            f = finished.get();
        }
        cancel();
        if (f != null) {
            try {
                f.join();
            } catch (RuntimeException ended) {
                // its failure is in the report already
            }
        }
    }
}
