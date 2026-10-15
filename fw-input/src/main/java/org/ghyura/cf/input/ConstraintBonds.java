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

package org.ghyura.cf.input;

import com.fasterxml.jackson.core.JsonParser;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.RuleValue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The constraint editor's core (GEN:operator-authoring-ui; the legacy constraints/editor.py sidecar_to_bonds,
 * bonds_to_sidecar and find_contradictions, and sieve.py describe): a sidecar as drawable bonds and bonds back as the
 * canonical sidecar, exactly as the legacy editor wrote it (pairs regrouped by sheets, gate, polarity and condition as
 * {@code bond<i>_...}, multi-value sides as {@code mm<j>_...} sets, {@code when<k>_...}, {@code map<m>_...} and
 * {@code assert<n>_...} rules, with the legacy descriptions), so a round trip through the editor changes nothing it does
 * not have to. Values are the sidecar's JSON values; Python's truthiness and str() decide what the legacy code decided.
 */
public final class ConstraintBonds {

    /**
     * A bond: a polarity between sides (sheet to values), a gate, and the advanced bodies the editor keeps as data.
     *
     * @param assertion the condition tree of an assert rule, or null when the bond has none (the key is then absent)
     */
    public record Bond(String polarity, Map<String, List<RuleValue>> sides, RuleValue gate, RuleValue when, RuleValue condition,
                       RuleValue mapping, RuleValue assertion, String desc) {
        public Bond {
            Objects.requireNonNull(polarity, "polarity");
            Map<String, List<RuleValue>> copy = new LinkedHashMap<>();
            sides.forEach((sheet, values) -> copy.put(sheet, List.copyOf(values)));
            sides = java.util.Collections.unmodifiableMap(copy);
            gate = gate == null ? new RuleValue.MapValue(Map.of()) : gate;
            desc = desc == null ? "" : desc;
        }

        /** A plain pair bond between single values, as the bond matrix draws it. */
        public static Bond pair(String polarity, String sheetA, String valueA, String sheetB, String valueB, RuleValue gate) {
            Map<String, List<RuleValue>> sides = new LinkedHashMap<>();
            sides.put(sheetA, List.of(new RuleValue.Text(valueA)));
            sides.put(sheetB, List.of(new RuleValue.Text(valueB)));
            return new Bond(polarity, sides, gate, null, null, null, null, "");
        }

        /** The bond as the legacy editor's dict: polarity, sides, gate, when, condition, mapping, assert (only when set), desc. */
        public RuleValue.MapValue toValue() {
            Map<String, RuleValue> m = new LinkedHashMap<>();
            m.put("polarity", new RuleValue.Text(polarity));
            Map<String, RuleValue> s = new LinkedHashMap<>();
            sides.forEach((sheet, values) -> s.put(sheet, new RuleValue.ListValue(values)));
            m.put("sides", new RuleValue.MapValue(s));
            m.put("gate", gate);
            m.put("when", orNull(when));
            m.put("condition", orNull(condition));
            m.put("mapping", orNull(mapping));
            if (assertion != null) {
                m.put("assert", assertion);
            }
            m.put("desc", new RuleValue.Text(desc));
            return new RuleValue.MapValue(m);
        }
    }

    /** What reading a sidecar gives: its bonds and its orders (the legacy _BondList.orders). */
    public record Bonds(List<Bond> bonds, RuleValue orders) {
        public Bonds {
            bonds = List.copyOf(bonds);
        }
    }

    /** Bonds that forbid exactly what others require: the same sheets, gate, values and condition, opposite polarities. */
    public record Contradiction(List<String> sheets, List<Object> gate, List<Map.Entry<String, List<String>>> sides, String condition) {
        public Contradiction {
            sheets = List.copyOf(sheets);
            gate = List.copyOf(gate);
            sides = List.copyOf(sides);
        }
    }

    private static final RuleValue.MapValue EMPTY = new RuleValue.MapValue(Map.of());

    private ConstraintBonds() {
    }

    // ---- reading ---------------------------------------------------------------------------------------------------

