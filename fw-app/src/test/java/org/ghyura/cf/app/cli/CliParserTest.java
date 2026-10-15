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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliParserTest {

    private static ParsedCommand parse(String... args) {
        return assertInstanceOf(CliRequest.Invocation.class, CliParser.parse(args)).command();
    }

    private static List<ErrorCode> failures(String... args) {
        FrameworkException e = assertThrows(FrameworkException.class, () -> CliParser.parse(args));
        return e.diagnostics().stream().map(Diagnostic::code).toList();
    }

    @Test
    void optionsAcceptSpaceAndEqualsForms() {
        ParsedCommand spaced = parse("run", "--scenario", "a.json", "--workers", "8");
        ParsedCommand equals = parse("run", "--scenario=a.json", "--workers=8");
        for (ParsedCommand c : List.of(spaced, equals)) {
            assertEquals("run", c.spec().name());
            assertEquals("a.json", c.value("--scenario").orElseThrow());
            assertEquals("8", c.value("--workers").orElseThrow());
            assertTrue(c.value("--seed").isEmpty());
            assertEquals(OutputFormat.TEXT, c.format());
        }
    }

    @Test
    void valuesMayContainEqualsSignsAndLookLikeNegativeNumbers() {
        ParsedCommand c = parse("validate", "--set", "cf.a=b=c", "--seed", "-5");
        assertEquals(List.of("cf.a=b=c"), c.values("--set"));
        assertEquals("-5", c.value("--seed").orElseThrow());
    }

    @Test
    void repeatableOptionsKeepCommandLineOrderAndFlagsAreRecorded() {
        ParsedCommand c = parse("validate", "--set", "cf.a=1", "--debug", "--set", "cf.b=2", "--format", "xml");
        assertEquals(List.of("cf.a=1", "cf.b=2"), c.values("--set"));
        assertTrue(c.flag("--debug"));
        assertFalse(c.flag("--help"));
        assertEquals(OutputFormat.XML, c.format());
    }

    @Test
    void helpAndVersionAreRecognisedAndHelpBeatsEveryOtherProblem() {
        assertEquals(new CliRequest.Help(null), CliParser.parse(new String[]{"--help"}));
        assertEquals(new CliRequest.Help(null), CliParser.parse(new String[]{"-h"}));
        assertEquals(new CliRequest.Version(), CliParser.parse(new String[]{"--version"}));
        assertEquals(new CliRequest.Help("run"), CliParser.parse(new String[]{"run", "--help"}));
        assertEquals(new CliRequest.Help("run"), CliParser.parse(new String[]{"run", "--bogus", "-h", "stray"}));
    }

    @Test
    void noCommandAndUnknownCommandsAreUsageErrorsWithSuggestions() {
        assertEquals(List.of(ErrorCode.USAGE_UNKNOWN_COMMAND), failures());
        assertEquals(List.of(ErrorCode.USAGE_UNKNOWN_COMMAND), failures("--scenario", "a.json"));
        FrameworkException e = assertThrows(FrameworkException.class, () -> CliParser.parse(new String[]{"valdate"}));
        assertEquals("validate", e.primary().attributes().get("suggestion"));
        assertFalse(assertThrows(FrameworkException.class, () -> CliParser.parse(new String[]{"qqqqqqqq"}))
                .primary().attributes().containsKey("suggestion"));
    }

    @Test
    void unknownOptionsAreReportedWithASuggestionAndTheirValueIsNotAlsoAStrayArgument() {
        FrameworkException e = assertThrows(FrameworkException.class,
                () -> CliParser.parse(new String[]{"plan", "--scenaro", "a.json"}));
        assertEquals(List.of(ErrorCode.USAGE_UNKNOWN_OPTION, ErrorCode.USAGE_MISSING_OPTION),
                e.diagnostics().stream().map(Diagnostic::code).toList());
        assertEquals("--scenario", e.diagnostics().get(0).attributes().get("suggestion"));
        assertEquals(List.of(ErrorCode.USAGE_UNKNOWN_OPTION), failures("capabilities", "-x"));
    }

    @Test
    void positionalArgumentsAreNeverAccepted() {
        assertEquals(List.of(ErrorCode.USAGE_UNEXPECTED_ARGUMENT), failures("capabilities", "extra"));
        assertEquals(List.of(ErrorCode.USAGE_UNEXPECTED_ARGUMENT, ErrorCode.USAGE_UNEXPECTED_ARGUMENT),
                failures("capabilities", "--", "a", "b"));
        assertEquals(List.of(ErrorCode.USAGE_UNEXPECTED_ARGUMENT), failures("capabilities", ""));
    }

    @Test
    void missingAndDuplicateAndMalformedValuesEachHaveTheirOwnCode() {
        assertEquals(List.of(ErrorCode.USAGE_MISSING_VALUE, ErrorCode.USAGE_MISSING_OPTION), failures("plan", "--scenario"));
        assertEquals(List.of(ErrorCode.USAGE_MISSING_VALUE), failures("validate", "--config", "--debug"));
        assertEquals(List.of(ErrorCode.USAGE_DUPLICATE_OPTION), failures("validate", "--config", "a", "--config", "b"));
        assertEquals(List.of(ErrorCode.USAGE_DUPLICATE_OPTION), failures("validate", "--debug", "--debug"));
        assertEquals(List.of(ErrorCode.USAGE_INVALID_VALUE), failures("validate", "--debug=yes"));
        assertEquals(List.of(ErrorCode.USAGE_INVALID_VALUE), failures("validate", "--config="));
        assertEquals(List.of(ErrorCode.USAGE_INVALID_VALUE), failures("validate", "--format", "json"));
        assertEquals(List.of(ErrorCode.USAGE_INVALID_VALUE), failures("validate", "--set", "no-equals-sign"));
        assertEquals(List.of(ErrorCode.USAGE_INVALID_VALUE), failures("validate", "--set", "=value"));
        assertEquals(List.of(ErrorCode.USAGE_INVALID_VALUE),
                failures("export", "--input", "a", "--output", "b", "--kind", "pdf"));
    }

    @Test
    void everyRequiredOptionOfEveryCommandIsEnforcedIndividually() {
        for (CommandSpec spec : CliSpec.COMMANDS) {
            for (String required : spec.required()) {
                List<String> args = new java.util.ArrayList<>(List.of(spec.name()));
                for (String other : spec.required()) {
                    if (!other.equals(required)) {
                        args.add(other);
                        args.add("x");
                    }
                }
                FrameworkException e = assertThrows(FrameworkException.class,
                        () -> CliParser.parse(args.toArray(new String[0])), spec.name() + " without " + required);
                assertEquals(List.of(ErrorCode.USAGE_MISSING_OPTION),
                        e.diagnostics().stream().map(Diagnostic::code).toList(), spec.name());
                assertEquals(required, e.primary().attributes().get("option"));
            }
        }
    }

    @Test
    void everyProblemIsReportedInOnePass() {
        assertEquals(List.of(ErrorCode.USAGE_UNKNOWN_OPTION, ErrorCode.USAGE_DUPLICATE_OPTION,
                        ErrorCode.USAGE_UNEXPECTED_ARGUMENT, ErrorCode.USAGE_MISSING_OPTION),
                failures("candidates", "--nope", "--limit", "1", "--limit", "2", "stray"));
    }

    @Test
    void usageDiagnosticsPointAtTheOffendingArgument() {
        FrameworkException e = assertThrows(FrameworkException.class,
                () -> CliParser.parse(new String[]{"capabilities", "--debug", "--oops"}));
        assertEquals("command line (argument 3)", e.primary().location().toString());
    }

    @Test
    void everyDeclaredCommandParsesWithItsRequiredOptions() {
        for (CommandSpec spec : CliSpec.COMMANDS) {
            List<String> args = new java.util.ArrayList<>(List.of(spec.name()));
            for (String required : spec.required()) {
                args.add(required);
                args.add("value");
            }
            ParsedCommand parsed = parse(args.toArray(new String[0]));
            assertEquals(spec.name(), parsed.spec().name());
        }
    }
}
