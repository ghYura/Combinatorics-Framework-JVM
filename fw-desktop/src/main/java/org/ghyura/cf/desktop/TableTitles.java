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

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.TitledBorder;

/**
 * Every table of the desktop shows its own name, saying what it holds: the scroll pane around the table carries a titled
 * border, and the table carries the same name as its component and accessible name, so a screen reader and the headless
 * tests read what the eye reads. A table whose content follows a selection (the authoring grid, the library's scenarios) is
 * renamed with it.
 */
final class TableTitles {

    private TableTitles() {
    }

    /** The table in a scroll pane titled with what it holds. */
    static JScrollPane titled(JTable table, String title) {
        JScrollPane pane = new JScrollPane(table);
        name(pane, table, title);
        return pane;
    }

    /** Names the table and titles its scroll pane; again whenever the table starts holding something else. */
    static void name(JScrollPane pane, JTable table, String title) {
        table.setName(title);
        table.getAccessibleContext().setAccessibleName(title);
        if (pane.getBorder() instanceof TitledBorder border) {
            border.setTitle(title);
            pane.repaint();
        } else {
            pane.setBorder(BorderFactory.createTitledBorder(pane.getBorder(), title));
        }
    }

    /** The name a table shows, or null when it shows none. */
    static String shown(JScrollPane pane) {
        return pane.getBorder() instanceof TitledBorder border ? border.getTitle() : null;
    }
}
