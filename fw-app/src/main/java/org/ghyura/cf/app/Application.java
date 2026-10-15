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

import org.ghyura.cf.app.cli.CliParser;
import org.ghyura.cf.app.cli.CliRequest;
import org.ghyura.cf.app.cli.CliSpec;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The whole command-line program as a function: arguments, environment and two streams in, exit code out.
 * {@link Main} is the only caller that touches the process. Nothing here reads global state, so every
 * behavior is testable in-process.
 */
public final class Application {

    private final CommandRegistry registry;

    private Application(CommandRegistry registry) {
        this.registry = registry;
    }

    public static int run(String[] args, Map<String, String> environment, PrintStream out, PrintStream err) {
        return run(new CommandRegistry(), args, environment, out, err);
    }

    static int run(CommandRegistry registry, String[] args, Map<String, String> environment, PrintStream out,
                   PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        return new Application(registry).execute(args, environment, out, err).code();
    }

    private ExitCode execute(String[] args, Map<String, String> environment, PrintStream out, PrintStream err) {
        CliRequest request;
        try {
            request = CliParser.parse(args);
        } catch (FrameworkException e) {
            for (Diagnostic d : e.diagnostics()) {
                err.println(d);
            }
            err.println("Run '" + CliSpec.PROGRAM + " --help' for usage.");
            return ExitCode.USAGE;
        }
        switch (request) {
            case CliRequest.Help help -> {
                out.print(help.command() == null ? HelpText.general(registry)
                        : HelpText.forCommand(CliSpec.command(help.command()).orElseThrow(), registry));
                return ExitCode.OK;
            }
            case CliRequest.Version ignored -> {
                out.println(Versions.banner());
                return ExitCode.OK;
            }
            case CliRequest.Invocation invocation -> {
                return invoke(invocation.command(), environment, out, err);
            }
        }
    }

    private ExitCode invoke(ParsedCommand command, Map<String, String> environment, PrintStream out, PrintStream err) {
        CliContext context = new CliContext(environment, out, err, command.format(), command.flag("--debug"));
        try {
            return registry.handler(command.spec().name()).execute(command, context);
        } catch (FrameworkException e) {
            return report(command.spec().name(), e.diagnostics(), context);
        } catch (RuntimeException e) {
            if (context.debug()) {
                e.printStackTrace(err);
            }
            Diagnostic unexpected = Diagnostic.error(ErrorCode.INTERNAL_ERROR,
                    "unexpected " + e.getClass().getName() + (e.getMessage() == null ? "" : ": " + e.getMessage()),
                    SourceLocation.NONE, "command", command.spec().name());
            return report(command.spec().name(), List.of(unexpected), context);
        }
    }

    private static ExitCode report(String command, List<Diagnostic> diagnostics, CliContext context) {
        ExitCode code = ExitCode.forFailure(diagnostics);
        if (context.format() == OutputFormat.XML) {
            StringBuilder xml = new StringBuilder(1024);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<cf-report command=\"").append(command).append("\" status=\"FAILED\" exitCode=\"")
                    .append(code.code()).append("\" exitName=\"").append(code.name()).append("\">\n");
            xml.append("  <diagnostics>\n");
            DiagnosticXml.appendAll(xml, "    ", diagnostics);
            xml.append("  </diagnostics>\n");
            xml.append("</cf-report>\n");
            context.out().print(xml);
        } else {
            for (Diagnostic d : diagnostics) {
                context.err().println(d);
            }
        }
        return code;
    }
}