    /** sidecar_to_bonds: every constraint as bonds; condition and mapping kept as data. */
    public static Bonds read(RuleValue sidecar) {
        Map<String, RuleValue> doc = map(sidecar);
        RuleValue orders = orEmpty(doc.get("orders"));
        List<Bond> bonds = new ArrayList<>();
        for (RuleValue cv : list(doc.get("constraints"))) {
            Map<String, RuleValue> c = map(cv);
            RuleValue mapping = absentIfNull(c.get("mapping"));
            RuleValue assertion = absentIfNull(c.get("assert"));
            List<String> sheets = sheetsOf(c, mapping, assertion);
            String polarity = c.containsKey("polarity") ? PyText.str(c.get("polarity")) : assertion != null ? "require" : "forbid";
            RuleValue gate = c.containsKey("gate") && truthy(c.get("gate")) ? c.get("gate") : EMPTY;
            RuleValue condition = absentIfNull(c.get("condition"));
            if (assertion != null) {
                bonds.add(new Bond(polarity, emptySides(sheets), gate, null, condition, null, assertion, descOf(c)));
            } else if (mapping != null) {
                bonds.add(new Bond(polarity, emptySides(sheets), gate, null, condition, mapping, null, descOf(c)));
            } else if (absentIfNull(c.get("when")) != null) {
                bonds.add(new Bond(polarity, emptySides(sheets), gate, c.get("when"), condition, null, null, descOf(c)));
            } else if (absentIfNull(c.get("sets")) != null) {
                Map<String, List<RuleValue>> sides = new LinkedHashMap<>();
                map(c.get("sets")).forEach((sheet, values) -> sides.put(sheet, list(values)));
                bonds.add(new Bond(polarity, sides, gate, null, condition, null, null, ""));
            } else {
                for (RuleValue ev : list(c.get("pairs"))) {
                    Map<String, RuleValue> e = map(ev);
                    Map<String, List<RuleValue>> sides = new LinkedHashMap<>();
                    for (String sheet : sheets) {
                        if (e.containsKey(sheet)) {
                            sides.put(sheet, List.of(e.get(sheet)));
                        }
                    }
                    bonds.add(new Bond(polarity, sides, gate, null, condition, null, null, ""));
                }
            }
        }
        return new Bonds(bonds, orders);
    }

    /** The legacy chain: sheets, else the sets' keys, else the mapping's source and target, else the assert's sheets, else the pairs' keys sorted. */
    private static List<String> sheetsOf(Map<String, RuleValue> c, RuleValue mapping, RuleValue assertion) {
        if (c.containsKey("sheets") && truthy(c.get("sheets"))) {
            return list(c.get("sheets")).stream().map(PyText::str).toList();
        }
        if (c.containsKey("sets") && !map(c.get("sets")).isEmpty()) {
            return List.copyOf(map(c.get("sets")).keySet());
        }
        if (mapping != null && truthy(mapping)) {
            Map<String, RuleValue> m = map(mapping);
            return List.of(PyText.str(m.getOrDefault("source", RuleValue.Null.NULL)), PyText.str(m.getOrDefault("target", RuleValue.Null.NULL)));
        }
        if (assertion != null) {
            List<String> sheets = conditionSheets(assertion);
            if (!sheets.isEmpty()) {
                return sheets;
            }
        }
        TreeSet<String> keys = new TreeSet<>(PyText.CODE_POINT_ORDER);
        for (RuleValue e : c.containsKey("pairs") ? list(c.get("pairs")) : List.<RuleValue>of()) {
            keys.addAll(map(e).keySet());
        }
        return List.copyOf(keys);
    }

    private static String descOf(Map<String, RuleValue> c) {
        RuleValue d = c.get("desc");
        return d != null && truthy(d) ? PyText.str(d) : describe(new RuleValue.MapValue(c));
    }

    private static Map<String, List<RuleValue>> emptySides(List<String> sheets) {
        Map<String, List<RuleValue>> sides = new LinkedHashMap<>();
        for (String s : sheets) {
            sides.put(s, List.of());
        }
        return sides;
    }

    // ---- writing ---------------------------------------------------------------------------------------------------

    private record Group(List<String> sheets, RuleValue gate, String polarity, RuleValue condition, List<RuleValue> pairs,
                         List<Integer> members) {
    }

    private record Other(List<String> sheets, RuleValue gate, String polarity, RuleValue body, RuleValue condition, int member) {
    }

