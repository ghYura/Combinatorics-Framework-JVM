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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.key.KeyEncodingPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigResolverTest {

    private static final long BIG_HEAP = 1L << 40;

    private static EffectiveConfig resolve(String fileText, Map<String, String> env, List<ConfigResolver.Override> overrides) {
        return ConfigResolver.resolveLayers("test.properties", fileText, env, overrides, BIG_HEAP);
    }

    private static FrameworkException failure(String fileText, Map<String, String> env, List<ConfigResolver.Override> overrides) {
        return assertThrows(FrameworkException.class, () -> resolve(fileText, env, overrides));
    }

    private static List<ErrorCode> codes(FrameworkException e) {
        return e.diagnostics().stream().map(Diagnostic::code).toList();
    }

    @Test
    void embeddedDefaultsEqualTheArchitectsRuntimeProperties() {
        RunConfig c = RunConfig.defaults();
        // hand-copied from handoff/runtime.properties, not read from the file under test
        assertEquals(1, c.schemaVersion());
        assertEquals(25, c.javaRelease());
        assertEquals(RunConfig.RuntimeMode.SINGLE_JVM, c.runtimeMode());
        assertEquals(RunConfig.StorageMode.HEAP, c.storageMode());
        assertEquals(KeyEncodingPolicy.AUTO_PRESERVE, c.keyEncodingPolicy());
        assertEquals(RunConfig.LogicalLabels.PRESERVE, c.logicalLabels());
        assertEquals(RunConfig.ExecutionMode.TRUSTED_COOPERATIVE, c.executionMode());
        assertEquals(4, c.workers());
        assertEquals(1, c.repeatCount());
        assertEquals(1, c.retryMaxAttempts());
        assertEquals(30000L, c.timeoutMillis());
        assertEquals(2000L, c.cancelGraceMillis());
        assertEquals(1048576, c.outputMaxBytes());
        assertEquals(256, c.queueMaxItems());
        assertEquals(16777216L, c.queueMaxBytes());
        assertEquals(256, c.ordinalWindow());
        assertEquals(268435456L, c.memoryBudgetBytes());
        assertEquals(RunConfig.MemoryExhaustion.FAIL_EXPLICITLY, c.memoryExhaustion());
        assertEquals(RunConfig.ResultsRetention.FULL, c.resultsRetention());
        assertEquals(100000L, c.resultsMaxSamples());
        assertEquals(16777216L, c.resultsMaxAttachmentBytes());
        assertEquals(33554432L, c.compilerCacheMaxBytes());
        assertEquals(RunConfig.ParetoMode.EXACT_BUDGETED, c.paretoMode());
        assertEquals(RunConfig.NonFinitePolicy.REJECT, c.nonFinitePolicy());
        assertEquals(1L, c.randomSeed());
        assertTrue(c.deterministicOrder());
        assertFalse(c.checkpointEnabled());
        assertFalse(c.artifactsEnabled());
        assertFalse(c.uiEnabled());
        // revision 2, decision G4-F25
        assertTrue(c.planAutoGenerateMissingSheets());
        assertEquals("FW_VIRTUAL_", c.planVirtualSheetPrefix());
        assertTrue(c.planHeadlessRowsGetTarget());
        assertTrue(c.planNestedJoinersEnabled());
        assertTrue(c.planAutoPromoteSingleVerb());
        assertTrue(c.planAutoGenerateSheetNames());
        // supplement to revision 2, decisions G6 and G7
        assertEquals(RunConfig.GenerationLibrary.PARALLEL, c.generationLibrary());
        assertFalse(c.generationDistinctTables());
        // M05: environments, repeats, serialization, run budget, listener deliveries, attachments
        assertEquals(List.of(""), c.environments());
        assertEquals(RunConfig.RepeatPolicy.LOCAL, c.repeatPolicy());
        assertEquals(RunConfig.RepeatScope.ALL, c.repeatScope());
        assertFalse(c.serializeSamples());
        assertEquals(0L, c.runBudgetMillis());
        assertEquals(0L, c.stopCandidates());
        assertEquals(0L, c.stopErrors());
        assertTrue(c.stopTarget().isNone());
        assertEquals(3, c.listenerMaxAttempts());
        assertEquals(RunConfig.Attach.FAILED, c.resultsAttach());
        // M06: the analysis of a run's final observations
        assertFalse(c.analysisEnabled());
        assertEquals(List.of(), c.analysisGoals());
        assertEquals(RunConfig.AnalysisMode.EXPLORATORY, c.analysisMode());
        assertTrue(c.analysisDiscover());
        assertEquals(RunConfig.DiscoverMode.AUTO, c.analysisDiscoverMode());
        assertEquals(1.0, c.analysisDiscoverWeight());
        assertEquals(20, c.analysisTopK());
        assertEquals(RunConfig.FrontAlgorithm.NSGA_II, c.analysisFront());
    }

    @Test
    void goalsAreKeyModeTargetAndWeightWithTheirErrorsRefused() {
        RunConfig c = resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.analysis.goals",
                " latency_ms:min , throughput:max:2.5, size:target:100:0 ", "--set"))).config();
        assertEquals(List.of(new RunConfig.AnalysisGoal("latency_ms", RunConfig.GoalMode.MIN, 0, 1),
                new RunConfig.AnalysisGoal("throughput", RunConfig.GoalMode.MAX, 0, 2.5),
                new RunConfig.AnalysisGoal("size", RunConfig.GoalMode.TARGET, 100, 0)), c.analysisGoals());
        for (String bad : List.of("latency", "latency:fast", "size:target", "a:min:-1", "a:min:NaN", "a:min,a:max", "1a:min",
                "a:min:1:2")) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> resolve(null, Map.of(),
                    List.of(new ConfigResolver.Override("cf.analysis.goals", bad, "--set"))), bad);
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(e), bad);
        }
    }

    @Test
    void environmentsAreDistinctLabelsAndEmptyPiecesAreDroppedAsTheLegacyControlPlaneDid() {
        RunConfig c = resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.execution.environments", " a , ,local:run-r,b ",
                "--set"))).config();
        assertEquals(List.of("a", "local:run-r", "b"), c.environments());
        for (String bad : List.of("a,a", "a b", "x".repeat(65), "\u00e9")) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> resolve(null, Map.of(),
                    List.of(new ConfigResolver.Override("cf.execution.environments", bad, "--set"))), bad);
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(e), bad);
        }
    }

    @Test
    void theGenerationSettingsAcceptExactlyTheirTokens() {
        assertEquals(List.of("CF_GENERATION_LIBRARY", "CF_GENERATION_DISTINCTTABLES", "CF_GENERATION_EMPTYSTAGEPOLICY",
                        "CF_GENERATION_REPLACE_PATTERN", "CF_GENERATION_REPLACE_UNMATCHED", "CF_GENERATION_REPLACE_UNPARSEABLE",
                        "CF_GENERATION_REPLACE_IDENTITY", "CF_GENERATION_REPLACE_DIAGNOSTICS"),
                ConfigKeys.all().stream().filter(k -> k.name().startsWith("cf.generation.")).map(ConfigKey::environmentAlias).toList());
        RunConfig c = resolve(null, Map.of("CF_GENERATION_LIBRARY", "COMBINATORICSLIB3"),
                List.of(new ConfigResolver.Override("cf.generation.distinctTables", "true", "--set"))).config();
        assertEquals(RunConfig.GenerationLibrary.COMBINATORICSLIB3, c.generationLibrary());
        assertTrue(c.generationDistinctTables());
        for (String bad : List.of("parallel", "combinatoricslib3", "LIB3", "", "PARALLEL,COMBINATORICSLIB3")) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> resolve(null, Map.of(),
                    List.of(new ConfigResolver.Override("cf.generation.library", bad, "--set"))), bad);
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(e), bad);
        }
        for (String bad : List.of("yes", "TRUE", "1", "")) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> resolve(null, Map.of(),
                    List.of(new ConfigResolver.Override("cf.generation.distinctTables", bad, "--set"))), bad);
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(e), bad);
        }
    }

    /**
     * Decision C1 (supplement to revision 2): defaults, then the legacy files in the order given, then the native
     * file, then the declared environment aliases, then explicit overrides; every replaced value is reported with
     * both origins, and replacing a default is not a conflict.
     */
    @Test
    void thePrecedenceIsFrozenAndEveryReplacedValueIsReportedWithBothOrigins() {
        LegacyImport legacy = LegacyImporter.importSources(List.of(
                new LegacyImporter.Source("first.properties", "core.threading.parallelizeSubCombosIfPossible=true\nfw.exec.threads=6\n"),
                new LegacyImporter.Source("second.properties", "core.threading.parallelizeSubCombosIfPossible=false\n")), Map.of());
        String file = "cf.generation.library=PARALLEL\ncf.execution.workers=6\ncf.random.seed=7\n";
        Map<String, String> env = Map.of("CF_GENERATION_LIBRARY", "COMBINATORICSLIB3", "CF_RANDOM_SEED", "8");
        List<ConfigResolver.Override> overrides = List.of(new ConfigResolver.Override("cf.generation.library", "PARALLEL", "--set"));

        EffectiveConfig onlyLegacy = ConfigResolver.resolve(null, Map.of(), List.of(), legacy);
        assertEquals(RunConfig.GenerationLibrary.COMBINATORICSLIB3, onlyLegacy.config().generationLibrary(), "the later legacy file wins");
        assertEquals(SourceLocation.of("second.properties", 1), onlyLegacy.entry(ConfigKeys.GENERATION_LIBRARY).location());
        assertEquals(List.of(), onlyLegacy.conflicts(), "two legacy files are one layer: the import already decided");

        EffectiveConfig all = ConfigResolver.resolveLayers("cf.properties", file, env, overrides, Runtime.getRuntime().maxMemory(), legacy);
        assertEquals(RunConfig.GenerationLibrary.PARALLEL, all.config().generationLibrary());
        assertEquals(ConfigOrigin.OVERRIDE, all.entry(ConfigKeys.GENERATION_LIBRARY).origin());
        assertEquals(8L, all.config().randomSeed());
        assertEquals(6, all.config().workers());
        List<String> reported = all.conflicts().stream().map(c -> c.key() + " " + c.overridden().displayValue() + "@"
                + c.overridden().origin() + " -> " + c.winner().displayValue() + "@" + c.winner().origin()).toList();
        assertEquals(List.of(
                "cf.generation.library COMBINATORICSLIB3@LEGACY -> PARALLEL@FILE",
                "cf.random.seed 7@FILE -> 8@ENVIRONMENT",
                "cf.generation.library PARALLEL@FILE -> COMBINATORICSLIB3@ENVIRONMENT",
                "cf.generation.library COMBINATORICSLIB3@ENVIRONMENT -> PARALLEL@OVERRIDE"), reported,
                "cf.execution.workers is set to the same value twice, which is no conflict");
        assertTrue(all.toXml().contains("<overridden key=\"cf.generation.library\" value=\"COMBINATORICSLIB3\" origin=\"LEGACY\" "
                + "location=\"second.properties:1\" by=\"FILE\"/>"), all.toXml());
        assertTrue(all.toText().contains("cf.random.seed: 7 [FILE cf.properties:3] overridden by 8 [ENVIRONMENT]"), all.toText());
    }

    @Test
    void thePlanSettingsHaveTheirEnvironmentAliasesAndStrictValues() {
        assertEquals(List.of("CF_PLAN_AUTOGENERATEMISSINGSHEETS", "CF_PLAN_VIRTUALSHEETPREFIX", "CF_PLAN_HEADLESSROWSGETTARGET",
                        "CF_PLAN_NESTEDJOINERSENABLED", "CF_PLAN_AUTOPROMOTESINGLEVERB", "CF_PLAN_AUTOGENERATESHEETNAMES"),
                ConfigKeys.all().stream().filter(k -> k.name().startsWith("cf.plan.")).map(ConfigKey::environmentAlias).toList());
        RunConfig c = resolve(null, Map.of("CF_PLAN_VIRTUALSHEETPREFIX", "V_", "CF_PLAN_NESTEDJOINERSENABLED", "false"),
                List.of(new ConfigResolver.Override("cf.plan.autoGenerateSheetNames", "false", "--set"))).config();
        assertEquals("V_", c.planVirtualSheetPrefix());
        assertFalse(c.planNestedJoinersEnabled());
        assertFalse(c.planAutoGenerateSheetNames());
        assertTrue(c.planAutoPromoteSingleVerb());
        assertEquals("x".repeat(64), resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.plan.virtualSheetPrefix",
                " " + "x".repeat(64) + " ", "--set"))).config().planVirtualSheetPrefix(), "the value is trimmed like every value");
        for (String bad : List.of("", "  ", "a b", "a,b", "a(b", "a)b", "a\"b", "a\u0001b", "x".repeat(65))) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> resolve(null, Map.of(),
                    List.of(new ConfigResolver.Override("cf.plan.virtualSheetPrefix", bad, "--set"))), bad);
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(e), bad);
        }
        for (String bad : List.of("yes", "TRUE", "1", "")) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> resolve(null, Map.of(),
                    List.of(new ConfigResolver.Override("cf.plan.autoPromoteSingleVerb", bad, "--set"))), bad);
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(e), bad);
        }
    }

    @Test
    void everyDeclaredKeyHasADefaultAndTheDefaultsDeclareNothingElse() {
        EffectiveConfig effective = resolve(null, Map.of(), List.of());
        assertEquals(ConfigKeys.all().size(), effective.entries().size());
        assertEquals(103, effective.entries().size());
        for (ConfigEntry entry : effective.entries()) {
            assertEquals(ConfigOrigin.DEFAULT, entry.origin(), entry.key());
        }
        assertEquals(List.of(), effective.diagnostics());
    }

    @Test
    void layersOverrideInTheDocumentedOrderAndRecordTheirOrigin() {
        String file = "# tuning\ncf.execution.workers=8\ncf.random.seed=42\ncf.execution.repeat.count=3\n";
        Map<String, String> env = Map.of("CF_EXECUTION_WORKERS", "16", "CF_RANDOM_SEED", "43");
        List<ConfigResolver.Override> overrides = List.of(new ConfigResolver.Override("cf.random.seed", "44", "--seed"));

        EffectiveConfig effective = resolve(file, env, overrides);
        RunConfig c = effective.config();
        assertEquals(16, c.workers());
        assertEquals(44L, c.randomSeed());
        assertEquals(3, c.repeatCount());
        assertEquals(1, c.retryMaxAttempts());

        ConfigEntry workers = effective.entry(ConfigKeys.EXECUTION_WORKERS);
        assertEquals(ConfigOrigin.ENVIRONMENT, workers.origin());
        assertEquals("environment (CF_EXECUTION_WORKERS)", workers.location().toString());
        ConfigEntry seed = effective.entry(ConfigKeys.RANDOM_SEED);
        assertEquals(ConfigOrigin.OVERRIDE, seed.origin());
        assertEquals("--seed (cf.random.seed)", seed.location().toString());
        ConfigEntry repeat = effective.entry(ConfigKeys.EXECUTION_REPEAT_COUNT);
        assertEquals(ConfigOrigin.FILE, repeat.origin());
        assertEquals("test.properties:4", repeat.location().toString());
        assertEquals(ConfigOrigin.DEFAULT, effective.entry(ConfigKeys.EXECUTION_TIMEOUT_MILLIS).origin());
    }

    @Test
    void onlyDeclaredEnvironmentAliasesAreRead() {
        Map<String, String> env = Map.of("CF_HOME", "/somewhere", "CF_EXECUTON_WORKERS", "99", "PATH", "/usr/bin",
                "cf_execution_workers", "77");
        assertEquals(4, resolve(null, env, List.of()).config().workers());
        assertEquals("CF_EXECUTION_CANCEL_GRACE_MILLIS", ConfigKeys.EXECUTION_CANCEL_GRACE_MILLIS.environmentAlias());
        assertEquals("CF_KEYS_ENCODING", ConfigKeys.KEYS_ENCODING.environmentAlias());
    }

    @Test
    void environmentAliasesDoNotDependOnTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); // upper-case of 'i' is a dotted capital here
            assertEquals("CF_PIPELINE_QUEUE_MAXITEMS", ConfigKeys.PIPELINE_QUEUE_MAX_ITEMS.environmentAlias());
            assertEquals(8, resolve(null, Map.of("CF_PIPELINE_QUEUE_MAXITEMS", "8"), List.of()).config().queueMaxItems());
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void valuesAreTrimmedButOtherwiseStrict() {
        assertEquals(12, resolve("cf.execution.workers =   12  \n", Map.of(), List.of()).config().workers());
        for (String bad : new String[]{"", "4.0", "four", "0", "-1", "1025", "4 4", "0x10"}) {
            FrameworkException e = failure("cf.execution.workers=" + bad + "\n", Map.of(), List.of());
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(e), "workers=" + bad);
            assertEquals(1, e.primary().location().line());
        }
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE),
                codes(failure("cf.order.deterministic=yes\n", Map.of(), List.of())));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE),
                codes(failure("cf.order.deterministic=TRUE\n", Map.of(), List.of())));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE),
                codes(failure("cf.keys.encoding=auto_preserve\n", Map.of(), List.of())));
        assertEquals(-5L, resolve("cf.random.seed=-5\n", Map.of(), List.of()).config().randomSeed());
        assertEquals(0L, resolve("cf.compiler.cache.maxBytes=0\n", Map.of(), List.of()).config().compilerCacheMaxBytes());
    }

    @Test
    void thePinnedSchemaAndJavaReleaseAreExact() {
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(failure("cf.schema.version=2\n", Map.of(), List.of())));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(failure("cf.java.release=21\n", Map.of(), List.of())));
    }

    @Test
    void architectureExcludedModesAreUnsupportedCapabilitiesNotTypos() {
        for (String mode : new String[]{"docker", "os-sandbox", "process-isolated"}) {
            FrameworkException e = failure("cf.execution.mode=" + mode + "\n", Map.of(), List.of());
            assertEquals(List.of(ErrorCode.UNSUPPORTED_CAPABILITY), codes(e), mode);
            assertTrue(e.primary().message().contains("trusted-cooperative"), e.primary().message());
        }
        for (String storage : new String[]{"postgres", "h2", "sqlite", "disk", "redis", "rocksdb"}) {
            assertEquals(List.of(ErrorCode.UNSUPPORTED_CAPABILITY),
                    codes(failure("cf.storage.mode=" + storage + "\n", Map.of(), List.of())), storage);
        }
        assertEquals(List.of(ErrorCode.UNSUPPORTED_CAPABILITY),
                codes(failure("cf.runtime.mode=distributed\n", Map.of(), List.of())));
        assertEquals(List.of(ErrorCode.UNSUPPORTED_CAPABILITY),
                codes(failure("cf.memory.exhaustion=spill-to-disk\n", Map.of(), List.of())));
        assertEquals(List.of(ErrorCode.UNSUPPORTED_CAPABILITY),
                codes(failure("cf.keys.logical.labels=anchor\n", Map.of(), List.of())));
        // a token nobody knows is an ordinary invalid value
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE),
                codes(failure("cf.execution.mode=turbo\n", Map.of(), List.of())));
    }

    @Test
    void unknownKeysFailWithSuggestionsAndNeverPassSilently() {
        FrameworkException typo = failure("cf.execution.workrs=2\n", Map.of(), List.of());
        assertEquals(List.of(ErrorCode.CONFIG_UNKNOWN_KEY), codes(typo));
        assertEquals("cf.execution.workers", typo.primary().attributes().get("suggestion"));
        assertEquals(1, typo.primary().location().line());

        FrameworkException legacy = failure("core.keys.dispatch=auto\ndb.host=localhost\n", Map.of(), List.of());
        assertEquals(List.of(ErrorCode.CONFIG_UNKNOWN_KEY, ErrorCode.CONFIG_UNKNOWN_KEY), codes(legacy));
        assertTrue(legacy.primary().message().contains("legacy"));
        assertFalse(legacy.primary().attributes().containsKey("suggestion"));

        FrameworkException override = failure(null, Map.of(),
                List.of(new ConfigResolver.Override("cf.nonsense", "1", "--set")));
        assertEquals(List.of(ErrorCode.CONFIG_UNKNOWN_KEY), codes(override));
        assertEquals("--set (cf.nonsense)", override.primary().location().toString());
    }

    @Test
    void everyProblemIsReportedTogether() {
        FrameworkException e = failure("cf.execution.workers=zero\ncf.bogus=1\ncf.execution.workers=2\n"
                + "cf.order.deterministic=maybe\n", Map.of("CF_RANDOM_SEED", "x"), List.of());
        assertEquals(List.of(ErrorCode.CONFIG_DUPLICATE_KEY, ErrorCode.CONFIG_UNKNOWN_KEY,
                ErrorCode.CONFIG_INVALID_VALUE, ErrorCode.CONFIG_INVALID_VALUE, ErrorCode.CONFIG_INVALID_VALUE),
                codes(e));
    }

    @Test
    void anEmptyEnvironmentValueIsAnInvalidValueNotAnAbsentOne() {
        FrameworkException e = failure(null, Map.of("CF_EXECUTION_WORKERS", ""), List.of());
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(e));
        assertEquals("ENVIRONMENT", e.primary().attributes().get("origin"));
    }

    @Test
    void aSubBudgetLargerThanTheTotalIsInconsistent() {
        FrameworkException e = failure("cf.memory.budget.bytes=1000000\ncf.pipeline.queue.maxBytes=2000000\n"
                + "cf.compiler.cache.maxBytes=3000000\n", Map.of(), List.of());
        assertEquals(List.of(ErrorCode.CONFIG_INCONSISTENT, ErrorCode.CONFIG_INCONSISTENT,
                ErrorCode.CONFIG_INCONSISTENT, ErrorCode.CONFIG_INCONSISTENT), codes(e));
        assertEquals("cf.pipeline.queue.maxBytes", e.diagnostics().get(0).attributes().get("key"));
        assertEquals(2, e.diagnostics().get(0).location().line());
    }

    @Test
    void aCheckpointNeedsTheArtifactSubsystem() {
        FrameworkException e = failure("cf.checkpoint.enabled=true\n", Map.of(), List.of());
        assertEquals(List.of(ErrorCode.CONFIG_INCONSISTENT), codes(e));
        EffectiveConfig ok = resolve("cf.checkpoint.enabled=true\ncf.artifacts.enabled=true\n", Map.of(), List.of());
        assertTrue(ok.config().checkpointEnabled());
        assertTrue(ok.config().artifactsEnabled());
        // M07: a checkpoint file is written only when checkpoints are enabled (run --checkpoint PATH sets all three)
        assertEquals(List.of(ErrorCode.CONFIG_INCONSISTENT), codes(failure("cf.checkpoint.file=cp.xml\n", Map.of(), List.of())));
        assertEquals("cp.xml", resolve("cf.checkpoint.file=cp.xml\ncf.checkpoint.enabled=true\ncf.artifacts.enabled=true\n", Map.of(),
                List.of()).config().checkpointFile());
    }

    @Test
    void warningsAreReportedWithoutBlockingTheRun() {
        EffectiveConfig effective = resolve("cf.execution.workers=64\ncf.pipeline.ordinal.window=8\n",
                Map.of(), List.of());
        assertEquals(64, effective.config().workers());
        assertEquals(1, effective.diagnostics().size());
        for (Diagnostic d : effective.diagnostics()) {
            assertEquals(Severity.WARNING, d.severity());
            assertEquals(ErrorCode.CONFIG_INCONSISTENT, d.code());
        }
        assertTrue(effective.diagnostics().get(0).message().contains("ordinal window"));
    }

    @Test
    void aggregateRetentionRefusesConsumersThatNeedObservations() {
        for (String consumer : List.of("cf.analysis.enabled=true", "cf.campaign.seed.output=seed.json", "cf.campaign.iterations=2",
                "cf.campaign.seed.input=seed.json", "cf.checkpoint.enabled=true\ncf.artifacts.enabled=true")) {
            FrameworkException e = failure("cf.results.retention=aggregate-only\n" + consumer + "\n", Map.of(), List.of());
            assertEquals(List.of(ErrorCode.CONFIG_INCONSISTENT), codes(e));
            assertTrue(e.diagnostics().getFirst().message().contains("needs full retention"));
        }
    }

    @Test
    void aBudgetBeyondTheHeapIsAWarning() {
        EffectiveConfig effective = ConfigResolver.resolveLayers(null, null, Map.of(), List.of(), 100_000_000L);
        assertEquals(1, effective.diagnostics().size());
        assertTrue(effective.diagnostics().get(0).message().contains("maximum heap"));
        assertEquals("100000000", effective.diagnostics().get(0).attributes().get("maxHeapBytes"));
    }

    @Test
    void aMissingOrUndecodableFileIsReportedAsUnreadable(@TempDir Path dir) throws IOException {
        Path missing = dir.resolve("absent.properties");
        FrameworkException gone = assertThrows(FrameworkException.class, () -> ConfigResolver.resolve(missing, Map.of(), List.of()));
        assertEquals(ErrorCode.CONFIG_UNREADABLE, gone.code());
        assertEquals(missing.toString(), gone.primary().location().origin());

        Path latin1 = dir.resolve("latin1.properties");
        Files.write(latin1, new byte[]{'a', '=', (byte) 0xC3, (byte) 0x28, '\n'});
        FrameworkException bad = assertThrows(FrameworkException.class, () -> ConfigResolver.resolve(latin1, Map.of(), List.of()));
        assertEquals(ErrorCode.CONFIG_UNREADABLE, bad.code());
    }

    @Test
    void aRealFileIsReadAsUtf8AndLocatedByLine(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("run.properties");
        Files.writeString(file, "# будь\n\ncf.execution.timeout.millis=5000\ncf.execution.workers=oops\n",
                StandardCharsets.UTF_8);
        FrameworkException e = assertThrows(FrameworkException.class, () -> ConfigResolver.resolve(file, Map.of(), List.of()));
        assertEquals(ErrorCode.CONFIG_INVALID_VALUE, e.code());
        assertEquals(file.toString(), e.primary().location().origin());
        assertEquals(4, e.primary().location().line());

        Files.writeString(file, "cf.execution.timeout.millis=5000\n", StandardCharsets.UTF_8);
        assertEquals(5000L, ConfigResolver.resolve(file, Map.of(), List.of()).config().timeoutMillis());
    }

    @Test
    void effectiveXmlIsWellFormedAndReportsOriginsAndLocations() throws Exception {
        EffectiveConfig effective = resolve("cf.random.seed=42\n", Map.of(), List.of());
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(effective.toXml().getBytes(StandardCharsets.UTF_8)));
        NodeList settings = doc.getElementsByTagName("setting");
        assertEquals(103, settings.getLength());
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < settings.getLength(); i++) {
            Element s = (Element) settings.item(i);
            keys.add(s.getAttribute("key"));
            if (s.getAttribute("key").equals("cf.random.seed")) {
                assertEquals("42", s.getAttribute("value"));
                assertEquals("FILE", s.getAttribute("origin"));
                assertEquals("test.properties:1", s.getAttribute("location"));
            }
        }
        assertEquals(ConfigKeys.byName().keySet(), keys);
    }

    @Test
    void secretEntriesAreNeverRendered() {
        ConfigEntry secret = new ConfigEntry("cf.example.token", "hunter2", ConfigOrigin.FILE,
                org.ghyura.cf.model.SourceLocation.of("x", 1), true);
        assertEquals("***", secret.displayValue());
        assertEquals("hunter2", new ConfigEntry("k", "hunter2", ConfigOrigin.FILE,
                org.ghyura.cf.model.SourceLocation.NONE, false).displayValue());
    }

    @Test
    void textReportListsEverySettingWithItsOrigin() {
        String text = resolve("cf.execution.workers=8\n", Map.of(), List.of()).toText();
        assertTrue(text.contains("cf.execution.workers"));
        assertTrue(text.contains("= 8  [FILE test.properties:1]"), text);
        assertTrue(text.contains("[DEFAULT]"));
    }

    @Test
    void keyNamesAreUniqueAndAliasesDoNotCollide() {
        Set<String> names = new HashSet<>();
        Set<String> aliases = new HashSet<>();
        for (ConfigKey<?> key : ConfigKeys.all()) {
            assertTrue(names.add(key.name()), key.name());
            assertTrue(aliases.add(key.environmentAlias()), key.environmentAlias());
            assertTrue(key.name().startsWith("cf."));
            assertFalse(key.description().isBlank(), key.name());
        }
    }
}
