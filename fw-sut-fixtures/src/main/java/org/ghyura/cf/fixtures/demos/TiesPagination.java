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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D8a's static four-record listing, its three page policies and policy-blind obligations. */
public final class TiesPagination {
    private static final List<String> IDS = List.of("A", "B", "C", "D");
    public record Page(int request, List<Integer> cursorIn, Integer offset, List<String> fullOrder,
                       List<List<Integer>> comparisons, List<String> ids, List<Integer> cursorOut) {
        public Page {
            cursorIn = cursorIn == null ? null : List.copyOf(cursorIn);
            cursorOut = cursorOut == null ? null : List.copyOf(cursorOut);
            fullOrder = List.copyOf(fullOrder); ids = List.copyOf(ids);
            comparisons = comparisons.stream().map(List::copyOf).toList();
        }
    }
    public record Result(List<Page> pages, List<String> collected, List<String> expected,
                         Map<String, Boolean> obligations, List<Map<String, Boolean>> comparatorLaws, boolean passed) {
        public Result {
            pages = List.copyOf(pages); collected = List.copyOf(collected); expected = List.copyOf(expected);
            obligations = frozen(obligations); comparatorLaws = comparatorLaws.stream().map(TiesPagination::frozen).toList();
        }
    }
    private TiesPagination() { }
    private static Map<String, Boolean> frozen(Map<String, Boolean> m) { return Collections.unmodifiableMap(new TreeMap<>(m)); }
    private static int[] ranks(String code) {
        if (code == null || !code.matches("[0-3]{4}")) throw new IllegalArgumentException("four canonical D8 ranks required");
        int[] r = code.chars().map(c -> c - '0').toArray(); int seen = 0, max = 0;
        for (int n : r) { seen |= 1 << n; max = Math.max(max, n); }
        if (seen != (1 << (max + 1)) - 1) throw new IllegalArgumentException("ranks must use exactly 0..max");
        return r;
    }
    private static int sign(String direction) {
        return switch (direction) { case "asc" -> 1; case "desc" -> -1; default -> throw new IllegalArgumentException("unknown D8 direction"); };
    }
    private static List<Integer> key(String policy, int[] r, int s, int request, int i) {
        if (policy.equals("score_only_cursor")) return List.of(s * r[i]);
        return List.of(s * r[i], policy.equals("alternating_ties") && request % 2 == 1 ? -i : i);
    }
    private static int compare(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < a.size(); i++) { int c = Integer.compare(a.get(i), b.get(i)); if (c != 0) return c; }
        return 0;
    }
    private static Page page(String policy, int[] r, int s, int request, List<Integer> cursor) {
        Comparator<Integer> cmp = (i, j) -> compare(key(policy, r, s, request, i), key(policy, r, s, request, j));
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3)); order.sort(cmp);
        List<List<Integer>> matrix = new ArrayList<>();
        for (int i = 0; i < 4; i++) { List<Integer> row = new ArrayList<>(); for (int j = 0; j < 4; j++) row.add(cmp.compare(i, j)); matrix.add(row); }
        List<Integer> chosen; Integer offset = null; List<Integer> outgoing = null;
        if (policy.equals("alternating_ties")) { offset = 2 * request; chosen = order.subList(Math.min(offset, 4), Math.min(offset + 2, 4)); }
        else {
            chosen = order.stream().filter(i -> cursor == null || compare(key(policy, r, s, request, i), cursor) > 0).limit(2).toList();
            if (!chosen.isEmpty()) outgoing = key(policy, r, s, request, chosen.getLast());
        }
        return new Page(request, cursor, offset, order.stream().map(IDS::get).toList(), matrix, chosen.stream().map(IDS::get).toList(), outgoing);
    }
    public static Result evaluate(String policy, String code, String direction) {
        if (!List.of("stable_cursor", "score_only_cursor", "alternating_ties").contains(policy)) throw new IllegalArgumentException("unknown D8 policy");
        int[] r = ranks(code); int s = sign(direction); List<Page> pages = new ArrayList<>(); List<String> collected = new ArrayList<>();
        List<Integer> cursor = null;
        for (int request = 0; request < 3; request++) {
            Page p = page(policy, r, s, request, cursor); pages.add(p); collected.addAll(p.ids());
            if (p.ids().isEmpty()) break;
            cursor = p.cursorOut();
        }
        var expected = expectedOrder(code, direction);
        var obligations = obligations(code, direction, collected, pages.getLast().ids().isEmpty());
        var laws = pages.stream().map(p -> comparatorLaws(p.comparisons(), code, direction)).toList();
        boolean passed = collected.equals(expected) && obligations.values().stream().allMatch(Boolean::booleanValue)
                && laws.stream().allMatch(m -> m.values().stream().allMatch(Boolean::booleanValue));
        return new Result(pages, collected, expected, obligations, laws, passed);
    }
    /** No policy or cursor enters the independent contract's expected listing. */
    public static List<String> expectedOrder(String code, String direction) {
        int[] r = ranks(code); int s = sign(direction); List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        order.sort(Comparator.<Integer>comparingInt(i -> s * r[i]).thenComparingInt(i -> i));
        return order.stream().map(IDS::get).toList();
    }
    public static Map<String, Boolean> obligations(String code, String direction, List<String> collected, boolean lastPageEmpty) {
        int[] r = ranks(code); int s = sign(direction); boolean primary = true, stable = true;
        if (collected.stream().anyMatch(id -> !IDS.contains(id))) throw new IllegalArgumentException("unknown D8 record identity");
        for (int n = 1; n < collected.size(); n++) {
            int a = IDS.indexOf(collected.get(n - 1)), b = IDS.indexOf(collected.get(n));
            primary &= s * r[a] <= s * r[b]; if (r[a] == r[b]) stable &= a <= b;
        }
        return frozen(Map.of("multiset_ok", collected.stream().sorted().toList().equals(IDS), "primary_order_ok", primary,
                "stable_ties_ok", stable, "terminated", lastPageEmpty));
    }
    public static Map<String, Boolean> comparatorLaws(List<List<Integer>> matrix, String code, String direction) {
        int[] r = ranks(code); int s = sign(direction);
        if (matrix.size() != 4 || matrix.stream().anyMatch(row -> row.size() != 4)) throw new IllegalArgumentException("4x4 comparison matrix required");
        boolean signs = true, reflexive = true, antisymmetric = true, transitive = true, primary = true;
        for (int i = 0; i < 4; i++) {
            reflexive &= matrix.get(i).get(i) == 0;
            for (int j = 0; j < 4; j++) {
                int ij = matrix.get(i).get(j); signs &= ij >= -1 && ij <= 1; antisymmetric &= ij == -matrix.get(j).get(i);
                if (r[i] != r[j]) primary &= ij == Integer.signum(s * (r[i] - r[j]));
                for (int k = 0; k < 4; k++) if (ij <= 0 && matrix.get(j).get(k) <= 0) transitive &= matrix.get(i).get(k) <= 0;
            }
        }
        return frozen(Map.of("signs_ok", signs, "reflexive", reflexive, "antisymmetric", antisymmetric, "transitive_le", transitive, "primary_direction", primary));
    }
    /** Supplemental proof: reverse each tie block, keeping primary order; these are not extra run cases. */
    public static List<String> reversedTieOrder(String code, String direction) {
        int[] r = ranks(code); List<String> alternative = new ArrayList<>(expectedOrder(code, direction));
        for (int start = 0; start < 4;) {
            int end = start + 1;
            while (end < 4 && r[IDS.indexOf(alternative.get(end))] == r[IDS.indexOf(alternative.get(start))]) end++;
            Collections.reverse(alternative.subList(start, end)); start = end;
        }
        return List.copyOf(alternative);
    }
}
