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


package org.ghyura.cf.generation;

import org.ghyura.cf.algebra.Concatenation;
import org.ghyura.cf.algebra.FixedTuples;
import org.ghyura.cf.algebra.LegacyLibrary;
import org.ghyura.cf.algebra.MultisetPermutations;
import org.ghyura.cf.algebra.Operators;
import org.ghyura.cf.algebra.RankedTuples;
import org.ghyura.cf.algebra.TupleCursor;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.RowBlocks;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The stages of one sheet: its directives run in order, as the legacy SheetWorker ran them (SheetWorker.java lines
 * 455-620 and 628-1330 at the pinned commit). Directive 0 builds the first table from the sheet's values; a later
 * combination directive builds the second table from the rows of the first, row by row, or, after FW_Group, from the
 * sorted rows taken as units; FW_Group and FW_Separator only prepare the next pass; a joiner appends the rows it
 * builds to the first table and moves everything to the second. After a pass that is neither the first nor the last
 * directive, the second table becomes the first (the legacy swap). With distinct tables the active table loses its
 * repeated rows after every combination directive and joiner, and the final table once more.
 *
 * <p>Tables are computed where the rows follow from their inputs (first passes, ungrouped later passes, joiners),
 * so they hold no rows; a grouped pass, de-duplication and the id order of a reshuffled table are stored, reserved
 * from the run's memory budget before they are allocated.</p>
 */
final class SheetStages {

    /** What the stages need from the rest of the run. */
    interface Context {
        /** The values of a sheet as labels, empty for a virtual sheet or an unknown name. */
        int[] values(String sheet);

        /** The table a joiner reads from an operand sheet: empty when it has none or an earlier joiner consumed it. */
        RowSource operand(String sheet, String reader);

        /** Records that the joiner of {@code reader} has read {@code sheet}: legacy cleanupOperand, after both reads. */
        void consume(String sheet, String reader);

        KeyEncoding encoding();

        GenerationOptions options();

        MemoryBudget budget();

        Cancellation cancellation();

        List<Diagnostic> diagnostics();

        Arena arena();

        /** Retain active local tables and every completed sheet still exposed by the generation. */
        void retain(RowSource... active);
    }

    private static final Pattern BRACKETS = Pattern.compile("[{}\\[\\]]");
    /** An upper bound of the heap one grouped unit keeps beyond its octets: row object, array header, sort slots. */
    static final long UNIT_OVERHEAD = 96;

    private final SheetPlan sheet;
    private final Context context;
    private RowSource fw = RowSource.empty();
    private RowSource fw2 = RowSource.empty();
    private boolean fwDistinct = true;
    private boolean fw2Distinct = true;
    private BigInteger fwIds = BigInteger.ZERO;
    private BigInteger fw2Ids = BigInteger.ZERO;
    private boolean combi2;
    private boolean materialized;
    private Map<String, String> group;
    private Directive.Group groupDirective;
    private Integer separator;
    private long unparseable;
    private String unparseableExample;
    private Directive unparseableAt;
    // FW_ReplaceRE accounting of the current grouped pass (legacy SheetState counters)
    private long rowsSeen;
    private long rowsRewritten;
    private long rowsDropped;
    private final Map<String, Long> hits = new LinkedHashMap<>();

    /** Characters a key-label text can contain (legacy CODE_STRING_CHARS, with the minus sign removed below). */
    private static final Pattern CODE_STRING_CHARS = Pattern.compile("[0-9,\\[\\]{}\\s]*");

    SheetStages(SheetPlan sheet, Context context) {
        this.sheet = sheet;
        this.context = context;
    }

