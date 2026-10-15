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

import org.ghyura.cf.analysis.Analysis;
import org.ghyura.cf.analysis.AnalysisReport;
import org.ghyura.cf.analysis.CoverageImport;
import org.ghyura.cf.analysis.MetricCorpus;
import org.ghyura.cf.app.cli.CliOverrides;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.store.MemoryBudget;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Metric corpus analysis with optional import-only coverage, through the shared budgeted ingestion service. */
final class AnalyzeCommand implements CommandHandler {
    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        EffectiveConfig effective = ConfigResolver.resolve(file, context.environment(), CliOverrides.from(command), legacy);
        Path results = Path.of(command.value("--results").orElseThrow());
        CoverageImport.Source coverage = null;
        if (command.value("--coverage-format").isPresent()) {
            try {
                coverage = new CoverageImport.Source(CoverageImport.Format.parse(command.value("--coverage-format").orElseThrow()),
                        command.value("--coverage-report").orElseThrow());
            } catch (IllegalArgumentException e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, e.getMessage(), SourceLocation.of("command line")));
            }
        }
        MemoryBudget budget = new MemoryBudget(Math.max(1, Math.min(effective.config().memoryBudgetBytes(), Runtime.getRuntime().maxMemory() / 4)));
        try (MetricCorpus corpus = MetricCorpus.read(results, coverage, budget)) {
            AnalysisReport report = Analysis.analyze(corpus.samples(), Analysis.Options.of(effective.config()), budget);
            List<Diagnostic> findings = new ArrayList<>(effective.diagnostics());
            findings.addAll(corpus.diagnostics());
            if (context.format() == OutputFormat.XML) {
                StringBuilder xml = new StringBuilder(4096);
                xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                xml.append("<cf-report command=\"analyze\" status=\"OK\" exitCode=\"0\" exitName=\"OK\">\n");
                effective.appendXml(xml, "  ");
                xml.append("  <results path=\"").append(Xml.escape(results.toString())).append("\" samples=\"").append(corpus.samples().size())
                        .append("\" missingVerdicts=\"").append(corpus.missingVerdicts()).append("\"/>\n");
                if (coverage != null) appendCoverage(xml, corpus);
                report.appendXml(xml, "  ");
                if (!findings.isEmpty()) {
                    xml.append("  <diagnostics>\n");
                    DiagnosticXml.appendAll(xml, "    ", findings);
                    xml.append("  </diagnostics>\n");
                }
                xml.append("</cf-report>\n");
                context.out().print(xml);
            } else {
                context.out().print("\nresults: " + results + " (" + corpus.samples().size() + " samples)\n");
                context.out().print(report.toText());
                if (coverage != null) context.out().println("coverage corpus sha256=" + corpus.sha256());
                for (MetricCorpus.Imported entry : corpus.imports()) {
                    CoverageImport.Report imported = entry.report();
                    context.out().println("coverage line " + entry.line() + ": " + imported.format().token() + " " + imported.path()
                            + " " + imported.status() + " sha256=" + imported.sha256() + " " + imported.metrics());
                }
                for (Diagnostic d : findings) context.err().println(d);
            }
        }
        return ExitCode.OK;
    }

    private static void appendCoverage(StringBuilder xml, MetricCorpus corpus) {
        xml.append("  <coverage-imports reports=\"").append(corpus.imports().size()).append("\" corpusSha256=\"")
                .append(corpus.sha256()).append("\">\n");
        for (MetricCorpus.Imported entry : corpus.imports()) {
            CoverageImport.Report report = entry.report();
            xml.append("    <import line=\"").append(entry.line()).append("\" candidate=\"").append(Xml.escape(entry.candidateId()))
                    .append("\" repeat=\"").append(entry.repeat()).append("\" environment=\"").append(Xml.escape(entry.environment()))
                    .append("\" format=\"").append(report.format().token()).append("\" path=\"").append(Xml.escape(report.path().toString()))
                    .append("\" status=\"").append(report.status()).append("\" sha256=\"").append(report.sha256()).append("\">\n");
            report.metrics().forEach((key, value) -> xml.append("      <metric key=\"").append(key).append("\" value=\"")
                    .append(value).append("\"/>\n"));
            xml.append("    </import>\n");
        }
        xml.append("  </coverage-imports>\n");
    }
}
