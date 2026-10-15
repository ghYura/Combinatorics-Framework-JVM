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
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.desktop.DesktopLauncher;
import org.ghyura.cf.desktop.FlatDesktopLauncher;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.Diagnostic;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code desktop} (M09): the Swing desktop over the session the command line's configuration resolves to; returns when its
 * window is closed. A headless JVM is refused before any Swing class is initialized.
 */
final class DesktopCommand implements CommandHandler {
    private final boolean flatlaf;

    DesktopCommand() {
        this(false);
    }

    DesktopCommand(boolean flatlaf) {
        this.flatlaf = flatlaf;
    }

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        EffectiveConfig effective = ConfigResolver.resolve(file, context.environment(), CliOverrides.from(command), legacy);
        for (Diagnostic d : effective.diagnostics()) {
            context.err().println(d);
        }
        Session session = Session.of(effective);
        Path scenario = command.value("--scenario").map(Path::of).orElse(null);
        if (flatlaf) {
            FlatDesktopLauncher.launch(session, scenario);
        } else {
            DesktopLauncher.launch(session, scenario);
        }
        return ExitCode.OK;
    }
}
