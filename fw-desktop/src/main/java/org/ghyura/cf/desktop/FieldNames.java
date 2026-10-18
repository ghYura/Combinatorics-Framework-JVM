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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.FlowLayout;

/**
 * Every field of the desktop shows its own name: a label beside it, linked to it as its label, or a title on the scroll
 * pane around it. The name is also the field's accessible name, so a screen reader and the tests read what the eye reads.
 */
final class FieldNames {

    private FieldNames() {
    }

    /** A label showing the field's name, linked to the field. */
    static JLabel label(String name, JComponent field) {
        JLabel label = new JLabel(name);
        label.setLabelFor(field);
        field.getAccessibleContext().setAccessibleName(bare(name));
        return label;
    }

    /** Shows, greyed inside the empty field, an example of what it takes (FlatLaf draws it; other looks keep the hint). */
    static <T extends JTextField> T placeholder(T field, String example) {
        field.putClientProperty("JTextField.placeholderText", example);
        return field;
    }

    /** A short muted line under a field: what goes in it, in plain words. */
    static JLabel note(String text) {
        JLabel note = new JLabel(text);
        note.setForeground(new Color(0x57606a));
        note.setFont(note.getFont().deriveFont(note.getFont().getSize2D() - 1f));
        note.setBorder(BorderFactory.createEmptyBorder(1, 2, 2, 2));
        return note;
    }

    /** The field with its name to its left, in one small row (for fields that take turns in one place). */
    static JPanel named(String name, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.add(label(name, field));
        row.add(field);
        return row;
    }

    /** The field in a scroll pane titled with its name. */
    static JScrollPane titled(JComponent field, String name) {
        JScrollPane pane = new JScrollPane(field);
        pane.setBorder(BorderFactory.createTitledBorder(pane.getBorder(), name));
        field.getAccessibleContext().setAccessibleName(name);
        return pane;
    }

    /** The name the field shows: its label's text, else its scroll pane's title; null when it shows none. */
    static String shown(JComponent field) {
        if (field.getClientProperty("labeledBy") instanceof JLabel label && label.getText() != null && !label.getText().isBlank()) {
            return bare(label.getText());
        }
        if (SwingUtilities.getAncestorOfClass(JScrollPane.class, field) instanceof JScrollPane pane && pane.getViewport().getView() == field
                && pane.getBorder() instanceof TitledBorder border && border.getTitle() != null && !border.getTitle().isBlank()) {
            return border.getTitle();
        }
        return null;
    }

    /** The label the field shows its name on, or null when its name is a title. */
    static JLabel labelOf(JComponent field) {
        return field.getClientProperty("labeledBy") instanceof JLabel label ? label : null;
    }

    /** The client property marking a section: a part of a step with its own visible title, whose fields' names differ. */
    static final String SECTION = "cf.section";

    /** Marks the container as a section titled as shown on screen; field names need only differ within it. */
    static <T extends JComponent> T section(T container, String title) {
        container.putClientProperty(SECTION, title);
        return container;
    }

    /** The section a component is in, or null for the step itself. */
    static String sectionOf(java.awt.Component c) {
        for (java.awt.Component k = c; k != null; k = k.getParent()) {
            if (k instanceof JComponent j && j.getClientProperty(SECTION) instanceof String s) {
                return s + "#" + System.identityHashCode(j);
            }
        }
        return null;
    }

    private static String bare(String name) {
        String s = name.strip();
        return s.endsWith(":") ? s.substring(0, s.length() - 1).strip() : s;
    }
}
