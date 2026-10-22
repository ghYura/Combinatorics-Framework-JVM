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

import org.ghyura.cf.desktop.AiDelegation.PathFormat;
import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The AI_delegation button: its place beside Hints, the agreement it shows, the remembered choice and the copied path. */
class AiDelegationTest {

    private static final String MANUAL = """
            # AI_DELEGATION.md: a manual

            ## 0. Read this first

            ### 0.1 How this document reaches you

            A busy human pressed the button.

            ### 0.2 Non-negotiable rules

            1. **No agreement, no operation.** Present the agreement
               and get a yes.
            2. Change nothing you were not asked to change.

            ### 0.3 Notation

            - `cf …` is the application.

            ---

            ## 1. The delegation agreement

            ### 1.2 Present this agreement

            > 1. I am a real human being.
            > 2. I can stop at any moment.

            ```
            ## not a heading inside code
            ```

            ---

            ## 2. What the Framework does
            """;

    /** The author's request: the button stands in one row with Hints, before it, in guif only. */
    @Test
    void guifHasTheButtonInOneRowWithHintsAndTheSwingDesktopDoesNot() throws Exception {
        Session session = Session.resolve(null, Map.of(), List.of());
        Desktop flat = edt(() -> new Desktop(session, null, FileTrail.inMemory(), true));
        Desktop swing = edt(() -> new Desktop(session, null, FileTrail.inMemory(), false));
        try {
            edt(() -> {
                JButton button = flat.aiDelegationButton();
                assertNotNull(button);
                assertEquals("AI_delegation", button.getText());
                assertSame(flat.hintsButton().getParent(), button.getParent(), "one row with Hints");
                List<Component> row = List.of(button.getParent().getComponents());
                assertEquals(row.indexOf(flat.hintsButton()) - 1, row.indexOf(button), "just before Hints");
                String hint = Hints.own(button);
                assertTrue(hint != null && hint.endsWith(".") && hint.contains("clipboard"), "the button explains itself: " + hint);
                button.doClick();   // headless: opens nothing and throws nothing
                assertNull(swing.aiDelegationButton());
                return null;
            });
        } finally {
            edt(flat::close).get(30, TimeUnit.SECONDS);
            edt(swing::close).get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void theAgreementIsTheRulesAndTheDelegationAgreementWithoutTheRest() {
        String agreement = AiDelegation.agreement(MANUAL);
        assertTrue(agreement.startsWith("### 0.2 Non-negotiable rules"), agreement);
        assertTrue(agreement.contains("and get a yes."));
        assertTrue(agreement.contains("## 1. The delegation agreement"));
        assertTrue(agreement.contains("> 2. I can stop at any moment."));
        assertTrue(agreement.contains("## not a heading inside code"), "a fence keeps its lines");
        assertTrue(agreement.endsWith("```"), "the closing rule is left out: " + agreement);
        for (String other : List.of("0.1 How", "0.3 Notation", "`cf …`", "## 2.")) {
            assertFalse(agreement.contains(other), "not in the agreement: " + other);
        }
        assertEquals("Just text.", AiDelegation.agreement("Just text.\n"), "a manual without the sections is shown whole");
    }

    /** The shipped manual has both sections, speaks of a busy human, and never uses the word the author ruled out. */
    @Test
    void theShippedManualHasItsAgreementAndSpeaksOfABusyHuman() throws Exception {
        Path manual = AiDelegation.locate(AiDelegation.folders(Examples.near()));
        assertNotNull(manual, "AI_DELEGATION.md at the top of this checkout");
        String text = Files.readString(manual, StandardCharsets.UTF_8);
        String agreement = AiDelegation.agreement(text);
        assertTrue(agreement.contains("Non-negotiable rules") && agreement.contains("The delegation agreement")
                && agreement.contains("When to stop"), agreement);
        assertFalse(agreement.contains("## 2."));
        assertTrue(text.contains("busy human") && AiDelegation.INTRO.contains("busy human"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("lazy"), "the manual says busy human");
        assertFalse(AiDelegation.INTRO.toLowerCase(Locale.ROOT).contains("lazy"));
    }

    @Test
    void theManualIsTheFirstFileOfItsNameWhoseTitleNamesIt(@TempDir Path dir) throws Exception {
        Path other = Files.createDirectories(dir.resolve("other"));
        Path checkout = Files.createDirectories(dir.resolve("checkout"));
        Files.writeString(other.resolve(AiDelegation.MANUAL), "notes of someone else\n");
        Files.writeString(checkout.resolve(AiDelegation.MANUAL), "\n" + MANUAL);
        assertEquals(checkout.resolve(AiDelegation.MANUAL), AiDelegation.locate(List.of(dir.resolve("missing"), other, checkout)));
        assertNull(AiDelegation.locate(List.of(other)));
        AiDelegation nowhere = new AiDelegation(null, List.of(other), () -> null);
        assertNull(nowhere.manual(), "neither a copy on disk nor one inside the application");
        assertTrue(nowhere.missing().contains(other.toString()), "the message says where it looked");
    }

    /** The author's request: the manual travels inside the jar, the same text as at the top of the checkout. */
    @Test
    void theApplicationCarriesTheManual() throws Exception {
        byte[] carried = AiDelegation.bundled();
        assertNotNull(carried, "org/ghyura/cf/desktop/AI_DELEGATION.md inside the application");
        Path checkout = AiDelegation.locate(AiDelegation.folders(Examples.near()));
        assertNotNull(checkout);
        assertTrue(java.util.Arrays.equals(Files.readAllBytes(checkout), carried), "the build copies the checkout's manual");
    }

    /** An installation without the file pastes the path of the application's copy, written out beside the settings. */
    @Test
    void anInstallationWithoutTheFileUsesTheApplicationsCopy(@TempDir Path dir) throws Exception {
        Path store = dir.resolve("config").resolve(AiDelegation.STORE);
        byte[] text = MANUAL.getBytes(StandardCharsets.UTF_8);
        AiDelegation installed = new AiDelegation(store, List.of(Files.createDirectories(dir.resolve("installation"))), () -> text);
        Path manual = installed.manual();
        assertEquals(store.resolveSibling(AiDelegation.MANUAL).toAbsolutePath().normalize(), manual);
        assertEquals(MANUAL, Files.readString(manual, StandardCharsets.UTF_8));
        Files.writeString(manual, "an older manual");
        assertEquals(MANUAL, Files.readString(installed.manual(), StandardCharsets.UTF_8), "a newer application writes its copy again");
        Files.writeString(dir.resolve("installation").resolve(AiDelegation.MANUAL), MANUAL + "\nthe installation's own\n");
        assertEquals(dir.resolve("installation").resolve(AiDelegation.MANUAL), installed.manual(), "a copy on disk comes first");
    }

    @Test
    void aRememberedChoiceSkipsTheAgreementUntilItsTextChanges(@TempDir Path dir) throws Exception {
        Path store = dir.resolve("config").resolve(AiDelegation.STORE);
        String agreement = AiDelegation.agreement(MANUAL);
        AiDelegation first = new AiDelegation(store, List.of());
        assertTrue(first.mustAsk(agreement));
        first.chose(agreement, false);
        assertTrue(first.mustAsk(agreement), "OK without Remember asks again");
        assertFalse(Files.exists(store), "and writes nothing");
        first.format(PathFormat.URI);
        first.chose(agreement, true);
        assertFalse(first.mustAsk(agreement));
        AiDelegation next = new AiDelegation(store, List.of());
        assertFalse(next.mustAsk(agreement), "remembered from one session to the next");
        assertEquals(PathFormat.URI, next.format(), "with the form of the path");
        assertTrue(next.mustAsk(agreement.replace("I can stop", "I may stop")), "a changed agreement is shown again");
        assertFalse(AiDelegation.fingerprint(agreement).equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest
                .getInstance("SHA-256").digest(agreement.getBytes(StandardCharsets.UTF_8)))), "the statements of respect are signed too");
        next.forget();
        assertTrue(new AiDelegation(store, List.of()).mustAsk(agreement), "Ask me again next time");
        Properties kept = new Properties();
        try (var in = Files.newBufferedReader(store, StandardCharsets.UTF_8)) {
            kept.load(in);
        }
        assertEquals("false", kept.getProperty("remember"));
        AiDelegation session = new AiDelegation(null, List.of());
        session.chose(agreement, true);
        assertFalse(session.mustAsk(agreement), "a desktop without a store remembers for its session");
    }

    @Test
    void theStoreLiesBesideTheDesktopsRememberedPlaces(@TempDir Path dir) throws Exception {
        Path places = dir.resolve("combinatorics-framework").resolve("desktop-places.properties");
        try (FileTrail trail = FileTrail.remembered(places)) {
            AiDelegation delegation = AiDelegation.of(trail, new Examples(List.of(dir)));
            delegation.chose("agreement", true);
            assertTrue(Files.isRegularFile(places.resolveSibling(AiDelegation.STORE)));
            assertEquals(dir.toAbsolutePath().normalize(), delegation.folders().getFirst());
        }
        try (FileTrail trail = FileTrail.inMemory()) {
            assertNull(trail.store());
        }
    }

    @Test
    void thePathIsFirstWrittenAsTheOperatingSystemWritesIt(@TempDir Path dir) {
        Path manual = dir.resolve("with space").resolve(AiDelegation.MANUAL);
        Map<PathFormat, String> forms = AiDelegation.forms(manual);
        assertEquals(PathFormat.NATIVE, forms.keySet().iterator().next());
        assertEquals(manual.toAbsolutePath().normalize().toString(), forms.get(PathFormat.NATIVE));
        assertEquals(manual.toUri().toString(), forms.get(PathFormat.URI));
        assertTrue(forms.get(PathFormat.URI).startsWith("file:") && forms.get(PathFormat.URI).contains("with%20space"));
        assertEquals(forms.size(), forms.values().stream().distinct().count(), "each form offered differs");
        assertTrue(forms.containsKey(PathFormat.SLASHES) == forms.get(PathFormat.NATIVE).contains("\\"),
                "forward slashes are offered only where the system writes backslashes");
        assertTrue(PathFormat.NATIVE.toString().startsWith("As ") && PathFormat.NATIVE.toString().endsWith(" writes it"));
    }

    /**
     * The licence-like window: the agreement, then the two statements of respect, Remember my choice and do not show this
     * further, and OK at the bottom; OK can be pressed only while both statements are ticked, and says whether to remember.
     */
    @Test
    void theAgreementWindowsOkWaitsForBothStatementsOfRespect(@TempDir Path dir) throws Exception {
        List<Boolean> accepted = new ArrayList<>();
        edt(() -> {
            var window = new AiDelegationDialog.Agreement(AiDelegation.agreement(MANUAL), dir.resolve(AiDelegation.MANUAL), accepted::add);
            var root = window.component();
            root.setSize(880, 760);
            HintLayerTest.layout(root);
            String shown = window.text.getDocument().getText(0, window.text.getDocument().getLength());
            assertTrue(shown.contains("No agreement, no operation.") && shown.contains("I am a real human being."), shown);
            assertFalse(shown.contains("**"), "the markdown is shown formatted");
            assertEquals(List.of("I respect human work/job", "I respect long work on this latest version from very beginning as a "
                    + "hand-made pet-project by the author of this Combinatorics Framework"),
                    window.pledges.stream().map(b -> b.getAccessibleContext().getAccessibleName()).toList(), "the author's words");
            assertEquals("Remember my choice and do not show this further", window.remember.getAccessibleContext().getAccessibleName());
            assertTrue(window.remember.getText().contains("Remember my choice and do not show this further"));
            assertFalse(window.remember.isSelected(), "nothing is remembered unless asked");
            assertEquals("OK", window.ok.getText());
            int textBottom = SwingUtilities.convertPoint(window.text.getParent(), 0, window.text.getParent().getHeight(), root).y;
            int previous = textBottom;
            for (Component c : List.of(window.pledges.get(0), window.pledges.get(1), window.remember)) {
                int top = SwingUtilities.convertPoint(c, 0, 0, root).y;
                assertTrue(top >= previous && c.getHeight() > 0, "under the agreement, in order: " + c);
                previous = top + c.getHeight();
            }
            int okTop = SwingUtilities.convertPoint(window.ok, 0, 0, root).y;
            int rememberTop = SwingUtilities.convertPoint(window.remember, 0, 0, root).y;
            assertTrue(okTop > textBottom && okTop + window.ok.getHeight() > rememberTop && okTop < rememberTop + window.remember.getHeight(),
                    "OK at the bottom, in one row with Remember");
            assertFalse(window.ok.isEnabled(), "OK waits for the statements");
            window.ok.doClick();
            window.remember.doClick();
            window.pledges.get(0).doClick();
            assertFalse(window.ok.isEnabled(), "one statement is not enough");
            window.ok.doClick();
            window.pledges.get(1).doClick();
            assertTrue(window.ok.isEnabled(), "both statements ticked");
            window.pledges.get(0).doClick();
            assertFalse(window.ok.isEnabled(), "untick one and OK waits again");
            window.pledges.get(0).doClick();
            window.ok.doClick();
            return null;
        });
        assertEquals(List.of(true), accepted, "only the OK pressed while enabled counts, with Remember ticked");
    }

    /** With the choice remembered, a notice says the path is copied; it fades in its last seconds and presses its own OK. */
    @Test
    void aRememberedChoiceShowsANoticeThatFadesAwayByItself(@TempDir Path dir) throws Exception {
        String agreement = AiDelegation.agreement(MANUAL);
        AiDelegation delegation = new AiDelegation(dir.resolve(AiDelegation.STORE), List.of(dir));
        delegation.chose(agreement, true);
        AtomicReference<Boolean> closed = new AtomicReference<>(false);
        edt(() -> {
            var notice = new AiDelegationDialog.Notice(delegation, "/home/someone/AI_DELEGATION.md", () -> closed.set(true));
            String text = notice.message.getText();
            assertTrue(text.contains("AI_DELEGATION.md is copied") && text.contains("how to use the Combinatorics Framework and what for")
                    && text.contains("/home/someone/AI_DELEGATION.md") && text.contains("10 seconds"), text);
            notice.askAgain.doClick();
            assertTrue(delegation.mustAsk(agreement), "Ask me again next time");
            notice.askAgain.doClick();
            assertFalse(delegation.mustAsk(agreement), "and remembered again");
            notice.ok.doClick();
            return null;
        });
        assertTrue(closed.get());
        assertEquals(10_000, AiDelegationDialog.NOTICE_MILLIS);
        assertEquals(1f, AiDelegationDialog.opacity(0));
        assertEquals(1f, AiDelegationDialog.opacity(AiDelegationDialog.NOTICE_MILLIS - AiDelegationDialog.FADE_MILLIS));
        float midway = AiDelegationDialog.opacity(AiDelegationDialog.NOTICE_MILLIS - AiDelegationDialog.FADE_MILLIS / 2);
        assertTrue(midway > 0.4f && midway < 0.6f, "it fades slowly: " + midway);
        assertTrue(AiDelegationDialog.opacity(9_900) > 0f);
        assertEquals(0f, AiDelegationDialog.opacity(10_000), "gone after ten seconds");
    }

    /** After OK the path is already on the clipboard; another form copies at once. */
    @Test
    void afterOkThePathIsOnTheClipboardReadyToPaste(@TempDir Path dir) throws Exception {
        Path manual = dir.resolve(AiDelegation.MANUAL);
        AiDelegation delegation = new AiDelegation(dir.resolve(AiDelegation.STORE), List.of(dir));
        List<String> clipboard = new ArrayList<>();
        AtomicReference<Boolean> closed = new AtomicReference<>(false);
        edt(() -> {
            var ready = new AiDelegationDialog.Ready(delegation, manual, clipboard::add, () -> closed.set(true));
            String nativeForm = manual.toAbsolutePath().normalize().toString();
            assertEquals(List.of(nativeForm), clipboard, "copied before the window shows");
            assertEquals(nativeForm, ready.path.getText());
            assertFalse(ready.path.isEditable());
            assertEquals("Copied.", ready.status.getText());
            ready.form.setSelectedItem(PathFormat.URI);
            assertEquals(manual.toUri().toString(), clipboard.getLast());
            assertEquals(clipboard.getLast(), ready.path.getText());
            assertEquals(PathFormat.URI, delegation.format());
            ready.copy.doClick();
            assertEquals(3, clipboard.size());
            ready.ok.doClick();
            var busy = new AiDelegationDialog.Ready(new AiDelegation(null, List.of()), manual, text -> {
                throw new IllegalStateException("owned by another application");
            }, () -> { });
            assertTrue(busy.status.getText().contains("copy it yourself"), busy.status.getText());
            return null;
        });
        assertTrue(closed.get());
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
