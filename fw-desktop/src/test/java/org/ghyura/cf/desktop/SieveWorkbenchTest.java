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
import org.ghyura.cf.desktop.session.*;
import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.model.rule.RuleValue;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SieveWorkbenchTest {
    @Test void face2ButtonFitsTheFileToolbarAndProtectsPendingDrafts(@TempDir Path dir) throws Exception {
        Path file = scenario(dir); Session session = session(); FileTrail trail = FileTrail.inMemory();
        SieveWorkbench view = edt(() -> new SieveWorkbench(() -> session, () -> file, new Navigator(trail, () -> file)));
        try {
            edt(view::open).get(30, TimeUnit.SECONDS);
            edt(() -> {
                var root = view.component(); root.setSize(1180, 740); layout(root);
                JButton browser = find(root, JButton.class, b -> "Open Face 2 in browser".equals(b.getName()));
                assertNotNull(browser); assertEquals("Face 2 (Sieve) ↗", browser.getText());
                assertTrue(browser.isEnabled()); assertTrue(browser.getWidth() > 0);
                assertTrue(browser.getX() + browser.getWidth() <= browser.getParent().getWidth(), "Face 2 must fit beside the file actions");
                assertTrue(browser.getY() + browser.getHeight() <= browser.getParent().getHeight(), "Face 2 must not be clipped vertically");
                var image = new BufferedImage(1180, 740, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics(); try { root.paint(graphics); } finally { graphics.dispose(); }
                Path screenshot = Path.of("target", "face2-workbench.png"); Files.createDirectories(screenshot.getParent());
                javax.imageio.ImageIO.write(image, "png", screenshot.toFile());
                var json = find(root, JTextArea.class, text -> "Sieve execution JSON".equals(text.getName()));
                assertNotNull(json); json.append(" ");
                return null;
            });
            var refused = edt(view::openFace2);
            assertThrows(ExecutionException.class, () -> refused.get(30, TimeUnit.SECONDS));
            assertTrue(edt(view::output).contains("pending"));
        } finally { edt(view::close).get(30, TimeUnit.SECONDS); trail.close(); }
    }

    private static void layout(Container parent) {
        parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }

    @Test void drawEditUndoSaveReloadAndRunUseTheSameRules(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir); Session session = session(); FileTrail trail = FileTrail.inMemory();
        SieveWorkbench view = edt(() -> new SieveWorkbench(() -> session, () -> scenario, new Navigator(trail, () -> scenario)));
        try {
            edt(view::open).get(30, TimeUnit.SECONDS);
            assertEquals(BigInteger.valueOf(4), session.preview(scenario, request(), Cancellation.NONE).generated());
            edt(() -> { view.pair(new SieveCanvas.Value("A", "a"), new SieveCanvas.Value("B", "1")); return null; });
            assertTrue(edt(view::dirty)); assertEquals(1, edt(() -> view.document().rules().size()));
            edt(view::save).get(30, TimeUnit.SECONDS);
            assertFalse(edt(view::dirty));
            var after = session.preview(scenario, request(), Cancellation.NONE);
            assertEquals(BigInteger.valueOf(3), after.estimates().candidates().stream().filter(s -> s.name().equals("executable")).findFirst().orElseThrow().count().value());
            assertEquals("sieve-1", after.rules().getFirst().name());
            assertEquals(edt(view::document), session.openSieve(scenario).document());
            assertTrue(Files.isRegularFile(SieveFiles.layoutOf(ConstraintSidecarReader.companionOf(scenario))));
            edt(() -> { view.undo(); return null; }); assertEquals(0, edt(() -> view.document().rules().size())); assertTrue(edt(view::dirty));
            edt(() -> { view.redo(); return null; }); assertEquals(1, edt(() -> view.document().rules().size()));
            String before = Files.readString(ConstraintSidecarReader.companionOf(scenario));
            var failed = edt(() -> view.applyJson("{\"version\":1,\"constraints\":[{\"id\":\"bad\",\"sets\":{}}]}"));
            assertThrows(ExecutionException.class, () -> failed.get(30, TimeUnit.SECONDS));
            assertEquals(1, edt(() -> view.document().rules().size())); assertEquals(before, Files.readString(ConstraintSidecarReader.companionOf(scenario)));
            Files.writeString(ConstraintSidecarReader.companionOf(scenario), "outside edit");
            var refused = edt(view::save); assertThrows(ExecutionException.class, () -> refused.get(30, TimeUnit.SECONDS));
            assertEquals("outside edit", Files.readString(ConstraintSidecarReader.companionOf(scenario)));
        } finally { edt(view::close).get(30, TimeUnit.SECONDS); trail.close(); }
    }

    @Test void undoingASavedLayoutIsDirtyAndRedoReturnsToSavedState(@TempDir Path dir) throws Exception {
        Path file = scenario(dir); Session session = session(); FileTrail trail = FileTrail.inMemory();
        SieveWorkbench view = edt(() -> new SieveWorkbench(() -> session, () -> file, new Navigator(trail, () -> file)));
        try {
            edt(view::open).get(30, TimeUnit.SECONDS);
            edt(() -> {
                var canvas = view.canvas(); canvas.setSize(900, 550); canvas.fit();
                Point card = canvas.screen(new Point2D.Double(40, 69)), end = new Point(card.x + 55, card.y + 30);
                mouse(canvas, MouseEvent.MOUSE_PRESSED, card, MouseEvent.BUTTON1);
                mouse(canvas, MouseEvent.MOUSE_DRAGGED, end, MouseEvent.NOBUTTON);
                mouse(canvas, MouseEvent.MOUSE_RELEASED, end, MouseEvent.BUTTON1);
                return null;
            });
            assertTrue(edt(view::dirty)); edt(view::save).get(30, TimeUnit.SECONDS); assertFalse(edt(view::dirty));
            edt(() -> { view.undo(); return null; }); assertTrue(edt(view::dirty), "undo changed saved positions and must protect closure");
            edt(() -> { view.redo(); return null; }); assertFalse(edt(view::dirty));
        } finally { edt(view::close).get(30, TimeUnit.SECONDS); trail.close(); }
    }

    @Test void inactiveOppositeRulesAreWarningsAndRemainSaveable(@TempDir Path dir) throws Exception {
        Path file = scenario(dir); Session session = session(); FileTrail trail = FileTrail.inMemory();
        SieveWorkbench view = edt(() -> new SieveWorkbench(() -> session, () -> file, new Navigator(trail, () -> file)));
        try {
            edt(view::open).get(30, TimeUnit.SECONDS);
            edt(() -> view.applyJson("""
                    {"version":1,"constraints":[
                     {"id":"opposite-a","polarity":"forbid","pairs":[{"A":"a","B":"1"}],"gate":{"within":0}},
                     {"id":"opposite-b","polarity":"require","pairs":[{"A":"a","B":"1"}],"gate":{"within":0}}
                    ]}
                    """)).get(30, TimeUnit.SECONDS);
            assertFalse(edt(() -> view.document().contradictions().isEmpty()));
            edt(view::save).get(30, TimeUnit.SECONDS);
            assertTrue(edt(view::output).contains("Opposite-polarity")); assertFalse(edt(view::dirty));
            assertEquals(edt(view::document), session.openSieve(file).document());
            assertEquals(BigInteger.valueOf(4), session.preview(file, request(), Cancellation.NONE).estimates().candidates().stream().filter(s -> s.name().equals("executable")).findFirst().orElseThrow().count().value());
        } finally { edt(view::close).get(30, TimeUnit.SECONDS); trail.close(); }
    }

    @Test void onlyOneEditorCanOwnAPendingDraft(@TempDir Path dir) throws Exception {
        Path file = scenario(dir); Session session = session(); FileTrail trail = FileTrail.inMemory();
        SieveWorkbench view = edt(() -> new SieveWorkbench(() -> session, () -> file, new Navigator(trail, () -> file)));
        try {
            edt(view::open).get(30, TimeUnit.SECONDS);
            edt(() -> {
                view.pair(new SieveCanvas.Value("A", "a"), new SieveCanvas.Value("B", "1"));
                JTextField id = field(view.component(), "Rule ID"); id.setText("pending-name");
                JTabbedPane tabs = find(view.component(), JTabbedPane.class, t -> t.indexOfTab("Sieve graph") >= 0);
                tabs.setSelectedIndex(2); assertEquals(0, tabs.getSelectedIndex()); assertEquals("pending-name", id.getText());
                find(view.component(), JButton.class, b -> b.getText().equals("Discard draft")).doClick();
                tabs.setSelectedIndex(2); assertFalse(id.isEnabled());
                find(view.component(), JButton.class, b -> b.getText().equals("Add attribute")).doClick();
                tabs.setSelectedIndex(3); assertEquals(2, tabs.getSelectedIndex());
                find(view.component(), JButton.class, b -> b.getText().equals("Discard data draft")).doClick();
                tabs.setSelectedIndex(3);
                JTextArea raw = find(view.component(), JTextArea.class, t -> "Sieve execution JSON".equals(t.getName())); raw.append(" ");
                tabs.setSelectedIndex(0); assertEquals(3, tabs.getSelectedIndex());
                find(view.component(), JButton.class, b -> b.getText().equals("Discard JSON draft")).doClick();
                tabs.setSelectedIndex(0); assertTrue(id.isEnabled());
                return null;
            });
        } finally { edt(view::close).get(30, TimeUnit.SECONDS); trail.close(); }
    }

    private static <T extends Component> T find(Container root, Class<T> type, java.util.function.Predicate<T> predicate) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && predicate.test(type.cast(c))) return type.cast(c);
            if (c instanceof Container child) { T found = find(child, type, predicate); if (found != null) return found; }
        }
        return null;
    }

    @Test void ruleInspectorDoesNotNormalizeUntouchedFields() throws Exception {
        String source = """
                {"version":1,"constraints":[{"id":"authored-id","desc":"Keep me","sheets":["B","A"],
                 "pairs":[{"B":2,"A":"a"},{"B":true,"A":"b"}],"gate":{"adjacent":true,"within":0},
                 "condition":{"not":{"sheet":"A","eq":"b"}}}]}
                """;
        var doc = SieveDocument.parse(source, "test");
        edt(() -> {
            SieveRuleEditor editor = new SieveRuleEditor(r -> { }, () -> { }, () -> { });
            editor.show(doc, doc.rules().getFirst(), Map.of("A", List.of("a", "b"), "B", List.of("1", "2")));
            assertEquals(doc.rules().getFirst(), editor.draft());
            JTextField id = field(editor.component(), "Rule ID"); id.setText("renamed-by-user");
            var expected = new java.util.LinkedHashMap<>(doc.rules().getFirst().entries()); expected.put("id", new RuleValue.Text("renamed-by-user"));
            assertEquals(new RuleValue.MapValue(expected), editor.draft());
            return null;
        });
    }

    @Test void typedGroupsAndMappingsRemainEditableWithoutFlattening() throws Exception {
        edt(() -> {
            var values = Map.of("A", List.of("a", "b"), "B", List.of("1", "2"));
            var doc = SieveDocument.parse("""
                    {"version":1,"constraints":[
                     {"id":"groups","sets":{"A":["a","b"],"B":[2,true]}},
                     {"id":"mapping","sheets":["B","A"],"mapping":{"source":"A","target":"B","allow":{"a":[2,true],"b":[]}}},
                     {"id":"formula","sheets":["A","B"],"when":"A.pos < B.pos","gate":{"adjacent":false,"within":0}},
                     {"id":"assertion","assert":{"any":[{"sheet":"A","eq":"a"},{"sheet":"B","eq":"2"}]}}
                     ]}
                    """, "test");
            SieveRuleEditor editor = new SieveRuleEditor(r -> { }, () -> { }, () -> { });
            for (var rule : doc.rules()) { editor.show(doc, rule, values); assertEquals(rule, editor.draft()); }
            editor.show(doc, doc.rules().get(0), values);
            JTable grid = table(editor.component()); grid.setValueAt(new RuleValue.Text("changed"), 0, 0);
            var group = editor.draft();
            assertFalse(group.entries().containsKey("sheets"), "editing a value does not invent sheet declarations");
            assertEquals(SieveDocument.map(doc.rules().get(0).entries().get("sets")).get("B"), SieveDocument.map(group.entries().get("sets")).get("B"));
            SieveDocument.parse(doc.replace(0, group).json(), "edited group");
            editor.show(doc, doc.rules().get(1), values); grid = table(editor.component()); grid.setValueAt(new RuleValue.Text("2"), 0, 1);
            var mapping = editor.draft(); assertEquals(doc.rules().get(1).entries().get("sheets"), mapping.entries().get("sheets"));
            var allowed = SieveDocument.map(SieveDocument.map(mapping.entries().get("mapping")).get("allow"));
            assertEquals(List.of(new RuleValue.Text("2"), new RuleValue.Bool(true)), SieveDocument.list(allowed.get("a")));
            assertEquals(List.of(), SieveDocument.list(allowed.get("b"))); SieveDocument.parse(doc.replace(1, mapping).json(), "edited mapping");
            return null;
        });
    }

    /** The Within distance of a pair rule steps up and down, to its bounds, without mixing number types (D002). */
    @Test void theWithinDistanceOfAPairRuleStepsWithinItsBoundsWithoutATypeClash() throws Exception {
        var doc = SieveDocument.parse("""
                {"version":1,"constraints":[{"id":"near","polarity":"forbid","pairs":[{"A":"a","B":"1"}],"gate":{"within":3}}]}
                """, "test");
        edt(() -> {
            SieveRuleEditor editor = new SieveRuleEditor(r -> { }, () -> { }, () -> { });
            editor.show(doc, doc.rules().getFirst(), Map.of("A", List.of("a", "b"), "B", List.of("1", "2")));
            JSpinner within = named(editor.component(), JSpinner.class, "columns apart");
            assertTrue(within.isEnabled(), "a pair rule may be gated");
            assertEquals(3L, within.getValue());
            assertEquals(4L, within.getNextValue());
            assertEquals(2L, within.getPreviousValue());
            within.setValue(Long.MAX_VALUE);
            assertNull(within.getNextValue(), "no step past the largest distance");
            assertEquals(Long.MAX_VALUE - 1, within.getPreviousValue());
            within.setValue(0L);
            assertNull(within.getPreviousValue(), "no negative distance");
            assertEquals(RuleValue.Int.of(0L), SieveDocument.map(editor.draft().entries().get("gate")).get("within"),
                    "the rule keeps the distance as edited");
            return null;
        });
    }

    /** A draft that cannot be applied says why in the editor, not in a dialog, so it also works without a display. */
    @Test void aDraftThatCannotBeAppliedSaysWhyWithoutADialog() throws Exception {
        var doc = SieveDocument.parse("""
                {"version":1,"constraints":[{"id":"pair","polarity":"forbid","pairs":[{"A":"a","B":"1"}]},
                 {"id":"mapping","sheets":["B","A"],"mapping":{"source":"A","target":"B","allow":{"a":["1"]}}}]}
                """, "test");
        var values = Map.of("A", List.of("a", "b"), "B", List.of("1", "2"));
        edt(() -> {
            List<String> problems = new java.util.ArrayList<>();
            SieveRuleEditor editor = new SieveRuleEditor(r -> { }, () -> { }, () -> { }, problems::add);
            editor.show(doc, doc.rules().getFirst(), values);
            JButton use = find(editor.component(), JButton.class, b -> b.getText().equals("Use chosen sheets"));
            assertTrue(use.isEnabled(), "the rule's own two sheets come selected");
            named(editor.component(), JList.class, "Sheets the rule reads").clearSelection();
            assertFalse(use.isEnabled(), "fewer than two sheets are no choice to use");
            editor.show(doc, doc.rules().get(1), values);
            table(editor.component()).setValueAt(RuleValue.Int.of(1L), 0, 0);
            find(editor.component(), JButton.class, b -> b.getText().equals("Apply rule")).doClick();
            assertEquals(List.of("The rule was not applied: Mapping source keys must be text."), problems);
            return null;
        });
    }

    /** With no rule shown the rule editor has nothing to change, so none of its gates, conditions or buttons can start a draft. */
    @Test void withNoRuleShownTheRuleEditorStartsNoDraft() throws Exception {
        var doc = SieveDocument.parse("""
                {"version":1,"constraints":[{"id":"pair","polarity":"forbid","pairs":[{"A":"a","B":"1"}],"condition":{"sheet":"A","eq":"a"}}]}
                """, "test");
        var values = Map.of("A", List.of("a", "b"), "B", List.of("1", "2"));
        edt(() -> {
            int[] changes = {0};
            SieveRuleEditor editor = new SieveRuleEditor(r -> { }, () -> { }, () -> changes[0]++);
            for (boolean shown : new boolean[]{false, true, false}) {
                editor.show(doc, shown ? doc.rules().getFirst() : null, values);
                for (String text : List.of("Adjacent", "Within", "+ condition", "Apply JSON", "Apply rule", "Discard draft")) {
                    AbstractButton b = find(editor.component(), AbstractButton.class, c -> text.equals(c.getText()));
                    assertEquals(shown, b.isEnabled(), text + (shown ? " with a rule shown" : " with none"));
                    if (!shown) b.doClick();
                }
                assertEquals(shown, field(editor.component(), "Rule ID").isEnabled());
                if (shown) find(editor.component(), JButton.class, c -> "Apply JSON".equals(c.getText())).doClick();
            }
            assertEquals(0, changes[0], "pressing what is off, or applying the condition the rule already has, is no edit");
            assertFalse(editor.dirty());
            return null;
        });
    }

    /** Before a scenario is opened the data editor has no values to describe; with no rule selected there is no gate to set. */
    @Test void beforeAScenarioOrARuleIsChosenNothingStartsADraft(@TempDir Path dir) throws Exception {
        Path file = scenario(dir); Session session = session(); FileTrail trail = FileTrail.inMemory();
        SieveWorkbench view = edt(() -> new SieveWorkbench(() -> session, () -> file, new Navigator(trail, () -> file)));
        try {
            edt(() -> {
                JTable emptyMatrix = find(view.component(), JTable.class,
                        t -> t.getColumnCount() > 0 && "Choose a row sheet".equals(t.getColumnName(0)));
                assertNotNull(emptyMatrix, "an unopened matrix gives a useful prompt instead of null");
                for (String text : List.of("Add attribute", "Add value", "Apply data", "Discard data draft"))
                    assertFalse(find(view.component(), JButton.class, b -> text.equals(b.getText())).isEnabled(), text + " before a scenario is opened");
                return null;
            });
            edt(view::open).get(30, TimeUnit.SECONDS);
            edt(() -> view.applyJson("""
                    {"version":1,"constraints":[{"id":"pair","polarity":"forbid","pairs":[{"A":"a","B":"1"}]}]}
                    """)).get(30, TimeUnit.SECONDS);
            AbstractButton adjacent = edt(() -> {
                assertTrue(find(view.component(), JButton.class, b -> "Add attribute".equals(b.getText())).isEnabled(), "an opened scenario has values");
                AbstractButton gate = find(view.component(), AbstractButton.class, b -> "Adjacent".equals(b.getText()));
                assertFalse(gate.isEnabled(), "no rule is selected after the rules arrive");
                gate.doClick();
                return gate;
            });
            edt(() -> view.validate(false)).get(30, TimeUnit.SECONDS);
            assertFalse(edt(view::output).contains("pending"), "nothing pressed left a draft to apply or discard: " + edt(view::output));
            edt(() -> { assertTrue(view.selectRule(0)); assertTrue(adjacent.isEnabled(), "a selected pair rule may be gated"); return null; });
        } finally { edt(view::close).get(30, TimeUnit.SECONDS); trail.close(); }
    }

    /** A palette block put on a filled place replaces that block, as its hint says, and the editor says what went. */
    @Test void aPaletteBlockOnAFilledPlaceSaysWhatItReplaced() throws Exception {
        edt(() -> {
            FormulaEditor f = new FormulaEditor(() -> { });
            f.show("1 > 2", List.of("A", "B"), s -> List.of("cost"));
            assertTrue(f.toBlocks());
            f.select("");
            assertTrue(f.paletteEnabled(FormulaBlocks.Kind.NOT), "a filled place takes a block of its type, which replaces it");
            JButton not = find(f.component(), JButton.class, b -> "not".equals(b.getText()));
            assertTrue(Hints.own(not).contains("replaced"), Hints.own(not));
            assertTrue(f.put(FormulaBlocks.Kind.NOT));
            assertEquals("Replaced (1 > 2) with a new not block. Discard draft returns to the rule as last applied.", f.hint());
            f.select("");
            f.delete();
            assertTrue(f.put(FormulaBlocks.Kind.CMP));
            assertFalse(f.hint().startsWith("Replaced"), "an empty place loses nothing: " + f.hint());
            return null;
        });
    }

    private static <T extends Component> T named(Container root, Class<T> type, String name) {
        return find(root, type, c -> name.equals(c.getAccessibleContext().getAccessibleName()));
    }

    private static JTable table(Container root) {
        for (Component c : root.getComponents()) { if (c instanceof JTable t) return t; if (c instanceof Container child) { JTable found = table(child); if (found != null) return found; } }
        return null;
    }

    @Test void canvasZoomHitTestingLinkCreationAndCancellationAgree() throws Exception {
        edt(() -> {
            AtomicReference<List<SieveCanvas.Value>> linked = new AtomicReference<>();
            AtomicReference<String> moved = new AtomicReference<>();
            SieveCanvas canvas = new SieveCanvas(i -> true, (a, b) -> linked.set(List.of(a, b)), () -> { }, moved::set);
            canvas.setSize(900, 550);
            var values = new java.util.LinkedHashMap<String, List<String>>(); values.put("A", List.of("a", "b")); values.put("B", List.of("1", "2"));
            canvas.show(SieveDocument.empty(), values, Set.of("B"), "");
            Point anchor = new Point(240, 170); Point2D before = canvas.world(anchor); canvas.zoomAt(anchor, 1.7);
            assertEquals(before.getX(), canvas.world(anchor).x, 1e-9); assertEquals(before.getY(), canvas.world(anchor).y, 1e-9);
            Point pin = canvas.screen(new Point2D.Double(160, 69)), target = canvas.screen(new Point2D.Double(240, 69));
            mouse(canvas, MouseEvent.MOUSE_PRESSED, pin, MouseEvent.BUTTON1); assertEquals(SieveCanvas.Mode.DRAW_LINK, canvas.mode());
            mouse(canvas, MouseEvent.MOUSE_DRAGGED, target, MouseEvent.NOBUTTON); mouse(canvas, MouseEvent.MOUSE_RELEASED, target, MouseEvent.BUTTON1);
            assertEquals(List.of(new SieveCanvas.Value("A", "a"), new SieveCanvas.Value("B", "1")), linked.get());
            assertEquals(SieveCanvas.Mode.IDLE, canvas.mode()); linked.set(null);
            mouse(canvas, MouseEvent.MOUSE_PRESSED, pin, MouseEvent.BUTTON1); canvas.cancelGesture(); mouse(canvas, MouseEvent.MOUSE_RELEASED, target, MouseEvent.BUTTON1); assertNull(linked.get());
            String originalLayout = canvas.layoutJson(); Point card = canvas.screen(new Point2D.Double(40, 69));
            mouse(canvas, MouseEvent.MOUSE_PRESSED, card, MouseEvent.BUTTON1); mouse(canvas, MouseEvent.MOUSE_DRAGGED, new Point(card.x + 65, card.y + 35), MouseEvent.NOBUTTON); canvas.cancelGesture();
            assertEquals(originalLayout, canvas.layoutJson()); assertNull(moved.get());
            mouse(canvas, MouseEvent.MOUSE_PRESSED, card, MouseEvent.BUTTON1); Point end = new Point(card.x + 65, card.y + 35);
            mouse(canvas, MouseEvent.MOUSE_DRAGGED, end, MouseEvent.NOBUTTON); mouse(canvas, MouseEvent.MOUSE_RELEASED, end, MouseEvent.BUTTON1); assertEquals(originalLayout, moved.get());
            String layout = canvas.layoutJson(); canvas.show(SieveDocument.empty(), values, Set.of(), "b"); canvas.show(SieveDocument.empty(), values, Set.of(), ""); assertEquals(layout, canvas.layoutJson());
            var g = new BufferedImage(900, 550, BufferedImage.TYPE_INT_ARGB).createGraphics(); try { canvas.paint(g); } finally { g.dispose(); }
            return null;
        });
    }

    private static JTextField field(Container root, String label) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l && label.equals(l.getText())) return (JTextField) l.getLabelFor();
            if (c instanceof Container child) { JTextField found = field(child, label); if (found != null) return found; }
        }
        return null;
    }
    private static void mouse(SieveCanvas canvas, int type, Point point, int button) {
        canvas.dispatchEvent(new MouseEvent(canvas, type, System.currentTimeMillis(), type == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0, point.x, point.y, 1, false, button));
    }
    private static Path scenario(Path dir) throws Exception { return Files.writeString(dir.resolve("small.json"), """
            {"sheets":{"FW_Seq":[["A","FW_Combi(1)"],["B","FW_Combi(1)"]],"A":[["a"],["b"]],"B":[["1"],["2"]]}}
            """); }
    private static Session session() { return Session.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.plan.autoPromoteSingleVerb", "false", "test"))); }
    private static CandidatePreview.Request request() { return new CandidatePreview.Request(BigInteger.valueOf(100), BigInteger.ZERO, 10, true, true); }
    private static <T> T edt(Callable<T> action) throws Exception { FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get(30, TimeUnit.SECONDS); }
}
