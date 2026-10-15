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

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Helpers shared by the fw-input tests. */
final class InputTestSupport {

    private InputTestSupport() {
    }

    static WorkbookData read(WorkbookReader reader, String name, String text) {
        return reader.read(InputSource.ofText(name, text)).workbook();
    }

    static FrameworkException expectCode(ErrorCode expected, Executable action) {
        FrameworkException thrown = assertThrows(FrameworkException.class, action);
        assertEquals(expected, thrown.code(), () -> "unexpected diagnostic: " + thrown.primary());
        return thrown;
    }

    /** Sheet names and, per sheet, per row, the cell texts (columns ignored): a compact view for assertions. */
    static List<String> shape(WorkbookData workbook) {
        List<String> out = new ArrayList<>();
        for (InputSheet sheet : workbook.sheets()) {
            StringBuilder line = new StringBuilder(sheet.name()).append(" =");
            for (InputRow row : sheet.rows()) {
                line.append(" [");
                for (int i = 0; i < row.cells().size(); i++) {
                    line.append(i == 0 ? "" : "|").append(row.cells().get(i).column()).append(':').append(row.cells().get(i).text());
                }
                line.append(']');
            }
            out.add(line.toString());
        }
        return out;
    }

    static InputCell cell(WorkbookData workbook, String sheet, int row, int column) {
        InputSheet s = workbook.sheet(sheet).orElseThrow();
        for (InputCell c : s.rows().get(row).cells()) {
            if (c.column() == column) {
                return c;
            }
        }
        throw new AssertionError("no cell at " + sheet + " row " + row + " column " + column);
    }

    static SourceLocation locationOf(WorkbookData workbook, String sheet, int row, int column) {
        return cell(workbook, sheet, row, column).location();
    }
}
