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

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;
import java.awt.Component;

/**
 * What each element of the desktop is for, in plain words, shown by the hint layer when the pointer rests on it. A hint
 * is a client property of its component; a tab's hint is kept on the tab's content. An element without a hint of its own
 * shows the hint of the control it belongs to (a spinner's text field, a table's header, the empty part of a scroll pane
 * around a table or list) or, failing that, the hint of the tab it is on, so every area of the desktop explains itself.
 */
final class Hints {

    /** The client property holding a component's own hint. */
    static final String KEY = "cf.hint";
    /** The client property holding a tab's hint, on the tab's content. */
    static final String TAB = "cf.tabHint";

    private Hints() {
    }

    /** Gives the component its hint and returns it. */
    static <T extends JComponent> T of(T component, String hint) {
        component.putClientProperty(KEY, hint);
        return component;
    }

    /** The component's own hint, or null. */
    static String own(Component c) {
        return c instanceof JComponent j && j.getClientProperty(KEY) instanceof String s && !s.isBlank() ? s : null;
    }

    /** The hint of the tab at the index, or null. */
    static String tab(JTabbedPane tabs, int index) {
        return tabs.getComponentAt(index) instanceof JComponent j && j.getClientProperty(TAB) instanceof String s ? s : null;
    }

    /**
     * What the tab at the index is for, as the pointer on its name shows it: the question, then the tab's hint without the
     * step label its name already shows ("Step 3 · Scenario: ", "Page 0.2 · Factors: ", "Step 2.1: ").
     */
    static String purpose(JTabbedPane tabs, int index) {
        String hint = tab(tabs, index).replaceFirst("^(Step|Page|Chart) [0-9.]+( · [^:]+)?: ", "");
        return "What is “" + tabs.getTitleAt(index) + "” for?\n" + Character.toUpperCase(hint.charAt(0)) + hint.substring(1);
    }

    /**
     * The component whose hint is shown for c: c itself or the nearest ancestor with a hint, a table header's table, a
     * table that only shows its title; or, failing those, the content of the tab c is on. Null when nothing explains c.
     */
    static JComponent holder(Component c) {
        for (Component k = c; k != null; k = k.getParent()) {
            if (k instanceof JTableHeader header && header.getTable() != null && own(header.getTable()) != null) {
                return header.getTable();
            }
            if (own(k) != null) {
                return (JComponent) k;
            }
            if (k instanceof JScrollPane pane && pane.getViewport().getView() instanceof JComponent view && own(view) != null) {
                return view;   // the empty part of a scroll pane says what it holds
            }
            if (k instanceof JTable table && SwingUtilities.getAncestorOfClass(JScrollPane.class, table) instanceof JScrollPane pane
                    && TableTitles.shown(pane) != null) {
                return table;
            }
        }
        for (Component k = c; k != null; k = k.getParent()) {
            if (k instanceof JComponent j && j.getClientProperty(TAB) instanceof String) {
                return j;
            }
        }
        return null;
    }

    /** The hint shown for c, or null. */
    static String resolve(Component c) {
        JComponent holder = holder(c);
        if (holder == null) {
            return null;
        }
        String own = own(holder);
        if (own != null) {
            return own;
        }
        if (holder instanceof JTable table && SwingUtilities.getAncestorOfClass(JScrollPane.class, table) instanceof JScrollPane pane) {
            return TableTitles.shown(pane) + ".";
        }
        return (String) holder.getClientProperty(TAB);
    }
}
