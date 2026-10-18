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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The closed registry of target settings, in the order of the embedded defaults file. Ranges are finite and
 * positive unless a key's description says a zero has a defined meaning (architecture.xml, configuration).
 */
public final class ConfigKeys {

    private static final List<ConfigKey<?>> ALL = new ArrayList<>();

    // ---- schema and runtime ------------------------------------------------------------------------------
    public static final ConfigKey<Integer> SCHEMA_VERSION = add("cf.schema.version",
            "Version of this settings schema; this build reads exactly 1.", Parsers.intRange(1, 1));
    public static final ConfigKey<Integer> JAVA_RELEASE = add("cf.java.release",
            "Java release the distribution targets; this build is compiled for exactly 25.", Parsers.intRange(25, 25));
    public static final ConfigKey<RunConfig.RuntimeMode> RUNTIME_MODE = add("cf.runtime.mode",
            "Where the framework runs. Only one JVM hosts every module.",
            Parsers.choice(RunConfig.RuntimeMode.class, RunConfig.RuntimeMode::token,
                    Set.of("multi-jvm", "distributed", "grpc", "http", "server", "remote")));
    public static final ConfigKey<RunConfig.StorageMode> STORAGE_MODE = add("cf.storage.mode",
            "Where run state lives. Heap only: no database, no embedded store, no disk transport.",
            Parsers.choice(RunConfig.StorageMode.class, RunConfig.StorageMode::token,
                    Set.of("postgres", "postgresql", "sql", "jdbc", "h2", "hsqldb", "sqlite", "disk", "file", "redis",
                            "rocksdb")));

    // ---- key encoding --------------------------------------------------------------------------------------
    public static final ConfigKey<KeyEncodingPolicy> KEYS_ENCODING = add("cf.keys.encoding",
            "AUTO_PRESERVE picks SIGNED_BYTE when every label fits -127..127 and PACKED_LEGACY16 otherwise; "
                    + "SIGNED_BYTE is strict compact mode; PACKED_LEGACY16 forces two octets per key.",
            raw -> KeyEncodingPolicy.parse(raw.trim()).orElseThrow(() -> new ConfigValueException(
                    "expected one of AUTO_PRESERVE, SIGNED_BYTE, PACKED_LEGACY16, got '" + raw.trim() + "'", false)));
    public static final ConfigKey<RunConfig.LogicalLabels> KEYS_LOGICAL_LABELS = add("cf.keys.logical.labels",
            "How value labels are numbered. 'preserve' keeps the legacy numbering S+1..S+C and refuses labels the "
                    + "encoding cannot hold; 'legacy-window' reproduces the legacy identity strategy of the 16-bit tier: the "
                    + "same numbering while it fits 32767, otherwise the window of C labels inside -32767..32767 with the "
                    + "fewest characters (decision F2). Relabelling for size (anchor, optimal) is not available.",
            Parsers.choice(RunConfig.LogicalLabels.class, RunConfig.LogicalLabels::token,
                    Set.of("anchor", "optimal", "window", "relabel")));
    public static final ConfigKey<RunConfig.WindowScope> KEYS_WINDOW_SCOPE = add("cf.keys.window.scope",
            "What the legacy-window mode counts against its 65535 labels: 'all' counts data sheets, values and virtual "
                    + "sheets, 'cells' only values (legacy core.keys.scope). 'preserve' ignores it.",
            Parsers.choice(RunConfig.WindowScope.class, RunConfig.WindowScope::token, Set.of()));

