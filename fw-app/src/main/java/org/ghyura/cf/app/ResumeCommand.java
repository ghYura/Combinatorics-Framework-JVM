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

import org.ghyura.cf.desktop.session.ScenarioValidation;
import org.ghyura.cf.app.cli.CliOverrides;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.artifacts.CheckpointCodec;
import org.ghyura.cf.artifacts.CampaignCheckpointCodec;
import org.ghyura.cf.campaign.Campaign;
import org.ghyura.cf.campaign.RunHandle;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunSummary;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.results.CampaignCheckpoint;
import org.ghyura.cf.store.MemoryBudget;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code resume --checkpoint FILE} (work package M07, gate A14): the rest of a run from one of its quiescent checkpoints. The
 * checkpoint is refused when it is truncated, altered or of another schema (CHECKPOINT_CORRUPT); its configuration is
 * restored (a setting given now that changes it makes the run another one); the scenario is read again from its path; and
 * once planned the run must have the checkpoint's scenario, plan, key dictionary, policy, SUT and configuration
 * (CHECKPOINT_MISMATCH otherwise, nothing run). The committed observations are restored and only the samples without a final
 * observation run, a cancelled one as its next attempt. Checkpoints go on to cf.checkpoint.file when the run wrote them.
 */
final class ResumeCommand implements CommandHandler {

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = Path.of(command.value("--checkpoint").orElseThrow());
        Path config = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        EffectiveConfig loading = ConfigResolver.resolve(config, context.environment(), CliOverrides.from(command), legacy);
        // An untrusted file cannot enlarge its own decoding allowance through its saved configuration.
        MemoryBudget inputBudget = new MemoryBudget(Math.min(loading.config().memoryBudgetBytes(),
                Math.max(1, Runtime.getRuntime().maxMemory() / 4)));
        CampaignCheckpoint campaignCheckpoint = CampaignCheckpointCodec.isCampaign(file)
                ? CampaignCheckpointCodec.read(file, inputBudget) : null;
        Checkpoint checkpoint = campaignCheckpoint == null ? CheckpointCodec.read(file, inputBudget) : null;
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        (checkpoint == null ? campaignCheckpoint.configuration() : checkpoint.configuration())
                .forEach((k, v) -> overrides.add(new ConfigResolver.Override(k, v, "checkpoint " + file)));
        overrides.addAll(CliOverrides.from(command));
        EffectiveConfig resolved = ConfigResolver.resolve(config, context.environment(), overrides, legacy);
        List<Diagnostic> findings = new ArrayList<>();
        RunSummary summary;
        String scenarioPath = checkpoint == null ? campaignCheckpoint.scenarioPath() : checkpoint.scenarioPath();
        ScenarioValidation.Opened opened = ScenarioValidation.open(Path.of(scenarioPath), resolved);
        EffectiveConfig effective = opened.effective();
        ScenarioLoader.Loaded loaded = opened.loaded();
        try {
            findings.addAll(loaded.diagnostics());
            if (campaignCheckpoint != null) {
                Campaign campaign = Campaign.resume(loaded.spec(), effective, campaignCheckpoint);
                RunCommand.checkpoints(campaign, effective, scenarioPath);
                return RunCommand.campaignReport("resume", command, context, effective, findings, campaign.run());
            }
            try (RunHandle handle = RunService.resume(loaded.spec(), effective, checkpoint)) {
                RunCommand.checkpoints(handle, effective, checkpoint.scenarioPath());
                summary = handle.await();
            }
        } catch (FrameworkException e) {
            List<Diagnostic> all = new ArrayList<>(findings);
            all.addAll(e.diagnostics());
            throw new FrameworkException(all);
        }
        return RunCommand.report("resume", command, context, effective, summary, findings);
    }
}
