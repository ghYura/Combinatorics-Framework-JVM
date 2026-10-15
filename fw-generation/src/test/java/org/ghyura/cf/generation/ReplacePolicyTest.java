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

import org.ghyura.cf.algebra.LegacyLibrary;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The legacy core.replace.* policies (SheetWorker), as cf.generation.replace.*. */
class ReplacePolicyTest {

    private static GenerationOptions with(RunConfig.ReplacePattern pattern, RunConfig.ReplaceUnmatched unmatched,
                                          RunConfig.ReplaceUnparseable unparseable, RunConfig.ReplaceIdentity identity,
                                          RunConfig.ReplaceDiagnostics diagnostics) {
        return new GenerationOptions(LegacyLibrary.PARALLEL, false,
                new GenerationOptions.ReplacePolicies(pattern, unmatched, unparseable, identity, diagnostics));
    }

    /** FW_Group with its replacements written in its own cell, as the legacy parser required. */
    private static Scenario grouped(String... replacements) {
        String group = "FW_Group " + String.join(" ", replacements);
        return Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(1)", group, "FW_Combi(2)").options(Scenario.UNPROMOTED);
    }

    private static List<Diagnostic> diagnostics(Scenario s, GenerationOptions options) {
        try (Scenario.Run run = s.run(options)) {
            return run.result().result().diagnostics();
        }
    }

    @Test
    void theDefaultsAreTheHistoricalBehaviour() {
        GenerationOptions legacy = GenerationOptions.defaults();
        assertEquals(GenerationOptions.ReplacePolicies.LEGACY, legacy.replace());
        assertTrue(diagnostics(grouped("FW_ReplaceRE(\"abc\", \"x\")", "FW_ReplaceRE(\"9\", \"9\")"), legacy).isEmpty(),
                "permissive, ignore, allow, off: nothing is reported");
    }

    @Test
    void aPatternThatCanOnlyBeANoOpIsReportedOrRefused() {
        Scenario s = grouped("FW_ReplaceRE(\"abc\", \"x\")", "FW_ReplaceRE(\"\\\\d+\", \"$0\")", "FW_ReplaceRE(\"-1\", \"1\")");
        List<Diagnostic> warned = diagnostics(s, with(RunConfig.ReplacePattern.WARN, RunConfig.ReplaceUnmatched.IGNORE,
                RunConfig.ReplaceUnparseable.WARN, RunConfig.ReplaceIdentity.ALLOW, RunConfig.ReplaceDiagnostics.OFF));
        assertEquals(List.of(ErrorCode.GEN_REPLACEMENT_PATTERN), warned.stream().map(Diagnostic::code).toList(),
                "only abc: \\\\d+ is a class and -1 has digits once the regular-expression structure is stripped");
        assertEquals("abc", warned.getFirst().attributes().get("pattern"));
        FrameworkException strict = assertThrows(FrameworkException.class, () -> diagnostics(s, with(RunConfig.ReplacePattern.STRICT,
                RunConfig.ReplaceUnmatched.IGNORE, RunConfig.ReplaceUnparseable.WARN, RunConfig.ReplaceIdentity.ALLOW,
                RunConfig.ReplaceDiagnostics.OFF)));
        assertEquals(ErrorCode.GEN_REPLACEMENT_PATTERN, strict.diagnostics().getFirst().code());
        assertTrue(GenerationOptions.class.getName().length() > 0 && SheetStages.canMatchCodeString("^\\[|\\]$"),
                "structural patterns stay acceptable");
    }

    @Test
    void unmatchedPatternsAreReportedOrFailAndIdentitiesAreReported() {
        Scenario s = grouped("FW_ReplaceRE(\"777\", \"1\")", "FW_ReplaceRE(\"5\", \"5\")");
        List<Diagnostic> warned = diagnostics(s, with(RunConfig.ReplacePattern.PERMISSIVE, RunConfig.ReplaceUnmatched.WARN,
                RunConfig.ReplaceUnparseable.WARN, RunConfig.ReplaceIdentity.WARN, RunConfig.ReplaceDiagnostics.SUMMARY));
        List<ErrorCode> codes = warned.stream().map(Diagnostic::code).toList();
        assertTrue(codes.contains(ErrorCode.GEN_REPLACEMENT_IDENTITY), codes.toString());
        assertTrue(codes.contains(ErrorCode.GEN_REPLACEMENT_UNMATCHED), "777 changes no row: labels are 2, 3, 4");
        Diagnostic summary = warned.stream().filter(d -> d.code() == ErrorCode.GEN_REPLACEMENT_SUMMARY).findFirst().orElseThrow();
        assertEquals(Severity.INFO, summary.severity());
        assertTrue(summary.message().contains("rows seen=3") && summary.message().contains("\"777\"=0"), summary.message());
        assertThrows(FrameworkException.class, () -> diagnostics(s, with(RunConfig.ReplacePattern.PERMISSIVE,
                RunConfig.ReplaceUnmatched.FAIL, RunConfig.ReplaceUnparseable.WARN, RunConfig.ReplaceIdentity.ALLOW,
                RunConfig.ReplaceDiagnostics.OFF)));
    }

    @Test
    void unparseableRowsAreWarnedDroppedWithANoteOrFail() {
        Scenario s = grouped("FW_ReplaceRE(\"2\", \"x\")");
        List<Diagnostic> warn = diagnostics(s, GenerationOptions.defaults());
        assertEquals(List.of(ErrorCode.GEN_UNPARSEABLE_ROW), warn.stream().map(Diagnostic::code).toList());
        assertEquals(Severity.WARNING, warn.getFirst().severity());
        assertEquals("2", warn.getFirst().attributes().get("rows"), "the two combinations with label 2 stopped being labels");
        List<Diagnostic> drop = diagnostics(s, with(RunConfig.ReplacePattern.PERMISSIVE, RunConfig.ReplaceUnmatched.IGNORE,
                RunConfig.ReplaceUnparseable.DROP, RunConfig.ReplaceIdentity.ALLOW, RunConfig.ReplaceDiagnostics.OFF));
        assertEquals(Severity.INFO, drop.getFirst().severity(), "drop: still counted, as a note, never silently");
        FrameworkException fail = assertThrows(FrameworkException.class, () -> diagnostics(s, with(RunConfig.ReplacePattern.PERMISSIVE,
                RunConfig.ReplaceUnmatched.IGNORE, RunConfig.ReplaceUnparseable.FAIL, RunConfig.ReplaceIdentity.ALLOW,
                RunConfig.ReplaceDiagnostics.OFF)));
        assertEquals(ErrorCode.GEN_UNPARSEABLE_ROW, fail.diagnostics().getFirst().code());
        assertFalse(fail.diagnostics().getFirst().message().isEmpty());
    }
}
