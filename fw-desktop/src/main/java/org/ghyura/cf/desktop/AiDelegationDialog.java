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
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The AI_delegation button beside Hints and what it opens. The first window reads like a licence: the rules an AI assistant
 * follows and the agreement it asks the human to accept, from the AI's manual; at the bottom, the human's two statements of
 * respect (OK waits until the end of the agreement has been reached and both are ticked), Remember my choice and do not
 * show this further, and OK. After OK the manual's absolute path is on the clipboard, written as the operating system in
 * use writes it, and a second window
 * says so, shows the path and offers its other forms (a file URI, forward slashes). Once the choice is remembered (and the
 * agreement has not changed since), a press copies the path at once and only a notice says so; the notice fades away by
 * itself after ten seconds, as if its OK were pressed, and the path stays on the clipboard. Every element of these
 * windows explains itself in a hint, shown while the desktop's Hints button is on (F1 there presses it). The windows'
 * contents are built apart from the windows, so tests build them headless.
 */
final class AiDelegationDialog {

    static final String TITLE = "AI_delegation";
    static final String HINT = "Hands work to an AI assistant that you direct: shows the agreement it works under, then puts the "
            + "path of its manual on the clipboard, ready to paste into its chat.";
    static final String REMEMBER = "Remember my choice and do not show this further";
    /** How long the notice stays, its fading included. */
    static final int NOTICE_MILLIS = 10_000;
    /** The last part of it, in which the notice fades away. */
    static final int FADE_MILLIS = 3_000;

    private final AiDelegation delegation;
    private final AbstractButton hintsSwitch;
    private final Consumer<String> clipboard;

    /** @param hintsSwitch the desktop's Hints button, which the windows' hints follow */
    AiDelegationDialog(AiDelegation delegation, AbstractButton hintsSwitch) {
        this(delegation, hintsSwitch, AiDelegationDialog::systemClipboard);
    }

