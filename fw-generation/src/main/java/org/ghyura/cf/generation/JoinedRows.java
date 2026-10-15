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

import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.LongColumn;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A joiner's rows as a computed table (BraceOperationHandler at the pinned commit). For every row of the first
 * operand and every row of the second (for M:1 the second operand is the outer loop), the multiplicity builds one
 * row: 1:1 alternates the elements of two rows of equal length, 1:N puts the relation and all of the second row
 * after each element of the first, M:M (rows of equal length) and M:N put the relation between the two rows, M:1
 * puts all of the first row and the relation before each element of the second; the separator goes between the
 * groups of 1:1, 1:N and M:1; the start and end values wrap every row; an empty result is no row. Ids continue the
 * sheet's first-table counter; a joined row has no parent.
 *
 * <p>When both operands have rows of one length each, a rank finds its pair by division and nothing is held;
 * otherwise one pass over each operand builds budgeted indexes (inner rows by length, running totals of the outer
 * rows' counts).</p>
 */
final class JoinedRows implements RowSource, AutoCloseable {

    private final Directive.Multiplicity multiplicity;
    private final RowSource outer;
    private final RowSource inner;
    private final Integer relation;
    private final Integer separator;
    private final Integer start;
    private final Integer end;
    private final KeyEncoding encoding;
    private final BigInteger idBase;
    private final boolean filtered;
    // the inner rows an outer row can pair with: every inner row, those of one length, or those that are not empty
    private final Map<Integer, LongColumn> innerByLength;
    private final LongColumn innerNonEmpty;
    private final BigInteger perOuter;
    private final PrefixCounts totals;
    private final BigInteger size;
    private final int uniformLength;

    private JoinedRows(Directive.Multiplicity multiplicity, RowSource outer, RowSource inner, Integer relation, Integer separator,
                       Integer start, Integer end, KeyEncoding encoding, BigInteger idBase, Map<Integer, LongColumn> innerByLength,
                       LongColumn innerNonEmpty, BigInteger perOuter, PrefixCounts totals, BigInteger size, int uniformLength) {
        this.multiplicity = multiplicity;
        this.outer = outer;
        this.inner = inner;
        this.relation = relation;
        this.separator = separator;
        this.start = start;
        this.end = end;
        this.encoding = encoding;
        this.idBase = idBase;
        this.filtered = multiplicity == Directive.Multiplicity.ONE_TO_ONE || multiplicity == Directive.Multiplicity.M_TO_M;
        this.innerByLength = innerByLength;
        this.innerNonEmpty = innerNonEmpty;
        this.perOuter = perOuter;
        this.totals = totals;
        this.size = size;
        this.uniformLength = uniformLength;
    }

    /**
     * @param first  the rows of the first operand
     * @param second the rows of the second operand
     * @throws org.ghyura.cf.model.FrameworkException RESOURCE_LIMIT when an index does not fit the budget,
     *                                                RUN_CANCELLED when cancelled while one is built
     */
    static JoinedRows of(Directive.Multiplicity multiplicity, RowSource first, RowSource second, Integer relation, Integer separator,
                         Integer start, Integer end, KeyEncoding encoding, BigInteger idBase, MemoryBudget budget, String purpose,
                         Cancellation cancellation) {
        boolean manyToOne = multiplicity == Directive.Multiplicity.M_TO_ONE;
        RowSource outer = manyToOne ? second : first;
        RowSource inner = manyToOne ? first : second;
        JoinedRows shape = new JoinedRows(multiplicity, outer, inner, relation, separator, start, end, encoding, idBase, null, null,
                BigInteger.ZERO, null, BigInteger.ZERO, 0);
        List<AutoCloseable> built = new ArrayList<>();
        try {
            Map<Integer, LongColumn> byLength = null;
            LongColumn nonEmpty = null;
            int innerLength = inner.uniformLength();
            if (inner.size().signum() > 0 && innerLength < 0) {
                byLength = new TreeMap<>();
                nonEmpty = new LongColumn(purpose + " (inner rows that are not empty)", budget);
                built.add(nonEmpty);
                long rank = 0;
                try (RowCursor rows = inner.open(cancellation)) {
                    while (rows.hasNext()) {
                        int length = rows.next().keys().size();
                        LongColumn column = byLength.get(length);
                        if (column == null) {
                            column = new LongColumn(purpose + " (inner rows of " + length + " keys)", budget);
                            built.add(column);
                            byLength.put(length, column);
                        }
                        column.add(rank);
                        if (length > 0) {
                            nonEmpty.add(rank);
                        }
                        rank++;
                    }
                }
            }
            JoinedRows indexed = new JoinedRows(multiplicity, outer, inner, relation, separator, start, end, encoding, idBase, byLength,
                    nonEmpty, BigInteger.ZERO, null, BigInteger.ZERO, 0);
            int outerLength = outer.uniformLength();
            if (outer.size().signum() == 0 || inner.size().signum() == 0) {
                return new JoinedRows(multiplicity, outer, inner, relation, separator, start, end, encoding, idBase, byLength, nonEmpty,
                        BigInteger.ZERO, null, BigInteger.ZERO, 0);
            }
            if (outerLength >= 0) {
                BigInteger per = indexed.countFor(outerLength);
                int keys = innerLength >= 0 && per.signum() > 0 ? shape.joinedLength(outerLength, innerLength) : -1;
                return new JoinedRows(multiplicity, outer, inner, relation, separator, start, end, encoding, idBase, byLength, nonEmpty,
                        per, null, outer.size().multiply(per), keys);
            }
            PrefixCounts totals = new PrefixCounts(purpose + " (running totals)", budget);
            built.add(totals);
            try (RowCursor rows = outer.open(cancellation)) {
                while (rows.hasNext()) {
                    totals.add(indexed.countFor(rows.next().keys().size()));
                }
            }
            return new JoinedRows(multiplicity, outer, inner, relation, separator, start, end, encoding, idBase, byLength, nonEmpty,
                    null, totals, totals.total(), -1);
        } catch (RuntimeException | Error e) {
            for (AutoCloseable c : built) {
                try {
                    c.close();
                } catch (Exception suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
    }

    /** Whether rows of these lengths make a row: equal lengths where required, and not an empty result. */
    private boolean pairs(int outerLength, int innerLength) {
        if (filtered && outerLength != innerLength) {
            return false;
        }
        if (start != null || end != null) {
            return true;
        }
        int a = multiplicity == Directive.Multiplicity.M_TO_ONE ? innerLength : outerLength;
        int b = multiplicity == Directive.Multiplicity.M_TO_ONE ? outerLength : innerLength;
        return switch (multiplicity) {
            case ONE_TO_ONE -> a > 0 || b > 0;
            case ONE_TO_N -> a > 0;
            case M_TO_M, M_TO_N -> a > 0 || b > 0 || relation != null;
            case M_TO_ONE -> b > 0;
        };
    }

    /** How many inner rows an outer row of this length pairs with. */
    private BigInteger countFor(int outerLength) {
        int innerLength = inner.uniformLength();
        if (innerByLength == null) {
            return pairs(outerLength, innerLength) ? inner.size() : BigInteger.ZERO;
        }
        long count = 0;
        for (Map.Entry<Integer, LongColumn> e : innerByLength.entrySet()) {
            if (pairs(outerLength, e.getKey())) {
                count += e.getValue().size();
            }
        }
        return BigInteger.valueOf(count);
    }

    /** The rank of the {@code k}-th inner row an outer row of this length pairs with, in inner order. */
    private BigInteger innerRank(int outerLength, BigInteger k) {
        if (innerByLength == null) {
            return k;
        }
        if (filtered) {
            return BigInteger.valueOf(innerByLength.get(outerLength).get(k.longValueExact()));
        }
        // unfiltered: either every inner row pairs, or every one but the empty ones
        LongColumn zero = innerByLength.get(0);
        if (zero != null && !pairs(outerLength, 0)) {
            return BigInteger.valueOf(innerNonEmpty.get(k.longValueExact()));
        }
        return k;
    }

    @Override
    public BigInteger size() {
        return size;
    }

    /** From the operands' distributions: every pair of lengths that makes a row, at the length of the joined row. */
    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
        if (size.signum() == 0) {
            return lengths;
        }
        SortedMap<Integer, BigInteger> outerLengths = outer.lengths();
        SortedMap<Integer, BigInteger> innerLengths = inner.lengths();
        if (outerLengths == null || innerLengths == null) {
            return null;
        }
        outerLengths.forEach((a, outerRows) -> innerLengths.forEach((b, innerRows) -> {
            if (pairs(a, b)) {
                lengths.merge(joinedLength(a, b), outerRows.multiply(innerRows), BigInteger::add);
            }
        }));
        return lengths;
    }

    @Override
    public String formula() {
        if (size.signum() == 0) {
            return "0";
        }
        return multiplicity.token() + ": " + (perOuter != null ? outer.size() + " × " + perOuter
                : "Σ over " + outer.size() + " rows of the rows each pairs with");
    }

    @Override
    public Row at(BigInteger rank) {
        RowSource.checkRank(rank, size);
        BigInteger outerRank;
        BigInteger k;
        if (perOuter != null) {
            BigInteger[] qr = rank.divideAndRemainder(perOuter);
            outerRank = qr[0];
            k = qr[1];
        } else {
            long row = totals.rowOf(rank);
            outerRank = BigInteger.valueOf(row);
            k = rank.subtract(totals.before(row));
        }
        KeyRow o = outer.at(outerRank).keys();
        KeyRow i = inner.at(innerRank(o.size(), k)).keys();
        boolean manyToOne = multiplicity == Directive.Multiplicity.M_TO_ONE;
        int[] joined = join(manyToOne ? i.labels() : o.labels(), manyToOne ? o.labels() : i.labels());
        return new Row(idBase.add(rank).add(BigInteger.ONE), BigInteger.ZERO, KeyRow.of(encoding, joined));
    }

    /** The joined labels of a row {@code a} of the first operand and a row {@code b} of the second, wrapped. */
    int[] join(int[] a, int[] b) {
        List<Integer> body = switch (multiplicity) {
            case ONE_TO_ONE -> alternate(a, b);
            case ONE_TO_N -> oneToN(a, b);
            case M_TO_M, M_TO_N -> manyToMany(a, b);
            case M_TO_ONE -> manyToOne(a, b);
        };
        List<Integer> row = new ArrayList<>(body.size() + 2);
        if (start != null) {
            row.add(start);
        }
        row.addAll(body);
        if (end != null) {
            row.add(end);
        }
        return row.stream().mapToInt(Integer::intValue).toArray();
    }

    private int joinedLength(int outerLength, int innerLength) {
        boolean manyToOne = multiplicity == Directive.Multiplicity.M_TO_ONE;
        return join(new int[manyToOne ? innerLength : outerLength], new int[manyToOne ? outerLength : innerLength]).length;
    }

    /** alternateAndBuild: a and b interleaved; the relation after every even place, the separator after every odd one but the last. */
    private List<Integer> alternate(int[] a, int[] b) {
        List<Integer> alternated = new ArrayList<>();
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            if (i < a.length) {
                alternated.add(a[i]);
            }
            if (i < b.length) {
                alternated.add(b[i]);
            }
        }
        List<Integer> out = new ArrayList<>();
        for (int j = 0; j < alternated.size(); j++) {
            out.add(alternated.get(j));
            if (j % 2 == 0) {
                if (relation != null) {
                    out.add(relation);
                }
            } else if (j != alternated.size() - 1 && separator != null) {
                out.add(separator);
            }
        }
        return out;
    }

    /** buildOneToN: for each element of a, the element, the relation and all of b, separated; no trailing separator. */
    private List<Integer> oneToN(int[] a, int[] b) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < a.length; i++) {
            out.add(a[i]);
            if (relation != null) {
                out.add(relation);
            }
            for (int x : b) {
                out.add(x);
            }
            if (separator != null && i < a.length - 1) {
                out.add(separator);
            }
        }
        return out;
    }

    /** buildManyToMany: all of a, the relation, all of b. */
    private List<Integer> manyToMany(int[] a, int[] b) {
        List<Integer> out = new ArrayList<>();
        for (int x : a) {
            out.add(x);
        }
        if (relation != null) {
            out.add(relation);
        }
        for (int x : b) {
            out.add(x);
        }
        return out;
    }

    /** buildManyToOne: for each element of b, all of a, the relation and the element, separated; no trailing separator. */
    private List<Integer> manyToOne(int[] a, int[] b) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < b.length; i++) {
            for (int x : a) {
                out.add(x);
            }
            if (relation != null) {
                out.add(relation);
            }
            out.add(b[i]);
            if (separator != null && i < b.length - 1) {
                out.add(separator);
            }
        }
        return out;
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
        long bytes = totals == null ? 0 : totals.reservedBytes();
        if (innerByLength != null) {
            for (LongColumn c : innerByLength.values()) {
                bytes += c.reservedBytes();
            }
            bytes += innerNonEmpty.reservedBytes();
        }
        return bytes;
    }

    @Override
    public void close() {
        if (totals != null) {
            totals.close();
        }
        if (innerByLength != null) {
            innerByLength.values().forEach(LongColumn::close);
            innerNonEmpty.close();
        }
    }
}
