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

package org.ghyura.cf.generation;

import org.ghyura.cf.algebra.LegacyLibrary;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.constraints.Coverage;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.key.SelectionState;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.rule.Gate;
import org.ghyura.cf.model.rule.Rule;
import org.ghyura.cf.model.rule.RuleValue;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Candidate assembly, case by case, with expectations worked out by hand from the legacy FinalTableAssembler and Reader
 * (mandatory product, baseline, optional subsets, absent branch, rendering) and from the supplement's R-M03-SEMANTICS.
 */
class AssemblyTest {

    private static final RenderOptions PLAIN = RenderOptions.defaults();
    private static final AssemblyOptions DEFAULTS = AssemblyOptions.defaults();

    private static AssemblyOptions with(RunConfig.EmptyStagePolicy policy) {
        return DEFAULTS.withEmptyStage(policy);
    }

    private static AssemblyOptions options(boolean distinct, long limit, RunConfig.OptionalMode mode, boolean absent,
                                           RunConfig.SizeSet sizes, long subsetLimit, int strength, long budget) {
        return new AssemblyOptions(RunConfig.EmptyStagePolicy.PRESERVE_EMPTY, distinct, limit, mode, absent, sizes,
                RunConfig.SizeSet.all(), subsetLimit, LegacyLibrary.PARALLEL, strength, Coverage.Method.GREEDY, budget, 2_000_000L);
    }

    private static Scenario twoByThree() {
        return Scenario.of().sheet("A", "a1", "a2").sheet("B", "b1", "b2", "b3")
                .seq("A", "FW_Combi(1)").seq("B", "FW_Combi(1)").options(Scenario.UNPROMOTED)
                .names("A", "A=", ";").names("B", "B=", ";");
    }

