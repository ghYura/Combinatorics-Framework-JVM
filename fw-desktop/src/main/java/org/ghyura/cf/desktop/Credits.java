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

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The Credits button, last in the row beside Hints, and the window it opens: who made the Combinatorics Framework, in
 * the author's own words. Every statement about him is taken from the LICENSE and NOTICE.md at the top of this
 * checkout ({@link #QUOTED} lists them, and a test keeps them equal to those files); the window copies his contact
 * addresses on request and opens his YouTube channel in the browser (when no browser can be opened, the link is copied
 * instead). Every element of the window explains itself in a hint, shown while the desktop's Hints button is on (F1
 * there presses it). Its content is built apart from its window, so tests build it headless.
 */
final class Credits {

    static final String TITLE = "Credits";
    static final String HINT = "Who made the Combinatorics Framework: its author, his statement in his own words, and how to "
            + "reach him.";
    /** The author's contact addresses, as LICENSE gives them. */
    static final String CONTACTS = "mail4yur@gmail.com, yurii.baranov.22@gmail.com";
    /** The author's YouTube channel, as he gave it. */
    static final String CHANNEL = "https://www.youtube.com/@yuriibaranov/videos";

    /**
     * The sentences the window quotes, each as it stands in LICENSE or NOTICE.md and on the page (white space, quotation
     * marks and markdown emphasis aside).
     */
    static final List<String> QUOTED = List.of(
            "Copyright (c) 2012–2026 Yurii Baranov, Kyiv, Ukraine.",
            "The Combinatorics Framework is entirely the work of Yurii Baranov.",
            "the Java code of this edition was written under his direction with the help of AI assistants",
            "Ethical motto: *\"Do good. Avoid evil\"*",
            "This code is made to serve real human beings.",
            "It is not offered for training or fine-tuning AI systems, nor for replacing people — only to help a person become "
                    + "familiar with this Combinatorics Framework and to learn to think in combinations.",
            "The knowledge this Framework teaches — combinatorial test design, the decomposition of a task into instances, "
                    + "actions, order and constraints, and their composition into verifiable scenarios — belongs to humanity. "
                    + "It is offered for the benefit of people, nature and the living world.",
            "— Yurii Baranov, author, Kyiv, Ukraine",
            "mail4yur@gmail.com or yurii.baranov.22@gmail.com",
            "Change Date: 2030-08-08");

    /** The page the window shows. */
    static final String PAGE = "<html><head><style>body { margin: 12px; } h2 { margin: 0 0 2px 0; } h3 { margin: 12px 0 4px 0; } "
            + "p { margin: 4px 0; } blockquote { margin: 4px 0 4px 10px; padding: 8px; background-color: #eef3f9; } "
            + ".small { font-size: 90%; }</style></head><body>"
            + "<h2>Combinatorics Framework</h2>"
            + "<p>also known as <i>the Bundle</i> &middot; JVM Edition</p>"
            + "<p><b>Created by Yurii Baranov</b>, Kyiv, Ukraine.<br>"
            + "Designed and built by hand since the end of 2012: the long work of one author.<br>"
            + "Copyright (c) 2012–2026 Yurii Baranov, Kyiv, Ukraine.</p>"
            + "<p>The Combinatorics Framework is entirely the work of Yurii Baranov. Its design, its semantics and the behaviour "
            + "every module reproduces are his; the Java code of this edition was written under his direction with the help of "
            + "AI assistants.</p>"
            + "<p><b>Ethical motto:</b> <i>&ldquo;Do good. Avoid evil&rdquo;</i></p>"
            + "<h3>Author's statement</h3>"
            + "<blockquote>This code is made to serve real human beings.<br><br>"
            + "It is not offered for training or fine-tuning AI systems, nor for replacing people — only to help a person become "
            + "familiar with this Combinatorics Framework and to learn to think in combinations.<br><br>"
            + "The knowledge this Framework teaches — combinatorial test design, the decomposition of a task into instances, "
            + "actions, order and constraints, and their composition into verifiable scenarios — belongs to humanity. It is "
            + "offered for the benefit of people, nature and the living world.<br><br>"
            + "— Yurii Baranov, author, Kyiv, Ukraine</blockquote>"
            + "<h3>Contact</h3>"
            + "<p>mail4yur@gmail.com or yurii.baranov.22@gmail.com<br>github.com/ghYura<br>"
            + "Videos on YouTube: <a href=\"" + CHANNEL + "\">youtube.com/@yuriibaranov/videos</a></p>"
            + "<p class=\"small\">Business Source License 1.1: free for an individual QA engineer, tester or student, for their own "
            + "learning, exploration, research and professional practice. Each version becomes AGPL-3.0-only on its Change Date: "
            + "2030-08-08. The binding terms are in LICENSE; NOTICE.md also lists the third-party components and their "
            + "licences.</p>"
            + "</body></html>";

    private final AbstractButton hintsSwitch;
    private final Consumer<String> clipboard;
    private final Function<URI, CompletableFuture<?>> browser;

    /** @param hintsSwitch the desktop's Hints button, which the window's hints follow */
    Credits(AbstractButton hintsSwitch) {
        this(hintsSwitch, Credits::systemClipboard, Credits::systemBrowser);
    }

    /**
     * @param clipboard takes the text to copy; throws IllegalStateException when the clipboard cannot be reached
     * @param browser   opens an address in the browser, off the event thread; its future fails when it cannot
     */
    Credits(AbstractButton hintsSwitch, Consumer<String> clipboard, Function<URI, CompletableFuture<?>> browser) {
        this.hintsSwitch = hintsSwitch;
        this.clipboard = clipboard;
        this.browser = browser;
    }

    /** The button; a headless desktop has it but opens nothing. */
    JButton button() {
        JButton button = Hints.of(new JButton(TITLE), HINT);
        button.addActionListener(e -> {
            if (!GraphicsEnvironment.isHeadless()) {
                open(button);
            }
        });
        return button;
    }

    private void open(Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, TITLE + " · Combinatorics Framework", Dialog.ModalityType.APPLICATION_MODAL);
        Page page = new Page(clipboard, browser, dialog::dispose);
        dialog.setContentPane(HintLayer.following(page.component(), hintsSwitch));
        dialog.getRootPane().setDefaultButton(page.close);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        // tall enough for the whole page, never taller than the screen
        var screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.min(660, screen.width), Math.min(800, screen.height));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void systemClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /** Opens the address in the system's browser, off the event thread, as the Sieve workbench opens Face 2. */
    private static CompletableFuture<?> systemBrowser(URI address) {
        return Background.run("credits-browser", () -> {
            if (!java.awt.Desktop.isDesktopSupported() || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Automatic browser opening is unavailable.");
            }
            try {
                java.awt.Desktop.getDesktop().browse(address);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return address;
        }, opened -> { }, problem -> { });
    }

    /**
     * The window's content: the page, whose YouTube link opens the channel, then Copy e-mail addresses, YouTube channel
     * and Close at the bottom.
     */
    static final class Page {
        final JEditorPane text = new JEditorPane();
        final JButton copy = new JButton("Copy e-mail addresses");
        final JButton channel = new JButton("YouTube channel");
        final JButton close = new JButton("Close");
        final JLabel status = new JLabel(" ");
        private final JPanel root = new JPanel(new BorderLayout(0, 8));
        private final Consumer<String> clipboard;
        private final Function<URI, CompletableFuture<?>> browser;

        Page(Consumer<String> clipboard, Function<URI, CompletableFuture<?>> browser, Runnable onClose) {
            this.clipboard = clipboard;
            this.browser = browser;
            text.setContentType("text/html");
            text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            text.setEditable(false);
            text.setText(PAGE);
            text.setCaretPosition(0);
            text.getAccessibleContext().setAccessibleName("Credits: the author of the Combinatorics Framework");
            JScrollPane scroll = new JScrollPane(text);
            scroll.setPreferredSize(new Dimension(600, 560));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            left.add(copy);
            left.add(channel);
            left.add(status);
            JPanel row = new JPanel(new BorderLayout());
            row.add(left, BorderLayout.WEST);
            row.add(close, BorderLayout.EAST);
            root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            root.add(scroll, BorderLayout.CENTER);
            root.add(row, BorderLayout.SOUTH);
            copy.addActionListener(e -> {
                try {
                    clipboard.accept(CONTACTS);
                    status.setText("Copied.");
                } catch (IllegalStateException busy) {
                    status.setText("The clipboard is busy: " + CONTACTS);
                }
            });
            Hints.of(text, "Who made the Combinatorics Framework, in the author's own words, with his contacts and the licence. "
                    + "The YouTube link opens his videos.");
            Hints.of(copy, "Puts the author's two e-mail addresses on the clipboard.");
            Hints.of(channel, "Opens the author's YouTube channel with his videos in your browser. Without a browser, it copies "
                    + "the link.");
            Hints.of(close, "Closes the Credits window.");
            channel.addActionListener(e -> openChannel());
            text.addHyperlinkListener(e -> {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && CHANNEL.equals(e.getDescription())) {
                    openChannel();
                }
            });
            close.addActionListener(e -> onClose.run());
        }

        /** Opens the author's channel in the browser; when none can be opened, copies the link and says so. */
        void openChannel() {
            status.setText("Opening your browser…");
            browser.apply(URI.create(CHANNEL)).whenComplete((opened, problem) -> SwingUtilities.invokeLater(() -> {
                if (problem == null) {
                    status.setText("Opened in your browser.");
                    return;
                }
                try {
                    clipboard.accept(CHANNEL);
                    status.setText("No browser could be opened; the link is copied.");
                } catch (IllegalStateException busy) {
                    status.setText("No browser could be opened: " + CHANNEL);
                }
            }));
        }

        JComponent component() {
            return root;
        }
    }
}