    // ---- execution -----------------------------------------------------------------------------------------
    public static final ConfigKey<RunConfig.ExecutionMode> EXECUTION_MODE = add("cf.execution.mode",
            "Trusted, cooperative in-JVM execution. OS sandboxes, process isolation and forced kill do not exist here.",
            Parsers.choice(RunConfig.ExecutionMode.class, RunConfig.ExecutionMode::token,
                    Set.of("process", "process-isolated", "docker", "container", "os-sandbox", "sandbox", "remote")));
    public static final ConfigKey<Integer> EXECUTION_WORKERS = add("cf.execution.workers",
            "Fixed number of CPU workers for generation, compilation and execution (1..1024).",
            Parsers.intRange(1, 1024));
    public static final ConfigKey<Integer> EXECUTION_REPEAT_COUNT = add("cf.execution.repeat.count",
            "Repeats K of every candidate; 1 means a single measurement (1..1000000).", Parsers.intRange(1, 1_000_000));
    public static final ConfigKey<Integer> EXECUTION_RETRY_MAX_ATTEMPTS = add("cf.execution.retry.maxAttempts",
            "Attempts allowed per sample, first try included; 1 means no retry (1..1000).", Parsers.intRange(1, 1000));
    public static final ConfigKey<Long> EXECUTION_TIMEOUT_MILLIS = add("cf.execution.timeout.millis",
            "Cooperative per-sample deadline in milliseconds (1..86400000).", Parsers.longRange(1, 86_400_000L));
    public static final ConfigKey<Long> EXECUTION_CANCEL_GRACE_MILLIS = add("cf.execution.cancel.grace.millis",
            "How long a cancelled sample may take to stop before the plugin is reported uncooperative (1..3600000).",
            Parsers.longRange(1, 3_600_000L));
    public static final ConfigKey<Integer> EXECUTION_OUTPUT_MAX_BYTES = add("cf.execution.output.maxBytes",
            "Captured output kept per sample, in bytes (1..1073741824).", Parsers.intRange(1, 1 << 30));
    public static final ConfigKey<String> RUN_ID = add("cf.run.id",
            "The run's name in every sample key: 1..64 characters of [A-Za-z0-9._-] (legacy reader.handoff.runId and the "
                    + "executor argument runId).", Parsers.runId());
    public static final ConfigKey<String> EXECUTION_SUT = add("cf.execution.sut",
            "What runs the candidates: 'generated-java' compiles each rendered candidate in memory and reads its verdict "
                    + "field; any other value is the id of a typed SUT plugin found with ServiceLoader.", Parsers.sutName());
    public static final ConfigKey<List<String>> EXECUTION_CLASSPATH = add("cf.execution.classpath",
            "Extra class path, entries separated by the platform path separator, for compiling and running generated "
                    + "candidates and for finding SUT plugins; empty for none (legacy -Dfw.cp).", Parsers.pathList());
    public static final ConfigKey<String> PYTHON_EXECUTABLE = add("cf.python.executable",
            "The Python interpreter the Python add-on starts for each candidate under cf.execution.sut=python: a command found "
                    + "on PATH or a file path (legacy py_executor --python / bundle python_cmd).", Parsers.path());
    public static final ConfigKey<List<String>> PYTHON_PATH = add("cf.python.path",
            "Directories put before PYTHONPATH for every Python candidate, separated by the platform path separator, so "
                    + "it can import the system under test; empty for none.", Parsers.pathList());
    public static final ConfigKey<List<String>> PYTHON_ENV = add("cf.python.env",
            "Environment variables for every Python candidate, NAME=VALUE entries separated by ';' (the legacy bundle's environment, "
                    + "such as BUNDLE_SUT_ROOT); they win over the inherited environment; empty for none.", Parsers.envAssignments());
    public static final ConfigKey<String> PYTHON_WORKDIR = add("cf.python.workdir",
            "The working directory of every Python candidate; empty for the directory of the scenario file (or this "
                    + "process's working directory when the scenario was not read from a file).", Parsers.path());
    public static final ConfigKey<String> PYTHON_SANDBOX = add("cf.python.sandbox",
            "Where the Python add-on runs each candidate: empty runs it directly (trusted, as legacy trusted-local); bwrap runs it in "
                    + "a bubblewrap sandbox with a read-only file system, a private /tmp and no network; bwrap-network keeps the host "
                    + "network; docker:IMAGE runs it in a read-only container without network or capabilities, docker:IMAGE@NETWORK "
                    + "on that network (legacy Executor sandbox, networked-api-probe). Only the add-on uses it; the core stays sandbox-free.",
            Parsers.pythonSandbox());
    public static final ConfigKey<RunConfig.VerdictField> EXECUTION_VERDICT = add("cf.execution.verdict",
            "The static int field a generated candidate leaves its verdict in: FW_VAR or FW_CUSTOM_VAR; 0 passes, any other "
                    + "value is a domain failure kept exactly (legacy results.db.onlyFW_CUSTOM_VAR).",
            Parsers.choice(RunConfig.VerdictField.class, RunConfig.VerdictField::token, Set.of()));
    public static final ConfigKey<List<String>> EXECUTION_ENVIRONMENTS = add("cf.execution.environments",
            "Environment ids every candidate is executed in, separated by commas, each 1..64 characters of [A-Za-z0-9._:-]; "
                    + "empty means the one default environment ''. They are labels the system under test receives with every "
                    + "sample: all of them run in this JVM on this host (legacy envId, AI_COMBI_ENVIRONMENT_ID).",
            Parsers.environmentList());
    public static final ConfigKey<RunConfig.RepeatPolicy> EXECUTION_REPEAT_POLICY = add("cf.execution.repeat.policy",
            "How repeats meet environments: 'local' pins the K repeats of candidate c to environment c mod E, 'disperse' sends "
                    + "repeat r of candidate c to environment (c + r) mod E, 'nested' runs K repeats in every environment "
                    + "(legacy fw.controlplane.policy).",
            Parsers.choice(RunConfig.RepeatPolicy.class, RunConfig.RepeatPolicy::token, Set.of()));
    public static final ConfigKey<RunConfig.RepeatScope> EXECUTION_REPEAT_SCOPE = add("cf.execution.repeat.scope",
            "'all' makes every repeat a verdict sample; 'metrics' makes repeat 0 the verdict sample and the other repeats "
                    + "measurements, run only after it gave a verdict (legacy repeatScope).",
            Parsers.choice(RunConfig.RepeatScope.class, RunConfig.RepeatScope::token, Set.of()));
    public static final ConfigKey<Boolean> EXECUTION_SERIALIZE = add("cf.execution.serialize",
            "true executes one sample at a time, for metrics that concurrent samples would disturb; every environment shares "
                    + "this host (legacy fw.controlplane.serializePerHost and fw.controlplane.metricSensitivity).", Parsers.bool());
    public static final ConfigKey<RunConfig.Coverage> EXECUTION_COVERAGE = add("cf.execution.coverage",
            "'lines' instruments generated Java candidates in memory and reports coverage_line, coverage_line_covered and "
                    + "coverage_line_total: the share of the candidate's own executable source lines each sample ran; 'none' "
                    + "instruments nothing (AN-12; no agent, no subprocess; plugins are not instrumented).",
            Parsers.choice(RunConfig.Coverage.class, RunConfig.Coverage::token, Set.of()));
    public static final ConfigKey<Long> RUN_BUDGET_MILLIS = add("cf.run.budget.millis",
            "Time budget of the run's execution in milliseconds: when it has passed, admission stops and running samples are "
                    + "cancelled cooperatively; 0 means no budget (0..2^40; legacy fw.controlplane.budgetMillis).",
            Parsers.longRange(0, 1L << 40));
    public static final ConfigKey<Long> RUN_STOP_CANDIDATES = add("cf.run.stop.candidates",
            "Admit at most this many candidates, the first ones in candidate order; 0 admits every executable candidate "
                    + "(0..2^62).", Parsers.longRange(0, 1L << 62));
    public static final ConfigKey<Long> RUN_STOP_ERRORS = add("cf.run.stop.errors",
            "End admission once this many verdict samples ended BROKEN, TIMEOUT or INFRA_FAIL, counted in candidate order; "
                    + "candidates already admitted finish; the run then fails with RUN_ERROR_BUDGET_EXHAUSTED; 0 means no budget "
                    + "(0..2^62).", Parsers.longRange(0, 1L << 62));
    public static final ConfigKey<RunConfig.StopTarget> RUN_STOP_TARGET = add("cf.run.stop.target",
            "End admission once a PASS verdict sample reports a metric at or beyond a value, in candidate order: KEY>=VALUE or "
                    + "KEY<=VALUE (a coverage target when the candidates report coverage, e.g. coverage_line>=0.9); candidates "
                    + "already admitted finish; empty for none.", Parsers.stopTarget());

