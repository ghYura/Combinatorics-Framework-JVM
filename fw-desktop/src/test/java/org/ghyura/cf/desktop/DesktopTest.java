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

import org.ghyura.cf.analysis.ScoreBreakdown;
import org.ghyura.cf.analysis.TextScoring;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.text.JTextComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A18 slice: every desktop tab drives the shared session, headless, with work off the event thread. */
class DesktopTest {

    /** Returns after the EDT dispatched an action even when its background work must wait on the controller monitor. */
    private static <T> T responsiveEdt(Callable<T> action) throws Exception {
        var dispatched = new java.util.concurrent.CompletableFuture<T>();
        SwingUtilities.invokeLater(() -> {
            try { dispatched.complete(action.call()); }
            catch (Throwable failure) { dispatched.completeExceptionally(failure); }
        });
        return dispatched.get(5, TimeUnit.SECONDS);
    }

    @Test
    void runPreparationExportsAndCloseLeaveTheEventThreadResponsive(@TempDir Path dir) throws Exception {
        Path input = scenario(dir);
        Desktop desktop = edt(() -> new Desktop(session("cf.execution.sut=inline-metrics"), input));
        RunController controller = desktop.run().controller();
        try {
            java.util.concurrent.CompletableFuture<Void> started;
            synchronized (controller) {
                started = responsiveEdt(() -> desktop.run().start());
                assertTrue(!started.isDone(), "preparation waits off the EDT");
            }
            started.get(60, TimeUnit.SECONDS);
            until(() -> controller.view().state() == RunState.COMPLETED && !controller.view().report().isEmpty());
            java.util.concurrent.CompletableFuture<Path> exported;
            Path target = dir.resolve("run.csv");
            synchronized (controller) {
                exported = responsiveEdt(() -> desktop.run().export("csv", target));
                assertTrue(!exported.isDone(), "export waits off the EDT");
            }
            assertEquals(target, exported.get(60, TimeUnit.SECONDS));
            assertTrue(Files.size(target) > 0);
            java.util.concurrent.CompletableFuture<Void> closed;
            synchronized (controller) {
                closed = responsiveEdt(desktop::close);
                assertTrue(!closed.isDone(), "run cleanup waits off the EDT");
            }
            closed.get(60, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> controller.start(input));
        } finally {
            edt(desktop::close).get(60, TimeUnit.SECONDS);
        }
    }

    @Test
    void closingTheFileTrailLeavesTheEventThreadResponsive(@TempDir Path dir) throws Exception {
        FileTrail trail = FileTrail.remembered(dir.resolve("desktop-places.properties"));
        Desktop desktop = edt(() -> new Desktop(session(), null, trail));
        try {
            edt(() -> java.util.concurrent.CompletableFuture.allOf(desktop.candidates().close(),
                    desktop.run().close(), desktop.campaign().close())).get(60, TimeUnit.SECONDS);
            java.util.concurrent.CompletableFuture<Void> closed;
            synchronized (trail) {
                closed = responsiveEdt(desktop::close);
                assertTrue(!closed.isDone(), "file-trail cleanup waits off the EDT");
                assertTrue(responsiveEdt(() -> true));
            }
            closed.get(60, TimeUnit.SECONDS);
        } finally {
            edt(desktop::close).get(60, TimeUnit.SECONDS);
        }
    }

