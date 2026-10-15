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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.ghyura.cf.input.InputTestSupport.cell;
import static org.ghyura.cf.input.InputTestSupport.expectCode;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The XLSX reader against workbooks built directly with POI (not through the framework's own writer). */
class XlsxWorkbookReaderTest {

    private static final XlsxWorkbookReader READER = new XlsxWorkbookReader();

    private static byte[] bytes(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    private static CellStyle style(XSSFWorkbook wb, String format) {
        return PoiStyles.withFormat(wb, format);
    }

    private static WorkbookData read(byte[] bytes) {
        return READER.read(InputSource.of("book.xlsx", bytes)).workbook();
    }

    @Test
    void everyCellTypeBecomesTheTextTheLegacyDataFormatterGave() throws IOException {
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("text");
            row.createCell(1).setCellValue(3.0);
            row.createCell(2).setCellValue(0.5);
            Cell money = row.createCell(3);
            money.setCellValue(1234.5);
            money.setCellStyle(style(wb, "#,##0.00"));
            Cell percent = row.createCell(4);
            percent.setCellValue(0.25);
            percent.setCellStyle(style(wb, "0%"));
            Cell date = row.createCell(5);
            date.setCellValue(LocalDate.of(2026, 10, 1));
            date.setCellStyle(style(wb, "yyyy-mm-dd"));
            row.createCell(6).setCellValue(true);
            row.createCell(7).setCellFormula("SUM(A1:A2)");
            row.createCell(8).setCellErrorValue(FormulaError.DIV0.getCode());
            row.createCell(9, CellType.BLANK);
            row.createCell(10).setCellValue("");
            row.createCell(11).setCellValue(-2);
            row.createCell(12).setCellValue(12345678901.0);
            file = bytes(wb);
        }
        InputRow row = read(file).sheet("Data").orElseThrow().rows().getFirst();
        assertEquals(List.of("text", "3", "0.5", "1,234.50", "25%", "2026-10-01", "TRUE", "SUM(A1:A2)", "#DIV/0!", "", "", "-2",
                "12345678901"), row.cells().stream().map(InputCell::text).toList());
        assertEquals(List.of(CellKind.TEXT, CellKind.NUMBER, CellKind.NUMBER, CellKind.NUMBER, CellKind.NUMBER, CellKind.NUMBER,
                CellKind.BOOLEAN, CellKind.FORMULA, CellKind.ERROR, CellKind.BLANK, CellKind.TEXT, CellKind.NUMBER, CellKind.NUMBER),
                row.cells().stream().map(InputCell::kind).toList());
    }

    @Test
    void aFormulaYieldsItsSourceTextNeverItsResult() throws IOException {
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("F");
            sheet.createRow(0).createCell(0).setCellValue(2);
            sheet.createRow(1).createCell(0).setCellValue(3);
            sheet.createRow(2).createCell(0).setCellFormula("SUM(A1:A2)*10");
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            file = bytes(wb);
        }
        InputCell formula = cell(read(file), "F", 2, 0);
        assertEquals(CellKind.FORMULA, formula.kind());
        assertEquals("SUM(A1:A2)*10", formula.text());
    }

    @Test
    void numberTextDoesNotDependOnTheDefaultLocale() throws IOException {
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Cell c = wb.createSheet("L").createRow(0).createCell(0);
            c.setCellValue(1234.5);
            c.setCellStyle(style(wb, "#,##0.00"));
            wb.getSheet("L").getRow(0).createCell(1).setCellValue(0.1);
            file = bytes(wb);
        }
        Locale saved = Locale.getDefault();
        try {
            for (String tag : List.of("de-DE", "tr-TR", "ar-SA", "fr-FR")) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                WorkbookData workbook = read(file);
                assertEquals("1,234.50", cell(workbook, "L", 0, 0).text(), tag);
                assertEquals("0.1", cell(workbook, "L", 0, 1).text(), tag);
            }
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void physicalRowsAreKeptIncludingEmptyOnesAndSparseNumbering() throws IOException {
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("S");
            sheet.createRow(0).createCell(0).setCellValue("first");
            sheet.createRow(3);                                   // a physical row with no cells
            Row sparse = sheet.createRow(10);
            sparse.createCell(2).setCellValue("c2");
            sparse.createCell(5).setCellValue("c5");
            file = bytes(wb);
        }
        InputSheet sheet = read(file).sheet("S").orElseThrow();
        assertEquals(List.of(0, 3, 10), sheet.rows().stream().map(InputRow::sourceRow).toList());
        assertTrue(sheet.rows().get(1).isEmpty());
        assertEquals(List.of(2, 5), sheet.rows().get(2).cells().stream().map(InputCell::column).toList());
    }

    @Test
    void sheetOrderNamesHiddenSheetsAndLocationsAreExact() throws IOException {
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("FW_Seq").createRow(0).createCell(0).setCellValue("A");
            wb.createSheet("My Sheet").createRow(1).createCell(1).setCellValue("B2");
            wb.createSheet("Hidden").createRow(0).createCell(0).setCellValue("h");
            wb.setSheetHidden(2, true);
            file = bytes(wb);
        }
        WorkbookData workbook = read(file);
        assertEquals(List.of("FW_Seq", "My Sheet", "Hidden"), workbook.sheetNames());
        assertEquals("xlsx:book.xlsx", workbook.source());
        assertEquals(new SourceLocation("book.xlsx", 0, 0, "'My Sheet'!B2"), cell(workbook, "My Sheet", 0, 1).location());
        assertEquals("FW_Seq!A1", cell(workbook, "FW_Seq", 0, 0).location().path());
        assertEquals("h", cell(workbook, "Hidden", 0, 0).text());
    }

    @Test
    void aLargeCodeCellSurvivesIntact() throws IOException {
        String code = "x".repeat(32_000) + "\n" + "y".repeat(766);
        assertEquals(32_767, code.length());
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("S").createRow(0).createCell(0).setCellValue(code);
            file = bytes(wb);
        }
        assertEquals(code, cell(read(file), "S", 0, 0).text());
    }

    @Test
    void whatIsNotAnOoxmlWorkbookIsRejectedWithAMalformedInputError() throws IOException {
        expectCode(ErrorCode.INPUT_MALFORMED, () -> READER.read(InputSource.of("a.xlsx", new byte[0])));
        expectCode(ErrorCode.INPUT_MALFORMED, () -> READER.read(InputSource.ofText("a.xlsx", "sheets: not a zip")));
        expectCode(ErrorCode.INPUT_MALFORMED, () -> READER.read(InputSource.of("a.xlsx", new byte[]{'P', 'K', 3})));

        byte[] notAWorkbook;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("hello.txt"));
            zip.write("hi".getBytes());
            zip.closeEntry();
            zip.finish();
            notAWorkbook = out.toByteArray();
        }
        expectCode(ErrorCode.INPUT_MALFORMED, () -> READER.read(InputSource.of("a.xlsx", notAWorkbook)));

        byte[] good;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("S").createRow(0).createCell(0).setCellValue("x");
            good = bytes(wb);
        }
        byte[] truncated = java.util.Arrays.copyOf(good, good.length / 2);
        FrameworkException e = expectCode(ErrorCode.INPUT_MALFORMED, () -> READER.read(InputSource.of("a.xlsx", truncated)));
        assertEquals("a.xlsx", e.primary().location().origin());
    }

    /**
     * Regression for an incident: POI's path-based factory opens a workbook read-write and re-saves it on close,
     * which rewrote the legacy Core's inputs and, once, 32 files of a reference checkout. Reading must leave the
     * file byte-identical, with its modification time unchanged.
     */
    @Test
    void readingAFileNeverModifiesIt(@TempDir Path dir) throws Exception {
        byte[] original;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("S").createRow(0).createCell(0).setCellValue("x");
            original = bytes(wb);
        }
        Path file = dir.resolve("input.xlsx");
        Files.write(file, original);
        FileTime past = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(file, past);

        ReadResult result = WorkbookFormats.readFile(file);

        assertEquals("x", cell(result.workbook(), "S", 0, 0).text());
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(past, Files.getLastModifiedTime(file));
        assertEquals(sha256(original), sha256(Files.readAllBytes(file)));
        // nothing else was created next to it (no temp or lock files)
        try (var listing = Files.list(dir)) {
            assertEquals(List.of("input.xlsx"), listing.map(p -> p.getFileName().toString()).toList());
        }
    }

    private static String sha256(byte[] data) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }
}