    /** @param clipboard takes the text to copy; throws IllegalStateException when the clipboard cannot be reached */
    AiDelegationDialog(AiDelegation delegation, AbstractButton hintsSwitch, Consumer<String> clipboard) {
        this.delegation = delegation;
        this.hintsSwitch = hintsSwitch;
        this.clipboard = clipboard;
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
        Path manual = delegation.manual();
        if (manual == null) {
            JOptionPane.showMessageDialog(owner, delegation.missing(), TITLE, JOptionPane.WARNING_MESSAGE);
            return;
        }
        String text;
        try {
            text = Files.readString(manual, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(owner, "Could not read " + manual + ":\n" + e.getMessage(), TITLE, JOptionPane.WARNING_MESSAGE);
            return;
        }
        String agreement = AiDelegation.agreement(text);
        if (delegation.mustAsk(agreement)) {
            JDialog dialog = new JDialog(owner, TITLE + " · agreement", Dialog.ModalityType.APPLICATION_MODAL);
            AtomicBoolean accepted = new AtomicBoolean();
            Agreement panel = new Agreement(agreement, manual, remember -> {
                delegation.chose(agreement, remember);
                accepted.set(true);
                dialog.dispose();
            });
            show(dialog, HintLayer.following(panel.component(), hintsSwitch), panel.ok, owner, new Dimension(880, 760));
            if (!accepted.get()) {
                return;   // closed without OK: nothing copied, nothing remembered
            }
        }
        if (!delegation.mustAsk(agreement)) {
            String copied = copied(manual);
            if (copied != null) {
                notice(owner, parent, copied);
                return;
            }
        }
        JDialog dialog = new JDialog(owner, TITLE + " · ready to paste", Dialog.ModalityType.APPLICATION_MODAL);
        Ready ready = new Ready(delegation, manual, clipboard, dialog::dispose);
        show(dialog, HintLayer.following(ready.component(), hintsSwitch), ready.ok, owner, null);
    }

    /** The path in the form chosen last, put on the clipboard; null when the clipboard cannot be reached. */
    private String copied(Path manual) {
        Map<AiDelegation.PathFormat, String> forms = AiDelegation.forms(manual);
        String text = forms.getOrDefault(delegation.format(), forms.get(AiDelegation.PathFormat.NATIVE));
        try {
            clipboard.accept(text);
            return text;
        } catch (IllegalStateException e) {
            return null;   // the window that shows the path lets the human copy it
        }
    }

    /** The notice under the button: it stays while the pointer rests on it, fades in its last seconds, then presses its OK. */
    private void notice(Window owner, Component near, String copied) {
        JDialog window = new JDialog(owner, TITLE, Dialog.ModalityType.MODELESS);
        window.setUndecorated(true);
        window.setAutoRequestFocus(false);
        Timer timer = new Timer(40, null);
        Notice notice = new Notice(delegation, copied, () -> {
            timer.stop();
            window.dispose();
        });
        window.setContentPane(HintLayer.following(notice.component(), hintsSwitch));
        window.pack();
        Rectangle screen = near.getGraphicsConfiguration().getBounds();
        var at = near.getLocationOnScreen();
        int x = Math.max(screen.x, Math.min(at.x + near.getWidth() - window.getWidth(), screen.x + screen.width - window.getWidth()));
        window.setLocation(x, Math.min(at.y + near.getHeight() + 4, screen.y + screen.height - window.getHeight()));
        boolean fades = near.getGraphicsConfiguration().getDevice()
                .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
        AtomicLong start = new AtomicLong(System.nanoTime());
        timer.addActionListener(e -> {
            PointerInfo pointer = MouseInfo.getPointerInfo();
            if (pointer != null && window.getBounds().contains(pointer.getLocation())) {
                start.set(System.nanoTime());   // being read: the ten seconds start again when the pointer leaves
            }
            float opacity = opacity((System.nanoTime() - start.get()) / 1_000_000);
            if (opacity <= 0f) {
                notice.ok.doClick(0);
            } else if (fades) {
                try {
                    window.setOpacity(opacity);
                } catch (RuntimeException unsupported) {
                    // shown without fading
                }
            }
        });
        window.setVisible(true);
        timer.start();
    }

    /** The notice's opacity so many milliseconds after it appeared: whole, then fading to nothing at {@link #NOTICE_MILLIS}. */
    static float opacity(long elapsedMillis) {
        if (elapsedMillis <= NOTICE_MILLIS - FADE_MILLIS) {
            return 1f;
        }
        if (elapsedMillis >= NOTICE_MILLIS) {
            return 0f;
        }
        return (NOTICE_MILLIS - elapsedMillis) / (float) FADE_MILLIS;
    }

    private static void show(JDialog dialog, JComponent content, JButton ok, Window owner, Dimension size) {
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (size == null) {
            dialog.pack();
        } else {
            dialog.setSize(size);
        }
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void systemClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /** Text that wraps at the width of the window's other parts. */
    private static JLabel wrapped(String html) {
        return new JLabel("<html><div style=\"width: 640px\">" + html + "</div></html>");
    }

    /** A box whose text wraps as the window's other text does; a screen reader reads the text itself. */
    private static JCheckBox box(String text) {
        JCheckBox box = new JCheckBox("<html><div style=\"width: 620px\">" + MarkdownLite.escape(text) + "</div></html>");
        box.getAccessibleContext().setAccessibleName(text);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    /**
     * The licence-like window: what delegation means, the agreement, then the human's statements of respect, Remember my
     * choice and do not show this further, and OK, which waits until the agreement's end has been reached and both
     * statements are ticked.
     */
    static final class Agreement {
        final JEditorPane text = new JEditorPane();
        final JScrollPane scroll = new JScrollPane(text);
        /** The statements of respect, in the order of {@link AiDelegation#PLEDGES}. */
        final List<JCheckBox> pledges = AiDelegation.PLEDGES.stream().map(AiDelegationDialog::box).toList();
        final JCheckBox remember = box(REMEMBER);
        final JButton ok = new JButton("OK");
        private final JPanel root = new JPanel(new BorderLayout(0, 8));
        private boolean reachedEnd;
        private boolean readCheckPending;

        /** @param accepted told at OK whether to remember the choice */
        Agreement(String agreement, Path manual, Consumer<Boolean> accepted) {
            JPanel top = new JPanel(new BorderLayout(0, 4));
            top.add(wrapped("<b>" + MarkdownLite.escape(AiDelegation.INTRO) + "</b>"), BorderLayout.NORTH);
            top.add(wrapped("From " + MarkdownLite.escape(manual.toString())), BorderLayout.SOUTH);
            text.setContentType("text/html");
            text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            text.setEditable(false);
            text.setText("<html><head><style>body { margin: 8px; } h2, h3 { margin-top: 10px; margin-bottom: 4px; } "
                    + "blockquote { margin: 6px 0 6px 12px; padding: 6px; background-color: #eef3f9; } "
                    + "li { margin-bottom: 3px; }</style></head><body>" + MarkdownLite.html(agreement) + "</body></html>");
            text.setCaretPosition(0);
            text.getAccessibleContext().setAccessibleName("The delegation agreement");
            scroll.setPreferredSize(new Dimension(820, 420));
            JPanel bottom = new JPanel();
            bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
            JLabel consent = wrapped("By pressing OK you confirm that you are a real human who directs this work and that you have "
                    + "read the agreement above. Reach the bottom of the agreement and tick both statements below to enable OK. "
                    + "Your AI assistant will still show the agreement to you and ask for your answers before it runs anything. Closing this window "
                    + "copies nothing.");
            consent.setAlignmentX(Component.LEFT_ALIGNMENT);
            bottom.add(consent);
            pledges.forEach(bottom::add);
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(remember, BorderLayout.CENTER);
            row.add(ok, BorderLayout.EAST);
            bottom.add(row);
            root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            root.add(top, BorderLayout.NORTH);
            root.add(scroll, BorderLayout.CENTER);
            root.add(bottom, BorderLayout.SOUTH);
            Hints.of(text, "The rules your AI assistant follows and the agreement it will ask you to accept, taken from its "
                    + "manual, AI_DELEGATION.md. Read to the bottom before accepting.");
            Hints.of(pledges.get(0), "Tick to state that you respect human work and jobs. OK waits until you reach the bottom "
                    + "of the agreement and both statements are ticked.");
            Hints.of(pledges.get(1), "Tick to state that you respect the author's long, hand-made work on this Framework. OK can "
                    + "be pressed after you reach the bottom of the agreement and while both statements are ticked.");
            Hints.of(remember, "Tick to skip this window from now on: the button will copy the path at once and show a short "
                    + "notice instead.");
            Hints.of(ok, "Accepts the agreement and puts the path of the AI's manual on the clipboard, ready to paste into its "
                    + "chat. It waits until you reach the bottom of the agreement and both statements are ticked.");
            ok.setEnabled(false);
            pledges.forEach(p -> p.addItemListener(e -> updateOk()));
            scroll.getViewport().addChangeListener(e -> scheduleReadCheck());
            scheduleReadCheck();
            ok.addActionListener(e -> {
                if (ok.isEnabled()) {
                    accepted.accept(remember.isSelected());
                }
            });
        }

        private void updateOk() {
            ok.setEnabled(reachedEnd && pledges.stream().allMatch(JCheckBox::isSelected));
        }

        private void scheduleReadCheck() {
            if (readCheckPending) return;
            readCheckPending = true;
            // Check after layout settles: an unlaid-out viewport must not count as reading the whole agreement.
            SwingUtilities.invokeLater(() -> {
                readCheckPending = false;
                Rectangle visible = scroll.getViewport().getViewRect();
                if (visible.height > 0 && text.getWidth() > 0 && text.getHeight() > 0
                        && visible.y + visible.height >= text.getHeight()) {
                    reachedEnd = true;
                }
                updateOk();
            });
        }

        JComponent component() {
            return root;
        }
    }

    /**
     * The window after OK while the choice is not remembered: the path is on the clipboard, in the form chosen last (the
     * operating system's own at first); another form copies at once and Copy again copies once more.
     */
    static final class Ready {
        final JTextField path = new JTextField();
        final JComboBox<AiDelegation.PathFormat> form;
        final JButton copy = new JButton("Copy again");
        final JButton ok = new JButton("OK");
        final JLabel status = new JLabel(" ");
        private final Map<AiDelegation.PathFormat, String> forms;
        private final Consumer<String> clipboard;
        private final JPanel root = new JPanel(new BorderLayout(0, 8));

        /** Copies the path at once. */
        Ready(AiDelegation delegation, Path manual, Consumer<String> clipboard, Runnable close) {
            this.clipboard = clipboard;
            forms = AiDelegation.forms(manual);
            form = new JComboBox<>(forms.keySet().toArray(AiDelegation.PathFormat[]::new));
            form.setSelectedItem(forms.containsKey(delegation.format()) ? delegation.format() : AiDelegation.PathFormat.NATIVE);
            form.getAccessibleContext().setAccessibleName("Form of the path");
            path.setEditable(false);
            path.setColumns(60);
            path.getAccessibleContext().setAccessibleName("Path of the AI's manual");
            JPanel middle = new JPanel(new BorderLayout(0, 6));
            middle.add(path, BorderLayout.NORTH);
            JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            choices.add(new JLabel("Form:"));
            choices.add(form);
            choices.add(copy);
            choices.add(status);
            middle.add(choices, BorderLayout.CENTER);
            middle.add(wrapped("Paste it into your AI assistant's chat with a request such as “Read this file and follow it; I am "
                    + "the human who directs this work.” The assistant will show you the agreement again and ask for your "
                    + "answers before it runs anything."), BorderLayout.SOUTH);
            JPanel row = new JPanel(new BorderLayout());
            row.add(ok, BorderLayout.EAST);
            root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            root.add(wrapped("<b>The path of the AI's manual is on the clipboard, ready to paste.</b>"), BorderLayout.NORTH);
            root.add(middle, BorderLayout.CENTER);
            root.add(row, BorderLayout.SOUTH);
            Hints.of(path, "The absolute path of the AI's manual, written in the form chosen below. It is already on the "
                    + "clipboard.");
            Hints.of(form, "How the path is written: as your operating system writes it, as a file address (file:///…), or "
                    + "with forward slashes. Choosing a form copies it.");
            Hints.of(copy, "Puts the path on the clipboard once more, in the form chosen beside it.");
            Hints.of(ok, "Closes this window. The path stays on the clipboard.");
            form.addActionListener(e -> {
                delegation.format(selected());
                copy();
            });
            copy.addActionListener(e -> copy());
            ok.addActionListener(e -> close.run());
            copy();
        }

        private AiDelegation.PathFormat selected() {
            return (AiDelegation.PathFormat) form.getSelectedItem();
        }

        /** Puts the path in the chosen form on the clipboard and says whether that worked. */
        private void copy() {
            String text = forms.get(selected());
            path.setText(text);
            path.setCaretPosition(0);
            try {
                clipboard.accept(text);
                status.setText("Copied.");
            } catch (IllegalStateException e) {
                path.selectAll();
                status.setText("The clipboard is busy: select the path and copy it yourself.");
            }
        }

        JComponent component() {
            return root;
        }
    }

    /**
     * The notice of a remembered choice: the path is already on the clipboard for the AI assistant to learn how to use the
     * framework and what for. Its OK closes it (the fading presses it), and Ask me again next time forgets the choice.
     */
    static final class Notice {
        final JLabel message;
        final JCheckBox askAgain = new JCheckBox("Ask me again next time");
        final JButton ok = new JButton("OK");
        private final JPanel root = new JPanel(new BorderLayout(0, 6));

        Notice(AiDelegation delegation, String copied, Runnable close) {
            message = new JLabel("<html><div style=\"width: 420px\"><b>The path to " + AiDelegation.MANUAL + " is copied to the "
                    + "clipboard.</b><br>Paste it to your AI assistant: the file tells it how to use the Combinatorics Framework "
                    + "and what for.<br><br><code>" + MarkdownLite.escape(copied) + "</code><br><br>This notice closes by itself "
                    + "in " + NOTICE_MILLIS / 1_000 + " seconds; the path stays on the clipboard.</div></html>");
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            askAgain.setOpaque(false);
            row.add(askAgain, BorderLayout.WEST);
            row.add(ok, BorderLayout.EAST);
            Color edge = UIManager.getColor("Component.borderColor");
            root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(edge == null ? Color.GRAY : edge),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)));
            root.add(message, BorderLayout.CENTER);
            root.add(row, BorderLayout.SOUTH);
            Hints.of(askAgain, "Tick to see the agreement window again the next time you press AI_delegation.");
            Hints.of(ok, "Closes this notice now. It also closes by itself after ten seconds, and the path stays on the "
                    + "clipboard.");
            askAgain.addActionListener(e -> {
                if (askAgain.isSelected()) {
                    delegation.forget();
                } else {
                    delegation.remember();
                }
            });
            ok.addActionListener(e -> close.run());
        }

        JComponent component() {
            return root;
        }
    }
}
