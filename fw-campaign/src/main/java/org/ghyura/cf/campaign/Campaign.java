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

import org.ghyura.cf.analysis.Analysis;
import org.ghyura.cf.analysis.BundleSeed;
import org.ghyura.cf.analysis.Goal;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.ghyura.cf.results.CampaignCheckpoint;
import org.ghyura.cf.results.Checkpoint;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A campaign of iterations at barriers (legacy generator {@code bundle iterate}; work package M07). Every iteration is a
 * complete analysed run of a scenario of its own, with run id {@code <base>-it<i>}. Iteration i+1 is derived from the base
 * scenario and the seed of iteration i by cf.campaign.strategy, never from iteration i's scenario (the legacy loop was not
 * cumulative): {@code bias} adds the forbid rules of the seed's {@link SeedBias bias plan}, {@code elitism} adds the winners as
 * {@code Seed_} sheets (legacy ElitismStrategy), {@code noop} runs the base scenario again. A seed given by
 * cf.campaign.seed.input derives the first iteration the same way. Scenarios are immutable, so no iteration changes what
 * another one reads. The campaign stops after cf.campaign.iterations; when the Pareto front's candidate ids stayed the same for
 * cf.campaign.stopWhenStable transitions (STABLE_FRONT); after an iteration whose bias carried no signal, which runs unbiased
 * as the legacy loop did (CONVERGED_NO_SIGNAL); when no candidate is left (EXHAUSTED); when an iteration fails, uses up its
 * error budget (cf.run.stop.errors) or yields no winners (FAILED); or when it is cancelled (CANCELLED). Its other stops
 * (a candidate limit, a reached target, the time budget) end only the iteration's admission. Completed outcomes such as
 * BROKEN samples without an error budget remain results and do not end the campaign.
 */
public final class Campaign {

    /** How a campaign ended. */
    public enum Status {
        COMPLETED, STABLE_FRONT, CONVERGED_NO_SIGNAL, EXHAUSTED, FAILED, CANCELLED
    }

    /**
     * One iteration of the lineage (legacy bundle.iterate/v1 iteration entry).
     *
     * @param index                1-based
     * @param runId                its run id
     * @param state                how its run ended
     * @param derivation           how its scenario came from the base scenario, in words
     * @param planStatus           the status of its bias plan (BIASED, CONVERGED_NO_SIGNAL), empty without one
     * @param biasPlanDigest       the digest of its bias plan, empty without one
     * @param parentScenarioDigest the previous iteration's scenario digest, empty for the first
     * @param parentPlanDigest     the previous iteration's plan digest, empty for the first
     * @param scenarioDigest       its scenario's digest
     * @param planDigest           its plan's digest
     * @param inputSeedDigest      the content digest of the seed it was derived from, empty for a full-space start
     * @param seedDigest           the content digest of the seed it produced, empty when it produced none
     * @param preBiasEstimate      the full-space estimate of its bias plan, or null
     * @param postBiasEstimate     the candidates its bias plan expected to keep, or null
     * @param generated            candidates generated
     * @param excluded             candidates the rules removed
     * @param executable           candidates left to execute
     * @param samples              samples with a final observation
     * @param winners              winners of the seed it produced
     * @param frontIds             the candidate ids of that seed's Pareto winners, sorted (legacy front_ids)
     * @param stableTransitions    how many transitions in a row the front stayed the same
     * @param summary              its run's summary
     */
    public record Iteration(int index, String runId, RunState state, String derivation, String planStatus, String biasPlanDigest,
                            String parentScenarioDigest, String parentPlanDigest, String scenarioDigest, String planDigest,
                            String inputSeedDigest, String seedDigest, BigInteger preBiasEstimate, BigInteger postBiasEstimate,
                            BigInteger generated, BigInteger excluded, BigInteger executable, long samples, int winners,
                            List<String> frontIds, int stableTransitions, RunSummary summary) {
        public Iteration {
            frontIds = List.copyOf(frontIds);
            Objects.requireNonNull(summary, "summary");
        }
    }

