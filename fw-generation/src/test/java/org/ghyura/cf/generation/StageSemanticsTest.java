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
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A05 building blocks (stage semantics), golden cases derived by hand from the legacy code at the pinned commit:
 * SheetWorker.runDirective/runFirstPass/runSubsequentPass (passes, resolveM, separator, FW_Group rewrite) and
 * BraceOperationHandler (joiners). Joiner expectations are the architect's CR-JOIN-01 examples. Rows are written as
 * value names; "id&lt;-parent: names" adds the legacy combi_id and the parent row's id.
 */
class StageSemanticsTest {

    private static final GenerationOptions PARALLEL = new GenerationOptions(LegacyLibrary.PARALLEL, false);
    private static final GenerationOptions LIB3 = new GenerationOptions(LegacyLibrary.COMBINATORICSLIB3, false);
    private static final GenerationOptions DISTINCT = new GenerationOptions(LegacyLibrary.PARALLEL, true);

    private static List<String> rows(Scenario scenario, GenerationOptions options, String sheet) {
        try (Scenario.Run run = scenario.run(options)) {
            return run.rows(sheet);
        }
    }

    private static List<String> provenance(Scenario scenario, GenerationOptions options, String sheet) {
        try (Scenario.Run run = scenario.run(options)) {
            return run.provenance(sheet);
        }
    }

    // ---- chained verbs and parent ids ---------------------------------------------------------------------------

    @Test
    void aLaterPassWorksRowByRowAndEveryResultKeepsItsSourceRowAsParent() {
        Scenario s = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(2)", "FW_Permut()");
        assertEquals(List.of("1<-1: a b", "2<-1: b a", "3<-2: a c", "4<-2: c a", "5<-3: b c", "6<-3: c b"), provenance(s, PARALLEL, "A"));
    }

    /**
     * The legacy promotion counted only verbs written with brackets (FW_Permut() but not FW_Permut) and FW_Subsets, and
     * appended the one counted verb when it was alone: FW_Combi(2), FW_Permut runs FW_Combi(2) a second time at the end.
     */
    @Test
    void thePromotionCountsOnlyBracketedVerbsAndAppendsTheLoneOne() {
        Scenario s = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(2)", "FW_Permut");
        assertEquals(3, s.plan().sheet("A").orElseThrow().directives().size());
        assertEquals(List.of("7<-1: a b", "8<-2: b a", "9<-3: a c", "10<-4: c a", "11<-5: b c", "12<-6: c b"), provenance(s, PARALLEL, "A"),
                "the second pass's table became the first (ids 1..6) and FW_Combi(2) kept every 2-key row, ids going on from 7");
    }

    @Test
    void aLoneVerbIsPromotedSoFwCombiOneRunsTwice() {
        Scenario s = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(1)");
        assertEquals(2, s.plan().sheet("A").orElseThrow().directives().size(), "the legacy auto-promotion");
        assertEquals(List.of("1<-1: a", "2<-2: b", "3<-3: c"), provenance(s, PARALLEL, "A"));
    }

    @Test
    void allAndSizeInTheFirstAndInLaterPasses() {
        Scenario first = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(all)", "FW_Combi(size)");
        assertEquals(List.of("1<-1: a", "2<-2: b", "3<-3: c", "4<-4: a b", "5<-5: a c", "6<-6: b c", "7<-7: a b c"),
                provenance(first, PARALLEL, "A"), "all: 1..n in the first pass; size: each row's own length later");
        Scenario later = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(2)", "FW_Combi(all)");
        assertEquals(List.of("1<-1: a", "2<-1: b", "3<-1: a b", "4<-2: a", "5<-2: c", "6<-2: a c", "7<-3: b", "8<-3: c", "9<-3: b c"),
                provenance(later, PARALLEL, "A"), "all in a later pass: 1..row length for each row");
    }

