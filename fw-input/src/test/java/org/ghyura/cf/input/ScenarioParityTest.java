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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A03 at the level of the normalized scenario: the same scenario, however it is written (XLSX, JSON, YAML,
 * TOML or the Java builder), is interpreted into the same {@link ScenarioSpec}. The workbook-level parity of the
 * readers is tested in {@code WorkbookWritersTest}; this test adds the interpretation on top of it.
 */
class ScenarioParityTest {

    private static final Pattern YAML_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TOML_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    private static final SourceLocation HERE = SourceLocation.of("test");

    private static ScenarioInterpreter.Interpretation interpret(WorkbookData workbook) {
        return ScenarioInterpreter.interpret(workbook, FileResolver.none());
    }

    private static WorkbookData throughFormat(WorkbookData workbook, InputFormat format) {
        byte[] written = WorkbookFormats.writer(format).write(workbook);
        return WorkbookFormats.reader(format).read(InputSource.of("parity." + format.token(), written)).workbook();
    }

    private static boolean namesAllow(WorkbookData workbook, InputFormat format) {
        return switch (format) {
            case YAML -> workbook.sheetNames().stream().allMatch(n -> YAML_NAME.matcher(n).matches());
            case TOML -> workbook.sheetNames().stream().allMatch(n -> TOML_KEY.matcher(n).matches());
            default -> true;
        };
    }

    // ---- the documented builder example -----------------------------------------------------------------------

    private static final String LEGACY_EXAMPLE_SPEC = "cf.scenario/1\n"
            + "SHEET\t1\tHEAD\n"
            + "VALUE\tt\t-\t-\tscenario=demo\n"
            + "SHEET\t2\tCOST\n"
            + "VALUE\tt\t-\t-\t id=1 cost=0.10\n"
            + "VALUE\tt\t-\t-\t id=2 cost=0.30\n"
            + "VALUE\tt\t-\t-\t id=3 cost=0.50\n"
            + "SHEET\t3\tLAT\n"
            + "VALUE\tt\t-\t-\t latency=10ms\n"
            + "VALUE\tt\t-\t-\t latency=30ms\n"
            + "VALUE\tt\t-\t-\t latency=80ms\n"
            + "SHEET\t4\tTAIL\n"
            + "VALUE\tt\t-\t-\t status=ok\n"
            + "SHEET\t5\tROW\n"
            + "VALUE\te\t-\t-\t\n"
            + "SHEETNAME\tHEAD\t\t\n"
            + "SHEETNAME\tCOST\t\t\n"
            + "SHEETNAME\tLAT\t\t\n"
            + "SHEETNAME\tTAIL\t\t\n"
            + "SHEETNAME\tROW\t\t\n"
            + "RUNME\tclass RunMeFirstOnce { public static String FW_ARGS; }\n"
            + "ARG\tnoargs\n"
            + "CUSTOM\t2\tFWCUSTOMVAR=2 nominal\n"
            + "SEQ\tpresent\n"
            + "ROW\t0\tHEAD\tFW_Exclude\tFW_Reuse\tFW_Combi(1)\tFW_Combi(1)\n"
            + "ROW\t1\tCOST\tFW_Exclude\tFW_Reuse\tFW_Combi(1)\tFW_Combi(1)\n"
            + "ROW\t2\tLAT\tFW_Exclude\tFW_Reuse\tFW_Combi(1)\tFW_Combi(1)\n"
            + "ROW\t3\tTAIL\tFW_Exclude\tFW_Reuse\tFW_Combi(1)\tFW_Combi(1)\n"
            + "ROW\t4\tROW\tFW_(HEAD,,COST,,LAT,,TAIL,,M:N)\n";

    @Test
    void theBuilderExampleMeansExactlyWhatItsHandDerivedSpecSays() {
        ScenarioInterpreter.Interpretation result = interpret(ScheduleBuilderTest.legacyExample().build());
        assertEquals(LEGACY_EXAMPLE_SPEC, result.spec().canonicalText());
        // the example's FW_RunMeFirstOnce code declares FW_ARGS but never assigns it, which the legacy parser also warned about
        assertEquals(1, result.diagnostics().size(), result.diagnostics().toString());
        assertEquals(ErrorCode.SPEC_RUNMEFIRSTONCE_SHAPE, result.diagnostics().getFirst().code());
    }

    @Test
    void theBuilderTheHandWrittenJsonAndEveryFormatGiveTheSameScenario() {
        WorkbookData built = ScheduleBuilderTest.legacyExample().build();
        WorkbookData json = new JsonWorkbookReader().read(InputSource.ofText("test_e2e_metric.json", ScheduleBuilderTest.LEGACY_EXAMPLE_AS_JSON)).workbook();
        assertEquals(LEGACY_EXAMPLE_SPEC, interpret(json).spec().canonicalText());
        for (InputFormat format : InputFormat.values()) {
            ScenarioSpec viaFormat = interpret(throughFormat(built, format)).spec();
            assertEquals(LEGACY_EXAMPLE_SPEC, viaFormat.canonicalText(), format.token());
            assertEquals(interpret(built).spec().contentDigest(), viaFormat.contentDigest(), format.token());
        }
    }

