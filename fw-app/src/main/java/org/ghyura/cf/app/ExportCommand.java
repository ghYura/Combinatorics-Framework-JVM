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

import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.CliOverrides;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.artifacts.CheckpointCodec;
import org.ghyura.cf.artifacts.ResultsExport;
import org.ghyura.cf.desktop.session.CorpusExport;
import org.ghyura.cf.desktop.session.RunReportPage;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@code export --input CHECKPOINT --output FILE [--kind report|results|csv|html|metrics]} (work package M07): a checkpoint's committed
 * observations as a summary report (XML), as the legacy results_v2 rows (CSV, one row per attempt), as a metrics table of
 * the final samples (CSV), as the self-contained page of the run (HTML, the legacy bundle report; M09) or as the metrics
 * corpus {@code analyze --results} reads (the desktop's metrics export, so a batch run reaches the same analysis). The input is refused when it is not an intact checkpoint (CHECKPOINT_CORRUPT); a failed write is
 * RESULT_EXPORT_FAILED and leaves the previous output file as it was.
 */
final class ExportCommand implements CommandHandler {

    @Override
    @SuppressWarnings("try")
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path input = Path.of(command.value("--input").orElseThrow());
        Path output = Path.of(command.value("--output").orElseThrow());
        String kind = command.value("--kind").orElse("report");
        var legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        var effective = ConfigResolver.resolve(command.value("--config").map(Path::of).orElse(null), context.environment(),
                CliOverrides.from(command), legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment()));
        MemoryBudget budget = new MemoryBudget(Math.max(1, Math.min(effective.config().memoryBudgetBytes(), Runtime.getRuntime().maxMemory() / 4)));
        Checkpoint checkpoint = CheckpointCodec.read(input, budget);
        try (MemoryBudget.Lease retained = budget.reserve(MemorySizes.retained(checkpoint), "checkpoint retained during export")) {
            byte[] bytes = kind.equals("html") ? RunReportPage.encode(checkpoint, input.toString(), budget)
                    : kind.equals("metrics") ? CorpusExport.encode(checkpoint, budget)
                    : ResultsExport.encode(checkpoint, kind, budget);
            AtomicFiles.write(output, bytes);
        } catch (IOException | RuntimeException e) {
            if (e instanceof FrameworkException failure) throw failure;
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the " + kind + " export of run '"
                    + checkpoint.runId() + "' could not be written to " + output + ": " + e.getMessage() + "; the checkpoint is unchanged",
                    SourceLocation.of(output.toString()), "path", output.toString()));
        }
        context.out().println("exported " + kind + " of run " + checkpoint.runId() + " (" + checkpoint.observations().size()
                + " committed attempts) to " + output);
        return ExitCode.OK;
    }
}
