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

import org.ghyura.cf.desktop.session.SieveDocument;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.RuleValue;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;
import java.util.function.*;

import static org.ghyura.cf.desktop.session.SieveDocument.*;

/** A lightweight view of executable rules. Coordinates are presentation only; the sidecar remains the authority. */
@SuppressWarnings("serial") // Swing state is never serialized; layout has an explicit JSON format.
final class SieveCanvas extends JComponent {
    record Value(String sheet, String value) { }
    enum Mode { IDLE, PAN, DRAG_NODE, DRAW_LINK }
    private static final double WIDTH = 160, HEIGHT = 36;
    private static final int MAX_NODES = 2500, MAX_POSITIONS = 5000;
    private record Node(String key, String title, String detail, Value value, int rule, boolean header) { }
    private record Edge(String from, String to, int rule) { }
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<String, Point2D.Double> positions = new LinkedHashMap<>();
    private final Map<String, Node> byKey = new LinkedHashMap<>();
    private final Set<Value> chosen = new LinkedHashSet<>();
    private final IntPredicate select;
    private final BiConsumer<Value, Value> link;
    private final Consumer<String> layoutChanged;
    private final Runnable delete;
    private double zoom = 1, panX = 24, panY = 24;
    private Mode mode = Mode.IDLE;
    private boolean space, fitPending;
    private Node dragged;
    private Point last;
    private Point2D.Double pointer;
    private String beforeDrag;
    private int selected = -1;
    private String note = "Open a scenario to see its values and constraints.";