    @Test
    void subsetsModesInTheFirstPassButEverySubsetIncludingTheEmptyOneLater() {
        assertEquals(List.of("", "a", "b", "a b"), rows(Scenario.of().sheet("A", "a", "b").seq("A", "FW_Subsets")
                .options(Scenario.UNPROMOTED), PARALLEL, "A"), "binary order, the empty subset first");
        assertEquals(List.of("a", "b", "c", "a b", "a c", "b c"), rows(Scenario.of().sheet("A", "a", "b", "c")
                .seq("A", "FW_Subsets_RANGE(1,2)").options(Scenario.UNPROMOTED), PARALLEL, "A"));
        Scenario later = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Subsets_EXACT(2)", "FW_Subsets")
                .options(Scenario.UNPROMOTED);
        assertEquals(List.of("1<-1: ", "2<-1: a", "3<-1: b", "4<-1: a b", "5<-2: ", "6<-2: a", "7<-2: c", "8<-2: a c",
                "9<-3: ", "10<-3: b", "11<-3: c", "12<-3: b c"), provenance(later, PARALLEL, "A"));
    }

    @Test
    void cartesPairsWithTheOtherSheetsOriginalValuesTheLastFactorFastest() {
        Scenario first = Scenario.of().sheet("A", "a", "b").sheet("X", "x", "y").options(Scenario.UNPROMOTED);
        assertEquals(List.of("a x", "a y", "b x", "b y"), rows(first.seq("A", "FW_Cartes(X)"), PARALLEL, "A"));
        assertEquals(List.of("x a", "x b", "y a", "y b"), rows(Scenario.of().sheet("A", "a", "b").sheet("X", "x", "y")
                .options(Scenario.UNPROMOTED).seq("A", "FW_Cartes_first(X)"), PARALLEL, "A"));
        assertEquals(List.of("1<-1: a x", "2<-1: a y", "3<-2: b x", "4<-2: b y"), provenance(Scenario.of().sheet("A", "a", "b")
                .sheet("X", "x", "y").seq("A", "FW_Combi(1)", "FW_Cartes(X)").options(Scenario.UNPROMOTED), PARALLEL, "A"));
        assertEquals(List.of("1<-1: x a", "2<-1: y a", "3<-2: x b", "4<-2: y b"), provenance(Scenario.of().sheet("A", "a", "b")
                .sheet("X", "x", "y").seq("A", "FW_Combi(1)", "FW_Cartes_first(X)").options(Scenario.UNPROMOTED), PARALLEL, "A"));
    }

    // ---- separator ----------------------------------------------------------------------------------------------

    @Test
    void theSeparatorGoesBetweenTheElementsOfLaterPassesAndAnEmptyResultIsNoRow() {
        Scenario permut = Scenario.of().sheet("A", "a", "b", "c").sheet("S", "s").seq("A", "FW_Combi(2)", "FW_Separator(S)", "FW_Permut()");
        assertEquals(List.of("1<-1: a s b", "2<-1: b s a", "3<-2: a s c", "4<-2: c s a", "5<-3: b s c", "6<-3: c s b"),
                provenance(permut, PARALLEL, "A"));
        Scenario subsets = Scenario.of().sheet("A", "a", "b", "c").sheet("S", "s").seq("A", "FW_Combi(2)", "FW_Separator(S)", "FW_Subsets");
        assertEquals(List.of("a", "b", "a s b", "a", "c", "a s c", "b", "c", "b s c"), rows(subsets, PARALLEL, "A"));
    }

    @Test
    void aSeparatorAsTheFirstDirectiveLeavesTheLaterPassWithoutRowsAndSaysSo() {
        Scenario s = Scenario.of().sheet("A", "a", "b").sheet("S", "s").seq("A", "FW_Separator(S)", "FW_Combi(2)")
                .options(Scenario.UNPROMOTED);
        try (Scenario.Run run = s.run(PARALLEL)) {
            assertEquals(List.of(), run.rows("A"));
            assertEquals(List.of(ErrorCode.GEN_NO_SOURCE_ROWS, ErrorCode.GEN_EMPTY_TABLE), run.codes());
        }
    }

    // ---- the grouped pass ---------------------------------------------------------------------------------------

