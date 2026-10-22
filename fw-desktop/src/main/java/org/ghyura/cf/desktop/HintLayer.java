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

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.plaf.LayerUI;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The hints layer: an opaque pop-up drawn over the desktop next to the element the pointer rests on, saying in plain words
 * what the element is for ({@link Hints}), followed by the element's tooltip when it has one; ordinary tooltips do not pop
 * up on their own while the layer is installed, so one pop-up explains each element. It only paints, so every click and key
 * reaches the desktop as before.
 *
 * <p>How it behaves: a pop-up appears once the pointer has rested on an element for {@link #INITIAL_DELAY} ms, so moving
 * across the window shows none; leaving the element removes it at once, and resting on the next element within
 * {@link #RESHOW_WINDOW} ms of a shown pop-up brings that one after only {@link #RESHOW_DELAY} ms. Moving within the same
 * element keeps the pop-up where it is. Pressing a mouse button or a key hides it, and it stays away from that element until
 * the pointer leaves it. The pop-up stands below the element (above, or beside it, when there is no room), never over the
 * element or the pointer, always inside the window. Hiding repaints the old pop-up's rectangle and showing paints only the
 * new one, so nothing of a previous pop-up is left behind; a JLayer paints from itself whenever anything under it repaints,
 * so the pop-up stays on top of an element that changes beneath it. A button given to {@link #control} (and F1) turns
 * the hints on and off.</p>
 *
 * <p>The pop-up is only drawn, never a component, so the pointer on top of it is on the element beneath it: that element
 * receives the mouse, and its hint takes over. A move over a part that listens to nothing goes to the layer itself, which
 * listens through its event mask, so every move inside the layer reaches the hints. Ordinary tooltips are off because a tip
 * that did not fit inside the window was shown in a window of its own, which took the mouse away from the elements under
 * it.</p>
 */
final class HintLayer extends LayerUI<JComponent> {

    /** How long the pointer rests on an element before its pop-up appears. */
    static final int INITIAL_DELAY = 600;
    /** The shorter rest for the next element right after a pop-up was shown. */
    static final int RESHOW_DELAY = 150;
    /** How long after a pop-up the shorter rest applies. */
    static final int RESHOW_WINDOW = 800;
    static final Color FILL = new Color(255, 250, 214);
    static final Color EDGE = new Color(150, 120, 20);
    static final Color TEXT = new Color(24, 24, 24);
    private static final long serialVersionUID = 1L;
    private static final int MAX_WIDTH = 380;
    private static final int PAD = 8;
    private static final int GAP = 6;
    /** An element taller or much wider than this is pointed at where the pointer rests, not by its whole rectangle. */
    private static final int LARGE = 140;

    /** Runs an action after a delay on the event thread; the returned action cancels it. */
    interface Later {
        Runnable after(int millis, Runnable action);
    }

    /** What a pop-up explains: the element, the words, the rectangle to stand beside, and where the pointer rested. */
    record Target(JComponent holder, String text, Rectangle anchor, Point pointer, boolean tab) {
        Target(JComponent holder, String text, Rectangle anchor, Point pointer) {
            this(holder, text, anchor, pointer, false);
        }

        boolean same(Target other) {
            return other != null && holder == other.holder && text.equals(other.text);
        }
    }

    private final transient Later later;
    private final transient LongSupplier clock;
    private boolean on = true;
    private transient AbstractButton button;
    private transient JLayer<? extends JComponent> installedLayer;
    private transient ItemListener switchListener;
    private transient Target shown;
    private transient Target pending;
    private transient Runnable cancelPending;
    private transient JComponent quiet;
    private long reshowUntil = Long.MIN_VALUE;
    private Rectangle bubble;

    HintLayer() {
        this((millis, action) -> {
            javax.swing.Timer timer = new javax.swing.Timer(millis, e -> action.run());
            timer.setRepeats(false);
            timer.start();
            return timer::stop;
        }, System::currentTimeMillis);
    }

    /** A layer whose delays run on the given scheduler and clock (tests advance them by hand). */
    HintLayer(Later later, LongSupplier clock) {
        this.later = later;
        this.clock = clock;
    }

    /**
     * Another window's content under hints that follow the desktop's Hints button, F1 there pressing it, as the chart
     * window's are (the AI_delegation and Credits windows use it).
     */
    static JLayer<JComponent> following(JComponent content, AbstractButton toggle) {
        HintLayer hints = new HintLayer();
        hints.control(toggle);
        return new JLayer<>(content, hints);
    }

    /**
     * The button that shows whether hints are on and turns them on and off; F1 presses it. The layer follows the button's
     * state, so the layer of another window (the chart window) can follow the desktop's button.
     */
    void control(AbstractButton toggle) {
        detachSwitch();
        this.button = toggle;
        followSwitch();
    }

    private void followSwitch() {
        if (button != null && installedLayer != null) {
            switchListener = new SwitchListener(this);
            button.addItemListener(switchListener);
            showHints(installedLayer, button.isSelected());
        }
    }

    private void detachSwitch() {
        if (button != null && switchListener != null) {
            button.removeItemListener(switchListener);
        }
        switchListener = null;
    }

    /** The desktop's long-lived button must not keep disposed chart windows alive. */
    private static final class SwitchListener implements ItemListener {
        private final WeakReference<HintLayer> owner;

        SwitchListener(HintLayer owner) {
            this.owner = new WeakReference<>(owner);
        }

        @Override
        public void itemStateChanged(ItemEvent event) {
            HintLayer ui = owner.get();
            if (ui == null) {
                event.getItemSelectable().removeItemListener(this);
            } else if (ui.installedLayer != null) {
                ui.showHints(ui.installedLayer, ui.hintsShown());
            }
        }
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        @SuppressWarnings("unchecked")
        JLayer<? extends JComponent> layer = (JLayer<? extends JComponent>) c;
        installedLayer = layer;
        layer.setLayerEventMask(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        layer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F1"), "cf.hints.toggle");
        layer.getActionMap().put("cf.hints.toggle", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (button != null) {
                    button.doClick();
                    showHints(layer, button.isSelected());   // this layer too, when the button is another window's
                } else {
                    showHints(layer, !on);
                }
            }
        });
        // a tooltip shows inside the pop-up, so the ordinary tooltip window would only repeat it (and, as a window of its own,
        // would take the mouse away from the elements under it)
        ToolTipManager.sharedInstance().setEnabled(false);
        followSwitch();
    }

    @Override
    public void uninstallUI(JComponent c) {
        if (installedLayer != null) {
            dismiss(installedLayer);
            quiet = null;
        }
        detachSwitch();
        installedLayer = null;
        ((JLayer<?>) c).setLayerEventMask(0);
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).remove(KeyStroke.getKeyStroke("F1"));
        c.getActionMap().remove("cf.hints.toggle");
        super.uninstallUI(c);
    }

    /** Hints on or off; off removes the shown pop-up and forgets the one being waited for. */
    void showHints(JLayer<? extends JComponent> layer, boolean shownNow) {
        on = shownNow;
        quiet = null;
        if (!shownNow) {
            dismiss(layer);
        }
    }

    /** Whether hints are shown: as the controlling button says, else as last switched. */
    boolean hintsShown() {
        return button != null ? button.isSelected() : on;
    }

    /** What a pop-up at the point would say, or null: for tests, without waiting for the pointer to rest. */
    String hintAt(JLayer<? extends JComponent> layer, Point point) {
        Target t = target(layer, point);
        return t == null ? null : t.text();
    }

    /** The shown hint, or null. */
    String text() {
        return shown == null ? null : shown.text();
    }

    /** The hint waiting for the pointer to rest, or null. */
    String pending() {
        return pending == null ? null : pending.text();
    }

    /** Where the shown pop-up is, in the layer's coordinates, or null. */
    Rectangle bubble() {
        return bubble == null ? null : new Rectangle(bubble);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends JComponent> layer) {
        hover(layer, SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), layer));
    }

    @Override
    protected void processMouseEvent(MouseEvent e, JLayer<? extends JComponent> layer) {
        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), layer);
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            press(layer, p);
        } else if (e.getID() == MouseEvent.MOUSE_EXITED && !new Rectangle(layer.getSize()).contains(p)) {
            hover(layer, null);
        }
    }

    @Override
    protected void processKeyEvent(KeyEvent e, JLayer<? extends JComponent> layer) {
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() != KeyEvent.VK_F1) {
            key(layer);
        }
    }

    /** The pointer is at the point, in the layer's coordinates, or off the layer for null. */
    void hover(JLayer<? extends JComponent> layer, Point point) {
        Target target = hintsShown() ? target(layer, point) : null;
        if (quiet != null && (target == null || target.holder() != quiet)) {
            quiet = null;   // the pointer left the element pressed on
        }
        if (target != null && target.holder() == quiet) {
            target = null;
        }
        if (target != null && (target.same(shown) || target.same(pending))) {
            return;   // the same element: its pop-up stays, or its rest goes on
        }
        if (shown != null) {
            hide(layer);
            reshowUntil = clock.getAsLong() + RESHOW_WINDOW;
        }
        cancelPending();
        if (target == null) {
            return;
        }
        Target waiting = target;
        pending = waiting;
        cancelPending = later.after(clock.getAsLong() <= reshowUntil ? RESHOW_DELAY : INITIAL_DELAY, () -> {
            if (pending == waiting) {
                pending = null;
                cancelPending = null;
                show(layer, waiting);
            }
        });
    }

    /** Shows the waiting pop-up now, as if the pointer had rested long enough. */
    void settle(JLayer<? extends JComponent> layer) {
        if (pending != null) {
            Target waiting = pending;
            cancelPending();
            show(layer, waiting);
        }
    }

    /** A mouse button went down at the point: the pop-up goes, and stays away from that element until the pointer leaves it. */
    void press(JLayer<? extends JComponent> layer, Point point) {
        Target target = target(layer, point);
        quiet = target == null ? null : target.holder();
        dismiss(layer);
    }

    /** A key went down: the pop-up goes, and stays away from its element until the pointer leaves it. */
    void key(JLayer<? extends JComponent> layer) {
        Target current = shown != null ? shown : pending;
        quiet = current == null ? null : current.holder();
        dismiss(layer);
    }

    private void dismiss(JLayer<? extends JComponent> layer) {
        cancelPending();
        if (shown != null) {
            hide(layer);
        }
    }

    private void cancelPending() {
        if (cancelPending != null) {
            cancelPending.run();
        }
        cancelPending = null;
        pending = null;
    }

    private void show(JLayer<? extends JComponent> layer, Target target) {
        bubble = place(layer, target.anchor(), target.pointer(), measure(layer, target.text(), target.tab()));
        shown = target;
        layer.repaint(bubble);
    }

    private void hide(JLayer<? extends JComponent> layer) {
        Rectangle old = bubble;
        shown = null;
        bubble = null;
        if (old != null) {
            layer.repaint(old);
        }
    }

    /** What the pointer rests on: a tab's header, or the element whose hint (and tooltip) explains the spot; null for none. */
    private Target target(JLayer<? extends JComponent> layer, Point point) {
        Component view = layer.getView();
        if (point == null || view == null || !new Rectangle(layer.getSize()).contains(point)) {
            return null;
        }
        Point inView = SwingUtilities.convertPoint(layer, point, view);
        Component deepest = SwingUtilities.getDeepestComponentAt(view, inView.x, inView.y);
        // a tab's name says what the tab is for; a scrolling row of names lies in a strip inside the pane, not on the pane
        JTabbedPane tabs = deepest instanceof JTabbedPane t ? t : (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, deepest);
        if (tabs != null) {
            Point inTabs = SwingUtilities.convertPoint(layer, point, tabs);
            int index = tabs.indexAtLocation(inTabs.x, inTabs.y);
            if (index >= 0 && Hints.tab(tabs, index) != null && tabs.getComponentAt(index) instanceof JComponent content) {
                return new Target(content, Hints.purpose(tabs, index), SwingUtilities.convertRectangle(tabs, tabs.getBoundsAt(index), layer),
                        point, true);
            }
        }
        if (deepest == null) {
            return null;
        }
        JComponent holder = Hints.holder(deepest);
        String text = Hints.resolve(deepest);
        String tip = tooltip(deepest, SwingUtilities.convertPoint(layer, point, deepest));
        if (tip != null && !tip.equals(text)) {
            text = text == null ? tip : text + "\n" + tip;
        }
        if (holder == null && deepest instanceof JComponent j && tip != null) {
            holder = j;
        }
        if (text == null || holder == null) {
            return null;
        }
        Rectangle area = SwingUtilities.convertRectangle(holder.getParent(), holder.getBounds(), layer);
        Rectangle anchor = area.width > LARGE * 3 || area.height > LARGE ? new Rectangle(point.x - 8, point.y - 8, 16, 16) : area;
        return new Target(holder, text, anchor, point);
    }

    /** The component's tooltip at the point, in plain text, or null. */
    private static String tooltip(Component c, Point p) {
        if (!(c instanceof JComponent j)) {
            return null;
        }
        return plain(j.getToolTipText(new MouseEvent(j, MouseEvent.MOUSE_MOVED, 0L, 0, p.x, p.y, 0, false)));
    }

    /** A tooltip as plain lines: an HTML one loses its tags and breaks its lines at its line breaks. */
    static String plain(String tip) {
        if (tip == null || tip.isBlank()) {
            return null;
        }
        if (!tip.regionMatches(true, 0, "<html>", 0, 6)) {
            return tip.strip();
        }
        String s = tip.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&").strip();
    }

    private static Font font(Component layer) {
        Font f = layer.getFont();
        return (f == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : f).deriveFont(Font.PLAIN, 12f);
    }

    /** The hint's lines, wrapped at word boundaries to the pop-up's width. */
    private static List<String> lines(FontMetrics metrics, String hint) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : hint.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && metrics.stringWidth(candidate) > MAX_WIDTH) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** A tab's pop-up starts with its question in bold, so it reads apart from an element's hint. */
    private static Dimension measure(JComponent layer, String hint, boolean tab) {
        FontMetrics metrics = layer.getFontMetrics(font(layer));
        FontMetrics bold = layer.getFontMetrics(font(layer).deriveFont(Font.BOLD));
        int width = 0;
        List<String> lines = lines(metrics, hint);
        for (int i = 0; i < lines.size(); i++) {
            width = Math.max(width, (tab && i == 0 ? bold : metrics).stringWidth(lines.get(i)));
        }
        return new Dimension(width + 2 * PAD + 2, lines.size() * metrics.getHeight() + 2 * PAD + 2);
    }

    /**
     * Below the element and the pointer when the pop-up fits there, else above them, else beside them; always inside the
     * layer, so neither the element nor the pointer is covered while there is room.
     */
    static Rectangle place(JComponent layer, Rectangle anchor, Point pointer, Dimension size) {
        int lw = layer.getWidth();
        int lh = layer.getHeight();
        int w = Math.min(size.width, Math.max(1, lw - 8));
        int h = Math.min(size.height, Math.max(1, lh - 8));
        Rectangle avoid = anchor.union(new Rectangle(pointer.x - 10, pointer.y - 10, 20, 20));
        int x = Math.max(4, Math.min(anchor.x, lw - w - 4));
        int below = avoid.y + avoid.height + GAP;
        int above = avoid.y - GAP - h;
        int y;
        if (below + h <= lh - 4) {
            y = below;
        } else if (above >= 4) {
            y = above;
        } else {
            y = Math.max(4, Math.min(anchor.y, lh - h - 4));
            int right = avoid.x + avoid.width + GAP;
            x = right + w <= lw - 4 ? right : Math.max(4, avoid.x - GAP - w);
        }
        return new Rectangle(x, y, w, h);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        if (shown == null || bubble == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.clipRect(bubble.x, bubble.y, bubble.width, bubble.height);   // nothing is drawn outside the repainted rectangle
            g2.setColor(FILL);
            g2.fillRect(bubble.x, bubble.y, bubble.width, bubble.height);   // opaque: nothing under the pop-up shows through
            g2.setColor(EDGE);
            g2.drawRect(bubble.x, bubble.y, bubble.width - 1, bubble.height - 1);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = font(c);
            g2.setFont(font);
            g2.setColor(TEXT);
            FontMetrics metrics = g2.getFontMetrics();
            int line = 0;
            for (String s : lines(metrics, shown.text())) {
                g2.setFont(shown.tab() && line == 0 ? font.deriveFont(Font.BOLD) : font);
                g2.drawString(s, bubble.x + PAD + 1, bubble.y + PAD + 1 + metrics.getAscent() + line++ * metrics.getHeight());
            }
        } finally {
            g2.dispose();
        }
    }
}
