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
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.Severity;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * {@code validate}: resolves the configuration and prints the effective settings with their origins. With
 * {@code --scenario} the scenario is also read, interpreted, planned and given its key dictionary; the report
 * says what was found, and any error ends the command with the validation exit code and every finding.
 * Nothing is executed.
 */
final class ValidateCommand implements CommandHandler {

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        EffectiveConfig effective = ConfigResolver.resolve(file, context.environment(), CliOverrides.from(command), legacy);
        Optional<String> scenario = command.value("--scenario");
        if (scenario.isEmpty()) {
            context.out().print(context.format() == OutputFormat.XML ? effective.toXml() : effective.toText());
            return ExitCode.OK;
        }
        ScenarioValidation.Opened opened = ScenarioValidation.open(java.nio.file.Path.of(scenario.get()), effective);
        effective = opened.effective();
        ScenarioValidation.Summary summary = ScenarioValidation.prepare(scenario.get(), opened.loaded(), effective.config(),
                (s, dictionary, budget) -> s);
        if (context.format() == OutputFormat.XML) {
            StringBuilder xml = new StringBuilder(4096);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<cf-report command=\"validate\" status=\"OK\" exitCode=\"").append(ExitCode.OK.code())
                    .append("\" exitName=\"").append(ExitCode.OK.name()).append("\">\n");
            effective.appendXml(xml, "  ");
            summary.appendXml(xml, "  ");
            if (!summary.findings().isEmpty()) {
                xml.append("  <diagnostics>\n");
                DiagnosticXml.appendAll(xml, "    ", summary.findings());
                xml.append("  </diagnostics>\n");
            }
            xml.append("</cf-report>\n");
            context.out().print(xml);
        } else {
            context.out().print(effective.toText());
            context.out().print(summary.toText());
            for (Diagnostic d : summary.findings()) {
                if (d.severity() == Severity.WARNING) {
                    context.err().println(d);
                }
            }
        }
        return ExitCode.OK;
    }
}
