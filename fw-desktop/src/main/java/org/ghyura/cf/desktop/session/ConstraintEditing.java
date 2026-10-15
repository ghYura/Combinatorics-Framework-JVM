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
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.RuleValue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A constraint sidecar being edited (GEN:operator-authoring-ui; the legacy constraints/editor.py and bond matrix): its
 * bonds, params and orders, the bond matrix's view of the plain pair bonds between two sheets under one gate, the
 * canonical sidecar they write and the contradictions that keep it from being saved, as the legacy editor refused to
 * submit them. Not thread-safe: one view edits it on its own thread.
 */
public final class ConstraintEditing {

    private final List<ConstraintBonds.Bond> bonds = new ArrayList<>();
    private RuleValue params;
    private final RuleValue orders;

    private ConstraintEditing(ConstraintBonds.Bonds read, RuleValue params) {
        bonds.addAll(read.bonds());
        this.params = params;
        this.orders = read.orders();
    }

    /** An independent copy, to try an edit on. */
    public ConstraintEditing copy() {
        return new ConstraintEditing(new ConstraintBonds.Bonds(bonds, orders), params);
    }

    /** The sidecar text being edited (an empty text starts an empty sidecar). */
    public static ConstraintEditing of(String json, String origin) {
        if (json.isBlank()) {
            return new ConstraintEditing(new ConstraintBonds.Bonds(List.of(), new RuleValue.MapValue(Map.of())), null);
        }
        RuleValue doc = ConstraintBonds.parse(json, origin);
        RuleValue params = doc instanceof RuleValue.MapValue m ? m.entries().get("params") : null;
        return new ConstraintEditing(ConstraintBonds.read(doc), params);
    }

    /** The scenario's companion sidecar, or an empty one when it has none. */
    public static ConstraintEditing open(Path scenario) {
        return open(scenario, InputSource::read);
    }