    // ---- pipeline and memory -------------------------------------------------------------------------------
    public static final ConfigKey<Integer> PIPELINE_QUEUE_MAX_ITEMS = add("cf.pipeline.queue.maxItems",
            "Bound on candidates waiting in the candidate queue (1..1000000).", Parsers.intRange(1, 1_000_000));
    public static final ConfigKey<Long> PIPELINE_QUEUE_MAX_BYTES = add("cf.pipeline.queue.maxBytes",
            "Bound on bytes held by queued candidates (1..2^40).", Parsers.longRange(1, 1L << 40));
    public static final ConfigKey<Integer> PIPELINE_ORDINAL_WINDOW = add("cf.pipeline.ordinal.window",
            "Candidates executed per deterministic window before results are released in ordinal order (1..1000000).",
            Parsers.intRange(1, 1_000_000));
    public static final ConfigKey<Integer> PIPELINE_LISTENER_MAX_ATTEMPTS = add("cf.pipeline.listener.maxAttempts",
            "Deliveries of one committed observation to a listener that throws, the first included, before the failed "
                    + "delivery is counted (1..100).", Parsers.intRange(1, 100));
    public static final ConfigKey<Long> MEMORY_BUDGET_BYTES = add("cf.memory.budget.bytes",
            "Admission budget for everything a run reserves before allocating (1..2^44).", Parsers.longRange(1, 1L << 44));
    public static final ConfigKey<RunConfig.MemoryExhaustion> MEMORY_EXHAUSTION = add("cf.memory.exhaustion",
            "What happens when a reservation would exceed the budget: fail explicitly with RESOURCE_LIMIT.",
            Parsers.choice(RunConfig.MemoryExhaustion.class, RunConfig.MemoryExhaustion::token,
                    Set.of("spill", "spill-to-disk", "drop", "evict", "ignore")));

