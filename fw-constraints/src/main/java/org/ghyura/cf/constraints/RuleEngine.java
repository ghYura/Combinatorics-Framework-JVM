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
import org.ghyura.cf.model.rule.LegacyBond;
import org.ghyura.cf.model.rule.OrderSpec;
import org.ghyura.cf.model.rule.Rule;
import org.ghyura.cf.model.rule.RuleValue;

import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * The rules of a constraint layer compiled against the candidates' columns, evaluated with the legacy sieve's
 * {@code row_violations} semantics (legacy bonds files with the Reader filter's). One engine serves both phases: a
 * rule that reads only mandatory columns cannot depend on an optional choice, so it may run once per mandatory row
 * before optional sheets are combined; every other rule runs on the assembled candidate, absence included.
 *
 * <p>Immutable and safe to share between threads; evaluation allocates only per call.</p>
 */
public final class RuleEngine {

    /** The rules of no constraint layer. */
    public static final RuleEngine NONE = new RuleEngine(new ColumnLayout(List.of()), List.of(), List.of(), label -> "");

    private final ColumnLayout layout;
    private final List<Compiled> rules;
    private final List<Diagnostic> findings;
    private final IntFunction<String> texts;

    private RuleEngine(ColumnLayout layout, List<Compiled> rules, List<Diagnostic> findings, IntFunction<String> texts) {
        this.layout = layout;
        this.rules = List.copyOf(rules);
        this.findings = List.copyOf(findings);
        this.texts = texts;
    }

    /**
     * Compiles a constraint layer.
     *
     * @param set            the scenario's constraint layer
     * @param layout         the candidates' columns
     * @param scenarioSheets every sheet name of the scenario and the plan (data and virtual), to tell an unknown sheet
     *                       from one that is no column
     * @param texts          a label's value text with surrounding white space stripped (legacy code2val)
     * @param valueTexts     every value text of the scenario, stripped: values a rule names outside it can never match
     * @throws FrameworkException CONSTRAINT_UNKNOWN_SHEET, CONSTRAINT_UNMATERIALIZED_SHEET or CONSTRAINT_INVALID with
     *                            every problem of the layer
     */
    public static RuleEngine compile(ConstraintSet set, ColumnLayout layout, Collection<String> scenarioSheets,
                                     IntFunction<String> texts, Collection<String> valueTexts) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(texts, "texts");
        Compiler compiler = new Compiler(set, layout, new HashSet<>(scenarioSheets), texts, new HashSet<>(valueTexts));
        return compiler.compile();
    }

    /** Warnings found while compiling (values no cell has, gates that never admit). */
    public List<Diagnostic> findings() {
        return findings;
    }

    public ColumnLayout layout() {
        return layout;
    }

    public int size() {
        return rules.size();
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** The name a report uses for rule {@code index} (its id, {@code <unnamed>}, or the bond's file and line). */
    public String name(int index) {
        return rules.get(index).name;
    }

    public SourceLocation location(int index) {
        return rules.get(index).where;
    }

    /** Whether rule {@code index} reads only mandatory columns, so that it may run once per mandatory row. */
    public boolean mandatoryOnly(int index) {
        return rules.get(index).mandatoryOnly;
    }

    /** Whether any rule reads an optional column. */
    public boolean hasOptionalRules() {
        return rules.stream().anyMatch(r -> !r.mandatoryOnly);
    }

    /** Whether any rule reads only mandatory columns. */
    public boolean hasMandatoryRules() {
        return rules.stream().anyMatch(r -> r.mandatoryOnly);
    }

    /**
     * The rules a candidate violates, all of them (the legacy {@code row_violations} collected every rule, for the
     * per-rule and overlap statistics).
     *
     * @param view          the candidate
     * @param mandatoryOnly true: evaluate only the rules that read mandatory columns; false: only the others
     * @throws FrameworkException ASSEMBLY_RULE_ERROR when a rule cannot be evaluated on this candidate
     */
    public BitSet violations(CandidateView view, boolean mandatoryOnly) {
        BitSet hits = new BitSet(rules.size());
        Selections selections = new Selections(view, texts);
        for (int i = 0; i < rules.size(); i++) {
            Compiled rule = rules.get(i);
            if (rule.mandatoryOnly != mandatoryOnly) {
                continue;
            }
            try {
                if (rule.violated(selections)) {
                    hits.set(i);
                }
            } catch (Py.Failure | EvaluationError e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.ASSEMBLY_RULE_ERROR, "rule " + rule.name + " cannot be "
                        + "evaluated on the candidate " + selections.describe() + ": " + e.getMessage(), rule.where, "rule", rule.name), e);
            }
        }
        return hits;
    }

    /** Every rule, both phases at once. */
    public BitSet violations(CandidateView view) {
        BitSet all = violations(view, true);
        all.or(violations(view, false));
        return all;
    }

    // ---- per-candidate selections -------------------------------------------------------------------------------

    /** The candidate's selections, decoded lazily per column: the stripped value texts of its labels. */
    static final class Selections {
        private final CandidateView view;
        private final IntFunction<String> texts;
        private final Map<Integer, List<String>> decoded = new LinkedHashMap<>();

        Selections(CandidateView view, IntFunction<String> texts) {
            this.view = view;
            this.texts = texts;
        }

        /** The values of the column, in row order; empty when the sheet is absent. */
        List<String> values(int column) {
            return decoded.computeIfAbsent(column, c -> {
                int[] labels = view.labels(c);
                if (labels == null) {
                    return List.of();
                }
                List<String> out = new ArrayList<>(labels.length);
                for (int label : labels) {
                    out.add(texts.apply(label));
                }
                return out;
            });
        }

        int[] labels(int column) {
            return view.labels(column);
        }

        String describe() {
            return decoded.toString();
        }
    }

    /** A rule that cannot be evaluated on a candidate (a value outside a declared order, an unparseable rank). */
    static final class EvaluationError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        EvaluationError(String message) {
            super(message);
        }
    }

    // ---- compiled rules ----------------------------------------------------------------------------------------

    private abstract static class Compiled {
        final String name;
        final SourceLocation where;
        final boolean mandatoryOnly;

        Compiled(String name, SourceLocation where, boolean mandatoryOnly) {
            this.name = name;
            this.where = where;
            this.mandatoryOnly = mandatoryOnly;
        }

        abstract boolean violated(Selections s);
    }

    /** A sidecar rule (row_violations). */
    private static final class SieveRule extends Compiled {
        final boolean forbid;
        final CompiledCondition guard;
        final Kind kind;
        final int[] targets;          // target columns, in the rule's sheet order
        final boolean gateAdmits;     // positions are fixed per column, so the gate is decided once
        final CompiledCondition assertion;
        final List<Map<Integer, RuleValue>> pairs; // column -> value per entry
        final Map<Integer, List<RuleValue>> sets;
        final org.ghyura.cf.model.rule.Expr when;
        final List<String> whenSheets;
        final Map<String, Map<String, Map<String, RuleValue>>> params;
        final int mapSource;
        final int mapTarget;
        final Map<String, List<RuleValue>> allow;

        enum Kind { ASSERT, MAPPING, PAIRS, SETS, WHEN }

        SieveRule(String name, SourceLocation where, boolean mandatoryOnly, boolean forbid, CompiledCondition guard, Kind kind,
                  int[] targets, boolean gateAdmits, CompiledCondition assertion, List<Map<Integer, RuleValue>> pairs,
                  Map<Integer, List<RuleValue>> sets, org.ghyura.cf.model.rule.Expr when, List<String> whenSheets,
                  Map<String, Map<String, Map<String, RuleValue>>> params, int mapSource, int mapTarget,
                  Map<String, List<RuleValue>> allow) {
            super(name, where, mandatoryOnly);
            this.forbid = forbid;
            this.guard = guard;
            this.kind = kind;
            this.targets = targets;
            this.gateAdmits = gateAdmits;
            this.assertion = assertion;
            this.pairs = pairs;
            this.sets = sets;
            this.when = when;
            this.whenSheets = whenSheets;
            this.params = params;
            this.mapSource = mapSource;
            this.mapTarget = mapTarget;
            this.allow = allow;
        }

        @Override
        boolean violated(Selections s) {
            if (guard != null && !guard.holds(s)) {
                return false;                                   // out of context: the bond is inert on this candidate
            }
            switch (kind) {
                case ASSERT -> {
                    boolean holds = assertion.holds(s);
                    return forbid == holds;
                }
                case MAPPING -> {
                    List<String> targetValues = s.values(mapTarget);
                    for (String sv : s.values(mapSource)) {
                        List<RuleValue> allowed = allow.get(sv);
                        if (allowed != null) {
                            for (String tv : targetValues) {
                                if (allowed.stream().noneMatch(a -> a.equalsText(tv))) {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }
                default -> {
                    if (!gateAdmits) {
                        return false;                           // every placement combination fails the gate
                    }
                    List<List<String>> lists = new ArrayList<>(targets.length);
                    for (int column : targets) {
                        List<String> values = s.values(column);
                        if (values.isEmpty()) {
                            return false;                       // a referenced sheet without placements: inert
                        }
                        lists.add(values);
                    }
                    int[] index = new int[targets.length];
                    while (true) {
                        boolean holds = holds(lists, index);
                        if (forbid == holds) {
                            return true;
                        }
                        int k = targets.length - 1;
                        while (k >= 0 && ++index[k] == lists.get(k).size()) {
                            index[k] = 0;
                            k--;
                        }
                        if (k < 0) {
                            return false;
                        }
                    }
                }
            }
        }

        private boolean holds(List<List<String>> lists, int[] index) {
            switch (kind) {
                case SETS -> {
                    for (int k = 0; k < targets.length; k++) {
                        String v = lists.get(k).get(index[k]);
                        if (sets.get(targets[k]).stream().noneMatch(x -> x.equalsText(v))) {
                            return false;
                        }
                    }
                    return true;
                }
                case PAIRS -> {
                    for (Map<Integer, RuleValue> entry : pairs) {
                        boolean all = true;
                        for (int k = 0; k < targets.length && all; k++) {
                            RuleValue want = entry.get(targets[k]);
                            all = want != null && want.equalsText(lists.get(k).get(index[k]));
                        }
                        if (all) {
                            return true;
                        }
                    }
                    return false;
                }
                case WHEN -> {
                    Map<String, Py.Value> names = new LinkedHashMap<>();
                    for (int k = 0; k < targets.length; k++) {
                        String sheet = whenSheets.get(k);
                        String value = lists.get(k).get(index[k]);
                        Map<String, Py.Value> attrs = new LinkedHashMap<>();
                        Map<String, RuleValue> declared = params.getOrDefault(sheet, Map.of()).getOrDefault(value, Map.of());
                        declared.forEach((a, v) -> attrs.put(a, Py.of(v)));
                        attrs.putIfAbsent("value", new Py.Str(value));
                        attrs.putIfAbsent("pos", new Py.Int(BigInteger.valueOf(targets[k])));
                        names.put(sheet, new Py.Namespace(sheet, attrs));
                    }
                    return PredicateEvaluator.holds(when, names);
                }
                default -> throw new IllegalStateException(kind.name());
            }
        }
    }

    /** A line of a legacy bonds file, with the Reader filter's semantics. */
    private static final class BondRule extends Compiled {
        final boolean forbid;
        final boolean gateOk;
        final int[] columns;
        final List<int[]> tuples;

        BondRule(String name, SourceLocation where, boolean mandatoryOnly, boolean forbid, boolean gateOk, int[] columns,
                 List<int[]> tuples) {
            super(name, where, mandatoryOnly);
            this.forbid = forbid;
            this.gateOk = gateOk;
            this.columns = columns;
            this.tuples = tuples;
        }

        @Override
        boolean violated(Selections s) {
            int[][] values = new int[columns.length][];
            for (int k = 0; k < columns.length; k++) {
                int[] labels = s.labels(columns[k]);
                if (labels == null || labels.length == 0) {
                    return false;                               // a referenced sheet absent or empty: not applicable
                }
                values[k] = labels;
            }
            if (!gateOk) {
                return !forbid;                                 // require can never hold here, forbid never fire
            }
            boolean holds = false;
            for (int[] tuple : tuples) {
                boolean all = true;
                for (int k = 0; k < tuple.length && all; k++) {
                    all = contains(values[k], tuple[k]);
                }
                if (all) {
                    holds = true;
                    break;
                }
            }
            return forbid == holds;
        }

        private static boolean contains(int[] labels, int label) {
            for (int x : labels) {
                if (x == label) {
                    return true;
                }
            }
            return false;
        }
    }

    // ---- conditions ----------------------------------------------------------------------------------------------

    private interface CompiledCondition {
        boolean holds(Selections s);
    }

    /** How the ordinal operators rank one column's values. */
    private record Ranker(String sheet, OrderSpec order) {
        Py.Value rank(String value) {
            return rankOf(new RuleValue.Text(value));
        }

        Py.Value rankOf(RuleValue value) {
            return switch (order) {
                case OrderSpec.Listed l -> {
                    for (int i = 0; i < l.values().size(); i++) {
                        if (RuleValue.pyEquals(l.values().get(i), value)) {
                            yield new Py.Int(BigInteger.valueOf(i));
                        }
                    }
                    throw new EvaluationError("value " + value.display() + " is not in the declared order for sheet " + sheet);
                }
                case OrderSpec.Numeric n -> {
                    try {
                        yield Py.toFloat(Py.of(value));
                    } catch (Py.Failure e) {
                        throw new EvaluationError("sheet " + sheet + " order is 'numeric' but value " + value.display() + " is not a number");
                    }
                }
                case OrderSpec.Date d -> {
                    String text = value instanceof RuleValue.Text t ? t.value() : value.display();
                    text = new String(text.codePoints().limit(10).toArray(), 0, (int) Math.min(10, text.codePoints().count()));
                    try {
                        yield new Py.Int(BigInteger.valueOf(isoDate(text).toEpochDay() + 719_163L));
                    } catch (DateTimeException | IllegalArgumentException e) {
                        throw new EvaluationError("sheet " + sheet + " order is 'date' but value " + value.display() + " is not ISO yyyy-mm-dd");
                    }
                }
            };
        }
    }

    /** Python date.fromisoformat (3.11): YYYY-MM-DD, YYYYMMDD and the ISO week forms. */
    static LocalDate isoDate(String text) {
        if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (text.matches("\\d{8}")) {
            return LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})-?W(\\d{2})(?:-?(\\d))?").matcher(text);
        if (m.matches()) {
            int year = Integer.parseInt(m.group(1));
            int week = Integer.parseInt(m.group(2));
            int day = m.group(3) == null ? 1 : Integer.parseInt(m.group(3));
            if (week < 1 || day < 1 || day > 7) {
                throw new IllegalArgumentException(text);
            }
            LocalDate first = LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 1).with(java.time.DayOfWeek.MONDAY);
            LocalDate result = first.plusWeeks(week - 1L).plusDays(day - 1L);
            if (result.get(IsoFields.WEEK_BASED_YEAR) != year) {
                throw new IllegalArgumentException(text);
            }
            return result;
        }
        throw new IllegalArgumentException(text);
    }

    /** Python min/max over ranks: keep the first, replace on a strict comparison (NaN never replaces nor is replaced). */
    private static Py.Value extreme(List<Py.Value> ranks, boolean min) {
        Py.Value best = ranks.getFirst();
        for (int i = 1; i < ranks.size(); i++) {
            if (Py.order(min ? "<" : ">", ranks.get(i), best)) {
                best = ranks.get(i);
            }
        }
        return best;
    }

    private static List<Py.Value> ranks(Ranker ranker, List<String> values) {
        List<Py.Value> out = new ArrayList<>(values.size());
        for (String v : values) {
            out.add(ranker.rank(v));
        }
        return out;
    }

    // ---- the compiler --------------------------------------------------------------------------------------------

    private static final class Compiler {
        private final ConstraintSet set;
        private final ColumnLayout layout;
        private final Set<String> scenarioSheets;
        private final IntFunction<String> texts;
        private final Set<String> valueTexts;
        private final List<Diagnostic> errors = new ArrayList<>();
        private final List<Diagnostic> warnings = new ArrayList<>();

        Compiler(ConstraintSet set, ColumnLayout layout, Set<String> scenarioSheets, IntFunction<String> texts, Set<String> valueTexts) {
            this.set = set;
            this.layout = layout;
            this.scenarioSheets = scenarioSheets;
            this.texts = texts;
            this.valueTexts = valueTexts;
        }

        RuleEngine compile() {
            for (String sheet : set.params().keySet()) {
                knownSheet(sheet, "params", SourceLocation.of(sourceName()));
            }
            for (String sheet : set.orders().keySet()) {
                knownSheet(sheet, "orders", SourceLocation.of(sourceName()));
            }
            List<Compiled> compiled = new ArrayList<>();
            for (Rule rule : set.rules()) {
                Compiled c = compile(rule);
                if (c != null) {
                    compiled.add(c);
                }
            }
            for (LegacyBond bond : set.bonds()) {
                Compiled c = compile(bond);
                if (c != null) {
                    compiled.add(c);
                }
            }
            if (!errors.isEmpty()) {
                throw new FrameworkException(errors);
            }
            return new RuleEngine(layout, compiled, warnings, texts);
        }

        private String sourceName() {
            return set.sources().isEmpty() ? "" : set.sources().getFirst().name();
        }

        private void knownSheet(String sheet, String where, SourceLocation location) {
            if (!scenarioSheets.contains(sheet) && layout.index(sheet) < 0) {
                errors.add(Diagnostic.error(ErrorCode.CONSTRAINT_UNKNOWN_SHEET, where + " names the sheet '" + sheet
                        + "', which the scenario does not have", location, "sheet", sheet));
            }
        }

        /** The column of a sheet a rule references, or -1 after reporting why it is none. */
        private int column(String sheet, String rule, SourceLocation where) {
            int column = layout.index(sheet);
            if (column >= 0) {
                return column;
            }
            if (scenarioSheets.contains(sheet)) {
                errors.add(Diagnostic.error(ErrorCode.CONSTRAINT_UNMATERIALIZED_SHEET, "rule " + rule + " names the sheet '" + sheet
                        + "', which is no column of the candidates (excluded, heading, consumed by a joiner, dropped or without "
                        + "directives), so it could never fire; bond a sheet the candidates contain, or check it in the oracle",
                        where, "sheet", sheet, "rule", rule));
            } else {
                errors.add(Diagnostic.error(ErrorCode.CONSTRAINT_UNKNOWN_SHEET, "rule " + rule + " names the sheet '" + sheet
                        + "', which the scenario does not have", where, "sheet", sheet, "rule", rule));
            }
            return -1;
        }

        private boolean mandatory(int column) {
            return !layout.column(column).optional();
        }

        private void inertValue(RuleValue value, String rule, String sheet, SourceLocation where) {
            if (!(value instanceof RuleValue.Text t) || !valueTexts.contains(t.value())) {
                warnings.add(Diagnostic.warning(ErrorCode.CONSTRAINT_INERT, "rule " + rule + " names the value " + describe(value)
                        + " for sheet " + sheet + ", which no cell has (cell values are compared as text with white space "
                        + "stripped), so that part never matches", where, "rule", rule, "sheet", sheet));
            }
        }

        private static String describe(RuleValue v) {
            return v instanceof RuleValue.Text t ? "'" + t.value() + "'" : v.display() + " (not a text)";
        }

        private Compiled compile(Rule rule) {
            String name = rule.name();
            SourceLocation where = rule.location();
            int before = errors.size();
            List<Integer> referenced = new ArrayList<>();
            for (String sheet : rule.referencedSheets()) {
                referenced.add(column(sheet, name, where));
            }
            if (errors.size() > before) {
                return null;
            }
            boolean mandatoryOnly = referenced.stream().allMatch(this::mandatory);
            CompiledCondition guard = rule.condition() == null ? null : condition(rule.condition(), name, where);
            boolean forbid = rule.polarity() == Rule.Polarity.FORBID;
            List<String> targetSheets = rule.targetSheets();
            int[] targets = targetSheets.stream().mapToInt(layout::index).toArray();
            boolean gateAdmits = rule.gate().admits(targets);
            if (!(rule.body() instanceof Rule.Assert) && !(rule.body() instanceof Rule.Mapping) && !gateAdmits) {
                warnings.add(Diagnostic.warning(ErrorCode.CONSTRAINT_INERT, "rule " + name + ": its gate never admits the columns "
                        + "of its sheets " + targetSheets + " (positions " + java.util.Arrays.toString(targets) + "), so it never fires",
                        where, "rule", name));
            }
            CompiledCondition assertion = null;
            List<Map<Integer, RuleValue>> pairs = List.of();
            Map<Integer, List<RuleValue>> sets = Map.of();
            org.ghyura.cf.model.rule.Expr when = null;
            List<String> whenSheets = List.of();
            int mapSource = -1;
            int mapTarget = -1;
            Map<String, List<RuleValue>> allow = Map.of();
            SieveRule.Kind kind;
            switch (rule.body()) {
                case Rule.Assert a -> {
                    kind = SieveRule.Kind.ASSERT;
                    assertion = condition(a.condition(), name, where);
                }
                case Rule.Mapping m -> {
                    kind = SieveRule.Kind.MAPPING;
                    mapSource = layout.index(m.source());
                    mapTarget = layout.index(m.target());
                    allow = m.allow();
                    m.allow().forEach((k, vs) -> {
                        inertValue(new RuleValue.Text(k), name, m.source(), where);
                        vs.forEach(v -> inertValue(v, name, m.target(), where));
                    });
                }
                case Rule.Pairs p -> {
                    kind = SieveRule.Kind.PAIRS;
                    List<Map<Integer, RuleValue>> entries = new ArrayList<>();
                    for (Map<String, RuleValue> e : p.entries()) {
                        Map<Integer, RuleValue> byColumn = new LinkedHashMap<>();
                        for (String sheet : p.sheets()) {
                            RuleValue v = e.get(sheet);
                            if (v == null) {
                                warnings.add(Diagnostic.warning(ErrorCode.CONSTRAINT_INERT, "rule " + name + ": the pairs entry "
                                        + new RuleValue.MapValue(e).display() + " does not name the sheet " + sheet
                                        + ", so it never matches", where, "rule", name));
                            } else {
                                byColumn.put(layout.index(sheet), v);
                                inertValue(v, name, sheet, where);
                            }
                        }
                        entries.add(byColumn);
                    }
                    pairs = entries;
                }
                case Rule.Sets s -> {
                    kind = SieveRule.Kind.SETS;
                    Map<Integer, List<RuleValue>> byColumn = new LinkedHashMap<>();
                    s.sets().forEach((sheet, values) -> {
                        byColumn.put(layout.index(sheet), values);
                        values.forEach(v -> inertValue(v, name, sheet, where));
                    });
                    sets = byColumn;
                }
                case Rule.When w -> {
                    kind = SieveRule.Kind.WHEN;
                    when = w.expression();
                    whenSheets = w.sheets();
                }
            }
            if (errors.size() > before) {
                return null;
            }
            return new SieveRule(name, where, mandatoryOnly, forbid, guard, kind, targets, gateAdmits, assertion, pairs, sets, when,
                    whenSheets, set.params(), mapSource, mapTarget, allow);
        }

        private Compiled compile(LegacyBond bond) {
            String name = bond.location().toString();
            int before = errors.size();
            int[] columns = new int[bond.sheets().size()];
            for (int k = 0; k < columns.length; k++) {
                columns[k] = column(bond.sheets().get(k), name, bond.location());
            }
            if (errors.size() > before) {
                return null;
            }
            List<int[]> tuples = new ArrayList<>();
            for (List<Integer> t : bond.tuples()) {
                int[] labels = t.stream().mapToInt(Integer::intValue).toArray();
                for (int label : labels) {
                    try {
                        texts.apply(label);
                    } catch (RuntimeException e) {
                        errors.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, "bond " + name + " names the label " + label
                                + ", which no value of this scenario has (the legacy filter matched nothing for it)", bond.location(),
                                "label", Integer.toString(label)));
                    }
                }
                tuples.add(labels);
            }
            if (errors.size() > before) {
                return null;
            }
            boolean mandatoryOnly = java.util.Arrays.stream(columns).allMatch(this::mandatory);
            return new BondRule(name, bond.location(), mandatoryOnly, bond.forbid(), bond.gateOk(), columns, tuples);
        }

        private CompiledCondition condition(Condition c, String rule, SourceLocation where) {
            return switch (c) {
                case Condition.Always a -> s -> true;
                case Condition.All a -> {
                    List<CompiledCondition> children = a.children().stream().map(x -> condition(x, rule, where)).toList();
                    yield s -> {
                        for (CompiledCondition child : children) {
                            if (!child.holds(s)) {
                                return false;
                            }
                        }
                        return true;
                    };
                }
                case Condition.Any a -> {
                    List<CompiledCondition> children = a.children().stream().map(x -> condition(x, rule, where)).toList();
                    yield s -> {
                        for (CompiledCondition child : children) {
                            if (child.holds(s)) {
                                return true;
                            }
                        }
                        return false;
                    };
                }
                case Condition.Not n -> {
                    CompiledCondition child = condition(n.child(), rule, where);
                    yield s -> !child.holds(s);
                }
                case Condition.Leaf l -> leaf(l, rule, where);
            };
        }

        private Ranker ranker(String sheet, String rule, SourceLocation where) {
            OrderSpec order = set.orders().get(sheet);
            if (order == null) {
                errors.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, "rule " + rule + " compares the values of sheet " + sheet
                        + " by order, but 'orders' declares none for it (the legacy sieve refused that when it evaluated)", where,
                        "rule", rule, "sheet", sheet));
                return null;
            }
            return new Ranker(sheet, order);
        }

        private CompiledCondition leaf(Condition.Leaf l, String rule, SourceLocation where) {
            int column = layout.index(l.sheet());
            int other = l.other() == null ? -1 : layout.index(l.other());
            switch (l.op().kind()) {
                case VALUE -> inertValue(l.value(), rule, l.sheet(), where);
                case VALUES -> l.values().forEach(v -> inertValue(v, rule, l.sheet(), where));
                default -> {
                }
            }
            RuleValue value = l.value();
            List<RuleValue> values = l.values();
            BigInteger count = l.count();
            return switch (l.op()) {
                case PRESENT -> s -> s.values(column).isEmpty() != l.flag();
                case EQ -> s -> {
                    List<String> v = s.values(column);
                    return v.size() == 1 && value.equalsText(v.getFirst());
                };
                case NE -> s -> {
                    List<String> v = s.values(column);
                    return !(v.size() == 1 && value.equalsText(v.getFirst()));
                };
                case HAS -> s -> s.values(column).stream().anyMatch(value::equalsText);
                case HASNT -> s -> s.values(column).stream().noneMatch(value::equalsText);
                case IN -> s -> s.values(column).stream().allMatch(v -> values.stream().anyMatch(x -> x.equalsText(v)));
                case NIN -> s -> s.values(column).stream().noneMatch(v -> values.stream().anyMatch(x -> x.equalsText(v)));
                case HAS_ANY -> s -> s.values(column).stream().anyMatch(v -> values.stream().anyMatch(x -> x.equalsText(v)));
                case EQ_SHEET -> s -> new HashSet<>(s.values(column)).equals(new HashSet<>(s.values(other)));
                case NE_SHEET -> s -> !new HashSet<>(s.values(column)).equals(new HashSet<>(s.values(other)));
                case SUB_OF -> s -> new HashSet<>(s.values(other)).containsAll(s.values(column));
                case SUP_OF -> s -> new HashSet<>(s.values(column)).containsAll(s.values(other));
                case COUNT -> s -> BigInteger.valueOf(s.values(column).size()).equals(count);
                case COUNT_GE -> s -> BigInteger.valueOf(s.values(column).size()).compareTo(count) >= 0;
                case COUNT_LE -> s -> BigInteger.valueOf(s.values(column).size()).compareTo(count) <= 0;
                case GE, GT, LE, LT -> {
                    Ranker ranker = ranker(l.sheet(), rule, where);
                    if (ranker == null) {
                        yield s -> true;
                    }
                    Py.Value constant;
                    try {
                        constant = ranker.rankOf(value);
                    } catch (EvaluationError e) {
                        errors.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, "rule " + rule + ": " + e.getMessage()
                                + " (the constant of '" + l.op().token() + "')", where, "rule", rule));
                        yield s -> true;
                    }
                    Condition.Op op = l.op();
                    yield s -> {
                        List<String> v = s.values(column);
                        if (v.isEmpty()) {
                            return true;                        // vacuous on absence
                        }
                        List<Py.Value> rs = ranks(ranker, v);
                        return switch (op) {
                            case GE -> Py.order(">=", extreme(rs, true), constant);
                            case GT -> Py.order(">", extreme(rs, true), constant);
                            case LE -> Py.order("<=", extreme(rs, false), constant);
                            default -> Py.order("<", extreme(rs, false), constant);
                        };
                    };
                }
                case GE_SHEET, GT_SHEET, LE_SHEET, LT_SHEET -> {
                    Ranker left = ranker(l.sheet(), rule, where);
                    Ranker right = ranker(l.other(), rule, where);
                    if (left == null || right == null) {
                        yield s -> true;
                    }
                    Condition.Op op = l.op();
                    yield s -> {
                        List<String> lv = s.values(column);
                        List<String> rv = s.values(other);
                        if (lv.isEmpty() || rv.isEmpty()) {
                            return true;
                        }
                        List<Py.Value> ls = ranks(left, lv);
                        List<Py.Value> rr = ranks(right, rv);
                        return switch (op) {
                            case GE_SHEET -> Py.order(">=", extreme(ls, true), extreme(rr, false));
                            case GT_SHEET -> Py.order(">", extreme(ls, true), extreme(rr, false));
                            case LE_SHEET -> Py.order("<=", extreme(ls, false), extreme(rr, true));
                            default -> Py.order("<", extreme(ls, false), extreme(rr, true));
                        };
                    };
                }
            };
        }
    }
}
