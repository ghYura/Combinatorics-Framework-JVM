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

import org.ghyura.cf.analysis.ScoreBreakdown;
import org.ghyura.cf.analysis.TextFeatures;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The scored lines in the legacy analyzer's result table: its columns, types and number formats. */
final class ScoreTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private static final String[] HEADERS = {"Line #", "Score", "Decision", "Original", "Type", "Length", "Entropy", "Tokens", "Keys",
            "Numbers", "Notes"};

    private transient List<ScoreBreakdown> rows = List.of();

    void set(List<ScoreBreakdown> lines) {
        rows = new ArrayList<>(lines);
        fireTableDataChanged();
    }

    ScoreBreakdown get(int row) {
        return rows.get(row);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return HEADERS.length;
    }

    @Override
    public String getColumnName(int column) {
        return HEADERS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case 0, 5, 7, 8, 9 -> Integer.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        ScoreBreakdown r = rows.get(row);
        TextFeatures f = r.features();
        return switch (column) {
            case 0 -> r.lineNo();
            case 1 -> String.format(Locale.ROOT, "%.2f", r.score());
            case 2 -> r.decision();
            case 3 -> r.originalLine();
            case 4 -> f.lineType();
            case 5 -> f.length();
            case 6 -> String.format(Locale.ROOT, "%.3f", f.entropy());
            case 7 -> f.tokens().size();
            case 8 -> f.keyValues().size();
            case 9 -> f.numbers().size();
            case 10 -> String.join(" ; ", r.notes());
            default -> "";
        };
    }
}