    /** The bonds as bonds_to_sidecar sorts them: pair groups by first appearance, then sets, whens, mappings and asserts. */
    private record Partition(Map<String, Group> groups, List<Other> sets, List<Other> whens, List<Other> mappings, List<Other> asserts) {
    }

    private static Partition partition(List<Bond> bonds) {
        Partition p = new Partition(new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int index = 0; index < bonds.size(); index++) {
            Bond b = bonds.get(index);
            List<String> sheets = new ArrayList<>(b.sides().keySet());
            List<String> sorted = sheets.stream().sorted(PyText.CODE_POINT_ORDER).toList();
            RuleValue gate = truthy(b.gate()) ? b.gate() : EMPTY;
            RuleValue condition = b.condition();
            if (b.assertion() != null) {
                p.asserts().add(new Other(sorted, gate, b.polarity(), b.assertion(), condition, index));
            } else if (b.mapping() != null && truthy(b.mapping())) {
                p.mappings().add(new Other(sorted, gate, b.polarity(), b.mapping(), condition, index));
            } else if (b.when() != null && truthy(b.when())) {
                p.whens().add(new Other(sorted, gate, b.polarity(), b.when(), condition, index));
            } else if (b.sides().values().stream().anyMatch(v -> v.size() > 1)) {
                Map<String, RuleValue> body = new LinkedHashMap<>();
                b.sides().forEach((sheet, values) -> body.put(sheet, new RuleValue.ListValue(values)));
                p.sets().add(new Other(sorted, gate, b.polarity(), new RuleValue.MapValue(body), condition, index));
            } else {
                String key = String.join("\u0000", sorted) + "\u0001" + pythonJson(gate, true, true) + "\u0001" + b.polarity() + "\u0001"
                        + (condition != null && truthy(condition) ? pythonJson(condition, true, false) : "");
                Group g = p.groups().computeIfAbsent(key, k -> new Group(sortedUnique(sheets), gate, b.polarity(), condition, new ArrayList<>(),
                        new ArrayList<>()));
                Map<String, RuleValue> pair = new LinkedHashMap<>();
                b.sides().forEach((sheet, values) -> pair.put(sheet, values.getFirst()));
                g.pairs().add(new RuleValue.MapValue(pair));
                g.members().add(index);
            }
        }
        return p;
    }

    /**
     * The bonds each constraint {@link #write} writes comes from, by their index, in its order: every bond of a group of
     * pairs, else the one bond.
     */
    public static List<List<Integer>> members(List<Bond> bonds) {
        Partition p = partition(bonds);
        List<List<Integer>> out = new ArrayList<>();
        p.groups().values().forEach(g -> out.add(List.copyOf(g.members())));
        for (List<Other> kind : List.of(p.sets(), p.whens(), p.mappings(), p.asserts())) {
            kind.forEach(o -> out.add(List.of(o.member())));
        }
        return out;
    }

