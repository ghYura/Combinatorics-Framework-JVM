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
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings of work package M03 (labels, replacement policies, empty stages, assembly, optional sheets, rendering,
 * constraints, covering selection) and the import of the legacy settings that M03 owns, each read the way the legacy
 * code read it.
 */
class AssemblySettingsTest {

    private static RunConfig resolve(String... overrides) {
        List<ConfigResolver.Override> list = new java.util.ArrayList<>();
        for (String o : overrides) {
            int eq = o.indexOf('=');
            list.add(new ConfigResolver.Override(o.substring(0, eq), o.substring(eq + 1), "--set"));
        }
        return ConfigResolver.resolve(null, Map.of(), list).config();
    }

    private static List<ErrorCode> refusal(String override) {
        FrameworkException e = assertThrows(FrameworkException.class, () -> resolve(override));
        return e.diagnostics().stream().map(Diagnostic::code).toList();
    }

    private static LegacyImport of(String text) {
        return LegacyImporter.importSources(List.of(new LegacyImporter.Source("fw.properties", text)), Map.of());
    }

    private static Map<String, String> overrides(LegacyImport imported) {
        Map<String, String> map = new LinkedHashMap<>();
        for (LegacyImport.TargetValue v : imported.overrides()) {
            if (v.where().isKnown()) {
                map.put(v.key(), v.value());
            }
        }
        return map;
    }

    private static List<ErrorCode> codes(LegacyImport imported) {
        return imported.diagnostics().stream().map(Diagnostic::code).toList();
    }

    @Test
    void theDefaultsKeepTheNativeBehaviour() {
        RunConfig c = RunConfig.defaults();
        assertEquals(RunConfig.LogicalLabels.PRESERVE, c.logicalLabels());
        assertEquals(RunConfig.WindowScope.ALL, c.windowScope());
        assertEquals(RunConfig.EmptyStagePolicy.PRESERVE_EMPTY, c.emptyStagePolicy());
        assertEquals(RunConfig.ReplacePattern.PERMISSIVE, c.replacePattern());
        assertEquals(RunConfig.ReplaceUnmatched.IGNORE, c.replaceUnmatched());
        assertEquals(RunConfig.ReplaceUnparseable.WARN, c.replaceUnparseable());
        assertEquals(RunConfig.ReplaceIdentity.ALLOW, c.replaceIdentity());
        assertEquals(RunConfig.ReplaceDiagnostics.OFF, c.replaceDiagnostics());
        assertFalse(c.assemblyDistinct());
        assertEquals(Long.MAX_VALUE, c.assemblyMandatoryLimit());
        assertEquals(RunConfig.OptionalMode.AUTO, c.optionalMode());
        assertTrue(c.optionalIncludeAbsent());
        assertEquals(RunConfig.SizeSet.all(), c.optionalSizes());
        assertEquals(RunConfig.SizeSet.all(), c.optionalProducedSizes());
        assertEquals(Long.MAX_VALUE, c.optionalSubsetRowLimit());
        assertEquals("", c.renderConcatenator());
        assertEquals(RunConfig.SheetConcatenator.APPLY, c.renderSheetConcatenator());
        assertFalse(c.renderPreserveWhitespace());
        assertEquals(List.of(), c.renderSpaceBefore());
        assertEquals(List.of(), c.renderNewlineAfter());
        assertTrue(c.renderMarkerReplacement());
        assertEquals("", c.constraintsFile());
        assertEquals("", c.constraintsBondsFile());
        assertEquals(0, c.coverageStrength());
        assertEquals(RunConfig.CoverageMethod.GREEDY, c.coverageMethod());
        assertEquals(0L, c.coverageBudget());
        assertEquals(2_000_000L, c.coverageHardCap());
    }

