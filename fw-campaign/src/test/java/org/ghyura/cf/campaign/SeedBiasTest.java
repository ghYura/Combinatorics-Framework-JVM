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

package org.ghyura.cf.campaign;

import org.ghyura.cf.analysis.BundleSeed;
import org.ghyura.cf.analysis.Goal;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.rule.Rule;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gate A13, the seed bias against the legacy plan. Every expected value below is the output of the legacy
 * generator_trunk/bundle/seedbias.py build_bias_plan (Combinatorics-Framework-QA-edition 0bc3e5a, file SHA-256
 * d57f5c1a6f55e194362b51a68df8c3e58648883b4a8e23204a5a1cdf0436d599), run on a scratch copy with these inputs; only
 * fwgen.estimate_core_combos was stubbed, to return the full space each case passes here.
 */
class SeedBiasTest {

    private static final SourceLocation AT = SourceLocation.of("test");

    /** Sheets as name, then its values. */
    private static ScenarioSpec scenario(Object... nameThenValues) {
        List<SheetSpec> sheets = new ArrayList<>();
        for (int i = 0; i < nameThenValues.length; i += 2) {
            List<ValueCell> cells = new ArrayList<>();
            for (String v : (String[]) nameThenValues[i + 1]) {
                cells.add(ValueCell.of(v, ValueKind.TEXT, AT));
            }
            sheets.add(new SheetSpec((String) nameThenValues[i], sheets.size() + 1, cells, AT));
        }
        return new ScenarioSpec("bias.json", sheets, new ControlSpec(List.of(), List.of(), List.of(), List.of()), false, List.of());
    }

