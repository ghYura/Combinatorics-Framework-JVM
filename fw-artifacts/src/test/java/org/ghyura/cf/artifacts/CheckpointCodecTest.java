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

package org.ghyura.cf.artifacts;

import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.results.ResultRepository;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Gate A14: the checkpoint file keeps every field exactly and refuses what was altered, truncated or written by another schema. */
class CheckpointCodecTest {

    @Test
    void oversizedHistoryAndInputFailBeforeAllocationAndKeepTheLastFile(@TempDir Path dir) throws Exception {
        Checkpoint small = checkpoint("1");
        Observation o = small.observations().getFirst();
        Observation large = new Observation(o.key(), o.display(), o.outcome(), o.domain(), o.elapsedNanos(), o.sourceDigest(),
                o.policyDigest(), o.failure(), o.failureMessage(), "x<&\"".repeat(4096), o.stderr(), false);
        Checkpoint huge = new Checkpoint(small.runId(), small.state(), small.scenarioPath(), small.scenarioDigest(), small.planDigest(),
                small.dictionaryDigest(), small.policyDigest(), small.plugin(), small.pluginVersion(), small.configuration(),
                java.util.Collections.nCopies(512, large), small.attachments());
        MemoryBudget budget = new MemoryBudget(8L << 20);
        Path file = dir.resolve("cp.xml");
        CheckpointCodec.write(file, small, budget);
        byte[] good = Files.readAllBytes(file);
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> CheckpointCodec.write(file, huge, budget)).diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());
        assertArrayEquals(good, Files.readAllBytes(file));
        assertEquals(small, CheckpointCodec.read(file, budget));
        Path oversized = dir.resolve("oversized.xml");
        try (var channel = java.nio.channels.FileChannel.open(oversized, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(256L << 20);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
        }
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> CheckpointCodec.read(oversized, budget)).diagnostics().getFirst().code());
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> CheckpointCodec.decode(good, "memory", new MemoryBudget(1024))).diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());
    }

    /**
     * A run whose captured output is long (cross-check with the legacy 2026-10-13, demo D2: one value over 100,000
     * characters, 4 MB in all) writes and reads its checkpoint under the default 256 MiB budget: encoding reserves from the
     * measured payload instead of a worst case, decoding from a measured factor, and the parser admits values beyond the
     * JDK's secure-processing size limits for this checksummed, DTD-free file.
     */
    @Test
    void aCheckpointWithLongCapturedOutputIsWrittenAndReadUnderTheDefaultBudget(@TempDir Path dir) {
        Checkpoint small = checkpoint("1");
        Observation o = small.observations().getFirst();
        String output = "app=d2 FW_VAR=0 rec=" + "ab<&\"cd\n".repeat(20_000) + " \u0436";
        List<Observation> many = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            SampleKey key = new SampleKey(o.key().runId(), o.key().candidateId(), o.key().attempt(), i, o.key().environmentId());
            many.add(new Observation(key, o.display(), o.outcome(), o.domain(), o.elapsedNanos(), o.sourceDigest(), o.policyDigest(),
                    o.failure(), o.failureMessage(), output, o.stderr(), false));
        }
        Checkpoint c = new Checkpoint(small.runId(), small.state(), small.scenarioPath(), small.scenarioDigest(), small.planDigest(),
                small.dictionaryDigest(), small.policyDigest(), small.plugin(), small.pluginVersion(), small.configuration(), many,
                small.attachments());
        MemoryBudget budget = new MemoryBudget(256L << 20);
        Path file = dir.resolve("cp.xml");
        CheckpointCodec.write(file, c, budget);
        assertEquals(c, CheckpointCodec.read(file, budget));
        assertEquals(0, budget.reservedBytes());
    }

    private static final String AWKWARD = "line one\r\nline\ttwo\n\u0000\u0007 <&\"'> 😀 \uD800";

    @Test
    void largeUnrepresentableValuesAndDenseMapsStayWithinAdmission() {
        Checkpoint small = checkpoint("1");
        Observation o = small.observations().getFirst();
        Map<String, String> attributes = new java.util.TreeMap<>();
        for (int i = 0; i < 10_000; i++) attributes.put("k" + i, "v");
        Observation large = new Observation(o.key(), o.display(), o.outcome(),
                Optional.of(new DomainObservation(0, Map.of(), attributes)), o.elapsedNanos(), o.sourceDigest(), o.policyDigest(),
                o.failure(), o.failureMessage(), "text\u0000\uD800".repeat(100_000), o.stderr(), false);
        Checkpoint c = new Checkpoint(small.runId(), small.state(), small.scenarioPath(), small.scenarioDigest(), small.planDigest(),
                small.dictionaryDigest(), small.policyDigest(), small.plugin(), small.pluginVersion(), small.configuration(),
                List.of(large), small.attachments());
        MemoryBudget budget = new MemoryBudget(96L << 20);
        byte[] bytes = CheckpointCodec.encode(c, budget);
        assertEquals(c, CheckpointCodec.decode(bytes, "dense", budget));
        assertEquals(0, budget.reservedBytes());
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> CheckpointCodec.encode(c, new MemoryBudget(1024))).diagnostics().getFirst().code());
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> CheckpointCodec.decode(bytes, "dense", new MemoryBudget(12L * bytes.length + 65_536)))
                .diagnostics().getFirst().code(), "dense DOM nodes need admission beyond text buffers");
    }

    private static Checkpoint checkpoint(String version) {
        RunId run = new RunId("r1");
        CandidateId id = new CandidateId("ab".repeat(32));
        SampleKey first = new SampleKey(run, id, 0, 0, "");
        SampleKey second = new SampleKey(run, id, 0, 1, "env-2");
        Observation pass = new Observation(first, "1_0_0", Outcome.PASS, Optional.of(new DomainObservation(0, Map.of("latency", 0.1 + 0.2,
                "big", 1e300), Map.of("app", AWKWARD))), 12345, "src", "pol", Optional.empty(), "", "out " + AWKWARD, "", false);
        Observation timeout = new Observation(second, "1_0_0", Outcome.TIMEOUT, Optional.empty(), 7, "src", "pol",
                Optional.of(ErrorCode.EXEC_CANCELLED), AWKWARD, "", AWKWARD, true);
        return new Checkpoint("r1", "PAUSED", "/tmp/s.json", "s".repeat(64), "p".repeat(64), "d", "pol", "inline-metrics", version,
                Map.of("cf.run.id", "r1", "cf.analysis.goals", " a:min , b:max "), List.of(pass, timeout),
                List.of(new ResultRepository.Attachment(first, "source", AWKWARD)));
    }

    @Test
    void everyFieldSurvivesAWriteAndARead(@TempDir Path dir) {
        Checkpoint c = checkpoint("1");
        Path file = dir.resolve("cp.xml");
        CheckpointCodec.write(file, c);
        Checkpoint back = CheckpointCodec.read(file);
        assertEquals(c, back);
        assertEquals(c.configDigest(), back.configDigest());
        assertArrayEquals(CheckpointCodec.encode(c), CheckpointCodec.encode(back), "deterministic bytes");
    }

    @Test
    void anAlteredTruncatedOrForeignFileIsRefused() {
        byte[] good = CheckpointCodec.encode(checkpoint("1"));
        byte[] altered = good.clone();
        int at = new String(good, StandardCharsets.ISO_8859_1).indexOf("12345");
        altered[at] = '9';
        assertCorrupt(altered, "SHA-256");
        assertCorrupt(Arrays.copyOf(good, good.length / 2), "truncated");
        assertCorrupt(new String(good, StandardCharsets.ISO_8859_1).replace("schema=\"1\"", "schema=\"2\"").getBytes(StandardCharsets.ISO_8859_1),
                "schema is 2");
        assertCorrupt("not a checkpoint".getBytes(StandardCharsets.UTF_8), "header");
    }

    private static void assertCorrupt(byte[] bytes, String why) {
        FrameworkException e = assertThrows(FrameworkException.class, () -> CheckpointCodec.decode(bytes, "cp.xml"));
        assertEquals(ErrorCode.CHECKPOINT_CORRUPT, e.diagnostics().getFirst().code());
        assertEquals(true, e.getMessage().contains(why), e.getMessage());
    }

    @Test
    void aFailedReplacementKeepsTheLastGoodCheckpoint(@TempDir Path dir) throws Exception {
        Path sub = Files.createDirectory(dir.resolve("cps"));
        Path file = sub.resolve("cp.xml");
        CheckpointCodec.write(file, checkpoint("1"));
        byte[] before = Files.readAllBytes(file);
        Files.setPosixFilePermissions(sub, PosixFilePermissions.fromString("r-x------"));
        try {
            assertThrows(java.io.UncheckedIOException.class, () -> CheckpointCodec.write(file, checkpoint("2")));
        } finally {
            Files.setPosixFilePermissions(sub, PosixFilePermissions.fromString("rwx------"));
        }
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals("1", CheckpointCodec.read(file).pluginVersion());
    }
}