    // ---- results -------------------------------------------------------------------------------------------
    public static final ConfigKey<RunConfig.ResultsRetention> RESULTS_RETENTION = add("cf.results.retention",
            "'full' keeps every sample; 'aggregate-only' gives up arbitrary historical queries.",
            Parsers.choice(RunConfig.ResultsRetention.class, RunConfig.ResultsRetention::token, Set.of()));
    public static final ConfigKey<Long> RESULTS_MAX_SAMPLES = add("cf.results.maxSamples",
            "Full-history capacity; the run stops with RESOURCE_LIMIT before admitting another sample (1..2^40).",
            Parsers.longRange(1, 1L << 40));
    public static final ConfigKey<Long> RESULTS_MAX_ATTACHMENT_BYTES = add("cf.results.maxAttachmentBytes",
            "Bytes of retained fail/winner attachments, such as candidate sources (1..2^40).",
            Parsers.longRange(1, 1L << 40));
    public static final ConfigKey<RunConfig.Attach> RESULTS_ATTACH = add("cf.results.attach",
            "Which samples keep their candidate source as an attachment: 'failed' (the legacy fail-only default), 'passed' "
                    + "(the winners of legacy fw.exec.mode=passonly), 'all' or 'none'; cf.results.maxAttachmentBytes bounds them.",
            Parsers.choice(RunConfig.Attach.class, RunConfig.Attach::token, Set.of()));
    public static final ConfigKey<Long> COMPILER_CACHE_MAX_BYTES = add("cf.compiler.cache.maxBytes",
            "Bound on cached compiled bytecode; 0 disables the cache (0..2^40).", Parsers.longRange(0, 1L << 40));

    // ---- analysis ------------------------------------------------------------------------------------------
    public static final ConfigKey<RunConfig.ParetoMode> ANALYSIS_PARETO_MODE = add("cf.analysis.pareto.mode",
            "Pareto archive policy: exact, with its growth charged to the memory budget.",
            Parsers.choice(RunConfig.ParetoMode.class, RunConfig.ParetoMode::token, Set.of()));
    public static final ConfigKey<RunConfig.NonFinitePolicy> ANALYSIS_NONFINITE = add("cf.analysis.nonfinite",
            "NaN and infinite metric values are rejected, never silently coerced.",
            Parsers.choice(RunConfig.NonFinitePolicy.class, RunConfig.NonFinitePolicy::token, Set.of()));
    public static final ConfigKey<Boolean> ANALYSIS_ENABLED = add("cf.analysis.enabled",
            "true analyses every run's final observations: metric statistics, the Pareto front of the goals, the top-K by "
                    + "goal score, balanced picks and repeat intervals (legacy fw.analyzer.enabled).", Parsers.bool());
    public static final ConfigKey<List<RunConfig.AnalysisGoal>> ANALYSIS_GOALS = add("cf.analysis.goals",
            "Declared goals, separated by commas: key:min, key:max or key:target:VALUE, each optionally followed by :WEIGHT "
                    + "(finite, at least 0, default 1); empty for none (legacy fw.analyzer.goals).", Parsers.goalList());
    public static final ConfigKey<List<String>> ANALYSIS_MINIMIZE = add("cf.analysis.minimize",
            "Metric keys to minimise with weight 1, separated by commas; used only when cf.analysis.goals is empty (legacy "
                    + "fw.analyzer.minimizeKeys).", Parsers.metricKeyList());
    public static final ConfigKey<List<String>> ANALYSIS_MAXIMIZE = add("cf.analysis.maximize",
            "Metric keys to maximise with weight 1, separated by commas; used only when cf.analysis.goals is empty (legacy "
                    + "fw.analyzer.maximizeKeys).", Parsers.metricKeyList());
    public static final ConfigKey<RunConfig.AnalysisMode> ANALYSIS_MODE = add("cf.analysis.mode",
            "'exploratory' may add metric keys it finds as goals (cf.analysis.discover); 'formal' uses the declared goals only "
                    + "and needs at least one.", Parsers.choice(RunConfig.AnalysisMode.class, RunConfig.AnalysisMode::token, Set.of()));
    public static final ConfigKey<Boolean> ANALYSIS_DISCOVER = add("cf.analysis.discover",
            "In exploratory mode, metric keys without a declared goal become goals (legacy fw.analyzer.autoDiscover).",
            Parsers.bool());
    public static final ConfigKey<RunConfig.DiscoverMode> ANALYSIS_DISCOVER_MODE = add("cf.analysis.discover.mode",
            "Direction of discovered goals: 'auto' infers it from the key's name (latency, error, cost: min; throughput, "
                    + "score, accuracy: max; otherwise min), 'min' or 'max' (legacy fw.analyzer.autoDiscoverMode).",
            Parsers.choice(RunConfig.DiscoverMode.class, RunConfig.DiscoverMode::token, Set.of()));
    public static final ConfigKey<Double> ANALYSIS_DISCOVER_WEIGHT = add("cf.analysis.discover.weight",
            "Weight of discovered goals in scores, finite and at least 0; 0 keeps them on the front only (legacy "
                    + "fw.analyzer.autoDiscoverWeight).", Parsers.nonNegativeDouble());
    public static final ConfigKey<Integer> ANALYSIS_TOP_K = add("cf.analysis.topK",
            "Eligible samples listed by goal score, best first (1..100000; legacy fw.analyzer.topK).", Parsers.intRange(1, 100_000));
    public static final ConfigKey<Integer> CAMPAIGN_ITERATIONS = add("cf.campaign.iterations",
            "Iterations of the adaptive loop: above 1, every iteration is a complete analysed run and the next one is derived "
                    + "from its winners by cf.campaign.strategy (1..10000; legacy bundle iterate --iterations).", Parsers.intRange(1, 10_000));
    public static final ConfigKey<Integer> CAMPAIGN_STOP_WHEN_STABLE = add("cf.campaign.stopWhenStable",
            "Stop after the Pareto front's candidate ids stayed the same for this many transitions; 0 never stops early "
                    + "(0..10000; legacy --stop-when-stable).", Parsers.intRange(0, 10_000));
    public static final ConfigKey<RunConfig.CampaignStrategy> CAMPAIGN_STRATEGY = add("cf.campaign.strategy",
            "How an iteration follows from the winners of the previous one: 'bias' forbids values absent from the winners "
                    + "(legacy seed bias), 'elitism' adds the winners as Seed_ sheets (legacy ElitismStrategy), 'noop' repeats the "
                    + "scenario.", Parsers.choice(RunConfig.CampaignStrategy.class, RunConfig.CampaignStrategy::token, Set.of()));
    public static final ConfigKey<Double> CAMPAIGN_EXPLORATION_FLOOR = add("cf.campaign.explorationFloor",
            "Share of each sheet's values a bias always keeps (0 < floor <= 1; legacy exploration_floor 0.25).", Parsers.unitInterval());
    public static final ConfigKey<Integer> CAMPAIGN_MIN_WINNER_SUPPORT = add("cf.campaign.minWinnerSupport",
            "Winners a sheet needs before a bias narrows it (1..1000000; legacy min_winner_support 5).", Parsers.intRange(1, 1_000_000));
    public static final ConfigKey<String> CAMPAIGN_SEED_INPUT = add("cf.campaign.seed.input",
            "A BundleSeed (schema 1) the first iteration is derived from with cf.campaign.strategy; empty for a full-space start "
                    + "(legacy --seed-from).", Parsers.path());
    public static final ConfigKey<String> CAMPAIGN_SEED_OUTPUT = add("cf.campaign.seed.output",
            "Where an analysed run writes its BundleSeed (schema 1, with the winners' selections); empty for none (legacy "
                    + "fw.analyzer.seedOutputPath).", Parsers.path());
    public static final ConfigKey<RunConfig.FrontAlgorithm> ANALYSIS_FRONT = add("cf.analysis.front",
            "Ranker of the Pareto front and its diagnostics: pareto, nsga-ii (crowding), nsga-iii (reference points), spea2 "
                    + "(strength and density) or moea-d (Tchebycheff decomposition); every one keeps the exact front.",
            Parsers.choice(RunConfig.FrontAlgorithm.class, RunConfig.FrontAlgorithm::token, Set.of()));

