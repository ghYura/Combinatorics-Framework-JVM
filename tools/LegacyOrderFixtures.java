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

// Not part of the build. Writes the cases of fw-algebra/src/test/resources/org/ghyura/cf/algebra/legacy-orders.xml by
// running the two legacy generator libraries:
//   java -cp ~/.m2/repository/com/github/dpaukov/combinatoricslib3/3.3.3/combinatoricslib3-3.3.3.jar:\
//            ~/.m2/repository/com/github/ghYura/combinatoricslib3parallel/3.4.2-SNAPSHOT/combinatoricslib3parallel-3.4.2-SNAPSHOT.jar \
//        tools/LegacyOrderFixtures.java > cases.xml
import java.util.*;
import java.util.function.*;
import org.paukov.combinatorics3.Generator;

/** Prints the order of every legacy generator on small inputs as XML cases (index tuples or class letters). */
public class LegacyOrderFixtures {
    static StringBuilder out = new StringBuilder();
    static List<String> elements(int n) { List<String> l = new ArrayList<>(); for (int i = 0; i < n; i++) l.add("e" + i); return l; }
    static String rows(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> r : rows) {
            if (sb.length() > 0) sb.append(';');
            StringBuilder t = new StringBuilder();
            for (String e : r) { if (t.length() > 0) t.append(' '); t.append(e.startsWith("e") ? e.substring(1) : e); }
            sb.append(t);
        }
        return sb.toString();
    }
    static void emit(String op, String lib, String params, Supplier<List<List<String>>> rows) {
        String body;
        String outcome;
        try { List<List<String>> r = rows.get(); body = rows(r); outcome = "rows=\"" + r.size() + "\""; }
        catch (RuntimeException e) { body = ""; outcome = "throws=\"" + e.getClass().getSimpleName() + "\""; }
        out.append("  <case op=\"").append(op).append("\" library=\"").append(lib).append("\" ").append(params).append(' ').append(outcome).append('>')
           .append(body).append("</case>\n");
    }
    static List<String> letters(String s) { List<String> l = new ArrayList<>(); for (char c : s.toCharArray()) l.add(String.valueOf(c)); return l; }
    public static void main(String[] args) {
        for (int n = 0; n <= 6; n++) for (int k = 0; k <= n + 1; k++) {
            final int fn = n, fk = k;
            emit("combinations", "combinatoricslib3", "n=\"" + n + "\" k=\"" + k + "\"", () -> Generator.combination(elements(fn)).simple(fk).stream().toList());
            emit("combinations", "parallel", "n=\"" + n + "\" k=\"" + k + "\"", () -> new org.ghYura.combinatorics3parallel.CombinationGenerator<>(elements(fn), fk).stream().toList());
        }
        for (int n = 0; n <= 5; n++) for (int k = 0; k <= 4; k++) {
            final int fn = n, fk = k;
            emit("multi-combinations", "combinatoricslib3", "n=\"" + n + "\" k=\"" + k + "\"", () -> Generator.combination(elements(fn)).multi(fk).stream().toList());
            emit("multi-combinations", "parallel", "n=\"" + n + "\" k=\"" + k + "\"", () -> new org.ghYura.combinatorics3parallel.MultiCombinationGenerator<>(elements(fn), fk).stream().toList());
        }
        for (int n = 0; n <= 6; n++) {
            final int fn = n;
            emit("permutations", "combinatoricslib3", "n=\"" + n + "\"", () -> Generator.permutation(elements(fn)).simple().stream().toList());
            emit("permutations", "parallel", "n=\"" + n + "\"", () -> new org.ghYura.combinatorics3parallel.PermutationGenerator<>(elements(fn)).stream().toList());
        }
        for (String m : List.of("aab", "baac", "aaa", "abab", "aabbc", "cbaab", "a", "")) {
            emit("multiset-permutations", "combinatoricslib3", "elements=\"" + m + "\"", () -> Generator.permutation(letters(m)).simple().stream().toList());
            emit("multiset-permutations", "parallel", "elements=\"" + m + "\"", () -> new org.ghYura.combinatorics3parallel.MultiPermutationGenerator<>(letters(m)).stream().toList());
        }
        for (int n = 0; n <= 4; n++) for (int k = 0; k <= 3; k++) {
            final int fn = n, fk = k;
            emit("repetition-permutations", "combinatoricslib3", "n=\"" + n + "\" k=\"" + k + "\"", () -> Generator.permutation(elements(fn)).withRepetitions(fk).stream().toList());
            emit("repetition-permutations", "parallel", "n=\"" + n + "\" k=\"" + k + "\"", () -> new org.ghYura.combinatorics3parallel.PermutationWithRepetitionGenerator<>(elements(fn), fk).stream().toList());
        }
        for (int n = 0; n <= 6; n++) {
            final int fn = n;
            emit("subsets", "combinatoricslib3", "n=\"" + n + "\"", () -> Generator.subset(elements(fn)).simple().stream().toList());
            emit("subsets", "parallel", "n=\"" + n + "\"", () -> new org.ghYura.combinatorics3parallel.SubSetGenerator<>(elements(fn)).stream().toList());
        }
        int[][] shapes = {{0}, {3}, {0, 2}, {2, 0}, {1, 1}, {2, 3}, {3, 2}, {2, 1, 2}, {3, 1, 2}};
        for (int[] shape : shapes) {
            List<List<String>> lists = new ArrayList<>();
            for (int size : shape) lists.add(elements(size));
            String sizes = Arrays.toString(shape).replaceAll("[\\[\\] ]", "");
            emit("cartesian", "combinatoricslib3", "sizes=\"" + sizes + "\"", () -> {
                @SuppressWarnings("unchecked") List<String>[] arr = lists.toArray(new List[0]);
                return Generator.cartesianProduct(arr).stream().toList();
            });
            emit("cartesian", "parallel", "sizes=\"" + sizes + "\"", () -> new org.ghYura.combinatorics3parallel.CartesianProductGenerator<>(lists).stream().toList());
        }
        System.out.print(out);
    }
}
