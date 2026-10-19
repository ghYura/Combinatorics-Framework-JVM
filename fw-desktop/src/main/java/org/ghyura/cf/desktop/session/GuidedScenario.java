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

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.generation.Cardinality;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.spi.Cancellation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A first scenario composed by answering questions, as the desktop's wizard (step 0) asks them: its factors and their
 * values, how one test case uses each factor's values, rules that remove combinations which cannot or must not happen, and
 * how many of the remaining combinations to keep. Each answer becomes the FW_Seq instructions whose count is the one the
 * answer means. The plan applies a lone bracketed verb of a row twice (its auto-promotion), which multiplies a repeating,
 * ordering or subset verb, so such a verb is followed in its row by one that keeps each row as it is, or written bare.
 *
 * <p>A factor is one of three kinds ({@link Role}), which a covering selection treats apart: static parameters are what
 * pairwise (t-wise) covers; a sequence of actions runs every order with each kept combination of the parameters (it is
 * written FW_Optional with a rule that it is always present, which keeps its orders out of the selection's mandatory rows),
 * unless the design lets the selection choose orders as it chooses values; and each sudden action, FW_Optional, happens or
 * not in every combination with the others, after the selection.</p>
 *
 * <p>Before them all stands the bootstrap, FW_RunMeFirstOnce: not a factor and never combined, it runs once per run before
 * any test case, receives the FW_Arguments line and leaves FW_ARGS, the arguments every test case's main receives. It is
 * where what all test cases share is prepared, such as starting or checking the system under test.</p>
 *
 * <p>The design is written as a workbook whose test cases are small Java programs: HEAD opens the program, each static
 * parameter's sheet adds a line that sets it, each sequence's a line that does its actions in order, each sudden action's a
 * line that makes it happen (the values stay plain, the FW_SheetNames prefix and ending make them code, so rules name them
 * as written), an optional check sets the verdict, and TAIL prints the values and the verdict as the metrics line. Beside
 * it go the companion constraints file with the rules and a properties file with the selection. A design uses at most 127
 * labels (sheets plus values), so its keys fit one signed byte. Immutable: every change makes another design.</p>
 *
 * @param budget    the most mandatory rows {@link Selection#BUDGET} keeps
 * @param smaller   whether covering selection takes, each time, the row that covers most (set-cover) rather than the first
 *                  that covers something new (greedy)
 * @param check     whether the scenario has a check sheet whose verdict code FW_CUSTOM_VAR names
 * @param allOrders whether, under a covering selection, every order of each sequence of actions runs with each kept
 *                  combination of the static parameters, rather than the selection choosing orders as it chooses values
 * @param bootstrap the code that runs once per run before any test case, and the arguments it receives
 */
public record GuidedScenario(String name, List<Factor> factors, List<Rule> rules, Selection selection, long budget, boolean smaller,
                             boolean check, boolean allOrders, Bootstrap bootstrap) {

    /** The most labels (sheets plus values) a scenario uses, so that its keys fit one signed byte. */
    public static final int MAX_LABELS = Byte.MAX_VALUE;
    /** The name a new design has. */
    public static final String FIRST_NAME = "first_scenario";
    /** Combinations few enough to run every one of them. */
    public static final BigInteger FEW = BigInteger.valueOf(300);
    /** The verdict name of the check sheet's code. */
    public static final String CHECK_FAILED = "CHECK_FAILED";
    /** The generated candidates a check reads at most, counting what the rules remove. */
    static final BigInteger SCAN = BigInteger.valueOf(1_000_000);
    /** The text that joins several values of one factor in a test case. */
    static final String JOIN = "+";
    /** The wizard's own sheets, whose names a factor cannot take. */
    private static final List<String> OWN = List.of("HEAD", "check", "TAIL");
    private static final Pattern FACTOR_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,30}");
    private static final Pattern SCENARIO_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    public GuidedScenario {
        Objects.requireNonNull(name, "name");
        factors = List.copyOf(factors);
        rules = List.copyOf(rules);
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(bootstrap, "bootstrap");
    }

    /**
     * The bootstrap, FW_RunMeFirstOnce: code that runs once per run, before any test case, with the FW_Arguments line as its
     * arguments; the String FW_ARGS it leaves, split at spaces, is what every test case's main receives. It prepares what the
     * test cases share and is never one of the combinations.
     *
     * @param on        whether the scenario has one
     * @param arguments the FW_Arguments line: words separated by spaces
     */
    public record Bootstrap(boolean on, String arguments) {

        public Bootstrap {
            Objects.requireNonNull(arguments, "arguments");
        }

        /** The arguments, one word each. */
        public List<String> words() {
            return Stream.of(arguments.strip().split("\\s+")).filter(w -> !w.isEmpty()).toList();
        }

        /** The code of the FW_RunMeFirstOnce sheet: it hands its arguments on to every test case. */
        public String code(String scenario) {
            return "/* The bootstrap of " + scenario + " (FW_RunMeFirstOnce): it runs once per run, before any test case, with the\n"
                    + "   FW_Arguments line as its arguments. Prepare here what every test case shares, such as starting or checking\n"
                    + "   the system you test, then set FW_ARGS: split at spaces, it is the arguments every test case receives. */\n"
                    + "public class RunMeFirstOnce {\n"
                    + "    public static String FW_ARGS;\n\n"
                    + "    public static void main(String[] args) {\n"
                    + "        FW_ARGS = String.join(\" \", args).trim();\n"
                    + "    }\n"
                    + "}\n";
        }
    }

    /**
     * A design without factors: the name {@value #FIRST_NAME}, every combination kept (a covering, once chosen, searched with
     * set-cover, as the showcase is), a check sheet, every order of a sequence and a bootstrap without arguments.
     */
    public static GuidedScenario empty() {
        return new GuidedScenario(FIRST_NAME, List.of(), List.of(), Selection.ALL, 100, true, true, true, new Bootstrap(true, ""));
    }

    // ---- answers ------------------------------------------------------------------------------------------------------

    /** How many of a factor's values one test case uses. */
    public enum Use {
        /** Exactly one of them. */
        ONE,
        /** A fixed number of them, k. */
        EXACTLY,
        /** Any number of them from one bound to another. */
        RANGE
    }

    /** What a factor is to a test case: it decides the factor's line of code and how a covering selection treats it. */
    public enum Role {
        /** A setting with one value from the start, like the browser: what a pairwise selection covers. */
        PARAMETER("static parameter"),
        /** Actions done one after another, like the steps of a workflow: their order is part of the test case. */
        SEQUENCE("sequence of actions"),
        /** An action that may or may not happen, like a timeout: with the others in every combination, after the selection. */
        SUDDEN("sudden action");

        private final String words;

        Role(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }

        /** The words, as a choice of kinds shows them. */
        @Override
        public String toString() {
            return words;
        }
    }

    /**
     * One thing that varies between test cases, its values, and how one test case uses them.
     *
     * @param k        how many values with {@link Use#EXACTLY}
     * @param from     the fewest values with {@link Use#RANGE}
     * @param to       the most values with {@link Use#RANGE}
     * @param ordered  whether the order of a test case's values matters: a sequence rather than a group
     * @param repeats  whether one value may appear more than once (with {@link Use#EXACTLY})
     * @param optional whether a test case may also go without the factor
     */
    public record Factor(String name, List<String> values, Use use, int k, int from, int to, boolean ordered, boolean repeats,
                         boolean optional) {

        public Factor {
            Objects.requireNonNull(name, "name");
            values = List.copyOf(values);
            Objects.requireNonNull(use, "use");
        }

        /** A factor of which each test case takes exactly one value. */
        public static Factor of(String name, List<String> values) {
            return new Factor(name, values, Use.ONE, 2, 0, values.size(), false, false, false);
        }

        /** The values of a text that separates them with commas, each without the spaces around it; empty ones left out. */
        public static List<String> split(String text) {
            return Stream.of(text.split(",")).map(String::strip).filter(v -> !v.isEmpty()).toList();
        }

        /** The values as {@link #split} reads them back. */
        public String valuesText() {
            return String.join(", ", values);
        }

        public Factor named(String newName) {
            return new Factor(newName, values, use, k, from, to, ordered, repeats, optional);
        }

        /** The factor with other values; a range that reached the last value still does. */
        public Factor withValues(List<String> newValues) {
            int newTo = use == Use.RANGE && to >= values.size() ? newValues.size() : to;
            return new Factor(name, newValues, use, k, from, newTo, ordered, repeats, optional);
        }

        public Factor using(Use newUse, int newK, int newFrom, int newTo) {
            return new Factor(name, values, newUse, newK, newFrom, newTo, ordered, repeats, optional);
        }

        public Factor withOrder(boolean newOrdered) {
            return new Factor(name, values, use, k, from, to, newOrdered, repeats, optional);
        }

        public Factor withRepeats(boolean newRepeats) {
            return new Factor(name, values, use, k, from, to, ordered, newRepeats, optional);
        }

        public Factor withOptional(boolean newOptional) {
            return new Factor(name, values, use, k, from, to, ordered, repeats, newOptional);
        }

        /** Whether a test case takes exactly one value of it. */
        public boolean single() {
            return use == Use.ONE || use == Use.EXACTLY && k == 1;
        }

        /** Whether a test case may take two or more of its values; they are then joined with {@value GuidedScenario#JOIN}. */
        public boolean several() {
            return use == Use.EXACTLY && k >= 2 || use == Use.RANGE && to >= 2;
        }

        /** Whether the order of its values is part of a test case. */
        public boolean orders() {
            return ordered && several();
        }

        /** Whether a value may appear twice in one test case. */
        public boolean repeating() {
            return repeats && use == Use.EXACTLY && k >= 2;
        }

        /** Its kind, from the answers: a factor that may be left out is a sudden action, one whose order matters a sequence. */
        public Role role() {
            return optional ? Role.SUDDEN : orders() ? Role.SEQUENCE : Role.PARAMETER;
        }

        /**
         * The factor as the kind's usual answers have it: a static parameter takes one value, a sequence takes up to three of
         * its values in a row (all of them when they are fewer), a sudden action happens in one of its ways or not at all.
         */
        public Factor as(Role role) {
            int n = values.size();
            return switch (role) {
                case PARAMETER -> using(Use.ONE, k, from, to).withOrder(false).withOptional(false);
                case SEQUENCE -> using(Use.EXACTLY, Math.max(2, Math.min(3, n)), from, to).withOrder(true).withRepeats(false).withOptional(false);
                case SUDDEN -> withOptional(true);
            };
        }

        /** The cells after the sheet's name in its FW_Seq row: flags, then the instructions. */
        public List<String> instructions() {
            List<String> cells = new ArrayList<>();
            if (optional) cells.add("FW_Optional");
            if (several()) cells.add("FW_Concatenator=" + JOIN);
            int n = values.size();
            if (single()) {
                cells.add("FW_Combi(1)");
            } else if (use == Use.RANGE) {
                cells.add("FW_Subsets_RANGE(" + from + "," + to + ")");
                // bare, FW_Permut is applied once; with brackets it would be the row's lone verb and be applied twice
                if (orders()) cells.add("FW_Permut");
            } else if (repeating()) {
                cells.add((ordered ? "FW_PermutR(" : "FW_CombiR(") + k + ")");
                // the second verb keeps each row as it is; alone, the first would be applied twice
                cells.add("FW_Combi(" + k + ")");
            } else if (!ordered) {
                cells.add("FW_Combi(" + k + ")");
            } else if (k >= n) {
                cells.add("FW_Permut");
            } else {
                cells.add("FW_Combi(" + k + ")");
                cells.add("FW_Permut()");
            }
            return cells;
        }

        /** The ways a test case can use it when present: its rows. */
        public BigInteger ways() {
            int n = values.size();
            if (use == Use.RANGE) {
                BigInteger sum = BigInteger.ZERO;
                for (int i = smallest(); i <= Math.min(to, n); i++) {
                    sum = sum.add(exactly(n, i, orders(), false));
                }
                return sum;
            }
            return exactly(n, single() ? 1 : k, orders(), repeating());
        }

        /** The fewest values a group of a range has: an ordered one has at least 1, since no order of no values is made. */
        int smallest() {
            return orders() ? Math.max(1, from) : Math.max(0, from);
        }

        /** Its ways, and one more when it may be left out. */
        public BigInteger rows() {
            return optional ? ways().add(BigInteger.ONE) : ways();
        }

        /** The kind of freedom the answers describe, in words. */
        public String shape() {
            int n = values.size();
            String shape;
            if (single()) {
                shape = "exactly one value";
            } else if (use == Use.RANGE) {
                String sizes = from == to ? from + "" : from + " to " + to;
                shape = !orders() ? from == 0 && to >= n ? "any group of its values, from none to all of them (every subset)"
                        : "a group of " + sizes + " different values; the order does not matter (bounded subsets)"
                        : sizes + " different values in a row; the order matters (ordered groups)";
            } else if (repeating()) {
                shape = ordered ? "a sequence of " + k + " values in which a value may repeat; the order matters"
                        : "a group of " + k + " values in which a value may repeat; the order does not matter";
            } else if (!ordered) {
                shape = k >= n ? "all " + n + " values together, as one group" : k + " of the " + n + " values as a group; the order "
                        + "does not matter (a " + k + "-combination)";
            } else {
                shape = k >= n ? "all " + n + " values in every order (a permutation)" : k + " different values in a row; the order "
                        + "matters (a " + k + "-permutation)";
            }
            return optional ? shape + ", or left out" : shape;
        }

        /** How its rows are counted, ending with their number. */
        public String formula() {
            int n = values.size();
            String count;
            if (single()) {
                count = "one of " + n + " values = " + figure(ways());
            } else if (use == Use.RANGE) {
                if (!orders() && from == 0 && to >= n) {
                    count = "2^" + n + " = " + figure(ways());
                } else {
                    List<String> terms = new ArrayList<>();
                    for (int i = smallest(); i <= Math.min(to, n); i++) terms.add((orders() ? "P(" : "C(") + n + "," + i + ")");
                    count = (terms.size() <= 4 ? String.join(" + ", terms) : "the sum of " + (orders() ? "P(" : "C(") + n + ",i) for i = "
                            + smallest() + ".." + Math.min(to, n)) + " = " + figure(ways());
                }
            } else if (repeating()) {
                count = (ordered ? n + "^" + k : "C(" + n + "+" + k + "−1," + k + ")") + " = " + figure(ways());
            } else if (!ordered) {
                count = "C(" + n + "," + k + ") = " + figure(ways());
            } else if (k >= n) {
                count = n + "! = " + figure(ways());
            } else {
                StringBuilder product = new StringBuilder();
                for (int i = 0; i < k && k <= 6; i++) product.append(i == 0 ? "" : "·").append(n - i);
                count = "P(" + n + "," + k + ") = " + (product.isEmpty() ? "" : product + " = ") + figure(ways());
            }
            return optional ? count + " + 1 (left out) = " + figure(rows()) : count;
        }

        /** What its instructions do and why they are written so. */
        public String why() {
            List<String> cells = instructions().stream().filter(c -> !c.equals("FW_Optional") && !c.startsWith("FW_Concatenator=")).toList();
            String first = cells.getFirst();
            String text;
            if (single()) {
                text = "FW_Combi(1) takes the values one at a time: every test case gets one of them.";
            } else if (use == Use.RANGE) {
                text = first + " takes every group of " + (from == to ? from + "" : from + " to " + to) + " values, each group once"
                        + (from == 0 ? "; the empty group reads none in the test case" : "") + "."
                        + (orders() ? " FW_Permut, in the same row, then puts each group in every order: a sequence in a row. It is "
                        + "written without brackets, so it is applied once." : "")
                        + (orders() && from == 0 ? " No empty sequence is made, since there is no order of no values: to have "
                        + "test cases without this factor, let it be left out." : "");
            } else if (repeating()) {
                text = first + (ordered ? " makes every sequence of " + k + " values in which a value may repeat (a+a, a+b, b+a, ...)"
                        : " takes every group of " + k + " values in which a value may repeat (a+a, a+b, ...)") + "; FW_Combi(" + k
                        + ") after it, in the same row, keeps each one as it is. Alone in its row, " + first + " would be applied twice "
                        + "and give more, repeated rows.";
            } else if (!ordered) {
                text = first + " takes every group of " + k + " different values, each group once: a+b is the same group as b+a.";
            } else if (k >= values.size()) {
                text = "FW_Permut puts all the values in every order. It is written without brackets, so it is applied once.";
            } else {
                text = "Two instructions in one row, a sequence in a row: FW_Combi(" + k + ") chooses each group of " + k + " different "
                        + "values, then FW_Permut() puts every group in every order. FW_Permut(" + k + ") alone would not do it: it "
                        + "ignores its number and orders all " + values.size() + " values.";
            }
            if (several()) {
                text += " FW_Concatenator=" + JOIN + " joins a test case's values with " + JOIN + ".";
            }
            if (optional) {
                text += " FW_Optional makes it a sudden action: it happens in one of these ways or not at all, in every combination "
                        + "with the other sudden actions. Sudden actions join after the selection of test cases, so this one multiplies "
                        + "their number by " + figure(rows()) + ".";
            }
            return text;
        }

        /** The first ways a test case can use it, at most {@code max}, as a test case shows them. */
        public List<String> examples(int max) {
            List<int[]> picks = new ArrayList<>();
            int n = values.size();
            if (use == Use.RANGE) {
                for (int size = smallest(); size <= Math.min(to, n) && picks.size() < max; size++) {
                    fill(new int[size], 0, n, orders(), false, new boolean[n], max, picks);
                }
            } else {
                fill(new int[single() ? 1 : Math.max(0, k)], 0, n, orders(), repeating(), new boolean[n], max, picks);
            }
            List<String> shown = new ArrayList<>();
            for (int[] pick : picks) {
                List<String> parts = new ArrayList<>();
                for (int i : pick) parts.add(values.get(i));
                shown.add(parts.isEmpty() ? "none" : String.join(JOIN, parts));
            }
            if (optional && shown.size() < max) shown.add("(left out)");
            return shown;
        }

        /** The value indexes of every way, in order, until {@code max} are found. */
        private static void fill(int[] pick, int at, int n, boolean ordered, boolean repeats, boolean[] used, int max, List<int[]> out) {
            if (out.size() >= max) return;
            if (at == pick.length) {
                out.add(pick.clone());
                return;
            }
            int start = ordered || at == 0 ? 0 : repeats ? pick[at - 1] : pick[at - 1] + 1;
            for (int v = start; v < n && out.size() < max; v++) {
                if (!repeats && used[v]) continue;
                pick[at] = v;
                used[v] = true;
                fill(pick, at + 1, n, ordered, repeats, used, max, out);
                used[v] = false;
            }
        }

        /** What makes the factor unusable, each a sentence naming it; empty when it is fine. */
        public List<String> problems() {
            List<String> problems = new ArrayList<>();
            String which = name.isBlank() ? "A factor" : "Factor " + name;
            if (name.isBlank()) {
                problems.add("A factor needs a name.");
            } else if (name.regionMatches(true, 0, "FW_", 0, 3)) {
                problems.add(which + ": a name that starts with FW_ belongs to the Framework; choose another.");
            } else if (!FACTOR_NAME.matcher(name).matches()) {
                problems.add(which + ": a name starts with a letter and has at most 31 letters, digits or _ (no spaces).");
            } else if (OWN.stream().anyMatch(own -> own.equalsIgnoreCase(name))) {
                problems.add(which + ": HEAD, check and TAIL are the wizard's own sheets; choose another name.");
            }
            if (values.isEmpty()) {
                problems.add(which + " needs at least one value; separate values with commas.");
            }
            Set<String> seen = new HashSet<>();
            for (String v : values) {
                if (!seen.add(v)) problems.add(which + " has the value " + v + " twice.");
                if (v.regionMatches(true, 0, "FW_", 0, 3)) problems.add(which + ": the value " + v + " starts with FW_, which belongs to the Framework.");
                if (v.chars().anyMatch(c -> c == '"' || c == '\\' || Character.isISOControl(c))) {
                    problems.add(which + ": the value " + v + " has a quote, a backslash or a line break; the test case's code cannot hold it.");
                }
            }
            int n = values.size();
            if (use == Use.EXACTLY && k < 1) problems.add(which + ": a test case takes at least 1 value.");
            if (use == Use.EXACTLY && !repeating() && k > n && n > 0) {
                problems.add(which + " has " + n + " values, so a test case cannot take " + k + " different ones; allow repeats or take fewer.");
            }
            if (use == Use.RANGE) {
                if (from < 0 || from > to) problems.add(which + ": the fewest values (" + from + ") must be 0 or more and at most the most (" + to + ").");
                else if (to > n) problems.add(which + " has " + n + " values, so a group has at most " + n + ".");
                else if (to == 0) problems.add(which + ": a group of at most 0 values is always empty; allow 1 or more.");
            }
            return problems;
        }
    }

    /** What a rule says about two values. */
    public enum Kind {
        /** The two values never appear in one test case. */
        NEVER("never together"),
        /** Whenever the first value appears, the second one appears too. */
        NEEDS("needs");

        private final String words;

        Kind(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }

        /** The words, as a choice of kinds shows them. */
        @Override
        public String toString() {
            return words;
        }
    }

    /**
     * A rule over two values, of two factors or of one factor that takes several values. A test case has a value when its
     * factor takes it, alone or among others.
     */
    public record Rule(Kind kind, String factor, String value, String other, String otherValue) {

        public Rule {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(factor, "factor");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(other, "other");
            Objects.requireNonNull(otherValue, "otherValue");
        }

        /** The rule as a sentence. */
        public String words() {
            return kind == Kind.NEVER ? factor + " = " + value + " is never together with " + other + " = " + otherValue
                    : factor + " = " + value + " needs " + other + " = " + otherValue;
        }

        /** How the constraints file writes it. */
        public String written() {
            if (kind == Kind.NEEDS) return "require: " + factor + " has not " + value + ", or " + other + " has " + otherValue;
            return factor.equals(other) ? "require: not both " + value + " and " + otherValue + " in " + factor
                    : "forbid the pair " + factor + " = " + value + ", " + other + " = " + otherValue;
        }

        /** A name for the rule, in letters, digits and _. */
        String id() {
            String words = kind == Kind.NEVER ? "never_" + factor + "_" + value + "_with_" + other + "_" + otherValue
                    : factor + "_" + value + "_needs_" + other + "_" + otherValue;
            String id = words.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
            return id.length() > 60 ? id.substring(0, 60) : id;
        }

        /** The rule as the constraints file holds it. */
        Map<String, Object> json(String id) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("id", id);
            rule.put("desc", words());
            if (kind == Kind.NEVER && !factor.equals(other)) {
                rule.put("polarity", "forbid");
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put(factor, value);
                pair.put(other, otherValue);
                rule.put("pairs", List.of(pair));
                return rule;
            }
            rule.put("polarity", "require");
            if (kind == Kind.NEVER) {
                // two values of one factor: a pair names a factor once, so the rule says "not both"
                rule.put("assert", Map.of("not", Map.of("all", List.of(leaf(factor, "has", value), leaf(other, "has", otherValue)))));
            } else {
                rule.put("assert", Map.of("any", List.of(leaf(factor, "hasnt", value), leaf(other, "has", otherValue))));
            }
            return rule;
        }

        private static Map<String, Object> leaf(String sheet, String test, String value) {
            Map<String, Object> leaf = new LinkedHashMap<>();
            leaf.put("sheet", sheet);
            leaf.put(test, value);
            return leaf;
        }

        /** What makes the rule unusable in a design with these factors, each a sentence; empty when it is fine. */
        public List<String> problems(List<Factor> factors) {
            List<String> problems = new ArrayList<>();
            Factor a = find(factors, factor);
            Factor b = find(factors, other);
            String rule = "The rule \"" + words() + "\"";
            for (String[] side : List.of(new String[]{factor, value}, new String[]{other, otherValue})) {
                Factor f = find(factors, side[0]);
                if (f == null) problems.add(rule + " names the factor " + side[0] + ", which the scenario does not have.");
                else if (!f.values().contains(side[1])) problems.add(rule + " names the value " + side[1] + ", which " + side[0] + " does not have.");
            }
            if (!problems.isEmpty()) return problems;
            if (factor.equals(other) && value.equals(otherValue)) {
                problems.add(rule + " names one value twice; a rule relates two values.");
            } else if (factor.equals(other) && !a.several()) {
                problems.add(rule + " is about two values of " + factor + ", which takes only one value in a test case"
                        + (kind == Kind.NEVER ? ", so they are never together anyway." : ", so it would remove every test case with "
                        + value + "."));
            }
            return problems;
        }
    }

    private static Factor find(List<Factor> factors, String name) {
        return factors.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
    }

    /** Which of the combinations become test cases. */
    public enum Selection {
        /** Every combination. */
        ALL(0),
        /** Enough combinations that every pair of values of any two factors appears together at least once (pairwise). */
        PAIRS(2),
        /** Enough combinations that every triple of values of any three factors appears together at least once. */
        TRIPLES(3),
        /** The strongest such covering that keeps at most the budget's rows. */
        BUDGET(0);

        private final int strength;

        Selection(int strength) {
            this.strength = strength;
        }

        /** The covering strength t; 0 for every combination or for a budget. */
        public int strength() {
            return strength;
        }
    }

    /** What a first scenario tests: a situation, the instructions that fit it and an example design to start from. */
    public enum Situation {
        SETTINGS("Settings or a form: each setting takes one value",
                "Each factor takes exactly one of its values (FW_Combi(1)) and is crossed with every other factor, so the "
                + "combinations multiply with each factor added: a pairwise selection usually keeps them few."),
        WORKFLOW("A workflow: steps whose order matters",
                "Steps that may run in any order are a sequence of actions: FW_Combi(k) then FW_Permut() in one row takes k of "
                + "them in every order (P(n,k)), FW_Permut alone puts all of them in every order (n!). Pairwise covers the static "
                + "parameters around it, like the user and the device, and every order runs with each combination it keeps. A rule "
                + "can say that one step needs another."),
        OPTIONS("Options that can be switched on together",
                "Options on in any mix are a group of any size: FW_Subsets_RANGE(0,n) takes every subset once (2^n). Bound the "
                + "sizes when only some of them make sense."),
        REPEATS("Repeated picks: the same item may come more than once",
                "Picks in which an item may repeat are a group with repetition: FW_CombiR(k) then FW_Combi(k) in one row "
                + "(C(n+k−1,k)); when their order matters too, FW_PermutR(k) then FW_Combi(k) (n^k)."),
        EVENTS("Something that may or may not happen",
                "An event that may or may not happen is a sudden action (FW_Optional): a test case has it in one of its ways or "
                + "not at all. Make each such action a factor of its own, so they happen together in every combination; sudden "
                + "actions join after the selection of the static parameters, so each multiplies the test cases by its ways plus "
                + "one."),
        OWN("None of these: I describe my own factors",
                "Start from an empty list: add each thing that varies between test cases as a factor, with its values, then say "
                + "how one test case uses it.");

        private final String title;
        private final String advice;

        Situation(String title, String advice) {
            this.title = title;
            this.advice = advice;
        }

        public String title() {
            return title;
        }

        /** The instructions that fit the situation, and why. */
        public String advice() {
            return advice;
        }

        /** An example design of the situation under the given name, with its rules and the selection that fits it. */
        public GuidedScenario example(String name) {
            List<Factor> factors;
            List<Rule> rules;
            switch (this) {
                case SETTINGS -> {
                    factors = List.of(Factor.of("browser", List.of("chrome", "firefox", "safari", "edge")),
                            Factor.of("os", List.of("linux", "mac os", "windows")), Factor.of("language", List.of("en", "de", "uk")),
                            Factor.of("screen", List.of("phone", "tablet", "desktop")), Factor.of("account", List.of("guest", "member", "admin")));
                    rules = List.of(new Rule(Kind.NEEDS, "browser", "safari", "os", "mac os"));
                }
                case WORKFLOW -> {
                    factors = List.of(Factor.of("user", List.of("guest", "member")), Factor.of("device", List.of("phone", "tablet", "laptop")),
                            Factor.of("browser", List.of("chrome", "firefox", "safari")),
                            Factor.of("steps", List.of("login", "search", "add to cart", "pay")).as(Role.SEQUENCE),
                            Factor.of("session_expires", List.of("once")).as(Role.SUDDEN));
                    rules = List.of(new Rule(Kind.NEEDS, "steps", "pay", "steps", "add to cart"));
                }
                case OPTIONS -> {
                    factors = List.of(Factor.of("extras", List.of("gift wrap", "express", "insurance")).using(Use.RANGE, 2, 0, 3),
                            Factor.of("payment", List.of("card", "wallet", "cash")), Factor.of("country", List.of("de", "fr", "uk")));
                    rules = List.of(new Rule(Kind.NEVER, "extras", "express", "payment", "cash"));
                }
                case REPEATS -> {
                    factors = List.of(Factor.of("cart", List.of("book", "pen", "mug")).using(Use.EXACTLY, 2, 0, 3).withRepeats(true),
                            Factor.of("customer", List.of("guest", "member")), Factor.of("coupon", List.of("none", "SAVE10", "FREESHIP")));
                    rules = List.of(new Rule(Kind.NEEDS, "coupon", "SAVE10", "customer", "member"));
                }
                case EVENTS -> {
                    factors = List.of(Factor.of("payment", List.of("card", "wallet")), Factor.of("network", List.of("fast", "slow")),
                            Factor.of("timeout", List.of("short", "long")).as(Role.SUDDEN),
                            Factor.of("duplicate_submit", List.of("once")).as(Role.SUDDEN));
                    rules = List.of(new Rule(Kind.NEVER, "network", "fast", "timeout", "long"));
                }
                default -> {
                    factors = List.of();
                    rules = List.of();
                }
            }
            GuidedScenario example = new GuidedScenario(name, factors, rules, Selection.ALL, 100, true, true, true, new Bootstrap(true, ""));
            return example.selecting(example.recommended(), 100, true);
        }
    }

    // ---- changes ------------------------------------------------------------------------------------------------------

    public GuidedScenario named(String newName) {
        return new GuidedScenario(newName, factors, rules, selection, budget, smaller, check, allOrders, bootstrap);
    }

    public GuidedScenario withFactors(List<Factor> newFactors) {
        return new GuidedScenario(name, newFactors, rules, selection, budget, smaller, check, allOrders, bootstrap);
    }

    /** The design with the factor at {@code index} replaced; rules that named it follow a new name. */
    public GuidedScenario withFactor(int index, Factor factor) {
        List<Factor> changed = new ArrayList<>(factors);
        String old = changed.set(index, factor).name();
        List<Rule> followed = rules;
        if (!old.equals(factor.name())) {
            followed = rules.stream().map(r -> new Rule(r.kind(), r.factor().equals(old) ? factor.name() : r.factor(), r.value(),
                    r.other().equals(old) ? factor.name() : r.other(), r.otherValue())).toList();
        }
        return new GuidedScenario(name, changed, followed, selection, budget, smaller, check, allOrders, bootstrap);
    }

    public GuidedScenario adding(Factor factor) {
        List<Factor> changed = new ArrayList<>(factors);
        changed.add(factor);
        return withFactors(changed);
    }

    public GuidedScenario removingFactor(int index) {
        List<Factor> changed = new ArrayList<>(factors);
        changed.remove(index);
        return withFactors(changed);
    }

    /** The design with the factor at {@code index} one place up (-1) or down (+1); its sheet moves with it. */
    public GuidedScenario moving(int index, int by) {
        int to = index + by;
        if (to < 0 || to >= factors.size()) return this;
        List<Factor> changed = new ArrayList<>(factors);
        changed.add(to, changed.remove(index));
        return withFactors(changed);
    }

    public GuidedScenario withRules(List<Rule> newRules) {
        return new GuidedScenario(name, factors, newRules, selection, budget, smaller, check, allOrders, bootstrap);
    }

    public GuidedScenario adding(Rule rule) {
        List<Rule> changed = new ArrayList<>(rules);
        changed.add(rule);
        return withRules(changed);
    }

    public GuidedScenario removingRule(int index) {
        List<Rule> changed = new ArrayList<>(rules);
        changed.remove(index);
        return withRules(changed);
    }

    public GuidedScenario selecting(Selection newSelection, long newBudget, boolean newSmaller) {
        return new GuidedScenario(name, factors, rules, newSelection, newBudget, newSmaller, check, allOrders, bootstrap);
    }

    public GuidedScenario withCheck(boolean newCheck) {
        return new GuidedScenario(name, factors, rules, selection, budget, smaller, newCheck, allOrders, bootstrap);
    }

    public GuidedScenario withAllOrders(boolean newAllOrders) {
        return new GuidedScenario(name, factors, rules, selection, budget, smaller, check, newAllOrders, bootstrap);
    }

    public GuidedScenario withBootstrap(Bootstrap newBootstrap) {
        return new GuidedScenario(name, factors, rules, selection, budget, smaller, check, allOrders, newBootstrap);
    }

    // ---- counts -------------------------------------------------------------------------------------------------------

    /** Whether sequences of actions stay apart from the selection: a covering selection runs every order with each kept row. */
    public boolean apart() {
        return allOrders && selection != Selection.ALL;
    }

    /**
     * Whether the covering selection chooses among the factor's ways: a static parameter's always, a sequence's unless its
     * orders stay apart, a sudden action's never (it happens or not after the selection).
     */
    public boolean covered(Factor f) {
        return switch (f.role()) {
            case PARAMETER -> true;
            case SEQUENCE -> !apart();
            case SUDDEN -> false;
        };
    }

    /** The factors of a kind, in their order. */
    public List<Factor> of(Role role) {
        return factors.stream().filter(f -> f.role() == role).toList();
    }

    /** The combinations the selection chooses among, the Framework's mandatory rows: the product of the covered factors' ways. */
    public BigInteger mandatory() {
        return factors.stream().filter(this::covered).map(Factor::ways).reduce(BigInteger.ONE, BigInteger::multiply);
    }

    /**
     * How many test cases each kept combination becomes: every order of a sequence that stays apart, times every way of each
     * sudden action or its absence.
     */
    public BigInteger perRow() {
        return factors.stream().filter(f -> !covered(f)).map(f -> f.role() == Role.SUDDEN ? f.rows() : f.ways())
                .reduce(BigInteger.ONE, BigInteger::multiply);
    }

    /** Every combination: what a run executes without rules or a selection. */
    public BigInteger total() {
        return mandatory().multiply(perRow());
    }

    /**
     * What the Framework generates: as {@link #total}, and for a sequence that stays apart also its absent branch, which
     * FW_Optional makes and the rule that the sequence always takes place removes.
     */
    public BigInteger generated() {
        return factors.stream().filter(f -> !covered(f)).map(f -> f.ways().add(BigInteger.ONE)).reduce(mandatory(), BigInteger::multiply);
    }

    /**
     * The fewest mandatory rows a covering of strength t can keep: the ways of the t covered factors with the most ways
     * multiplied, since each of their combinations needs a row of its own (every mandatory combination when fewer vary).
     */
    public BigInteger floor(int t) {
        List<BigInteger> ways = factors.stream().filter(this::covered).map(Factor::ways).sorted(Comparator.reverseOrder()).toList();
        return ways.stream().limit(t).reduce(BigInteger.ONE, BigInteger::multiply);
    }

    /** The covered factors whose ways are more than one: those a covering selection can choose among. */
    public long varying() {
        return factors.stream().filter(f -> covered(f) && f.ways().compareTo(BigInteger.ONE) > 0).count();
    }

    /** The labels the scenario uses: its data sheets (the factors, HEAD, TAIL, the check) and their values. */
    public int labels() {
        int own = 2 + (check ? 1 : 0);
        return factors.size() + own + factors.stream().mapToInt(f -> f.values().size()).sum() + own;
    }

    /**
     * The selection that fits the design: pairwise when the static parameters (and sequences, were they chosen among) give many
     * combinations; every combination when they are few or when covering cannot make them fewer.
     */
    public Selection recommended() {
        long parameters = factors.stream().filter(f -> f.role() != Role.SUDDEN && (f.role() == Role.PARAMETER || !allOrders)
                && f.ways().compareTo(BigInteger.ONE) > 0).count();
        return total().compareTo(FEW) <= 0 || parameters < 3 ? Selection.ALL : Selection.PAIRS;
    }

    /** Why the recommended selection fits, with the numbers that decide it, kind by kind. */
    public String advice() {
        if (factors.isEmpty()) return "Add factors first: the count follows from them.";
        BigInteger total = total();
        GuidedScenario pairwise = selecting(Selection.PAIRS, budget, smaller);
        StringBuilder text = new StringBuilder();
        if (total.compareTo(FEW) <= 0) {
            text.append(figure(total)).append(" combinations are few enough to run every one of them.");
        } else if (pairwise.varying() < 3) {
            text.append(figure(total)).append(" combinations, but a covering selection cannot make them fewer: it covers the static "
                    + "parameters").append(allOrders ? "" : " and the sequences").append(", and with fewer than three of them that vary, "
                    + "every pair of their values is already a whole combination. To have fewer test cases, look again at how each "
                    + "factor varies (page 0.3) and at what cannot happen (page 0.4).");
        } else {
            BigInteger runs = pairwise.perRow();
            text.append(figure(total)).append(" combinations are many. Pairwise is for the static parameters").append(allOrders ? ""
                    : " and the sequences, whose orders count as values").append(": every value of each meets every value of every "
                    + "other in at least one test case, and most defects come from one value or two values together. It needs at least ")
                    .append(figure(pairwise.floor(2).multiply(runs))).append(" test cases: the two with the most ways have ")
                    .append(figure(pairwise.floor(2))).append(" combinations");
            List<String> ways = new ArrayList<>();
            if (pairwise.apart() && !of(Role.SEQUENCE).isEmpty()) ways.add("every order of the sequences");
            if (!of(Role.SUDDEN).isEmpty()) ways.add("the sudden actions happening or not");
            text.append(runs.equals(BigInteger.ONE) ? "." : ", and each runs " + figure(runs) + " ways: " + String.join(", and ", ways) + ".");
        }
        List<Factor> sequences = of(Role.SEQUENCE);
        if (!sequences.isEmpty()) {
            text.append(allOrders ? " Under a selection, every order of each sequence of actions runs with each combination it keeps ("
                    : " Under a selection, the orders of each sequence of actions are chosen like values (");
            text.append(String.join(", ", sequences.stream().map(f -> f.name() + ": " + figure(f.ways()) + " orders").toList())).append(").");
        }
        List<Factor> sudden = of(Role.SUDDEN);
        if (!sudden.isEmpty()) {
            text.append(" Each sudden action happens or not in every combination with the others, after the selection (")
                    .append(String.join(", ", sudden.stream().map(f -> f.name() + ": × " + figure(f.rows())).toList())).append(").");
        }
        return text.toString();
    }

    /** What the selection keeps, in words: at least, at most or exactly how many test cases. */
    public String kept() {
        BigInteger per = perRow();
        return switch (selection) {
            case ALL -> "all " + figure(total()) + " combinations";
            case PAIRS, TRIPLES -> "at least " + figure(floor(selection.strength()).multiply(per)) + " test cases, so that every "
                    + (selection == Selection.PAIRS ? "pair" : "triple") + " of values appears";
            case BUDGET -> "at most " + figure(BigInteger.valueOf(Math.max(1, budget)).min(mandatory()).multiply(per))
                    + " test cases, the strongest covering that fits";
        };
    }

    /** What makes the design unusable, each a sentence saying what to change; empty when it can be written. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (!SCENARIO_NAME.matcher(name).matches()) {
            problems.add("The scenario's name starts with a letter and has at most 64 letters, digits, _ or -.");
        }
        if (factors.isEmpty()) problems.add("Add at least one factor: a thing that varies between test cases.");
        Set<String> names = new HashSet<>();
        for (Factor f : factors) {
            problems.addAll(f.problems());
            if (!f.name().isBlank() && !names.add(f.name().toLowerCase(Locale.ROOT))) problems.add("Two factors are named " + f.name() + ".");
        }
        for (Rule r : rules) problems.addAll(r.problems(factors));
        if (labels() > MAX_LABELS) {
            problems.add("The scenario uses " + labels() + " labels (sheets plus values); at most " + MAX_LABELS + " fit one signed byte. "
                    + "Remove values or factors, or split the scenario in two.");
        }
        if (selection == Selection.BUDGET && budget < 1) problems.add("A budget keeps at least 1 test case.");
        for (String word : bootstrap.words()) {
            if (word.regionMatches(true, 0, "FW_", 0, 3)) problems.add("The bootstrap's argument " + word + " starts with FW_, which belongs to the Framework.");
        }
        return problems;
    }

    // ---- files --------------------------------------------------------------------------------------------------------

    /** The scenario's file name: its name with .json. */
    public String fileName() {
        return name + ".json";
    }

    /** The name of the test cases' Java class: the scenario's name in words joined, each capitalized. */
    public String className() {
        StringBuilder out = new StringBuilder();
        for (String part : name.split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() || !Character.isLetter(out.charAt(0)) ? "FirstScenario" : out.toString();
    }

    /** The data sheet ordinal the check's verdict code is: HEAD, every factor, then the check. */
    public int checkCode() {
        return factors.size() + 2;
    }

    /** The cells after the factor's name in its FW_Seq row, in this design: a sequence that stays apart is also FW_Optional. */
    public List<String> instructions(Factor f) {
        List<String> cells = new ArrayList<>(f.instructions());
        if (f.role() == Role.SEQUENCE && apart()) cells.addFirst("FW_Optional");
        return cells;
    }

    /** What the factor's instructions do in this design, and how the selection treats its kind. */
    public String why(Factor f) {
        String why = f.why();
        return switch (f.role()) {
            case PARAMETER -> why + " As a static parameter, it is set before any action, and a covering selection covers its values.";
            case SEQUENCE -> why + (apart() ? " As a sequence of actions, its actions are done in order, and every order runs with each "
                    + "combination the selection keeps: FW_Optional takes it out of the selection's rows, and the rule " + f.name()
                    + "_always keeps it from being left out." : selection == Selection.ALL ? " As a sequence of actions, its actions are "
                    + "done in order, one test case for each order." : " As a sequence of actions, its actions are done in order; the "
                    + "selection chooses its orders as it chooses the values of a parameter.");
            case SUDDEN -> why + " Its line stands where its sheet does, so it happens before, between or after the others' lines as "
                    + "you order the factors.";
        };
    }

    /** The line of code a factor's sheet adds, by its kind: it sets a parameter, does actions in order, or makes something happen. */
    private static String call(Factor f) {
        return switch (f.role()) {
            case PARAMETER -> "set";
            case SEQUENCE -> "act";
            case SUDDEN -> "happen";
        };
    }

    /**
     * The scenario as a workbook: the control sheets FW_Seq, FW_SheetNames, FW_CUSTOM_VAR and, with a bootstrap,
     * FW_RunMeFirstOnce and FW_Arguments (one argument a row); then the data sheets HEAD, the factors, the check and TAIL.
     */
    public ScenarioDocument document() {
        ScenarioDocument d = ScenarioDocument.create();
        d.addSheet("FW_SheetNames");
        d.addSheet("FW_CUSTOM_VAR");
        if (bootstrap.on()) {
            d.addSheet("FW_RunMeFirstOnce");
            d.setCell(3, 0, 0, bootstrap.code(name));
            d.addSheet("FW_Arguments");
            List<String> words = bootstrap.words();
            for (int i = 0; i < words.size(); i++) d.setCell(4, i, 0, words.get(i));
        }
        int[] row = {0};
        sheet(d, row, "HEAD", List.of("FW_Combi(1)"), List.of(head()), "FW_EMPTY_STRING", "FW_EMPTY_STRING");
        for (Factor f : factors) {
            // the values stay as written; the prefix and ending make each factor's block one line of code
            sheet(d, row, f.name(), instructions(f), f.values(), call(f) + "(\"" + f.name() + "\", \"", "\");");
        }
        if (check) sheet(d, row, "check", List.of("FW_Combi(1)"), List.of(checkCell()), "FW_EMPTY_STRING", "FW_EMPTY_STRING");
        sheet(d, row, "TAIL", List.of("FW_Combi(1)"), List.of(tail()), "FW_EMPTY_STRING", "FW_EMPTY_STRING");
        d.setCell(2, 0, 0, "0");
        d.setCell(2, 0, 1, "PASS");
        if (check) {
            d.setCell(2, 1, 0, Integer.toString(checkCode()));
            d.setCell(2, 1, 1, CHECK_FAILED);
        }
        return d;
    }

    /** Adds a data sheet with its values, its FW_Seq row and its FW_SheetNames row. */
    private static void sheet(ScenarioDocument d, int[] row, String sheet, List<String> instructions, List<String> values, String prefix,
                              String ending) {
        d.setCell(0, row[0], 0, sheet);
        for (int i = 0; i < instructions.size(); i++) d.setCell(0, row[0], i + 1, instructions.get(i));
        d.setCell(1, row[0], 0, sheet);
        d.setCell(1, row[0], 1, prefix);
        d.setCell(1, row[0], 2, ending);
        row[0]++;
        d.addSheet(sheet);
        int index = d.sheetCount() - 1;
        for (int i = 0; i < values.size(); i++) d.setCell(index, i, 0, values.get(i));
    }

    // a cell's white space may be collapsed into single spaces, so the code has no // comments
    private String head() {
        return "import java.util.*;\n\n"
                + "/* " + name + ", composed by the desktop's wizard (step 0). Each test case is this program with one combination of\n"
                + "   the values: each static parameter's line sets it, each sequence's line does its actions in order, each sudden\n"
                + "   action's line makes it happen" + (check ? ", the check decides the verdict," : ",") + " and the last line prints the "
                + "values and\n   the verdict for the results analysis (step 8). Several values of one factor read a" + JOIN + "b. */\n"
                + "public class " + className() + " {\n"
                + "    public static int FW_VAR = 0;\n"
                + "    /* the static parameters, set before any action */\n"
                + "    static final Map<String, String> settings = new LinkedHashMap<>();\n"
                + "    /* every action in the order it is done, a sudden one where it happens */\n"
                + "    static final List<String> actions = new ArrayList<>();\n"
                + "    /* each factor's values in this test case, for the results analysis; an empty group reads none */\n"
                + "    static final Map<String, String> chosen = new LinkedHashMap<>();\n"
                + "    static void set(String parameter, String value) { settings.put(parameter, value); chosen.put(parameter, value.isEmpty() "
                + "? \"none\" : value); }\n"
                + "    static void act(String sequence, String steps) { for (String step : steps.split(\"[" + JOIN + "]\")) if (!step.isEmpty()) "
                + "actions.add(step); chosen.put(sequence, steps.isEmpty() ? \"none\" : steps); }\n"
                + "    static void happen(String action, String how) { actions.add(action + \":\" + how); chosen.put(action, how); }\n"
                + "    /* the arguments the bootstrap (FW_RunMeFirstOnce) prepared, the same for every test case */\n"
                + "    static String[] shared = {};\n"
                + "    public static void main(String[] args) {\n"
                + "        shared = args;\n";
    }

    private String checkCell() {
        return "        /* Your check: call the system you test with the settings, do the actions in their order (a sudden one where it\n"
                + "           happens), with what the bootstrap prepared in shared, and set misbehaved when its answer is wrong. Until\n"
                + "           then every test case passes. */\n"
                + "        boolean misbehaved = false;\n"
                + "        if (misbehaved) { FW_VAR = FW_EXIT_CODE; }\n";
    }

    private String tail() {
        return "        StringBuilder line = new StringBuilder(\"app=" + name + "\");\n"
                + "        chosen.forEach((factor, values) -> line.append(' ').append(factor).append('=').append(values.replace(' ', '_')"
                + ".replace('=', '_')));\n"
                + "        System.out.println(line + \" FW_VAR=\" + FW_VAR);\n"
                + "    }\n"
                + "}\n";
    }

    /** The name of the rule that keeps a sequence which stays apart from the selection from being left out. */
    static String always(Factor f) {
        return f.name() + "_always";
    }

    /**
     * The companion constraints file: every rule, named after what it says, and for each sequence that stays apart from the
     * selection the rule that it always takes place.
     */
    public String constraintsJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        List<Object> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Factor f : factors) {
            if (f.role() == Role.SEQUENCE && apart()) {
                ids.add(always(f));
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("id", always(f));
                rule.put("desc", f.name() + " always takes place: FW_Optional only keeps its orders apart from the selection");
                rule.put("polarity", "require");
                Map<String, Object> present = new LinkedHashMap<>();
                present.put("sheet", f.name());
                present.put("present", true);
                rule.put("assert", present);
                list.add(rule);
            }
        }
        for (Rule r : rules) {
            String id = r.id();
            for (int i = 2; !ids.add(id); i++) id = r.id() + "_" + i;
            list.add(r.json(id));
        }
        root.put("constraints", list);
        return Json.write(root);
    }

    /** The selection as configuration settings: covering strength, budget and method. */
    public Map<String, String> settings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("cf.coverage.strength", Integer.toString(selection.strength()));
        settings.put("cf.coverage.budget", selection == Selection.BUDGET ? Long.toString(budget) : "0");
        settings.put("cf.coverage.method", smaller ? "set-cover" : "greedy");
        return settings;
    }

    /** The selection in words, as step 3 and the properties file say it. */
    public String selectionWords() {
        return switch (selection) {
            case ALL -> "every combination";
            case PAIRS -> "every pair of values (pairwise, t = 2)";
            case TRIPLES -> "every triple of values (t = 3)";
            case BUDGET -> "the strongest covering within " + budget + " rows";
        } + (smaller && selection != Selection.ALL ? ", searched harder for fewer rows (set-cover)" : "");
    }

    /** The settings file beside the scenario: its run name, the selection, and keys that fit one signed byte. */
    public String properties() {
        StringBuilder text = new StringBuilder();
        text.append("# Settings of ").append(fileName()).append(", written by the desktop's wizard (step 0). ./cf.sh applies a scenario's\n")
                .append("# companion <stem>.properties; the command line reads it with --config ").append(name).append(".properties.\n")
                .append("cf.run.id=").append(name).append('\n')
                .append("# Which combinations become test cases: ").append(selectionWords())
                .append(selection == Selection.ALL ? "" : " of the static parameters").append(".\n");
        settings().forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        text.append("# The scenario uses ").append(labels()).append(" of ").append(MAX_LABELS).append(" labels, so its keys fit one signed byte; ")
                .append("growing beyond fails loudly.\n")
                .append("cf.keys.encoding=SIGNED_BYTE\n");
        return text.toString();
    }

    /** The files a save wrote; {@code constraints} is null when there were no rules and no companion to empty. */
    public record Saved(Path scenario, Path constraints, Path properties) {
    }

    /**
     * Writes the scenario, its companion constraints file (when it has rules, or empties one already there so its old rules
     * stop applying) and its settings beside it, each file replaced only once complete.
     *
     * @throws FrameworkException USAGE_INVALID_VALUE naming the problems when the design cannot be written yet,
     *                            INPUT_UNREADABLE when a file cannot be written
     */
    public Saved save(Path scenario) {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, "the scenario cannot be written yet: "
                    + String.join(" ", problems), SourceLocation.of(scenario.toString())));
        }
        Path file = scenario.toAbsolutePath();
        document().save(file);
        Path constraints = ConstraintSidecarReader.companionOf(file);
        boolean companion = !rules.isEmpty() || factors.stream().anyMatch(f -> f.role() == Role.SEQUENCE && apart()) || Files.exists(constraints);
        if (companion) write(constraints, constraintsJson());
        String stem = file.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        Path properties = file.resolveSibling(stem + ".properties");
        write(properties, properties());
        return new Saved(file, companion ? constraints : null, properties);
    }

    private static void write(Path file, String text) {
        try {
            AtomicFiles.write(file, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, "the file cannot be written: " + e.getMessage(),
                    SourceLocation.of(file.toString())));
        }
    }

    // ---- the Framework's own count ------------------------------------------------------------------------------------

    /**
     * The Framework's counts of the design, as step 5 shows them for the saved scenario.
     *
     * @param generated  every combination the plan generates
     * @param executable the test cases a run executes, after the rules and the selection
     * @param selected   what the covering selection kept, in the plan's words, or null without a selection
     * @param rules      each rule with the combinations it removes
     * @param first      the first test case's code, or empty when none is left
     */
    public record Checked(BigInteger generated, BigInteger executable, String selected, List<String> rules, String first) {

        public Checked {
            rules = List.copyOf(rules);
        }

        /** The counts in words. */
        public String text() {
            StringBuilder text = new StringBuilder();
            text.append("The Framework generates ").append(figure(generated)).append(" combinations; ")
                    .append(figure(executable)).append(" of them are test cases a run executes.\n");
            for (String r : rules) text.append("  rule ").append(r).append('\n');
            if (selected != null) text.append("  selection: ").append(selected).append('\n');
            text.append(first.isEmpty() ? "No combination is left to run.\n" : "The first test case's code:\n" + first + "\n");
            return text.toString();
        }
    }

    /**
     * Writes the design to a folder of its own, has the session plan and count it with the design's selection and keys that
     * fit one signed byte, as a run would, then deletes the folder. Nothing runs.
     *
     * @throws FrameworkException with the Framework's findings when the design cannot be planned
     */
    public Checked check(Session session, Cancellation cancellation) {
        Path folder;
        try {
            folder = Files.createTempDirectory("cf-wizard-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            Path file = folder.resolve(fileName());
            save(file);
            List<ConfigResolver.Override> overrides = new ArrayList<>();
            settings().forEach((key, value) -> overrides.add(new ConfigResolver.Override(key, value, "the wizard (step 0)")));
            overrides.add(new ConfigResolver.Override("cf.keys.encoding", "SIGNED_BYTE", "the wizard (step 0)"));
            if (!session.configuration().config().constraintsFile().isEmpty()) {
                // a configured constraints file would stand for the companion that holds the design's rules
                overrides.add(new ConfigResolver.Override("cf.constraints.file", "", "the wizard (step 0)"));
            }
            Session checking = Session.of(ConfigResolver.derive(session.configuration(), overrides));
            CandidatePreview preview = checking.preview(file, new CandidatePreview.Request(SCAN, BigInteger.ZERO, 1, true, true), cancellation);
            Set<String> always = new HashSet<>(of(Role.SEQUENCE).stream().map(GuidedScenario::always).toList());
            List<String> removed = preview.rules().stream().map(r -> r.name() + " removes " + (r.removed() == null ? "an uncounted number"
                    : figure(r.removed())) + (always.contains(r.name()) ? ": the test cases without the sequence, which FW_Optional makes "
                    + "while it keeps the orders apart from the selection" : "")).toList();
            String selected = stage(preview, "selected rows") == null ? null : stage(preview, "selected rows").describe();
            String first = preview.page().rows().isEmpty() ? "" : preview.page().rows().getFirst().source();
            return new Checked(stage(preview, "generated").value(), stage(preview, "executable").value(), selected, removed, first);
        } finally {
            delete(folder);
        }
    }

    private static Cardinality stage(CandidatePreview preview, String name) {
        return preview.estimates().candidates().stream().filter(s -> s.name().equals(name)).findFirst().map(PlanEstimates.Stage::count)
                .orElse(null);
    }

    private static void delete(Path folder) {
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // a temporary folder left behind is removed with the others of its kind
        }
    }

    // ---- shared helpers -----------------------------------------------------------------------------------------------

    /** C(n,k), P(n,k), C(n+k-1,k) or n^k: the ways to take k of n values. */
    static BigInteger exactly(int n, int k, boolean ordered, boolean repeats) {
        if (k < 0 || !repeats && k > n) return BigInteger.ZERO;
        BigInteger ways = BigInteger.ONE;
        if (ordered && repeats) return BigInteger.valueOf(n).pow(k);
        int top = repeats ? n + k - 1 : n;
        for (int i = 0; i < k; i++) ways = ways.multiply(BigInteger.valueOf(top - i));
        if (ordered) return ways;
        for (int i = 2; i <= k; i++) ways = ways.divide(BigInteger.valueOf(i));
        return ways;
    }

    /** A count with its thousands grouped: 1,728. */
    public static String figure(BigInteger count) {
        return String.format(Locale.ROOT, "%,d", count);
    }
}
