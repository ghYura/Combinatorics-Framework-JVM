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

import org.ghyura.cf.config.ConfigKeys;
import org.ghyura.cf.config.ConfigResolver.Override;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the settings-related options of a parsed command into config overrides: {@code --set KEY=VALUE},
 * the shortcuts {@code --workers}, {@code --repeat}, {@code --seed}, {@code --constraints}, {@code run --sut} and {@code run --run-id},
 * and what {@code run --out} and
 * {@code run --checkpoint} imply. Setting one key twice on the command line is an error, never a silent
 * last-wins; two implications that agree are merged.
 */
public final class CliOverrides {

    private CliOverrides() {
    }

    /** @throws FrameworkException USAGE_DUPLICATE_OPTION when one key is set twice with different sources */
    public static List<Override> from(ParsedCommand command) {
        Map<String, Override> byKey = new LinkedHashMap<>();
        List<Diagnostic> problems = new ArrayList<>();

        for (String keyValue : command.values("--set")) {
            int eq = keyValue.indexOf('=');
            explicit(byKey, problems, new Override(keyValue.substring(0, eq), keyValue.substring(eq + 1), "--set"));
        }
        shortcut(command, "--workers", ConfigKeys.EXECUTION_WORKERS.name(), byKey, problems);
        shortcut(command, "--repeat", ConfigKeys.EXECUTION_REPEAT_COUNT.name(), byKey, problems);
        shortcut(command, "--seed", ConfigKeys.RANDOM_SEED.name(), byKey, problems);
        shortcut(command, "--constraints", ConfigKeys.CONSTRAINTS_FILE.name(), byKey, problems);

        if (command.spec().name().equals("run")) {
            shortcut(command, "--sut", ConfigKeys.EXECUTION_SUT.name(), byKey, problems);
            shortcut(command, "--run-id", ConfigKeys.RUN_ID.name(), byKey, problems);
            if (command.value("--out").isPresent()) {
                implied(byKey, problems, new Override(ConfigKeys.ARTIFACTS_ENABLED.name(), "true", "--out"));
            }
            if (command.value("--checkpoint").isPresent()) {
                implied(byKey, problems, new Override(ConfigKeys.ARTIFACTS_ENABLED.name(), "true", "--checkpoint"));
                implied(byKey, problems, new Override(ConfigKeys.CHECKPOINT_ENABLED.name(), "true", "--checkpoint"));
                implied(byKey, problems, new Override(ConfigKeys.CHECKPOINT_FILE.name(), command.value("--checkpoint").get(), "--checkpoint"));
            }
        }
        if (!problems.isEmpty()) {
            throw new FrameworkException(problems);
        }
        return new ArrayList<>(byKey.values());
    }

    private static void shortcut(ParsedCommand command, String option, String key, Map<String, Override> byKey,
                                 List<Diagnostic> problems) {
        command.value(option).ifPresent(value -> explicit(byKey, problems, new Override(key, value, option)));
    }

    private static void explicit(Map<String, Override> byKey, List<Diagnostic> problems, Override override) {
        Override previous = byKey.putIfAbsent(override.key(), override);
        if (previous != null) {
            problems.add(conflict(override.key(), previous.origin(), override.origin()));
        }
    }

    private static void implied(Map<String, Override> byKey, List<Diagnostic> problems, Override override) {
        Override previous = byKey.get(override.key());
        if (previous == null) {
            byKey.put(override.key(), override);
        } else if (!previous.value().equals(override.value())) {
            problems.add(conflict(override.key(), previous.origin(), override.origin()));
        }
    }

    private static Diagnostic conflict(String key, String first, String second) {
        return Diagnostic.error(ErrorCode.USAGE_DUPLICATE_OPTION,
                key + " is set twice on the command line (" + first + ", " + second + ")",
                SourceLocation.of("command line"), "key", key, "first", first, "second", second);
    }
}