    /** Runs every directive and returns the sheet's final table (the second one when the last directive wrote it). */
    RowSource run() {
        List<Directive> directives = sheet.directives();
        for (int i = 0; i < directives.size(); i++) {
            context.cancellation().throwIfCancelled();
            materialized = false;
            Directive directive = directives.get(i);
            boolean generating = true;
            switch (directive) {
                case Directive.Joiner joiner -> joiner(joiner);
                case Directive.Group g -> {
                    group = replacements(g);
                    groupDirective = g;
                    authoringPolicies(g);
                    generating = false;
                }
                case Directive.Separator s -> {
                    int[] values = context.values(s.sheet());
                    separator = values.length == 0 ? null : values[0];
                    generating = false;
                }
                default -> {
                    if (i == 0) {
                        firstPass(directive);
                        combi2 = false;
                    } else {
                        if (group != null) {
                            groupedPass(directive);
                            reportReplaceOutcome(directive);
                            group = null;
                        } else {
                            laterPass(directive);
                        }
                        combi2 = true;
                    }
                }
            }
            if (generating && context.options().distinctTables()) {
                distinctActive();
            }
            markDistinctActive();
            if (combi2 && i > 0 && i < directives.size() - 1) {
                fw = RowSources.withoutParents(fw2);
                fwDistinct = fw2Distinct;
                fw2 = RowSource.empty();
                fw2Distinct = true;
                combi2 = false;
            }
            if (materialized) context.retain(fw, fw2);
        }
        if (unparseable > 0) {
            boolean drop = context.options().replace().unparseable() == RunConfig.ReplaceUnparseable.DROP;
            String message = "sheet " + sheet.name() + ": " + unparseable + " grouped row(s) were no longer a list of key labels after "
                    + "FW_ReplaceRE and were dropped (cf.generation.replace.unparseable=" + (drop ? "drop" : "warn") + "); the first was "
                    + unparseableExample;
            context.diagnostics().add(Diagnostic.of(drop ? Severity.INFO : Severity.WARNING, ErrorCode.GEN_UNPARSEABLE_ROW, message,
                    unparseableAt.location(), "sheet", sheet.name(), "rows", Long.toString(unparseable), "example", unparseableExample));
        }
        if (context.options().distinctTables()) {
            distinctActive();
        }
        markDistinctActive();
        RowSource result = combi2 ? fw2 : fw;
        context.retain(result);
        return result;
    }

    private void markDistinctActive() {
        if (combi2) {
            if (fw2Distinct) fw2 = RowSources.knownDistinct(fw2);
        } else if (fwDistinct) {
            fw = RowSources.knownDistinct(fw);
        }
    }

    private String purpose(String what) {
        return what + " of sheet " + sheet.name();
    }

    // ---- the first pass ----------------------------------------------------------------------------------------

    private void firstPass(Directive directive) {
        if (!sheet.virtual() && sheet.valueCount() == 0) {
            // a data sheet without values was missing from the legacy values map (WorkbookParser registered only sheets
            // with cells), and buildDirectiveGenerator then returned the subsets of an empty list: one empty row, whatever
            // the verb; the (all) forms looped over the sizes 1..0 first and made nothing (SheetWorker 704-714, 810-818)
            boolean all = directive instanceof Directive.Combinatoric c && c.verb() != Directive.Verb.PERMUT
                    && c.count().kind() == Directive.CountKind.ALL;
            RowSource rows = new TupleRows(all ? FixedTuples.NONE : FixedTuples.ONE_EMPTY, new int[0], null, context.encoding(), fwIds, 0);
            fw = RowSources.concat(fw, rows);
            fwDistinct = true;
            fwIds = fwIds.add(rows.size());
            return;
        }
        int[] values = context.values(sheet.name());
        LegacyLibrary library = context.options().library();
        int n = values.length;
        boolean distinctValues = distinct(values);
        RankedTuples tuples;
        int[] a = values;
        int[] b = null;
        int length;
        boolean distinctRows = distinctValues;
        switch (directive) {
            case Directive.Combinatoric c -> {
                if (c.verb() == Directive.Verb.PERMUT) {
                    // the size is ignored (SheetWorker.buildGenerator); the duplicate marker uses the multiset generator
                    int[] classes = MultisetPermutations.classesOf(Arrays.stream(values).boxed().toList());
                    tuples = c.duplicatesIdentical() ? Operators.distinctArrangements(classes) : Operators.permutations(classes, library);
                    distinctRows |= c.duplicatesIdentical() || library == LegacyLibrary.COMBINATORICSLIB3;
                    length = n;
                } else if (c.count().kind() == Directive.CountKind.ALL) {
                    List<RankedTuples> parts = new ArrayList<>();
                    for (int m = 1; m <= n; m++) {
                        parts.add(verb(c.verb(), n, m, library));
                    }
                    tuples = new Concatenation(parts);
                    length = n == 1 ? 1 : -1;
                } else {
                    int m = switch (c.count().kind()) {
                        case SIZE -> n;
                        case EXACT -> c.count().value();
                        default -> 1;
                    };
                    tuples = verb(c.verb(), n, m, library);
                    length = tuples == FixedTuples.ONE_EMPTY ? 0 : m;
                }
            }
            case Directive.Subsets s -> {
                tuples = subsets(s, n, library);
                length = uniformSubsetLength(s, n);
            }
            case Directive.Cartes c -> {
                int[] other = context.values(c.sheet());
                if (c.first()) {
                    tuples = Operators.cartesian(other.length, n);
                    a = other;
                    b = values;
                } else {
                    tuples = Operators.cartesian(n, other.length);
                    b = other;
                }
                distinctRows = distinctValues && distinct(other);
                length = 2;
            }
            default -> throw new IllegalStateException("not a combination directive: " + directive);
        }
        RowSource rows = new TupleRows(tuples, a, b, context.encoding(), fwIds, length);
        fw = RowSources.concat(fw, rows);
        fwDistinct = distinctRows;
        fwIds = fwIds.add(rows.size());
    }