    // ---- determinism and artifacts -------------------------------------------------------------------------
    public static final ConfigKey<Long> RANDOM_SEED = add("cf.random.seed",
            "Run seed; per-sample randomness derives from it, never from thread scheduling.", Parsers.anyLong());
    public static final ConfigKey<Boolean> ORDER_DETERMINISTIC = add("cf.order.deterministic",
            "Execute bounded ordinal windows and feed analysis in ordinal order.", Parsers.bool());
    public static final ConfigKey<Boolean> CHECKPOINT_ENABLED = add("cf.checkpoint.enabled",
            "Allow explicit checkpoint export at quiescent barriers; requires cf.artifacts.enabled.", Parsers.bool());
    public static final ConfigKey<String> CHECKPOINT_FILE = add("cf.checkpoint.file",
            "Where quiescent checkpoints are written (replaced atomically, the last good one kept on failure); empty for none "
                    + "(run --checkpoint PATH).", Parsers.path());
    public static final ConfigKey<Boolean> ARTIFACTS_ENABLED = add("cf.artifacts.enabled",
            "Allow optional XML/JSON/CSV exports; normal runs touch no file.", Parsers.bool());
    public static final ConfigKey<Boolean> UI_ENABLED = add("cf.ui.enabled",
            "Initialize the Swing desktop shell; headless runs never do.", Parsers.bool());

