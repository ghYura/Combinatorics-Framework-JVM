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

import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.model.rule.RuleValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The FW_Seq dependency graph of the legacy fwseq_graph module (STEP 37), exactly: slot nodes, passive data sheets of a
 * workbook and brace nodes, edges oriented dependent to dependency (cartes, separator, brace_operand, brace_nested_operand,
 * brace_ref, produces), the structural issues with the legacy messages (missing_operand, cycle and consumed_result_ambiguity
 * errors, the unused_advanced_node warning), the canonical JSON, the Graphviz DOT text and the graph hash that bundle plan
 * recorded. A spec document's graph is built from its slots and seq_extra rows as fwgen built it; a workbook's from its
 * sheets and FW_Seq rows as fwgen.load_workbook_spec read a legacy XLSX workbook for bundle plan. Immutable.
 */
public final class SeqGraph {

    /** The level of an issue that rejects a spec document. */
    public static final String ERROR = "error";
    /** The level of an issue that is only reported. */
    public static final String WARNING = "warning";

    /** A slot or brace node and its attributes, in the legacy order. */
    public record Node(String id, String kind, Map<String, RuleValue> attrs) {
        public Node {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            attrs = Collections.unmodifiableMap(new LinkedHashMap<>(attrs));
        }

        /** A true attribute (Python truthiness of a bool, text or list attribute). */
        public boolean is(String attr) {
            return switch (attrs.get(attr)) {
                case RuleValue.Bool b -> b.value();
                case RuleValue.Text t -> !t.value().isEmpty();
                case RuleValue.ListValue l -> !l.items().isEmpty();
                case null, default -> false;
            };
        }
    }

    /** {@code src} is computed from {@code dst}. */
    public record Edge(String src, String dst, String kind) {
    }

    /** A structural finding and the nodes it concerns. */
    public record Issue(String level, String code, String message, List<String> nodes) {
        public Issue {
            nodes = List.copyOf(nodes);
        }
    }

    /** A slot as build_graph reads it from a spec. */
    record SlotInput(String sheet, String verb, int values, List<String> flags, boolean group, String separator, List<String> chain) {
    }

    private static final String BRACE_OPERAND = "(?:[A-Za-z0-9_]+|FW_\\(\\)G?)";
    private static final Pattern BRACE = Pattern.compile("FW_\\([A-Za-z0-9_]*,," + BRACE_OPERAND + ",[A-Za-z0-9_]*,"
            + BRACE_OPERAND + ",,[A-Za-z0-9_]*,[A-Za-z0-9_]*,(1:[1Nn]|[Mm]:[1MmNn])\\)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CARTES = Pattern.compile("FW_Cartes(?:(?i:_first))?\\(([A-Za-z0-9_]+)\\)");
    private static final Pattern SEPARATOR = Pattern.compile("FW_Separator\\(([A-Za-z0-9_]+)\\)");
    private static final String WS = "[\\t\\n\\x0B\\f\\r\\x1C-\\x1F \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]";
    private static final Pattern FW_VAR_EXIT = Pattern.compile("(?s).*?" + WS + "+?FW_VAR" + WS + "*?=" + WS + "*?FW_EXIT_CODE.*?|^" + WS
            + "*FW_VAR" + WS + "*?=" + WS + "*?FW_EXIT_CODE.*?");
    private static final List<String> SEQ_FLAG_SUFFIXES = List.of("FW_Exclude", "FW_Heading", "FW_LastInQueue", "FW_Reuse",
            "FW_ReuseTableOnly", "FW_Optional");
    private static final Comparator<Edge> EDGE_ORDER = Comparator.comparing(Edge::src, PyText.CODE_POINT_ORDER)
            .thenComparing(Edge::dst, PyText.CODE_POINT_ORDER).thenComparing(Edge::kind, PyText.CODE_POINT_ORDER);

    private final Map<String, Node> nodes;
    private final List<Edge> edges;

    private SeqGraph(Map<String, Node> nodes, List<Edge> edges) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.edges = List.copyOf(edges);
    }

