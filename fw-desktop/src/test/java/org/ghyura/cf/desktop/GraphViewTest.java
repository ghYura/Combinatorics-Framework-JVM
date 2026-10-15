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

import org.ghyura.cf.input.InputFormat;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.input.SeqGraph;
import org.ghyura.cf.input.WorkbookFormats;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drawing of the FW_Seq dependency graph: its geometry is a pure function of the graph, and every graph draws. */
class GraphViewTest {

    private static SeqGraph graph(String json) {
        return SeqGraph.ofWorkbook(WorkbookFormats.reader(InputFormat.JSON).read(InputSource.ofText("graph.json", json)).workbook());
    }

    private static final String NESTED = """
            {"sheets": {"FW_Seq": [["A", "FW_Exclude", "FW_Reuse", "FW_Combi(1)"], ["B", "FW_Exclude", "FW_Combi(1)"],
              ["AB", "FW_Exclude", "FW_(,,A,,B,,,,1:1)"], ["C", "FW_Exclude", "FW_Combi(2)"], ["ABC", "FW_(,,FW_(),,C,,SEP,,M:N)"],
              ["X", "FW_Cartes(Y)"]],
             "A": [["a1"], ["a2"]], "B": [["b1"]], "AB": [["x"]], "C": [["c1"], ["c2"]], "ABC": [["y"]], "SEP": [[" | "]],
             "X": [["x1"]], "Y": [["y1"]]}}""";

    private static final String BROKEN = """
            {"sheets": {"FW_Seq": [["A", "FW_Cartes(B)"], ["B", "FW_Cartes(A)"], ["C", "FW_Combi(1)"], ["J1", "FW_(,,C,,NOPE,,,,1:1)"],
              ["J2", "FW_(,,FW_(),,FW_()G,,,,M:1)"], ["D", "FW_Exclude", "FW_Combi(1)"]],
             "A": [["a"]], "B": [["b"]], "C": [["c"]], "D": [["d"]], "J1": [["j"]], "J2": [["j"]]}}""";

    @Test
    void dependenciesStandLeftOfWhatIsComputedFromThemAndNothingOverlaps() {
        SeqGraph g = graph(NESTED);
        GraphView.Layout layout = GraphView.layout(g);
        assertEquals(g.nodes().keySet(), layout.nodes().keySet(), "no edge names an undeclared node here");
        for (SeqGraph.Edge e : g.edges()) {
            assertTrue(layout.nodes().get(e.src()).column() > layout.nodes().get(e.dst()).column(), e.toString());
        }
        // operand sheets, the brace joining them, the sheet it produces, the brace reading that, the sheet it produces
        assertEquals(List.of(0, 1, 2, 3, 4), List.of(layout.nodes().get("A").column(), layout.nodes().get("brace#0.1").column(),
                layout.nodes().get("AB").column(), layout.nodes().get("brace#1.1").column(), layout.nodes().get("ABC").column()));
        List<Rectangle2D> boxes = new ArrayList<>(layout.nodes().values().stream().map(GraphView.Placed::box).toList());
        for (int i = 0; i < boxes.size(); i++) {
            assertTrue(boxes.get(i).getMaxX() <= layout.size().width && boxes.get(i).getMaxY() <= layout.size().height, boxes.get(i).toString());
            for (int j = i + 1; j < boxes.size(); j++) {
                assertFalse(boxes.get(i).intersects(boxes.get(j)), boxes.get(i) + " " + boxes.get(j));
            }
        }
        assertEquals(layout, GraphView.layout(g), "the same graph is laid out the same way");
        assertTrue(layout.nodes().get("SEP").passive() && layout.nodes().get("A").excluded() && layout.nodes().get("ABC").joinResult());
    }

    @Test
    void cyclesAndUndeclaredOperandsAreDrawnAndMarked() throws Exception {
        SeqGraph g = graph(BROKEN);
        GraphView.Layout layout = GraphView.layout(g);
        assertTrue(layout.nodes().get("NOPE").missing() && !layout.nodes().get("C").missing(), layout.nodes().keySet().toString());
        assertTrue(layout.nodes().containsKey("@missing_nested_prior:1:4"), "the second nested marker has no earlier brace target");
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            GraphView view = new GraphView();
            view.show(g);
            view.setSize(view.getPreferredSize());
            BufferedImage img = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            view.paint(g2);
            g2.dispose();
            image.set(img);
        });
        Rectangle2D missing = layout.nodes().get("NOPE").box();
        int border = image.get().getRGB((int) missing.getCenterX(), (int) Math.round(missing.getMinY()));
        assertTrue(((border >> 16) & 0xFF) > 150 && ((border >> 8) & 0xFF) < 100, "an undeclared operand is outlined in red: "
                + Integer.toHexString(border));
        SwingUtilities.invokeAndWait(() -> {
            GraphView empty = new GraphView();
            empty.setSize(empty.getPreferredSize());
            BufferedImage img = new BufferedImage(empty.getWidth(), empty.getHeight(), BufferedImage.TYPE_INT_RGB);
            empty.paint(img.createGraphics());
        });
    }
}
