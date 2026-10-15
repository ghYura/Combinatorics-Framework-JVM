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

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.constraints.CandidateView;
import org.ghyura.cf.constraints.ColumnLayout;
import org.ghyura.cf.constraints.Coverage;
import org.ghyura.cf.constraints.Py;
import org.ghyura.cf.constraints.RuleEngine;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.key.SelectionState;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.plan.SheetRole;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * The candidates of a plan (work package M03): the mandatory product of the mandatory sheets' tables, each mandatory
 * row combined with the absent branch and with every optional row of every assembled size, filtered by the scenario's
 * rules, optionally reduced by covering selection. Nothing is materialized but what the semantics need (de-duplicated
 * tables, held optional combinations, the rows covering selection works on), each reserved before it is allocated and
 * released on {@link #close()}.
 *
 * <p>Order (decision M03-D3): the absent branch of every mandatory row first, then for each optional size ascending every
 * mandatory row with every optional row of that size; this is the legacy Reader's pass structure (final-only pass, then
 * final x fw_opt{k}) without its chunking and thread interleaving. Mandatory rows follow the legacy product: columns in
 * ascending sheet key, the first slowest.</p>
 *
 * <p>The tables of the generation result must stay open while the assembly is in use; close the assembly first.</p>
 */
public final class Assembly implements AutoCloseable {

    private final List<Factor> columns;
    private final List<Factor> mandatory;
    private final int[] mandatoryColumn;
    private final List<Factor> optional;
    private final int[] optionalColumn;
    private final BigInteger mandatorySize;
    private final BigInteger mandatoryUncut;
    private final boolean absentIncluded;
    private final List<OptionalSpace> spaces;
    private final RuleEngine rules;
    private final List<Diagnostic> diagnostics;
    private final String context;
    private final Arena arena;
    private final Row[] baseline;
    private final long[] selected;
    // the same ranks, ascending, for membership questions
    private final long[] selectedSorted;
    private final Coverage.Selection selection;
    private final List<String> dropped;
    private final long coverRemovedRows;
    private final long[] coverRemovedByRule;
    private final long coverRemovedOverlap;

    private Assembly(Builder b) {
        this.columns = b.columns;
        this.mandatory = b.mandatory;
        this.mandatoryColumn = b.mandatoryColumn;
        this.optional = b.optional;
        this.optionalColumn = b.optionalColumn;
        this.mandatorySize = b.mandatorySize;
        this.mandatoryUncut = b.mandatoryUncut;
        this.absentIncluded = b.absentIncluded;
        this.spaces = b.spaces;
        this.rules = b.rules;
        this.diagnostics = List.copyOf(b.diagnostics);
        this.context = b.context;
        this.arena = b.arena;
        this.baseline = b.baseline;
        this.selected = b.selected;
        this.selectedSorted = b.selected == null ? null : b.selected.clone();
        if (selectedSorted != null) {
            java.util.Arrays.sort(selectedSorted);
        }
        this.selection = b.selection;
        this.dropped = List.copyOf(b.dropped);
        this.coverRemovedRows = b.removedRows;
        this.coverRemovedByRule = b.removedByRule;
        this.coverRemovedOverlap = b.removedOverlap;
    }

    private static final class Builder {
        List<Factor> columns = new ArrayList<>();
        List<Factor> mandatory = new ArrayList<>();
        int[] mandatoryColumn;
        List<Factor> optional = new ArrayList<>();
        int[] optionalColumn;
        BigInteger mandatorySize;
        BigInteger mandatoryUncut;
        boolean absentIncluded;
        List<OptionalSpace> spaces = new ArrayList<>();
        RuleEngine rules = RuleEngine.NONE;
        List<Diagnostic> diagnostics = new ArrayList<>();
        String context;
        Arena arena;
        Row[] baseline;
        long[] selected;
        Coverage.Selection selection;
        List<String> dropped = new ArrayList<>();
        long removedRows;
        long[] removedByRule;
        long removedOverlap;
    }