    @Test
    void aGroupedPassRewritesTheLegacyTextAndReadsTheLabelsBack() {
        // sheets A, J: values a=3, b=4, j=5 (labels start after the two sheet keys)
        Scenario literal = Scenario.of().sheet("A", "a", "b").sheet("J", "j")
                .seq("A", "FW_Combi(1)", "FW_Group FW_ReplaceRE(\"3\", \"4\")", "FW_Combi(2)");
        assertEquals(List.of("1<-0: b b"), provenance(literal, PARALLEL, "A"), "[[3], [4]] became [[4], [4]]");
        Scenario joined = Scenario.of().sheet("A", "a", "b").sheet("J", "j")
                .seq("A", "FW_Combi(1)", "FW_Group FW_ReplaceRE(\"3\", \"[\"+J+\"]\")", "FW_Combi(2)");
        assertEquals(List.of("j b"), rows(joined, PARALLEL, "A"), "the first value of J replaced 3");
    }

    @Test
    void anUnparseableGroupedRowIsDroppedWithACountedLocatedDiagnostic() {
        Scenario s = Scenario.of().sheet("A", "a", "b").sheet("J", "j")
                .seq("A", "FW_Combi(1)", "FW_Group FW_ReplaceRE(\"4\", \"x\")", "FW_Subsets");
        try (Scenario.Run run = s.run(PARALLEL)) {
            assertEquals(List.of("a"), run.rows("A"), "[] is empty and no row; [[4]] and [[3], [4]] became text");
            Diagnostic d = run.result().result().diagnostics().getFirst();
            assertEquals(ErrorCode.GEN_UNPARSEABLE_ROW, d.code());
            assertEquals("2", d.attributes().get("rows"));
            assertEquals("\"[[x]]\" (was \"[[4]]\")", d.attributes().get("example"));
            assertEquals("FW_Seq!0:3", d.location().path());
        }
    }

    @Test
    void groupedUnitsAreSortedByLabelsThenLengthAndCombinedWithCombinatoricslib3WhateverTheLibrary() {
        Scenario sorted = Scenario.of().sheet("A", "a", "b").seq("A", "FW_Combi(all)", "FW_Group", "FW_Combi(2)");
        assertEquals(List.of("a a b", "a b", "a b b"), rows(sorted, PARALLEL, "A"), "units [a] < [a b] < [b]");
        Scenario permut = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(1)", "FW_Group", "FW_Permut()");
        List<String> adjacentSwap = List.of("a b c", "a c b", "c a b", "c b a", "b c a", "b a c");
        assertEquals(adjacentSwap, rows(permut, PARALLEL, "A"), "a grouped pass always used combinatoricslib3");
        assertEquals(adjacentSwap, rows(permut, LIB3, "A"));
        Scenario cartes = Scenario.of().sheet("A", "a", "b").sheet("X", "x", "y").seq("A", "FW_Combi(1)", "FW_Group", "FW_Cartes(X)")
                .options(Scenario.UNPROMOTED);
        assertEquals(List.of("a x", "a y", "b x", "b y"), rows(cartes, PARALLEL, "A"));
        Scenario first = Scenario.of().sheet("A", "a", "b").sheet("X", "x", "y").seq("A", "FW_Combi(1)", "FW_Group", "FW_Cartes_first(X)")
                .options(Scenario.UNPROMOTED);
        assertEquals(List.of("x a", "x b", "y a", "y b"), rows(first, PARALLEL, "A"));
    }

    // ---- the two generator libraries ----------------------------------------------------------------------------

    @Test
    void theLibraryDecidesTheOrderOfFirstAndUngroupedPassesOnly() {
        Scenario permut = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(3)", "FW_Permut()");
        assertEquals(List.of("a b c", "a c b", "b a c", "b c a", "c a b", "c b a"), rows(permut, PARALLEL, "A"), "lexicographic");
        assertEquals(List.of("a b c", "a c b", "c a b", "c b a", "b c a", "b a c"), rows(permut, LIB3, "A"), "adjacent swaps");
        Scenario repetition = Scenario.of().sheet("A", "a", "b").seq("A", "FW_PermutR(2)").options(Scenario.UNPROMOTED);
        assertEquals(List.of("a a", "a b", "b a", "b b"), rows(repetition, PARALLEL, "A"), "last place fastest");
        assertEquals(List.of("a a", "b a", "a b", "b b"), rows(repetition, LIB3, "A"), "first place fastest");
        Scenario marker = Scenario.of().sheet("A", "a", "b", "c").options(Scenario.UNPROMOTED)
                .seq("A", "FW_Permut(PermutationGenerator.TreatDuplicatesAs.IDENTICAL)");
        assertEquals(List.of("a b c", "a c b", "b a c", "b c a", "c a b", "c b a"), rows(marker, LIB3, "A"),
                "the duplicate marker always used the parallel multiset generator");
    }

