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


package org.ghyura.cf.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The legacy bundle report as export --kind html: one self-contained, deterministic page of a run's checkpoint. */
class RunReportExportTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void aRunsPageShowsItsVerdictsFindingsAndAttachmentsAndSaysWhatItLacks(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("a&b<c>.json"), "{\"sheets\": {\"FW_Seq\": [[\"P\", \"FW_Combi(1)\"], [\"M\", \"FW_Combi(1)\"]], "
                + "\"P\": [[\"proto=http;\"], [\"proto=ws;\"]], \"M\": [[\"mode=a FW_VAR=0 inv=none\"], [\"mode=b FW_VAR=1 inv=I4\"]]}}",
                StandardCharsets.UTF_8);
        Path checkpoint = dir.resolve("page.cp.xml");
        Result ran = run("run", "--scenario", scenario.toString(), "--sut", "inline-metrics", "--run-id", "page", "--checkpoint",
                checkpoint.toString());
        assertEquals(0, ran.code(), ran.err());

        Path html = dir.resolve("page.html");
        Result exported = run("export", "--input", checkpoint.toString(), "--output", html.toString(), "--kind", "html");
        assertEquals(0, exported.code(), exported.err());
        assertEquals("exported html of run page (4 committed attempts) to " + html + "\n", exported.out());
        String page = Files.readString(html);
        assertTrue(page.startsWith("<!doctype html>\n<html lang='en'><head><meta charset='utf-8'>"), page);
        assertTrue(page.contains("<title>Run page</title>") && page.endsWith("</main></body></html>\n"), page);
        assertTrue(page.contains("a&amp;b&lt;c&gt;.json") && !page.contains("a&b<c>"), "every recorded value is escaped");
        for (String external : new String[]{"<script", "http://", "https://", "src=", "@import", "url("}) {
            assertFalse(page.contains(external), "self-contained: " + external);
        }
        assertTrue(page.contains("<span class='pill ok'>COMPLETED</span>"), page);
        assertTrue(page.contains("<span class='chip ok'>PASS <b>2</b></span><span class='chip fail'>DOMAIN_FAIL <b>2</b></span>"), page);
        assertTrue(page.contains("<th>pass rate</th><td>50.0%  (2/4)</td>"), page);
        assertTrue(page.contains("<td class='mono'>inv=I4 mode=b</td><td class='num'>2</td><td class='num'>100.0%</td>"), "the triage findings: " + page);
        assertTrue(page.contains("A checkpoint keeps observations, not stage timings"), page);
        assertTrue(page.contains("A checkpoint keeps no analysis"), page);
        assertEquals(2, page.split("<td class='mono'>source</td>", -1).length - 1, "the two failing sources were kept and are listed");

        Path again = dir.resolve("again.html");
        assertEquals(0, run("export", "--input", checkpoint.toString(), "--output", again.toString(), "--kind", "html").code());
        assertArrayEquals(Files.readAllBytes(html), Files.readAllBytes(again), "the same checkpoint renders the same bytes");

        Path small = Files.writeString(dir.resolve("small.properties"), "cf.memory.budget.bytes=1048576\n"
                + "cf.pipeline.queue.maxBytes=1024\ncf.compiler.cache.maxBytes=1024\ncf.results.maxAttachmentBytes=1024\n");
        byte[] checkpointBefore = Files.readAllBytes(checkpoint);
        Result refused = run("export", "--input", checkpoint.toString(), "--output", html.toString(), "--kind", "html",
                "--config", small.toString());
        assertEquals(5, refused.code(), refused.err());
        assertTrue(refused.err().contains("RESOURCE_LIMIT"), refused.err());
        assertEquals(page, Files.readString(html), "resource refusal preserves the last successful export");
        assertArrayEquals(checkpointBefore, Files.readAllBytes(checkpoint));
        var state = org.ghyura.cf.artifacts.CheckpointCodec.read(checkpoint);
        var tiny = new org.ghyura.cf.store.MemoryBudget(65536);
        assertEquals(org.ghyura.cf.model.ErrorCode.RESOURCE_LIMIT,
                org.junit.jupiter.api.Assertions.assertThrows(org.ghyura.cf.model.FrameworkException.class,
                        () -> org.ghyura.cf.desktop.session.RunReportPage.encode(state, checkpoint.toString(), tiny)).primary().code());
        assertEquals(0, tiny.reservedBytes(), "failed rendering releases its workspace");

        Result unknown = run("export", "--input", checkpoint.toString(), "--output", dir.resolve("x.out").toString(), "--kind", "pdf");
        assertEquals(2, unknown.code(), unknown.err());
        assertTrue(unknown.err().contains("USAGE_INVALID_VALUE") && unknown.err().contains("report, results, csv, html"), unknown.err());
        assertFalse(Files.exists(dir.resolve("x.out")));
    }

    @Test
    void aRunWithoutFailuresSaysThereIsNothingToTriage(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("clean.json"), "{\"sheets\": {\"FW_Seq\": [[\"P\", \"FW_Combi(1)\"]], "
                + "\"P\": [[\"lat=1 FW_VAR=0\"], [\"lat=2 FW_VAR=0\"]]}}", StandardCharsets.UTF_8);
        Path checkpoint = dir.resolve("clean.cp.xml");
        assertEquals(0, run("run", "--scenario", scenario.toString(), "--sut", "inline-metrics", "--run-id", "clean", "--checkpoint",
                checkpoint.toString()).code());
        Path html = dir.resolve("clean.html");
        assertEquals(0, run("export", "--input", checkpoint.toString(), "--output", html.toString(), "--kind", "html").code());
        String page = Files.readString(html);
        assertTrue(page.contains("No sample failed: nothing to triage."), page);
        assertTrue(page.contains("No attachment was kept (cf.results.attach)."), page);
    }
}
