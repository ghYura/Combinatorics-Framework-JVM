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

import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import java.awt.Color;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The author's request: a Credits button, last in the row after Hints, about the author of the Combinatorics Framework. */
class CreditsTest {

    @Test
    void guifEndsTheRowWithCreditsRightAfterHintsAndTheSwingDesktopHasNone() throws Exception {
        Session session = Session.resolve(null, Map.of(), List.of());
        Desktop flat = edt(() -> new Desktop(session, null, FileTrail.inMemory(), true));
        Desktop swing = edt(() -> new Desktop(session, null, FileTrail.inMemory(), false));
        try {
            edt(() -> {
                JButton credits = flat.creditsButton();
                assertNotNull(credits);
                assertEquals("Credits", credits.getText());
                assertSame(flat.hintsButton().getParent(), credits.getParent(), "one row with Hints");
                List<Component> row = List.of(credits.getParent().getComponents());
                assertEquals(row.size() - 1, row.indexOf(credits), "the last button of the row");
                assertEquals(row.indexOf(flat.hintsButton()) + 1, row.indexOf(credits), "right after Hints");
                assertEquals(row.indexOf(flat.hintsButton()) - 1, row.indexOf(flat.aiDelegationButton()), "AI_delegation keeps its place");
                String hint = Hints.own(credits);
                assertTrue(hint != null && hint.endsWith(".") && hint.contains("author"), "the button explains itself: " + hint);
                credits.doClick();   // headless: opens nothing and throws nothing
                assertNull(swing.creditsButton());
                // hint mode works for the row's new buttons as for every element of the desktop
                var layer = flat.component();
                layer.setSize(1400, 900);
                HintLayerTest.layout(layer);
                @SuppressWarnings("unchecked")
                javax.swing.JLayer<javax.swing.JComponent> l = (javax.swing.JLayer<javax.swing.JComponent>) layer;
                for (JButton button : List.of(flat.aiDelegationButton(), credits)) {
                    flat.hints().hover(l, HintLayerTest.center(button, layer));
                    flat.hints().settle(l);
                    assertEquals(Hints.own(button), flat.hints().text(), "the pop-up of " + button.getText());
                }
                flat.hintsButton().doClick();
                flat.hints().hover(l, HintLayerTest.center(flat.aiDelegationButton(), layer));
                flat.hints().settle(l);
                assertNull(flat.hints().text(), "and none while hints are off");
                flat.hintsButton().doClick();
                return null;
            });
        } finally {
            edt(flat::close).get(30, TimeUnit.SECONDS);
            edt(swing::close).get(30, TimeUnit.SECONDS);
        }
    }

    /** Every sentence about the author stands, word for word, in the checkout's LICENSE or NOTICE.md, and on the page. */
    @Test
    void everyStatementAboutTheAuthorIsQuotedFromLicenseOrNotice() throws Exception {
        Path checkout = Examples.near().checkout();
        String sources = normal(Files.readString(checkout.resolve("NOTICE.md"), StandardCharsets.UTF_8) + "\n"
                + Files.readString(checkout.resolve("LICENSE"), StandardCharsets.UTF_8));
        String page = edt(() -> {
            var text = new Credits.Page(s -> { }, CompletableFuture::completedFuture, () -> { }).text;
            return normal(text.getDocument().getText(0, text.getDocument().getLength()));
        });
        List<String> unsourced = new ArrayList<>();
        List<String> unshown = new ArrayList<>();
        for (String quote : Credits.QUOTED) {
            if (!sources.contains(normal(quote))) unsourced.add(quote);
            if (!page.contains(normal(quote))) unshown.add(quote);
        }
        assertEquals(List.of(), unsourced, "quotes not found in LICENSE or NOTICE.md");
        assertEquals(List.of(), unshown, "quotes the page does not show as written");
        assertTrue(page.contains("Created by Yurii Baranov") && page.contains("since the end of 2012"), page);
        assertTrue(sources.contains(normal(Credits.CONTACTS.replace(", ", " or "))), "the contact addresses are LICENSE's");
    }

