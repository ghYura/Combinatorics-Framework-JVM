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

import org.ghyura.cf.config.ConfigResolver.Override;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliOverridesTest {

    private static List<Override> overrides(String... args) {
        ParsedCommand command = ((CliRequest.Invocation) CliParser.parse(args)).command();
        return CliOverrides.from(command);
    }

    @Test
    void setSplitsAtTheFirstEqualsSignAndKeepsTheRestAsTheValue() {
        assertEquals(List.of(new Override("cf.execution.workers", "8", "--set"),
                        new Override("cf.keys.encoding", "A=B", "--set")),
                overrides("validate", "--set", "cf.execution.workers=8", "--set", "cf.keys.encoding=A=B"));
    }

    @Test
    void shortcutsMapToTheirKeysAndNameThemselvesAsTheOrigin() {
        assertEquals(List.of(
                        new Override("cf.execution.workers", "8", "--workers"),
                        new Override("cf.execution.repeat.count", "3", "--repeat"),
                        new Override("cf.random.seed", "-7", "--seed")),
                overrides("validate", "--seed", "-7", "--workers", "8", "--repeat", "3"));
    }

    @Test
    void settingOneKeyTwiceOnTheCommandLineIsAnErrorWhicheverFormsAreMixed() {
        for (String[] args : new String[][]{
                {"validate", "--set", "cf.execution.workers=2", "--workers", "8"},
                {"validate", "--set", "cf.random.seed=1", "--set", "cf.random.seed=2"},
                {"validate", "--set", "cf.random.seed=1", "--set", "cf.random.seed=1"}}) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> overrides(args));
            assertEquals(ErrorCode.USAGE_DUPLICATE_OPTION, e.code(), String.join(" ", args));
        }
    }

    @Test
    void runImpliesArtifactsForItsOutputAndCheckpointOptions() {
        assertEquals(List.of(new Override("cf.artifacts.enabled", "true", "--out")),
                overrides("run", "--scenario", "s.json", "--out", "report.xml"));
        assertEquals(List.of(new Override("cf.artifacts.enabled", "true", "--checkpoint"),
                        new Override("cf.checkpoint.enabled", "true", "--checkpoint"), new Override("cf.checkpoint.file", "cp.bin", "--checkpoint")),
                overrides("run", "--scenario", "s.json", "--checkpoint", "cp.bin"));
    }

    @Test
    void agreeingImplicationsMergeAndContradictingOnesFail() {
        assertEquals(List.of(new Override("cf.artifacts.enabled", "true", "--out"),
                        new Override("cf.checkpoint.enabled", "true", "--checkpoint"), new Override("cf.checkpoint.file", "cp.bin", "--checkpoint")),
                overrides("run", "--scenario", "s.json", "--out", "r.xml", "--checkpoint", "cp.bin"));
        FrameworkException e = assertThrows(FrameworkException.class,
                () -> overrides("run", "--scenario", "s.json", "--checkpoint", "cp.bin", "--set", "cf.artifacts.enabled=false"));
        assertEquals(ErrorCode.USAGE_DUPLICATE_OPTION, e.code());
        assertEquals("cf.artifacts.enabled", e.primary().attributes().get("key"));
    }

    @Test
    void otherCommandsDoNotImplyAnything() {
        assertEquals(List.of(), overrides("validate"));
        assertEquals(List.of(), overrides("capabilities"));
        assertEquals(List.of(), overrides("resume", "--checkpoint", "cp.bin", "--out", "r.xml"));
    }
}