    /**
     * What a campaign did (legacy {@code <base>-iterate.json}).
     *
     * @param status             how it ended
     * @param reason             why, in words
     * @param baseRunId          the run id the iterations' ids derive from
     * @param strategy           cf.campaign.strategy
     * @param iterationsRequested cf.campaign.iterations
     * @param stopWhenStable     cf.campaign.stopWhenStable
     * @param explorationFloor   cf.campaign.explorationFloor
     * @param minWinnerSupport   cf.campaign.minWinnerSupport
     * @param baseScenarioDigest the base scenario's digest
     * @param inputSeedDigest    the content digest of cf.campaign.seed.input, empty without one
     * @param iterations         the iterations that ran, in order
     * @param diagnostics        the campaign's own findings (each run's are in its summary)
     */
    public record Result(Status status, String reason, String baseRunId, String strategy, int iterationsRequested, int stopWhenStable,
                         double explorationFloor, int minWinnerSupport, String baseScenarioDigest, String inputSeedDigest,
                         List<Iteration> iterations, List<Diagnostic> diagnostics) {
        public Result {
            iterations = List.copyOf(iterations);
            diagnostics = List.copyOf(diagnostics);
        }

        /** The last iteration's run, if any ran. */
        public Optional<RunSummary> last() {
            return iterations.isEmpty() ? Optional.empty() : Optional.of(iterations.getLast().summary());
        }

        /** The seed of the last iteration that produced one. */
        public Optional<BundleSeed> lastSeed() {
            for (int i = iterations.size() - 1; i >= 0; i--) {
                if (iterations.get(i).summary().seed().isPresent()) {
                    return iterations.get(i).summary().seed();
                }
            }
            return Optional.empty();
        }

        /** A deterministic text of the lineage: everything but times and the runs' own diagnostics. */
        public String canonicalLineage() {
            StringBuilder out = new StringBuilder("cf.campaign/1\n");
            out.append("CAMPAIGN\t").append(status).append('\t').append(Canonical.escape(baseRunId)).append('\t').append(strategy).append('\t')
                    .append(iterationsRequested).append('\t').append(stopWhenStable).append('\t').append(explorationFloor).append('\t')
                    .append(minWinnerSupport).append('\t').append(baseScenarioDigest).append('\t').append(inputSeedDigest).append('\n');
            for (Iteration it : iterations) {
                out.append("ITERATION\t").append(it.index()).append('\t').append(Canonical.escape(it.runId())).append('\t').append(it.state())
                        .append('\t').append(it.planStatus()).append('\t').append(it.biasPlanDigest()).append('\t')
                        .append(it.parentScenarioDigest()).append('\t').append(it.parentPlanDigest()).append('\t').append(it.scenarioDigest())
                        .append('\t').append(it.planDigest()).append('\t').append(it.inputSeedDigest()).append('\t').append(it.seedDigest())
                        .append('\t').append(it.preBiasEstimate()).append('\t').append(it.postBiasEstimate()).append('\t').append(it.generated())
                        .append('\t').append(it.excluded()).append('\t').append(it.executable()).append('\t').append(it.samples()).append('\t')
                        .append(it.winners()).append('\t').append(it.stableTransitions()).append("\tfront");
                it.frontIds().forEach(id -> out.append('\t').append(Canonical.escape(id)));
                out.append('\n');
            }
            return out.toString();
        }
    }

    private static final SourceLocation WHERE = SourceLocation.of("campaign");

    private final ScenarioSpec base;
    private final EffectiveConfig effective;
    private final RunConfig config;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile RunHandle current;
    private CampaignCheckpoint restore;
    private String scenarioPath = "";
    private volatile BiConsumer<CampaignCheckpoint, MemoryBudget> checkpointSink;
    private final MemoryBudget budget;
    private final List<MemoryBudget.Lease> historyLeases = new ArrayList<>();
    private final List<BiConsumer<Integer, Progress>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Iteration>> iterationListeners = new CopyOnWriteArrayList<>();
    private final List<Diagnostic> checkpointFindings = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean();

    private Campaign(ScenarioSpec base, EffectiveConfig effective) {
        this.base = base;
        this.effective = effective;
        this.config = effective.config();
        this.budget = MemoryBudget.of(config);
    }