    @Test
    void theWindowCopiesTheContactAddressesAndReadsWell() throws Exception {
        List<String> clipboard = new ArrayList<>();
        AtomicReference<Boolean> closed = new AtomicReference<>(false);
        edt(() -> {
            var page = new Credits.Page(clipboard::add, CompletableFuture::completedFuture, () -> closed.set(true));
            page.copy.doClick();
            assertEquals(List.of(Credits.CONTACTS), clipboard);
            assertEquals("Copied.", page.status.getText());
            var busy = new Credits.Page(text -> {
                throw new IllegalStateException("owned by another application");
            }, CompletableFuture::completedFuture, () -> { });
            busy.copy.doClick();
            assertTrue(busy.status.getText().contains(Credits.CONTACTS), "the addresses stay readable when copying fails");
            page.close.doClick();
            assertTrue(!page.text.isEditable(), "a page to read, on the outputs' gradient in guif");
            return null;
        });
        assertTrue(closed.get());
        Color quote = Color.decode("#eef3f9");
        assertTrue(CandidateCodeView.contrast(Color.BLACK, quote) >= 4.5 && CandidateCodeView.contrast(Color.BLACK, GrayGradient.BOTTOM) >= 4.5,
                "the author's statement reads well on its background");
    }

    /** The author's request: the window links to his YouTube channel; the link and its button open it in the browser. */
    @Test
    void theAuthorsYouTubeChannelOpensFromItsLinkAndItsButton() throws Exception {
        assertEquals("https://www.youtube.com/@yuriibaranov/videos", Credits.CHANNEL);
        List<URI> opened = new ArrayList<>();
        List<String> clipboard = new ArrayList<>();
        var page = edt(() -> new Credits.Page(clipboard::add, address -> {
            opened.add(address);
            return CompletableFuture.completedFuture(address);
        }, () -> { }));
        edt(() -> {
            String shown = page.text.getDocument().getText(0, page.text.getDocument().getLength());
            assertTrue(shown.contains("Videos on YouTube: youtube.com/@yuriibaranov/videos"), shown);
            assertTrue(Credits.PAGE.contains("<a href=\"" + Credits.CHANNEL + "\">"), "a link the reader can click");
            page.text.fireHyperlinkUpdate(new HyperlinkEvent(page.text, HyperlinkEvent.EventType.ACTIVATED,
                    URI.create(Credits.CHANNEL).toURL(), Credits.CHANNEL));
            page.channel.doClick();
            return null;
        });
        assertEquals(List.of(URI.create(Credits.CHANNEL), URI.create(Credits.CHANNEL)), opened, "the link and the button open the channel");
        assertEquals("Opened in your browser.", edt(page.status::getText));
        assertEquals(List.of(), clipboard);

        var noBrowser = edt(() -> new Credits.Page(clipboard::add, address -> CompletableFuture.failedFuture(
                new IllegalStateException("Automatic browser opening is unavailable.")), () -> { }));
        edt(() -> {
            noBrowser.channel.doClick();
            return null;
        });
        assertEquals("No browser could be opened; the link is copied.", edt(noBrowser.status::getText));
        assertEquals(List.of(Credits.CHANNEL), clipboard, "without a browser the link is on the clipboard");
    }

    /** The author's request: hints work in the Credits window as on the desktop, following its Hints button. */
    @Test
    void everyElementOfTheCreditsWindowExplainsItselfWhileHintsAreOn() throws Exception {
        edt(() -> {
            AiDelegationTest.hintsFollowTheDesktopsButton(new Credits.Page(s -> { }, CompletableFuture::completedFuture, () -> { }).component());
            return null;
        });
    }

    /** White space, quotation marks, markdown emphasis and quote markers aside. */
    private static String normal(String text) {
        return text.replaceAll("(?m)^\\s*>\\s?", " ").replaceAll("[*\"“”]", "").replaceAll("\\s+", " ").trim();
    }

    private static <T> T edt(Callable<T> work) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                value.set(work.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) throw failure.get();
        return value.get();
    }
}
