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

package org.ghyura.cf.store;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.key.KeyCensus;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyEncodingPolicy;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.key.LabelWindow;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;

import java.util.Objects;

/**
 * The run-scoped dictionary: a bijection between value labels and the values of the scenario, plus the one
 * key encoding the whole plan uses. It replaces the legacy {@code number_to_value} table and the parser's
 * static maps; nothing here is shared between runs and nothing is a database row.
 *
 * <p>Labels follow the legacy identity numbering the byte-migration contract preserves (B06): data sheet keys
 * are 1..S (a separate namespace, B08) and values are labelled S+1..S+C in sheet order, duplicates included,
 * each with its own label while that numbering fits. Otherwise each namespace moves into the encoding's full
 * signed range before widening or refusing. Sheet ordinals in the spec and plan remain stable 1-based int indices;
 * {@link #sheetLabel(int)} gives their encoded labels. Under cf.keys.logical.labels=legacy-window a scenario whose S+C exceeds 32767 is
 * labelled in the legacy 16-bit tier's cheapest window instead ({@link LabelWindow}, decision F2). Virtual sheets take the sheet keys S+1..S+V in the sheet namespace. The census
 * covers all of them and decides, once, whether one byte per key is enough (B04).</p>
 *
 * <p>Memory ownership: from {@link #build} on, the dictionary owns the scenario. It keeps the
 * {@link ScenarioSpec} for the later stages ({@link #spec()}), and its one lease covers everything reachable
 * from it (sheet names, values, control sheets, FW_Seq rows, every source location) plus the dictionary's own
 * arrays, so retained heap cannot grow without the reservation growing. Nothing else charges the spec again.
 * The lease is taken before the dictionary allocates anything; it does not cover what reading and
 * interpreting the input allocated before {@code build} was called (that is bounded by the input size
 * limits of fw-input, not by this budget).</p>
 */
public final class KeyDictionary implements AutoCloseable {

    private final ScenarioSpec spec;
    private final int virtualSheets;
    private final KeyEncoding encoding;
    private final KeyCensus.Snapshot census;
    private final int firstLabel;
    private final int firstSheetLabel;
    private final LabelWindow window;
    private final ValueCell[] values;
    private final int[] sheetStart;
    private final MemoryBudget.Lease lease;
    private boolean closed;

    private KeyDictionary(ScenarioSpec spec, int virtualSheets, KeyEncoding encoding, KeyCensus.Snapshot census,
                          LabelWindow window, int firstSheetLabel, ValueCell[] values, int[] sheetStart, MemoryBudget.Lease lease) {
        this.spec = spec;
        this.virtualSheets = virtualSheets;
        this.encoding = encoding;
        this.census = census;
        this.window = window;
        this.firstLabel = window.firstLabel();
        this.firstSheetLabel = firstSheetLabel;
        this.values = values;
        this.sheetStart = sheetStart;
        this.lease = lease;
    }

    /**
     * @param virtualSheets how many sheets the plan generated for FW_Seq references that name no data sheet
     * @param policy        the configured key encoding policy
     * @throws FrameworkException KEY_CENSUS_OVERFLOW or KEY_LABEL_RESERVED when the labels do not fit what the
     *                            policy allows; RESOURCE_LIMIT when the dictionary does not fit the budget
     */
    public static KeyDictionary build(ScenarioSpec spec, int virtualSheets, KeyEncodingPolicy policy, MemoryBudget budget) {
        return build(spec, virtualSheets, policy, LabelWindow.Mode.PRESERVE, LabelWindow.Scope.ALL, budget);
    }