    private static String[] values(String prefix, int n) {
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = prefix + (i + 1);
        }
        return out;
    }

    /** A winner with selections written as SHEET=value. */
    private static BundleSeed.Winner w(String role, String... selections) {
        Map<String, List<String>> s = new LinkedHashMap<>();
        for (String sel : selections) {
            s.put(sel.substring(0, sel.indexOf('=')), List.of(sel.substring(sel.indexOf('=') + 1)));
        }
        return new BundleSeed.Winner(1, Double.NaN, role, Map.of("candidate_id", "1_0_0"), Double.NaN, null, null, s);
    }

    private static BundleSeed seed(List<String> declared, BundleSeed.Winner... winners) {
        return new BundleSeed(0, "r", winners.length, declared, Map.of(), List.of(winners));
    }

    private static void sheet(SeedBias.Plan plan, String sheet, int observations, int minKeep, List<String> kept, List<String> excluded) {
        SeedBias.SheetBias b = plan.sheets().stream().filter(x -> x.sheet().equals(sheet)).findFirst().orElseThrow();
        assertEquals(observations, b.observations(), sheet + " observations");
        assertEquals(minKeep, b.minKeep(), sheet + " min_keep");
        assertEquals(kept, b.kept(), sheet + " kept");
        assertEquals(excluded, b.excluded(), sheet + " excluded");
    }

    @Test
    void adjacencyCoverageSupportAndRolesFollowTheLegacyPlan() {
        String[] p = values("p", 12);
        p[2] = " p3 ";
        ScenarioSpec base = scenario("P", p, "Q", values("q", 4), "R", values("r", 10), "S", values("s", 5), "C", values("c", 3),
                "E", new String[0]);
        BundleSeed seed = seed(List.of("lat"),
                w("pareto", "P=p2", "Q=q1", "R=r1", "C=c1"), w("pareto", "P=p3", "Q=q1", "R=r2", "C=c2"),
                w("pareto", "P=p6", "Q=q1", "R=r9", "C=c3"), w("champion-min:lat", "P=p7", "Q=q2", "R=r5"),
                w("champion-max:lat", "P=p12", "Q=q4", "R=r7"), w("champion-max:thr", "P=p11", "Q=q3", "R=r7"),
                w("balanced:knee", "P=p2", "Q=q1", "R=r1", "S=s1"));
        SeedBias.Plan plan = SeedBias.plan(base, seed, List.of(Goal.min("lat"), Goal.max("thr")), 0.25, 3, BigInteger.valueOf(7200));
        assertEquals(SeedBias.Status.BIASED, plan.status(), plan.reason());
        assertEquals(5, plan.relevant(), "pareto, balanced and the champion agreeing with its declared goal");
        sheet(plan, "P", 5, 3, List.of("p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8"), List.of("p9", "p10", "p11", "p12"));
        sheet(plan, "Q", 5, 2, List.of("q1", "q2", "q3"), List.of("q4"));
        sheet(plan, "R", 5, 3, List.of("r1", "r2", "r3", "r4", "r5", "r6", "r8", "r9", "r10"), List.of("r7"));
        sheet(plan, "S", 1, 2, List.of("s1", "s2", "s3", "s4", "s5"), List.of());
        sheet(plan, "C", 3, 2, List.of("c1", "c2", "c3"), List.of());
        sheet(plan, "E", 0, 0, List.of(), List.of());
        assertEquals("support 1 < min_winner_support 3", plan.sheets().get(3).reason());
        assertEquals("winner coverage 1.000 >= 0.800; no narrowing signal", plan.sheets().get(4).reason());
        assertEquals(0.45, plan.keepFraction());
        assertEquals(BigInteger.valueOf(3240), plan.postBias());
        assertEquals(List.of("seedbias.forbid.P.e0517ffd4c", "seedbias.forbid.P.af205137b8", "seedbias.forbid.P.86fe149f46",
                "seedbias.forbid.P.ea299ec2b3", "seedbias.forbid.Q.9a7676821a", "seedbias.forbid.R.daf1ccf042"),
                plan.rules().stream().map(Rule::id).toList());
        assertEquals("BundleSeed bias: exclude P='p9' (not observed among winners)", plan.rules().getFirst().description());

        // the next iteration's scenario: the base one plus the rules, a rule the author wrote already not added twice
        ScenarioSpec authored = base.withConstraints(new ConstraintSet(List.of(), Map.of(), Map.of(), List.of(plan.rules().get(4)), List.of()));
        ScenarioSpec next = plan.apply(authored);
        assertEquals(6, next.constraints().rules().size());
        assertEquals(1, authored.constraints().rules().size(), "the base scenario is not changed");
        assertNotEquals(authored.contentDigest(), next.contentDigest());
    }

    @Test
    void floorFillAndTheRelevanceFallbackFollowTheLegacyPlan() {
        SeedBias.Plan fill = SeedBias.plan(scenario("P", values("p", 10), "Q", values("q", 6)),
                seed(List.of(), w("pareto", "P=p10", "Q=q1"), w("pareto", "P=p10", "Q=q4")), List.of(), 0.5, 2, BigInteger.valueOf(60));
        assertEquals(SeedBias.Status.BIASED, fill.status(), fill.reason());
        sheet(fill, "P", 2, 5, List.of("p1", "p2", "p3", "p9", "p10"), List.of("p4", "p5", "p6", "p7", "p8"));
        sheet(fill, "Q", 2, 3, List.of("q1", "q2", "q3", "q4", "q5"), List.of("q6"));
        assertEquals(0.4166666666666667, fill.keepFraction());
        assertEquals(BigInteger.valueOf(25), fill.postBias());
        assertEquals(List.of("seedbias.forbid.P.1b9645e71b", "seedbias.forbid.P.4197f5d833", "seedbias.forbid.P.826f05c1f9",
                "seedbias.forbid.P.e88b9eb4a2", "seedbias.forbid.P.0cb9b51e82", "seedbias.forbid.Q.05454bef94"),
                fill.rules().stream().map(Rule::id).toList());

        // no row is relevant (a champion against its goal, one of an undeclared key): every row counts
        SeedBias.Plan fallback = SeedBias.plan(scenario("P", values("p", 8)),
                seed(List.of(), w("champion-max:lat", "P=p1"), w("champion-min:zzz", "P=p5")), List.of(Goal.min("lat")), 0.25, 2,
                BigInteger.valueOf(8));
        assertEquals(2, fallback.relevant());
        sheet(fallback, "P", 2, 2, List.of("p1", "p2", "p4", "p5", "p6"), List.of("p3", "p7", "p8"));
        assertEquals(BigInteger.valueOf(5), fallback.postBias());
    }

    @Test
    void degenerateAndExhaustedPlansFollowTheLegacyPlan() {
        BundleSeed.Winner one = w("pareto", "P=p1");
        SeedBias.Plan floor = SeedBias.plan(scenario("P", values("p", 8)), seed(List.of(), one, one, one, one, one), List.of(), 0.25, 5,
                BigInteger.valueOf(8));
        assertEquals(SeedBias.Status.CONVERGED_NO_SIGNAL, floor.status());
        assertEquals("seed bias would push every narrowed sheet to its exploration floor; refusing to prune noise", floor.reason());
        assertEquals(List.of(), floor.rules());

        SeedBias.Plan covered = SeedBias.plan(scenario("P", values("p", 5)), seed(List.of(),
                new BundleSeed.Winner(1, 0, "pareto", Map.of(), 0, null, null, Map.of("P", List.of("p1", "p2"))),
                new BundleSeed.Winner(2, 0, "pareto", Map.of(), 0, null, null, Map.of("P", List.of("p3", "p4")))), List.of(), 0.25, 1,
                BigInteger.valueOf(5));
        assertEquals(SeedBias.Status.CONVERGED_NO_SIGNAL, covered.status());
        assertEquals("seed bias produced no exclusions: every sheet lacked support, already covered >=80%, or was protected by the "
                + "exploration floor", covered.reason());

        SeedBias.Plan none = SeedBias.plan(scenario("P", values("p", 10), "Q", values("q", 6)),
                seed(List.of(), w("pareto", "P=p10", "Q=q1"), w("pareto", "P=p10", "Q=q4")), List.of(), 0.5, 2, BigInteger.ONE);
        assertEquals(SeedBias.Status.EXHAUSTED, none.status());
        assertEquals("seed bias would keep <1 candidate: full_space_estimate=1, est_keep_fraction=0.41666667", none.reason());
    }

    @Test
    void aSeedWhoseWinnersCarryNoSelectionsCannotBeJoined() {
        BundleSeed legacy = seed(List.of(), new BundleSeed.Winner(1, 0, "pareto", Map.of("candidate_id", "3_0_0"), 0, null, null, Map.of()));
        FrameworkException e = assertThrows(FrameworkException.class,
                () -> SeedBias.plan(scenario("P", values("p", 8)), legacy, List.of(), 0.25, 1, null));
        assertEquals(ErrorCode.CAMPAIGN_SEED_UNJOINABLE, e.diagnostics().getFirst().code());
    }
}