    /**
     * The generator of a verb for m elements per row, with the legacy guards of SheetWorker.buildGenerator (lines
     * 1514-1531): a size the verb cannot take gave the subsets of an empty list, which is one empty row in both
     * libraries, not none.
     */
    static RankedTuples verb(Directive.Verb verb, int n, int m, LegacyLibrary library) {
        return switch (verb) {
            case COMBI -> m < 0 || m > n ? FixedTuples.ONE_EMPTY : Operators.combinations(n, m, library);
            case COMBI_R -> m < 0 ? FixedTuples.ONE_EMPTY : Operators.combinationsWithRepetition(n, m, library);
            case PERMUT_R -> m < 0 ? FixedTuples.ONE_EMPTY : Operators.permutationsWithRepetition(n, m, library);
            case PERMUT -> throw new IllegalStateException("FW_Permut ignores the size; it is built by its caller");
        };
    }

    /** CombinatorialGenerator.subsets: all subsets, or the combinations of every size the mode selects. */
    private static RankedTuples subsets(Directive.Subsets s, int n, LegacyLibrary library) {
        TreeSet<Integer> sizes = subsetSizes(s, n);
        return sizes == null ? Operators.subsets(n) : Operators.subsetsOfSizes(n, sizes, library);
    }

    /** The sizes a FW_Subsets mode selects, or null for every subset. */
    private static TreeSet<Integer> subsetSizes(Directive.Subsets s, int n) {
        if (s.mode() == Directive.SubsetMode.DEFAULT || s.sizes().isEmpty()) {
            return null;
        }
        List<Integer> p = s.sizes();
        TreeSet<Integer> sizes = new TreeSet<>();
        for (int size = 0; size <= n; size++) {
            boolean match = switch (s.mode()) {
                case BEFORE -> size < p.getFirst();
                case AFTER -> size > p.getFirst();
                case EXACT -> size == p.getFirst();
                case RANGE -> size >= p.get(0) && size <= p.get(1);
                case GIVEN -> p.contains(size);
                case DEFAULT -> true;
            };
            if (match) {
                sizes.add(size);
            }
        }
        return sizes;
    }

    private static int uniformSubsetLength(Directive.Subsets s, int n) {
        TreeSet<Integer> sizes = subsetSizes(s, n);
        if (sizes == null) {
            return n == 0 ? 0 : -1;
        }
        return sizes.size() == 1 ? sizes.first() : -1;
    }

    private static boolean distinct(int[] values) {
        Set<Integer> seen = new HashSet<>();
        for (int v : values) {
            if (!seen.add(v)) {
                return false;
            }
        }
        return true;
    }

    // ---- later passes, one row at a time -----------------------------------------------------------------------

    private void laterPass(Directive directive) {
        RowSource source = IdOrder.of(fw, context.budget(), purpose("the first table"), context.cancellation(), context.arena());
        if (source.size().signum() == 0) {
            noSourceRows(directive);
            return;
        }
        int[] other = directive instanceof Directive.Cartes c ? context.values(c.sheet()) : null;
        RowExpansion expansion = new RowExpansion(directive, context.options().library(), other, separator);
        ExpandedRows rows = context.arena().own(ExpandedRows.of(source, expansion, context.encoding(), fw2Ids, context.budget(),
                purpose("the running totals of '" + directive.source() + "'"), context.cancellation()));
        fw2Distinct = fw2.size().signum() == 0 && (rows.size().compareTo(BigInteger.ONE) <= 0
                || fwDistinct && expansion.preservesDistinctRows(source.uniformLength()));
        fw2 = RowSources.concat(fw2, rows);
        fw2Ids = fw2Ids.add(rows.size());
    }

