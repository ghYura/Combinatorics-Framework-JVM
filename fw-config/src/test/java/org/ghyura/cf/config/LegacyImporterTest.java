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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The legacy configuration importer, key by key, against the rules of the legacy parsing code. */
class LegacyImporterTest {

    private static LegacyImport of(String text) {
        return LegacyImporter.importSources(List.of(new LegacyImporter.Source("fw.properties", text)), Map.of());
    }

    private static LegacyImport.Entry entry(LegacyImport imported, String key) {
        return imported.entries().stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow(() -> new AssertionError(key));
    }

    private static List<ErrorCode> codes(LegacyImport imported) {
        return imported.diagnostics().stream().map(Diagnostic::code).toList();
    }

    /** The cf.* values the imported keys produced; a legacy default reproduced for an absent key is left out. */
    private static Map<String, String> overrides(LegacyImport imported) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (LegacyImport.TargetValue v : imported.overrides()) {
            if (v.where().isKnown()) {
                map.put(v.key(), v.value());
            }
        }
        return map;
    }

    /** Every cf.* value of the import, including legacy defaults reproduced for absent keys. */
    private static Map<String, String> allOverrides(LegacyImport imported) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (LegacyImport.TargetValue v : imported.overrides()) {
            map.put(v.key(), v.value());
        }
        return map;
    }

    // ---- mapped keys follow the legacy parsing code ---------------------------------------------------------

    @Test
    void legacyBooleansAreTrueOnlyForTrueInAnyCaseAndAnythingElseIsFalseWithAWarning() {
        LegacyImport imported = of("seq.autoPromoteSingleVerb = TRUE\nseq.nestedFwBraceEnabled=false\n"
                + "seq.headlessRowsAutoSynthesizeTarget= yes\nworkbook.autoGenerateMissingFwSheetNames=\n");
        assertEquals(Map.of("cf.plan.autoPromoteSingleVerb", "true", "cf.plan.nestedJoinersEnabled", "false",
                "cf.plan.headlessRowsGetTarget", "false"), overrides(imported));
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_COERCED), codes(imported));
        assertEquals(SourceLocation.of("fw.properties", 3), imported.diagnostics().getFirst().location());
        LegacyImport.Entry blank = entry(imported, "workbook.autoGenerateMissingFwSheetNames");
        assertEquals(LegacySetting.Disposition.MAPPED, blank.disposition());
        assertNull(blank.targetValue(), "a blank value keeps the legacy default, which is this build's default");
        assertTrue(blank.note().contains("legacy default true"), blank.note());
    }

    /**
     * Decision G6: AppConfig read the switch with Boolean.parseBoolean(getProperty(key, "false")), so only "true" in
     * any case, with nothing around it, chose the parallel library; and a legacy configuration without the key ran
     * with combinatoricslib3, which an import reproduces.
     */
    @Test
    void theLibrarySwitchMapsLikeParseBooleanAndAnAbsentSwitchReproducesTheLegacyFalse() {
        String key = "core.threading.parallelizeSubCombosIfPossible";
        assertEquals(Map.of("cf.generation.library", "PARALLEL"), overrides(of(key + "=true\n")));
        assertEquals(Map.of("cf.generation.library", "PARALLEL"), overrides(of(key + "=TRUE\n")));
        assertEquals(Map.of("cf.generation.library", "COMBINATORICSLIB3"), overrides(of(key + "=false\n")));
        for (String coerced : List.of("true ", "yes", "1")) {
            LegacyImport imported = of(key + "=" + coerced + "\n");
            assertEquals(Map.of("cf.generation.library", "COMBINATORICSLIB3"), overrides(imported), "'" + coerced + "'");
            assertEquals(List.of(ErrorCode.CONFIG_LEGACY_COERCED), codes(imported), "'" + coerced + "'");
        }
        LegacyImport blank = of(key + "=\n");
        assertEquals(Map.of("cf.generation.library", "COMBINATORICSLIB3"), overrides(blank));
        assertEquals(List.of(), codes(blank), "parseBoolean(\"\") is the legacy default itself");

        LegacyImport absent = of("seq.autoPromoteSingleVerb=true\n");
        assertEquals(Map.of("cf.plan.autoPromoteSingleVerb", "true", "cf.generation.library", "COMBINATORICSLIB3",
                "cf.keys.logical.labels", "legacy-window"), allOverrides(absent));
        LegacyImport.Entry reproduced = entry(absent, key);
        assertEquals(LegacySetting.Disposition.MAPPED, reproduced.disposition());
        assertEquals(SourceLocation.NONE, reproduced.where());
        assertTrue(reproduced.note().startsWith("Absent from every imported file: the legacy default false applies"), reproduced.note());
        assertEquals(List.of(), codes(absent));

        LegacyImport nothing = LegacyImporter.importSources(List.of(), Map.of());
        assertEquals(List.of(), nothing.overrides(), "without an imported file there is no legacy run to reproduce");
        assertEquals(RunConfig.GenerationLibrary.PARALLEL,
                ConfigResolver.resolve(null, Map.of(), List.of(), nothing).config().generationLibrary());
    }

    @Test
    void aNativeSettingOverridesTheReproducedLegacyLibraryAndTheReportKeepsBothOrigins() {
        LegacyImport absent = of("seq.autoPromoteSingleVerb=true\n");
        EffectiveConfig legacyOnly = ConfigResolver.resolve(null, Map.of(), List.of(), absent);
        assertEquals(RunConfig.GenerationLibrary.COMBINATORICSLIB3, legacyOnly.config().generationLibrary());
        assertEquals(ConfigOrigin.LEGACY, legacyOnly.entry(ConfigKeys.GENERATION_LIBRARY).origin());

        EffectiveConfig native_ = ConfigResolver.resolveLayers("cf.properties", "cf.generation.library=PARALLEL\n", Map.of(), List.of(),
                Runtime.getRuntime().maxMemory(), absent);
        assertEquals(RunConfig.GenerationLibrary.PARALLEL, native_.config().generationLibrary());
        assertEquals(ConfigOrigin.FILE, native_.entry(ConfigKeys.GENERATION_LIBRARY).origin());
        ConfigConflict conflict = native_.conflicts().getFirst();
        assertEquals("cf.generation.library", conflict.key());
        assertEquals(ConfigOrigin.LEGACY, conflict.overridden().origin());
        assertEquals("COMBINATORICSLIB3", conflict.overridden().rawValue());
        assertTrue(native_.toXml().contains("legacy default false applies"), "the import report says why the legacy layer said so");
    }

    /** Decision G7: distinctify__fwX_Y__Tables was Boolean.parseBoolean(getProperty(key)); absent was false, as here. */
    @Test
    void theDistinctTablesSwitchMapsLikeParseBoolean() {
        assertEquals(Map.of("cf.generation.distinctTables", "true", "cf.generation.library", "COMBINATORICSLIB3",
                "cf.keys.logical.labels", "legacy-window"), allOverrides(of("distinctify__fwX_Y__Tables=True\n")));
        LegacyImport coerced = of("distinctify__fwX_Y__Tables=on\n");
        assertEquals("false", overrides(coerced).get("cf.generation.distinctTables"));
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_COERCED), codes(coerced));
        assertNull(overrides(of("db.host=x\n")).get("cf.generation.distinctTables"), "absent keeps this build's own false");
        assertEquals(List.of(), of("distinctify__fwX_Y__Tables=true\n").pending(), "no longer deferred");
    }

    @Test
    void stringsAreTrimmedAndABlankKeepsTheDefault() {
        assertEquals(Map.of("cf.plan.virtualSheetPrefix", "V_"), overrides(of("workbook.virtualSheetNamePrefix =   V_  \n")));
        assertEquals(Map.of(), overrides(of("workbook.virtualSheetNamePrefix=\n")));
    }

    @Test
    void keyDispatchAndStrategyMapToTheEncodingAndLabelSettings() {
        assertEquals(Map.of("cf.keys.encoding", "PACKED_LEGACY16", "cf.keys.logical.labels", "legacy-window"),
                overrides(of("core.keys.dispatch=SHORT\ncore.keys.strategy=Identity\n")),
                "the legacy identity strategy is the legacy-window mode (decision F2)");
        assertEquals(Map.of("cf.keys.encoding", "AUTO_PRESERVE"), overrides(of("core.keys.dispatch=auto\n")));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("core.keys.dispatch=byte\n")));
        LegacyImport anchor = of("core.keys.strategy=anchor\ncore.keys.anchorSheet=A\n");
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNSUPPORTED), codes(anchor));
        assertEquals(LegacySetting.Disposition.RETIRED, entry(anchor, "core.keys.anchorSheet").disposition());
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("core.keys.strategy=window\n")));
    }

    @Test
    void workerCountsFollowTheLegacyRulesOfEachPool() {
        assertEquals(Map.of("cf.execution.workers", "6"), overrides(of("core.providedNofWorkerThreadsIntoFixedThreadPool= 006 \n")));
        for (String everyProcessor : List.of("", "0", "00")) {
            LegacyImport imported = of("core.providedNofWorkerThreadsIntoFixedThreadPool=" + everyProcessor + "\n");
            assertEquals(Map.of(), overrides(imported), everyProcessor);
            assertTrue(entry(imported, "core.providedNofWorkerThreadsIntoFixedThreadPool").note().contains("every processor"));
        }
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("core.providedNofWorkerThreadsIntoFixedThreadPool=6x\n")));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("core.providedNofWorkerThreadsIntoFixedThreadPool=-2\n")));
        assertEquals(Map.of("cf.execution.workers", "4"), overrides(of("fw.exec.threads=4\n")));
        for (String bad : List.of("0", "-1", "x", "")) {
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("fw.exec.threads=" + bad + "\n")), bad);
        }
        // the two legacy pools were separate; one value for both is fine, two different values are refused
        assertEquals(List.of(), codes(of("core.providedNofWorkerThreadsIntoFixedThreadPool=4\nfw.exec.threads=4\n")));
        LegacyImport conflict = of("core.providedNofWorkerThreadsIntoFixedThreadPool=6\nfw.exec.threads=4\n");
        assertEquals(List.of(ErrorCode.CONFIG_INCONSISTENT), codes(conflict));
        assertTrue(conflict.diagnostics().getFirst().message().contains("fw.properties:1"), conflict.diagnostics().toString());
    }

    @Test
    void theRepeatCountReadsLikeParseIntWithDefaultOneAndTheOlderNameIsShadowed() {
        assertEquals(Map.of("cf.execution.repeat.count", "3"), overrides(of("fw.controlplane.repeatK=3\n")));
        LegacyImport word = of("fw.controlplane.repeatK=three\n");
        assertEquals(Map.of("cf.execution.repeat.count", "1"), overrides(word));
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_COERCED), codes(word));
        assertEquals(Map.of("cf.execution.repeat.count", "2"), overrides(of("fw.executor.repeatEachCandidate=2\n")),
                "the older name alone counts");
        LegacyImport both = of("fw.executor.repeatEachCandidate=2\nfw.controlplane.repeatK=5\n");
        assertEquals(Map.of("cf.execution.repeat.count", "5"), overrides(both), "first() read the newer name first");
        assertTrue(entry(both, "fw.executor.repeatEachCandidate").note().startsWith("Shadowed"));
        assertEquals(List.of(), codes(both));
    }

    // ---- every other disposition ---------------------------------------------------------------------------

    @Test
    void retiredInertAndSupersededKeysAreReportedAndChangeNothing() {
        LegacyImport imported = of("db.host=localhost\ncore.optimizer.memory.pojo.enabled=true\nexcel.file=./in.xlsx\n"
                + "core.intermediate.storage=postgres\n");
        assertEquals(List.of(), imported.diagnostics());
        assertEquals(Map.of(), overrides(imported));
        assertEquals(LegacySetting.Disposition.RETIRED, entry(imported, "db.host").disposition());
        assertEquals(LegacySetting.Disposition.INERT, entry(imported, "core.optimizer.memory.pojo.enabled").disposition());
        LegacyImport.Entry excel = entry(imported, "excel.file");
        assertEquals(LegacySetting.Disposition.SUPERSEDED, excel.disposition());
        assertEquals("--scenario", excel.target());
        assertEquals("./in.xlsx", excel.shownValue());
        assertEquals(SourceLocation.of("fw.properties", 3), excel.where());
    }

    /**
     * The embedded catalog with two deferred settings of a later package added: since M07 no setting of the catalog is
     * deferred, and decision R-RUN-GATE still has to hold for the next one.
     */
    private static LegacyImport withDeferred(String text) {
        List<LegacySetting> settings = new java.util.ArrayList<>(LegacySettings.catalog().all());
        for (String name : List.of("x.later.one", "x.later.two")) {
            settings.add(new LegacySetting(name, false, LegacySetting.Kind.PROPERTY, LegacySetting.Disposition.DEFERRED, null, null, null,
                    false, null, "M99", "test", false, "retained behaviour of a later package", List.of(), List.of(), List.of()));
        }
        return LegacyImporter.importSources(List.of(new LegacyImporter.Source("fw.properties", text)), Map.of(),
                LegacySettings.of("test", settings));
    }

    @Test
    void theSeedSettingsMapToTheCampaignKeysOrAreSuperseded() {
        LegacyImport imported = of("core.seed.inputPath=seed.json\nfw.analyzer.seedOutputPath= seeds.json \nfw.analyzer.seedSourceRunId=r1\n");
        assertEquals(List.of(), imported.pending());
        assertEquals(Map.of("cf.campaign.seed.output", "seeds.json"), overrides(imported));
        assertEquals(LegacySetting.Disposition.SUPERSEDED, entry(imported, "core.seed.inputPath").disposition(),
                "the legacy Core only logged the seed it read");
        assertEquals("cf.campaign.seed.input", entry(imported, "core.seed.inputPath").target());
        assertEquals(LegacySetting.Disposition.SUPERSEDED, entry(imported, "fw.analyzer.seedSourceRunId").disposition());
    }

    @Test
    void deferredKeysArePendingWithTheirOwnerAndAWarning() {
        LegacyImport imported = withDeferred("x.later.one=1\nx.later.two=2\n");
        assertEquals(List.of("M99", "M99"), imported.pending().stream().map(LegacyImport.Pending::owner).toList());
        assertTrue(imported.diagnostics().stream().allMatch(d -> d.code() == ErrorCode.CONFIG_LEGACY_PENDING
                && d.severity() == Severity.WARNING));
        assertFalse(imported.hasErrors());
        assertEquals(SourceLocation.of("fw.properties", 2), imported.pending().get(1).where());
    }

    @Test
    void theControlPlaneAndExecutorModeSettingsMapAsTheLegacyCodeReadThem() {
        Map<String, String> first = overrides(of("fw.executor.repeatPolicy=Nested\nfw.controlplane.maxInFlight=0\n"
                + "fw.controlplane.metricSensitivity=Latency\nfw.controlplane.budgetMillis=-5\n"));
        assertEquals("nested", first.get("cf.execution.repeat.policy"));
        assertEquals("1", first.get("cf.execution.workers"));
        assertEquals("true", first.get("cf.execution.serialize"));
        assertEquals("0", first.get("cf.run.budget.millis"));
        // an explicit serializePerHost wins over the metric type; passonly (any case) attaches the winners' sources
        Map<String, String> second = overrides(of("fw.controlplane.serializePerHost=false\nfw.controlplane.metricSensitivity=noisy\n"
                + "fw.exec.mode=PassOnly\nfw.controlplane.maxInFlight=many\n"));
        assertEquals("false", second.get("cf.execution.serialize"));
        assertEquals("passed", second.get("cf.results.attach"));
        assertEquals("64", second.get("cf.execution.workers"));
        // an unknown policy or metric type was refused by the legacy control plane as well
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("fw.controlplane.policy=adaptive\n")));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("fw.controlplane.metricSensitivity=jittery\n")));
    }

    @Test
    void theAnalyzerSettingsMapAsTheLegacyReaderWiringReadThem() {
        Map<String, String> goals = overrides(of("fw.analyzer.enabled=true\nfw.analyzer.goals=latency:min:0:2, tput:Maximize, size:target:64,"
                + " bad, odd:fastest, tput:min\nfw.analyzer.topK=0\nfw.analyzer.autoDiscover=0\nfw.analyzer.autoDiscoverMode=Max\n"
                + "fw.analyzer.autoDiscoverWeight=heavy\n"));
        assertEquals("true", goals.get("cf.analysis.enabled"));
        assertEquals("latency:min:2.0,size:target:64.0,odd:min,tput:min", goals.get("cf.analysis.goals"),
                "the third part of min and max is an ignored target; unknown modes minimise; the last of a repeated key wins");
        assertEquals("1", goals.get("cf.analysis.topK"));
        assertEquals("false", goals.get("cf.analysis.discover"));
        assertEquals("max", goals.get("cf.analysis.discover.mode"));
        assertEquals("1.0", goals.get("cf.analysis.discover.weight"));
        Map<String, String> keys = overrides(of("fw.analyzer.minimizeKeys= cost, ,latency\nfw.analyzer.maximizeKeys=tput\n"));
        assertEquals("cost,latency", keys.get("cf.analysis.minimize"));
        assertEquals("tput", keys.get("cf.analysis.maximize"));
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNSUPPORTED), codes(of("fw.analyzer.executorType=shell\n")));
        assertEquals(List.of(), codes(of("fw.analyzer.executorType=inline\nfw.analyzer.coverage=none\nfw.analyzer.cache=true\n")));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("fw.analyzer.goals=a:min:x\n")));
    }

    @Test
    void unsupportedValuesAreErrorsAndValueRulesDecidePerValue() {
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNSUPPORTED), codes(of("fw.exec.dispatch=policy\n")));
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNSUPPORTED), codes(of("fw.exec.trusted=false\n")));
        LegacyImport trusted = of("fw.exec.trusted=true\n");
        assertEquals(List.of(), codes(trusted));
        assertEquals(LegacySetting.Disposition.HONORED, entry(trusted, "fw.exec.trusted").disposition());
        assertEquals(LegacySetting.Disposition.HONORED, entry(of("reader.out.fileExtension=.JAVA\n"), "reader.out.fileExtension").disposition());
        assertEquals(LegacySetting.Disposition.RETIRED, entry(of("reader.out.fileExtension=.csv\n"), "reader.out.fileExtension").disposition());
        LegacyImport python = of("reader.out.fileExtension=.py\n");
        assertEquals(List.of(), codes(python), "Python candidates import since the Python add-on (author decision 2026-10-12)");
        assertEquals(LegacySetting.Disposition.HONORED, entry(python, "reader.out.fileExtension").disposition());
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNSUPPORTED), codes(of("reader.out.candidateSink=grpc\n")));
        assertEquals(List.of(), codes(of("reader.out.candidateSink=sharded\n")));
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNSUPPORTED), codes(of("fw.exec.compiler=javac\n")));
        assertEquals(List.of(), codes(of("fw.exec.compiler=ecj\n")));
        assertEquals(Map.of("cf.keys.window.scope", "cells"), overrides(of("core.keys.scope=Cells\n")), "mapped since M03");
        assertEquals(List.of(), codes(of("core.keys.scope=ALL\n")));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("core.keys.scope=both\n")), "parseEnum refused it too");
    }

    @Test
    void aKeyTheCatalogDoesNotKnowOrDoesNotHoldAsAPropertiesKeyIsRefused() {
        LegacyImport typo = of("seq.autoPromoteSingelVerb=true\n");
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNCLASSIFIED), codes(typo));
        assertTrue(typo.diagnostics().getFirst().message().contains("did you mean 'seq.autoPromoteSingleVerb'"),
                typo.diagnostics().getFirst().message());
        assertTrue(typo.hasErrors());
        assertNull(entry(typo, "seq.autoPromoteSingelVerb").disposition());
        for (String key : List.of("repeat", "java.home", "CORE_DB_PASSWORD", "cf.execution.workers")) {
            LegacyImport refused = of(key + "=1\n");
            assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNCLASSIFIED), codes(refused), key);
        }
        assertTrue(of("repeat=1\n").diagnostics().getFirst().message().contains("executor command-line argument"));
        assertEquals(LegacySetting.Disposition.RETIRED, entry(of("jmol.logger.com.x=debug\n"), "jmol.logger.com.x").disposition(),
                "a family of names matches its prefix entry");
    }

    // ---- the file rules -----------------------------------------------------------------------------------------

    @Test
    void aKeyRepeatedInOneFileKeepsItsLastValueWithAWarningAndLaterFilesWinSilently() {
        LegacyImport repeated = of("fw.exec.threads=2\nfw.exec.threads=3\n");
        assertEquals(Map.of("cf.execution.workers", "3"), overrides(repeated));
        assertEquals(List.of(ErrorCode.CONFIG_DUPLICATE_KEY), codes(repeated));
        assertEquals(Severity.WARNING, repeated.diagnostics().getFirst().severity());
        assertEquals(SourceLocation.of("fw.properties", 2), entry(repeated, "fw.exec.threads").where());
        LegacyImport layered = LegacyImporter.importSources(List.of(new LegacyImporter.Source("a.properties", "fw.exec.threads=2\n"),
                new LegacyImporter.Source("b.properties", "fw.exec.threads=5\n")), Map.of());
        assertEquals(Map.of("cf.execution.workers", "5"), overrides(layered));
        assertEquals(List.of(), codes(layered));
        assertEquals(List.of("a.properties", "b.properties"), layered.files());
    }

    @Test
    void theJavaPropertiesSyntaxIsReadExactlyIncludingContinuationsEscapesAndSeparators(@TempDir Path dir) throws Exception {
        String text = "# comment\n! also a comment\n"
                + "workbook.virtualSheetNamePrefix : V\\u0041_\n"
                + "fw.exec.threads 7\n"
                + "core.optional.includeOptionalCombiPairsToDBCSVList = 1,\\\n   2,\\\n   3\n"
                + "db.name\\=x = y\n";
        Path file = dir.resolve("fw.properties");
        Files.writeString(file, text);
        LegacyImport imported = LegacyImporter.importFiles(List.of(file), Map.of());
        java.util.Properties jdk = new java.util.Properties();
        try (var in = Files.newInputStream(file)) {
            jdk.load(in);
        }
        assertEquals(Map.of("cf.plan.virtualSheetPrefix", jdk.getProperty("workbook.virtualSheetNamePrefix"),
                "cf.execution.workers", jdk.getProperty("fw.exec.threads"),
                "cf.optional.producedSizes", jdk.getProperty("core.optional.includeOptionalCombiPairsToDBCSVList")),
                overrides(imported));
        assertEquals("VA_", jdk.getProperty("workbook.virtualSheetNamePrefix"));
        assertEquals(jdk.getProperty("core.optional.includeOptionalCombiPairsToDBCSVList"),
                entry(imported, "core.optional.includeOptionalCombiPairsToDBCSVList").shownValue());
        assertEquals(5, entry(imported, "core.optional.includeOptionalCombiPairsToDBCSVList").where().line());
        assertTrue(imported.entries().stream().anyMatch(e -> e.key().equals("db.name=x")), "an escaped separator is part of the key");
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNCLASSIFIED), codes(imported), "the size list is mapped since M03");
    }

    @Test
    void filesAreReadAsIso88591LikeTheLegacyPropertiesLoad(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("fw.properties");
        Files.write(file, new byte[]{'d', 'b', '.', 'h', 'o', 's', 't', '=', (byte) 0xE9, (byte) 0xC3, (byte) 0xA9});
        assertEquals("\u00e9\u00c3\u00a9", entry(LegacyImporter.importFiles(List.of(file), Map.of()), "db.host").shownValue());
        assertEquals(List.of(ErrorCode.CONFIG_UNREADABLE),
                codes(LegacyImporter.importFiles(List.of(dir.resolve("missing.properties")), Map.of())));
        assertEquals(List.of(ErrorCode.CONFIG_MALFORMED), codes(of("db.host=\\uZZZZ\n")));
    }

    // ---- secrets and the environment -------------------------------------------------------------------------

    @Test
    void noSecretValueAppearsAnywhereInTheImport() {
        String secret = "hunter2-" + System.nanoTime();
        LegacyImport imported = LegacyImporter.importSources(List.of(new LegacyImporter.Source("fw.properties",
                        "db.password=" + secret + "\nresults.db.password=" + secret + "\nmy.api.token=" + secret + "\n")),
                Map.of("CORE_DB_PASSWORD", secret, "BUNDLE_RESULTS_DB_HOST", "db.example", "PATH", "/usr/bin"));
        StringBuilder xml = new StringBuilder();
        imported.appendXml(xml, "");
        String everything = imported + "\n" + imported.toText() + "\n" + xml + "\n" + imported.diagnostics();
        assertFalse(everything.contains(secret), "a secret leaked");
        assertEquals(LegacyImport.REDACTED, entry(imported, "db.password").shownValue());
        assertEquals(LegacyImport.REDACTED, entry(imported, "my.api.token").shownValue(), "redacted by its name although unknown");
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNCLASSIFIED), codes(imported));
        assertEquals(List.of("BUNDLE_RESULTS_DB_HOST", "CORE_DB_PASSWORD"),
                imported.environment().stream().map(LegacyImport.EnvironmentEntry::name).sorted().toList(),
                "only legacy variables are reported; PATH is not a legacy setting");
        assertEquals(LegacyImport.REDACTED, imported.environment().stream()
                .filter(e -> e.name().equals("CORE_DB_PASSWORD")).findFirst().orElseThrow().shownValue());
    }

    // ---- the configuration layer -------------------------------------------------------------------------------

    @Test
    void theLegacyLayerSitsBelowTheFileTheEnvironmentAndTheCommandLine() {
        LegacyImport legacy = of("fw.exec.threads=6\nseq.autoPromoteSingleVerb=false\n");
        EffectiveConfig alone = ConfigResolver.resolve(null, Map.of(), List.of(), legacy);
        assertEquals(6, alone.config().workers());
        assertFalse(alone.config().planAutoPromoteSingleVerb());
        assertEquals(ConfigOrigin.LEGACY, alone.entry(ConfigKeys.EXECUTION_WORKERS).origin());
        assertEquals(SourceLocation.of("fw.properties", 1), alone.entry(ConfigKeys.EXECUTION_WORKERS).location());
        assertTrue(alone.legacyImport().isPresent());
        assertTrue(alone.toText().contains("legacy import") && alone.toXml().contains("<legacy-import "), alone.toText());

        EffectiveConfig file = ConfigResolver.resolveLayers("cf.properties", "cf.execution.workers=3\n", Map.of(), List.of(),
                Runtime.getRuntime().maxMemory(), legacy);
        assertEquals(3, file.config().workers());
        assertEquals(ConfigOrigin.FILE, file.entry(ConfigKeys.EXECUTION_WORKERS).origin());
        assertEquals(ConfigOrigin.LEGACY, file.entry(ConfigKeys.PLAN_AUTO_PROMOTE_SINGLE_VERB).origin());
        EffectiveConfig environment = ConfigResolver.resolve(null, Map.of("CF_EXECUTION_WORKERS", "2"), List.of(), legacy);
        assertEquals(ConfigOrigin.ENVIRONMENT, environment.entry(ConfigKeys.EXECUTION_WORKERS).origin());
        EffectiveConfig command = ConfigResolver.resolve(null, Map.of("CF_EXECUTION_WORKERS", "2"),
                List.of(new ConfigResolver.Override("cf.execution.workers", "1", "--set")), legacy);
        assertEquals(1, command.config().workers());
    }

    @Test
    void importErrorsFailTheResolutionAndATargetRangeErrorPointsAtTheLegacyLine() {
        FrameworkException unknown = assertThrows(FrameworkException.class,
                () -> ConfigResolver.resolve(null, Map.of(), List.of(), of("no.such.key=1\n")));
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_UNCLASSIFIED), unknown.diagnostics().stream().map(Diagnostic::code).toList());
        FrameworkException range = assertThrows(FrameworkException.class,
                () -> ConfigResolver.resolve(null, Map.of(), List.of(), of("\n\ncore.providedNofWorkerThreadsIntoFixedThreadPool=2000\n")));
        Diagnostic d = range.diagnostics().getFirst();
        assertEquals(ErrorCode.CONFIG_INVALID_VALUE, d.code());
        assertEquals(SourceLocation.of("fw.properties", 3), d.location());
        assertEquals("LEGACY", d.attributes().get("origin"));
        // pending legacy behaviour is a warning of the effective configuration, not a resolution failure
        EffectiveConfig pending = ConfigResolver.resolve(null, Map.of(), List.of(), withDeferred("x.later.one=1\n"));
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_PENDING), pending.diagnostics().stream().map(Diagnostic::code).toList());
        assertEquals(1, pending.legacyImport().orElseThrow().pending().size());
    }
}
