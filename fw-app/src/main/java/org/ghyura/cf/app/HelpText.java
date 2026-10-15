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

import org.ghyura.cf.app.cli.CliSpec;
import org.ghyura.cf.app.cli.CommandSpec;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OptionSpec;

import java.util.ArrayList;
import java.util.List;

/** Usage text generated from the frozen specs, so help can never disagree with the parser. */
final class HelpText {

    private HelpText() {
    }

    static String general(CommandRegistry registry) {
        StringBuilder text = new StringBuilder(2048);
        text.append("Usage: ").append(CliSpec.PROGRAM).append(" <command> [options]\n\nCommands:\n");
        for (CommandSpec c : CliSpec.COMMANDS) {
            text.append(String.format("  %-13s %s%n", c.name(), c.summary()));
            if (!registry.isAvailable(c.name())) {
                text.append("                [not available in this build: planned for work package ")
                        .append(c.owner()).append("]\n");
            }
        }
        text.append("\nOptions accepted by every command:\n");
        appendOptions(text, CliSpec.GLOBAL_OPTIONS, List.of());
        text.append(String.format("  %-24s %s%n", "--help, -h", "Show help; after a command, help for that command."));
        text.append(String.format("  %-24s %s%n", "--version", "Print the version and exit."));
        text.append("\nExit codes:\n");
        for (ExitCode e : ExitCode.values()) {
            text.append(String.format("  %-4d %s%n", e.code(), e.meaning()));
        }
        return text.toString();
    }

    static String forCommand(CommandSpec spec, CommandRegistry registry) {
        StringBuilder text = new StringBuilder(1024);
        text.append("Usage: ").append(CliSpec.PROGRAM).append(' ').append(spec.name());
        for (String required : spec.required()) {
            OptionSpec o = spec.options().stream().filter(x -> x.name().equals(required)).findFirst().orElseThrow();
            text.append(' ').append(o.name()).append(' ').append(o.valueName());
        }
        text.append(" [options]\n\n").append(spec.summary()).append('\n');
        if (!registry.isAvailable(spec.name())) {
            text.append("Not available in this build: planned for work package ").append(spec.owner()).append(".\n");
        }
        if (!spec.options().isEmpty()) {
            text.append("\nOptions:\n");
            appendOptions(text, spec.options(), spec.required());
        }
        text.append("\nOptions accepted by every command:\n");
        appendOptions(text, CliSpec.GLOBAL_OPTIONS, List.of());
        return text.toString();
    }

    private static void appendOptions(StringBuilder text, List<OptionSpec> options, List<String> required) {
        for (OptionSpec o : options) {
            List<String> parts = new ArrayList<>();
            parts.add(o.name());
            if (o.takesValue()) {
                parts.add(o.valueName());
            }
            String left = String.join(" ", parts);
            String note = (required.contains(o.name()) ? "(required) " : "") + o.description()
                    + (o.repeatable() ? " May be repeated." : "");
            text.append(String.format("  %-24s %s%n", left, note));
        }
    }
}
