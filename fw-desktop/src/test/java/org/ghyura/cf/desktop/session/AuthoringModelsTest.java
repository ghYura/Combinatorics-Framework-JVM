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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GEN:operator-authoring-ui: the legacy palette and the operand lifetimes a joiner sees, through the shared session. */
class AuthoringModelsTest {

    /** A document with FW_Seq rows (first cell the target sheet) and data sheets of the given values. */
    private static ScenarioDocument document(List<List<String>> seq, Map<String, List<String>> sheets) {
        ScenarioDocument d = ScenarioDocument.create();
        for (int r = 0; r < seq.size(); r++) {
            for (int c = 0; c < seq.get(r).size(); c++) {
                d.setCell(0, r, c, seq.get(r).get(c));
            }
        }
        for (Map.Entry<String, List<String>> e : sheets.entrySet()) {
            d.addSheet(e.getKey());
            int index = d.sheetNames().indexOf(e.getKey());
            for (int r = 0; r < e.getValue().size(); r++) {
                d.setCell(index, r, 0, e.getValue().get(r));
            }
        }
        return d;
    }

    private static List<Diagnostic> errors(Session session, ScenarioDocument d, Path file) {
        try {
            return session.validate(d, file).findings().stream().filter(x -> x.severity() == Severity.ERROR).toList();
        } catch (FrameworkException e) {
            return e.diagnostics().stream().filter(x -> x.severity() == Severity.ERROR).toList();
        }
    }

    private static final Map<String, List<String>> HELPERS = new java.util.LinkedHashMap<>();

    static {
        // a fixed sheet order (Map.of iterates in no stable order); the group cleanup cell's replacement "[" + 0 +"]"
        // names a sheet 0, as the placeholders name theirs
        String[][] helpers = {{"A", "a", "b", "c"}, {"OTHER", "x", "y"}, {"LEFT", "l1", "l2"}, {"RIGHT", "r1", "r2"}, {"START", "s"},
                {"RELATION", "rel"}, {"END", "e"}, {"SEPARATOR", "sep"}, {"J00", "j00"}, {"J0", "j0"}, {"J", "j"}, {"0", "0"}};
        for (String[] h : helpers) {
            HELPERS.put(h[0], List.of(java.util.Arrays.copyOfRange(h, 1, h.length)));
        }
    }

    @Test
    void thePaletteIsTheLegacyOneAndThePlannerAcceptsEveryTemplate(@TempDir Path dir) {
        List<CellTemplates.CellTemplate> all = CellTemplates.ALL;
        assertEquals(55, all.size());
        assertEquals(all.size(), all.stream().map(CellTemplates.CellTemplate::id).distinct().count(), "ids are unique (drag and drop)");
        assertEquals(List.of("Row role", "Choose", "Order", "Set", "Compose", "Transform"), CellTemplates.categories());
        Set<String> directives = new HashSet<>(all.stream().map(CellTemplates.CellTemplate::directive).toList());
        // the legacy face1 test_core_templates expectations
        assertTrue(directives.containsAll(Set.of("FW_Exclude", "FW_Optional", "FW_Reuse", "FW_ReuseTableOnly", "FW_Heading", "FW_LastInQueue",
                "FW_Concatenator=")));
        assertTrue(directives.containsAll(Set.of("FW_Combi", "FW_Combi()", "FW_Combi(0)", "FW_Combi(1)", "FW_Combi(2)", "FW_Combi(size)",
                "FW_Combi(all)", "FW_Combi(full)", "FW_CombiR(2)", "FW_CombiR(size)", "FW_CombiR(all)", "FW_CombiR(full)")));
        assertTrue(directives.containsAll(Set.of("FW_Permut", "FW_Permut()", "FW_Permut(2)",
                "FW_Permut(PermutationGenerator.TreatDuplicatesAs.IDENTICAL)", "FW_PermutR(2)", "FW_PermutR(size)", "FW_PermutR(all)",
                "FW_PermutR(full)")));
        assertTrue(directives.containsAll(Set.of("FW_Subsets", "FW_Subsets_EXACT(2)", "FW_Subsets_BEFORE(2)", "FW_Subsets_AFTER(2)",
                "FW_Subsets_RANGE(2,3)", "FW_Subsets_GIVEN(1,3)")));
        for (String m : List.of("1:1", "1:N", "M:1", "M:M", "M:N")) {
            assertTrue(directives.contains("FW_(,,LEFT,,RIGHT,,,," + m + ")"), m);
        }

        Session session = SessionTest.session();
        Path file = dir.resolve("palette.json");
        List<String> refused = new ArrayList<>();
        for (CellTemplates.CellTemplate t : all) {
            List<List<String>> seq = new ArrayList<>(List.of(List.of("LEFT", "FW_Exclude", "FW_Combi(1)", "FW_Reuse"),
                    List.of("RIGHT", "FW_Exclude", "FW_Combi(1)", "FW_Reuse")));
            switch (t.category()) {
                case "Row role" -> seq.add(List.of("A", t.directive(), "FW_Combi(1)"));
                // a FW_ReplaceRE line joins its FW_Group cell, as the palette puts it there
                case "Transform" -> seq.add(List.of("A", "FW_Combi(2)", CellTemplates.apply(t.id().equals("replace") ? "FW_Group" : "",
                        t), "FW_Combi(1)"));
                default -> {
                    if (t.directive().startsWith("FW_(")) {
                        // a nested operand names an earlier joiner target; "both" forms name the two most recent ones
                        seq.add(List.of("J00", "FW_(,,LEFT,,RIGHT,,,,M:N)"));
                        seq.add(List.of("J0", "FW_(,,LEFT,,RIGHT,,,,M:N)"));
                        seq.add(List.of("J", t.directive()));
                    } else {
                        seq.add(List.of("A", t.directive()));
                    }
                }
            }
            List<Diagnostic> errors = errors(session, document(seq, HELPERS), file);
            if (!errors.isEmpty()) {
                refused.add(t.id() + ": " + errors);
            }
        }
        assertEquals(List.of(), refused, "every palette template plans without an error in its context");
        CellTemplates.CellTemplate replace = CellTemplates.byId("replace").orElseThrow();
        assertEquals("FW_Group\n" + replace.directive(), CellTemplates.apply("FW_Group ", replace), "a FW_ReplaceRE line joins its group cell");
        assertEquals(replace.directive(), CellTemplates.apply("FW_Combi(2)", replace), "elsewhere a template replaces the cell");
    }