    // ---- a scenario that exercises every directive and awkward text ---------------------------------------------

    private static InputCell text(int column, String text) {
        return InputCell.text(column, text, HERE);
    }

    private static InputSheet sheet(String name, List<InputRow> rows) {
        return new InputSheet(name, HERE, rows);
    }

    private static WorkbookData trickyScenario() {
        return new WorkbookData("tricky", List.of(
                sheet("FW_Seq", List.of(
                        new InputRow(0, List.of(text(0, "HEAD"), text(2, "FW_Reuse"), text(3, "FW_Combi(1)"))),
                        new InputRow(1, List.of()),
                        new InputRow(2, List.of(text(4, "later"))))),
                sheet("FW_SheetNames", List.of(new InputRow(0, List.of(text(0, "HEAD"), text(1, "FW_EMPTY_STRING"), text(2, "end"))))),
                sheet("FW_CUSTOM_VAR", List.of(new InputRow(0, List.of(text(0, "2"), text(1, "declaration"))))),
                sheet("FW_Arguments", List.of(new InputRow(0, List.of(text(0, "a b"))))),
                sheet("HEAD", List.of(
                        new InputRow(0, List.of(text(0, "plain"), text(1, "FW_EMPTY_STRING"))),
                        new InputRow(1, List.of(text(0, "int  a  =  1 ;"), text(1, "FW_RefineCodeExceptQuoted"), text(2, "FW_Optional"))),
                        new InputRow(2, List.of(text(0, "FW_VAR = FW_EXIT_CODE"), text(3, "FW_CUSTOM_VAR=2 nominal"))))),
                sheet("Payload_1", List.of(
                        new InputRow(0, List.of(text(0, "quote \" backslash \\ tab \t newline \n cr \r end"))),
                        new InputRow(1, List.of(text(0, "будь 😀 café"), text(1, ""))),
                        new InputRow(2, List.of(text(0, "x,]"), text(1, "a # b"), text(2, " leading and trailing "))),
                        new InputRow(3, List.of(text(0, "# D3 code\nstart(\"correct\")\n"))))),
                sheet("EMPTY", List.of())));
    }

    @Test
    void everyDirectiveAndAwkwardTextMeansTheSameInEveryFormat() {
        WorkbookData original = trickyScenario();
        ScenarioSpec expected = interpret(original).spec();

        SheetSpec head = expected.sheet("HEAD").orElseThrow();
        assertEquals(List.of("plain", "", "int a=1;", "FW_VAR = 1", "FW_CUSTOM_VAR=2 nominal"), head.cells().stream().map(ValueCell::text).toList());
        assertEquals(List.of(ValueKind.TEXT, ValueKind.EMPTY, ValueKind.TEXT, ValueKind.EXIT_CODE_VAR, ValueKind.TEXT),
                head.cells().stream().map(ValueCell::kind).toList());
        assertEquals(List.of(false, false, true, false, false), head.cells().stream().map(ValueCell::optional).toList());
        assertEquals(List.of(false, false, true, false, false), head.cells().stream().map(ValueCell::refined).toList());
        // the awkward payload text is exactly the input's text, cell for cell, in reading order
        List<String> payload = new ArrayList<>();
        original.sheet("Payload_1").orElseThrow().rows().forEach(r -> r.cells().forEach(c -> payload.add(c.text())));
        assertEquals(payload, expected.sheet("Payload_1").orElseThrow().cells().stream().map(ValueCell::text).toList());
        assertTrue(expected.sheet("EMPTY").orElseThrow().cells().isEmpty());
        assertEquals(List.of(0, 2), expected.sequence().stream().map(r -> r.ordinal()).toList());

        for (InputFormat format : InputFormat.values()) {
            ScenarioSpec viaFormat = interpret(throughFormat(original, format)).spec();
            assertEquals(expected.canonicalText(), viaFormat.canonicalText(), format.token());
            assertEquals(expected.contentDigest(), viaFormat.contentDigest(), format.token());
        }
    }

    @Test
    void aFormulaIsAFormulaValueInXlsxAndTheOtherFormatsRefuseToCarryOne() {
        WorkbookData withFormula = new WorkbookData("f", List.of(sheet("S", List.of(new InputRow(0, List.of(text(0, "a"),
                new InputCell(1, CellKind.FORMULA, "SUM(A1:A3)", HERE), new InputCell(2, CellKind.BLANK, "", HERE)))))));
        ScenarioInterpreter.Interpretation result = interpret(throughFormat(withFormula, InputFormat.XLSX));
        List<ValueCell> cells = result.spec().sheet("S").orElseThrow().cells();
        assertEquals(List.of("a", "SUM(A1:A3)", ""), cells.stream().map(ValueCell::text).toList());
        assertEquals(List.of(ValueKind.TEXT, ValueKind.FORMULA, ValueKind.TEXT), cells.stream().map(ValueCell::kind).toList());
        assertEquals(1, result.diagnostics().stream().filter(d -> d.code() == ErrorCode.SPEC_FORMULA_AS_TEXT).count());
        // the logical difference is kept: the same text as a plain string is a different scenario
        WorkbookData asText = new WorkbookData("f", List.of(sheet("S", List.of(new InputRow(0, List.of(text(0, "a"), text(1, "SUM(A1:A3)"), text(2, "")))))));
        assertTrue(!interpret(asText).spec().canonicalText().equals(result.spec().canonicalText()));
    }

