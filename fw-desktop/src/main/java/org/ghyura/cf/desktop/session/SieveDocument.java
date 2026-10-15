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

import org.ghyura.cf.input.ConstraintBonds;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.rule.RuleValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An immutable execution sidecar. Drawing and editing never regroup rules or coerce JSON value types. */
public record SieveDocument(RuleValue.MapValue root) {
    public static final int MAX_BYTES = 16 * 1024 * 1024;

    public static SieveDocument empty() {
        return new SieveDocument(object("version", RuleValue.Int.of(1), "constraints", new RuleValue.ListValue(List.of())));
    }

    /** Structural errors are refused; broken expressions may be opened and repaired, but cannot be saved. */
    public static SieveDocument parse(String json, String origin) {
        if (json.length() > MAX_BYTES) throw new IllegalArgumentException("The interactive Sieve document limit is 16 MiB.");
        InputSource source = InputSource.ofText(origin, json);
        try {
            ConstraintSidecarReader.read(source);
        } catch (FrameworkException e) {
            if (e.diagnostics().stream().anyMatch(d -> d.code() != ErrorCode.CONSTRAINT_EXPRESSION)) throw e;
        }
        return new SieveDocument((RuleValue.MapValue) ConstraintBonds.parse(json, origin));
    }

    public String json() { return ConstraintBonds.pretty(root) + "\n"; }

    public List<RuleValue.MapValue> rules() {
        return list(root.entries().get("constraints")).stream().map(v -> (RuleValue.MapValue) v).toList();
    }

    public SieveDocument replace(int index, RuleValue.MapValue rule) {
        List<RuleValue> rules = new ArrayList<>(rules());
        if (index < 0) rules.add(rule); else rules.set(index, rule);
        return field("constraints", new RuleValue.ListValue(rules));
    }

    public SieveDocument remove(int index) {
        List<RuleValue> rules = new ArrayList<>(rules());
        rules.remove(index);
        return field("constraints", new RuleValue.ListValue(rules));
    }

    public SieveDocument move(int index, int destination) {
        List<RuleValue> rules = new ArrayList<>(rules());
        RuleValue value = rules.remove(index);
        rules.add(destination, value);
        return field("constraints", new RuleValue.ListValue(rules));
    }

    public SieveDocument field(String key, RuleValue value) {
        Map<String, RuleValue> entries = new LinkedHashMap<>(root.entries());
        if (value == null) entries.remove(key); else entries.put(key, value);
        return new SieveDocument(new RuleValue.MapValue(entries));
    }

    public String nextId() {
        Set<String> ids = new LinkedHashSet<>();
        rules().forEach(r -> ids.add(text(r.entries().get("id"))));
        for (int n = 1; ; n++) if (!ids.contains("sieve-" + n)) return "sieve-" + n;
    }

    public List<String> parameterNames(String sheet) {
        Set<String> names = new LinkedHashSet<>();
        map(map(root.entries().get("params")).get(sheet)).values().forEach(v -> names.addAll(map(v).keySet()));
        names.add("value"); names.add("pos");
        return List.copyOf(names);
    }

    /** Conservative exact conflicts only: never conflate text with numbers, different assertions or combined gates. */
    public List<String> contradictions() {
        Map<String, Map<String, String>> seen = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();
        int index = 0;
        for (var rule : rules()) {
            if (kind(rule).equals("mapping")) { index++; continue; } // Mapping polarity is ignored by the engine.
            var meaning = new LinkedHashMap<>(rule.entries());
            String name = label(rule, index++);
            String polarity = text(meaning.getOrDefault("polarity", new RuleValue.Text(kind(rule).equals("assert") ? "require" : "forbid")));
            meaning.remove("id"); meaning.remove("desc"); meaning.remove("polarity");
            String key = ConstraintBonds.pythonJson(new RuleValue.MapValue(meaning), true, false);
            var polarities = seen.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            String other = polarities.get(polarity.equals("forbid") ? "require" : "forbid");
            if (other != null) conflicts.add(other + " ↔ " + name);
            polarities.putIfAbsent(polarity, name);
        }
        return List.copyOf(conflicts);
    }

    public static String kind(RuleValue.MapValue rule) {
        for (String key : List.of("pairs", "sets", "when", "mapping", "assert"))
            if (rule.entries().containsKey(key)) return key;
        return "unknown";
    }

    public static String label(RuleValue.MapValue rule, int index) {
        String id = text(rule.entries().get("id"));
        return id.isBlank() ? "Unnamed rule " + (index + 1) : id;
    }

    public static Set<String> sheets(RuleValue.MapValue rule) {
        Set<String> sheets = new LinkedHashSet<>();
        list(rule.entries().get("sheets")).forEach(v -> sheets.add(text(v)));
        sheets.addAll(map(rule.entries().get("sets")).keySet());
        list(rule.entries().get("pairs")).forEach(v -> sheets.addAll(map(v).keySet()));
        Map<String, RuleValue> mapping = map(rule.entries().get("mapping"));
        if (!mapping.isEmpty()) {
            sheets.add(text(mapping.get("source")));
            sheets.add(text(mapping.get("target")));
        }
        sheets.addAll(ConstraintBonds.conditionSheets(rule.entries().get("condition")));
        sheets.addAll(ConstraintBonds.conditionSheets(rule.entries().get("assert")));
        sheets.remove("");
        return sheets;
    }

    public static RuleValue.MapValue object(Object... fields) {
        Map<String, RuleValue> entries = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) entries.put((String) fields[i], (RuleValue) fields[i + 1]);
        return new RuleValue.MapValue(entries);
    }

    public static Map<String, RuleValue> map(RuleValue value) {
        return value instanceof RuleValue.MapValue m ? m.entries() : Map.of();
    }

    public static List<RuleValue> list(RuleValue value) {
        return value instanceof RuleValue.ListValue l ? l.items() : List.of();
    }

    public static String text(RuleValue value) { return value instanceof RuleValue.Text t ? t.value() : ""; }

    public static RuleValue.ListValue texts(List<String> values) {
        return new RuleValue.ListValue(values.stream().map(v -> (RuleValue) new RuleValue.Text(v)).toList());
    }
}