    // ---- the grouped pass after FW_Group ----------------------------------------------------------------------

    /**
     * The rows of the first table in id order, sorted by signed labels and then length, combined as units with the
     * combinatoricslib3 generators; each result's legacy text ([[1, 2], [3, 4]]) is rewritten by the replacements in
     * order and by the separator, then read back as labels. The units and the result are stored, both budgeted.
     */
    private void groupedPass(Directive directive) {
        RowSource source = IdOrder.of(fw, context.budget(), purpose("the first table"), context.cancellation(), context.arena());
        if (source.size().signum() == 0) {
            context.diagnostics().add(Diagnostic.warning(ErrorCode.GEN_NO_SOURCE_ROWS, "sheet " + sheet.name() + ": '"
                    + directive.source() + "' after FW_Group works on the rows of the earlier directives, and there are none",
                    directive.location(), "sheet", sheet.name()));
            return;
        }
        if (source.size().bitLength() > 31) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, "sheet " + sheet.name() + ": FW_Group would take "
                    + source.size() + " rows as units, more than one array holds", directive.location(), "sheet", sheet.name()));
        }
        int n = source.size().intValue();
        long octets = 0;
        try (RowCursor rows = source.open(context.cancellation())) {
            while (rows.hasNext()) {
                octets += rows.next().keys().payloadLength();
            }
        }
        RowBlocks out = context.arena().own(new RowBlocks(purpose("the grouped pass '" + directive.source() + "'"),
                context.encoding(), context.budget()));
        materialized = true;
        MemoryBudget.Lease unitsLease = context.budget().reserve(64 + UNIT_OVERHEAD * n + octets, purpose("the FW_Group units"));
        try {
            if (context.options().distinctTables()) out.indexContent();
            KeyRow[] units = new KeyRow[n];
            try (RowCursor rows = source.open(context.cancellation())) {
                for (int i = 0; i < n; i++) {
                    units[i] = rows.next().keys();
                }
            }
            Arrays.sort(units, KeyRow.LABEL_ORDER);
            for (int m : groupSizes(directive, n)) {
                RankedTuples tuples;
                int[] operand = null;
                boolean operandFirst = false;
                switch (directive) {
                    case Directive.Combinatoric c -> {
                        if (c.verb() == Directive.Verb.COMBI && m > n) {
                            continue;
                        }
                        int[] identity = new int[n];
                        for (int i = 0; i < n; i++) {
                            identity[i] = i;
                        }
                        tuples = switch (c.verb()) {
                            case COMBI -> Operators.combinations(n, m, LegacyLibrary.COMBINATORICSLIB3);
                            case COMBI_R -> Operators.combinationsWithRepetition(n, m, LegacyLibrary.COMBINATORICSLIB3);
                            case PERMUT -> Operators.permutations(identity, LegacyLibrary.COMBINATORICSLIB3);
                            case PERMUT_R -> Operators.permutationsWithRepetition(n, m, LegacyLibrary.COMBINATORICSLIB3);
                        };
                    }
                    case Directive.Subsets s -> tuples = Operators.subsets(n);
                    case Directive.Cartes c -> {
                        operand = context.values(c.sheet());
                        if (operand.length == 0) {
                            context.diagnostics().add(Diagnostic.warning(ErrorCode.GEN_NO_SOURCE_ROWS, "sheet " + sheet.name()
                                    + ": the grouped FW_Cartes operand '" + c.sheet() + "' has no values, so the pass writes no rows",
                                    c.location(), "sheet", sheet.name()));
                            continue;
                        }
                        operandFirst = c.first();
                        tuples = operandFirst ? Operators.cartesian(operand.length, n) : Operators.cartesian(n, operand.length);
                    }
                    default -> throw new IllegalStateException("not a combination directive: " + directive);
                }
                try (TupleCursor cursor = tuples.cursor(BigInteger.ZERO, tuples.size(), context.cancellation())) {
                    while (cursor.hasNext()) {
                        int[] tuple = cursor.next();
                        StringBuilder text = new StringBuilder("[");
                        for (int i = 0; i < tuple.length; i++) {
                            text.append(i == 0 ? "" : ", ");
                            boolean fromOperand = operand != null && operandFirst == (i == 0);
                            text.append(fromOperand ? Integer.toString(operand[tuple[i]]) : units[tuple[i]].describe());
                        }
                        int[] labels = rewrite(text.append(']').toString(), directive);
                        if (labels != null && labels.length > 0) {
                            fw2Ids = fw2Ids.add(BigInteger.ONE);
                            KeyRow keys = KeyRow.of(context.encoding(), labels);
                            if (context.options().distinctTables()) {
                                out.appendIfAbsent(fw2Ids, BigInteger.ZERO, keys);
                            } else {
                                out.append(fw2Ids, BigInteger.ZERO, keys);
                            }
                        }
                    }
                }
            }
        } finally {
            unitsLease.close();
            out.dropContentIndex();
        }
        fw2Distinct = fw2.size().signum() == 0 && out.contentDistinct();
        fw2 = RowSources.concat(fw2, out);
    }

    private static List<Integer> groupSizes(Directive directive, int groupSize) {
        if (!(directive instanceof Directive.Combinatoric c)) {
            return List.of(1);
        }
        return switch (c.count().kind()) {
            case SIZE -> List.of(groupSize);
            case ALL -> {
                if (c.verb() == Directive.Verb.PERMUT) {
                    yield List.of(1);
                }
                List<Integer> all = new ArrayList<>();
                for (int m = 1; m <= groupSize; m++) {
                    all.add(m);
                }
                yield all;
            }
            case EXACT -> List.of(Math.max(1, c.count().value()));
            case DEFAULT -> List.of(1);
        };
    }

    /** The legacy rewrite of one grouped result: the replacements in order, the separator, then the labels; null when unparseable. */
    private int[] rewrite(String text, Directive directive) {
        String current = text;
        rowsSeen++;
        for (Map.Entry<String, String> replacement : group.entrySet()) {
            try {
                String before = current;
                current = current.replaceAll(replacement.getKey(), replacement.getValue());
                if (!current.equals(before)) {
                    hits.merge(replacement.getKey(), 1L, Long::sum);
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.GEN_REPLACEMENT_INVALID, "sheet " + sheet.name()
                        + ": FW_ReplaceRE(\"" + replacement.getKey() + "\", \"" + replacement.getValue() + "\") cannot be applied to "
                        + text + ": " + e.getMessage(), directive.location(), "sheet", sheet.name()), e);
            }
        }
        if (!current.equals(text)) {
            rowsRewritten++;
        }
        if (separator != null) {
            current = current.replace(", ", ", " + separator + ", ");
        }
        String[] parts = BRACKETS.matcher(current).replaceAll("").split(", ");
        List<Integer> labels = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int label = Integer.parseInt(trimmed);
                if (!context.encoding().accepts(label)) {
                    return unparseable(text, current, directive);
                }
                labels.add(label);
            } catch (NumberFormatException e) {
                return unparseable(text, current, directive);
            }
        }
        return labels.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] unparseable(String before, String after, Directive directive) {
        rowsDropped++;
        if (context.options().replace().unparseable() == RunConfig.ReplaceUnparseable.FAIL) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.GEN_UNPARSEABLE_ROW, "sheet " + sheet.name() + ": FW_ReplaceRE "
                    + "produced \"" + after + "\" from \"" + before + "\", which is no longer a list of key labels "
                    + "(cf.generation.replace.unparseable=fail)", directive.location(), "sheet", sheet.name()));
        }
        if (unparseable++ == 0) {
            unparseableExample = "\"" + after + "\" (was \"" + before + "\")";
            unparseableAt = directive;
        }
        return null;
    }

    /**
     * The legacy authoring checks of one FW_Group (SheetWorker.applyReplaceAuthoringPolicies): a replacement equal to its
     * pattern (identity policy), and a pattern whose literal text cannot occur in a key-label list (pattern policy).
     */
    private void authoringPolicies(Directive.Group g) {
        rowsSeen = 0;
        rowsRewritten = 0;
        rowsDropped = 0;
        hits.clear();
        GenerationOptions.ReplacePolicies policies = context.options().replace();
        for (Map.Entry<String, String> replacement : group.entrySet()) {
            String pattern = replacement.getKey();
            hits.put(pattern, 0L);
            if (policies.identity() == RunConfig.ReplaceIdentity.WARN && pattern.equals(replacement.getValue())) {
                context.diagnostics().add(Diagnostic.warning(ErrorCode.GEN_REPLACEMENT_IDENTITY, "sheet " + sheet.name()
                        + ": FW_ReplaceRE pattern and replacement are both \"" + pattern + "\"; the rewrite does nothing but change the "
                        + "plan's identity (cf.generation.replace.identity=warn)", g.location(), "sheet", sheet.name(), "pattern", pattern));
            }
            if (policies.pattern() == RunConfig.ReplacePattern.PERMISSIVE || canMatchCodeString(pattern)) {
                continue;
            }
            String message = "sheet " + sheet.name() + ": FW_ReplaceRE pattern \"" + pattern + "\" needs text a key-label list such as "
                    + "[47, 48] never contains, so it can only ever be a no-op: the rewrite runs on the labels, not on the values' "
                    + "text (cf.generation.replace.pattern=" + policies.pattern().token() + ")";
            if (policies.pattern() == RunConfig.ReplacePattern.STRICT) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.GEN_REPLACEMENT_PATTERN, message, g.location(), "sheet",
                        sheet.name(), "pattern", pattern));
            }
            context.diagnostics().add(Diagnostic.warning(ErrorCode.GEN_REPLACEMENT_PATTERN, message, g.location(), "sheet",
                    sheet.name(), "pattern", pattern));
        }
    }

    /**
     * Whether a pattern could match a key-label text (legacy SheetWorker.canMatchCodeString): regular-expression classes,
     * escapes and structure are stripped, and what remains must be digits, commas, brackets, braces or white space.
     */
    static boolean canMatchCodeString(String pattern) {
        String literals = pattern
                .replaceAll("\\\\[dDwWsSbBAZzGQE]", "")
                .replaceAll("\\\\[pP]\\{\\w+}", "")
                .replaceAll("\\\\.", "")
                .replaceAll("[\\[\\]{}()|.*+?^$\\-]", "");
        return CODE_STRING_CHARS.matcher(literals).matches();
    }

    /** The legacy per-pass report (SheetWorker.reportReplaceOutcome): the summary, then the unmatched patterns. */
    private void reportReplaceOutcome(Directive directive) {
        GenerationOptions.ReplacePolicies policies = context.options().replace();
        if (policies.diagnostics() == RunConfig.ReplaceDiagnostics.SUMMARY) {
            StringBuilder perPattern = new StringBuilder();
            hits.forEach((pattern, count) -> perPattern.append(perPattern.isEmpty() ? "" : ", ").append('"').append(pattern)
                    .append("\"=").append(count));
            context.diagnostics().add(Diagnostic.info(ErrorCode.GEN_REPLACEMENT_SUMMARY, "FW_ReplaceRE summary, sheet " + sheet.name()
                    + ": rows seen=" + rowsSeen + " rewritten=" + rowsRewritten + " dropped=" + rowsDropped + "; rows changed per "
                    + "pattern: " + perPattern, groupDirective.location(), "sheet", sheet.name()));
        }
        if (policies.unmatched() == RunConfig.ReplaceUnmatched.IGNORE) {
            return;
        }
        List<String> unmatched = new ArrayList<>();
        hits.forEach((pattern, count) -> {
            if (count == 0) {
                unmatched.add(pattern);
            }
        });
        if (unmatched.isEmpty()) {
            return;
        }
        String message = "FW_ReplaceRE on sheet " + sheet.name() + ": pattern(s) " + unmatched + " changed no row out of " + rowsSeen
                + "; the rewrite runs on the key labels of each combination, so a pattern written against value text never matches "
                + "(cf.generation.replace.unmatched=" + policies.unmatched().token() + ")";
        if (policies.unmatched() == RunConfig.ReplaceUnmatched.FAIL) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.GEN_REPLACEMENT_UNMATCHED, message, groupDirective.location(),
                    "sheet", sheet.name()));
        }
        context.diagnostics().add(Diagnostic.warning(ErrorCode.GEN_REPLACEMENT_UNMATCHED, message, groupDirective.location(),
                "sheet", sheet.name()));
    }

    /**
     * FW_Group's replacements as the legacy map kept them: keyed by pattern in written order (a repeated pattern
     * keeps its first place and its last replacement); a joined replacement whose sheet has no values is left out.
     */
    private Map<String, String> replacements(Directive.Group g) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Directive.Replacement r : g.replacements()) {
            if (r.sheetReference() == null) {
                map.put(r.pattern(), r.prefix());
                continue;
            }
            int[] values = context.values(r.sheetReference());
            if (values.length == 0) {
                context.diagnostics().add(Diagnostic.warning(ErrorCode.GEN_REPLACEMENT_UNRESOLVED, "sheet " + sheet.name()
                        + ": FW_ReplaceRE(\"" + r.pattern() + "\", " + r.written() + ") names '" + r.sheetReference()
                        + "', which has no values; the replacement is left out, as the legacy Core did", g.location(),
                        "sheet", sheet.name()));
                continue;
            }
            map.put(r.pattern(), r.prefix() + values[0] + r.suffix());
        }
        return map;
    }

    // ---- joiners -----------------------------------------------------------------------------------------------

    private void joiner(Directive.Joiner j) {
        RowSource a = operand(j.first());
        RowSource b = operand(j.second());
        for (Directive.Operand o : List.of(j.first(), j.second())) {
            if (o.sheet() != null) {
                context.consume(o.sheet(), sheet.name());
            }
        }
        JoinedRows joined = context.arena().own(JoinedRows.of(j.multiplicity(), a, b, first(j.relation()), first(j.separator()),
                first(j.start()), first(j.end()), context.encoding(), fwIds, context.budget(),
                purpose("the joiner '" + j.source() + "'"), context.cancellation()));
        fw = RowSources.concat(fw, joined);
        fwIds = fwIds.add(joined.size());
        fw2 = RowSources.concat(fw2, RowSources.withoutParents(fw));
        fw2Distinct = false;
        fw = RowSource.empty();
        fwDistinct = true;
        combi2 = true;
    }

    private RowSource operand(Directive.Operand operand) {
        if (operand.sheet() == null) {
            return RowSource.empty();
        }
        RowSource rows = context.operand(operand.sheet(), sheet.name());
        if (operand.kind() != Directive.OperandKind.NESTED_GROUPED || rows.size().signum() == 0) {
            return rows;
        }
        // FW_()G: the operand's rows as one row
        long octets = 0;
        try (RowCursor cursor = rows.open(context.cancellation())) {
            while (cursor.hasNext()) {
                octets += cursor.next().keys().payloadLength();
            }
        }
        RowBlocks one = context.arena().own(new RowBlocks(purpose("the grouped operand " + operand.sheet()), context.encoding(),
                context.budget()));
        materialized = true;
        MemoryBudget.Lease scratch = context.budget().reserve(64 + octets, purpose("joining the grouped operand " + operand.sheet()));
        try {
            byte[] all = new byte[Math.toIntExact(octets)];
            int at = 0;
            try (RowCursor cursor = rows.open(context.cancellation())) {
                while (cursor.hasNext()) {
                    KeyRow keys = cursor.next().keys();
                    keys.copyPayloadTo(all, at);
                    at += keys.payloadLength();
                }
            }
            one.append(BigInteger.ONE, BigInteger.ZERO, KeyRow.fromPayload(context.encoding(), all));
        } finally {
            scratch.close();
        }
        return one;
    }

    private Integer first(String sheetName) {
        if (sheetName.isEmpty()) {
            return null;
        }
        int[] values = context.values(sheetName);
        return values.length == 0 ? null : values[0];
    }

    // ---- distinct tables -----------------------------------------------------------------------------------------

    /**
     * Legacy distinctify of the active table: row content only, the first occurrence kept with its id and parent, in
     * order. A table known to have no repeated rows is left as it is.
     */
    private void distinctActive() {
        if (combi2) {
            if (!fw2Distinct) {
                fw2 = distinct(fw2, "the second table");
                fw2Distinct = true;
            }
        } else if (!fwDistinct) {
            fw = distinct(fw, "the first table");
            fwDistinct = true;
        }
    }

    private RowSource distinct(RowSource table, String which) {
        RowBlocks out = context.arena().own(new RowBlocks(purpose(which + " without repeated rows"), context.encoding(), context.budget()));
        out.indexContent();
        try (RowCursor rows = table.open(context.cancellation())) {
            while (rows.hasNext()) {
                Row row = rows.next();
                out.appendIfAbsent(row.id(), row.parent(), row.keys());
            }
        }
        out.dropContentIndex();
        materialized = true;
        return out;
    }

    private void noSourceRows(Directive directive) {
        context.diagnostics().add(Diagnostic.warning(ErrorCode.GEN_NO_SOURCE_ROWS, "sheet " + sheet.name() + ": '"
                + directive.source() + "' works on the rows of the earlier directives, and there are none", directive.location(),
                "sheet", sheet.name()));
    }
}
