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
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.ghyura.cf.input.InputTestSupport.expectCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gate A03, format side: what one writer writes, every reader of that format reads back as the same logical model. */
class WorkbookWritersTest {

    private static final SourceLocation HERE = SourceLocation.of("test");

    private static InputCell text(int column, String text) {
        return new InputCell(column, CellKind.TEXT, text, HERE);
    }

    private static WorkbookData tricky() {
        return new WorkbookData("tricky", List.of(
                new InputSheet("FW_Seq", HERE, List.of(
                        new InputRow(0, List.of(text(0, "HEAD"), text(2, "FW_Reuse"), text(3, "FW_Combi(1)"))),
                        new InputRow(1, List.of()),
                        new InputRow(2, List.of(text(4, "later"))))),
                new InputSheet("EMPTY", HERE, List.of()),
                new InputSheet("Payload_1", HERE, List.of(
                        new InputRow(0, List.of(text(0, "quote \" backslash \\ tab \t newline \n cr \r end"))),
                        new InputRow(1, List.of(text(0, "\u0431\u0443\u0434\u044c \uD83D\uDE00 caf\u00e9"), text(1, ""))),
                        new InputRow(2, List.of(text(0, "x,]"), text(1, "a # b"), text(2, " leading and trailing "))),
                        new InputRow(3, List.of(text(0, "# D3 code\nstart(\"correct\")\n")))))));
    }

    @Test
    void everyFormatRoundTripsATrickyModelToTheSameLogicalContent() {
        WorkbookData original = tricky();
        for (InputFormat format : InputFormat.values()) {
            byte[] written = WorkbookFormats.writer(format).write(original);
            WorkbookData back = WorkbookFormats.reader(format).read(InputSource.of("round." + format.token(), written)).workbook();
            assertEquals(original.canonicalText(), back.canonicalText(), format.token());
            assertEquals(original.sheetNames(), back.sheetNames(), format.token());
        }
    }

    @Test
    void theBuilderScenarioRoundTripsThroughEveryFormatAndEqualsItself() {
        WorkbookData built = ScheduleBuilderTest.legacyExample().build();
        for (InputFormat format : InputFormat.values()) {
            byte[] written = WorkbookFormats.writer(format).write(built);
            WorkbookData back = WorkbookFormats.reader(format).read(InputSource.of("built." + format.token(), written)).workbook();
            assertEquals(built.canonicalText(), back.canonicalText(), format.token());
            assertEquals(built.contentDigest(), back.contentDigest(), format.token());
        }
    }

    @Test
    void theJsonWriterProducesExactlyThisText() {
        WorkbookData workbook = new WorkbookData("x", List.of(
                new InputSheet("A", HERE, List.of(new InputRow(0, List.of(text(0, "a"), text(2, "b\"c"))), new InputRow(1, List.of()))),
                new InputSheet("B", HERE, List.of())));
        String expected = "{\n  \"sheets\": {\n    \"A\": [\n      [\"a\", null, \"b\\\"c\"],\n      []\n    ],\n    \"B\": []\n  }\n}\n";
        assertEquals(expected, new String(TextWorkbookWriters.json().write(workbook), StandardCharsets.UTF_8));
        assertEquals("{\n  \"sheets\": {}\n}\n", new String(TextWorkbookWriters.json().write(new WorkbookData("e", List.of())),
                StandardCharsets.UTF_8));
    }

    @Test
    void theYamlAndTomlWritersProduceExactlyThisText() {
        WorkbookData workbook = new WorkbookData("x", List.of(
                new InputSheet("A", HERE, List.of(new InputRow(0, List.of(text(1, "b"))), new InputRow(1, List.of()))),
                new InputSheet("B-2", HERE, List.of())));
        assertEquals("[sheets]\nA = [\n  [null, \"b\"],\n  [],\n]\nB-2 = []\n",
                new String(TextWorkbookWriters.toml().write(workbook), StandardCharsets.UTF_8));
        WorkbookData yamlSafe = new WorkbookData("x", List.of(workbook.sheets().getFirst(),
                new InputSheet("B_2", HERE, List.of())));
        assertEquals("sheets:\n  A:\n    - [null, \"b\"]\n    - []\n  B_2:\n",
                new String(TextWorkbookWriters.yaml().write(yamlSafe), StandardCharsets.UTF_8));
    }

