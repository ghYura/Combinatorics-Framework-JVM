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

import org.ghyura.cf.model.row.RankCursor;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.RowBlocks;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/** Computed views over tables: concatenation (appending to a legacy table) and dropping the parent column. */
final class RowSources {

    private RowSources() {
    }

    /** Dependencies borrowed by a computed view; ownership stays with the generation arena. */
    static List<RowSource> inputs(RowSource source) {
        return switch (source) {
            case ExpandedRows rows -> List.of(rows.source());
            case JoinedRows rows -> rows.inputs();
            case IdOrder rows -> List.of(rows.source());
            case Concat rows -> rows.parts;
            case NoParents rows -> List.of(rows.source);
            case KnownDistinct rows -> List.of(rows.source());
            default -> List.of();
        };
    }

    /** A proof, not a scan; false also means that uniqueness is unknown. */
    static boolean distinct(RowSource source) {
        if (source.size().compareTo(BigInteger.ONE) <= 0) return true;
        while (true) {
            switch (source) {
                case KnownDistinct ignored -> { return true; }
                case RowBlocks rows -> { return rows.contentDistinct(); }
                case NoParents rows -> source = rows.source;
                case IdOrder rows -> source = rows.source();
                default -> { return false; }
            }
        }
    }

    static RowSource knownDistinct(RowSource source) {
        return distinct(source) ? source : new KnownDistinct(source);
    }

    /** Carries a proven property without changing row order, IDs, parents or storage ownership. */
    private record KnownDistinct(RowSource source) implements RowSource {
        @Override public BigInteger size() { return source.size(); }
        @Override public Row at(BigInteger rank) { return source.at(rank); }
        @Override public RowCursor open(BigInteger from, BigInteger to, Cancellation cancellation) {
            return source.open(from, to, cancellation);
        }
        @Override public int uniformLength() { return source.uniformLength(); }
        @Override public SortedMap<Integer, BigInteger> lengths() { return source.lengths(); }
        @Override public String formula() { return source.formula(); }
        @Override public boolean idsAscending() { return source.idsAscending(); }
        @Override public boolean stored() { return source.stored(); }
        @Override public long reservedBytes() { return source.reservedBytes(); }
    }

    /** {@code first} followed by {@code second}, each row keeping its id (the legacy INSERT ... SELECT appended rows). */
    static RowSource concat(RowSource first, RowSource second) {
        if (first.size().signum() == 0) {
            return second;
        }
        if (second.size().signum() == 0) {
            return first;
        }
        List<RowSource> parts = new ArrayList<>();
        for (RowSource s : List.of(first, second)) {
            if (s instanceof Concat c) {
                parts.addAll(c.parts);
            } else {
                parts.add(s);
            }
        }
        return new Concat(parts);
    }

    /** The rows without their parent ids (the legacy fw_ table has no parent column). */
    static RowSource withoutParents(RowSource source) {
        if (source.size().signum() == 0 || source instanceof NoParents) {
            return source;
        }
        return new NoParents(source);
    }

    /** Rows appended one table after another. */
    static final class Concat implements RowSource {
        private final List<RowSource> parts;
        private final BigInteger[] starts;
        private final BigInteger size;
        private final int uniformLength;
        private final boolean ascending;

        Concat(List<RowSource> parts) {
            this.parts = List.copyOf(parts);
            this.starts = new BigInteger[parts.size()];
            BigInteger total = BigInteger.ZERO;
            int length = -2;
            boolean up = true;
            BigInteger lastId = null;
            for (int i = 0; i < parts.size(); i++) {
                RowSource part = parts.get(i);
                starts[i] = total;
                total = total.add(part.size());
                if (part.size().signum() > 0) {
                    int l = part.uniformLength();
                    length = length == -2 ? l : length == l ? l : -1;
                    up &= part.idsAscending() && (lastId == null || part.at(BigInteger.ZERO).id().compareTo(lastId) > 0);
                    lastId = part.at(part.size().subtract(BigInteger.ONE)).id();
                }
            }
            this.size = total;
            this.uniformLength = length == -2 ? 0 : length;
            this.ascending = up;
        }

