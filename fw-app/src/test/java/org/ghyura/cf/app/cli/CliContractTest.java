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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CLI syntax is frozen at M00: this build fails when the specs drift from the reviewed XML contract.
 * It is a change detector, not independent evidence of any behavior.
 */
class CliContractTest {

    @Test
    void theSpecsMatchTheFrozenContractFile() throws IOException {
        String frozen;
        try (InputStream in = CliSpec.class.getResourceAsStream("/org/ghyura/cf/app/cli-contract.xml")) {
            assertNotNull(in, "missing cli-contract.xml");
            frozen = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(withoutComments(frozen), withoutComments(CliSpec.toXml()),
                "CliSpec changed: review the change, then regenerate cli-contract.xml");
    }

    /** The architect's eight commands keep their names and order; later packages only add (M03: candidates). */
    @Test
    void commandsAreTheArchitectsEightPlusAddedOnesAndAreUnique() {
        assertEquals(java.util.List.of("run", "validate", "plan", "candidates", "analyze", "resume", "export", "score", "desktop", "guif", "decompose",
                "triage", "doctor", "benchmark", "capabilities"), CliSpec.commandNames());
        assertTrue(CliSpec.commandNames().containsAll(java.util.List.of("run", "validate", "plan", "analyze", "resume", "export",
                "desktop", "capabilities")));
        assertEquals("M03", CliSpec.command("candidates").orElseThrow().owner());
        assertEquals(CliSpec.COMMANDS.size(), new HashSet<>(CliSpec.commandNames()).size());
    }

    @Test
    void noCommandOptionShadowsAGlobalOneAndEveryRequiredOptionExists() {
        Set<String> globals = new HashSet<>();
        for (OptionSpec o : CliSpec.GLOBAL_OPTIONS) {
            assertTrue(globals.add(o.name()), "duplicate global " + o.name());
        }
        for (CommandSpec c : CliSpec.COMMANDS) {
            Set<String> own = new HashSet<>();
            for (OptionSpec o : c.options()) {
                assertFalse(globals.contains(o.name()), c.name() + " shadows global " + o.name());
                assertTrue(own.add(o.name()), c.name() + " declares " + o.name() + " twice");
            }
            for (String required : c.required()) {
                assertTrue(own.contains(required), c.name() + " requires undeclared " + required);
            }
        }
        assertFalse(globals.contains("--help"));
        assertFalse(globals.contains("--version"));
    }

    @Test
    void exitCodesAreDistinctAndFollowTheSigintConvention() {
        Set<Integer> codes = new HashSet<>();
        for (ExitCode e : ExitCode.values()) {
            assertTrue(codes.add(e.code()), e.name());
            assertFalse(e.meaning().isBlank());
        }
        assertEquals(0, ExitCode.OK.code());
        assertEquals(130, ExitCode.INTERRUPTED.code());
    }

    private static String withoutComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->\\s*", "").replace("\r\n", "\n");
    }
}