    // ---- planning (architecture revision 2, decision G4-F25; legacy defaults) ------------------------------
    public static final ConfigKey<Boolean> PLAN_AUTO_GENERATE_MISSING_SHEETS = add("cf.plan.autoGenerateMissingSheets",
            "A sheet name used in FW_Seq that names no data sheet becomes an empty virtual sheet instead of an error "
                    + "(legacy workbook.autoGenerateMissingSheetsFromFwSeq).", Parsers.bool());
    public static final ConfigKey<String> PLAN_VIRTUAL_SHEET_PREFIX = add("cf.plan.virtualSheetPrefix",
            "Name prefix of the sheet generated for a headless FW_Seq row: one to 64 characters without white space, "
                    + "commas, brackets or quotes (legacy workbook.virtualSheetNamePrefix).", Parsers.virtualSheetPrefix());
    public static final ConfigKey<Boolean> PLAN_HEADLESS_ROWS_GET_TARGET = add("cf.plan.headlessRowsGetTarget",
            "A FW_Seq row whose first cell is a directive gets a generated sheet as its target "
                    + "(legacy seq.headlessRowsAutoSynthesizeTarget).", Parsers.bool());
    public static final ConfigKey<Boolean> PLAN_NESTED_JOINERS_ENABLED = add("cf.plan.nestedJoinersEnabled",
            "The joiner operands FW_() and FW_()G refer to the tables of earlier joiners (legacy seq.nestedFwBraceEnabled).",
            Parsers.bool());
    public static final ConfigKey<Boolean> PLAN_AUTO_PROMOTE_SINGLE_VERB = add("cf.plan.autoPromoteSingleVerb",
            "A FW_Seq row with exactly one combination verb and no joiner repeats the verb (legacy seq.autoPromoteSingleVerb).",
            Parsers.bool());
    public static final ConfigKey<Boolean> PLAN_AUTO_GENERATE_SHEET_NAMES = add("cf.plan.autoGenerateSheetNames",
            "Every sheet without a FW_SheetNames row gets an empty one (legacy workbook.autoGenerateMissingFwSheetNames).",
            Parsers.bool());

    // ---- generation (supplement to revision 2, decisions G6 and G7; native defaults) -------------------------
    public static final ConfigKey<RunConfig.GenerationLibrary> GENERATION_LIBRARY = add("cf.generation.library",
            "Row order of the first and the ungrouped later passes: PARALLEL (the parallel library's order, which each "
                    + "shipped legacy Core configuration selected) or COMBINATORICSLIB3 (combinatoricslib3 3.3.3, the legacy order "
                    + "when core.threading.parallelizeSubCombosIfPossible was false or absent). Grouped passes and the "
                    + "FW_Permut duplicate marker keep their own legacy generators whatever this says.",
            Parsers.choice(RunConfig.GenerationLibrary.class, RunConfig.GenerationLibrary::token, Set.of()));
    public static final ConfigKey<Boolean> GENERATION_DISTINCT_TABLES = add("cf.generation.distinctTables",
            "Remove repeated rows from a sheet's table after every combination directive and joiner and from its final "
                    + "table, comparing row content only and keeping the first occurrence (legacy distinctify__fwX_Y__Tables).",
            Parsers.bool());
    public static final ConfigKey<RunConfig.EmptyStagePolicy> GENERATION_EMPTY_STAGE_POLICY = add(
            "cf.generation.emptyStagePolicy",
            "What a mandatory sheet whose table has no rows does to the candidates: PRESERVE_EMPTY leaves no candidates "
                    + "and reports the sheet; LEGACY_DROP_WITH_DIAGNOSTIC reproduces the legacy Core, which dropped the sheet "
                    + "from the product, and reports the removed column (supplement, R-M03-SEMANTICS).",
            Parsers.choice(RunConfig.EmptyStagePolicy.class, RunConfig.EmptyStagePolicy::token, Set.of()));
    public static final ConfigKey<RunConfig.ReplacePattern> GENERATION_REPLACE_PATTERN = add("cf.generation.replace.pattern",
            "FW_ReplaceRE patterns that cannot match a key-label text before the run: 'permissive' accepts them, 'warn' "
                    + "reports them, 'strict' refuses the plan (legacy core.replace.patternPolicy).",
            Parsers.choice(RunConfig.ReplacePattern.class, RunConfig.ReplacePattern::token, Set.of()));
    public static final ConfigKey<RunConfig.ReplaceUnmatched> GENERATION_REPLACE_UNMATCHED = add(
            "cf.generation.replace.unmatched",
            "FW_ReplaceRE patterns that changed no row of their sheet: 'ignore', 'warn' or 'fail' "
                    + "(legacy core.replace.unmatchedPolicy).",
            Parsers.choice(RunConfig.ReplaceUnmatched.class, RunConfig.ReplaceUnmatched::token, Set.of()));
    public static final ConfigKey<RunConfig.ReplaceUnparseable> GENERATION_REPLACE_UNPARSEABLE = add(
            "cf.generation.replace.unparseable",
            "A grouped row that is no longer a list of key labels after FW_ReplaceRE: 'warn' drops it and reports the "
                    + "count, 'drop' drops it and reports the count as information, 'fail' stops the run "
                    + "(legacy core.replace.unparseablePolicy).",
            Parsers.choice(RunConfig.ReplaceUnparseable.class, RunConfig.ReplaceUnparseable::token, Set.of()));
    public static final ConfigKey<RunConfig.ReplaceIdentity> GENERATION_REPLACE_IDENTITY = add("cf.generation.replace.identity",
            "An FW_ReplaceRE whose pattern and replacement are equal: 'allow' or 'warn' (legacy core.replace.identityPolicy).",
            Parsers.choice(RunConfig.ReplaceIdentity.class, RunConfig.ReplaceIdentity::token, Set.of()));
    public static final ConfigKey<RunConfig.ReplaceDiagnostics> GENERATION_REPLACE_DIAGNOSTICS = add(
            "cf.generation.replace.diagnostics",
            "'summary' reports, per grouped sheet, the rows seen, rewritten and dropped and the rows each pattern changed; "
                    + "'off' does not (legacy core.replace.diagnostics).",
            Parsers.choice(RunConfig.ReplaceDiagnostics.class, RunConfig.ReplaceDiagnostics::token, Set.of()));

