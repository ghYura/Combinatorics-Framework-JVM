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

import org.ghyura.cf.algebra.TupleCursor;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.row.RankCursor;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * An ungrouped later pass as a computed table: every row of the source table, in id order, expanded by a
 * {@link RowExpansion}; each result keeps the source row's id as its parent, and ids continue the sheet's
 * second-table counter. When every source row has the same length and the count depends on the length only, a
 * rank finds its source row by division and the table holds nothing; otherwise one pass over the source builds
 * the running totals of the counts (budgeted, {@link PrefixCounts}) and a rank finds its row by binary search.
 */
final class ExpandedRows implements RowSource, AutoCloseable {

    private final RowSource source;
    private final RowExpansion expansion;
    private final KeyEncoding encoding;
    private final BigInteger idBase;
    private final BigInteger perRow;
    private final PrefixCounts totals;
    private final BigInteger size;
    private final int uniformLength;
    private final SortedMap<Integer, BigInteger> lengths;
    private final String formula;

    private ExpandedRows(RowSource source, RowExpansion expansion, KeyEncoding encoding, BigInteger idBase, BigInteger perRow,
                         PrefixCounts totals, BigInteger size, int uniformLength, SortedMap<Integer, BigInteger> lengths, String formula) {
        this.source = source;
        this.expansion = expansion;
        this.encoding = encoding;
        this.idBase = idBase;
        this.perRow = perRow;
        this.totals = totals;
        this.size = size;
        this.uniformLength = uniformLength;
        this.lengths = lengths;
        this.formula = formula;
    }

    /**
     * @param source the previous table in id order
     * @throws org.ghyura.cf.model.FrameworkException RESOURCE_LIMIT when the running totals do not fit the budget,
     *                                                RUN_CANCELLED when cancelled while they are built
     */
    static ExpandedRows of(RowSource source, RowExpansion expansion, KeyEncoding encoding, BigInteger idBase,
                           MemoryBudget budget, String purpose, Cancellation cancellation) {
        Objects.requireNonNull(source, "source");
        int length = source.uniformLength();
        BigInteger perRow = length >= 0 ? expansion.countForLength(length) : null;
        if (source.size().signum() == 0 || perRow != null) {
            BigInteger count = perRow == null ? BigInteger.ZERO : perRow;
            int keys = length >= 0 ? expansion.outputLength(length) : -1;
            boolean rows = perRow != null && source.size().signum() > 0;
            String formula = rows ? grouped(source.formula()) + " × " + expansion.formulaForLength(length) : "0";
            return new ExpandedRows(source, expansion, encoding, idBase, count, null, source.size().multiply(count), keys, null, formula);
        }
        PrefixCounts totals = new PrefixCounts(purpose, budget);
        int keys = -2;
        // results by number of keys; source rows by length where the results of one row differ in length
        SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
        Map<Integer, long[]> mixed = new TreeMap<>();
        try (RowCursor rows = source.open(cancellation)) {
            while (rows.hasNext()) {
                int[] in = rows.next().keys().labels();
                BigInteger count = expansion.plan(in).count();
                totals.add(count);
                if (count.signum() > 0) {
                    int l = expansion.outputLength(in.length);
                    keys = keys == -2 ? l : keys == l ? l : -1;
                    if (l >= 0) {
                        lengths.merge(l, count, BigInteger::add);
                    } else {
                        mixed.computeIfAbsent(in.length, k -> new long[1])[0]++;
                    }
                }
            }
        } catch (RuntimeException | Error e) {
            totals.close();
            throw e;
        }
        // a row whose results differ in length is never a content-dependent permutation, so its length decides them
        boolean knownLengths = true;
        try {
            mixed.forEach((rowLength, rows) -> expansion.lengthsForLength(rowLength)
                    .forEach((l, c) -> lengths.merge(l, c.multiply(BigInteger.valueOf(rows[0])), BigInteger::add)));
        } catch (FrameworkException e) {
            if (e.diagnostics().stream().anyMatch(d -> d.code() != ErrorCode.RESOURCE_LIMIT)) {
                totals.close();
                throw e;
            }
            knownLengths = false;
        } catch (RuntimeException | Error e) {
            totals.close();
            throw e;
        }
        return new ExpandedRows(source, expansion, encoding, idBase, null, totals, totals.total(), keys == -2 ? 0 : keys,
                knownLengths ? lengths : null,
                "Σ " + expansion.describe() + " over the " + source.size() + " rows of " + grouped(source.formula()));
    }