    SieveCanvas(IntPredicate select, BiConsumer<Value, Value> link, Runnable delete, Consumer<String> layoutChanged) {
        this.select = select; this.link = link; this.delete = delete; this.layoutChanged = layoutChanged;
        setFont(UIManager.getFont("Label.font"));
        setForeground(UIManager.getColor("Label.foreground")); setBackground(UIManager.getColor("Panel.background"));
        setFocusable(true); setOpaque(true); setPreferredSize(new Dimension(700, 520));
        getAccessibleContext().setAccessibleName("Sieve constraint graph");
        getAccessibleContext().setAccessibleDescription("Drag cards to arrange them. Drag a value's right pin to another value to create a pair. Shift-click values to build a group. The rule list and matrix provide keyboard alternatives.");
        setToolTipText("");
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { press(e); }
            @Override public void mouseDragged(MouseEvent e) { drag(e); }
            @Override public void mouseReleased(MouseEvent e) { release(e); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { zoomAt(e.getPoint(), Math.pow(1.12, -e.getPreciseWheelRotation())); }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
        addComponentListener(new ComponentAdapter() { @Override public void componentResized(ComponentEvent e) { if (fitPending) fit(); } });
        addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { space = false; cancelGesture(); } });
        key("pressed SPACE", "pan-on", () -> space = true); key("released SPACE", "pan-off", () -> space = false);
        key("ESCAPE", "cancel", () -> { cancelGesture(); chosen.clear(); repaint(); });
        key("DELETE", "delete-rule", () -> { if (selected >= 0) delete.run(); });
        key("EQUALS", "zoom-in", () -> zoomAt(center(), 1.2)); key("MINUS", "zoom-out", () -> zoomAt(center(), 1 / 1.2));
        key("0", "fit", this::fit);
    }

    @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() { };
        return accessibleContext;
    }

    private void key(String stroke, String name, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(stroke), name);
        getActionMap().put(name, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { action.run(); } });
    }
    Mode mode() { return mode; }
    List<Value> chosenValues() { return List.copyOf(chosen); }
    void clearChosen() { chosen.clear(); repaint(); }
    void select(int rule) { selected = rule; repaint(); }
    String note() { return note; }

    void show(SieveDocument document, Map<String, List<String>> values, Set<String> optional, String filter) {
        cancelGesture(); nodes.clear(); edges.clear(); byKey.clear();
        String query = filter.strip().toLowerCase(Locale.ROOT);
        int col = 0, omitted = 0;
        for (var sheet : values.entrySet()) {
            if (nodes.size() >= MAX_NODES) { omitted += 1 + sheet.getValue().size(); continue; }
            String key = sheetKey(sheet.getKey()); double x = col++ * 205;
            add(new Node(key, sheet.getKey(), optional.contains(sheet.getKey()) ? "optional sheet" : "sheet", null, -1, true), x, 0);
            int row = 1;
            for (String value : sheet.getValue()) {
                if (!query.isEmpty() && !(sheet.getKey() + " " + value).toLowerCase(Locale.ROOT).contains(query)) continue;
                if (nodes.size() >= MAX_NODES) { omitted++; continue; }
                Value v = new Value(sheet.getKey(), value);
                add(new Node(valueKey(v), value, sheet.getKey(), v, -1, false), x, row++ * 51);
            }
        }
        Map<String, Integer> ids = new HashMap<>();
        List<RuleValue.MapValue> rules = document.rules();
        for (int i = 0; i < rules.size(); i++) {
            var rule = rules.get(i); String title = SieveDocument.label(rule, i), kind = kind(rule);
            int occurrence = ids.merge(title, 1, Integer::sum);
            if (!query.isEmpty() && !ConstraintBonds.pretty(rule).toLowerCase(Locale.ROOT).contains(query)) continue;
            if (nodes.size() >= MAX_NODES) { omitted++; continue; }
            String key = stableKey("rule", title + ":" + occurrence);
            String polarity = text(rule.entries().getOrDefault("polarity", new RuleValue.Text(kind.equals("assert") ? "require" : "forbid")));
            String detail = (kind.equals("mapping") ? "mapping" : polarity) + " · " + switch (kind) {
                case "pairs" -> SieveDocument.list(rule.entries().get("pairs")).size() + " tuple row(s)";
                case "sets" -> "value sets (product)";
                case "when" -> "formula";
                case "mapping" -> "allowed mapping";
                default -> "assertion";
            };
            add(new Node(key, title, detail, null, i, false), col * 205 + (i % 2) * 195, 25 + (i / 2) * 72);
            Set<String> endpoints = new LinkedHashSet<>();
            for (var bond : ConstraintBonds.read(object("constraints", new RuleValue.ListValue(List.of(rule)))).bonds())
                bond.sides().forEach((s, vs) -> {
                    if (vs.isEmpty()) endpoints.add(sheetKey(s));
                    else for (var v : vs) endpoints.add(v instanceof RuleValue.Text t ? valueKey(new Value(s, t.value())) : sheetKey(s));
                });
            var mapping = map(rule.entries().get("mapping"));
            if (!mapping.isEmpty()) {
                map(mapping.get("allow")).forEach((s, ts) -> {
                    endpoints.add(valueKey(new Value(text(mapping.get("source")), s)));
                    for (var t : SieveDocument.list(ts)) if (t instanceof RuleValue.Text target) endpoints.add(valueKey(new Value(text(mapping.get("target")), target.value())));
                });
            }
            for (String s : SieveDocument.sheets(rule)) if (!kind.equals("pairs") && !kind.equals("sets") || rule.entries().containsKey("condition")) endpoints.add(sheetKey(s));
            for (String endpoint : endpoints) if (byKey.containsKey(endpoint)) edges.add(new Edge(endpoint, key, i));
        }
        if (query.isEmpty()) positions.keySet().retainAll(byKey.keySet());
        if (positions.size() > MAX_POSITIONS) {
            var iterator = positions.keySet().iterator();
            while (positions.size() > MAX_POSITIONS && iterator.hasNext()) if (!byKey.containsKey(iterator.next())) iterator.remove();
        }
        chosen.removeIf(v -> !byKey.containsKey(valueKey(v)));
        note = nodes.size() + " cards · " + rules.size() + " rules. " + (omitted > 0 ? omitted + " cards hidden; narrow the graph search. " : "")
                + "Links show rule membership; tuple rows remain alternatives. Shift-click values for a group.";
        repaint();
    }

    private void add(Node node, double x, double y) {
        nodes.add(node); byKey.put(node.key(), node); positions.putIfAbsent(node.key(), new Point2D.Double(x, y));
    }
    private static String sheetKey(String sheet) { return stableKey("sheet", sheet); }
    private static String valueKey(Value value) { return stableKey("value", ConstraintBonds.pythonJson(texts(List.of(value.sheet(), value.value())), false, false)); }
    private static String stableKey(String kind, String value) {
        try { return kind + ":" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private Rectangle2D.Double bounds(Node node) { var p = positions.get(node.key()); return new Rectangle2D.Double(p.x, p.y, WIDTH, HEIGHT); }
    private Point2D.Double pin(Node node) { var p = positions.get(node.key()); return new Point2D.Double(p.x + WIDTH, p.y + HEIGHT / 2); }
    Point2D.Double world(Point point) { return new Point2D.Double((point.x - panX) / zoom, (point.y - panY) / zoom); }
    Point screen(Point2D point) { return new Point((int) Math.round(point.getX() * zoom + panX), (int) Math.round(point.getY() * zoom + panY)); }
    private Point center() { return new Point(getWidth() / 2, getHeight() / 2); }
    void zoomAt(Point anchor, double factor) {
        if (!isEnabled()) return;
        Point2D before = world(anchor); zoom = Math.max(.2, Math.min(3, zoom * factor));
        panX = anchor.x - before.getX() * zoom; panY = anchor.y - before.getY() * zoom; repaint();
    }
    void fit() {
        if (nodes.isEmpty()) return;
        if (getWidth() < 100 || getHeight() < 100) { fitPending = true; return; }
        fitPending = false;
        Rectangle2D box = null;
        for (Node n : nodes) { if (box == null) box = bounds(n); else box.add(bounds(n)); }
        zoom = Math.max(.2, Math.min(1.5, Math.min(Math.max(200, getWidth() - 60) / box.getWidth(), Math.max(150, getHeight() - 60) / box.getHeight())));
        panX = 30 - box.getX() * zoom; panY = 30 - box.getY() * zoom; repaint();
    }
    void focusRule(int rule) {
        for (Node node : nodes) if (node.rule() == rule) { var b = bounds(node); panX = getWidth() / 2.0 - b.getCenterX() * zoom; panY = getHeight() / 2.0 - b.getCenterY() * zoom; repaint(); return; }
    }
    void arrange() {
        String before = layoutJson(); int col = 0; Map<String, Integer> sheetColumns = new LinkedHashMap<>(), rows = new HashMap<>(); int rule = 0;
        for (Node node : nodes) {
            if (node.header()) { sheetColumns.put(node.title(), col); positions.put(node.key(), new Point2D.Double(col++ * 205, 0)); }
            else if (node.value() != null) { int row = rows.merge(node.value().sheet(), 1, Integer::sum); positions.put(node.key(), new Point2D.Double(sheetColumns.get(node.value().sheet()) * 205, row * 51)); }
        }
        for (Node node : nodes) if (node.rule() >= 0) { positions.put(node.key(), new Point2D.Double(col * 205 + (rule % 2) * 195, 25 + (rule / 2) * 72)); rule++; }
        fit(); layoutChanged.accept(before);
    }

    private Node hit(Point2D point) {
        for (int i = nodes.size() - 1; i >= 0; i--) { Node n = nodes.get(i); if (bounds(n).contains(point) || n.value() != null && pin(n).distance(point) <= 8 / zoom) return n; }
        return null;
    }
    private CubicCurve2D curve(Edge edge) {
        Point2D a = pin(byKey.get(edge.from())); var b = positions.get(edge.to()); double x = b.x, y = b.y + HEIGHT / 2;
        double bend = Math.max(45, Math.abs(x - a.getX()) * .45);
        return new CubicCurve2D.Double(a.getX(), a.getY(), a.getX() + bend, a.getY(), x - bend, y, x, y);
    }
    private void press(MouseEvent e) {
        if (!isEnabled()) return;
        requestFocusInWindow(); cancelGesture(); last = e.getPoint(); pointer = world(last);
        if (space || SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isRightMouseButton(e)) { mode = Mode.PAN; return; }
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        Node node = hit(pointer);
        if (node != null) {
            if (node.rule() >= 0 && !select.test(node.rule())) return;
            if (node.value() != null && e.isShiftDown()) { if (!chosen.add(node.value())) chosen.remove(node.value()); repaint(); return; }
            dragged = node;
            if (node.value() != null && pin(node).distance(pointer) <= 10 / zoom) mode = Mode.DRAW_LINK;
            else { mode = Mode.DRAG_NODE; beforeDrag = layoutJson(); }
            return;
        }
        for (int i = edges.size() - 1; i >= 0; i--) if (new BasicStroke((float) (10 / zoom)).createStrokedShape(curve(edges.get(i))).contains(pointer)) {
            select.test(edges.get(i).rule()); return;
        }
        mode = Mode.PAN;
    }
    private void drag(MouseEvent e) {
        if (!isEnabled() || mode == Mode.IDLE || last == null) return;
        if (mode == Mode.PAN) { panX += e.getX() - last.x; panY += e.getY() - last.y; }
        else if (mode == Mode.DRAG_NODE) { var p = positions.get(dragged.key()); p.x += (e.getX() - last.x) / zoom; p.y += (e.getY() - last.y) / zoom; }
        pointer = world(e.getPoint()); last = e.getPoint(); repaint();
    }
    private void release(MouseEvent e) {
        Mode finished = mode; Node start = dragged, end = hit(world(e.getPoint())); String before = beforeDrag;
        mode = Mode.IDLE; dragged = null; beforeDrag = null; last = null; pointer = null; repaint();
        if (finished == Mode.DRAW_LINK && start != null && end != null && end.value() != null && !start.value().sheet().equals(end.value().sheet())) link.accept(start.value(), end.value());
        if (finished == Mode.DRAG_NODE && before != null && !before.equals(layoutJson())) layoutChanged.accept(before);
    }
    void cancelGesture() {
        if (mode == Mode.DRAG_NODE && beforeDrag != null) restoreLayout(beforeDrag);
        mode = Mode.IDLE; dragged = null; beforeDrag = null; last = null; pointer = null; repaint();
    }

    void clearLayout() { positions.clear(); zoom = 1; panX = panY = 24; }

    String layoutJson() {
        Map<String, RuleValue> layout = new LinkedHashMap<>(); positions.forEach((k, p) -> layout.put(k, object("x", new RuleValue.Real(p.x), "y", new RuleValue.Real(p.y))));
        return ConstraintBonds.pretty(object("version", RuleValue.Int.of(1), "positions", new RuleValue.MapValue(layout), "zoom", new RuleValue.Real(zoom), "panX", new RuleValue.Real(panX), "panY", new RuleValue.Real(panY))) + "\n";
    }
    void restoreLayout(String json) {
        if (json == null || json.isBlank()) return;
        if (json.length() > 4 * 1024 * 1024) throw new IllegalArgumentException("Layout exceeds 4 MiB.");
        var layout = map(ConstraintBonds.parse(json, "Sieve layout"));
        if (!(layout.get("version") instanceof RuleValue.Int n) || !n.value().equals(java.math.BigInteger.ONE)) throw new IllegalArgumentException("Unsupported Sieve layout version.");
        Map<String, Point2D.Double> next = new LinkedHashMap<>();
        map(layout.get("positions")).forEach((key, pos) -> next.put(key, new Point2D.Double(number(map(pos).get("x")), number(map(pos).get("y")))));
        double z = number(layout.get("zoom")), x = number(layout.get("panX")), y = number(layout.get("panY"));
        if (z < .2 || z > 3 || next.size() > MAX_POSITIONS) throw new IllegalArgumentException("Layout is outside the editor limits.");
        positions.putAll(next); zoom = z; panX = x; panY = y; repaint();
    }
    private static double number(RuleValue value) {
        double result = value instanceof RuleValue.Real r ? r.value() : value instanceof RuleValue.Int n ? n.value().doubleValue() : Double.NaN;
        if (!Double.isFinite(result) || Math.abs(result) > 10_000_000) throw new IllegalArgumentException("Invalid layout coordinate."); return result;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(color("Panel.background", getBackground())); g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.translate(panX, panY); g.scale(zoom, zoom);
            Rectangle2D visible = new Rectangle2D.Double(-panX / zoom, -panY / zoom, getWidth() / zoom, getHeight() / zoom);
            for (Edge edge : edges) {
                CubicCurve2D curve = curve(edge); if (!curve.getBounds2D().intersects(visible)) continue;
                g.setColor(edge.rule() == selected ? color("Component.focusColor", getForeground()) : color("Separator.foreground", getForeground()));
                g.setStroke(new BasicStroke((float) ((edge.rule() == selected ? 2.5 : 1.2) / zoom))); g.draw(curve);
            }
            for (Node node : nodes) {
                Rectangle2D box = bounds(node); if (!box.intersects(visible)) continue;
                boolean active = node.rule() >= 0 && node.rule() == selected || chosen.contains(node.value());
                g.setColor(color(active ? "List.selectionBackground" : node.header() ? "Button.background" : "TextField.background", getBackground()));
                g.fill(new RoundRectangle2D.Double(box.getX(), box.getY(), WIDTH, HEIGHT, 10, 10));
                g.setColor(color(active ? "Component.focusColor" : "Component.borderColor", getForeground()));
                g.setStroke(new BasicStroke((float) (1.2 / zoom))); g.draw(new RoundRectangle2D.Double(box.getX(), box.getY(), WIDTH, HEIGHT, 10, 10));
                g.setColor(color(active ? "List.selectionForeground" : "Label.foreground", getForeground()));
                g.setFont(getFont().deriveFont(node.header() ? Font.BOLD : Font.PLAIN, 12f));
                g.drawString(trim(g, node.title(), 142), (float) box.getX() + 8, (float) box.getY() + 15);
                g.setFont(getFont().deriveFont(10f)); g.drawString(trim(g, node.detail(), 142), (float) box.getX() + 8, (float) box.getY() + 29);
                if (node.value() != null) { Point2D p = pin(node); g.fill(new Ellipse2D.Double(p.getX() - 4, p.getY() - 4, 8, 8)); }
            }
            if (mode == Mode.DRAW_LINK && dragged != null && pointer != null) {
                Point2D p = pin(dragged); g.setColor(color("Component.focusColor", getForeground())); g.setStroke(new BasicStroke((float) (2 / zoom)));
                g.draw(new CubicCurve2D.Double(p.getX(), p.getY(), p.getX() + 70, p.getY(), pointer.x - 70, pointer.y, pointer.x, pointer.y));
            }
        } finally { g.dispose(); }
    }
    private static String trim(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) return text;
        int end = Math.min(text.length(), 80); while (end > 0 && g.getFontMetrics().stringWidth(text.substring(0, end) + "…") > width) end--;
        return text.substring(0, end) + "…";
    }
    private Color color(String key, Color fallback) { Color c = UIManager.getColor(key); return c != null ? c : fallback != null ? fallback : UIManager.getColor("Label.foreground"); }
    @Override public String getToolTipText(MouseEvent event) {
        Node node = hit(world(event.getPoint())); return node == null ? "Wheel: zoom · empty space/Space: pan · right pin: draw pair · Shift-click: select group · 0: fit"
                : node.title() + " · " + node.detail() + (node.value() != null ? " · drag the right pin to link" : "");
    }
}
