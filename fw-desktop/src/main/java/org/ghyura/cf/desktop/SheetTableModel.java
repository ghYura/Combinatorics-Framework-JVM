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

package org.ghyura.cf.desktop;

import org.ghyura.cf.desktop.session.ScenarioDocument;

import javax.swing.table.AbstractTableModel;

/**
 * One sheet of a scenario document as an editable grid: the present rows and columns plus one spare row and column to type
 * into. Column headers are spreadsheet letters; a cleared cell is removed from the document.
 */
final class SheetTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private transient ScenarioDocument document;
    private int sheet = -1;

    void show(ScenarioDocument shown, int sheetIndex) {
        document = shown;
        sheet = sheetIndex;
        fireTableStructureChanged();
    }

    int sheet() {
        return sheet;
    }

    @Override
    public int getRowCount() {
        return sheet < 0 ? 0 : document.rowCount(sheet) + 1;
    }

    @Override
    public int getColumnCount() {
        return sheet < 0 ? 0 : Math.max(3, document.columnCount(sheet) + 1);
    }

    @Override
    public String getColumnName(int column) {
        StringBuilder name = new StringBuilder();
        for (int c = column + 1; c > 0; c = (c - 1) / 26) {
            name.insert(0, (char) ('A' + (c - 1) % 26));
        }
        return name.toString();
    }

    @Override
    public Object getValueAt(int row, int column) {
        String text = document.cell(sheet, row, column);
        return text == null ? "" : text;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return true;
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        String text = value == null ? "" : value.toString();
        if (text.equals(getValueAt(row, column))) {
            return;
        }
        int rows = getRowCount();
        int columns = getColumnCount();
        document.setCell(sheet, row, column, text);
        if (getRowCount() != rows || getColumnCount() != columns) {
            fireTableStructureChanged();
        } else {
            fireTableCellUpdated(row, column);
        }
    }
}