    /** A formula as a factor: parenthesized when it is a sum. */
    static String grouped(String formula) {
        return formula.contains(" + ") || formula.contains(" − ") || formula.startsWith("Σ") ? "(" + formula + ")" : formula;
    }

    @Override
    public BigInteger size() {
        return size;
    }

    /** Computed from the operator's parameters, or counted by length while the running totals were built. */
    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        if (size.signum() == 0) return new TreeMap<>();
        if (lengths != null) return new TreeMap<>(lengths);
        if (perRow == null) return null;
        SortedMap<Integer, BigInteger> result = expansion.lengthsForLength(source.uniformLength());
        result.replaceAll((length, count) -> count.multiply(source.size()));
        return result;
    }

    @Override
    public String formula() {
        return formula;
    }

    @Override
    public Row at(BigInteger rank) {
        RowSource.checkRank(rank, size);
        BigInteger sourceRank;
        BigInteger within;
        if (perRow != null) {
            BigInteger[] qr = rank.divideAndRemainder(perRow);
            sourceRank = qr[0];
            within = qr[1];
        } else {
            long row = totals.rowOf(rank);
            sourceRank = BigInteger.valueOf(row);
            within = rank.subtract(totals.before(row));
        }
        Row from = source.at(sourceRank);
        int[] in = from.keys().labels();
        RowExpansion.Plan plan = expansion.plan(in);
        int[] tuple = plan.tuples().get(within.add(plan.skip()));
        return new Row(idBase.add(rank).add(BigInteger.ONE), from.id(), KeyRow.of(encoding, expansion.labels(in, tuple)));
    }

    /** Walks the source rows once, each row's results through the operator's own cursor. */
    @Override
    public RowCursor open(BigInteger from, BigInteger to, Cancellation cancellation) {
        RankCursor.checkRange(from, to, size);
        Objects.requireNonNull(cancellation, "cancellation");
        return new RowCursor() {
            private BigInteger rank = from;
            private BigInteger nextSource = startRow(from);
            private BigInteger firstWithin = from.signum() == 0 || from.compareTo(size) >= 0 ? BigInteger.ZERO : withinOf(from);
            private Row current;
            private int[] in;
            private RowExpansion.Plan plan;
            private TupleCursor tuples;

            private BigInteger startRow(BigInteger r) {
                if (r.compareTo(size) >= 0) {
                    return source.size();
                }
                return perRow != null ? r.divide(perRow) : BigInteger.valueOf(totals.rowOf(r));
            }

            private BigInteger withinOf(BigInteger r) {
                if (perRow != null) {
                    return r.mod(perRow);
                }
                long row = totals.rowOf(r);
                return r.subtract(totals.before(row));
            }

            @Override
            public boolean hasNext() {
                return rank.compareTo(to) < 0;
            }

            @Override
            public Row next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("the cursor is at the end of its range " + to);
                }
                while (tuples == null || !tuples.hasNext()) {
                    if (tuples != null) {
                        tuples.close();
                    }
                    current = source.at(nextSource);
                    nextSource = nextSource.add(BigInteger.ONE);
                    in = current.keys().labels();
                    plan = expansion.plan(in);
                    BigInteger start = plan.skip().add(firstWithin);
                    firstWithin = BigInteger.ZERO;
                    tuples = plan.tuples().cursor(start.min(plan.tuples().size()), plan.tuples().size(), cancellation);
                }
                Row row = new Row(idBase.add(rank).add(BigInteger.ONE), current.id(), KeyRow.of(encoding, expansion.labels(in, tuples.next())));
                rank = rank.add(BigInteger.ONE);
                return row;
            }

            @Override
            public BigInteger position() {
                return rank;
            }

            @Override
            public void close() {
                if (tuples != null) {
                    tuples.close();
                }
                rank = to;
            }
        };
    }

    @Override
    public int uniformLength() {
        return size.signum() == 0 ? 0 : uniformLength;
    }

    @Override
    public boolean idsAscending() {
        return true;
    }

    @Override
    public boolean stored() {
        return false;
    }

    @Override
    public long reservedBytes() {
        return totals == null ? 0 : totals.reservedBytes();
    }

    /** Whether a rank finds its source row by division (true) or through the budgeted running totals. */
    boolean uniform() {
        return perRow != null;
    }

    @Override
    public void close() {
        if (totals != null) {
            totals.close();
        }
    }
}