    @Test
    void sizeSetsAreAllNoneOrDistinctSizesFromOneTo64() {
        assertEquals(RunConfig.SizeSet.of(List.of(1, 2, 3)), resolve("cf.optional.sizes= 3, 1 ,2 ").optionalSizes());
        assertEquals("1,2,3", resolve("cf.optional.sizes=3,1,2").optionalSizes().token());
        assertEquals("none", resolve("cf.optional.producedSizes=none").optionalProducedSizes().token());
        for (String bad : List.of("", "0", "65", "1,1", "1,,2", "-1", "one", "all,1", " , ")) {
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), refusal("cf.optional.sizes=" + bad), "'" + bad + "'");
        }
        RunConfig.SizeSet some = RunConfig.SizeSet.of(List.of(1, 3));
        assertTrue(some.within(RunConfig.SizeSet.all(), 3));
        assertTrue(some.within(RunConfig.SizeSet.of(List.of(1, 2, 3)), 3));
        assertFalse(some.within(RunConfig.SizeSet.of(List.of(1, 2)), 3));
        assertTrue(some.within(RunConfig.SizeSet.of(List.of(1)), 2), "size 3 forms no combination of two optional sheets");
        assertFalse(RunConfig.SizeSet.all().within(RunConfig.SizeSet.of(List.of(1)), 2));
        assertTrue(RunConfig.SizeSet.all().contains(2, 2));
        assertFalse(RunConfig.SizeSet.all().contains(3, 2));
    }

    @Test
    void textSettingsKeepWhiteSpaceAndRegularExpressionListsSplitAsTheReaderDid() {
        assertEquals(", ", resolve("cf.render.concatenator=, ").renderConcatenator(), "a separator keeps its white space");
        assertEquals(List.of("\\{", ";"), resolve("cf.render.spaceAfter= \\{ ; ").renderSpaceAfter());
        assertEquals(List.of("else", "\\}"), resolve("cf.render.newlineBefore=else\n\\}\n").renderNewlineBefore());
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), refusal("cf.render.spaceBefore=( {"),
                "an expression that does not compile is refused before any candidate is rendered");
        assertEquals("cons.json", resolve("cf.constraints.file= cons.json ").constraintsFile());
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), refusal("cf.constraints.file=a\u0007b"));
    }

    @Test
    void choicesAcceptTheirTokensAndAMinimumCoverIsNotOffered() {
        RunConfig c = resolve("cf.keys.logical.labels=legacy-window", "cf.keys.window.scope=cells",
                "cf.generation.emptyStagePolicy=LEGACY_DROP_WITH_DIAGNOSTIC", "cf.generation.replace.unparseable=fail",
                "cf.optional.mode=off", "cf.render.sheetConcatenator=legacy-ignore", "cf.coverage.method=set-cover");
        assertEquals(RunConfig.LogicalLabels.LEGACY_WINDOW, c.logicalLabels());
        assertEquals(RunConfig.WindowScope.CELLS, c.windowScope());
        assertEquals(RunConfig.EmptyStagePolicy.LEGACY_DROP_WITH_DIAGNOSTIC, c.emptyStagePolicy());
        assertEquals(RunConfig.ReplaceUnparseable.FAIL, c.replaceUnparseable());
        assertEquals(RunConfig.OptionalMode.OFF, c.optionalMode());
        assertEquals(RunConfig.SheetConcatenator.LEGACY_IGNORE, c.renderSheetConcatenator());
        assertEquals(RunConfig.CoverageMethod.SET_COVER, c.coverageMethod());
        assertEquals(List.of(ErrorCode.UNSUPPORTED_CAPABILITY), refusal("cf.coverage.method=optimal"),
                "the legacy nwise_optimal was a greedy set cover; no setting claims a minimum suite");
        assertEquals(List.of(ErrorCode.UNSUPPORTED_CAPABILITY), refusal("cf.keys.logical.labels=optimal"));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), refusal("cf.generation.emptyStagePolicy=drop"));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), refusal("cf.coverage.strength=65"));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), refusal("cf.assembly.mandatoryLimit=0"));
        assertEquals(0L, resolve("cf.optional.subsetRowLimit=0").optionalSubsetRowLimit(), "0 leaves out every subset, as legacy");
    }

    // ---- the legacy settings M03 owns ----------------------------------------------------------------------------

    @Test
    void theIdentityStrategyIsTheLegacyWindowAndAnImportWithoutItReproducesIt() {
        assertEquals(Map.of("cf.keys.logical.labels", "legacy-window"), overrides(of("core.keys.strategy=IDENTITY\n")));
        LegacyImport absent = of("db.host=x\n");
        assertEquals("legacy-window", absent.overrides().stream().filter(v -> v.key().equals("cf.keys.logical.labels"))
                .findFirst().orElseThrow().value(), "the legacy default identity, reproduced");
        assertEquals(RunConfig.LogicalLabels.LEGACY_WINDOW,
                ConfigResolver.resolve(null, Map.of(), List.of(), absent).config().logicalLabels());
        assertEquals(RunConfig.LogicalLabels.PRESERVE, RunConfig.defaults().logicalLabels(), "the native default stays preserve");
        assertEquals(Map.of("cf.keys.window.scope", "cells"), overrides(of("core.keys.scope= CELLS \n")));
    }

    @Test
    void replacementPoliciesAreReadLikeParseEnum() {
        assertEquals(Map.of("cf.generation.replace.pattern", "strict", "cf.generation.replace.unmatched", "warn",
                        "cf.generation.replace.unparseable", "drop", "cf.generation.replace.identity", "warn",
                        "cf.generation.replace.diagnostics", "summary"),
                overrides(of("core.replace.patternPolicy=STRICT\ncore.replace.unmatchedPolicy=Warn\ncore.replace.unparseablePolicy=drop\n"
                        + "core.replace.identityPolicy=warn\ncore.replace.diagnostics=Summary\n")));
        LegacyImport blank = of("core.replace.unmatchedPolicy=\n");
        assertEquals(Map.of(), overrides(blank), "blank keeps the legacy default, which is this build's default");
        assertEquals(List.of(), codes(blank));
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("core.replace.unparseablePolicy=ignore\n")),
                "parseEnum threw on an unknown value");
    }

    @Test
    void limitsAreReadWithTheLegacyRanges() {
        assertEquals(Map.of("cf.assembly.mandatoryLimit", "9223372036854775799"),
                overrides(of("core.limitVarGivenLessThan=9223372036854775799\n")));
        for (String bad : List.of("0", "-5", "x", "9223372036854775808")) {
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("core.limitVarGivenLessThan=" + bad + "\n")), bad);
        }
        assertEquals(Map.of("cf.optional.subsetRowLimit", "2147483639"),
                overrides(of("core.optional.limitOptionalSheetsCombosMax=2147483639\n")));
        assertEquals(Map.of("cf.optional.subsetRowLimit", "0"), overrides(of("core.optional.limitOptionalSheetsCombosMax=0\n")));
        for (String bad : List.of("-1", "2147483648", "1.5")) {
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE),
                    codes(of("core.optional.limitOptionalSheetsCombosMax=" + bad + "\n")), bad);
        }
    }

    @Test
    void optionalSizeListsAreTheTwoSidesOfTheOptionalContract() {
        assertEquals(Map.of("cf.optional.producedSizes", "1,2,3", "cf.optional.sizes", "1,2"),
                overrides(of("core.optional.includeOptionalCombiPairsToDBCSVList=3, 1 ,2\nreader.core.isOptCSVList=2,1,1\n")));
        assertEquals(Map.of("cf.optional.producedSizes", "none"),
                overrides(of("core.optional.includeOptionalCombiPairsToDBCSVList=\n")), "the Core wrote no optional table");
        assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("reader.core.isOptCSVList=\n")),
                "the Reader failed on a blank list");
        for (String bad : List.of("0", "1,x", "-2", "65")) {
            assertEquals(List.of(ErrorCode.CONFIG_INVALID_VALUE), codes(of("reader.core.isOptCSVList=" + bad + "\n")), bad);
        }
        EffectiveConfig effective = ConfigResolver.resolve(null, Map.of(), List.of(),
                of("core.optional.includeOptionalCombiPairsToDBCSVList=1,2,3,4\nreader.core.isOptCSVList=1,2,3\n"));
        assertEquals(RunConfig.SizeSet.of(List.of(1, 2, 3)), effective.config().optionalSizes());
        assertEquals(RunConfig.SizeSet.of(List.of(1, 2, 3, 4)), effective.config().optionalProducedSizes());
    }

    @Test
    void readerSwitchesKeepTheirLegacyReading() {
        assertEquals(Map.of("cf.optional.mode", "on", "cf.optional.includeAbsent", "true"),
                overrides(of("reader.core.processIsOpt=TRUE\nreader.core.processBothFinalAndOpt=true\n")));
        assertEquals(Map.of("cf.optional.mode", "off"), overrides(of("reader.core.processIsOpt=false\n")));
        LegacyImport yes = of("reader.core.processIsOpt=yes\n");
        assertEquals(Map.of("cf.optional.mode", "off"), overrides(yes));
        assertEquals(List.of(ErrorCode.CONFIG_LEGACY_COERCED), codes(yes));
        assertEquals(Map.of("cf.render.markerReplacement", "true"),
                overrides(of("reader.out.replaceWithCurrentComboSequence=fw_replace_me_with_current_combo_sequence\n")));
        assertEquals(Map.of("cf.render.markerReplacement", "false"), overrides(of("reader.out.replaceWithCurrentComboSequence=none\n")));
        assertEquals(Map.of("cf.render.concatenator", ", "), overrides(of("reader.core.concatenator=  , \n")),
                "java.util.Properties drops leading white space and keeps the rest");
        assertEquals(Map.of("cf.render.concatenator", ""), overrides(of("reader.core.concatenator=\n")));
        assertEquals(Map.of("cf.render.preserveWhitespace", "true", "cf.assembly.distinct", "true"),
                overrides(of("reader.cells.preserveWhitespace=true\ndistinctify__fw_final_opt__Tables=true\n")));
        assertEquals(Map.of("cf.render.spaceBefore", "== !="),
                overrides(of("reader.javacode.oneSpace2AddBeforeCharsButNotInQuotesList=== !=\n")));
        assertEquals(Map.of("cf.constraints.bondsFile", "bonds.txt"), overrides(of("reader.constraints.bondsFile= bonds.txt \n")));
        assertEquals(Map.of(), overrides(of("reader.constraints.bondsFile=\n")), "blank is no file, as loadOrNull read it");
        assertEquals(List.of(), of("reader.constraints.bondsFile=x\nreader.core.isOptCSVList=1\n").pending(), "no longer deferred");
    }
}
