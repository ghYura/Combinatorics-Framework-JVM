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

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CandidateCodeViewTest {
    @Test
    void anyLanguageHasNavigableOriginsAndColorFreeModeWithoutChangingCode() throws Exception {
        var source = CodeFormatterTest.source("SELECT * ", "FROM events ", "WHERE urgent = TRUE;");
        CandidateCodeView view = edt(CandidateCodeView::new);
        try {
            edt(() -> {
                view.show(source);
                assertEquals(3, view.originCount());
                assertEquals(3, view.code().getHighlighter().getHighlights().length);
                view.selectOriginRow(2);
                assertTrue(view.detail().contains("Step2!A1"));
                assertTrue(view.detail().contains("FW_Optional"));
                assertEquals(source.chunks().get(2).start(), view.code().getCaretPosition());
                view.mode(CandidateCodeView.Mode.LIST);
                assertEquals(0, view.code().getHighlighter().getHighlights().length);
                assertEquals(source.text(), view.source());
                view.code().setCaretPosition(0);
                assertTrue(view.detail().contains("Step0!A1"));
                view.mode(CandidateCodeView.Mode.CODE);
                view.mode(CandidateCodeView.Mode.BACKGROUNDS);
                assertEquals(3, view.code().getHighlighter().getHighlights().length);
                return null;
            });
        } finally { edt(view::close).get(15, TimeUnit.SECONDS); }
    }

    @Test
    void formatRestoreSelectAndCloseCannotApplyAnOldCandidatesFormattingToANewCandidate() throws Exception {
        CandidateCodeView view = edt(CandidateCodeView::new);
        var first = CodeFormatterTest.source("{\"first\":", "[1,2,3]}");
        var second = CodeFormatterTest.source("SELECT * FROM other;");
        try {
            edt(() -> { view.show(first); return null; });
            edt(view::format).get(15, TimeUnit.SECONDS);
            edt(() -> {
                assertTrue(view.source().contains("\n"));
                assertTrue(view.status().startsWith("Formatted JSON"));
                assertEquals(2, view.originCount());
                view.original();
                assertEquals(first.text(), view.source());
                return null;
            });
            var stale = edt(() -> {
                var future = view.format();
                view.show(second); // happens before the result can be delivered to the EDT
                return future;
            });
            stale.get(15, TimeUnit.SECONDS);
            assertEquals(second.text(), edt(view::source));
            var discarded = edt(() -> {
                view.show(first);
                var future = view.format();
                view.original();
                return future;
            });
            discarded.get(15, TimeUnit.SECONDS);
            assertEquals(first.text(), edt(view::source));
            var closed = edt(() -> { view.format(); return view.close(); });
            closed.get(15, TimeUnit.SECONDS);
            assertEquals(first.text(), edt(view::source));
        } finally { edt(view::close).get(15, TimeUnit.SECONDS); }
    }

    /** The author's rule: odd rows of every table stand apart; only the colored mode turns the chunk numbers into swatches. */
    @Test
    void theOriginListKeepsItsAlternatingRowsAndShowsSwatchesOnlyInTheColoredMode() throws Exception {
        var source = CodeFormatterTest.source("SELECT * ", "FROM events ", "WHERE urgent = TRUE;");
        Object previous = javax.swing.UIManager.get("Table.alternateRowColor");
        Color alternate = new Color(0xe4ebf5);
        javax.swing.UIManager.put("Table.alternateRowColor", alternate);
        CandidateCodeView view = edt(CandidateCodeView::new);
        try {
            edt(() -> {
                view.show(source);
                var table = view.originTable();
                java.util.function.IntFunction<Color> chunk = row -> table.getCellRenderer(row, 0)
                        .getTableCellRendererComponent(table, row + 1, false, false, row, 0).getBackground();
                java.util.function.IntFunction<Color> origin = row -> table.getCellRenderer(row, 2)
                        .getTableCellRendererComponent(table, "x", false, false, row, 2).getBackground();
                for (CandidateCodeView.Mode mode : new CandidateCodeView.Mode[]{CandidateCodeView.Mode.LIST, CandidateCodeView.Mode.CODE}) {
                    view.mode(mode);
                    assertEquals(alternate, chunk.apply(1), mode + ": the odd row's chunk number");
                    assertNotEquals(chunk.apply(0), chunk.apply(1), mode + ": even and odd chunk numbers look apart");
                    assertEquals(chunk.apply(2), chunk.apply(0), mode + ": no swatch is left over from an earlier row");
                }
                view.mode(CandidateCodeView.Mode.BACKGROUNDS);
                Color[] swatches = {chunk.apply(0), chunk.apply(1), chunk.apply(2)};
                assertEquals(3, java.util.Set.of(swatches).size(), "each chunk number shows its own code background");
                for (int row = 0; row < 3; row++) assertNotEquals(alternate, swatches[row]);
                assertEquals(alternate, origin.apply(1), "the other columns keep the alternating rows");
                view.mode(CandidateCodeView.Mode.LIST);
                assertEquals(alternate, chunk.apply(1), "back to the color-free mode, the rows alternate again");
                return null;
            });
        } finally {
            edt(view::close).get(15, TimeUnit.SECONDS);
            javax.swing.UIManager.put("Table.alternateRowColor", previous);
        }
    }

    @Test
    void paletteMeetsTextContrastAcrossLightDarkAndMidtoneThemes() {
        for (Color text : new Color[]{Color.BLACK, Color.WHITE, new Color(0x333333), new Color(0xcccccc),
                new Color(0x777777), new Color(0x35658d)}) {
            for (int i = 0; i < 6; i++) assertTrue(CandidateCodeView.contrast(text, CandidateCodeView.background(i, text)) >= 4.5);
        }
    }

    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
