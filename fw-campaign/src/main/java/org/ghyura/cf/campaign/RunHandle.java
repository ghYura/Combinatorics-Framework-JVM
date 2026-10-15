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

import org.ghyura.cf.analysis.BundleSeed;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.compiler.JavaSourceCompiler;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.execution.ExecutionEngine;
import org.ghyura.cf.execution.ExecutionPolicy;
import org.ghyura.cf.execution.GeneratedJavaTarget;
import org.ghyura.cf.execution.PluginTarget;
import org.ghyura.cf.execution.SutTarget;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyCounts;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Candidate;
import org.ghyura.cf.generation.CandidateRenderer;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.key.LabelWindow;
import org.ghyura.cf.model.spec.CustomVar;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.plan.PlanOptions;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.results.ResultRepository;
import org.ghyura.cf.spi.CancellationSource;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.RunInputs;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One run: plan, key dictionary, generation, assembly, execution and the committed results, owned from start to close
 * (architecture D03). Commands are idempotent: starting twice returns the same result, cancelling a finished run does
 * nothing. The run executes on a thread of its own; {@link #start()} returns at once.
 */
public final class RunHandle implements AutoCloseable {

    /** The most candidates the exact counts may visit before they are reported as not exact. */
    static final BigInteger COUNT_SCAN = BigInteger.valueOf(10_000_000L);

    private final RunId id;
    private final ScenarioSpec scenario;
    private final ImportedCandidates imported;
    private final RunConfig config;
    private final SutResolver resolver;
    private final SutPlugin plugin;
    private final CancellationSource cancel = new CancellationSource();
    private final AtomicReference<RunState> state = new AtomicReference<>(RunState.READY);
    private final CompletableFuture<RunSummary> result = new CompletableFuture<>();
    private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong committed = new AtomicLong();
    private final AtomicLong listenerFailures = new AtomicLong();
    private volatile ExecutionEngine engine;
    private volatile Thread thread;
    private volatile List<Observation> observations = List.of();
    private volatile List<Observation> finals = List.of();
    private volatile List<ResultRepository.Attachment> attachments = List.of();
    private MemoryBudget.Lease cacheLease;
    private MemoryBudget.Lease analysisLease;
    private volatile MemoryBudget budget;
    private volatile boolean budgetExhausted;
    private volatile org.ghyura.cf.analysis.AnalysisReport analysis;
    private volatile BundleSeed seed;
    /** Value offsets from the dictionary's first label; logical labels themselves may be negative. */
    private final java.util.BitSet exercised = new java.util.BitSet();
    private volatile List<RunSummary.Axis> axes = List.of();
    private volatile long restoredAttempts;
    private volatile long restoredSamples;
    /** The run's one stop owner (AN-10), made when the results exist. */
    private volatile StopPolicy stop;
    private volatile boolean seedRequested;
    private Map<String, String> configuration = Map.of();
    private org.ghyura.cf.results.Checkpoint restore;
    private volatile java.util.function.BiConsumer<org.ghyura.cf.results.Checkpoint, MemoryBudget> checkpointSink;
    private volatile String scenarioPath = "";
    private volatile ResultRepository liveResults;
    private volatile String livePlanDigest = "";
    private volatile String dictionaryDigest = "";
    private volatile SutTarget liveTarget;
    private boolean endCheckpointWritten;
    private final List<Diagnostic> lateFindings = new CopyOnWriteArrayList<>();

    RunHandle(RunId id, ScenarioSpec scenario, RunConfig config, SutResolver resolver, SutPlugin plugin) {
        this(id, scenario, null, config, resolver, plugin);
    }

    RunHandle(RunId id, ScenarioSpec scenario, ImportedCandidates imported, RunConfig config, SutResolver resolver, SutPlugin plugin) {
        this.id = id;
        this.imported = imported;
        this.scenario = scenario;
        this.config = config;
        this.resolver = resolver;
        this.plugin = plugin;
        this.budget = MemoryBudget.of(config);
    }

    /** Campaign iterations share admission with retained reports and checkpoint buffers. */
    RunHandle useMemoryBudget(MemoryBudget shared) {
        if (state.get() != RunState.READY) throw new IllegalStateException("set the budget before starting");
        budget = java.util.Objects.requireNonNull(shared, "shared");
        return this;
    }

    public RunId id() {
        return id;
    }

    /** The configuration a checkpoint records, and the checkpoint a resumed run restores (null for a fresh run). */
    void identity(Map<String, String> configuration, org.ghyura.cf.results.Checkpoint restore) {
        this.configuration = Map.copyOf(configuration);
        this.restore = restore;
    }

    /**
     * Hands a quiescent checkpoint to {@code sink} whenever the run is paused and drained, and once when it ends; call before
     * {@link #start()}. A sink that throws is reported (CHECKPOINT_WRITE_FAILED) and the run goes on. Only a scenario run has
     * checkpoints.
     *
     * @param scenarioPath the scenario file a resume reads again
     */
    public void checkpointTo(String scenarioPath, java.util.function.Consumer<org.ghyura.cf.results.Checkpoint> sink) {
        java.util.Objects.requireNonNull(sink, "sink");
        checkpointWithBudgetTo(scenarioPath, (checkpoint, memory) -> sink.accept(checkpoint));
    }

    /** A synchronous sink uses the same budget as the live run; reservations must be released before it returns. */
    public void checkpointWithBudgetTo(String scenarioPath,
                             java.util.function.BiConsumer<org.ghyura.cf.results.Checkpoint, MemoryBudget> sink) {
        if (config.resultsRetention() == RunConfig.ResultsRetention.AGGREGATE_ONLY)
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT,
                    "aggregate-only retention cannot create an exact checkpoint", SourceLocation.NONE));
        if (imported != null) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.UNSUPPORTED_CAPABILITY, "checkpoints cover scenario runs; an "
                    + "imported legacy hand-over is run again from its files instead", SourceLocation.NONE));
        }
        this.scenarioPath = java.util.Objects.requireNonNull(scenarioPath, "scenarioPath");
        this.checkpointSink = sink;
    }

    /** Writes a checkpoint of what is committed now: at a drained pause, or once at the end (a pause after the end is ignored). */
    @SuppressWarnings("try")
    private synchronized void writeCheckpoint(RunState at, ResultRepository repository) {
        java.util.function.BiConsumer<org.ghyura.cf.results.Checkpoint, MemoryBudget> sink = checkpointSink;
        ExecutionEngine e = engine;
        SutTarget t = liveTarget;
        if (sink == null || e == null || t == null || livePlanDigest.isEmpty() || endCheckpointWritten) {
            return;
        }
        endCheckpointWritten = at.terminal();
        long references = repository.checkpointReferences();
        try (MemoryBudget.Lease lease = budget.reserve(MemorySizes.add(65_536, MemorySizes.add(
                MemorySizes.multiply(32, references), MemorySizes.add(MemorySizes.retained(configuration),
                MemorySizes.multiply(32, MemorySizes.retained(scenario))))), "checkpoint snapshot references and identity")) {
            sink.accept(new org.ghyura.cf.results.Checkpoint(id.value(), at.name(), scenarioPath, scenario.contentDigest(), livePlanDigest,
                    dictionaryDigest, e.policyDigest(), pluginId(), t.version(), configuration, repository.all(),
                    repository.allAttachments()), budget);
        } catch (RuntimeException ex) {
            if (ex instanceof FrameworkException failure) {
                lateFindings.addAll(failure.diagnostics());
                if (failure.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)) checkpointSink = null;
            }
            lateFindings.add(Diagnostic.error(ErrorCode.CHECKPOINT_WRITE_FAILED, "the " + at + " checkpoint could not be written: "
                    + ex.getMessage() + "; the last good checkpoint was kept and the committed observations are unchanged",
                    SourceLocation.of(scenarioPath)));
        }
    }

    private String pluginId() {
        return plugin != null ? plugin.id() : RunConfig.GENERATED_JAVA;
    }

    public RunState state() {
        return state.get();
    }

    /** Starts the run on its own thread; later calls return the same stage. */
    public synchronized CompletionStage<RunSummary> start() {
        if (thread == null && state.get() == RunState.READY) {
            Thread t = Thread.ofPlatform().name("cf-run-" + id).unstarted(() -> result.complete(execute()));
            thread = t;
            t.start();
        }
        return result;
    }

    /** Starts the run if needed and waits for its summary. */
    public RunSummary await() {
        return start().toCompletableFuture().join();
    }

    /** Stops admitting candidates: PAUSING while admitted samples finish, then PAUSED. */
    public void pause() {
        ExecutionEngine e = engine;
        if (e != null && state.compareAndSet(RunState.RUNNING, RunState.PAUSING)) {
            notifyListeners();
            e.pause(() -> {
                if (state.compareAndSet(RunState.PAUSING, RunState.PAUSED)) {
                    ResultRepository r = liveResults;
                    if (r != null) {
                        writeCheckpoint(RunState.PAUSED, r);
                    }
                    notifyListeners();
                }
            });
        }
    }

    public void resume() {
        ExecutionEngine e = engine;
        if (e != null && (state.compareAndSet(RunState.PAUSED, RunState.RUNNING) || state.compareAndSet(RunState.PAUSING, RunState.RUNNING))) {
            e.resume();
            notifyListeners();
        }
    }

    /** Requests cooperative cancellation; idempotent, harmless after the run ended. */
    public void cancel() {
        cancel.cancel();
        RunState s = state.get();
        if (!s.terminal() && s != RunState.READY) {
            state.compareAndSet(s, RunState.CANCELLING);
        }
        if (state.compareAndSet(RunState.READY, RunState.CANCELLED)) {
            result.complete(summary(RunState.CANCELLED, "", null, null, null, null, 0, List.of(Diagnostic.info(ErrorCode.RUN_CANCELLED,
                    "the run was cancelled before it started", SourceLocation.NONE))));
        }
    }

    /** Adds a listener; closing the returned handle removes it. */
    public AutoCloseable observe(ProgressListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Every committed attempt, in commit order, once the run has ended. */
    public List<Observation> observations() {
        return observations;
    }

    /** The attachments the run kept (candidate sources by cf.results.attach), in commit order, once the run has ended. */
    public List<ResultRepository.Attachment> attachments() {
        return attachments;
    }

    /** The final observation of every sample, once the run has ended. */
    public List<Observation> finals() {
        return finals;
    }

    /** Cancels a run that is still going, waits for it within its deadlines and releases what the run holds. */
    @Override
    public void close() {
        Thread t = thread;
        if (t != null && !result.isDone()) {
            cancel();
            try {
                result.get(config.timeoutMillis() + 2 * config.cancelGraceMillis() + 5_000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // the summary says what happened; an uncooperative sample is reported there, not stopped here
            }
        }
        resolver.close();
    }

    private void transition(RunState to) {
        state.updateAndGet(s -> s.terminal() || s == RunState.CANCELLING ? s : to);
        notifyListeners();
    }

    /** Starts the run's time budget (cf.run.budget.millis): when it passes first, the run is cancelled cooperatively. */
    private void startBudget() {
        long millis = config.runBudgetMillis();
        if (millis <= 0) {
            return;
        }
        Thread.ofPlatform().daemon().name("cf-budget-" + id).start(() -> {
            try {
                result.get(millis, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                budgetExhausted = true;
                cancel();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException e) {
                // the run ended on its own
            }
        });
    }

    private void notifyListeners() {
        Progress p = new Progress(id, state.get(), committed.get());
        for (ProgressListener l : listeners) {
            try {
                l.onProgress(p);
            } catch (RuntimeException e) {
                listenerFailures.incrementAndGet();
            }
        }
    }

    @SuppressWarnings("try")
    private RunSummary execute() {
        long started = System.nanoTime();
        List<Diagnostic> findings = new ArrayList<>();
        String planDigest = "";
        AssemblyCounts counts = null;
        ResultRepository results = null;
        ExecutionEngine.Report report = null;
        SutTarget target = null;
        transition(RunState.VALIDATING);
        if (imported != null) {
            return executeImported(started, findings);
        }
        try (MemoryBudget.Lease restored = budget.reserve(restore == null ? 0 : MemorySizes.retained(restore),
                "restored checkpoint state")) {
            SeqPlanner.Planned planned = SeqPlanner.plan(scenario, PlanOptions.of(config));
            findings.addAll(planned.diagnostics());
            SeqPlan plan = planned.plan();
            planDigest = plan.contentDigest();
            MemoryBudget budget = this.budget;
            GenerationOptions generation = GenerationOptions.of(config);
            RenderOptions render = RenderOptions.of(config);
            try (MemoryBudget.Lease planLease = budget.reserve(MemorySizes.retained(plan), "compiled run plan");
                 KeyDictionary dictionary = KeyDictionary.build(scenario, (int) plan.virtualSheetCount(), config.keyEncodingPolicy(),
                    labelMode(config), windowScope(config), budget);
                 Generation.Result generated = Generation.run(plan, dictionary, generation, budget, cancel);
                 Assembly assembly = Assembly.of(plan, generated.result(), dictionary, generation, AssemblyOptions.of(config), render,
                         config.randomSeed(), budget, cancel);
                 CandidateRenderer renderer = CandidateRenderer.of(assembly, plan, dictionary, render, budget)) {
                findings.addAll(generated.result().diagnostics());
                findings.addAll(assembly.diagnostics());
                counts = assembly.count(cancel, COUNT_SCAN);
                target = target(budget);
                results = repository(budget);
                ResultRepository repository = results;
                stop = new StopPolicy(config, o -> repository.isMeasurement(o.key()));
                ExecutionEngine e = new ExecutionEngine(target, ExecutionPolicy.of(config), results, budget, planDigest);
                if (restore != null) {
                    restore(restore, plan, dictionary, e, target, results);
                }
                livePlanDigest = planDigest;
                dictionaryDigest = dictionary.identity();
                liveTarget = target;
                liveResults = results;
                engine = e;
                transition(RunState.RUNNING);
                startBudget();
                report = e.runOrdered(candidates(assembly, dictionary, renderer), cancel, (ordinal, o) -> {
                    stop.observe(ordinal + 1, o);
                    committed.incrementAndGet();
                    notifyListeners();
                });
                stop.delivered();
                axes = axes(dictionary, assembly.columnSheets(), joinTargets(plan));
                return afterRun(planDigest, counts, results, report, target, started, findings,
                        ids -> selections(assembly, dictionary, renderer, ids));
            }
        } catch (FrameworkException e) {
            findings.addAll(e.diagnostics());
            boolean cancelled = e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RUN_CANCELLED);
            return finish(cancelled ? RunState.CANCELLED : RunState.FAILED, planDigest, counts, results, report, target, started, findings);
        } catch (RuntimeException e) {
            findings.add(Diagnostic.error(ErrorCode.INTERNAL_ERROR, "the run failed unexpectedly: " + e, SourceLocation.NONE));
            return finish(RunState.FAILED, planDigest, counts, results, report, target, started, findings);
        } finally {
            restore = null;
            if (analysisLease != null) analysisLease.close();
            if (results != null) {
                results.close();
            }
            if (target != null) {
                target.close();
            }
            if (cacheLease != null) {
                cacheLease.close();
            }
        }
    }

    /**
     * Restores a checkpoint into a fresh result repository once the run is planned: everything that identifies the run must
     * be what the checkpoint recorded, or nothing is restored and nothing runs.
     *
     * @throws FrameworkException CHECKPOINT_MISMATCH
     */
    private void restore(org.ghyura.cf.results.Checkpoint c, SeqPlan plan, KeyDictionary dictionary, ExecutionEngine e, SutTarget target,
                         ResultRepository results) {
        List<String> differences = c.mismatches(scenario.contentDigest(), plan.contentDigest(), dictionary.identity(), e.policyDigest(),
                pluginId(), target.version(), org.ghyura.cf.results.Checkpoint.configDigest(configuration));
        if (!differences.isEmpty()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CHECKPOINT_MISMATCH, "the checkpoint of run '" + c.runId()
                    + "' does not belong to this run; nothing was resumed. Different: " + String.join("; ", differences),
                    SourceLocation.of(c.scenarioPath())));
        }
        for (Observation o : c.observations()) {
            results.commit(o);
        }
        restoredAttempts = c.observations().size();
        restoredSamples = results.totals().samples() + results.totals().measurements();
        stop.restored(c.observations());
        for (ResultRepository.Attachment a : c.attachments()) {
            results.attach(a.key(), a.name(), a.text());
        }
    }

    private ResultRepository repository(MemoryBudget budget) {
        return new ResultRepository(id, config.resultsMaxSamples(), budget, config.repeatScope() == RunConfig.RepeatScope.METRICS
                ? ResultRepository.Scope.METRICS : ResultRepository.Scope.ALL, config.resultsMaxAttachmentBytes(),
                config.resultsRetention() == RunConfig.ResultsRetention.AGGREGATE_ONLY ? ResultRepository.Retention.AGGREGATE_ONLY
                        : ResultRepository.Retention.FULL);
    }

    /** Runs imported candidates: nothing is planned, generated or assembled; execution, results and summary are shared. */
    private RunSummary executeImported(long started, List<Diagnostic> findings) {
        findings.addAll(imported.diagnostics());
        BigInteger n = BigInteger.valueOf(imported.candidates().size());
        AssemblyCounts counts = new AssemblyCounts(true, n, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, n, null, BigInteger.ZERO);
        ResultRepository results = null;
        ExecutionEngine.Report report = null;
        SutTarget target = null;
        try {
            MemoryBudget budget = this.budget;
            if (config.resultsRetention() == RunConfig.ResultsRetention.AGGREGATE_ONLY) {
                MemoryBudget.Lease checked = budget.reserve(96L * imported.candidates().size(), "imported candidate identity check");
                try {
                    Set<org.ghyura.cf.model.CandidateId> ids = new java.util.HashSet<>();
                    for (SutCandidate candidate : imported.candidates()) {
                        if (!ids.add(candidate.id())) throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_CONFLICT,
                                "aggregate-only requires distinct imported candidate ids: " + candidate.id(), SourceLocation.NONE));
                    }
                } finally { checked.close(); }
            }
            target = target(budget);
            results = repository(budget);
            ResultRepository repository = results;
            stop = new StopPolicy(config, o -> repository.isMeasurement(o.key()));
            ExecutionEngine e = new ExecutionEngine(target, ExecutionPolicy.of(config), results, budget, imported.digest());
            engine = e;
            transition(RunState.RUNNING);
            startBudget();
            Iterator<? extends SutCandidate> list = imported.candidates().iterator();
            report = e.runOrdered(new Iterator<SutCandidate>() {
                @Override
                public boolean hasNext() {
                    boolean more = !cancel.isCancelled() && list.hasNext() && !stop.stopsAdmission();
                    if (!more && !cancel.isCancelled()) {
                        transition(RunState.DRAINING);
                    }
                    return more;
                }

                @Override
                public SutCandidate next() {
                    stop.admit();
                    return list.next();
                }
            }, cancel, (ordinal, o) -> {
                stop.observe(ordinal + 1, o);
                committed.incrementAndGet();
                notifyListeners();
            });
            stop.delivered();
            return afterRun(imported.digest(), counts, results, report, target, started, findings, ids -> Map.of());
        } catch (FrameworkException e) {
            findings.addAll(e.diagnostics());
            boolean cancelled = e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RUN_CANCELLED);
            return finish(cancelled ? RunState.CANCELLED : RunState.FAILED, imported.digest(), counts, results, report, target, started,
                    findings);
        } catch (RuntimeException e) {
            findings.add(Diagnostic.error(ErrorCode.INTERNAL_ERROR, "the run failed unexpectedly: " + e, SourceLocation.NONE));
            return finish(RunState.FAILED, imported.digest(), counts, results, report, target, started, findings);
        } finally {
            if (analysisLease != null) analysisLease.close();
            if (results != null) {
                results.close();
            }
            if (target != null) {
                target.close();
            }
            if (cacheLease != null) {
                cacheLease.close();
            }
        }
    }

    /** What every run reports after its engine returned: abandoned threads, listener failures, omitted attachments, budget. */
    @SuppressWarnings("try")
    private RunSummary afterRun(String planDigest, AssemblyCounts counts, ResultRepository results, ExecutionEngine.Report report,
                                SutTarget target, long started, List<Diagnostic> findings,
                                Function<Set<String>, Map<String, Map<String, List<String>>>> selections) {
        if (report.abandonedThreads() > 0) {
            findings.add(Diagnostic.warning(ErrorCode.EXEC_ABANDONED_THREADS, report.abandonedThreads() + " samples ignored their "
                    + "deadline or the cancellation; their threads were interrupted and left running, "
                    + report.stillRunning().size() + " still alive at the end: " + report.stillRunning(), SourceLocation.NONE));
        }
        if (target instanceof GeneratedJavaTarget g && g.coverageOmitted() > 0) {
            findings.add(Diagnostic.warning(ErrorCode.EXEC_COVERAGE_OMITTED, g.coverageOmitted() + " verdict samples have no line "
                    + "coverage (cf.execution.coverage=lines): " + g.coverageNote(), SourceLocation.NONE, "key", "cf.execution.coverage"));
        }
        StopPolicy.Reason decided = stop == null ? null : stop.decided();
        if (decided == StopPolicy.Reason.ERROR_BUDGET) {
            findings.add(Diagnostic.error(ErrorCode.RUN_ERROR_BUDGET_EXHAUSTED, stop.detail() + "; admission ended after candidate "
                    + stop.boundary() + " in candidate order, " + report.admittedCandidates() + " candidates were admitted",
                    SourceLocation.NONE, "key", "cf.run.stop.errors"));
        } else if (decided != null) {
            findings.add(Diagnostic.info(ErrorCode.RUN_STOPPED, stop.detail() + "; admission ended after candidate " + stop.boundary()
                    + " in candidate order, " + report.admittedCandidates() + " candidates were admitted", SourceLocation.NONE));
        }
        if (report.listenerFailures() > 0) {
            findings.add(Diagnostic.warning(ErrorCode.RESULT_LISTENER_FAILED, report.listenerFailures() + " committed observations "
                    + "were never taken by the run's listener after " + config.listenerMaxAttempts() + " deliveries each; the results "
                    + "and totals are complete. First: " + report.firstListenerFailure(), SourceLocation.NONE));
        }
        ResultRepository.AttachmentTotals attached = results.attachmentTotals();
        if (attached.omitted() > 0) {
            findings.add(Diagnostic.warning(ErrorCode.RESULT_ATTACHMENT_OMITTED, attached.omitted() + " attachments ("
                    + attached.omittedBytes() + " bytes) " + (config.resultsRetention() == RunConfig.ResultsRetention.AGGREGATE_ONLY
                    ? "were omitted by aggregate-only retention" : "did not fit cf.results.maxAttachmentBytes = " + attached.maxBytes())
                    + " and were not kept; " + attached.kept() + " were", SourceLocation.NONE));
        }
        if (budgetExhausted) {
            findings.add(Diagnostic.warning(ErrorCode.RUN_BUDGET_EXHAUSTED, "the run's time budget of " + config.runBudgetMillis()
                    + " ms passed: admission stopped after " + report.admittedCandidates() + " candidates and running samples "
                    + "were cancelled", SourceLocation.NONE));
        }
        if (config.analysisEnabled()) {
            // the analysis reads the committed final observations; its failure is reported apart from them
            try (MemoryBudget.Lease snapshot = budget.reserve(MemorySizes.add(1024,
                    MemorySizes.multiply(32, results.checkpointReferences())), "analysis final-observation snapshot")) {
                var produced = org.ghyura.cf.analysis.Analysis.analyzeObservations(results.finals(),
                        org.ghyura.cf.analysis.Analysis.Options.of(config), budget);
                // Keep the report charged while seed/checkpoint/summary work still shares this run's budget.
                // On return, the completed summary belongs to the caller; a campaign admits its retained history.
                analysisLease = budget.reserve(MemorySizes.retained(produced), "retained run analysis report");
                analysis = produced;
            } catch (FrameworkException e) {
                findings.addAll(e.diagnostics());
            }
        }
        RunState end = cancel.isCancelled() || report.cancelled() ? RunState.CANCELLED : RunState.COMPLETED;
        if (end == RunState.COMPLETED && analysis != null && seedWanted()) {
            seed = BundleSeed.of(analysis, selections.apply(BundleSeed.winnerIds(analysis)), id.value(), System.currentTimeMillis());
            if (!config.campaignSeedOutput().isEmpty()) {
                Path out = Path.of(config.campaignSeedOutput());
                try {
                    AtomicFiles.write(out, seed.toJson().getBytes(StandardCharsets.UTF_8));
                } catch (IOException | RuntimeException e) {
                    findings.add(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the run's seed could not be written to " + out + ": "
                            + e.getMessage() + "; the committed observations and the analysis are unchanged", SourceLocation.of(out.toString()),
                            "key", "cf.campaign.seed.output"));
                }
            }
        }
        return finish(end, planDigest, counts, results, report, target, started, findings);
    }

    /** A seed is built when it is written (cf.campaign.seed.output) or a campaign derives its next iteration from it. */
    private boolean seedWanted() {
        return seedRequested || !config.campaignSeedOutput().isEmpty();
    }

    /** Asks for the run's seed even when cf.campaign.seed.output names no file (a campaign reads it); before start. */
    void requestSeed() {
        if (config.resultsRetention() == RunConfig.ResultsRetention.AGGREGATE_ONLY) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT,
                    "a campaign seed needs full retention", SourceLocation.NONE));
        }
        seedRequested = true;
    }

    /**
     * The sheet selections of the candidates with these ids, found by walking the assembly again (the run keeps no candidate
     * once it is delivered). A sheet the candidate leaves absent or empty has no entry; a sheet in several columns lists all.
     */
    private Map<String, Map<String, List<String>>> selections(Assembly assembly, KeyDictionary dictionary, CandidateRenderer renderer,
                                                              Set<String> ids) {
        Map<String, Map<String, List<String>>> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        List<String> sheets = assembly.columnSheets();
        Iterator<Candidate> walk = assembly.candidates(cancel);
        while (out.size() < ids.size() && !cancel.isCancelled() && walk.hasNext()) {
            Candidate c = walk.next();
            String hex = c.id().hex();
            if (!ids.contains(hex) || out.containsKey(hex)) {
                continue;
            }
            Map<String, List<String>> bySheet = new LinkedHashMap<>();
            for (org.ghyura.cf.spi.SheetSelection s : new AssembledCandidate(c, sheets, dictionary, renderer).selections()) {
                if (!s.values().isEmpty()) {
                    bySheet.computeIfAbsent(s.sheet(), k -> new ArrayList<>()).addAll(s.values());
                }
            }
            out.put(hex, bySheet);
        }
        return out;
    }

    private RunSummary finish(RunState end, String planDigest, AssemblyCounts counts, ResultRepository results,
                              ExecutionEngine.Report report, SutTarget target, long started, List<Diagnostic> findings) {
        if (results != null) {
            observations = results.all();
            finals = results.finals();
            attachments = results.allAttachments();
            if (results == liveResults) {
                writeCheckpoint(end, results);
            }
        }
        findings.addAll(lateFindings);
        state.updateAndGet(s -> s.terminal() ? s : end);
        RunSummary summary = summary(end, planDigest, counts, results, report, target, System.nanoTime() - started, findings);
        notifyListeners();
        return summary;
    }

    /**
     * The sheets whose first directive is a joiner: their rows are built from the joiner's operands, never from their own
     * values (the first pass builds a table from the sheet's values only when it is a combination).
     */
    private static java.util.Set<String> joinTargets(SeqPlan plan) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (org.ghyura.cf.plan.SheetPlan sheet : plan.sheets()) {
            if (!sheet.directives().isEmpty() && sheet.directives().getFirst() instanceof org.ghyura.cf.plan.Directive.Joiner) {
                out.add(sheet.name());
            }
        }
        return out;
    }

    /**
     * Per data sheet the candidates read (the column sheets, and any sheet a held value belongs to), how many of its
     * values the admitted candidates held; computed while the dictionary is open. A joiner target's own values are not
     * among them: its column holds the rows the joiner builds, whose values belong to the operand sheets.
     */
    private List<RunSummary.Axis> axes(KeyDictionary dictionary, List<String> columnSheets, java.util.Set<String> joinTargets) {
        java.util.LinkedHashSet<Integer> keys = new java.util.LinkedHashSet<>();
        for (String name : columnSheets) {
            if (!joinTargets.contains(name)) {
                scenario.sheet(name).ifPresent(sheet -> keys.add(sheet.key()));
            }
        }
        for (int offset = exercised.nextSetBit(0); offset >= 0; offset = exercised.nextSetBit(offset + 1)) {
            keys.add(dictionary.sheetOfLabel(dictionary.firstLabel() + offset));
        }
        List<RunSummary.Axis> out = new ArrayList<>();
        for (int key : keys) {
            int[] labels = dictionary.labelsOf(key);
            int held = 0;
            List<String> missed = new ArrayList<>();
            for (int label : labels) {
                if (exercised.get(label - dictionary.firstLabel())) {
                    held++;
                } else if (missed.size() < 5) {
                    String text = dictionary.value(label).text().strip();
                    missed.add(text.length() > 40 ? text.substring(0, 40) + "..." : text);
                }
            }
            out.add(new RunSummary.Axis(scenario.sheetByKey(key).map(SheetSpec::name).orElse("#" + key), labels.length, held, missed));
        }
        return out;
    }

    private RunSummary summary(RunState end, String planDigest, AssemblyCounts counts, ResultRepository results,
                               ExecutionEngine.Report report, SutTarget target, long elapsedNanos, List<Diagnostic> findings) {
        RunSummary summary = plainSummary(end, planDigest, counts, results, report, target, elapsedNanos, findings);
        Reconciliation reconciliation = summary.reconciliation();
        if (reconciliation.consistent()) {
            return summary;
        }
        List<Diagnostic> withFailure = new ArrayList<>(findings);
        withFailure.add(Diagnostic.error(ErrorCode.RUN_RECONCILIATION_FAILED, "the run's totals do not reconcile: " + String.join("; ",
                reconciliation.failures().stream().filter(c -> c.kind() == Reconciliation.Kind.CONSISTENCY)
                        .map(c -> c.id() + " (" + c.rule() + ": expected " + c.expected() + ", found " + c.actual() + ")").toList())
                + "; this is a defect of the framework, the observations are kept as committed", SourceLocation.NONE));
        return plainSummary(end, planDigest, counts, results, report, target, elapsedNanos, withFailure);
    }

    private RunSummary plainSummary(RunState end, String planDigest, AssemblyCounts counts, ResultRepository results,
                                    ExecutionEngine.Report report, SutTarget target, long elapsedNanos, List<Diagnostic> findings) {
        ResultRepository.Totals totals = results == null ? null : results.totals();
        long compilations = 0;
        long hits = 0;
        if (target instanceof GeneratedJavaTarget g) {
            JavaSourceCompiler.CacheStats stats = g.cacheStats();
            compilations = stats.misses();
            hits = stats.hits();
        }
        Map<Integer, String> names = target != null ? target.verdictNames() : (plugin != null ? plugin.verdictNames() : customVarNames());
        return new RunSummary(id, end, imported != null ? imported.source() : scenario.source(),
                imported != null ? imported.digest() : scenario.contentDigest(), planDigest,
                plugin != null ? plugin.id() : RunConfig.GENERATED_JAVA, target != null ? target.version() : (plugin != null ? plugin.version() : ""),
                engine != null ? engine.policyDigest() : "", counts != null && counts.exact(),
                counts == null ? BigInteger.ZERO : counts.generated(), counts == null || !counts.exact() ? BigInteger.ZERO : counts.excluded(),
                counts == null || !counts.exact() ? BigInteger.ZERO : counts.unselected(),
                counts == null || !counts.exact() ? BigInteger.ZERO : counts.executable(),
                report == null ? 0 : report.admittedCandidates(), totals == null ? 0 : totals.samples(), totals == null ? 0 : totals.attempts(),
                totals == null ? 0 : totals.retried(), totals == null ? Map.of() : totals.outcomes(), totals == null ? Map.of() : totals.verdicts(),
                names, report == null ? 0 : report.abandonedThreads(), report == null ? List.of() : report.stillRunning(), compilations, hits,
                TimeUnit.NANOSECONDS.toMillis(elapsedNanos), findings, execution(totals, results, report), java.util.Optional.ofNullable(analysis),
                java.util.Optional.ofNullable(seed), stopOf(end, report));
    }

    /**
     * The one reason admission ended: a failure, else a stop-policy decision, else the time budget, else a cancellation,
     * else the end of the executable candidates.
     */
    private RunSummary.Stop stopOf(RunState end, ExecutionEngine.Report report) {
        long admitted = report == null ? 0 : report.admittedCandidates();
        StopPolicy policy = stop;
        StopPolicy.Reason decided = policy == null ? null : policy.decided();
        if (end == RunState.FAILED) {
            return new RunSummary.Stop(StopPolicy.Reason.FAILED, -1, admitted, "the run failed");
        }
        if (decided != null) {
            return new RunSummary.Stop(decided, policy.boundary(), admitted, policy.detail());
        }
        if (budgetExhausted) {
            return new RunSummary.Stop(StopPolicy.Reason.TIME_BUDGET, -1, admitted, "cf.run.budget.millis = " + config.runBudgetMillis() + " passed");
        }
        if (end == RunState.CANCELLED) {
            return new RunSummary.Stop(StopPolicy.Reason.CANCELLED, -1, admitted, "the run was cancelled");
        }
        return new RunSummary.Stop(StopPolicy.Reason.EXHAUSTED, admitted, admitted, "every executable candidate was admitted");
    }

    private RunSummary.Execution execution(ResultRepository.Totals totals, ResultRepository results, ExecutionEngine.Report report) {
        ResultRepository.AttachmentTotals attached = results == null ? null : results.attachmentTotals();
        MemoryBudget b = budget;
        return new RunSummary.Execution(config.environments(), config.repeatPolicy().token(), config.repeatScope().token(),
                totals == null ? 0 : totals.measurements(), totals == null ? Map.of() : totals.measurementOutcomes(),
                attached == null ? 0 : attached.kept(), attached == null ? 0 : attached.keptBytes(), attached == null ? 0 : attached.omitted(),
                report == null ? 0 : report.deliveries(), report == null ? 0 : report.listenerRetries(),
                report == null ? 0 : report.listenerFailures(), report == null ? 0 : report.peakQueuedBytes(),
                report == null ? 0 : report.peakWindow(), b == null ? 0 : b.highWaterBytes(), config.memoryBudgetBytes(), budgetExhausted,
                config.resultsRetention().token(), report == null ? restoredSamples
                        : report.admittedSamples() + restoredSamples - engine.restoredSamplesRevisited(), restoredAttempts, axes);
    }

    private SutTarget target(MemoryBudget budget) {
        if (plugin != null) {
            if (config.coverage() == RunConfig.Coverage.LINES) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.UNSUPPORTED_CAPABILITY, "cf.execution.coverage=lines instruments "
                        + "generated Java candidates only; the plugin " + plugin.id() + " runs code this build does not instrument",
                        SourceLocation.NONE, "key", "cf.execution.coverage"));
            }
            SutPlugin forRun;
            try {
                forRun = plugin.forRun(runInputs());
            } catch (FrameworkException e) {
                throw e;
            } catch (Exception e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.EXEC_PREPARE_FAILED, "the plugin " + plugin.id()
                        + " could not prepare the run: " + e.getMessage(), SourceLocation.NONE, "plugin", plugin.id()));
            }
            return new PluginTarget(forRun == null ? plugin : forRun);
        }
        // the bytecode cache can grow to its bound: that much is reserved from the run's budget before anything compiles
        cacheLease = config.compilerCacheMaxBytes() > 0 ? budget.reserve(config.compilerCacheMaxBytes(), "compiler cache") : null;
        JavaSourceCompiler compiler = JavaSourceCompiler.create(config.executionClasspath(), config.compilerCacheMaxBytes());
        Map<Integer, String> carriers = new TreeMap<>();
        if (scenario != null) {
            for (SheetSpec sheet : scenario.sheets()) {
                if (sheet.exitCodeVarCount() > 0) {
                    carriers.put(sheet.key(), sheet.name());
                }
            }
        }
        GeneratedJavaTarget g = new GeneratedJavaTarget(compiler, config.executionVerdict(), customVarNames(), carriers,
                imported != null ? imported.arguments() : scenario.controls().arguments().stream().map(LocatedText::text).toList(),
                config.executionClasspath(), config.coverage());
        List<LocatedText> once = imported != null
                ? imported.runFirstOnce().map(text -> List.of(new LocatedText(text, SourceLocation.of(imported.source())))).orElse(List.of())
                : scenario.controls().runMeFirstOnce();
        if (!once.isEmpty()) {
            try {
                g.runFirstOnce(once.getFirst().text(), once.getFirst().location());
            } catch (RuntimeException e) {
                g.close();
                throw e;
            }
        }
        return g;
    }

    /** The scenario's legacy run inputs and the plugins' own settings, told to the plugin once before the first sample. */
    private RunInputs runInputs() {
        List<String> arguments = imported != null ? imported.arguments()
                : scenario.controls().arguments().stream().map(LocatedText::text).toList();
        java.util.Optional<String> once = imported != null ? imported.runFirstOnce()
                : scenario.controls().runMeFirstOnce().stream().map(LocatedText::text).findFirst();
        String source = imported != null ? imported.source() : scenario.source();
        java.util.Optional<java.nio.file.Path> directory = java.util.Optional.empty();
        try {
            java.nio.file.Path file = java.nio.file.Path.of(source);
            if (java.nio.file.Files.isRegularFile(file)) {
                directory = java.util.Optional.of(file.toAbsolutePath().normalize().getParent());
            }
        } catch (java.nio.file.InvalidPathException e) {
            // a logical name, not a file: no directory
        }
        Map<String, String> settings = new TreeMap<>();
        settings.put("cf.python.executable", config.pythonExecutable());
        settings.put("cf.python.path", String.join(java.io.File.pathSeparator, config.pythonPath()));
        settings.put("cf.python.workdir", config.pythonWorkdir());
        settings.put("cf.python.sandbox", config.pythonSandbox());
        settings.put("cf.python.env", String.join(";", config.pythonEnv()));
        return new RunInputs(arguments, once, config.executionVerdict().token(), customVarNames(), directory, settings);
    }

    private Map<Integer, String> customVarNames() {
        if (imported != null) {
            return imported.verdictNames();
        }
        Map<Integer, String> names = new TreeMap<>();
        for (CustomVar v : scenario.controls().customVars()) {
            names.putIfAbsent(v.index(), v.declaration());
        }
        return names;
    }

    /** The assembled candidates as SUT candidates; a cancellation ends the sequence instead of failing it. */
    private Iterator<SutCandidate> candidates(Assembly assembly, KeyDictionary dictionary, CandidateRenderer renderer) {
        Iterator<Candidate> walk = assembly.candidates(cancel);
        List<String> sheets = assembly.columnSheets();
        return new Iterator<>() {
            private boolean ready;

            @Override
            public boolean hasNext() {
                // Once a candidate is offered, next() must honour it even if delivery decides to stop meanwhile.
                if (ready) {
                    return true;
                }
                if (cancel.isCancelled()) {
                    return false;
                }
                try {
                    // the stop policy ends admission before a candidate that exists, so the admitted ones are a prefix
                    boolean more = walk.hasNext() && !stop.stopsAdmission();
                    ready = more;
                    if (!more && !cancel.isCancelled()) {
                        transition(RunState.DRAINING);
                    }
                    return more;
                } catch (FrameworkException e) {
                    if (cancel.isCancelled()) {
                        return false;
                    }
                    throw e;
                }
            }

            @Override
            public SutCandidate next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Candidate candidate = walk.next();
                ready = false;
                stop.admit();
                for (int column = 0; column < sheets.size(); column++) {
                    int[] labels = candidate.labels(column);
                    if (labels != null) {
                        for (int label : labels) {
                            if (dictionary.contains(label)) {
                                exercised.set(label - dictionary.firstLabel());
                            }
                        }
                    }
                }
                return new AssembledCandidate(candidate, sheets, dictionary, renderer);
            }
        };
    }

    private static LabelWindow.Mode labelMode(RunConfig config) {
        return switch (config.logicalLabels()) {
            case PRESERVE -> LabelWindow.Mode.PRESERVE;
            case LEGACY_WINDOW -> LabelWindow.Mode.LEGACY_WINDOW;
        };
    }

    private static LabelWindow.Scope windowScope(RunConfig config) {
        return switch (config.windowScope()) {
            case ALL -> LabelWindow.Scope.ALL;
            case CELLS -> LabelWindow.Scope.CELLS;
        };
    }
}