    @Test
    void fwPermutIgnoresItsSizeWhilePermuteKIsCombiKThenPermut() {
        Scenario ignored = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Permut(2)").options(Scenario.UNPROMOTED);
        assertEquals(List.of("a b c", "a c b", "b a c", "b c a", "c a b", "c b a"), rows(ignored, PARALLEL, "A"));
        Scenario permuteK = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(2)", "FW_Permut()");
        assertEquals(List.of("a b", "b a", "a c", "c a", "b c", "c b"), rows(permuteK, PARALLEL, "A"));
    }

    // ---- joiners (CR-JOIN-01) -------------------------------------------------------------------------------------

    private static Scenario joinOperands() {
        return Scenario.of().sheet("A", "a1", "a2").sheet("B", "b1", "b2").sheet("C", "c1", "c2", "c3").sheet("R", "r")
                .sheet("S", "s").sheet("T", "t").sheet("E", "e")
                .seq("A", "FW_Reuse", "FW_Combi(2)").seq("B", "FW_Reuse", "FW_Combi(2)").seq("C", "FW_Reuse", "FW_Combi(3)");
    }

    @Test
    void everyMultiplicityBuildsTheArchitectsExampleRows() {
        Scenario s = joinOperands()
                .seq("J11", "FW_(,,A,R,B,,,S,1:1)").seq("J1N", "FW_(,,A,R,B,,,S,1:N)").seq("JM1", "FW_(,,A,R,B,,,S,M:1)")
                .seq("JMM", "FW_(,,A,R,B,,,S,M:M)").seq("JMN", "FW_(T,,A,R,B,,E,S,M:N)");
        try (Scenario.Run run = s.run(PARALLEL)) {
            assertEquals(List.of("1<-0: a1 r b1 s a2 r b2"), run.provenance("J11"));
            assertEquals(List.of("a1 r b1 b2 s a2 r b1 b2"), run.rows("J1N"));
            assertEquals(List.of("a1 a2 r b1 s a1 a2 r b2"), run.rows("JM1"));
            assertEquals(List.of("a1 a2 r b1 b2"), run.rows("JMM"));
            assertEquals(List.of("t a1 a2 r b1 b2 e"), run.rows("JMN"), "start and end wrap every row");
        }
    }

    @Test
    void oneToOneAndManyToManyNeedRowsOfEqualLength() {
        Scenario s = joinOperands().seq("U11", "FW_(,,A,R,C,,,S,1:1)").seq("UMM", "FW_(,,A,R,C,,,S,M:M)")
                .seq("UMN", "FW_(,,A,R,C,,,S,M:N)").seq("U1N", "FW_(,,A,R,C,,,S,1:N)");
        try (Scenario.Run run = s.run(PARALLEL)) {
            assertEquals(List.of(), run.rows("U11"));
            assertEquals(List.of(), run.rows("UMM"));
            assertEquals(List.of("a1 a2 r c1 c2 c3"), run.rows("UMN"));
            assertEquals(List.of("a1 r c1 c2 c3 s a2 r c1 c2 c3"), run.rows("U1N"));
        }
    }

