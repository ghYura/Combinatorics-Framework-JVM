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


package org.ghyura.cf.generation;

import org.ghyura.cf.model.Diagnostic;

import java.util.List;
import java.util.Optional;

/** The tables of every sheet that has directives, in sheet-key order, and the findings of the generation. */
public record GenerationResult(List<SheetOutcome> sheets, List<Diagnostic> diagnostics) {

    public GenerationResult {
        sheets = List.copyOf(sheets);
        diagnostics = List.copyOf(diagnostics);
    }

    public Optional<SheetOutcome> sheet(String name) {
        return sheets.stream().filter(s -> s.sheet().equals(name)).findFirst();
    }
}
