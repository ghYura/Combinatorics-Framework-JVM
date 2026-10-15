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

package org.ghyura.cf.fixtures.tutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pure Java port of SUT/combination_thinking_tutor/tutor_sut.py (SUT checkout 8d5aa2d): the deterministic parcel
 * workflow, including its two deliberate interaction defects. Same constants, the same validation, the same rule
 * precedence and the same result fields; the HTTP surface of the Python module is not ported (a framework run calls the
 * typed plugin instead).
 */
public final class TutorSut {

    public static final List<String> CHANNELS = List.of("web", "mobile");
    public static final List<String> ACCOUNTS = List.of("guest", "member");
    public static final List<String> PAYMENTS = List.of("card", "wallet");
    public static final List<String> WORKFLOW_STEPS = List.of("reserve", "charge", "dispatch");
    public static final List<String> STRESS_ACTIONS = List.of("duplicate_submit", "carrier_timeout");

    /** tutor_sut.VERDICTS, in its order. */
    public static final Map<String, Integer> VERDICTS;

    static {
        Map<String, Integer> v = new LinkedHashMap<>();
        v.put("PASS", 0);
        v.put("INPUT_ERROR", 10);
        v.put("MODEL_INVALID", 11);
        v.put("ORDER_VIOLATION", 20);
        v.put("DOUBLE_CHARGE", 30);
        v.put("DISPATCH_LOST", 31);
        VERDICTS = java.util.Collections.unmodifiableMap(v);
    }

    /** One candidate as tutor_sut.normalize_case returns it. */
    public record Case(String channel, String account, String payment, List<String> steps, Set<String> stress) {
        public Case {
            steps = List.copyOf(steps);
            stress = java.util.Collections.unmodifiableSet(new TreeSet<>(stress));
        }
    }

    /**
     * tutor_sut.evaluate's result.
     *
     * @param interaction the interaction lines (channel=..., steps=a&gt;b&gt;c, stress=... sorted)
     * @param charges     state.charges
     * @param dispatches  state.dispatches
     */
    public record Result(String outcome, int verdictCode, boolean passed, String reason, List<String> interaction, int charges,
                         int dispatches) {
        public Result {
            interaction = List.copyOf(interaction);
        }

        /** tutor_sut's metrics: safe, faults, charges, dispatches. */
        public Map<String, Double> metrics() {
            Map<String, Double> m = new TreeMap<>();
            m.put("safe", passed ? 1.0 : 0.0);
            m.put("faults", passed ? 0.0 : 1.0);
            m.put("charges", (double) charges);
            m.put("dispatches", (double) dispatches);
            return m;
        }
    }

    private TutorSut() {
    }

