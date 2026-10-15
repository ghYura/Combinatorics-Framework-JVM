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

package org.ghyura.cf.model.spec;

import java.util.List;
import java.util.Objects;

/**
 * What the control sheets other than FW_Seq say, typed. The legacy Core copied each of these into a database
 * table at parse time; here they are plain values owned by the scenario. FW_Info and the older algorithm
 * sheets (FW_Complex, FW_Combi, ...) are recognised by the reader and carry no behaviour, so they are not
 * represented.
 *
 * @param sheetNames     FW_SheetNames rows in sheet order
 * @param runMeFirstOnce the non-blank cells of FW_RunMeFirstOnce in reading order (code that runs once)
 * @param arguments      the non-blank cells of FW_Arguments in reading order
 * @param customVars     FW_CUSTOM_VAR rows in sheet order
 */
public record ControlSpec(List<SheetDecoration> sheetNames, List<LocatedText> runMeFirstOnce,
                          List<LocatedText> arguments, List<CustomVar> customVars) {

    public static final ControlSpec EMPTY = new ControlSpec(List.of(), List.of(), List.of(), List.of());

    public ControlSpec {
        Objects.requireNonNull(sheetNames, "sheetNames");
        Objects.requireNonNull(runMeFirstOnce, "runMeFirstOnce");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(customVars, "customVars");
        sheetNames = List.copyOf(sheetNames);
        runMeFirstOnce = List.copyOf(runMeFirstOnce);
        arguments = List.copyOf(arguments);
        customVars = List.copyOf(customVars);
    }
}