    @Test
    void nestedPlaceholdersReadTheMostRecentJoinerAndTheGroupedOneFlattensItsRows() {
        Scenario plain = Scenario.of().sheet("A", "a1", "a2").sheet("B", "b1", "b2").sheet("C", "c").sheet("R", "r")
                .seq("A", "FW_Combi(2)").seq("B", "FW_Combi(1)").seq("C", "FW_Reuse", "FW_Combi(1)")
                .seq("J1", "FW_(,,A,R,B,,,,M:N)").seq("J2", "FW_(,,FW_(),,C,,,,M:N)");
        try (Scenario.Run run = plain.run(PARALLEL)) {
            assertEquals(List.of("a1 a2 r b1", "a1 a2 r b2"), run.rows("J1"));
            assertEquals(List.of("a1 a2 r b1 c", "a1 a2 r b2 c"), run.rows("J2"));
            assertEquals("J2", run.consumedBy("J1"), "the nested joiner consumed the earlier one");
        }
        Scenario grouped = Scenario.of().sheet("A", "a1", "a2").sheet("B", "b1", "b2").sheet("C", "c").sheet("R", "r")
                .seq("A", "FW_Combi(2)").seq("B", "FW_Combi(1)").seq("C", "FW_Reuse", "FW_Combi(1)")
                .seq("J1", "FW_(,,A,R,B,,,,M:N)").seq("J2", "FW_(,,FW_()G,,C,,,,M:N)");
        assertEquals(List.of("a1 a2 r b1 a1 a2 r b2 c"), rows(grouped, PARALLEL, "J2"));
    }

    /**
     * Delta D19: the legacy memory store fell back to an operand's first table when its second was empty, the
     * PostgreSQL store read the empty second table; a joiner here reads the operand's final table.
     */
    @Test
    void aJoinerReadsTheFinalTableOfItsOperandEvenWhenItIsEmpty() {
        Scenario s = Scenario.of().sheet("A", "a", "b").sheet("B", "b0").sheet("S", "s")
                .seq("A", "FW_Combi(2)", "FW_Separator(S)", "FW_Combi(3)").seq("B", "FW_Combi(1)")
                .seq("J", "FW_(,,A,,B,,,,M:N)");
        try (Scenario.Run run = s.run(PARALLEL)) {
            assertEquals(List.of(), run.rows("A"), "the separated guard row is no row, so the final table is empty");
            assertEquals(List.of(), run.rows("J"), "the memory store would have joined the earlier [a b]");
        }
    }

    @Test
    void aJoinerConsumesItsOperandsUnlessTheyAreReused() {
        Scenario consumed = Scenario.of().sheet("A", "a").sheet("B", "b").seq("A", "FW_Combi(1)").seq("B", "FW_Combi(1)")
                .seq("J1", "FW_(,,A,,B,,,,M:N)").seq("J2", "FW_(,,A,,B,,,,M:N)");
        try (Scenario.Run run = consumed.run(PARALLEL)) {
            assertEquals(List.of("a b"), run.rows("J1"));
            assertEquals(List.of(), run.rows("J2"));
            assertEquals("J1", run.consumedBy("A"));
            assertEquals(List.of(ErrorCode.GEN_OPERAND_CONSUMED, ErrorCode.GEN_OPERAND_CONSUMED, ErrorCode.GEN_EMPTY_TABLE), run.codes());
        }
        Scenario reused = Scenario.of().sheet("A", "a").sheet("B", "b").seq("A", "FW_Reuse", "FW_Combi(1)")
                .seq("B", "FW_Reuse", "FW_Combi(1)").seq("J1", "FW_(,,A,,B,,,,M:N)").seq("J2", "FW_(,,A,,B,,,,M:N)");
        try (Scenario.Run run = reused.run(PARALLEL)) {
            assertEquals(List.of("a b"), run.rows("J2"));
            assertEquals(List.of(), run.codes());
        }
    }

    // ---- distinct tables (decision G7) ---------------------------------------------------------------------------

    @Test
    void distinctTablesKeepTheFirstRowOfEveryContentWithItsIdAndParent() {
        Scenario chained = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(2)", "FW_Combi(1)");
        assertEquals(List.of("1<-1: a", "2<-1: b", "3<-2: a", "4<-2: c", "5<-3: b", "6<-3: c"), provenance(chained, PARALLEL, "A"));
        assertEquals(List.of("1<-1: a", "2<-1: b", "4<-2: c"), provenance(chained, DISTINCT, "A"),
                "content only decides; the ids keep their gaps, the parents stay those of the first rows");
        // sheet A alone: a=2, b=3
        Scenario grouped = Scenario.of().sheet("A", "a", "b").seq("A", "FW_Combi(1)", "FW_Group FW_ReplaceRE(\"2\", \"3\")", "FW_Subsets");
        assertEquals(List.of("1<-0: b", "2<-0: b", "3<-0: b b"), provenance(grouped, PARALLEL, "A"));
        assertEquals(List.of("1<-0: b", "3<-0: b b"), provenance(grouped, DISTINCT, "A"));
    }

