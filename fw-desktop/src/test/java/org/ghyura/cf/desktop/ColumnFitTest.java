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

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.Container;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GEN:operator-authoring-ui: every table's columns fit their name and data, plus one space, and follow the data. */
class ColumnFitTest {

    private static <T> T edt(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        // a second turn runs what the first one scheduled (a fit after a burst of changes)
        SwingUtilities.invokeAndWait(() -> { });
        return result.get();
    }

    /** One space and the grid line: what a column has beyond its widest content. */
    private static int space(JTable t) {
        FontMetrics m = t.getFontMetrics(t.getFont());
        return m.stringWidth(" ") + t.getIntercellSpacing().width;
    }

    /** The narrowest column: ten spaces of the table's font. */
    private static int least(JTable t) {
        return t.getFontMetrics(t.getFont()).stringWidth(" ") * ColumnFit.LEAST_SPACES;
    }

    /** A column fitted to content of a width: that width and one space, ten spaces at least. */
    private static int fitted(JTable t, int content) {
        return Math.max(least(t), content + space(t));
    }

    @Test
    void anEmptyOrUnnamedColumnIsTenSpacesWideAtLeast() throws Exception {
        DefaultTableModel model = new DefaultTableModel(new Object[][]{{"", "x"}, {null, "y"}}, new Object[]{"", "B"});
        JTable t = edt(() -> ColumnFit.install(new JTable(model)));
        assertEquals(least(t), (int) edt(() -> width(t, 0)), "no name, no value: ten spaces");
        assertEquals(least(t), (int) edt(() -> width(t, 1)), "a one-letter name and values: ten spaces still");
        assertTrue(least(t) > 0);
    }

    @Test
    void typingWidensTheColumnWithEveryCharacterWithoutEndingTheEdit() throws Exception {
        DefaultTableModel model = new DefaultTableModel(new Object[][]{{"", "x"}}, new Object[]{"", "B"});
        JTable t = edt(() -> new JTable(model));
        JScrollPane scroll = edt(() -> new JScrollPane(t));
        edt(() -> {
            ColumnFit.install(t);
            scroll.setSize(300, 200);
            scroll.doLayout();
            return null;
        });
        int before = edt(() -> width(t, 0));
        assertEquals(least(t), before);
        javax.swing.text.JTextComponent editor = edt(() -> {
            assertTrue(t.editCellAt(0, 0));
            return (javax.swing.text.JTextComponent) t.getEditorComponent();
        });
        String typed = "FW_Combi(1) then a long directive typed one character after another";
        int widest = before;
        for (int i = 0; i < typed.length(); i++) {
            char ch = typed.charAt(i);
            edt(() -> {
                editor.getDocument().insertString(editor.getDocument().getLength(), String.valueOf(ch), null);
                return null;
            });
            edt(() -> null);
            int now = edt(() -> t.getColumnModel().getColumn(0).getWidth());
            assertTrue(now >= widest, "the column never narrows while typing: " + now + " after " + widest);
            widest = now;
            assertTrue(edt(t::isEditing), "the edit goes on after character " + (i + 1));
        }
        java.awt.FontMetrics m = editor.getFontMetrics(editor.getFont());
        java.awt.Insets in = editor.getInsets();
        int needed = m.stringWidth(typed) + in.left + in.right + space(t);
        assertEquals(needed, (int) edt(() -> t.getColumnModel().getColumn(0).getWidth()), "as wide as the typed text needs");
        assertEquals((int) edt(() -> t.getCellRect(0, 0, false).width), (int) edt(editor::getWidth), "the editor follows its cell");
        assertEquals(typed, edt(editor::getText), "nothing typed was lost");

        // the edit done, the value is in the model and the column fits the data again
        edt(() -> t.getCellEditor().stopCellEditing());
        assertEquals(typed, model.getValueAt(0, 0));
        assertEquals(fitted(t, cellWidth(t, 0, 0)), (int) edt(() -> width(t, 0)));
    }

    private static int cellWidth(JTable t, int row, int column) {
        return t.prepareRenderer(t.getCellRenderer(row, column), row, column).getPreferredSize().width;
    }

    private static int nameWidth(JTable t, int column) {
        TableColumn c = t.getColumnModel().getColumn(column);
        return t.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(t, c.getHeaderValue(), false, false, -1, column)
                .getPreferredSize().width;
    }

    private static int width(JTable t, int column) {
        return t.getColumnModel().getColumn(column).getPreferredWidth();
    }