    /** The nodes in the order the legacy builder made them. */
    public Map<String, Node> nodes() {
        return nodes;
    }

    /** The edges in the order the legacy builder made them. */
    public List<Edge> edges() {
        return edges;
    }

    // ---- building ----------------------------------------------------------------------------------------------

    /** build_graph for a spec document (TOML or JSON): its slots, then its seq_extra rows. */
    static SeqGraph ofSpec(List<SlotInput> slots, List<List<String>> seqExtra) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        for (SlotInput s : slots) {
            nodes.put(s.sheet(), slotNode(s.sheet(), s.verb(), s.values(), s.flags(), s.group(), s.separator(), s.chain(), true));
        }
        for (SlotInput s : slots) {
            operandEdges(s.sheet(), List.of(s.verb()), edges);
            if (!s.separator().isEmpty()) {
                edges.add(new Edge(s.sheet(), s.separator(), "separator"));
            }
        }
        braces(seqExtra, nodes, edges);
        return new SeqGraph(nodes, edges);
    }

    /**
     * build_graph for a workbook, read as fwgen.load_workbook_spec read a legacy XLSX workbook: the data sheets (names not
     * starting with FW_) and their values in the Core's reading order, the FW_Seq program as the Core's SeqParser reads it,
     * one slot per sheet the program gives directives (in program order) and a passive node for every other data sheet.
     */
    public static SeqGraph ofWorkbook(WorkbookData workbook) {
        List<String> data = new ArrayList<>();
        Map<String, Integer> values = new HashMap<>();
        for (InputSheet sheet : workbook.sheets()) {
            if (!sheet.name().startsWith("FW_")) {
                data.add(sheet.name());
                values.put(sheet.name(), valueCount(sheet));
            }
        }
        List<List<String>> seqRows = new ArrayList<>();
        workbook.sheet("FW_Seq").ifPresent(seq -> {
            for (InputRow row : sortedRows(seq)) {
                List<String> cells = new ArrayList<>();
                for (InputCell cell : sortedCells(row)) {
                    cells.add(cellText(cell));
                }
                seqRows.add(cells);
            }
        });
        Map<String, Entry> program = program(seqRows, data);
        List<Map.Entry<String, Entry>> ordered = byRow(program);
        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        Set<String> active = new LinkedHashSet<>();
        List<Map.Entry<String, Entry>> slots = new ArrayList<>();
        for (Map.Entry<String, Entry> e : ordered) {
            Entry entry = e.getValue();
            if (entry.directives.isEmpty() || !values.containsKey(e.getKey())) {
                continue;
            }
            List<String> flags = new ArrayList<>();
            for (String f : entry.flags) {
                if (!(f.equals("FW_Optional") && !entry.role.equals("optional"))
                        && !((f.equals("FW_Exclude") || f.equals("FW_Heading")) && !entry.role.equals("excluded"))) {
                    flags.add(f);
                }
            }
            if (entry.concat != null) {
                flags.add("FW_Concatenator=" + entry.concat);
            }
            nodes.put(e.getKey(), slotNode(e.getKey(), entry.directives.getFirst(), values.get(e.getKey()), flags, false, "", List.of(),
                    false));
            active.add(e.getKey());
            slots.add(e);
        }
        for (String name : data) {
            if (!active.contains(name)) {
                Map<String, RuleValue> attrs = new LinkedHashMap<>();
                attrs.put("verb", RuleValue.Null.NULL);
                attrs.put("n", RuleValue.Int.of(values.get(name)));
                attrs.put("optional", new RuleValue.Bool(false));
                attrs.put("excluded", new RuleValue.Bool(false));
                attrs.put("heading", new RuleValue.Bool(false));
                attrs.put("group", new RuleValue.Bool(false));
                attrs.put("separator", RuleValue.Null.NULL);
                attrs.put("keeps_rows", new RuleValue.Bool(false));
                attrs.put("passive", new RuleValue.Bool(true));
                nodes.put(name, new Node(name, "slot", attrs));
            }
        }
        for (Map.Entry<String, Entry> e : slots) {
            operandEdges(e.getKey(), e.getValue().directives, edges);
        }
        List<List<String>> braceRows = new ArrayList<>();
        for (Map.Entry<String, Entry> e : ordered) {
            List<String> directives = e.getValue().directives;
            if (!directives.isEmpty() && braceFields(directives.getFirst()) != null) {
                braceRows.add(List.of(e.getKey(), directives.getFirst()));
            }
        }
        braces(braceRows, nodes, edges);
        return new SeqGraph(nodes, edges);
    }

    private static Node slotNode(String sheet, String verb, int values, List<String> flags, boolean group, String separator,
                                 List<String> chain, boolean compactDefault) {
        Map<String, RuleValue> attrs = new LinkedHashMap<>();
        attrs.put("verb", new RuleValue.Text(verb));
        attrs.put("n", RuleValue.Int.of(values));
        attrs.put("optional", new RuleValue.Bool(flags.contains("FW_Optional")));
        attrs.put("excluded", new RuleValue.Bool(flags.contains("FW_Exclude")));
        attrs.put("heading", new RuleValue.Bool(flags.contains("FW_Heading")));
        attrs.put("group", new RuleValue.Bool(group));
        attrs.put("separator", separator.isEmpty() ? RuleValue.Null.NULL : new RuleValue.Text(separator));
        attrs.put("chain", chain.isEmpty() ? RuleValue.Null.NULL
                : new RuleValue.ListValue(chain.stream().<RuleValue>map(RuleValue.Text::new).toList()));
        attrs.put("keeps_rows", new RuleValue.Bool(flags.contains("FW_Reuse") || (compactDefault && !flags.contains("FW_ReuseTableOnly"))));
        return new Node(sheet, "slot", attrs);
    }

    private static void operandEdges(String sheet, List<String> directives, List<Edge> edges) {
        for (String d : directives) {
            String first = PyText.firstLine(d);
            for (Matcher m = CARTES.matcher(first); m.find(); ) {
                edges.add(new Edge(sheet, m.group(1), "cartes"));
            }
            for (Matcher m = SEPARATOR.matcher(first); m.find(); ) {
                edges.add(new Edge(sheet, m.group(1), "separator"));
            }
        }
    }

    /** The brace joiners of the rows: each consumes prior result tables, FW_()/FW_()G from the newest brace targets. */
    private static void braces(List<List<String>> rows, Map<String, Node> nodes, List<Edge> edges) {
        List<String> priorTargets = new ArrayList<>();
        for (int ri = 0; ri < rows.size(); ri++) {
            List<String> row = rows.get(ri);
            String target = row.isEmpty() ? "" : row.getFirst();
            for (int ci = 0; ci < row.size(); ci++) {
                String[] fields = braceFields(row.get(ci));
                if (fields == null) {
                    continue;
                }
                String bid = "brace#" + ri + "." + ci;
                Map<String, RuleValue> attrs = new LinkedHashMap<>();
                attrs.put("expr", new RuleValue.Text(PyText.firstLine(row.get(ci))));
                attrs.put("mult", new RuleValue.Text(fields.length > 8 ? fields[8] : ""));
                attrs.put("target", new RuleValue.Text(target));
                nodes.put(bid, new Node(bid, "brace", attrs));
                if (!target.isEmpty()) {
                    edges.add(new Edge(target, bid, "produces"));
                    Node t = nodes.get(target);
                    if (t != null && t.kind().equals("slot")) {
                        Map<String, RuleValue> a = new LinkedHashMap<>(t.attrs());
                        a.put("is_join_result", new RuleValue.Bool(true));
                        nodes.put(target, new Node(target, "slot", a));
                    }
                }
                int prior = priorTargets.size() - 1;
                for (int idx : new int[]{2, 4}) {
                    String op = idx < fields.length ? fields[idx] : "";
                    if (op.equals("FW_()") || op.equals("FW_()G")) {
                        op = prior >= 0 ? priorTargets.get(prior--) : "@missing_nested_prior:" + ri + ":" + idx;
                        edges.add(new Edge(bid, op, "brace_nested_operand"));
                    } else if (!op.isEmpty()) {
                        edges.add(new Edge(bid, op, "brace_operand"));
                    }
                }
                for (int idx : new int[]{0, 3, 6, 7}) {
                    String op = idx < fields.length ? fields[idx] : "";
                    if (!op.isEmpty()) {
                        edges.add(new Edge(bid, op, "brace_ref"));
                    }
                }
                if (!target.isEmpty()) {
                    priorTargets.add(target);
                }
            }
        }
        // the whole FW_Seq is resolved before any brace runs: a nested operand keeps its rows for every join
        Set<String> nested = new LinkedHashSet<>();
        for (Edge e : edges) {
            if (e.kind().equals("brace_nested_operand")) {
                nested.add(e.dst());
            }
        }
        for (String op : nested) {
            Node node = nodes.get(op);
            if (node != null && node.kind().equals("slot")) {
                Map<String, RuleValue> a = new LinkedHashMap<>(node.attrs());
                a.put("keeps_rows", new RuleValue.Bool(true));
                a.put("implicit_reuse", new RuleValue.Bool(true));
                nodes.put(op, new Node(op, "slot", a));
            }
        }
    }

    /** The nine fields of a brace's first line, or null when the cell is not a brace. */
    static String[] braceFields(String cell) {
        String first = PyText.firstLine(cell);
        if (!BRACE.matcher(first).matches()) {
            return null;
        }
        return first.substring(first.indexOf('(') + 1, first.lastIndexOf(')')).split(",", -1);
    }

    // ---- the workbook as fwgen read it ---------------------------------------------------------------------------

    private static final class Entry {
        List<String> directives = List.of();
        final List<String> flags = new ArrayList<>();
        String concat;
        Integer row;
        String role = "mandatory";
    }

    /** parse_fw_seq_rows: the program of every sheet the FW_Seq rows touch, as the Core's SeqParser reads them. */
    private static Map<String, Entry> program(List<List<String>> rows, List<String> sheetNames) {
        Set<String> names = new HashSet<>(sheetNames);
        Map<String, Entry> out = new LinkedHashMap<>();
        String key = null;
        for (int ri = 0; ri < rows.size(); ri++) {
            List<String> seq = new ArrayList<>();
            for (String v : rows.get(ri)) {
                if (v == null || PyText.strip(v).isEmpty()) {
                    continue;
                }
                if (names.contains(v)) {
                    key = v;
                }
                String flag = seqFlag(v);
                if (flag != null) {
                    if (key != null) {
                        Entry entry = out.computeIfAbsent(key, k -> new Entry());
                        if (!entry.flags.contains(flag)) {
                            entry.flags.add(flag);
                        }
                        if (entry.role.equals("mandatory")) {
                            if (flag.equals("FW_Exclude") || flag.equals("FW_Heading")) {
                                entry.role = "excluded";
                            } else if (flag.equals("FW_Optional")) {
                                entry.role = "optional";
                            }
                        }
                    }
                } else if (v.startsWith("FW_Concatenator")) {
                    if (key != null) {
                        out.computeIfAbsent(key, k -> new Entry()).concat = v.replaceFirst(Pattern.quote("FW_Concatenator="), "");
                    }
                } else if (v.startsWith("FW_")) {
                    seq.add(v);
                }
            }
            seq = promote(seq);
            if (!seq.isEmpty() && key != null) {
                Entry entry = out.computeIfAbsent(key, k -> new Entry());
                entry.directives = List.copyOf(seq);
                entry.row = ri;
            }
        }
        return out;
    }

    private static String seqFlag(String v) {
        for (String suffix : SEQ_FLAG_SUFFIXES) {
            if (v.endsWith(suffix)) {
                return suffix;
            }
        }
        return null;
    }

    /** promote_directives: a row with exactly one combination-rule verb and no brace gets that verb again. */
    private static List<String> promote(List<String> seq) {
        if (seq.isEmpty() || seq.stream().anyMatch(s -> PyText.firstLine(s).startsWith("FW_("))) {
            return seq;
        }
        List<String> combo = seq.stream().filter(s -> comboRuleVerb(PyText.firstLine(s))).toList();
        if (combo.size() != 1) {
            return seq;
        }
        List<String> out = new ArrayList<>(seq);
        out.add(combo.getFirst());
        return out;
    }

    private static boolean comboRuleVerb(String v) {
        return v.startsWith("FW_Combi(") || v.startsWith("FW_CombiR(") || v.startsWith("FW_Permut(") || v.startsWith("FW_PermutR(")
                || v.equals("FW_Subsets") || v.startsWith("FW_Subsets(");
    }

    /** The program's entries in FW_Seq row order (entries without directives last), as fwgen sorted them. */
    private static List<Map.Entry<String, Entry>> byRow(Map<String, Entry> program) {
        List<Map.Entry<String, Entry>> ordered = new ArrayList<>(program.entrySet());
        ordered.sort(Comparator.comparing((Map.Entry<String, Entry> e) -> e.getValue().row == null)
                .thenComparing(e -> e.getValue().row == null ? 0 : e.getValue().row));
        return ordered;
    }

    /** _workbook_cell_text: a cell's value as fwgen read it from openpyxl (a formula with its "=", a blank as nothing). */
    private static String cellText(InputCell cell) {
        return switch (cell.kind()) {
            case BLANK -> null;
            case FORMULA -> "=" + cell.text();
            default -> cell.text();
        };
    }

    /** _workbook_sheet_values: how many values the Core reads from a data sheet. */
    private static int valueCount(InputSheet sheet) {
        int count = 0;
        for (InputRow row : sheet.rows()) {
            for (InputCell cell : row.cells()) {
                if (cell.kind() == CellKind.BLANK) {
                    continue;
                }
                String text = cell.kind() == CellKind.FORMULA ? cell.text()
                        : cell.kind() == CellKind.TEXT && cell.text().startsWith("=") ? cell.text().substring(1) : cell.text();
                if (!text.startsWith("FW_") || (text.contains("FW_VAR") && text.contains("FW_EXIT_CODE") && FW_VAR_EXIT.matcher(text).lookingAt())
                        || text.startsWith("FW_EMPTY_STRING") || text.startsWith("FW_File=")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static List<InputRow> sortedRows(InputSheet sheet) {
        List<InputRow> rows = new ArrayList<>(sheet.rows());
        rows.sort(Comparator.comparingInt(InputRow::sourceRow));
        return rows;
    }

    private static List<InputCell> sortedCells(InputRow row) {
        List<InputCell> cells = new ArrayList<>(row.cells());
        cells.sort(Comparator.comparingInt(InputCell::column));
        return cells;
    }

    // ---- validation ---------------------------------------------------------------------------------------------

    /** validate: every structural issue, errors and warnings, in the legacy order. */
    public List<Issue> validate() {
        List<Issue> issues = new ArrayList<>();
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(EDGE_ORDER);
        for (Edge e : sorted) {
            if (!nodes.containsKey(e.dst())) {
                issues.add(new Issue(ERROR, "missing_operand", "edge " + e.src() + " --" + e.kind() + "--> " + PyText.repr(e.dst())
                        + ": operand sheet " + PyText.repr(e.dst()) + " is not a declared node", List.of(e.src(), e.dst())));
            }
            if (!nodes.containsKey(e.src())) {
                issues.add(new Issue(ERROR, "missing_operand", "edge source " + PyText.repr(e.src()) + " (--" + e.kind() + "--> " + e.dst()
                        + ") is not a declared node", List.of(e.src(), e.dst())));
            }
        }
        issues.addAll(cycles());
        Map<String, Set<String>> consumers = new TreeMap<>(PyText.CODE_POINT_ORDER);
        for (Edge e : edges) {
            if (e.kind().equals("brace_operand") || e.kind().equals("brace_nested_operand")) {
                consumers.computeIfAbsent(e.dst(), x -> new TreeSet<>(PyText.CODE_POINT_ORDER)).add(e.src());
            }
        }
        consumers.forEach((slot, braces) -> {
            Node node = nodes.get(slot);
            if (braces.size() > 1 && !(node != null && node.is("keeps_rows"))) {
                issues.add(new Issue(ERROR, "consumed_result_ambiguity", "result of " + PyText.repr(slot) + " is consumed by " + braces.size()
                        + " braces (" + String.join(", ", braces) + ") but its rows are cleaned after the first join (no FW_Reuse), so the "
                        + "later joins would read an empty operand — mark it FW_Reuse to reuse its data", List.of(slot)));
            }
            boolean nested = edges.stream().anyMatch(e -> e.dst().equals(slot) && e.kind().equals("brace_nested_operand"));
            if (node != null && !nested && !node.is("excluded")) {
                issues.add(new Issue(ERROR, "consumed_result_ambiguity", "brace operand " + PyText.repr(slot) + " is consumed as a prior "
                        + "result but is not FW_Exclude'd — it stays an independent axis AND is joined, so which table feeds the brace "
                        + "is ambiguous", List.of(slot)));
            }
        });
        Set<String> consumed = new HashSet<>(consumers.keySet());
        List<String> ids = new ArrayList<>(nodes.keySet());
        ids.sort(PyText.CODE_POINT_ORDER);
        for (String id : ids) {
            Node node = nodes.get(id);
            if (node.kind().equals("slot") && node.is("excluded") && !consumed.contains(id)) {
                issues.add(new Issue(WARNING, "unused_advanced_node", "slot " + PyText.repr(id) + " is FW_Exclude'd (set aside from the "
                        + "cartesian) but never consumed by a brace operand — it is unreachable and contributes nothing", List.of(id)));
            }
        }
        return issues;
    }

    /** The error-level issues, which reject a spec document before its workbook is written. */
    public List<Issue> errors() {
        return validate().stream().filter(i -> i.level().equals(ERROR)).toList();
    }

    private List<Issue> cycles() {
        Map<String, List<String>> adj = new HashMap<>();
        for (String n : nodes.keySet()) {
            adj.put(n, new ArrayList<>());
        }
        for (Edge e : edges) {
            if (adj.containsKey(e.src()) && nodes.containsKey(e.dst())) {
                adj.get(e.src()).add(e.dst());
            }
        }
        Map<String, Integer> color = new HashMap<>();
        List<List<String>> found = new ArrayList<>();
        List<String> order = new ArrayList<>(nodes.keySet());
        order.sort(PyText.CODE_POINT_ORDER);
        for (String n : order) {
            if (color.getOrDefault(n, 0) == 0) {
                dfs(n, new ArrayList<>(), adj, color, found);
            }
        }
        Set<Set<String>> seen = new HashSet<>();
        List<Issue> out = new ArrayList<>();
        for (List<String> cycle : found) {
            if (seen.add(new HashSet<>(cycle))) {
                out.add(new Issue(ERROR, "cycle", "dependency cycle: " + String.join(" -> ", cycle), List.copyOf(new LinkedHashSet<>(cycle))));
            }
        }
        return out;
    }

    private static void dfs(String u, List<String> stack, Map<String, List<String>> adj, Map<String, Integer> color, List<List<String>> found) {
        color.put(u, 1);
        stack.add(u);
        List<String> next = new ArrayList<>(adj.get(u));
        next.sort(PyText.CODE_POINT_ORDER);
        for (String v : next) {
            int c = color.getOrDefault(v, 0);
            if (c == 1) {
                List<String> cycle = new ArrayList<>(stack.subList(stack.indexOf(v), stack.size()));
                cycle.add(v);
                found.add(cycle);
            } else if (c == 0) {
                dfs(v, stack, adj, color, found);
            }
        }
        stack.removeLast();
        color.put(u, 2);
    }

    // ---- serializations ---------------------------------------------------------------------------------------

    /** to_json: the canonical projection, nodes by id and edges by source, target and kind. */
    public RuleValue toJson() {
        List<Node> sortedNodes = new ArrayList<>(nodes.values());
        sortedNodes.sort(Comparator.comparing(Node::id, PyText.CODE_POINT_ORDER));
        List<RuleValue> n = new ArrayList<>();
        for (Node node : sortedNodes) {
            Map<String, RuleValue> m = new LinkedHashMap<>();
            m.put("id", new RuleValue.Text(node.id()));
            m.put("kind", new RuleValue.Text(node.kind()));
            m.put("attrs", new RuleValue.MapValue(node.attrs()));
            n.add(new RuleValue.MapValue(m));
        }
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(EDGE_ORDER);
        List<RuleValue> e = new ArrayList<>();
        for (Edge edge : sorted) {
            Map<String, RuleValue> m = new LinkedHashMap<>();
            m.put("src", new RuleValue.Text(edge.src()));
            m.put("dst", new RuleValue.Text(edge.dst()));
            m.put("kind", new RuleValue.Text(edge.kind()));
            e.add(new RuleValue.MapValue(m));
        }
        Map<String, RuleValue> doc = new LinkedHashMap<>();
        doc.put("nodes", new RuleValue.ListValue(n));
        doc.put("edges", new RuleValue.ListValue(e));
        return new RuleValue.MapValue(doc);
    }

    /** The JSON as fwgen graph printed it: two-space indented, non-ASCII kept, with a final line break. */
    public String json() {
        return ConstraintBonds.pretty(toJson());
    }

    /** to_dot: the Graphviz text, nodes and edges in their canonical order. */
    public String dot() {
        StringBuilder out = new StringBuilder("digraph fwseq {\n  rankdir=LR;\n");
        List<Node> sortedNodes = new ArrayList<>(nodes.values());
        sortedNodes.sort(Comparator.comparing(Node::id, PyText.CODE_POINT_ORDER));
        for (Node n : sortedNodes) {
            List<String> tags = tags(n);
            String label = n.id() + (tags.isEmpty() ? "" : "\\n[" + String.join(",", tags) + "]");
            out.append("  \"").append(n.id()).append("\" [shape=").append(n.kind().equals("brace") ? "box" : "ellipse").append(", label=\"")
                    .append(label).append("\"];\n");
        }
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(EDGE_ORDER);
        for (Edge e : sorted) {
            out.append("  \"").append(e.src()).append("\" -> \"").append(e.dst()).append("\" [label=\"").append(e.kind()).append("\"];\n");
        }
        return out.append("}\n").toString();
    }

    /** The tags the DOT label shows under a node's id. */
    public static List<String> tags(Node n) {
        List<String> tags = new ArrayList<>();
        if (n.is("excluded")) {
            tags.add("excluded");
        }
        if (n.is("optional")) {
            tags.add("optional");
        }
        if (n.is("heading")) {
            tags.add("heading");
        }
        if (n.is("group")) {
            tags.add("group");
        }
        if (n.is("is_join_result")) {
            tags.add("join-result");
        }
        return tags;
    }

    /** graph_hash: sha256 of the canonical JSON with sorted keys, as bundle plan recorded it. */
    public String hash() {
        try {
            byte[] canonical = ConstraintBonds.pythonJson(toJson(), true, false).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this runtime", e);
        }
    }

    /** The first line fwgen graph printed for a spec: its name, the counts and the hash. */
    public String summary(String name) {
        return name + ": " + nodes.size() + " nodes, " + edges.size() + " edges   " + hash();
    }
}
