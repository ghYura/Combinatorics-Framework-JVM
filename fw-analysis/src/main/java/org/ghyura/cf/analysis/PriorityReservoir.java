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

package org.ghyura.cf.analysis;

import org.ghyura.cf.model.Canonical;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * A bounded weighted sample (Efraimidis-Spirakis A-Res) with deterministic keys: each item's key is u^(1/w), where u is
 * drawn from SHA-256 of the seed, the metric and the item's identity, and the reservoir keeps the capacity largest keys.
 * Because an item's key never depends on the order or the partition it arrived in, merging reservoirs (keep the largest
 * keys of the union) gives exactly the sample one pass over all items would have kept, whatever the sizes of the parts;
 * this replaces the legacy uniform shuffle of the union, which over-represented smaller partitions (audit AN-06).
 */
public final class PriorityReservoir {

    /** One kept item. */
    public record Item(String id, double value, double weight, double key) {
    }

    private static final Comparator<Item> ORDER = Comparator.comparingDouble(Item::key).thenComparing(Item::id, Comparator.reverseOrder());

    private final long seed;
    private final String metric;
    private final int capacity;
    private final PriorityQueue<Item> kept;
    private long seen;
    private double totalWeight;

    public PriorityReservoir(long seed, String metric, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("a reservoir holds at least one item: " + capacity);
        }
        this.seed = seed;
        this.metric = Objects.requireNonNull(metric, "metric");
        this.capacity = capacity;
        this.kept = new PriorityQueue<>(capacity + 1, ORDER);
    }

    /** Offers an item with a positive finite weight. */
    public void offer(String id, double value, double weight) {
        if (!(weight > 0) || !Double.isFinite(weight)) {
            throw new IllegalArgumentException("a sample weight is finite and positive: " + weight);
        }
        seen++;
        totalWeight += weight;
        keep(new Item(id, value, weight, Math.pow(uniform(id), 1.0 / weight)));
    }

    /** Merges another reservoir of the same seed and metric: the largest keys of the union stay. */
    public void merge(PriorityReservoir other) {
        if (other.seed != seed || !other.metric.equals(metric)) {
            throw new IllegalArgumentException("reservoirs merge only with the same seed and metric");
        }
        seen += other.seen;
        totalWeight += other.totalWeight;
        for (Item item : other.kept) {
            keep(item);
        }
    }

    private void keep(Item item) {
        kept.add(item);
        if (kept.size() > capacity) {
            kept.poll();
        }
    }

    /** u in (0, 1) from SHA-256 of seed, metric and identity. */
    private double uniform(String id) {
        byte[] digest = Canonical.sha256(Canonical.utf8("reservoir/1\n" + seed + "\n" + Canonical.escape(metric) + "\n" + Canonical.escape(id)
                + "\n"));
        long bits = ByteBuffer.wrap(digest, 0, 8).getLong() >>> 11;
        return (bits + 0.5) / (1L << 53);
    }

    /** The kept items, by identity. */
    public List<Item> items() {
        List<Item> list = new ArrayList<>(kept);
        list.sort(Comparator.comparing(Item::id));
        return list;
    }

    /** Items offered to this reservoir and the ones merged into it. */
    public long seen() {
        return seen;
    }

    /** Whether the sample is every item (no approximation yet). */
    public boolean exact() {
        return seen <= capacity;
    }

    public double totalWeight() {
        return totalWeight;
    }
}
