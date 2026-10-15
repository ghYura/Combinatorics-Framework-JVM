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

package org.ghyura.cf.constraints;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.Condition;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.rule.ExprParser;
import org.ghyura.cf.model.rule.Gate;
import org.ghyura.cf.model.rule.LegacyBond;
import org.ghyura.cf.model.rule.OrderSpec;
import org.ghyura.cf.model.rule.Rule;
import org.ghyura.cf.model.rule.RuleValue;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-derived cases of the rule engine, independent of the golden comparison: absence-sensitive rules on optional
 * sheets, the early (mandatory-only) and late split, reference and order errors with locations, inert parts, and the
 * worked example of the legacy sieve's own demo.
 */
class RuleEngineTest {

    private static final SourceLocation HERE = SourceLocation.of("test.constraints.json", 1);
    private static final List<String> TEXTS = List.of("a11", "a12", "c11", "c12", "o1", "o2", "m1");

    private static int label(String text) {
        return TEXTS.indexOf(text);
    }

    private static ColumnLayout layout(String... sheets) {
        List<ColumnLayout.Column> columns = new ArrayList<>();
        for (int i = 0; i < sheets.length; i++) {
            boolean optional = sheets[i].startsWith("O");
            columns.add(new ColumnLayout.Column(sheets[i], i + 1, optional));
        }
        return new ColumnLayout(columns);
    }

    private static RuleEngine engine(ConstraintSet set, ColumnLayout layout, List<String> sheets) {
        return RuleEngine.compile(set, layout, sheets, TEXTS::get, TEXTS);
    }

    private static ConstraintSet rules(Rule... rules) {
        return new ConstraintSet(List.of(), Map.of(), Map.of(), List.of(rules), List.of());
    }

    private static Rule rule(String id, Rule.Polarity polarity, Gate gate, Condition guard, Rule.Body body) {
        return new Rule(id, polarity, gate, guard, body, null, HERE);
    }

    /** A candidate: each column's texts, or null for an absent sheet. */
    private static CandidateView view(String[]... columns) {
        return column -> {
            String[] texts = columns[column];
            if (texts == null) {
                return null;
            }
            int[] labels = new int[texts.length];
            for (int i = 0; i < texts.length; i++) {
                labels[i] = label(texts[i]);
            }
            return labels;
        };
    }

    private static String[] v(String... texts) {
        return texts;
    }

    private static List<String> names(RuleEngine engine, BitSet hits) {
        List<String> out = new ArrayList<>();
        hits.stream().forEach(i -> out.add(engine.name(i)));
        return out;
    }

    /** constraints/sieve.py _demo_sidecar: A and C adjacent; a11 never with c11; like charges never adjacent. */
    @Test
    void theLegacyDemoKeepsOneRowOfFour() {
        Map<String, Map<String, Map<String, RuleValue>>> params = new LinkedHashMap<>();
        params.put("A", Map.of("a11", Map.of("charge", RuleValue.Int.of(2)), "a12", Map.of("charge", RuleValue.Int.of(-1))));
        params.put("C", Map.of("c11", Map.of("charge", RuleValue.Int.of(-2)), "c12", Map.of("charge", RuleValue.Int.of(3))));
        Rule ban = rule("ban_a11_c11", Rule.Polarity.FORBID, Gate.ANY, null,
                new Rule.Pairs(List.of("A", "C"), List.of(Map.of("A", new RuleValue.Text("a11"), "C", new RuleValue.Text("c11")))));
        Rule like = rule("no_like_charge_adjacency", Rule.Polarity.FORBID, new Gate(true, -1), null,
                new Rule.When(List.of("A", "C"), "A.charge * C.charge > 0", ExprParser.parse("A.charge * C.charge > 0")));
        RuleEngine e = engine(new ConstraintSet(List.of(), params, Map.of(), List.of(ban, like), List.of()), layout("A", "C"), List.of("A", "C"));
        assertEquals(List.of("ban_a11_c11"), names(e, e.violations(view(v("a11"), v("c11")))), "2 * -2 is not like-charged");
        assertEquals(List.of("no_like_charge_adjacency"), names(e, e.violations(view(v("a11"), v("c12")))));
        assertEquals(List.of("no_like_charge_adjacency"), names(e, e.violations(view(v("a12"), v("c11")))));
        assertEquals(List.of(), names(e, e.violations(view(v("a12"), v("c12")))));
        assertTrue(e.mandatoryOnly(0) && e.mandatoryOnly(1));
    }

