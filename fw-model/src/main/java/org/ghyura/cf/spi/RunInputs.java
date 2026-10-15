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

package org.ghyura.cf.spi;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What a run tells its plugin once, before the first sample ({@link SutPlugin#forRun}): the scenario's legacy run inputs
 * and the plugin's own settings. A typed Java plugin needs none of them; a plugin that runs each candidate's rendered
 * source in another language, as the Python add-on does, needs them the way the legacy executor read them from its
 * hand-over manifest.
 *
 * @param arguments         the FW_Arguments cells, in reading order
 * @param runMeFirstOnce    the FW_RunMeFirstOnce source, when the scenario has one
 * @param verdictVariable   the variable that holds the verdict, {@code FW_VAR} or {@code FW_CUSTOM_VAR}
 *                          ({@code cf.execution.verdict})
 * @param verdictNames      the FW_CUSTOM_VAR sheet's names of verdict codes
 * @param scenarioDirectory the directory of the scenario file, when the run read one from a file
 * @param settings          the plugin's own settings by {@code cf.*} key, as resolved for the run
 */
public record RunInputs(List<String> arguments, Optional<String> runMeFirstOnce, String verdictVariable, Map<Integer, String> verdictNames,
                        Optional<Path> scenarioDirectory, Map<String, String> settings) {

    public RunInputs {
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(runMeFirstOnce, "runMeFirstOnce");
        Objects.requireNonNull(verdictVariable, "verdictVariable");
        verdictNames = Map.copyOf(new TreeMap<>(verdictNames));
        Objects.requireNonNull(scenarioDirectory, "scenarioDirectory");
        settings = Map.copyOf(new TreeMap<>(settings));
    }
}
