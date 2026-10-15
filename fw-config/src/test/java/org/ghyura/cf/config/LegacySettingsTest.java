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

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legacy settings catalog is internally consistent: every mapped key goes to a declared cf.* key with a
 * transform, blank legacy values fall back to the same default here as there, every retained semantics has an
 * owner package, secrets are flagged, and every location is a plain relative path. Completeness against the
 * legacy checkout itself is LegacyConfigCompletenessTest in fw-acceptance.
 */
class LegacySettingsTest {

    private static final LegacySettings CATALOG = LegacySettings.catalog();
    private static final Set<String> PACKAGES = Set.of("M00", "M01", "M01R", "M02", "M03", "M04", "M05", "M06", "M07", "M08",
            "M09", "M10");

    @Test
    void theCatalogHoldsEveryEntryItDeclaresOnceAndNamesItsSourceCommit() {
        assertEquals(297, CATALOG.all().size());
        assertEquals("0bc3e5acf6cf8f612a08ce640d236132c448b29b", CATALOG.sourceCommit());
        Set<String> seen = new HashSet<>();
        for (LegacySetting s : CATALOG.all()) {
            assertTrue(seen.add(s.kind() + " " + s.name()), "twice: " + s.name());
            assertFalse(s.reason().isBlank(), s.name());
        }
    }

    /**
     * Mapped keys whose absence had no defined legacy behaviour: reader.core.processBothFinalAndOpt was a nullable
     * Boolean that the Reader's pipeline unboxed, so a configuration without it failed as soon as optional tables existed.
     * This build's default is the value every shipped configuration and the optional contract set.
     */
    private static final java.util.Set<String> NO_USABLE_LEGACY_DEFAULT = java.util.Set.of("reader.core.processBothFinalAndOpt");

    @Test
    void everyMappedKeyGoesToADeclaredKeyAndABlankValueFallsBackToTheSameDefaultHereAsThere() {
        Map<String, ConfigKey<?>> declared = ConfigKeys.byName();
        EffectiveConfig defaults = ConfigResolver.resolve(null, Map.of(), List.of());
        int mapped = 0;
        for (LegacySetting s : CATALOG.all()) {
            if (s.disposition() != LegacySetting.Disposition.MAPPED || !s.kind().importable()) {
                continue;
            }
            mapped++;
            assertNotNull(declared.get(s.target()), s.name() + " maps to an undeclared key " + s.target());
            assertNotNull(s.transform(), s.name());
            if (NO_USABLE_LEGACY_DEFAULT.contains(s.name())) {
                assertNull(s.legacyDefault(), s.name());
                assertTrue(s.reason().contains("failed"), s.name() + ": the reason says what the legacy did without the key");
                continue;
            }
            if (s.transform() == LegacySetting.Transform.LEGACY_BOOLEAN || s.transform() == LegacySetting.Transform.LEGACY_STRING
                    || s.transform() == LegacySetting.Transform.PARSE_BOOLEAN) {
                assertEquals(s.legacyDefault(), defaults.entry(declared.get(s.target())).rawValue(),
                        s.name() + ": a blank legacy value keeps the legacy default, which must be this build's default too");
            }
        }
        assertEquals(54, mapped);
    }

    @Test
    void retainedSemanticsAndRefusalsNameTheirOwnerPackage() {
        for (LegacySetting s : CATALOG.all()) {
            boolean needsOwner = s.disposition() == LegacySetting.Disposition.DEFERRED
                    || s.disposition() == LegacySetting.Disposition.UNSUPPORTED
                    || s.values().stream().anyMatch(v -> v.disposition() == LegacySetting.Disposition.UNSUPPORTED);
            if (needsOwner) {
                assertNotNull(s.owner(), s.name());
            }
            if (s.owner() != null) {
                assertTrue(PACKAGES.contains(s.owner()), s.name() + ": unknown package " + s.owner());
            }
        }
    }

    @Test
    void valueRulesAndAliasesAreWellFormed() {
        for (LegacySetting s : CATALOG.all()) {
            for (LegacySetting.ValueRule rule : s.values()) {
                assertNotEquals(s.disposition(), rule.disposition(), s.name() + ": a value rule must change the disposition");
                assertFalse(rule.reason().isBlank(), s.name());
            }
            if (s.aliasOf() != null) {
                LegacySetting primary = CATALOG.find(s.aliasOf()).orElseThrow();
                assertEquals(primary.target(), s.target(), s.name());
                assertEquals(primary.disposition(), s.disposition(), s.name());
            }
        }
    }

    @Test
    void everyCredentialNameIsFlaggedSecretAndNothingElseIs() {
        Pattern credential = Pattern.compile("(?i)password|passwd|secret|token|credential");
        for (LegacySetting s : CATALOG.all()) {
            assertEquals(credential.matcher(s.name()).find(), s.secret(), s.name());
        }
    }

    @Test
    void everyLocationIsARelativePathInTheCheckoutWithAPositiveLine() {
        for (LegacySetting s : CATALOG.all()) {
            for (LegacySetting.Location l : s.foundIn()) {
                assertTrue(l.path().endsWith(".properties"), s.name() + " " + l);
            }
            for (List<LegacySetting.Location> list : List.of(s.foundIn(), s.readAt())) {
                for (LegacySetting.Location l : list) {
                    assertTrue(l.line() > 0 && !l.path().startsWith("/") && !l.path().contains(".."), s.name() + " " + l);
                }
            }
            if (s.kind() == LegacySetting.Kind.PROPERTY && s.disposition() != LegacySetting.Disposition.NOT_A_SETTING) {
                assertFalse(s.foundIn().isEmpty() && s.readAt().isEmpty(), s.name() + " has no evidence of where it came from");
            }
        }
    }

    @Test
    void lookupsPreferAnExactEntryAndOtherwiseTheLongestPrefix() {
        assertEquals("seq.autoPromoteSingleVerb", CATALOG.find("seq.autoPromoteSingleVerb").orElseThrow().name());
        LegacySetting logger = CATALOG.find("jmol.logger.com.company.Foo").orElseThrow();
        assertEquals("jmol.logger.", logger.name());
        assertTrue(logger.prefix());
        assertTrue(CATALOG.find("jmol.logger").isEmpty(), "a prefix covers only names that start with all of it");
        assertTrue(CATALOG.find("no.such.key").isEmpty());
        assertEquals(LegacySetting.Disposition.HONORED, CATALOG.find("fw.exec.trusted").orElseThrow().dispositionFor(" TRUE "));
        assertEquals(LegacySetting.Disposition.UNSUPPORTED, CATALOG.find("fw.exec.trusted").orElseThrow().dispositionFor("false"));
    }
}
