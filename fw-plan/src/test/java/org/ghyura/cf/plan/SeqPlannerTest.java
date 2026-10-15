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

package org.ghyura.cf.plan;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.CustomVar;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetDecoration;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every expectation is derived by hand from the rules of the legacy SeqParser and of the virtual-sheet pre-scan. */
class SeqPlannerTest {

    private static final PlanOptions LEGACY = PlanOptions.legacyDefaults();

    /** Data sheets in order; "A:2" is sheet A with two values, "A" alone has one. */
    private static List<SheetSpec> sheets(String... namesWithCounts) {
        List<SheetSpec> list = new ArrayList<>();
        for (int i = 0; i < namesWithCounts.length; i++) {
            String[] parts = namesWithCounts[i].split(":");
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            List<ValueCell> cells = new ArrayList<>();
            for (int v = 0; v < count; v++) {
                cells.add(ValueCell.of(parts[0] + v, ValueKind.TEXT, SourceLocation.ofPath("t.json", parts[0] + "!" + v)));
            }
            list.add(new SheetSpec(parts[0], i + 1, cells, SourceLocation.ofPath("t.json", parts[0])));
        }
        return list;
    }

    private static SeqRow row(int ordinal, String... texts) {
        List<LocatedText> cells = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            cells.add(new LocatedText(texts[i], SourceLocation.ofPath("t.json", "FW_Seq!" + ordinal + ":" + i)));
        }
        return new SeqRow(ordinal, cells);
    }

    private static ScenarioSpec spec(List<SheetSpec> sheets, SeqRow... rows) {
        return new ScenarioSpec("t.json", sheets, ControlSpec.EMPTY, true, List.of(rows));
    }

    private static SeqPlanner.Planned plan(ScenarioSpec spec) {
        return SeqPlanner.plan(spec, LEGACY);
    }

    private static List<Diagnostic> failures(ScenarioSpec spec, PlanOptions options) {
        FrameworkException e = assertThrows(FrameworkException.class, () -> SeqPlanner.plan(spec, options));
        return e.diagnostics();
    }

    private static List<ErrorCode> codes(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(Diagnostic::code).toList();
    }

    private static List<String> directives(SeqPlan plan, String sheet) {
        return plan.sheet(sheet).orElseThrow().directives().stream().map(Directive::normalized).toList();
    }

    private static PlanOptions with(boolean autoGenerate, String prefix, boolean headless, boolean nested, boolean promote, boolean names) {
        return new PlanOptions(autoGenerate, prefix, headless, nested, promote, names);
    }

    // ---- the shape of a plan ----------------------------------------------------------------------------------

    @Test
    void aSimplePlanHasExactlyThisCanonicalText() {
        SeqPlanner.Planned planned = plan(spec(sheets("A:2", "B:1"), row(0, "A", "FW_Combi(1)"), row(1, "B", "FW_Permut")));
        assertEquals("cf.plan/2\n"
                + "OPT\ttrue\tFW_VIRTUAL_\ttrue\ttrue\ttrue\ttrue\n"
                + "SHEET\t1\tA\tD\tMANDATORY\t----P\t-\t2\n"
                + "DIR\tVERB\tCOMBI\tEXACT\t1\n"
                + "DIR\tVERB\tCOMBI\tEXACT\t1\n"
                + "SHEET\t2\tB\tD\tMANDATORY\t-----\t-\t1\n"
                + "DIR\tVERB\tPERMUT\tEXACT\t1\n"
                + "FIRST\t[]\n"
                + "SECOND\t[]\n"
                + "DECOR\tA\t\t\n"
                + "DECOR\tB\t\t\n", planned.plan().canonicalText());
        assertEquals(List.of(), planned.diagnostics());
        assertEquals(64, planned.plan().contentDigest().length());
    }

    @Test
    void theAccessorsFindSheetsByNameAndKeyAndSeparateActiveFromInactive() {
        SeqPlan plan = plan(spec(sheets("A", "B", "C"), row(0, "A", "FW_Combi(1)"), row(1, "Ghost", "FW_Combi(1)"))).plan();
        assertEquals(List.of("A", "B", "C", "Ghost"), plan.sheets().stream().map(SheetPlan::name).toList());
        assertEquals("B", plan.sheetByKey(2).orElseThrow().name());
        assertTrue(plan.sheetByKey(0).isEmpty() && plan.sheetByKey(5).isEmpty());
        assertTrue(plan.sheet("nope").isEmpty());
        assertEquals(1, plan.virtualSheetCount());
        assertEquals(List.of("A", "Ghost"), plan.activeSheets().stream().map(SheetPlan::name).toList());
        assertTrue(plan.sheet("A").orElseThrow().active());
        assertFalse(plan.sheet("B").orElseThrow().active());
        assertEquals(4, plan.sheet("Ghost").orElseThrow().key(), "virtual sheets continue the sheet keys after the data sheets");
    }

    @Test
    void theDocumentedBuilderExampleBecomesThePlanTheLegacyParserBuilt() {
        String flags = "FW_Exclude";
        ScenarioSpec spec = spec(sheets("HEAD:1", "COST:3", "LAT:3", "TAIL:1", "ROW:1"),
                row(0, "HEAD", flags, "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"),
                row(1, "COST", flags, "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"),
                row(2, "LAT", flags, "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"),
                row(3, "TAIL", flags, "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"),
                row(4, "ROW", "FW_(HEAD,,COST,,LAT,,TAIL,,M:N)"));
        SeqPlanner.Planned planned = plan(spec);
        SeqPlan plan = planned.plan();
        assertEquals(List.of(), planned.diagnostics());
        for (String name : List.of("HEAD", "COST", "LAT", "TAIL")) {
            SheetPlan sheet = plan.sheet(name).orElseThrow();
            assertEquals(SheetRole.EXCLUDED, sheet.role(), name);
            assertTrue(sheet.reuse() && !sheet.reuseTableOnly() && !sheet.promoted(), name);
            assertEquals(List.of("FW_Combi(1)", "FW_Combi(1)"), directives(plan, name), "two verbs are already the dual form: no promotion");
        }
        SheetPlan row = plan.sheet("ROW").orElseThrow();
        assertEquals(SheetRole.MANDATORY, row.role());
        assertEquals(List.of("FW_(HEAD,,COST,,LAT,,TAIL,,M:N)"), directives(plan, "ROW"));
        assertEquals(List.of(2), plan.firstOperands());
        assertEquals(List.of(3), plan.secondOperands());
    }

    // ---- flags ------------------------------------------------------------------------------------------------

    @Test
    void aFlagMovesASheetOnlyWhileItIsMandatoryAndTheFirstMoveWins() {
        SeqPlanner.Planned planned = plan(spec(sheets("A", "B", "C", "D", "E:0", "F", "G", "H", "I", "J"),
                row(0, "A", "FW_Exclude", "FW_Combi(1)"),
                row(1, "B", "FW_Optional", "FW_Combi(1)"),
                row(2, "C", "FW_Heading", "FW_Combi(1)"),
                row(3, "D", "FW_LastInQueue", "FW_Reuse", "FW_ReuseTableOnly", "FW_Combi(1)"),
                row(4, "E", "FW_Exclude", "FW_Optional", "FW_Combi(1)"),
                row(5, "F", "FW_Exclude", "FW_Optional"),
                row(6, "G", "FW_Optional", "FW_Exclude"),
                row(7, "H", "FW_Exclude", "FW_LastInQueue"),
                row(8, "I", "FW_LastInQueue", "FW_Exclude"),
                row(9, "J", "FW_Exclude"),
                row(10, "J", "FW_Optional")));
        SeqPlan plan = planned.plan();
        assertEquals(SheetRole.EXCLUDED, plan.sheet("A").orElseThrow().role());
        assertEquals(SheetRole.OPTIONAL, plan.sheet("B").orElseThrow().role());
        SheetPlan c = plan.sheet("C").orElseThrow();
        assertEquals(SheetRole.EXCLUDED, c.role());
        assertTrue(c.heading(), "a heading is excluded like FW_Exclude and remembered as a heading");
        SheetPlan d = plan.sheet("D").orElseThrow();
        assertEquals(SheetRole.MANDATORY, d.role());
        assertTrue(d.lastInQueue() && d.reuse() && d.reuseTableOnly());
        assertEquals(SheetRole.NONE, plan.sheet("E").orElseThrow().role(), "a sheet without values has nothing to move");
        assertEquals(SheetRole.EXCLUDED, plan.sheet("F").orElseThrow().role());
        assertEquals(SheetRole.OPTIONAL, plan.sheet("G").orElseThrow().role());
        assertFalse(plan.sheet("H").orElseThrow().lastInQueue(), "nothing was left to copy once the sheet was excluded");
        assertTrue(plan.sheet("I").orElseThrow().lastInQueue());
        assertEquals(SheetRole.EXCLUDED, plan.sheet("I").orElseThrow().role());
        assertEquals(SheetRole.EXCLUDED, plan.sheet("J").orElseThrow().role(), "a later row cannot move it again");

        assertEquals(List.of(ErrorCode.PLAN_CONFLICTING_FLAGS, ErrorCode.PLAN_CONFLICTING_FLAGS, ErrorCode.PLAN_CONFLICTING_FLAGS),
                codes(planned.diagnostics()));
        assertEquals(List.of("F", "G", "J"), planned.diagnostics().stream().map(x -> x.attributes().get("sheet")).toList());
        assertTrue(planned.diagnostics().getFirst().message().contains("FW_Optional") && planned.diagnostics().getFirst().message().contains("FW_Exclude"));
        assertTrue(planned.diagnostics().get(1).message().contains("flagged FW_Exclude but already moved by FW_Optional"));
        assertEquals("FW_Seq!10:1", planned.diagnostics().get(2).location().path());
        assertTrue(planned.diagnostics().stream().allMatch(x -> x.severity() == Severity.WARNING));
    }

    @Test
    void theConcatenatorIsTheTextAfterTheEqualsSignAndRowsKeepAccumulatingFlags() {
        SeqPlan plan = plan(spec(sheets("A", "B"), row(0, "A", "FW_Concatenator=, ", "FW_Combi(1)"),
                row(1, "B", "FW_Concatenator=", "FW_Combi(1)"))).plan();
        assertEquals(", ", plan.sheet("A").orElseThrow().concatenator());
        assertEquals("", plan.sheet("B").orElseThrow().concatenator());
        assertTrue(plan.sheet("A").orElseThrow().directives().size() == 2 && plan.sheet("B").orElseThrow().directives().size() == 2);

        List<Diagnostic> bad = failures(spec(sheets("A"), row(0, "A", "FW_Concatenator", "FW_Combi(1)")), LEGACY);
        assertEquals(List.of(ErrorCode.PLAN_DIRECTIVE_SYNTAX), codes(bad));
        assertTrue(bad.getFirst().message().contains("FW_Concatenator=text"));
        // without generated sheets nothing can be the target of a row that starts with the token
        List<Diagnostic> orphan = failures(spec(sheets("A"), row(0, "FW_Concatenator=x", "A")), with(false, "FW_VIRTUAL_", true, true, true, true));
        assertEquals(List.of(ErrorCode.PLAN_UNKNOWN_SHEET), codes(orphan));
    }

    /**
     * Architect decision F23: white space around a flag or directive cell is insignificant (the legacy endsWith and
     * startsWith tests missed " FW_Optional " and read "FW_Combi(2) " as FW_Combi(1)); a sheet name still matches
     * exactly, and a concatenator keeps everything after its '='.
     */
    @Test
    void whiteSpaceAroundAFlagOrDirectiveCellIsInsignificantButAConcatenatorKeepsItsValue() {
        SeqPlan spaced = plan(spec(sheets("A:2", "B"), row(0, "A", " FW_Optional\t", " FW_Combi( 2 ) "),
                row(1, "B", "  FW_Concatenator=, ", "\tFW_Permut "))).plan();
        SeqPlan plain = plan(spec(sheets("A:2", "B"), row(0, "A", "FW_Optional", "FW_Combi(2)"),
                row(1, "B", "FW_Concatenator=, ", "FW_Permut"))).plan();
        assertEquals(SheetRole.OPTIONAL, spaced.sheet("A").orElseThrow().role());
        assertEquals(List.of("FW_Combi(2)", "FW_Combi(2)"), directives(spaced, "A"), "promoted like the plain spelling");
        assertEquals(", ", spaced.sheet("B").orElseThrow().concatenator(), "the value after '=' keeps its white space");
        assertEquals(plain.canonicalText(), spaced.canonicalText());
        // a sheet name with white space around it is no target (unchanged rule, with its explanation)
        List<Diagnostic> padded = plan(spec(sheets("A"), row(0, "A", "FW_Combi(1)"), row(1, " A", "FW_Combi(1)"))).diagnostics();
        assertTrue(padded.stream().anyMatch(d -> d.code() == ErrorCode.PLAN_IGNORED_CELL
                && d.message().contains("white space around the name of a sheet")), padded.toString());
    }

    // ---- targets and overwriting ------------------------------------------------------------------------------

    @Test
    void aRowThatNamesNoSheetAndGetsNoGeneratedTargetTakesTheTargetOfTheRowBeforeAndReplacesItsDirectives() {
        // a row that starts with an FW_ token normally gets a generated target; inheritance needs that switched off,
        // either by not generating sheets at all or by not letting headless rows use the generated one
        ScenarioSpec spec = spec(sheets("A", "B"), row(0, "A", "FW_Combi(1)"), row(1, "FW_Permut(2)"));
        PlanOptions noGeneratedSheets = with(false, "FW_VIRTUAL_", true, true, true, true);
        PlanOptions headlessNotUsed = with(true, "FW_VIRTUAL_", false, true, true, true);
        for (PlanOptions options : List.of(noGeneratedSheets, headlessNotUsed)) {
            SeqPlanner.Planned planned = SeqPlanner.plan(spec, options);
            assertEquals(List.of("FW_Permut(2)", "FW_Permut(2)"), directives(planned.plan(), "A"), options.toString());
            assertTrue(planned.plan().sheet("A").orElseThrow().promoted());
            assertFalse(planned.plan().sheet("B").orElseThrow().active());
            List<Diagnostic> warnings = planned.diagnostics().stream().filter(d -> d.code() != ErrorCode.PLAN_VIRTUAL_SHEET).toList();
            assertEquals(List.of(ErrorCode.PLAN_AMBIGUOUS_TARGET, ErrorCode.PLAN_ROW_OVERWRITTEN), codes(warnings), options.toString());
            assertTrue(warnings.get(0).message().contains("row 2") && warnings.get(0).message().contains("'A'"), warnings.get(0).message());
            assertTrue(warnings.get(1).message().contains("row 2 replaces the directives that row 1 gave to 'A'"), warnings.get(1).message());
            assertEquals("FW_Seq!1:0", warnings.get(1).location().path());
        }
        // the second variant still generated the sheet in the pre-scan, as the legacy parser did, and says so
        assertEquals(List.of(ErrorCode.PLAN_VIRTUAL_SHEET, ErrorCode.PLAN_AMBIGUOUS_TARGET, ErrorCode.PLAN_ROW_OVERWRITTEN),
                codes(SeqPlanner.plan(spec, headlessNotUsed).diagnostics()));
        assertEquals(List.of(ErrorCode.PLAN_AMBIGUOUS_TARGET, ErrorCode.PLAN_ROW_OVERWRITTEN),
                codes(SeqPlanner.plan(spec, noGeneratedSheets).diagnostics()));
    }

    @Test
    void aFlagBeforeTheSheetNameOfItsRowHitsTheGeneratedTargetOrTheTargetOfTheRowBefore() {
        ScenarioSpec spec = spec(sheets("A", "B"), row(0, "A", "FW_Combi(1)"), row(1, "FW_Exclude", "B", "FW_Combi(1)"));
        // the row starts with an FW_ token, so the legacy parser generated a target for it and the flag went there
        SeqPlanner.Planned generated = plan(spec);
        assertEquals(SheetRole.MANDATORY, generated.plan().sheet("A").orElseThrow().role());
        assertEquals(SheetRole.MANDATORY, generated.plan().sheet("B").orElseThrow().role(), "the flag did not reach B either");
        SheetPlan unused = generated.plan().sheet("FW_VIRTUAL_2").orElseThrow();
        assertEquals(3, unused.key());
        assertFalse(unused.active(), "the directives of the row are stored under the last sheet it names");
        assertEquals(List.of("FW_Combi(1)", "FW_Combi(1)"), directives(generated.plan(), "B"));
        assertEquals(List.of(ErrorCode.PLAN_VIRTUAL_SHEET, ErrorCode.PLAN_AMBIGUOUS_TARGET), codes(generated.diagnostics()));
        Diagnostic hazard = generated.diagnostics().get(1);
        assertTrue(hazard.message().contains("FW_Exclude comes before the sheet name 'B'")
                && hazard.message().contains("generated sheet 'FW_VIRTUAL_2'") && hazard.message().contains("not to 'B'"), hazard.message());
        assertEquals("FW_Seq!1:0", hazard.location().path());

        // with the generated target switched off the flag lands on the target of the row before
        SeqPlanner.Planned inherited = SeqPlanner.plan(spec, with(true, "FW_VIRTUAL_", false, true, true, true));
        assertEquals(SheetRole.EXCLUDED, inherited.plan().sheet("A").orElseThrow().role(), "the legacy hazard, now reported");
        assertEquals(SheetRole.MANDATORY, inherited.plan().sheet("B").orElseThrow().role());
        Diagnostic ambiguous = inherited.diagnostics().stream().filter(d -> d.code() == ErrorCode.PLAN_AMBIGUOUS_TARGET).findFirst().orElseThrow();
        assertTrue(ambiguous.message().contains("FW_Exclude comes before the sheet name of its row, so it applies to 'A'"), ambiguous.message());
    }

    @Test
    void aGeneratedTargetThatTheRowUsesWithoutNamingASheetCausesNoWarning() {
        SeqPlanner.Planned planned = plan(spec(sheets("A"), row(0, "FW_Exclude", "FW_Combi(2)")));
        SheetPlan generated = planned.plan().sheet("FW_VIRTUAL_1").orElseThrow();
        assertTrue(generated.active());
        assertEquals(List.of(ErrorCode.PLAN_VIRTUAL_SHEET), codes(planned.diagnostics()));
    }

    @Test
    void whenARowNamesTwoSheetsItsDirectivesBelongToTheLastOne() {
        SeqPlan plan = plan(spec(sheets("A", "B"), row(0, "A", "B", "FW_Combi(1)"))).plan();
        assertFalse(plan.sheet("A").orElseThrow().active());
        assertEquals(List.of("FW_Combi(1)", "FW_Combi(1)"), directives(plan, "B"));
    }

    @Test
    void laterRowsReplaceTheDirectivesButTheFlagsOfEarlierRowsStay() {
        SeqPlanner.Planned planned = plan(spec(sheets("A"), row(0, "A", "FW_Exclude", "FW_Reuse", "FW_Combi(1)"),
                row(1, "A", "FW_Combi(2)"), row(2, "A", "FW_Optional")));
        SheetPlan a = planned.plan().sheet("A").orElseThrow();
        assertEquals(List.of("FW_Combi(2)", "FW_Combi(2)"), directives(planned.plan(), "A"));
        assertEquals(SheetRole.EXCLUDED, a.role());
        assertTrue(a.reuse());
        assertEquals(List.of(ErrorCode.PLAN_ROW_OVERWRITTEN, ErrorCode.PLAN_CONFLICTING_FLAGS), codes(planned.diagnostics()),
                "a row that has flags only does not overwrite anything");
    }

    @Test
    void severalDirectivesOfOneRowAreKeptInOrderEvenWhenTheSheetNameIsRepeated() {
        SeqPlan plan = plan(spec(sheets("A"), row(0, "A", "FW_Combi(1)", "A", "FW_Permut(2)"))).plan();
        assertEquals(List.of("FW_Combi(1)", "FW_Permut(2)"), directives(plan, "A"));
        assertFalse(plan.sheet("A").orElseThrow().promoted(), "two combination verbs are not promoted");
    }

    // ---- promotion of a single verb ---------------------------------------------------------------------------

    @Test
    void aSingleCombinationVerbIsRepeatedAtTheEndOfTheRowByTheLegacyRuleOfTheCellText() {
        SeqPlan plan = plan(spec(sheets("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10", "S11", "S12", "S13", "A", "B"),
                row(0, "S1", "FW_Combi(1)"),
                row(1, "S2", "FW_Combi"),
                row(2, "S3", "FW_Combi()"),
                row(3, "S4", "FW_Permut"),
                row(4, "S5", "FW_Permut()"),
                row(5, "S6", "FW_Subsets"),
                row(6, "S7", "FW_Subsets()"),
                row(7, "S8", "FW_Subsets_EXACT(2)"),
                row(8, "S9", "FW_Cartes(A)"),
                row(9, "S10", "FW_Group"),
                row(10, "S11", "FW_Separator(A)"),
                row(11, "S12", "FW_Combi(1)", "FW_Permut(2)"),
                row(12, "S13", "FW_Combi(1)", "FW_Group"))).plan();
        assertEquals(List.of("FW_Combi(1)", "FW_Combi(1)"), directives(plan, "S1"));
        assertEquals(List.of("FW_Combi(1)"), directives(plan, "S2"), "without brackets the cell text is not 'FW_Combi('");
        assertEquals(List.of("FW_Combi(1)", "FW_Combi(1)"), directives(plan, "S3"));
        assertEquals(List.of("FW_Permut(1)"), directives(plan, "S4"));
        assertEquals(List.of("FW_Permut(1)", "FW_Permut(1)"), directives(plan, "S5"));
        assertEquals(List.of("FW_Subsets", "FW_Subsets"), directives(plan, "S6"));
        assertEquals(List.of("FW_Subsets", "FW_Subsets"), directives(plan, "S7"));
        assertEquals(List.of("FW_Subsets_EXACT(2)"), directives(plan, "S8"), "only the plain subsets forms are repeated");
        assertEquals(List.of("FW_Cartes(A)"), directives(plan, "S9"));
        assertEquals(List.of("FW_Group"), directives(plan, "S10"));
        assertEquals(List.of("FW_Separator(A)"), directives(plan, "S11"));
        assertEquals(List.of("FW_Combi(1)", "FW_Permut(2)"), directives(plan, "S12"));
        assertEquals(List.of("FW_Combi(1)", "FW_Group", "FW_Combi(1)"), directives(plan, "S13"), "the copy goes to the end, after the group");
        assertEquals(List.of(true, false, true, false, true, true, true, false, false, false, false, false, true),
                plan.sheets().subList(0, 13).stream().map(SheetPlan::promoted).toList());
    }

    @Test
    void aJoinerRowIsNeverPromotedAndNeitherIsARowWithoutTheOption() {
        SeqPlan plan = plan(spec(sheets("J1", "J2", "A", "B"), row(0, "J1", "FW_(,,A,,B,,,,M:N)"),
                row(1, "J2", "FW_Combi(1)", "FW_(,,A,,B,,,,M:N)"))).plan();
        assertEquals(List.of("FW_(,,A,,B,,,,M:N)"), directives(plan, "J1"));
        assertEquals(List.of("FW_Combi(1)", "FW_(,,A,,B,,,,M:N)"), directives(plan, "J2"));
        assertFalse(plan.sheet("J2").orElseThrow().promoted());

        SeqPlan off = SeqPlanner.plan(spec(sheets("A"), row(0, "A", "FW_Combi(1)")), with(true, "FW_VIRTUAL_", true, true, false, true)).plan();
        assertEquals(List.of("FW_Combi(1)"), directives(off, "A"));
        assertNotEquals(plan(spec(sheets("A"), row(0, "A", "FW_Combi(1)"))).plan().canonicalText(), off.canonicalText());
    }

    // ---- virtual sheets ---------------------------------------------------------------------------------------

    @Test
    void aRowThatStartsWithAnFwTokenGetsAGeneratedTargetNamedAfterItsPosition() {
        SeqPlanner.Planned planned = plan(spec(sheets("A"), row(0, "FW_Combi(2)"), row(1, "A", "FW_Combi(1)")));
        SeqPlan plan = planned.plan();
        SheetPlan virtual = plan.sheet("FW_VIRTUAL_1").orElseThrow();
        assertTrue(virtual.virtual());
        assertEquals(2, virtual.key());
        assertEquals(SheetRole.NONE, virtual.role());
        assertEquals(0, virtual.valueCount());
        assertEquals(java.util.Map.of(0, 2), plan.headlessTargets());
        assertEquals(List.of("FW_Combi(2)", "FW_Combi(2)"), directives(plan, "FW_VIRTUAL_1"));
        assertEquals(List.of(ErrorCode.PLAN_VIRTUAL_SHEET), codes(planned.diagnostics()));
        Diagnostic note = planned.diagnostics().getFirst();
        assertEquals(Severity.INFO, note.severity());
        assertTrue(note.message().contains("row 1") && note.message().contains("FW_VIRTUAL_1"), note.message());
        assertEquals("FW_Seq!0:0", note.location().path());
    }

    @Test
    void aGeneratedNameThatIsTakenGetsASuffixStartingAtTwo() {
        PlanOptions options = with(true, "V", true, true, true, true);
        SeqPlan plan = SeqPlanner.plan(spec(sheets("V3"), row(0, "V3", "FW_Combi(1)"), row(2, "FW_Combi(2)")), options).plan();
        assertEquals(List.of("V3", "V3_2"), plan.sheets().stream().map(SheetPlan::name).toList());
        assertEquals(java.util.Map.of(2, 2), plan.headlessTargets());
        SeqPlan first = SeqPlanner.plan(spec(sheets("V1"), row(0, "FW_Combi(1)")), options).plan();
        assertEquals("V1_2", first.sheets().get(1).name());
    }

    @Test
    void aFirstCellThatNamesNoSheetBecomesAnEmptyVirtualSheetWithAWarning() {
        SeqPlanner.Planned planned = plan(spec(sheets("A"), row(0, "Ghost", "FW_Combi(1)")));
        SheetPlan ghost = planned.plan().sheet("Ghost").orElseThrow();
        assertTrue(ghost.virtual() && ghost.active());
        assertEquals(2, ghost.key());
        assertEquals(1, planned.diagnostics().size());
        Diagnostic warning = planned.diagnostics().getFirst();
        assertEquals(ErrorCode.PLAN_VIRTUAL_SHEET, warning.code());
        assertEquals(Severity.WARNING, warning.severity());
        assertTrue(warning.message().contains("first cell of FW_Seq row 1") && warning.message().contains("'Ghost'"), warning.message());
        assertEquals("Ghost", warning.attributes().get("sheet"));
    }

    @Test
    void anUnknownJoinerOperandBecomesAVirtualSheetAndALiteralSeparatorIsCalledOutAsSuch() {
        SeqPlanner.Planned planned = plan(spec(sheets("ROW", "E1"), row(0, "ROW", "FW_(,,E1,,E2,,,;,M:N)")));
        SeqPlan plan = planned.plan();
        assertEquals(List.of("ROW", "E1", "E2", ";"), plan.sheets().stream().map(SheetPlan::name).toList());
        assertEquals(List.of(1L), List.of(plan.sheets().stream().filter(s -> s.name().equals(";")).count()));
        assertEquals(List.of(ErrorCode.PLAN_VIRTUAL_SHEET, ErrorCode.PLAN_VIRTUAL_SHEET), codes(planned.diagnostics()));
        assertTrue(planned.diagnostics().stream().allMatch(d -> d.severity() == Severity.WARNING && d.location().path().equals("FW_Seq!0:1")));
        assertTrue(planned.diagnostics().get(1).message().contains("separator field names a sheet"), planned.diagnostics().get(1).message());
        assertEquals(List.of(2), plan.firstOperands());
        assertEquals(List.of(3), plan.secondOperands());
    }

    @Test
    void whiteSpaceAroundASheetNameFindsTheSheetWhenScanningButNotWhenMatchingTargets() {
        List<Diagnostic> found = failures(spec(sheets("A"), row(0, " A ", "FW_Combi(1)")), LEGACY);
        assertEquals(List.of(ErrorCode.PLAN_IGNORED_CELL, ErrorCode.PLAN_UNKNOWN_SHEET), codes(found));
        assertTrue(found.get(0).message().contains("white space around the name of a sheet"), found.get(0).message());
        assertEquals(Severity.WARNING, found.get(0).severity());
    }

    @Test
    void withoutGeneratedSheetsAnUnknownNameIsAnErrorEverywhere() {
        PlanOptions strict = with(false, "FW_VIRTUAL_", true, true, true, true);
        assertEquals(List.of(ErrorCode.PLAN_IGNORED_CELL, ErrorCode.PLAN_UNKNOWN_SHEET),
                codes(failures(spec(sheets("A"), row(0, "Ghost", "FW_Combi(1)")), strict)), "no virtual sheet, so no target");
        assertEquals(List.of(ErrorCode.PLAN_UNKNOWN_SHEET), codes(failures(spec(sheets("A"), row(0, "FW_Combi(1)")), strict)));
        List<Diagnostic> operand = failures(spec(sheets("A", "B"), row(0, "A", "FW_(,,B,,Zed,,,,M:N)")), strict);
        assertEquals(List.of(ErrorCode.PLAN_UNKNOWN_SHEET), codes(operand));
        assertTrue(operand.getFirst().message().contains("the second table of a joiner") && operand.getFirst().message().contains("'Zed'"),
                operand.getFirst().message());
    }

    @Test
    void headlessRowsNeedTheirOptionToUseTheGeneratedTarget() {
        PlanOptions off = with(true, "FW_VIRTUAL_", false, true, true, true);
        List<Diagnostic> found = failures(spec(sheets("A"), row(0, "FW_Combi(1)")), off);
        assertEquals(List.of(ErrorCode.PLAN_VIRTUAL_SHEET, ErrorCode.PLAN_UNKNOWN_SHEET), codes(found),
                "the pre-scan still generates the sheet, as in the legacy parser, but the row does not use it");
    }

    // ---- references --------------------------------------------------------------------------------------------

    @Test
    void everyReferenceOfEveryDirectiveIsCheckedAndAllProblemsAreReportedTogether() {
        PlanOptions strict = with(false, "FW_VIRTUAL_", true, true, true, true);
        ScenarioSpec spec = spec(sheets("A", "B", "TMP", "C"),
                row(0, "A", "FW_Cartes(Bb)", "FW_Separator(Zzzzz)"),
                row(1, "B", "FW_Group\nFW_ReplaceRE(\"x\", \"[\" + Nada + \"]\")"),
                row(2, "C", "FW_(St,,B,Rel,TMP,,En,Sp,M:N)"));
        List<Diagnostic> found = failures(spec, strict);
        assertTrue(found.stream().allMatch(d -> d.code() == ErrorCode.PLAN_UNKNOWN_SHEET && d.severity() == Severity.ERROR), found.toString());
        List<String> sheetsNamed = found.stream().map(d -> d.attributes().get("sheet")).toList();
        assertEquals(List.of("Bb", "Zzzzz", "Nada", "St", "Rel", "En", "Sp"), sheetsNamed,
                "the replacement \"[\" + Nada + \"]\" names the sheet Nada: its non-word characters are dropped");
        assertTrue(found.get(0).message().contains("did you mean 'B'?"), found.get(0).message());
        assertFalse(found.get(1).message().contains("did you mean"), found.get(1).message());
        assertEquals(List.of("FW_Cartes", "FW_Separator", "the replacement \"[\" + Nada + \"]\"", "the start of a joiner",
                        "the relation of a joiner", "the end of a joiner", "the separator of a joiner"),
                found.stream().map(d -> d.message().substring(0, d.message().indexOf(" in '"))).toList());
        assertTrue(found.stream().allMatch(d -> d.location().isKnown()));
        assertEquals("FW_Seq!1:1", found.get(2).location().path());

        SeqPlan resolved = SeqPlanner.plan(spec(sheets("A", "B", "TMP"), row(0, "A", "FW_Cartes(B)", "FW_Separator(TMP)"),
                row(1, "B", "FW_Group\nFW_ReplaceRE(\"x\", \"[\" + TMP + \"]\")")), strict).plan();
        assertEquals(List.of("FW_Cartes(B)", "FW_Separator(TMP)"), directives(resolved, "A"));
    }

    @Test
    void withGeneratedSheetsTheJoinerNamesBecomeVirtualSheetsWithWarningsOnly() {
        SeqPlanner.Planned planned = plan(spec(sheets("A", "B", "C"), row(0, "C", "FW_(St,,A,Rel,B,,En,Sp,M:N)")));
        assertEquals(List.of("A", "B", "C", "St", "Rel", "En", "Sp"), planned.plan().sheets().stream().map(SheetPlan::name).toList());
        assertEquals(4, planned.diagnostics().stream().filter(d -> d.code() == ErrorCode.PLAN_VIRTUAL_SHEET).count());
    }

    // ---- nested joiners ---------------------------------------------------------------------------------------

    @Test
    void aNestedPlaceholderNamesTheMostRecentEarlierJoinerTargetsNewestFirstAndMarksThemForReuse() {
        ScenarioSpec spec = spec(sheets("X", "Y", "Z", "J1", "J2", "J3"),
                row(0, "J1", "FW_(,,X,,Y,,,,M:N)"),
                row(1, "J2", "FW_(,,FW_(),,Z,,,,M:N)"),
                row(2, "J3", "FW_(,,FW_()G,,FW_(),,,,M:N)"));
        SeqPlan plan = plan(spec).plan();
        assertEquals(List.of("FW_(,,X,,Y,,,,M:N)"), directives(plan, "J1"));
        assertEquals(List.of("FW_(,,J1~FWN,,Z,,,,M:N)"), directives(plan, "J2"));
        assertEquals(List.of("FW_(,,J2~FWG,,J1~FWN,,,,M:N)"), directives(plan, "J3"));
        assertEquals("FW_(,,FW_()G,,FW_(),,,,M:N)", plan.sheet("J3").orElseThrow().directives().getFirst().source(), "the text as written is kept");
        assertEquals(List.of(1, 4, 5), plan.firstOperands());
        assertEquals(List.of(2, 3, 4), plan.secondOperands());
        assertTrue(plan.sheet("J1").orElseThrow().reuse() && plan.sheet("J1").orElseThrow().reuseTableOnly());
        assertTrue(plan.sheet("J2").orElseThrow().reuse() && plan.sheet("J2").orElseThrow().reuseTableOnly());
        assertFalse(plan.sheet("J3").orElseThrow().reuse() || plan.sheet("J3").orElseThrow().reuseTableOnly());
        assertFalse(plan.sheet("X").orElseThrow().reuse());
    }

    @Test
    void aNestedPlaceholderWithNoEarlierJoinerIsAnErrorAndSoIsAnotherWhenOnlyOneIsLeft() {
        List<Diagnostic> none = failures(spec(sheets("J", "Z"), row(0, "J", "FW_(,,FW_(),,Z,,,,M:N)")), LEGACY);
        assertEquals(List.of(ErrorCode.PLAN_UNKNOWN_SHEET), codes(none));
        assertTrue(none.getFirst().message().contains("first table") && none.getFirst().message().contains("there is none"), none.getFirst().message());

        List<Diagnostic> one = failures(spec(sheets("J1", "J2", "X", "Y"), row(0, "J1", "FW_(,,X,,Y,,,,M:N)"),
                row(1, "J2", "FW_(,,FW_(),,FW_(),,,,M:N)")), LEGACY);
        assertEquals(List.of(ErrorCode.PLAN_UNKNOWN_SHEET), codes(one));
        assertTrue(one.getFirst().message().contains("second table"), one.getFirst().message());
    }

    @Test
    void nestedPlaceholdersAreRefusedWhenTheOptionIsOff() {
        PlanOptions off = with(true, "FW_VIRTUAL_", true, false, true, true);
        List<Diagnostic> found = failures(spec(sheets("J1", "J2", "X", "Y"), row(0, "J1", "FW_(,,X,,Y,,,,M:N)"),
                row(1, "J2", "FW_(,,FW_(),,Y,,,,M:N)")), off);
        assertEquals(List.of(ErrorCode.PLAN_DIRECTIVE_SYNTAX), codes(found));
        assertTrue(found.getFirst().message().contains("nested joiners are disabled"));
    }

    @Test
    void theOperandListsFollowTheOrderTheJoinersWereReadEvenWhenALaterRowReplacesOne() {
        SeqPlanner.Planned planned = plan(spec(sheets("J", "X", "Y"), row(0, "J", "FW_(,,X,,Y,,,,M:N)"), row(1, "J", "FW_(,,Y,,X,,,,M:N)")));
        assertEquals(List.of("FW_(,,Y,,X,,,,M:N)"), directives(planned.plan(), "J"));
        assertEquals(List.of(2, 3), planned.plan().firstOperands());
        assertEquals(List.of(3, 2), planned.plan().secondOperands());
        assertEquals(List.of(ErrorCode.PLAN_ROW_OVERWRITTEN), codes(planned.diagnostics()));
    }

    // ---- cycles -----------------------------------------------------------------------------------------------

    @Test
    void aJoinerThatWaitsForItsOwnTableIsACycle() {
        List<Diagnostic> found = failures(spec(sheets("A", "X"), row(0, "A", "FW_(,,A,,X,,,,M:N)")), LEGACY);
        assertEquals(List.of(ErrorCode.PLAN_CYCLE), codes(found));
        assertEquals("A -> A", found.getFirst().attributes().get("cycle"));
        assertEquals("FW_Seq!0:1", found.getFirst().location().path());
    }

    @Test
    void joinersThatWaitForEachOtherInACircleAreReportedOncePerCircle() {
        List<Diagnostic> two = failures(spec(sheets("A", "B", "X"), row(0, "A", "FW_(,,B,,X,,,,M:N)"), row(1, "B", "FW_(,,A,,X,,,,M:N)")), LEGACY);
        assertEquals(List.of("A -> B -> A"), two.stream().map(d -> d.attributes().get("cycle")).toList());
        List<Diagnostic> three = failures(spec(sheets("A", "B", "C", "X"), row(0, "A", "FW_(,,B,,X,,,,M:N)"), row(1, "B", "FW_(,,C,,X,,,,M:N)"),
                row(2, "C", "FW_(,,A,,X,,,,M:N)")), LEGACY);
        assertEquals(List.of("A -> B -> C -> A"), three.stream().map(d -> d.attributes().get("cycle")).toList());
        List<Diagnostic> disjoint = failures(spec(sheets("A", "B", "C", "D", "X"), row(0, "A", "FW_(,,B,,X,,,,M:N)"), row(1, "B", "FW_(,,A,,X,,,,M:N)"),
                row(2, "C", "FW_(,,D,,X,,,,M:N)"), row(3, "D", "FW_(,,C,,X,,,,M:N)")), LEGACY);
        assertEquals(List.of("A -> B -> A", "C -> D -> C"), disjoint.stream().map(d -> d.attributes().get("cycle")).toList());
    }

    @Test
    void sharedDependenciesAreNotCycles() {
        SeqPlan plan = plan(spec(sheets("A", "B", "C", "D", "X"), row(0, "A", "FW_(,,B,,C,,,,M:N)"), row(1, "B", "FW_(,,D,,X,,,,M:N)"),
                row(2, "C", "FW_(,,D,,X,,,,M:N)"))).plan();
        assertEquals(3, plan.activeSheets().size());
    }

    // ---- decorations, unknown cells and the plan's identity ----------------------------------------------------

    @Test
    void sheetNameRowsAreKeptAndUnlistedSheetsGetEmptyOnesWhenEnabled() {
        ControlSpec controls = new ControlSpec(List.of(new SheetDecoration("A", "p", "e", SourceLocation.ofPath("t.json", "FW_SheetNames!0:0")),
                new SheetDecoration("Gost", "x", "y", SourceLocation.ofPath("t.json", "FW_SheetNames!1:0"))), List.of(), List.of(), List.of(new CustomVar(1, "d", SourceLocation.NONE)));
        ScenarioSpec spec = new ScenarioSpec("t.json", sheets("A", "B"), controls, true,
                List.of(row(0, "A", "FW_Combi(1)"), row(1, "Ghost", "FW_Combi(1)")));
        SeqPlanner.Planned planned = plan(spec);
        assertEquals(List.of("A|p|e", "Gost|x|y", "B||", "Ghost||"),
                planned.plan().decorations().stream().map(d -> d.sheet() + "|" + d.prefix() + "|" + d.ending()).toList());
        Diagnostic unknown = planned.diagnostics().stream().filter(d -> d.code() == ErrorCode.PLAN_UNKNOWN_SHEET).findFirst().orElseThrow();
        assertEquals(Severity.WARNING, unknown.severity());
        assertTrue(unknown.message().contains("FW_SheetNames lists 'Gost'") && unknown.message().contains("did you mean 'Ghost'?"), unknown.message());
        assertEquals("FW_SheetNames!1:0", unknown.location().path());

        SeqPlan off = SeqPlanner.plan(spec, with(true, "FW_VIRTUAL_", true, true, true, false)).plan();
        assertEquals(List.of("A", "Gost"), off.decorations().stream().map(SheetDecoration::sheet).toList());
    }

    @Test
    void aMissingOrEmptySequenceSheetStopsThePlan() {
        FrameworkException absent = assertThrows(FrameworkException.class,
                () -> plan(new ScenarioSpec("t.json", sheets("A"), ControlSpec.EMPTY, false, List.of())));
        assertEquals(ErrorCode.PLAN_NO_SEQUENCE, absent.code());
        assertTrue(absent.primary().message().contains("no FW_Seq sheet"));
        FrameworkException empty = assertThrows(FrameworkException.class,
                () -> plan(new ScenarioSpec("t.json", sheets("A"), ControlSpec.EMPTY, true, List.of())));
        assertEquals(ErrorCode.PLAN_NO_SEQUENCE, empty.code());
        assertTrue(empty.primary().message().contains("no row with content"));
    }

    @Test
    void unknownTokensAndMisspelledFlagsAreErrorsAndStrayTextIsAWarning() {
        List<Diagnostic> found = failures(spec(sheets("A"),
                row(0, "A", "FW_Optionl"), row(1, "A", "FW_Zzzzzzzz"), row(2, "A", "FW_Combi(abc)"), row(3, "A", "FW_Group junk"),
                row(4, "A", "xFW_Exclude"), row(5, "A", "FW_Foo FW_Exclude")), LEGACY);
        assertEquals(List.of(ErrorCode.PLAN_UNKNOWN_DIRECTIVE, ErrorCode.PLAN_UNKNOWN_DIRECTIVE, ErrorCode.PLAN_DIRECTIVE_SYNTAX,
                ErrorCode.PLAN_DIRECTIVE_SYNTAX, ErrorCode.PLAN_UNKNOWN_DIRECTIVE, ErrorCode.PLAN_UNKNOWN_DIRECTIVE), codes(found));
        assertTrue(found.get(0).message().contains("did you mean FW_Optional?"), found.get(0).message());
        assertFalse(found.get(1).message().contains("did you mean"), found.get(1).message());
        assertTrue(found.get(4).message().contains("ends with FW_Exclude") && found.get(4).message().contains("alone in its cell"), found.get(4).message());
        assertTrue(found.get(5).message().contains("did you mean FW_Exclude?"), found.get(5).message());
        assertEquals("FW_Seq!4:1", found.get(4).location().path());

        SeqPlanner.Planned stray = plan(spec(sheets("A"), row(0, "A", "note", "FW_Combi(1)")));
        assertEquals(List.of(ErrorCode.PLAN_IGNORED_CELL), codes(stray.diagnostics()));
        assertTrue(stray.diagnostics().getFirst().message().contains("neither the name of a sheet nor an FW_ token"));
        assertEquals(List.of("FW_Combi(1)", "FW_Combi(1)"), directives(stray.plan(), "A"));
    }

    @Test
    void theIdentityOfAPlanIgnoresLocationsButNotOptionsFlagsOrDirectives() {
        SeqPlan base = plan(spec(sheets("A", "B"), row(0, "A", "FW_Exclude", "FW_Combi(1)"), row(1, "B", "FW_Permut(2)"))).plan();
        List<SheetSpec> moved = new ArrayList<>();
        for (SheetSpec sheet : sheets("A", "B")) {
            moved.add(new SheetSpec(sheet.name(), sheet.key(), sheet.cells(), SourceLocation.of("other.xlsx", 9, 9)));
        }
        SeqRow relocated0 = new SeqRow(0, List.of(new LocatedText("A", SourceLocation.of("x")), new LocatedText("FW_Exclude", SourceLocation.of("x")),
                new LocatedText("FW_Combi(1)", SourceLocation.of("x"))));
        SeqRow relocated1 = new SeqRow(1, List.of(new LocatedText("B", SourceLocation.of("x")), new LocatedText("FW_Permut(2)", SourceLocation.of("x"))));
        SeqPlan elsewhere = plan(new ScenarioSpec("other.xlsx", moved, ControlSpec.EMPTY, true, List.of(relocated0, relocated1))).plan();
        assertEquals(base.canonicalText(), elsewhere.canonicalText());
        assertEquals(base.contentDigest(), elsewhere.contentDigest());

        List<String> others = List.of(
                SeqPlanner.plan(spec(sheets("A", "B"), row(0, "A", "FW_Exclude", "FW_Combi(1)"), row(1, "B", "FW_Permut(3)")), LEGACY).plan().contentDigest(),
                SeqPlanner.plan(spec(sheets("A", "B"), row(0, "A", "FW_Optional", "FW_Combi(1)"), row(1, "B", "FW_Permut(2)")), LEGACY).plan().contentDigest(),
                SeqPlanner.plan(spec(sheets("A", "B"), row(0, "A", "FW_Exclude", "FW_Combi(1)"), row(1, "B", "FW_Permut(2)")),
                        with(false, "FW_VIRTUAL_", true, true, true, true)).plan().contentDigest(),
                SeqPlanner.plan(spec(sheets("A", "B"), row(0, "A", "FW_Exclude", "FW_Combi(1)"), row(1, "B", "FW_Permut(2)")),
                        with(true, "V_", true, true, true, true)).plan().contentDigest(),
                SeqPlanner.plan(spec(sheets("A", "B"), row(0, "A", "FW_Exclude", "FW_Combi(1)"), row(1, "B", "FW_Permut(2)")),
                        with(true, "FW_VIRTUAL_", false, true, true, true)).plan().contentDigest(),
                SeqPlanner.plan(spec(sheets("A", "B"), row(0, "A", "FW_Exclude", "FW_Combi(1)"), row(1, "B", "FW_Permut(2)")),
                        with(true, "FW_VIRTUAL_", true, false, true, true)).plan().contentDigest(),
                SeqPlanner.plan(spec(sheets("A", "B"), row(0, "A", "FW_Exclude", "FW_Combi(1)"), row(1, "B", "FW_Permut(2)")),
                        with(true, "FW_VIRTUAL_", true, true, false, true)).plan().contentDigest(),
                SeqPlanner.plan(spec(sheets("A", "B"), row(0, "A", "FW_Exclude", "FW_Combi(1)"), row(1, "B", "FW_Permut(2)")),
                        with(true, "FW_VIRTUAL_", true, true, true, false)).plan().contentDigest());
        assertEquals(others.size(), others.stream().distinct().count());
        assertFalse(others.contains(base.contentDigest()));
    }

    /** Review finding IP01, the reviewer's two reproductions at plan level: different replacement programs, one digest. */
    @Test
    void plansWithDifferentReplacementProgramsHaveDifferentIdentities() {
        String[][] pairs = {
                {"FW_Group FW_ReplaceRE(\"5\", \"\" + X + \"\")", "FW_Group FW_ReplaceRE(\"5\", \"[\" + X + \"]\")"},
                {"FW_Group FW_ReplaceRE(\"a=>b\", \"c\")", "FW_Group FW_ReplaceRE(\"a\", \"b=>c\")"},
                {"FW_Group FW_ReplaceRE(\"a\", \"b\")\nFW_ReplaceRE(\"c\", \"d\")", "FW_Group FW_ReplaceRE(\"a\", \"b\")"},
                {"FW_(,,X,,Y,,,,M:N)", "FW_(,,Y,,X,,,,M:N)"}};
        for (String[] pair : pairs) {
            SeqPlan one = plan(spec(sheets("A", "X", "Y"), row(0, "A", "FW_Combi(1)", pair[0]), row(1, "X", "FW_Combi(1)"),
                    row(2, "Y", "FW_Combi(1)"))).plan();
            SeqPlan two = plan(spec(sheets("A", "X", "Y"), row(0, "A", "FW_Combi(1)", pair[1]), row(1, "X", "FW_Combi(1)"),
                    row(2, "Y", "FW_Combi(1)"))).plan();
            assertNotEquals(one.canonicalText(), two.canonicalText(), pair[0]);
            assertNotEquals(one.contentDigest(), two.contentDigest(), pair[0]);
        }
        // white space around the tokens of a replacement is not part of the identity
        SeqPlan tight = plan(spec(sheets("A", "X"), row(0, "A", "FW_Combi(1)", "FW_Group FW_ReplaceRE(\"5\", \"[\"+X+\"]\")"))).plan();
        SeqPlan loose = plan(spec(sheets("A", "X"), row(0, "A", "FW_Combi(1)", "FW_Group\n FW_ReplaceRE( \"5\" , \"[\" + X + \"]\" )"))).plan();
        assertEquals(tight.contentDigest(), loose.contentDigest());
    }

    /**
     * Review finding IP02: an acyclic chain of joiners overflowed the thread stack in the recursive cycle check.
     * 16,000 chained sheets and a leaf (S + C = 32,002, inside the packed encoding) plan without error; a back
     * edge near the end of the chain is a PLAN_CYCLE located at the joiner that closes the circle.
     */
    @Test
    void aJoinerChainNearTheKeyCapacityPlansAndABackEdgeNearItsEndIsALocatedCycle() {
        int n = 16_000;
        String[] names = new String[n + 1];
        for (int i = 0; i < n; i++) {
            names[i] = "A" + i;
        }
        names[n] = "Leaf";
        List<SheetSpec> chainSheets = sheets(names);
        List<SeqRow> rows = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            rows.add(row(i, "A" + i, "FW_(,,A" + (i + 1) + ",,Leaf,,,,M:N)"));
        }
        rows.add(row(n - 1, "A" + (n - 1), "FW_Combi(1)"));
        rows.add(row(n, "Leaf", "FW_Combi(1)"));
        SeqPlanner.Planned chain = plan(new ScenarioSpec("t.json", chainSheets, ControlSpec.EMPTY, true, rows));
        assertEquals(n + 1, chain.plan().sheets().size());
        assertTrue(chain.diagnostics().stream().noneMatch(d -> d.code() == ErrorCode.PLAN_CYCLE));

        List<SeqRow> looped = new ArrayList<>(rows);
        looped.set(n - 1, row(n - 1, "A" + (n - 1), "FW_(,,A" + (n - 10) + ",,Leaf,,,,M:N)"));
        List<Diagnostic> found = failures(new ScenarioSpec("t.json", chainSheets, ControlSpec.EMPTY, true, looped), LEGACY);
        List<Diagnostic> cycles = found.stream().filter(d -> d.code() == ErrorCode.PLAN_CYCLE).toList();
        assertEquals(1, cycles.size(), found.toString());
        Diagnostic cycle = cycles.getFirst();
        StringBuilder expected = new StringBuilder();
        for (int i = n - 10; i < n; i++) {
            expected.append("A").append(i).append(" -> ");
        }
        expected.append("A").append(n - 10);
        assertEquals(expected.toString(), cycle.attributes().get("cycle"));
        assertEquals(SourceLocation.ofPath("t.json", "FW_Seq!" + (n - 10) + ":1"), cycle.location(),
                "the joiner of the sheet that closes the circle");
    }
}