    // ---- the 32 real workbooks --------------------------------------------------------------------------------

    @Test
    void everyRealWorkbookIsInterpretedWithoutErrorAndWithTheSheetsAndValuesAnIndependentSurveyCounted() throws Exception {
        long sheets = 0;
        long values = 0;
        long sequenceRows = 0;
        long sequenceCells = 0;
        for (LegacyCorpus.Entry entry : LegacyCorpus.entries()) {
            ScenarioInterpreter.Interpretation result = interpret(LegacyCorpus.workbook(entry));
            ScenarioSpec spec = result.spec();
            int[] surveyed = LegacyCorpus.SURVEY.get(entry.file());
            assertEquals(surveyed[0], spec.sheetCount(), entry.file() + " data sheets");
            assertEquals(surveyed[1], spec.valueCount(), entry.file() + " values");
            assertTrue(spec.hasSequenceSheet(), entry.file());
            assertTrue(result.diagnostics().stream().noneMatch(d -> d.severity() == Severity.ERROR), entry.file());
            sheets += spec.sheetCount();
            values += spec.valueCount();
            sequenceRows += spec.sequence().size();
            sequenceCells += spec.sequence().stream().mapToLong(r -> r.cells().size()).sum();
        }
        assertEquals(429, sheets, "the survey counted 429 data sheets");
        assertEquals(1093, values, "the survey counted 1093 data cells");
        assertEquals(580, sequenceRows, "the survey counted 580 FW_Seq rows, all with content");
        assertEquals(2332, sequenceCells, "the survey counted 2332 non-blank FW_Seq cells");
    }

    @Test
    void theOnlyFindingsOnTheRealWorkbooksAreRepeatedValuesAndTheyMatchAnIndependentTally() throws Exception {
        for (LegacyCorpus.Entry entry : LegacyCorpus.entries()) {
            WorkbookData workbook = LegacyCorpus.workbook(entry);
            ScenarioInterpreter.Interpretation result = interpret(workbook);
            // independent tally on the plain input: a repeat is a plain value whose text occurred earlier in its sheet,
            // where an FW_EMPTY_STRING counts as an earlier empty text
            for (InputSheet sheet : workbook.sheets()) {
                if (sheet.isControl()) {
                    continue;
                }
                Set<String> seen = new HashSet<>();
                int repeats = 0;
                for (InputRow row : sheet.rows()) {
                    for (InputCell cell : row.cells()) {
                        if (cell.text().equals("FW_EMPTY_STRING")) {
                            seen.add("");
                        } else {
                            if (seen.contains(cell.text())) {
                                repeats++;
                            }
                            seen.add(cell.text());
                        }
                    }
                }
                List<Diagnostic> notes = result.diagnostics().stream()
                        .filter(d -> d.code() == ErrorCode.SPEC_DUPLICATE_VALUE && sheet.name().equals(d.attributes().get("sheet"))).toList();
                assertEquals(repeats > 0 ? 1 : 0, notes.size(), entry.file() + " / " + sheet.name());
                if (repeats > 0) {
                    assertEquals(Integer.toString(repeats), notes.getFirst().attributes().get("repeats"), entry.file() + " / " + sheet.name());
                }
            }
            for (Diagnostic d : result.diagnostics()) {
                assertTrue(d.code() == ErrorCode.SPEC_DUPLICATE_VALUE || d.code() == ErrorCode.SPEC_RUNMEFIRSTONCE_SHAPE,
                        entry.file() + ": unexpected finding " + d);
            }
        }
    }

    @Test
    void everyRealWorkbookMeansTheSameWhicheverFormatItIsWrittenIn() throws Exception {
        int viaYaml = 0;
        int viaToml = 0;
        for (LegacyCorpus.Entry entry : LegacyCorpus.entries()) {
            WorkbookData workbook = LegacyCorpus.workbook(entry);
            ScenarioSpec expected = interpret(workbook).spec();
            for (InputFormat format : InputFormat.values()) {
                if (!namesAllow(workbook, format)) {
                    continue;
                }
                ScenarioSpec viaFormat = interpret(throughFormat(workbook, format)).spec();
                assertEquals(expected.canonicalText(), viaFormat.canonicalText(), entry.file() + " via " + format.token());
                assertEquals(expected.contentDigest(), viaFormat.contentDigest(), entry.file() + " via " + format.token());
                viaYaml += format == InputFormat.YAML ? 1 : 0;
                viaToml += format == InputFormat.TOML ? 1 : 0;
            }
        }
        assertTrue(viaYaml >= 16 && viaToml >= 16, "too few real workbooks exercised YAML/TOML: yaml=" + viaYaml + " toml=" + viaToml);
    }
}
