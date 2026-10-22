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

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.CellRendererPane;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The author's request: guif's backgrounds contrast with the data and fields on them mildly and smoothly. Every visible
 * text of every step, with data in its outputs and tables, reads at 4.5:1 or more against what it is drawn on (WCAG AA),
 * the examples inside empty fields included; disabled text, which must look disabled, at 3:1 or more. Neighbouring
 * backgrounds differ softly, and a table's stripes stay apart from its top to its bottom.
 */
class GuifContrastTest {

    private static final double TEXT = 4.5;
    private static final double DISABLED = 3.0;

    private record Pair(String what, Color foreground, Color background, double least) {
        double ratio() {
            return CandidateCodeView.contrast(foreground, background);
        }
    }

    @Test
    void everyTextOfGuifReadsWellOnItsBackground(@TempDir Path dir) throws Exception {
        Path scenario = Files.writeString(dir.resolve("metrics.json"), """
                {"sheets":{"FW_Seq":[["P","FW_Combi(1)"],["Q","FW_Combi(1)"]],
                "P":[["latency=10; "],["latency=20; "],["latency=30; "],["latency=40; "]],
                "Q":[["throughput=40;"],["throughput=30;"],["throughput=20;"]]}}
                """);
        Session session = Session.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.execution.sut", "inline-metrics", "test")));
        LookAndFeel previous = UIManager.getLookAndFeel();
        String nativeSetting = System.getProperty("flatlaf.useNativeLibrary");
        String animations = System.getProperty("flatlaf.animation");
        Desktop desktop = edt(() -> {
            FlatDesktopLauncher.installLookAndFeel();
            return new Desktop(session, scenario, FileTrail.inMemory(), true);
        });
        try {
            edt(() -> desktop.scenario().validate()).get(30, TimeUnit.SECONDS);
            edt(() -> desktop.candidates().preview()).get(30, TimeUnit.SECONDS);
            List<Pair> pairs = edt(() -> {
                List<Pair> out = new ArrayList<>();
                JComponent root = desktop.component();
                root.setSize(1200, 820);
                for (int tab = 0; tab < desktop.tabs().getTabCount(); tab++) {
                    desktop.tabs().setSelectedIndex(tab);
                    HintLayerTest.layout(root);
                    walk(desktop.tabs().getSelectedComponent(), desktop.tabs().getTitleAt(tab), out);
                }
                walk(desktop.hintsButton().getParent(), "the top bar", out);
                Color panel = UIManager.getColor("Panel.background");
                out.add(new Pair("step titles", UIManager.getColor("TabbedPane.foreground"), panel, TEXT));
                out.add(new Pair("disabled step titles", UIManager.getColor("TabbedPane.disabledForeground"), panel, DISABLED));
                out.add(new Pair("hint pop-ups", HintLayer.TEXT, HintLayer.FILL, TEXT));
                out.add(new Pair("selected rows", UIManager.getColor("Table.selectionForeground"), UIManager.getColor("Table.selectionBackground"), TEXT));
                out.add(new Pair("placeholders on a field", UIManager.getColor("TextField.placeholderForeground"), UIManager.getColor("TextField.background"), TEXT));
                out.add(new Pair("placeholders on a disabled field", UIManager.getColor("TextField.placeholderForeground"),
                        UIManager.getColor("TextField.disabledBackground"), TEXT));
                return out;
            });
            assertTrue(pairs.size() > 250, "texts measured: " + pairs.size());
            List<String> weak = pairs.stream().filter(p -> p.ratio() < p.least())
                    .map(p -> String.format("%s: %.2f:1, %s on %s", p.what(), p.ratio(), hex(p.foreground()), hex(p.background())))
                    .distinct().toList();
            assertEquals(List.of(), weak, "texts that do not read well on their background");
        } finally {
            edt(desktop::close).get(30, TimeUnit.SECONDS);
            edt(() -> {
                UIManager.setLookAndFeel(previous);
                return null;
            });
            restore("flatlaf.useNativeLibrary", nativeSetting);
            restore("flatlaf.animation", animations);
        }
    }