    /** Whether a configuration asks for a campaign rather than a single run: more than one iteration, or a seed to start from. */
    public static boolean wanted(RunConfig config) {
        return config.campaignIterations() > 1 || !config.campaignSeedInput().isEmpty();
    }

    /**
     * A campaign of a base scenario; nothing runs until {@link #run()}. What would stop an iteration before it runs is refused
     * here: pending legacy settings (R-RUN-GATE) and a base run id the iteration ids cannot extend.
     */
    public static Campaign of(ScenarioSpec base, EffectiveConfig effective) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(effective, "effective");
        PendingSettings.refuseIfAny(effective);
        Campaign c = new Campaign(base, effective);
        c.derive(c.config.campaignIterations());
        return c;
    }

    /** Restores a barrier or drained pause; base and configuration mismatches are refused before any work. */
    @SuppressWarnings("try")
    public static Campaign resume(ScenarioSpec base, EffectiveConfig effective, CampaignCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (!base.contentDigest().equals(checkpoint.baseScenarioDigest())
                || !Checkpoint.configDigest(RunService.configuration(effective)).equals(checkpoint.configDigest())) {
            throw mismatch("base scenario or configuration differs");
        }
        Campaign c = of(base, effective);
        RunConfig config = c.config;
        if (!checkpoint.strategy().equals(config.campaignStrategy().token()) || checkpoint.iterationsRequested() != config.campaignIterations()
                || checkpoint.stopWhenStable() != config.campaignStopWhenStable()
                || Double.compare(checkpoint.explorationFloor(), config.campaignExplorationFloor()) != 0
                || checkpoint.minWinnerSupport() != config.campaignMinWinnerSupport()) throw mismatch("strategy parameters differ");
        try (MemoryBudget.Lease decoded = c.budget.reserve(restoredBytes(checkpoint), "campaign restore validation")) {
            List<Iteration> completed = checkpoint.completedIterations().stream().map(CampaignState::iteration).toList();
            String parentScenario = "", parentPlan = "", seed = checkpoint.inputSeedDigest();
            int stable = 0;
            List<String> front = List.of();
            for (int i = 0; i < completed.size(); i++) {
                Iteration it = completed.get(i);
                if (it.index() != i + 1 || !it.runId().equals(config.runId() + "-it" + (i + 1))
                        || !it.runId().equals(it.summary().run().value()) || it.state() != it.summary().state()
                        || !it.scenarioDigest().equals(it.summary().scenarioDigest()) || !it.planDigest().equals(it.summary().planDigest())
                        || !it.parentScenarioDigest().equals(parentScenario) || !it.parentPlanDigest().equals(parentPlan)
                        || !it.inputSeedDigest().equals(seed)
                        || !it.seedDigest().equals(it.summary().seed().map(BundleSeed::contentDigest).orElse("")))
                    throw mismatch("completed iteration lineage is inconsistent");
                if (it.state() == RunState.COMPLETED && !it.frontIds().isEmpty()) {
                    stable = !front.isEmpty() && front.equals(it.frontIds()) ? stable + 1 : 0;
                    front = it.frontIds();
                }
                if (stable != it.stableTransitions()) throw mismatch("completed stable counter differs");
                parentScenario = it.scenarioDigest(); parentPlan = it.planDigest(); seed = it.seedDigest();
            }
            if (stable != checkpoint.stableTransitions() || !front.equals(checkpoint.previousFront()))
                throw mismatch("front or stable counter differs");
            if (!completed.isEmpty() && !completed.getLast().summary().sutVersion().equals(RunService.sutVersion(effective)))
                throw mismatch("SUT version differs");
            if (checkpoint.status().isEmpty() && !completed.isEmpty()) {
                BundleSeed next = CampaignState.seed(checkpoint.nextSeedJson());
                if (!next.contentDigest().equals(seed)) throw mismatch("next seed differs from the completed iteration");
                RunSummary last = completed.getLast().summary();
                if (!checkpoint.fullSpace().equals(last.countsExact() ? last.generated().toString() : ""))
                    throw mismatch("full-space estimate differs from the completed iteration");
            }
            if (checkpoint.currentRun().isPresent() && !checkpoint.currentRun().get().runId().equals(config.runId() + "-it" + checkpoint.iteration()))
                throw mismatch("current run belongs to another iteration");
            if (checkpoint.currentRun().isPresent()) {
                Checkpoint child = checkpoint.currentRun().get();
                if (!child.plugin().equals(config.executionSut()) || !child.pluginVersion().equals(RunService.sutVersion(effective)))
                    throw mismatch("current SUT identity differs");
            }
            if (!checkpoint.status().isEmpty()) {
                try { Status.valueOf(checkpoint.status()); }
                catch (IllegalArgumentException e) { throw mismatch("unknown terminal campaign status"); }
            }
            c.restore = checkpoint; c.scenarioPath = checkpoint.scenarioPath();
            return c;
        }
    }

    /** Optional sink, configured before run; files are the caller's choice (CLI uses fw-artifacts). */
    public void checkpointTo(String path, Consumer<CampaignCheckpoint> sink) {
        Objects.requireNonNull(sink, "sink");
        checkpointWithBudgetTo(path, (checkpoint, memory) -> sink.accept(checkpoint));
    }

    /** The synchronous codec shares admission with every retained iteration and the current child run. */
    public void checkpointWithBudgetTo(String path, BiConsumer<CampaignCheckpoint, MemoryBudget> sink) {
        if (started.get()) throw new IllegalStateException("configure checkpoints before starting the campaign");
        scenarioPath = Objects.requireNonNull(path, "path"); checkpointSink = Objects.requireNonNull(sink, "sink");
    }

    /** Progress of the current iteration; callbacks may pause or cancel the campaign. */
    public AutoCloseable observe(BiConsumer<Integer, Progress> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener")); return () -> listeners.remove(listener);
    }

    /**
     * Every iteration's lineage entry as soon as it is recorded, on the campaign's thread, before the next iteration is
     * derived; callbacks may cancel the campaign, and no further iteration then starts.
     */
    public AutoCloseable observeIterations(Consumer<Iteration> listener) {
        iterationListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> iterationListeners.remove(listener);
    }

    public RunState state() { RunHandle h = current; return h == null ? RunState.READY : h.state(); }
    public void pause() { RunHandle h = current; if (h != null) h.pause(); }
    public void resume() { RunHandle h = current; if (h != null) h.resume(); }

    /** Cancels the campaign: the running iteration is cancelled and no further iteration starts. */
    public void cancel() {
        cancelled.set(true);
        RunHandle h = current;
        if (h != null) {
            h.cancel();
        }
    }

    /** Runs the iterations on the calling thread and returns the lineage. */
    public Result run() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("a campaign runs once");
        try {
            if (restore != null) historyLeases.add(budget.reserve(restoredBytes(restore), "restored campaign history"));
            return runIterations();
        } finally {
            current = null;
            restore = null;
            for (MemoryBudget.Lease lease : historyLeases) lease.close();
            historyLeases.clear();
        }
    }

    /** On return the immutable result belongs to the embedding caller; campaign-owned reservations have been released. */
    private Result runIterations() {
        List<Diagnostic> findings = new ArrayList<>(restore == null ? List.of()
                : restore.diagnostics().stream().map(CampaignState::diagnostic).toList());
        List<Iteration> iterations = new ArrayList<>(restore == null ? List.of()
                : restore.completedIterations().stream().map(CampaignState::iteration).toList());
        List<Goal> goals = Analysis.Options.of(config).declared();
        BundleSeed seed = restore == null ? readInputSeed() : restore.nextSeedJson().isEmpty() ? null
                : CampaignState.seed(restore.nextSeedJson());
        String inputSeedDigest = restore == null ? seed == null ? "" : seed.contentDigest() : restore.inputSeedDigest();
        BigInteger fullSpace = restore == null || restore.fullSpace().isEmpty() ? null : new BigInteger(restore.fullSpace());
        Set<String> previousFront = restore == null || restore.previousFront().isEmpty() ? null : new HashSet<>(restore.previousFront());
        int stable = restore == null ? 0 : restore.stableTransitions();
        String parentScenario = iterations.isEmpty() ? "" : iterations.getLast().scenarioDigest();
        String parentPlan = iterations.isEmpty() ? "" : iterations.getLast().planDigest();
        if (restore != null && !restore.status().isEmpty()) return result(Status.valueOf(restore.status()), restore.reason(),
                inputSeedDigest, iterations, findings);
        Status status = Status.COMPLETED;
        String reason = "ran the " + config.campaignIterations() + " iterations requested";
        int first = restore == null ? 1 : restore.iteration();
        for (int i = first; i <= config.campaignIterations(); i++) {
            if (cancelled.get()) {
                status = Status.CANCELLED;
                reason = "cancelled before iteration " + i;
                break;
            }
            ScenarioSpec scenario = base;
            String derivation = "the base scenario (full space)";
            SeedBias.Plan plan = null;
            if (seed != null) {
                switch (config.campaignStrategy()) {
                    case BIAS -> {
                        try {
                            plan = SeedBias.plan(base, seed, goals, config.campaignExplorationFloor(), config.campaignMinWinnerSupport(),
                                    fullSpace);
                        } catch (FrameworkException e) {
                            findings.addAll(e.diagnostics());
                            status = Status.FAILED;
                            reason = "iteration " + i + " could not be derived from its seed";
                            break;
                        }
                        scenario = plan.apply(base);
                        derivation = "bias: " + plan.reason();
                    }
                    case ELITISM -> {
                        Elitism e = elitism(base, seed);
                        scenario = e.scenario();
                        derivation = e.summary();
                    }
                    case NOOP -> derivation = "Noop: input passed through unchanged";
                }
                if (status == Status.FAILED) {
                    break;
                }
                if (plan != null && plan.status() == SeedBias.Status.EXHAUSTED) {
                    status = Status.EXHAUSTED;
                    reason = "iteration " + i + " would have no candidate: " + plan.reason();
                    findings.add(Diagnostic.warning(ErrorCode.CAMPAIGN_EXHAUSTED, reason, WHERE));
                    break;
                }
            }
            EffectiveConfig derived = derive(i);
            Checkpoint child = restore != null && i == first ? restore.currentRun().orElse(null) : null;
            if (child != null && !scenario.contentDigest().equals(restore.childScenarioDigest()))
                throw mismatch("the current iteration's derived scenario differs");
            RunSummary summary;
            try (RunHandle h = child == null ? RunService.prepare(scenario, derived) : RunService.resume(scenario, derived, child)) {
                h.useMemoryBudget(budget);
                h.requestSeed();
                current = h;
                int index = i;
                h.observe(p -> { for (BiConsumer<Integer, Progress> listener : listeners) listener.accept(index, p); });
                if (checkpointSink != null) {
                    String childDigest = scenario.contentDigest();
                    BundleSeed priorSeed = seed;
                    BigInteger priorSpace = fullSpace;
                    Set<String> priorFront = previousFront;
                    int priorStable = stable;
                    h.checkpointWithBudgetTo(scenarioPath, (cp, memory) -> {
                        if (cp.state().equals(RunState.PAUSED.name())) saveCheckpoint(index, childDigest, "", "", iterations,
                                findings, priorSeed, inputSeedDigest, priorSpace, priorFront, priorStable, Optional.of(cp));
                    });
                }
                if (cancelled.get()) {
                    h.cancel();
                }
                summary = h.await();
                // The child created its summary while admitted. Retain it before admitting another child or report copy.
                historyLeases.add(budget.reserve(MemorySizes.add(16_384, MemorySizes.multiply(2, MemorySizes.retained(summary))),
                        "completed campaign iteration"));
            } catch (FrameworkException e) {
                findings.addAll(e.diagnostics());
                status = Status.FAILED;
                reason = "iteration " + i + " could not finish within its resources";
                break;
            } finally {
                current = null;
            }
            if (child != null) {
                List<Diagnostic> identityErrors = summary.diagnostics().stream().filter(d -> d.code() == ErrorCode.CHECKPOINT_MISMATCH).toList();
                if (!identityErrors.isEmpty()) throw new FrameworkException(identityErrors);
            }
            BundleSeed produced = summary.seed().orElse(null);
            List<String> front = produced == null ? List.of() : frontIds(produced);
            Set<String> frontSet = new HashSet<>(front);
            if (summary.state() == RunState.COMPLETED && !front.isEmpty()) {
                stable = previousFront != null && frontSet.equals(previousFront) ? stable + 1 : 0;
                previousFront = frontSet;
            }
            iterations.add(new Iteration(i, derived.config().runId(), summary.state(), derivation, plan == null ? "" : plan.status().name(),
                    plan == null ? "" : plan.contentDigest(), parentScenario, parentPlan, summary.scenarioDigest(), summary.planDigest(),
                    seed == null ? "" : seed.contentDigest(), produced == null ? "" : produced.contentDigest(),
                    plan == null ? null : plan.fullSpace(), plan == null ? null : plan.postBias(), summary.generated(), summary.excluded(),
                    summary.executable(), summary.samples(), produced == null ? 0 : produced.winners().size(), front, stable, summary));
            for (Consumer<Iteration> listener : iterationListeners) {
                listener.accept(iterations.getLast());
            }
            parentScenario = summary.scenarioDigest();
            parentPlan = summary.planDigest();
            if (summary.state() == RunState.CANCELLED) {
                status = Status.CANCELLED;
                reason = "iteration " + i + " was cancelled";
                break;
            }
            if (summary.state() != RunState.COMPLETED) {
                status = Status.FAILED;
                reason = "iteration " + i + " ended " + summary.state();
                break;
            }
            // decision M09-CAMPAIGN-ERRORS: a used-up error budget fails its iteration's command, so it ends the campaign as
            // a run failing its outcome policy ended the legacy iterate loop; the iterations so far and their results stay
            if (summary.stop() != null && summary.stop().reason() == StopPolicy.Reason.ERROR_BUDGET) {
                status = Status.FAILED;
                reason = "iteration " + i + " used up its error budget (cf.run.stop.errors): " + summary.stop().detail();
                findings.add(Diagnostic.error(ErrorCode.RUN_ERROR_BUDGET_EXHAUSTED, reason, WHERE, "runId", derived.config().runId()));
                break;
            }
            if (summary.countsExact() && summary.executable().signum() == 0) {
                status = Status.EXHAUSTED;
                reason = "iteration " + i + " had no executable candidate";
                findings.add(Diagnostic.warning(ErrorCode.CAMPAIGN_EXHAUSTED, reason, WHERE));
                break;
            }
            if (front.isEmpty()) {
                status = Status.FAILED;
                reason = "iteration " + i + " produced no seed with front winners; candidates may emit no metrics the analysis can rank";
                findings.add(Diagnostic.error(ErrorCode.CAMPAIGN_NO_WINNERS, reason, WHERE, "runId", derived.config().runId()));
                break;
            }
            seed = produced;
            fullSpace = summary.countsExact() ? summary.generated() : null;
            if (plan != null && plan.status() == SeedBias.Status.CONVERGED_NO_SIGNAL) {
                status = Status.CONVERGED_NO_SIGNAL;
                reason = "iteration " + i + " ran unbiased: " + plan.reason();
                findings.add(Diagnostic.warning(ErrorCode.CAMPAIGN_NO_SIGNAL, reason, WHERE));
                break;
            }
            if (config.campaignStopWhenStable() > 0 && stable >= config.campaignStopWhenStable()) {
                status = Status.STABLE_FRONT;
                reason = "the front stayed the same for " + stable + " transitions after iteration " + i;
                break;
            }
            if (i < config.campaignIterations() && checkpointSink != null) saveCheckpoint(i + 1, "", "", "",
                    iterations, findings, seed, inputSeedDigest, fullSpace, previousFront, stable, Optional.empty());
        }
        // Cancellation preserves the last drained pause/barrier rather than replacing it with cancellation observations.
        boolean memoryFailed = findings.stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)
                || iterations.stream().anyMatch(it -> it.summary().diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT));
        if (status != Status.CANCELLED && !memoryFailed && checkpointSink != null)
            saveCheckpoint(iterations.size() + 1, "", status.name(), reason, iterations, findings, seed, inputSeedDigest,
                    fullSpace, previousFront, stable, Optional.empty());
        findings.addAll(checkpointFindings);
        return result(status, reason, inputSeedDigest, iterations, findings);
    }

    private Result result(Status status, String reason, String inputSeedDigest, List<Iteration> iterations, List<Diagnostic> findings) {
        return new Result(status, reason, config.runId(), config.campaignStrategy().token(), config.campaignIterations(),
                config.campaignStopWhenStable(), config.campaignExplorationFloor(), config.campaignMinWinnerSupport(), base.contentDigest(),
                inputSeedDigest, iterations, findings);
    }

    private CampaignCheckpoint checkpoint(int index, String childDigest, String status, String reason, List<Iteration> iterations,
                                          List<Diagnostic> findings, BundleSeed seed, String inputDigest, BigInteger fullSpace,
                                          Set<String> previousFront, int stable) {
        List<Diagnostic> allFindings = new ArrayList<>(findings);
        allFindings.addAll(checkpointFindings);
        return new CampaignCheckpoint(scenarioPath, base.contentDigest(), RunService.configuration(effective), config.campaignStrategy().token(),
                config.campaignIterations(), config.campaignStopWhenStable(), config.campaignExplorationFloor(), config.campaignMinWinnerSupport(),
                iterations.stream().map(CampaignState::iteration).toList(), inputDigest, seed == null ? "" : CampaignState.seed(seed),
                fullSpace == null ? "" : fullSpace.toString(), previousFront == null ? List.of() : previousFront.stream().sorted().toList(),
                stable, index, childDigest, Optional.empty(), status, reason, allFindings.stream().map(CampaignState::diagnostic).toList());
    }

    @SuppressWarnings("try")
    private void saveCheckpoint(int index, String childDigest, String status, String reason, List<Iteration> iterations,
                                List<Diagnostic> findings, BundleSeed seed, String inputDigest, BigInteger fullSpace,
                                Set<String> previousFront, int stable, Optional<Checkpoint> child) {
        if (checkpointSink == null) return;
        try {
            long metadata = MemorySizes.multiply(32, MemorySizes.add(MemorySizes.retained(base), MemorySizes.retained(effective.entries())));
            long encoding = MemorySizes.add(CampaignState.encodingBytes(iterations), MemorySizes.add(CampaignState.encodingBytes(seed),
                    MemorySizes.add(CampaignState.encodingBytes(findings), CampaignState.encodingBytes(checkpointFindings))));
            // Admission precedes CampaignState's record-to-map conversion, JSON construction and snapshot list copies.
            try (MemoryBudget.Lease lease = budget.reserve(MemorySizes.add(metadata, encoding),
                    "campaign checkpoint state construction")) {
                CampaignCheckpoint state = checkpoint(index, childDigest, status, reason, iterations, findings, seed, inputDigest,
                        fullSpace, previousFront, stable);
                checkpointSink.accept(child.isEmpty() ? state : state.withCurrentRun(child.get()), budget);
            }
        } catch (RuntimeException e) {
            boolean resourceFailure = e instanceof FrameworkException failure
                    && failure.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT);
            if (resourceFailure) checkpointSink = null;
            else {
                try {
                    historyLeases.add(budget.reserve(MemorySizes.add(8192,
                            MemorySizes.multiply(8, String.valueOf(e.getMessage()).length())), "checkpoint failure history"));
                } catch (FrameworkException exhausted) {
                    checkpointSink = null;
                    checkpointFindings.addAll(exhausted.diagnostics());
                }
            }
            if (e instanceof FrameworkException failure) checkpointFindings.addAll(failure.diagnostics());
            checkpointFindings.add(Diagnostic.error(ErrorCode.CHECKPOINT_WRITE_FAILED,
                "the campaign checkpoint could not be written: " + e.getMessage() + "; the last good checkpoint was kept",
                SourceLocation.of(scenarioPath)));
        }
    }

    private static long restoredBytes(CampaignCheckpoint checkpoint) {
        // Includes the encoded strings still held by the checkpoint, JSON trees and reconstructed reports/seed.
        return MemorySizes.add(65_536, MemorySizes.multiply(96, MemorySizes.retained(checkpoint)));
    }

    private static FrameworkException mismatch(String reason) {
        return new FrameworkException(Diagnostic.error(ErrorCode.CHECKPOINT_MISMATCH,
                "the campaign checkpoint cannot be resumed: " + reason + "; nothing was resumed", WHERE));
    }

    /** Iteration i's configuration: the campaign's, with run id {@code <base>-it<i>} and the analysis on. */
    private EffectiveConfig derive(int i) {
        return ConfigResolver.derive(effective, List.of(new ConfigResolver.Override("cf.run.id", config.runId() + "-it" + i, "campaign"),
                new ConfigResolver.Override("cf.analysis.enabled", "true", "campaign")));
    }

    private BundleSeed readInputSeed() {
        String input = config.campaignSeedInput();
        if (input.isEmpty()) {
            return null;
        }
        Path path = Path.of(input);
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, "cf.campaign.seed.input: the seed " + path
                    + " cannot be read: " + e.getMessage(), SourceLocation.of(input), "key", "cf.campaign.seed.input"));
        }
        return BundleSeed.read(text, input);
    }

    /** The candidate ids of a seed's Pareto winners, of every winner when none is one, sorted (legacy _seed_front_ids). */
    static List<String> frontIds(BundleSeed seed) {
        TreeSet<String> ids = new TreeSet<>();
        seed.winners().stream().filter(w -> w.role().equals("pareto")).map(Campaign::candidateId).filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            seed.winners().stream().map(Campaign::candidateId).filter(Objects::nonNull).forEach(ids::add);
        }
        return List.copyOf(ids);
    }

    /** A winner's candidate id: candidate_id, combi_id or id of its pairs, else a candidate_id token of its line (legacy). */
    private static String candidateId(BundleSeed.Winner w) {
        for (String key : List.of("candidate_id", "combi_id", "id")) {
            String v = w.kvPairs().get(key);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        if (w.originalLine() != null) {
            for (String token : w.originalLine().strip().split("\\s+")) {
                if (token.startsWith("candidate_id=")) {
                    return token.substring("candidate_id=".length());
                }
            }
        }
        return null;
    }

    record Elitism(ScenarioSpec scenario, String summary) {
    }

    /**
     * Legacy ElitismStrategy with its defaults (prefix {@code Seed_}, every role, the role in the name): per winner, a data sheet
     * {@code Seed_<lineNo>_<role>} (the role with every character outside [A-Za-z0-9] replaced by '_') holding one value, the
     * winner's pairs as {@code key=value} text separated by spaces. A sheet whose name exists already (ignoring case, as sheet
     * names are compared here) is skipped; the sheets are additive and FW_Seq is left as the author wrote it.
     */
    static Elitism elitism(ScenarioSpec base, BundleSeed seed) {
        if (seed.winners().isEmpty()) {
            return new Elitism(base, "Elitism: no winners in seed (added 0 sheets)");
        }
        List<SheetSpec> sheets = new ArrayList<>(base.sheets());
        Set<String> existing = new HashSet<>();
        sheets.forEach(s -> existing.add(s.name().toLowerCase(Locale.ROOT)));
        int added = 0;
        int skipped = 0;
        SourceLocation where = SourceLocation.of("elitism of run " + seed.sourceRunId());
        for (BundleSeed.Winner w : seed.winners()) {
            String row = kvText(w.kvPairs());
            if (row.isEmpty()) {
                continue;
            }
            String name = "Seed_" + w.lineNo() + "_" + (w.role().isEmpty() ? "x" : w.role().replaceAll("[^A-Za-z0-9]", "_"));
            if (!existing.add(name.toLowerCase(Locale.ROOT))) {
                skipped++;
                continue;
            }
            sheets.add(new SheetSpec(name, sheets.size() + 1, List.of(ValueCell.of(row, ValueKind.TEXT, where)), where));
            added++;
        }
        ScenarioSpec scenario = new ScenarioSpec(base.source(), sheets, base.controls(), base.hasSequenceSheet(), base.sequence(),
                base.constraints());
        return new Elitism(scenario, String.format(Locale.ROOT, "Elitism: added %d Seed_* sheets (filtered out 0 by role-prefix '', "
                + "skipped %d already-present) from %d total winners", added, skipped, seed.winners().size()));
    }

    private static String kvText(Map<String, String> pairs) {
        StringBuilder out = new StringBuilder();
        pairs.forEach((k, v) -> {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(k).append('=').append(v == null ? "" : v);
        });
        return out.toString();
    }
}