    @Test
    void backgroundCallbacksSettleAndPlanningPreservesInvalidManualRules(@TempDir Path dir) throws Exception {
        var failed = Background.run("failure-check", () -> { throw new IllegalArgumentException("work failure"); },
                value -> { }, failure -> { throw new IllegalStateException("callback failure"); });
        var caught = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
                () -> failed.get(5, TimeUnit.SECONDS));
        assertEquals("work failure", caught.getCause().getMessage());
        assertEquals("callback failure", caught.getCause().getSuppressed()[0].getMessage());
        Path input = Files.writeString(dir.resolve("corpus.data"), "latency=1\nlatency=2\nlatency=3\n");
        Desktop desktop = edt(() -> new Desktop(session(), null));
        try {
            edt(() -> { desktop.scorer().choose(input, "{ unfinished"); return desktop.scorer().autoPlan(); }).get(60, TimeUnit.SECONDS);
            assertEquals("{ unfinished", edt(() -> desktop.scorer().rulesText()));
            assertTrue(edt(() -> desktop.scorer().status()).contains("kept the rules unchanged"));
        } finally { edt(desktop::close).get(60, TimeUnit.SECONDS); }
    }

    private static Session session(String... settings) {
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        for (String s : settings) {
            overrides.add(new ConfigResolver.Override(s.substring(0, s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test"));
        }
        return Session.resolve(null, Map.of(), overrides);
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

    private static void until(Callable<Boolean> condition) throws Exception {
        for (int i = 0; i < 2000 && !condition.call(); i++) {
            Thread.sleep(5);
        }
        assertTrue(condition.call(), "timed out");
    }

    private static Path scenario(Path dir) throws IOException {
        StringBuilder p = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            p.append(i == 1 ? "" : ", ").append("[\"latency=").append(10 * i).append("; throughput=").append(100 * (9 - i)).append(";\"]");
        }
        return Files.writeString(dir.resolve("metrics.json"), "{\"sheets\": {\"FW_Seq\": [[\"P\", \"FW_Combi(1)\"], [\"Q\", \"FW_Combi(1)\"]], "
                + "\"P\": [" + p + "], \"Q\": [[\"mode=m0;\"], [\"mode=m1;\"], [\"mode=m2;\"], [\"mode=m3;\"]]}}", StandardCharsets.UTF_8);
    }

    @Test
    void thePaletteFillsCellsAndValidationShowsWhichOperandAJoinerFindsEmpty(@TempDir Path dir) throws Exception {
        Session session = session();
        Desktop desktop = edt(() -> new Desktop(session, null));
        AuthoringView a = desktop.authoring();
        edt(() -> {
            a.insert("exclude");
            return null;
        });
        assertTrue(edt(a::output).startsWith("Select a cell of the sheet first"), "nothing is put anywhere without a selected cell");
        edt(() -> {
            a.newDocument();
            for (String sheet : List.of("LEFT", "RIGHT", "JOIN1", "JOIN2")) {
                a.addSheet(sheet);
            }
            a.selectSheet(0);
            String[] targets = {"LEFT", "RIGHT", "JOIN1", "JOIN2"};
            for (int r = 0; r < 4; r++) {
                a.type(r, 0, targets[r]);
            }
            // LEFT has no FW_Reuse: JOIN1 consumes its rows and JOIN2 reads none
            for (int r = 0; r < 2; r++) {
                a.selectCell(r, 1);
                a.insert("exclude");
                a.selectCell(r, 2);
                a.insert("combi_one");
            }
            a.selectCell(1, 3);
            a.insert("reuse");
            for (int r = 2; r < 4; r++) {
                a.selectCell(r, 1);
                a.insert("brace");
            }
            String[][] values = {{"a", "b"}, {"c", "d"}, {"j1"}, {"j2"}};
            for (int s = 0; s < 4; s++) {
                a.selectSheet(s + 1);
                for (int r = 0; r < values[s].length; r++) {
                    a.type(r, 0, values[s][r]);
                }
            }
            return null;
        });
        assertEquals("FW_(,,LEFT,,RIGHT,,,,M:N)", edt(() -> a.document().cell(0, 3, 1)), "the palette put the joiner into its cell");
        edt(a::validate).get(60, TimeUnit.SECONDS);
        assertTrue(edt(a::output).contains("GEN_OPERAND_CONSUMED") && edt(a::output).contains("the joiner of JOIN2 would read LEFT"),
                edt(a::output));
        assertEquals(2, (int) edt(() -> a.dependencies().getRowCount()));
        assertEquals("LEFT: reads none, consumed by JOIN1", edt(() -> a.dependencies().getValueAt(1, 2)));
        assertEquals("RIGHT: rows kept (FW_Reuse)", edt(() -> a.dependencies().getValueAt(1, 3)));
        // the same finding in the FW_Seq dependency graph, which bundle plan would have refused and is reported here
        assertTrue(edt(a::graphInfo).startsWith("6 nodes, 6 edges, sha256:") && edt(a::graphInfo).contains("; 1 errors, 0 warnings"),
                edt(a::graphInfo));
        assertTrue(edt(a::graphIssues).startsWith("✗ [consumed_result_ambiguity] result of 'LEFT' is consumed by 2 braces (brace#0.1, "
                + "brace#1.1)"), edt(a::graphIssues));
        GraphView.Layout layout = edt(() -> a.graphView().currentLayout());
        assertTrue(layout.nodes().get("LEFT").column() < layout.nodes().get("brace#0.1").column()
                && layout.nodes().get("brace#0.1").column() < layout.nodes().get("JOIN1").column(), layout.toString());
        Path dot = dir.resolve("plan.dot");
        edt(() -> {
            a.saveGraph(dot);
            return null;
        });
        assertTrue(Files.readString(dot).startsWith("digraph fwseq {\n  rankdir=LR;\n  \"JOIN1\" [shape=ellipse, label=\"JOIN1\\n[join-result]\"];"),
                Files.readString(dot));

        edt(() -> {
            a.selectSheet(1);
            a.type(0, 1, "FW_Group");
            a.selectCell(0, 1);
            a.insert("replace");
            return null;
        });
        assertEquals("FW_Group\nFW_ReplaceRE(\"pattern\", \"replacement\")", edt(() -> a.document().cell(1, 0, 1)),
                "a FW_ReplaceRE line joins its FW_Group cell");

        List<org.ghyura.cf.desktop.session.ControlSheets.Issue> issues = edt(() -> {
            a.setControls(new org.ghyura.cf.desktop.session.ControlSheets("class RunMeFirstOnce { public static String FW_ARGS; "
                    + "static { FW_ARGS = \"x\"; } }", List.of("one", "", "three"), List.of(new org.ghyura.cf.desktop.session.ControlSheets.Verdict(
                    "7", "carrier"), new org.ghyura.cf.desktop.session.ControlSheets.Verdict("07", "again"))));
            return a.applyControls();
        });
        assertEquals(1, issues.size(), issues.toString());
        assertTrue(edt(a::output).contains("duplicates verdict code 7"), edt(a::output));
        assertTrue(edt(() -> a.document().sheetNames()).containsAll(List.of("FW_RunMeFirstOnce", "FW_Arguments", "FW_CUSTOM_VAR")));
        edt(() -> {
            a.setControls(new org.ghyura.cf.desktop.session.ControlSheets("", List.of(), List.of()));
            a.loadControls();
            return null;
        });
        assertEquals(List.of("one", "", "three"), edt(() -> a.controls().arguments()), "the editors load the document's control sheets back");
        edt(desktop::close).get(60, TimeUnit.SECONDS);
    }

    @Test
    void theConstraintsTabDrawsPairsChecksTheirImpactAndSavesTheSidecar(@TempDir Path dir) throws Exception {
        Path scenario = org.ghyura.cf.desktop.session.CandidatePreviewTest.tutor(dir);
        Session session = session("cf.plan.autoPromoteSingleVerb=false");
        Desktop desktop = edt(() -> new Desktop(session, scenario));
        ConstraintsView view = desktop.constraints();
        edt(view::open).get(60, TimeUnit.SECONDS);
        edt(() -> {
            view.choose("payment", "account", "forbid", "any", 1);
            return null;
        });
        assertEquals("x forbid", edt(() -> view.matrix().getValueAt(1, 1)), "wallet (row 2) and guest (column 1) are the companion's bond");
        assertEquals("", edt(() -> view.matrix().getValueAt(0, 1)));
        edt(() -> {
            view.toggle(0, 0);
            return null;
        });
        assertEquals("x forbid", edt(() -> view.matrix().getValueAt(0, 1)));
        assertEquals(1, (int) edt(() -> view.rules().getRowCount()), "both pairs share one regrouped rule");
        assertEquals("2 forbid account\u00d7payment bond(s)", edt(() -> view.rules().getValueAt(0, 2)),
                "in words, without the legacy marker emoji desktop fonts may not draw (found on display, 0039)");
        assertTrue(edt(view::json).contains("\"card\""), edt(view::json));
        // the graph draws both pairs as threads between the values; the regrouped rule shows its parts
        assertEquals(2, (int) edt(() -> view.graph().currentLayout().bonds().size()));
        edt(() -> {
            view.selectRule(0);
            view.showGraph();
            return null;
        });
        assertTrue(edt(view::parts).startsWith("bond0_account_payment (forbid)\n  sheets: account, payment\n  pairs: 2\n    account="),
                edt(view::parts));
        edt(view::checkImpact).get(60, TimeUnit.SECONDS);
        assertTrue(edt(view::output).contains("executable: 96 (EXACT"), edt(view::output));
        edt(view::save).get(60, TimeUnit.SECONDS);
        assertTrue(edt(view::output).startsWith("Saved "), edt(view::output));
        assertTrue(Files.readString(dir.resolve("tutor.constraints.json")).contains("\"card\""));
        edt(desktop::close).get(60, TimeUnit.SECONDS);
    }

    @Test
    void mappingEntriesCanBeEditedAsValidatedJsonWithoutLosingTheLastGoodDraft(@TempDir Path dir) throws Exception {
        Path scenario = org.ghyura.cf.desktop.session.CandidatePreviewTest.tutor(dir);
        Path companion = dir.resolve("tutor.constraints.json");
        String original = Files.readString(companion);
        Session session = session("cf.plan.autoPromoteSingleVerb=false");
        Desktop desktop = edt(() -> new Desktop(session, scenario));
        try {
            ConstraintsView view = desktop.constraints();
            edt(view::open).get(60, TimeUnit.SECONDS);
            String mapping = """
                    {"version":1,"params":{"payment":{"card":{"risk":2}}},"constraints":[
                      {"id":"member_only","mapping":{"source":"payment","target":"account",
                        "allow":{"card":["member"],"wallet":["member"]}}}]}
                    """;
            edt(() -> view.applyJson(mapping)).get(60, TimeUnit.SECONDS);
            String applied = edt(view::json);
            assertTrue(applied.contains("\"mapping\"") && applied.contains("\"allow\""), applied);
            assertEquals(original, Files.readString(companion), "Apply changes the draft only");
            for (String invalid : List.of("{", mapping.replace("\"allow\"", "\"map\""))) {
                assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> edt(() -> view.applyJson(invalid)).get(60, TimeUnit.SECONDS));
                assertEquals(applied, edt(view::json), "malformed or unsupported data never replaces the last valid draft");
                assertEquals(original, Files.readString(companion));
            }
            edt(view::checkImpact).get(60, TimeUnit.SECONDS);
            assertTrue(edt(view::output).contains("executable: 96 (EXACT"), edt(view::output));
            edt(view::save).get(60, TimeUnit.SECONDS);
            assertEquals(applied, Files.readString(companion));
            session.validate(scenario);
            edt(view::open).get(60, TimeUnit.SECONDS);
            assertEquals(applied, edt(view::json), "saved mapping entries survive reopening");
        } finally {
            edt(desktop::close).get(60, TimeUnit.SECONDS);
        }
    }

    @Test
    void anInvalidCompanionCanBeOpenedRepairedAndSavedWithoutRunningIt(@TempDir Path dir) throws Exception {
        Path scenario = org.ghyura.cf.desktop.session.CandidatePreviewTest.tutor(dir);
        Path companion = dir.resolve("tutor.constraints.json");
        String invalid = """
                {"version":1,"constraints":[{"sheets":["payment","account"],"when":"payment.risk >","polarity":"forbid"}]}
                """;
        Files.writeString(companion, invalid);
        Session session = session("cf.plan.autoPromoteSingleVerb=false");
        assertThrows(org.ghyura.cf.model.FrameworkException.class, () -> session.validate(scenario));
        Desktop desktop = edt(() -> new Desktop(session, scenario));
        try {
            ConstraintsView view = desktop.constraints();
            edt(view::open).get(60, TimeUnit.SECONDS);
            assertEquals(1, (int) edt(() -> view.rules().getRowCount()), edt(view::output));
            RuleEditor editor = view.editor();
            edt(() -> { view.selectRule(0); return null; });
            assertTrue(edt(editor::problems).contains("is not a supported expression"), edt(editor::problems));
            assertEquals(invalid, Files.readString(companion), "opening for repair never rewrites the file");
            edt(() -> {
                editor.formula().setText("payment.value == 'card'");
                editor.check();
                return null;
            });
            assertEquals(RuleEditor.READS, edt(editor::problems));
            assertEquals(0, (int) edt(editor::apply));
            assertEquals(invalid, Files.readString(companion), "Apply changes only the draft");
            edt(view::save).get(60, TimeUnit.SECONDS);
            assertTrue(Files.readString(companion).contains("payment.value == 'card'"));
            session.validate(scenario);
        } finally {
            edt(desktop::close).get(60, TimeUnit.SECONDS);
        }
    }

    @Test
    void theRuleEditorBuildsFormulasAsBlocksNestsConditionsAndChecksTheirImpact(@TempDir Path dir) throws Exception {
        Path scenario = org.ghyura.cf.desktop.session.CandidatePreviewTest.tutor(dir);
        Session session = session("cf.plan.autoPromoteSingleVerb=false");
        Desktop desktop = edt(() -> new Desktop(session, scenario));
        ConstraintsView view = desktop.constraints();
        edt(view::open).get(60, TimeUnit.SECONDS);
        // the parameters formulas read, in the legacy params table
        edt(() -> {
            ConstraintsView.ParamsModel p = view.params();
            Map<String, String> rows = Map.of("payment:card", "risk=2", "payment:wallet", "risk=5", "account:guest", "trust=1",
                    "account:member", "trust=3");
            for (int r = 0; r < p.getRowCount(); r++) {
                if (rows.containsKey((String) p.getValueAt(r, 0))) {
                    p.setValueAt(rows.get((String) p.getValueAt(r, 0)), r, 1);
                }
            }
            return null;
        });
        assertTrue(edt(view::json).contains("\"risk\": 5"), edt(view::json));
        // a new formula rule built as blocks: compare payment.risk > account.trust
        RuleEditor editor = view.editor();
        edt(() -> {
            view.newFormulaRule();
            editor.setSheets(List.of("account", "payment"));
            FormulaEditor f = editor.formula();
            assertTrue(f.toBlocks(), "an empty text opens as an empty root slot");
            assertTrue(!f.paletteEnabled(org.ghyura.cf.desktop.session.FormulaBlocks.Kind.NUM), "the root takes a truth value, not a number");
            assertTrue(f.put(org.ghyura.cf.desktop.session.FormulaBlocks.Kind.CMP));
            f.select("a");
            assertTrue(f.put(org.ghyura.cf.desktop.session.FormulaBlocks.Kind.PARAM));
            f.setParam("payment", "risk");
            f.select("b");
            assertTrue(!f.put(org.ghyura.cf.desktop.session.FormulaBlocks.Kind.LOGIC), "a compare slot takes a number");
            assertTrue(f.put(org.ghyura.cf.desktop.session.FormulaBlocks.Kind.PARAM));
            f.setParam("account", "trust");
            editor.check();
            return null;
        });
        assertEquals("(payment.risk > account.trust)", edt(() -> editor.formula().built()));
        assertEquals("compare >\n  payment.risk\n  account.trust\n", edt(() -> editor.formula().dump()));
        assertEquals(RuleEditor.READS, edt(editor::problems));
        assertEquals(1, (int) edt(editor::apply), "the formula rule follows the companion's pair rule");
        assertEquals(2, (int) edt(() -> view.rules().getRowCount()));
        assertTrue(edt(view::json).contains("\"when\": \"(payment.risk > account.trust)\""), edt(view::json));
        assertTrue(edt(editor::title).startsWith("when0_account_payment (when rule)"), edt(editor::title));
        edt(view::checkImpact).get(60, TimeUnit.SECONDS);
        assertTrue(edt(view::output).contains("executable: 48 (EXACT"), "only card with member is left: " + edt(view::output));

        // its "only when", nested: on the web, and not with a duplicate submit
        edt(() -> {
            ConditionEditor c = editor.condition();
            c.add();
            c.add();
            c.setSheet("duplicate_submit");
            c.setOp(org.ghyura.cf.model.rule.Condition.Op.PRESENT);
            c.negate();
            return null;
        });
        assertEquals("all of\n  channel=web\n  not\n    duplicate_submit present\n", edt(() -> editor.condition().dump()));
        assertEquals(1, (int) edt(editor::apply));
        assertEquals("when0_account_payment (forbid)\n  sheets: account, payment\n  when: (payment.risk > account.trust)\n  condition\n    all of\n"
                + "      channel=web\n      not\n        duplicate_submit present\n", edt(view::parts));
        edt(view::checkImpact).get(60, TimeUnit.SECONDS);
        assertTrue(edt(view::output).contains("executable: 120 (EXACT"), edt(view::output));

        // the text mode: a formula the blocks cannot show stays text, a broken one is reported as a run reports it
        edt(() -> {
            FormulaEditor f = editor.formula();
            f.setText("payment.risk > account.trust and account.value != 'guest'");
            assertTrue(!f.toBlocks());
            editor.check();
            return null;
        });
        assertEquals("The blocks have no piece for the text 'guest' (the number block holds numbers only); keep editing it as text.",
                edt(() -> editor.formula().hint()));
        assertEquals(RuleEditor.READS, edt(editor::problems));
        edt(() -> {
            editor.formula().setText("payment.risk >");
            editor.check();
            return null;
        });
        assertTrue(edt(editor::problems).contains("'when' \"payment.risk >\" is not a supported expression"), edt(editor::problems));
        assertEquals(null, edt(editor::apply), "a refused draft is not applied");
        edt(() -> {
            editor.revert();
            return null;
        });
        assertEquals("(payment.risk > account.trust)", edt(() -> editor.formula().text()));

        // a new assert rule (the legacy relation dialog) and its removal
        edt(() -> {
            view.newAssertRule();
            editor.setPolarity("require");
            return null;
        });
        assertEquals("channel present\n", edt(() -> editor.assertion().dump()));
        assertEquals(2, (int) edt(editor::apply));
        assertTrue(edt(view::json).contains("\"assert\": {\n        \"sheet\": \"channel\""), edt(view::json));
        edt(() -> {
            view.removeRule();
            return null;
        });
        assertEquals(2, (int) edt(() -> view.rules().getRowCount()));
        edt(view::save).get(60, TimeUnit.SECONDS);
        String saved = Files.readString(dir.resolve("tutor.constraints.json"));
        assertTrue(saved.contains("\"not\": {") && saved.contains("\"when\": \"(payment.risk > account.trust)\"") && saved.contains("\"trust\": 3"),
                saved);
        edt(desktop::close).get(60, TimeUnit.SECONDS);
    }

    @Test
    void theLibraryTabListsSuitesOpensAScenarioAndChecksItsDocumentedCount(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("generator_trunk");
        Files.createDirectories(root.resolve("intake"));
        Files.createDirectories(root.resolve("usecases/pairs"));
        Files.createDirectories(root.resolve("usecases/gate"));
        Path spec = Files.writeString(root.resolve("usecases/pairs/pairs.toml"), "[[slots]]\nsheet = \"A\"\nvalues = [\"a\", \"b\", \"c\"]\n"
                + "[[slots]]\nsheet = \"B\"\nvalues = [\"x\", \"y\"]\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("usecases/gate/gen.py"), "# made by its application\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("usecases/pairs/subsets.toml"), "[[slots]]\nsheet = \"S\"\nverb = \"FW_Subsets\"\n"
                + "values = [\"p\", \"q\"]\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("usecases/pairs/sieved.toml"), "[[slots]]\nsheet = \"A\"\nraw = true\nvalues = [\"a\", \"b\"]\n"
                + "[[slots]]\nsheet = \"B\"\nraw = true\nvalues = [\"x\", \"y\"]\n[[constraints]]\nid = \"no_ax\"\nsheets = [\"A\", \"B\"]\n"
                + "pairs = [{A = \"a\", B = \"x\"}]\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("intake/verified_examples.json"), "[{\"id\": \"pairs\", \"spec\": \"usecases/pairs/pairs.toml\", "
                + "\"title\": \"Pairs\", \"blurb\": \"three letters by two\", \"candidates\": \"6\", \"pass\": \"6\", \"analyzer\": \"x:max\"}, "
                + "{\"id\": \"miscounted\", \"spec\": \"usecases/pairs/pairs.toml\", \"title\": \"Miscounted\", \"candidates\": \"7\", "
                + "\"pass\": \"7\"}, {\"id\": \"subsets\", \"spec\": \"usecases/pairs/subsets.toml\", \"candidates\": \"4\", \"pass\": \"4\"}, "
                + "{\"id\": \"sieved\", \"spec\": \"usecases/pairs/sieved.toml\", \"candidates\": \"4\", \"pass\": \"4\"}]",
                StandardCharsets.UTF_8);
        Files.writeString(root.resolve("intake/automation_scenarios.json"), "[{\"id\": \"gate\", \"spec_materializer\": "
                + "\"release_gate_breakpoint\", \"run_profile\": \"ai_combi_release_gate_breakpoint\", \"source\": \"usecases/gate/gen.py\", "
                + "\"difficulty_units\": [2, 4], \"suite\": \"ai\", \"suite_title\": \"AI\", \"title\": \"Release gate\"}]",
                StandardCharsets.UTF_8);
        Desktop desktop = edt(() -> new Desktop(session(), null));
        LibraryView library = desktop.library();
        edt(() -> library.open(root)).get(60, TimeUnit.SECONDS);
        assertEquals(List.of("AI (1)", "Verified examples (4)"), edt(library::suiteNames), "suites in manifest order, automation first");
        assertTrue(edt(library::where).endsWith(": 5 scenario(s) in 2 suite(s)"), edt(library::where));
        assertEquals("1024 candidates", edt(() -> library.scenarios().getValueAt(0, 1)), "a ladder of two levels is 2 x 512 candidates");
        assertTrue(edt(() -> tableNames(library.component())).contains("Scenarios of suite AI: documented counts and sources"),
                "the scenarios table names the suite it shows");
        edt(() -> {
            library.openSelected();
            return null;
        });
        assertTrue(edt(library::details).contains("made fresh by its application, which this edition does not carry: scenarios and specs are inputs"),
                edt(library::details));
        edt(() -> {
            library.selectSuite(1);
            library.selectScenario(0);
            return null;
        });
        assertTrue(edt(() -> tableNames(library.component())).contains("Scenarios of suite Verified examples: documented counts and sources"));
        assertTrue(edt(library::details).startsWith("Pairs\nid pairs · documented 6/6 PASS · goals x:max\nusecases/pairs/pairs.toml\n\n"
                + "three letters by two"), edt(library::details));
        edt(library::checkSelected).get(60, TimeUnit.SECONDS);
        assertTrue(edt(library::details).endsWith("plan: 6 executable candidates (EXACT); the library documents 6: equal\n"), edt(library::details));
        edt(() -> {
            library.selectScenario(1);
            return null;
        });
        edt(library::checkSelected).get(60, TimeUnit.SECONDS);
        // a repeated FW_Combi(1) gives each row back once and is not named as a reason
        assertTrue(edt(library::details).endsWith("the library documents 7: different\n"), edt(library::details));
        // the two known reasons a legacy figure differs (handoff 0056): a repeated lone FW_Subsets grows, and rules always apply
        edt(() -> {
            library.selectScenario(2);
            return null;
        });
        edt(library::checkSelected).get(60, TimeUnit.SECONDS);
        assertTrue(edt(library::details).endsWith("plan: 8 executable candidates (EXACT); the library documents 4: different; 1 sheet(s) "
                + "give duplicate rows that legacy runs removed: their fw.properties de-duplicated every pass and the final tables (here "
                + "cf.generation.distinctTables=true and cf.assembly.distinct=true)\n"), edt(library::details));
        edt(() -> {
            library.selectScenario(3);
            return null;
        });
        edt(library::checkSelected).get(60, TimeUnit.SECONDS);
        assertTrue(edt(library::details).endsWith("plan: 3 executable candidates (EXACT); the library documents 4: different; the "
                + "scenario's rules remove 1 candidate(s), which a legacy run kept unless it was started with --sieve\n"), edt(library::details));
        edt(() -> {
            library.selectScenario(1);
            return null;
        });
        edt(() -> {
            library.openSelected();
            return null;
        });
        assertEquals(spec.toRealPath(), edt(() -> desktop.scenario().scenario()), "the scenario tab now names the spec");
        edt(desktop::close).get(60, TimeUnit.SECONDS);

        // with the de-duplication of the legacy fw.properties the repeated FW_Subsets gives the documented count
        Desktop legacy = edt(() -> new Desktop(session("cf.generation.distinctTables=true", "cf.assembly.distinct=true"), null));
        edt(() -> legacy.library().open(root)).get(60, TimeUnit.SECONDS);
        edt(() -> {
            legacy.library().selectSuite(1);
            legacy.library().selectScenario(2);
            return null;
        });
        edt(() -> legacy.library().checkSelected()).get(60, TimeUnit.SECONDS);
        assertTrue(edt(() -> legacy.library().details()).endsWith("plan: 4 executable candidates (EXACT); the library documents 4: equal\n"),
                edt(() -> legacy.library().details()));
        edt(legacy::close).get(60, TimeUnit.SECONDS);
    }

    @Test
    void theCampaignTabShowsEachIterationAsItIsRecordedAndIsPausedResumedAndCancelled(@TempDir Path dir) throws Exception {
        Path scenario = scenario(dir);
        Session session = session("cf.execution.sut=inline-metrics", "cf.run.id=camp", "cf.execution.workers=1",
                "cf.pipeline.ordinal.window=2", "cf.analysis.goals=latency:min", "cf.campaign.iterations=3", "cf.campaign.strategy=noop");
        Desktop desktop = edt(() -> new Desktop(session, scenario));
        CampaignView view = desktop.campaign();
        CampaignController controller = view.controller();
        java.util.concurrent.atomic.AtomicBoolean pausedOnce = new java.util.concurrent.atomic.AtomicBoolean();
        controller.observe((iteration, p) -> {
            if (iteration == 2 && p.committed() >= 3 && pausedOnce.compareAndSet(false, true)) controller.pause();
        });
        edt(view::start).get(60, TimeUnit.SECONDS);
        until(() -> controller.view().state() == RunState.PAUSED);
        assertEquals(1, controller.view().iterations().size(), "iteration 1 is recorded while iteration 2 runs");
        edt(() -> {
            view.refresh();
            return null;
        });
        assertTrue(edt(view::status).contains("iteration 2 of 3 (noop), PAUSED"), edt(view::status));
        assertEquals(1, (int) edt(() -> view.lineage().getRowCount()), "the lineage grows as iterations are recorded");
        controller.resume();
        until(() -> !controller.view().going());
        edt(() -> {
            view.refresh();
            return null;
        });
        assertEquals(3, (int) edt(() -> view.lineage().getRowCount()));
        assertTrue(edt(view::output).startsWith("campaign COMPLETED: ran the 3 iterations requested"), edt(view::output));
        assertEquals("  campaign COMPLETED after 3 of 3 iterations (noop)", edt(view::status),
                "an ended campaign shows its own end, not the idle state of its last run (found on display, 0036)");
        assertEquals(RunState.COMPLETED, edt(() -> view.lineage().row(1).state()), "the paused iteration completed");

        controller.observeIterations(it -> controller.cancel());
        edt(view::start).get(60, TimeUnit.SECONDS);
        until(() -> !controller.view().going());
        edt(() -> {
            view.refresh();
            return null;
        });
        assertTrue(edt(view::output).startsWith("campaign CANCELLED: cancelled before iteration 2"), edt(view::output));
        assertEquals("  campaign CANCELLED after 1 of 3 iterations (noop)", edt(view::status));
        assertEquals(1, (int) edt(() -> view.lineage().getRowCount()));
        edt(desktop::close).get(60, TimeUnit.SECONDS);
    }

    @Test
    void theCandidatesTabPagesTheSieveOffTheEventThreadWithinTheScan(@TempDir Path dir) throws Exception {
        Path scenario = org.ghyura.cf.desktop.session.CandidatePreviewTest.tutor(dir);
        Session session = session("cf.plan.autoPromoteSingleVerb=false");
        Desktop desktop = edt(() -> new Desktop(session, scenario));
        CandidatesView view = desktop.candidates();
        edt(view::preview).get(60, TimeUnit.SECONDS);
        assertTrue(edt(() -> view.estimates().rows().stream().anyMatch(s -> s.name().equals("executable")
                && s.count().figure().equals("144") && s.count().mode() == org.ghyura.cf.generation.Cardinality.Mode.EXACT)));
        assertEquals("wallet_requires_member", edt(() -> view.rules().row(0).name()));
        assertEquals(java.math.BigInteger.valueOf(48), edt(() -> view.rules().row(0).removed()));
        assertEquals(50, (int) edt(() -> view.rows().getRowCount()));
        assertTrue(edt(view::status).startsWith("192 generated; this page holds 50 rows from ordinal 0"), edt(view::status));
        edt(() -> {
            view.select(0);
            return null;
        });
        assertTrue(edt(view::source).contains("web"), edt(view::source));
        java.math.BigInteger last = edt(() -> view.rows().row(49).ordinal());
        edt(view::nextPage).get(60, TimeUnit.SECONDS);
        assertTrue(edt(() -> view.rows().row(0).ordinal()).compareTo(last) > 0, "the next page follows the last one shown");

        edt(() -> {
            view.settings("2", 50, true);
            return null;
        });
        edt(view::preview).get(60, TimeUnit.SECONDS);
        assertTrue(edt(view::status).contains("scan limit of 2"), edt(view::status));

        var cancelled = edt(() -> {
            var future = view.preview();
            view.cancel();
            return future;
        });
        assertThrows(java.util.concurrent.ExecutionException.class, () -> cancelled.get(60, TimeUnit.SECONDS));
        assertTrue(edt(view::status).startsWith("The preview stopped: ") && edt(view::status).contains("RUN_CANCELLED"), edt(view::status));
    }

    @Test
    void campaignRefreshReplacesLineageEvenWhenItsLengthHasNotChanged(@TempDir Path dir) throws Exception {
        Path input = scenario(dir);
        CampaignView view = edt(() -> new CampaignView(session("cf.execution.sut=inline-metrics", "cf.campaign.iterations=1",
                "cf.campaign.strategy=noop", "cf.analysis.goals=latency:min"), () -> input));
        try {
            // Both completions occur between timer ticks: refresh must compare the data, not just its row count.
            edt(() -> {
                var first = view.controller().start(input).get(60, TimeUnit.SECONDS);
                view.refresh();
                Files.writeString(input, "{\"sheets\":{\"FW_Seq\":[[\"P\",\"FW_Combi(1)\"]],\"P\":[[\"latency=7;\"]]}}");
                var second = view.controller().start(input).get(60, TimeUnit.SECONDS);
                assertTrue(!first.iterations().getFirst().scenarioDigest().equals(second.iterations().getFirst().scenarioDigest()));
                view.refresh();
                assertEquals(second.iterations().getFirst().scenarioDigest(), view.lineage().row(0).scenarioDigest());
                assertEquals(second.iterations().getFirst().generated(), view.lineage().row(0).generated());
                return null;
            });
        } finally {
            edt(view::close).get(60, TimeUnit.SECONDS);
        }
    }

    @Test
    void aCampaignPreparationFailureIsNotOverwrittenByThePreviousReport(@TempDir Path dir) throws Exception {
        AtomicReference<Path> input = new AtomicReference<>(scenario(dir));
        CampaignView view = edt(() -> new CampaignView(session("cf.execution.sut=inline-metrics", "cf.campaign.iterations=1",
                "cf.campaign.strategy=noop", "cf.analysis.goals=latency:min"), input::get));
        try {
            view.controller().start(input.get()).get(60, TimeUnit.SECONDS);
            input.set(dir.resolve("missing.json"));
            var failure = edt(view::start);
            assertThrows(java.util.concurrent.ExecutionException.class, () -> failure.get(60, TimeUnit.SECONDS));
            edt(() -> { view.refresh(); return null; });
            assertTrue(edt(view::output).startsWith("The campaign could not start:"), edt(view::output));
            assertTrue(edt(view::status).contains("could not start"), edt(view::status));
        } finally {
            edt(view::close).get(60, TimeUnit.SECONDS);
        }
    }

    @Test
    void closingTheDesktopEndsItsPreviewAndRefusesAnother(@TempDir Path dir) throws Exception {
        Path input = Files.writeString(dir.resolve("filtered.json"), """
                {"constraints":[{"id":"none","assert":{"sheet":"P","present":false},"polarity":"require"}],
                 "slots":[{"sheet":"P","values":["a","b"],"verb":"FW_PermutR(40)"}]}
                """);
        Desktop desktop = edt(() -> new Desktop(session("cf.plan.autoPromoteSingleVerb=false"), input));
        var pending = edt(() -> {
            var preview = desktop.candidates().preview();
            desktop.close();
            return preview;
        });
        edt(desktop::close).get(60, TimeUnit.SECONDS);
        assertTrue(pending.isDone(), "close includes completion of the preview callback");
        assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.get(60, TimeUnit.SECONDS));
        var refused = edt(() -> desktop.candidates().preview());
        assertThrows(java.util.concurrent.ExecutionException.class, () -> refused.get(60, TimeUnit.SECONDS));
    }

    @Test
    void everyTabDrivesTheSharedSessionHeadlessWithPauseResumeAndCancel(@TempDir Path dir) throws Exception {
        assertTrue(GraphicsEnvironment.isHeadless(), "the tests never open a window");
        Path scenario = scenario(dir);
        Session session = session("cf.execution.sut=inline-metrics", "cf.run.id=desk", "cf.execution.workers=1", "cf.pipeline.ordinal.window=2",
                "cf.analysis.goals=latency:min", "cf.analysis.discover=false");
        Desktop desktop = edt(() -> new Desktop(session, scenario));

        edt(() -> desktop.scenario().validate()).get(60, TimeUnit.SECONDS);
        String validated = edt(() -> desktop.scenario().output());
        assertTrue(validated.contains("scenario: " + scenario) && validated.contains("plan"), validated);
        assertTrue(edt(() -> desktop.scenario().outputAtTop()), "a long summary is shown from its first line (found on display, 0036)");

        RunController controller = desktop.run().controller();
        java.util.concurrent.atomic.AtomicBoolean pausedOnce = new java.util.concurrent.atomic.AtomicBoolean();
        controller.observe(p -> {
            // resume() reports the same committed count again, so pause only once
            if (p.committed() >= 5 && pausedOnce.compareAndSet(false, true)) controller.pause();
        });
        edt(() -> {
            desktop.run().start();
            return null;
        });
        until(() -> controller.view().state() == RunState.PAUSED);
        edt(() -> {
            desktop.run().refresh();
            return null;
        });
        assertTrue(edt(() -> desktop.run().status()).contains("PAUSED"), "the view shows the paused run");
        controller.resume();
        until(() -> controller.view().state() == RunState.COMPLETED && !controller.view().report().isEmpty());
        edt(() -> {
            desktop.run().refresh();
            return null;
        });
        String report = edt(() -> desktop.run().output());
        assertTrue(report.contains("desk") && report.contains("32"), report);
        assertTrue(edt(() -> desktop.run().status()).contains("COMPLETED, 32 samples committed"));
        for (String kind : List.of("report", "results", "csv", "metrics", "checkpoint")) {
            Path out = dir.resolve("run." + kind);
            edt(() -> desktop.run().export(kind, out)).get(60, TimeUnit.SECONDS);
            assertTrue(edt(() -> desktop.run().status()).contains("exported the run's " + kind), kind);
            assertTrue(Files.size(out) > 0, kind);
        }
        assertTrue(Files.readString(dir.resolve("run.results")).startsWith(String.join(",", org.ghyura.cf.artifacts.ResultsExport.RESULTS_V2_COLUMNS)));
        var checkpoint = org.ghyura.cf.artifacts.CheckpointCodec.read(dir.resolve("run.checkpoint"));
        assertEquals(32, checkpoint.observations().size(), "the desktop's checkpoint is the artifact resume reads");

        // the metrics the run saves are what step 8 reads, and step 8 reads them as the run's own analysis read its samples
        edt(() -> {
            desktop.analysis().choose(dir.resolve("run.metrics"), null, null);
            return desktop.analysis().analyze();
        }).get(60, TimeUnit.SECONDS);
        String fromRun = edt(() -> desktop.analysis().output());
        String own = org.ghyura.cf.analysis.Analysis.analyzeObservations(org.ghyura.cf.desktop.session.CorpusExport.finals(checkpoint),
                org.ghyura.cf.analysis.Analysis.Options.of(session.configuration().config()), session.analysisBudget()).toText();
        // every count, measure, front and score alike; only which of tied samples is named first may differ, as the corpus
        // names samples by their display ids where the run's own analysis breaks ties by candidate id
        java.util.function.UnaryOperator<String> unnamed = text -> text.replaceAll("\\b\\d+_\\d+_\\d+\\b", "#");
        assertTrue(fromRun.contains("(32 samples, 0 without a verdict)") && unnamed.apply(fromRun).contains(unnamed.apply(own)),
                fromRun + "\n---\n" + own);
        // the run's tables and report are refused with the way to its metrics, not misread as metrics
        for (String kind : List.of("csv", "report")) {
            edt(() -> {
                desktop.analysis().choose(dir.resolve("run." + kind), null, null);
                return desktop.analysis().analyze();
            }).get(60, TimeUnit.SECONDS);
            String refused = edt(() -> desktop.analysis().output());
            assertTrue(refused.contains("metrics for step 8 (.kv)") && !refused.contains("samples"), kind + ": " + refused);
        }

        controller.observe(p -> {
            if (p.committed() == 3) controller.cancel();
        });
        edt(() -> {
            desktop.run().start();
            return null;
        });
        until(() -> controller.view().state() == RunState.CANCELLED && !controller.view().report().isEmpty());
        assertTrue(controller.view().report().contains("CANCELLED"), controller.view().report());

        Path corpus = Files.writeString(dir.resolve("results.kv"), "candidate_id=a latency=3 FW_VAR=0\ncandidate_id=b latency=1 FW_VAR=0\n",
                StandardCharsets.UTF_8);
        edt(() -> {
            desktop.analysis().choose(corpus, null, null);
            return desktop.analysis().analyze();
        }).get(60, TimeUnit.SECONDS);
        String analysed = edt(() -> desktop.analysis().output());
        assertTrue(analysed.contains("2 samples") && analysed.contains("front"), analysed);

        Path text = Files.writeString(dir.resolve("lines.log"), "run=1 latency=12 status=ok\nrun=2 latency=40 status=ok\n"
                + "run=3 latency=9 status=error\nrun=4 latency=11 status=ok\n", StandardCharsets.UTF_8);
        {
            edt(() -> {
                desktop.scorer().choose(text, "{\"forbidden_tokens\":[\"error\"]}");
                return desktop.scorer().autoPlan();
            }).get(60, TimeUnit.SECONDS);
            String planned = edt(() -> desktop.scorer().rulesText());
            assertTrue(planned.contains("\"forbidden_tokens\"") && planned.contains("\"key\": \"latency\""), planned);
            assertTrue(edt(() -> desktop.scorer().summary()).contains("suggested goals"), "the plan is shown");
        }
        TextScoring.Report scored = edt(() -> {
            desktop.scorer().choose(text, "{\"forbidden_tokens\":[\"error\"],\"optimization\":{\"key\":\"latency\",\"weight\":40}}");
            desktop.scorer().tune(2, 12, 50.0, 20.0);
            return desktop.scorer().score();
        }).get(60, TimeUnit.SECONDS);
        assertEquals(4, scored.lines().size());
        assertEquals(TextScoring.Report.class, scored.getClass());
        assertTrue(edt(() -> desktop.scorer().summary()).contains("=== OPTIMAL VARIANT (Highest Score) ==="));
        assertEquals(4, (int) edt(() -> desktop.scorer().visibleRows()));
        assertEquals(1, (int) edt(() -> {
            desktop.scorer().setFilter("ERROR");
            return desktop.scorer().visibleRows();
        }), "the legacy filter is case-insensitive over every column");
        String detail = edt(() -> {
            desktop.scorer().select(0);
            return desktop.scorer().detail();
        });
        assertTrue(detail.contains("ForbidToken:error") && detail.contains("line 3"), detail);
        javax.swing.JTabbedPane charts = edt(() -> desktop.scorer().charts());
        assertEquals(4, charts.getTabCount());
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(640, 420, java.awt.image.BufferedImage.TYPE_INT_RGB);
        edt(() -> {
            for (int i = 0; i < charts.getTabCount(); i++) {
                java.awt.Component chart = charts.getComponentAt(i);
                chart.setSize(640, 420);
                java.awt.Graphics2D g = image.createGraphics();
                chart.paint(g);
                g.dispose();
            }
            return null;
        });
        List<Double> scores = scored.lines().stream().map(ScoreBreakdown::score).toList();
        int[][] cost = ScoreChart.points(ScoreChart.Kind.COST, scores, 640, 420);
        assertEquals(ScoreChart.MARGIN, cost[0][0]);
        assertEquals(640 - ScoreChart.MARGIN, cost[3][0]);
        for (int i = 0; i < 4; i++) {
            int expected = 420 - ScoreChart.MARGIN - (int) ((100.0 - scores.get(i)) * (420 - 2 * ScoreChart.MARGIN) / 100.0);
            assertEquals(Math.max(ScoreChart.MARGIN, Math.min(420 - ScoreChart.MARGIN, expected)), cost[i][1], "the legacy cost geometry");
        }
        Path csv = dir.resolve("scores.csv");
        edt(() -> desktop.scorer().exportCsv(csv)).get(60, TimeUnit.SECONDS);
        assertEquals(scored.toCsv(), Files.readString(csv, StandardCharsets.UTF_8));
        assertEquals(ScoreBreakdown.REJECT, scored.lines().get(2).decision());

        edt(() -> {
            desktop.scorer().choose(text, "{\"required_regexes\":[\"(\"]}");
            return desktop.scorer().score();
        }).handle((r, e) -> null).get(60, TimeUnit.SECONDS);
        assertTrue(edt(() -> desktop.scorer().summary()).contains("INPUT_MALFORMED"), "a refusal is shown, not thrown");
        edt(desktop::close).get(60, TimeUnit.SECONDS);
        assertEquals(0, Frame.getFrames().length, "no window was created");
    }

    /**
     * The shown name of every table under the root, in component order: the title of the scroll pane around it, which the
     * table also carries as its component and accessible name.
     */
    private static List<String> tableNames(Container root) {
        List<String> names = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JTable t) {
                JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, t);
                String shown = pane == null ? null : TableTitles.shown(pane);
                assertTrue(shown != null && !shown.isBlank(), "a table shows no name: " + t.getModel().getClass().getName());
                assertEquals(shown, t.getName(), "the table carries the name it shows");
                assertEquals(shown, t.getAccessibleContext().getAccessibleName(), "a screen reader reads the shown name");
                names.add(shown);
            }
            if (c instanceof Container inner) {
                names.addAll(tableNames(inner));
            }
        }
        return names;
    }

    /** The author's rule: each table on the GUI shows its own name, saying what it holds; a table that follows a selection is renamed. */
    @Test
    void everyTableShowsItsOwnNameSayingWhatItHolds() throws Exception {
        Desktop desktop = edt(() -> new Desktop(session(), null));
        List<String> names = edt(() -> tableNames(desktop.component()));
        assertEquals(names.size(), new HashSet<>(names).size(), "each table has its own name: " + names);
        List<String> expected = List.of("FW_Seq plan: each sheet's role, values and directives",
                "Plan estimates: each count with its confidence and formula", "Rule impact: what each rule reads and the candidates it removes",
                "Candidate page: each candidate's ids and fate under the rules", "Operand dependencies: each joiner's operands and their lifetimes",
                "Chunk origins in generated order",
                "FW_CUSTOM_VAR: verdict codes and their names", "Bond matrix: forbidden or required value pairs between two sheets",
                "Rules of the constraints sidecar, in words", "Value parameters for formulas (the legacy params table)",
                "Campaign lineage: each iteration's run, counts, winners and front", "Scored lines: each line's score, decision and features",
                "Scenarios of the selected suite: documented counts and sources");
        assertTrue(names.containsAll(expected), names.toString());
        assertEquals(expected.size() + 1, names.size(), "and the authoring grid: " + names);
        assertTrue(names.stream().anyMatch(n -> n.startsWith("Cells of ")), names.toString());
        List<String> authoring = edt(() -> {
            AuthoringView a = desktop.authoring();
            a.newDocument();
            a.addSheet("Prices");
            a.selectSheet(a.document().sheetNames().indexOf("Prices"));
            return tableNames(a.component());
        });
        assertTrue(authoring.contains("Cells of sheet Prices"), "the grid names the sheet it shows: " + authoring);
        List<String> renamed = edt(() -> {
            AuthoringView a = desktop.authoring();
            a.renameSheet(a.document().sheetNames().indexOf("Prices"), "Discounts");
            return tableNames(a.component());
        });
        assertTrue(renamed.contains("Cells of sheet Discounts") && !renamed.contains("Cells of sheet Prices"), renamed.toString());
    }

    /** Every component under the root, depth first. */
    private static List<Component> all(Component root) {
        List<Component> out = new ArrayList<>(List.of(root));
        if (root instanceof Container k) {
            for (Component c : k.getComponents()) {
                out.addAll(all(c));
            }
        }
        return out;
    }

    /** The tab titles of every tab group under the root, in component order. */
    private static List<List<String>> tabGroups(Component root) {
        List<List<String>> groups = new ArrayList<>();
        for (Component c : all(root)) {
            if (c instanceof JTabbedPane tabs) {
                List<String> titles = new ArrayList<>();
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    titles.add(tabs.getTitleAt(i));
                }
                groups.add(titles);
            }
        }
        return groups;
    }

    /** The author's rule: every tab starts with its step, in the order a user works through the desktop. */
    @Test
    void everyTabStartsWithItsStepInTheOrderAUserWorksThroughThem(@TempDir Path dir) throws Exception {
        Desktop desktop = edt(() -> new Desktop(session(), null));
        List<List<String>> groups = edt(() -> tabGroups(desktop.component()));
        assertEquals(List.of("1 · Library", "2 · Authoring", "3 · Scenario", "4 · Constraints", "5 · Candidates", "6 · Run", "7 · Campaign",
                "8 · Results analysis", "9 · Text scorer"), groups.getFirst(), "the steps, numbered in working order");
        assertEquals(0, edt(() -> desktop.tabs().getSelectedIndex()), "without a scenario the desktop starts at step 1");
        // the tabs inside a step continue its number: 2.1, 2.2, ... and 4.1 .. 4.6 across the constraints' two groups
        java.util.Map<String, List<String>> nested = new java.util.LinkedHashMap<>();
        for (List<String> group : groups.subList(1, groups.size())) {
            for (String title : group) {
                nested.computeIfAbsent(title.substring(0, title.indexOf('.')), k -> new ArrayList<>()).add(title);
            }
        }
        assertEquals(List.of("2.1 · Control sheets", "2.2 · Validation", "2.3 · Operand dependencies", "2.4 · Dependency graph"), nested.get("2"));
        assertEquals(List.of("4.1 · Bond matrix", "4.2 · Graph", "4.3 · Parameters", "4.4 · Rule editor", "4.5 · Rule parts", "4.6 · JSON"),
                nested.get("4"));
        assertEquals(List.of("9.1 · Objective Function", "9.2 · Cost Function", "9.3 · Loss Function", "9.4 · Fitness Function"),
                edt(() -> tabGroups(desktop.scorer().charts()).getFirst()), "the scorer's chart window numbers its tabs too");
        Path scenario = Files.writeString(dir.resolve("s.json"), "{\"sheets\": {\"FW_Seq\": [[\"X\", \"FW_Combi(1)\"]], \"X\": [[\"a\"]]}}");
        Desktop opened = edt(() -> new Desktop(session(), scenario));
        assertEquals("3 · Scenario", edt(() -> opened.tabs().getTitleAt(opened.tabs().getSelectedIndex())), "a desktop opened on a scenario starts at step 3");
    }

    /**
     * Whether the component belongs to another control (a combo box's arrow, a spinner's field, a scroll bar's button) or is
     * a part the look and feel adds by itself (the scroll arrows of a tab strip that scrolls, as the FlatLaf desktop's does).
     */
    private static boolean partOfAnotherControl(Component c) {
        if (c instanceof javax.swing.plaf.UIResource) {
            return true;
        }
        for (Component k = c.getParent(); k != null; k = k.getParent()) {
            if (k instanceof JComboBox || k instanceof JSpinner || k instanceof JScrollBar || k instanceof JTable) {
                return true;
            }
        }
        return false;
    }

    /** The author's rules: hovering over any element shows a hint, and every hint is clear to any user. */
    @ParameterizedTest(name = "guif: {0}")
    @ValueSource(booleans = {false, true})
    void everyElementAndEveryTabHasAHintInPlainWords(boolean flat) throws Exception {
        Desktop desktop = edt(() -> new Desktop(session(), null, FileTrail.inMemory(), flat));
        List<String> missing = new ArrayList<>();
        List<String> hints = new ArrayList<>();
        edt(() -> {
            for (Component c : all(desktop.component())) {
                boolean control = c instanceof AbstractButton || c instanceof JTextComponent || c instanceof JList || c instanceof JTable
                        || c instanceof JComboBox || c instanceof JSpinner || c instanceof JTree;
                if (control && !partOfAnotherControl(c)) {
                    String own = Hints.own(c);
                    if (own == null) {
                        missing.add(c.getClass().getSimpleName() + (c instanceof AbstractButton b ? " \"" + b.getText() + "\"" : "") + " in "
                                + SwingUtilities.getAncestorOfClass(JTabbedPane.class, c));
                    } else {
                        hints.add(own);
                    }
                }
                if (c instanceof JTabbedPane tabs) {
                    for (int i = 0; i < tabs.getTabCount(); i++) {
                        String tab = Hints.tab(tabs, i);
                        if (tab == null) {
                            missing.add("tab " + tabs.getTitleAt(i));
                        } else {
                            hints.add(tab);
                        }
                    }
                }
            }
            return null;
        });
        assertEquals(List.of(), missing, "elements and tabs without a hint");
        assertTrue(hints.size() > 100, "hints: " + hints.size());
        for (String hint : hints) {
            assertTrue(hint.endsWith(".") && hint.split(" ").length >= 4, "a hint is a short sentence: " + hint);
            for (String word : List.of("legacy", "sidecar", "joiner", "operand", "directive", "the Core", "Reader")) {
                assertTrue(!hint.contains(word), "a hint avoids the internal word '" + word + "': " + hint);
            }
        }
    }

    /** Resting on each step's tab and on elements of the desktop shows their hints; moving on redraws the GUI correctly. */
    @Test
    void restingOnTabsAndElementsShowsTheirHintsAndMovingOnRedrawsTheGuiCorrectly() throws Exception {
        Desktop desktop = edt(() -> new Desktop(session(), null));
        edt(() -> {
            JComponent layer = desktop.component();
            layer.setSize(1400, 900);
            HintLayerTest.layout(layer);
            @SuppressWarnings("unchecked")
            javax.swing.JLayer<JComponent> l = (javax.swing.JLayer<JComponent>) layer;
            HintLayerTest.Recorder recorder = new HintLayerTest.Recorder(layer);
            HintLayerTest.recording(recorder, () -> {
                BufferedImage blank = HintLayerTest.paint(layer);
                BufferedImage screen = HintLayerTest.paint(layer);
                JTabbedPane tabs = desktop.tabs();
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    Rectangle header = tabs.getBoundsAt(i);
                    Point point = SwingUtilities.convertPoint(tabs, new Point(header.x + header.width / 2, header.y + header.height / 2), layer);
                    desktop.hints().hover(l, point);
                    assertEquals(null, desktop.hints().text(), "the previous pop-up goes as soon as the pointer moves on");
                    desktop.hints().settle(l);
                    assertEquals(Hints.purpose(tabs, i), desktop.hints().text(), "the hint of " + tabs.getTitleAt(i));
                    HintLayerTest.replay(recorder, layer, screen);
                    assertEquals(0, HintLayerTest.differing(screen, HintLayerTest.paint(layer)), "after resting on " + tabs.getTitleAt(i));
                }
                for (Component c : all(desktop.library().component())) {
                    if ((c instanceof JButton || c instanceof JTable) && c.isVisible() && c.getWidth() > 0) {
                        // a table may be empty and flat: its scroll pane's area stands for it
                        Component target = c instanceof JTable ? SwingUtilities.getAncestorOfClass(JScrollPane.class, c) : c;
                        desktop.hints().hover(l, HintLayerTest.center(target, layer));
                        desktop.hints().settle(l);
                        assertTrue(desktop.hints().text().startsWith(Hints.own(c)), "the hint of " + c + ", then its tooltip if any: " + desktop.hints().text());
                        HintLayerTest.replay(recorder, layer, screen);
                        assertEquals(0, HintLayerTest.differing(screen, HintLayerTest.paint(layer)), "after resting on " + c);
                    }
                }
                desktop.hints().hover(l, null);
                HintLayerTest.replay(recorder, layer, screen);
                assertEquals(0, HintLayerTest.differing(screen, blank), "moving off the desktop leaves no trace of any pop-up");
            });
            return null;
        });
    }

    /** A seeded walk over the real desktop: moves, rests, clicks, step changes and the hints button; every step leaves a correct GUI. */
    @Test
    void anyWalkOverTheDesktopLeavesTheGuiExactlyAsAFreshPaintingWould() throws Exception {
        Desktop desktop = edt(() -> new Desktop(session(), null));
        edt(() -> {
            JComponent layer = desktop.component();
            layer.setSize(1400, 900);
            HintLayerTest.layout(layer);
            @SuppressWarnings("unchecked")
            javax.swing.JLayer<JComponent> l = (javax.swing.JLayer<JComponent>) layer;
            HintLayerTest.Recorder recorder = new HintLayerTest.Recorder(layer);
            java.util.Random random = new java.util.Random(11102026L);
            HintLayerTest.recording(recorder, () -> {
                BufferedImage screen = HintLayerTest.paint(layer);
                int shown = 0;
                for (int step = 0; step < 160; step++) {
                    int what = random.nextInt(100);
                    JTabbedPane tabs = desktop.tabs();
                    if (what < 40) {
                        List<Component> hinted = new ArrayList<>();
                        for (Component c : all(tabs.getSelectedComponent())) {
                            boolean shownOnScreen = true;
                            for (Component k = c; k != null && k != layer; k = k.getParent()) {
                                shownOnScreen &= k.isVisible();
                            }
                            if (shownOnScreen && c.getWidth() > 0 && c.getHeight() > 0 && Hints.own(c) != null) {
                                hinted.add(c);
                            }
                        }
                        if (!hinted.isEmpty()) {
                            Point p = HintLayerTest.center(hinted.get(random.nextInt(hinted.size())), layer);
                            assertNotNull(HintLayerTest.move(l, p), "a move at " + p + " reaches the hints, step " + step);
                            if (random.nextBoolean()) {
                                desktop.hints().settle(l);
                            }
                        }
                    } else if (what < 50) {
                        Rectangle header = tabs.getBoundsAt(random.nextInt(tabs.getTabCount()));
                        Point p = SwingUtilities.convertPoint(tabs, new Point(header.x + 5, header.y + header.height / 2), layer);
                        assertNotNull(HintLayerTest.move(l, p), "a move at " + p + " reaches the hints, step " + step);
                    } else if (what < 75) {
                        desktop.hints().settle(l);
                    } else if (what < 82) {
                        tabs.setSelectedIndex(random.nextInt(tabs.getTabCount()));
                        HintLayerTest.layout(layer);
                        desktop.hints().press(l, new Point(10, 40));
                        screen = HintLayerTest.paint(layer);   // a new step is a new picture; from here on only repaints update it
                    } else if (what < 88) {
                        desktop.hints().press(l, new Point(random.nextInt(1400), random.nextInt(900)));
                    } else if (what < 90) {
                        desktop.hintsButton().doClick();
                        HintLayerTest.replay(recorder, layer, screen);
                        assertEquals(0, HintLayerTest.differing(screen, HintLayerTest.paint(layer)), "after the hints button, step " + step);
                        desktop.hintsButton().doClick();   // and back on, so the walk keeps showing pop-ups
                    } else {
                        Point p = new Point(random.nextInt(1400), random.nextInt(900));
                        assertNotNull(HintLayerTest.move(l, p), "a move at " + p + " reaches the hints, step " + step);
                    }
                    HintLayerTest.replay(recorder, layer, screen);
                    assertEquals(0, HintLayerTest.differing(screen, HintLayerTest.paint(layer)), "step " + step);
                    if (desktop.hints().text() != null) {
                        shown++;
                    }
                }
                assertTrue(shown > 20, "pop-ups shown in the walk: " + shown);
            });
            return null;
        });
    }

    /** The author's rule: hints and tooltips are switched by their own button; F1 presses it. */
    @Test
    void theHintsButtonTurnsHintsAndTooltipsOffAndOnAndF1PressesIt() throws Exception {
        Desktop desktop = edt(() -> new Desktop(session(), null));
        edt(() -> {
            JComponent layer = desktop.component();
            layer.setSize(1400, 900);
            HintLayerTest.layout(layer);
            @SuppressWarnings("unchecked")
            javax.swing.JLayer<JComponent> l = (javax.swing.JLayer<JComponent>) layer;
            javax.swing.JToggleButton button = desktop.hintsButton();
            assertTrue(button.isShowing() || button.getParent() != null, "the button is part of the desktop");
            assertEquals("Hints on", button.getText());
            assertTrue(Hints.own(button) != null, "the button explains itself");
            assertTrue(!javax.swing.ToolTipManager.sharedInstance().isEnabled(), "tooltips show inside the hint pop-ups, not on their own");
            Rectangle header = desktop.tabs().getBoundsAt(2);
            Point point = SwingUtilities.convertPoint(desktop.tabs(), new Point(header.x + 5, header.y + 5), layer);
            desktop.hints().hover(l, point);
            desktop.hints().settle(l);
            assertTrue(desktop.hints().text() != null);
            button.doClick();
            assertEquals("Hints off", button.getText());
            assertEquals(null, desktop.hints().text(), "turning hints off removes the pop-up");
            desktop.hints().hover(l, new Point(point.x + 2, point.y));
            desktop.hints().settle(l);
            assertEquals(null, desktop.hints().text(), "and none comes while they are off");
            l.getActionMap().get("cf.hints.toggle").actionPerformed(new java.awt.event.ActionEvent(l, 0, "F1"));
            assertTrue(button.isSelected() && button.getText().equals("Hints on"), "F1 presses the button");
            desktop.hints().hover(l, point);
            desktop.hints().settle(l);
            assertTrue(desktop.hints().text() != null, "hints are back");
            return null;
        });
    }

    /** The same rule in the chart window: its hints follow the desktop's Hints button, and F1 there presses that button. */
    @Test
    void theChartWindowsHintsFollowTheHintsButtonAndF1ThereToo() throws Exception {
        Desktop desktop = edt(() -> new Desktop(session(), null));
        edt(() -> {
            javax.swing.JLayer<JComponent> charts = desktop.scorer().chartWindowContent();
            charts.setSize(680, 460);
            HintLayerTest.layout(charts);
            HintLayer hints = (HintLayer) charts.getUI();
            JTabbedPane tabs = (JTabbedPane) charts.getView();
            java.util.function.IntFunction<Point> header = i -> {
                Rectangle r = tabs.getBoundsAt(i);
                return SwingUtilities.convertPoint(tabs, new Point(r.x + 5, r.y + r.height / 2), charts);
            };
            assertNotNull(HintLayerTest.move(charts, header.apply(1)), "a move in the chart window reaches its hints");
            hints.settle(charts);
            assertTrue(hints.text() != null && hints.text().startsWith("What is “9.2"), "a chart tab explains itself: " + hints.text());
            assertEquals("cf.hints.toggle", charts.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(javax.swing.KeyStroke.getKeyStroke("F1")));
            charts.getActionMap().get("cf.hints.toggle").actionPerformed(new java.awt.event.ActionEvent(charts, 0, "F1"));
            assertEquals("Hints off", desktop.hintsButton().getText(), "F1 in the chart window presses the desktop's button");
            assertEquals(null, hints.text(), "and removes the chart window's pop-up");
            HintLayerTest.move(charts, header.apply(2));
            hints.settle(charts);
            assertEquals(null, hints.text(), "no pop-up in the chart window while hints are off");
            HintLayerTest.leave(charts);
            desktop.hintsButton().doClick();
            assertEquals("Hints on", desktop.hintsButton().getText());
            HintLayerTest.move(charts, header.apply(2));
            hints.settle(charts);
            assertTrue(hints.text() != null && hints.text().startsWith("What is “9.3"), "hints are back in the chart window: " + hints.text());
            return null;
        });
    }

    /** Whether the component is a field: something a user reads or types into, other than a button or a table. */
    private static boolean field(Component c) {
        return (c instanceof JTextComponent || c instanceof JComboBox || c instanceof JSpinner || c instanceof JList || c instanceof JTree)
                && !partOfAnotherControl(c);
    }

    /** The author's rule: every field shows its own name, the name its screen reader reads; names differ within a step. */
    @ParameterizedTest(name = "guif: {0}")
    @ValueSource(booleans = {false, true})
    void everyFieldShowsItsOwnName(boolean flat) throws Exception {
        Desktop desktop = edt(() -> new Desktop(session(), null, FileTrail.inMemory(), flat));
        List<String> problems = new ArrayList<>();
        edt(() -> {
            JTabbedPane tabs = desktop.tabs();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                java.util.Map<String, Integer> names = new java.util.HashMap<>();
                for (Component c : all(tabs.getComponentAt(i))) {
                    if (!field(c)) {
                        continue;
                    }
                    JComponent f = (JComponent) c;
                    String shown = FieldNames.shown(f);
                    String what = f.getClass().getSimpleName() + " (" + Hints.own(f) + ") in " + tabs.getTitleAt(i);
                    if (shown == null) {
                        problems.add("no visible name: " + what);
                        continue;
                    }
                    if (!shown.equals(f.getAccessibleContext().getAccessibleName())) {
                        problems.add("accessible name " + f.getAccessibleContext().getAccessibleName() + " is not the shown " + shown + ": " + what);
                    }
                    javax.swing.JLabel label = FieldNames.labelOf(f);
                    if (label != null && label.isVisible() != f.isVisible()) {
                        problems.add("the name " + shown + " is not shown together with its field: " + what);
                    }
                    names.merge(FieldNames.sectionOf(f) + " / " + shown, 1, Integer::sum);
                }
                names.forEach((name, n) -> {
                    if (n > 1) {
                        problems.add(n + " fields of one section share the name " + name);
                    }
                });
            }
            return null;
        });
        assertEquals(List.of(), problems);
    }

    /** The author's rule: an empty text field or area says in its hint what fills it. */
    @ParameterizedTest(name = "guif: {0}")
    @ValueSource(booleans = {false, true})
    void everyTextFieldAndAreaSaysInItsHintWhenItIsFilled(boolean flat) throws Exception {
        Desktop desktop = edt(() -> new Desktop(session(), null, FileTrail.inMemory(), flat));
        List<String> problems = new ArrayList<>();
        java.util.regex.Pattern filled = java.util.regex.Pattern.compile("\\bFilled (when|after|while|by|from|with|as)\\b");
        edt(() -> {
            for (Component c : all(desktop.component())) {
                if (c instanceof JTextComponent text && !partOfAnotherControl(c)) {
                    String hint = Hints.own(text);
                    if (hint == null || !filled.matcher(hint).find()) {
                        problems.add(text.getClass().getSimpleName() + " named " + FieldNames.shown(text) + ": " + hint);
                    }
                }
            }
            return null;
        });
        assertEquals(List.of(), problems, "text fields and areas whose hint does not say what fills them");
    }

    @Test
    void aScenarioAuthoredInTheEditorIsValidatedUnsavedSavedPlannedAndRun(@TempDir Path dir) throws Exception {
        Session session = session("cf.execution.sut=inline-metrics", "cf.run.id=authored", "cf.execution.workers=1");
        Desktop desktop = edt(() -> new Desktop(session, null));
        edt(() -> {
            AuthoringView a = desktop.authoring();
            a.newDocument();
            a.addSheet("P");
            a.addSheet("Q");
            a.addSheet("q");
            a.selectSheet(0);
            a.type(0, 0, "P");
            a.type(0, 1, "FW_Combi(1)");
            a.type(1, 0, "Q");
            a.type(1, 1, "FW_Combi(1)");
            a.selectSheet(1);
            a.type(0, 0, "latency=10; throughput=5;");
            a.type(1, 0, "latency=20; throughput=9;");
            a.type(2, 0, "latency=30; throughput=1;");
            a.selectSheet(2);
            a.type(0, 0, "mode=a;");
            a.type(1, 0, "mode=b;");
            return null;
        });
        assertEquals(List.of("FW_Seq", "P", "Q"), edt(() -> desktop.authoring().document().sheetNames()), "a duplicate name is refused");
        edt(() -> desktop.authoring().validate()).get(60, TimeUnit.SECONDS);
        String unsaved = edt(() -> desktop.authoring().output());
        assertTrue(unsaved.contains("plan") && unsaved.contains("2 sheets have directives"), unsaved);
        Path file = dir.resolve("authored.yaml");
        edt(() -> desktop.authoring().save(file)).get(60, TimeUnit.SECONDS);
        assertEquals(file, edt(() -> desktop.scenario().scenario()), "the saved scenario is the scenario tab's choice");
        edt(() -> desktop.scenario().validate()).get(60, TimeUnit.SECONDS);
        assertEquals(2, (int) edt(() -> desktop.scenario().plan().getRowCount()));
        assertEquals("P", edt(() -> desktop.scenario().plan().getValueAt(0, 1)));
        assertEquals("FW_Combi(1)  FW_Combi(1)", edt(() -> desktop.scenario().plan().getValueAt(0, 7)),
                "a single bracketed verb is repeated (legacy promotion), and the plan shows it");
        RunController controller = desktop.run().controller();
        edt(() -> {
            desktop.run().start();
            return null;
        });
        until(() -> controller.view().state() == RunState.COMPLETED && !controller.view().report().isEmpty());
        assertEquals(6, controller.view().committed());
        edt(desktop::close).get(60, TimeUnit.SECONDS);
    }
}