    @Test
    void aColumnIsAsWideAsItsWidestCellOrItsNameWhicheverIsWiderPlusOneSpace() throws Exception {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"id", "a column name wider than its values"}, 0);
        model.addRow(new Object[]{"a rather long identifier", "x"});
        model.addRow(new Object[]{"short", "y"});
        JTable t = edt(() -> ColumnFit.install(new JTable(model)));
        assertEquals(fitted(t, cellWidth(t, 0, 0)), (int) edt(() -> width(t, 0)), "the widest cell decides the first column");
        assertEquals(fitted(t, nameWidth(t, 1)), (int) edt(() -> width(t, 1)), "a name wider than every value decides the column");
        assertEquals(JTable.AUTO_RESIZE_OFF, t.getAutoResizeMode(), "the widths are kept, never squeezed to the view");

        // the data changes: the column follows it, both ways
        edt(() -> {
            model.addRow(new Object[]{"an identifier longer than every one before it", "z"});
            return null;
        });
        assertEquals(fitted(t, cellWidth(t, 2, 0)), (int) edt(() -> width(t, 0)));
        edt(() -> {
            model.removeRow(2);
            model.removeRow(0);
            return null;
        });
        assertEquals(fitted(t, Math.max(nameWidth(t, 0), cellWidth(t, 0, 0))), (int) edt(() -> width(t, 0)), "and narrows again");

        // a new model, new columns
        edt(() -> {
            t.setModel(new DefaultTableModel(new Object[][]{{"one", "two", "three"}}, new Object[]{"A", "B", "a long third name"}));
            return null;
        });
        assertEquals(3, t.getColumnCount());
        assertEquals(fitted(t, nameWidth(t, 2)), (int) edt(() -> width(t, 2)));
    }

    @Test
    void theLastColumnTakesWhatAWiderViewLeavesAndANarrowerViewScrolls() throws Exception {
        DefaultTableModel model = new DefaultTableModel(new Object[][]{{"alpha", "beta"}, {"gamma", "delta"}}, new Object[]{"first", "second"});
        JTable t = edt(() -> new JTable(model));
        JScrollPane scroll = edt(() -> new JScrollPane(t));
        edt(() -> ColumnFit.install(t));
        int first = edt(() -> width(t, 0));
        int second = edt(() -> width(t, 1));
        edt(() -> {
            scroll.setSize(first + second + 400, 200);
            scroll.doLayout();
            return null;
        });
        int view = edt(() -> scroll.getViewport().getExtentSize().width);
        assertEquals(first, (int) edt(() -> width(t, 0)), "the first column keeps its fitted width");
        assertEquals(view - first, (int) edt(() -> width(t, 1)), "the last column fills the view");
        edt(() -> {
            scroll.setSize(first / 2, 200);
            scroll.doLayout();
            return null;
        });
        assertEquals(first, (int) edt(() -> width(t, 0)));
        assertEquals(second, (int) edt(() -> width(t, 1)), "a narrower view squeezes nothing: the table scrolls sideways");
        assertTrue(edt(() -> t.getPreferredSize().width) > edt(() -> scroll.getViewport().getExtentSize().width));
    }

    @Test
    void longCellTextStopsAtTheCapButANameAlwaysShowsInFull() throws Exception {
        String longName = "n".repeat(ColumnFit.MOST_CHARACTERS + 30);
        DefaultTableModel model = new DefaultTableModel(new Object[][]{{"x".repeat(1000), "y"}}, new Object[]{"source", longName});
        JTable t = edt(() -> ColumnFit.install(new JTable(model)));
        FontMetrics m = t.getFontMetrics(t.getFont());
        assertEquals(fitted(t, m.charWidth('n') * ColumnFit.MOST_CHARACTERS), (int) edt(() -> width(t, 0)), "cell text counts up to the cap");
        assertEquals(fitted(t, nameWidth(t, 1)), (int) edt(() -> width(t, 1)), "the name is never cut");
    }

    @Test
    void everyTableOfTheDesktopFitsItsColumns() throws Exception {
        org.ghyura.cf.desktop.session.Session session = org.ghyura.cf.desktop.session.Session.resolve(null, java.util.Map.of(), List.of());
        Desktop desktop = edt(() -> new Desktop(session, null));
        List<JTable> tables = new ArrayList<>();
        edt(() -> {
            collect(desktop.component(), tables);
            return null;
        });
        assertTrue(tables.size() >= 13, tables.size() + " tables");
        for (JTable t : tables) {
            assertTrue(ColumnFit.installed(t), t.getModel().getClass().getName());
        }
        edt(desktop::close).get(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void collect(Container c, List<JTable> out) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof JTable t) {
                out.add(t);
            }
            if (child instanceof Container inner) {
                collect(inner, out);
            }
        }
    }
}