    /**
     * Plans the candidates of a generated plan.
     *
     * @param plan       the plan the tables were generated from
     * @param generation the generated tables; must stay open while the assembly is used
     * @param dictionary the run's key dictionary (labels, value texts, the scenario and its constraint layer)
     * @param seed       the run seed, part of every candidate's identity (D05)
     * @throws FrameworkException CONSTRAINT_* when the rules cannot apply to these candidates, CONFIG_INCONSISTENT when the
     *                            optional sizes are not within the produced ones, RESOURCE_LIMIT when a held structure does
     *                            not fit the budget, ASSEMBLY_COVERAGE_REFUSED, RUN_CANCELLED
     */
    public static Assembly of(SeqPlan plan, GenerationResult generation, KeyDictionary dictionary, GenerationOptions generationOptions,
                              AssemblyOptions options, RenderOptions render, long seed, MemoryBudget budget, Cancellation cancellation) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(dictionary, "dictionary");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(render, "render");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(cancellation, "cancellation");
        Builder b = new Builder();
        b.arena = new Arena();
        try {
            build(b, plan, generation, dictionary, generationOptions, options, render, seed, budget, cancellation);
            return new Assembly(b);
        } catch (RuntimeException | Error e) {
            b.arena.close();
            throw e;
        }
    }

    private static void build(Builder b, SeqPlan plan, GenerationResult generation, KeyDictionary dictionary,
                              GenerationOptions generationOptions, AssemblyOptions options, RenderOptions render, long seed,
                              MemoryBudget budget, Cancellation cancellation) {
        // ---- columns: active mandatory and optional sheets a joiner did not consume, in sheet-key order
        for (SheetOutcome outcome : generation.sheets()) {
            if (outcome.consumedBy() != null || (outcome.role() != SheetRole.MANDATORY && outcome.role() != SheetRole.OPTIONAL)) {
                continue;
            }
            SheetPlan sheet = plan.sheet(outcome.sheet()).orElseThrow();
            boolean isOptional = outcome.role() == SheetRole.OPTIONAL;
            if (!isOptional && outcome.empty()) {
                if (options.emptyStage() == RunConfig.EmptyStagePolicy.LEGACY_DROP_WITH_DIAGNOSTIC) {
                    b.dropped.add(sheet.name());
                    b.diagnostics.add(Diagnostic.warning(ErrorCode.ASSEMBLY_SHEET_DROPPED, "the mandatory sheet " + sheet.name()
                            + " has no rows; cf.generation.emptyStagePolicy=LEGACY_DROP_WITH_DIAGNOSTIC drops it from the candidates "
                            + "as the legacy Core did, so they no longer contain that column (a weaker scenario than the one written)",
                            sheet.location(), "sheet", sheet.name()));
                    continue;
                }
                b.diagnostics.add(Diagnostic.warning(ErrorCode.ASSEMBLY_EMPTY_FACTOR, "the mandatory sheet " + sheet.name()
                        + " has no rows, so no candidate exists: zero candidates are executable and nothing passed "
                        + "(cf.generation.emptyStagePolicy=PRESERVE_EMPTY; the legacy Core dropped the sheet instead)",
                        sheet.location(), "sheet", sheet.name()));
            }
            String concatenator = render.sheetConcatenator() == RunConfig.SheetConcatenator.APPLY ? sheet.concatenator() : null;
            Factor factor = options.distinct()
                    ? Factor.distinct(sheet.name(), sheet.key(), isOptional, sheet.location(), concatenator, outcome.rows(), budget,
                    cancellation, b.arena)
                    : Factor.of(sheet.name(), sheet.key(), isOptional, sheet.location(), concatenator, outcome.rows());
            b.columns.add(factor);
        }
        b.columns.sort((x, y) -> Integer.compare(x.key, y.key));
        List<Integer> mc = new ArrayList<>();
        List<Integer> oc = new ArrayList<>();
        for (int c = 0; c < b.columns.size(); c++) {
            Factor f = b.columns.get(c);
            if (f.optional) {
                b.optional.add(f);
                oc.add(c);
            } else {
                b.mandatory.add(f);
                mc.add(c);
            }
        }
        b.mandatoryColumn = mc.stream().mapToInt(Integer::intValue).toArray();
        b.optionalColumn = oc.stream().mapToInt(Integer::intValue).toArray();

        // ---- the mandatory product and its legacy cut-off
        BigInteger product = BigInteger.ONE;
        for (Factor f : b.mandatory) {
            product = product.multiply(f.size());
        }
        b.mandatoryUncut = product;
        b.mandatorySize = product;
        if (options.mandatoryLimit() != Long.MAX_VALUE && product.signum() > 0 && !b.mandatory.isEmpty()) {
            BigInteger legacyRows = legacyMandatoryRows(b.mandatory, options.mandatoryLimit(), cancellation);
            BigInteger kept = keptBelow(b.mandatory, legacyRows);
            if (kept.compareTo(product) < 0) {
                b.mandatorySize = kept;
                b.diagnostics.add(Diagnostic.warning(ErrorCode.ASSEMBLY_TRUNCATED, "the mandatory product keeps its first " + kept + " of "
                        + product + " rows (cf.assembly.mandatoryLimit=" + options.mandatoryLimit() + ", the legacy limitVar rule)",
                        b.mandatory.getFirst().where));
            }
        }
        b.baseline = new Row[b.mandatory.size()];
        for (int i = 0; i < b.mandatory.size(); i++) {
            Factor f = b.mandatory.get(i);
            if (f.size().signum() > 0) {
                b.baseline[i] = f.row(BigInteger.ZERO);
            } else {
                b.diagnostics.add(Diagnostic.warning(ErrorCode.ASSEMBLY_BASELINE_UNAVAILABLE, "the mandatory sheet " + f.sheet
                        + " has no first row, so it has no baseline; nothing refers to it and no value is invented", f.where,
                        "sheet", f.sheet));
            }
        }

        // ---- the optional contract
        int n = b.optional.size();
        boolean enabled = switch (options.optional()) {
            case AUTO -> n > 0;
            case ON -> true;
            case OFF -> false;
        };
        b.absentIncluded = !enabled || options.includeAbsent();
        if (enabled) {
            if (!options.sizes().within(options.producedSizes(), n)) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT, "cf.optional.sizes=" + options.sizes().token()
                        + " assembles optional combinations that cf.optional.producedSizes=" + options.producedSizes().token()
                        + " does not produce (the legacy Reader would have read a table the Core never wrote)", SourceLocation.NONE));
            }
            if (!options.sizes().every()) {
                for (int size : options.sizes().sizes()) {
                    if (size > n) {
                        b.diagnostics.add(Diagnostic.warning(ErrorCode.ASSEMBLY_SIZE_UNUSED, "the optional size " + size + " is larger "
                                + "than the " + n + " optional sheet(s), so it forms no combination", SourceLocation.NONE,
                                "size", Integer.toString(size)));
                    }
                }
            }
            for (int k = 1; k <= n; k++) {
                cancellation.throwIfCancelled();
                if (options.sizes().contains(k, n)) {
                    b.spaces.add(OptionalSpace.of(k, b.optional, options, budget, b.arena, b.diagnostics, cancellation));
                }
            }
        }

        // ---- the rules
        ScenarioSpec spec = dictionary.spec();
        if (!spec.constraints().isEmpty()) {
            List<ColumnLayout.Column> layout = new ArrayList<>();
            for (Factor f : b.columns) {
                layout.add(new ColumnLayout.Column(f.sheet, f.key, f.optional));
            }
            Set<String> sheets = new LinkedHashSet<>();
            plan.sheets().forEach(s -> sheets.add(s.name()));
            List<String> texts = new ArrayList<>();
            for (int label = dictionary.firstLabel(); label <= dictionary.lastLabel(); label++) {
                texts.add(Py.strip(dictionary.value(label).text()));
            }
            int first = dictionary.firstLabel();
            b.rules = RuleEngine.compile(spec.constraints(), new ColumnLayout(layout), sheets, label -> texts.get(label - first), texts);
            b.diagnostics.addAll(b.rules.findings());
        }

        // ---- identity context (D05)
        b.context = "cf.candidate/1\nplan=" + plan.contentDigest() + "\ndictionary=" + dictionary.identity() + "\ngeneration="
                + (generationOptions == null ? "-" : generationOptions.canonical()) + "\nassembly=" + options.canonical() + "\nrender="
                + render.canonical() + "\nseed=" + seed + "\ncolumns=" + columnsText(b.columns);

        // ---- covering selection
        if (options.coverage()) {
            cover(b, options, budget, cancellation);
        }
    }

    private static String columnsText(List<Factor> columns) {
        StringBuilder out = new StringBuilder();
        for (Factor f : columns) {
            out.append(out.isEmpty() ? "" : ",").append(f.key).append(f.optional ? 'o' : 'm').append(':').append(Canonical.escape(f.sheet));
        }
        return out.toString();
    }

    // ---- the legacy cut-off ------------------------------------------------------------------------------------------

    /**
     * FinalTableAssembler.computeLimitVar and the truncated limit: the product of the trailing sheets' largest ids while
     * it stays at or below the limit (1 when even the last exceeds it), capped by the product's own row count.
     */
    private static BigInteger legacyMandatoryRows(List<Factor> factors, long limit, Cancellation cancellation) {
        BigInteger bound = BigInteger.valueOf(limit);
        BigInteger product = BigInteger.ONE;
        BigInteger limitVar = BigInteger.ONE;
        for (int i = factors.size() - 1; i >= 0; i--) {
            product = product.multiply(factors.get(i).maxId(cancellation));
            if (product.compareTo(bound) > 0) {
                break;
            }
            limitVar = product;
        }
        BigInteger rows = BigInteger.ONE;
        for (Factor f : factors) {
            rows = rows.multiply(f.originalSize());
        }
        return rows.min(limitVar);
    }

    /** The legacy row cap of one optional subset: min(product, limit). */
    static BigInteger legacySubsetRows(BigInteger legacyProduct, long limit) {
        return limit == Long.MAX_VALUE ? legacyProduct : legacyProduct.min(BigInteger.valueOf(limit));
    }

    /**
     * How many rows of the product of {@code factors} (as read, possibly de-duplicated) have an original product rank
     * below {@code legacyRows}: the rows the legacy kept when it cut the product first and de-duplicated afterwards. The
     * original rank grows along the product, so they are a prefix, found by bisection.
     */
    static BigInteger keptBelow(List<Factor> factors, BigInteger legacyRows) {
        BigInteger size = BigInteger.ONE;
        boolean dedup = false;
        for (Factor f : factors) {
            size = size.multiply(f.size());
            dedup |= f.deduplicated();
        }
        if (!dedup) {
            return size.min(legacyRows);
        }
        BigInteger low = BigInteger.ZERO;
        BigInteger high = size;
        while (low.compareTo(high) < 0) {
            BigInteger middle = low.add(high).shiftRight(1);
            if (originalRank(factors, digits(factors, middle)).compareTo(legacyRows) < 0) {
                low = middle.add(BigInteger.ONE);
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** The mixed-radix digits of a product rank, the first factor slowest. */
    static BigInteger[] digits(List<Factor> factors, BigInteger rank) {
        BigInteger[] d = new BigInteger[factors.size()];
        BigInteger rest = rank;
        for (int i = factors.size() - 1; i >= 0; i--) {
            BigInteger[] qr = rest.divideAndRemainder(factors.get(i).size());
            d[i] = qr[1];
            rest = qr[0];
        }
        return d;
    }

    /** The rank the same row has in the product of the sheets' original tables (the legacy combination id minus one). */
    static BigInteger originalRank(List<Factor> factors, BigInteger[] digits) {
        BigInteger rank = BigInteger.ZERO;
        for (int i = 0; i < factors.size(); i++) {
            rank = rank.multiply(factors.get(i).originalSize()).add(factors.get(i).originalRank(digits[i]));
        }
        return rank;
    }

    // ---- covering selection -------------------------------------------------------------------------------------------

    private static void cover(Builder b, AssemblyOptions options, MemoryBudget budget, Cancellation cancellation) {
        if (b.mandatory.isEmpty()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.ASSEMBLY_COVERAGE_REFUSED, "covering selection needs mandatory "
                    + "sheets to cover; this plan has none", SourceLocation.NONE));
        }
        if (b.mandatorySize.compareTo(BigInteger.valueOf(options.coverageHardCap())) > 0) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.ASSEMBLY_COVERAGE_REFUSED, "covering selection works on the "
                    + b.mandatorySize + " mandatory rows, more than cf.coverage.hardCap=" + options.coverageHardCap(), SourceLocation.NONE));
        }
        int k = b.mandatory.size();
        int[] levels = new int[k];
        for (int i = 0; i < k; i++) {
            levels[i] = b.mandatory.get(i).size().intValueExact();
        }
        long rowsTotal = b.mandatorySize.longValueExact();
        b.arena.own(budget.reserve(64 + rowsTotal * (40L + 4L * k), "the mandatory rows covering selection works on"));
        List<int[]> rows = new ArrayList<>();
        List<Long> ranks = new ArrayList<>();
        Assembly probe = new Assembly(b);
        b.removedByRule = new long[b.rules.size()];
        for (long r = 0; r < rowsTotal; r++) {
            if ((r & 1023) == 0) {
                cancellation.throwIfCancelled();
            }
            BigInteger[] digits = digits(b.mandatory, BigInteger.valueOf(r));
            if (b.rules.hasMandatoryRules()) {
                BitSet hits = probe.mandatoryHits(digits);
                if (!hits.isEmpty()) {
                    b.removedRows++;
                    hits.stream().forEach(i -> b.removedByRule[i]++);
                    if (hits.cardinality() > 1) {
                        b.removedOverlap++;
                    }
                    continue;
                }
            }
            int[] row = new int[k];
            for (int i = 0; i < k; i++) {
                row[i] = digits[i].intValueExact();
            }
            rows.add(row);
            ranks.add(r);
        }
        Coverage.Universe universe = Coverage.universe(levels, Math.min(Math.max(1, options.coverageStrength()), k));
        b.arena.own(budget.reserve(3 * universe.bitSetBytes(), "the t-tuple universe of covering selection"));
        Coverage.Selection selection = options.coverageBudget() > 0
                ? Coverage.forBudget(rows, levels, options.coverageBudget(), options.coverageMethod(), cancellation)
                : Coverage.select(rows, levels, options.coverageStrength(), options.coverageMethod(), cancellation);
        int[] kept = selection.kept();
        long[] selected = new long[kept.length];
        for (int i = 0; i < kept.length; i++) {
            selected[i] = ranks.get(kept[i]);
        }
        b.selected = selected;
        b.selection = selection;
        if (selection.infeasible() > 0) {
            b.diagnostics.add(Diagnostic.info(ErrorCode.ASSEMBLY_COVERAGE_INFEASIBLE, selection.infeasible() + " of " + selection.universe()
                    + " " + selection.strength() + "-tuples of the mandatory sheets' rows appear in no row the rules allow; they are "
                    + "infeasible, not uncovered (" + selection.feasible() + " feasible, all covered)", SourceLocation.NONE));
        }
    }

    // ---- what the plan holds --------------------------------------------------------------------------------------

    /** Findings of the assembly: empty or dropped sheets, cut-offs, skipped subsets, inert rules, infeasible tuples. */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /** The sheets of the candidates' columns, in rendering order. */
    public List<String> columnSheets() {
        return columns.stream().map(f -> f.sheet).toList();
    }

    /** Whether column {@code c} is an optional sheet. */
    public boolean optionalColumn(int c) {
        return columns.get(c).optional;
    }

    /** The FW_Concatenator= text applied to column {@code c}, or null. */
    String concatenator(int c) {
        return columns.get(c).concatenator;
    }

    /** The mandatory sheets dropped under LEGACY_DROP_WITH_DIAGNOSTIC. */
    public List<String> droppedSheets() {
        return dropped;
    }

    public RuleEngine rules() {
        return rules;
    }

    /** Rows of the mandatory product (1 for a plan without mandatory sheets: the structural empty tuple). */
    public BigInteger mandatoryRows() {
        return mandatorySize;
    }

    /** Rows of the mandatory product before the legacy cut-off. */
    public BigInteger mandatoryRowsUncut() {
        return mandatoryUncut;
    }

    /** Candidates per mandatory row: the absent branch (when included) plus every optional row of every assembled size. */
    public BigInteger perMandatoryRow() {
        BigInteger w = absentIncluded ? BigInteger.ONE : BigInteger.ZERO;
        for (OptionalSpace s : spaces) {
            w = w.add(s.rows);
        }
        return w;
    }

    /** The size of the generated candidate space, before rules and covering selection. */
    public BigInteger generated() {
        return mandatorySize.multiply(perMandatoryRow());
    }

    /** The optional sizes assembled, ascending, with their row counts (the legacy fw_opt{k} tables). */
    public List<long[]> optionalSizes() {
        List<long[]> out = new ArrayList<>();
        for (OptionalSpace s : spaces) {
            out.add(new long[]{s.size, s.rows.bitLength() < 63 ? s.rows.longValue() : -1});
        }
        return out;
    }

    public boolean absentIncluded() {
        return absentIncluded;
    }

    /** Whether covering selection kept the mandatory row of this rank; every row is kept without a selection. */
    public boolean selectedRow(BigInteger mandatoryRank) {
        return selectedSorted == null || mandatoryRank.bitLength() < 64
                && java.util.Arrays.binarySearch(selectedSorted, mandatoryRank.longValue()) >= 0;
    }

    /** The covering selection, or null when none was requested. */
    public Coverage.Selection coverage() {
        return selection;
    }

    /** The identity context every candidate's id is computed in. */
    public String identityContext() {
        return context;
    }

    // ---- candidates -------------------------------------------------------------------------------------------------

    /** The mandatory rows walked: every row of the product, or the rows covering selection kept. */
    private BigInteger mandatoryCount() {
        return selected == null ? mandatorySize : BigInteger.valueOf(selected.length);
    }

    private BigInteger mandatoryRank(BigInteger position) {
        return selected == null ? position : BigInteger.valueOf(selected[position.intValueExact()]);
    }

    /** The rules a mandatory row violates (the early phase), given its digits. */
    BitSet mandatoryHits(BigInteger[] digits) {
        Row[] rows = new Row[columns.size()];
        for (int i = 0; i < mandatory.size(); i++) {
            rows[mandatoryColumn[i]] = mandatory.get(i).row(digits[i]);
        }
        return rules.violations(column -> rows[column] == null ? null : rows[column].keys().labels(), true);
    }

    /** Every candidate that is not excluded, in canonical order, polling {@code cancellation}. */
    public Iterator<Candidate> candidates(Cancellation cancellation) {
        Walk walk = new Walk(cancellation);
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return walk.hasNext();
            }

            @Override
            public Candidate next() {
                return walk.next().candidate();
            }
        };
    }

    /** One assembled candidate of a mandatory row the early rules kept, with the late rules it violates. */
    record Step(Candidate candidate, BitSet lateHits) {
    }

    /** A generated candidate with every rule it violates (both phases); empty when it is kept. */
    public record Assessed(Candidate candidate, BitSet violations) {
        public Assessed {
            violations = (BitSet) violations.clone();
        }

        @Override
        public BitSet violations() {
            return (BitSet) violations.clone();
        }
    }

    /**
     * Every generated candidate, before covering selection, in canonical order, with every rule it violates: what the
     * legacy sieve's dry run reported (row_violations collects every rule, for per-rule, overlap and sequential counts).
     * The legacy never assembled the optional forms of a mandatory row its early rules removed; this view does, so a
     * report can say which rules each of them breaks.
     */
    public Iterator<Assessed> assessed(Cancellation cancellation) {
        return assessed(cancellation, BigInteger.ZERO);
    }

    /**
     * As {@link #assessed(Cancellation)}, from the generated candidate whose ordinal is {@code from}: the walk starts
     * there directly (absent branch, then each optional size, mandatory rows by rank), reading nothing before it.
     */
    public Iterator<Assessed> assessed(Cancellation cancellation, BigInteger from) {
        Objects.requireNonNull(cancellation, "cancellation");
        if (from.signum() < 0) {
            throw new IllegalArgumentException("a generated ordinal is 0 or more: " + from);
        }
        return new Iterator<>() {
            private final Walk walk = new Walk(cancellation, true, true).from(from);

            @Override
            public boolean hasNext() {
                return walk.hasNext();
            }

            @Override
            public Assessed next() {
                Candidate c = walk.next().candidate();
                return new Assessed(c, rules.violations(c));
            }
        };
    }

    /**
     * The walk over phases (absent, then each size), the mandatory rows the early rules (or covering selection) keep, and
     * their optional rows; every assembled candidate is returned with its late-rule hits.
     */
    final class Walk implements Iterator<Step> {
        private final Cancellation cancellation;
        private final boolean keepExcluded;
        private final boolean everyRow;    // also the rows the early rules remove and covering selection leaves out
        private int phase;                 // -1 absent, then index into spaces
        private BigInteger position = BigInteger.ZERO;
        private BigInteger local = BigInteger.ZERO;
        private BigInteger[] mDigits;
        private Row[] mRows;
        private boolean rowChecked;
        private Step next;
        private long polls;

        /** Only the candidates no rule excludes. */
        Walk(Cancellation cancellation) {
            this(cancellation, false);
        }

        Walk(Cancellation cancellation, boolean keepExcluded) {
            this(cancellation, keepExcluded, false);
        }

        Walk(Cancellation cancellation, boolean keepExcluded, boolean everyRow) {
            this.cancellation = cancellation;
            this.keepExcluded = keepExcluded;
            this.everyRow = everyRow;
            this.phase = absentIncluded ? -1 : 0;
        }

        /** Starts a walk over every row at a generated ordinal (past the end: an empty walk). */
        Walk from(BigInteger ordinal) {
            if (!everyRow) {
                throw new IllegalStateException("only a walk over every row starts at an ordinal");
            }
            BigInteger rest = ordinal;
            if (absentIncluded) {
                if (rest.compareTo(mandatorySize) < 0) {
                    position = rest;
                    return this;
                }
                rest = rest.subtract(mandatorySize);
            }
            for (int p = 0; p < spaces.size(); p++) {
                BigInteger width = spaces.get(p).rows;
                BigInteger block = mandatorySize.multiply(width);
                if (rest.compareTo(block) < 0) {
                    BigInteger[] qr = rest.divideAndRemainder(width);
                    phase = p;
                    position = qr[0];
                    local = qr[1];
                    return this;
                }
                rest = rest.subtract(block);
            }
            phase = spaces.size();
            return this;
        }

        private BigInteger rows() {
            return everyRow ? mandatorySize : mandatoryCount();
        }

        private BigInteger rank(BigInteger position) {
            return everyRow ? position : mandatoryRank(position);
        }

        @Override
        public boolean hasNext() {
            if (next == null) {
                next = advance();
            }
            return next != null;
        }

        @Override
        public Step next() {
            if (!hasNext()) {
                throw new NoSuchElementException("no more candidates");
            }
            Step c = next;
            next = null;
            return c;
        }

        private BigInteger width() {
            return phase < 0 ? BigInteger.ONE : spaces.get(phase).rows;
        }

        private Step advance() {
            while (true) {
                if ((++polls & 255) == 0) {
                    cancellation.throwIfCancelled();
                }
                if (phase >= spaces.size()) {
                    return null;
                }
                if (position.compareTo(rows()) >= 0 || width().signum() == 0) {
                    phase++;
                    position = BigInteger.ZERO;
                    local = BigInteger.ZERO;
                    mDigits = null;
                    continue;
                }
                if (mDigits == null) {
                    BigInteger rank = rank(position);
                    mDigits = digits(mandatory, rank);
                    mRows = new Row[mandatory.size()];
                    for (int i = 0; i < mandatory.size(); i++) {
                        mRows[i] = mandatory.get(i).row(mDigits[i]);
                    }
                    rowChecked = false;
                }
                if (!rowChecked) {
                    rowChecked = true;
                    if (!everyRow && selected == null && rules.hasMandatoryRules() && !mandatoryHits(mDigits).isEmpty()) {
                        nextMandatory();
                        continue;
                    }
                }
                Candidate c = assemble();
                stepLocal();
                BitSet late = !everyRow && rules.hasOptionalRules() ? rules.violations(c, false) : new BitSet();
                if (!late.isEmpty() && !keepExcluded) {
                    continue;
                }
                return new Step(c, late);
            }
        }

        private void stepLocal() {
            local = local.add(BigInteger.ONE);
            if (local.compareTo(width()) >= 0) {
                nextMandatory();
            }
        }

        private void nextMandatory() {
            position = position.add(BigInteger.ONE);
            local = BigInteger.ZERO;
            mDigits = null;
        }

        private Candidate assemble() {
            int count = columns.size();
            Row[] rows = new Row[count];
            SelectionState[] states = new SelectionState[count];
            java.util.Arrays.fill(states, SelectionState.ABSENT);
            BigInteger[] originals = new BigInteger[mandatory.size()];
            for (int i = 0; i < mandatory.size(); i++) {
                int c = mandatoryColumn[i];
                rows[c] = mRows[i];
                states[c] = baseline[i] != null && baseline[i].keys().equals(mRows[i].keys()) ? SelectionState.BASELINE_REFERENCE
                        : SelectionState.PRESENT;
                originals[i] = mandatory.get(i).originalRank(mDigits[i]);
            }
            BigInteger mRank = rank(position);
            BigInteger finalId = originalRank(mandatory, mDigits).add(BigInteger.ONE);
            StringBuilder coordinates = new StringBuilder("m=");
            for (int i = 0; i < originals.length; i++) {
                coordinates.append(i == 0 ? "" : ",").append(originals[i]);
            }
            BigInteger ordinal;
            String display;
            int size;
            BigInteger optionalRank;
            if (phase < 0) {
                ordinal = mRank;
                display = finalId + "_0_0";
                size = 0;
                optionalRank = BigInteger.ONE.negate();
                coordinates.append(";b=-");
            } else {
                OptionalSpace space = spaces.get(phase);
                BigInteger before = absentIncluded ? mandatorySize : BigInteger.ZERO;
                for (int p = 0; p < phase; p++) {
                    before = before.add(mandatorySize.multiply(spaces.get(p).rows));
                }
                ordinal = before.add(mRank.multiply(space.rows)).add(local);
                int s = space.subsetAt(local);
                OptionalSpace.Subset subset = space.subsets.get(s);
                List<Factor> subsetFactors = new ArrayList<>();
                for (int f : subset.factors()) {
                    subsetFactors.add(optional.get(f));
                }
                BigInteger[] oDigits = digits(subsetFactors, local.subtract(subset.offset()));
                coordinates.append(";b=").append(space.size).append(':');
                for (int i = 0; i < subset.factors().length; i++) {
                    int f = subset.factors()[i];
                    int c = optionalColumn[f];
                    rows[c] = subsetFactors.get(i).row(oDigits[i]);
                    states[c] = SelectionState.PRESENT;
                    coordinates.append(i == 0 ? "" : ".").append(columns.get(c).key);
                }
                coordinates.append(':');
                for (int i = 0; i < oDigits.length; i++) {
                    coordinates.append(i == 0 ? "" : ",").append(subsetFactors.get(i).originalRank(oDigits[i]));
                }
                BigInteger optId = subset.legacyOffset().add(originalRank(subsetFactors, oDigits)).add(BigInteger.ONE);
                display = finalId + "_" + optId + "_" + space.size;
                size = space.size;
                optionalRank = local;
            }
            return new Candidate(ordinal, mRank, size, optionalRank, java.util.Arrays.asList(rows), java.util.Arrays.asList(states),
                    display, coordinates.toString(), context);
        }
    }

    /**
     * The exact counts of the candidate space: generated, removed by the early (mandatory-only) rules, left out by
     * covering selection, removed by the late rules, executable, and per rule. Counting needs every mandatory row the
     * early rules read and every candidate the late rules read; without rules and selection it is arithmetic.
     *
     * @param scanLimit the most candidates (and mandatory rows) a scan may visit; beyond it the counts are not exact
     */
    public AssemblyCounts count(Cancellation cancellation, BigInteger scanLimit) {
        BigInteger w = perMandatoryRow();
        BigInteger generated = mandatorySize.multiply(w);
        int n = rules.size();
        if (rules.isEmpty() && selection == null) {
            return AssemblyCounts.exact(generated, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, generated, new BigInteger[0],
                    BigInteger.ZERO);
        }
        BigInteger[] perRule = new BigInteger[n];
        java.util.Arrays.fill(perRule, BigInteger.ZERO);
        BigInteger early = BigInteger.ZERO;
        BigInteger overlap = BigInteger.ZERO;
        BigInteger unselected = BigInteger.ZERO;
        BigInteger kept;
        if (selection != null) {
            early = BigInteger.valueOf(coverRemovedRows).multiply(w);
            for (int i = 0; i < n; i++) {
                perRule[i] = perRule[i].add(BigInteger.valueOf(coverRemovedByRule[i]).multiply(w));
            }
            overlap = overlap.add(BigInteger.valueOf(coverRemovedOverlap).multiply(w));
            long allowed = mandatorySize.longValueExact() - coverRemovedRows;
            unselected = BigInteger.valueOf(allowed - selected.length).multiply(w);
            kept = BigInteger.valueOf(selected.length);
        } else if (rules.hasMandatoryRules()) {
            if (mandatorySize.compareTo(scanLimit) > 0) {
                return AssemblyCounts.unknown(generated);
            }
            long rows = 0;
            BigInteger removedRows = BigInteger.ZERO;
            for (BigInteger r = BigInteger.ZERO; r.compareTo(mandatorySize) < 0; r = r.add(BigInteger.ONE)) {
                if ((++rows & 1023) == 0) {
                    cancellation.throwIfCancelled();
                }
                BitSet hits = mandatoryHits(digits(mandatory, r));
                if (!hits.isEmpty()) {
                    removedRows = removedRows.add(BigInteger.ONE);
                    early = early.add(w);
                    hits.stream().forEach(i -> perRule[i] = perRule[i].add(w));
                    if (hits.cardinality() > 1) {
                        overlap = overlap.add(w);
                    }
                }
            }
            kept = mandatorySize.subtract(removedRows);
        } else {
            kept = mandatorySize;
        }
        BigInteger late = BigInteger.ZERO;
        if (rules.hasOptionalRules()) {
            if (kept.multiply(w).compareTo(scanLimit) > 0) {
                return AssemblyCounts.unknown(generated);
            }
            Walk walk = new Walk(cancellation, true);
            while (walk.hasNext()) {
                BitSet hits = walk.next().lateHits();
                if (!hits.isEmpty()) {
                    late = late.add(BigInteger.ONE);
                    hits.stream().forEach(i -> perRule[i] = perRule[i].add(BigInteger.ONE));
                    if (hits.cardinality() > 1) {
                        overlap = overlap.add(BigInteger.ONE);
                    }
                }
            }
        }
        BigInteger executable = generated.subtract(early).subtract(unselected).subtract(late);
        return AssemblyCounts.exact(generated, early, unselected, late, executable, perRule, overlap);
    }

    /** A view of the candidate the rules read, for callers that check one themselves. */
    public static CandidateView view(Candidate candidate) {
        return candidate;
    }

    @Override
    public void close() {
        arena.close();
    }

    /** Values of the dictionary in label order (for reports). */
    static List<ValueCell> values(KeyDictionary dictionary) {
        List<ValueCell> out = new ArrayList<>();
        for (int label = dictionary.firstLabel(); label <= dictionary.lastLabel(); label++) {
            out.add(dictionary.value(label));
        }
        return out;
    }
}
