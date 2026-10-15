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

package org.ghyura.cf.fixtures.demos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Original D9 named reduction rules, checked against a separate literal world table. */
public final class RobustPortfolios {
    public static final List<String> MODULES = List.of("cache", "quota", "network_backup", "disk_replica", "staff_reserve", "cross_region");
    public static final List<String> FIELDS = List.of("fixed_cost", "gross_loss", "reductions", "raw_loss", "residual_loss", "total_cost");
    public record Cost(int fixedCost, int grossLoss, List<Integer> reductions, int rawLoss, int residualLoss, int totalCost) {
        public Cost { reductions = List.copyOf(reductions); if (reductions.size() != 6) throw new IllegalArgumentException("six reductions required"); }
        public Map<String, String> fields() {
            return Map.of("fixed_cost", Integer.toString(fixedCost), "gross_loss", Integer.toString(grossLoss),
                    "reductions", reductions.toString(), "raw_loss", Integer.toString(rawLoss),
                    "residual_loss", Integer.toString(residualLoss), "total_cost", Integer.toString(totalCost));
        }
    }
    public record Result(String design, int demand, int disruption, Cost observed, Cost reference, List<String> mismatched) {
        public Result { mismatched = List.copyOf(mismatched); }
        public boolean passed() { return mismatched.isEmpty(); }
        public String caseId() { return "P=" + design + "|D=" + demand + "|X=" + disruption; }
    }
    private RobustPortfolios() { }
    public static String design(int n) {
        if (n < 0 || n > 63) throw new IllegalArgumentException("six-bit design required");
        return String.format(java.util.Locale.ROOT, "%6s", Integer.toBinaryString(n)).replace(' ', '0');
    }
    private static void validate(String design, int d, int x) {
        if (design == null || !design.matches("[01]{6}") || d < 0 || d > 3 || x < 0 || x > 3)
            throw new IllegalArgumentException("six-bit design and original 0..3 world required");
    }
    public static List<String> selected(String design) {
        validate(design, 0, 0); var selected = new ArrayList<String>();
        for (int i = 0; i < 6; i++) if (design.charAt(i) == '1') selected.add(MODULES.get(i));
        return List.copyOf(selected);
    }
    public static Cost sut(String design, int d, int x) {
        validate(design, d, x);
        int[] fixedCosts = {3, 2, 5, 6, 4, 8};
        int[] benefit = {new int[]{0, 3, 8, 6}[d], new int[]{0, 2, 6, 7}[d],
                x == 1 ? 11 : 0, x == 2 ? 14 : 0, x == 3 ? 6 : 0, x == 1 || x == 2 ? 8 : 0};
        int fixed = 0, totalReduction = 0; List<Integer> reductions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            boolean on = design.charAt(i) == '1'; int reduction = on ? benefit[i] : 0;
            if (on) fixed += fixedCosts[i]; reductions.add(reduction); totalReduction += reduction;
        }
        int gross = new int[]{4, 11, 22, 15}[d] + new int[]{0, 14, 19, 9}[x];
        int raw = gross - totalReduction, residual = Math.max(0, raw);
        return new Cost(fixed, gross, reductions, raw, residual, fixed + residual);
    }
    /** Policy-independent oracle: literal fixed costs, gross losses and all sixteen benefit rows. */
    public static Cost reference(String design, int d, int x) {
        validate(design, d, x);
        int[] fixedTable = {3, 2, 5, 6, 4, 8};
        int[] grossTable = {4, 18, 23, 13, 11, 25, 30, 20, 22, 36, 41, 31, 15, 29, 34, 24};
        int[][] benefits = {{0,0,0,0,0,0},{0,0,11,0,0,8},{0,0,0,14,0,8},{0,0,0,0,6,0},
                {3,2,0,0,0,0},{3,2,11,0,0,8},{3,2,0,14,0,8},{3,2,0,0,6,0},
                {8,6,0,0,0,0},{8,6,11,0,0,8},{8,6,0,14,0,8},{8,6,0,0,6,0},
                {6,7,0,0,0,0},{6,7,11,0,0,8},{6,7,0,14,0,8},{6,7,0,0,6,0}};
        int fixed = 0, loss = grossTable[4*d+x]; List<Integer> reductions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int value = design.charAt(i) == '1' ? benefits[4*d+x][i] : 0;
            if (design.charAt(i) == '1') fixed += fixedTable[i]; reductions.add(value); loss -= value;
        }
        int residual = loss < 0 ? 0 : loss;
        return new Cost(fixed, grossTable[4*d+x], reductions, loss, residual, fixed + residual);
    }
    public static Result judge(String design, int d, int x, Cost observed) {
        Cost reference = reference(design, d, x); var got = observed.fields(); var expected = reference.fields();
        return new Result(design, d, x, observed, reference, FIELDS.stream().filter(f -> !got.get(f).equals(expected.get(f))).toList());
    }
    public static Result evaluate(String design, int d, int x) { return judge(design, d, x, sut(design, d, x)); }
    public static Map<String, String> attributes(Result r) {
        var a = new TreeMap<String, String>(); a.putAll(r.observed().fields());
        r.reference().fields().forEach((k,v) -> a.put("reference_" + k, v));
        a.put("schema", "d9.observation/v1"); a.put("contract", "v1"); a.put("case_id", r.caseId());
        a.put("design", r.design()); a.put("selected", selected(r.design()).toString());
        a.put("demand", Integer.toString(r.demand())); a.put("disruption", Integer.toString(r.disruption()));
        a.put("mismatched_fields", r.mismatched().toString()); a.put("verdict", r.passed() ? "PASS" : "DOMAIN_FAIL");
        a.put("fw_var", r.passed() ? "0" : "2"); a.put("carrier", "DESIGN position 2 (legacy positional, not causal)");
        return Map.copyOf(a);
    }
}