    /** Neighbouring backgrounds differ softly (never more than 1.35:1), and a table's stripes stay apart everywhere. */
    @Test
    void neighbouringBackgroundsDifferSoftlyAndTableStripesNeverFade() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            edt(() -> {
                FlatDesktopLauncher.installLookAndFeel();
                Color panel = UIManager.getColor("Panel.background");
                Color field = UIManager.getColor("TextField.background");
                Color tint = Color.decode(FlatDesktopLauncher.ALTERNATE_ROW);
                for (Color c : new Color[]{field, GrayGradient.TOP, GrayGradient.BOTTOM, GrayGradient.TABLE_BOTTOM, tint}) {
                    double step = CandidateCodeView.contrast(panel, c);
                    assertTrue(step <= 1.35, "a soft step from the window's background to " + hex(c) + ": " + step);
                }
                assertTrue(CandidateCodeView.contrast(GrayGradient.TOP, GrayGradient.BOTTOM) >= 1.2, "a text's gradient is visible");
                for (Color even : new Color[]{GrayGradient.TOP, GrayGradient.TABLE_BOTTOM}) {
                    double step = CandidateCodeView.contrast(even, tint);
                    assertTrue(step >= 1.03 && step <= 1.35, "stripes apart but soft at " + hex(even) + ": " + step);
                    assertTrue(CandidateCodeView.contrast(even, Color.BLACK) > CandidateCodeView.contrast(tint, Color.BLACK),
                            "even rows lighter than odd rows at " + hex(even));
                }
                UIManager.setLookAndFeel(previous);
                return null;
            });
        } finally {
            edt(() -> {
                UIManager.setLookAndFeel(previous);
                return null;
            });
        }
    }

    private static void walk(Component c, String step, List<Pair> out) {
        if (c == null || !c.isVisible() || c.getWidth() <= 0 || c.getHeight() <= 0) {
            return;
        }
        String where = step + ": " + c.getClass().getSimpleName() + " " + name(c);
        if (c instanceof JComponent j) {
            Border border = j.getBorder();
            while (border instanceof CompoundBorder compound) {
                title(compound.getOutsideBorder(), c, where, out);
                border = compound.getInsideBorder();
            }
            title(border, c, where, out);
        }
        if (c instanceof JLabel label && label.getText() != null && !label.getText().isBlank()
                && SwingUtilities.getAncestorOfClass(CellRendererPane.class, c) == null) {
            out.add(label.isEnabled() ? new Pair(where, label.getForeground(), behind(c), TEXT)
                    : new Pair(where + " (disabled)", UIManager.getColor("Label.disabledForeground"), behind(c), DISABLED));
        } else if (c instanceof AbstractButton button && button.getText() != null && !button.getText().isBlank()) {
            boolean box = button instanceof JCheckBox || button instanceof JRadioButton;
            Color background = box ? behind(c) : button instanceof JToggleButton && button.isSelected()
                    ? UIManager.getColor("ToggleButton.selectedBackground") : UIManager.getColor("Button.background");
            out.add(button.isEnabled() ? new Pair(where, button.getForeground(), background, TEXT)
                    : new Pair(where + " (disabled)", UIManager.getColor("Button.disabledText"), background, DISABLED));
        } else if (c instanceof JTextComponent text) {
            Color background = fieldBackground(text);
            if (GrayGradient.output((JComponent) c) && !(c instanceof JTextField)) {
                out.add(new Pair(where + " (gradient top)", text.getForeground(), GrayGradient.TOP, TEXT));
                out.add(new Pair(where + " (gradient bottom)", text.getForeground(), GrayGradient.BOTTOM, TEXT));
            } else if (!text.getText().isEmpty()) {
                out.add(text.isEnabled() ? new Pair(where, text.getForeground(), background, TEXT)
                        : new Pair(where + " (disabled)", text.getDisabledTextColor(), background, DISABLED));
            } else if (text.getClientProperty("JTextField.placeholderText") instanceof String example && !example.isBlank()) {
                out.add(new Pair(where + " (example inside the empty field)", UIManager.getColor("TextField.placeholderForeground"), background, TEXT));
            }
        } else if (c instanceof JTable table) {
            boolean output = GrayGradient.output(table);
            for (int row = 0; row < Math.min(2, table.getRowCount()); row++) {
                for (int column = 0; column < table.getColumnCount(); column++) {
                    Component cell = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
                    if (output && cell.getBackground().equals(table.getBackground())) {
                        out.add(new Pair(where + " (even row, gradient top)", cell.getForeground(), GrayGradient.TOP, TEXT));
                        out.add(new Pair(where + " (even row, gradient bottom)", cell.getForeground(), GrayGradient.TABLE_BOTTOM, TEXT));
                    } else {
                        out.add(new Pair(where + " (row " + row + ")", cell.getForeground(), cell.getBackground(), TEXT));
                    }
                }
            }
            if (table.getTableHeader() != null) {
                out.add(new Pair(where + " (header)", UIManager.getColor("TableHeader.foreground"), UIManager.getColor("TableHeader.background"), TEXT));
            }
        } else if (c instanceof JList<?> list) {
            @SuppressWarnings("unchecked")
            JList<Object> items = (JList<Object>) list;
            for (int i = 0; i < items.getModel().getSize(); i++) {
                Component cell = items.getCellRenderer().getListCellRendererComponent(items, items.getModel().getElementAt(i), i, false, false);
                out.add(new Pair(where + " [" + i + "]", cell.getForeground(), cell.isOpaque() ? cell.getBackground() : items.getBackground(), TEXT));
            }
        } else if (c instanceof JComboBox<?> combo) {
            out.add(combo.isEnabled() ? new Pair(where, combo.getForeground(), combo.getBackground(), TEXT)
                    : new Pair(where + " (disabled)", UIManager.getColor("ComboBox.disabledForeground"), UIManager.getColor("ComboBox.disabledBackground"), DISABLED));
        }
        if (c instanceof Container parent && !(c instanceof JTable) && !(c instanceof JList) && !(c instanceof JComboBox)) {
            for (Component child : parent.getComponents()) {
                walk(child, step, out);
            }
        }
    }

    private static void title(Border border, Component c, String where, List<Pair> out) {
        if (border instanceof TitledBorder titled && titled.getTitle() != null && !titled.getTitle().isBlank()) {
            Color color = titled.getTitleColor() != null ? titled.getTitleColor() : UIManager.getColor("TitledBorder.titleColor");
            out.add(new Pair(where + " (title '" + titled.getTitle() + "')", color, behind(c), TEXT));
        }
    }

    /** What a field is drawn on: FlatLaf paints a field's own background, the disabled and the read-only one included. */
    private static Color fieldBackground(JTextComponent text) {
        if (!(text instanceof JTextField)) {
            return text.isOpaque() ? text.getBackground() : behind(text);
        }
        if (!text.isEnabled()) {
            return UIManager.getColor("TextField.disabledBackground");
        }
        return text.isEditable() ? text.getBackground() : UIManager.getColor("TextField.inactiveBackground");
    }

    /** The background a transparent component shows: that of its nearest opaque ancestor. */
    private static Color behind(Component c) {
        for (Component k = c; k != null; k = k.getParent()) {
            if (k instanceof JComponent j && j.isOpaque() && j.getBackground() != null) {
                return j.getBackground();
            }
        }
        return UIManager.getColor("Panel.background");
    }

    private static String name(Component c) {
        String name = c.getAccessibleContext() == null ? null : c.getAccessibleContext().getAccessibleName();
        if (name == null && c instanceof JLabel label) {
            name = label.getText();
        }
        return name == null ? "" : "'" + (name.length() > 50 ? name.substring(0, 50) + "…" : name) + "'";
    }

    private static String hex(Color c) {
        return c == null ? "none" : String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
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
