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

package org.ghyura.cf.input;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.ghyura.cf.input.InputTestSupport.cell;
import static org.ghyura.cf.input.InputTestSupport.expectCode;
import static org.ghyura.cf.input.InputTestSupport.locationOf;
import static org.ghyura.cf.input.InputTestSupport.read;
import static org.ghyura.cf.input.InputTestSupport.shape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomlWorkbookReaderTest {

    private static final TomlWorkbookReader READER = new TomlWorkbookReader();

    /** The example in the legacy TomlScheduleParser documentation (trailing commas, multi-line arrays), literally. */
    private static final String LEGACY_EXAMPLE = """
            [sheets]
            FW_Seq = [
              ["HEAD", "FW_Exclude", "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"],
              ["ROW",  null, null, "FW_(HEAD,,COST,,LAT,,TAIL,,M:N)"],
            ]
            HEAD = [["scenario=demo"]]
            COST = [
              [" id=1 cost=0.10"],
              [" id=2 cost=0.30"],
            ]
            """;

    @Test
    void theLegacyDocumentedScheduleReadsToTheExpectedSheetsRowsAndCells() {
        WorkbookData workbook = read(READER, "demo.toml", LEGACY_EXAMPLE);
        assertEquals("toml:demo.toml", workbook.source());
        assertEquals(List.of(
                "FW_Seq = [0:HEAD|1:FW_Exclude|2:FW_Reuse|3:FW_Combi(1)|4:FW_Combi(1)]"
                        + " [0:ROW|3:FW_(HEAD,,COST,,LAT,,TAIL,,M:N)]",
                "HEAD = [0:scenario=demo]",
                "COST = [0: id=1 cost=0.10] [0: id=2 cost=0.30]"), shape(workbook));
    }

    @Test
    void commentsEverywhereTrailingCommasAndWindowsLineEndingsAreHandled() {
        String text = "# top\r\n[sheets] # the table\r\n# between\r\nA = [ # open\r\n  [\"x\"], # row comment\r\n  [\"y\", null, \"z\",], # trailing\r\n]\r\nB-2 = [[\"b\"]]\r\n9 = []\r\n";
        WorkbookData workbook = read(READER, "a.toml", text);
        assertEquals(List.of("A = [0:x] [0:y|2:z]", "B-2 = [0:b]", "9 ="), shape(workbook));
    }

    @Test
    void aHashOrCommaBracketInsideAStringIsDataNotSyntax() {
        WorkbookData workbook = read(READER, "a.toml", "[sheets]\nA = [[\"a # not a comment\", \"x,]\", \"p ,\\n]\"]]\n");
        assertEquals("a # not a comment", cell(workbook, "A", 0, 0).text());
        // the legacy cleaner stripped the comma of ",]" even inside a string; the data is kept intact here
        assertEquals("x,]", cell(workbook, "A", 0, 1).text());
        assertEquals("p ,\n]", cell(workbook, "A", 0, 2).text());
    }

    @Test
    void everyCellKeepsItsOriginalLineAndColumnEvenAfterCommentsAreBlanked() {
        String text = "[sheets]\nFW_Seq = [\n  [\"HEAD\", null, \"FW_Reuse\"], # c\n]\nHEAD = [[\"x\"]]\n";
        WorkbookData workbook = read(READER, "dir/s.toml", text);
        assertEquals(new SourceLocation("dir/s.toml", 3, 4, "/sheets/FW_Seq/0/0"), locationOf(workbook, "FW_Seq", 0, 0));
        assertEquals(new SourceLocation("dir/s.toml", 3, 18, "/sheets/FW_Seq/0/2"), locationOf(workbook, "FW_Seq", 0, 2));
        assertEquals(new SourceLocation("dir/s.toml", 2, 1, "/sheets/FW_Seq"), workbook.sheet("FW_Seq").orElseThrow().location());
        assertEquals(new SourceLocation("dir/s.toml", 5, 10, "/sheets/HEAD/0/0"), locationOf(workbook, "HEAD", 0, 0));
    }

    @Test
    void theSheetsTableIsRequired() {
        FrameworkException e = expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.toml", "A = [[\"x\"]]\n"));
        assertTrue(e.primary().message().contains("'[sheets]' table"));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.toml", ""));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.toml", "[other]\nA = [[\"x\"]]\n"));
    }

    @Test
    void constructsOutsideTheSubsetAreNamedAndLocated() {
        String[][] cases = {
                {"[sheets]\nA = [['literal']]\n", "literal strings"},
                {"[sheets]\nA = [[\"\"\"multi\"\"\"]]\n", "multi-line strings"},
                {"[sheets]\nA = [[{k = 1}]]\n", "inline tables"},
                {"[sheets]\n\"quoted\" = [[\"x\"]]\n", "bare key"},
                {"[sheets]\nA.b = [[\"x\"]]\n", "expected '='"},
                {"[sheets]\nA [[\"x\"]]\n", "expected '='"},
                {"[sheets]\nA = \"not an array\"\n", "must be an array"},
                {"[sheets]\nA = [[\"x\"]\n", "never closed"},
        };
        for (String[] c : cases) {
            FrameworkException e = expectCode(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, () -> read(READER, "a.toml", c[0]));
            assertTrue(e.primary().message().contains(c[1]), c[0].replace("\n", "\\n") + " -> " + e.primary().message());
            assertEquals(2, e.primary().location().line(), c[0].replace("\n", "\\n"));
        }
    }

    @Test
    void duplicateKeysAreAnErrorAndAMalformedStringIsLocated() {
        FrameworkException dup = expectCode(ErrorCode.INPUT_DUPLICATE_SHEET,
                () -> read(READER, "a.toml", "[sheets]\nA = [[\"x\"]]\na = [[\"y\"]]\n"));
        assertEquals(3, dup.primary().location().line());
        FrameworkException bad = expectCode(ErrorCode.INPUT_MALFORMED,
                () -> read(READER, "a.toml", "[sheets]\nA = [[\"x\", \"unterminated]]\n"));
        assertEquals(2, bad.primary().location().line());
        expectCode(ErrorCode.INPUT_MALFORMED, () -> read(READER, "a.toml", "[sheets]\nA = [[1 2]]\n"));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.toml", "[sheets]\nA = [[\"x\", [\"nested\"]]]\n"));
    }

    @Test
    void contentOutsideTheSubsetBeforeOrAfterTheSheetsTableIsWarnedAboutNotDropped() {
        ReadResult result = READER.read(InputSource.ofText("a.toml",
                "title = \"demo\"\n[sheets]\nA = [[\"x\"]]\n[extra]\nB = [[\"y\"]]\n"));
        assertEquals(List.of("A = [0:x]"), shape(result.workbook()));
        assertEquals(2, result.warnings().size());
        Diagnostic before = result.warnings().get(0);
        Diagnostic after = result.warnings().get(1);
        assertEquals(Severity.WARNING, before.severity());
        assertEquals(1, before.location().line());
        assertEquals(4, after.location().line());
        assertEquals(List.of(), READER.read(InputSource.ofText("a.toml", "# comment only before\n[sheets]\nA = [[\"x\"]]\n"))
                .warnings());
    }

    @Test
    void numbersAndBooleansBecomeTextLikeInJson() {
        WorkbookData workbook = read(READER, "a.toml", "[sheets]\nA = [[1, 2.0, true, false, 1e3]]\n");
        assertEquals(List.of("A = [0:1|1:2.0|2:true|3:false|4:1000.0]"), shape(workbook));
    }
}
