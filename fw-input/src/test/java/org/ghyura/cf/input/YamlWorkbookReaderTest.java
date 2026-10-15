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

import static org.ghyura.cf.input.InputTestSupport.expectCode;
import static org.ghyura.cf.input.InputTestSupport.locationOf;
import static org.ghyura.cf.input.InputTestSupport.read;
import static org.ghyura.cf.input.InputTestSupport.shape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlWorkbookReaderTest {

    private static final YamlWorkbookReader READER = new YamlWorkbookReader();

    /** The example in the legacy YamlScheduleParser documentation, literally. */
    private static final String LEGACY_EXAMPLE = """
            sheets:
              FW_Seq:
                - ["HEAD", "FW_Exclude", "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"]
                - ["COST", "FW_Exclude", "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"]
                - ["ROW", null, null, "FW_(HEAD,,COST,,LAT,,TAIL,,M:N)"]
              COST:
                - [" id=1 cost=0.10"]
                - [" id=2 cost=0.30"]
              ROW:
                - ["FW_EMPTY_STRING"]
            """;

    @Test
    void theLegacyDocumentedScheduleReadsToTheExpectedSheetsRowsAndCells() {
        WorkbookData workbook = read(READER, "demo.yaml", LEGACY_EXAMPLE);
        assertEquals("yaml:demo.yaml", workbook.source());
        assertEquals(List.of(
                "FW_Seq = [0:HEAD|1:FW_Exclude|2:FW_Reuse|3:FW_Combi(1)|4:FW_Combi(1)]"
                        + " [0:COST|1:FW_Exclude|2:FW_Reuse|3:FW_Combi(1)|4:FW_Combi(1)]"
                        + " [0:ROW|3:FW_(HEAD,,COST,,LAT,,TAIL,,M:N)]",
                "COST = [0: id=1 cost=0.10] [0: id=2 cost=0.30]",
                "ROW = [0:FW_EMPTY_STRING]"), shape(workbook));
    }

    @Test
    void commentsBlankLinesWindowsLineEndingsAndEmptySheetsAreHandled() {
        String text = "# header comment\r\n\r\nsheets:\r\n  # sheet comment\r\n  A:\r\n    - [\"x\"]\r\n\r\n  B:\r\n  C:\r\n    - [\"y\", 1, 2.5, true]\r\n";
        WorkbookData workbook = read(READER, "a.yml", text);
        assertEquals(List.of("A = [0:x]", "B =", "C = [0:y|1:1|2:2.5|3:true]"), shape(workbook));
    }

    @Test
    void everyCellCarriesItsExactLineAndColumnInTheOriginalFile() {
        String text = "sheets:\n  FW_Seq:\n    - [\"HEAD\", null, \"FW_Reuse\"]\n  HEAD:\n    - [\"x\"]\n";
        WorkbookData workbook = read(READER, "dir/s.yaml", text);
        assertEquals(new SourceLocation("dir/s.yaml", 3, 8, "/sheets/FW_Seq/0/0"), locationOf(workbook, "FW_Seq", 0, 0));
        assertEquals(new SourceLocation("dir/s.yaml", 3, 22, "/sheets/FW_Seq/0/2"), locationOf(workbook, "FW_Seq", 0, 2));
        assertEquals(new SourceLocation("dir/s.yaml", 2, 3, "/sheets/FW_Seq"), workbook.sheet("FW_Seq").orElseThrow().location());
        assertEquals(new SourceLocation("dir/s.yaml", 5, 8, "/sheets/HEAD/0/0"), locationOf(workbook, "HEAD", 0, 0));
    }

    @Test
    void theTopLevelMustStartWithSheets() {
        FrameworkException e = expectCode(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, () -> read(READER, "a.yaml", "name: demo\nsheets:\n"));
        assertEquals(1, e.primary().location().line());
        assertTrue(e.primary().message().contains("'sheets:' first"));
        expectCode(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, () -> read(READER, "a.yaml", "  indented: first\nsheets:\n"));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.yaml", "# only a comment\n"));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.yaml", ""));
    }

    @Test
    void everyConstructOutsideTheSubsetIsRejectedWithItsLine() {
        String[][] cases = {
                {"sheets:\n  A:\n    - plain text\n", "flow-style arrays"},
                {"sheets:\n    - [\"x\"]\n", "before any sheet name"},
                {"sheets:\n  A:\n  - [\"x\"]\n", "indent must exceed"},
                {"sheets:\n  A:\n    - [\"x\"]\n    B:\n", "indent is inconsistent"},
                {"sheets:\n  A:\n    key: value\n", "unrecognised syntax"},
                {"sheets:\n  my-sheet:\n", "unrecognised syntax"},
                {"sheets:\n  A:\n\t- [\"x\"]\n", "unexpected dedent"},
                {"sheets:\n  A:\n    - &anchor [\"x\"]\n", "flow-style arrays"},
                {"sheets:\n  A: |\n    block\n", "unrecognised syntax"},
        };
        for (String[] c : cases) {
            FrameworkException e = expectCode(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, () -> read(READER, "a.yaml", c[0]));
            assertTrue(e.primary().message().contains(c[1]), c[0].replace("\n", "\\n") + " -> " + e.primary().message());
            assertTrue(e.primary().location().line() >= 2, c[0].replace("\n", "\\n") + " -> " + e.primary().location());
        }
    }

    @Test
    void aTrailingCommentAfterARowIsNotAllowedInThisSubsetAndIsLocated() {
        FrameworkException e = expectCode(ErrorCode.INPUT_MALFORMED,
                () -> read(READER, "a.yaml", "sheets:\n  A:\n    - [\"x\"] # why\n"));
        assertEquals(3, e.primary().location().line());
        assertEquals(13, e.primary().location().column()); // the '#' of    - ["x"] # why
        expectCode(ErrorCode.INPUT_MALFORMED, () -> read(READER, "a.yaml", "sheets:\n  A:\n    - [\"x\", ]\n"));
        expectCode(ErrorCode.INPUT_MALFORMED, () -> read(READER, "a.yaml", "sheets:\n  A:\n    - [\"x\"\n"));
    }

    @Test
    void aSheetThatAppearsTwiceIsAnErrorWhereLegacyMergedItsRows() {
        FrameworkException e = expectCode(ErrorCode.INPUT_DUPLICATE_SHEET,
                () -> read(READER, "a.yaml", "sheets:\n  A:\n    - [\"x\"]\n  a:\n    - [\"y\"]\n"));
        assertEquals(4, e.primary().location().line());
        expectCode(ErrorCode.INPUT_DUPLICATE_SHEET, () -> read(READER, "a.yaml", "sheets:\n  A:\n  A:\n"));
    }

    @Test
    void otherTopLevelKeysAfterTheSheetsBlockAreWarnedAboutNotDropped() {
        ReadResult result = READER.read(InputSource.ofText("a.yaml",
                "sheets:\n  A:\n    - [\"x\"]\n_comment:\n  ignored: true\nversion:\n"));
        assertEquals(List.of("A = [0:x]"), shape(result.workbook()));
        assertEquals(2, result.warnings().size());
        Diagnostic first = result.warnings().getFirst();
        assertEquals(Severity.WARNING, first.severity());
        assertEquals("_comment", first.attributes().get("key"));
        assertEquals(4, first.location().line());
        expectCode(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, () -> read(READER, "a.yaml", "sheets:\n  A:\nsheets:\n  B:\n"));
    }

    @Test
    void containersInsideCellsAreRejectedWithTheirPointer() {
        FrameworkException e = expectCode(ErrorCode.INPUT_SCHEMA,
                () -> read(READER, "a.yaml", "sheets:\n  A:\n    - [\"x\", [\"nested\"]]\n"));
        assertEquals("/sheets/A/0/1", e.primary().location().path());
        assertEquals(3, e.primary().location().line());
    }
}
