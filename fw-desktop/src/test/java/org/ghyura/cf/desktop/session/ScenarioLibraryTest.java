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

import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scenario library against the unchanged legacy intake/scenario_library.py: scenario-library-goldens.json (made by
 * handoff/outbox/devtools/library/library_goldens.py on a scratch copy) holds hand-made generator trees and what
 * load_scenarios, suite_catalog, scenarios_in_suite, scenario_source_label and release_gate_candidate_count made of them.
 */
class ScenarioLibraryTest {

    @Test
    @SuppressWarnings("unchecked")
    void everyTreeIsCatalogedAsTheLegacyLibraryCatalogedIt(@TempDir Path dir) throws IOException {
        List<Object> goldens;
        try (InputStream in = ScenarioLibraryTest.class.getResourceAsStream("scenario-library-goldens.json")) {
            goldens = (List<Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "scenario-library-goldens.json");
        }
        int trees = 0;
        for (Object g : goldens) {
            Map<String, Object> tree = (Map<String, Object>) g;
            Path root = dir.resolve((String) tree.get("tree")).resolve("root");
            for (Map.Entry<String, Object> file : ((Map<String, Object>) tree.get("files")).entrySet()) {
                Path path = root.resolve(file.getKey()).normalize();
                Files.createDirectories(path.getParent());
                Files.writeString(path, (String) file.getValue(), StandardCharsets.UTF_8);
            }
            List<ScenarioLibrary.Entry> entries = ScenarioLibrary.load(root);
            List<Object> scenarios = new ArrayList<>();
            for (ScenarioLibrary.Entry e : entries) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.id());
                m.put("suite", e.suite());
                m.put("suite_title", e.suiteTitle());
                m.put("label", e.sourceLabel());
                if (e.dynamic()) {
                    m.put("release_gate_candidates", (long) e.releaseGateCandidates());
                }
                scenarios.add(m);
            }
            List<Object> suites = new ArrayList<>();
            Map<String, Object> inSuite = new LinkedHashMap<>();
            for (ScenarioLibrary.Suite s : ScenarioLibrary.suites(entries)) {
                suites.add(List.of(s.id(), s.title()));
                inSuite.put(s.id(), ScenarioLibrary.inSuite(entries, s.id()).stream().map(ScenarioLibrary.Entry::id).toList());
            }
            Map<String, Object> expected = (Map<String, Object>) tree.get("expected");
            assertEquals(expected.get("scenarios"), scenarios, (String) tree.get("tree"));
            assertEquals(expected.get("suites"), suites, (String) tree.get("tree"));
            assertEquals(expected.get("in_suite"), inSuite, (String) tree.get("tree"));
            trees++;
        }
        assertEquals(2, trees);
        ScenarioLibrary.Entry first = ScenarioLibrary.load(dir.resolve("mixed").resolve("root")).get(3);
        assertEquals(List.of("ok1", "A", "first", "6", "6", "x:max"), List.of(first.id(), first.title(), first.blurb(), first.candidates(),
                first.pass(), first.analyzer()));
        assertTrue(first.spec().endsWith(Path.of("usecases", "a", "a.toml")) && !first.dynamic(), first.toString());
    }
}
