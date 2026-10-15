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
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.model.spec.CustomVar;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetDecoration;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioInterpreterTest {

    private static InputCell cell(String sheet, int row, int column, CellKind kind, String text) {
        return new InputCell(column, kind, text, SourceLocation.ofPath("t.json", sheet + "!" + row + ":" + column));
    }

    private static InputCell t(String sheet, int row, int column, String text) {
        return cell(sheet, row, column, CellKind.TEXT, text);
    }

    private static InputRow row(int sourceRow, InputCell... cells) {
        return new InputRow(sourceRow, List.of(cells));
    }

    private static InputSheet sheet(String name, InputRow... rows) {
        return new InputSheet(name, SourceLocation.ofPath("t.json", name), List.of(rows));
    }

    /** A sheet with one text cell per row, all in column 0. */
    private static InputSheet column(String name, String... texts) {
        List<InputRow> rows = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            rows.add(row(i, t(name, i, 0, texts[i])));
        }
        return new InputSheet(name, SourceLocation.ofPath("t.json", name), rows);
    }

    private static WorkbookData book(InputSheet... sheets) {
        return new WorkbookData("t.json", List.of(sheets));
    }

    private static ScenarioInterpreter.Interpretation interpret(InputSheet... sheets) {
        return ScenarioInterpreter.interpret(book(sheets), FileResolver.none());
    }

    private static List<Diagnostic> failures(InputSheet... sheets) {
        FrameworkException e = assertThrows(FrameworkException.class, () -> interpret(sheets));
        return e.diagnostics();
    }

    private static Diagnostic only(List<Diagnostic> diagnostics, ErrorCode code) {
        List<Diagnostic> matching = diagnostics.stream().filter(d -> d.code() == code).toList();
        assertEquals(1, matching.size(), "expected exactly one " + code + " in " + diagnostics);
        return matching.getFirst();
    }

    private static List<String> texts(SheetSpec sheet) {
        return sheet.cells().stream().map(ValueCell::text).toList();
    }

    // ---- data sheets ------------------------------------------------------------------------------------------

    @Test
    void everyKindOfPlainCellIsAValueInSheetOrderAndSheetKeysCountDataSheetsOnly() {
        ScenarioSpec spec = interpret(
                sheet("FW_Seq"),
                sheet("Beta", row(0, t("Beta", 0, 0, "a"), cell("Beta", 0, 1, CellKind.NUMBER, "3.5")),
                        row(2, cell("Beta", 2, 0, CellKind.BOOLEAN, "TRUE"), cell("Beta", 2, 3, CellKind.ERROR, "#DIV/0!"),
                                cell("Beta", 2, 4, CellKind.BLANK, ""))),
                sheet("FW_Info"),
                sheet("Alpha", row(0, t("Alpha", 0, 0, "x")))).spec();
        assertEquals(List.of("Beta", "Alpha"), spec.sheets().stream().map(SheetSpec::name).toList());
        assertEquals(List.of(1, 2), spec.sheets().stream().map(SheetSpec::key).toList());
        assertEquals(List.of("a", "3.5", "TRUE", "#DIV/0!", ""), texts(spec.sheet("Beta").orElseThrow()));
        assertTrue(spec.sheet("Beta").orElseThrow().cells().stream().allMatch(v -> v.kind() == ValueKind.TEXT && !v.optional() && !v.refined()));
        assertEquals(6, spec.valueCount());
    }

    @Test
    void controlSheetNamesAreCaseSensitiveSoFwUnderscoreSeqInLowerCaseIsADataSheet() {
        ScenarioSpec spec = interpret(column("fw_seq", "v"), column("Fw_Info", "w")).spec();
        assertEquals(List.of("fw_seq", "Fw_Info"), spec.sheets().stream().map(SheetSpec::name).toList());
        assertFalse(spec.hasSequenceSheet());
    }

    @Test
    void everyValueKeepsTheLocationOfItsCell() {
        ScenarioSpec spec = interpret(sheet("S", row(3, t("S", 3, 0, "a"), t("S", 3, 1, "b")), row(7, t("S", 7, 2, "c")))).spec();
        assertEquals(List.of("S!3:0", "S!3:1", "S!7:2"),
                spec.sheet("S").orElseThrow().cells().stream().map(v -> v.location().path()).toList());
    }

    @Test
    void emptyStringDirectiveIsAnEmptyValueAndTrailingTextIsAWarning() {
        ScenarioInterpreter.Interpretation result = interpret(column("S", "FW_EMPTY_STRING", "FW_EMPTY_STRING!"));
        SheetSpec sheet = result.spec().sheet("S").orElseThrow();
        assertEquals(List.of("", ""), texts(sheet));
        assertTrue(sheet.cells().stream().allMatch(v -> v.kind() == ValueKind.EMPTY));
        Diagnostic warning = only(result.diagnostics(), ErrorCode.SPEC_DIRECTIVE_SYNTAX);
        assertEquals(Severity.WARNING, warning.severity());
        assertEquals("S!1:0", warning.location().path());
        assertTrue(warning.message().contains("ignored"), warning.message());
    }

    @Test
    void optionalMarksThePreviousValueOfItsOwnSheetAndMakesNoValueItself() {
        ScenarioSpec spec = interpret(column("S", "a", "FW_Optional", "b", "c", "FW_Optional", "FW_Optional")).spec();
        SheetSpec sheet = spec.sheet("S").orElseThrow();
        assertEquals(List.of("a", "b", "c"), texts(sheet));
        assertEquals(List.of(true, false, true), sheet.cells().stream().map(ValueCell::optional).toList());
    }

    @Test
    void optionalOrRefineWithNoEarlierValueInItsSheetIsAnErrorAtThatCell() {
        List<Diagnostic> found = failures(column("One", "x"), column("Two", "FW_Optional", "y"), column("Three", "FW_RefineCodeExceptQuoted"));
        assertEquals(2, found.size(), found.toString());
        assertTrue(found.stream().allMatch(d -> d.code() == ErrorCode.SPEC_DIRECTIVE_WITHOUT_TARGET && d.severity() == Severity.ERROR));
        assertEquals(List.of("Two!0:0", "Three!0:0"), found.stream().map(d -> d.location().path()).toList());
        assertEquals(List.of("FW_Optional", "FW_RefineCodeExceptQuoted"), found.stream().map(d -> d.attributes().get("directive")).toList());
    }

    @Test
    void refineCompactsThePreviousValueKeepsItsKindAndCombinesWithOptional() {
        ScenarioSpec spec = interpret(column("S", "int  a  =  1 ;", "FW_RefineCodeExceptQuoted", "x  y", "FW_Optional",
                "FW_RefineCodeExceptQuoted", "untouched  value")).spec();
        List<ValueCell> cells = spec.sheet("S").orElseThrow().cells();
        assertEquals(List.of("int a=1;", "x y", "untouched  value"), cells.stream().map(ValueCell::text).toList());
        assertEquals(List.of(true, true, false), cells.stream().map(ValueCell::refined).toList());
        assertEquals(List.of(false, true, false), cells.stream().map(ValueCell::optional).toList());
        assertTrue(cells.stream().allMatch(v -> v.kind() == ValueKind.TEXT));
    }

    @Test
    void refineOfAValueThatMakesTheLegacyExpressionsExplodeIsALimitErrorNotAHang() {
        String pathological = "\"" + "\\".repeat(60) + "x";
        List<Diagnostic> found = assertTimeoutPreemptively(Duration.ofSeconds(60),
                () -> failures(column("S", pathological, "FW_RefineCodeExceptQuoted")));
        Diagnostic limit = only(found, ErrorCode.INPUT_LIMIT);
        assertEquals("S!1:0", limit.location().path(), "reported at the directive that asked for the refinement");
    }

    @Test
    void exitCodeVariablesCarryTheKeyOfTheirOwnSheet() {
        ScenarioSpec spec = interpret(
                column("A", "FW_VAR = FW_EXIT_CODE"),
                column("B", "x", "  FW_VAR=FW_EXIT_CODE; // FW_EXIT_CODE", "int FW_VAR = FW_EXIT_CODE;", "xFW_VAR = FW_EXIT_CODE",
                        "FW_VAR = 7", "FW_EXIT_CODE", "FW_VAR = FW_EXIT_CODE")).spec();
        SheetSpec a = spec.sheet("A").orElseThrow();
        SheetSpec b = spec.sheet("B").orElseThrow();
        assertEquals(List.of("FW_VAR = 1"), texts(a));
        assertEquals(ValueKind.EXIT_CODE_VAR, a.cells().getFirst().kind());
        assertEquals(List.of("x", "  FW_VAR=2; // 2", "int FW_VAR = 2;", "xFW_VAR = FW_EXIT_CODE", "FW_VAR = 7", "FW_EXIT_CODE",
                "FW_VAR = 2"), texts(b));
        assertEquals(List.of(ValueKind.TEXT, ValueKind.EXIT_CODE_VAR, ValueKind.EXIT_CODE_VAR, ValueKind.TEXT, ValueKind.TEXT,
                ValueKind.TEXT, ValueKind.EXIT_CODE_VAR), b.cells().stream().map(ValueCell::kind).toList());
        assertEquals(1, a.exitCodeVarCount());
        assertEquals(3, b.exitCodeVarCount());
    }

    @Test
    void customVarAndExitCodeCellsAreOrdinaryValuesNotDirectives() {
        ScenarioInterpreter.Interpretation result = interpret(column("S", "FW_CUSTOM_VAR=2 nominal", "FW_EXIT_CODE", "FW_VARIABLE"));
        assertEquals(List.of("FW_CUSTOM_VAR=2 nominal", "FW_EXIT_CODE", "FW_VARIABLE"), texts(result.spec().sheet("S").orElseThrow()));
        assertEquals(List.of(), result.diagnostics());
    }

    @Test
    void aFormulaIsItsFormulaTextWithAWarningAndNeverADirective() {
        ScenarioInterpreter.Interpretation result = interpret(sheet("S",
                row(0, cell("S", 0, 0, CellKind.FORMULA, "SUM(A1:A2)"), cell("S", 0, 1, CellKind.FORMULA, "FW_Optional"))));
        List<ValueCell> cells = result.spec().sheet("S").orElseThrow().cells();
        assertEquals(List.of("SUM(A1:A2)", "FW_Optional"), cells.stream().map(ValueCell::text).toList());
        assertTrue(cells.stream().allMatch(v -> v.kind() == ValueKind.FORMULA && !v.optional()));
        assertEquals(2, result.diagnostics().stream().filter(d -> d.code() == ErrorCode.SPEC_FORMULA_AS_TEXT
                && d.severity() == Severity.WARNING).count());
        assertEquals("S!0:1", result.diagnostics().get(1).location().path());
    }

    /**
     * Review finding IP03: the legacy parser judged the formatted text of every cell that is not a formula, so a
     * number whose custom format renders FW_EMPTY_STRING1 is that directive. Only a formula is protected.
     */
    @Test
    void everyCellButAFormulaIsJudgedByItsFormattedText() {
        ScenarioInterpreter.Interpretation result = interpret(sheet("S",
                row(0, t("S", 0, 0, "x"), cell("S", 0, 1, CellKind.NUMBER, "FW_Optional"),
                        cell("S", 0, 2, CellKind.NUMBER, "FW_EMPTY_STRING1"), cell("S", 0, 3, CellKind.BOOLEAN, "FW_EMPTY_STRING"),
                        cell("S", 0, 4, CellKind.FORMULA, "FW_Optional"), cell("S", 0, 5, CellKind.NUMBER, "FW_VAR = FW_EXIT_CODE"))));
        List<ValueCell> cells = result.spec().sheet("S").orElseThrow().cells();
        assertEquals(List.of("x", "", "", "FW_Optional", "FW_VAR = 1"), cells.stream().map(ValueCell::text).toList());
        assertEquals(List.of(ValueKind.TEXT, ValueKind.EMPTY, ValueKind.EMPTY, ValueKind.FORMULA, ValueKind.EXIT_CODE_VAR),
                cells.stream().map(ValueCell::kind).toList());
        assertTrue(cells.getFirst().optional(), "the formatted number FW_Optional marks the value before it");
        assertEquals(List.of(ErrorCode.SPEC_DIRECTIVE_SYNTAX, ErrorCode.SPEC_FORMULA_AS_TEXT),
                result.diagnostics().stream().map(Diagnostic::code).toList(), "the trailing 1 of FW_EMPTY_STRING1 is a warning");
        assertEquals("S!0:2", result.diagnostics().getFirst().location().path());
    }

    @Test
    void fileDirectiveReadsThroughTheResolverAndTheValueIsAFileValue() {
        List<String> asked = new ArrayList<>();
        FileResolver resolver = (path, where) -> {
            asked.add(path + " @ " + where.path());
            return "line1\nline2\n";
        };
        ScenarioSpec spec = ScenarioInterpreter.interpret(book(column("S", "before", "FW_File=data/x.txt", "FW_Optional", "FW_File= spaced.txt ")),
                resolver).spec();
        List<ValueCell> cells = spec.sheet("S").orElseThrow().cells();
        assertEquals(List.of("before", "line1\nline2\n", "line1\nline2\n"), cells.stream().map(ValueCell::text).toList());
        assertEquals(List.of(ValueKind.TEXT, ValueKind.FILE, ValueKind.FILE), cells.stream().map(ValueCell::kind).toList());
        assertEquals(List.of(false, true, false), cells.stream().map(ValueCell::optional).toList());
        assertEquals(List.of("data/x.txt @ S!1:0", " spaced.txt  @ S!3:0"), asked, "the path is exactly the text after the equals sign");
        assertEquals("S!1:0", cells.get(1).location().path());
    }

    @Test
    void fileDirectiveProblemsAreReportedAtTheCell() {
        List<Diagnostic> found = failures(column("S", "FW_File=", "FW_File=a.txt"));
        assertEquals(2, found.size(), found.toString());
        assertEquals(ErrorCode.SPEC_DIRECTIVE_SYNTAX, found.get(0).code());
        assertEquals("S!0:0", found.get(0).location().path());
        assertEquals(ErrorCode.INPUT_UNREADABLE, found.get(1).code());
        assertEquals("S!1:0", found.get(1).location().path());
    }

    @Test
    void csvDatabaseAndBinaryDirectivesAreRefusedWithTheirCapability() {
        List<Diagnostic> found = failures(column("S", "FW_CSVFile=a.csv", "FW_Separator=;", "FW_BinaryFile=b.bin", "FW_DBURL=jdbc:x",
                "FW_DBUSER=u", "FW_DBPASS=p", "FW_SQL=select 1", "ok"));
        assertEquals(7, found.size(), found.toString());
        assertTrue(found.stream().allMatch(d -> d.code() == ErrorCode.UNSUPPORTED_CAPABILITY && d.severity() == Severity.ERROR));
        assertEquals(List.of("FW_CSVFile=", "FW_Separator=", "FW_BinaryFile=", "FW_DBURL=", "FW_DBUSER=", "FW_DBPASS=", "FW_SQL="),
                found.stream().map(d -> d.attributes().get("directive")).toList());
        assertEquals(List.of("csv-import", "csv-import", "binary-file", "database", "database", "database", "database"),
                found.stream().map(d -> d.attributes().get("capability")).toList());
        assertEquals("S!3:0", found.get(3).location().path());
    }

    @Test
    void anUnknownDirectiveIsAnErrorWithASpellingSuggestionWhenOneIsClose() {
        List<Diagnostic> found = failures(column("S", "FW_Optionl", "FW_Flie=x", "FW_Zzzzzzzz", "FW_EMPTYSTRING"));
        assertEquals(4, found.size(), found.toString());
        assertTrue(found.stream().allMatch(d -> d.code() == ErrorCode.SPEC_UNKNOWN_DIRECTIVE));
        assertTrue(found.get(0).message().contains("did you mean FW_Optional?"), found.get(0).message());
        assertTrue(found.get(1).message().contains("did you mean FW_File=?"), found.get(1).message());
        assertFalse(found.get(2).message().contains("did you mean"), found.get(2).message());
        assertEquals("FW_Optionl", found.get(0).attributes().get("directive"));
    }

    @Test
    void trailingTextAfterTheThreePrefixDirectivesIsAWarningAndTheyStillApply() {
        ScenarioInterpreter.Interpretation result = interpret(column("S", "v  w", "FW_RefineCodeExceptQuoted please", "FW_Optional?"));
        ValueCell value = result.spec().sheet("S").orElseThrow().cells().getFirst();
        assertEquals("v w", value.text());
        assertTrue(value.refined() && value.optional());
        assertEquals(2, result.diagnostics().size());
        assertTrue(result.diagnostics().stream().allMatch(d -> d.code() == ErrorCode.SPEC_DIRECTIVE_SYNTAX && d.severity() == Severity.WARNING));
    }

    @Test
    void repeatsInASheetStayDistinctValuesAndAreSummarisedOnceAsANote() {
        // a b a a, then an empty value, a plain empty cell (repeats the empty value), c, and another plain empty cell
        ScenarioInterpreter.Interpretation result = interpret(
                column("S", "a", "b", "a", "a", "FW_EMPTY_STRING", "", "c", ""),
                column("T", "a", "b"));
        SheetSpec s = result.spec().sheet("S").orElseThrow();
        assertEquals(List.of("a", "b", "a", "a", "", "", "c", ""), texts(s));
        Diagnostic note = only(result.diagnostics(), ErrorCode.SPEC_DUPLICATE_VALUE);
        assertEquals(Severity.INFO, note.severity());
        assertEquals("4", note.attributes().get("repeats"));
        assertEquals("S", note.attributes().get("sheet"));
        assertEquals("S!2:0", note.location().path(), "the first repeat");
        assertEquals(1, result.diagnostics().size(), "repeats across sheets are not repeats");
    }

    // ---- control sheets ---------------------------------------------------------------------------------------

    @Test
    void sheetNamesRowsAreTypedAndIncompleteOrRepeatedRowsAreWarnings() {
        ScenarioInterpreter.Interpretation result = interpret(sheet("FW_SheetNames",
                row(0, t("FW_SheetNames", 0, 0, "HEAD"), t("FW_SheetNames", 0, 1, "FW_EMPTY_STRING"), t("FW_SheetNames", 0, 2, "fw_empty_string")),
                row(1, t("FW_SheetNames", 1, 0, "COST"), t("FW_SheetNames", 1, 1, "pre"), t("FW_SheetNames", 1, 2, "post")),
                row(2, t("FW_SheetNames", 2, 0, "X"), t("FW_SheetNames", 2, 1, "p")),
                row(3, t("FW_SheetNames", 3, 1, "orphan"), t("FW_SheetNames", 3, 2, "z")),
                row(4, t("FW_SheetNames", 4, 0, "COST"), t("FW_SheetNames", 4, 2, "e2")),
                row(5)));
        List<SheetDecoration> rows = result.spec().controls().sheetNames();
        assertEquals(List.of("HEAD|||", "COST|pre|post", "COST||e2"),
                rows.stream().map(d -> d.sheet() + "|" + d.prefix() + "|" + d.ending() + (d.sheet().equals("HEAD") ? "|" : "")).toList());
        assertEquals(3, result.diagnostics().size(), result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().allMatch(d -> d.code() == ErrorCode.SPEC_CONTROL_ROW && d.severity() == Severity.WARNING));
        assertEquals(List.of("FW_SheetNames!2:0", "FW_SheetNames!3:1", "FW_SheetNames!4:0"),
                result.diagnostics().stream().map(d -> d.location().path()).toList());
    }

    @Test
    void customVarRowsAreTypedAndABadIndexIsAnError() {
        ScenarioSpec spec = interpret(sheet("FW_CUSTOM_VAR",
                row(0, t("FW_CUSTOM_VAR", 0, 0, "2"), t("FW_CUSTOM_VAR", 0, 1, "FWCUSTOMVAR=2 nominal")),
                row(1, cell("FW_CUSTOM_VAR", 1, 0, CellKind.NUMBER, "4"), t("FW_CUSTOM_VAR", 1, 1, "FW_EMPTY_STRING")))).spec();
        assertEquals(List.of(2, 4), spec.controls().customVars().stream().map(CustomVar::index).toList());
        assertEquals(List.of("FWCUSTOMVAR=2 nominal", ""), spec.controls().customVars().stream().map(CustomVar::declaration).toList());

        List<Diagnostic> found = failures(sheet("FW_CUSTOM_VAR",
                row(0, t("FW_CUSTOM_VAR", 0, 0, "x"), t("FW_CUSTOM_VAR", 0, 1, "d")),
                row(1, t("FW_CUSTOM_VAR", 1, 0, "3")),
                row(2, t("FW_CUSTOM_VAR", 2, 0, " 5"), t("FW_CUSTOM_VAR", 2, 1, "d"))));
        assertEquals(3, found.size(), found.toString());
        assertEquals(List.of(Severity.ERROR, Severity.WARNING, Severity.ERROR), found.stream().map(Diagnostic::severity).toList());
        assertTrue(found.stream().allMatch(d -> d.code() == ErrorCode.SPEC_CONTROL_ROW));
        assertEquals("FW_CUSTOM_VAR!0:0", found.get(0).location().path());
        assertEquals("FW_CUSTOM_VAR!2:0", found.get(2).location().path(), "no trimming: ' 5' is not a number");
    }

    @Test
    void runMeFirstOnceAndArgumentsKeepTheirNonBlankCellsInReadingOrder() {
        String goodClass = "class RunMeFirstOnce { public static String FW_ARGS = \"\"; }";
        ScenarioInterpreter.Interpretation good = interpret(
                sheet("FW_RunMeFirstOnce", row(0, t("FW_RunMeFirstOnce", 0, 0, goodClass), t("FW_RunMeFirstOnce", 0, 1, " ")),
                        row(1, t("FW_RunMeFirstOnce", 1, 0, "more code"))),
                sheet("FW_Arguments", row(0, t("FW_Arguments", 0, 0, "a"), t("FW_Arguments", 0, 1, ""), cell("FW_Arguments", 0, 2, CellKind.NUMBER, "5")),
                        row(1, t("FW_Arguments", 1, 0, "b c"))));
        assertEquals(List.of(goodClass, "more code"), good.spec().controls().runMeFirstOnce().stream().map(LocatedText::text).toList());
        assertEquals(List.of("a", "5", "b c"), good.spec().controls().arguments().stream().map(LocatedText::text).toList());
        assertEquals(List.of(), good.diagnostics());

        ScenarioInterpreter.Interpretation bad = interpret(
                sheet("FW_RunMeFirstOnce", row(0, t("FW_RunMeFirstOnce", 0, 0, ""), t("FW_RunMeFirstOnce", 0, 1, "class Other { }"))));
        Diagnostic shape = only(bad.diagnostics(), ErrorCode.SPEC_RUNMEFIRSTONCE_SHAPE);
        assertEquals(Severity.WARNING, shape.severity());
        assertEquals("FW_RunMeFirstOnce!0:1", shape.location().path(), "the first non-blank cell is the one checked");
        // each of the three required parts is checked on its own
        for (String lacking : List.of("public static String FW_ARGS = \"\";", "class RunMeFirstOnce { String FW_ARGS = \"\"; }",
                "class RunMeFirstOnce { public static String FW_ARGS; }")) {
            assertEquals(1, interpret(sheet("FW_RunMeFirstOnce", row(0, t("FW_RunMeFirstOnce", 0, 0, lacking)))).diagnostics().size(), lacking);
        }
    }

    @Test
    void inertControlSheetsAreAcceptedSilentlyAndUnknownFwSheetsGetAWarning() {
        ScenarioInterpreter.Interpretation result = interpret(column("FW_Info", "anything"), column("FW_Complex", "x"), column("FW_Combi", "x"),
                column("FW_CombiR", "x"), column("FW_Permut", "x"), column("FW_PermutR", "x"), column("FW_Subsets", "x"),
                column("FW_Cartes", "x"), column("Real", "v"));
        assertEquals(List.of(), result.diagnostics());
        assertEquals(List.of("Real"), result.spec().sheets().stream().map(SheetSpec::name).toList());

        List<Diagnostic> unknown = interpret(column("FW_Seqs", "x"), column("FW_Zzzzzzzzzz", "x")).diagnostics();
        assertEquals(2, unknown.size(), unknown.toString());
        assertTrue(unknown.stream().allMatch(d -> d.code() == ErrorCode.SPEC_CONTROL_SHEET_UNKNOWN && d.severity() == Severity.WARNING));
        assertTrue(unknown.get(0).message().contains("did you mean FW_Seq?"), unknown.get(0).message());
        assertFalse(unknown.get(1).message().contains("did you mean"), unknown.get(1).message());
        assertEquals("FW_Seqs", unknown.get(0).location().path());
    }

    @Test
    void sequenceRowsKeepTheirPositionAmongAllPhysicalRowsAndTheirTextUntrimmed() {
        // positions: 0 content, 1 empty row, 2 content, 3 only blank cells, 4 content; the source row numbers have gaps
        ScenarioSpec spec = interpret(sheet("FW_Seq",
                row(0, t("FW_Seq", 0, 0, "HEAD"), t("FW_Seq", 0, 1, ""), t("FW_Seq", 0, 2, "FW_Exclude")),
                row(1),
                row(5, t("FW_Seq", 5, 0, "  "), t("FW_Seq", 5, 1, " COST ")),
                row(9, t("FW_Seq", 9, 0, " "), t("FW_Seq", 9, 3, "")),
                row(10, t("FW_Seq", 10, 0, "ROW"), t("FW_Seq", 10, 3, "FW_(A,,B,,C,,D,,M:N)")))).spec();
        assertTrue(spec.hasSequenceSheet());
        assertEquals(List.of(0, 2, 4), spec.sequence().stream().map(SeqRow::ordinal).toList());
        assertEquals(List.of("HEAD", "FW_Exclude"), spec.sequence().get(0).cells().stream().map(LocatedText::text).toList());
        assertEquals(List.of(" COST "), spec.sequence().get(1).cells().stream().map(LocatedText::text).toList());
        assertEquals(List.of("ROW", "FW_(A,,B,,C,,D,,M:N)"), spec.sequence().get(2).cells().stream().map(LocatedText::text).toList());
        assertEquals("FW_Seq!5:1", spec.sequence().get(1).cells().getFirst().location().path());
    }

    @Test
    void anAbsentFwSeqSheetIsNotTheSameAsAnEmptyOne() {
        ScenarioSpec absent = interpret(column("S", "v")).spec();
        ScenarioSpec empty = interpret(column("S", "v"), sheet("FW_Seq")).spec();
        ScenarioSpec blankOnly = interpret(column("S", "v"), column("FW_Seq", " ", "")).spec();
        assertFalse(absent.hasSequenceSheet());
        assertTrue(empty.hasSequenceSheet() && empty.sequence().isEmpty());
        assertTrue(blankOnly.hasSequenceSheet() && blankOnly.sequence().isEmpty());
        assertTrue(absent.canonicalText().contains("SEQ\tabsent") && empty.canonicalText().contains("SEQ\tpresent"));
    }

    // ---- reporting --------------------------------------------------------------------------------------------

    @Test
    void everyFindingOfEverySheetIsReportedTogetherInWorkbookOrderWithALocation() {
        List<Diagnostic> found = failures(column("One", "FW_Bad1"), column("FW_Unknown", "x"), column("Two", "ok", "FW_Bad2"),
                column("Three", "FW_DBURL=x"));
        assertEquals(List.of(ErrorCode.SPEC_UNKNOWN_DIRECTIVE, ErrorCode.SPEC_CONTROL_SHEET_UNKNOWN, ErrorCode.SPEC_UNKNOWN_DIRECTIVE,
                ErrorCode.UNSUPPORTED_CAPABILITY), found.stream().map(Diagnostic::code).toList());
        assertTrue(found.stream().allMatch(d -> d.location().isKnown()));
        assertEquals(ErrorCode.SPEC_UNKNOWN_DIRECTIVE, assertThrows(FrameworkException.class, () -> interpret(
                column("One", "FW_Bad1"), column("Two", "FW_DBURL=x"))).code(), "the first error is the primary one");
    }

    // ---- the census -------------------------------------------------------------------------------------------

    @Test
    void theCensusPredicateGivesTheLegacyAnswerForEveryKindOfCell() {
        Map<String, Integer> byText = new java.util.LinkedHashMap<>();
        byText.put("plain", 1);
        byText.put("", 1);
        byText.put("FW_File=x.txt", 1);
        byText.put("FW_EMPTY_STRING", 1);
        byText.put("FW_EMPTY_STRING and more", 1);
        byText.put("FW_Optional", 0);
        byText.put("FW_RefineCodeExceptQuoted", 0);
        byText.put("FW_CSVFile=a.csv", 0);
        byText.put("FW_Separator=;", 0);
        byText.put("FW_DBURL=x", 0);
        byText.put("FW_Foo", 0);
        byText.put("FW_VAR = FW_EXIT_CODE", 1);
        byText.put("FW_EXIT_CODE", 1);
        byText.put("FW_CUSTOM_VAR=1 x", 1);
        byText.put(" FW_Optional", 1);
        byText.forEach((text, expected) -> assertEquals(expected, ScenarioInterpreter.keysConsumedBy(t("S", 0, 0, text)), text));
        // every cell but a formula is judged by its formatted text (review finding IP03)
        for (CellKind kind : List.of(CellKind.NUMBER, CellKind.BOOLEAN, CellKind.ERROR)) {
            byText.forEach((text, expected) -> assertEquals(expected,
                    ScenarioInterpreter.keysConsumedBy(cell("S", 0, 0, kind, text)), kind + " " + text));
        }
        for (String text : byText.keySet()) {
            assertEquals(1, ScenarioInterpreter.keysConsumedBy(cell("S", 0, 0, CellKind.FORMULA, text)), "formula " + text);
        }
        assertEquals(1, ScenarioInterpreter.keysConsumedBy(cell("S", 0, 0, CellKind.BLANK, "")));
    }

    @Test
    void theInterpreterIssuesExactlyTheValuesAnIndependentTallyExpectsOnRandomScenarios() {
        Random random = new Random(20261001L);
        FileResolver files = (path, where) -> "file\n";
        for (int round = 0; round < 40; round++) {
            List<InputSheet> sheets = new ArrayList<>();
            List<List<String>> expected = new ArrayList<>();
            for (int s = 1; s <= 1 + random.nextInt(6); s++) {
                String name = "S" + s;
                List<InputRow> rows = new ArrayList<>();
                List<String> values = new ArrayList<>();
                int cellsLeft = random.nextInt(9);
                int rowIndex = 0;
                while (cellsLeft > 0) {
                    List<InputCell> cells = new ArrayList<>();
                    int inRow = Math.min(cellsLeft, 1 + random.nextInt(3));
                    for (int c = 0; c < inRow; c++) {
                        int pick = random.nextInt(values.isEmpty() ? 8 : 10);
                        String text;
                        switch (pick) {
                            case 0 -> { cells.add(t(name, rowIndex, c, text = "v" + random.nextInt(5))); values.add(text); }
                            case 1 -> { cells.add(t(name, rowIndex, c, "FW_EMPTY_STRING")); values.add(""); }
                            case 2 -> { cells.add(t(name, rowIndex, c, "FW_VAR = FW_EXIT_CODE")); values.add("FW_VAR = " + s); }
                            case 3 -> { cells.add(cell(name, rowIndex, c, CellKind.NUMBER, text = Integer.toString(random.nextInt(100)))); values.add(text); }
                            case 4 -> { cells.add(cell(name, rowIndex, c, CellKind.BLANK, "")); values.add(""); }
                            case 5 -> { cells.add(cell(name, rowIndex, c, CellKind.FORMULA, "SUM(A1)")); values.add("SUM(A1)"); }
                            case 6 -> { cells.add(t(name, rowIndex, c, "FW_File=f.txt")); values.add("file\n"); }
                            case 7 -> { cells.add(t(name, rowIndex, c, "FW_CUSTOM_VAR=1 x")); values.add("FW_CUSTOM_VAR=1 x"); }
                            default -> cells.add(t(name, rowIndex, c, "FW_Optional")); // makes no value; only drawn when a value exists
                        }
                    }
                    rows.add(new InputRow(rowIndex++, cells));
                    cellsLeft -= inRow;
                }
                sheets.add(new InputSheet(name, SourceLocation.ofPath("t.json", name), rows));
                expected.add(values);
            }
            WorkbookData workbook = new WorkbookData("t.json", sheets);
            long predicted = 0;
            for (InputSheet sheet : sheets) {
                for (InputRow r : sheet.rows()) {
                    for (InputCell c : r.cells()) {
                        predicted += ScenarioInterpreter.keysConsumedBy(c);
                    }
                }
            }
            ScenarioSpec spec = ScenarioInterpreter.interpret(workbook, files).spec();
            for (int s = 0; s < sheets.size(); s++) {
                assertEquals(expected.get(s), texts(spec.sheets().get(s)), "round " + round + " sheet S" + (s + 1));
            }
            assertEquals(predicted, spec.valueCount(), "round " + round);
            assertEquals(expected.stream().mapToLong(List::size).sum(), spec.valueCount(), "round " + round);
        }
    }
}
