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

import org.ghyura.cf.input.SeqGraph;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The FW_Seq dependency graph drawn as Graphviz would draw its DOT text from left to right: a node's dependencies stand in
 * columns to its left (operand sheets, then the braces that join them, then the sheets they produce), slots as rounded
 * boxes and braces as boxes with the DOT label's tags, edges as curves labelled with their kind. Excluded slots have a
 * dashed border, passive data sheets a grey fill, join results a bold border; a node that an edge names but no sheet or
 * brace declares is drawn in red, as are the nodes of error-level issues (warnings in orange). The geometry is a pure
 * function of the graph ({@link #layout(SeqGraph)}).
 */
final class GraphView extends JPanel {

    private static final long serialVersionUID = 1L;
    static final int MARGIN = 24;
    static final int COLUMN_GAP = 90;
    static final int ROW_GAP = 22;
    static final int CHAR_WIDTH = 8;
    static final int LINE_HEIGHT = 16;

    /** A placed node: its id, kind, tags, whether only an edge names it, and its box. */
    record Placed(String id, String kind, List<String> tags, boolean missing, boolean passive, boolean excluded, boolean joinResult,
                  int column, Rectangle2D box) {
    }

    /** Where everything goes: the nodes by id and the size the drawing needs. */
    record Layout(Map<String, Placed> nodes, List<SeqGraph.Edge> edges, Dimension size) {
    }

    private transient SeqGraph graph;
    private transient Layout layout;
    private transient Map<String, Color> marked = Map.of();

    GraphView() {
        setBackground(Color.WHITE);
        show(null);
    }

    /** Draws a graph (null: nothing to draw). */
    void show(SeqGraph shown) {
        graph = shown;
        layout = shown == null ? new Layout(Map.of(), List.of(), new Dimension(400, 120)) : layout(shown);
        Map<String, Color> marks = new HashMap<>();
        if (shown != null) {
            for (SeqGraph.Issue issue : shown.validate()) {
                for (String id : issue.nodes()) {
                    if (issue.level().equals(SeqGraph.ERROR) || !marks.containsKey(id)) {
                        marks.put(id, issue.level().equals(SeqGraph.ERROR) ? new Color(200, 30, 30) : new Color(220, 120, 0));
                    }
                }
            }
        }
        marked = marks;
        setPreferredSize(layout.size());
        revalidate();
        repaint();
    }

    SeqGraph graph() {
        return graph;
    }

    Layout currentLayout() {
        return layout;
    }

    /**
     * Columns by the longest chain of dependencies below a node (edges back into a node still being followed, the
     * cycles, are not followed), rows ordered by the mean row of each node's dependencies and then of its dependents.
     */
    static Layout layout(SeqGraph graph) {
        Set<String> ids = new LinkedHashSet<>(graph.nodes().keySet());
        for (SeqGraph.Edge e : graph.edges()) {
            ids.add(e.src());
            ids.add(e.dst());
        }
        Map<String, List<String>> deps = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (String id : ids) {
            deps.put(id, new ArrayList<>());
            dependents.put(id, new ArrayList<>());
        }
        for (SeqGraph.Edge e : graph.edges()) {
            deps.get(e.src()).add(e.dst());
            dependents.get(e.dst()).add(e.src());
        }
        List<String> order = new ArrayList<>(ids);
        order.sort(Comparator.naturalOrder());
        Map<String, Integer> column = new HashMap<>();
        Set<String> onPath = new HashSet<>();
        for (String id : order) {
            columnOf(id, deps, column, onPath);
        }
        int columns = column.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        List<List<String>> byColumn = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            byColumn.add(new ArrayList<>());
        }
        for (String id : order) {
            byColumn.get(column.get(id)).add(id);
        }
        Map<String, Double> row = new HashMap<>();
        for (List<String> col : byColumn) {
            for (int i = 0; i < col.size(); i++) {
                row.put(col.get(i), (double) i);
            }
        }
        for (int sweep = 0; sweep < 2; sweep++) {
            for (int c = 1; c < columns; c++) {
                sortByMean(byColumn.get(c), deps, row);
            }
            for (int c = columns - 2; c >= 0; c--) {
                sortByMean(byColumn.get(c), dependents, row);
            }
        }
        Map<String, Placed> placed = new LinkedHashMap<>();
        int x = MARGIN;
        int height = 0;
        for (int c = 0; c < columns; c++) {
            int width = 0;
            for (String id : byColumn.get(c)) {
                width = Math.max(width, boxWidth(graph, id));
            }
            int y = MARGIN;
            for (String id : byColumn.get(c)) {
                SeqGraph.Node node = graph.nodes().get(id);
                List<String> tags = node == null ? List.of() : SeqGraph.tags(node);
                int h = LINE_HEIGHT * (tags.isEmpty() ? 1 : 2) + 12;
                placed.put(id, new Placed(id, node == null ? "missing" : node.kind(), tags, node == null, node != null && node.is("passive"),
                        node != null && node.is("excluded"), node != null && node.is("is_join_result"), c,
                        new Rectangle2D.Double(x + (width - boxWidth(graph, id)) / 2.0, y, boxWidth(graph, id), h)));
                y += h + ROW_GAP;
            }
            height = Math.max(height, y);
            x += width + COLUMN_GAP;
        }
        return new Layout(placed, graph.edges(), new Dimension(Math.max(400, x - COLUMN_GAP + MARGIN), Math.max(120, height + MARGIN)));
    }

    private static int columnOf(String id, Map<String, List<String>> deps, Map<String, Integer> column, Set<String> onPath) {
        Integer known = column.get(id);
        if (known != null) {
            return known;
        }
        onPath.add(id);
        int c = 0;
        for (String d : deps.get(id)) {
            if (!onPath.contains(d)) {
                c = Math.max(c, columnOf(d, deps, column, onPath) + 1);
            }
        }
        onPath.remove(id);
        column.put(id, c);
        return c;
    }

    private static void sortByMean(List<String> col, Map<String, List<String>> neighbours, Map<String, Double> row) {
        Map<String, Double> mean = new HashMap<>();
        for (String id : col) {
            mean.put(id, neighbours.get(id).stream().mapToDouble(n -> row.getOrDefault(n, 0.0)).average().orElse(row.get(id)));
        }
        col.sort(Comparator.comparing((String id) -> mean.get(id)).thenComparing(Comparator.naturalOrder()));
        for (int i = 0; i < col.size(); i++) {
            row.put(col.get(i), (double) i);
        }
    }

    private static int boxWidth(SeqGraph graph, String id) {
        SeqGraph.Node node = graph.nodes().get(id);
        String label = node == null ? "missing: " + id : id;
        int chars = label.length();
        if (node != null && !SeqGraph.tags(node).isEmpty()) {
            chars = Math.max(chars, String.join(",", SeqGraph.tags(node)).length() + 2);
        }
        return Math.min(320, chars * CHAR_WIDTH + 28);
    }

    // ---- painting ------------------------------------------------------------------------------------------------

    static Color edgeColor(String kind) {
        return switch (kind) {
            case "produces" -> new Color(30, 90, 200);
            case "brace_operand" -> new Color(20, 130, 60);
            case "brace_nested_operand" -> new Color(90, 170, 60);
            case "cartes" -> new Color(210, 120, 0);
            case "separator" -> new Color(130, 60, 170);
            default -> new Color(140, 140, 140);
        };
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
                g2.drawString(graph == null ? "Validate the scenario to see its FW_Seq dependency graph." : "The FW_Seq program has no sheets.",
                        MARGIN, MARGIN + 12);
                return;
            }
            Font plain = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
            List<Rectangle2D> labels = new ArrayList<>();
            for (SeqGraph.Edge e : layout.edges()) {
                drawEdge(g2, e, small, labels);
            }
            for (Placed p : layout.nodes().values()) {
                drawNode(g2, p, plain, small);
            }
        } finally {
            g2.dispose();
        }
    }

    private void drawEdge(Graphics2D g2, SeqGraph.Edge e, Font small, List<Rectangle2D> labels) {
        Placed from = layout.nodes().get(e.src());
        Placed to = layout.nodes().get(e.dst());
        Rectangle2D a = from.box();
        Rectangle2D b = to.box();
        boolean forward = from.column() > to.column();
        Point2D start = forward ? new Point2D.Double(a.getMinX(), a.getCenterY()) : new Point2D.Double(a.getMaxX(), a.getCenterY());
        Point2D end = forward ? new Point2D.Double(b.getMaxX(), b.getCenterY()) : new Point2D.Double(b.getMaxX(), b.getCenterY());
        double bend = forward ? Math.max(30, (start.getX() - end.getX()) / 2) : 60 + 10 * Math.abs(from.column() - to.column());
        CubicCurve2D curve = forward
                ? new CubicCurve2D.Double(start.getX(), start.getY(), start.getX() - bend, start.getY(), end.getX() + bend, end.getY(),
                        end.getX(), end.getY())
                : new CubicCurve2D.Double(start.getX(), start.getY(), start.getX() + bend, start.getY() + bend, end.getX() + bend,
                        end.getY() + bend, end.getX(), end.getY());
        Color color = edgeColor(e.kind());
        Stroke previous = g2.getStroke();
        g2.setColor(color);
        g2.setStroke(switch (e.kind()) {
            case "brace_ref" -> new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0);
            case "brace_nested_operand" -> new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{7, 4}, 0);
            default -> new BasicStroke(1.6f);
        });
        g2.draw(curve);
        g2.setStroke(previous);
        // the arrowhead points at the dependency, as the DOT edge does
        double angle = Math.atan2(end.getY() - curve.getCtrlY2(), end.getX() - curve.getCtrlX2());
        Path2D head = new Path2D.Double();
        head.moveTo(end.getX(), end.getY());
        head.lineTo(end.getX() - 9 * Math.cos(angle - 0.4), end.getY() - 9 * Math.sin(angle - 0.4));
        head.lineTo(end.getX() - 9 * Math.cos(angle + 0.4), end.getY() - 9 * Math.sin(angle + 0.4));
        head.closePath();
        g2.fill(head);
        g2.setFont(small);
        double mx = 0.125 * curve.getX1() + 0.375 * curve.getCtrlX1() + 0.375 * curve.getCtrlX2() + 0.125 * curve.getX2();
        double my = 0.125 * curve.getY1() + 0.375 * curve.getCtrlY1() + 0.375 * curve.getCtrlY2() + 0.125 * curve.getY2();
        FontMetrics fm = g2.getFontMetrics();
        // parallel edges share a midpoint: a label moves down until it covers no label already drawn
        Rectangle2D label = new Rectangle2D.Double(mx - fm.stringWidth(e.kind()) / 2.0 - 2, my - fm.getAscent(), fm.stringWidth(e.kind()) + 4,
                fm.getHeight());
        for (int tries = 0; tries < 6 && labels.stream().anyMatch(label::intersects); tries++) {
            label.setRect(label.getX(), label.getY() + fm.getHeight(), label.getWidth(), label.getHeight());
        }
        labels.add(label);
        g2.setColor(new Color(255, 255, 255, 210));
        g2.fill(label);
        g2.setColor(color.darker());
        g2.drawString(e.kind(), (float) (label.getX() + 2), (float) (label.getY() + fm.getAscent()));
    }

    private void drawNode(Graphics2D g2, Placed p, Font plain, Font small) {
        Rectangle2D box = p.box();
        java.awt.Shape shape = p.kind().equals("brace") ? box
                : new RoundRectangle2D.Double(box.getX(), box.getY(), box.getWidth(), box.getHeight(), box.getHeight(), box.getHeight());
        g2.setColor(p.missing() ? new Color(255, 235, 235) : p.passive() ? new Color(232, 232, 232)
                : p.kind().equals("brace") ? new Color(232, 240, 255) : new Color(255, 252, 235));
        g2.fill(shape);
        Color mark = marked.get(p.id());
        g2.setColor(p.missing() ? new Color(200, 30, 30) : mark != null ? mark : new Color(60, 60, 60));
        g2.setStroke(p.missing() || p.excluded() ? new BasicStroke(p.joinResult() ? 2.6f : 1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                new float[]{6, 4}, 0) : new BasicStroke(p.joinResult() || mark != null ? 2.6f : 1.4f));
        g2.draw(shape);
        g2.setColor(Color.BLACK);
        g2.setFont(plain);
        FontMetrics fm = g2.getFontMetrics();
        String label = p.missing() ? "missing: " + p.id() : p.id();
        double top = box.getY() + 6 + fm.getAscent();
        g2.drawString(label, (float) (box.getCenterX() - fm.stringWidth(label) / 2.0), (float) top);
        if (!p.tags().isEmpty()) {
            g2.setFont(small);
            g2.setColor(new Color(90, 90, 90));
            String tags = "[" + String.join(",", p.tags()) + "]";
            FontMetrics sm = g2.getFontMetrics();
            g2.drawString(tags, (float) (box.getCenterX() - sm.stringWidth(tags) / 2.0), (float) (top + LINE_HEIGHT));
        }
    }
}
