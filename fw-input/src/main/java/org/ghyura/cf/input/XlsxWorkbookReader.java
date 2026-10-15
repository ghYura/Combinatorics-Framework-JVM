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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads an OOXML workbook (.xlsx, .xlsm) the way the legacy {@code WorkbookParser} saw it: sheets in workbook
 * order, physical rows in row order, and the cells that exist in each row in column order. A cell's text is what
 * POI's {@link DataFormatter} renders for it (so a numeric 3 is {@code 3}); a formula cell yields its formula
 * text, never its result.
 *
 * <p>Two deliberate differences from the legacy reader: the document is opened from its bytes, never from a
 * path (POI's path-based factory re-saves the file on close, which would rewrite the user's input), and the
 * formatter uses the root locale, so the text of a number or date does not change with the machine's regional
 * settings.</p>
 */
public final class XlsxWorkbookReader implements WorkbookReader {

    /** The ZIP local-file header every OOXML package starts with. */
    private static final byte[] ZIP_SIGNATURE = {'P', 'K', 3, 4};

    @Override
    public InputFormat format() {
        return InputFormat.XLSX;
    }

    @Override
    public ReadResult read(InputSource source) {
        String origin = source.name();
        byte[] bytes = source.view();
        if (bytes.length < ZIP_SIGNATURE.length || bytes[0] != ZIP_SIGNATURE[0] || bytes[1] != ZIP_SIGNATURE[1]
                || bytes[2] != ZIP_SIGNATURE[2] || bytes[3] != ZIP_SIGNATURE[3]) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED,
                    "not an OOXML workbook: the ZIP signature is missing", SourceLocation.of(origin)));
        }
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            List<InputSheet> sheets = new ArrayList<>();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                sheets.add(readSheet(workbook.getSheetAt(s), origin, formatter));
            }
            return new ReadResult(new WorkbookData(Utf8.displayName("xlsx", origin), sheets), List.of());
        } catch (FrameworkException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED,
                    "the workbook cannot be read (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")",
                    SourceLocation.of(origin)), e);
        }
    }

    private static InputSheet readSheet(Sheet sheet, String origin, DataFormatter formatter) {
        String name = sheet.getSheetName();
        List<InputRow> rows = new ArrayList<>();
        for (Row row : sheet) {
            List<InputCell> cells = new ArrayList<>();
            for (Cell cell : row) {
                String reference = new CellReference(name, cell.getRowIndex(), cell.getColumnIndex(), false, false)
                        .formatAsString();
                SourceLocation where = SourceLocation.ofPath(origin, reference);
                CellType type = cell.getCellType();
                switch (type) {
                    case FORMULA -> cells.add(new InputCell(cell.getColumnIndex(), CellKind.FORMULA, cell.getCellFormula(), where));
                    case STRING -> cells.add(new InputCell(cell.getColumnIndex(), CellKind.TEXT, formatter.formatCellValue(cell), where));
                    case NUMERIC -> cells.add(new InputCell(cell.getColumnIndex(), CellKind.NUMBER, formatter.formatCellValue(cell), where));
                    case BOOLEAN -> cells.add(new InputCell(cell.getColumnIndex(), CellKind.BOOLEAN, formatter.formatCellValue(cell), where));
                    case ERROR -> cells.add(new InputCell(cell.getColumnIndex(), CellKind.ERROR, formatter.formatCellValue(cell), where));
                    case BLANK -> cells.add(new InputCell(cell.getColumnIndex(), CellKind.BLANK, "", where));
                    default -> throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED,
                            "cell " + reference + " has the unexpected type " + type, where));
                }
            }
            rows.add(new InputRow(row.getRowNum(), cells));
        }
        return new InputSheet(name, SourceLocation.ofPath(origin, "'" + name + "'"), rows);
    }
}
