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

import org.ghyura.cf.desktop.session.PlanEstimates;
import org.ghyura.cf.desktop.session.ScenarioValidation;
import org.ghyura.cf.app.cli.CliOverrides;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.input.SeqGraph;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.spi.Cancellation;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code plan}: everything {@code validate --scenario} does, the FW_Seq dependency graph included (written with --graph as
 * DOT or JSON), then every sheet's stages run (work package M02) with the configured generator library and distinct-table
 * switch, and the report shows each final table's exact size with its formula and rows by length, whether it is computed
 * or stored, its first rows and the memory the generation reserved; then the candidate space is assembled and every later
 * count is given with how sure it is, with resource estimates kept apart from the reserved memory ({@link PlanEstimates}).
 * Nothing is executed. Pending legacy settings are reported, not refused: only a run refuses them (decision R-RUN-GATE).
 */
final class PlanCommand implements CommandHandler {

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        EffectiveConfig resolved = ConfigResolver.resolve(file, context.environment(), CliOverrides.from(command), legacy);
        String scenario = command.value("--scenario").orElseThrow();
        BigInteger scan = BigInteger.valueOf(CandidatesCommand.number(command, "--scan", 1_000_000));
        ScenarioValidation.Opened opened = ScenarioValidation.open(Path.of(scenario), resolved);
        EffectiveConfig effective = opened.effective();
        GenerationOptions options = GenerationOptions.of(effective.config());
        List<Diagnostic> findings = new ArrayList<>();
        Path graphFile = command.value("--graph").map(Path::of).orElse(null);
        if (graphFile != null) {
            SeqGraph graph = opened.loaded().graph();
            String text = graphFile.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".dot") ? graph.dot() : graph.json();
            try {
                AtomicFiles.write(graphFile, text.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the dependency graph cannot be written: "
                        + e.getMessage(), SourceLocation.of(graphFile.toString()), "path", graphFile.toString()));
            }
        }
        ScenarioValidation.prepare(scenario, opened.loaded(), effective.config(), (summary, dictionary, budget) -> {
            findings.addAll(summary.findings());
            try (Generation.Result result = Generation.run(summary.plan(), dictionary, options, budget, Cancellation.NONE)) {
                PlanReport plan = PlanReport.of(summary, options, result, dictionary, budget);
                findings.addAll(plan.diagnostics());
                PlanEstimates estimates = PlanEstimates.of(summary, result, dictionary, budget, effective.config(), scan);
                findings.addAll(estimates.diagnostics());
                if (context.format() == OutputFormat.XML) {
                    StringBuilder xml = new StringBuilder(8192);
                    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                    xml.append("<cf-report command=\"plan\" status=\"OK\" exitCode=\"").append(ExitCode.OK.code())
                            .append("\" exitName=\"").append(ExitCode.OK.name()).append("\">\n");
                    effective.appendXml(xml, "  ");
                    summary.appendXml(xml, "  ");
                    plan.appendXml(xml, "  ");
                    estimates.appendXml(xml, "  ");
                    if (!findings.isEmpty()) {
                        xml.append("  <diagnostics>\n");
                        DiagnosticXml.appendAll(xml, "    ", findings);
                        xml.append("  </diagnostics>\n");
                    }
                    xml.append("</cf-report>\n");
                    context.out().print(xml);
                } else {
                    context.out().print(effective.toText());
                    context.out().print(summary.toText());
                    context.out().print(plan.toText());
                    context.out().print(estimates.toText());
                    if (graphFile != null) {
                        context.out().println("  ✓ dependency graph -> " + graphFile + " (" + summary.graph().hash() + ")");
                    }
                }
                return plan;
            }
        });
        if (context.format() != OutputFormat.XML) {
            for (Diagnostic d : findings) {
                if (d.severity() == Severity.WARNING) {
                    context.err().println(d);
                }
            }
        }
        return ExitCode.OK;
    }
}