        @Override
        public BigInteger size() {
            return size;
        }

        @Override
        public Row at(BigInteger rank) {
            RowSource.checkRank(rank, size);
            int i = partOf(rank);
            return parts.get(i).at(rank.subtract(starts[i]));
        }

        private int partOf(BigInteger rank) {
            int low = 0;
            int high = parts.size() - 1;
            while (low < high) {
                int mid = (low + high + 1) >>> 1;
                if (starts[mid].compareTo(rank) <= 0) {
                    low = mid;
                } else {
                    high = mid - 1;
                }
            }
            while (parts.get(low).size().signum() == 0) {
                low++;
            }
            return low;
        }

        @Override
        public RowCursor open(BigInteger from, BigInteger to, Cancellation cancellation) {
            RankCursor.checkRange(from, to, size);
            Objects.requireNonNull(cancellation, "cancellation");
            return new RowCursor() {
                private BigInteger rank = from;
                private int part = from.compareTo(size) < 0 ? partOf(from) : parts.size();
                private RowCursor current;

                @Override
                public boolean hasNext() {
                    return rank.compareTo(to) < 0;
                }

                @Override
                public Row next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException("the cursor is at the end of its range " + to);
                    }
                    while (current == null || !current.hasNext()) {
                        if (current != null) {
                            current.close();
                            part++;
                        }
                        RowSource p = parts.get(part);
                        BigInteger local = rank.subtract(starts[part]);
                        BigInteger end = to.subtract(starts[part]).min(p.size());
                        current = p.open(local, end, cancellation);
                    }
                    rank = rank.add(BigInteger.ONE);
                    return current.next();
                }

                @Override
                public BigInteger position() {
                    return rank;
                }

                @Override
                public void close() {
                    if (current != null) {
                        current.close();
                    }
                    rank = to;
                }
            };
        }

        @Override
        public int uniformLength() {
            return uniformLength;
        }

        @Override
        public SortedMap<Integer, BigInteger> lengths() {
            SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
            for (RowSource part : parts) {
                SortedMap<Integer, BigInteger> of = part.lengths();
                if (of == null) {
                    return null;
                }
                of.forEach((length, count) -> lengths.merge(length, count, BigInteger::add));
            }
            return lengths;
        }

        @Override
        public String formula() {
            return String.join(" + ", parts.stream().map(RowSource::formula).toList());
        }

        @Override
        public boolean idsAscending() {
            return ascending;
        }

        @Override
        public boolean stored() {
            return parts.stream().allMatch(RowSource::stored);
        }

        @Override
        public long reservedBytes() {
            return 0;
        }
    }

    /** A table seen without its parent column. */
    static final class NoParents implements RowSource {
        private final RowSource source;

        NoParents(RowSource source) {
            this.source = source;
        }

        @Override
        public BigInteger size() {
            return source.size();
        }

        @Override
        public Row at(BigInteger rank) {
            return source.at(rank).withoutParent();
        }

        @Override
        public RowCursor open(BigInteger from, BigInteger to, Cancellation cancellation) {
            RowCursor inner = source.open(from, to, cancellation);
            return new RowCursor() {
                @Override
                public boolean hasNext() {
                    return inner.hasNext();
                }

                @Override
                public Row next() {
                    return inner.next().withoutParent();
                }

                @Override
                public BigInteger position() {
                    return inner.position();
                }

                @Override
                public void close() {
                    inner.close();
                }
            };
        }

        @Override
        public int uniformLength() {
            return source.uniformLength();
        }

        @Override
        public SortedMap<Integer, BigInteger> lengths() {
            return source.lengths();
        }

        @Override
        public String formula() {
            return source.formula();
        }

        @Override
        public boolean idsAscending() {
            return source.idsAscending();
        }

        @Override
        public boolean stored() {
            return source.stored();
        }

        @Override
        public long reservedBytes() {
            return 0;
        }
    }
}
