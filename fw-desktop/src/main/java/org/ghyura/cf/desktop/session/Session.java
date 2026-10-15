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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.analysis.Analysis;
import org.ghyura.cf.analysis.AnalysisReport;
import org.ghyura.cf.analysis.AutoPlan;
import org.ghyura.cf.analysis.CoverageImport;
import org.ghyura.cf.analysis.MetricCorpus;
import org.ghyura.cf.analysis.RuleSet;
import org.ghyura.cf.analysis.TextScoring;
import org.ghyura.cf.campaign.Campaign;
import org.ghyura.cf.campaign.RunHandle;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.input.FileResolver;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.input.ReadResult;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.MemoryBudget;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The application facade the command line, the desktop and embedding applications share: one resolved configuration and
 * the operations on files. It validates a scenario, prepares its run (started, paused, resumed, cancelled and observed
 * through the {@link RunHandle}), analyzes a results corpus and scores a text, with the same services and the same
 * configuration rules as the commands. A session keeps no static or shared mutable state: two sessions in one JVM have
 * their own configuration, key dictionaries, memory budgets, seeds and runs, and may work concurrently. Nothing here
 * loads a windowing toolkit class, so headless use never initializes Swing.
 */
public final class Session {

    private final EffectiveConfig effective;

    private Session(EffectiveConfig effective) {
        this.effective = Objects.requireNonNull(effective, "effective");
    }

    public static Session of(EffectiveConfig effective) {
        return new Session(effective);
    }

    /** A session over a configuration file (or none), an environment and overrides, resolved as the command line resolves them. */
    public static Session resolve(Path configFile, Map<String, String> environment, List<ConfigResolver.Override> overrides) {
        return new Session(ConfigResolver.resolve(configFile, environment, overrides));
    }

    public EffectiveConfig configuration() {
        return effective;
    }

    /**
     * Reads, interprets, plans and keys a scenario without running anything.
     *
     * @throws org.ghyura.cf.model.FrameworkException with every finding so far when a stage has an error
     */
    public ScenarioValidation.Summary validate(Path scenario) {
        ScenarioValidation.Opened opened = ScenarioValidation.open(scenario, effective);
        return ScenarioValidation.prepare(scenario.toString(), opened.loaded(), opened.effective().config(), (summary, dictionary, budget) -> summary);
    }

    /** The scenario's shape and its companion as editable data, including formulas that need repair. */
    public record OpenedConstraints(ScenarioValidation.Summary summary, ConstraintEditing editing) {
    }

    /**
     * Opens the companion for repair without requiring its formulas to pass execution validation. The base scenario is
     * still validated, and its files and the companion share reading admission. Check impact and execution continue to
     * validate every active constraint, including a configured bonds file.
     */
    public OpenedConstraints openConstraints(Path scenario) {
        ScenarioLoader.Loaded raw;
        ConstraintEditing editing;
        try (ScenarioInputs inputs = new ScenarioInputs(scenario, effective.config())) {
            raw = companionScenario(scenario, inputs);
            editing = ConstraintEditing.open(scenario, inputs);
        }
        ScenarioValidation.Summary summary = ScenarioValidation.prepare(scenario.toString(), raw, effective.config(),
                (checked, dictionary, budget) -> checked);
        return new OpenedConstraints(summary, editing);
    }

    /** Raw sidecar for the visual Sieve: IDs, descriptions, value types and rule ordering stay as authored. */
    public record OpenedSieve(ScenarioValidation.Summary summary, SieveDocument document, String fingerprint) { }

    public OpenedSieve openSieve(Path scenario) {
        ScenarioLoader.Loaded raw;
        SieveFiles.Opened opened;
        try (ScenarioInputs inputs = new ScenarioInputs(scenario, effective.config())) {
            raw = companionScenario(scenario, inputs);
            opened = SieveFiles.open(scenario, inputs);
        }
        var summary = ScenarioValidation.prepare(scenario.toString(), raw, effective.config(), (checked, dictionary, budget) -> checked);
        return new OpenedSieve(summary, opened.document(), opened.fingerprint());
    }

    /** Compiles through the actual execution path, without requesting a candidate page or counts. */
    public CandidatePreview validateSieve(Path scenario, String json, Cancellation cancellation) {
        return preview(scenario, json, new CandidatePreview.Request(java.math.BigInteger.ZERO, java.math.BigInteger.ZERO,
                1, false, false), cancellation);
    }

