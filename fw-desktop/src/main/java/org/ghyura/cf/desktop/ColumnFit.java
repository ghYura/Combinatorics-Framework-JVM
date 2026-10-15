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

import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Columns that fit what they show: every column of a table as wide as its widest cell or its name, whichever is wider,
 * plus one space, measured again whenever the table's data, model or columns change (once per burst of changes). The
 * table keeps those widths (no proportional squeezing or stretching); when the view is wider than all of them the last
 * column takes the rest, and when it is narrower the table scrolls sideways. Cell text stops counting at
 * {@link #MOST_CHARACTERS} characters (a whole candidate source would push every other column out of view); a column's
 * name always shows in full. No column is narrower than {@link #LEAST_SPACES} spaces, so an empty one (an unnamed grid
 * column) still takes a click and shows what is typed into it. While a cell is edited its column widens with every
 * character typed, without ending the edit; the edit done, the column fits the data again. At most {@link #MOST_ROWS}
 * rows are measured.
 */
final class ColumnFit implements TableModelListener, TableColumnModelListener {

    /** The widest cell text a column grows for, in characters of the table's font. */
    static final int MOST_CHARACTERS = 120;

    /** The narrowest a column is, in spaces of the table's font. */
    static final int LEAST_SPACES = 10;

    /** The most rows measured; a longer table takes its widths from its first rows. */
    static final int MOST_ROWS = 5_000;

    private static final String KEY = ColumnFit.class.getName();

    private final JTable table;
    private final Map<TableColumn, Integer> widths = new IdentityHashMap<>();
    private JViewport viewport;
    private boolean pending;

    private final ComponentAdapter resized = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            layout();
        }
    };

    private ColumnFit(JTable table) {
        this.table = table;
    }

    /** Fits the columns of every table in a component tree, and keeps them fitted. */
    static void installAll(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTable t) {
                install(t);
            }
            if (c instanceof Container inner) {
                installAll(inner);
            }
        }
    }

    /** Fits a table's columns now and whenever its data, model or columns change; installing twice does nothing. */
    static JTable install(JTable table) {
        if (table.getClientProperty(KEY) == null) {
            ColumnFit fit = new ColumnFit(table);
            table.putClientProperty(KEY, fit);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.getModel().addTableModelListener(fit);
            table.getColumnModel().addColumnModelListener(fit);
            table.addPropertyChangeListener("model", e -> {
                ((TableModel) e.getOldValue()).removeTableModelListener(fit);
                ((TableModel) e.getNewValue()).addTableModelListener(fit);
                fit.schedule();
            });
            table.addPropertyChangeListener("columnModel", e -> {
                ((TableColumnModel) e.getOldValue()).removeColumnModelListener(fit);
                ((TableColumnModel) e.getNewValue()).addColumnModelListener(fit);
                fit.schedule();
            });
            table.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
                    fit.watchViewport();
                }
            });
            table.addPropertyChangeListener("tableCellEditor", e -> fit.editing());
            fit.watchViewport();
            fit.measure();
        }
        return table;
    }

    /** Whether a table's columns are kept fitted. */
    static boolean installed(JTable table) {
        return table.getClientProperty(KEY) != null;
    }

    /** Measures a fitted table's columns now (the event thread does it by itself after a change). */
    static void fit(JTable table) {
        if (table.getClientProperty(KEY) instanceof ColumnFit fit) {
            fit.measure();
        }
    }

    // ---- events: data and structure are measured again, column moves and view sizes only lay out ---------------------

    @Override
    public void tableChanged(TableModelEvent e) {
        schedule();
    }

    @Override
    public void columnAdded(TableColumnModelEvent e) {
        schedule();
    }

    @Override
    public void columnRemoved(TableColumnModelEvent e) {
        schedule();
    }

    @Override
    public void columnMoved(TableColumnModelEvent e) {
        if (e.getFromIndex() != e.getToIndex()) {
            layout();
        }
    }

    @Override
    public void columnMarginChanged(ChangeEvent e) {
        // the widths this class sets; nothing to do
    }

    @Override
    public void columnSelectionChanged(ListSelectionEvent e) {
        // selection does not change what the columns show
    }

    /** One measurement after the burst of events in progress, once the table has rebuilt its columns from them. */
    private void schedule() {
        if (!pending) {
            pending = true;
            SwingUtilities.invokeLater(() -> {
                pending = false;
                measure();
            });
        }
    }

    private void watchViewport() {
        JViewport now = table.getParent() instanceof JViewport v ? v : null;
        if (now != viewport) {
            if (viewport != null) {
                viewport.removeComponentListener(resized);
            }
            viewport = now;
            if (viewport != null) {
                viewport.addComponentListener(resized);
            }
            layout();
        }
    }

    // ---- measuring and laying out -------------------------------------------------------------------------------------

    /**
     * Each column's width: the wider of its name and its widest cell, as the renderers draw them, plus one space; cell
     * text counts up to the cap, the name in full.
     */
    private void measure() {
        TableColumnModel columns = table.getColumnModel();
        FontMetrics metrics = table.getFontMetrics(table.getFont());
        int space = metrics.stringWidth(" ") + table.getIntercellSpacing().width;
        int most = metrics.charWidth('n') * MOST_CHARACTERS;
        int rows = Math.min(table.getRowCount(), MOST_ROWS);
        JTableHeader header = table.getTableHeader();
        widths.clear();
        for (int c = 0; c < columns.getColumnCount(); c++) {
            TableColumn column = columns.getColumn(c);
            int name = 0;
            if (header != null) {
                TableCellRenderer renderer = column.getHeaderRenderer() != null ? column.getHeaderRenderer() : header.getDefaultRenderer();
                name = renderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, c).getPreferredSize().width;
            }
            int cells = 0;
            for (int r = 0; r < rows && cells < most; r++) {
                cells = Math.max(cells, table.prepareRenderer(table.getCellRenderer(r, c), r, c).getPreferredSize().width);
            }
            widths.put(column, Math.max(Math.max(column.getMinWidth(), least()), Math.max(name, Math.min(most, cells)) + space));
        }
        layout();
    }

    /** {@link #LEAST_SPACES} spaces of the table's font. */
    private int least() {
        return table.getFontMetrics(table.getFont()).stringWidth(" ") * LEAST_SPACES;
    }

    /** The measured widths, the last column taking what the view has left over. */
    private void layout() {
        TableColumnModel columns = table.getColumnModel();
        int n = columns.getColumnCount();
        if (n == 0) {
            return;
        }
        resize(() -> {
            int total = 0;
            for (int c = 0; c < n; c++) {
                TableColumn column = columns.getColumn(c);
                int width = widths.getOrDefault(column, column.getPreferredWidth());
                if (c == n - 1 && viewport != null) {
                    width = Math.max(width, viewport.getExtentSize().width - total);
                }
                if (column.getPreferredWidth() != width) {
                    column.setPreferredWidth(width);
                }
                total += width;
            }
        });
    }

    // ---- editing: the column follows what is typed ------------------------------------------------------------------

    private JTextComponent typing;
    private final DocumentListener typed = FormulaEditor.listener(() -> SwingUtilities.invokeLater(this::grow));

    /** An edit begins or ends (the table's tableCellEditor changes): follow the editor's text while it lasts. */
    private void editing() {
        if (typing != null) {
            typing.getDocument().removeDocumentListener(typed);
            typing = null;
        }
        if (table.isEditing() && table.getEditorComponent() instanceof JTextComponent text) {
            typing = text;
            typing.getDocument().addDocumentListener(typed);
            SwingUtilities.invokeLater(this::grow);
        }
    }

    /**
     * Widens the edited column as far as the typed text needs (its width in the editor's font, the editor's insets, one
     * space), never narrower than {@link #LEAST_SPACES} spaces; it does not narrow while the edit lasts.
     */
    private void grow() {
        if (typing == null || !table.isEditing() || table.getEditingColumn() < 0) {
            return;
        }
        TableColumn column = table.getColumnModel().getColumn(table.getEditingColumn());
        FontMetrics metrics = typing.getFontMetrics(typing.getFont());
        java.awt.Insets insets = typing.getInsets();
        int text = 0;
        for (String line : typing.getText().split("\n", -1)) {
            text = Math.max(text, metrics.stringWidth(line));
        }
        int needed = Math.max(least(), text + insets.left + insets.right + metrics.stringWidth(" ") + table.getIntercellSpacing().width);
        if (needed > column.getWidth()) {
            widths.put(column, needed);
            resize(() -> {
                column.setPreferredWidth(needed);
                column.setWidth(needed);
            });
        }
        // once the table is laid out at its new width, the view follows the caret (a text field scrolls only itself)
        SwingUtilities.invokeLater(this::followCaret);
    }

    /** Scrolls the table's view so that the editor's caret, and a space after it, stays in sight while typing. */
    private void followCaret() {
        if (typing == null || !table.isEditing()) {
            return;
        }
        try {
            java.awt.geom.Rectangle2D at = typing.modelToView2D(typing.getCaretPosition());
            if (at != null) {
                java.awt.Rectangle caret = at.getBounds();
                caret.width = Math.max(caret.width, typing.getFontMetrics(typing.getFont()).stringWidth(" ") * 2);
                table.scrollRectToVisible(SwingUtilities.convertRectangle(typing, caret, table));
            }
        } catch (javax.swing.text.BadLocationException e) {
            // a caret outside its text has nothing to show
        }
    }

    /**
     * Changes column widths without ending an edit in progress: a width change tells the table its columns' margins
     * changed, and the table stops editing on that (JTable.columnMarginChanged); while an edit lasts the table does not
     * hear of it, the change is laid out here and the editor moved to the cell's new bounds.
     */
    private void resize(Runnable change) {
        TableColumnModel columns = table.getColumnModel();
        boolean edit = table.isEditing();
        if (edit) {
            columns.removeColumnModelListener(table);
        }
        try {
            change.run();
        } finally {
            if (edit) {
                columns.addColumnModelListener(table);
            }
        }
        table.revalidate();
        table.repaint();
        if (table.getTableHeader() != null) {
            table.getTableHeader().revalidate();
            table.getTableHeader().repaint();
        }
        Component editor = table.getEditorComponent();
        if (edit && editor != null && table.getEditingRow() >= 0 && table.getEditingColumn() >= 0) {
            editor.setBounds(table.getCellRect(table.getEditingRow(), table.getEditingColumn(), false));
            editor.validate();
        }
    }
}
