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

import org.ghyura.cf.desktop.session.RunReport;
import org.ghyura.cf.desktop.session.ScenarioValidation;
import org.ghyura.cf.app.cli.CliOverrides;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.artifacts.CheckpointCodec;
import org.ghyura.cf.artifacts.CampaignCheckpointCodec;
import org.ghyura.cf.artifacts.LegacyHandoff;
import org.ghyura.cf.campaign.Campaign;
import org.ghyura.cf.campaign.ImportedCandidates;
import org.ghyura.cf.campaign.RunHandle;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.campaign.RunSummary;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code run} (work package M04): read the scenario, refuse pending legacy settings (R-RUN-GATE), then plan, generate,
 * assemble and execute every executable candidate against the configured system under test, and report the run. With
 * {@code --legacy-handoff} (M05, audit EX01) the candidates come from an imported legacy hand-over instead.
 * Domain failures are results: a run that completes exits 0 however many defects it found.
 */
final class RunCommand implements CommandHandler {

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        boolean scenarioGiven = command.value("--scenario").isPresent();
        boolean handoffGiven = command.value("--legacy-handoff").isPresent();
        if (scenarioGiven == handoffGiven) {
            throw new FrameworkException(Diagnostic.error(scenarioGiven ? ErrorCode.USAGE_UNEXPECTED_ARGUMENT : ErrorCode.USAGE_MISSING_OPTION,
                    "run needs exactly one of --scenario FILE and --legacy-handoff FILE", SourceLocation.of("command line")));
        }
        Path file = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        LegacyHandoff.Bundle bundle = handoffGiven ? LegacyHandoff.read(Path.of(command.value("--legacy-handoff").get())) : null;
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        if (bundle != null) {
            // the manifest describes its candidates: its run id and verdict mode apply, below explicit command-line settings
            String origin = "legacy hand-over " + bundle.manifest();
            overrides.add(new ConfigResolver.Override("cf.run.id", bundle.runId(), origin));
            overrides.add(new ConfigResolver.Override("cf.execution.verdict", bundle.verdict().token(), origin));
        }
        overrides.addAll(CliOverrides.from(command));
        EffectiveConfig resolved = ConfigResolver.resolve(file, context.environment(), overrides, legacy);
        if (bundle != null && (bundle.language() == LegacyHandoff.Language.PYTHON) != resolved.config().executionSut().equals(RunConfig.PYTHON)) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT, "the legacy hand-over declares "
                    + bundle.language().token() + " candidates; " + (bundle.language() == LegacyHandoff.Language.PYTHON
                    ? "select cf.execution.sut=python and install the Python add-on explicitly"
                    : "select a Java runner instead of cf.execution.sut=python"), SourceLocation.of(bundle.manifest().toString())));
        }
        if (bundle != null && bundle.container().isPresent()) {
            String refusal = LegacyHandoff.containerRefusal(bundle.container().get(), resolved.config().pythonSandbox());
            if (refusal != null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.UNSUPPORTED_CAPABILITY, "legacy hand-over " + bundle.manifest() + ": "
                        + refusal, SourceLocation.of(bundle.manifest().toString()), "key", "cf.python.sandbox"));
            }
        }
        List<Diagnostic> findings = new ArrayList<>();
        // the scenario is read once, and its own choices (an fwgen spec's goals and coverage) join the configuration
        ScenarioValidation.Opened opened = scenarioGiven ? ScenarioValidation.open(Path.of(command.value("--scenario").get()), resolved) : null;
        EffectiveConfig effective = opened == null ? resolved : opened.effective();
        if (opened != null) {
            findings.addAll(opened.loaded().diagnostics());
        }
        if (Campaign.wanted(effective.config())) {
            if (bundle != null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT, "a campaign (cf.campaign."
                        + "iterations above 1 or cf.campaign.seed.input) derives each iteration from a scenario; a legacy hand-over has "
                        + "none, so nothing was run", SourceLocation.of("command line"), "key", "cf.campaign.iterations"));
            }
            return campaign(command, context, effective, findings, opened.loaded());
        }
        RunSummary summary;
        try (RunHandle handle = bundle != null
                ? RunService.prepare(new ImportedCandidates(bundle.manifest().toString(), bundle.digest(), bundle.candidates(),
                        bundle.verdictNames(), bundle.arguments(), bundle.runFirstOnce(), bundle.diagnostics()), effective)
                : RunService.prepare(opened.loaded().spec(), effective)) {
            checkpoints(handle, effective, command.value("--scenario").orElse(""));
            summary = handle.await();
        } catch (FrameworkException e) {
            List<Diagnostic> all = new ArrayList<>(findings);
            all.addAll(e.diagnostics());
            throw new FrameworkException(all);
        }
        return report("run", command, context, effective, summary, findings);
    }

    /** With cf.checkpoint.file set, the run writes its quiescent checkpoints there (fw-artifacts CheckpointCodec). */
    static void checkpoints(RunHandle handle, EffectiveConfig effective, String scenarioPath) {
        String file = effective.config().checkpointFile();
        if (!file.isEmpty()) {
            Path target = Path.of(file);
            handle.checkpointWithBudgetTo(Path.of(scenarioPath).toAbsolutePath().toString(), (c, budget) -> CheckpointCodec.write(target, c, budget));
        }
    }

    /** The run's report on standard output (and with --out in a file), its findings on standard error; returns the exit code. */
    static ExitCode report(String name, ParsedCommand command, CliContext context, EffectiveConfig effective, RunSummary summary,
                           List<Diagnostic> findings) {
        findings.addAll(summary.diagnostics());
        ExitCode code = switch (summary.state()) {
            case COMPLETED -> ExitCode.OK;
            case CANCELLED -> ExitCode.CANCELLED;
            default -> ExitCode.forFailure(findings);
        };
        if (code == ExitCode.OK) {
            // an analysis that failed, or a seed that could not be written, fails the command, not the run's results
            code = failedCommand(findings);
        }
        String xml = xml(name, effective, summary, findings, code);
        if (command.value("--out").isPresent()) {
            Path out = Path.of(command.value("--out").get());
            try {
                Files.writeString(out, xml, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                // an export failure is reported apart: the committed observations and the summary above are unchanged
                Diagnostic failed = Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the run finished as " + summary.state()
                        + " with the results below, but its report could not be written to " + out + ": " + e.getMessage(),
                        SourceLocation.of(out.toString()), "path", out.toString());
                findings.add(failed);
                code = code == ExitCode.OK ? ExitCode.forFailure(List.of(failed)) : code;
                xml = xml(name, effective, summary, findings, code);
            }
        }
        if (context.format() == OutputFormat.XML) {
            context.out().print(xml);
        } else {
            context.out().print(effective.toText());
            context.out().print(RunReport.toText(summary));
            for (Diagnostic d : findings) {
                if (d.severity() != Severity.INFO) {
                    context.err().println(d);
                }
            }
        }
        return code;
    }

    /** A campaign of iterations (M07): the lineage, then every iteration's run report; the last iteration decides the exit code. */
    private static ExitCode campaign(ParsedCommand command, CliContext context, EffectiveConfig effective, List<Diagnostic> findings,
                                     ScenarioLoader.Loaded loaded) {
        Campaign.Result result;
        try {
            Campaign campaign = Campaign.of(loaded.spec(), effective);
            checkpoints(campaign, effective, command.value("--scenario").orElseThrow());
            result = campaign.run();
        } catch (FrameworkException e) {
            List<Diagnostic> all = new ArrayList<>(findings);
            all.addAll(e.diagnostics());
            throw new FrameworkException(all);
        }
        return campaignReport("run", command, context, effective, findings, result);
    }

    static void checkpoints(Campaign campaign, EffectiveConfig effective, String scenarioPath) {
        String file = effective.config().checkpointFile();
        if (!file.isEmpty()) campaign.checkpointWithBudgetTo(Path.of(scenarioPath).toAbsolutePath().toString(),
                (c, budget) -> CampaignCheckpointCodec.write(Path.of(file), c, budget));
    }

    static ExitCode campaignReport(String name, ParsedCommand command, CliContext context, EffectiveConfig effective,
                                   List<Diagnostic> findings, Campaign.Result result) {
        for (Campaign.Iteration it : result.iterations()) {
            findings.addAll(it.summary().diagnostics());
        }
        findings.addAll(result.diagnostics());
        ExitCode code = switch (result.status()) {
            case COMPLETED, STABLE_FRONT, CONVERGED_NO_SIGNAL, EXHAUSTED -> failedCommand(findings);
            case CANCELLED -> ExitCode.CANCELLED;
            case FAILED -> ExitCode.forFailure(findings);
        };
        StringBuilder xml = new StringBuilder(16384);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<cf-report command=\"").append(name).append("\" status=\"").append(reportStatus(code))
                .append("\" exitCode=\"").append(code.code()).append("\" exitName=\"").append(code.name()).append("\">\n");
        effective.appendXml(xml, "  ");
        CampaignReport.appendXml(xml, "  ", result);
        if (!findings.isEmpty()) {
            xml.append("  <diagnostics>\n");
            DiagnosticXml.appendAll(xml, "    ", findings);
            xml.append("  </diagnostics>\n");
        }
        xml.append("</cf-report>\n");
        if (command.value("--out").isPresent()) {
            Path out = Path.of(command.value("--out").get());
            try {
                Files.writeString(out, xml, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                Diagnostic failed = Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the campaign ended " + result.status()
                        + ", but its report could not be written to " + out + ": " + e.getMessage(), SourceLocation.of(out.toString()),
                        "path", out.toString());
                findings.add(failed);
                code = code == ExitCode.OK ? ExitCode.forFailure(List.of(failed)) : code;
            }
        }
        if (context.format() == OutputFormat.XML) {
            context.out().print(xml);
        } else {
            context.out().print(effective.toText());
            context.out().print(CampaignReport.toText(result));
            for (Diagnostic d : findings) {
                if (d.severity() != Severity.INFO) {
                    context.err().println(d);
                }
            }
        }
        return code;
    }

    /**
     * OK unless an error the run's results do not contain fails the command: an analysis that failed (formal mode without
     * eligible samples) or an export that could not be written.
     */
    private static ExitCode failedCommand(List<Diagnostic> findings) {
        List<Diagnostic> errors = findings.stream().filter(d -> d.severity().isError() && (d.code().category() == ErrorCode.Category.ANALYSIS
                || d.code() == ErrorCode.RESULT_EXPORT_FAILED || d.code() == ErrorCode.CHECKPOINT_WRITE_FAILED
                || d.code() == ErrorCode.RUN_RECONCILIATION_FAILED || d.code() == ErrorCode.RUN_ERROR_BUDGET_EXHAUSTED)).toList();
        return errors.isEmpty() ? ExitCode.OK : ExitCode.forFailure(errors);
    }

    private static String reportStatus(ExitCode code) {
        return code == ExitCode.OK ? "OK" : code == ExitCode.CANCELLED ? "CANCELLED" : "FAILED";
    }

    private static String xml(String name, EffectiveConfig effective, RunSummary summary, List<Diagnostic> findings, ExitCode code) {
        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<cf-report command=\"").append(name).append("\" status=\"").append(reportStatus(code))
                .append("\" exitCode=\"").append(code.code()).append("\" exitName=\"").append(code.name()).append("\">\n");
        effective.appendXml(xml, "  ");
        RunReport.appendXml(xml, "  ", summary);
        if (!findings.isEmpty()) {
            xml.append("  <diagnostics>\n");
            DiagnosticXml.appendAll(xml, "    ", findings);
            xml.append("  </diagnostics>\n");
        }
        xml.append("</cf-report>\n");
        return xml.toString();
    }
}
