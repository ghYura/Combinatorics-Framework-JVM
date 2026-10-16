// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
package org.ghyura.cf.desktop;

import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.*;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Browser JSON -> real HTTP handler -> EDT -> normal save -> fresh Framework run. */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class Face2WorkbenchTest {
    private static final String PAIR = """
            {"version":1,"revision":0,"paramsPatch":[],"edits":[{"bond":{"polarity":"forbid","sides":{"A":["a"],"B":["1"]}}}]}
            """;

    @Test void browserSavesAreAcquiredByWorkbenchUndoPreviewAndANewRun(@TempDir Path dir) throws Exception {
        try (var fixture = new Fixture(dir)) {
            var bridge = edt(fixture.view::prepareFace2);
            var saved = Face2TestClient.request(bridge, "submit", PAIR);
            assertEquals(200, saved.status(), saved.text());
            assertFalse(edt(fixture.view::dirty));
            assertEquals(edt(fixture.view::document), fixture.session().openSieve(fixture.file).document());
            assertEquals(BigInteger.valueOf(3), fixture.kept());
            // A fresh Session has no editor state: only the normal saved companion can affect this run.
            try (var run = session().prepareRun(fixture.file)) {
                var summary = run.handle().await();
                assertEquals(RunState.COMPLETED, summary.state()); assertEquals(3, summary.samples());
            }
            edt(() -> { fixture.view.undo(); return null; });
            assertTrue(edt(fixture.view::dirty)); assertTrue(edt(() -> fixture.view.document().rules().isEmpty()));
            edt(() -> { fixture.view.redo(); return null; }); assertFalse(edt(fixture.view::dirty));
            assertTrue(Face2TestClient.request(bridge, "", "").text().contains("\"jvmRevision\": 1"));
            var cleared = Face2TestClient.request(bridge, "submit", "{\"version\":1,\"revision\":1,\"edits\":[],\"paramsPatch\":[]}");
            assertEquals(200, cleared.status(), cleared.text()); assertFalse(edt(fixture.view::dirty));
            assertEquals(BigInteger.valueOf(4), fixture.kept());
        }
    }

    @Test void failedSaveLeavesBothEditorsUnchangedAndTheSameSubmissionCanRetry(@TempDir Path dir) throws Exception {
        try (var fixture = new Fixture(dir)) {
            var bridge = edt(fixture.view::prepareFace2);
            var before = edt(fixture.view::document);
            Path companion = ConstraintSidecarReader.companionOf(fixture.file);
            Files.writeString(companion, before.json()); // an external file appeared after opening the editor
            var failed = Face2TestClient.request(bridge, "submit", PAIR);
            assertNotEquals(200, failed.status()); assertTrue(failed.text().contains("changed"), failed.text());
            assertEquals(before, edt(fixture.view::document)); assertFalse(edt(fixture.view::dirty));
            assertEquals(before.json(), Files.readString(companion));
            Files.delete(companion); // restore this test fixture to its original fingerprint
            var retried = Face2TestClient.request(bridge, "submit", PAIR);
            assertEquals(200, retried.status(), retried.text()); assertEquals(BigInteger.valueOf(3), fixture.kept());
            edt(() -> { fixture.view.undo(); return null; });
            assertEquals(before, edt(fixture.view::document), "a failed save must not add an undo entry");
        }
    }

    @Test void nativeEditsAndAReplacedConnectionRefuseOldBrowserSubmissions(@TempDir Path dir) throws Exception {
        try (var fixture = new Fixture(dir)) {
            var bridge = edt(fixture.view::prepareFace2);
            edt(() -> { fixture.view.pair(new SieveCanvas.Value("A", "b"), new SieveCanvas.Value("B", "2")); return null; });
            var nativeEdit = edt(fixture.view::document);
            var refused = Face2TestClient.request(bridge, "submit", PAIR);
            assertEquals(409, refused.status()); assertTrue(refused.text().contains("changed"));
            assertEquals(nativeEdit, edt(fixture.view::document)); assertEquals(BigInteger.valueOf(4), fixture.kept());
            edt(fixture.view::prepareFace2);
            assertEquals(410, Face2TestClient.request(bridge, "submit", PAIR).status());
        }
    }

    @Test void changedRunnerConfigurationRefusesAnOldBrowserSnapshot(@TempDir Path dir) throws Exception {
        try (var fixture = new Fixture(dir)) {
            var bridge = edt(fixture.view::prepareFace2);
            fixture.sessions.set(session());
            assertEquals(409, Face2TestClient.request(bridge, "submit", PAIR).status());
            assertFalse(edt(fixture.view::dirty)); assertEquals(BigInteger.valueOf(4), fixture.kept());
        }
    }

    private static Session session() {
        return Session.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.plan.autoPromoteSingleVerb", "false", "test"),
                new ConfigResolver.Override("cf.execution.sut", "inline-metrics", "test")));
    }

    private static final class Fixture implements AutoCloseable {
        final Path file;
        final AtomicReference<Session> sessions = new AtomicReference<>(Face2WorkbenchTest.session());
        final FileTrail trail = FileTrail.inMemory();
        final SieveWorkbench view;
        Fixture(Path dir) throws Exception {
            file = Files.writeString(dir.resolve("face2.json"), """
                    {"sheets":{"FW_Seq":[["A","FW_Combi(1)"],["B","FW_Combi(1)"]],"A":[["a"],["b"]],"B":[["1"],["2"]]}}
                    """);
            view = edt(() -> new SieveWorkbench(sessions::get, () -> file, new Navigator(trail, () -> file)));
            edt(view::open).get(30, TimeUnit.SECONDS);
        }
        Session session() { return sessions.get(); }
        BigInteger kept() {
            return session().preview(file, new CandidatePreview.Request(BigInteger.valueOf(100), BigInteger.ZERO, 10, true, true), Cancellation.NONE)
                    .estimates().candidates().stream().filter(s -> s.name().equals("executable")).findFirst().orElseThrow().count().value();
        }
        @Override public void close() {
            try { edt(view::close).get(30, TimeUnit.SECONDS); trail.close(); }
            catch (Exception e) { throw new IllegalStateException("Could not close the test workbench", e); }
        }
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get(30, TimeUnit.SECONDS);
    }
}