    /** bonds_to_sidecar: the canonical sidecar of bonds, with params ({} without) and orders (left out when empty). */
    public static RuleValue.MapValue write(List<Bond> bonds, RuleValue params, RuleValue orders) {
        Partition p = partition(bonds);
        Map<String, Group> groups = p.groups();
        List<Other> sets = p.sets();
        List<Other> whens = p.whens();
        List<Other> mappings = p.mappings();
        List<Other> asserts = p.asserts();
        List<RuleValue> constraints = new ArrayList<>();
        int i = 0;
        for (Group g : groups.values()) {
            Map<String, RuleValue> c = new LinkedHashMap<>();
            c.put("id", new RuleValue.Text("bond" + i++ + "_" + String.join("_", g.sheets())));
            c.put("polarity", new RuleValue.Text(g.polarity()));
            c.put("sheets", texts(g.sheets()));
            c.put("pairs", new RuleValue.ListValue(g.pairs()));
            c.put("gate", g.gate());
            c.put("desc", new RuleValue.Text(g.pairs().size() + " " + g.polarity() + " " + String.join("×", g.sheets()) + " bond(s)"));
            putCondition(c, g.condition());
            constraints.add(new RuleValue.MapValue(c));
        }
        int j = 0;
        for (Other o : sets) {
            Map<String, RuleValue> c = new LinkedHashMap<>();
            List<String> parts = new ArrayList<>();
            map(o.body()).forEach((sheet, values) -> parts.add(sheet + "∈{" + String.join(",", list(values).stream().map(PyText::str).toList()) + "}"));
            c.put("id", new RuleValue.Text("mm" + j++ + "_" + String.join("_", o.sheets())));
            c.put("polarity", new RuleValue.Text(o.polarity()));
            c.put("sheets", texts(o.sheets()));
            c.put("sets", o.body());
            c.put("gate", o.gate());
            c.put("desc", new RuleValue.Text(o.polarity() + " " + String.join(" × ", parts)));
            putCondition(c, o.condition());
            constraints.add(new RuleValue.MapValue(c));
        }
        int k = 0;
        for (Other o : whens) {
            Map<String, RuleValue> c = new LinkedHashMap<>();
            c.put("id", new RuleValue.Text("when" + k++ + "_" + String.join("_", o.sheets())));
            c.put("polarity", new RuleValue.Text(o.polarity()));
            c.put("sheets", texts(o.sheets()));
            c.put("when", o.body());
            c.put("gate", o.gate());
            c.put("desc", new RuleValue.Text(o.polarity() + " " + String.join("/", o.sheets()) + " when " + PyText.str(o.body())));
            putCondition(c, o.condition());
            constraints.add(new RuleValue.MapValue(c));
        }
        int m = 0;
        for (Other o : mappings) {
            Map<String, RuleValue> c = new LinkedHashMap<>();
            Map<String, RuleValue> mapping = map(o.body());
            c.put("id", new RuleValue.Text("map" + m++ + "_" + String.join("_", o.sheets())));
            c.put("polarity", new RuleValue.Text(o.polarity()));
            c.put("sheets", texts(o.sheets()));
            c.put("mapping", o.body());
            c.put("gate", o.gate());
            c.put("desc", new RuleValue.Text("mapping " + PyText.str(mapping.getOrDefault("source", RuleValue.Null.NULL)) + "->"
                    + PyText.str(mapping.getOrDefault("target", RuleValue.Null.NULL))));
            putCondition(c, o.condition());
            constraints.add(new RuleValue.MapValue(c));
        }
        int n = 0;
        for (Other o : asserts) {
            Map<String, RuleValue> c = new LinkedHashMap<>();
            Map<String, RuleValue> described = new LinkedHashMap<>();
            described.put("polarity", new RuleValue.Text(o.polarity()));
            described.put("assert", o.body());
            described.put("condition", orNull(o.condition()));
            c.put("id", new RuleValue.Text("assert" + n++ + "_" + String.join("_", o.sheets())));
            c.put("polarity", new RuleValue.Text(o.polarity()));
            c.put("assert", o.body());
            c.put("desc", new RuleValue.Text(describe(new RuleValue.MapValue(described))));
            putCondition(c, o.condition());
            constraints.add(new RuleValue.MapValue(c));
        }
        Map<String, RuleValue> out = new LinkedHashMap<>();
        out.put("version", new RuleValue.Int(BigInteger.ONE));
        out.put("params", params != null && truthy(params) ? params : EMPTY);
        out.put("constraints", new RuleValue.ListValue(constraints));
        if (orders != null && truthy(orders)) {
            out.put("orders", orders);
        }
        return new RuleValue.MapValue(out);
    }

    /** sorted(set(sheets)) in code point order. */
    private static List<String> sortedUnique(List<String> sheets) {
        TreeSet<String> unique = new TreeSet<>(PyText.CODE_POINT_ORDER);
        unique.addAll(sheets);
        return List.copyOf(unique);
    }

    private static void putCondition(Map<String, RuleValue> c, RuleValue condition) {
        if (condition != null && truthy(condition)) {
            c.put("condition", condition);
        }
    }

    // ---- contradictions --------------------------------------------------------------------------------------------