    /** The companion as editable data, with its bytes admitted by the owning session before parsing. */
    public static ConstraintEditing open(Path scenario, InputSource.Reader inputs) {
        Path companion = ConstraintSidecarReader.companionOf(scenario);
        if (!Files.isRegularFile(companion)) {
            return of("", companion.toString());
        }
        try {
            InputSource source = inputs.read(companion, InputSource.DEFAULT_MAX_BYTES);
            try {
                ConstraintSidecarReader.read(source);
            } catch (FrameworkException e) {
                // The editor retains formula text for repair. Other malformed structures may lose data when converted
                // to bonds, so do not offer to rewrite them through this editor.
                if (e.diagnostics().stream().anyMatch(d -> d.code() != ErrorCode.CONSTRAINT_EXPRESSION)) {
                    throw e;
                }
            }
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(
                    source.copyOfBytes())).toString();
            return of(json, companion.toString());
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE, "the constraint sidecar cannot be read: "
                    + e.getMessage(), SourceLocation.of(companion.toString())));
        }
    }

    public List<ConstraintBonds.Bond> bonds() {
        return List.copyOf(bonds);
    }

    /** The canonical sidecar of the bonds, as the legacy editor wrote it (its params as the page's cleanParams left them). */
    public RuleValue.MapValue sidecar() {
        return ConstraintBonds.write(bonds, cleanParams(params), orders);
    }

    /**
     * The legacy page's cleanParams: the values whose parameters are empty left out, then the sheets left without values, in
     * the order the page walked them (array-index keys first, ascending; JSON.stringify kept that order at every level).
     */
    static RuleValue cleanParams(RuleValue params) {
        if (!(params instanceof RuleValue.MapValue all)) {
            return params;
        }
        Map<String, RuleValue> out = new java.util.LinkedHashMap<>();
        for (String s : FormulaBlocks.jsKeys(all.entries().keySet())) {
            if (!(all.entries().get(s) instanceof RuleValue.MapValue values)) {
                continue;
            }
            Map<String, RuleValue> kept = new java.util.LinkedHashMap<>();
            for (String v : FormulaBlocks.jsKeys(values.entries().keySet())) {
                RuleValue p = values.entries().get(v);
                switch (p) {
                    case RuleValue.MapValue pm when !pm.entries().isEmpty() -> {
                        Map<String, RuleValue> ordered = new java.util.LinkedHashMap<>();
                        FormulaBlocks.jsKeys(pm.entries().keySet()).forEach(k -> ordered.put(k, pm.entries().get(k)));
                        kept.put(v, new RuleValue.MapValue(ordered));
                    }
                    case RuleValue.Text t when !t.value().isEmpty() -> kept.put(v, p);
                    case RuleValue.ListValue l when !l.items().isEmpty() -> kept.put(v, p);
                    default -> { }
                }
            }
            if (!kept.isEmpty()) {
                out.put(s, new RuleValue.MapValue(kept));
            }
        }
        return new RuleValue.MapValue(out);
    }

    /** The sidecar as its file holds it. */
    public String json() {
        return ConstraintBonds.pretty(sidecar());
    }

    public List<ConstraintBonds.Contradiction> contradictions() {
        return ConstraintBonds.contradictions(bonds);
    }

    /** {@code {}} for any position, {@code {"adjacent": true}}, or {@code {"within": n}}. */
    public static RuleValue gate(String kind, int within) {
        return switch (kind) {
            case "adjacent" -> new RuleValue.MapValue(Map.of("adjacent", new RuleValue.Bool(true)));
            case "within" -> new RuleValue.MapValue(Map.of("within", new RuleValue.Int(BigInteger.valueOf(within))));
            default -> new RuleValue.MapValue(Map.of());
        };
    }

    /** The polarity of the plain pair bond between value a of sheet A and value b of sheet B under the gate, or null. */
    public String pair(String sheetA, String a, String sheetB, String b, RuleValue gate) {
        int i = find(sheetA, a, sheetB, b, gate);
        return i < 0 ? null : bonds.get(i).polarity();
    }

    /** Sets the plain pair bond to a polarity, or removes it with null; a new bond goes after the others. */
    public void setPair(String sheetA, String a, String sheetB, String b, RuleValue gate, String polarity) {
        if (sheetA.equals(sheetB)) {
            throw new IllegalArgumentException("a bond joins two different sheets");
        }
        int i = find(sheetA, a, sheetB, b, gate);
        if (polarity == null) {
            if (i >= 0) {
                bonds.remove(i);
            }
            return;
        }
        ConstraintBonds.Bond bond = ConstraintBonds.Bond.pair(polarity, sheetA, a, sheetB, b, gate);
        if (i >= 0) {
            bonds.set(i, bond);
        } else {
            bonds.add(bond);
        }
    }

    /** Removes the bond at an index of {@link #bonds()}. */
    public void remove(int index) {
        bonds.remove(index);
    }

    // ---- rules -------------------------------------------------------------------------------------------------------

    /** A constraint of the sidecar as it is written, its kind (pairs, sets, when, mapping, assert) and the bonds it comes from. */
    public record Rule(String id, String kind, List<Integer> bonds) {
        public Rule {
            bonds = List.copyOf(bonds);
        }
    }

    /** The sidecar's constraints in its order, each with the bonds it comes from (a group of pairs, else one bond). */
    public List<Rule> rules() {
        List<List<Integer>> members = ConstraintBonds.members(bonds);
        List<RuleValue> written = ((RuleValue.ListValue) sidecar().entries().get("constraints")).items();
        List<Rule> out = new ArrayList<>();
        for (int i = 0; i < written.size(); i++) {
            Map<String, RuleValue> c = ((RuleValue.MapValue) written.get(i)).entries();
            String kind = c.containsKey("assert") ? "assert" : c.containsKey("mapping") ? "mapping" : c.containsKey("when") ? "when"
                    : c.containsKey("sets") ? "sets" : "pairs";
            out.add(new Rule(text(c.get("id")), kind, members.get(i)));
        }
        return out;
    }

    /** The index in {@link #rules()} of the rule a bond belongs to. */
    public int ruleOf(int bond) {
        List<Rule> all = rules();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).bonds().contains(bond)) {
                return i;
            }
        }
        throw new IllegalArgumentException("no bond " + bond);
    }

    /**
     * What the rule editor changes of a rule: the polarity, gate and condition of any rule, the sheets and predicate of a
     * when rule, the tree of an assert rule; a rule of another kind ignores the fields it does not have.
     *
     * @param condition the "only when" tree, null for none
     */
    public record Draft(String polarity, RuleValue gate, RuleValue condition, List<String> sheets, String when, RuleValue assertion) {
        public Draft {
            sheets = sheets == null ? List.of() : List.copyOf(sheets);
        }
    }

    /** A rule as the editor starts from it: its first bond's polarity, gate, condition, sheets, predicate and tree. */
    public Draft draft(int rule) {
        ConstraintBonds.Bond b = bonds.get(rules().get(rule).bonds().getFirst());
        return new Draft(b.polarity(), b.gate(), b.condition(), List.copyOf(b.sides().keySet()),
                b.when() instanceof RuleValue.Text t ? t.value() : null, b.assertion());
    }

    /** The legacy editor's BAN: what its formula fields refused as an unsafe formula. */
    public static final java.util.regex.Pattern BAN = java.util.regex.Pattern.compile(
            "__|import|lambda|:=|=>|;|\\bexec\\b|\\beval\\b|\\bopen\\b|\\bglobals\\b|\\blocals\\b|\\bwhile\\b|\\bfor\\b");

    /**
     * Applies a draft to every bond of a rule, in place as the legacy editor changed a selected bond, refusing what its
     * dialogs refused: fewer than two sheets or no predicate for a when rule, an unsafe predicate, an assert without a
     * condition. The predicate is kept trimmed; an assert rule's sides follow its tree's sheets, as reading it back gives.
     *
     * @return the index of the rule afterwards (rules regroup, so it may move)
     * @throws IllegalArgumentException with the legacy message for what the dialogs refused
     */
    public int apply(int rule, Draft draft) {
        Rule r = rules().get(rule);
        validate(r.kind(), draft);
        for (int i : r.bonds()) {
            bonds.set(i, changed(bonds.get(i), r.kind(), draft));
        }
        return ruleOf(r.bonds().getFirst());
    }

    /** Adds a formula rule, as the legacy formula dialog did: two sheets or more and a predicate over their parameters. */
    public int addWhen(List<String> sheets, String polarity, RuleValue gate, String when) {
        Draft d = new Draft(polarity, gate, null, sheets, when, null);
        validate("when", d);
        bonds.add(changed(new ConstraintBonds.Bond(polarity, Map.of(), gate, null, null, null, null, ""), "when", d));
        return ruleOf(bonds.size() - 1);
    }

    /** Adds an assert rule, as the legacy relation dialog did: a condition tree that must hold (require) or must not (forbid). */
    public int addAssert(String polarity, RuleValue assertion) {
        Draft d = new Draft(polarity, null, null, null, null, assertion);
        validate("assert", d);
        bonds.add(changed(new ConstraintBonds.Bond(polarity, Map.of(), null, null, null, null, null, ""), "assert", d));
        return ruleOf(bonds.size() - 1);
    }

    /** Removes a rule: every bond it comes from. */
    public void removeRule(int rule) {
        List<Integer> members = new ArrayList<>(rules().get(rule).bonds());
        members.sort(java.util.Comparator.reverseOrder());
        for (int i : members) {
            bonds.remove(i);
        }
    }

    private static void validate(String kind, Draft d) {
        if (!"forbid".equals(d.polarity()) && !"require".equals(d.polarity())) {
            throw new IllegalArgumentException("the polarity is forbid or require, not " + d.polarity());
        }
        if (kind.equals("when")) {
            String when = d.when() == null ? "" : d.when().strip();
            if (d.sheets().size() < 2) {
                throw new IllegalArgumentException("Pick ≥2 sheets");
            }
            if (when.isEmpty()) {
                throw new IllegalArgumentException("Enter a formula");
            }
            if (BAN.matcher(when).find()) {
                throw new IllegalArgumentException("Unsafe formula");
            }
        }
        if (kind.equals("assert") && (d.assertion() == null || ConstraintBonds.conditionSheets(d.assertion()).isEmpty())) {
            throw new IllegalArgumentException("Add at least one condition");
        }
    }

    private static ConstraintBonds.Bond changed(ConstraintBonds.Bond b, String kind, Draft d) {
        RuleValue condition = d.condition() == null || ConditionBlocks.kind(d.condition()).equals("always") ? null : d.condition();
        RuleValue gate = d.gate() == null ? b.gate() : d.gate();
        return switch (kind) {
            case "when" -> new ConstraintBonds.Bond(d.polarity(), emptySides(d.sheets()), gate, new RuleValue.Text(d.when().strip()), condition,
                    null, null, b.desc());
            case "assert" -> new ConstraintBonds.Bond(d.polarity(), emptySides(ConstraintBonds.conditionSheets(d.assertion())), gate, null,
                    condition, null, d.assertion(), b.desc());
            default -> new ConstraintBonds.Bond(d.polarity(), b.sides(), gate, b.when(), condition, b.mapping(), b.assertion(), b.desc());
        };
    }

    private static Map<String, List<RuleValue>> emptySides(List<String> sheets) {
        Map<String, List<RuleValue>> sides = new java.util.LinkedHashMap<>();
        sheets.forEach(s -> sides.put(s, List.of()));
        return sides;
    }

    /** What a run refuses in the sidecar as it stands, read as a constraint file: the messages of its errors, none when it reads. */
    public List<String> problems() {
        try {
            ConstraintSidecarReader.read(org.ghyura.cf.input.InputSource.of("constraints", json().getBytes(StandardCharsets.UTF_8)));
            return List.of();
        } catch (FrameworkException e) {
            return e.diagnostics().stream().filter(d -> d.severity() == org.ghyura.cf.model.Severity.ERROR).map(Diagnostic::message).toList();
        }
    }

    /** The problems of the sidecar with a draft applied to a rule, this one left as it is; a refused draft gives its refusal. */
    public List<String> problems(int rule, Draft draft) {
        ConstraintEditing tried = copy();
        try {
            tried.apply(rule, draft);
        } catch (IllegalArgumentException e) {
            return List.of(e.getMessage());
        }
        return tried.problems();
    }

    // ---- value parameters --------------------------------------------------------------------------------------------

    /** The sidecar's params: per sheet, per value, the parameters a formula reads as sheet.param. */
    public RuleValue params() {
        return params == null ? new RuleValue.MapValue(Map.of()) : params;
    }

    /** The legacy params row of a sheet's value: its parameters as k=x, joined by ", " (the browser's text of each). */
    public String paramsText(String sheet, String value) {
        if (params() instanceof RuleValue.MapValue all && all.entries().get(sheet) instanceof RuleValue.MapValue values
                && values.entries().get(value) instanceof RuleValue.MapValue p) {
            List<String> parts = new ArrayList<>();
            for (String k : FormulaBlocks.jsKeys(p.entries().keySet())) {
                parts.add(k + "=" + JsText.string(p.entries().get(k)));
            }
            return String.join(", ", parts);
        }
        return "";
    }

    /**
     * The legacy params row edited: parts split at commas, each k=x with exactly one "=", trimmed, k not empty; x a number
     * when the browser read it as one (written as JSON gave it to the sidecar), else the text; a repeated k keeps its place
     * and takes the last x. The row replaces the value's parameters (the sidecar then leaves an empty row out).
     */
    public void setParams(String sheet, String value, String text) {
        Map<String, RuleValue> all = new java.util.LinkedHashMap<>();
        if (params() instanceof RuleValue.MapValue m) {
            all.putAll(m.entries());
        }
        Map<String, RuleValue> values = new java.util.LinkedHashMap<>();
        if (all.get(sheet) instanceof RuleValue.MapValue vm) {
            values.putAll(vm.entries());
        }
        values.put(value, new RuleValue.MapValue(parseRow(text)));
        all.put(sheet, new RuleValue.MapValue(values));
        params = new RuleValue.MapValue(all);
    }

    /** The legacy params row's parse, its keys in the page's order (JSON.stringify of the object it built). */
    static Map<String, RuleValue> parseRow(String text) {
        Map<String, RuleValue> row = new java.util.LinkedHashMap<>();
        for (String part : text.split(",", -1)) {
            String[] kx = part.split("=", -1);
            if (kx.length == 2) {
                String k = JsText.trim(kx[0]);
                String x = JsText.trim(kx[1]);
                if (!k.isEmpty()) {
                    RuleValue n = JsText.number(x);
                    row.put(k, n == null ? new RuleValue.Text(x) : n);
                }
            }
        }
        Map<String, RuleValue> ordered = new java.util.LinkedHashMap<>();
        FormulaBlocks.jsKeys(row.keySet()).forEach(k -> ordered.put(k, row.get(k)));
        return ordered;
    }

    private int find(String sheetA, String a, String sheetB, String b, RuleValue gate) {
        for (int i = 0; i < bonds.size(); i++) {
            ConstraintBonds.Bond bond = bonds.get(i);
            Map<String, List<RuleValue>> sides = bond.sides();
            if (bond.when() != null || bond.mapping() != null || bond.assertion() != null || bond.condition() != null
                    || sides.size() != 2 || !sides.containsKey(sheetA) || !sides.containsKey(sheetB)
                    || sides.get(sheetA).size() != 1 || sides.get(sheetB).size() != 1 || !sameGate(bond.gate(), gate)) {
                continue;
            }
            if (text(sides.get(sheetA).getFirst()).equals(a) && text(sides.get(sheetB).getFirst()).equals(b)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameGate(RuleValue x, RuleValue y) {
        return ConstraintBonds.pythonJson(x, true, true).equals(ConstraintBonds.pythonJson(y, true, true));
    }

    private static String text(RuleValue v) {
        return v instanceof RuleValue.Text t ? t.value() : ConstraintBonds.pythonJson(v, false, true);
    }

    /**
     * Writes the scenario's companion sidecar atomically; refused while bonds contradict each other.
     *
     * @return the companion's path
     */
    public Path save(Path scenario) {
        List<ConstraintBonds.Contradiction> conflicts = contradictions();
        if (!conflicts.isEmpty()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, conflicts.size() + " bond(s) forbid what others require "
                    + "(the same sheets, gate and values): " + conflicts.getFirst().sides() + "; remove one of each before saving",
                    SourceLocation.of(scenario.toString())));
        }
        Path companion = ConstraintSidecarReader.companionOf(Objects.requireNonNull(scenario, "scenario"));
        try {
            AtomicFiles.write(companion, json().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the constraint sidecar cannot be written: "
                    + e.getMessage(), SourceLocation.of(companion.toString())));
        }
        return companion;
    }
}
