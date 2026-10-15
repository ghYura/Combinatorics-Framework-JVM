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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Authoring (A18): the editable document keeps the workbook exactly, edits as the interpreter reads them, and validates unsaved. */
class ScenarioDocumentTest {

    @Test
    void aSaveSnapshotKeepsItsCellsRowsAndSheetNamesWhileTheEditorChanges(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        ScenarioDocument live = ScenarioDocument.create();
        live.addSheet("Data");
        live.setCell(1, 0, 0, "original");
        ScenarioDocument snapshot = live.copy();
        live.setCell(1, 0, 0, "changed");
        live.insertRow(1, 0);
        live.renameSheet(1, "Renamed");
        snapshot.save(dir.resolve("snapshot.json"));
        ScenarioDocument saved = ScenarioDocument.read(dir.resolve("snapshot.json"));
        assertEquals("Data", saved.sheetNames().get(1));
        assertEquals("original", saved.cell(1, 0, 0));
        assertEquals("Renamed", live.sheetNames().get(1));
        assertEquals("changed", live.cell(1, 1, 0));
        snapshot.setCell(1, 0, 0, "snapshot edit");
        assertEquals("changed", live.cell(1, 1, 0));
    }

    private static final String JSON = "{\"sheets\": {\"FW_Seq\": [[\"HEAD\", \"FW_Combi(1)\"], [\"X\", \"FW_Combi(1)\"], [\"TAIL\", \"FW_Combi(1)\"]], "
            + "\"HEAD\": [[\"public class M { public static int FW_VAR = -1; public static void main(String[] a) {\"]], "
            + "\"X\": [[\"FW_VAR = 0;\"], [\"FW_VAR = FW_EXIT_CODE;\"], [\"\"]], \"TAIL\": [[\"} }\"]], "
            + "\"FW_CUSTOM_VAR\": [[2, \"CARRIED_BY_X\"]]}}";

    @Test
    void everyFormatRoundTripsTheCanonicalWorkbookAndUnsavedValidationEqualsTheSavedFile(@TempDir Path dir) throws Exception {
        Path json = Files.writeString(dir.resolve("mini.json"), JSON, StandardCharsets.UTF_8);
        String canonical = WorkbookFormats.readFile(json).workbook().canonicalText();
        ScenarioDocument document = ScenarioDocument.read(json);
        assertEquals(canonical, document.toWorkbook(json.toString()).canonicalText(), "an unedited document is the workbook it read");
        assertEquals("", document.cell(document.sheetNames().indexOf("X"), 2, 0), "a present empty cell stays present");
        for (String name : List.of("copy.yaml", "copy.toml", "copy.xlsx", "copy.json")) {
            Path copy = dir.resolve(name);
            document.save(copy);
            assertEquals(canonical, WorkbookFormats.readFile(copy).workbook().canonicalText(), name);
            assertEquals(canonical, ScenarioDocument.read(copy).toWorkbook(name).canonicalText(), name);
        }

        Session session = SessionTest.session();
        ScenarioValidation.Summary fromFile = session.validate(json);
        ScenarioValidation.Summary unsaved = session.validate(document, dir.resolve("unsaved.json"));
        assertEquals(fromFile.spec().contentDigest(), unsaved.spec().contentDigest());
        assertEquals(fromFile.plan().contentDigest(), unsaved.plan().contentDigest());

        int x = document.sheetNames().indexOf("X");
        document.setCell(x, 1, 0, "FW_VAR = 3;");
        document.setCell(x, 2, 0, "");
        assertNull(document.cell(x, 2, 0), "a cleared cell is removed");
        document.addSheet("Y");
        assertThrows(IllegalArgumentException.class, () -> document.addSheet("y"), "sheet names are unique ignoring case");
        assertThrows(IllegalArgumentException.class, () -> document.renameSheet(x, "FW_SEQ"));
        int seq = document.sheetNames().indexOf("FW_Seq");
        document.insertRow(seq, 2);
        document.setCell(seq, 2, 0, "Y");
        document.setCell(seq, 2, 1, "FW_Combi(1)");
        document.setCell(document.sheetNames().indexOf("Y"), 4, 1, "y5");
        assertEquals(5, document.rowCount(document.sheetNames().indexOf("Y")));
        assertEquals(2, document.columnCount(document.sheetNames().indexOf("Y")));
        Path edited = dir.resolve("edited.yaml");
        document.save(edited);
        ScenarioValidation.Summary saved = session.validate(edited);
        ScenarioValidation.Summary inMemory = session.validate(document, edited);
        assertEquals(saved.spec().contentDigest(), inMemory.spec().contentDigest());
        assertEquals(saved.plan().contentDigest(), inMemory.plan().contentDigest());
        assertEquals(4, saved.plan().activeSheets().size(), "HEAD, X, Y and TAIL have directives");
        document.removeRow(seq, 2);
        document.removeSheet(document.sheetNames().indexOf("Y"));
        document.setCell(x, 1, 0, "FW_VAR = FW_EXIT_CODE;");
        assertNotEquals(session.validate(json).spec().contentDigest(), session.validate(document, json).spec().contentDigest(),
                "X's cleared empty cell no longer exists, so it has no key");
        assertEquals(session.validate(json).spec().contentDigest(), session.validate(ScenarioDocument.read(json), json).spec().contentDigest());
    }
}