    @Test
    void absenceSensitiveRulesOnOptionalSheetsAreEvaluatedWhereTheLegacyReaderFailedClosed() {
        // require: when O1 is absent, O2 is absent too (an assertion over presence)
        Condition o1Absent = Condition.Leaf.present("O1", false);
        Rule bothOrNone = rule("both_or_none", Rule.Polarity.REQUIRE, Gate.ANY, o1Absent,
                new Rule.Assert(Condition.Leaf.present("O2", false)));
        Rule hasntOnOptional = rule("o2_never_o1", Rule.Polarity.REQUIRE, Gate.ANY, null,
                new Rule.Assert(new Condition.Any(List.of(Condition.Leaf.value("O2", Condition.Op.HASNT, new RuleValue.Text("o2")),
                        Condition.Leaf.present("M", false)))));
        ColumnLayout layout = layout("M", "O1", "O2");
        RuleEngine e = engine(rules(bothOrNone, hasntOnOptional), layout, List.of("M", "O1", "O2"));
        assertFalse(e.mandatoryOnly(0), "it reads optional sheets, so it runs on assembled candidates");
        assertEquals(List.of(), names(e, e.violations(view(v("m1"), null, null), false)), "both absent");
        assertEquals(List.of("both_or_none"), names(e, e.violations(view(v("m1"), null, v("o1")), false)));
        assertEquals(List.of(), names(e, e.violations(view(v("m1"), v("o1"), v("o1")), false)), "out of context when O1 is present");
        assertEquals(List.of("o2_never_o1"), names(e, e.violations(view(v("m1"), v("o1"), v("o2")), false)));
        assertEquals(List.of(), names(e, e.violations(view(v("m1"), v("o1"), v()), false)),
                "a present empty row selects nothing, like absence, for every sieve operator");
    }

    @Test
    void theEarlyPhaseHoldsOnlyRulesThatCannotDependOnAnOptionalChoice() {
        Rule mandatory = rule("m", Rule.Polarity.FORBID, Gate.ANY, null,
                new Rule.Sets(Map.of("A", List.of(new RuleValue.Text("a11")), "C", List.of(new RuleValue.Text("c11")))));
        Rule guarded = rule("g", Rule.Polarity.FORBID, Gate.ANY, Condition.Leaf.present("O1", true),
                new Rule.Sets(Map.of("A", List.of(new RuleValue.Text("a11")), "C", List.of(new RuleValue.Text("c12")))));
        RuleEngine e = engine(rules(mandatory, guarded), layout("A", "C", "O1"), List.of("A", "C", "O1"));
        assertTrue(e.mandatoryOnly(0));
        assertFalse(e.mandatoryOnly(1), "a mandatory target guarded by an optional condition waits for the optional choice");
        assertTrue(e.hasMandatoryRules() && e.hasOptionalRules());
        CandidateView row = view(v("a11"), v("c12"), null);
        assertEquals(List.of(), names(e, e.violations(row, true)));
        assertEquals(List.of(), names(e, e.violations(row, false)), "without O1 the guard leaves the bond inert");
        assertEquals(List.of("g"), names(e, e.violations(view(v("a11"), v("c12"), v("o1")), false)));
    }

    @Test
    void referencesOrdersAndGatesAreCheckedBeforeAnyCandidateWithLocations() {
        Rule unknown = rule("u", Rule.Polarity.FORBID, Gate.ANY, null,
                new Rule.Sets(Map.of("A", List.of(new RuleValue.Text("a11")), "Nope", List.of(new RuleValue.Text("x")))));
        FrameworkException e = assertThrows(FrameworkException.class,
                () -> engine(rules(unknown), layout("A"), List.of("A", "Excluded")));
        assertEquals(List.of(ErrorCode.CONSTRAINT_UNKNOWN_SHEET), codes(e.diagnostics()));
        assertEquals(HERE, e.diagnostics().getFirst().location());

        Rule excluded = rule("x", Rule.Polarity.FORBID, Gate.ANY, null,
                new Rule.Sets(Map.of("A", List.of(new RuleValue.Text("a11")), "Excluded", List.of(new RuleValue.Text("x")))));
        e = assertThrows(FrameworkException.class, () -> engine(rules(excluded), layout("A"), List.of("A", "Excluded")));
        assertEquals(List.of(ErrorCode.CONSTRAINT_UNMATERIALIZED_SHEET), codes(e.diagnostics()),
                "a sheet that is no column could never fire; the legacy sieve refused it too");

        Rule ordinal = rule("o", Rule.Polarity.REQUIRE, Gate.ANY, null, new Rule.Assert(Condition.Leaf.value("A", Condition.Op.GE,
                new RuleValue.Text("a12"))));
        e = assertThrows(FrameworkException.class, () -> engine(rules(ordinal), layout("A"), List.of("A")));
        assertEquals(List.of(ErrorCode.CONSTRAINT_INVALID), codes(e.diagnostics()), "no order is declared for A");

        ConstraintSet listed = new ConstraintSet(List.of(), Map.of(), Map.of("A", OrderSpec.listed(List.of(new RuleValue.Text("a11")))),
                List.of(ordinal), List.of());
        e = assertThrows(FrameworkException.class, () -> engine(listed, layout("A"), List.of("A")));
        assertTrue(e.diagnostics().getFirst().message().contains("not in the declared order"), "the constant a12 is not in it");

        ConstraintSet both = new ConstraintSet(List.of(), Map.of(), Map.of("A", OrderSpec.listed(List.of(new RuleValue.Text("a11"),
                new RuleValue.Text("a12")))), List.of(ordinal), List.of());
        RuleEngine ok = engine(both, layout("A"), List.of("A"));
        assertEquals(List.of(), names(ok, ok.violations(view(v("a12")))));
        assertEquals(List.of("o"), names(ok, ok.violations(view(v("a11", "a12")))), "min rank of the selection is a11");
        FrameworkException runtime = assertThrows(FrameworkException.class, () -> ok.violations(view(v("c11"))));
        assertEquals(ErrorCode.ASSEMBLY_RULE_ERROR, runtime.diagnostics().getFirst().code(),
                "a selected value outside the declared order stops the run, as the legacy ValueError did");
        assertEquals(HERE, runtime.diagnostics().getFirst().location());

        Rule farApart = rule("far", Rule.Polarity.FORBID, new Gate(true, -1), null,
                new Rule.Sets(Map.of("A", List.of(new RuleValue.Text("a11")), "C", List.of(new RuleValue.Text("c11")))));
        RuleEngine gated = engine(rules(farApart), layout("A", "M", "C"), List.of("A", "M", "C"));
        assertEquals(List.of(ErrorCode.CONSTRAINT_INERT), codes(gated.findings()), "columns 0 and 2 are never adjacent");
        assertEquals(List.of(), names(gated, gated.violations(view(v("a11"), v("m1"), v("c11")))));
    }

