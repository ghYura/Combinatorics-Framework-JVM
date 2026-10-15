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

package org.ghyura.cf.config;

import org.ghyura.cf.model.key.KeyEncodingPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The fully validated, immutable settings of one run, resolved once (architecture.xml, configuration).
 * Nothing in the framework reads a system property, environment variable or file after this object exists.
 *
 * <p>Obtain one from {@link ConfigResolver}; {@link #defaults()} is the embedded default set.</p>
 */
public final class RunConfig {

    /** Where the framework runs: one JVM hosts every module. */
    public enum RuntimeMode {
        SINGLE_JVM("single-jvm");

        private final String token;

        RuntimeMode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** Where run state lives: the heap of the run's own session. */
    public enum StorageMode {
        HEAP("heap");

        private final String token;

        StorageMode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** How logical key labels are assigned (decision F2). */
    public enum LogicalLabels {
        PRESERVE("preserve"),
        LEGACY_WINDOW("legacy-window");

        private final String token;

        LogicalLabels(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** What the legacy-window gate counts (legacy core.keys.scope). */
    public enum WindowScope {
        ALL("all"),
        CELLS("cells");

        private final String token;

        WindowScope(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** The supported execution contract: trusted, cooperative, in-JVM. */
    public enum ExecutionMode {
        TRUSTED_COOPERATIVE("trusted-cooperative");

        private final String token;

        ExecutionMode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** Whose row order the first and the ungrouped later passes follow (decision G6). */
    public enum GenerationLibrary {
        PARALLEL("PARALLEL"),
        COMBINATORICSLIB3("COMBINATORICSLIB3");

        private final String token;

        GenerationLibrary(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** What a mandatory sheet without rows does to the candidates (R-M03-SEMANTICS). */
    public enum EmptyStagePolicy {
        PRESERVE_EMPTY("PRESERVE_EMPTY"),
        LEGACY_DROP_WITH_DIAGNOSTIC("LEGACY_DROP_WITH_DIAGNOSTIC");

        private final String token;

        EmptyStagePolicy(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** FW_ReplaceRE patterns that cannot match a label text (legacy core.replace.patternPolicy). */
    public enum ReplacePattern {
        PERMISSIVE("permissive"),
        WARN("warn"),
        STRICT("strict");

        private final String token;

        ReplacePattern(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** FW_ReplaceRE patterns that changed no row (legacy core.replace.unmatchedPolicy). */
    public enum ReplaceUnmatched {
        IGNORE("ignore"),
        WARN("warn"),
        FAIL("fail");

        private final String token;

        ReplaceUnmatched(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** Grouped rows that are no longer labels after FW_ReplaceRE (legacy core.replace.unparseablePolicy). */
    public enum ReplaceUnparseable {
        WARN("warn"),
        DROP("drop"),
        FAIL("fail");

        private final String token;

        ReplaceUnparseable(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** FW_ReplaceRE whose pattern equals its replacement (legacy core.replace.identityPolicy). */
    public enum ReplaceIdentity {
        ALLOW("allow"),
        WARN("warn");

        private final String token;

        ReplaceIdentity(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** Per-sheet FW_ReplaceRE accounting (legacy core.replace.diagnostics). */
    public enum ReplaceDiagnostics {
        OFF("off"),
        SUMMARY("summary");

        private final String token;

        ReplaceDiagnostics(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** Whether optional sheets are assembled (legacy reader.core.processIsOpt). */
    public enum OptionalMode {
        AUTO("auto"),
        ON("on"),
        OFF("off");

        private final String token;

        OptionalMode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** What FW_Concatenator= does when a candidate is rendered. */
    public enum SheetConcatenator {
        APPLY("apply"),
        LEGACY_IGNORE("legacy-ignore");

        private final String token;

        SheetConcatenator(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /**
     * A metric target that ends admission (cf.run.stop.target): a PASS verdict sample whose metric {@code key} is at least
     * ({@code atLeast}) or at most {@code value}.
     */
    public record StopTarget(String key, boolean atLeast, double value) {
        /** No target. */
        public static final StopTarget NONE = new StopTarget("", true, 0);

        public StopTarget {
            Objects.requireNonNull(key, "key");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("a stop target's value is finite");
            }
        }

        public boolean isNone() {
            return key.isEmpty();
        }

        /** Whether a reported value meets the target. */
        public boolean reachedBy(double reported) {
            return atLeast ? reported >= value : reported <= value;
        }

        @Override
        public String toString() {
            return isNone() ? "" : key + (atLeast ? ">=" : "<=") + value;
        }
    }

    /** Greedy covering selection strategy; neither claims a minimum. */
    public enum CoverageMethod {
        GREEDY("greedy"),
        SET_COVER("set-cover");

        private final String token;

        CoverageMethod(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /**
     * A set of optional combination sizes: every size ({@link #all()}) or an explicit sorted set of sizes from 1 to 64.
     *
     * @param every whether every size from 1 to the number of optional sheets is meant
     * @param sizes the explicit sizes in ascending order; empty when {@code every}
     */
    public record SizeSet(boolean every, java.util.SortedSet<Integer> sizes) {
        public SizeSet {
            Objects.requireNonNull(sizes, "sizes");
            sizes = java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(sizes));
            if (every && !sizes.isEmpty()) {
                throw new IllegalArgumentException("'all' has no explicit sizes");
            }
            for (int size : sizes) {
                if (size < 1 || size > 64) {
                    throw new IllegalArgumentException("an optional combination size is from 1 to 64: " + size);
                }
            }
        }

        public static SizeSet all() {
            return new SizeSet(true, new java.util.TreeSet<>());
        }

        public static SizeSet of(java.util.Collection<Integer> sizes) {
            return new SizeSet(false, new java.util.TreeSet<>(sizes));
        }

        /** Whether {@code size} is in the set, for a plan with {@code optionalSheets} optional sheets. */
        public boolean contains(int size, int optionalSheets) {
            return every ? size >= 1 && size <= optionalSheets : sizes.contains(size);
        }

        /**
         * Whether every size of this set that a plan with {@code optionalSheets} optional sheets can form is also in
         * {@code other}; sizes above the number of optional sheets form no combination and are not compared.
         */
        public boolean within(SizeSet other, int optionalSheets) {
            for (int size = 1; size <= optionalSheets; size++) {
                if (contains(size, optionalSheets) && !other.contains(size, optionalSheets)) {
                    return false;
                }
            }
            return true;
        }

        /** The text form the parser reads: {@code all}, {@code none} or the sizes joined by commas. */
        public String token() {
            if (every) {
                return "all";
            }
            if (sizes.isEmpty()) {
                return "none";
            }
            StringBuilder out = new StringBuilder();
            for (int size : sizes) {
                out.append(out.isEmpty() ? "" : ",").append(size);
            }
            return out.toString();
        }
    }

    /** What a budget overrun does. */
    public enum MemoryExhaustion {
        FAIL_EXPLICITLY("fail-explicitly");

        private final String token;

        MemoryExhaustion(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** How much of the result history is kept. */
    public enum ResultsRetention {
        FULL("full"),
        AGGREGATE_ONLY("aggregate-only");

        private final String token;

        ResultsRetention(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** Pareto archive policy. */
    public enum ParetoMode {
        EXACT_BUDGETED("exact-budgeted");

        private final String token;

        ParetoMode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** What to do with NaN and infinite metric values. */
    public enum NonFinitePolicy {
        REJECT("reject");

        private final String token;

        NonFinitePolicy(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** cf.execution.verdict: the static int field of a generated candidate that holds its verdict. */
    public enum VerdictField {
        FW_VAR("FW_VAR"),
        FW_CUSTOM_VAR("FW_CUSTOM_VAR");

        private final String token;

        VerdictField(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** cf.execution.repeat.policy: how the repeats of a candidate are spread over the environments (legacy BundleControlPlane). */
    public enum RepeatPolicy {
        LOCAL("local"),
        DISPERSE("disperse"),
        NESTED("nested");

        private final String token;

        RepeatPolicy(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** cf.execution.repeat.scope: whether every repeat is a verdict sample or repeat 0 alone, the others measurements. */
    public enum RepeatScope {
        ALL("all"),
        METRICS("metrics");

        private final String token;

        RepeatScope(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** cf.execution.coverage: whether generated Java candidates are instrumented for line coverage (AN-12). */
    public enum Coverage {
        NONE("none"),
        LINES("lines");

        private final String token;

        Coverage(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** cf.results.attach: the samples whose candidate source is kept as an attachment. */
    public enum Attach {
        FAILED("failed"),
        PASSED("passed"),
        ALL("all"),
        NONE("none");

        private final String token;

        Attach(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** cf.analysis.mode: whether undeclared metric keys may become goals. */
    public enum AnalysisMode {
        EXPLORATORY("exploratory"),
        FORMAL("formal");

        private final String token;

        AnalysisMode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** cf.analysis.discover.mode: the direction of discovered goals. */
    public enum DiscoverMode {
        AUTO("auto"),
        MIN("min"),
        MAX("max");

        private final String token;

        DiscoverMode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** cf.analysis.front: the ranker of the Pareto front (legacy FrontAlgorithm). */
    public enum FrontAlgorithm {
        PARETO("pareto"),
        NSGA_II("nsga-ii"),
        NSGA_III("nsga-iii"),
        SPEA2("spea2"),
        MOEA_D("moea-d");

        private final String token;

        FrontAlgorithm(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** cf.campaign.strategy: how one iteration follows from the previous one's winners. */
    public enum CampaignStrategy {
        BIAS("bias"),
        ELITISM("elitism"),
        NOOP("noop");

        private final String token;

        CampaignStrategy(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /** The direction of a declared goal. */
    public enum GoalMode {
        MIN,
        MAX,
        TARGET
    }

    /**
     * One declared goal of cf.analysis.goals.
     *
     * @param key    the metric key
     * @param mode   min, max or target
     * @param target the target value (0 unless the mode is target)
     * @param weight finite, at least 0
     */
    public record AnalysisGoal(String key, GoalMode mode, double target, double weight) {
        public AnalysisGoal {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(mode, "mode");
            if (!Double.isFinite(target) || !Double.isFinite(weight) || weight < 0) {
                throw new IllegalArgumentException("a goal's target and weight are finite, the weight at least 0");
            }
        }

        /** The goal as cf.analysis.goals writes it. */
        public String token() {
            String w = weight == 1 ? "" : ":" + Double.toString(weight);
            return switch (mode) {
                case MIN -> key + ":min" + w;
                case MAX -> key + ":max" + w;
                case TARGET -> key + ":target:" + Double.toString(target) + w;
            };
        }
    }

    /** The cf.execution.sut value that selects compiled generated candidates instead of a typed plugin. */
    public static final String GENERATED_JAVA = "generated-java";

    /** The cf.execution.sut value of the Python add-on: each candidate is rendered Python run by the configured interpreter. */
    public static final String PYTHON = "python";

    private final Map<String, Object> values;

    RunConfig(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        for (ConfigKey<?> key : ConfigKeys.all()) {
            if (!values.containsKey(key.name())) {
                throw new IllegalArgumentException("missing value for " + key.name());
            }
        }
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** The embedded defaults, validated like any other configuration. */
    public static RunConfig defaults() {
        return ConfigResolver.resolve(null, Map.of(), List.of()).config();
    }

    private <T> T get(ConfigKey<T> key) {
        return key.cast(values.get(key.name()));
    }

    public int schemaVersion() {
        return get(ConfigKeys.SCHEMA_VERSION);
    }

    public int javaRelease() {
        return get(ConfigKeys.JAVA_RELEASE);
    }

    public RuntimeMode runtimeMode() {
        return get(ConfigKeys.RUNTIME_MODE);
    }

    public StorageMode storageMode() {
        return get(ConfigKeys.STORAGE_MODE);
    }

    public KeyEncodingPolicy keyEncodingPolicy() {
        return get(ConfigKeys.KEYS_ENCODING);
    }

    public LogicalLabels logicalLabels() {
        return get(ConfigKeys.KEYS_LOGICAL_LABELS);
    }

    public ExecutionMode executionMode() {
        return get(ConfigKeys.EXECUTION_MODE);
    }

    public int workers() {
        return get(ConfigKeys.EXECUTION_WORKERS);
    }

    public int repeatCount() {
        return get(ConfigKeys.EXECUTION_REPEAT_COUNT);
    }

    public int retryMaxAttempts() {
        return get(ConfigKeys.EXECUTION_RETRY_MAX_ATTEMPTS);
    }

    public long timeoutMillis() {
        return get(ConfigKeys.EXECUTION_TIMEOUT_MILLIS);
    }

    public long cancelGraceMillis() {
        return get(ConfigKeys.EXECUTION_CANCEL_GRACE_MILLIS);
    }

    public int outputMaxBytes() {
        return get(ConfigKeys.EXECUTION_OUTPUT_MAX_BYTES);
    }

    public String runId() {
        return get(ConfigKeys.RUN_ID);
    }

    public String executionSut() {
        return get(ConfigKeys.EXECUTION_SUT);
    }

    public List<String> executionClasspath() {
        return get(ConfigKeys.EXECUTION_CLASSPATH);
    }

    /** Whether the candidates run as Python through the Python add-on (cf.execution.sut=python). */
    public boolean runsPython() {
        return PYTHON.equals(executionSut());
    }

    public String pythonExecutable() {
        return get(ConfigKeys.PYTHON_EXECUTABLE);
    }

    public List<String> pythonPath() {
        return get(ConfigKeys.PYTHON_PATH);
    }

    /** NAME=VALUE environment entries for Python candidates. */
    public List<String> pythonEnv() {
        return get(ConfigKeys.PYTHON_ENV);
    }

    /** The configured working directory of Python candidates; empty for the scenario file's directory. */
    public String pythonWorkdir() {
        return get(ConfigKeys.PYTHON_WORKDIR);
    }

    /** Where Python candidates run: empty for directly, bwrap, bwrap-network, docker:IMAGE or docker:IMAGE@NETWORK. */
    public String pythonSandbox() {
        return get(ConfigKeys.PYTHON_SANDBOX);
    }

    public VerdictField executionVerdict() {
        return get(ConfigKeys.EXECUTION_VERDICT);
    }

    /** The environments of the run; never empty: no configured environment is the one default environment ''. */
    public List<String> environments() {
        List<String> envs = get(ConfigKeys.EXECUTION_ENVIRONMENTS);
        return envs.isEmpty() ? List.of("") : envs;
    }

    public RepeatPolicy repeatPolicy() {
        return get(ConfigKeys.EXECUTION_REPEAT_POLICY);
    }

    public RepeatScope repeatScope() {
        return get(ConfigKeys.EXECUTION_REPEAT_SCOPE);
    }

    public Coverage coverage() {
        return get(ConfigKeys.EXECUTION_COVERAGE);
    }

    public boolean serializeSamples() {
        return get(ConfigKeys.EXECUTION_SERIALIZE);
    }

    public long runBudgetMillis() {
        return get(ConfigKeys.RUN_BUDGET_MILLIS);
    }

    public long stopCandidates() {
        return get(ConfigKeys.RUN_STOP_CANDIDATES);
    }

    public long stopErrors() {
        return get(ConfigKeys.RUN_STOP_ERRORS);
    }

    public StopTarget stopTarget() {
        return get(ConfigKeys.RUN_STOP_TARGET);
    }

    public int listenerMaxAttempts() {
        return get(ConfigKeys.PIPELINE_LISTENER_MAX_ATTEMPTS);
    }

    public int queueMaxItems() {
        return get(ConfigKeys.PIPELINE_QUEUE_MAX_ITEMS);
    }

    public long queueMaxBytes() {
        return get(ConfigKeys.PIPELINE_QUEUE_MAX_BYTES);
    }

    public int ordinalWindow() {
        return get(ConfigKeys.PIPELINE_ORDINAL_WINDOW);
    }

    public long memoryBudgetBytes() {
        return get(ConfigKeys.MEMORY_BUDGET_BYTES);
    }

    public MemoryExhaustion memoryExhaustion() {
        return get(ConfigKeys.MEMORY_EXHAUSTION);
    }

    public ResultsRetention resultsRetention() {
        return get(ConfigKeys.RESULTS_RETENTION);
    }

    public long resultsMaxSamples() {
        return get(ConfigKeys.RESULTS_MAX_SAMPLES);
    }

    public long resultsMaxAttachmentBytes() {
        return get(ConfigKeys.RESULTS_MAX_ATTACHMENT_BYTES);
    }

    public Attach resultsAttach() {
        return get(ConfigKeys.RESULTS_ATTACH);
    }

    public long compilerCacheMaxBytes() {
        return get(ConfigKeys.COMPILER_CACHE_MAX_BYTES);
    }

    public ParetoMode paretoMode() {
        return get(ConfigKeys.ANALYSIS_PARETO_MODE);
    }

    public NonFinitePolicy nonFinitePolicy() {
        return get(ConfigKeys.ANALYSIS_NONFINITE);
    }

    public boolean analysisEnabled() {
        return get(ConfigKeys.ANALYSIS_ENABLED);
    }

    public List<AnalysisGoal> analysisGoals() {
        return get(ConfigKeys.ANALYSIS_GOALS);
    }

    public List<String> analysisMinimize() {
        return get(ConfigKeys.ANALYSIS_MINIMIZE);
    }

    public List<String> analysisMaximize() {
        return get(ConfigKeys.ANALYSIS_MAXIMIZE);
    }

    public AnalysisMode analysisMode() {
        return get(ConfigKeys.ANALYSIS_MODE);
    }

    public boolean analysisDiscover() {
        return get(ConfigKeys.ANALYSIS_DISCOVER);
    }

    public DiscoverMode analysisDiscoverMode() {
        return get(ConfigKeys.ANALYSIS_DISCOVER_MODE);
    }

    public double analysisDiscoverWeight() {
        return get(ConfigKeys.ANALYSIS_DISCOVER_WEIGHT);
    }

    public int analysisTopK() {
        return get(ConfigKeys.ANALYSIS_TOP_K);
    }

    public FrontAlgorithm analysisFront() {
        return get(ConfigKeys.ANALYSIS_FRONT);
    }

    public int campaignIterations() {
        return get(ConfigKeys.CAMPAIGN_ITERATIONS);
    }

    public int campaignStopWhenStable() {
        return get(ConfigKeys.CAMPAIGN_STOP_WHEN_STABLE);
    }

    public CampaignStrategy campaignStrategy() {
        return get(ConfigKeys.CAMPAIGN_STRATEGY);
    }

    public double campaignExplorationFloor() {
        return get(ConfigKeys.CAMPAIGN_EXPLORATION_FLOOR);
    }

    public int campaignMinWinnerSupport() {
        return get(ConfigKeys.CAMPAIGN_MIN_WINNER_SUPPORT);
    }

    public String campaignSeedInput() {
        return get(ConfigKeys.CAMPAIGN_SEED_INPUT);
    }

    public String campaignSeedOutput() {
        return get(ConfigKeys.CAMPAIGN_SEED_OUTPUT);
    }

    public String checkpointFile() {
        return get(ConfigKeys.CHECKPOINT_FILE);
    }

    public long randomSeed() {
        return get(ConfigKeys.RANDOM_SEED);
    }

    public boolean deterministicOrder() {
        return get(ConfigKeys.ORDER_DETERMINISTIC);
    }

    public boolean checkpointEnabled() {
        return get(ConfigKeys.CHECKPOINT_ENABLED);
    }

    public boolean artifactsEnabled() {
        return get(ConfigKeys.ARTIFACTS_ENABLED);
    }

    public boolean uiEnabled() {
        return get(ConfigKeys.UI_ENABLED);
    }

    public boolean planAutoGenerateMissingSheets() {
        return get(ConfigKeys.PLAN_AUTO_GENERATE_MISSING_SHEETS);
    }

    public String planVirtualSheetPrefix() {
        return get(ConfigKeys.PLAN_VIRTUAL_SHEET_PREFIX);
    }

    public boolean planHeadlessRowsGetTarget() {
        return get(ConfigKeys.PLAN_HEADLESS_ROWS_GET_TARGET);
    }

    public boolean planNestedJoinersEnabled() {
        return get(ConfigKeys.PLAN_NESTED_JOINERS_ENABLED);
    }

    public boolean planAutoPromoteSingleVerb() {
        return get(ConfigKeys.PLAN_AUTO_PROMOTE_SINGLE_VERB);
    }

    public boolean planAutoGenerateSheetNames() {
        return get(ConfigKeys.PLAN_AUTO_GENERATE_SHEET_NAMES);
    }

    public GenerationLibrary generationLibrary() {
        return get(ConfigKeys.GENERATION_LIBRARY);
    }

    public boolean generationDistinctTables() {
        return get(ConfigKeys.GENERATION_DISTINCT_TABLES);
    }

    public WindowScope windowScope() {
        return get(ConfigKeys.KEYS_WINDOW_SCOPE);
    }

    public EmptyStagePolicy emptyStagePolicy() {
        return get(ConfigKeys.GENERATION_EMPTY_STAGE_POLICY);
    }

    public ReplacePattern replacePattern() {
        return get(ConfigKeys.GENERATION_REPLACE_PATTERN);
    }

    public ReplaceUnmatched replaceUnmatched() {
        return get(ConfigKeys.GENERATION_REPLACE_UNMATCHED);
    }

    public ReplaceUnparseable replaceUnparseable() {
        return get(ConfigKeys.GENERATION_REPLACE_UNPARSEABLE);
    }

    public ReplaceIdentity replaceIdentity() {
        return get(ConfigKeys.GENERATION_REPLACE_IDENTITY);
    }

    public ReplaceDiagnostics replaceDiagnostics() {
        return get(ConfigKeys.GENERATION_REPLACE_DIAGNOSTICS);
    }

    public boolean assemblyDistinct() {
        return get(ConfigKeys.ASSEMBLY_DISTINCT);
    }

    public long assemblyMandatoryLimit() {
        return get(ConfigKeys.ASSEMBLY_MANDATORY_LIMIT);
    }

    public OptionalMode optionalMode() {
        return get(ConfigKeys.OPTIONAL_MODE);
    }

    public boolean optionalIncludeAbsent() {
        return get(ConfigKeys.OPTIONAL_INCLUDE_ABSENT);
    }

    public SizeSet optionalSizes() {
        return get(ConfigKeys.OPTIONAL_SIZES);
    }

    public SizeSet optionalProducedSizes() {
        return get(ConfigKeys.OPTIONAL_PRODUCED_SIZES);
    }

    public long optionalSubsetRowLimit() {
        return get(ConfigKeys.OPTIONAL_SUBSET_ROW_LIMIT);
    }

    /** The text after every sheet; under Python an empty one is a line break (the legacy bundle's reader.core.concatenator=\n). */
    public String renderConcatenator() {
        String configured = get(ConfigKeys.RENDER_CONCATENATOR);
        return configured.isEmpty() && runsPython() ? "\n" : configured;
    }

    public SheetConcatenator renderSheetConcatenator() {
        return get(ConfigKeys.RENDER_SHEET_CONCATENATOR);
    }

    /** Whether values render as written: as configured, and always for Python, whose line breaks are its syntax. */
    public boolean renderPreserveWhitespace() {
        return get(ConfigKeys.RENDER_PRESERVE_WHITESPACE) || runsPython();
    }

    public List<String> renderSpaceBefore() {
        return get(ConfigKeys.RENDER_SPACE_BEFORE);
    }

    public List<String> renderSpaceAfter() {
        return get(ConfigKeys.RENDER_SPACE_AFTER);
    }

    public List<String> renderNewlineBefore() {
        return get(ConfigKeys.RENDER_NEWLINE_BEFORE);
    }

    public List<String> renderNewlineAfter() {
        return get(ConfigKeys.RENDER_NEWLINE_AFTER);
    }

    public boolean renderMarkerReplacement() {
        return get(ConfigKeys.RENDER_MARKER_REPLACEMENT);
    }

    public String constraintsFile() {
        return get(ConfigKeys.CONSTRAINTS_FILE);
    }

    public String constraintsBondsFile() {
        return get(ConfigKeys.CONSTRAINTS_BONDS_FILE);
    }

    public int coverageStrength() {
        return get(ConfigKeys.COVERAGE_STRENGTH);
    }

    public CoverageMethod coverageMethod() {
        return get(ConfigKeys.COVERAGE_METHOD);
    }

    public long coverageBudget() {
        return get(ConfigKeys.COVERAGE_BUDGET);
    }

    public long coverageHardCap() {
        return get(ConfigKeys.COVERAGE_HARD_CAP);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof RunConfig other && values.equals(other.values));
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "RunConfig" + values;
    }
}
