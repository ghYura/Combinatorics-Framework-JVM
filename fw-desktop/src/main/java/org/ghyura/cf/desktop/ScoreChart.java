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
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * The legacy optimization charts (OptimizationVisualizer): the line scores in input order as a polyline with dots on a
 * 0..100 axis, seen as an objective (the score), a cost (100 - score), a loss (80 - score, not below 0) or a fitness
 * (1.2 x score), each in its legacy color, clamped to the plot. The geometry is a pure function of the scores and the size.
 */
final class ScoreChart extends JPanel {

    private static final long serialVersionUID = 1L;
    static final int MARGIN = 50;

    enum Kind {
        OBJECTIVE("Objective Function", new Color(0, 102, 204)),
        COST("Cost Function", new Color(204, 51, 51)),
        LOSS("Loss Function", new Color(204, 102, 0)),
        FITNESS("Fitness Function", new Color(51, 153, 51));

        final String title;
        final Color color;

        Kind(String title, Color color) {
            this.title = title;
            this.color = color;
        }

        double transform(double score) {
            return switch (this) {
                case OBJECTIVE -> score;
                case COST -> 100.0 - score;
                case LOSS -> Math.max(0, 80.0 - score);
                case FITNESS -> score * 1.2;
            };
        }
    }

    private final Kind kind;
    private transient List<Double> scores;

    ScoreChart(Kind kind, List<Double> scores) {
        this.kind = kind;
        this.scores = List.copyOf(scores);
    }

    void show(List<Double> shown) {
        scores = List.copyOf(shown);
        repaint();
    }

    /** The legacy pixel positions of the scores: x spread evenly between the margins, y from the transformed score. */
    static int[][] points(Kind kind, List<Double> scores, int w, int h) {
        int n = scores.size();
        int[][] xy = new int[n][2];
        for (int i = 0; i < n; i++) {
            int x = MARGIN + (i * (w - 2 * MARGIN) / Math.max(1, n - 1));
            int y = h - MARGIN - (int) (kind.transform(scores.get(i)) * (h - 2 * MARGIN) / 100.0);
            xy[i][0] = x;
            xy[i][1] = Math.max(MARGIN, Math.min(h - MARGIN, y));
        }
        return xy;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Color.GRAY);
            g2.drawLine(MARGIN, h - MARGIN, w - MARGIN, h - MARGIN);
            g2.drawLine(MARGIN, MARGIN, MARGIN, h - MARGIN);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("0", MARGIN - 22, h - MARGIN + 4);
            g2.drawString("100", MARGIN - 32, MARGIN + 8);
            g2.drawString(kind.title, MARGIN + 8, MARGIN - 8);
            g2.setColor(kind.color);
            int[][] xy = points(kind, scores, w, h);
            for (int i = 0; i < xy.length; i++) {
                if (i > 0) g2.drawLine(xy[i - 1][0], xy[i - 1][1], xy[i][0], xy[i][1]);
                g2.fillOval(xy[i][0] - 2, xy[i][1] - 2, 4, 4);
            }
        } finally {
            g2.dispose();
        }
    }
}
