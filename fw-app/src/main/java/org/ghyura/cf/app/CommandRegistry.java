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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds every command of the frozen syntax to a handler. Commands whose package has not landed get an
 * {@link UnavailableCommand}, so the syntax is stable from M00 on and behavior arrives package by package.
 */
final class CommandRegistry {

    private final Map<String, CommandHandler> handlers;

    CommandRegistry() {
        Map<String, CommandHandler> map = new LinkedHashMap<>();
        map.put("capabilities", new CapabilitiesCommand());
        map.put("validate", new ValidateCommand());
        map.put("plan", new PlanCommand());
        map.put("candidates", new CandidatesCommand());
        map.put("run", new RunCommand());
        map.put("analyze", new AnalyzeCommand());
        map.put("resume", new ResumeCommand());
        map.put("export", new ExportCommand());
        map.put("score", new ScoreCommand());
        map.put("desktop", new DesktopCommand());
        map.put("guif", new DesktopCommand(true));
        map.put("decompose", new DecomposeCommand());
        map.put("triage", new TriageCommand());
        map.put("doctor", new DoctorCommand());
        map.put("benchmark", new BenchmarkCommand());
        for (CommandSpec spec : CliSpec.COMMANDS) {
            map.putIfAbsent(spec.name(), new UnavailableCommand(spec));
        }
        this.handlers = Collections.unmodifiableMap(map);
    }

    private CommandRegistry(Map<String, CommandHandler> handlers) {
        this.handlers = Collections.unmodifiableMap(new LinkedHashMap<>(handlers));
    }

    /** A copy with one command rebound; lets tests exercise failure paths no real command triggers. */
    CommandRegistry withHandler(String name, CommandHandler handler) {
        Map<String, CommandHandler> copy = new LinkedHashMap<>(handlers);
        copy.put(name, handler);
        return new CommandRegistry(copy);
    }

    CommandHandler handler(String name) {
        CommandHandler handler = handlers.get(name);
        if (handler == null) {
            throw new IllegalStateException("no handler bound for command " + name);
        }
        return handler;
    }

    boolean isAvailable(String name) {
        return !(handler(name) instanceof UnavailableCommand);
    }
}