    /** LEFT and RIGHT with their reuse flags joined by JOIN1 and, when asked, JOIN2 (the legacy joined_project). */
    private static ScenarioDocument joined(List<String> left, List<String> right, boolean second) {
        List<List<String>> seq = new ArrayList<>();
        List<String> l = new ArrayList<>(List.of("LEFT", "FW_Exclude", "FW_Combi(1)"));
        l.addAll(left);
        List<String> r = new ArrayList<>(List.of("RIGHT", "FW_Exclude", "FW_Combi(1)"));
        r.addAll(right);
        seq.add(l);
        seq.add(r);
        seq.add(List.of("JOIN1", "FW_(,,LEFT,,RIGHT,,,,M:N)"));
        if (second) {
            seq.add(List.of("JOIN2", "FW_(,,LEFT,,RIGHT,,,,M:N)"));
        }
        // sheets in a fixed order: their keys decide the order joiners run in (ties in Generation.order)
        Map<String, List<String>> sheets = new java.util.LinkedHashMap<>();
        sheets.put("LEFT", List.of("a", "b"));
        sheets.put("RIGHT", List.of("c", "d"));
        sheets.put("JOIN1", List.of("j1"));
        sheets.put("JOIN2", List.of("j2"));
        return document(seq, sheets);
    }

    private static OperandDependencies dependencies(Session session, ScenarioDocument d, Path file) {
        return OperandDependencies.of(session.validate(d, file).plan());
    }

