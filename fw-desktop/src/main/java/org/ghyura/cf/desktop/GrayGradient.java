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

import com.formdev.flatlaf.ui.FlatEditorPaneUI;
import com.formdev.flatlaf.ui.FlatTableUI;
import com.formdev.flatlaf.ui.FlatTextAreaUI;
import com.formdev.flatlaf.ui.FlatTextPaneUI;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Map;

/**
 * The author's request for guif: every output shows on a grayscale gradient, light at the top of what is visible and
 * darker at its bottom. Outputs are the read-only texts (each step's output, the code preview, details, problem lists)
 * and the tables none of whose cells can be edited. The authoring sheet grid also opts in, with row and column stripes;
 * other inputs and anything whose code chose its own background keep the plain background. The gradient belongs to the visible area of the
 * enclosing scroll pane, not to the content, so it stays in place while the content scrolls; a table's odd rows keep
 * their tint, its even rows and the empty space below its last row show the gradient. A table's gradient ends lighter
 * ({@link #TABLE_BOTTOM}) than a text's, so its even rows stay lighter than the tinted odd rows at every height and the
 * stripes never fade into each other. The FlatLaf desktop maps its text area, text pane, editor pane and table UIs to
 * the delegates here; the Swing desktop is left as it is.
 */
public final class GrayGradient {

    /** The colour at the top of the visible area. */
    static final Color TOP = new Color(0xFB, 0xFB, 0xFB);
    /** The colour at its bottom. */
    static final Color BOTTOM = new Color(0xDF, 0xDF, 0xDF);
    /** The colour at the bottom of a table, lighter than the odd rows' tint ({@link FlatDesktopLauncher#ALTERNATE_ROW}). */
    static final Color TABLE_BOTTOM = new Color(0xEE, 0xEE, 0xEE);