    @Test
    void aFirstPassOverDistinctLabelsStaysComputedWhenTablesAreDistinct() {
        Scenario s = Scenario.of().sheet("A", "a", "b", "c", "d").seq("A", "FW_Permut").options(Scenario.UNPROMOTED);
        try (Scenario.Run run = s.run(DISTINCT)) {
            assertFalse(run.table("A").stored(), "every label is a value of its own, so no row can repeat");
            assertEquals(24, run.rows("A").size());
        }
    }

    // ---- empty results --------------------------------------------------------------------------------------------

    /**
     * SheetWorker.buildGenerator returned CombinatorialGenerator.subsets(emptyList) for a size FW_Combi cannot take,
     * and the subsets of an empty list are one empty row in both libraries (fixtures from the jars), so the legacy
     * wrote one empty row, in the first pass and for every source row of a later pass; only a separator drops it.
     */
    @Test
    void aCombiLargerThanItsSourceGivesOneEmptyRowAsTheLegacyGuardDid() {
        Scenario first = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(5)").options(Scenario.UNPROMOTED);
        try (Scenario.Run run = first.run(PARALLEL)) {
            assertEquals(List.of("1<-0: "), run.provenance("A"));
            assertEquals(List.of(), run.codes(), "one present empty row is not an empty table");
        }
        Scenario later = Scenario.of().sheet("A", "a", "b", "c").seq("A", "FW_Combi(2)", "FW_Combi(3)");
        assertEquals(List.of("1<-1: ", "2<-2: ", "3<-3: "), provenance(later, PARALLEL, "A"));
        Scenario separated = Scenario.of().sheet("A", "a", "b", "c").sheet("S", "s").seq("A", "FW_Combi(2)", "FW_Separator(S)", "FW_Combi(3)");
        try (Scenario.Run run = separated.run(PARALLEL)) {
            assertEquals(List.of(), run.rows("A"));
            assertEquals(List.of(ErrorCode.GEN_EMPTY_TABLE), run.codes());
        }
    }

    /**
     * A data sheet without values was missing from the legacy values map, so its first pass got the subsets of an
     * empty list whatever the verb (one empty row), except the (all) forms, which looped over no size at all; a
     * virtual sheet had an empty list and followed each library's own empty-input result.
     */
    @Test
    void sheetsWithoutValuesFollowTheLegacyValuesMap() {
        for (GenerationOptions options : List.of(PARALLEL, LIB3)) {
            assertEquals(List.of(""), rows(Scenario.of().sheet("E").seq("E", "FW_Permut()").options(Scenario.UNPROMOTED), options, "E"));
            assertEquals(List.of(""), rows(Scenario.of().sheet("E").seq("E", "FW_CombiR(2)").options(Scenario.UNPROMOTED), options, "E"));
            try (Scenario.Run run = Scenario.of().sheet("E").seq("E", "FW_Combi(all)").options(Scenario.UNPROMOTED).run(options)) {
                assertEquals(List.of(), run.rows("E"));
                assertTrue(run.result().result().sheet("E").orElseThrow().empty());
                assertEquals(List.of(ErrorCode.GEN_EMPTY_TABLE), run.codes());
                assertNull(run.consumedBy("E"));
            }
        }
        Scenario virtual = Scenario.of().sheet("A", "a").seq("V", "FW_Permut()").options(Scenario.UNPROMOTED);
        assertTrue(virtual.plan().sheet("V").orElseThrow().virtual());
        assertEquals(List.of(""), rows(virtual, PARALLEL, "V"), "the parallel library permutes an empty list once");
        assertEquals(List.of(), rows(virtual, LIB3, "V"), "combinatoricslib3 does not");
        Scenario combi = Scenario.of().sheet("A", "a").seq("V", "FW_Combi(1)").options(Scenario.UNPROMOTED);
        assertEquals(List.of(""), rows(combi, LIB3, "V"), "1 > 0 values: the guard's empty row");
    }
}
