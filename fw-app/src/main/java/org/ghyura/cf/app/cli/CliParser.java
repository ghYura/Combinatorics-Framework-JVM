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

package org.ghyura.cf.app.cli;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Suggestions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the frozen syntax: {@code <command> [--option value | --option=value | --flag]...}. No positional
 * arguments exist. Every problem found is reported together, each as a USAGE_* diagnostic located at its
 * argument; {@code --help} and {@code -h} win over any other problem.
 */
public final class CliParser {

    private static final int SUGGESTION_DISTANCE = 2;

    private CliParser() {
    }

    /** @throws FrameworkException with USAGE_* diagnostics when the command line is invalid */
    public static CliRequest parse(String[] args) {
        if (args.length == 0) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_UNKNOWN_COMMAND,
                    "no command given (commands: " + String.join(", ", CliSpec.commandNames()) + ")",
                    SourceLocation.ofPath("command line", "no arguments")));
        }
        String first = args[0];
        if (first.equals("--help") || first.equals("-h")) {
            return new CliRequest.Help(null);
        }
        if (first.equals("--version")) {
            return new CliRequest.Version();
        }
        CommandSpec spec = CliSpec.command(first).orElseThrow(() -> unknownCommand(first));
        return parseCommand(spec, args);
    }

    private static CliRequest parseCommand(CommandSpec spec, String[] args) {
        Map<String, OptionSpec> known = new LinkedHashMap<>();
        for (OptionSpec o : CliSpec.GLOBAL_OPTIONS) {
            known.put(o.name(), o);
        }
        for (OptionSpec o : spec.options()) {
            known.put(o.name(), o);
        }

        List<Diagnostic> problems = new ArrayList<>();
        Map<String, List<String>> values = new LinkedHashMap<>();
        Set<String> flags = new LinkedHashSet<>();
        boolean help = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--help") || arg.equals("-h")) {
                help = true;
                continue;
            }
            if (arg.equals("--")) {
                for (int j = i + 1; j < args.length; j++) {
                    problems.add(unexpected(args[j], j));
                }
                break;
            }
            if (!arg.startsWith("--")) {
                problems.add(arg.length() > 1 && arg.startsWith("-")
                        ? unknownOption(spec, arg, known, i) : unexpected(arg, i));
                continue;
            }
            int eq = arg.indexOf('=');
            String name = eq > 0 ? arg.substring(0, eq) : arg;
            String inline = eq > 0 ? arg.substring(eq + 1) : null;
            OptionSpec option = known.get(name);
            if (option == null) {
                problems.add(unknownOption(spec, name, known, i));
                if (inline == null && i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    i++; // its probable value, so it is not also reported as a stray argument
                }
                continue;
            }
            if (!option.takesValue()) {
                if (inline != null) {
                    problems.add(problem(ErrorCode.USAGE_INVALID_VALUE, name + " is a flag and takes no value", i,
                            "option", name));
                } else if (!flags.add(name)) {
                    problems.add(duplicate(name, i));
                }
                continue;
            }
            String value;
            if (inline != null) {
                value = inline;
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            } else {
                problems.add(problem(ErrorCode.USAGE_MISSING_VALUE, name + " needs a value (" + option.valueName() + ")",
                        i, "option", name));
                continue;
            }
            List<String> list = values.computeIfAbsent(name, k -> new ArrayList<>());
            if (!list.isEmpty() && !option.repeatable()) {
                problems.add(duplicate(name, i));
            } else {
                list.add(value);
            }
        }

        if (help) {
            return new CliRequest.Help(spec.name());
        }
        for (String required : spec.required()) {
            if (!values.containsKey(required)) {
                problems.add(Diagnostic.error(ErrorCode.USAGE_MISSING_OPTION,
                        "command '" + spec.name() + "' requires " + required + " "
                                + known.get(required).valueName(), SourceLocation.ofPath("command line", spec.name()),
                        "option", required, "command", spec.name()));
            }
        }
        checkValues(values, problems);
        if (!problems.isEmpty()) {
            throw new FrameworkException(problems);
        }
        return new CliRequest.Invocation(new ParsedCommand(spec, values, flags));
    }

    private static void checkValues(Map<String, List<String>> values, List<Diagnostic> problems) {
        if (values.containsKey("--coverage-format") != values.containsKey("--coverage-report")) {
            problems.add(problem(ErrorCode.USAGE_MISSING_OPTION, "--coverage-format and --coverage-report must be supplied together", -1));
        }
        for (Map.Entry<String, List<String>> e : values.entrySet()) {
            for (String value : e.getValue()) {
                String name = e.getKey();
                if (name.equals("--set")) {
                    if (value.indexOf('=') <= 0) {
                        problems.add(problem(ErrorCode.USAGE_INVALID_VALUE, "--set expects KEY=VALUE, got '" + value + "'",
                                -1, "option", name, "value", value));
                    }
                } else if (value.isEmpty()) {
                    problems.add(problem(ErrorCode.USAGE_INVALID_VALUE, name + " has an empty value", -1, "option", name));
                } else if (name.equals("--coverage-format") && !List.of("jacoco", "coverage-py", "gcov").contains(value)) {
                    problems.add(problem(ErrorCode.USAGE_INVALID_VALUE, "--coverage-format expects jacoco, coverage-py or gcov", -1));
                } else if (name.equals("--format") && OutputFormat.parse(value).isEmpty()) {
                    problems.add(problem(ErrorCode.USAGE_INVALID_VALUE,
                            "--format expects text or xml, got '" + value + "'", -1, "option", name, "value", value));
                } else if (name.equals("--kind") && !CliSpec.EXPORT_KINDS.contains(value)) {
                    problems.add(problem(ErrorCode.USAGE_INVALID_VALUE, "--kind expects one of "
                            + String.join(", ", CliSpec.EXPORT_KINDS) + ", got '" + value + "'", -1, "option", name,
                            "value", value));
                }
            }
        }
    }

    private static FrameworkException unknownCommand(String name) {
        String suggestion = Suggestions.closest(name, CliSpec.commandNames(), SUGGESTION_DISTANCE);
        String message = "unknown command '" + name + "'" + (suggestion == null ? "" : "; did you mean '" + suggestion + "'?")
                + " (commands: " + String.join(", ", CliSpec.commandNames()) + ")";
        return new FrameworkException(suggestion == null
                ? Diagnostic.error(ErrorCode.USAGE_UNKNOWN_COMMAND, message, SourceLocation.ofPath("command line", "argument 1"),
                "command", name)
                : Diagnostic.error(ErrorCode.USAGE_UNKNOWN_COMMAND, message, SourceLocation.ofPath("command line", "argument 1"),
                "command", name, "suggestion", suggestion));
    }

    private static Diagnostic unknownOption(CommandSpec spec, String name, Map<String, OptionSpec> known, int index) {
        String suggestion = Suggestions.closest(name, known.keySet(), SUGGESTION_DISTANCE);
        String message = "unknown option " + name + " for command '" + spec.name() + "'"
                + (suggestion == null ? "" : "; did you mean " + suggestion + "?");
        SourceLocation where = SourceLocation.ofPath("command line", "argument " + (index + 1));
        return suggestion == null
                ? Diagnostic.error(ErrorCode.USAGE_UNKNOWN_OPTION, message, where, "option", name, "command", spec.name())
                : Diagnostic.error(ErrorCode.USAGE_UNKNOWN_OPTION, message, where, "option", name, "command", spec.name(),
                "suggestion", suggestion);
    }

    private static Diagnostic unexpected(String arg, int index) {
        return problem(ErrorCode.USAGE_UNEXPECTED_ARGUMENT, "unexpected argument '" + arg
                + "' (this command takes options only)", index, "argument", arg);
    }

    private static Diagnostic duplicate(String name, int index) {
        return problem(ErrorCode.USAGE_DUPLICATE_OPTION, name + " was given more than once", index, "option", name);
    }

    private static Diagnostic problem(ErrorCode code, String message, int index, String... attributes) {
        SourceLocation where = index < 0 ? SourceLocation.of("command line")
                : SourceLocation.ofPath("command line", "argument " + (index + 1));
        return Diagnostic.error(code, message, where, attributes);
    }
}
