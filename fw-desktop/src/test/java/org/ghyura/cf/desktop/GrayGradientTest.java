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

import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The author's request: in guif every output, read-only text and read-only tables, shows on a grayscale gradient. */
class GrayGradientTest {

    private LookAndFeel previous;
    private String nativeSetting;
    private String animations;

    @BeforeEach
    void flatLaf() throws Exception {
        previous = UIManager.getLookAndFeel();
        nativeSetting = System.getProperty("flatlaf.useNativeLibrary");
        animations = System.getProperty("flatlaf.animation");
        edt(() -> {
            FlatDesktopLauncher.installLookAndFeel();
            return null;
        });
    }

    @AfterEach
    void restore() throws Exception {
        edt(() -> {
            UIManager.setLookAndFeel(previous);
            return null;
        });
        restore("flatlaf.useNativeLibrary", nativeSetting);
        restore("flatlaf.animation", animations);
    }

    @Test
    void guifMapsItsTextsAndTablesToTheGradientAndTheSwingDesktopKeepsItsOwn() throws Exception {
        edt(() -> {
            assertInstanceOf(GrayGradient.TextArea.class, new JTextArea().getUI());
            assertInstanceOf(GrayGradient.TextPane.class, new JTextPane().getUI());
            assertInstanceOf(GrayGradient.EditorPane.class, new JEditorPane().getUI());
            assertInstanceOf(GrayGradient.Table.class, new JTable().getUI());
            UIManager.setLookAndFeel(previous);
            assertFalse(new JTextArea().getUI() instanceof GrayGradient.TextArea, "the Swing desktop's look is untouched");
            assertFalse(new JTable().getUI() instanceof GrayGradient.Table);
            return null;
        });
    }

