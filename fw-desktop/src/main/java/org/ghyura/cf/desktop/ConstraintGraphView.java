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

import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.RuleValue;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The constraint graph of the legacy editor's canvas (constraints/editor.py, after graphspec.py): a column per sheet with its
 * values, a forbid bond in red and a require bond in green, a line between the two values of a pair and, for an n-ary tuple
 * or a many-to-many set, a hub (a square turned 45 degrees) with an arm to each value. A gated bond is dashed and a bond that
 * contradicts another is drawn thick. when, mapping and assert rules name sheets, not values, and are not drawn, as the
 * legacy canvas left them to the rule list (their conditions show as a tree beside it). The geometry is a pure function of
 * the sheets and bonds ({@link #layout}).
 */
final class ConstraintGraphView extends JPanel {

    private static final long serialVersionUID = 1L;
    static final int MARGIN = 20;
    static final int HEADER = 30;
    static final int COLUMN_WIDTH = 190;
    static final int NODE_WIDTH = 130;
    static final int NODE_HEIGHT = 22;
    static final int ROW_HEIGHT = 32;
    private static final int LABEL_CHARS = 18;

    /** A bond as drawn: its index among the bonds, polarity, gate, contradiction, the values it joins and its hub. */
    record Drawn(int index, String polarity, boolean gated, boolean conflicting, List<Point2D> points, Point2D hub) {
    }

    /** The value nodes by sheet and value, the sheet headers, the drawn bonds and the size the drawing needs. */
    record Layout(Map<String, Map<String, Rectangle2D>> nodes, Map<String, Rectangle2D> headers, List<Drawn> bonds, Dimension size) {
    }

    private transient Layout layout = new Layout(Map.of(), Map.of(), List.of(), new Dimension(400, 120));
    private transient Set<String> optional = Set.of();

    ConstraintGraphView() {
        setBackground(Color.WHITE);
        setPreferredSize(layout.size());
        setToolTipText("");
    }

    /** A value's whole text under the pointer: the boxes show at most a few words of it. */
    @Override
    public String getToolTipText(java.awt.event.MouseEvent event) {
        return valueAt(event.getX(), event.getY());
    }

    /** The sheet and value whose box holds the point, in plain words, or null. */
    String valueAt(double x, double y) {
        for (Map.Entry<String, Map<String, Rectangle2D>> column : layout.nodes().entrySet()) {
            for (Map.Entry<String, Rectangle2D> n : column.getValue().entrySet()) {
                if (n.getValue().contains(x, y)) {
                    return "Sheet " + column.getKey() + ", value " + n.getKey();
                }
            }
        }
        return null;
    }

    /** Draws the sheets' values and the bonds between them; optional sheets carry the legacy badge. */
    void show(Map<String, List<String>> sheets, Set<String> optionalSheets, List<ConstraintBonds.Bond> bonds) {
        layout = layout(sheets, bonds);
        optional = Set.copyOf(optionalSheets);
        setPreferredSize(layout.size());
        revalidate();
        repaint();
    }

    Layout currentLayout() {
        return layout;
    }

    static Layout layout(Map<String, List<String>> sheets, List<ConstraintBonds.Bond> bonds) {
        Map<String, Map<String, Rectangle2D>> nodes = new LinkedHashMap<>();
        Map<String, Rectangle2D> headers = new LinkedHashMap<>();
        int column = 0;
        int rows = 0;
        for (Map.Entry<String, List<String>> sheet : sheets.entrySet()) {
            double x = MARGIN + column * COLUMN_WIDTH;
            headers.put(sheet.getKey(), new Rectangle2D.Double(x, MARGIN, NODE_WIDTH, HEADER - 6));
            Map<String, Rectangle2D> values = new LinkedHashMap<>();
            int row = 0;
            for (String value : new LinkedHashSet<>(sheet.getValue())) {
                values.put(value, new Rectangle2D.Double(x, MARGIN + HEADER + row * ROW_HEIGHT, NODE_WIDTH, NODE_HEIGHT));
                row++;
            }
            nodes.put(sheet.getKey(), values);
            rows = Math.max(rows, row);
            column++;
        }
        Set<Integer> conflicting = conflicting(bonds);
        List<Drawn> drawn = new ArrayList<>();
        for (int i = 0; i < bonds.size(); i++) {
            ConstraintBonds.Bond b = bonds.get(i);
            if (b.when() != null || b.mapping() != null || b.assertion() != null) {
                continue;
            }
            List<Point2D> points = new ArrayList<>();
            b.sides().forEach((sheet, values) -> {
                for (RuleValue v : values) {
                    Rectangle2D box = v instanceof RuleValue.Text t ? nodes.getOrDefault(sheet, Map.of()).get(t.value()) : null;
                    if (box != null) {
                        points.add(new Point2D.Double(box.getCenterX(), box.getCenterY()));
                    }
                }
            });
            if (points.size() < 2) {
                continue;
            }
            Point2D hub = null;
            if (points.size() > 2) {
                hub = new Point2D.Double(points.stream().mapToDouble(Point2D::getX).average().orElse(0),
                        points.stream().mapToDouble(Point2D::getY).average().orElse(0));
            }
            boolean gated = b.gate() instanceof RuleValue.MapValue m && !m.entries().isEmpty();
            drawn.add(new Drawn(i, b.polarity(), gated, conflicting.contains(i), List.copyOf(points), hub));
        }
        int width = MARGIN * 2 + Math.max(1, sheets.size()) * COLUMN_WIDTH - (COLUMN_WIDTH - NODE_WIDTH);
        int height = MARGIN * 2 + HEADER + Math.max(1, rows) * ROW_HEIGHT;
        return new Layout(nodes, headers, drawn, new Dimension(Math.max(400, width), Math.max(120, height)));
    }

    /** The bonds that forbid what another requires (the legacy findConflicts), by index. */
    static Set<Integer> conflicting(List<ConstraintBonds.Bond> bonds) {
        Set<Integer> out = new LinkedHashSet<>();
        for (int i = 0; i < bonds.size(); i++) {
            for (int j = i + 1; j < bonds.size(); j++) {
                if (!bonds.get(i).polarity().equals(bonds.get(j).polarity())
                        && !ConstraintBonds.contradictions(List.of(bonds.get(i), bonds.get(j))).isEmpty()) {
                    out.add(i);
                    out.add(j);
                }
            }
        }
        return out;
    }

    static Color colour(String polarity) {
        return polarity.equals("forbid") ? new Color(200, 40, 40) : new Color(30, 140, 60);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (layout.nodes().isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.drawString("Open the scenario's constraints to see its sheets, values and bonds.", MARGIN, MARGIN + 12);
                return;
            }
            for (Drawn d : layout.bonds()) {
                g2.setColor(colour(d.polarity()));
                float width = d.conflicting() ? 3.5f : 1.8f;
                g2.setStroke(d.gated() ? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[]{8, 5}, 0)
                        : new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (d.hub() == null) {
                    g2.draw(new Line2D.Double(d.points().get(0), d.points().get(1)));
                } else {
                    for (Point2D p : d.points()) {
                        g2.draw(new Line2D.Double(p, d.hub()));
                    }
                    Path2D diamond = new Path2D.Double();
                    diamond.moveTo(d.hub().getX(), d.hub().getY() - 8);
                    diamond.lineTo(d.hub().getX() + 8, d.hub().getY());
                    diamond.lineTo(d.hub().getX(), d.hub().getY() + 8);
                    diamond.lineTo(d.hub().getX() - 8, d.hub().getY());
                    diamond.closePath();
                    g2.fill(diamond);
                }
            }
            g2.setStroke(new BasicStroke(1.2f));
            Font bold = new Font(Font.SANS_SERIF, Font.BOLD, 12);
            Font plain = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            Font small = new Font(Font.SANS_SERIF, Font.ITALIC, 10);
            for (Map.Entry<String, Rectangle2D> h : layout.headers().entrySet()) {
                g2.setFont(bold);
                g2.setColor(Color.BLACK);
                Rectangle2D r = h.getValue();
                g2.drawString(clip(h.getKey()), (float) r.getX(), (float) (r.getY() + 14));
                if (optional.contains(h.getKey())) {
                    g2.setFont(small);
                    g2.setColor(new Color(110, 110, 110));
                    g2.drawString("optional", (float) r.getX(), (float) (r.getY() + 25));
                }
            }
            g2.setFont(plain);
            FontMetrics fm = g2.getFontMetrics();
            for (Map<String, Rectangle2D> column : layout.nodes().values()) {
                for (Map.Entry<String, Rectangle2D> n : column.entrySet()) {
                    Rectangle2D r = n.getValue();
                    RoundRectangle2D box = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 10, 10);
                    g2.setColor(new Color(250, 250, 240));
                    g2.fill(box);
                    g2.setColor(new Color(90, 90, 90));
                    g2.draw(box);
                    g2.setColor(Color.BLACK);
                    String label = clip(n.getKey());
                    g2.drawString(label, (float) (r.getCenterX() - fm.stringWidth(label) / 2.0), (float) (r.getCenterY() + fm.getAscent() / 2.0 - 2));
                }
            }
        } finally {
            g2.dispose();
        }
    }

    private static String clip(String text) {
        String one = text.replace('\n', ' ');
        return one.length() <= LABEL_CHARS ? one : one.substring(0, LABEL_CHARS - 1) + "…";
    }
}