    /** A gradient anchored to a viewport is repainted, not copied, when the view scrolls. */
    private static final HierarchyListener REPAINT_ON_SCROLL = e -> {
        if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0 && e.getComponent() instanceof JComponent c) {
            repaintOnScroll(c);
        }
    };

    private GrayGradient() {
    }

    /** The UI class names the FlatLaf desktop maps to these delegates. */
    static Map<String, String> uiClasses() {
        return Map.of("TextAreaUI", TextArea.class.getName(), "TextPaneUI", TextPane.class.getName(),
                "EditorPaneUI", EditorPane.class.getName(), "TableUI", Table.class.getName());
    }

    /** Read-only outputs and the authoring sheet grid use the gradient on the theme's background. */
    static boolean output(JComponent c) {
        if (!(c.getBackground() instanceof UIResource)) {
            return false;   // a colour the code chose means something
        }
        if (c instanceof JTextComponent text) {
            return !text.isEditable();
        }
        return c instanceof JTable table && (table instanceof SheetTable || readOnly(table.getModel()));
    }

    /** Whether no column of the model can be edited (asked of its first row; a model that cannot answer counts as read-only). */
    static boolean readOnly(TableModel model) {
        for (int column = 0; column < model.getColumnCount(); column++) {
            try {
                if (model.isCellEditable(0, column)) {
                    return false;
                }
            } catch (RuntimeException e) {
                // no first row to ask about
            }
        }
        return true;
    }

    /** Paints the gradient over the visible part of c, spanning the enclosing viewport (or c itself outside one). */
    static void paintBehind(Graphics g, JComponent c) {
        Rectangle visible = c.getVisibleRect();
        if (visible.isEmpty()) {
            return;
        }
        fill(g, visible, span(c), c instanceof JTable ? TABLE_BOTTOM : BOTTOM);
    }

    private static Rectangle span(JComponent c) {
        Rectangle span = new Rectangle(0, 0, c.getWidth(), c.getHeight());
        if (SwingUtilities.getAncestorOfClass(JViewport.class, c) instanceof JViewport viewport) {
            span = SwingUtilities.convertRectangle(viewport, new Rectangle(0, 0, viewport.getWidth(), viewport.getHeight()), c);
        }
        return span;
    }

    /** Paints a table's gradient over a whole viewport (its own coordinates): the space below the table's last row. */
    static void paintBehind(Graphics g, JViewport viewport) {
        Rectangle all = new Rectangle(0, 0, viewport.getWidth(), viewport.getHeight());
        fill(g, all, all, TABLE_BOTTOM);
    }

    private static void fill(Graphics g, Rectangle area, Rectangle span, Color bottom) {
        fill(g, area, span, TOP, bottom);
    }

    private static void fill(Graphics g, Rectangle area, Rectangle span, Color top, Color bottom) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setPaint(new GradientPaint(0, span.y, top, 0, span.y + Math.max(1, span.height), bottom));
            g2.fill(area);
        } finally {
            g2.dispose();
        }
    }

    private static void follow(JComponent c) {
        c.addHierarchyListener(REPAINT_ON_SCROLL);
        repaintOnScroll(c);
    }

    private static void unfollow(JComponent c) {
        c.removeHierarchyListener(REPAINT_ON_SCROLL);
    }

    private static void repaintOnScroll(JComponent c) {
        if (SwingUtilities.getAncestorOfClass(JViewport.class, c) instanceof JViewport viewport
                && viewport.getScrollMode() != JViewport.SIMPLE_SCROLL_MODE) {
            viewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        }
    }

    /** The editable sheet grid: stripes follow current row/column positions, including after edits and resizing. */
    static final class SheetTable extends JTable {
        private static final long serialVersionUID = 1L;

        SheetTable(TableModel model) {
            super(model);
            setDefaultRenderer(Object.class, new SheetCell());
        }

        @Override public Component prepareEditor(TableCellEditor editor, int row, int column) {
            Component field = super.prepareEditor(editor, row, column);
            if (getUI() instanceof Table) {
                Rectangle cell = getCellRect(row, column, false), visible = span(this);
                double fraction = Math.max(0, Math.min(1, (cell.getCenterY() - visible.y) / Math.max(1, visible.height)));
                int level = (int) Math.round(TOP.getRed() + fraction * (TABLE_BOTTOM.getRed() - TOP.getRed()));
                field.setBackground(gray(level + shade(row, column)));
            }
            return field;
        }
    }

    private static int shade(int row, int column) {
        return (row % 2 == 0 ? 0 : -20) + (column % 2 == 0 ? 0 : 4);
    }

    private static Color gray(int level) {
        int value = Math.max(0, Math.min(255, level));
        return new Color(value, value, value);
    }

    private static final class SheetCell extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private JTable table;
        private int row, column;
        private boolean selected;

        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                 boolean focus, int row, int column) {
            this.table = table;
            this.row = row;
            this.column = column;
            this.selected = selected;
            return super.getTableCellRendererComponent(table, value, selected, focus, row, column);
        }

        @Override protected void paintComponent(Graphics g) {
            if (selected || !(table.getUI() instanceof Table)) {
                super.paintComponent(g);
                return;
            }
            Rectangle cell = table.getCellRect(row, column, false), visible = span(table);
            visible.translate(-cell.x, -cell.y);
            int shade = shade(row, column);
            fill(g, new Rectangle(0, 0, getWidth(), getHeight()), visible,
                    gray(GrayGradient.TOP.getRed() + shade), gray(TABLE_BOTTOM.getRed() + shade));
            setOpaque(false);
            try { super.paintComponent(g); } finally { setOpaque(true); }
        }
    }

    /** guif's text areas: read-only ones on the gradient. */
    public static final class TextArea extends FlatTextAreaUI {
        public static ComponentUI createUI(JComponent c) {
            return new TextArea();
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            follow(c);
        }

        @Override
        public void uninstallUI(JComponent c) {
            unfollow(c);
            super.uninstallUI(c);
        }

        @Override
        protected void paintBackground(Graphics g) {
            if (output(getComponent())) {
                paintBehind(g, getComponent());
            } else {
                super.paintBackground(g);
            }
        }
    }

    /** guif's text panes: read-only ones on the gradient. */
    public static final class TextPane extends FlatTextPaneUI {
        public static ComponentUI createUI(JComponent c) {
            return new TextPane();
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            follow(c);
        }

        @Override
        public void uninstallUI(JComponent c) {
            unfollow(c);
            super.uninstallUI(c);
        }

        @Override
        protected void paintBackground(Graphics g) {
            if (output(getComponent())) {
                paintBehind(g, getComponent());
            } else {
                super.paintBackground(g);
            }
        }
    }

    /** guif's editor panes (HTML and plain text views): read-only ones on the gradient. */
    public static final class EditorPane extends FlatEditorPaneUI {
        public static ComponentUI createUI(JComponent c) {
            return new EditorPane();
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            follow(c);
        }

        @Override
        public void uninstallUI(JComponent c) {
            unfollow(c);
            super.uninstallUI(c);
        }

        @Override
        protected void paintBackground(Graphics g) {
            if (output(getComponent())) {
                paintBehind(g, getComponent());
            } else {
                super.paintBackground(g);
            }
        }
    }

    /**
     * guif's tables: a read-only table paints the gradient instead of its plain background, so its even rows (whose cells
     * take the table's colour and leave the painting to it) show the gradient and its odd rows keep their tint; the space
     * below its last row, which its viewport shows, gets the gradient too.
     */
    public static final class Table extends FlatTableUI {
        public static ComponentUI createUI(JComponent c) {
            return new Table();
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            follow(c);
        }

        @Override
        public void uninstallUI(JComponent c) {
            unfollow(c);
            super.uninstallUI(c);
        }

        @Override
        public void update(Graphics g, JComponent c) {
            if (c.isOpaque() && output(c)) {
                paintBehind(g, c);
                paint(g, c);
            } else {
                super.update(g, c);
            }
        }

        /** FlatLaf fills this space with the table's background; an output's gets the gradient (unless rows are drawn there). */
        @Override
        public void paintViewport(Graphics g, JComponent c, JViewport viewport) {
            if (output(c) && viewport.isOpaque() && !UIManager.getBoolean("Table.paintOutsideAlternateRows")) {
                paintBehind(g, viewport);
            } else {
                super.paintViewport(g, c, viewport);
            }
        }
    }
}
