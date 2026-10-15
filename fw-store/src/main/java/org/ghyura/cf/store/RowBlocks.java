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
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.row.RankCursor;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.spi.Cancellation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A stored table: rows kept in chunked byte pages, each page reserved from the run's memory budget before it is
 * allocated (the legacy fw_ and fw2_ tables, without a database). A page holds the rows' encoded keys back to back
 * and, per row, its id, its parent id and the end of its keys. Pages grow from {@value #FIRST_PAGE_ROWS} rows and
 * {@value #FIRST_PAGE_BYTES} octets of keys to {@value #MAX_PAGE_ROWS} rows and {@value #MAX_PAGE_BYTES} octets, so
 * a small table costs little and a large one is never copied. A page's reservation covers all of it, used or not,
 * with its object and array headers, so the reserved bytes bound what the table keeps on the heap.
 *
 * <p>An optional content index lets {@link #appendIfAbsent} keep only the first row of every content (legacy
 * distinctify: row content only, ids and parents ignored); it is reserved the same way and released by
 * {@link #dropContentIndex} or {@link #close}. A row that does not fit the budget is refused with RESOURCE_LIMIT
 * and the table keeps what it had. Filling is for one thread; a filled table may be read from several.</p>
 */
public final class RowBlocks implements RowSource, AutoCloseable {

    static final int FIRST_PAGE_ROWS = 64;
    static final int MAX_PAGE_ROWS = 1024;
    static final int FIRST_PAGE_BYTES = 4 * 1024;
    static final int MAX_PAGE_BYTES = 64 * 1024;
    /** The page object, the headers of its four arrays and its slot in the page list: an upper bound. */
    static final long PAGE_OVERHEAD = 256;
    /** The index array's header and the scratch buffer's header: an upper bound. */
    static final long INDEX_OVERHEAD = 64;

    private final String purpose;
    private final KeyEncoding encoding;
    private final MemoryBudget budget;
    private final List<Page> pages = new ArrayList<>();
    private final List<MemoryBudget.Lease> leases = new ArrayList<>();
    private long count;
    private long reserved;
    private int uniformLength = -2;
    // rows by number of keys, counted as they are appended
    private final SortedMap<Integer, long[]> lengthCounts = new TreeMap<>();
    private long lastId;
    private boolean ascending = true;
    private boolean closed;

    // the content index: open addressing over row indexes plus one, 0 marking a free slot
    private long[] slots;
    private MemoryBudget.Lease slotLease;
    private byte[] scratch = new byte[0];

    public RowBlocks(String purpose, KeyEncoding encoding, MemoryBudget budget) {
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.encoding = Objects.requireNonNull(encoding, "encoding");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    /** What the table's reservations are for, as the budget names them. */
    public String purpose() {
        return purpose;
    }

    /**
     * Appends a row. Ids and parents are kept as 64-bit integers, as the legacy bigint columns were.
     *
     * @throws FrameworkException RESOURCE_LIMIT when the page the row needs does not fit the budget, or when an id
     *                            is beyond 2^63 - 1
     */
    public void append(Row row) {
        append(row.id(), row.parent(), row.keys());
    }

    public void append(BigInteger id, BigInteger parent, KeyRow keys) {
        requireOpen();
        Objects.requireNonNull(keys, "keys");
        if (keys.encoding() != encoding) {
            throw new IllegalArgumentException("a " + keys.encoding() + " row in a " + encoding + " table");
        }
        long idValue = stored(id, "id");
        long parentValue = stored(parent, "parent id");
        if (idValue <= 0 || parentValue < 0) {
            throw new IllegalArgumentException("ids start at 1 and a parent id is 0 or positive: " + id + ", " + parent);
        }
        int length = keys.payloadLength();
        Page page = pages.isEmpty() ? null : pages.getLast();
        if (page == null || page.rows == page.ids.length || page.used + length > page.data.length) {
            page = newPage(page, length);
        }
        keys.copyPayloadTo(page.data, page.used);
        page.used += length;
        page.ids[page.rows] = idValue;
        page.parents[page.rows] = parentValue;
        page.ends[page.rows] = page.used;
        page.rows++;
        count++;
        int keyCount = keys.size();
        uniformLength = uniformLength == -2 ? keyCount : uniformLength == keyCount ? keyCount : -1;
        lengthCounts.computeIfAbsent(keyCount, k -> new long[1])[0]++;
        ascending &= count == 1 || idValue > lastId;
        lastId = idValue;
    }

    private Page newPage(Page last, int length) {
        int rows = last == null ? FIRST_PAGE_ROWS : Math.min(MAX_PAGE_ROWS, last.ids.length * 2);
        int bytes = Math.max(last == null ? FIRST_PAGE_BYTES : Math.min(MAX_PAGE_BYTES, last.data.length * 2), length);
        MemoryBudget.Lease lease = budget.reserve(Page.bytes(rows, bytes), purpose);
        Page page;
        try {
            page = new Page(count, rows, bytes);
        } catch (RuntimeException | Error e) {
            lease.close();
            throw e;
        }
        pages.add(page);
        leases.add(lease);
        reserved += lease.bytes();
        return page;
    }

    private long stored(BigInteger value, String what) {
        Objects.requireNonNull(value, what);
        if (value.bitLength() > 63) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, "a stored table keeps ids up to 2^63 - 1, "
                    + "as the legacy bigint columns did; the " + what + " " + value + " of " + purpose + " is beyond it",
                    SourceLocation.NONE, "purpose", purpose, "value", value.toString()));
        }
        return value.longValue();
    }

    // ---- the content index -----------------------------------------------------------------------------------

    /** Starts indexing row content, including the rows already in the table, so {@link #appendIfAbsent} can be used. */
    public void indexContent() {
        requireOpen();
        if (slots == null) {
            rehash(capacityFor(Math.max(count, 16)));
        }
    }

    /**
     * Appends the row unless a row with the same keys is already in the table (legacy distinctify keeps the first
     * occurrence and compares content only).
     *
     * @return whether the row was appended
     */
    public boolean appendIfAbsent(BigInteger id, BigInteger parent, KeyRow keys) {
        requireOpen();
        if (slots == null) {
            throw new IllegalStateException("indexContent() first");
        }
        int length = keys.payloadLength();
        if (scratch.length < length) {
            scratch = new byte[Math.max(length, scratch.length * 2)];
        }
        keys.copyPayloadTo(scratch, 0);
        int hash = hash(scratch, 0, length);
        int mask = slots.length - 1;
        int slot = hash & mask;
        while (slots[slot] != 0) {
            if (sameContent(slots[slot] - 1, scratch, length)) {
                return false;
            }
            slot = (slot + 1) & mask;
        }
        if ((count + 1) * 2 > slots.length) {
            append(id, parent, keys);
            rehash(capacityFor(count));
        } else {
            append(id, parent, keys);
            slots[slot] = count;
        }
        return true;
    }

    /** Stops indexing content and returns the index's reservation. */
    public void dropContentIndex() {
        slots = null;
        scratch = new byte[0];
        if (slotLease != null) {
            reserved -= slotLease.bytes();
            slotLease.close();
            slotLease = null;
        }
    }

    private static int capacityFor(long rows) {
        long capacity = Long.highestOneBit(Math.max(16, rows * 2 - 1)) << 1;
        if (capacity > 1 << 30) {
            throw new IllegalStateException("a content index of " + rows + " rows is beyond a single array");
        }
        return (int) capacity;
    }

    private void rehash(int capacity) {
        MemoryBudget.Lease lease = budget.reserve(INDEX_OVERHEAD + 8L * capacity, purpose + " (content index)");
        long[] fresh;
        try {
            fresh = new long[capacity];
        } catch (RuntimeException | Error e) {
            lease.close();
            throw e;
        }
        int mask = capacity - 1;
        for (Page page : pages) {
            for (int i = 0; i < page.rows; i++) {
                int start = i == 0 ? 0 : page.ends[i - 1];
                int slot = hash(page.data, start, page.ends[i]) & mask;
                while (fresh[slot] != 0) {
                    slot = (slot + 1) & mask;
                }
                fresh[slot] = page.first + i + 1;
            }
        }
        if (slotLease != null) {
            reserved -= slotLease.bytes();
            slotLease.close();
        }
        slots = fresh;
        slotLease = lease;
        reserved += lease.bytes();
    }

    private boolean sameContent(long index, byte[] keys, int length) {
        Page page = pageOf(index);
        int i = (int) (index - page.first);
        int start = i == 0 ? 0 : page.ends[i - 1];
        return page.ends[i] - start == length && java.util.Arrays.equals(page.data, start, page.ends[i], keys, 0, length);
    }

    private static int hash(byte[] bytes, int from, int to) {
        int h = 1;
        for (int i = from; i < to; i++) {
            h = 31 * h + bytes[i];
        }
        return h ^ (h >>> 16);
    }

    // ---- reading ---------------------------------------------------------------------------------------------

    /** How many rows the table holds. */
    public long count() {
        return count;
    }

    @Override
    public BigInteger size() {
        return BigInteger.valueOf(count);
    }

    @Override
    public Row at(BigInteger rank) {
        RowSource.checkRank(rank, size());
        return row(rank.longValue());
    }

    /** The row at an index below {@link #count()}. */
    public Row row(long index) {
        Objects.checkIndex(index, count);
        Page page = pageOf(index);
        return page.row((int) (index - page.first), encoding);
    }

    /** The number of keys of the row at {@code index}, without building the row. */
    public int keyCount(long index) {
        Objects.checkIndex(index, count);
        Page page = pageOf(index);
        int i = (int) (index - page.first);
        return ((page.ends[i] - (i == 0 ? 0 : page.ends[i - 1])) / encoding.bytesPerKey());
    }

    /** The id of the row at {@code index}, without building the row. */
    public long id(long index) {
        Objects.checkIndex(index, count);
        Page page = pageOf(index);
        return page.ids[(int) (index - page.first)];
    }

    private Page pageOf(long index) {
        int low = 0;
        int high = pages.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (pages.get(mid).first <= index) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return pages.get(low);
    }

    @Override
    public RowCursor open(BigInteger from, BigInteger to, Cancellation cancellation) {
        RankCursor.checkRange(from, to, size());
        Objects.requireNonNull(cancellation, "cancellation");
        return new PageCursor(from.longValue(), to.longValue(), cancellation);
    }

    @Override
    public int uniformLength() {
        return uniformLength == -2 ? 0 : uniformLength;
    }

    /** Counted while the rows were appended. */
    @Override
    public SortedMap<Integer, BigInteger> lengths() {
        SortedMap<Integer, BigInteger> lengths = new TreeMap<>();
        lengthCounts.forEach((length, rows) -> lengths.put(length, BigInteger.valueOf(rows[0])));
        return lengths;
    }

    @Override
    public String formula() {
        return count + " stored rows";
    }

    @Override
    public boolean idsAscending() {
        return ascending;
    }

    @Override
    public boolean stored() {
        return true;
    }

    /** The bytes reserved for the pages and the content index. */
    @Override
    public long reservedBytes() {
        return reserved;
    }

    /** Drops every row and returns every reservation; the table cannot be used afterwards. Closing twice is harmless. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        dropContentIndex();
        pages.clear();
        for (MemoryBudget.Lease lease : leases) {
            lease.close();
        }
        leases.clear();
        reserved = 0;
        count = 0;
        lengthCounts.clear();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(purpose + " is closed");
        }
    }

    private static final class Page {
        final long first;
        final long[] ids;
        final long[] parents;
        final int[] ends;
        final byte[] data;
        int rows;
        int used;

        Page(long first, int rows, int bytes) {
            this.first = first;
            this.ids = new long[rows];
            this.parents = new long[rows];
            this.ends = new int[rows];
            this.data = new byte[bytes];
        }

        static long bytes(int rows, int bytes) {
            return PAGE_OVERHEAD + 20L * rows + bytes;
        }

        Row row(int i, KeyEncoding encoding) {
            int start = i == 0 ? 0 : ends[i - 1];
            KeyRow keys = KeyRow.fromPayload(encoding, data, start, ends[i] - start, SourceLocation.NONE);
            return new Row(BigInteger.valueOf(ids[i]), BigInteger.valueOf(parents[i]), keys);
        }
    }

    /** Walks the pages directly. */
    private final class PageCursor implements RowCursor {
        private final long to;
        private final Cancellation cancellation;
        private long index;
        private int sincePoll = RankCursor.POLL_INTERVAL;
        private boolean done;

        PageCursor(long from, long to, Cancellation cancellation) {
            this.index = from;
            this.to = to;
            this.cancellation = cancellation;
        }

        @Override
        public boolean hasNext() {
            return !done && index < to;
        }

        @Override
        public Row next() {
            if (!hasNext()) {
                throw new NoSuchElementException("the cursor is at the end of its range " + to);
            }
            if (++sincePoll >= RankCursor.POLL_INTERVAL) {
                sincePoll = 0;
                cancellation.throwIfCancelled();
            }
            return row(index++);
        }

        @Override
        public BigInteger position() {
            return BigInteger.valueOf(index);
        }

        @Override
        public void close() {
            done = true;
        }
    }
}
