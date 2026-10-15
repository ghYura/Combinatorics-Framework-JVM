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

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * A flow of components that wraps to the next line when its container is too narrow, and asks for the height its lines
 * take (FlowLayout lays out wrapped lines but asks for one line). {@link Tracking} is a panel that takes the width of the
 * scroll pane showing it, so that the rows inside wrap instead of scrolling sideways.
 */
final class WrapLayout extends FlowLayout {

    private static final long serialVersionUID = 1L;

    WrapLayout() {
        super(FlowLayout.LEFT, 4, 1);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return size(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = size(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Container sized = target;
            while (sized.getSize().width == 0 && sized.getParent() != null) {
                sized = sized.getParent();
            }
            int width = sized.getSize().width == 0 ? Integer.MAX_VALUE : sized.getSize().width;
            Insets insets = target.getInsets();
            int sides = insets.left + insets.right + getHgap() * 2;
            int max = width - sides;
            int lineWidth = 0;
            int lineHeight = 0;
            Dimension out = new Dimension(0, 0);
            for (Component c : target.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                if (lineWidth > 0 && lineWidth + getHgap() + d.width > max) {
                    line(out, lineWidth, lineHeight);
                    lineWidth = 0;
                    lineHeight = 0;
                }
                lineWidth += (lineWidth > 0 ? getHgap() : 0) + d.width;
                lineHeight = Math.max(lineHeight, d.height);
            }
            line(out, lineWidth, lineHeight);
            out.width += sides;
            out.height += insets.top + insets.bottom + getVgap() * 2;
            return out;
        }
    }

    private void line(Dimension out, int width, int height) {
        out.width = Math.max(out.width, width);
        if (out.height > 0) {
            out.height += getVgap();
        }
        out.height += height;
    }

    /** A row of components that wraps. */
    static JPanel row() {
        return new JPanel(new WrapLayout());
    }

    /** Cards that ask for the size of the card shown, not of the largest. */
    static final class Cards extends JPanel {
        private static final long serialVersionUID = 1L;

        Cards() {
            super(new java.awt.CardLayout());
        }

        void show(String name) {
            ((java.awt.CardLayout) getLayout()).show(this, name);
            revalidate();
        }

        @Override
        public Dimension getPreferredSize() {
            for (Component c : getComponents()) {
                if (c.isVisible()) {
                    Dimension d = c.getPreferredSize();
                    Insets insets = getInsets();
                    return new Dimension(d.width + insets.left + insets.right, d.height + insets.top + insets.bottom);
                }
            }
            return super.getPreferredSize();
        }
    }

    /** A panel that is as wide as the scroll pane showing it and as tall as its content. */
    static final class Tracking extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        Tracking(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
