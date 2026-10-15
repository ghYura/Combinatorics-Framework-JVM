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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Every expectation below was worked out by hand from the legacy regular expressions (comments become one
 * space, a double-quoted string is kept and followed by one space, line breaks become one space, and white
 * space survives only between two word characters or inside quotes), not copied from the output of the code.
 */
class CodeRefinerTest {

    // ---- comments ---------------------------------------------------------------------------------------------

    @Test
    void aLineCommentBecomesOneSpaceAndTheLineBreakStays() {
        // "a " + the replacement space + "\nb": the space before the comment is kept as well
        assertEquals("a  \nb", CodeRefiner.stripComments("a // note\nb"));
    }

    @Test
    void aBlockCommentBecomesOneSpaceEvenAcrossLines() {
        assertEquals("x   y", CodeRefiner.stripComments("x /* c1 */ y"));
        assertEquals("a c", CodeRefiner.stripComments("a/*\nb\n*/c"));
    }

    @Test
    void aDoubleQuotedStringIsKeptAndFollowedByASpace() {
        // the // inside the string is not a comment; the legacy expression appends one space after the string
        assertEquals("s = \"a // not a comment\" ;", CodeRefiner.stripComments("s = \"a // not a comment\";"));
        assertEquals("\"a\\\"b\"  c", CodeRefiner.stripComments("\"a\\\"b\" c"));
    }

    @Test
    void aLoneDoubleQuoteIsLeftAlone() {
        assertEquals("\"", CodeRefiner.stripComments("\""));
    }

    // ---- the whole refinement ---------------------------------------------------------------------------------

    @Test
    void commentsLineBreaksAndSpacesAreSqueezedOutOfCode() {
        assertEquals("int a=1;int b;", CodeRefiner.refine("int  a  =  1 ; // note\nint b;"));
        assertEquals("a b", CodeRefiner.refine("a /* x\ny */ b"));
        assertEquals("if(x){y;}", CodeRefiner.refine("if (x)\r\n{\r\n  y;\r\n}"));
    }

    @Test
    void whiteSpaceInsideDoubleQuotesIsKept() {
        assertEquals("print(\"a   b\");", CodeRefiner.refine("print(  \"a   b\"  );"));
        assertEquals("say(\"it's  fine\")", CodeRefiner.refine("say(\"it's  fine\")"));
    }

    @Test
    void whiteSpaceInsideSingleQuotesIsKept() {
        assertEquals("x='a   b';", CodeRefiner.refine("x  =  'a   b'  ;"));
    }

    @Test
    void anEscapedQuoteDoesNotEndAString() {
        assertEquals("x=\"a\\\"b\";", CodeRefiner.refine("x = \"a\\\"b\"  ;"));
    }

    @Test
    void nothingOrOnlyWhiteSpaceRefinesToNothing() {
        assertEquals("", CodeRefiner.refine(""));
        assertEquals("", CodeRefiner.refine("   "));
        assertEquals("", CodeRefiner.refine(" \t \n "));
    }

    @Test
    void anUnclosedDoubleQuoteGetsClosedAsTheLegacyCodeDidIt() {
        // a quirk of the legacy splitter, kept so that the output stays the same for every text it accepted
        assertEquals("a\"b  c\"", CodeRefiner.refine("a \"b  c"));
    }

    @Test
    void refiningTwiceChangesNothingMore() {
        String once = CodeRefiner.refine("for (int i = 0;  i < 3;  i++) { // loop\n  total += i; /* sum */ }");
        assertEquals("for(int i=0;i<3;i++){total+=i;}", once);
        assertEquals(once, CodeRefiner.refine(once));
    }

    // ---- limits ------------------------------------------------------------------------------------------------

    @Test
    void anOrdinaryLongTextIsRefinedWithinItsBudget() {
        String line = "int  a  =  1 ;\n";
        String text = line.repeat(1_500); // 22,500 characters, longer than any cell of the real workbooks
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> assertEquals("int a=1;".repeat(1_500), CodeRefiner.refine(text)));
    }

    @Test
    void anUnclosedQuoteWithManyBackslashesIsRefusedInsteadOfHangingTheParser() {
        // 60 backslashes can be tiled in about 2.5e12 ways by the legacy expression; the work budget stops it
        String pathological = "\"" + "\\".repeat(60) + "x";
        assertTimeoutPreemptively(Duration.ofSeconds(30), () ->
                assertThrows(CodeRefiner.TooComplexException.class, () -> CodeRefiner.refine(pathological)));
    }
}
