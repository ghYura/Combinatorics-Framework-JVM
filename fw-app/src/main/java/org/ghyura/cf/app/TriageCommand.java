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

import org.ghyura.cf.analysis.Triage;
import org.ghyura.cf.app.cli.CliOverrides;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.artifacts.CheckpointCodec;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.store.MemoryBudget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code triage --input PATH [--top N] [--json FILE | --write] [--fail-on-findings]} (GEN:diagnostics-reporting-release; the
 * legacy {@code bundle triage}): a finished run's failures as distinct findings, a minimal witness each and the values that
 * explain them. PATH is a checkpoint of this framework or a legacy run directory (or a scratch root holding runs/RUN/, the
 * latest taken). The text report is the legacy one; --json writes the legacy bundle.triage/v1 document and --write puts it
 * as triage.json into the run directory (beside a checkpoint). With --fail-on-findings a run with a finding exits 1
 * (ANALYSIS_FINDINGS) after the report. Nothing is executed.
 */
final class TriageCommand implements CommandHandler {

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        EffectiveConfig effective = ConfigResolver.resolve(file, context.environment(), CliOverrides.from(command), legacy);
        MemoryBudget budget = new MemoryBudget(Math.max(1, Math.min(effective.config().memoryBudgetBytes(), Runtime.getRuntime().maxMemory() / 4)));
        int top = top(command.value("--top").orElse("12"));
        Path input = Path.of(command.value("--input").orElseThrow());
        Triage.Report report;
        Path written;
        if (Files.isRegularFile(input)) {
            report = Triage.ofCheckpoint(CheckpointCodec.read(input), input.toString(), budget);
            written = input.resolveSibling("triage.json");
        } else {
            Path run = Triage.resolveRun(input);
            report = Triage.ofLegacyRun(run, budget);
            written = run.resolve("triage.json");
        }
        Path json = command.value("--json").map(Path::of).orElse(command.flag("--write") ? written : null);
        if (json != null) {
            try {
                AtomicFiles.write(json, Triage.jsonBytes(report));
            } catch (IOException e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the triage report cannot be written: "
                        + e.getMessage(), SourceLocation.of(json.toString()), "path", json.toString()));
            }
        }
        Diagnostic failure = command.flag("--fail-on-findings") && !report.findings().isEmpty()
                ? Diagnostic.error(ErrorCode.ANALYSIS_FINDINGS, report.findings().size() + " distinct finding(s) in this run; " + report.failing()
                + " of " + report.records() + " candidates failed", SourceLocation.of(input.toString()))
                : null;
        if (context.format() == OutputFormat.XML) {
            ExitCode code = failure == null ? ExitCode.OK : ExitCode.FAILED;
            StringBuilder xml = new StringBuilder(4096);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<cf-report command=\"triage\" status=\"").append(failure == null ? "OK" : "FAILED").append("\" exitCode=\"")
                    .append(code.code()).append("\" exitName=\"").append(code.name()).append("\"");
            if (json != null) {
                xml.append(" json=\"").append(Xml.escape(json.toString())).append('"');
            }
            xml.append(">\n");
            xml.append(Triage.xml(report, "  "));
            if (failure != null) {
                xml.append("  <diagnostics>\n");
                DiagnosticXml.appendAll(xml, "    ", List.of(failure));
                xml.append("  </diagnostics>\n");
            }
            xml.append("</cf-report>\n");
            context.out().print(xml);
            return code;
        }
        context.out().println(Triage.text(report, top));
        if (json != null) {
            context.out().println("  ✓ triage report -> " + json);
        }
        if (failure != null) {
            throw new FrameworkException(failure);
        }
        return ExitCode.OK;
    }

    private static int top(String raw) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, "--top needs an integer: " + raw,
                    SourceLocation.of("command line"), "option", "--top"));
        }
    }
}