    // ---- candidate assembly ------------------------------------------------------------------------------------
    public static final ConfigKey<Boolean> ASSEMBLY_DISTINCT = add("cf.assembly.distinct",
            "Remove repeated rows from the mandatory product and from every optional table, comparing content and "
                    + "keeping the first (legacy distinctify__fw_final_opt__Tables).", Parsers.bool());
    public static final ConfigKey<Long> ASSEMBLY_MANDATORY_LIMIT = add("cf.assembly.mandatoryLimit",
            "Cut-off of the mandatory product, and of every optional subset's product: the legacy rule keeps the first rows "
                    + "of the product, as many as the product of the trailing sheets' largest ids that stays at or below this "
                    + "value (1..9223372036854775807; legacy core.limitVarGivenLessThan). The maximum, the default, means no cut-off.",
            Parsers.longRange(1, Long.MAX_VALUE));
    public static final ConfigKey<RunConfig.OptionalMode> OPTIONAL_MODE = add("cf.optional.mode",
            "Whether optional sheets are assembled: 'auto' when the plan has optional sheets, 'on' always, 'off' never, "
                    + "so every optional sheet is absent (legacy reader.core.processIsOpt).",
            Parsers.choice(RunConfig.OptionalMode.class, RunConfig.OptionalMode::token, Set.of()));
    public static final ConfigKey<Boolean> OPTIONAL_INCLUDE_ABSENT = add("cf.optional.includeAbsent",
            "Whether each mandatory row also yields the candidate in which every optional sheet is absent, when optional "
                    + "sheets are assembled (legacy reader.core.processBothFinalAndOpt).", Parsers.bool());
    public static final ConfigKey<RunConfig.SizeSet> OPTIONAL_SIZES = add("cf.optional.sizes",
            "The numbers of optional sheets a candidate combines: 'all' (every size from 1 to the number of optional "
                    + "sheets) or a comma-separated list of sizes from 1 to 64 (legacy reader.core.isOptCSVList). Must be a "
                    + "subset of cf.optional.producedSizes.", Parsers.sizeSet());
    public static final ConfigKey<RunConfig.SizeSet> OPTIONAL_PRODUCED_SIZES = add("cf.optional.producedSizes",
            "The sizes the optional contract produces: 'all' or a comma-separated list from 1 to 64 (legacy "
                    + "core.optional.includeOptionalCombiPairsToDBCSVList).", Parsers.sizeSet());
    public static final ConfigKey<Long> OPTIONAL_SUBSET_ROW_LIMIT = add("cf.optional.subsetRowLimit",
            "An optional subset whose product of rows exceeds this is left out with a warning; 0 leaves out every subset "
                    + "that has rows (0..9223372036854775807; legacy core.optional.limitOptionalSheetsCombosMax).",
            Parsers.longRange(0, Long.MAX_VALUE));

