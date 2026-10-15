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

import org.ghyura.cf.input.builder.Flag;
import org.ghyura.cf.input.builder.JoinSpec;
import org.ghyura.cf.input.builder.Multiplicity;
import org.ghyura.cf.input.builder.ScheduleBuilder;
import org.ghyura.cf.input.builder.Verb;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.ghyura.cf.input.InputTestSupport.expectCode;
import static org.ghyura.cf.input.InputTestSupport.shape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Gate A03, builder side: the Java builder produces the model a file with the same sheets would. */
class ScheduleBuilderTest {

    /** The builder example documented in the legacy ProgrammaticScheduleBuilder. */
    static ScheduleBuilder legacyExample() {
        return ScheduleBuilder.newSchedule()
                .sheet("HEAD").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1)).row("scenario=demo")
                .sheet("COST").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1))
                .rows(" id=1 cost=0.10", " id=2 cost=0.30", " id=3 cost=0.50")
                .sheet("LAT").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1))
                .rows(" latency=10ms", " latency=30ms", " latency=80ms")
                .sheet("TAIL").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1)).row(" status=ok")
                .join("ROW", JoinSpec.from("HEAD").via("COST", "LAT").to("TAIL").as(Multiplicity.M_N)).row("FW_EMPTY_STRING")
                .runMeFirstOnce("class RunMeFirstOnce { public static String FW_ARGS; }")
                .arguments("noargs")
                .customVar(2, "FWCUSTOMVAR=2 nominal");
    }

    /** The same scenario as hand-written JSON in the legacy layout (test_e2e_metric.json style). */
    static final String LEGACY_EXAMPLE_AS_JSON = """
            {"sheets": {
              "FW_Seq": [
                ["HEAD", "FW_Exclude", "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"],
                ["COST", "FW_Exclude", "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"],
                ["LAT",  "FW_Exclude", "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"],
                ["TAIL", "FW_Exclude", "FW_Reuse", "FW_Combi(1)", "FW_Combi(1)"],
                ["ROW",  null, null, "FW_(HEAD,,COST,,LAT,,TAIL,,M:N)"]
              ],
              "FW_SheetNames": [
                ["HEAD", "FW_EMPTY_STRING", "FW_EMPTY_STRING"],
                ["COST", "FW_EMPTY_STRING", "FW_EMPTY_STRING"],
                ["LAT",  "FW_EMPTY_STRING", "FW_EMPTY_STRING"],
                ["TAIL", "FW_EMPTY_STRING", "FW_EMPTY_STRING"],
                ["ROW",  "FW_EMPTY_STRING", "FW_EMPTY_STRING"]
              ],
              "FW_RunMeFirstOnce": [["class RunMeFirstOnce { public static String FW_ARGS; }"]],
              "FW_Arguments": [["noargs"]],
              "FW_CUSTOM_VAR": [[2, "FWCUSTOMVAR=2 nominal"]],
              "HEAD": [["scenario=demo"]],
              "COST": [[" id=1 cost=0.10"], [" id=2 cost=0.30"], [" id=3 cost=0.50"]],
              "LAT":  [[" latency=10ms"], [" latency=30ms"], [" latency=80ms"]],
              "TAIL": [[" status=ok"]],
              "ROW":  [["FW_EMPTY_STRING"]]
            }}
            """;

    @Test
    void theBuilderEqualsTheHandWrittenJsonOfTheSameScenarioCellForCell() {
        WorkbookData built = legacyExample().build();
        WorkbookData json = new JsonWorkbookReader().read(InputSource.ofText("test_e2e_metric.json", LEGACY_EXAMPLE_AS_JSON)).workbook();
        assertEquals(json.sheetNames(), built.sheetNames());
        assertEquals(json.canonicalText(), built.canonicalText());
        assertEquals("programmatic:builder", built.source());
    }

    @Test
    void theSheetLayoutFollowsTheLegacyMaterialisation() {
        WorkbookData built = ScheduleBuilder.newSchedule()
                .sheet("X").verbs("FW_Combi(1)")
                .sheet("Y").flags(Flag.OPTIONAL).verbs(Verb.cartes(), Verb.group(), Verb.separator("/")).rowCells("a", null, "c")
                .and().build();
        assertEquals(List.of(
                "FW_Seq = [0:X|1:|2:|3:FW_Combi(1)] [0:Y|1:FW_Optional|2:|3:FW_Cartes|4:FW_Group|5:FW_Separator(/)]",
                "FW_SheetNames = [0:X|1:FW_EMPTY_STRING|2:FW_EMPTY_STRING] [0:Y|1:FW_EMPTY_STRING|2:FW_EMPTY_STRING]",
                "X =",
                "Y = [0:a|2:c]"), shape(built));
    }

    @Test
    void jointRowsComeLastAndCarryOnlyTheNameAndTheExpression() {
        WorkbookData built = ScheduleBuilder.newSchedule()
                .join("J", JoinSpec.from("A").to("B").withSeparator(";").as(Multiplicity.ONE_N))
                .sheet("A").verbs(Verb.combi(2))
                .joinRaw("K", "FW_(A,,,,,,B,,1:1)")
                .and().build();
        assertEquals("FW_Seq = [0:A|1:|2:|3:FW_Combi(2)] [0:J|3:FW_(A,,,,,,B,;,1:N)] [0:K|3:FW_(A,,,,,,B,,1:1)]",
                shape(built).getFirst());
    }

    @Test
    void joinSpecRendersTheNineFieldForm() {
        assertEquals("FW_(HEAD,,COST,,LAT,,TAIL,,M:N)", JoinSpec.from("HEAD").via("COST", "LAT").to("TAIL").toFwExpression());
        assertEquals("FW_(A,,,,,,B,,M:N)", JoinSpec.from("A").to("B").toString());
        assertEquals("FW_(A,,M,,,,B,#,M:M)", JoinSpec.from("A").via("M", " ", null).to("B").withSeparator("#").as(Multiplicity.M_M).toString());
        assertEquals("FW_(A,,,,,,,,1:1)", JoinSpec.from("A").as(Multiplicity.ONE_1).toString());
    }

    @Test
    void reselectingASheetAppendsAndTheBuilderCanBuildTwice() {
        ScheduleBuilder builder = ScheduleBuilder.newSchedule();
        builder.sheet("A").row("one");
        builder.sheet("A").row("two").flag(Flag.REUSE);
        WorkbookData first = builder.build();
        WorkbookData second = builder.build();
        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(List.of("FW_Seq = [0:A|1:FW_Reuse|2:]", "FW_SheetNames = [0:A|1:FW_EMPTY_STRING|2:FW_EMPTY_STRING]",
                "A = [0:one] [0:two]"), shape(first));
    }

    @Test
    void namesAreValidatedAndUniqueIgnoringCase() {
        ScheduleBuilder builder = ScheduleBuilder.newSchedule();
        assertThrows(IllegalArgumentException.class, () -> builder.sheet(" "));
        assertThrows(IllegalArgumentException.class, () -> builder.sheet(null));
        assertThrows(IllegalArgumentException.class, () -> builder.sheet("FW_Seq"));
        assertThrows(IllegalArgumentException.class, () -> builder.join("FW_X", JoinSpec.from("A")));
        assertThrows(IllegalArgumentException.class, () -> builder.join("J", null));
        assertThrows(IllegalArgumentException.class, () -> builder.joinRaw("J", "not a joiner"));
        builder.sheet("Head");
        assertThrows(IllegalArgumentException.class, () -> builder.sheet("HEAD"));
        builder.sheet("Head"); // the same spelling re-selects
        assertThrows(IllegalArgumentException.class, () -> JoinSpec.from(" "));
        assertThrows(IllegalArgumentException.class, () -> JoinSpec.from("A").to(""));
        assertThrows(IllegalArgumentException.class, () -> JoinSpec.from("A").as(null));
    }

    @Test
    void moreFlagsOrVerbsThanARowHoldsIsAnErrorNotASilentDrop() {
        expectCode(ErrorCode.INPUT_SCHEMA, () -> ScheduleBuilder.newSchedule()
                .sheet("A").flags(Flag.EXCLUDE, Flag.REUSE, Flag.OPTIONAL).build());
        expectCode(ErrorCode.INPUT_SCHEMA, () -> ScheduleBuilder.newSchedule()
                .sheet("A").verbs("v1", "v2", "v3", "v4").build());
        ScheduleBuilder.newSchedule().sheet("A").flags(Flag.EXCLUDE, Flag.REUSE).verbs("v1", "v2", "v3").build();
    }

    @Test
    void controlSheetsAppearOnlyWhenSetAndInTheLegacyOrder() {
        assertEquals(List.of("FW_Seq", "FW_SheetNames"), ScheduleBuilder.newSchedule().build().sheetNames());
        assertEquals(List.of("FW_Seq", "FW_SheetNames", "FW_RunMeFirstOnce", "FW_Arguments", "FW_CUSTOM_VAR", "A"),
                ScheduleBuilder.newSchedule().customVar(1, "x").arguments("a").runMeFirstOnce("r").sheet("A").build().sheetNames());
        WorkbookData vars = ScheduleBuilder.newSchedule().customVar(5, "five").customVar(2, "two").build();
        assertEquals("FW_CUSTOM_VAR = [0:5|1:five] [0:2|1:two]", shape(vars).get(2));
    }
}