    @Test
    void outputsAreReadOnlyTextsAndReadOnlyTablesOnTheThemesBackground() throws Exception {
        edt(() -> {
            JTextArea text = new JTextArea();
            assertFalse(GrayGradient.output(text), "editable text is an input");
            text.setEditable(false);
            assertTrue(GrayGradient.output(text), "read-only text is an output");
            text.setBackground(Color.PINK);
            assertFalse(GrayGradient.output(text), "a colour the code chose is kept");
            assertTrue(GrayGradient.output(new JTable(new AbstractTableModel() {
                @Override public int getRowCount() { return 0; }
                @Override public int getColumnCount() { return 2; }
                @Override public Object getValueAt(int row, int column) { return null; }
            })), "a model whose cells cannot be edited");
            assertFalse(GrayGradient.output(new JTable(new DefaultTableModel(new Object[]{"a"}, 0))), "an editable model is an input");
            assertTrue(GrayGradient.output(new JTable(new DefaultTableModel(new Object[]{"a"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { throw new IndexOutOfBoundsException(row); }
            })), "a model that cannot answer for a first row it does not have counts as read-only");
            return null;
        });
    }

    @Test
    void readOnlyTextShowsAGrayGradientThatStaysWithTheVisibleArea() throws Exception {
        edt(() -> {
            JTextArea output = new JTextArea("x\n".repeat(200));
            output.setEditable(false);
            JScrollPane pane = new JScrollPane(output);
            pane.setSize(300, 200);
            HintLayerTest.layout(pane);
            JViewport viewport = pane.getViewport();
            assertEquals(JViewport.SIMPLE_SCROLL_MODE, viewport.getScrollMode(), "repainted, not copied, when it scrolls");
            Point top = SwingUtilities.convertPoint(viewport, viewport.getWidth() - 8, 2, pane);
            Point bottom = SwingUtilities.convertPoint(viewport, viewport.getWidth() - 8, viewport.getHeight() - 3, pane);
            BufferedImage first = HintLayerTest.paint(pane);
            Color upper = new Color(first.getRGB(top.x, top.y));
            Color lower = new Color(first.getRGB(bottom.x, bottom.y));
            assertGray(upper);
            assertGray(lower);
            assertTrue(upper.getRed() > lower.getRed() + 20, "light at the top, darker at the bottom: " + upper + " " + lower);
            assertTrue(upper.getRed() >= GrayGradient.BOTTOM.getRed() && lower.getRed() <= GrayGradient.TOP.getRed());
            viewport.setViewPosition(new Point(0, 600));
            HintLayerTest.layout(pane);
            BufferedImage scrolled = HintLayerTest.paint(pane);
            assertEquals(first.getRGB(top.x, top.y), scrolled.getRGB(top.x, top.y), "the gradient stays with the visible area");
            assertEquals(first.getRGB(bottom.x, bottom.y), scrolled.getRGB(bottom.x, bottom.y));

            JTextArea input = new JTextArea("x\n".repeat(3));
            JScrollPane plain = new JScrollPane(input);
            plain.setSize(300, 200);
            HintLayerTest.layout(plain);
            BufferedImage flat = HintLayerTest.paint(plain);
            JViewport v = plain.getViewport();
            Point a = SwingUtilities.convertPoint(v, v.getWidth() - 8, 2, plain);
            Point b = SwingUtilities.convertPoint(v, v.getWidth() - 8, v.getHeight() - 3, plain);
            assertEquals(flat.getRGB(a.x, a.y), flat.getRGB(b.x, b.y), "text that can be edited keeps a plain background");
            return null;
        });
    }

    @Test
    void aReadOnlyTableShowsTheGradientBehindItsEvenRowsAndBelowItsLastRowAndKeepsItsOddRowsTint() throws Exception {
        edt(() -> {
            JTable table = new JTable(new AbstractTableModel() {
                @Override public int getRowCount() { return 2; }
                @Override public int getColumnCount() { return 1; }
                @Override public Object getValueAt(int row, int column) { return "x"; }
            });
            JScrollPane pane = new JScrollPane(table);
            pane.setSize(300, 240);
            HintLayerTest.layout(pane);
            JViewport viewport = pane.getViewport();
            BufferedImage image = HintLayerTest.paint(pane);
            int x = viewport.getWidth() - 8;
            Color even = at(image, pane, viewport, x, table.getRowHeight() / 2);
            Color odd = at(image, pane, viewport, x, table.getRowHeight() + table.getRowHeight() / 2);
            Color below = at(image, pane, viewport, x, viewport.getHeight() - 3);
            assertGray(even);
            assertGray(below);
            assertTrue(even.getRed() > below.getRed() + 6, "the gradient behind the rows goes on below them: " + even + " " + below);
            assertTrue(below.getRed() >= GrayGradient.TABLE_BOTTOM.getRed(), "a table's gradient ends lighter than a text's: " + below);
            assertEquals(Color.decode(FlatDesktopLauncher.ALTERNATE_ROW), odd, "odd rows keep their tint");
            Color tint = Color.decode(FlatDesktopLauncher.ALTERNATE_ROW);
            assertTrue(CandidateCodeView.contrast(GrayGradient.TABLE_BOTTOM, Color.BLACK) > CandidateCodeView.contrast(tint, Color.BLACK),
                    "even rows stay lighter than the tinted odd rows at every height, so the stripes never fade into each other");

            JTable editable = new JTable(new DefaultTableModel(new Object[][]{{"x"}, {"y"}}, new Object[]{"a"}));
            JScrollPane plain = new JScrollPane(editable);
            plain.setSize(300, 240);
            HintLayerTest.layout(plain);
            BufferedImage flat = HintLayerTest.paint(plain);
            JViewport v = plain.getViewport();
            assertEquals(at(flat, plain, v, v.getWidth() - 8, v.getHeight() - 40), at(flat, plain, v, v.getWidth() - 8, v.getHeight() - 3),
                    "a table that can be edited keeps a plain background");
            return null;
        });
    }

    @Test
    void theSheetGridKeepsBothStripesThroughRowChangesColumnResizingAndEditing() throws Exception {
        edt(() -> {
            var document = org.ghyura.cf.desktop.session.ScenarioDocument.create();
            int sheet = document.sheetNames().indexOf("FW_Seq");
            assertTrue(sheet >= 0);
            document.setCell(sheet, 0, 0, "ITEMS");
            document.setCell(sheet, 0, 1, "FW_Combi(2)");
            document.setCell(sheet, 1, 0, "OTHER");
            SheetTableModel model = new SheetTableModel();
            model.show(document, sheet);
            JTable table = new GrayGradient.SheetTable(model);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            JScrollPane pane = new JScrollPane(table);
            pane.setSize(440, 260);
            HintLayerTest.layout(pane);
            assertSheetStripes(pane, table);
            document.insertRow(sheet, 0);
            model.show(document, sheet);
            HintLayerTest.layout(pane);
            assertEquals("ITEMS", table.getValueAt(1, 0));
            assertSheetStripes(pane, table);
            document.removeRow(sheet, 0);
            model.show(document, sheet);
            table.getColumnModel().getColumn(0).setPreferredWidth(160);
            table.getColumnModel().getColumn(0).setWidth(160);
            HintLayerTest.layout(pane);
            assertSheetStripes(pane, table);
            assertTrue(table.editCellAt(1, 1));
            var editor = (javax.swing.JTextField) table.getEditorComponent();
            assertSheetGray(editor.getBackground());
            editor.setText("FW_Permut()");
            assertTrue(table.getCellEditor().stopCellEditing());
            assertEquals("FW_Permut()", document.cell(sheet, 1, 1));
            assertSheetStripes(pane, table);
            table.changeSelection(0, 1, false, false);
            var selected = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
            assertEquals(table.getSelectionBackground(), selected.getBackground(), "selection remains recognizable");
            return null;
        });
    }

    private static void assertSheetStripes(JScrollPane pane, JTable table) {
        BufferedImage image = HintLayerTest.paint(pane);
        var first = table.getCellRect(0, 0, false);
        var column = table.getCellRect(0, 1, false);
        var row = table.getCellRect(1, 0, false);
        Color base = at(image, pane, table, first.x + first.width - 6, first.y + first.height / 2);
        Color lighter = at(image, pane, table, column.x + column.width - 6, column.y + column.height / 2);
        Color darker = at(image, pane, table, row.x + row.width - 6, row.y + row.height / 2);
        for (Color color : List.of(base, lighter, darker)) assertSheetGray(color);
        assertTrue(lighter.getRed() > base.getRed(), "alternate columns are slightly lighter");
        assertTrue(base.getRed() > darker.getRed() + 12, "alternate rows remain distinct");
        JViewport viewport = pane.getViewport();
        Color below = at(image, pane, viewport, 10, viewport.getHeight() - 3);
        assertTrue(base.getRed() > below.getRed() + 6, "the gradient continues below the sheet");
    }

    /** Every output and the authoring grid of the FlatLaf desktop is on the gradient. */
    @Test
    void everyOutputOfGuifIsOnTheGradient() throws Exception {
        Session session = Session.resolve(null, Map.of(), List.of());
        Desktop desktop = edt(() -> new Desktop(session, null, FileTrail.inMemory(), true));
        try {
            edt(() -> {
                List<String> plain = new ArrayList<>();
                int texts = 0;
                int tables = 0;
                int inputs = 0;
                for (Component c : all(desktop.component())) {
                    if (c instanceof JTextComponent text && !(c instanceof javax.swing.JTextField) && !text.isEditable()) {
                        texts++;
                        if (!GrayGradient.output(text) || !(text.getUI() instanceof GrayGradient.TextArea
                                || text.getUI() instanceof GrayGradient.TextPane || text.getUI() instanceof GrayGradient.EditorPane)) {
                            plain.add(c.getClass().getSimpleName() + ": " + Hints.own((JComponent) c));
                        }
                    }
                    if (c instanceof JTable table) {
                        assertInstanceOf(GrayGradient.Table.class, table.getUI());
                        if (GrayGradient.output(table)) tables++;
                        else inputs++;
                    }
                }
                assertEquals(List.of(), plain, "read-only texts that are not on the gradient");
                assertTrue(texts >= 15, "read-only texts found: " + texts);
                assertTrue(tables >= 10, "read-only tables found: " + tables);
                assertTrue(inputs >= 1, "other editable tables keep their normal backgrounds");
                return null;
            });
        } finally {
            edt(desktop::close).get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void theBundledAgreementOnGuifsGradientCanBeAcceptedAfterScrolling() throws Exception {
        String agreement = AiDelegation.agreement(new String(AiDelegation.bundled(), StandardCharsets.UTF_8));
        List<Boolean> accepted = new ArrayList<>();
        var window = edt(() -> {
            var content = new AiDelegationDialog.Agreement(agreement, Path.of(AiDelegation.MANUAL), accepted::add);
            content.component().setSize(880, 760);
            HintLayerTest.layout(content.component());
            return content;
        });
        edt(() -> {
            assertInstanceOf(GrayGradient.EditorPane.class, window.text.getUI());
            var bar = window.scroll.getVerticalScrollBar();
            assertTrue(bar.getMaximum() > bar.getVisibleAmount(), "the bundled agreement needs scrolling");
            window.pledges.forEach(b -> b.doClick());
            assertFalse(window.ok.isEnabled(), "the real guif agreement starts unread");
            bar.setValue(bar.getMaximum());
            return null;
        });
        edt(() -> {
            assertTrue(window.ok.isEnabled(), "guif enables OK at the bottom with both statements ticked");
            window.ok.doClick();
            return null;
        });
        assertEquals(List.of(false), accepted);
    }

    private static void assertSheetGray(Color color) {
        assertTrue(color.getRed() == color.getGreen() && color.getGreen() == color.getBlue()
                && color.getRed() >= 200, "a light gray sheet background: " + color);
    }

    private static Color at(BufferedImage image, JComponent root, Component viewport, int x, int y) {
        Point p = SwingUtilities.convertPoint(viewport, x, y, root);
        return new Color(image.getRGB(p.x, p.y));
    }

    private static void assertGray(Color c) {
        assertTrue(c.getRed() == c.getGreen() && c.getGreen() == c.getBlue(), "a shade of gray: " + c);
        assertTrue(c.getRed() >= GrayGradient.BOTTOM.getRed() && c.getRed() <= GrayGradient.TOP.getRed(), "between the gradient's ends: " + c);
    }

    private static List<Component> all(Component root) {
        List<Component> out = new ArrayList<>();
        out.add(root);
        if (root instanceof Container parent) {
            for (Component child : parent.getComponents()) {
                out.addAll(all(child));
            }
        }
        return out;
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }

    private static <T> T edt(Callable<T> work) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                value.set(work.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) throw failure.get();
        return value.get();
    }
}