    private static List<String> displays(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::display).toList();
    }

    private static List<ErrorCode> codes(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(Diagnostic::code).toList();
    }

    // ---- the mandatory product and its baseline (CR-FINAL-01) ------------------------------------------------------

    @Test
    void aTwoByThreeMandatoryProductGivesSixCandidatesFirstSheetSlowestWithTheLastSeparatorTrimmed() {
        try (Scenario.Run run = twoByThree().run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals(List.of("A=a1;B=b1", "A=a1;B=b2", "A=a1;B=b3", "A=a2;B=b1", "A=a2;B=b2", "A=a2;B=b3"), run.sources(a, PLAIN));
            List<Candidate> c = run.candidates(a);
            assertEquals(List.of("1_0_0", "2_0_0", "3_0_0", "4_0_0", "5_0_0", "6_0_0"), displays(c));
            assertEquals(List.of(SelectionState.BASELINE_REFERENCE, SelectionState.BASELINE_REFERENCE), c.get(0).states(),
                    "the first row of each table is the baseline (legacy fw_final_base)");
            assertEquals(List.of(SelectionState.PRESENT, SelectionState.PRESENT), c.get(4).states());
            assertEquals(BigInteger.valueOf(6), a.generated());
            assertEquals(List.of(BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO), c.subList(0, 3).stream().map(Candidate::ordinal).toList());
        }
    }

    @Test
    void aBaselineRowThatIsEmptyIsAPresentEmptyRowNotAnInventedFirstValue() {
        Scenario s = Scenario.of().sheet("S", "s1", "s2").sheet("B", "b1").seq("S", "FW_Subsets").seq("B", "FW_Combi(1)")
                .options(Scenario.UNPROMOTED).names("S", "S[", "]").names("B", "B=", ";");
        try (Scenario.Run run = s.run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals("", run.rows("S").getFirst(), "FW_Subsets starts with the empty subset");
            List<Candidate> c = run.candidates(a);
            assertEquals(SelectionState.BASELINE_REFERENCE, c.getFirst().states().getFirst());
            assertEquals(0, c.getFirst().keys(0).size(), "the baseline it refers to is the empty row");
            assertEquals(List.of("S[]B=b1", "S[s1]B=b1", "S[s2]B=b1", "S[s1s2]B=b1"), run.sources(a, PLAIN),
                    "the empty baseline writes its prefix and ending; the legacy sieve would have read the first value s1 here");
        }
    }

    // ---- empty stages (supplement R-M03-SEMANTICS 2) ---------------------------------------------------------------

    @Test
    void aRequiredSheetWithoutRowsLeavesNoCandidateUnlessTheLegacyDropIsChosenExplicitly() {
        Scenario s = twoByThree().sheet("E").sheet("C", "c1", "c2").seq("C", "FW_Cartes(E)").names("C", "C=", ";");
        try (Scenario.Run run = s.run(GenerationOptions.defaults())) {
            assertEquals(List.of(), run.rows("C"), "a product with an empty sheet has no rows");
            try (Assembly preserve = run.assemble(DEFAULTS, PLAIN)) {
                assertEquals(BigInteger.ZERO, preserve.generated());
                assertEquals(List.of(), run.candidates(preserve));
                assertTrue(codes(preserve.diagnostics()).contains(ErrorCode.ASSEMBLY_EMPTY_FACTOR));
                Diagnostic d = preserve.diagnostics().stream().filter(x -> x.code() == ErrorCode.ASSEMBLY_EMPTY_FACTOR).findFirst().orElseThrow();
                assertEquals("C", d.attributes().get("sheet"));
                assertTrue(d.message().contains("nothing passed"));
            }
            try (Assembly drop = run.assemble(with(RunConfig.EmptyStagePolicy.LEGACY_DROP_WITH_DIAGNOSTIC), PLAIN)) {
                assertEquals(BigInteger.valueOf(6), drop.generated(), "the legacy dropped the sheet and ran the weaker scenario");
                assertEquals(List.of("C"), drop.droppedSheets());
                assertEquals(List.of("A", "B"), drop.columnSheets());
                assertTrue(codes(drop.diagnostics()).contains(ErrorCode.ASSEMBLY_SHEET_DROPPED));
            }
        }
    }

    @Test
    void zeroMandatorySheetsGiveOneStructuralTupleThatOptionalSheetsExpand() {
        Scenario s = Scenario.of().sheet("O", "o1", "o2").seq("O", "FW_Optional", "FW_Combi(1)").options(Scenario.UNPROMOTED)
                .names("O", "O=", ";");
        try (Scenario.Run run = s.run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals(BigInteger.ONE, a.mandatoryRows(), "the product of no factor is one empty tuple");
            assertEquals(List.of("", "O=o1", "O=o2"), run.sources(a, PLAIN));
            assertEquals(List.of(SelectionState.ABSENT), run.candidates(a).getFirst().states());
        }
        Scenario excludedOnly = Scenario.of().sheet("X", "x").seq("X", "FW_Exclude", "FW_Combi(1)").options(Scenario.UNPROMOTED);
        try (Scenario.Run run = excludedOnly.run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals(List.of(), a.columnSheets(), "an excluded sheet is no column");
            assertEquals(List.of(""), run.sources(a, PLAIN), "no column at all: one structural candidate, with an empty source");
        }
    }

    // ---- optional sheets (CR-FINAL-02, R-M03-SEMANTICS 3-4) --------------------------------------------------------

    @Test
    void twoOptionalSheetsOfTwoAndThreeRowsGiveFiveAndSixOptionalRowsSo66BearingCandidatesPlusSixAbsent() {
        Scenario s = twoByThree().sheet("O1", "o1", "o2").sheet("O2", "p1", "p2", "p3")
                .seq("O1", "FW_Optional", "FW_Combi(1)").seq("O2", "FW_Optional", "FW_Combi(1)")
                .names("O1", "O1=", ";").names("O2", "O2=", ";");
        try (Scenario.Run run = s.run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals(List.of(new long[]{1, 5}[1], new long[]{2, 6}[1]), a.optionalSizes().stream().map(x -> x[1]).toList());
            assertEquals(BigInteger.valueOf(12), a.perMandatoryRow());
            assertEquals(BigInteger.valueOf(72), a.generated());
            List<Candidate> c = run.candidates(a);
            assertEquals(6, c.stream().filter(Candidate::absentBranch).count());
            assertEquals(66, c.stream().filter(x -> !x.absentBranch()).count());
            List<String> sources = run.sources(a, PLAIN);
            assertEquals("A=a1;B=b1", sources.get(0), "the absent branch of every mandatory row comes first");
            assertEquals(List.of("A=a1;B=b1;O1=o1", "A=a1;B=b1;O1=o2", "A=a1;B=b1;O2=p1", "A=a1;B=b1;O2=p2", "A=a1;B=b1;O2=p3"),
                    sources.subList(6, 11), "size 1: subset {O1}, then {O2}");
            assertEquals("A=a1;B=b1;O1=o1;O2=p1", sources.get(36), "size 2 starts after 6 + 6 x 5");
            assertEquals(List.of("1_1_1", "1_2_1", "1_3_1", "1_4_1", "1_5_1"), displays(c.subList(6, 11)));
            assertEquals("2_1_1", c.get(11).display());
            assertEquals("1_6_2", c.get(41).display());
            assertEquals(List.of(SelectionState.BASELINE_REFERENCE, SelectionState.BASELINE_REFERENCE, SelectionState.PRESENT,
                    SelectionState.ABSENT), c.get(6).states());
        }
        try (Scenario.Run run = s.run(GenerationOptions.defaults());
             Assembly sizeTwo = run.assemble(options(false, Long.MAX_VALUE, RunConfig.OptionalMode.AUTO, false,
                     RunConfig.SizeSet.of(List.of(2)), Long.MAX_VALUE, 0, 0), PLAIN)) {
            assertEquals(BigInteger.valueOf(36), sizeTwo.generated(), "only the size-2 rows, without the absent branch");
        }
        try (Scenario.Run run = s.run(GenerationOptions.defaults());
             Assembly off = run.assemble(options(false, Long.MAX_VALUE, RunConfig.OptionalMode.OFF, false,
                     RunConfig.SizeSet.all(), Long.MAX_VALUE, 0, 0), PLAIN)) {
            assertEquals(BigInteger.valueOf(6), off.generated(), "optional sheets off: every candidate without them");
        }
    }

    @Test
    void anOptionalSheetWithoutRowsGivesOnlyItsAbsentBranchAndAPresentEmptyRowStaysPresent() {
        Scenario s = Scenario.of().sheet("M", "m1").sheet("E").sheet("O1", "o1", "o2").sheet("O2", "p1", "p2")
                .seq("M", "FW_Combi(1)").seq("O1", "FW_Optional", "FW_Cartes(E)").seq("O2", "FW_Optional", "FW_Combi(5)")
                .options(Scenario.UNPROMOTED).names("M", "M=", ";").names("O1", "O1=", ";").names("O2", "O2=", ";");
        try (Scenario.Run run = s.run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals(List.of(), run.rows("O1"));
            assertEquals(List.of(""), run.rows("O2"), "FW_Combi(5) of two values: the legacy guard's one empty row");
            assertEquals(List.of("M=m1", "M=m1;O2="), run.sources(a, PLAIN),
                    "{O1} and {O1,O2} contribute nothing; {O2} is its present empty row, written as prefix and ending");
            Candidate present = run.candidates(a).get(1);
            assertEquals(SelectionState.PRESENT, present.states().get(2));
            assertEquals(0, present.keys(2).size());
            assertEquals(SelectionState.ABSENT, present.states().get(1));
        }
    }

    @Test
    void sizesNotProducedAreRefusedAndSizesAboveTheOptionalSheetsAreReported() {
        Scenario s = twoByThree().sheet("O", "o").seq("O", "FW_Optional", "FW_Combi(1)").names("O", "O=", ";");
        try (Scenario.Run run = s.run(GenerationOptions.defaults())) {
            AssemblyOptions inconsistent = new AssemblyOptions(RunConfig.EmptyStagePolicy.PRESERVE_EMPTY, false, Long.MAX_VALUE,
                    RunConfig.OptionalMode.AUTO, true, RunConfig.SizeSet.of(List.of(1)), RunConfig.SizeSet.of(List.of()), Long.MAX_VALUE,
                    LegacyLibrary.PARALLEL, 0, Coverage.Method.GREEDY, 0, 2_000_000L);
            FrameworkException e = assertThrows(FrameworkException.class, () -> run.assemble(inconsistent, PLAIN).close());
            assertEquals(ErrorCode.CONFIG_INCONSISTENT, e.diagnostics().getFirst().code());
            try (Assembly a = run.assemble(options(false, Long.MAX_VALUE, RunConfig.OptionalMode.AUTO, true,
                    RunConfig.SizeSet.of(List.of(1, 3)), Long.MAX_VALUE, 0, 0), PLAIN)) {
                assertTrue(codes(a.diagnostics()).contains(ErrorCode.ASSEMBLY_SIZE_UNUSED));
                assertEquals(BigInteger.valueOf(12), a.generated());
            }
        }
    }

    // ---- rules: early on mandatory rows, late on assembled candidates ---------------------------------------------

    private static Rule forbid(String id, String sheetA, String valueA, String sheetB, String valueB) {
        return new Rule(id, Rule.Polarity.FORBID, Gate.ANY, null, new Rule.Pairs(List.of(sheetA, sheetB),
                List.of(Map.of(sheetA, new RuleValue.Text(valueA), sheetB, new RuleValue.Text(valueB)))), null,
                SourceLocation.of("tutor.constraints.json", 1));
    }

    /** The parcel-dispatch lesson of SUT/combination_thinking_tutor, as a workbook: 192 raw, 48 excluded, 144 executable. */
    private static Scenario tutor() {
        return Scenario.of().sheet("channel", "web", "mobile").sheet("account", "guest", "member").sheet("payment", "card", "wallet")
                .sheet("steps", "reserve", "charge", "dispatch").sheet("duplicate_submit", "dup").sheet("carrier_timeout", "timeout")
                .seq("channel", "FW_Combi(1)").seq("account", "FW_Combi(1)").seq("payment", "FW_Combi(1)").seq("steps", "FW_Permut()")
                .seq("duplicate_submit", "FW_Optional", "FW_Combi(1)").seq("carrier_timeout", "FW_Optional", "FW_Combi(1)")
                .options(Scenario.UNPROMOTED);
    }

    @Test
    void theTutorLessonGenerates192Excludes48AndLeaves144() {
        ConstraintSet rule = new ConstraintSet(List.of(), Map.of(), Map.of(),
                List.of(forbid("wallet_requires_member", "payment", "wallet", "account", "guest")), List.of());
        try (Scenario.Run run = tutor().constraints(rule).run(GenerationOptions.defaults());
             Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals(BigInteger.valueOf(48), a.mandatoryRows());
            assertEquals(BigInteger.valueOf(4), a.perMandatoryRow(), "absent, {dup}, {timeout}, {dup, timeout}");
            AssemblyCounts counts = a.count(Cancellation.NONE, BigInteger.valueOf(1_000_000));
            assertTrue(counts.exact());
            assertEquals(BigInteger.valueOf(192), counts.generated());
            assertEquals(BigInteger.valueOf(48), counts.excluded());
            assertEquals(BigInteger.valueOf(144), counts.executable());
            assertEquals(BigInteger.valueOf(48), counts.perRule()[0]);
            assertEquals(144, run.candidates(a).size());
            assertTrue(a.rules().mandatoryOnly(0), "the rule reads mandatory sheets only, so it runs once per mandatory row");
        }
        ConstraintSet late = new ConstraintSet(List.of(), Map.of(), Map.of(), List.of(
                forbid("wallet_requires_member", "payment", "wallet", "account", "guest"),
                forbid("no_duplicates_on_mobile", "channel", "mobile", "duplicate_submit", "dup")), List.of());
        try (Scenario.Run run = tutor().constraints(late).run(GenerationOptions.defaults());
             Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            AssemblyCounts counts = a.count(Cancellation.NONE, BigInteger.valueOf(1_000_000));
            assertEquals(BigInteger.valueOf(48), counts.removedEarly());
            assertEquals(BigInteger.valueOf(36), counts.removedLate(), "18 allowed mobile rows x the 2 branches holding dup");
            assertEquals(BigInteger.valueOf(108), counts.executable());
            assertEquals(108, run.candidates(a).size());
            assertFalse(a.rules().mandatoryOnly(1));
            assertTrue(run.candidates(a).stream().noneMatch(c -> c.keys(0) != null && run.text(row(c, 0)).equals("mobile")
                    && c.keys(4) != null));
        }
    }

    private static org.ghyura.cf.model.row.Row row(Candidate c, int column) {
        return c.rows().get(column);
    }

    // ---- covering selection ---------------------------------------------------------------------------------------------

    @Test
    void coveringSelectionKeepsTheGreedyRowsAndOptionalBranchesFollowEachKeptRow() {
        Scenario s = Scenario.of().sheet("A", "a1", "a2", "a3").sheet("B", "b1", "b2", "b3").sheet("C", "c1", "c2")
                .sheet("O", "o").seq("A", "FW_Combi(1)").seq("B", "FW_Combi(1)").seq("C", "FW_Combi(1)")
                .seq("O", "FW_Optional", "FW_Combi(1)").options(Scenario.UNPROMOTED);
        try (Scenario.Run run = s.run(GenerationOptions.defaults());
             Assembly a = run.assemble(options(false, Long.MAX_VALUE, RunConfig.OptionalMode.AUTO, true, RunConfig.SizeSet.all(),
                     Long.MAX_VALUE, 2, 0), PLAIN)) {
            List<int[]> product = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    for (int k = 0; k < 2; k++) {
                        product.add(new int[]{i, j, k});
                    }
                }
            }
            Coverage.Selection expected = Coverage.select(product, new int[]{3, 3, 2}, 2, Coverage.Method.GREEDY, Cancellation.NONE);
            assertEquals(expected.kept().length, a.coverage().kept().length);
            List<Candidate> c = run.candidates(a);
            assertEquals(2 * expected.kept().length, c.size(), "each kept row with its absent and {O} branch");
            assertEquals(0, a.coverage().infeasible());
            AssemblyCounts counts = a.count(Cancellation.NONE, BigInteger.valueOf(1_000_000));
            assertEquals(BigInteger.valueOf(36), counts.generated());
            assertEquals(BigInteger.valueOf(2L * (18 - expected.kept().length)), counts.unselected());
            assertEquals(BigInteger.valueOf(2L * expected.kept().length), counts.executable());
        }
    }

    // ---- de-duplication and the legacy cut-off -------------------------------------------------------------------------

    @Test
    void deDuplicationKeepsFirstOccurrencesAndTheLegacyIdsOfThem() {
        Scenario s = Scenario.of().sheet("A", "a", "b", "c").sheet("B", "x", "y").seq("A", "FW_Combi(2)", "FW_Combi(1)")
                .seq("B", "FW_Combi(1)").options(Scenario.UNPROMOTED).names("A", "", "").names("B", "", "");
        try (Scenario.Run run = s.run(GenerationOptions.defaults())) {
            assertEquals(List.of("a", "b", "a", "c", "b", "c"), run.rows("A"));
            try (Assembly all = run.assemble(DEFAULTS, PLAIN)) {
                assertEquals(BigInteger.valueOf(12), all.generated());
            }
            try (Assembly distinct = run.assemble(options(true, Long.MAX_VALUE, RunConfig.OptionalMode.AUTO, true, RunConfig.SizeSet.all(),
                    Long.MAX_VALUE, 0, 0), PLAIN)) {
                List<Candidate> c = run.candidates(distinct);
                assertEquals(List.of("ax", "ay", "bx", "by", "cx", "cy"), run.sources(distinct, PLAIN));
                assertEquals(List.of("1_0_0", "2_0_0", "3_0_0", "4_0_0", "7_0_0", "8_0_0"), displays(c),
                        "c's first occurrence is row 4 of A, so its fw_final ids are 7 and 8 (MIN(combi_id) kept)");
            }
            try (Assembly cut = run.assemble(options(false, 5, RunConfig.OptionalMode.AUTO, true, RunConfig.SizeSet.all(),
                    Long.MAX_VALUE, 0, 0), PLAIN)) {
                assertEquals(BigInteger.valueOf(2), cut.mandatoryRows(),
                        "limitVar: the product of trailing largest ids at or below 5 is 2 (B), 6 x 2 = 12 exceeds it");
                assertTrue(codes(cut.diagnostics()).contains(ErrorCode.ASSEMBLY_TRUNCATED));
            }
        }
    }

    // ---- rendering --------------------------------------------------------------------------------------------------------

    @Test
    void renderingFollowsTheReaderAndFwConcatenatorJoinsTheValuesOfARow() {
        Scenario s = Scenario.of().sheet("A", "x  = 1;", "y").sheet("B", "FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE")
                .seq("A", "FW_Concatenator= + ", "FW_Combi(2)").seq("B", "FW_Combi(1)").options(Scenario.UNPROMOTED)
                .names("A", "<", ">").names("B", "[", "]");
        try (Scenario.Run run = s.run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals(List.of("<x = 1; + y>[1_0_0"), run.sources(a, PLAIN),
                    "values refined (runs of spaces collapse), joined by FW_Concatenator, the marker replaced, the last ending cut");
            RenderOptions legacyIgnore = new RenderOptions("", RunConfig.SheetConcatenator.LEGACY_IGNORE, true, List.of(), List.of(),
                    List.of(), List.of(), false);
            try (Assembly ignored = run.assemble(DEFAULTS, legacyIgnore)) {
                assertEquals(List.of("<x  = 1;y>[FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE"), run.sources(ignored, legacyIgnore),
                        "legacy-ignore: no glue; preserved white space; marker kept");
            }
            RenderOptions global = new RenderOptions(",", RunConfig.SheetConcatenator.APPLY, true, List.of(), List.of(), List.of(),
                    List.of(), true);
            try (Assembly g = run.assemble(DEFAULTS, global)) {
                assertEquals(List.of("<x  = 1; + y,[1_0_0"), run.sources(g, global), "the global concatenator replaces every ending");
            }
        }
    }

    @Test
    void theReaderRefinerKeepsQuotedTextAndAppliesAffixRulesOutsideQuotes() {
        RenderOptions affix = new RenderOptions("", RunConfig.SheetConcatenator.APPLY, false, List.of("\\{"), List.of(";"), List.of(),
                List.of(), false);
        ReaderRefiner refiner = new ReaderRefiner(affix);
        // the comment becomes a space and the quoted string gains one (removeComments' "$1 "); the run before { collapses
        // and { gains a space before it; ; gains one after it; the quoted text is untouched
        assertEquals("if (a)  {x(\"  k  \" );  ", refiner.refine("if (a)   {x(\"  k  \"); // c"));
        assertEquals("a {b; c", refiner.refine("a{b;c"));
        assertEquals("s = 'a  b' + x", new ReaderRefiner(RenderOptions.defaults()).refine("s   =   'a  b'  +   x"),
                "white space inside quotes stays, runs outside collapse to one space");
        assertEquals("a b", new ReaderRefiner(RenderOptions.defaults()).refine("a // comment\nb").strip());
    }

    // ---- identity ----------------------------------------------------------------------------------------------------------

    @Test
    void candidateIdsAreStableDistinctAndIncludeThePolicyButNotTheRules() {
        List<String> first;
        try (Scenario.Run run = twoByThree().run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            first = run.candidates(a).stream().map(c -> c.id().hex()).toList();
        }
        try (Scenario.Run run = twoByThree().run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals(first, run.candidates(a).stream().map(c -> c.id().hex()).toList(), "deterministic");
        }
        assertEquals(6, first.stream().distinct().count());
        RenderOptions other = new RenderOptions(",", RunConfig.SheetConcatenator.APPLY, false, List.of(), List.of(), List.of(),
                List.of(), true);
        try (Scenario.Run run = twoByThree().run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, other)) {
            assertNotEquals(first.getFirst(), run.candidates(a).getFirst().id().hex(), "rendering policy is part of the identity");
        }
        ConstraintSet rule = new ConstraintSet(List.of(), Map.of(), Map.of(), List.of(forbid("r", "A", "a2", "B", "b1")), List.of());
        try (Scenario.Run run = twoByThree().constraints(rule).run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            List<Candidate> kept = run.candidates(a);
            assertEquals(5, kept.size());
            assertNotEquals(first.getFirst(), kept.getFirst().id().hex(), "the scenario's content (with its rules) is part of the dictionary");
        }
    }

    // ---- cancellation and budgets -----------------------------------------------------------------------------------------

    @Test
    void walkingPollsCancellationAndTheBudgetComesBackAfterClose() {
        Scenario s = Scenario.of().sheet("A", "a1", "a2", "a3", "a4").sheet("B", "b1", "b2", "b3", "b4").sheet("C", "c1", "c2", "c3", "c4")
                .seq("A", "FW_Permut()").seq("B", "FW_Permut()").seq("C", "FW_Permut()").options(Scenario.UNPROMOTED);
        try (Scenario.Run run = s.run(GenerationOptions.defaults()); Assembly a = run.assemble(DEFAULTS, PLAIN)) {
            assertEquals(BigInteger.valueOf(24L * 24 * 24), a.generated());
            AtomicInteger polls = new AtomicInteger();
            Cancellation soon = () -> polls.incrementAndGet() > 3;
            java.util.Iterator<Candidate> it = a.candidates(soon);
            FrameworkException e = assertThrows(FrameworkException.class, () -> {
                while (it.hasNext()) {
                    it.next();
                }
            });
            assertEquals(ErrorCode.RUN_CANCELLED, e.diagnostics().getFirst().code());
        }
        Scenario dup = Scenario.of().sheet("A", "a", "b", "c", "d", "e", "f", "g", "h").seq("A", "FW_Combi(3)", "FW_Combi(1)")
                .options(Scenario.UNPROMOTED);
        try (Scenario.Run run = dup.run(GenerationOptions.defaults())) {
            long before = run.budget().reservedBytes();
            MemoryBudget tiny = new MemoryBudget(before + 1024);
            FrameworkException e = assertThrows(FrameworkException.class, () -> Assembly.of(run.plan(), run.result().result(),
                    run.dictionary(), GenerationOptions.defaults(), options(true, Long.MAX_VALUE, RunConfig.OptionalMode.AUTO, true,
                            RunConfig.SizeSet.all(), Long.MAX_VALUE, 0, 0), PLAIN, 1L, tiny, Cancellation.NONE));
            assertEquals(ErrorCode.RESOURCE_LIMIT, e.diagnostics().getFirst().code());
            assertEquals(0, tiny.reservedBytes(), "the failed assembly returned every reservation it took");
            try (Assembly a = run.assemble(options(true, Long.MAX_VALUE, RunConfig.OptionalMode.AUTO, true, RunConfig.SizeSet.all(),
                    Long.MAX_VALUE, 0, 0), PLAIN)) {
                assertTrue(run.budget().reservedBytes() > before, "the de-duplicated table is reserved");
                assertEquals(BigInteger.valueOf(8), a.generated());
            }
            assertEquals(before, run.budget().reservedBytes(), "and returned on close");
        }
    }
}
