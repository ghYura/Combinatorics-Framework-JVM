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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review finding IP03: a number cell whose custom format renders text that starts with FW_ is a cell directive,
 * because the legacy parser judged {@code DataFormatter.formatCellValue} of every non-formula cell. The same
 * scenario as a formatted XLSX workbook (built with POI directly, not with the framework's writer) and as JSON
 * strings must interpret to the same values, kinds, flags and diagnostics, and the census must count what the
 * interpreter issues. Formula text stays protected.
 */
class FormattedDirectiveCellTest {

    /** The formatted texts the workbook's number cells render, row by row; checked against POI before use. */
    private static final List<String> RENDERED = List.of("x", "FW_Optional7", "FW_EMPTY_STRING1", "FW_File=f2.txt",
            "FW_RefineCodeExceptQuoted3", "y");

    private static byte[] formattedWorkbook() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("A");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("x");
            number(wb, row.createCell(1), 7, "\"FW\\_Optional\"0");
            number(wb, row.createCell(2), 1, "\"FW\\_EMPTY\\_STRING\"0");
            number(wb, row.createCell(3), 2, "\"FW\\_File=f\"0\".txt\"");
            number(wb, row.createCell(4), 3, "\"FW\\_RefineCodeExceptQuoted\"0");
            row.createCell(5).setCellValue("y");
            row.createCell(6).setCellFormula("\"FW_\"&\"Optional\"");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void number(XSSFWorkbook wb, Cell cell, double value, String format) {
        cell.setCellValue(value);
        cell.setCellStyle(PoiStyles.withFormat(wb, format));
    }

    private static final String JSON = """
            {"sheets": {"A": [["x", "FW_Optional7", "FW_EMPTY_STRING1", "FW_File=f2.txt", "FW_RefineCodeExceptQuoted3", "y"]]}}
            """;

    private static final List<String> asked = new java.util.ArrayList<>();
    private static final FileResolver FILES = (path, where) -> {
        if (!asked.contains(path)) {
            asked.add(path);
        }
        return "a b\n";
    };

    @Test
    void aFormattedNumberIsTheDirectiveItRendersExactlyAsTheSameStringInJson() throws IOException {
        WorkbookData xlsx = new XlsxWorkbookReader().read(InputSource.of("book.xlsx", formattedWorkbook())).workbook();
        List<InputCell> cells = xlsx.sheet("A").orElseThrow().rows().getFirst().cells();
        for (int i = 0; i < RENDERED.size(); i++) {
            assertEquals(RENDERED.get(i), cells.get(i).text(), "POI renders cell " + i + " so");
        }
        assertEquals(List.of(CellKind.TEXT, CellKind.NUMBER, CellKind.NUMBER, CellKind.NUMBER, CellKind.NUMBER, CellKind.TEXT,
                CellKind.FORMULA), cells.stream().map(InputCell::kind).toList());

        ScenarioInterpreter.Interpretation fromXlsx = ScenarioInterpreter.interpret(xlsx, FILES);
        WorkbookData json = new JsonWorkbookReader().read(InputSource.ofText("book.json", JSON)).workbook();
        ScenarioInterpreter.Interpretation fromJson = ScenarioInterpreter.interpret(json, FILES);

        ScenarioSpec spec = fromXlsx.spec();
        List<ValueCell> values = spec.sheet("A").orElseThrow().cells();
        assertEquals(List.of("x", "", "a b", "y", "\"FW_\"&\"Optional\""), values.stream().map(ValueCell::text).toList());
        assertEquals(List.of(ValueKind.TEXT, ValueKind.EMPTY, ValueKind.FILE, ValueKind.TEXT, ValueKind.FORMULA),
                values.stream().map(ValueCell::kind).toList());
        assertTrue(values.get(0).optional(), "the formatted FW_Optional marks x");
        assertTrue(values.get(2).refined(), "the formatted FW_RefineCodeExceptQuoted refines the file value");

        // the JSON strings mean the same; a JSON string cannot be a formula, so the workbook has one value more
        List<ValueCell> jsonValues = fromJson.spec().sheet("A").orElseThrow().cells();
        assertEquals(values.subList(0, 4).stream().map(v -> v.text() + v.kind() + v.optional() + v.refined()).toList(),
                jsonValues.subList(0, 4).stream().map(v -> v.text() + v.kind() + v.optional() + v.refined()).toList());
        // the digits after FW_Optional, FW_EMPTY_STRING and FW_RefineCodeExceptQuoted are ignored with a warning each
        List<ErrorCode> trailing = List.of(ErrorCode.SPEC_DIRECTIVE_SYNTAX, ErrorCode.SPEC_DIRECTIVE_SYNTAX, ErrorCode.SPEC_DIRECTIVE_SYNTAX);
        assertEquals(trailing, codes(fromJson.diagnostics()));
        assertEquals(java.util.stream.Stream.concat(trailing.stream(), java.util.stream.Stream.of(ErrorCode.SPEC_FORMULA_AS_TEXT)).toList(),
                codes(fromXlsx.diagnostics()));
        assertEquals(List.of("f2.txt"), asked);

        // the census predicate counts exactly the values the interpreter issued
        long counted = cells.stream().mapToLong(ScenarioInterpreter::keysConsumedBy).sum();
        assertEquals(values.size(), counted);
        assertEquals(spec.valueCount(), counted);
    }

    private static List<ErrorCode> codes(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(Diagnostic::code).toList();
    }
}
