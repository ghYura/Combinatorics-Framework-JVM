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

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.results.CampaignCheckpoint;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CampaignCheckpointCodecTest {
    @Test
    void nestedHistoryUsesOneBudgetAndEveryFailureReleasesIt(@TempDir Path dir) throws Exception {
        CampaignCheckpoint small = checkpoint();
        CampaignCheckpoint huge = new CampaignCheckpoint(small.scenarioPath(), small.baseScenarioDigest(), small.configuration(),
                small.strategy(), 513, 0, 0.25, 5, java.util.Collections.nCopies(512, "{\"data\":\"" + "\\u0000".repeat(2048) + "\"}"),
                small.inputSeedDigest(), small.nextSeedJson(), small.fullSpace(), small.previousFront(), 0, 513,
                small.childScenarioDigest(), small.currentRun(), "", "", small.diagnostics());
        MemoryBudget budget = new MemoryBudget(8L << 20);
        Path file = dir.resolve("campaign.xml");
        CampaignCheckpointCodec.write(file, small, budget);
        byte[] good = Files.readAllBytes(file);
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> CampaignCheckpointCodec.write(file, huge, budget)).diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());
        org.junit.jupiter.api.Assertions.assertArrayEquals(good, Files.readAllBytes(file));
        assertEquals(small, CampaignCheckpointCodec.read(file, budget));
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> CampaignCheckpointCodec.read(file, new MemoryBudget(1024))).diagnostics().getFirst().code());
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> CampaignCheckpointCodec.decode(good, "memory", new MemoryBudget(1024))).diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());
        assertThrows(FrameworkException.class, () -> CampaignCheckpointCodec.decode(new byte[] {0}, "corrupt", budget));
        assertEquals(0, budget.reservedBytes());
    }
    private static CampaignCheckpoint checkpoint() {
        return checkpoint("bias");
    }

    @Test
    void aCampaignWithALargeCurrentRunFitsTheDefaultRunBudget() {
        CampaignCheckpoint small = checkpoint();
        Checkpoint child = small.currentRun().orElseThrow();
        var key = new org.ghyura.cf.model.SampleKey(new org.ghyura.cf.model.RunId(child.runId()),
                new org.ghyura.cf.model.CandidateId("a".repeat(64)), 0, 0, "local");
        Checkpoint large = new Checkpoint(child.runId(), child.state(), child.scenarioPath(), child.scenarioDigest(), child.planDigest(),
                child.dictionaryDigest(), child.policyDigest(), child.plugin(), child.pluginVersion(), child.configuration(), List.of(),
                List.of(new org.ghyura.cf.results.ResultRepository.Attachment(key, "source", "abc".repeat(500_000))));
        CampaignCheckpoint c = small.withCurrentRun(large);
        MemoryBudget budget = new MemoryBudget(256L << 20);
        byte[] bytes = CampaignCheckpointCodec.encode(c, budget);
        assertEquals(c, CampaignCheckpointCodec.decode(bytes, "campaign", budget));
        assertEquals(0, budget.reservedBytes());
        assertEquals(ErrorCode.RESOURCE_LIMIT, assertThrows(FrameworkException.class,
                () -> CampaignCheckpointCodec.encode(c, new MemoryBudget(1024))).diagnostics().getFirst().code());
    }

    private static CampaignCheckpoint checkpoint(String strategy) {
        Checkpoint run = new Checkpoint("base-it2", "PAUSED", "scenario\u0000\ud800", "child", "plan", "keys", "policy", "sut", "1",
                Map.of("k", "\n\t\u0000\ud800"), List.of(), List.of());
        return new CampaignCheckpoint("scenario\u0000\ud800", "base", Map.of("k", "\n\t\u0000\ud800"), strategy, 3, 0, 0.25, 5,
                List.of("{\"text\":\"\ud800\"}"), "seed1", "{\"seed\":1}", "18446744073709551616", List.of("a", "b"), 0, 2, "child",
                Optional.of(run), "", "", List.of("{\"message\":\"line\nend\"}"));
    }

    @Test
    void everyFieldIncludingCurrentRunAndUnrepresentableTextRoundTripsExactly(@TempDir Path dir) throws Exception {
        CampaignCheckpoint original = checkpoint();
        assertEquals(original, CampaignCheckpointCodec.decode(CampaignCheckpointCodec.encode(original), "memory"));
        Path file = dir.resolve("campaign.xml");
        CampaignCheckpointCodec.write(file, original);
        assertTrue(CampaignCheckpointCodec.isCampaign(file));
        assertEquals(original, CampaignCheckpointCodec.read(file));
    }

    @Test
    void corruptTruncatedForeignAndUnsupportedDocumentsAreRefused() {
        String encoded = new String(CampaignCheckpointCodec.encode(checkpoint()), StandardCharsets.US_ASCII);
        int payload = encoded.indexOf("<payload>") + "<payload>".length();
        List<String> invalid = List.of(encoded.substring(0, encoded.length() - 5), encoded.replace("schema=\"1\"", "schema=\"2\""),
                encoded.substring(0, payload) + "B" + encoded.substring(payload + 1), encoded.replace("cf-campaign-checkpoint", "other"),
                "<!DOCTYPE cf-campaign-checkpoint [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + encoded);
        for (String text : invalid) assertEquals(ErrorCode.CHECKPOINT_CORRUPT, assertThrows(FrameworkException.class,
                () -> CampaignCheckpointCodec.decode(text.getBytes(StandardCharsets.US_ASCII), "memory")).diagnostics().getFirst().code());
    }

    @Test
    void aFailedAtomicReplacementKeepsTheLastCheckpoint(@TempDir Path dir) throws Exception {
        Path sub = Files.createDirectory(dir.resolve("checkpoints"));
        Path file = sub.resolve("campaign.xml");
        CampaignCheckpointCodec.write(file, checkpoint());
        byte[] good = Files.readAllBytes(file);
        Files.setPosixFilePermissions(sub, PosixFilePermissions.fromString("r-x------"));
        try { assertThrows(java.io.UncheckedIOException.class, () -> CampaignCheckpointCodec.write(file, checkpoint("noop"))); }
        finally { Files.setPosixFilePermissions(sub, PosixFilePermissions.fromString("rwx------")); }
        assertTrue(java.util.Arrays.equals(good, Files.readAllBytes(file)));
        assertEquals(checkpoint(), CampaignCheckpointCodec.read(file));
    }
}
