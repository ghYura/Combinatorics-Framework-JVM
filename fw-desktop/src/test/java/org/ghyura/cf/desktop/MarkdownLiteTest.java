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

package org.ghyura.cf.desktop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The manuals' Markdown as a Swing text pane shows it. */
class MarkdownLiteTest {

    @Test
    void headingsParagraphsAndRules() {
        assertEquals("<h3>0.2 Rules (summary; details in §1)</h3><p>One sentence that goes on.</p><hr><h2>1. Next</h2>",
                MarkdownLite.html("### 0.2 Rules (summary; details in §1)\n\nOne sentence\nthat goes on.\n\n---\n\n## 1. Next\n"));
    }

    @Test
    void numberedItemsKeepTheirContinuedLinesAndNestedBullets() {
        String html = MarkdownLite.html("""
                1. **No agreement, no operation.** Before you run anything,
                   get a yes.
                2. The human directs.

                - **The button.** It shows:
                  - the rules;
                  - the agreement.
                - Remember skips it.
                """);
        assertEquals("<ol><li><b>No agreement, no operation.</b> Before you run anything, get a yes.</li><li>The human directs.</li></ol>"
                + "<ul><li><b>The button.</b> It shows:<ul><li>the rules;</li><li>the agreement.</li></ul></li><li>Remember skips it.</li></ul>",
                html);
    }

    @Test
    void aListThatStartsLaterKeepsItsNumbers() {
        assertEquals("<ol start=\"3\"><li>three</li><li>four</li></ol>", MarkdownLite.html("3. three\n4. four"));
    }

    @Test
    void aQuoteHoldsItsOwnListAndParagraphs() {
        String html = MarkdownLite.html("""
                > **Delegating**
                >
                > 1. I am a real human being, and I direct
                >    this work.
                > 2. I can stop.
                >
                > Workspace folder: `__________`
                """);
        assertEquals("<blockquote><p><b>Delegating</b></p><ol><li>I am a real human being, and I direct this work.</li>"
                + "<li>I can stop.</li></ol><p>Workspace folder: <code>__________</code></p></blockquote>", html);
    }

    @Test
    void aTableHasItsHeaderAndCells() {
        String html = MarkdownLite.html("""
                | Allowed | Never |
                |---|---|
                | `cf --help`, `validate` | Training an AI |
                """);
        assertEquals("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\"><tr><th valign=\"top\">Allowed</th><th valign=\"top\">Never</th></tr>"
                + "<tr><td valign=\"top\"><code>cf --help</code>, <code>validate</code></td><td valign=\"top\">Training an AI</td></tr></table>",
                html);
    }

    @Test
    void codeIsShownAsWrittenAndTextIsEscaped() {
        assertEquals("<p>Record it in <code>&lt;workspace&gt;/OPERATIONS.md</code> &amp; keep <code>**this**</code> as is.</p>",
                MarkdownLite.html("Record it in `<workspace>/OPERATIONS.md` & keep `**this**` as is."));
        assertEquals("<pre>## not a heading\n&lt;b&gt;\n</pre>", MarkdownLite.html("```\n## not a heading\n<b>\n```"));
        String names = MarkdownLite.inline("FW_Seq, AI_DELEGATION and *one* word; see [the licence](LICENSE).");
        assertEquals("FW_Seq, AI_DELEGATION and <i>one</i> word; see the licence.", names);
        assertFalse(MarkdownLite.html("<script>x</script>").contains("<script>"));
        assertTrue(MarkdownLite.inline("a `lone backtick").contains("`lone"));
    }
}