    /** A complete JSON draft, including mapping entries, admitted and checked exactly as an execution sidecar. */
    public ConstraintEditing constraintDraft(String json) {
        try (ScenarioInputs inputs = new ScenarioInputs(Path.of("unsaved-constraints"), effective.config())) {
            ConstraintSidecarReader.read(inputs.text("unsaved constraints", json));
            ConstraintEditing draft = ConstraintEditing.of(json, "unsaved constraints");
            List<String> problems = draft.problems();
            if (!problems.isEmpty()) throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED,
                    String.join("; ", problems), SourceLocation.NONE));
            return draft;
        }
    }

    /** The configuration a scenario runs under in this session: an fwgen spec's own choices below every explicit setting. */
    public EffectiveConfig configurationFor(Path scenario) {
        return ScenarioValidation.open(scenario, effective).effective();
    }

    /**
     * Validates an edited document as if it were saved as {@code file}: its FW_File= paths resolve against the file's
     * directory and its constraint layer is the file's; nothing is written.
     *
     * @throws org.ghyura.cf.model.FrameworkException with every finding so far when a stage has an error
     */
    public ScenarioValidation.Summary validate(ScenarioDocument document, Path file) {
        Path absolute = file.toAbsolutePath();
        ScenarioLoader.Loaded loaded;
        try (ScenarioInputs inputs = new ScenarioInputs(file, effective.config())) {
            ScenarioLoader.Loaded interpreted = ScenarioLoader.interpret(new ReadResult(document.toWorkbook(file.toString()), List.of()),
                    FileResolver.inDirectory(absolute.getParent(), InputSource.DEFAULT_MAX_BYTES, inputs));
            loaded = ScenarioLoader.withConstraints(file, interpreted, ScenarioValidation.constraintFiles(effective.config()), inputs);
        }
        return ScenarioValidation.prepare(file.toString(), loaded, effective.config(), (summary, dictionary, budget) -> summary);
    }

    /**
     * One bounded page of the scenario's candidates and sieve, with the plan's counts when asked: validated, generated and
     * assembled again for every request, so nothing stays open between pages ({@link CandidatePreview}).
     *
     * @throws org.ghyura.cf.model.FrameworkException with every finding so far when a stage has an error, RUN_CANCELLED
     *                                                when cancelled
     */
    public CandidatePreview preview(Path scenario, CandidatePreview.Request request, Cancellation cancellation) {
        cancellation.throwIfCancelled();
        ScenarioValidation.Opened opened = ScenarioValidation.open(scenario, effective);
        return preview(scenario, opened.loaded(), opened.effective().config(), request, cancellation);
    }

    /**
     * The same preview with an unsaved constraint sidecar standing for the scenario's companion file, as the constraint
     * editor checks its impact before saving. A legacy bonds file the configuration names still applies.
     *
     * @throws org.ghyura.cf.model.FrameworkException CONSTRAINT_AMBIGUOUS for an fwgen spec (it carries its own constraints)
     *                                                or when the configuration names another sidecar; the reader's errors
     *                                                for a sidecar it refuses
     */
    public CandidatePreview preview(Path scenario, String sidecarJson, CandidatePreview.Request request, Cancellation cancellation) {
        cancellation.throwIfCancelled();
        ScenarioLoader.Loaded loaded = withSidecar(scenario, sidecarJson);
        EffectiveConfig config = ConfigResolver.withScenario(effective, ScenarioValidation.settings(scenario, loaded));
        return preview(scenario, loaded, config.config(), request, cancellation);
    }

    /** The scenario read without its companion file, the given sidecar text applied in its place. */
    ScenarioLoader.Loaded withSidecar(Path scenario, String sidecarJson) {
        ScenarioLoader.ConstraintFiles files = ScenarioValidation.constraintFiles(effective.config());
        try (ScenarioInputs inputs = new ScenarioInputs(scenario, effective.config())) {
            ScenarioLoader.Loaded raw = companionScenario(scenario, inputs);
            Path companion = ConstraintSidecarReader.companionOf(scenario);
            ConstraintSet layer = raw.spec().constraints().plus(ConstraintSidecarReader.read(inputs.text(companion.toString(), sidecarJson)));
            if (files.bonds() != null) {
                layer = layer.plus(ConstraintSidecarReader.readBonds(inputs.read(files.bonds(), InputSource.DEFAULT_MAX_BYTES)));
            }
            return raw.with(raw.spec().withConstraints(layer), raw.diagnostics());
        }
    }

    private ScenarioLoader.Loaded companionScenario(Path scenario, ScenarioInputs inputs) {
        ScenarioLoader.ConstraintFiles files = ScenarioValidation.constraintFiles(effective.config());
        if (files.sidecar() != null) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_AMBIGUOUS, "the configuration names the sidecar "
                    + files.sidecar() + " (cf.constraints.file); the editor edits the companion beside the scenario", SourceLocation.of(
                    files.sidecar().toString())));
        }
        ScenarioLoader.Loaded raw = ScenarioLoader.loadWithInputs(scenario, inputs);
        if (raw.authored() != null) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_AMBIGUOUS, "an fwgen spec carries its own constraints; edit "
                    + "them in the spec, not in a companion sidecar", SourceLocation.of(scenario.toString())));
        }
        return raw;
    }

    private CandidatePreview preview(Path scenario, ScenarioLoader.Loaded loaded, RunConfig config, CandidatePreview.Request request,
                                     Cancellation cancellation) {
        return ScenarioValidation.prepare(scenario.toString(), loaded, config, (summary, dictionary, budget) -> {
            try (Generation.Result result = Generation.run(summary.plan(), dictionary, GenerationOptions.of(config), budget, cancellation)) {
                return CandidatePreview.of(summary, result, dictionary, budget, config, request, cancellation);
            }
        });
    }

    /**
     * A campaign of the scenario (cf.campaign.*), prepared and not started, with the reader's findings: run it on a thread of
     * the caller's, observe its iterations and progress, pause, resume or cancel it.
     */
    public PreparedCampaign prepareCampaign(Path scenario) {
        ScenarioValidation.Opened opened = ScenarioValidation.open(scenario, effective);
        return new PreparedCampaign(Campaign.of(opened.loaded().spec(), opened.effective()), opened.effective(), opened.loaded().diagnostics());
    }

    /** A prepared campaign and the configuration it runs under. */
    public record PreparedCampaign(Campaign campaign, EffectiveConfig configuration, List<Diagnostic> findings) {
        public PreparedCampaign {
            Objects.requireNonNull(campaign, "campaign");
            findings = List.copyOf(findings);
        }
    }

    /** A run of the scenario, prepared and not started, with the reader's findings; closing it closes the run. */
    public PreparedRun prepareRun(Path scenario) {
        ScenarioValidation.Opened opened = ScenarioValidation.open(scenario, effective);
        return new PreparedRun(RunService.prepare(opened.loaded().spec(), opened.effective()), opened.loaded().diagnostics());
    }

    /** A prepared run: start it, or await it to start and finish it; pause, resume and cancel it; observe its progress. */
    public record PreparedRun(RunHandle handle, List<Diagnostic> findings) implements AutoCloseable {
        public PreparedRun {
            Objects.requireNonNull(handle, "handle");
            findings = List.copyOf(findings);
        }

        @Override
        public void close() {
            handle.close();
        }
    }

    /** A corpus analysed under this session's goals, with what was read. */
    public record Analyzed(AnalysisReport report, int samples, long missingVerdicts, String sha256, List<MetricCorpus.Imported> imports,
                           List<Diagnostic> diagnostics) {
        public Analyzed {
            imports = List.copyOf(imports);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /** Analyses a metrics corpus, with coverage imports when a source is given; the corpus is admitted until the analysis is done. */
    public Analyzed analyze(Path results, CoverageImport.Source coverage) {
        MemoryBudget budget = analysisBudget();
        try (MetricCorpus corpus = MetricCorpus.read(results, coverage, budget)) {
            AnalysisReport report = Analysis.analyze(corpus.samples(), Analysis.Options.of(effective.config()), budget);
            return new Analyzed(report, corpus.samples().size(), corpus.missingVerdicts(), corpus.sha256(), corpus.imports(),
                    corpus.diagnostics());
        }
    }

    /** Scores a text with the legacy analyzer's manual rules (a JSON file, or none) and tuning. */
    public TextScoring.Report score(Path text, Path rules, TextScoring.Settings settings) {
        MemoryBudget budget = analysisBudget();
        RuleSet ruleSet = rules == null ? RuleSet.NONE : RuleSet.read(rules, budget);
        return TextScoring.score(text, ruleSet, settings, budget);
    }

    /** Scores a text with rules already read (a rules editor's, for instance). */
    public TextScoring.Report score(Path text, RuleSet rules, TextScoring.Settings settings) {
        return TextScoring.score(text, rules, settings, analysisBudget());
    }

    /** Suggests goals from a bounded prefix under this session's analysis budget. */
    public AutoPlan.Plan suggest(Path text) {
        return AutoPlan.read(text, analysisBudget());
    }

    /** The budget of one analysis: the configured memory budget, at most a quarter of the heap. */
    public MemoryBudget analysisBudget() {
        return new MemoryBudget(Math.max(1, Math.min(effective.config().memoryBudgetBytes(), Runtime.getRuntime().maxMemory() / 4)));
    }
}