    @Test
    void operandLifetimesArePredictedAsTheGenerationReadsThem(@TempDir Path dir) {
        Session session = SessionTest.session();
        Path file = dir.resolve("joined.json");
        OperandDependencies kept = dependencies(session, joined(List.of("FW_Reuse"), List.of("FW_Reuse"), true), file);
        assertEquals(List.of("JOIN1", "JOIN2"), kept.joins().stream().map(OperandDependencies.Join::target).toList());
        assertEquals(List.of(), kept.findings(), "retained operands feed two joins");
        assertEquals(OperandDependencies.Lifetime.KEPT, kept.joins().get(1).first().lifetime());

        for (List<String> mode : List.of(List.<String>of(), List.of("FW_ReuseTableOnly"))) {
            OperandDependencies cleaned = dependencies(session, joined(mode, List.of("FW_Reuse"), true), file);
            assertEquals(1, cleaned.findings().size(), mode.toString());
            Diagnostic d = cleaned.findings().getFirst();
            assertEquals(ErrorCode.GEN_OPERAND_CONSUMED, d.code());
            assertTrue(d.message().startsWith("the joiner of JOIN2 would read LEFT, whose rows the joiner of JOIN1 consumes"), d.message());
            assertTrue(cleaned.joins().get(1).first().readsNothing());
            assertEquals("JOIN1", cleaned.joins().get(1).first().consumedBy());
            // the generation, when it runs, reports the same read of an empty operand
            List<Diagnostic> generated = ScenarioValidation.prepare(file.toString(), org.ghyura.cf.input.ScenarioLoader.interpret(
                    new org.ghyura.cf.input.ReadResult(joined(mode, List.of("FW_Reuse"), true).toWorkbook(file.toString()), List.of()),
                    org.ghyura.cf.input.FileResolver.inDirectory(dir, 1 << 20)), session.configuration().config(), (summary, dictionary, budget) -> {
                        try (Generation.Result r = Generation.run(summary.plan(), dictionary, GenerationOptions.of(session.configuration().config()),
                                budget, Cancellation.NONE)) {
                            return r.result().diagnostics();
                        }
                    });
            assertEquals(1, generated.stream().filter(g -> g.code() == ErrorCode.GEN_OPERAND_CONSUMED && g.message().startsWith(
                    "the joiner of JOIN2 reads LEFT, whose rows the joiner of JOIN1 consumed")).count(), generated.toString());
        }
        assertEquals(List.of(), dependencies(session, joined(List.of(), List.of("FW_ReuseTableOnly"), false), file).findings(),
                "a single join's operands have independent cleanup modes");
        assertEquals(List.of(), dependencies(session, joined(List.of("FW_Reuse", "FW_ReuseTableOnly"), List.of("FW_Reuse"), true), file)
                .findings(), "FW_Reuse keeps the rows when both flags are present");

        ScenarioDocument nested = joined(List.of("FW_Reuse"), List.of("FW_Reuse"), true);
        nested.setCell(0, 3, 1, "FW_(,,FW_(),,RIGHT,,,,M:N)");
        OperandDependencies resolved = dependencies(session, nested, file);
        assertEquals("FW_()", resolved.joins().get(1).first().written());
        assertEquals("JOIN1", resolved.joins().get(1).first().sheet(), "a nested operand names the most recent earlier joiner target");
        assertNull(resolved.joins().get(1).first().consumedBy());
    }

    private static final String RUN_ONCE = """
            class RunMeFirstOnce {
            public static String FW_ARGS;
            public static void main(String[] args) {
                FW_ARGS = "prepared";
            }
            }""";

    @Test
    void controlSheetsRoundTripWithTheirMarkersAndTheChecksOfTheLegacyEditor(@TempDir Path dir) {
        ScenarioDocument d = document(List.of(List.of("A", "FW_Combi(1)")), Map.of("A", List.of("a")));
        ControlSheets written = new ControlSheets(RUN_ONCE, List.of("arg-one", "", "arg-three"), List.of(new ControlSheets.Verdict("2",
                "bad response"), new ControlSheets.Verdict("9", "")));
        written.applyTo(d);
        assertEquals(written, ControlSheets.of(d), "the control sheets read back as written");
        int args = d.sheetNames().indexOf("FW_Arguments");
        int vars = d.sheetNames().indexOf("FW_CUSTOM_VAR");
        assertEquals(List.of("arg-one", "FW_EMPTY_STRING", "arg-three"), List.of(d.cell(args, 0, 0), d.cell(args, 1, 0), d.cell(args, 2, 0)),
                "an empty argument keeps its place as the legacy marker");
        assertEquals("FW_EMPTY_STRING", d.cell(vars, 1, 1));
        assertEquals(List.of(), written.issues());

        ScenarioValidation.Summary summary = SessionTest.session().validate(d, dir.resolve("controls.json"));
        assertEquals(RUN_ONCE, summary.spec().controls().runMeFirstOnce().getFirst().text(), "the interpreter reads the bootstrap cell");
        assertEquals(List.of(2, 9), summary.spec().controls().customVars().stream().map(v -> v.index()).toList());

        ControlSheets wrong = new ControlSheets("print('not the expected bootstrap class')", List.of(), List.of(
                new ControlSheets.Verdict("abc", "bad code"), new ControlSheets.Verdict("2", "first"), new ControlSheets.Verdict("02", "duplicate")));
        List<ControlSheets.Issue> issues = wrong.issues();
        assertTrue(issues.stream().anyMatch(i -> i.severity() == Severity.WARNING && i.message().startsWith("FW_RunMeFirstOnce A1")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("'abc' must be a Java integer")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("'02' duplicates verdict code 2 of row 2")), issues.toString());
    }
}
