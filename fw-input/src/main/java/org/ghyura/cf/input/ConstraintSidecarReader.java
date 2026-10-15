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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import org.ghyura.cf.input.DocTree.Arr;
import org.ghyura.cf.input.DocTree.Leaf;
import org.ghyura.cf.input.DocTree.Obj;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.Condition;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.rule.Expr;
import org.ghyura.cf.model.rule.ExprParser;
import org.ghyura.cf.model.rule.Gate;
import org.ghyura.cf.model.rule.LegacyBond;
import org.ghyura.cf.model.rule.OrderSpec;
import org.ghyura.cf.model.rule.Rule;
import org.ghyura.cf.model.rule.RuleValue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a constraint sidecar (the legacy sieve's version-1 JSON: params, orders, constraints) and the legacy compiled
 * bonds file, strictly: every check of the legacy strict loader (fwgen {@code load_workbook_sidecar},
 * {@code _constraint_shape_error}, {@code _condition_error}, {@code _mapping_error}, the strict
 * {@code validate_sidecar}) is made here, with the line and column of the offending part, and an authored rule is
 * never dropped. Which sheets the rules name is checked later, against the plan.
 */
public final class ConstraintSidecarReader {

    /** A workbook's companion sidecar is {@code <stem>.constraints.json} beside it (fwgen.workbook_sidecar_path). */
    public static final String COMPANION_SUFFIX = ".constraints.json";

    private static final JsonFactory FACTORY = JsonFactory.builder()
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .build();

    private static final Set<String> TOP_KEYS = Set.of("version", "params", "constraints", "orders");
    private static final Set<String> RULE_KEYS = Set.of("id", "sheets", "pairs", "sets", "when", "gate", "polarity", "desc",
            "condition", "mapping", "assert");
    private static final List<String> BODIES = List.of("pairs", "sets", "when", "mapping", "assert");
    private static final Set<String> NODE_KEYS = Set.of("all", "any", "not");

    private final String origin;
    private final List<Diagnostic> errors = new ArrayList<>();

    private ConstraintSidecarReader(String origin) {
        this.origin = origin;
    }

    /** The companion sidecar path of a scenario file: its name with the last extension replaced. */
    public static Path companionOf(Path scenario) {
        String name = scenario.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        return scenario.resolveSibling(stem + COMPANION_SUFFIX);
    }

    /**
     * @throws FrameworkException CONSTRAINT_UNREADABLE when the document is not UTF-8 JSON, CONSTRAINT_INVALID or
     *                            CONSTRAINT_EXPRESSION with every problem found
     */
    public static ConstraintSet read(InputSource source) {
        ConstraintSetBuilder built = new ConstraintSidecarReader(source.name()).readDocument(source);
        return built.build(source);
    }

    private void error(ErrorCode code, String message, SourceLocation where) {
        errors.add(Diagnostic.error(code, message, where));
    }

    /**
     * Checks a constraint layer already parsed elsewhere (the params, orders and constraints of an fwgen spec, put in
     * the shape of the sidecar fwgen's emit_sidecar writes) exactly as a sidecar file is checked.
     *
     * @throws FrameworkException CONSTRAINT_INVALID or CONSTRAINT_EXPRESSION with every problem found
     */
    static ConstraintSet fromTree(String origin, DocTree sidecar, ConstraintSet.Source source) {
        return new ConstraintSidecarReader(origin).check(sidecar).build(source);
    }

    // ---- the document ------------------------------------------------------------------------------------------

    private record ConstraintSetBuilder(Map<String, Map<String, Map<String, RuleValue>>> params, Map<String, OrderSpec> orders,
                                        List<Rule> rules, List<Diagnostic> errors) {
        ConstraintSet build(InputSource source) {
            return build(new ConstraintSet.Source(source.name(), Canonical.sha256Hex(source.copyOfBytes()), "sidecar"));
        }

        ConstraintSet build(ConstraintSet.Source source) {
            if (!errors.isEmpty()) {
                throw new FrameworkException(errors);
            }
            return new ConstraintSet(List.of(source), params, orders, rules, List.of());
        }
    }

    private ConstraintSetBuilder readDocument(InputSource source) {
        String text;
        try {
            text = Utf8.decode(source);
        } catch (FrameworkException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE, "the constraint sidecar is not valid UTF-8",
                    SourceLocation.of(origin)), e);
        }
        DocTree root;
        try (JsonParser parser = FACTORY.createParser(text)) {
            if (parser.nextToken() == null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE, "the constraint sidecar is empty",
                        SourceLocation.of(origin)));
            }
            root = DocTree.readJson(parser, origin, "", (key, keyAt) -> error(ErrorCode.CONSTRAINT_INVALID, "the key \"" + key
                    + "\" appears twice in one object (the legacy json.loads kept the last silently)", keyAt));
            if (parser.nextToken() != null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE,
                        "content after the end of the JSON document", DocTree.at(origin, parser.currentTokenLocation(), "")));
            }
        } catch (JsonProcessingException e) {
            JsonLocation l = e.getLocation();
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE, "the constraint sidecar is not valid JSON: "
                    + e.getOriginalMessage(), l == null ? SourceLocation.of(origin) : DocTree.at(origin, l, "")));
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE, "the constraint sidecar cannot be read: "
                    + e.getMessage(), SourceLocation.of(origin)));
        }
        return check(root);
    }

    private ConstraintSetBuilder check(DocTree root) {
        Map<String, Map<String, Map<String, RuleValue>>> params = new LinkedHashMap<>();
        Map<String, OrderSpec> orders = new LinkedHashMap<>();
        List<Rule> rules = new ArrayList<>();
        if (!(root instanceof Obj top)) {
            error(ErrorCode.CONSTRAINT_INVALID, "the top level must be a JSON object", root.where());
            return new ConstraintSetBuilder(params, orders, rules, errors);
        }
        for (Map.Entry<String, DocTree> e : top.fields().entrySet()) {
            if (!TOP_KEYS.contains(e.getKey())) {
                error(ErrorCode.CONSTRAINT_INVALID, "unsupported top-level key \"" + e.getKey() + "\"; allowed: constraints, orders, "
                        + "params, version", e.getValue().where());
            }
        }
        DocTree version = top.fields().get("version");
        if (!(version instanceof Leaf v && v.value() instanceof RuleValue.Int i && i.value().equals(BigInteger.ONE))) {
            error(ErrorCode.CONSTRAINT_INVALID, "'version' must be the integer 1", version == null ? top.where() : version.where());
        }
        DocTree p = top.fields().get("params");
        if (p != null) {
            readParams(p, params);
        }
        DocTree o = top.fields().get("orders");
        if (o != null) {
            readOrders(o, orders);
        }
        DocTree c = top.fields().get("constraints");
        if (c != null) {
            if (!(c instanceof Arr list)) {
                error(ErrorCode.CONSTRAINT_INVALID, "'constraints' must be a list of objects", c.where());
            } else {
                for (DocTree item : list.items()) {
                    Rule rule = readRule(item);
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
            }
        }
        return new ConstraintSetBuilder(params, orders, rules, errors);
    }

    private void readParams(DocTree p, Map<String, Map<String, Map<String, RuleValue>>> params) {
        if (!(p instanceof Obj sheets)) {
            error(ErrorCode.CONSTRAINT_INVALID, "'params' must map sheet -> value -> {attr: value}", p.where());
            return;
        }
        sheets.fields().forEach((sheet, values) -> {
            if (!(values instanceof Obj vs)) {
                error(ErrorCode.CONSTRAINT_INVALID, "'params." + sheet + "' must map value -> {attr: value}", values.where());
                return;
            }
            Map<String, Map<String, RuleValue>> byValue = new LinkedHashMap<>();
            vs.fields().forEach((value, attrs) -> {
                if (!(attrs instanceof Obj as)) {
                    error(ErrorCode.CONSTRAINT_INVALID, "'params." + sheet + "' value '" + value + "' must map to {attr: value}", attrs.where());
                    return;
                }
                Map<String, RuleValue> a = new LinkedHashMap<>();
                as.fields().forEach((k, v) -> a.put(k, DocTree.value(v)));
                byValue.put(value, a);
            });
            params.put(sheet, byValue);
        });
    }

    private void readOrders(DocTree o, Map<String, OrderSpec> orders) {
        if (!(o instanceof Obj sheets)) {
            error(ErrorCode.CONSTRAINT_INVALID, "'orders' must be an object", o.where());
            return;
        }
        sheets.fields().forEach((sheet, spec) -> {
            if (spec instanceof Arr list) {
                orders.put(sheet, OrderSpec.listed(list.items().stream().map(DocTree::value).toList()));
            } else if (spec instanceof Leaf l && l.value().equalsText("numeric")) {
                orders.put(sheet, OrderSpec.Numeric.NUMERIC);
            } else if (spec instanceof Leaf l && l.value().equalsText("date")) {
                orders.put(sheet, OrderSpec.Date.DATE);
            } else {
                error(ErrorCode.CONSTRAINT_INVALID, "orders[\"" + sheet + "\"] must be a list, \"numeric\" or \"date\"", spec.where());
            }
        });
    }

    // ---- one rule ----------------------------------------------------------------------------------------------

    private Rule readRule(DocTree item) {
        int before = errors.size();
        if (!(item instanceof Obj c)) {
            error(ErrorCode.CONSTRAINT_INVALID, "a constraint must be an object", item.where());
            return null;
        }
        Map<String, DocTree> f = c.fields();
        String id = f.get("id") instanceof Leaf l && l.value() instanceof RuleValue.Text t ? t.value() : null;
        String label = "constraint " + (id == null ? "#" + c.where().path() : "'" + id + "'");
        for (String key : f.keySet()) {
            if (!RULE_KEYS.contains(key)) {
                error(ErrorCode.CONSTRAINT_INVALID, label + " has the unknown key \"" + key + "\"", f.get(key).where());
            }
        }
        List<String> bodies = BODIES.stream().filter(f::containsKey).toList();
        if (bodies.size() != 1) {
            error(ErrorCode.CONSTRAINT_INVALID, label + (bodies.isEmpty() ? " needs 'pairs', 'sets', 'when', 'mapping', or 'assert'"
                    : " needs exactly one body of [pairs, sets, when, mapping, assert], got " + bodies), c.where());
            return null;
        }
        if (f.containsKey("id") && id == null || id != null && id.isEmpty()) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": 'id' must be a non-empty string", f.get("id").where());
        }
        String description = null;
        if (f.containsKey("desc")) {
            if (f.get("desc") instanceof Leaf l && l.value() instanceof RuleValue.Text t) {
                description = t.value();
            } else {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": 'desc' must be a string", f.get("desc").where());
            }
        }
        List<String> sheets = null;
        if (f.containsKey("sheets")) {
            sheets = stringList(f.get("sheets"));
            if (sheets == null || sheets.isEmpty()) {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": 'sheets' must be a non-empty list of sheet names", f.get("sheets").where());
                sheets = null;
            } else if (Set.copyOf(sheets).size() != sheets.size()) {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": 'sheets' names a sheet twice", f.get("sheets").where());
            }
        }
        Gate gate = Gate.ANY;
        if (f.containsKey("gate")) {
            gate = gate(f.get("gate"), label);
        }
        Rule.Polarity polarity = null;
        if (f.containsKey("polarity")) {
            DocTree pn = f.get("polarity");
            if (pn instanceof Leaf l && l.value().equalsText("forbid")) {
                polarity = Rule.Polarity.FORBID;
            } else if (pn instanceof Leaf l && l.value().equalsText("require")) {
                polarity = Rule.Polarity.REQUIRE;
            } else {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": polarity must be \"forbid\" or \"require\" (the legacy sieve read any "
                        + "other value as require; its strict loader refused it, as this build does)", pn.where());
            }
        }
        Condition condition = f.containsKey("condition") ? condition(f.get("condition"), "condition", label, true) : null;
        String body = bodies.getFirst();
        DocTree b = f.get(body);
        Rule.Body parsed = switch (body) {
            case "pairs" -> pairs(b, sheets, label);
            case "sets" -> sets(b, label);
            case "when" -> when(b, sheets, f.containsKey("sheets"), label);
            case "mapping" -> mapping(b, label);
            default -> {
                Condition a = condition(b, "assert", label, false);
                if (a == null || a == Condition.Always.ALWAYS) {
                    if (errors.size() == before) {
                        error(ErrorCode.CONSTRAINT_INVALID, label + ": assert references no sheet", b.where());
                    }
                    yield null;
                }
                yield new Rule.Assert(a);
            }
        };
        if (parsed == null || errors.size() > before) {
            return null;
        }
        if (polarity == null) {
            polarity = parsed instanceof Rule.Assert ? Rule.Polarity.REQUIRE : Rule.Polarity.FORBID;
        }
        Rule rule = new Rule(id, polarity, gate, condition, parsed, description, c.where());
        if (!(parsed instanceof Rule.Assert) && rule.referencedSheets().size() < 2) {
            error(ErrorCode.CONSTRAINT_INVALID, label + " references " + rule.referencedSheets().size() + " sheet(s) "
                    + rule.referencedSheets() + "; a bond needs at least two (only an assert may name one), and the strict "
                    + "legacy validation refused it rather than skip it", c.where());
            return null;
        }
        return rule;
    }

    private List<String> stringList(DocTree n) {
        if (!(n instanceof Arr a)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (DocTree item : a.items()) {
            if (!(item instanceof Leaf l && l.value() instanceof RuleValue.Text t && !t.value().isEmpty())) {
                return null;
            }
            out.add(t.value());
        }
        return out;
    }

    private Gate gate(DocTree n, String label) {
        if (!(n instanceof Obj g) || !Set.of("adjacent", "within").containsAll(g.fields().keySet())) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": 'gate' must be an object with only 'adjacent' and/or 'within'", n.where());
            return Gate.ANY;
        }
        boolean adjacent = false;
        long within = -1;
        DocTree a = g.fields().get("adjacent");
        if (a != null) {
            if (a instanceof Leaf l && l.value() instanceof RuleValue.Bool bool) {
                adjacent = bool.value();
            } else {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": 'gate.adjacent' must be true or false", a.where());
            }
        }
        DocTree w = g.fields().get("within");
        if (w != null) {
            if (w instanceof Leaf l && l.value() instanceof RuleValue.Int i && i.value().signum() >= 0 && i.value().bitLength() < 63) {
                within = i.value().longValue();
            } else {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": 'gate.within' must be a non-negative integer", w.where());
            }
        }
        return new Gate(adjacent, within);
    }

    private Rule.Body pairs(DocTree n, List<String> sheets, String label) {
        if (!(n instanceof Arr list) || list.items().isEmpty()) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": 'pairs' must be a non-empty list of {sheet: value} objects", n.where());
            return null;
        }
        List<Map<String, RuleValue>> entries = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (DocTree item : list.items()) {
            if (!(item instanceof Obj e) || e.fields().isEmpty() || e.fields().keySet().stream().anyMatch(String::isEmpty)) {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": each 'pairs' entry must be a non-empty {sheet: value} object", item.where());
                return null;
            }
            Map<String, RuleValue> entry = new LinkedHashMap<>();
            for (Map.Entry<String, DocTree> kv : e.fields().entrySet()) {
                RuleValue v = DocTree.value(kv.getValue());
                if (!v.scalar()) {
                    error(ErrorCode.CONSTRAINT_INVALID, label + ": 'pairs' values must be single values (string/number/boolean), not "
                            + "lists, objects or null", kv.getValue().where());
                    return null;
                }
                entry.put(kv.getKey(), v);
                if (!seen.contains(kv.getKey())) {
                    seen.add(kv.getKey());
                }
            }
            entries.add(entry);
        }
        return new Rule.Pairs(sheets != null ? sheets : seen, entries);
    }

    private Rule.Body sets(DocTree n, String label) {
        if (!(n instanceof Obj o) || o.fields().isEmpty() || o.fields().keySet().stream().anyMatch(String::isEmpty)) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": 'sets' must be a non-empty {sheet: [values]} object", n.where());
            return null;
        }
        Map<String, List<RuleValue>> sets = new LinkedHashMap<>();
        for (Map.Entry<String, DocTree> kv : o.fields().entrySet()) {
            if (!(kv.getValue() instanceof Arr a) || a.items().isEmpty()) {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": 'sets." + kv.getKey() + "' must be a non-empty list of values", kv.getValue().where());
                return null;
            }
            List<RuleValue> values = new ArrayList<>();
            for (DocTree item : a.items()) {
                RuleValue v = DocTree.value(item);
                if (!v.scalar()) {
                    error(ErrorCode.CONSTRAINT_INVALID, label + ": 'sets." + kv.getKey() + "' values must be single values "
                            + "(string/number/boolean)", item.where());
                    return null;
                }
                values.add(v);
            }
            sets.put(kv.getKey(), values);
        }
        return new Rule.Sets(sets);
    }

    private Rule.Body when(DocTree n, List<String> sheets, boolean sheetsWritten, String label) {
        if (!(n instanceof Leaf l && l.value() instanceof RuleValue.Text t) || t.value().strip().isEmpty()) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": 'when' must be a non-empty predicate string", n.where());
            return null;
        }
        if (!sheetsWritten) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": a 'when' bond needs 'sheets' (the sheets its predicate ranges over)", n.where());
            return null;
        }
        if (sheets == null) {
            return null;
        }
        String text = t.value();
        Expr expression;
        try {
            // the legacy eval stripped leading and trailing spaces and tabs before parsing
            expression = ExprParser.parse(text.strip());
        } catch (ExprParser.SyntaxError e) {
            error(ErrorCode.CONSTRAINT_EXPRESSION, label + ": 'when' \"" + text + "\" is not a supported expression: " + e.getMessage()
                    + " (at character " + (e.offset() + 1) + ")", n.where());
            return null;
        }
        for (String name : expression.names()) {
            boolean call = isCalled(expression, name);
            if (sheets.contains(name)) {
                continue;
            }
            if (call && ExprParser.FUNCTIONS.contains(name)) {
                continue;
            }
            error(ErrorCode.CONSTRAINT_EXPRESSION, label + ": 'when' uses the name '" + name + "', which is neither one of its sheets "
                    + sheets + " nor a function it may call (" + String.join(", ", new java.util.TreeSet<>(ExprParser.FUNCTIONS)) + ")",
                    n.where());
            return null;
        }
        return new Rule.When(sheets, text, expression);
    }

    private static boolean isCalled(Expr e, String name) {
        return switch (e) {
            case Expr.Call c -> c.function().equals(name) || c.args().stream().anyMatch(a -> isCalled(a, name));
            case Expr.Attribute a -> isCalled(a.target(), name);
            case Expr.Subscript s -> isCalled(s.target(), name) || isCalled(s.index(), name);
            case Expr.Unary u -> isCalled(u.operand(), name);
            case Expr.Binary b -> isCalled(b.left(), name) || isCalled(b.right(), name);
            case Expr.Compare c -> isCalled(c.first(), name) || c.rest().stream().anyMatch(x -> isCalled(x, name));
            case Expr.And a -> a.operands().stream().anyMatch(x -> isCalled(x, name));
            case Expr.Or o -> o.operands().stream().anyMatch(x -> isCalled(x, name));
            case Expr.Not x -> isCalled(x.operand(), name);
            case Expr.IfElse i -> isCalled(i.test(), name) || isCalled(i.then(), name) || isCalled(i.otherwise(), name);
            case Expr.ListDisplay l -> l.items().stream().anyMatch(x -> isCalled(x, name));
            case Expr.TupleDisplay t -> t.items().stream().anyMatch(x -> isCalled(x, name));
            case Expr.Constant c -> false;
            case Expr.Name x -> false;
        };
    }

    private Rule.Body mapping(DocTree n, String label) {
        if (!(n instanceof Obj m)) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": mapping must be an object", n.where());
            return null;
        }
        for (String key : m.fields().keySet()) {
            if (!Set.of("source", "target", "allow").contains(key)) {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": mapping has the unknown key \"" + key + "\"", m.fields().get(key).where());
                return null;
            }
        }
        String source = m.fields().get("source") instanceof Leaf l && l.value() instanceof RuleValue.Text t && !t.value().isEmpty()
                ? t.value() : null;
        String target = m.fields().get("target") instanceof Leaf l && l.value() instanceof RuleValue.Text t && !t.value().isEmpty()
                ? t.value() : null;
        if (source == null || target == null) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": mapping needs non-empty source and target sheet names", m.where());
            return null;
        }
        Map<String, List<RuleValue>> allow = new LinkedHashMap<>();
        DocTree a = m.fields().get("allow");
        if (a != null) {
            if (!(a instanceof Obj ao)) {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": mapping.allow must be an object", a.where());
                return null;
            }
            for (Map.Entry<String, DocTree> kv : ao.fields().entrySet()) {
                if (!(kv.getValue() instanceof Arr list)) {
                    error(ErrorCode.CONSTRAINT_INVALID, label + ": mapping.allow[\"" + kv.getKey() + "\"] must be a list of values",
                            kv.getValue().where());
                    return null;
                }
                allow.put(kv.getKey(), list.items().stream().map(DocTree::value).toList());
            }
        }
        return new Rule.Mapping(source, target, allow);
    }

    /**
     * A condition in the legacy grammar ({@code _condition_error}); {@code null} or {@code {}} at the top of a rule's
     * {@code condition} is no condition. Returns null after reporting an error.
     */
    private Condition condition(DocTree n, String path, String label, boolean topLevelGuard) {
        if (n instanceof Leaf l && l.value() == RuleValue.Null.NULL) {
            return topLevelGuard ? null : Condition.Always.ALWAYS;
        }
        if (!(n instanceof Obj c)) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + " must be an object", n.where());
            return null;
        }
        Map<String, DocTree> f = c.fields();
        if (f.isEmpty()) {
            return topLevelGuard ? null : Condition.Always.ALWAYS;
        }
        for (String key : f.keySet()) {
            if (!NODE_KEYS.contains(key) && !key.equals("sheet") && Condition.Op.ofToken(key) == null) {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + " has the unknown key \"" + key + "\"", f.get(key).where());
                return null;
            }
        }
        List<String> nodeKeys = f.keySet().stream().filter(NODE_KEYS::contains).toList();
        List<Condition.Op> ops = f.keySet().stream().map(Condition.Op::ofToken).filter(java.util.Objects::nonNull).toList();
        if (!nodeKeys.isEmpty()) {
            if (nodeKeys.size() != 1 || !ops.isEmpty() || f.containsKey("sheet")) {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + " must be either one boolean node or one sheet leaf", c.where());
                return null;
            }
            String key = nodeKeys.getFirst();
            DocTree child = f.get(key);
            if (key.equals("not")) {
                Condition inner = condition(child, path + ".not", label, false);
                return inner == null ? null : new Condition.Not(inner);
            }
            if (!(child instanceof Arr list) || list.items().isEmpty()) {
                error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + "." + key + " must be a non-empty list", child.where());
                return null;
            }
            List<Condition> children = new ArrayList<>();
            for (int i = 0; i < list.items().size(); i++) {
                Condition inner = condition(list.items().get(i), path + "." + key + "[" + i + "]", label, false);
                if (inner == null) {
                    return null;
                }
                children.add(inner);
            }
            return key.equals("all") ? new Condition.All(children) : new Condition.Any(children);
        }
        DocTree sheetNode = f.get("sheet");
        if (sheetNode == null) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + " leaf needs 'sheet'", c.where());
            return null;
        }
        if (!(sheetNode instanceof Leaf sl && sl.value() instanceof RuleValue.Text st && !st.value().isEmpty())) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + ".sheet must be a sheet name", sheetNode.where());
            return null;
        }
        String sheet = st.value();
        if (ops.size() != 1) {
            error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + " leaf needs exactly one operator, got "
                    + (ops.isEmpty() ? "none" : ops.stream().map(Condition.Op::token).toList()), c.where());
            return null;
        }
        Condition.Op op = ops.getFirst();
        DocTree operand = f.get(op.token());
        RuleValue v = DocTree.value(operand);
        switch (op.kind()) {
            case VALUE -> {
                return Condition.Leaf.value(sheet, op, v);
            }
            case VALUES -> {
                if (!(v instanceof RuleValue.ListValue list)) {
                    error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + "." + op.token() + " must be a list of values", operand.where());
                    return null;
                }
                return Condition.Leaf.values(sheet, op, list.items());
            }
            case COUNT -> {
                if (v instanceof RuleValue.Int i) {
                    return Condition.Leaf.count(sheet, op, i.value());
                }
                if (v instanceof RuleValue.Bool b) {
                    return Condition.Leaf.count(sheet, op, b.value() ? BigInteger.ONE : BigInteger.ZERO);
                }
                error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + "." + op.token() + " must be an integer", operand.where());
                return null;
            }
            case SHEET, ORDINAL_SHEET -> {
                if (!(v instanceof RuleValue.Text t && !t.value().isEmpty())) {
                    error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + "." + op.token() + " must name another sheet", operand.where());
                    return null;
                }
                return Condition.Leaf.sheet(sheet, op, t.value());
            }
            case FLAG -> {
                if (!(v instanceof RuleValue.Bool b)) {
                    error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + ".present must be true or false", operand.where());
                    return null;
                }
                return Condition.Leaf.present(sheet, b.value());
            }
            case ORDINAL -> {
                if (!v.scalar()) {
                    error(ErrorCode.CONSTRAINT_INVALID, label + ": " + path + "." + op.token() + " must be a scalar constant "
                            + "(rank via `orders`)", operand.where());
                    return null;
                }
                return Condition.Leaf.value(sheet, op, v);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    // ---- the legacy compiled bonds file ------------------------------------------------------------------------

    /**
     * Reads a legacy compiled bonds file (OptionalBondFilter format, one bond per line: {@code F|R} | {@code 1/0} |
     * {@code sheet,sheet,...} | {@code l,l,...;l,l,...}; blank lines and {@code #} comments ignored). Where the legacy
     * filter skipped a malformed line or ignored the whole file, this refuses it with the line.
     */
    public static ConstraintSet readBonds(InputSource source) {
        String origin = source.name();
        String text;
        try {
            text = Utf8.decode(source);
        } catch (FrameworkException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE, "the bonds file is not valid UTF-8",
                    SourceLocation.of(origin)), e);
        }
        List<Diagnostic> problems = new ArrayList<>();
        List<LegacyBond> bonds = new ArrayList<>();
        String[] lines = text.split("\r\n|\r|\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            SourceLocation where = SourceLocation.of(origin, i + 1);
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            String[] p = line.split("\\|", -1);
            if (p.length != 4) {
                problems.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, "a bond line has four fields F|R, 1|0, sheets and tuples "
                        + "separated by '|'; this one has " + p.length + " (the legacy filter skipped it silently)", where));
                continue;
            }
            String polarity = p[0].trim();
            if (!polarity.equalsIgnoreCase("F") && !polarity.equalsIgnoreCase("R")) {
                problems.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, "the polarity of a bond is F or R, got '" + polarity
                        + "' (the legacy filter read anything but R as F)", where));
                continue;
            }
            String gate = p[1].trim();
            if (!gate.equals("0") && !gate.equals("1")) {
                problems.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, "the gate flag of a bond is 1 or 0, got '" + gate + "'", where));
                continue;
            }
            List<String> sheets = new ArrayList<>();
            for (String s : p[2].split(",", -1)) {
                sheets.add(s.trim());
            }
            if (sheets.stream().anyMatch(String::isEmpty)) {
                problems.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, "a bond names its sheets separated by commas, none empty", where));
                continue;
            }
            List<List<Integer>> tuples = new ArrayList<>();
            boolean ok = true;
            if (!p[3].isEmpty()) {
                for (String tuple : p[3].split(";", -1)) {
                    String[] labels = tuple.split(",", -1);
                    if (labels.length != sheets.size()) {
                        problems.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, "the tuple '" + tuple + "' has " + labels.length
                                + " labels for " + sheets.size() + " sheets (the legacy filter could fail on such a line)", where));
                        ok = false;
                        break;
                    }
                    List<Integer> t = new ArrayList<>();
                    for (String label : labels) {
                        try {
                            t.add(Integer.parseInt(label.trim()));
                        } catch (NumberFormatException e) {
                            problems.add(Diagnostic.error(ErrorCode.CONSTRAINT_INVALID, "'" + label.trim() + "' is not a key label "
                                    + "(the legacy filter ignored the whole file then)", where));
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) {
                        break;
                    }
                    tuples.add(t);
                }
            }
            if (ok) {
                bonds.add(new LegacyBond(!polarity.equalsIgnoreCase("R"), gate.equals("1"), sheets, tuples, where));
            }
        }
        if (!problems.isEmpty()) {
            throw new FrameworkException(problems);
        }
        return new ConstraintSet(List.of(new ConstraintSet.Source(origin, Canonical.sha256Hex(source.copyOfBytes()), "bonds")),
                Map.of(), Map.of(), List.of(), bonds);
    }
}
