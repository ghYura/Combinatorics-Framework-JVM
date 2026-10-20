// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
package org.ghyura.cf.desktop.session;

import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.model.rule.RuleValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.ghyura.cf.desktop.session.SieveDocument.*;

/** Adapts the original QA web editor to the JVM document without regrouping independent rules.
 * The browser sends changes to its projection, so untouched integers, IDs, order and metadata
 * remain in Java, including integers that JavaScript cannot represent exactly. */
public final class Face2Document {
    private static final String RESOURCE = "/org/ghyura/cf/desktop/face2/";
    private static final Set<String> BOND_FIELDS = Set.of("polarity", "sides", "gate", "when", "condition", "mapping", "assert", "desc");
    private final SieveDocument original;
    private final List<ConstraintBonds.Bond> originals = new ArrayList<>();
    private final List<List<Integer>> members = new ArrayList<>();

    public Face2Document(SieveDocument original) {
        this.original = original;
        for (var rule : original.rules()) {
            List<Integer> indices = new ArrayList<>();
            for (var bond : ConstraintBonds.read(object("constraints", new RuleValue.ListValue(List.of(rule)))).bonds()) {
                indices.add(originals.size()); originals.add(bond);
            }
            members.add(List.copyOf(indices));
        }
    }

    /** Original template with its data slots, transport adapter and a separate presentation layer. */
    public String html(Map<String, List<String>> sheets, Set<String> optional, String base) {
        return html(sheets, optional, base, 0);
    }

    public String html(Map<String, List<String>> sheets, Set<String> optional, String base, long revision) {
        List<RuleValue> sheetRows = new ArrayList<>();
        BigInteger full = BigInteger.ONE;
        for (var entry : sheets.entrySet()) {
            sheetRows.add(new RuleValue.ListValue(List.of(new RuleValue.Text(entry.getKey()), texts(entry.getValue()))));
            full = full.multiply(BigInteger.valueOf(Math.max(1, entry.getValue().size())));
        }
        var data = object("sheets", new RuleValue.ListValue(sheetRows),
                "params", original.root().entries().getOrDefault("params", object()),
                "orders", original.root().entries().getOrDefault("orders", object()),
                "bonds", new RuleValue.ListValue(originals.stream().map(b -> (RuleValue) b.toValue()).toList()),
                "optional_sheets", texts(optional.stream().sorted().toList()), "full", new RuleValue.Int(full),
                "lang", new RuleValue.Text("en"), "post", new RuleValue.Text(base + "submit"),
                "impact", new RuleValue.Text(base + "impact"), "jvm", new RuleValue.Text(base), "jvmRevision", RuleValue.Int.of(revision));
        String json = ConstraintBonds.pretty(data).replace("&", "\\u0026").replace("<", "\\u003c").replace(">", "\\u003e");
        return resource("editor.html").replace("__TITLE__", "Face 2 (Sieve) · Combinatorics Framework")
                .replace("/*__DATA__*/null", json)
                .replace("</head>", "<style>\n" + resource("visibility.css") + "\n</style></head>")
                .replace("</body></html>", "<script>\n" + resource("bridge.js") + "\n</script><script>\n"
                        + resource("visibility.js") + "\n</script></body></html>");
    }

