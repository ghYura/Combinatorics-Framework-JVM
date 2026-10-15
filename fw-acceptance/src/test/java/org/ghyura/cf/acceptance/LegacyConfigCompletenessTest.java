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

package org.ghyura.cf.acceptance;

import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.config.LegacySetting;
import org.ghyura.cf.config.LegacySettings;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architect decision F6-F31 and review HG05: the legacy settings catalog covers every setting name the pinned
 * legacy checkout reads, and its evidence still points at real lines. The checkout is scanned read-only, with
 * scanning rules written here independently of how the catalog was produced: every key of every properties file
 * (java.util.Properties); every dotted name, or name set in a properties file, passed to the legacy property
 * readers (getProperty and its typed helpers, require*, rawProperty, getOrDefault, map gets, first(a, b),
 * Boolean.getBoolean, Integer.getInteger, Long.getLong, set/clearProperty); every System.getenv name; every
 * Python os.environ name. Then the seven real legacy files are imported. Without the checkout the test is
 * skipped and shows as skipped.
 */
class LegacyConfigCompletenessTest {

    private static final Pattern CALL = Pattern.compile("(?:\\b(?:p|prop|props|properties)\\.get|getProperty|getBoolOrDefault"
            + "|getIntOrDefault|getLongOrDefault|getStringOrDefault|requireNonBlank|requireInt|requireLong|rawProperty"
            + "|getOrDefault|Boolean\\.getBoolean|Integer\\.getInteger|Long\\.getLong|setProperty|clearProperty)"
            + "\\(\\s*(?:[\\w.]+\\s*,\\s*)?\"([^\"]+)\"");
    private static final Pattern FIRST = Pattern.compile("\\bfirst\\(\\s*\\w+\\s*,\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern GETENV = Pattern.compile("System\\.getenv\\(\\s*\"([A-Za-z0-9_]+)\"\\s*\\)");
    private static final Pattern PYTHON_ENV = Pattern.compile("(?:os\\.environ(?:\\.get)?[\\[(]|os\\.getenv\\(|environ\\.get\\()"
            + "\\s*[\"']([A-Za-z0-9_]+)[\"']");
    private static final Pattern DOTTED = Pattern.compile("[A-Za-z][A-Za-z0-9_\\-]*(?:\\.[A-Za-z0-9_\\-]+)+");
    private static final List<String> REAL_FILES = List.of("Core_trunk/fw.properties", "Core_trunk/fw.experimental-byte-keys.properties",
            "generator_trunk/config/core.fw.properties", "generator_trunk/config/reader.fw.properties", "Reader_trunk/fw.properties",
            "SmallUseCasesDemo/05-d5-nested-pipelines/phase-b/config/core-b.fw.properties",
            "SmallUseCasesDemo/15-d13-composed-carriers/config/core-d13b.fw.properties");

    private static Path checkout;

    @BeforeAll
    static void findTheCheckout() {
        checkout = Reactor.root().getParent().resolve("Combinatorics-Framework-QA-edition");
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve("Core_trunk")),
                "the legacy checkout is not beside the project: the completeness evidence cannot be produced");
    }

    private static List<Path> files(String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(checkout)) {
            return walk.filter(p -> !p.toString().contains("/.git/") && !p.toString().contains("/target/"))
                    .filter(p -> p.getFileName().toString().endsWith(suffix) && Files.isRegularFile(p)).sorted().toList();
        }
    }

    private static List<String> lines(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).lines().toList();
    }

    /** name to "path:line" of the first place the scan found it; environment names carry the prefix "env:". */
    private static Map<String, String> scan() throws IOException {
        Map<String, String> found = new TreeMap<>();
        TreeSet<String> fileKeys = new TreeSet<>();
        for (Path file : files(".properties")) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
            for (String key : p.stringPropertyNames()) {
                fileKeys.add(key);
                found.putIfAbsent(key, checkout.relativize(file).toString());
            }
        }
        for (Path file : files(".java")) {
            List<String> lines = lines(file);
            for (int i = 0; i < lines.size(); i++) {
                String where = checkout.relativize(file) + ":" + (i + 1);
                Matcher call = CALL.matcher(lines.get(i));
                while (call.find()) {
                    keep(call.group(1), fileKeys, where, found);
                }
                Matcher first = FIRST.matcher(lines.get(i));
                while (first.find()) {
                    keep(first.group(1), fileKeys, where, found);
                    keep(first.group(2), fileKeys, where, found);
                }
                Matcher env = GETENV.matcher(lines.get(i));
                while (env.find()) {
                    found.putIfAbsent("env:" + env.group(1), where);
                }
            }
        }
        for (Path file : files(".py")) {
            List<String> lines = lines(file);
            for (int i = 0; i < lines.size(); i++) {
                Matcher env = PYTHON_ENV.matcher(lines.get(i));
                while (env.find()) {
                    found.putIfAbsent("env:" + env.group(1), checkout.relativize(file) + ":" + (i + 1));
                }
            }
        }
        return found;
    }

    private static void keep(String name, TreeSet<String> fileKeys, String where, Map<String, String> found) {
        if (DOTTED.matcher(name).matches() || fileKeys.contains(name)) {
            found.putIfAbsent(name, where);
        }
    }

    @Test
    void everySettingNameTheLegacyCheckoutReadsHasExactlyOneDispositionInTheCatalog() throws IOException {
        Map<String, String> found = scan();
        assertTrue(found.size() >= 270, "the scan looks too small: " + found.size());
        LegacySettings catalog = LegacySettings.catalog();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> name : found.entrySet()) {
            boolean environment = name.getKey().startsWith("env:");
            String plain = environment ? name.getKey().substring(4) : name.getKey();
            boolean covered = catalog.all().stream().anyMatch(s -> s.matches(plain)
                    && (s.kind() == LegacySetting.Kind.ENVIRONMENT) == environment);
            if (!covered) {
                missing.add(name.getKey() + " (" + name.getValue() + ")");
            }
        }
        assertEquals(List.of(), missing, "names the legacy checkout reads without a catalog entry");
    }

    @Test
    void theCatalogsEvidencePointsAtRealLinesOfTheCheckout() throws IOException {
        List<String> stale = new ArrayList<>();
        Map<String, List<String>> cache = new TreeMap<>();
        for (LegacySetting s : LegacySettings.catalog().all()) {
            for (LegacySetting.Location l : s.foundIn()) {
                String line = line(cache, l);
                if (line == null || !line.stripLeading().startsWith(s.name())) {
                    stale.add("found-in " + s.name() + " " + l);
                }
            }
            for (LegacySetting.Location l : s.readAt()) {
                String line = line(cache, l);
                if (line == null || !line.contains(s.name())) {
                    stale.add("read-at " + s.name() + " " + l);
                }
            }
        }
        assertEquals(List.of(), stale);
    }

    private static String line(Map<String, List<String>> cache, LegacySetting.Location l) throws IOException {
        if (!cache.containsKey(l.path())) {
            Path file = checkout.resolve(l.path());
            cache.put(l.path(), Files.isRegularFile(file) ? lines(file) : List.of());
        }
        List<String> lines = cache.get(l.path());
        return l.line() <= lines.size() ? lines.get(l.line() - 1) : null;
    }

    @Test
    void theSevenRealLegacyFilesImportWithEveryKeyClassifiedAndNoSecretShown() throws IOException {
        List<Path> paths = REAL_FILES.stream().map(checkout::resolve).toList();
        LegacyImport imported = LegacyImporter.importFiles(paths, Map.of());
        assertEquals(135, imported.entries().size(), "the keys of the seven files, the later file winning");
        assertTrue(imported.entries().stream().allMatch(e -> e.disposition() != null), "unclassified keys");
        Map<LegacySetting.Disposition, Integer> expected = new EnumMap<>(LegacySetting.Disposition.class);
        // supplement to revision 2: the library switch and distinctify__fwX_Y__Tables are mapped now (G6, G7);
        // M03 maps the fifteen replace, assembly, optional, render and constraint settings these files use; M04 maps
        // results.db.onlyFW_CUSTOM_VAR and supersedes the two results-table layout settings; M06 maps the five analyzer
        // settings these files use and supersedes fw.analyzer.queueCapacity, so nothing is pending any more
        expected.putAll(Map.of(LegacySetting.Disposition.MAPPED, 31, LegacySetting.Disposition.RETIRED, 88,
                LegacySetting.Disposition.INERT, 12, LegacySetting.Disposition.SUPERSEDED, 4));
        assertEquals(expected, imported.counts());
        assertEquals(List.of(), imported.diagnostics().stream().map(Diagnostic::code).distinct().toList());
        assertEquals(0, imported.pending().size());
        // each file alone imports without an error either; the generator Reader file's .py candidates too, since the Python
        // add-on runs them (author decision 2026-10-12, D115)
        for (Path path : paths) {
            LegacyImport one = LegacyImporter.importFiles(List.of(path), Map.of());
            List<ErrorCode> errors = one.diagnostics().stream().filter(d -> d.severity().isError()).map(Diagnostic::code).toList();
            assertEquals(List.of(), errors, path.toString());
        }
        // no credential value of any file appears in any rendering
        StringBuilder xml = new StringBuilder();
        imported.appendXml(xml, "");
        String everything = imported + xml.toString() + imported.toText() + imported.diagnostics();
        for (Path path : paths) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(path)) {
                p.load(in);
            }
            for (String key : p.stringPropertyNames()) {
                String value = p.getProperty(key).trim();
                if (Pattern.compile("(?i)password|passwd|secret|token|credential").matcher(key).find() && !value.isEmpty()) {
                    assertFalse(everything.contains(value), "the value of " + key + " leaked");
                }
            }
        }
        assertTrue(imported.entries().stream().filter(e -> e.key().endsWith("password"))
                .allMatch(e -> e.shownValue().equals(LegacyImport.REDACTED)));
    }
}
