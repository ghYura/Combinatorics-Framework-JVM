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

package org.ghyura.cf.config;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertiesTextTest {

    private static List<PropertiesText.Line> parse(String text, List<Diagnostic> problems) {
        return PropertiesText.parse("test.properties", text, problems);
    }

    private static List<PropertiesText.Line> parse(String text) {
        List<Diagnostic> problems = new ArrayList<>();
        List<PropertiesText.Line> lines = parse(text, problems);
        assertEquals(List.of(), problems);
        return lines;
    }

    @Test
    void keysCarryTheirLineNumbersAndCommentsAndBlankLinesAreSkipped() {
        List<PropertiesText.Line> lines = parse("# header\n\na=1\n  ! another comment\n   b = 2\n\nc:3\nd 4\n");
        assertEquals(List.of(
                new PropertiesText.Line("a", "1", 3),
                new PropertiesText.Line("b", "2", 5),
                new PropertiesText.Line("c", "3", 7),
                new PropertiesText.Line("d", "4", 8)), lines);
    }

    @Test
    void allThreeLineTerminatorsCountAsOneLineBreak() {
        for (String terminator : new String[]{"\n", "\r\n", "\r"}) {
            List<PropertiesText.Line> lines = parse("a=1" + terminator + terminator + "b=2" + terminator + "c=3");
            assertEquals(List.of(1, 3, 4), lines.stream().map(PropertiesText.Line::line).toList(),
                    "terminator " + terminator.replace("\r", "CR").replace("\n", "LF"));
        }
    }

    @Test
    void aFinalLineWithoutATerminatorIsStillRead() {
        assertEquals(List.of(new PropertiesText.Line("last", "value", 2)), parse("# c\nlast=value"));
        assertEquals(List.of(), parse(""));
        assertEquals(List.of(), parse("\n\n# only comments\n"));
    }

    @Test
    void continuationLinesJoinIntoOneValueAndKeepTheStartingLine() {
        List<PropertiesText.Line> lines = parse("a=one \\\n    two \\\n  three\nb=4\n");
        assertEquals(new PropertiesText.Line("a", "one two three", 1), lines.get(0));
        assertEquals(new PropertiesText.Line("b", "4", 4), lines.get(1));
    }

    @Test
    void anEvenNumberOfTrailingBackslashesDoesNotContinue() {
        List<PropertiesText.Line> lines = parse("a=path\\\\\nb=2\n");
        assertEquals("path\\", lines.get(0).value());
        assertEquals(new PropertiesText.Line("b", "2", 2), lines.get(1));
    }

    @Test
    void escapesFollowTheJdkRules() {
        List<PropertiesText.Line> lines = parse("a=tab\\there\nb=caf\\u00e9\nc\\ d=e\\:f\n");
        assertEquals("tab\there", lines.get(0).value());
        assertEquals("caf\u00e9", lines.get(1).value());
        assertEquals(new PropertiesText.Line("c d", "e:f", 3), lines.get(2));
    }

    @Test
    void aLeadingByteOrderMarkIsDropped() {
        assertEquals(List.of(new PropertiesText.Line("cf.random.seed", "7", 1)), parse("\uFEFFcf.random.seed=7\n"));
    }

    @Test
    void aDuplicateKeyIsAnErrorAndTheFirstDefinitionWins() {
        List<Diagnostic> problems = new ArrayList<>();
        List<PropertiesText.Line> lines = parse("a=1\nb=2\na=3\n", problems);
        assertEquals(2, lines.size());
        assertEquals("1", lines.get(0).value());
        assertEquals(1, problems.size());
        Diagnostic d = problems.get(0);
        assertEquals(ErrorCode.CONFIG_DUPLICATE_KEY, d.code());
        assertEquals(3, d.location().line());
        assertEquals("1", d.attributes().get("firstLine"));
        assertEquals("a", d.attributes().get("key"));
    }

    @Test
    void aMalformedUnicodeEscapeIsReportedAtItsLine() {
        List<Diagnostic> problems = new ArrayList<>();
        List<PropertiesText.Line> lines = parse("ok=1\nbad=\\u12\nlater=2\n", problems);
        assertEquals(List.of("ok", "later"), lines.stream().map(PropertiesText.Line::key).toList());
        assertEquals(1, problems.size());
        assertEquals(ErrorCode.CONFIG_MALFORMED, problems.get(0).code());
        assertEquals(2, problems.get(0).location().line());
    }

    @Test
    void aKeyWithoutAValueIsAnEmptyValue() {
        assertEquals(List.of(new PropertiesText.Line("flag", "", 1)), parse("flag\n"));
        assertTrue(PropertiesText.physicalLines("").isEmpty());
        assertEquals(List.of("a", "", "b"), PropertiesText.physicalLines("a\n\nb\n"));
    }
}
