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

import org.ghyura.cf.desktop.session.FileTrail.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GEN:operator-authoring-ui: the file trail that tells every file button where the framework's files are and will be. */
class FileTrailTest {

    private static Path write(Path file, String text, long minutesAgo) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - minutesAgo * 60_000));
        return file;
    }

    private static void until(BooleanSupplier condition, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < end, "not seen in time: " + what);
            Thread.sleep(20);
        }
    }

    private static Path newest(FileTrail trail, Kind kind) {
        FileTrail.Seen s = trail.newest(EnumSet.of(kind));
        return s == null ? null : s.file();
    }

    @Test
    void filesAreKnownByNameAndByTheirFirstBytes(@TempDir Path dir) throws IOException {
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("shop.xlsx", "PK");
        files.put("shop.json", "{\"sheets\": {\"FW_Seq\": []}}");
        files.put("spec.toml", "[[slots]]");
        files.put("shop.constraints.json", "{\"version\": 1}");
        files.put("sidecar.json", "{\"version\": 1, \"constraints\": []}");
        files.put("showcase.properties", "cf.run.id=x");
        files.put("export.xml", "<?xml version=\"1.0\"?>\n<cf-export kind=\"report\" schema=\"1\">");
        files.put("run.checkpoint", "<cf-checkpoint schema=\"1\" payloadSha256=\"\">");
        files.put("saved.xml", "<?xml version=\"1.0\"?>\n<cf-checkpoint schema=\"1\" payloadSha256=\"\">");
        files.put("results.csv", "candidate_id,repeat_idx,env_id,attempt,outcome,verdict_code\n");
        files.put("metrics.kv", "latency=1");
        files.put("lines-scores.csv", "line,score,decision\n");
        files.put("other.csv", "a,b\n");
        files.put("page.html", "<html>");
        files.put("plan.dot", "digraph {}");
        files.put("plan.json", "{\"nodes\": [], \"edges\": []}");
        files.put("manual.json", "{\"required_tokens\": []}");
        files.put("commands.txt", "ls -la");
        files.put("verified_examples.json", "[]");
        files.put("notes.md", "# notes");
        files.put(".hidden.json", "{\"sheets\": {}}");
        Map<String, Kind> expected = new java.util.LinkedHashMap<>();
        List<Kind> kinds = java.util.Arrays.asList(Kind.SCENARIO, Kind.SCENARIO, Kind.SCENARIO, Kind.CONSTRAINTS, Kind.CONSTRAINTS,
                Kind.SETTINGS, Kind.REPORT, Kind.CHECKPOINT, Kind.CHECKPOINT, Kind.RESULTS, Kind.METRICS, Kind.SCORES, null, Kind.PAGE,
                Kind.GRAPH, Kind.GRAPH, Kind.RULES, Kind.TEXT, Kind.LIBRARY, null, null);
        int i = 0;
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path f = write(dir.resolve(e.getKey()), e.getValue(), 0);
            assertEquals(kinds.get(i++), FileTrail.classify(f), e.getKey());
        }
    }

    @Test
    void theNewestFileOfEachKindIsKnownAndAFileWrittenLaterIsSeenByTheWatcher(@TempDir Path dir) throws Exception {
        Path older = write(dir.resolve("old-results.csv"), "candidate_id,repeat_idx\n", 30);
        Path newer = write(dir.resolve("results.csv"), "candidate_id,repeat_idx\n", 5);
        write(dir.resolve("shop.xlsx"), "PK", 60);
        try (FileTrail trail = FileTrail.inMemory()) {
            java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
            trail.addListener(changes::incrementAndGet);
            trail.watch(dir);
            until(() -> newer.equals(newest(trail, Kind.RESULTS)), "the newer of two results already there");
            assertEquals(dir.resolve("shop.xlsx"), newest(trail, Kind.SCENARIO), "a scan finds what was written before");
            assertNull(newest(trail, Kind.REPORT));
            assertTrue(changes.get() > 0, "listeners hear of what the trail learns");

            // a file appears later, written by anyone: the watcher sees it
            Path fresh = dir.resolve("run").resolve("again-results.csv");
            Files.createDirectories(fresh.getParent());
            trail.watch(fresh.getParent());
            trail.settle(5_000);
            Files.writeString(fresh, "candidate_id,repeat_idx,env_id\n", StandardCharsets.UTF_8);
            until(() -> fresh.equals(newest(trail, Kind.RESULTS)), "a results file written after the watch began");

            // deleted, it gives its place back
            Files.delete(fresh);
            until(() -> newer.equals(newest(trail, Kind.RESULTS)), "the newest results again after the deletion");
            assertTrue(trail.folders().contains(dir) && trail.folders().contains(fresh.getParent()), trail.folders().toString());
            assertTrue(Files.exists(older));
        }
    }

    @Test
    void aRecordedFileIsKnownAtOnceAndItsFolderWatched(@TempDir Path dir) throws Exception {
        Path report = write(dir.resolve("out").resolve("shop-report.xml"), "<cf-export kind=\"report\" schema=\"1\">", 0);
        try (FileTrail trail = FileTrail.inMemory()) {
            trail.record(report);
            assertEquals(report, newest(trail, Kind.REPORT), "known without waiting for the watcher");
            trail.settle(5_000);
            until(() -> trail.folders().contains(report.getParent()), "its folder watched");
            Path library = Files.createDirectories(dir.resolve("generator_trunk").resolve("intake")).getParent();
            trail.record(library);
            assertEquals(library, newest(trail, Kind.LIBRARY), "a folder with an intake folder is a library");
        }
    }

    @Test
    void aRememberedTrailComesBackInTheNextSession(@TempDir Path dir) throws Exception {
        Path store = dir.resolve("config").resolve("desktop-places.properties");
        Path scenario = write(dir.resolve("work").resolve("shop.xlsx"), "PK", 0);
        try (FileTrail trail = FileTrail.remembered(store)) {
            trail.record(scenario);
            trail.settle(5_000);
        }
        assertTrue(Files.readString(store).contains("newest.SCENARIO=" + scenario.toString().replace(":", "\\:")), Files.readString(store));
        try (FileTrail next = FileTrail.remembered(store)) {
            assertEquals(scenario, newest(next, Kind.SCENARIO), "known again, its folder watched again");
            next.settle(5_000);
            until(() -> next.folders().contains(scenario.getParent()), "its folder watched again");
        }
        assertEquals(Path.of("/x/cf").resolve("combinatorics-framework").resolve("desktop-places.properties"),
                FileTrail.defaultStore(Map.of("XDG_CONFIG_HOME", "/x/cf")));
        assertTrue(FileTrail.defaultStore(Map.of()).endsWith(Path.of(".config", "combinatorics-framework", "desktop-places.properties")));
    }

    @Test
    void rememberedPathsKeepUnicodeAcrossSessions(@TempDir Path dir) throws Exception {
        Path store = dir.resolve("desktop-places.properties");
        Path scenario = write(dir.resolve("Київ").resolve("сценарій.xlsx"), "PK", 0);
        try (FileTrail trail = FileTrail.remembered(store)) {
            trail.record(scenario);
            trail.settle(5_000);
        }
        try (FileTrail next = FileTrail.remembered(store)) {
            assertEquals(scenario, newest(next, Kind.SCENARIO));
            next.settle(5_000);
            until(() -> next.folders().contains(scenario.getParent()), "the Unicode folder watched again");
        }
    }

    @Test
    void anInvalidRememberedPathDoesNotDiscardTheValidOnes(@TempDir Path dir) throws Exception {
        Path scenario = write(dir.resolve("work").resolve("shop.xlsx"), "PK", 0);
        Path store = dir.resolve("desktop-places.properties");
        java.util.Properties saved = new java.util.Properties();
        saved.setProperty("folder.000", "invalid\0folder");
        saved.setProperty("newest.REPORT", "invalid\0report.xml");
        saved.setProperty("newest.SCENARIO", scenario.toString());
        try (var writer = Files.newBufferedWriter(store, StandardCharsets.UTF_8)) {
            saved.store(writer, "test");
        }
        try (FileTrail next = FileTrail.remembered(store)) {
            assertEquals(scenario, newest(next, Kind.SCENARIO));
            assertNull(newest(next, Kind.REPORT));
        }
    }
}