    @Test
    void valuesNoCellHasAreReportedAsInert() {
        Rule typo = rule("t", Rule.Polarity.FORBID, Gate.ANY, null,
                new Rule.Pairs(List.of("A", "C"), List.of(Map.of("A", new RuleValue.Text("a11 "), "C", RuleValue.Int.of(11)))));
        RuleEngine e = engine(rules(typo), layout("A", "C"), List.of("A", "C"));
        assertEquals(2, e.findings().size());
        assertTrue(e.findings().stream().allMatch(d -> d.code() == ErrorCode.CONSTRAINT_INERT));
        assertEquals(List.of(), names(e, e.violations(view(v("a11"), v("c11")))), "neither 'a11 ' nor the number 11 matches a text");
    }

    @Test
    void legacyBondsKeepTheReaderFilterRule() {
        LegacyBond require = new LegacyBond(false, true, List.of("O1", "M"), List.of(List.of(label("o1"), label("m1"))), HERE);
        LegacyBond gateFalse = new LegacyBond(false, false, List.of("O2"), List.of(), HERE);
        ColumnLayout layout = layout("M", "O1", "O2");
        RuleEngine e = engine(new ConstraintSet(List.of(), Map.of(), Map.of(), List.of(), List.of(require)), layout, List.of("M", "O1", "O2"));
        assertTrue(e.violations(view(v("m1"), v("o2"), null)).cardinality() == 1, "no tuple holds: require removes");
        assertTrue(e.violations(view(v("m1"), v("o1", "o2"), null)).isEmpty(), "a tuple holds when each label is among its sheet's keys");
        assertTrue(e.violations(view(v("m1"), null, null)).isEmpty(), "absent: not applicable");
        assertTrue(e.violations(view(v("m1"), v(), null)).isEmpty(), "present but empty: not applicable either");
        RuleEngine g = engine(new ConstraintSet(List.of(), Map.of(), Map.of(), List.of(), List.of(gateFalse)), layout, List.of("M", "O1", "O2"));
        assertEquals(1, g.violations(view(v("m1"), null, v("o1"))).cardinality(), "gate 0: a require bond always removes");
        LegacyBond badLabel = new LegacyBond(true, true, List.of("M"), List.of(List.of(99)), HERE);
        FrameworkException x = assertThrows(FrameworkException.class, () -> engine(new ConstraintSet(List.of(), Map.of(), Map.of(),
                List.of(), List.of(badLabel)), layout, List.of("M")));
        assertEquals(List.of(ErrorCode.CONSTRAINT_INVALID), codes(x.diagnostics()));
    }

    @Test
    void mappingsAndCountsFollowTheLegacyDefinitions() {
        Rule map = rule("map", Rule.Polarity.REQUIRE, Gate.ANY, null, new Rule.Mapping("A", "C",
                Map.of("a11", List.of(new RuleValue.Text("c11")))));
        Rule count = rule("two", Rule.Polarity.REQUIRE, Gate.ANY, null,
                new Rule.Assert(Condition.Leaf.count("C", Condition.Op.COUNT_LE, BigInteger.ONE)));
        RuleEngine e = engine(rules(map, count), layout("A", "C"), List.of("A", "C"));
        assertEquals(List.of("map"), names(e, e.violations(view(v("a11"), v("c12")))));
        assertEquals(List.of(), names(e, e.violations(view(v("a12"), v("c12")))), "a source value the mapping does not list is free");
        assertEquals(List.of("map", "two"), names(e, e.violations(view(v("a11"), v("c11", "c12")))), "every rule is reported");
        assertEquals(List.of(), names(e, e.violations(view(v("a11"), v("c11")))));
    }

    private static List<ErrorCode> codes(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(Diagnostic::code).toList();
    }
}
