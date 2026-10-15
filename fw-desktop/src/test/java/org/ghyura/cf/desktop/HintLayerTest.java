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

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hints layer as the author asked for it: an opaque pop-up for the element the pointer rests on, a sensible change from
 * the previous element to the next, and a correct redraw of everything it covered. Time is a hand-driven clock; Swing's
 * painting is replayed headless: a repaint manager records every region asked to be repainted, and the regions are painted
 * onto a running image the way Swing paints them, from the layer (a JLayer never optimizes its drawing, so painting starts
 * there). After every step the running image must equal a fresh painting of the whole layer. Pointer moves go where the
 * windowing system sends them ({@link #move}), so the layer sees only the moves it would really hear.
 */
class HintLayerTest {

    @Test
    void oneSwitchImmediatelyDismissesShownAndPendingHintsInEveryControlledLayer() throws Exception {
        onEdt(() -> {
            Scene first = scene(), second = scene();
            var toggle = new javax.swing.JToggleButton("Hints", true);
            first.ui().control(toggle); second.ui().control(toggle);
            move(first.layer(), center(first.alpha(), first.layer())); first.ui().settle(first.layer());
            move(second.layer(), center(second.alpha(), second.layer()));
            assertNotNull(first.ui().text()); assertNotNull(second.ui().pending());
            toggle.setSelected(false);
            assertNull(first.ui().text(), "the other window must remove its painted hint without a mouse move");
            assertNull(second.ui().pending(), "the other window's timer must be cancelled too");
            first.time().advance(1000); second.time().advance(1000);
            assertNull(first.ui().text()); assertNull(second.ui().text());
            toggle.setSelected(true);
            move(second.layer(), center(second.beta(), second.layer())); second.ui().settle(second.layer());
            assertNotNull(second.ui().text());
            first.layer().setUI(null); second.layer().setUI(null);
        });
    }

    @Test
    void removingTheLayerCancelsItsPendingHintAndDetachesItsSwitch() throws Exception {
        onEdt(() -> {
            Scene s = scene(); var toggle = new javax.swing.JToggleButton("Hints", true);
            int listeners = toggle.getItemListeners().length;
            s.ui().control(toggle); move(s.layer(), center(s.alpha(), s.layer()));
            assertNotNull(s.ui().pending());
            s.layer().setUI(null); s.time().advance(1000);
            assertNull(s.ui().text(), "an uninstalled layer must not resurrect a hint from its timer");
            assertNull(s.ui().pending()); assertEquals(listeners, toggle.getItemListeners().length);
        });
    }

    static final int W = 700;
    static final int H = 420;

    /** A clock and scheduler driven by hand. */
    static final class ManualTime implements HintLayer.Later, LongSupplier {
        private final class Task {
            final long at;
            final Runnable action;
            boolean cancelled;

            Task(long at, Runnable action) {
                this.at = at;
                this.action = action;
            }
        }

        long now = 1_000_000;
        private final List<Task> tasks = new ArrayList<>();

        @Override
        public Runnable after(int millis, Runnable action) {
            Task task = new Task(now + millis, action);
            tasks.add(task);
            return () -> task.cancelled = true;
        }

        @Override
        public long getAsLong() {
            return now;
        }

        /** Lets the time pass, running every task that falls due in order. */
        void advance(long millis) {
            long end = now + millis;
            while (true) {
                Task next = null;
                for (Task t : tasks) {
                    if (!t.cancelled && t.at <= end && (next == null || t.at < next.at)) {
                        next = t;
                    }
                }
                if (next == null) {
                    break;
                }
                tasks.remove(next);
                now = next.at;
                next.action.run();
            }
            tasks.removeIf(t -> t.cancelled);
            now = end;
        }
    }

    /** Records repaint requests in the root's coordinates instead of scheduling them; nothing is double buffered. */
    static final class Recorder extends RepaintManager {
        final List<Rectangle> dirty = new ArrayList<>();
        private final JComponent root;

        Recorder(JComponent root) {
            this.root = root;
            setDoubleBufferingEnabled(false);
        }

        @Override
        public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
            dirty.add(SwingUtilities.convertRectangle(c, new Rectangle(x, y, w, h), root));
        }

        @Override
        public void addInvalidComponent(JComponent invalidComponent) {
        }
    }

    static void layout(Component c) {
        if (c instanceof Container k) {
            k.doLayout();
            for (Component child : k.getComponents()) {
                layout(child);
            }
        }
    }

    /** A fresh painting of the whole root. */
    static BufferedImage paint(JComponent root) {
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        root.paint(g);
        g.dispose();
        return image;
    }

    /** Paints every recorded region onto the running image as Swing would, then forgets the regions. */
    static void replay(Recorder recorder, JComponent root, BufferedImage screen) {
        for (Rectangle r : recorder.dirty) {
            Graphics2D g = screen.createGraphics();
            g.setClip(r);
            root.paint(g);
            g.dispose();
        }
        recorder.dirty.clear();
    }

    /** Pixels inside the area (and outside the excluded one, if any) where the two images differ. */
    static int differing(BufferedImage a, BufferedImage b, Rectangle area, Rectangle excluded) {
        int n = 0;
        Rectangle in = area.intersection(new Rectangle(a.getWidth(), a.getHeight()));
        for (int y = in.y; y < in.y + in.height; y++) {
            for (int x = in.x; x < in.x + in.width; x++) {
                if ((excluded == null || !excluded.contains(x, y)) && a.getRGB(x, y) != b.getRGB(x, y)) {
                    n++;
                }
            }
        }
        return n;
    }

    static int differing(BufferedImage a, BufferedImage b) {
        return differing(a, b, new Rectangle(a.getWidth(), a.getHeight()), null);
    }

    static Point center(Component c, Component root) {
        return SwingUtilities.convertPoint(c.getParent(), new Point(c.getX() + c.getWidth() / 2, c.getY() + c.getHeight() / 2), root);
    }

    static void onEdt(Runnable work) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() instanceof Error e) {
            throw e;
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    /** Runs the work with the recorder as the repaint manager, restoring the previous one afterwards. */
    static void recording(Recorder recorder, Runnable work) {
        RepaintManager previous = RepaintManager.currentManager((JComponent) null);
        RepaintManager.setCurrentManager(recorder);
        try {
            work.run();
        } finally {
            RepaintManager.setCurrentManager(previous);
        }
    }

    /** Whether AWT counts the element as listening to the mouse: it has mouse listeners, or mouse events are enabled on it. */
    static boolean listens(Component c) {
        long mouse = AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK;
        return c.getMouseListeners().length > 0 || c.getMouseMotionListeners().length > 0 || c.getMouseWheelListeners().length > 0
                || c instanceof JLayer<?> l && (l.getLayerEventMask() & mouse) != 0
                || c instanceof JComponent j && (j.getAutoscrolls() || j.getComponentPopupMenu() != null);
    }

    /**
     * The element a pointer move at the point (in the element's coordinates) goes to, picked the way AWT's dispatcher picks
     * it: the deepest visible element there that listens to the mouse, the topmost child first; null when none does.
     */
    static Component receiver(Component c, int x, int y) {
        if (!c.isVisible() || !c.contains(x, y)) {
            return null;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                Component deeper = receiver(child, x - child.getX(), y - child.getY());
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return listens(c) ? c : null;
    }

    /**
     * Moves the pointer to the point as the window reports it: the move goes to its receiver, and the hints hear of it only
     * when that receiver is the layer or inside it and the layer asked for moves, as the layer's event listener decides.
     * Returns the receiver, or null when the move went to the window outside the layer.
     */
    static Component move(JLayer<JComponent> layer, Point p) {
        Component to = receiver(layer, p.x, p.y);
        if (to != null && (layer.getLayerEventMask() & AWTEvent.MOUSE_MOTION_EVENT_MASK) != 0) {
            Point at = SwingUtilities.convertPoint(layer, p, to);
            layer.getUI().eventDispatched(new MouseEvent(to, MouseEvent.MOUSE_MOVED, 0L, 0, at.x, at.y, 0, false), layer);
        }
        return to;
    }

    /** The pointer leaves the window. */
    static void leave(JLayer<JComponent> layer) {
        layer.getUI().eventDispatched(new MouseEvent(layer, MouseEvent.MOUSE_EXITED, 0L, 0, -1, -1, 0, false), layer);
    }

    /** A test GUI: buttons, a field, a large text area, an element at each far edge, a list with tooltips, and a quiet label. */
    record Scene(JLayer<JComponent> layer, HintLayer ui, ManualTime time, JPanel panel, JButton alpha, JButton beta, JTextField field,
                 JTextArea large, JButton corner, JButton edge, JList<String> list, JLabel quiet, List<JComponent> hinted) {
    }

    static Scene scene() {
        JPanel panel = new JPanel(null);
        panel.setBackground(Color.WHITE);
        JButton alpha = Hints.of(new JButton("Alpha"), "The first element, with a short hint.");
        JButton beta = Hints.of(new JButton("Beta"), "The second element, with a longer hint that wraps over more than one line because it "
                + "says more about what the element does and when to use it.");
        JTextField field = Hints.of(new JTextField(), "A field. Filled by you: type something.");
        JTextArea large = Hints.of(new JTextArea(), "A large area: its pop-up stands by the pointer. Filled after a run.");
        JButton corner = Hints.of(new JButton("Corner"), "An element in the bottom right corner.");
        JButton edge = Hints.of(new JButton("Edge"), "An element at the right edge.");
        JList<String> list = Hints.of(new JList<>(new String[]{"first", "second", "third"}), "A list whose items have their own tooltips.");
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                Component c = super.getListCellRendererComponent(l, value, index, selected, focus);
                setToolTipText("<html><b>" + value + "</b> item<br>number " + (index + 1) + "</html>");
                return c;
            }
        });
        JLabel quiet = new JLabel("an element without a hint");
        alpha.setBounds(20, 20, 120, 30);
        beta.setBounds(300, 150, 140, 30);
        field.setBounds(160, 20, 120, 26);
        JScrollPane largePane = new JScrollPane(large);
        largePane.setBounds(20, 200, 420, 170);
        corner.setBounds(W - 110, H - 34, 100, 28);
        edge.setBounds(W - 90, 100, 84, 28);
        JScrollPane listPane = new JScrollPane(list);
        listPane.setBounds(480, 180, 110, 80);
        quiet.setBounds(20, 385, 200, 20);
        for (Component c : List.of(alpha, beta, field, largePane, corner, edge, listPane, quiet)) {
            panel.add(c);
        }
        ManualTime time = new ManualTime();
        HintLayer ui = new HintLayer(time, time);
        JLayer<JComponent> layer = new JLayer<>(panel, ui);
        layer.setSize(W, H);
        layout(layer);
        return new Scene(layer, ui, time, panel, alpha, beta, field, large, corner, edge, list, quiet, List.of(alpha, beta, field, large, corner, edge, list));
    }

    /** One scene, its recorder and a running screen; every step is checked against a fresh painting. */
    static final class Run {
        final Scene s = scene();
        final Recorder recorder = new Recorder(s.layer());
        BufferedImage screen;
        BufferedImage blank;

        void start() {
            blank = paint(s.layer());
            screen = paint(s.layer());
        }

        void hover(Component c) {
            assertNotNull(move(s.layer(), center(c, s.layer())), "the move is heard");
            check("after moving onto " + c.getClass().getSimpleName());
        }

        /** Moves the pointer to the point, or out of the window for null. */
        void hover(Point p) {
            if (p == null) {
                leave(s.layer());
            } else {
                assertNotNull(move(s.layer(), p), "a move inside the window is heard: " + p);
            }
            check("after moving to " + p);
        }

        /** Moves the pointer to the point and says where the move went. */
        Component moveTo(Point p) {
            Component to = move(s.layer(), p);
            check("after moving to " + p);
            return to;
        }

        void rest(long millis) {
            s.time().advance(millis);
            check("after resting " + millis + " ms");
        }

        void check(String when) {
            replay(recorder, s.layer(), screen);
            assertEquals(0, differing(screen, paint(s.layer())), "the GUI as a fresh painting would show it, " + when);
        }
    }

    @Test
    void aPopUpAppearsOnlyAfterThePointerRestsOnAnElement() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            recording(r.recorder, () -> {
                r.start();
                r.hover(r.s.alpha());
                assertNull(r.s.ui().text(), "no pop-up while the pointer has only arrived");
                assertEquals("The first element, with a short hint.", r.s.ui().pending());
                r.rest(HintLayer.INITIAL_DELAY - 1);
                assertNull(r.s.ui().text());
                r.rest(1);
                assertEquals("The first element, with a short hint.", r.s.ui().text());
                assertTrue(differing(r.blank, r.screen, r.s.ui().bubble(), null) > 200, "the pop-up shows");
            });
        });
    }

    @Test
    void crossingTheWindowQuicklyShowsNoPopUpAtAll() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            recording(r.recorder, () -> {
                r.start();
                for (JComponent c : List.of(r.s.alpha(), r.s.field(), r.s.beta(), r.s.edge(), r.s.alpha())) {
                    r.hover(c);
                    r.rest(HintLayer.INITIAL_DELAY / 3);
                }
                assertNull(r.s.ui().text(), "passing over elements flashes nothing");
                assertEquals(0, differing(r.screen, r.blank), "nothing was drawn");
                r.rest(HintLayer.INITIAL_DELAY);
                assertEquals("The first element, with a short hint.", r.s.ui().text(), "resting on the last one shows its hint");
            });
        });
    }

    @Test
    void leavingRemovesThePopUpAtOnceAndTheNextElementsComesQuickly() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            recording(r.recorder, () -> {
                r.start();
                r.hover(r.s.alpha());
                r.rest(HintLayer.INITIAL_DELAY);
                Rectangle first = r.s.ui().bubble();
                r.hover(r.s.beta());
                assertNull(r.s.ui().text(), "the previous pop-up goes as soon as the pointer leaves its element");
                assertEquals(0, differing(r.blank, r.screen, first, null), "and its area shows the GUI again at once");
                r.rest(HintLayer.RESHOW_DELAY - 1);
                assertNull(r.s.ui().text());
                r.rest(1);
                assertTrue(r.s.ui().text().startsWith("The second element"), "the next one follows quickly");
                r.hover(r.s.quiet());
                assertNull(r.s.ui().text());
                assertEquals(0, differing(r.screen, r.blank), "an element without a hint: the GUI is as it was");
                r.rest(HintLayer.RESHOW_WINDOW + 50);
                r.hover(r.s.alpha());
                r.rest(HintLayer.RESHOW_DELAY);
                assertNull(r.s.ui().text(), "long after the last pop-up, the full rest applies again");
                r.rest(HintLayer.INITIAL_DELAY - HintLayer.RESHOW_DELAY);
                assertNotNull(r.s.ui().text());
                r.hover((Point) null);
                assertEquals(0, differing(r.screen, r.blank), "leaving the window removes the pop-up");
            });
        });
    }

    @Test
    void movingWithinTheSameElementKeepsItsPopUpStill() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            recording(r.recorder, () -> {
                r.start();
                r.hover(r.s.beta());
                r.rest(HintLayer.INITIAL_DELAY);
                Rectangle bubble = r.s.ui().bubble();
                Point c = center(r.s.beta(), r.s.layer());
                for (int dx : new int[]{-60, -20, 25, 60}) {
                    move(r.s.layer(), new Point(c.x + dx, c.y + 6));
                    assertEquals(List.of(), r.recorder.dirty, "no repaint while the pointer stays on the element");
                    assertEquals(bubble, r.s.ui().bubble());
                }
            });
        });
    }

    @Test
    void aClickOrAKeyHidesThePopUpUntilThePointerLeavesTheElement() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            recording(r.recorder, () -> {
                r.start();
                r.hover(r.s.alpha());
                r.rest(HintLayer.INITIAL_DELAY);
                r.s.ui().press(r.s.layer(), center(r.s.alpha(), r.s.layer()));
                r.check("after a click");
                assertNull(r.s.ui().text(), "a click hides the pop-up");
                assertEquals(0, differing(r.screen, r.blank));
                Point c = center(r.s.alpha(), r.s.layer());
                r.hover(new Point(c.x + 10, c.y));
                r.rest(5_000);
                assertNull(r.s.ui().text(), "it stays away while the pointer stays on that element");
                r.hover(r.s.field());
                r.rest(HintLayer.INITIAL_DELAY);
                assertTrue(r.s.ui().text().startsWith("A field."), "another element shows its hint again");
                r.s.ui().key(r.s.layer());
                r.check("after a key");
                assertNull(r.s.ui().text(), "typing hides the pop-up");
                r.rest(5_000);
                assertNull(r.s.ui().text());
                r.hover(r.s.alpha());
                r.rest(HintLayer.INITIAL_DELAY);
                assertNotNull(r.s.ui().text(), "after leaving, hints come back");
            });
        });
    }

    @Test
    void turningHintsOffRemovesTheShownPopUpAndShowsNoneUntilTheyAreOn() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            recording(r.recorder, () -> {
                r.start();
                r.hover(r.s.beta());
                r.rest(HintLayer.INITIAL_DELAY);
                r.s.ui().showHints(r.s.layer(), false);
                r.check("after turning hints off");
                assertEquals(0, differing(r.screen, r.blank));
                r.hover(r.s.alpha());
                r.rest(5_000);
                assertNull(r.s.ui().text(), "no pop-up while hints are off");
                r.s.ui().showHints(r.s.layer(), true);
                r.hover(r.s.field());
                r.rest(HintLayer.INITIAL_DELAY);
                assertNotNull(r.s.ui().text(), "hints are back");
            });
        });
    }

    @Test
    void thePopUpIsOpaque() throws Exception {
        onEdt(() -> {
            Scene s = scene();
            Recorder recorder = new Recorder(s.layer());
            recording(recorder, () -> {
                move(s.layer(), center(s.alpha(), s.layer()));
                s.time().advance(HintLayer.INITIAL_DELAY);
                Rectangle b = s.ui().bubble();
                BufferedImage overWhite = paint(s.layer());
                s.panel().setBackground(Color.BLACK);
                BufferedImage overBlack = paint(s.layer());
                assertTrue(differing(overWhite, overBlack, new Rectangle(0, 0, W, H), b) > 1000, "the GUI around the pop-up did change");
                assertEquals(0, differing(overWhite, overBlack, b, null), "nothing under the pop-up shows through it");
            });
        });
    }

    @Test
    void thePopUpNeverCoversItsElementOrThePointerAndStaysInsideTheWindow() throws Exception {
        onEdt(() -> {
            Scene s = scene();
            recording(new Recorder(s.layer()), () -> {
                for (JComponent c : s.hinted()) {
                    Component target = c instanceof JTextArea || c instanceof JList ? SwingUtilities.getAncestorOfClass(JScrollPane.class, c) : c;
                    Point p = center(target, s.layer());
                    leave(s.layer());
                    s.time().advance(HintLayer.RESHOW_WINDOW + 1);
                    move(s.layer(), p);
                    s.time().advance(HintLayer.INITIAL_DELAY);
                    Rectangle b = s.ui().bubble();
                    assertNotNull(b, "a pop-up for " + c);
                    assertTrue(new Rectangle(0, 0, W, H).contains(b), "inside the window: " + b);
                    assertFalse(b.intersects(new Rectangle(p.x - 10, p.y - 10, 20, 20)), "never under the pointer: " + c);
                    Rectangle area = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), s.layer());
                    if (area.height <= 140 && area.width <= 420) {
                        assertFalse(b.intersects(area), "never over the element it explains: " + c);
                    }
                }
            });
        });
    }

    @Test
    void anElementsTooltipShowsInsideItsPopUpAndEachItemGetsItsOwn() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            recording(r.recorder, () -> {
                r.start();
                Rectangle first = r.s.list().getCellBounds(0, 0);
                Rectangle second = r.s.list().getCellBounds(1, 1);
                r.hover(SwingUtilities.convertPoint(r.s.list(), new Point(first.x + 10, first.y + first.height / 2), r.s.layer()));
                r.rest(HintLayer.INITIAL_DELAY);
                assertEquals("A list whose items have their own tooltips.\nfirst item\nnumber 1", r.s.ui().text(), "the hint, then the tooltip as plain lines");
                r.hover(SwingUtilities.convertPoint(r.s.list(), new Point(second.x + 10, second.y + second.height / 2), r.s.layer()));
                assertNull(r.s.ui().text(), "another item: the old pop-up goes");
                r.rest(HintLayer.RESHOW_DELAY);
                assertTrue(r.s.ui().text().endsWith("second item\nnumber 2"), r.s.ui().text());
            });
        });
    }

    @Test
    void anElementThatRepaintsUnderAPopUpLeavesThePopUpOnTop() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            JLabel counter = new JLabel("0 test cases");
            counter.setBounds(20, 56, 220, 24);   // where Alpha's pop-up opens, just below Alpha
            r.s.panel().add(counter);
            layout(r.s.layer());
            recording(r.recorder, () -> {
                r.start();
                r.hover(r.s.alpha());
                r.rest(HintLayer.INITIAL_DELAY);
                assertTrue(r.s.ui().bubble().intersects(counter.getBounds()), "the pop-up covers the counter");
                counter.setText("12 345 test cases");
                assertFalse(r.recorder.dirty.isEmpty(), "the counter asks to be repainted");
                r.check("after the covered element changed");
            });
        });
    }

    /**
     * The author's report: with the pointer on a pop-up, the element under the pop-up must take over. The pop-up is only
     * drawn, so a move onto it goes to the element beneath it, or to the layer itself where that part listens to nothing.
     */
    @Test
    void aMoveOntoThePopUpGoesToTheElementUnderItAndItsHintTakesOver() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            JButton gamma = Hints.of(new JButton("Gamma"), "An element that Alpha's pop-up covers.");
            gamma.setBounds(24, 62, 90, 22);   // where Alpha's pop-up opens, just below Alpha
            JLabel plain = new JLabel("plain words");
            plain.setBounds(130, 62, 90, 22);
            r.s.panel().add(gamma);
            r.s.panel().add(plain);
            layout(r.s.layer());
            recording(r.recorder, () -> {
                r.start();
                assertEquals(r.s.alpha(), r.moveTo(center(r.s.alpha(), r.s.layer())), "a move onto Alpha goes to Alpha");
                r.rest(HintLayer.INITIAL_DELAY);
                assertEquals("The first element, with a short hint.", r.s.ui().text());
                Rectangle bubble = r.s.ui().bubble();
                Point onGamma = center(gamma, r.s.layer());
                Point onPlain = center(plain, r.s.layer());
                assertTrue(bubble.contains(onGamma) && bubble.contains(onPlain), "Alpha's pop-up covers Gamma and the label: " + bubble);
                assertEquals(gamma, r.moveTo(onGamma), "the pointer on the pop-up is on Gamma, and the move goes to Gamma");
                assertNull(r.s.ui().text(), "Alpha's pop-up goes at once");
                assertEquals(0, differing(r.screen, r.blank), "and the GUI shows again where it was");
                r.rest(HintLayer.RESHOW_DELAY);
                assertEquals("An element that Alpha's pop-up covers.", r.s.ui().text(), "Gamma's hint takes over");
                assertFalse(r.s.ui().bubble().intersects(SwingUtilities.convertRectangle(r.s.panel(), gamma.getBounds(), r.s.layer())),
                        "and Gamma's pop-up leaves Gamma in view");
                r.moveTo(center(r.s.alpha(), r.s.layer()));
                r.rest(HintLayer.RESHOW_DELAY);
                assertEquals(bubble, r.s.ui().bubble(), "Alpha's pop-up again, in the same place");
                assertEquals(r.s.layer(), r.moveTo(onPlain), "a label listens to nothing, so the move goes to the layer itself");
                assertNull(r.s.ui().text(), "the hints heard it: Alpha's pop-up goes");
                assertNull(r.s.ui().pending(), "and the label has no hint to wait for");
                assertEquals(0, differing(r.screen, r.blank), "the GUI is as it was");
                r.rest(5_000);
                assertNull(r.s.ui().text());
            });
        });
    }

    /** A seeded walk of moves, rests, clicks, keys, hint switches and changing elements; every step must leave a correct GUI. */
    @Test
    void anyWalkOfThePointerLeavesTheGuiExactlyAsAFreshPaintingWould() throws Exception {
        onEdt(() -> {
            Run r = new Run();
            JLabel counter = new JLabel("0");
            counter.setBounds(160, 56, 200, 22);
            r.s.panel().add(counter);
            layout(r.s.layer());
            Random random = new Random(20261011L);
            recording(r.recorder, () -> {
                r.start();
                int shown = 0;
                for (int step = 0; step < 1200; step++) {
                    int what = random.nextInt(100);
                    if (what < 40) {
                        JComponent c = r.s.hinted().get(random.nextInt(r.s.hinted().size()));
                        Component target = c instanceof JTextArea || c instanceof JList ? SwingUtilities.getAncestorOfClass(JScrollPane.class, c) : c;
                        Point p = center(target, r.s.layer());
                        r.hover(new Point(p.x + random.nextInt(9) - 4, p.y + random.nextInt(9) - 4));
                    } else if (what < 48) {
                        r.hover(new Point(random.nextInt(W), random.nextInt(H)));
                    } else if (what < 82) {
                        r.rest(random.nextInt(900));
                    } else if (what < 87) {
                        r.s.ui().press(r.s.layer(), new Point(random.nextInt(W), random.nextInt(H)));
                        r.check("after a click");
                    } else if (what < 90) {
                        r.s.ui().key(r.s.layer());
                        r.check("after a key");
                    } else if (what < 91) {
                        r.s.ui().showHints(r.s.layer(), false);
                        r.check("after switching hints off");
                        r.hover(r.s.alpha());
                        r.rest(random.nextInt(900));
                        r.s.ui().showHints(r.s.layer(), true);
                        r.check("after switching hints on");
                    } else if (what < 97) {
                        counter.setText(Integer.toString(step));
                        r.check("after an element changed");
                    } else {
                        r.hover((Point) null);
                    }
                    if (r.s.ui().text() != null) {
                        shown++;
                        assertTrue(new Rectangle(0, 0, W, H).contains(r.s.ui().bubble()));
                    }
                }
                assertTrue(shown > 150, "the walk showed pop-ups often enough to mean something: " + shown);
            });
        });
    }
}
