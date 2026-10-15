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
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import org.ghyura.cf.config.ConfigKeys;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.DocTree.Arr;
import org.ghyura.cf.input.DocTree.Leaf;
import org.ghyura.cf.input.DocTree.Obj;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.rule.RuleValue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an fwgen spec document (bundle.spec/v1, or a legacy spec without {@code spec_version}; TOML or JSON) the way
 * the legacy {@code fwgen.parse_spec} did in its default compatibility mode, runs the legacy FW_Seq dependency gate
 * ({@code fwseq_graph.validate_spec_graph}) and compiles it as {@code build_compact_core_json} did: into the workbook
 * the Core read, plus the constraint sidecar {@code emit_sidecar} wrote and the goals and coverage choices the run
 * applied. Messages are the legacy ones, so a spec fails here exactly where it failed before; what the legacy
 * compatibility mode printed and went on with is a warning.
 *
 * <p>Deliberate differences (compatibility delta D57): the spec's constraints also pass the strict sidecar checks;
 * two slots may not declare one sheet; a goal direction other than min or max is refused (the legacy turned it into a
 * target without a value); a TOML date or time inside a list or table, YAML specs and malformed shapes the legacy
 * crashed on are refused with a message; a byte order mark is ignored.</p>
 */
public final class SpecReader {

    /** The authoring contract this reader implements. */
    public static final String FORMAT = "bundle.spec/v1";

    private static final String DEFAULT_VERB = "FW_Combi(1)";
    private static final List<String> KNOWN = List.of("spec_version", "title", "note", "args", "runme", "slots", "goals",
            "custom_vars", "params", "constraints", "seq_extra", "orders", "coverage_strength", "coverage_optimal", "coverage_budget");
    private static final List<String> SLOT_KNOWN = List.of("sheet", "key", "values", "verb", "flags", "raw", "prefix", "ending",
            "separator", "group_replace", "alias");
    private static final List<String> GOAL_KNOWN = List.of("key", "dir", "direction");
    private static final List<String> CUSTOM_VAR_KNOWN = List.of("code", "msg");
    private static final List<String> CONSTRAINT_KNOWN = List.of("id", "sheets", "pairs", "sets", "when", "gate", "polarity", "desc",
            "condition", "mapping", "assert");
    private static final List<String> BODIES = List.of("pairs", "sets", "when", "mapping", "assert");
    private static final List<String> ALIASES = List.of("choose_k", "choose_one", "feature_subset", "optional_action", "permute");

    private static final Set<String> COND_NODE = Set.of("all", "any", "not");
    private static final Set<String> COND_SET = Set.of("in", "nin", "hasAny");
    private static final Set<String> COND_COUNT = Set.of("count", "countGe", "countLe");
    private static final List<String> COND_SHEET = List.of("eqSheet", "neSheet", "geSheet", "gtSheet", "leSheet", "ltSheet", "subOf", "supOf");
    private static final Set<String> COND_ORD = Set.of("ge", "gt", "le", "lt");
    private static final Set<String> COND_LEAF;
    private static final Set<String> COND_ALLOWED;

    static {
        Set<String> leaf = new HashSet<>(List.of("eq", "ne", "has", "hasnt", "in", "nin", "hasAny", "present"));
        leaf.addAll(COND_SET);
        leaf.addAll(COND_COUNT);
        leaf.addAll(COND_SHEET);
        leaf.addAll(COND_ORD);
        COND_LEAF = Set.copyOf(leaf);
        Set<String> allowed = new HashSet<>(leaf);
        allowed.addAll(COND_NODE);
        allowed.add("sheet");
        COND_ALLOWED = Set.copyOf(allowed);
    }

