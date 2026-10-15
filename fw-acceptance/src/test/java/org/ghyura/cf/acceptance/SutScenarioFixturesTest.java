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

import org.ghyura.cf.desktop.session.CandidatePreview;
import org.ghyura.cf.desktop.session.ScenarioValidation;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The SUT's FW_scenario_inputs workbooks (byte-identical copies) against predictions frozen from their cells, read by an independent reader, and the generator's wiring. */
class SutScenarioFixturesTest {
    private static final String ROOT = "/org/ghyura/cf/acceptance/sut-scenarios/";
    /** Cell text the legacy refinement can change: comments, line breaks, whitespace runs, quotes. */
    private static final java.util.regex.Pattern REFINED = java.util.regex.Pattern.compile("//|/\\*|\\*/|\\R|\\s{2,}|[\"']");
    private static String normalized(String text) { return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("\\s+", ""); }
    private static Path copy(Path dir, String name) throws Exception {
        Path target = dir.resolve(name.replace('/', '_'));
        try (var in = SutScenarioFixturesTest.class.getResourceAsStream(ROOT + name)) { assertNotNull(in, name); Files.write(target, in.readAllBytes()); }
        return target;
    }
    private static List<?> predictions() throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var in = SutScenarioFixturesTest.class.getResourceAsStream(ROOT + "sut-scenarios-golden.xml")) {
            assertNotNull(in); return (List<?>) Json.parse(factory.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("predictions").item(0).getTextContent(), "predictions");
        }
    }
    private static ScenarioValidation.Summary valid(Session session, Path file) {
        var summary = session.validate(file);
        assertTrue(summary.findings().stream().noneMatch(d -> d.severity() == Severity.ERROR), file + ": " + summary.findings());
        return summary;
    }
    private static List<String> candidates(Session session, Path file, int count) {
        var page = session.preview(file, new CandidatePreview.Request(BigInteger.valueOf(1_000_000), BigInteger.ZERO, count + 1, true, false), Cancellation.NONE).page();
        return page.rows().stream().map(CandidatePreview.Row::source).toList();
    }
    @Test void everyGeneratedScenarioAndAblationSeedAssemblesExactlyThePredictedCandidatesAndControls(@TempDir Path work) throws Exception {
        var session = Session.resolve(null, Map.of(), List.of()); int checked = 0;
        for (Object item : predictions()) {
            var row = (Map<?, ?>) item; String name = (String) row.get("file");
            if (!row.containsKey("candidates") || name.startsWith("legacy_root_fixtures/")) continue;
            var file = copy(work, name); var summary = valid(session, file); int count = ((Number) row.get("count")).intValue();
            assertEquals(row.get("sheets"), summary.spec().sheets().stream().map(SheetSpec::name).toList(), name);
            var observed = candidates(session, file, count);
            assertEquals(count, observed.size(), name);
            var predicted = (List<?>) row.get("candidates");
            if (predicted.stream().anyMatch(t -> REFINED.matcher((String) t).find())) {
                // The legacy Reader refines every cell (comments, line breaks, whitespace runs outside quotes); compare content without them.
                assertTrue(observed.stream().noneMatch(t -> t.contains("/*")), name);
                assertEquals(predicted.stream().map(t -> normalized((String) t)).sorted().toList(), observed.stream().map(SutScenarioFixturesTest::normalized).sorted().toList(), name);
            } else {
                assertEquals(row.get("first"), observed.getFirst(), name); assertEquals(row.get("last"), observed.getLast(), name);
                assertEquals(predicted, observed.stream().sorted().toList(), name);
            }
            var controls = summary.spec().controls();
            assertEquals(row.get("arguments"), controls.arguments().stream().map(LocatedText::text).toList(), name);
            assertEquals(List.of(row.get("runme")), controls.runMeFirstOnce().stream().map(LocatedText::text).toList(), name);
            assertEquals(row.get("custom_var"), controls.customVars().stream().map(v -> List.of((long) v.index(), v.declaration())).toList(), name);
            checked++;
        }
        assertEquals(16, checked);
    }
    @Test void legacyRootFixturesReadTheSameFromWorkbookAndJsonExport(@TempDir Path work) throws Exception {
        var session = Session.resolve(null, Map.of(), List.of()); int pairs = 0;
        for (Object item : predictions()) {
            var row = (Map<?, ?>) item; String name = (String) row.get("file");
            if (!name.startsWith("legacy_root_fixtures/")) continue;
            var xlsxFile = copy(work, name); valid(session, xlsxFile);
            if (row.containsKey("twin")) {
                // The JSON export may list extra empty sheets (join placeholders), so the documents differ but their candidates must not.
                assertEquals(Boolean.TRUE, row.containsKey("json_only_sheets") ? row.get("json_only_sheets_empty") : Boolean.TRUE, name);
                var jsonFile = copy(work, (String) row.get("twin")); valid(session, jsonFile);
                var request = new CandidatePreview.Request(BigInteger.valueOf(10_000_000), BigInteger.ZERO, CandidatePreview.MAX_PAGE, true, false);
                var fromXlsx = session.preview(xlsxFile, request, Cancellation.NONE); var fromJson = session.preview(jsonFile, request, Cancellation.NONE);
                assertEquals(fromXlsx.generated(), fromJson.generated(), name);
                assertEquals(fromXlsx.page().rows().stream().map(CandidatePreview.Row::source).toList(), fromJson.page().rows().stream().map(CandidatePreview.Row::source).toList(), name);
                pairs++;
            }
            if (row.containsKey("candidates")) {
                int count = ((Number) row.get("count")).intValue(); var observed = candidates(session, copy(work, name), count);
                assertEquals(row.get("candidates"), observed.stream().sorted().toList(), name);
                assertEquals(row.get("first"), observed.getFirst(), name); assertEquals(row.get("last"), observed.getLast(), name);
            }
        }
        assertEquals(3, pairs);
    }
}
