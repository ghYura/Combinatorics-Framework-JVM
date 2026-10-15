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

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectiveParserTest {

    private static final SourceLocation WHERE = SourceLocation.ofPath("scenario.xlsx", "FW_Seq!D4");

    private static Directive parse(String text) {
        Directive directive = DirectiveParser.parse(text, WHERE);
        assertEquals(text, directive.source());
        assertEquals(WHERE, directive.location());
        return directive;
    }

    /** The message of the refusal; the refusal always names the text and the place. */
    private static String refused(String text) {
        FrameworkException e = assertThrows(FrameworkException.class, () -> DirectiveParser.parse(text, WHERE), text);
        assertEquals(ErrorCode.PLAN_DIRECTIVE_SYNTAX, e.code(), text);
        assertEquals(Severity.ERROR, e.primary().severity());
        assertEquals(WHERE, e.primary().location());
        assertEquals(text, e.primary().attributes().get("directive"));
        assertTrue(e.primary().message().startsWith("'" + text + "': "), e.primary().message());
        return e.primary().message();
    }

    private static Directive.Combinatoric combinatoric(String text) {
        return (Directive.Combinatoric) parse(text);
    }

    // ---- combination verbs ------------------------------------------------------------------------------------

    @Test
    void aVerbWithoutArgumentOrWithEmptyBracketsMeansTheDefaultOfOne() {
        for (String text : List.of("FW_Combi", "FW_Combi()")) {
            Directive.Combinatoric d = combinatoric(text);
            assertEquals(Directive.Verb.COMBI, d.verb());
            assertEquals(Directive.CountKind.DEFAULT, d.count().kind());
            assertFalse(d.duplicatesIdentical());
            assertEquals("FW_Combi(1)", d.normalized());
        }
        assertEquals("FW_Permut(1)", combinatoric("FW_Permut()").normalized());
        assertEquals("FW_CombiR(1)", combinatoric("FW_CombiR").normalized());
        assertEquals("FW_PermutR(1)", combinatoric("FW_PermutR").normalized());
    }

    @Test
    void aWholeNumberSizeAllAndFullAreTheOtherArguments() {
        Directive.Combinatoric two = combinatoric("FW_Combi(2)");
        assertEquals(Directive.CountKind.EXACT, two.count().kind());
        assertEquals(2, two.count().value());
        assertEquals("FW_Combi(2)", two.normalized());
        assertEquals("FW_Combi(0)", combinatoric("FW_Combi(0)").normalized());
        assertEquals("FW_Combi(2147483647)", combinatoric("FW_Combi(2147483647)").normalized());
        for (String text : List.of("FW_Combi(size)", "FW_Combi(SIZE)", "FW_Combi(Size)")) {
            assertEquals(Directive.CountKind.SIZE, combinatoric(text).count().kind(), text);
            assertEquals("FW_Combi(size)", combinatoric(text).normalized(), text);
        }
        for (String text : List.of("FW_PermutR(all)", "FW_PermutR(ALL)", "FW_PermutR(full)", "FW_PermutR(Full)")) {
            assertEquals(Directive.CountKind.ALL, combinatoric(text).count().kind(), text);
            assertEquals("FW_PermutR(all)", combinatoric(text).normalized(), text);
        }
        assertEquals(Directive.Verb.COMBI_R, combinatoric("FW_CombiR(3)").verb());
        assertEquals(Directive.Verb.PERMUT_R, combinatoric("FW_PermutR(3)").verb());
    }

    @Test
    void thePermutDuplicateMarkerIsALegacySpellingOfFwPermutOnly() {
        Directive.Combinatoric d = combinatoric("FW_Permut(PermutationGenerator.TreatDuplicatesAs.IDENTICAL)");
        assertEquals(Directive.Verb.PERMUT, d.verb());
        assertTrue(d.duplicatesIdentical());
        assertEquals("FW_Permut(IDENTICAL)", d.normalized());
        assertTrue(refused("FW_PermutR(PermutationGenerator.TreatDuplicatesAs.IDENTICAL)").contains("FW_Permut only"));
        refused("FW_Permut(PermutationGenerator.TreatDuplicatesAs.DISTINCT)");
    }

    @Test
    void everyOtherSpellingOfAVerbIsRefusedWithTheFormsThatAreAccepted() {
        for (String text : List.of("FW_Combi(abc)", "FW_Combi(-1)", "FW_Combi(2", "FW_Combi2", "FW_CombiRx(2)", "FW_Combi(2)x",
                "FW_Combi(1,2)", "FW_Permut(k)", "FW_Combi(2.5)", "FW_Combi(2 2)", "FW_Combi(si ze)")) {
            String message = refused(text);
            assertTrue(message.contains("combination verb is written") && message.contains("silently read other spellings as"),
                    text + ": " + message);
        }
        assertTrue(refused("FW_Combi(99999999999)").contains("too large"));
        assertTrue(refused("FW_Combi(abc)").contains("FW_Combi(n)"));
        assertTrue(refused("FW_Permut(k)").contains("FW_Permut(size)"));
    }

    /**
     * Architect decision F23: white space around tokens is accepted and means what it would mean without it.
     * The legacy dispatcher read FW_Combi( 2 ) as FW_Combi(1); the new grammar reads 2.
     */
    @Test
    void whiteSpaceAroundTheTokensOfAVerbIsInsignificant() {
        for (String text : List.of("FW_Combi( 2 )", "FW_Combi(2 )", "FW_Combi( 2)", "FW_Combi (2)", " FW_Combi(2)", "FW_Combi(2) ",
                "FW_Combi\t(\t2\t)", "FW_Combi(\n2\n)")) {
            Directive.Combinatoric d = combinatoric(text);
            assertEquals(new Directive.Count(Directive.CountKind.EXACT, 2), d.count(), text);
            assertEquals("FW_Combi(2)", d.normalized(), text);
            assertEquals(combinatoric("FW_Combi(2)").identity(), d.identity(), text);
        }
        assertEquals(Directive.CountKind.SIZE, combinatoric("FW_CombiR( size )").count().kind());
        assertEquals(Directive.CountKind.ALL, combinatoric("FW_PermutR ( ALL )").count().kind());
        assertEquals(Directive.CountKind.DEFAULT, combinatoric("FW_Permut ( )").count().kind());
        assertTrue(combinatoric("FW_Permut( PermutationGenerator.TreatDuplicatesAs.IDENTICAL )").duplicatesIdentical());
        assertEquals(List.of(2, 5), ((Directive.Subsets) parse("FW_Subsets_RANGE ( 2 ,5 ) ")).sizes());
        assertEquals(Directive.SubsetMode.DEFAULT, ((Directive.Subsets) parse("FW_Subsets ( )")).mode());
        assertEquals("A", ((Directive.Cartes) parse("FW_Cartes( A )")).sheet(), "the legacy parser looked up ' A '");
        assertEquals("A B", ((Directive.Cartes) parse("FW_Cartes_first ( A B )")).sheet(), "space inside a name is kept");
        assertEquals("S1", ((Directive.Separator) parse("FW_Separator ( S1 )")).sheet());
        // the default and an explicit 1 are the same verb; FW_Permut(k) keeps its k although the legacy engine
        // ignored it (permute(k) of the builder is FW_Combi(k) followed by FW_Permut, a different directive pair)
        assertEquals(combinatoric("FW_Combi").identity(), combinatoric("FW_Combi(1)").identity());
        assertFalse(combinatoric("FW_Permut(2)").identity().equals(combinatoric("FW_Permut(3)").identity()));
    }

    // ---- subsets ----------------------------------------------------------------------------------------------

    @Test
    void subsetsWithoutAModeAreAllSubsets() {
        for (String text : List.of("FW_Subsets", "FW_Subsets()")) {
            Directive.Subsets d = (Directive.Subsets) parse(text);
            assertEquals(Directive.SubsetMode.DEFAULT, d.mode());
            assertEquals(List.of(), d.sizes());
            assertEquals("FW_Subsets", d.normalized());
        }
    }

    @Test
    void aSubsetModeIsCaseInsensitiveAndTakesTheSizesItNeeds() {
        Directive.Subsets after = (Directive.Subsets) parse("FW_Subsets_after(1)");
        assertEquals(Directive.SubsetMode.AFTER, after.mode());
        assertEquals(List.of(1), after.sizes());
        assertEquals("FW_Subsets_AFTER(1)", after.normalized());
        assertEquals("FW_Subsets_BEFORE(4)", parse("FW_Subsets_BEFORE(4)").normalized());
        assertEquals("FW_Subsets_EXACT(3)", parse("FW_Subsets_Exact(3)").normalized());
        assertEquals("FW_Subsets_RANGE(2,5)", parse("FW_Subsets_RANGE(2,5)").normalized());
        assertEquals("FW_Subsets_RANGE(2,5)", parse("FW_Subsets_range( 2 , 5 )").normalized());
        assertEquals("FW_Subsets_RANGE(3,3)", parse("FW_Subsets_RANGE(3,3)").normalized());
        Directive.Subsets given = (Directive.Subsets) parse("FW_Subsets_GIVEN(1,3,5)");
        assertEquals(List.of(1, 3, 5), given.sizes());
        assertEquals("FW_Subsets_GIVEN(1,3,5)", given.normalized());
        assertEquals("FW_Subsets_GIVEN(7)", parse("FW_Subsets_GIVEN(7)").normalized());
    }

    @Test
    void subsetsThatTheLegacyParserReadAsAllSubsetsAreRefused() {
        assertTrue(refused("FW_Subsets(3)").contains("all subsets"));
        assertTrue(refused("FW_Subsets_RANGE").contains("MODE"));
        assertTrue(refused("FW_Subsets_BEFORE()").contains("MODE"));
        assertTrue(refused("FW_Subsets_GIVEN(1,x)").contains("MODE"));
        assertTrue(refused("FW_SubsetsX").contains("MODE"));
        assertTrue(refused("FW_Subsets_WHATEVER(1)").contains("not a subset mode"));
        assertTrue(refused("FW_Subsets_DEFAULT(1)").contains("not a subset mode"));
        assertTrue(refused("FW_Subsets_EXACT(1,2)").contains("exactly one size"));
        assertTrue(refused("FW_Subsets_BEFORE(1,2)").contains("exactly one size"));
        assertTrue(refused("FW_Subsets_RANGE(5,2)").contains("smaller first"));
        assertTrue(refused("FW_Subsets_RANGE(1)").contains("two sizes"));
        assertTrue(refused("FW_Subsets_RANGE(1,2,3)").contains("two sizes"));
        assertTrue(refused("FW_Subsets_EXACT(99999999999)").contains("too large"));
    }

    // ---- cartesian product and separator ----------------------------------------------------------------------

    @Test
    void aCartesianProductNamesTheOtherSheetAndMayPutItFirst() {
        Directive.Cartes plain = (Directive.Cartes) parse("FW_Cartes(1)");
        assertFalse(plain.first());
        assertEquals("1", plain.sheet());
        assertEquals("FW_Cartes(1)", plain.normalized());
        Directive.Cartes first = (Directive.Cartes) parse("FW_Cartes_first(LEAF_END)");
        assertTrue(first.first());
        assertEquals("LEAF_END", first.sheet());
        assertEquals("FW_Cartes_first(LEAF_END)", first.normalized());
        assertTrue(((Directive.Cartes) parse("FW_Cartes_FIRST(Colors)")).first(), "the legacy test of _first ignores case");
        assertEquals("My Sheet (2)", ((Directive.Cartes) parse("FW_Cartes(My Sheet (2))")).sheet(), "up to the last bracket");
    }

    @Test
    void aSeparatorNamesTheSheetWhoseFirstValueSeparates() {
        Directive.Separator separator = (Directive.Separator) parse("FW_Separator(S1)");
        assertEquals("S1", separator.sheet());
        assertEquals("FW_Separator(S1)", separator.normalized());
    }

    @Test
    void cartesAndSeparatorWithoutASheetOrWithTrailingTextAreRefused() {
        for (String text : List.of("FW_Cartes", "FW_Cartes()", "FW_Cartes(A) junk", "FW_Cartes_firstA(B)", "FW_Cartesian(B)")) {
            assertTrue(refused(text).contains("FW_Cartes(sheet)"), text);
        }
        for (String text : List.of("FW_Separator", "FW_Separator()", "FW_Separator(S1) x", "FW_Separators(S1)")) {
            assertTrue(refused(text).contains("FW_Separator(sheet)"), text);
        }
    }

    // ---- group with replacements ------------------------------------------------------------------------------

    @Test
    void aGroupAloneIsLegalAndItsReplacementsAreReadLikeTheLegacyParserDid() {
        Directive.Group bare = (Directive.Group) parse("FW_Group");
        assertEquals(List.of(), bare.replacements());
        assertEquals("FW_Group", bare.normalized());

        Directive.Group group = (Directive.Group) parse("FW_Group\nFW_ReplaceRE(\"^\\[\", \"\")\nFW_ReplaceRE(\"\\]$\", \"\")\n"
                + "FW_ReplaceRE(\"a\", \"b\")");
        assertEquals(List.of("^\\[", "\\]$", "a"), group.replacements().stream().map(Directive.Replacement::pattern).toList());
        assertEquals(List.of("", "", "b"), group.replacements().stream().map(Directive.Replacement::prefix).toList());
        assertTrue(group.replacements().stream().allMatch(r -> r.sheetReference() == null && r.suffix().isEmpty()));
        assertEquals("FW_Group FW_ReplaceRE(\"^\\[\", \"\") FW_ReplaceRE(\"\\]$\", \"\") FW_ReplaceRE(\"a\", \"b\")",
                group.normalized());
    }

    @Test
    void aJoinedReplacementIsTheTextBeforeOneSheetsLabelAndTheTextAfterIt() {
        // the legacy parser looked up the sheet named by the word characters of the expression; the literals around
        // it (white space outside the quotes aside) surround that sheet's first label
        Directive.Group group = (Directive.Group) parse("FW_Group\nFW_ReplaceRE(\"\\[\\]\", \"[\" + 0 +\"]\")\n"
                + "FW_ReplaceRE(\"^\\[\\[\", \"[[\" + TMP + \", \")");
        assertEquals(List.of("0", "TMP"), group.replacements().stream().map(Directive.Replacement::sheetReference).toList());
        Directive.Replacement first = group.replacements().getFirst();
        assertEquals(List.of("[", "]"), List.of(first.prefix(), first.suffix()));
        assertEquals("\"[\" + 0 +\"]\"", first.written());
        Directive.Replacement second = group.replacements().get(1);
        assertEquals(List.of("[[", ", "), List.of(second.prefix(), second.suffix()));
        assertEquals("FW_Group FW_ReplaceRE(\"\\[\\]\", \"[\" + 0 +\"]\") FW_ReplaceRE(\"^\\[\\[\", \"[[\" + TMP + \", \")",
                group.normalized());
        Directive.Replacement several = ((Directive.Group) parse("FW_Group FW_ReplaceRE(\"x\", \"[\" + \"(\" + LEAF + \")\" + \"]\")"))
                .replacements().getFirst();
        assertEquals(List.of("[(", "LEAF", ")]"), List.of(several.prefix(), several.sheetReference(), several.suffix()));
    }

    @Test
    void aGroupMayStayOnOneLineWithOneOrMoreReplacements() {
        Directive.Group group = (Directive.Group) parse("FW_Group FW_ReplaceRE(\"1\", \"2\")");
        assertEquals(1, group.replacements().size());
        assertEquals("1", group.replacements().getFirst().pattern());
        assertEquals("2", group.replacements().getFirst().prefix());
        // the legacy expression ran the first replacement to the last '")' of the line; the grammar reads both items
        Directive.Group two = (Directive.Group) parse("FW_Group FW_ReplaceRE(\"a\", \"b\") FW_ReplaceRE(\"c\", \"d\")");
        assertEquals(List.of("a", "c"), two.replacements().stream().map(Directive.Replacement::pattern).toList());
        assertEquals(List.of("b", "d"), two.replacements().stream().map(Directive.Replacement::prefix).toList());
    }

    @Test
    void whiteSpaceAroundTheTokensOfAReplacementIsInsignificantButInsideQuotesItIsKept() {
        Directive.Group canonical = (Directive.Group) parse("FW_Group FW_ReplaceRE(\"a b\", \" x \")");
        for (String text : List.of("FW_Group FW_ReplaceRE( \"a b\" , \" x \" )", "FW_Group\tFW_ReplaceRE (\"a b\",\" x \")",
                "FW_Group\n  FW_ReplaceRE(\"a b\",  \" x \")  ")) {
            Directive.Group group = (Directive.Group) parse(text);
            assertEquals(canonical.identity(), group.identity(), text);
            assertEquals(List.of("a b"), group.replacements().stream().map(Directive.Replacement::pattern).toList(), text);
            assertEquals(List.of(" x "), group.replacements().stream().map(Directive.Replacement::prefix).toList(), text);
        }
        Directive.Group joined = (Directive.Group) parse("FW_Group FW_ReplaceRE(\"5\", \"[\"+X+\"]\")");
        assertEquals(((Directive.Group) parse("FW_Group FW_ReplaceRE(\"5\", \"[\"  +  X   + \"]\")")).identity(), joined.identity());
        assertFalse(joined.identity().equals(((Directive.Group) parse("FW_Group FW_ReplaceRE(\"5\", \"[ \" + X + \"]\")")).identity()));
        // a '+' inside the quotes is text: the legacy parser took the literal for a joined replacement
        Directive.Replacement plus = ((Directive.Group) parse("FW_Group FW_ReplaceRE(\"a\", \"1+2\")")).replacements().getFirst();
        assertEquals("1+2", plus.prefix());
        assertEquals(null, plus.sheetReference());
    }

    @Test
    void aBrokenGroupIsRefusedWithWhatIsExpected() {
        assertTrue(refused("FW_Groups").contains("stands alone"));
        assertTrue(refused("FW_Group junk").contains("unexpected text 'junk'"));
        String invalid = refused("FW_Group\nFW_ReplaceRE(\"[\", \"4\")");
        assertTrue(invalid.contains("not a valid regular expression") && invalid.contains("\"[\""), invalid);
        assertTrue(refused("FW_ReplaceRE(\"a\", \"b\")").contains("inside a FW_Group cell"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"\", \"b\")").contains("non-empty text in double quotes"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\" \"b\")").contains("non-empty text in double quotes"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\nb\", \"c\")").contains("on one line"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", \"b)").contains("not closed"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", \"b\"").contains("closing bracket"));
        assertTrue(refused("FW_Group FW_ReplaceRE \"a\", \"b\")").contains("opening bracket"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", \"b\")x").contains("unexpected text 'x'"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", \"b\"c\")").contains("closing bracket"), "a quote inside a literal");
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", X)").contains("without quoted texts"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", \"\" + X)").contains("starts and ends with a quoted text"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", X + \"\")").contains("starts and ends with a quoted text"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", \"\" + X + Y + \"\")").contains("exactly one sheet"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", \"[\" + \"]\")").contains("exactly one sheet"));
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", \"\" + X + \"\" + )").contains("one sheet name joined"));
        String word = refused("FW_Group FW_ReplaceRE(\"a\", \"x\" + X + \"\")");
        assertTrue(word.contains("holds a letter, digit or underscore") && word.contains("\"x\""), word);
        assertTrue(refused("FW_Group FW_ReplaceRE(\"a\", \"\" + My Sheet + \"\")").contains("exactly one sheet"),
                "two word runs are two names");
    }

    // ---- joiner -----------------------------------------------------------------------------------------------

    private static Directive.Joiner joiner(String text) {
        return (Directive.Joiner) parse(text);
    }

    @Test
    void aJoinerHasNineFieldsOfWhichSixAreSheetNamesAndTwoAreTheTablesToJoin() {
        Directive.Joiner j = joiner("FW_(St1,,E13,R1,E14,,En1,S1,M:1)");
        assertEquals("St1", j.start());
        assertEquals(Directive.OperandKind.SHEET, j.first().kind());
        assertEquals("E13", j.first().sheet());
        assertEquals("R1", j.relation());
        assertEquals("E14", j.second().sheet());
        assertEquals("En1", j.end());
        assertEquals("S1", j.separator());
        assertEquals(Directive.Multiplicity.M_TO_ONE, j.multiplicity());
        assertEquals("FW_(St1,,E13,R1,E14,,En1,S1,M:1)", j.normalized());
        assertEquals("FW_(,,E1,,E2,,,,1:1)", joiner("FW_(,,E1,,E2,,,,1:1)").normalized());
        assertEquals(Directive.Multiplicity.ONE_TO_N, joiner("FW_(,,E9,,E10,,,,1:N)").multiplicity());
        assertEquals(Directive.Multiplicity.M_TO_M, joiner("FW_(,,E5,,E6,,,,M:M)").multiplicity());
        assertEquals(Directive.Multiplicity.M_TO_N, joiner("FW_(,,E3,,E4,,,,M:N)").multiplicity());
        assertEquals(Directive.Multiplicity.ONE_TO_ONE, joiner("FW_(,,E3,,E4,,,,1:1)").multiplicity());
    }

    @Test
    void fieldsAreTrimmedAndTheNestedPlaceholdersAreTheTwoLegacyForms() {
        Directive.Joiner spaced = joiner("FW_( A ,, B , C , D ,, E , F , M:N )");
        assertEquals("FW_(A,,B,C,D,,E,F,M:N)", spaced.normalized());

        Directive.Joiner grouped = joiner("FW_(BUNDLE_OPEN,,FW_()G,,SEAL,,BUNDLE_CLOSE,,M:N)");
        assertEquals(Directive.OperandKind.NESTED_GROUPED, grouped.first().kind());
        assertTrue(grouped.first().isPlaceholder());
        assertEquals(null, grouped.first().sheet(), "resolved later by the planner");
        assertEquals("SEAL", grouped.second().sheet());
        assertEquals("FW_(BUNDLE_OPEN,,~FWG,,SEAL,,BUNDLE_CLOSE,,M:N)", grouped.normalized());

        Directive.Joiner plain = joiner("FW_(CONTEXT_OPEN,,FW_(),COMMA,NOTE,,CONTEXT_CLOSE,,M:N)");
        assertEquals(Directive.OperandKind.NESTED, plain.first().kind());
        assertEquals("COMMA", plain.relation());
        assertEquals("FW_(CONTEXT_OPEN,,~FWN,COMMA,NOTE,,CONTEXT_CLOSE,,M:N)", plain.normalized());

        Directive.Joiner second = joiner("FW_(PIPE_OPEN,,JZIP,REL_S,FW_(),,PIPE_CLOSE,,M:N)");
        assertEquals(Directive.OperandKind.NESTED, second.second().kind());
        assertEquals("JZIP", second.first().sheet());
    }

    @Test
    void aJoinerWithTheWrongShapeIsRefusedWithWhatIsExpected() {
        assertTrue(refused("FW_(A,,B,,C)").contains("nine comma-separated fields"));
        assertTrue(refused("FW_(,,E1,,E2,,,,1:1,x)").contains("not 10"));
        assertTrue(refused("FW_(,,E1,,E2,,,,1:1").contains("closing bracket"));
        assertTrue(refused("FW_(,x,E1,,E2,,,,1:1)").contains("reserved"));
        assertTrue(refused("FW_(,,E1,,E2,x,,,1:1)").contains("reserved"));
        assertTrue(refused("FW_(,,,,E2,,,,1:1)").contains("third field names the table to join"));
        assertTrue(refused("FW_(,,E1,,,,,,1:1)").contains("fifth field names the table to join"));
        assertTrue(refused("FW_(,,FW_(A),,E2,,,,1:1)").contains("exactly FW_() or FW_()G"));
        assertTrue(refused("FW_(A(1),,E1,,E2,,,,1:1)").contains("must be a sheet name or empty"));
        assertTrue(refused("FW_(,,E1,R(2),E2,,,,1:1)").contains("fourth field"));
    }

    @Test
    void whiteSpaceInsideTheNestedPlaceholdersAndTheMultiplicityIsInsignificant() {
        Directive.Joiner spaced = joiner("FW_( A ,, FW_( ) , R , FW_(  )G ,, E , , M : N )");
        assertEquals(Directive.OperandKind.NESTED, spaced.first().kind());
        assertEquals(Directive.OperandKind.NESTED_GROUPED, spaced.second().kind());
        assertEquals(Directive.Multiplicity.M_TO_N, spaced.multiplicity());
        assertEquals(joiner("FW_(A,,FW_(),R,FW_()G,,E,,M:N)").identity(), spaced.identity());
        assertTrue(refused("FW_(,,FW_() G,,E2,,,,1:1)").contains("exactly FW_() or FW_()G"), "the legacy parser read it as FW_()");
        assertTrue(refused("FW_(,,E1,,E2,,,,M : n)").contains("not one of"));
    }

    /**
     * Review finding IP01: the identity of a directive must tell apart everything that means something different.
     * Each text below means something different from every other one; their identities must all differ.
     */
    @Test
    void directivesThatMeanDifferentThingsHaveDifferentIdentities() {
        List<String> texts = List.of(
                "FW_Group FW_ReplaceRE(\"5\", \"\" + X + \"\")",
                "FW_Group FW_ReplaceRE(\"5\", \"[\" + X + \"]\")",
                "FW_Group FW_ReplaceRE(\"5\", \"[\" + X + \"\")",
                "FW_Group FW_ReplaceRE(\"5\", \"\" + X + \"[\")",
                "FW_Group FW_ReplaceRE(\"5\", \"\" + Y + \"\")",
                "FW_Group FW_ReplaceRE(\"5\", \"X\")",
                "FW_Group FW_ReplaceRE(\"5\", \"\")",
                "FW_Group FW_ReplaceRE(\"a=>b\", \"c\")",
                "FW_Group FW_ReplaceRE(\"a\", \"b=>c\")",
                "FW_Group FW_ReplaceRE(\"a\", \"b\") FW_ReplaceRE(\"c\", \"d\")",
                "FW_Group FW_ReplaceRE(\"c\", \"d\") FW_ReplaceRE(\"a\", \"b\")",
                "FW_Group FW_ReplaceRE(\"a\\tb\", \"c\")",
                "FW_Group FW_ReplaceRE(\"a\", \"\\tb\tc\")",
                "FW_Group FW_ReplaceRE(\"a\", \"-\")",
                "FW_Group",
                "FW_(,,X~FWN,,E,,,,M:N)",
                "FW_(,,FW_(),,E,,,,M:N)",
                "FW_(,,FW_()G,,E,,,,M:N)",
                "FW_(,,E,,FW_(),,,,M:N)",
                "FW_(A,,B,C,D,,E,F,M:N)",
                "FW_(A,,B,C,D,,E,F,M:M)",
                "FW_(,,A,B,C,,D,E,M:N)",
                "FW_Cartes(A)",
                "FW_Cartes_first(A)",
                "FW_Separator(A)",
                "FW_Combi(2)",
                "FW_CombiR(2)",
                "FW_Combi(size)",
                "FW_Combi(all)",
                "FW_Permut(PermutationGenerator.TreatDuplicatesAs.IDENTICAL)",
                "FW_Permut",
                "FW_Subsets",
                "FW_Subsets_GIVEN(1,2)",
                "FW_Subsets_GIVEN(12)",
                "FW_Subsets_RANGE(1,2)");
        java.util.Map<String, String> seen = new java.util.HashMap<>();
        for (String text : texts) {
            String identity = parse(text).identity();
            assertFalse(identity.contains("\n"), text);
            String earlier = seen.put(identity, text);
            assertNull(earlier, () -> "'" + text + "' and '" + earlier + "' share the identity " + identity);
        }
    }

    @Test
    void spellingsThatTheGrammarDefinesAsEqualShareTheirIdentity() {
        List<List<String>> classes = List.of(
                List.of("FW_Combi", "FW_Combi()", "FW_Combi(1)", " FW_Combi ( 1 ) "),
                List.of("FW_PermutR(all)", "FW_PermutR(FULL)", "FW_PermutR( All )"),
                List.of("FW_Subsets", "FW_Subsets()", "FW_Subsets ( )"),
                List.of("FW_Subsets_range(2,5)", "FW_Subsets_RANGE( 2 , 5 )"),
                List.of("FW_Cartes_first(A)", "FW_Cartes_FIRST( A )"),
                List.of("FW_Group FW_ReplaceRE(\"5\", \"[\" + X + \"]\")", "FW_Group\nFW_ReplaceRE( \"5\",\"[\"+X+\"]\" )"),
                List.of("FW_(A,,FW_()G,R,B,,E,S,1:N)", "FW_( A ,,FW_( )G, R ,B ,, E,S , 1 : N)"));
        for (List<String> equal : classes) {
            String identity = parse(equal.getFirst()).identity();
            for (String text : equal) {
                assertEquals(identity, parse(text).identity(), text);
            }
        }
    }

    @Test
    void theMultiplicityMustBeOneOfTheFiveUppercaseTokens() {
        String lower = refused("FW_(,,E1,,E2,,,,m:n)");
        assertTrue(lower.contains("did you mean M:N") && lower.contains("did nothing"), lower);
        assertTrue(refused("FW_(,,E1,,E2,,,,2:2)").contains("not one of 1:1, 1:N, M:1, M:M, M:N"));
        assertTrue(refused("FW_(,,E1,,E2,,,,1:n)").contains("did you mean 1:N"));
        assertTrue(refused("FW_(,,E1,,E2,,,,)").contains("not one of"));
        assertTrue(refused("FW_(,,E1,,E2,,,,N:1)").contains("not one of"));
    }

    // ---- not directives at all --------------------------------------------------------------------------------

    @Test
    void textThatIsNoDirectiveFamilyGivesNullSoTheCallerCanReportItAsUnknown() {
        for (String text : List.of("FW_Zzz", "FW_Optional", "FW_Exclude", "FW_Concatenator=,", "FW_", "plain", "", "fw_combi(1)", "  ")) {
            assertNull(DirectiveParser.parse(text, WHERE), text);
        }
    }

    @Test
    void splittingOnCommasHonoursBracketsAndTrimsEachPart() {
        assertEquals(List.of("a", "b(c,d)", "e"), DirectiveParser.splitTopLevelByComma("a,b(c,d),e"));
        assertEquals(List.of("a", "b", "c"), DirectiveParser.splitTopLevelByComma(" a , b ,c "));
        assertEquals(List.of("", "", ""), DirectiveParser.splitTopLevelByComma(",,"));
        assertEquals(List.of(""), DirectiveParser.splitTopLevelByComma(""));
        assertEquals(List.of("a)", "b"), DirectiveParser.splitTopLevelByComma("a),b"), "a stray closing bracket does not go below depth zero");
        assertEquals(List.of("a(b,c"), DirectiveParser.splitTopLevelByComma("a(b,c"), "an unclosed bracket swallows the rest");
        assertEquals(List.of("FW_()G", "x"), DirectiveParser.splitTopLevelByComma("FW_()G,x"));
    }
}