    /** find_contradictions: enumerated bonds (not when, not mapping) asserted with both polarities. */
    public static List<Contradiction> contradictions(List<Bond> bonds) {
        Map<String, String> seen = new LinkedHashMap<>();
        List<Contradiction> out = new ArrayList<>();
        for (Bond b : bonds) {
            if (b.when() != null && truthy(b.when()) || b.mapping() != null && truthy(b.mapping())) {
                continue;
            }
            List<String> sheets = b.sides().keySet().stream().sorted(PyText.CODE_POINT_ORDER).toList();
            List<Map.Entry<String, List<String>>> sides = new ArrayList<>();
            for (String s : sheets) {
                sides.add(Map.entry(s, b.sides().get(s).stream().map(PyText::str).sorted(PyText.CODE_POINT_ORDER).toList()));
            }
            String condition = pythonJson(b.condition() != null && truthy(b.condition()) ? b.condition() : EMPTY, true, false);
            List<Object> gate = canonicalGate(b.gate());
            String key = sheets + "\u0001" + gate + "\u0001" + sides + "\u0001" + condition;
            if (seen.containsKey(key) && !seen.get(key).equals(b.polarity())) {
                out.add(new Contradiction(sheets, gate, sides, condition));
            }
            seen.putIfAbsent(key, b.polarity());
        }
        return out;
    }

    private static List<Object> canonicalGate(RuleValue gate) {
        Map<String, RuleValue> g = gate instanceof RuleValue.MapValue m ? m.entries() : Map.of();
        if (g.containsKey("adjacent") && truthy(g.get("adjacent"))) {
            return List.of("adjacent");
        }
        if (g.containsKey("within")) {
            RuleValue w = g.get("within");
            return List.of("within", w instanceof RuleValue.Int i ? i.value() : BigInteger.valueOf((long) ((RuleValue.Real) w).value()));
        }
        return List.of();
    }

    // ---- plain language (sieve.describe, describe_condition, _condition_sheets) -----------------------------------

    private static final List<String> SHEET_OPS = List.of("eqSheet", "neSheet", "geSheet", "gtSheet", "leSheet", "ltSheet");