    /** tutor_sut.normalize_case: validates shape, applies no domain rule. */
    public static Case normalize(String channel, String account, String payment, List<String> steps, List<String> stress) {
        String c = channel == null ? "" : channel.strip();
        String a = account == null ? "" : account.strip();
        String p = payment == null ? "" : payment.strip();
        if (steps == null) {
            // _step_tuple raises before any other check is collected
            throw new IllegalArgumentException("steps must be a list or comma-separated string");
        }
        List<String> s = new ArrayList<>();
        for (String step : steps) {
            s.add(step.strip());
        }
        Set<String> st = new TreeSet<>();
        if (stress != null) {
            for (String item : stress) {
                if (!item.strip().isEmpty()) {
                    st.add(item.strip());
                }
            }
        }
        List<String> errors = new ArrayList<>();
        if (!CHANNELS.contains(c)) {
            errors.add("channel must be one of " + String.join(", ", CHANNELS));
        }
        if (!ACCOUNTS.contains(a)) {
            errors.add("account must be one of " + String.join(", ", ACCOUNTS));
        }
        if (!PAYMENTS.contains(p)) {
            errors.add("payment must be one of " + String.join(", ", PAYMENTS));
        }
        if (s.size() != WORKFLOW_STEPS.size() || !new TreeSet<>(s).equals(new TreeSet<>(WORKFLOW_STEPS))) {
            errors.add("steps must contain reserve, charge, and dispatch exactly once");
        }
        Set<String> unknown = new TreeSet<>(st);
        unknown.removeAll(STRESS_ACTIONS);
        if (!unknown.isEmpty()) {
            errors.add("unknown stress action(s): " + String.join(", ", unknown));
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        return new Case(c, a, p, s, st);
    }

    /** tutor_sut.evaluate with its documented precedence of rules. */
    public static Result evaluate(String channel, String account, String payment, List<String> steps, List<String> stress) {
        Case kase;
        try {
            kase = normalize(channel, account, payment, steps, stress);
        } catch (IllegalArgumentException e) {
            return new Result("INPUT_ERROR", VERDICTS.get("INPUT_ERROR"), false, e.getMessage(), List.of(), 0, 0);
        }
        if (kase.payment().equals("wallet") && !kase.account().equals("member")) {
            return result(kase, "MODEL_INVALID", "wallet payment requires a member account", 0, 0);
        }
        List<String> order = kase.steps();
        if (order.indexOf("charge") < order.indexOf("reserve")) {
            return result(kase, "ORDER_VIOLATION", "charge happened before reservation", 0, 0);
        }
        if (order.indexOf("dispatch") < order.indexOf("charge")) {
            return result(kase, "ORDER_VIOLATION", "dispatch happened before charge", 0, 0);
        }
        // intentional three-way defect: each component works alone, their interaction does not
        if (kase.stress().contains("duplicate_submit") && kase.channel().equals("mobile") && kase.payment().equals("wallet")) {
            return result(kase, "DOUBLE_CHARGE", "duplicate mobile wallet submission charged twice", 2, 1);
        }
        // intentional stress interaction: a timeout loses dispatch acknowledgement on this path
        if (kase.stress().contains("carrier_timeout") && kase.channel().equals("web") && kase.payment().equals("card")) {
            return result(kase, "DISPATCH_LOST", "web card flow lost dispatch after carrier timeout", 1, 0);
        }
        return result(kase, "PASS", "workflow invariants hold", 1, 1);
    }

    private static Result result(Case kase, String outcome, String reason, int charges, int dispatches) {
        List<String> interaction = new ArrayList<>(List.of("channel=" + kase.channel(), "account=" + kase.account(),
                "payment=" + kase.payment(), "steps=" + String.join(">", kase.steps())));
        for (String s : kase.stress()) {
            interaction.add("stress=" + s);
        }
        return new Result(outcome, VERDICTS.get(outcome), outcome.equals("PASS"), reason, interaction, charges, dispatches);
    }

    /** tutor_sut.matrix_cases: the raw 192 candidates in its product order, optionally without the declared sieve. */
    public static List<List<Object>> matrixCases(boolean includeModelInvalid) {
        List<List<Object>> cases = new ArrayList<>();
        for (String channel : CHANNELS) {
            for (String account : ACCOUNTS) {
                for (String payment : PAYMENTS) {
                    for (List<String> steps : permutations(WORKFLOW_STEPS)) {
                        for (List<String> stress : stressSubsets()) {
                            if (!includeModelInvalid && payment.equals("wallet") && !account.equals("member")) {
                                continue;
                            }
                            cases.add(List.of(channel, account, payment, steps, stress));
                        }
                    }
                }
            }
        }
        return cases;
    }

    /** itertools.permutations order: lexicographic by position. */
    static List<List<String>> permutations(List<String> items) {
        List<List<String>> out = new ArrayList<>();
        permute(items, new ArrayList<>(), new boolean[items.size()], out);
        return out;
    }

    private static void permute(List<String> items, List<String> prefix, boolean[] used, List<List<String>> out) {
        if (prefix.size() == items.size()) {
            out.add(List.copyOf(prefix));
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!used[i]) {
                used[i] = true;
                prefix.add(items.get(i));
                permute(items, prefix, used, out);
                prefix.removeLast();
                used[i] = false;
            }
        }
    }

    /** tutor_sut.stress_subsets: product((False, True), repeat=2). */
    static List<List<String>> stressSubsets() {
        return List.of(List.of(), List.of("carrier_timeout"), List.of("duplicate_submit"), List.of("duplicate_submit", "carrier_timeout"));
    }

    /** tutor_sut.matrix_summary: raw and executable counts and the executable outcome distribution. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> matrixSummary() {
        List<List<Object>> raw = matrixCases(true);
        List<List<Object>> executable = matrixCases(false);
        Map<String, Integer> outcomes = new TreeMap<>();
        int rejected = 0;
        for (List<Object> c : raw) {
            Result r = evaluate((String) c.get(0), (String) c.get(1), (String) c.get(2), (List<String>) c.get(3), (List<String>) c.get(4));
            if (r.outcome().equals("MODEL_INVALID")) {
                rejected++;
            }
        }
        for (List<Object> c : executable) {
            Result r = evaluate((String) c.get(0), (String) c.get(1), (String) c.get(2), (List<String>) c.get(3), (List<String>) c.get(4));
            outcomes.merge(r.outcome(), 1, Integer::sum);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mandatory_candidates", CHANNELS.size() * ACCOUNTS.size() * PAYMENTS.size() * 6);
        summary.put("mandatory_constraint_rejected", CHANNELS.size() * 6);
        summary.put("raw_candidates", raw.size());
        summary.put("constraint_rejected", rejected);
        summary.put("executable_candidates", executable.size());
        summary.put("outcomes", outcomes);
        return summary;
    }
}
