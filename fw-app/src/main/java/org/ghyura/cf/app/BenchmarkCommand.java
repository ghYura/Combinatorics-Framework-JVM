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

import org.ghyura.cf.app.cli.CliOverrides;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.desktop.session.Benchmark;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code benchmark [--profile 10K|1M|10M|N] [--stages LIST] [--allow-huge] [--out FILE]} (GEN:diagnostics-reporting-release;
 * the legacy bundle bench): the per-stage benchmark of {@link Benchmark}, its table on standard output (or the report as
 * XML) and, with --out, the versioned JSON report. Sizes above 10 000 000 need --allow-huge.
 */
final class BenchmarkCommand implements CommandHandler {

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        List<ConfigResolver.Override> overrides = CliOverrides.from(command);
        String profile = command.value("--profile").orElse("10K");
        List<String> stages = command.value("--stages").map(s -> Arrays.stream(s.split(",")).map(String::strip).filter(x -> !x.isEmpty()).toList())
                .orElse(List.of());
        Benchmark.Report report;
        try {
            report = Benchmark.run(profile, stages, command.flag("--allow-huge"), extra -> {
                List<ConfigResolver.Override> all = new ArrayList<>(overrides);
                all.addAll(extra);
                return ConfigResolver.resolve(file, context.environment(), all, legacy);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path out = command.value("--out").map(Path::of).orElse(null);
        if (out != null) {
            try {
                AtomicFiles.write(out, Benchmark.json(report).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the benchmark report cannot be written: "
                        + e.getMessage(), SourceLocation.of(out.toString()), "path", out.toString()));
            }
        }
        if (context.format() == OutputFormat.XML) {
            StringBuilder xml = new StringBuilder(4096);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<cf-report command=\"benchmark\" status=\"OK\" exitCode=\"0\" exitName=\"OK\"");
            if (out != null) {
                xml.append(" json=\"").append(Xml.escape(out.toString())).append('"');
            }
            xml.append(">\n");
            Benchmark.appendXml(report, xml, "  ");
            xml.append("</cf-report>\n");
            context.out().print(xml);
            return ExitCode.OK;
        }
        context.out().println(Benchmark.table(report));
        if (out != null) {
            context.out().println("  ✓ benchmark -> " + out);
        }
        return ExitCode.OK;
    }
}
