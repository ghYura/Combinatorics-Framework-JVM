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

import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SheetPlan;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.stream.Collectors;

/** A validated plan's sheets as a table: key, name, role, whether active and virtual, values, reuse and directives. */
final class PlanTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private static final String[] HEADERS = {"Key", "Sheet", "Role", "Active", "Virtual", "Values", "Reuse", "Directives"};

    private transient List<SheetPlan> sheets = List.of();

    void show(SeqPlan plan) {
        sheets = plan == null ? List.of() : List.copyOf(plan.sheets());
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return sheets.size();
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
    public Object getValueAt(int row, int column) {
        SheetPlan s = sheets.get(row);
        return switch (column) {
            case 0 -> s.key();
            case 1 -> s.name();
            case 2 -> s.role();
            case 3 -> s.active();
            case 4 -> s.virtual();
            case 5 -> s.valueCount();
            case 6 -> s.reuse();
            case 7 -> s.directives().stream().map(Directive::normalized).collect(Collectors.joining("  "));
            default -> "";
        };
    }
}
