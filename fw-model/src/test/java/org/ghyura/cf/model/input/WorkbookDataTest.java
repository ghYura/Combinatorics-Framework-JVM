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

package org.ghyura.cf.model.input;

import org.ghyura.cf.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbookDataTest {

    private static final SourceLocation HERE = SourceLocation.of("test");

    private static InputCell text(int column, String text) {
        return new InputCell(column, CellKind.TEXT, text, HERE);
    }

    private static InputSheet sheet(String name, InputRow... rows) {
        return new InputSheet(name, HERE, List.of(rows));
    }

    private static InputRow row(int sourceRow, InputCell... cells) {
        return new InputRow(sourceRow, List.of(cells));
    }

    @Test
    void canonicalTextIsExactlyWhatTheFormatDocumentsIncludingEscapesAndEmptyRows() {
        WorkbookData workbook = new WorkbookData("x", List.of(
                sheet("A", row(0, text(0, "x"), text(2, "y\tz\\")), row(1)),
                sheet("B", row(4, new InputCell(0, CellKind.FORMULA, "SUM(A1)", HERE), text(1, "line\nbreak\r\u0001")))));
        String expected = "cf.workbook/1\n"
                + "S\tA\n"
                + "R\n"
                + "C\t0\tv\tx\n"
                + "C\t2\tv\ty\\tz\\\\\n"
                + "R\n"
                + "S\tB\n"
                + "R\n"
                + "C\t0\tf\tSUM(A1)\n"
                + "C\t1\tv\tline\\nbreak\\r\\u0001\n";
        assertEquals(expected, workbook.canonicalText());
    }

    /** Review finding BM01 for the workbook identity: a lone surrogate must not share the digest of '?'. */
    @Test
    void unpairedSurrogatesInCellsAndSheetNamesKeepTheirOwnDigest() {
        String high = String.valueOf((char) 0xD800);
        WorkbookData lone = new WorkbookData("x", List.of(sheet("A", row(0, text(0, high)))));
        WorkbookData question = new WorkbookData("x", List.of(sheet("A", row(0, text(0, "?")))));
        WorkbookData named = new WorkbookData("x", List.of(sheet("A" + high, row(0, text(0, "?")))));
        WorkbookData namedQuestion = new WorkbookData("x", List.of(sheet("A?", row(0, text(0, "?")))));
        assertEquals("cf.workbook/1\nS\tA\nR\nC\t0\tv\t\\ud800\n", lone.canonicalText());
        assertNotEquals(lone.contentDigest(), question.contentDigest());
        assertNotEquals(named.contentDigest(), namedQuestion.contentDigest());
    }

    @Test
    void theLogicalModelIgnoresLocationsSourceRowNumbersAndCellKindsExceptFormulas() {
        WorkbookData fromJson = new WorkbookData("json:a", List.of(
                sheet("S", row(0, new InputCell(0, CellKind.TEXT, "3", SourceLocation.of("a.json", 1, 2)), text(1, "FW_Reuse")))));
        WorkbookData fromXlsx = new WorkbookData("xlsx:a", List.of(
                sheet("S", row(7, new InputCell(0, CellKind.NUMBER, "3", SourceLocation.ofPath("a.xlsx", "S!A8")),
                        new InputCell(1, CellKind.TEXT, "FW_Reuse", SourceLocation.ofPath("a.xlsx", "S!B8"))))));
        assertNotEquals(fromJson, fromXlsx);
        assertEquals(fromJson.canonicalText(), fromXlsx.canonicalText());
        assertEquals(fromJson.contentDigest(), fromXlsx.contentDigest());

        WorkbookData formula = new WorkbookData("f", List.of(
                sheet("S", row(0, new InputCell(0, CellKind.FORMULA, "3", HERE), text(1, "FW_Reuse")))));
        assertNotEquals(fromJson.canonicalText(), formula.canonicalText());
    }

    @Test
    void sheetOrderEmptyRowsColumnsAndTextAllMatterToTheLogicalModel() {
        WorkbookData base = new WorkbookData("x", List.of(sheet("A", row(0, text(0, "a"))), sheet("B")));
        List<WorkbookData> different = List.of(
                new WorkbookData("x", List.of(sheet("B"), sheet("A", row(0, text(0, "a"))))),
                new WorkbookData("x", List.of(sheet("A", row(0, text(0, "a")), row(1)), sheet("B"))),
                new WorkbookData("x", List.of(sheet("A", row(0, text(1, "a"))), sheet("B"))),
                new WorkbookData("x", List.of(sheet("A", row(0, text(0, "a "))), sheet("B"))),
                new WorkbookData("x", List.of(sheet("a", row(0, text(0, "a"))), sheet("B"))));
        for (WorkbookData other : different) {
            assertNotEquals(base.canonicalText(), other.canonicalText());
        }
        assertEquals(base.canonicalText(), new WorkbookData("y", List.of(sheet("A", row(5, text(0, "a"))), sheet("B"))).canonicalText());
    }

    @Test
    void theDigestIsTheSha256OfTheCanonicalText() throws Exception {
        WorkbookData workbook = new WorkbookData("x", List.of(sheet("A", row(0, text(0, "б")))));
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(workbook.canonicalText().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : expected) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        assertEquals(hex.toString(), workbook.contentDigest());
        assertEquals(64, workbook.contentDigest().length());
    }

    @Test
    void sheetNamesAreUniqueIgnoringCaseButKeepTheirSpelling() {
        assertThrows(IllegalArgumentException.class, () -> new WorkbookData("x", List.of(sheet("Head"), sheet("HEAD"))));
        WorkbookData workbook = new WorkbookData("x", List.of(sheet("Head"), sheet("Tail")));
        assertEquals(List.of("Head", "Tail"), workbook.sheetNames());
        assertTrue(workbook.sheet("Head").isPresent());
        assertFalse(workbook.sheet("head").isPresent());
    }

    @Test
    void invariantsAreEnforcedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new InputCell(-1, CellKind.TEXT, "x", HERE));
        assertThrows(IllegalArgumentException.class, () -> new InputCell(0, CellKind.BLANK, "x", HERE));
        assertThrows(IllegalArgumentException.class, () -> row(0, text(2, "a"), text(1, "b")));
        assertThrows(IllegalArgumentException.class, () -> row(0, text(1, "a"), text(1, "b")));
        assertThrows(IllegalArgumentException.class, () -> row(-1));
        assertThrows(IllegalArgumentException.class, () -> sheet(" "));
        assertThrows(IllegalArgumentException.class, () -> sheet("A", row(3), row(3)));
        assertThrows(NullPointerException.class, () -> new InputCell(0, CellKind.TEXT, null, HERE));
        assertEquals(CellKind.BLANK, new InputCell(0, CellKind.BLANK, "", HERE).kind());
    }

    @Test
    void sheetsReportControlStatusAndCellCounts() {
        InputSheet control = sheet("FW_Seq", row(0, text(0, "a"), text(1, "b")), row(1, text(0, "c")));
        assertTrue(control.isControl());
        assertEquals(3, control.cellCount());
        assertFalse(sheet("HEAD").isControl());
        assertFalse(sheet("fw_seq").isControl());
        assertTrue(row(0).isEmpty());
    }

    @Test
    void theModelIsImmutableAndDefensive() {
        java.util.ArrayList<InputCell> cells = new java.util.ArrayList<>(List.of(text(0, "a")));
        InputRow row = new InputRow(0, cells);
        cells.add(text(1, "b"));
        assertEquals(1, row.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> row.cells().add(text(2, "c")));
        WorkbookData workbook = new WorkbookData("x", List.of(sheet("A", row)));
        assertThrows(UnsupportedOperationException.class, () -> workbook.sheets().clear());
    }
}
