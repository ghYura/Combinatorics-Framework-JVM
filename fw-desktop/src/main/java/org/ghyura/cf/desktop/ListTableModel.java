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

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.function.Function;

/** Records of one kind as a table: a header and a value per column. Confined to the event dispatch thread. */
final class ListTableModel<T> extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private final String[] headers;
    private final transient List<Function<T, Object>> columns;
    private transient List<T> rows = List.of();

    ListTableModel(List<String> headers, List<Function<T, Object>> columns) {
        if (headers.size() != columns.size()) {
            throw new IllegalArgumentException(headers.size() + " headers for " + columns.size() + " columns");
        }
        this.headers = headers.toArray(String[]::new);
        this.columns = List.copyOf(columns);
    }

    void show(List<T> shown) {
        rows = shown == null ? List.of() : List.copyOf(shown);
        fireTableDataChanged();
    }

    List<T> rows() {
        return rows;
    }

    T row(int index) {
        return rows.get(index);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public String getColumnName(int column) {
        return headers[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        return columns.get(column).apply(rows.get(row));
    }
}