    /** _condition_sheets: the sheets a condition tree names, in first-seen order. */
    public static List<String> conditionSheets(RuleValue cond) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        collectSheets(cond, out);
        return List.copyOf(out);
    }

    private static void collectSheets(RuleValue cond, LinkedHashSet<String> out) {
        if (cond == null || !truthy(cond) || !(cond instanceof RuleValue.MapValue m)) {
            return;
        }
        for (String key : List.of("all", "any")) {
            RuleValue items = m.entries().get(key);
            if (items != null && truthy(items)) {
                for (RuleValue c : list(items)) {
                    collectSheets(c, out);
                }
            }
        }
        RuleValue not = m.entries().get("not");
        if (not != null && truthy(not)) {
            collectSheets(not, out);
        }
        List<RuleValue> named = new ArrayList<>();
        named.add(m.entries().get("sheet"));
        for (String op : SHEET_OPS) {
            named.add(m.entries().get(op));
        }
        for (RuleValue s : named) {
            if (s != null && s != RuleValue.Null.NULL) {
                out.add(PyText.str(s));
            }
        }
    }

    /** describe_condition: a short phrase for a condition tree. */
    public static String describeCondition(RuleValue cond) {
        if (cond == null || !truthy(cond) || !(cond instanceof RuleValue.MapValue mv)) {
            return "";
        }
        Map<String, RuleValue> c = mv.entries();
        if (c.containsKey("all")) {
            return String.join(" and ", list(c.get("all")).stream().map(x -> "(" + describeCondition(x) + ")").toList());
        }
        if (c.containsKey("any")) {
            return String.join(" or ", list(c.get("any")).stream().map(x -> "(" + describeCondition(x) + ")").toList());
        }
        if (c.containsKey("not")) {
            return "not (" + describeCondition(c.get("not")) + ")";
        }
        String s = PyText.str(c.getOrDefault("sheet", RuleValue.Null.NULL));
        if (c.containsKey("present")) {
            return s + (truthy(c.get("present")) ? " present" : " absent");
        }
        String[][] scalar = {{"eq", "="}, {"ne", "≠"}, {"has", "∋"}, {"hasnt", "∌"}, {"eqSheet", "="}, {"neSheet", "≠"},
                {"ge", "≥"}, {"gt", ">"}, {"le", "≤"}, {"lt", "<"}, {"geSheet", "≥"}, {"gtSheet", ">"}, {"leSheet", "≤"},
                {"ltSheet", "<"}, {"subOf", "⊆"}, {"supOf", "⊇"}};
        for (String[] op : scalar) {
            if (c.containsKey(op[0])) {
                return s + op[1] + PyText.str(c.get(op[0]));
            }
        }
        String[][] sets = {{"in", "∈"}, {"nin", "∉"}, {"hasAny", "∩"}};
        for (String[] op : sets) {
            if (c.containsKey(op[0])) {
                return s + op[1] + "{" + String.join(", ", list(c.get(op[0])).stream().map(PyText::str).toList()) + "}";
            }
        }
        String[][] counts = {{"count", "="}, {"countGe", "≥"}, {"countLe", "≤"}};
        for (String[] op : counts) {
            if (c.containsKey(op[0])) {
                return "|" + s + "|" + op[1] + PyText.str(c.get(op[0]));
            }
        }
        return "";
    }

    /** sieve.describe: a constraint as a sentence. */
    public static String describe(RuleValue constraint) {
        Map<String, RuleValue> c = map(constraint);
        String polarityText = c.containsKey("polarity") ? PyText.str(c.get("polarity")) : null;
        if (c.containsKey("desc") && truthy(c.get("desc"))) {
            return ("forbid".equals(polarityText == null ? "forbid" : polarityText) ? "❌ " : "✅ ") + PyText.str(c.get("desc"));
        }
        Map<String, RuleValue> gate = c.get("gate") instanceof RuleValue.MapValue g ? g.entries() : Map.of();
        String where = gate.containsKey("adjacent") && truthy(gate.get("adjacent")) ? " when adjacent"
                : gate.containsKey("within") ? " when within " + PyText.str(gate.get("within")) : " together";
        String ctx = describeCondition(c.get("condition"));
        String condText = ctx.isEmpty() ? "" : "  [only when " + ctx + "]";
        if (c.containsKey("assert") && c.get("assert") != RuleValue.Null.NULL) {
            String pol = "require".equals(polarityText == null ? "require" : polarityText) ? "Require" : "Forbid";
            return pol + ": " + describeCondition(c.get("assert")) + condText;
        }
        String pol = "forbid".equals(polarityText == null ? "forbid" : polarityText) ? "Forbid" : "Require";
        if (c.containsKey("mapping") && truthy(c.get("mapping"))) {
            Map<String, RuleValue> m = map(c.get("mapping"));
            List<String> parts = new ArrayList<>();
            Map<String, RuleValue> allow = m.get("allow") instanceof RuleValue.MapValue a ? a.entries() : Map.of();
            allow.forEach((key, values) -> parts.add(PyText.str(m.get("source")) + "=" + key + "→" + PyText.str(m.get("target")) + "∈{"
                    + String.join(", ", list(values).stream().map(PyText::str).toList()) + "}"));
            return "Map " + String.join("; ", parts) + condText;
        }
        if (c.containsKey("when")) {
            String sheets = String.join("/", list(c.getOrDefault("sheets", new RuleValue.ListValue(List.of()))).stream().map(PyText::str).toList());
            return pol + " " + sheets + where + ": " + PyText.str(c.get("when")) + condText;
        }
        if (c.containsKey("sets") && truthy(c.get("sets"))) {
            List<String> parts = new ArrayList<>();
            map(c.get("sets")).forEach((sheet, values) -> parts.add(sheet + "∈{" + String.join(", ", list(values).stream().map(PyText::str)
                    .toList()) + "}"));
            return pol + where + ": " + String.join(" × ", parts) + condText;
        }
        List<String> pairs = new ArrayList<>();
        for (RuleValue e : c.containsKey("pairs") ? list(c.get("pairs")) : List.<RuleValue>of()) {
            List<String> kv = new ArrayList<>();
            map(e).forEach((k, v) -> kv.add(k + ":" + PyText.str(v)));
            pairs.add(String.join("+", kv));
        }
        return pol + where + ": " + String.join(", ", pairs) + condText;
    }

    // ---- JSON ------------------------------------------------------------------------------------------------------

    /** A sidecar's JSON text as values, keys in order; a repeated key is refused as the reader refuses it. */
    public static RuleValue parse(String json, String origin) {
        try (JsonParser parser = new com.fasterxml.jackson.core.JsonFactory().createParser(json)) {
            if (parser.nextToken() == null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE, "the constraint sidecar is empty", SourceLocation.of(origin)));
            }
            List<Diagnostic> twice = new ArrayList<>();
            DocTree tree = DocTree.readJson(parser, origin, "", (key, at) -> twice.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID,
                    "the key \"" + key + "\" appears twice in one object", at)));
            if (!twice.isEmpty()) {
                throw new FrameworkException(twice);
            }
            return DocTree.value(tree);
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE, "the constraint sidecar is not valid JSON: "
                    + e.getMessage(), SourceLocation.of(origin)));
        }
    }

    /** json.dumps(value, ensure_ascii, sort_keys) with Python's default separators ", " and ": ". */
    public static String pythonJson(RuleValue v, boolean sortKeys, boolean ensureAscii) {
        StringBuilder out = new StringBuilder();
        appendPython(out, v, sortKeys, ensureAscii, -1, 0);
        return out.toString();
    }

    /** The sidecar as a file: two-space indented JSON, non-ASCII kept, keys in order (the legacy editor's JSON.stringify(s, null, 2)). */
    public static String pretty(RuleValue v) {
        StringBuilder out = new StringBuilder();
        appendPython(out, v, false, false, 2, 0);
        return out.append('\n').toString();
    }

    private static void appendPython(StringBuilder out, RuleValue v, boolean sortKeys, boolean ensureAscii, int indent, int depth) {
        switch (v) {
            case RuleValue.ListValue l -> {
                if (l.items().isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append('[');
                for (int i = 0; i < l.items().size(); i++) {
                    out.append(i == 0 ? "" : indent < 0 ? ", " : ",");
                    newline(out, indent, depth + 1);
                    appendPython(out, l.items().get(i), sortKeys, ensureAscii, indent, depth + 1);
                }
                newline(out, indent, depth);
                out.append(']');
            }
            case RuleValue.MapValue m -> {
                if (m.entries().isEmpty()) {
                    out.append("{}");
                    return;
                }
                List<String> keys = new ArrayList<>(m.entries().keySet());
                if (sortKeys) {
                    keys.sort(PyText.CODE_POINT_ORDER);
                }
                out.append('{');
                for (int i = 0; i < keys.size(); i++) {
                    out.append(i == 0 ? "" : indent < 0 ? ", " : ",");
                    newline(out, indent, depth + 1);
                    string(out, keys.get(i), ensureAscii);
                    out.append(": ");
                    appendPython(out, m.entries().get(keys.get(i)), sortKeys, ensureAscii, indent, depth + 1);
                }
                newline(out, indent, depth);
                out.append('}');
            }
            case RuleValue.Text t -> string(out, t.value(), ensureAscii);
            default -> DocTree.appendJson(out, v);
        }
    }

    private static void newline(StringBuilder out, int indent, int depth) {
        if (indent >= 0) {
            out.append('\n').append(" ".repeat(indent * depth));
        }
    }

    private static void string(StringBuilder out, String s, boolean ensureAscii) {
        if (ensureAscii) {
            DocTree.appendJsonString(out, s);
            return;
        }
        out.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    // ---- Python value helpers --------------------------------------------------------------------------------------

    private static Map<String, RuleValue> map(RuleValue v) {
        return v instanceof RuleValue.MapValue m ? m.entries() : Map.of();
    }

    private static List<RuleValue> list(RuleValue v) {
        return v instanceof RuleValue.ListValue l ? l.items() : List.of();
    }

    private static RuleValue texts(List<String> values) {
        return new RuleValue.ListValue(values.stream().<RuleValue>map(RuleValue.Text::new).toList());
    }

    private static RuleValue orNull(RuleValue v) {
        return v == null ? RuleValue.Null.NULL : v;
    }

    private static RuleValue orEmpty(RuleValue v) {
        return v == null || !truthy(v) ? EMPTY : v;
    }

    private static RuleValue absentIfNull(RuleValue v) {
        return v == null || v == RuleValue.Null.NULL ? null : v;
    }

    /** Python truthiness of a JSON value. */
    static boolean truthy(RuleValue v) {
        return switch (v) {
            case RuleValue.Null n -> false;
            case RuleValue.Bool b -> b.value();
            case RuleValue.Int i -> i.value().signum() != 0;
            case RuleValue.Real r -> r.value() != 0.0;
            case RuleValue.Text t -> !t.value().isEmpty();
            case RuleValue.ListValue l -> !l.items().isEmpty();
            case RuleValue.MapValue m -> !m.entries().isEmpty();
        };
    }
}