    /**
     * A dictionary whose value labels are placed by {@code mode} (decision F2): the legacy numbering, or the legacy
     * 16-bit tier's window when that numbering does not fit.
     *
     * @throws FrameworkException KEY_CENSUS_OVERFLOW or KEY_LABEL_RESERVED when the labels do not fit what the policy or
     *                            the legacy-window gate allows; RESOURCE_LIMIT when the dictionary does not fit the budget
     */
    public static KeyDictionary build(ScenarioSpec spec, int virtualSheets, KeyEncodingPolicy policy, LabelWindow.Mode mode,
                                      LabelWindow.Scope scope, MemoryBudget budget) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(budget, "budget");
        if (virtualSheets < 0) {
            throw new IllegalArgumentException("virtualSheets must be >= 0: " + virtualSheets);
        }
        SourceLocation where = SourceLocation.of(spec.source());
        long sheets = spec.sheetCount();
        long valueCount = spec.valueCount();
        if (sheets + virtualSheets + valueCount >= Integer.MAX_VALUE) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.KEY_CENSUS_OVERFLOW, "the scenario has "
                    + valueCount + " values and " + (sheets + virtualSheets) + " sheets, which is more labels than any "
                    + "key encoding can hold", where, "values", Long.toString(valueCount)));
        }
        LabelWindow window = LabelWindow.place(mode, scope, (int) sheets, virtualSheets, valueCount, where);
        int firstSheetLabel = 1;
        if (mode == LabelWindow.Mode.PRESERVE) {
            long sheetCount = sheets + virtualSheets;
            KeyEncoding target = policy == KeyEncodingPolicy.PACKED_LEGACY16
                    || (policy == KeyEncodingPolicy.AUTO_PRESERVE
                        && Math.max(valueCount, sheetCount) > KeyEncoding.SIGNED_BYTE.capacity())
                    ? KeyEncoding.PACKED_LEGACY16 : KeyEncoding.SIGNED_BYTE;
            int first = LabelWindow.fittingFirstLabel(target, window.firstLabel(), valueCount, where);
            window = new LabelWindow(mode, scope, first, first != window.firstLabel());
            firstSheetLabel = LabelWindow.fittingFirstLabel(target, 1, sheetCount, where);
        }
        KeyCensus census = new KeyCensus();
        census.addRange(firstSheetLabel, sheets + virtualSheets);
        census.addRange(window.firstLabel(), valueCount);
        KeyEncoding encoding = census.select(policy, where);

        MemoryBudget.Lease lease = budget.reserve(retainedBytes(spec), purpose(spec));
        try {
            ValueCell[] values = new ValueCell[(int) valueCount];
            int[] sheetStart = new int[spec.sheetCount() + 1];
            int next = 0;
            for (SheetSpec sheet : spec.sheets()) {
                sheetStart[sheet.key() - 1] = next;
                for (ValueCell cell : sheet.cells()) {
                    values[next++] = cell;
                }
            }
            sheetStart[spec.sheetCount()] = next;
            return new KeyDictionary(spec, virtualSheets, encoding, census.snapshot(), window, firstSheetLabel, values, sheetStart, lease);
        } catch (RuntimeException | Error e) {
            lease.close();
            throw e;
        }
    }

    /**
     * What a dictionary built from {@code spec} reserves: an upper bound of the dictionary's own objects and
     * arrays plus everything reachable from the spec it keeps.
     */
    public static long retainedBytes(ScenarioSpec spec) {
        long own = Footprint.object(6, Footprint.INT * 3 + 1)                    // this object
                + Footprint.referenceArray(spec.valueCount())                     // values
                + Footprint.array(Footprint.INT * (spec.sheetCount() + 1L))       // sheetStart
                + Footprint.object(0, Footprint.LONG + 2 * Footprint.INT)         // census snapshot
                + Footprint.object(3, Footprint.LONG) + Footprint.object(0, Footprint.INT) // lease and its flag
                + Footprint.string(purpose(spec));                                // the lease's purpose text
        return own + Footprint.of(spec);
    }

    private static String purpose(ScenarioSpec spec) {
        return "the key dictionary of " + spec.source() + " and the scenario it keeps";
    }

    /** The one encoding every row of this plan uses. */
    public KeyEncoding encoding() {
        return encoding;
    }

    /** What the census saw: the extremes and count of every sheet key and value label; an immutable value. */
    public KeyCensus.Snapshot census() {
        return census;
    }

    /** The scenario this dictionary owns; its reservation covers it, so later stages keep it without charging it again. */
    public ScenarioSpec spec() {
        ensureOpen();
        return spec;
    }

    public int sheetCount() {
        return spec.sheetCount();
    }

    public int virtualSheetCount() {
        return virtualSheets;
    }

    public int valueCount() {
        return values.length;
    }

    /** How the value labels were placed: the mode, the gate's scope, the first label and whether the window moved. */
    public LabelWindow labelWindow() {
        return window;
    }

    /**
     * The identity of the numbering every row of this dictionary uses: the scenario's content, the encoding and the
     * label window. Two dictionaries with equal identities give every value the same label in the same octets.
     */
    public String identity() {
        ensureOpen();
        return spec.contentDigest() + ";encoding=" + encoding.name() + ";" + window.canonical()
                + (firstSheetLabel == 1 ? "" : ";firstSheet=" + firstSheetLabel);
    }

    /** Encoded sheet key; plan/spec indices stay 1..S+V and never narrow to a byte. */
    public int sheetLabel(int ordinal) {
        ensureOpen();
        if (ordinal < 1 || ordinal > sheetCount() + virtualSheets) {
            throw new IllegalArgumentException("no sheet has ordinal " + ordinal);
        }
        return firstSheetLabel + ordinal - 1;
    }

    /** The label of the first value: S+1 unless a signed window moved it. */
    public int firstLabel() {
        return firstLabel;
    }

    /** The largest label in use, or S when there are no values. */
    public int lastLabel() {
        return firstLabel - 1 + values.length;
    }

    public boolean contains(int label) {
        return label >= firstLabel && label <= lastLabel();
    }

    /**
     * @throws FrameworkException KEY_LABEL_OUT_OF_RANGE when no value has this label; a label produced by a
     *                            replacement that names no value is reported here, never silently accepted (B10)
     */
    public ValueCell value(int label) {
        ensureOpen();
        if (!contains(label)) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.KEY_LABEL_OUT_OF_RANGE, "no value has the label "
                    + label + (values.length == 0 ? " (the scenario has no values)" : " (values are labelled "
                    + firstLabel + ".." + lastLabel() + ")"), SourceLocation.of(spec.source()), "label", Integer.toString(label)));
        }
        return values[label - firstLabel];
    }

    /** The key (1..S) of the data sheet that holds this value. */
    public int sheetOfLabel(int label) {
        value(label);
        int index = label - firstLabel;
        int low = 0;
        int high = sheetStart.length - 2;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (sheetStart[middle] <= index) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low + 1;
    }

    /** The labels of a data sheet's values in sheet order: the legacy "sheet key to cell key list". */
    public int[] labelsOf(int sheetKey) {
        ensureOpen();
        if (sheetKey < 1 || sheetKey > spec.sheetCount()) {
            throw new IllegalArgumentException("no data sheet has the key " + sheetKey + " (1.." + spec.sheetCount() + ")");
        }
        int from = sheetStart[sheetKey - 1];
        int to = sheetStart[sheetKey];
        int[] labels = new int[to - from];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = firstLabel + from + i;
        }
        return labels;
    }

    /** The same labels as one encoded row of this dictionary's encoding. */
    public KeyRow rowOf(int sheetKey) {
        return KeyRow.of(encoding, labelsOf(sheetKey));
    }

    /** What the dictionary reserved in the memory budget (a conservative estimate, not a measurement). */
    public long reservedBytes() {
        return lease.bytes();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("the key dictionary was closed with its run");
        }
    }

    /** Returns the reservation to the budget; the dictionary must not be used afterwards. */
    @Override
    public void close() {
        closed = true;
        lease.close();
    }
}
