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

package org.ghyura.cf.input;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * The only place the tests touch POI's number-format API, whose method signatures carry the 16-bit integer type
 * that rule R04 forbids in owned code. The value passes straight from one POI call to the next and never reaches
 * a variable, field or cast of owned code. ByteTypeGateTest approves exactly this class, and only for calls into
 * {@code org.apache.poi}; anything else in it is still a finding.
 */
final class PoiStyles {

    private PoiStyles() {
    }

    /** A cell style that renders numbers with the given Excel format string, for example {@code #,##0.00}. */
    static CellStyle withFormat(XSSFWorkbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }
}
