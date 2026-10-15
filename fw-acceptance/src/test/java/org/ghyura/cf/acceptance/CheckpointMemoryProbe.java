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

import org.ghyura.cf.artifacts.CampaignCheckpointCodec;
import org.ghyura.cf.artifacts.CheckpointCodec;
import org.ghyura.cf.campaign.Campaign;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.ghyura.cf.results.CampaignCheckpoint;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.spi.DomainObservation;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Runs against only the distribution jar, with a 64 MiB heap, 8 MiB codec and 16 MiB campaign budgets. */
public final class CheckpointMemoryProbe {
    private CheckpointMemoryProbe() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        MemoryBudget memory = new MemoryBudget(8L << 20);
        Checkpoint small = new Checkpoint("run", "PAUSED", "scenario", "child", "plan", "keys", "policy", "sut", "1",
                Map.of("value", "\ud800\n\u0000"), List.of(), List.of());
        String text = "<&\"\ud800\u0000".repeat(32_768);
        Observation observation = new Observation(new SampleKey(new RunId("run"), new CandidateId("ab".repeat(32)), 0, 0, ""),
                "candidate", Outcome.PASS, Optional.of(new DomainObservation(0, Map.of(), Map.of())), 1, "source", "policy",
                Optional.empty(), "", text, "", false);
        Checkpoint large = new Checkpoint("run", "PAUSED", "scenario", "child", "plan", "keys", "policy", "sut", "1",
                Map.of(), Collections.nCopies(512, observation), List.of());
        Path single = directory.resolve("single.xml");
        CheckpointCodec.write(single, small, memory);
        byte[] good = Files.readAllBytes(single);
        limited(() -> CheckpointCodec.write(single, large, memory));
        require(Arrays.equals(good, Files.readAllBytes(single)), "single last-good file changed");
        require(small.equals(CheckpointCodec.read(single, memory)), "single round trip differs");

        CampaignCheckpoint campaign = new CampaignCheckpoint("scenario", "base", Map.of(), "noop", 513, 0, 0.25, 1,
                Collections.nCopies(512, text), "seed", "", "", List.of(), 0, 513, "child", Optional.of(large), "", "", List.of());
        Path history = directory.resolve("campaign.xml");
        Files.write(history, good);
        limited(() -> CampaignCheckpointCodec.write(history, campaign, memory));
        require(Arrays.equals(good, Files.readAllBytes(history)), "campaign last-good file changed");
        Path oversized = directory.resolve("oversized.xml");
        try (FileChannel channel = FileChannel.open(oversized, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(256L << 20);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
        limited(() -> CheckpointCodec.read(oversized, memory));
        limited(() -> CampaignCheckpointCodec.read(oversized, memory));
        require(memory.reservedBytes() == 0, "codec reservation leaked");

        SourceLocation at = SourceLocation.of("memory-probe");
        List<ValueCell> values = new ArrayList<>();
        for (int i = 1; i <= 32; i++) values.add(ValueCell.of("latency=" + i + "; throughput=" + i + ";", ValueKind.TEXT, at));
        ScenarioSpec scenario = new ScenarioSpec("memory-probe", List.of(new SheetSpec("P", 1, values, at)), ControlSpec.EMPTY,
                true, List.of(new SeqRow(0, List.of(new LocatedText("P", at), new LocatedText("FW_Combi(1)", at)))));
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        for (String setting : List.of("cf.run.id=memory", "cf.execution.sut=inline-metrics", "cf.analysis.minimize=latency",
                "cf.analysis.maximize=throughput", "cf.campaign.iterations=100", "cf.campaign.strategy=noop",
                "cf.memory.budget.bytes=16777216", "cf.pipeline.queue.maxBytes=65536", "cf.compiler.cache.maxBytes=0",
                "cf.results.maxAttachmentBytes=65536", "cf.execution.output.maxBytes=65536")) {
            int equals = setting.indexOf('=');
            overrides.add(new ConfigResolver.Override(setting.substring(0, equals), setting.substring(equals + 1), "probe"));
        }
        Campaign running = Campaign.of(scenario, ConfigResolver.resolve(null, Map.of(), overrides));
        AtomicReference<MemoryBudget> shared = new AtomicReference<>();
        AtomicReference<CampaignCheckpoint> barrier = new AtomicReference<>();
        running.checkpointWithBudgetTo("memory-probe", (checkpoint, budget) -> { shared.set(budget); barrier.set(checkpoint); });
        Campaign.Result result = running.run();
        require(result.status() == Campaign.Status.FAILED && result.iterations().size() > 1 && result.iterations().size() < 100,
                "campaign history was not bounded: " + result.status() + " " + result.reason());
        require(result.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)
                || result.iterations().stream().anyMatch(it -> it.summary().diagnostics().stream()
                .anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)), "campaign lost RESOURCE_LIMIT");
        require(barrier.get() != null && barrier.get().status().isEmpty(), "resumable campaign barrier was lost: " + result.diagnostics());
        require(shared.get().reservedBytes() == 0, "campaign reservation leaked");
        System.out.println("<checkpoint-memory heapBytes=\"" + Runtime.getRuntime().maxMemory()
                + "\" codecBudgetBytes=\"8388608\" campaignBudgetBytes=\"16777216\" iterations=\""
                + result.iterations().size() + "\" status=\"verified\"/>");
    }

    private static void limited(Runnable action) {
        try { action.run(); }
        catch (FrameworkException e) {
            require(e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT), "wrong failure: " + e);
            return;
        }
        throw new AssertionError("oversized checkpoint was admitted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
