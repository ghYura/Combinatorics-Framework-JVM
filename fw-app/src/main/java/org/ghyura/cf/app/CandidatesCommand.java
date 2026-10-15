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
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.artifacts.HandoffExport;
import org.ghyura.cf.artifacts.LegacyHandoff;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyCounts;
import org.ghyura.cf.generation.Candidate;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.CandidateRenderer;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.CustomVar;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * {@code candidates}: everything {@code plan} does, then the candidates are assembled (work package M03) with the
 * scenario's rules, the optional contract and covering selection as configured, counted and the first ones rendered.
 * Nothing is executed. A plan without candidates is reported with its reason, never as a success of tests. With
 * {@code --export DIR} every executable candidate is also written as a legacy hand-over (C03, {@link HandoffExport}): loose
 * files or, with {@code --transport sharded}, compressed shards, and the bundle.handoff/v2 manifest; only an exact count
 * within {@code --scan} is walked.
 */
final class CandidatesCommand implements CommandHandler {

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        EffectiveConfig resolved = ConfigResolver.resolve(file, context.environment(), CliOverrides.from(command), legacy);
        long limit = number(command, "--limit", 10);
        long scan = number(command, "--scan", 1_000_000);
        String scenario = command.value("--scenario").orElseThrow();
        Path export = command.value("--export").map(Path::of).orElse(null);
        HandoffExport.Transport transport = HandoffExport.Transport.of(command.value("--transport").orElse("loose-files"));
        ScenarioValidation.Opened opened = ScenarioValidation.open(Path.of(scenario), resolved);
        EffectiveConfig effective = opened.effective();
        RunConfig config = effective.config();
        GenerationOptions generation = GenerationOptions.of(config);
        AssemblyOptions assemblyOptions = AssemblyOptions.of(config);
        RenderOptions render = RenderOptions.of(config);
        List<Diagnostic> findings = new ArrayList<>();
        ScenarioValidation.prepare(scenario, opened.loaded(), config, (summary, dictionary, budget) -> {
            findings.addAll(summary.findings());
            try (Generation.Result result = Generation.run(summary.plan(), dictionary, generation, budget, Cancellation.NONE)) {
                findings.addAll(result.result().diagnostics());
                try (Assembly assembly = Assembly.of(summary.plan(), result.result(), dictionary, generation, assemblyOptions, render,
                        config.randomSeed(), budget, Cancellation.NONE);
                     CandidateRenderer renderer = CandidateRenderer.of(assembly, summary.plan(), dictionary, render, budget)) {
                    findings.addAll(assembly.diagnostics());
                    CandidatesReport report = CandidatesReport.of(assembly, renderer, limit, BigInteger.valueOf(scan), Cancellation.NONE);
                    HandoffExport.Written exported = export == null ? null
                            : export(export, transport, summary, assembly, renderer, report.counts(), config, scan, budget);
                    if (context.format() == OutputFormat.XML) {
                        StringBuilder xml = new StringBuilder(8192);
                        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                        xml.append("<cf-report command=\"candidates\" status=\"OK\" exitCode=\"").append(ExitCode.OK.code())
                                .append("\" exitName=\"").append(ExitCode.OK.name()).append("\">\n");
                        effective.appendXml(xml, "  ");
                        summary.appendXml(xml, "  ");
                        report.appendXml(xml, "  ");
                        if (exported != null) {
                            xml.append("  <export transport=\"").append(exported.transport().manifestName()).append("\" manifest=\"")
                                    .append(Xml.escape(exported.manifest().toString())).append("\" candidates=\"").append(exported.candidates())
                                    .append("\" bytes=\"").append(exported.bytes()).append("\" index=\"").append(exported.index())
                                    .append("\" files=\"").append(exported.files()).append("\"/>\n");
                        }
                        xml.append("  <memory budgetBytes=\"").append(budget.totalBytes()).append("\" highWaterBytes=\"").append(budget.highWaterBytes())
                                .append("\"/>\n");
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
                        context.out().print(report.toText());
                        if (exported != null) {
                            context.out().printf(java.util.Locale.ROOT, "  %-13s %d candidates (%d bytes, index %s) as %s, manifest %s%n", "export",
                                    exported.candidates(), exported.bytes(), exported.index(), exported.transport().manifestName(), exported.manifest());
                        }
                        context.out().printf(java.util.Locale.ROOT, "  %-13s budget %d bytes; high water %d%n", "memory", budget.totalBytes(),
                                budget.highWaterBytes());
                    }
                    return report;
                }
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

    /** The executable candidates as a legacy hand-over; their number must be exact and within --scan. */
    private static HandoffExport.Written export(Path directory, HandoffExport.Transport transport, ScenarioValidation.Summary summary,
                                                Assembly assembly, CandidateRenderer renderer, AssemblyCounts counts, RunConfig config,
                                                long scan, MemoryBudget budget) {
        if (!counts.exact() || counts.executable().compareTo(BigInteger.valueOf(scan)) > 0) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, "--export writes every executable candidate, so their "
                    + "number must be counted exactly within --scan " + scan + (counts.exact() ? "; it is " + counts.executable() : "")
                    + "; raise --scan or narrow the scenario", SourceLocation.of(summary.file()), "option", "--scan"));
        }
        ControlSpec controls = summary.spec().controls();
        Map<Integer, String> verdicts = new TreeMap<>();
        for (CustomVar v : controls.customVars()) {
            verdicts.putIfAbsent(v.index(), v.declaration());
        }
        RunConfig.VerdictField verdict = config.executionVerdict();
        // the legacy results insert template: six base columns, and one combos column per fw_final sheet under FW_VAR
        int placeholders = verdict == RunConfig.VerdictField.FW_CUSTOM_VAR ? 6 : 6 + assembly.columnSheets().size();
        HandoffExport.Header header = new HandoffExport.Header(config.runId(), verdict, placeholders, verdicts,
                controls.arguments().stream().map(LocatedText::text).toList(),
                controls.runMeFirstOnce().isEmpty() ? Optional.empty() : Optional.of(controls.runMeFirstOnce().getFirst().text()),
                config.executionSut().equals(RunConfig.PYTHON) ? LegacyHandoff.Language.PYTHON : LegacyHandoff.Language.JAVA);
        Iterator<Candidate> walk = assembly.candidates(Cancellation.NONE);
        Iterator<HandoffExport.Rendered> rendered = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return walk.hasNext();
            }

            @Override
            public HandoffExport.Rendered next() {
                Candidate c = walk.next();
                return new HandoffExport.Rendered(c.display(), renderer.render(c));
            }
        };
        return HandoffExport.write(directory, transport, header, rendered, budget);
    }

    static long number(ParsedCommand command, String option, long fallback) {
        String text = command.value(option).orElse(null);
        if (text == null) {
            return fallback;
        }
        if (!text.matches("[0-9]{1,18}")) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, option + " takes a whole number from 0, got '"
                    + text + "'", SourceLocation.NONE, "option", option));
        }
        return Long.parseLong(text);
    }
}