    /** Apply a browser delta to the authoritative Java snapshot. Semantic validation is the Session's job. */
    public SieveDocument apply(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > SieveDocument.MAX_BYTES)
            throw new IllegalArgumentException("The Face 2 document limit is 16 MiB.");
        Map<String, RuleValue> request = map(ConstraintBonds.parse(json, "Face 2 changes"));
        if (!RuleValue.Int.of(1).equals(request.get("version")) || !(request.get("edits") instanceof RuleValue.ListValue))
            throw new IllegalArgumentException("Expected Face 2 changes, version 1.");
        Map<Integer, ConstraintBonds.Bond> remaining = new LinkedHashMap<>();
        List<ConstraintBonds.Bond> added = new ArrayList<>();
        Set<Integer> changed = new HashSet<>();
        for (RuleValue value : list(request.get("edits"))) {
            Map<String, RuleValue> edit = map(value);
            if (edit.get("source") instanceof RuleValue.Int source) {
                int index = source.value().intValueExact();
                if (index < 0 || index >= originals.size() || remaining.containsKey(index))
                    throw new IllegalArgumentException("Invalid or repeated original bond: " + index);
                var before = originals.get(index);
                RuleValue after = patch(before.toValue(), edit.get("patch"), true);
                var bond = bond(after);
                remaining.put(index, bond);
                if (!bond.equals(before)) changed.add(index);
            } else if (edit.get("bond") instanceof RuleValue.MapValue newBond) {
                added.add(bond(newBond));
            } else throw new IllegalArgumentException("A Face 2 edit needs a source or a new bond.");
        }
        List<RuleValue> rules = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            List<Integer> indices = members.get(i);
            var rule = original.rules().get(i);
            if (indices.stream().allMatch(n -> remaining.containsKey(n) && !changed.contains(n))) {
                rules.add(rule); // Preserve the exact authored representation of untouched rules.
                continue;
            }
            List<ConstraintBonds.Bond> kept = indices.stream().filter(remaining::containsKey).map(remaining::get).toList();
            if (kept.isEmpty()) continue;
            var converted = converted(kept);
            if (converted.size() != 1) throw new IllegalArgumentException("The links of rule '" + label(rule, i)
                    + "' must keep the same type, gate and condition. Edit that rule together in the JVM rule editor; splitting it could change its meaning.");
            Map<String, RuleValue> fields = new LinkedHashMap<>(rule.entries());
            for (String key : List.of("pairs", "sets", "when", "mapping", "assert", "sheets", "gate", "condition", "polarity")) fields.remove(key);
            fields.putAll(converted.getFirst().entries());
            for (String key : List.of("id", "desc")) {
                if (rule.entries().containsKey(key)) fields.put(key, rule.entries().get(key)); else fields.remove(key);
            }
            var oldSheets = rule.entries().get("sheets");
            if (oldSheets != null && new HashSet<>(list(oldSheets)).equals(new HashSet<>(list(fields.get("sheets"))))) fields.put("sheets", oldSheets);
            // Retain the authored encoding of an unchanged gate.
            if (rule.entries().containsKey("gate") && kept.stream().allMatch(b -> b.gate().equals(originals.get(indices.getFirst()).gate())))
                fields.put("gate", rule.entries().get("gate"));
            rules.add(new RuleValue.MapValue(fields));
        }
        Set<String> ids = new HashSet<>(); original.rules().forEach(r -> ids.add(text(r.entries().get("id"))));
        int nextId = 1;
        // Each newly drawn link is an independent rule. In particular, never OR two independent require rules.
        for (var bond : added) {
            for (var rule : converted(List.of(bond))) {
                while (ids.contains("face2-" + nextId)) nextId++;
                var fields = new LinkedHashMap<>(rule.entries());
                fields.put("id", new RuleValue.Text("face2-" + nextId++));
                rules.add(new RuleValue.MapValue(fields));
            }
        }
        var result = original.field("constraints", new RuleValue.ListValue(rules));
        if (!list(request.get("paramsPatch")).isEmpty()) result = result.field("params",
                patch(original.root().entries().getOrDefault("params", object()), request.get("paramsPatch"), false));
        return SieveDocument.parse(result.json(), "Face 2 submission");
    }

    private static List<RuleValue.MapValue> converted(List<ConstraintBonds.Bond> bonds) {
        var converted = list(ConstraintBonds.write(bonds, object(), null).entries().get("constraints")).stream()
                .map(v -> (RuleValue.MapValue) v).toList();
        var groups = ConstraintBonds.members(bonds);
        List<RuleValue.MapValue> result = new ArrayList<>();
        for (int i = 0; i < converted.size(); i++) {
            var rule = converted.get(i);
            if (rule.entries().containsKey("assert")) {
                // The QA exporter omits these assertion fields. Preserve the submitted data;
                // whether its gate affects execution remains the existing JVM engine's decision.
                var source = bonds.get(groups.get(i).getFirst());
                var fields = new LinkedHashMap<>(rule.entries());
                fields.put("gate", source.gate());
                if (!source.sides().isEmpty()) fields.put("sheets", texts(List.copyOf(source.sides().keySet())));
                rule = new RuleValue.MapValue(fields);
            }
            result.add(rule);
        }
        return List.copyOf(result);
    }

    private static ConstraintBonds.Bond bond(RuleValue value) {
        var m = map(value);
        String polarity = text(m.get("polarity"));
        if (!Set.of("forbid", "require").contains(polarity) || !(m.get("sides") instanceof RuleValue.MapValue))
            throw new IllegalArgumentException("A bond needs forbid/require and its sheet sides.");
        Map<String, List<RuleValue>> sides = new LinkedHashMap<>();
        map(m.get("sides")).forEach((key, values) -> {
            if (!(values instanceof RuleValue.ListValue)) throw new IllegalArgumentException("Bond sides must be arrays.");
            sides.put(key, list(values));
        });
        if (nullable(m.get("when")) == null && nullable(m.get("mapping")) == null && nullable(m.get("assert")) == null
                && (sides.isEmpty() || sides.values().stream().anyMatch(List::isEmpty)))
            throw new IllegalArgumentException("A value link needs nonempty sides.");
        return new ConstraintBonds.Bond(polarity, sides, nullable(m.get("gate")), nullable(m.get("when")),
                nullable(m.get("condition")), nullable(m.get("mapping")), nullable(m.get("assert")), text(m.get("desc")));
    }

    private static RuleValue nullable(RuleValue value) { return value == RuleValue.Null.NULL ? null : value; }

    private static RuleValue patch(RuleValue start, RuleValue operations, boolean bond) {
        RuleValue result = start;
        if (!(operations instanceof RuleValue.ListValue)) throw new IllegalArgumentException("Expected an array of changes.");
        for (var operation : list(operations)) {
            var m = map(operation);
            List<String> path = list(m.get("path")).stream().map(v -> {
                if (!(v instanceof RuleValue.Text t)) throw new IllegalArgumentException("A change path must contain text keys.");
                return t.value();
            }).toList();
            if (path.isEmpty() || path.size() > 64 || bond && !BOND_FIELDS.contains(path.getFirst()))
                throw new IllegalArgumentException("Invalid change path.");
            boolean remove = new RuleValue.Bool(true).equals(m.get("remove"));
            if (!remove && !m.containsKey("value")) throw new IllegalArgumentException("A change needs a value.");
            result = replace(result, path, 0, remove ? null : m.get("value"));
        }
        return result;
    }

    private static RuleValue replace(RuleValue current, List<String> path, int depth, RuleValue value) {
        if (!(current instanceof RuleValue.MapValue)) throw new IllegalArgumentException("Change path is not an object.");
        var fields = new LinkedHashMap<>(map(current)); String key = path.get(depth);
        if (depth + 1 < path.size()) value = replace(fields.get(key), path, depth + 1, value);
        if (value == null) fields.remove(key); else fields.put(key, value);
        return new RuleValue.MapValue(fields);
    }

    private static String resource(String name) {
        try (var in = Face2Document.class.getResourceAsStream(RESOURCE + name)) {
            if (in == null) throw new IllegalStateException("Missing Face 2 resource: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
