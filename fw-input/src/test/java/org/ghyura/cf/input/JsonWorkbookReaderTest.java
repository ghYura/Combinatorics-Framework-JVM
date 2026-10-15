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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.ghyura.cf.input.InputTestSupport.cell;
import static org.ghyura.cf.input.InputTestSupport.expectCode;
import static org.ghyura.cf.input.InputTestSupport.locationOf;
import static org.ghyura.cf.input.InputTestSupport.read;
import static org.ghyura.cf.input.InputTestSupport.shape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonWorkbookReaderTest {

    private static final JsonWorkbookReader READER = new JsonWorkbookReader();

    /** JSON string bodies including escapes, so the differential test exercises unescaping too. */
    private static final String[] STRING_BODIES = {"abc", "é", "line\\nbreak", "tab\\there", "\\u00e9\\u4e2d",
            "quote\\\"d", "back\\\\slash", "", "FW_Optional", "  padded  "};

    /** The schedule documented in the legacy JsonScheduleParser, copied literally. */
    private static final String LEGACY_EXAMPLE = """
            {
              "sheets": {
                "FW_Seq": [
                  ["HEAD", "FW_Exclude", "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"],
                  ["COST", "FW_Exclude", "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"],
                  ["ROW",  null, null, "FW_(HEAD,,COST,,LAT,,TAIL,,M:N)"]
                ],
                "FW_SheetNames": [
                  ["HEAD", "FW_EMPTY_STRING", "FW_EMPTY_STRING"]
                ],
                "FW_RunMeFirstOnce": [
                  ["class RunMeFirstOnce { }"]
                ],
                "FW_Arguments": [["noargs"]],
                "FW_CUSTOM_VAR": [[2, "FWCUSTOMVAR=2 nominal"]],
                "HEAD":  [["scenario=demo"]],
                "COST":  [[" id=1 cost=0.10"], [" id=2 cost=0.30"], [" id=3 cost=0.50"]],
                "ROW":   [["FW_EMPTY_STRING"]]
              }
            }
            """;

    @Test
    void theLegacyDocumentedScheduleReadsToTheExpectedSheetsRowsAndCells() {
        WorkbookData workbook = read(READER, "test_e2e_metric.json", LEGACY_EXAMPLE);
        assertEquals("json:test_e2e_metric.json", workbook.source());
        assertEquals(List.of("FW_Seq", "FW_SheetNames", "FW_RunMeFirstOnce", "FW_Arguments", "FW_CUSTOM_VAR", "HEAD", "COST", "ROW"),
                workbook.sheetNames());
        assertEquals(List.of(
                "FW_Seq = [0:HEAD|1:FW_Exclude|2:FW_Reuse|3:FW_Combi(1)|4:FW_Combi(1)]"
                        + " [0:COST|1:FW_Exclude|2:FW_Reuse|3:FW_Combi(1)|4:FW_Combi(1)]"
                        + " [0:ROW|3:FW_(HEAD,,COST,,LAT,,TAIL,,M:N)]",
                "FW_SheetNames = [0:HEAD|1:FW_EMPTY_STRING|2:FW_EMPTY_STRING]",
                "FW_RunMeFirstOnce = [0:class RunMeFirstOnce { }]",
                "FW_Arguments = [0:noargs]",
                "FW_CUSTOM_VAR = [0:2|1:FWCUSTOMVAR=2 nominal]",
                "HEAD = [0:scenario=demo]",
                "COST = [0: id=1 cost=0.10] [0: id=2 cost=0.30] [0: id=3 cost=0.50]",
                "ROW = [0:FW_EMPTY_STRING]"), shape(workbook));
    }

    @Test
    void anAbsentCellIsNotACellAndGapsKeepTheirColumns() {
        WorkbookData workbook = read(READER, "a.json", "{\"sheets\":{\"S\":[[\"a\",null,null,\"d\"]]}}");
        assertEquals(List.of("S = [0:a|3:d]"), shape(workbook));
        assertEquals(CellKind.TEXT, cell(workbook, "S", 0, 3).kind());
    }

    @Test
    void scalarRowsNullRowsAndEmptyRowsAreAllPhysicalRows() {
        WorkbookData workbook = read(READER, "a.json", "{\"sheets\":{\"S\":[[\"a\"], \"b\", 7, null, []]}}");
        assertEquals(List.of("S = [0:a] [0:b] [0:7] [] []"), shape(workbook));
        assertEquals(5, workbook.sheet("S").orElseThrow().rows().size());
        assertTrue(workbook.sheet("S").orElseThrow().rows().get(3).isEmpty());
    }

    @Test
    void numbersBooleansAndStringsGetTheTextTheLegacyTreeModelGave() {
        String row = "[1, 2.0, 1e3, 12345678901234567890, 0.1, 1.5E-7, true, false, \"x\", \"\", 9223372036854775807]";
        WorkbookData workbook = read(READER, "a.json", "{\"sheets\":{\"S\":[" + row + "]}}");
        List<String> texts = workbook.sheet("S").orElseThrow().rows().getFirst().cells().stream().map(InputCell::text).toList();
        assertEquals(List.of("1", "2.0", "1000.0", "12345678901234567890", "0.1", "1.5E-7", "true", "false", "x", "",
                "9223372036854775807"), texts);
    }

    @Test
    void cellTextEqualsTheLegacyReadTreeAsTextOnRandomScalars() throws Exception {
        ObjectMapper legacyMapper = new ObjectMapper();
        SplittableRandom random = new SplittableRandom(20261002L);
        for (int round = 0; round < 300; round++) {
            List<String> literals = new ArrayList<>();
            int n = 1 + random.nextInt(8);
            for (int i = 0; i < n; i++) {
                literals.add(switch (random.nextInt(8)) {
                    case 0 -> Long.toString(random.nextLong());
                    case 1 -> Integer.toString(random.nextInt(-1000, 1000));
                    case 2 -> Double.toString(random.nextDouble(-1e6, 1e6));
                    case 3 -> random.nextInt(2) == 0 ? "true" : "false";
                    case 4 -> "\"" + STRING_BODIES[random.nextInt(STRING_BODIES.length)] + "\"";
                    case 5 -> random.nextInt(1, 9) + "e" + random.nextInt(-5, 12);
                    case 6 -> random.nextLong(1_000_000_000_000_000L, Long.MAX_VALUE) + "0";
                    default -> "\"" + random.nextInt(100_000) + "\"";
                });
            }
            String json = "{\"sheets\":{\"S\":[[" + String.join(",", literals) + "]]}}";
            JsonNode row = legacyMapper.readTree(json).path("sheets").path("S").get(0);
            List<String> expected = new ArrayList<>();
            for (JsonNode node : row) {
                expected.add(node.asText());
            }
            List<String> actual = read(READER, "r.json", json).sheet("S").orElseThrow().rows().getFirst().cells().stream()
                    .map(InputCell::text).toList();
            assertEquals(expected, actual, json);
        }
    }

    @Test
    void everyCellCarriesItsExactLineColumnAndPointer() {
        String json = "{\n  \"sheets\": {\n    \"FW_Seq\": [\n      [\"HEAD\", null, \"FW_Reuse\"]\n    ],\n"
                + "    \"HEAD\": [[\"x\"]]\n  }\n}\n";
        WorkbookData workbook = read(READER, "dir/s.json", json);
        assertEquals(new SourceLocation("dir/s.json", 4, 8, "/sheets/FW_Seq/0/0"), locationOf(workbook, "FW_Seq", 0, 0));
        assertEquals(new SourceLocation("dir/s.json", 4, 22, "/sheets/FW_Seq/0/2"), locationOf(workbook, "FW_Seq", 0, 2));
        assertEquals(new SourceLocation("dir/s.json", 3, 5, "/sheets/FW_Seq"), workbook.sheet("FW_Seq").orElseThrow().location());
        assertEquals(new SourceLocation("dir/s.json", 6, 5, "/sheets/HEAD"), workbook.sheet("HEAD").orElseThrow().location());
        assertEquals(6, locationOf(workbook, "HEAD", 0, 0).line());
    }

    @Test
    void sheetNamesWithSlashesAndTildesAreEscapedInPointers() {
        WorkbookData workbook = read(READER, "a.json", "{\"sheets\":{\"a/b~c\":[[\"x\"]]}}");
        assertEquals("/sheets/a~1b~0c/0/0", cell(workbook, "a/b~c", 0, 0).location().path());
    }

    @Test
    void structuralMistakesAreSchemaErrorsWithLocations() {
        String legacyMessage = "Expected top-level 'sheets' object in JSON schedule";
        assertEquals(legacyMessage, expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", "{\"other\":1}"))
                .primary().message());
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", "[1,2]"));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", ""));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", "{\"sheets\":[]}"));
        FrameworkException notArray = expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", "{\"sheets\":{\"S\":{}}}"));
        assertTrue(notArray.primary().message().contains("sheets[\"S\"] must be a JSON array of row arrays"));
        assertEquals("/sheets/S", notArray.primary().location().path());
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", "{\"sheets\":{\"S\":[[\"a\"]]},\"sheets\":{}}"));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", "{\"sheets\":{\" \":[]}}"));
    }

    @Test
    void containersAndBooleansWhereCellsOrRowsBelongAreRejectedNotSilentlyBlanked() {
        FrameworkException nested = expectCode(ErrorCode.INPUT_SCHEMA,
                () -> read(READER, "a.json", "{\"sheets\":{\"S\":[[\"a\", [\"nested\"]]]}}"));
        assertEquals("/sheets/S/0/1", nested.primary().location().path());
        assertTrue(nested.primary().message().contains("found an array"));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", "{\"sheets\":{\"S\":[[{\"k\":1}]]}}"));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", "{\"sheets\":{\"S\":[true]}}"));
        expectCode(ErrorCode.INPUT_SCHEMA, () -> read(READER, "a.json", "{\"sheets\":{\"S\":[{\"k\":1}]}}"));
    }

    @Test
    void sheetNamesAreUniqueIgnoringCase() {
        FrameworkException dup = expectCode(ErrorCode.INPUT_DUPLICATE_SHEET,
                () -> read(READER, "a.json", "{\"sheets\":{\"HEAD\":[],\n\"head\":[]}}"));
        assertEquals(2, dup.primary().location().line());
        assertEquals("head", dup.primary().attributes().get("sheet"));
        expectCode(ErrorCode.INPUT_DUPLICATE_SHEET, () -> read(READER, "a.json", "{\"sheets\":{\"HEAD\":[],\"HEAD\":[]}}"));
    }

    @Test
    void malformedJsonIsReportedWithItsPositionAndNoSourceSnippet() {
        FrameworkException e = expectCode(ErrorCode.INPUT_MALFORMED,
                () -> read(READER, "a.json", "{\n \"sheets\": {\n  \"S\": [[\"a\"\n }}"));
        assertEquals(4, e.primary().location().line());
        assertTrue(e.primary().location().column() >= 1);
        expectCode(ErrorCode.INPUT_MALFORMED, () -> read(READER, "a.json", "{\"sheets\":{}} trailing"));
        expectCode(ErrorCode.INPUT_MALFORMED, () -> read(READER, "a.json", "{\"sheets\":{},}"));
        expectCode(ErrorCode.INPUT_MALFORMED, () -> read(READER, "a.json", "{'sheets':{}}"));
        expectCode(ErrorCode.INPUT_MALFORMED, () -> read(READER, "a.json", "{\"sheets\":{}} // comment"));
    }

    @Test
    void otherTopLevelKeysAreWarningsNotSilence() {
        ReadResult result = READER.read(InputSource.ofText("a.json",
                "{\"name\":\"demo\",\"sheets\":{\"S\":[[\"a\"]]},\"extra\":{\"x\":[1,2]}}"));
        assertEquals(1, result.workbook().sheets().size());
        assertEquals(2, result.warnings().size());
        Diagnostic first = result.warnings().getFirst();
        assertEquals(Severity.WARNING, first.severity());
        assertEquals(ErrorCode.INPUT_SCHEMA, first.code());
        assertEquals("name", first.attributes().get("key"));
        assertEquals("/name", first.location().path());
    }

    @Test
    void utf8WithAByteOrderMarkAndNonBmpTextIsPreserved() {
        byte[] body = "{\"sheets\":{\"S\":[[\"будь 😀 \\u0041\\n\\t\"]]}}".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        WorkbookData workbook = READER.read(InputSource.of("a.json", withBom)).workbook();
        assertEquals("будь 😀 A\n\t", cell(workbook, "S", 0, 0).text());
    }

    @Test
    void bytesThatAreNotUtf8AreRejected() {
        byte[] bad = {'{', '"', 's', (byte) 0xC3, (byte) 0x28, '"', ':', '1', '}'};
        FrameworkException e = expectCode(ErrorCode.INPUT_MALFORMED, () -> READER.read(InputSource.of("a.json", bad)));
        assertTrue(e.primary().message().contains("UTF-8"));
    }

    @Test
    void aLargeCodeCellSurvivesIntact() {
        String code = "x".repeat(32_000) + "\n" + "y".repeat(500);
        WorkbookData workbook = read(READER, "a.json", "{\"sheets\":{\"S\":[[\"" + code.replace("\n", "\\n") + "\"]]}}");
        assertEquals(code, cell(workbook, "S", 0, 0).text());
    }

    @Test
    void aStringBeyondJacksonsConfiguredLimitIsAnInputLimitNotACrash() {
        String huge = "{\"sheets\":{\"S\":[[\"" + "z".repeat(21_000_000) + "\"]]}}";
        expectCode(ErrorCode.INPUT_LIMIT, () -> read(READER, "big.json", huge));
    }

    @Test
    void readingDoesNotDependOnTheDefaultLocale() {
        java.util.Locale saved = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            WorkbookData workbook = read(READER, "a.json", "{\"sheets\":{\"S\":[[1.5, \"I\"]], \"s2\":[]}}");
            assertEquals(List.of("S = [0:1.5|1:I]", "s2 ="), shape(workbook));
            expectCode(ErrorCode.INPUT_DUPLICATE_SHEET, () -> read(READER, "a.json", "{\"sheets\":{\"I\":[],\"i\":[]}}"));
        } finally {
            java.util.Locale.setDefault(saved);
        }
    }
}