    @Test
    void whatAFormatCannotRepresentIsRefusedNotAltered() {
        WorkbookData formula = new WorkbookData("x", List.of(new InputSheet("S", HERE,
                List.of(new InputRow(0, List.of(new InputCell(0, CellKind.FORMULA, "SUM(A1)", SourceLocation.ofPath("b.xlsx", "S!A1"))))))));
        for (InputFormat format : List.of(InputFormat.JSON, InputFormat.YAML, InputFormat.TOML)) {
            assertEquals("S!A1", expectCode(ErrorCode.INPUT_UNSUPPORTED_SYNTAX,
                    () -> WorkbookFormats.writer(format).write(formula)).primary().location().path());
        }
        expectCode(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, () -> TextWorkbookWriters.yaml().write(
                new WorkbookData("x", List.of(new InputSheet("my-sheet", HERE, List.of())))));
        expectCode(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, () -> TextWorkbookWriters.yaml().write(
                new WorkbookData("x", List.of(new InputSheet("1st", HERE, List.of())))));
        expectCode(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, () -> TextWorkbookWriters.toml().write(
                new WorkbookData("x", List.of(new InputSheet("has space", HERE, List.of())))));
    }

    @Test
    void xlsxKeepsFormulasAndEnforcesItsOwnLimitsOnlyHere() {
        WorkbookData formula = new WorkbookData("x", List.of(new InputSheet("S", HERE, List.of(new InputRow(0, List.of(
                text(0, "a"), new InputCell(1, CellKind.FORMULA, "SUM(A1:A3)", HERE), new InputCell(2, CellKind.BLANK, "", HERE)))))));
        WorkbookData back = new XlsxWorkbookReader().read(InputSource.of("f.xlsx", new XlsxWorkbookWriter().write(formula))).workbook();
        assertEquals(formula.canonicalText(), back.canonicalText());
        assertEquals(CellKind.FORMULA, back.sheet("S").orElseThrow().rows().getFirst().cells().get(1).kind());
        assertEquals(CellKind.BLANK, back.sheet("S").orElseThrow().rows().getFirst().cells().get(2).kind());

        String longName = "N".repeat(32);
        expectCode(ErrorCode.INPUT_LIMIT, () -> new XlsxWorkbookWriter().write(new WorkbookData("x", List.of(new InputSheet(longName, HERE, List.of())))));
        expectCode(ErrorCode.INPUT_LIMIT, () -> new XlsxWorkbookWriter().write(new WorkbookData("x", List.of(new InputSheet("a:b", HERE, List.of())))));
        WorkbookData huge = new WorkbookData("x", List.of(new InputSheet("S", HERE, List.of(new InputRow(0, List.of(text(0, "z".repeat(32_768))))))));
        expectCode(ErrorCode.INPUT_LIMIT, () -> new XlsxWorkbookWriter().write(huge));
        // the same model is fine in a text format: the limit belongs to the XLSX adapter only
        assertEquals(huge.canonicalText(), WorkbookFormats.reader(InputFormat.JSON)
                .read(InputSource.of("h.json", TextWorkbookWriters.json().write(huge))).workbook().canonicalText());
        expectCode(ErrorCode.INPUT_SCHEMA, () -> new XlsxWorkbookWriter().write(new WorkbookData("x", List.of(new InputSheet("S", HERE,
                List.of(new InputRow(0, List.of(new InputCell(0, CellKind.FORMULA, "SUM((", HERE)))))))));
    }

    @Test
    void formatsAreChosenByExtensionWithoutALegacyFallbackToXlsx() {
        assertEquals(InputFormat.JSON, WorkbookFormats.formatOf("a.JSON"));
        assertEquals(InputFormat.YAML, WorkbookFormats.formatOf("dir/a.yml"));
        assertEquals(InputFormat.YAML, WorkbookFormats.formatOf("a.yaml"));
        assertEquals(InputFormat.TOML, WorkbookFormats.formatOf("a.toml"));
        assertEquals(InputFormat.XLSX, WorkbookFormats.formatOf("a.xlsm"));
        assertEquals(InputFormat.XLSX, WorkbookFormats.formatOf("demo.XLSX"));
        for (String name : List.of("a.txt", "a", "a.xls", "scenario.json.bak", "a.csv")) {
            assertTrue(expectCode(ErrorCode.INPUT_UNRECOGNIZED_FORMAT, () -> WorkbookFormats.formatOf(name))
                    .primary().message().contains(name));
        }
        assertEquals(InputFormat.JSON, InputFormat.fromToken(" JSON ").orElseThrow());
        assertEquals(InputFormat.YAML, InputFormat.fromToken("yml").orElseThrow());
        assertEquals(InputFormat.XLSX, InputFormat.fromToken("xlsm").orElseThrow());
        assertTrue(InputFormat.fromToken("csv").isEmpty());
    }
}