    private static final int U = Pattern.UNICODE_CHARACTER_CLASS;
    private static final String ARG_WORD = "(?i:all|full|size)";
    private static final Pattern VERB = Pattern.compile(
            "FW_CombiR(\\(\\d+\\)|\\(" + ARG_WORD + "\\)|\\(\\))?"
                    + "|FW_Combi(\\(\\d+\\)|\\(" + ARG_WORD + "\\)|\\(\\))?"
                    + "|FW_PermutR(\\(\\d+\\)|\\(" + ARG_WORD + "\\)|\\(\\))?"
                    + "|FW_Permut(\\(\\d+\\)|\\(" + ARG_WORD + "\\)|\\(\\s*\\)|\\(PermutationGenerator\\.TreatDuplicatesAs\\.IDENTICAL\\))?"
                    + "|FW_Subsets(_(?i:BEFORE|AFTER|EXACT|RANGE|GIVEN)\\(\\d+(,\\d+)*\\)|\\(\\d+\\))?"
                    + "|FW_Cartes((?i:_first))?\\([A-Za-z0-9_]+\\)"
                    + "|FW_Separator\\([A-Za-z0-9_]+\\)", U);
    private static final String BRACE_OPERAND = "(?:[A-Za-z0-9_]+|FW_\\(\\)G?)";
    private static final Pattern BRACE = Pattern.compile("FW_\\([A-Za-z0-9_]*,," + BRACE_OPERAND + ",[A-Za-z0-9_]*,"
            + BRACE_OPERAND + ",,[A-Za-z0-9_]*,[A-Za-z0-9_]*,(1:[1Nn]|[Mm]:[1MmNn])\\)", U);
    private static final Pattern FLAG = Pattern.compile(
            "FW_Optional|FW_Exclude|FW_Heading|FW_LastInQueue|FW_Reuse|FW_ReuseTableOnly|FW_Concatenator=[^\\n]*", U);
    private static final Pattern OPERAND = Pattern.compile("FW_(?:Cartes(?:(?i:_first))?|Separator)\\(([A-Za-z0-9_]+)\\)", U);
    private static final Pattern ALIAS = Pattern.compile("([a-z_]+)(?:\\((\\d+)\\))?", U);
    private static final Pattern ALIAS_CHAIN = Pattern.compile("permute\\(\\s*\\d+\\s*\\)", U);
    private static final Pattern OPTION = Pattern.compile("(?<!\\\\)\\$\\{([^}]+)\\}", U);
    private static final Pattern INERT_PERMUT = Pattern.compile("FW_Permut\\((\\d+)\\)", U);
    private static final Pattern FIRST_ARG = Pattern.compile("\\((\\d+)\\)", U);
    private static final Pattern ALL_ARG = Pattern.compile("\\((all|full)\\)", Pattern.CASE_INSENSITIVE | U);
    private static final Pattern SIZE_ARG = Pattern.compile("\\(size\\)", Pattern.CASE_INSENSITIVE | U);
    private static final Pattern DIGITS = Pattern.compile("\\d+", U);
    private static final Pattern SUBSET_MODE = Pattern.compile("FW_Subsets_(\\w+)", U);
    private static final Pattern METRIC_KEY = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_.\\-]*");

    private static final List<String> MIN_TOKENS = List.of("cost", "price", "latency", "lat", "error", "err", "loss", "size", "time",
            "duration", "ms", "wait", "far", "frr", "cycles", "length", "p95", "p99", "memory", "ram", "bytes");
    private static final List<String> MAX_TOKENS = List.of("throughput", "tps", "rps", "accuracy", "acc", "f1", "score", "coverage",
            "recall", "precision", "map", "fps", "relevance", "bypass", "crash", "success", "quality", "reward");

    private static final JsonFactory JSON = JsonFactory.builder()
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .build();
    private static final Pattern TOML_SPEC = Pattern.compile(
            "(?m)^[ \\t]*(?:\\[\\[[ \\t]*(?:slots|\"slots\"|'slots')[ \\t]*\\]\\]|(?:slots|\"slots\"|'slots')[ \\t]*=)");
    private static final Pattern YAML_SPEC = Pattern.compile("(?m)^slots[ \\t]*:");

    // ---- results --------------------------------------------------------------------------------------------

    /** A slot written with a domain alias, and the expert verb, flags and chained directives it compiled to. */
    public record AliasUse(String sheet, String alias, String verb, List<String> flags, List<String> chain) {
        public AliasUse {
            flags = List.copyOf(flags);
            chain = List.copyOf(chain);
        }
    }

    /**
     * What a spec says besides its workbook: its name (the file stem), contract version, title, note, slot count, the
     * aliases it used and the settings it chose (goals and coverage, applied below every explicit setting).
     */
    public record Document(String name, String specVersion, String title, String note, int slots, List<AliasUse> aliases,
                           List<ConfigResolver.ScenarioValue> settings) {
        public Document {
            Objects.requireNonNull(name, "name");
            aliases = List.copyOf(aliases);
            settings = List.copyOf(settings);
        }
    }

    /**
     * A compiled spec: the workbook the Core read, the constraint sidecar (as {@code emit_sidecar} wrote it, before the
     * strict checks) and the constraint layer read from it, with the compatibility warnings in the order printed, and the
     * FW_Seq dependency graph fwgen gated it with.
     */
    public record Compiled(Document document, WorkbookData workbook, DocTree.Obj sidecar, ConstraintSet constraints,
                           List<Diagnostic> warnings, SeqGraph graph) {
        public Compiled {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(workbook, "workbook");
            Objects.requireNonNull(sidecar, "sidecar");
            Objects.requireNonNull(constraints, "constraints");
            Objects.requireNonNull(graph, "graph");
            warnings = List.copyOf(warnings);
        }

        /** The sheets of the compiled workbook as the Core's JSON schedule writes them (compact; absent cells are null). */
        public String sheetsJson() {
            StringBuilder out = new StringBuilder("{");
            boolean firstSheet = true;
            for (InputSheet sheet : workbook.sheets()) {
                if (!firstSheet) {
                    out.append(',');
                }
                firstSheet = false;
                DocTree.appendJsonString(out, sheet.name());
                out.append(":[");
                for (int r = 0; r < sheet.rows().size(); r++) {
                    if (r > 0) {
                        out.append(',');
                    }
                    InputRow row = sheet.rows().get(r);
                    out.append('[');
                    int width = row.cells().isEmpty() ? 0 : row.cells().getLast().column() + 1;
                    Map<Integer, String> byColumn = new HashMap<>();
                    row.cells().forEach(c -> byColumn.put(c.column(), c.text()));
                    for (int c = 0; c < width; c++) {
                        if (c > 0) {
                            out.append(',');
                        }
                        String text = byColumn.get(c);
                        if (text == null) {
                            out.append("null");
                        } else {
                            DocTree.appendJsonString(out, text);
                        }
                    }
                    out.append(']');
                }
                out.append(']');
            }
            return out.append('}').toString();
        }

        /** The constraint sidecar fwgen's emit_sidecar wrote for this spec (compact JSON). */
        public String sidecarJson() {
            return DocTree.toJson(sidecar);
        }
    }

    // ---- detection ------------------------------------------------------------------------------------------

    /**
     * Whether a scenario file in this format is an fwgen spec document rather than a workbook: a JSON document whose top
     * level has {@code slots}, or a TOML document with a top-level {@code [[slots]]} table or {@code slots} key (the
     * Core's TOML schedule has {@code [sheets]}). A YAML spec is refused (no YAML reader for full documents).
     *
     * @throws FrameworkException INPUT_UNSUPPORTED_SYNTAX for a YAML spec document
     */
    public static boolean isSpec(InputFormat format, InputSource source) {
        if (format == InputFormat.XLSX) {
            return false;
        }
        String text;
        try {
            text = Utf8.decode(source);
        } catch (FrameworkException e) {
            return false;
        }
        return switch (format) {
            case TOML -> TOML_SPEC.matcher(text).find() && tomlHasSlots(source.name(), text);
            case JSON -> jsonHasSlots(text);
            case YAML -> {
                if (YAML_SPEC.matcher(text).find()) {
                    throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, "this YAML document is an fwgen "
                            + "spec (it has top-level slots); spec documents are read from TOML or JSON only: write it as .toml or "
                            + ".json", SourceLocation.of(source.name())));
                }
                yield false;
            }
            default -> false;
        };
    }

    private static boolean tomlHasSlots(String origin, String text) {
        try {
            return TomlReader.read(origin, newlines(text)).fields().containsKey("slots");
        } catch (FrameworkException e) {
            // Let the spec reader report its located syntax or limit diagnostic.
            return true;
        }
    }

    private static boolean jsonHasSlots(String text) {
        try (JsonParser parser = JSON.createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return false;
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                if (parser.currentName().equals("slots")) {
                    return true;
                }
                parser.nextToken();
                parser.skipChildren();
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /** Python's text mode: CR LF and a lone CR read as LF. */
    private static String newlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    // ---- reading --------------------------------------------------------------------------------------------

    /**
     * Reads and compiles a spec document.
     *
     * @throws FrameworkException INPUT_MALFORMED for a document that is not TOML or JSON, INPUT_VERSION_UNSUPPORTED for
     *                            an unknown spec_version, INPUT_SCHEMA (with the legacy message) for a spec the legacy
     *                            refused or could not read, and the strict sidecar errors of its constraints
     */
    public static Compiled read(InputSource source, InputFormat format) {
        return reader(source, format).compile(source);
    }

    /**
     * The FW_Seq dependency graph of a spec document as fwgen graph built it from the parsed spec, whether or not the
     * gate would reject it (an error-level issue rejects the spec in {@link #read}).
     *
     * @throws FrameworkException when the document fails before its slots and seq_extra rows are read
     */
    public static SeqGraph graph(InputSource source, InputFormat format) {
        SpecReader reader = reader(source, format);
        try {
            return reader.compile(source).graph();
        } catch (FrameworkException e) {
            if (reader.graph != null) {
                return reader.graph;
            }
            throw e;
        }
    }

    private static SpecReader reader(InputSource source, InputFormat format) {
        String origin = source.name();
        String text = newlines(Utf8.decode(source));
        Obj root = switch (format) {
            case TOML -> TomlReader.read(origin, text);
            case JSON -> json(origin, text);
            default -> throw new IllegalArgumentException("spec documents are TOML or JSON, not " + format);
        };
        String fileName = origin.substring(Math.max(origin.lastIndexOf('/'), origin.lastIndexOf('\\')) + 1);
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        return new SpecReader(origin, stem, root);
    }

    private static Obj json(String origin, String text) {
        List<Diagnostic> duplicates = new ArrayList<>();
        DocTree root;
        try (JsonParser parser = JSON.createParser(text)) {
            if (parser.nextToken() == null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, "the spec document is empty", SourceLocation.of(origin)));
            }
            root = DocTree.readJson(parser, origin, "", (key, at) -> duplicates.add(Diagnostic.error(ErrorCode.INPUT_MALFORMED,
                    "the key \"" + key + "\" appears twice in one object (the legacy json.loads kept the last silently)", at)));
            if (parser.nextToken() != null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, "content after the end of the JSON document",
                        DocTree.at(origin, parser.currentTokenLocation(), "")));
            }
        } catch (JsonProcessingException e) {
            JsonLocation l = e.getLocation();
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, "not valid JSON: " + e.getOriginalMessage(),
                    l == null ? SourceLocation.of(origin) : DocTree.at(origin, l, "")));
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, "the spec cannot be read: " + e.getMessage(),
                    SourceLocation.of(origin)));
        }
        if (!duplicates.isEmpty()) {
            throw new FrameworkException(duplicates);
        }
        if (!(root instanceof Obj top)) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_SCHEMA, "a spec document is an object with 'slots'", root.where()));
        }
        return top;
    }

    // ---- the parsed spec, as fwgen's Slot and Spec ----------------------------------------------------------

    private record Slot(String sheet, String key, List<String> values, List<DocTree> valueNodes, String verb, List<String> flags,
                        boolean raw, String prefix, String ending, String separator, List<String[]> groupReplace, String alias,
                        List<String> chain, Obj node) {
    }

    private record ExtraRow(List<String> cells, List<DocTree> nodes) {
    }

    private record CustomVar(BigInteger code, String msg, DocTree node) {
    }

    private record Goal(String key, String direction, DocTree node) {
    }

    private final String origin;
    private final String name;
    private final Obj raw;
    private SeqGraph graph;
    private final List<Diagnostic> warnings = new ArrayList<>();
    private int expandedValues;
    private long expansionBytes;

    private SpecReader(String origin, String name, Obj raw) {
        this.origin = origin;
        this.name = name;
        this.raw = raw;
    }

    private FrameworkException refuse(String message, DocTree where) {
        return refuse(ErrorCode.INPUT_SCHEMA, message, where == null ? SourceLocation.of(origin) : where.where());
    }

    private static FrameworkException refuse(ErrorCode code, String message, SourceLocation where) {
        return new FrameworkException(Diagnostic.error(code, message, where));
    }

    private void warn(String message, DocTree where) {
        warnings.add(Diagnostic.warning(ErrorCode.INPUT_AUTHORING_WARNING, message, where.where()));
    }

    private DocTree get(Obj o, String key) {
        return o.fields().get(key);
    }

    /** {@code str()} of a part, refusing what the port cannot render as the legacy did. */
    private String str(DocTree n) {
        try {
            return PyText.str(n);
        } catch (PyText.NotRenderable e) {
            throw refuse(e.getMessage(), n);
        }
    }

    private String repr(DocTree n) {
        try {
            return PyText.repr(n);
        } catch (PyText.NotRenderable e) {
            throw refuse(e.getMessage(), n);
        }
    }

    private BigInteger integer(DocTree n) {
        try {
            return PyText.integer(n);
        } catch (PyText.IntError e) {
            throw refuse(e.getMessage(), n);
        }
    }

    private Arr list(DocTree n, String what) {
        if (n instanceof Arr a) {
            return a;
        }
        throw refuse("spec '" + name + "' " + what + " must be a list", n);
    }

    private Obj table(DocTree n, String what) {
        if (n instanceof Obj o) {
            return o;
        }
        throw refuse("spec '" + name + "' " + what + " must be a table", n);
    }

    private void checkUnknownKeys(Obj o, List<String> known, String where) {
        TreeSet<String> unknown = new TreeSet<>(PyText.CODE_POINT_ORDER);
        for (String k : o.fields().keySet()) {
            if (!known.contains(k)) {
                unknown.add(k);
            }
        }
        if (!unknown.isEmpty()) {
            warn("spec '" + name + "': " + where + " unknown field(s) " + PyText.reprList(List.copyOf(unknown)) + " ignored (compatibility mode)", o);
        }
    }

    // ---- parse_spec -----------------------------------------------------------------------------------------

    private Compiled compile(InputSource source) {
        DocTree versionNode = get(raw, "spec_version");
        String specVersion = versionNode == null ? "legacy" : str(versionNode);
        if (!specVersion.equals("legacy") && !specVersion.equals("1")) {
            throw refuse(ErrorCode.INPUT_VERSION_UNSUPPORTED, "spec '" + name + "' has unsupported spec_version " + PyText.repr(specVersion)
                    + " (supported: ['1', 'legacy'])", versionNode.where());
        }
        checkUnknownKeys(raw, KNOWN, "top level");

        List<Slot> slots = new ArrayList<>();
        DocTree slotsNode = get(raw, "slots");
        if (slotsNode != null) {
            for (DocTree item : list(slotsNode, "'slots'").items()) {
                slots.add(slot(table(item, "a slot")));
            }
        }
        if (slots.isEmpty()) {
            throw refuse("spec '" + name + "' has no slots", slotsNode == null ? raw : slotsNode);
        }
        Map<String, Slot> bySheet = new LinkedHashMap<>();
        Set<String> folded = new HashSet<>();
        for (Slot s : slots) {
            if (bySheet.containsKey(s.sheet())) {
                throw refuse(ErrorCode.INPUT_DUPLICATE_SHEET, "two slots declare the sheet '" + s.sheet() + "' (the legacy compiler kept "
                        + "only the last slot's values)", s.node().where());
            }
            if (!folded.add(s.sheet().toLowerCase(Locale.ROOT))) {
                throw refuse(ErrorCode.INPUT_DUPLICATE_SHEET, "the sheet '" + s.sheet() + "' is declared twice ignoring case (the legacy "
                        + "Core refused it when it built the workbook)", s.node().where());
            }
            bySheet.put(s.sheet(), s);
        }
        for (Slot s : slots) {
            if (!s.separator().isEmpty() && !bySheet.containsKey(s.separator())) {
                throw refuse("slot '" + s.sheet() + "' separator '" + s.separator() + "' is not a declared sheet", s.node());
            }
        }
        List<ExtraRow> seqExtra = seqExtra(bySheet);
        // fwgen graph built the graph of the parsed spec; the gate below rejects its errors where build_compact_core_json did
        graph = SeqGraph.ofSpec(slots.stream().map(s -> new SeqGraph.SlotInput(s.sheet(), s.verb(), s.values().size(), s.flags(),
                !s.groupReplace().isEmpty(), s.separator(), s.chain())).toList(), seqExtra.stream().map(ExtraRow::cells).toList());
        Map<String, DocTree> params = params(bySheet);
        List<Obj> constraints = constraints(bySheet);
        Map<String, DocTree> orders = orders(bySheet);
        List<Goal> goals = goals();
        List<CustomVar> customVars = customVars();
        List<String> args = new ArrayList<>();
        List<DocTree> argNodes = new ArrayList<>();
        DocTree argsNode = get(raw, "args");
        if (argsNode != null) {
            for (DocTree a : list(argsNode, "'args'").items()) {
                args.add(str(a));
                argNodes.add(a);
            }
        }
        DocTree runmeNode = get(raw, "runme");
        String runme = runmeNode != null && PyText.truthy(runmeNode) ? str(runmeNode)
                : "class RunMeFirstOnce { public static String FW_ARGS; public static void main(String[] args){ FW_ARGS = \"\"; "
                + "System.out.println(\"" + name + " search space\"); } }";
        List<ConfigResolver.ScenarioValue> settings = new ArrayList<>(goalSetting(goals));
        coverage(settings);
        DocTree titleNode = get(raw, "title");
        DocTree noteNode = get(raw, "note");

        gate();

        Obj sidecar = sidecar(params, constraints, orders);
        ConstraintSet layer = params.isEmpty() && constraints.isEmpty() && orders.isEmpty() ? ConstraintSet.NONE
                : ConstraintSidecarReader.fromTree(origin, sidecar, new ConstraintSet.Source(origin, Canonical.sha256Hex(source.copyOfBytes()), "spec"));
        WorkbookData workbook = workbook(slots, seqExtra, runme, runmeNode, args, argNodes, customVars);
        List<AliasUse> aliases = slots.stream().filter(s -> !s.alias().isEmpty())
                .map(s -> new AliasUse(s.sheet(), s.alias(), s.verb(), s.flags(), s.chain())).toList();
        Document document = new Document(name, specVersion, titleNode == null ? name : str(titleNode), noteNode == null ? "" : str(noteNode),
                slots.size(), aliases, settings);
        return new Compiled(document, workbook, sidecar, layer, warnings, graph);
    }

    private Slot slot(Obj s) {
        DocTree sheetNode = get(s, "sheet");
        checkUnknownKeys(s, SLOT_KNOWN, "slot '" + (sheetNode == null ? "?" : str(sheetNode)) + "'");
        if (!(sheetNode instanceof Leaf sl && sl.value() instanceof RuleValue.Text sheetText && sl.temporal() == DocTree.Temporal.NONE)) {
            throw refuse("spec '" + name + "' slot needs 'sheet', the name of its data sheet", sheetNode == null ? s : sheetNode);
        }
        String sheet = sheetText.value();
        if (sheet.startsWith("FW_")) {
            throw refuse("slot sheet '" + sheet + "' must not start with FW_ (it would be read as a control sheet)", sheetNode);
        }
        if (sheet.isBlank()) {
            throw refuse("spec '" + name + "' slot needs a sheet name that is not blank", sheetNode);
        }
        DocTree rawNode = get(s, "raw");
        boolean isRaw = rawNode != null && PyText.truthy(rawNode);
        DocTree valuesNode = get(s, "values");
        if (valuesNode == null) {
            throw refuse("spec '" + name + "' slot '" + sheet + "' needs 'values'", s);
        }
        List<String> values = new ArrayList<>();
        List<DocTree> valueNodes = new ArrayList<>();
        for (DocTree v : list(valuesNode, "slot '" + sheet + "' values").items()) {
            String text = str(v);
            if (isRaw) admitExpansion(1, text.length(), v);
            for (String expanded : isRaw ? List.of(text) : expandOptions(text, v)) {
                values.add(expanded);
                valueNodes.add(v);
            }
        }
        DocTree aliasNode = get(s, "alias");
        String aliasToken = aliasNode == null ? "" : PyText.strip(str(aliasNode));
        List<String> aliasFlags = List.of();
        List<String> chain = List.of();
        String verb;
        if (!aliasToken.isEmpty()) {
            DocTree verbNode = get(s, "verb");
            if (verbNode != null) {
                throw refuse("slot '" + sheet + "' sets BOTH alias " + PyText.repr(aliasToken) + " and raw verb " + PyText.repr(str(verbNode))
                        + " — choose one (an alias already compiles to a verb; it cannot also carry an explicit verb)", verbNode);
            }
            String[] compiled = compileAlias(aliasToken, sheet, aliasNode);
            verb = compiled[0];
            aliasFlags = compiled.length > 1 ? List.of(compiled[1]) : List.of();
            chain = ALIAS_CHAIN.matcher(PyText.strip(str(aliasNode))).matches() ? List.of("FW_Permut()") : List.of();
        } else {
            DocTree verbNode = get(s, "verb");
            verb = verbNode == null ? DEFAULT_VERB : PyText.strip(str(verbNode));
        }
        if (!isCoreVerb(verb)) {
            throw refuse("slot '" + sheet + "' verb " + PyText.repr(verb) + " is not a recognized Core FW_Seq verb (e.g. FW_Combi(2), "
                    + "FW_CombiR(2), FW_Permut, FW_PermutR(2), FW_Subsets, FW_Subsets_EXACT(2), FW_Cartes(OTHER), FW_Group)", s);
        }
        List<String> flags = new ArrayList<>();
        DocTree flagsNode = get(s, "flags");
        if (flagsNode != null) {
            for (DocTree f : list(flagsNode, "slot '" + sheet + "' flags").items()) {
                flags.add(PyText.strip(str(f)));
            }
        }
        for (String f : aliasFlags) {
            if (!flags.contains(f)) {
                flags.add(f);
            }
        }
        List<String> bad = flags.stream().filter(f -> !FLAG.matcher(PyText.strip(f)).matches()).toList();
        if (!bad.isEmpty()) {
            throw refuse("slot '" + sheet + "' has unrecognized flag(s) " + PyText.reprList(bad), flagsNode == null ? s : flagsNode);
        }
        DocTree separatorNode = get(s, "separator");
        String separator = separatorNode == null ? "" : PyText.strip(str(separatorNode));
        List<String[]> groupReplace = new ArrayList<>();
        DocTree grNode = get(s, "group_replace");
        if (grNode != null) {
            for (DocTree e : list(grNode, "slot '" + sheet + "' group_replace").items()) {
                if (!(e instanceof Arr pair) || pair.items().size() != 2) {
                    throw refuse("slot '" + sheet + "' group_replace entries must be [pattern, replacement], got " + repr(e), e);
                }
            }
            for (DocTree e : ((Arr) grNode).items()) {
                List<DocTree> pair = ((Arr) e).items();
                groupReplace.add(new String[]{str(pair.get(0)), str(pair.get(1))});
            }
        }
        checkInertPermutArg(sheet, verb, values, s);
        if (isRaw) {
            checkRawLiteralNewline(sheet, values, s);
            checkRawRowArity(sheet, verb, values, flags, s);
        }
        DocTree keyNode = get(s, "key");
        String key = keyNode == null ? sheet.toLowerCase(Locale.ROOT) : str(keyNode);
        DocTree prefixNode = get(s, "prefix");
        DocTree endingNode = get(s, "ending");
        return new Slot(sheet, key, values, valueNodes, verb, List.copyOf(flags), isRaw, prefixNode == null ? "" : str(prefixNode),
                endingNode == null ? "" : str(endingNode), separator, groupReplace, aliasToken, chain, s);
    }

    /** fwgen's compile_alias: {@code [verb]} or {@code [verb, flag]}. */
    private String[] compileAlias(String token, String sheet, DocTree where) {
        Matcher m = ALIAS.matcher(PyText.firstLine(token));
        if (!m.matches() || !ALIASES.contains(m.group(1))) {
            throw refuse("slot '" + sheet + "': unknown authoring alias " + PyText.repr(token) + " (supported: " + String.join(", ", ALIASES) + ")",
                    where);
        }
        String alias = m.group(1);
        BigInteger k = m.group(2) == null ? null : PyText.parseInt(m.group(2));
        String prefix = "slot '" + sheet + "': ";
        switch (alias) {
            case "choose_one" -> {
                noArg(alias, k, token, prefix, where);
                return new String[]{"FW_Combi(1)"};
            }
            case "choose_k" -> {
                if (k == null) {
                    throw refuse(prefix + "alias 'choose_k' requires a (k) argument, e.g. choose_k(2); got " + PyText.repr(token), where);
                }
                if (k.signum() < 1) {
                    throw refuse(prefix + "alias 'choose_k(k)' needs k >= 1, got " + PyText.repr(token), where);
                }
                return new String[]{"FW_Combi(" + k + ")"};
            }
            case "permute" -> {
                if (k != null && k.signum() < 1) {
                    throw refuse(prefix + "alias 'permute(k)' needs k >= 1, got " + PyText.repr(token), where);
                }
                return new String[]{k != null ? "FW_Combi(" + k + ")" : "FW_Permut"};
            }
            case "feature_subset" -> {
                return new String[]{k != null ? "FW_Subsets(" + k + ")" : "FW_Subsets"};
            }
            default -> {
                noArg(alias, k, token, prefix, where);
                return new String[]{"FW_Combi(1)", "FW_Optional"};
            }
        }
    }

    private void noArg(String alias, BigInteger k, String token, String prefix, DocTree where) {
        if (k != null) {
            throw refuse(prefix + "alias " + PyText.repr(alias) + " takes no (k) argument, got " + PyText.repr(token), where);
        }
    }

    static boolean isCoreVerb(String token) {
        String first = PyText.firstLine(token);
        if (first.startsWith("FW_Group")) {
            return true;
        }
        return VERB.matcher(first).matches() || BRACE.matcher(first).matches();
    }

    /** fwgen's expand_options: every combination of the {@code ${a|b}} groups, the last group fastest. */
    private List<String> expandOptions(String value, DocTree where) {
        List<String> texts = new ArrayList<>();
        List<String[]> groups = new ArrayList<>();
        Matcher m = OPTION.matcher(value);
        int last = 0;
        while (m.find()) {
            texts.add(value.substring(last, m.start()));
            groups.add(m.group(1).split("\\|", -1));
            last = m.end();
        }
        if (groups.isEmpty()) {
            admitExpansion(1, value.length(), where);
            return List.of(value);
        }
        texts.add(value.substring(last));
        long count = 1;
        long length = texts.stream().mapToLong(String::length).sum();
        for (String[] group : groups) {
            count *= group.length;
            requireValueCapacity(count, where);
            length += java.util.Arrays.stream(group).mapToInt(String::length).max().orElse(0);
        }
        admitExpansion(count, length, where);
        List<String> out = new ArrayList<>((int) count);
        String[] chosen = new String[groups.size()];
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int rest = ordinal;
            for (int g = groups.size() - 1; g >= 0; g--) {
                String[] group = groups.get(g);
                chosen[g] = group[rest % group.length];
                rest /= group.length;
            }
            StringBuilder text = new StringBuilder((int) length);
            for (int g = 0; g < chosen.length; g++) text.append(texts.get(g)).append(chosen[g]);
            out.add(text.append(texts.getLast()).toString());
        }
        return out;
    }

    private void requireValueCapacity(long count, DocTree where) {
        int capacity = org.ghyura.cf.model.key.KeyEncoding.PACKED_LEGACY16.capacity();
        if (count > capacity - expandedValues) {
            throw refuse(ErrorCode.KEY_CENSUS_OVERFLOW, "spec option expansion exceeds the " + capacity
                    + " input value labels the byte-backed dictionary can represent", where.where());
        }
    }

    /** Bound expanded strings and their workbook representations before allocation, across every slot. */
    private void admitExpansion(long count, long maxLength, DocTree where) {
        requireValueCapacity(count, where);
        long bytes = count * (256 + 32 * maxLength);
        long limit = Math.min(InputSource.DEFAULT_MAX_BYTES, Runtime.getRuntime().maxMemory() / 4);
        if (bytes > limit - expansionBytes) {
            throw refuse(ErrorCode.RESOURCE_LIMIT, "spec option expansion needs about " + (expansionBytes + bytes)
                    + " bytes; the input expansion allowance is " + limit + " bytes", where.where());
        }
        expansionBytes += bytes;
        expandedValues += (int) count;
    }

    private void checkInertPermutArg(String sheet, String verb, List<String> values, DocTree where) {
        Matcher m = INERT_PERMUT.matcher(PyText.strip(PyText.firstLine(verb)));
        if (!m.matches()) {
            return;
        }
        BigInteger k = PyText.parseInt(m.group(1));
        int n = values.size();
        if (k.compareTo(BigInteger.valueOf(n)) >= 0) {
            return;
        }
        BigInteger factorial = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            factorial = factorial.multiply(BigInteger.valueOf(i));
        }
        BigInteger perm = BigInteger.ONE;
        for (int i = 0; i < k.intValueExact(); i++) {
            perm = perm.multiply(BigInteger.valueOf(n - i));
        }
        warn("spec '" + name + "' slot '" + sheet + "': FW_Permut(" + k + ") over " + n + " values does NOT draw " + k + "-permutations. "
                + "The Core's PermutationsSimpleG takes no size argument, so the " + k + " is inert and the slot emits all " + n
                + "! orderings -- " + String.format(Locale.ROOT, "%,d", factorial) + " rows, not " + String.format(Locale.ROOT, "%,d", perm)
                + ". For k-permutations use the alias permute(" + k + ") (FW_Combi(" + k + ") then FW_Permut() in one FW_Seq row), "
                + "FW_Permut over a sheet of the size you want, or FW_PermutR(" + k + ") if repetition is acceptable.", where);
    }

    private void checkRawLiteralNewline(String sheet, List<String> values, DocTree where) {
        List<Integer> bad = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String v = PyText.rstripSpaceTab(values.get(i));
            if (v.endsWith("\\n") && !v.endsWith("\\\\n")) {
                bad.add(i);
            }
        }
        if (!bad.isEmpty()) {
            throw refuse("spec '" + name + "' slot '" + sheet + "': raw value(s) " + bad.toString() + " end in a literal backslash-n. TOML "
                    + "single-quoted strings do not process escapes, so the candidate would get a backslash and an 'n', not a newline. "
                    + "Drop the \\n (each slot value already ends its own line) or use a double-quoted string.", where);
        }
    }

    private void checkRawRowArity(String sheet, String verb, List<String> values, List<String> flags, DocTree where) {
        if (flags.stream().anyMatch(f -> f.startsWith("FW_Concatenator="))) {
            return;
        }
        int arity = maxRowArity(verb, values.size());
        if (arity < 2) {
            return;
        }
        List<Integer> fused = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String v = PyText.rstripSpaceTab(values.get(i));
            if (!v.isEmpty() && !(v.endsWith("\n") || v.endsWith("\r") || v.endsWith(";") || v.endsWith("}") || v.endsWith("{")
                    || v.endsWith(","))) {
                fused.add(i);
            }
        }
        if (fused.isEmpty()) {
            return;
        }
        warn("spec '" + name + "' slot '" + sheet + "': verb " + verb + " places up to " + arity + " values in one row and the sheet has no "
                + "FW_Concatenator, so raw value(s) at index " + fused + " — which end in neither a newline nor a statement terminator "
                + "— will be concatenated onto a SINGLE line. End each value with a newline (or ';'), or set FW_Concatenator=.", where);
    }

    /** fwgen's verb_max_row_arity. */
    static int maxRowArity(String verb, int n) {
        String v = PyText.strip(verb);
        if (v.startsWith("FW_Group") || v.startsWith("FW_Cartes")) {
            return Math.max(2, n);
        }
        if (v.startsWith("FW_CombiR") || v.startsWith("FW_Combi") || v.startsWith("FW_PermutR")) {
            if (ALL_ARG.matcher(v).find() || SIZE_ARG.matcher(v).find()) {
                return n;
            }
            Matcher m = FIRST_ARG.matcher(v);
            return m.find() ? PyText.parseInt(m.group(1)).intValueExact() : 1;
        }
        if (v.startsWith("FW_Permut")) {
            return n;
        }
        if (v.startsWith("FW_Subsets_")) {
            List<Integer> sizes = new ArrayList<>();
            Matcher d = DIGITS.matcher(v);
            while (d.find()) {
                sizes.add(PyText.parseInt(d.group()).intValueExact());
            }
            Matcher mode = SUBSET_MODE.matcher(v);
            String m = mode.find() ? mode.group(1).toUpperCase(Locale.ROOT) : "";
            return switch (m) {
                case "EXACT" -> sizes.isEmpty() ? n : sizes.getFirst();
                case "RANGE" -> sizes.size() >= 2 ? sizes.get(1) : n;
                case "BEFORE" -> sizes.isEmpty() ? n : Math.max(0, sizes.getFirst() - 1);
                case "GIVEN" -> sizes.isEmpty() ? n : sizes.stream().mapToInt(Integer::intValue).max().orElse(n);
                default -> n;
            };
        }
        if (v.startsWith("FW_Subsets")) {
            return n;
        }
        return 1;
    }

    private static List<String> sheetOperands(String token) {
        String first = PyText.firstLine(token);
        List<String> ops = new ArrayList<>();
        Matcher m = OPERAND.matcher(first);
        while (m.find()) {
            ops.add(m.group(1));
        }
        String[] fields = braceFields(first);
        if (fields != null) {
            for (int i : new int[]{0, 2, 3, 4, 6, 7}) {
                if (i < fields.length && !fields[i].isEmpty() && !fields[i].equals("FW_()") && !fields[i].equals("FW_()G")) {
                    ops.add(fields[i]);
                }
            }
        }
        return ops;
    }

    /** The nine fields of a brace's first line, or null when it is not a brace. */
    private static String[] braceFields(String firstLine) {
        if (!BRACE.matcher(firstLine).matches()) {
            return null;
        }
        return firstLine.substring(firstLine.indexOf('(') + 1, firstLine.lastIndexOf(')')).split(",", -1);
    }

    private List<ExtraRow> seqExtra(Map<String, Slot> bySheet) {
        List<ExtraRow> rows = new ArrayList<>();
        DocTree node = get(raw, "seq_extra");
        if (node == null) {
            return rows;
        }
        for (DocTree rowNode : list(node, "'seq_extra'").items()) {
            List<String> cells = new ArrayList<>();
            List<DocTree> nodes = new ArrayList<>();
            for (DocTree c : list(rowNode, "a seq_extra row").items()) {
                String text = str(c);
                if (!PyText.strip(text).isEmpty()) {
                    cells.add(text);
                    nodes.add(c);
                }
            }
            if (cells.isEmpty()) {
                continue;
            }
            if (cells.stream().noneMatch(SpecReader::isCoreVerb)) {
                throw refuse("spec '" + name + "' seq_extra row " + PyText.reprList(cells) + " has no recognized Core verb/joiner", rowNode);
            }
            for (int i = 0; i < cells.size(); i++) {
                String c = cells.get(i);
                for (String op : sheetOperands(c)) {
                    if (!bySheet.containsKey(op)) {
                        throw refuse("spec '" + name + "' seq_extra references sheet '" + op + "' that is not a declared slot", nodes.get(i));
                    }
                }
                String first = PyText.firstLine(c);
                String[] inner = braceFields(first);
                if (inner != null) {
                    for (int idx : new int[]{2, 4}) {
                        String op = idx < inner.length ? inner[idx] : "";
                        if (op.equals("FW_()") || op.equals("FW_()G")) {
                            continue;
                        }
                        Slot operand = bySheet.get(op);
                        List<String> opFlags = operand == null ? List.of() : operand.flags();
                        if (!op.isEmpty() && !opFlags.contains("FW_Exclude") && !opFlags.contains("FW_Heading")) {
                            throw refuse("spec '" + name + "' brace " + PyText.repr(first) + " joins operand '" + op + "', but that slot is not "
                                    + "FW_Exclude'd. Brace operands must carry flags=['FW_Exclude'] (set aside from the cartesian) so they "
                                    + "appear only via the join — add it, or the Core double-counts the operand. (Core: "
                                    + "SeqParser/BraceOperationHandler.)", nodes.get(i));
                        }
                    }
                }
            }
            rows.add(new ExtraRow(cells, nodes));
        }
        return rows;
    }

    private Map<String, DocTree> params(Map<String, Slot> bySheet) {
        Map<String, Map<String, Map<String, DocTree>>> params = new LinkedHashMap<>();
        Map<String, DocTree> sheetNodes = new LinkedHashMap<>();
        DocTree node = get(raw, "params");
        if (node == null) {
            return Map.of();
        }
        for (DocTree rowNode : list(node, "'params'").items()) {
            Obj row = table(rowNode, "a params row");
            DocTree sheetNode = get(row, "sheet");
            String sheet = sheetNode == null ? "None" : str(sheetNode);
            DocTree valueNode = get(row, "value");
            String value = valueNode == null ? "None" : str(valueNode);
            if (!(sheetNode instanceof Leaf l && l.value() instanceof RuleValue.Text) || !bySheet.containsKey(sheet)) {
                throw refuse("spec '" + name + "' params reference undeclared sheet '" + sheet + "'", sheetNode == null ? row : sheetNode);
            }
            Map<String, DocTree> attrs = new LinkedHashMap<>();
            row.fields().forEach((k, v) -> {
                if (!k.equals("sheet") && !k.equals("value")) {
                    attrs.put(k, v);
                }
            });
            params.computeIfAbsent(sheet, x -> new LinkedHashMap<>()).put(value, attrs);
            sheetNodes.putIfAbsent(sheet, row);
        }
        Map<String, DocTree> out = new LinkedHashMap<>();
        params.forEach((sheet, byValue) -> {
            Map<String, DocTree> values = new LinkedHashMap<>();
            byValue.forEach((value, attrs) -> values.put(value, new Obj(attrs, sheetNodes.get(sheet).where())));
            out.put(sheet, new Obj(values, sheetNodes.get(sheet).where()));
        });
        return out;
    }

    // ---- constraints and orders, as _validated_constraints and _validated_orders -----------------------------

    private List<Obj> constraints(Map<String, Slot> bySheet) {
        List<Obj> out = new ArrayList<>();
        DocTree node = get(raw, "constraints");
        if (node == null) {
            return out;
        }
        for (DocTree item : list(node, "'constraints'").items()) {
            String shapeError = shapeError(item);
            if (shapeError != null) {
                String cid = item instanceof Obj o && get(o, "id") != null ? repr(get(o, "id")) : "None";
                throw refuse("spec '" + name + "' constraint " + cid + " has an invalid shape: " + shapeError, item);
            }
            Obj c = (Obj) item;
            String cid = get(c, "id") == null ? "None" : repr(get(c, "id"));
            DocTree polarity = get(c, "polarity");
            if (polarity != null && !(polarity instanceof Leaf p && (p.value().equalsText("forbid") || p.value().equalsText("require")))) {
                warn("spec '" + name + "': constraint " + cid + " polarity " + repr(polarity) + " is neither 'forbid' nor 'require'; the sieve "
                        + "treats it as 'require' (compatibility mode)", polarity);
            }
            checkUnknownKeys(c, CONSTRAINT_KNOWN, "constraint '" + (get(c, "id") == null ? "?" : str(get(c, "id"))) + "'");
            String condError = conditionError(get(c, "condition"), "condition");
            if (condError != null) {
                throw refuse("spec '" + name + "' constraint " + cid + " has invalid condition: " + condError, get(c, "condition"));
            }
            DocTree assertion = get(c, "assert");
            String assertError = assertion != null && !isNull(assertion) ? conditionError(assertion, "assert") : null;
            if (assertError != null) {
                throw refuse("spec '" + name + "' constraint " + cid + " has invalid assert: " + assertError, assertion);
            }
            DocTree mapping = get(c, "mapping");
            String mappingError = mappingError(mapping);
            if (mappingError != null) {
                throw refuse("spec '" + name + "' constraint " + cid + " has invalid mapping: " + mappingError, mapping);
            }
            LinkedHashSet<String> ref = new LinkedHashSet<>(constraintSheets(c));
            if (mapping instanceof Obj mp && !mp.fields().isEmpty()) {
                addSheet(ref, get(mp, "source"));
                addSheet(ref, get(mp, "target"));
            }
            conditionSheets(get(c, "condition"), ref);
            conditionSheets(assertion, ref);
            for (String sheet : ref) {
                if (!sheet.isEmpty() && !bySheet.containsKey(sheet)) {
                    throw refuse("spec '" + name + "' constraint " + cid + " references undeclared sheet '" + sheet + "'", c);
                }
            }
            out.add(c);
        }
        return out;
    }

    private void addSheet(Set<String> ref, DocTree n) {
        if (n != null && PyText.truthy(n)) {
            ref.add(str(n));
        }
    }

    /** The {@code cs} of _validated_constraints: sheets, else the sets' sheets, else the mapping's, else the pairs' sorted. */
    private List<String> constraintSheets(Obj c) {
        DocTree sheets = get(c, "sheets");
        if (sheets != null && PyText.truthy(sheets)) {
            return ((Arr) sheets).items().stream().map(this::str).toList();
        }
        if (get(c, "sets") instanceof Obj sets && !sets.fields().isEmpty()) {
            return List.copyOf(sets.fields().keySet());
        }
        if (get(c, "mapping") instanceof Obj mp && !mp.fields().isEmpty()) {
            List<String> both = new ArrayList<>();
            both.add(str(get(mp, "source")));
            both.add(str(get(mp, "target")));
            return both;
        }
        TreeSet<String> pairs = new TreeSet<>(PyText.CODE_POINT_ORDER);
        if (get(c, "pairs") instanceof Arr pr) {
            for (DocTree e : pr.items()) {
                pairs.addAll(((Obj) e).fields().keySet());
            }
        }
        return List.copyOf(pairs);
    }

    private static boolean isNull(DocTree n) {
        return n instanceof Leaf l && l.value() == RuleValue.Null.NULL;
    }

    private static boolean isText(DocTree n) {
        return n instanceof Leaf l && l.value() instanceof RuleValue.Text && l.temporal() == DocTree.Temporal.NONE;
    }

    private static boolean isScalar(DocTree n) {
        return n instanceof Leaf l && l.temporal() == DocTree.Temporal.NONE && l.value().scalar();
    }

    /** fwgen's _constraint_shape_error. */
    private String shapeError(DocTree item) {
        if (!(item instanceof Obj c)) {
            return "must be an object";
        }
        List<String> bodies = BODIES.stream().filter(c.fields()::containsKey).toList();
        if (bodies.size() != 1) {
            return bodies.isEmpty() ? "needs 'pairs', 'sets', 'when', 'mapping', or 'assert'"
                    : "needs exactly one body of " + PyText.reprList(BODIES) + ", got " + PyText.reprList(bodies);
        }
        if (c.fields().containsKey("id") && !(isText(get(c, "id")) && PyText.truthy(get(c, "id")))) {
            return "'id' must be a non-empty string";
        }
        if (c.fields().containsKey("desc") && !isText(get(c, "desc"))) {
            return "'desc' must be a string";
        }
        if (c.fields().containsKey("sheets")) {
            if (!(get(c, "sheets") instanceof Arr sh && !sh.items().isEmpty()
                    && sh.items().stream().allMatch(x -> isText(x) && PyText.truthy(x)))) {
                return "'sheets' must be a non-empty list of sheet names";
            }
            if (sh.items().stream().map(this::str).distinct().count() != sh.items().size()) {
                return "'sheets' names a sheet twice";
            }
        }
        if (c.fields().containsKey("gate")) {
            if (!(get(c, "gate") instanceof Obj g) || !Set.of("adjacent", "within").containsAll(g.fields().keySet())) {
                return "'gate' must be an object with only 'adjacent' and/or 'within'";
            }
            if (g.fields().containsKey("adjacent") && !(get(g, "adjacent") instanceof Leaf a && a.value() instanceof RuleValue.Bool)) {
                return "'gate.adjacent' must be true or false";
            }
            if (g.fields().containsKey("within") && !(get(g, "within") instanceof Leaf w && w.value() instanceof RuleValue.Int i
                    && i.value().signum() >= 0)) {
                return "'gate.within' must be a non-negative integer";
            }
        }
        switch (bodies.getFirst()) {
            case "pairs" -> {
                if (!(get(c, "pairs") instanceof Arr pr && !pr.items().isEmpty())) {
                    return "'pairs' must be a non-empty list of {sheet: value} objects";
                }
                for (DocTree entry : pr.items()) {
                    if (!(entry instanceof Obj e && !e.fields().isEmpty() && e.fields().keySet().stream().noneMatch(String::isEmpty))) {
                        return "each 'pairs' entry must be a non-empty {sheet: value} object";
                    }
                    if (!e.fields().values().stream().allMatch(SpecReader::isScalar)) {
                        return "'pairs' values must be single values (string/number/boolean), not lists or objects";
                    }
                }
            }
            case "sets" -> {
                if (!(get(c, "sets") instanceof Obj st && !st.fields().isEmpty() && st.fields().keySet().stream().noneMatch(String::isEmpty))) {
                    return "'sets' must be a non-empty {sheet: [values]} object";
                }
                for (Map.Entry<String, DocTree> e : st.fields().entrySet()) {
                    if (!(e.getValue() instanceof Arr vals && !vals.items().isEmpty())) {
                        return "'sets." + e.getKey() + "' must be a non-empty list of values";
                    }
                    if (!vals.items().stream().allMatch(SpecReader::isScalar)) {
                        return "'sets." + e.getKey() + "' values must be single values (string/number/boolean)";
                    }
                }
            }
            case "when" -> {
                if (!(isText(get(c, "when")) && !PyText.strip(str(get(c, "when"))).isEmpty())) {
                    return "'when' must be a non-empty predicate string";
                }
                if (!c.fields().containsKey("sheets")) {
                    return "a 'when' bond needs 'sheets' (the sheets its predicate ranges over)";
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** fwgen's _condition_error; several operators are listed in sorted order (the legacy set order was arbitrary). */
    private String conditionError(DocTree cond, String path) {
        if (cond == null || isNull(cond) || (cond instanceof Obj e && e.fields().isEmpty())) {
            return null;
        }
        if (!(cond instanceof Obj c)) {
            return path + " must be an object";
        }
        TreeSet<String> unknown = new TreeSet<>(PyText.CODE_POINT_ORDER);
        c.fields().keySet().stream().filter(k -> !COND_ALLOWED.contains(k)).forEach(unknown::add);
        if (!unknown.isEmpty()) {
            return path + " has unknown key(s) " + PyText.reprList(List.copyOf(unknown));
        }
        List<String> nodeKeys = List.of("all", "any", "not").stream().filter(c.fields()::containsKey).toList();
        TreeSet<String> leafOps = new TreeSet<>(PyText.CODE_POINT_ORDER);
        c.fields().keySet().stream().filter(COND_LEAF::contains).forEach(leafOps::add);
        if (!nodeKeys.isEmpty()) {
            if (nodeKeys.size() != 1 || !leafOps.isEmpty() || c.fields().containsKey("sheet")) {
                return path + " must be either one boolean node or one sheet leaf";
            }
            String key = nodeKeys.getFirst();
            if (!key.equals("not")) {
                if (!(get(c, key) instanceof Arr children) || children.items().isEmpty()) {
                    return path + "." + key + " must be a non-empty list";
                }
                for (int i = 0; i < children.items().size(); i++) {
                    String err = conditionError(children.items().get(i), path + "." + key + "[" + i + "]");
                    if (err != null) {
                        return err;
                    }
                }
                return null;
            }
            return conditionError(get(c, "not"), path + ".not");
        }
        if (!c.fields().containsKey("sheet")) {
            return path + " leaf needs 'sheet'";
        }
        if (leafOps.size() != 1) {
            return path + " leaf needs exactly one operator, got " + (leafOps.isEmpty() ? "none" : PyText.reprList(List.copyOf(leafOps)));
        }
        String op = leafOps.first();
        DocTree val = get(c, op);
        if (COND_SET.contains(op) && !(val instanceof Arr)) {
            return path + "." + op + " must be a list of values";
        }
        if (COND_COUNT.contains(op) && !(val instanceof Leaf l && (l.value() instanceof RuleValue.Int || l.value() instanceof RuleValue.Bool))) {
            return path + "." + op + " must be an integer";
        }
        if (COND_SHEET.contains(op) && !(isText(val) && PyText.truthy(val))) {
            return path + "." + op + " must name another sheet";
        }
        if (op.equals("present") && !(val instanceof Leaf l && l.value() instanceof RuleValue.Bool)) {
            return path + ".present must be true or false";
        }
        if (COND_ORD.contains(op) && (isNull(val) || val instanceof Arr || val instanceof Obj)) {
            return path + "." + op + " must be a scalar constant (rank via `orders`)";
        }
        return null;
    }

    /** fwgen's _condition_sheets, into {@code out} in document order. */
    private void conditionSheets(DocTree cond, Set<String> out) {
        if (!(cond instanceof Obj c) || c.fields().isEmpty()) {
            return;
        }
        for (String key : List.of("all", "any")) {
            if (get(c, key) instanceof Arr children) {
                for (DocTree child : children.items()) {
                    conditionSheets(child, out);
                }
            }
        }
        if (get(c, "not") != null && PyText.truthy(get(c, "not"))) {
            conditionSheets(get(c, "not"), out);
        }
        List<String> keys = new ArrayList<>(List.of("sheet"));
        keys.addAll(COND_SHEET);
        for (String key : keys) {
            DocTree v = get(c, key);
            if (v != null && PyText.truthy(v)) {
                out.add(str(v));
            }
        }
    }

    /** fwgen's _mapping_error. */
    private String mappingError(DocTree m) {
        if (m == null || isNull(m)) {
            return null;
        }
        if (!(m instanceof Obj mp)) {
            return "mapping must be an object";
        }
        TreeSet<String> unknown = new TreeSet<>(PyText.CODE_POINT_ORDER);
        mp.fields().keySet().stream().filter(k -> !Set.of("source", "target", "allow").contains(k)).forEach(unknown::add);
        if (!unknown.isEmpty()) {
            return "mapping has unknown key(s) " + PyText.reprList(List.copyOf(unknown));
        }
        if (get(mp, "source") == null || !PyText.truthy(get(mp, "source")) || get(mp, "target") == null || !PyText.truthy(get(mp, "target"))) {
            return "mapping needs non-empty source and target";
        }
        DocTree allow = get(mp, "allow");
        if (allow != null && !(allow instanceof Obj)) {
            return "mapping.allow must be an object";
        }
        if (allow instanceof Obj a) {
            for (Map.Entry<String, DocTree> e : a.fields().entrySet()) {
                if (!(e.getValue() instanceof Arr)) {
                    return "mapping.allow[" + PyText.repr(e.getKey()) + "] must be a list of values";
                }
            }
        }
        return null;
    }

    private Map<String, DocTree> orders(Map<String, Slot> bySheet) {
        DocTree node = get(raw, "orders");
        if (node == null || !PyText.truthy(node)) {
            return Map.of();
        }
        Map<String, DocTree> out = new LinkedHashMap<>();
        for (Map.Entry<String, DocTree> e : table(node, "'orders'").fields().entrySet()) {
            String sheet = e.getKey();
            DocTree spec = e.getValue();
            if (!bySheet.containsKey(sheet)) {
                throw refuse("spec '" + name + "' orders reference undeclared sheet '" + sheet + "'", spec);
            }
            if (!(spec instanceof Arr || (spec instanceof Leaf l && (l.value().equalsText("numeric") || l.value().equalsText("date"))))) {
                throw refuse("spec '" + name + "' orders[" + PyText.repr(sheet) + "] must be a list, 'numeric', or 'date'", spec);
            }
            out.put(sheet, spec);
        }
        return out;
    }

    // ---- goals, custom variables and settings ---------------------------------------------------------------

    private List<Goal> goals() {
        List<Goal> goals = new ArrayList<>();
        DocTree node = get(raw, "goals");
        if (node == null) {
            return goals;
        }
        for (DocTree g : list(node, "'goals'").items()) {
            if (isText(g)) {
                String key = str(g);
                goals.add(new Goal(key, inferDirection(key), g));
                continue;
            }
            Obj goal = table(g, "a goal");
            DocTree keyNode = get(goal, "key");
            checkUnknownKeys(goal, GOAL_KNOWN, "goal '" + (keyNode == null ? "?" : str(keyNode)) + "'");
            if (!isText(keyNode)) {
                throw refuse("spec '" + name + "' goal needs 'key', a metric name", keyNode == null ? goal : keyNode);
            }
            String key = str(keyNode);
            DocTree dir = get(goal, "dir");
            DocTree direction = get(goal, "direction");
            String chosen = dir != null && PyText.truthy(dir) ? str(dir) : direction != null && PyText.truthy(direction) ? str(direction)
                    : inferDirection(key);
            goals.add(new Goal(key, chosen, goal));
        }
        return goals;
    }

    /** fwgen's infer_direction (mirrors the Analyzer's planner lexicon). */
    static String inferDirection(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        if (MIN_TOKENS.stream().anyMatch(k::contains)) {
            return "min";
        }
        return "max";
    }

    private List<CustomVar> customVars() {
        List<CustomVar> vars = new ArrayList<>();
        DocTree node = get(raw, "custom_vars");
        if (node != null) {
            for (DocTree item : list(node, "'custom_vars'").items()) {
                Obj c = table(item, "a custom_var");
                DocTree code = get(c, "code");
                checkUnknownKeys(c, CUSTOM_VAR_KNOWN, "custom_var (code=" + (code == null ? "?" : str(code)) + ")");
                DocTree msg = get(c, "msg");
                if (code == null || msg == null) {
                    throw refuse("spec '" + name + "' custom_var needs 'code' and 'msg'", c);
                }
                vars.add(new CustomVar(integer(code), str(msg), c));
            }
        }
        if (vars.isEmpty()) {
            vars.add(new CustomVar(BigInteger.TWO, "candidate failed verdict (nonzero FW_CUSTOM_VAR)", raw));
        }
        return vars;
    }

    private List<ConfigResolver.ScenarioValue> goalSetting(List<Goal> goals) {
        if (goals.isEmpty()) {
            return List.of();
        }
        List<String> pieces = new ArrayList<>();
        for (Goal g : goals) {
            if (!METRIC_KEY.matcher(g.key()).matches()) {
                throw refuse("spec '" + name + "' goal key '" + g.key() + "' is not a metric key (a letter or '_', then letters, digits, '_', "
                        + "'.' or '-')", g.node());
            }
            if (!g.direction().equals("min") && !g.direction().equals("max")) {
                throw refuse("spec '" + name + "' goal '" + g.key() + "' has the direction " + PyText.repr(g.direction()) + "; a spec goal is "
                        + "min or max (the legacy made any other direction a target without a value): give a target goal as "
                        + ConfigKeys.ANALYSIS_GOALS.name() + "=" + g.key() + ":target:VALUE", g.node());
            }
            pieces.add(g.key() + ":" + g.direction());
        }
        return List.of(new ConfigResolver.ScenarioValue(ConfigKeys.ANALYSIS_GOALS.name(), String.join(",", pieces), get(raw, "goals").where()));
    }

    private void coverage(List<ConfigResolver.ScenarioValue> settings) {
        DocTree strength = get(raw, "coverage_strength");
        if (strength != null) {
            settings.add(new ConfigResolver.ScenarioValue(ConfigKeys.COVERAGE_STRENGTH.name(),
                    PyText.truthy(strength) ? integer(strength).toString() : "0", strength.where()));
        }
        DocTree optimal = get(raw, "coverage_optimal");
        if (optimal != null) {
            settings.add(new ConfigResolver.ScenarioValue(ConfigKeys.COVERAGE_METHOD.name(), PyText.truthy(optimal) ? "set-cover" : "greedy",
                    optimal.where()));
        }
        DocTree budget = get(raw, "coverage_budget");
        if (budget != null) {
            settings.add(new ConfigResolver.ScenarioValue(ConfigKeys.COVERAGE_BUDGET.name(),
                    PyText.truthy(budget) ? integer(budget).toString() : "0", budget.where()));
        }
    }

    // ---- the FW_Seq dependency gate (fwseq_graph.validate_spec_graph, for TOML/JSON specs) -------------------

    /** fwseq_graph.validate_spec_graph: an error-level issue of the graph rejects the spec before its workbook is written. */
    private void gate() {
        List<SeqGraph.Issue> errors = graph.errors();
        if (!errors.isEmpty()) {
            throw refuse("FW_Seq dependency graph rejected before workbook: " + String.join("; ", errors.stream()
                    .map(e -> "[" + e.code() + "] " + e.message()).toList()), raw);
        }
    }

    // ---- compilation (build_compact_core_json and emit_sidecar) ---------------------------------------------

    private Obj sidecar(Map<String, DocTree> params, List<Obj> constraints, Map<String, DocTree> orders) {
        SourceLocation at = raw.where();
        Map<String, DocTree> fields = new LinkedHashMap<>();
        fields.put("version", new Leaf(new RuleValue.Int(BigInteger.ONE), at));
        fields.put("params", new Obj(params, at));
        fields.put("constraints", new Arr(List.copyOf(constraints), at));
        if (!orders.isEmpty()) {
            fields.put("orders", new Obj(orders, at));
        }
        Obj sidecar = new Obj(fields, at);
        noTemporal(sidecar);
        return sidecar;
    }

    private void noTemporal(DocTree n) {
        switch (n) {
            case Leaf l when l.temporal() != DocTree.Temporal.NONE -> throw refuse("a TOML date or time cannot be written to the constraint "
                    + "sidecar (the legacy json.dumps raised TypeError); write it as a string", l);
            case Arr a -> a.items().forEach(this::noTemporal);
            case Obj o -> o.fields().values().forEach(this::noTemporal);
            default -> {
            }
        }
    }

    private WorkbookData workbook(List<Slot> slots, List<ExtraRow> seqExtra, String runme, DocTree runmeNode, List<String> args,
                                  List<DocTree> argNodes, List<CustomVar> customVars) {
        List<InputSheet> sheets = new ArrayList<>();
        SourceLocation top = raw.where();
        List<InputRow> seq = new ArrayList<>();
        for (Slot s : slots) {
            SourceLocation at = s.node().where();
            Map<Integer, String> cells = new TreeMap<>();
            String reuse = s.flags().contains("FW_ReuseTableOnly") && !s.flags().contains("FW_Reuse") ? "FW_ReuseTableOnly" : "FW_Reuse";
            cells.put(1, s.sheet());
            cells.put(2, reuse);
            int col = 3;
            for (String f : s.flags()) {
                if (!f.equals(reuse)) {
                    cells.put(col++, f);
                }
            }
            int vcol = Math.max(col, 4);
            cells.put(vcol, cellForXlsx(s.verb()));
            int mcol = vcol + 1;
            for (String d : s.chain()) {
                cells.put(mcol++, cellForXlsx(d));
            }
            if (!s.separator().isEmpty()) {
                cells.put(mcol++, "FW_Separator(" + s.separator() + ")");
            }
            if (!s.groupReplace().isEmpty()) {
                StringBuilder group = new StringBuilder("FW_Group");
                for (String[] e : s.groupReplace()) {
                    group.append("\nFW_ReplaceRE(\"").append(e[0]).append("\", \"").append(e[1]).append("\")");
                }
                cells.put(mcol, group.toString());
            }
            List<InputCell> row = new ArrayList<>();
            cells.forEach((c, text) -> row.add(new InputCell(c - 1, CellKind.TEXT, text, at)));
            seq.add(new InputRow(seq.size(), row));
        }
        for (ExtraRow extra : seqExtra) {
            List<InputCell> row = new ArrayList<>();
            for (int c = 0; c < extra.cells().size(); c++) {
                row.add(new InputCell(c, CellKind.TEXT, cellForXlsx(extra.cells().get(c)), extra.nodes().get(c).where()));
            }
            seq.add(new InputRow(seq.size(), row));
        }
        sheets.add(new InputSheet("FW_Seq", top, seq));
        List<InputRow> names = new ArrayList<>();
        for (Slot s : slots) {
            SourceLocation at = s.node().where();
            names.add(new InputRow(names.size(), List.of(new InputCell(0, CellKind.TEXT, s.sheet(), at),
                    new InputCell(1, CellKind.TEXT, s.prefix().isEmpty() ? "FW_EMPTY_STRING" : s.prefix(), at),
                    new InputCell(2, CellKind.TEXT, s.ending().isEmpty() ? "FW_EMPTY_STRING" : s.ending(), at))));
        }
        sheets.add(new InputSheet("FW_SheetNames", top, names));
        SourceLocation runmeAt = runmeNode == null ? top : runmeNode.where();
        sheets.add(new InputSheet("FW_RunMeFirstOnce", runmeAt, List.of(new InputRow(0, List.of(new InputCell(0, CellKind.TEXT, runme, runmeAt))))));
        List<InputRow> argRows = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            argRows.add(new InputRow(i, List.of(new InputCell(0, CellKind.TEXT, args.get(i), argNodes.get(i).where()))));
        }
        if (argRows.isEmpty()) {
            argRows.add(new InputRow(0, List.of(new InputCell(0, CellKind.TEXT, "noargs", top))));
        }
        sheets.add(new InputSheet("FW_Arguments", top, argRows));
        List<InputRow> vars = new ArrayList<>();
        for (CustomVar v : customVars) {
            SourceLocation at = v.node().where();
            vars.add(new InputRow(vars.size(), List.of(new InputCell(0, CellKind.TEXT, v.code().toString(), at),
                    new InputCell(1, CellKind.TEXT, "FWCUSTOMVAR=" + v.code() + " " + v.msg(), at))));
        }
        sheets.add(new InputSheet("FW_CUSTOM_VAR", top, vars));
        for (Slot s : slots) {
            List<InputRow> rows = new ArrayList<>();
            for (int i = 0; i < s.values().size(); i++) {
                String v = s.values().get(i);
                String text = s.raw() ? v : cellForXlsx(" " + s.key() + "=" + v);
                rows.add(new InputRow(i, List.of(new InputCell(0, CellKind.TEXT, text, s.valueNodes().get(i).where()))));
            }
            sheets.add(new InputSheet(s.sheet(), s.node().where(), rows));
        }
        return new WorkbookData(Utf8.displayName("spec", origin), sheets);
    }

    /** fwgen's cell_for_xlsx: a literal backslash-n or backslash-t becomes a real newline or tab. */
    private static String cellForXlsx(String text) {
        return text.replace("\\n", "\n").replace("\\t", "\t");
    }
}
