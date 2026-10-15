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

import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Full frozen D7 catalogue through the original scenario. Python source cells are data, never executed. */
public final class ReductionTreePlugin implements SutPlugin {
    public static final String ID = "demo-d7-reduction-trees";
    private record Vector(List<Double> values, BigDecimal binaryBudget, BigDecimal fsumBudget) { }
    private static BigDecimal fraction(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator));
    }
    private static final Map<String, Vector> VECTORS = Map.of(
            "small_integers", new Vector(List.of(1.0, 2.0, 3.0, 4.0, 5.0), BigDecimal.ZERO, fraction(1, 1L << 50)),
            "cancellation", new Vector(List.of(0x1p53, 1.0, -0x1p53, 1.0, 1.0), fraction(1, 4), fraction(1, 1L << 52)),
            "swamped", new Vector(List.of(0x1p54, 1.0, 1.0, 1.0, -0x1p54), BigDecimal.ONE, fraction(1, 1L << 52)),
            "decimal_inputs", new Vector(List.of(0.1, 0.2, 0.3, 0.4, 0.5), fraction(1, 1L << 52), fraction(1, 1L << 53)));
    private static final Map<String, String> VECTOR_CELLS = Map.of(
            "small_integers", "vector('small_integers', ('0x1.0000000000000p+0', '0x1.0000000000000p+1', '0x1.8000000000000p+1', '0x1.0000000000000p+2', '0x1.4000000000000p+2'), {'tree_binary64': ('0', '1'), 'flat_fsum': ('1', '1125899906842624'), 'tree_rational': ('0', '1')})\n",
            "cancellation", "vector('cancellation', ('0x1.0000000000000p+53', '0x1.0000000000000p+0', '-0x1.0000000000000p+53', '0x1.0000000000000p+0', '0x1.0000000000000p+0'), {'tree_binary64': ('1', '4'), 'flat_fsum': ('1', '4503599627370496'), 'tree_rational': ('0', '1')})\n",
            "swamped", "vector('swamped', ('0x1.0000000000000p+54', '0x1.0000000000000p+0', '0x1.0000000000000p+0', '0x1.0000000000000p+0', '-0x1.0000000000000p+54'), {'tree_binary64': ('1', '1'), 'flat_fsum': ('1', '4503599627370496'), 'tree_rational': ('0', '1')})\n",
            "decimal_inputs", "vector('decimal_inputs', ('0x1.999999999999ap-4', '0x1.999999999999ap-3', '0x1.3333333333333p-2', '0x1.999999999999ap-2', '0x1.0000000000000p-1'), {'tree_binary64': ('1', '4503599627370496'), 'flat_fsum': ('1', '9007199254740992'), 'tree_rational': ('0', '1')})\n");
    @Override public String id() { return ID; }
    @Override public String version() { return "07-d7@0bc3e5a/java-1"; }
    @Override public Map<Integer, String> verdictNames() { return Map.of(0, "PASS", 2, "DOMAIN_FAIL"); }
    private static String one(SutCandidate candidate, String sheet) {
        var selection = candidate.selection(sheet).orElseThrow(() -> new IllegalArgumentException("missing " + sheet));
        if (!selection.inCandidate() || selection.values().size() != 1) throw new IllegalArgumentException("one " + sheet + " value required");
        return selection.values().getFirst();
    }
    private static String argument(String name, String text) {
        String prefix = name + "(\"", suffix = "\")\n";
        if (!text.startsWith(prefix) || !text.endsWith(suffix)) throw new IllegalArgumentException("malformed D7 catalogue entry");
        return text.substring(prefix.length(), text.length() - suffix.length());
    }
    @Override public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) {
                ctx.cancellation().throwIfCancelled();
                try (var memory = ctx.reserve(262144, "D7 exact arithmetic and trace")) {
                    if (memory.bytes() != 262144) throw new IllegalStateException("invalid arithmetic reservation");
                    one(candidate, "HEAD"); one(candidate, "TAIL");
                    String policy = argument("impl", one(candidate, "IMPL"));
                    String written = one(candidate, "VECTOR");
                    if (!written.startsWith("vector('") || written.length() > 4096) throw new IllegalArgumentException("malformed vector entry");
                    int end = written.indexOf("'", 8);
                    if (end < 0) throw new IllegalArgumentException("malformed vector entry");
                    String vectorId = written.substring(8, end);
                    Vector vector = VECTORS.get(vectorId);
                    if (vector == null || !written.equals(VECTOR_CELLS.get(vectorId))) throw new IllegalArgumentException("unknown or changed D7 vector catalogue entry");
                    String tree = argument("tree", one(candidate, "TREE"));
                    BigDecimal budget = switch (policy) {
                        case "tree_binary64" -> vector.binaryBudget;
                        case "flat_fsum" -> vector.fsumBudget;
                        case "tree_rational" -> BigDecimal.ZERO;
                        default -> throw new IllegalArgumentException("unknown D7 policy");
                    };
                    var result = ReductionTrees.evaluate(policy, tree, vector.values, budget);
                    Map<String, Double> metrics = result.finite() ? Map.of("absolute_error", result.error().doubleValue(),
                            "result", result.value().doubleValue(), "node_count", (double) result.nodes().size()) : Map.of("node_count", (double) result.nodes().size());
                    return new DomainObservation(result.passed() ? 0 : 2, metrics, Map.of(
                            "case", policy + "|V=" + vectorId + "|T=" + tree,
                            "policy", policy, "vector", vectorId, "tree", tree, "finite", Boolean.toString(result.finite()),
                            "exact_result", result.finite() ? result.value().toPlainString() : "nonfinite",
                            "reference", result.reference().toPlainString(), "budget", budget.toPlainString(),
                            "nodes", result.nodes().toString()));
                }
            }
            @Override public void close() { }
        };
    }
}
