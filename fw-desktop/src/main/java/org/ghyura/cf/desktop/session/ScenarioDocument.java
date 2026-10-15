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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.input.WorkbookFormats;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * An editable scenario workbook for authoring: ordered sheets of rows of cells, read from and written to any input format.
 * It keeps what the interpreter distinguishes: a row or cell that is absent differs from one that is present and empty
 * (the legacy parser gives an empty cell a key), and a cell keeps the kind it was read with (number, boolean, formula,
 * blank) until it is edited, which makes it text; clearing a cell removes it. An unedited document writes the same
 * workbook it read ({@link WorkbookData#canonicalText()}). Not thread-safe: an editor uses it on one thread.
 */
public final class ScenarioDocument {

    private record Cell(CellKind kind, String text) {
    }

    /** One sheet: its name and rows by index (row i is source row i + 1); a null row is absent. */
    private static final class Sheet {
        String name;
        final List<TreeMap<Integer, Cell>> rows = new ArrayList<>();

        Sheet(String name) {
            this.name = name;
        }
    }

    private final List<Sheet> sheets = new ArrayList<>();

    private ScenarioDocument() {
    }

    /** A new scenario: an empty FW_Seq sheet. */
    public static ScenarioDocument create() {
        ScenarioDocument d = new ScenarioDocument();
        d.sheets.add(new Sheet("FW_Seq"));
        return d;
    }

    /** @throws FrameworkException INPUT_* when the file cannot be read in its format */
    public static ScenarioDocument read(Path file) {
        return of(WorkbookFormats.readFile(file).workbook());
    }

    /** Snapshot on the document owner's thread before handing it to a background reader or writer. */
    public ScenarioDocument copy() {
        ScenarioDocument copy = new ScenarioDocument();
        for (Sheet sheet : sheets) {
            Sheet cloned = new Sheet(sheet.name);
            for (TreeMap<Integer, Cell> row : sheet.rows) cloned.rows.add(row == null ? null : new TreeMap<>(row));
            copy.sheets.add(cloned);
        }
        return copy;
    }

    public static ScenarioDocument of(WorkbookData workbook) {
        ScenarioDocument d = new ScenarioDocument();
        for (InputSheet in : workbook.sheets()) {
            Sheet sheet = new Sheet(in.name());
            for (InputRow row : in.rows()) {
                while (sheet.rows.size() < row.sourceRow() - 1) {
                    sheet.rows.add(null);
                }
                TreeMap<Integer, Cell> cells = new TreeMap<>();
                for (InputCell c : row.cells()) {
                    cells.put(c.column(), new Cell(c.kind(), c.text()));
                }
                sheet.rows.add(cells);
            }
            d.sheets.add(sheet);
        }
        return d;
    }

    /** The workbook this document means, every location naming {@code source}. */
    public WorkbookData toWorkbook(String source) {
        List<InputSheet> out = new ArrayList<>(sheets.size());
        for (Sheet sheet : sheets) {
            List<InputRow> rows = new ArrayList<>();
            for (int r = 0; r < sheet.rows.size(); r++) {
                TreeMap<Integer, Cell> cells = sheet.rows.get(r);
                if (cells == null) {
                    continue;
                }
                List<InputCell> row = new ArrayList<>(cells.size());
                for (var e : cells.entrySet()) {
                    row.add(new InputCell(e.getKey(), e.getValue().kind(), e.getValue().text(),
                            SourceLocation.of(source + "#" + sheet.name, r + 1, e.getKey() + 1)));
                }
                rows.add(new InputRow(r + 1, row));
            }
            out.add(new InputSheet(sheet.name, SourceLocation.of(source + "#" + sheet.name), rows));
        }
        return new WorkbookData(source, out);
    }

    /**
     * Writes the document in the format its extension names, replacing the file only once complete.
     *
     * @throws FrameworkException INPUT_UNRECOGNIZED_FORMAT, INPUT_* when the format cannot represent the document, or
     *                            INPUT_UNREADABLE when the file cannot be written
     */
    public void save(Path file) {
        byte[] bytes = WorkbookFormats.writer(WorkbookFormats.formatOf(file.getFileName().toString())).write(toWorkbook(file.toString()));
        try {
            AtomicFiles.write(file, bytes);
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, "the scenario cannot be written: " + e.getMessage(),
                    SourceLocation.of(file.toString())));
        }
    }

    public List<String> sheetNames() {
        return sheets.stream().map(s -> s.name).toList();
    }

    public int sheetCount() {
        return sheets.size();
    }

    /** Adds a sheet at the end. @throws IllegalArgumentException for a blank name or one already used, ignoring case */
    public void addSheet(String name) {
        checkName(name, -1);
        sheets.add(new Sheet(name));
    }

    public void renameSheet(int sheet, String name) {
        checkName(name, sheet);
        sheets.get(sheet).name = name;
    }

    public void removeSheet(int sheet) {
        sheets.remove(sheet);
    }

    private void checkName(String name, int except) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a sheet needs a name");
        }
        for (int i = 0; i < sheets.size(); i++) {
            if (i != except && sheets.get(i).name.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("the workbook already has a sheet '" + sheets.get(i).name + "'");
            }
        }
    }

    /** Rows up to the last present one. */
    public int rowCount(int sheet) {
        return sheets.get(sheet).rows.size();
    }

    /** Columns up to the last present cell of any row. */
    public int columnCount(int sheet) {
        int columns = 0;
        for (TreeMap<Integer, Cell> row : sheets.get(sheet).rows) {
            if (row != null && !row.isEmpty()) {
                columns = Math.max(columns, row.lastKey() + 1);
            }
        }
        return columns;
    }

    /** The cell's text, or null when the cell is absent. */
    public String cell(int sheet, int row, int column) {
        List<TreeMap<Integer, Cell>> rows = sheets.get(sheet).rows;
        if (row >= rows.size() || rows.get(row) == null) {
            return null;
        }
        Cell c = rows.get(row).get(column);
        return c == null ? null : c.text();
    }

    /** Sets a cell's text as an edited text cell; null or empty text removes the cell. Rows are created as needed. */
    public void setCell(int sheet, int row, int column, String text) {
        List<TreeMap<Integer, Cell>> rows = sheets.get(sheet).rows;
        if (text == null || text.isEmpty()) {
            if (row < rows.size() && rows.get(row) != null) {
                rows.get(row).remove(column);
            }
            return;
        }
        while (rows.size() <= row) {
            rows.add(null);
        }
        if (rows.get(row) == null) {
            rows.set(row, new TreeMap<>());
        }
        rows.get(row).put(column, new Cell(CellKind.TEXT, text));
    }

    /** Inserts an absent row before {@code row}; the rows below move down. */
    public void insertRow(int sheet, int row) {
        List<TreeMap<Integer, Cell>> rows = sheets.get(sheet).rows;
        if (row <= rows.size()) {
            rows.add(row, null);
        }
    }

    /** Removes a row; the rows below move up. */
    public void removeRow(int sheet, int row) {
        List<TreeMap<Integer, Cell>> rows = sheets.get(sheet).rows;
        if (row < rows.size()) {
            rows.remove(row);
        }
    }
}