    // ---- candidate rendering -----------------------------------------------------------------------------------
    public static final ConfigKey<String> RENDER_CONCATENATOR = add("cf.render.concatenator",
            "Text written after every sheet of a candidate instead of the sheet's FW_SheetNames ending when not empty; the "
                    + "last one is trimmed (legacy reader.core.concatenator). Taken as written, white space included. Empty under "
                    + "cf.execution.sut=python means a line break, as the legacy bundle rendered Python.",
            Parsers.verbatim());
    public static final ConfigKey<RunConfig.SheetConcatenator> RENDER_SHEET_CONCATENATOR = add("cf.render.sheetConcatenator",
            "'apply' joins the values of one row of a sheet with its FW_Concatenator= text, as the authoring layer "
                    + "describes it; 'legacy-ignore' reproduces the legacy Core, which parsed FW_Concatenator and never used it.",
            Parsers.choice(RunConfig.SheetConcatenator.class, RunConfig.SheetConcatenator::token, Set.of()));
    public static final ConfigKey<Boolean> RENDER_PRESERVE_WHITESPACE = add("cf.render.preserveWhitespace",
            "Render every value as written; false refines each value not refined at parse (comments, line breaks, runs "
                    + "of white space outside quotes) as the legacy Reader did (legacy reader.cells.preserveWhitespace). Always "
                    + "true under cf.execution.sut=python, as the legacy bundle rendered Python: line breaks are its syntax.",
            Parsers.bool());
    public static final ConfigKey<List<String>> RENDER_SPACE_BEFORE = add("cf.render.spaceBefore",
            "Regular expressions, separated by single white space characters, before whose matches outside quotes a "
                    + "refined value gains one space (legacy reader.javacode.oneSpace2AddBeforeCharsButNotInQuotesList).",
            Parsers.regexList("\\s{1}"));
    public static final ConfigKey<List<String>> RENDER_SPACE_AFTER = add("cf.render.spaceAfter",
            "Regular expressions, separated by single white space characters, after whose matches outside quotes a "
                    + "refined value gains one space (legacy reader.javacode.oneSpace2AddAfterCharsButNotInQuotesList).",
            Parsers.regexList("\\s{1}"));
    public static final ConfigKey<List<String>> RENDER_NEWLINE_BEFORE = add("cf.render.newlineBefore",
            "Regular expressions, one per line, before whose matches outside quotes a refined value gains a line break "
                    + "(legacy reader.javacode.newLine2AddBeforeThatStartsWithButNotInQuotesList).",
            Parsers.regexList("\\n"));
    public static final ConfigKey<List<String>> RENDER_NEWLINE_AFTER = add("cf.render.newlineAfter",
            "Regular expressions, one per line, after whose matches outside quotes a refined value gains a line break "
                    + "(legacy reader.javacode.newLine2AddAfterThatEndsWithButNotInQuotesList).",
            Parsers.regexList("\\n"));
    public static final ConfigKey<Boolean> RENDER_MARKER_REPLACEMENT = add("cf.render.markerReplacement",
            "Replace every FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE in a rendered candidate by its legacy display id "
                    + "(legacy reader.out.replaceWithCurrentComboSequence).", Parsers.bool());

    // ---- constraints and covering selection --------------------------------------------------------------------
    public static final ConfigKey<String> CONSTRAINTS_FILE = add("cf.constraints.file",
            "Constraint sidecar (JSON, TOML or YAML: params, orders, constraints) to apply; empty for none. A scenario's "
                    + "companion <stem>.constraints.json is read when this is empty.", Parsers.path());
    public static final ConfigKey<String> CONSTRAINTS_BONDS_FILE = add("cf.constraints.bondsFile",
            "Legacy compiled optional-bond file (lines F|R | 1/0 | sheets | label tuples) to apply as well; empty for "
                    + "none (legacy reader.constraints.bondsFile).", Parsers.path());
    public static final ConfigKey<Integer> COVERAGE_STRENGTH = add("cf.coverage.strength",
            "Keep only mandatory rows that cover the feasible t-tuples of the mandatory sheets' rows, t being this value; "
                    + "0 keeps every row (0..64). Not used while cf.coverage.budget is positive: the budget then decides t.",
            Parsers.intRange(0, 64));
    public static final ConfigKey<RunConfig.CoverageMethod> COVERAGE_METHOD = add("cf.coverage.method",
            "'greedy' keeps a row when it covers a tuple not covered yet; 'set-cover' repeatedly takes the row covering most "
                    + "uncovered tuples. Both are greedy: neither claims a minimum suite.",
            Parsers.choice(RunConfig.CoverageMethod.class, RunConfig.CoverageMethod::token, Set.of("optimal", "minimum")));
    public static final ConfigKey<Long> COVERAGE_BUDGET = add("cf.coverage.budget",
            "When positive, the strongest t whose selection keeps at most this many mandatory rows is chosen, or the whole "
                    + "product when it fits; it then decides t, and cf.coverage.strength is not used (0..2^40).",
            Parsers.longRange(0, 1L << 40));
    public static final ConfigKey<Long> COVERAGE_HARD_CAP = add("cf.coverage.hardCap",
            "Covering selection refuses mandatory products larger than this many rows (1..2^40; legacy 2000000).",
            Parsers.longRange(1, 1L << 40));

    private ConfigKeys() {
    }

    private static <T> ConfigKey<T> add(String name, String description, Parsers.Parser<T> parser) {
        ConfigKey<T> key = new ConfigKey<>(name, description, false, parser);
        ALL.add(key);
        return key;
    }

    /** Every declared key, in the order of the embedded defaults file. */
    public static List<ConfigKey<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Declared keys by name, in declaration order. */
    public static Map<String, ConfigKey<?>> byName() {
        Map<String, ConfigKey<?>> map = new LinkedHashMap<>();
        for (ConfigKey<?> key : ALL) {
            map.put(key.name(), key);
        }
        return Collections.unmodifiableMap(map);
    }
}
