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

import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Writes the model as an XLSX package. Values are written as string cells (a numeric cell read from a workbook
 * comes back as the text POI rendered for it), formula cells as formulas and blank cells as blanks. The XLSX
 * limits are enforced here and nowhere else in the framework: a sheet name must be a valid Excel sheet name
 * (at most 31 characters, none of {@code \ / ? * [ ] :}) and a cell holds at most 32767 characters.
 */
public final class XlsxWorkbookWriter implements WorkbookWriter {

    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private static final String FORBIDDEN_IN_SHEET_NAME = "\\/?*[]:";

    @Override
    public InputFormat format() {
        return InputFormat.XLSX;
    }

    @Override
    public byte[] write(WorkbookData data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (InputSheet source : data.sheets()) {
                String problem = invalidSheetName(source.name());
                if (problem != null) {
                    throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_LIMIT,
                            "the sheet name '" + source.name() + "' is not valid in an XLSX workbook: " + problem,
                            source.location(), "sheet", source.name()));
                }
                Sheet sheet;
                try {
                    sheet = workbook.createSheet(source.name());
                } catch (IllegalArgumentException e) {
                    throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_LIMIT,
                            "the sheet name '" + source.name() + "' is not valid in an XLSX workbook: " + e.getMessage(),
                            source.location(), "sheet", source.name()), e);
                }
                for (InputRow sourceRow : source.rows()) {
                    Row row = sheet.createRow(sourceRow.sourceRow());
                    for (InputCell cell : sourceRow.cells()) {
                        write(row, cell);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, String.valueOf(e.getMessage()),
                    org.ghyura.cf.model.SourceLocation.of(data.source())), e);
        }
    }

    /**
     * Excel's own rules for a sheet name; POI 5.5.1 does not enforce the length, so a model that came from a text
     * format with a longer name would otherwise write a file Excel refuses to open.
     */
    static String invalidSheetName(String name) {
        if (name.isEmpty() || name.length() > MAX_SHEET_NAME_LENGTH) {
            return "it has " + name.length() + " characters and Excel allows 1 to " + MAX_SHEET_NAME_LENGTH;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (FORBIDDEN_IN_SHEET_NAME.indexOf(c) >= 0) {
                return "the character '" + c + "' at position " + (i + 1) + " is not allowed";
            }
        }
        if (name.charAt(0) == '\'' || name.charAt(name.length() - 1) == '\'') {
            return "it must not start or end with an apostrophe";
        }
        if (name.equalsIgnoreCase("History")) {
            return "'History' is reserved by Excel";
        }
        return null;
    }

    private static void write(Row row, InputCell source) {
        try {
            switch (source.kind()) {
                case BLANK -> row.createCell(source.column(), CellType.BLANK);
                case FORMULA -> {
                    Cell cell = row.createCell(source.column());
                    cell.setCellFormula(source.text());
                }
                default -> row.createCell(source.column()).setCellValue(source.text());
            }
        } catch (FormulaParseException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_SCHEMA,
                    "the formula '" + source.text() + "' cannot be written: " + e.getMessage(), source.location()), e);
        } catch (IllegalArgumentException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_LIMIT,
                    "the cell cannot be written to XLSX: " + e.getMessage(), source.location()), e);
        }
    }
}
