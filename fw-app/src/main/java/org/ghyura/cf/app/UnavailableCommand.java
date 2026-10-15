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

import org.ghyura.cf.app.cli.CommandSpec;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

/**
 * A command whose syntax is frozen but whose behavior is not built yet. It never pretends: it refuses with
 * UNSUPPORTED_CAPABILITY and names the work package that will implement it. Replaced by a real handler when
 * that package lands.
 */
final class UnavailableCommand implements CommandHandler {

    private final CommandSpec spec;

    UnavailableCommand(CommandSpec spec) {
        this.spec = spec;
    }

    String owner() {
        return spec.owner();
    }

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        throw new FrameworkException(Diagnostic.error(ErrorCode.UNSUPPORTED_CAPABILITY,
                "command '" + spec.name() + "' is specified but not implemented in this build (work package "
                        + spec.owner() + ")", SourceLocation.NONE, "command", spec.name(), "workPackage", spec.owner()));
    }
}
