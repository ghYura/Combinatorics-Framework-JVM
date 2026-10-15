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

package org.ghyura.cf.model.key;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.util.Objects;

/**
 * Accumulates every label a plan will use (cells, sheets, virtual sheets) in O(1) memory and then picks the
 * one {@link KeyEncoding} the whole plan uses (B04). It records the extremes and a count, nothing else:
 * whether an encoding fits depends only on the smallest and largest label.
 *
 * <p>Instances are not thread-safe; a census belongs to the single parse that feeds it. Whoever keeps the
 * result after the parse keeps a {@link #snapshot()}, which cannot change.</p>
 *
 * <p>Every addition is validated with overflow-free arithmetic before any field changes, so a refused
 * addition leaves the census exactly as it was.</p>
 */
public final class KeyCensus {

    private long count;
    private int min = Integer.MAX_VALUE;
    private int max = Integer.MIN_VALUE;

    public KeyCensus() {
    }

    /** Continues a census from what an earlier one saw. */
    KeyCensus(Snapshot from) {
        this.count = from.count();
        this.min = from.min();
        this.max = from.max();
    }

    /** @throws ArithmeticException when the count would exceed {@code Long.MAX_VALUE}; nothing changes then */
    public KeyCensus add(int label) {
        if (count == Long.MAX_VALUE) {
            throw new ArithmeticException("the census already counts " + count + " labels and cannot count more");
        }
        count++;
        if (label < min) {
            min = label;
        }
        if (label > max) {
            max = label;
        }
        return this;
    }

    /**
     * Registers {@code labelCount} consecutive labels starting at {@code firstLabel} (the S+1..S+C numbering).
     *
     * @throws IllegalArgumentException when {@code labelCount} is negative or the range runs past
     *                                  {@code Integer.MAX_VALUE}; nothing changes then
     * @throws ArithmeticException      when the count would exceed {@code Long.MAX_VALUE}; nothing changes then
     */
    public KeyCensus addRange(int firstLabel, long labelCount) {
        if (labelCount < 0) {
            throw new IllegalArgumentException("labelCount must be >= 0: " + labelCount);
        }
        if (labelCount == 0) {
            return this;
        }
        long room = (long) Integer.MAX_VALUE - firstLabel + 1;
        if (labelCount > room) {
            throw new IllegalArgumentException(labelCount + " labels from " + firstLabel + " exceed the int range (at most "
                    + room + " fit)");
        }
        if (labelCount > Long.MAX_VALUE - count) {
            throw new ArithmeticException("the census counts " + count + " labels and cannot count " + labelCount + " more");
        }
        int last = (int) (firstLabel + labelCount - 1);
        count += labelCount;
        if (firstLabel < min) {
            min = firstLabel;
        }
        if (last > max) {
            max = last;
        }
        return this;
    }

    public long count() {
        return count;
    }

    /** Smallest label seen; only meaningful when {@link #count()} is positive. */
    public int min() {
        return min;
    }

    /** Largest label seen; only meaningful when {@link #count()} is positive. */
    public int max() {
        return max;
    }

    public boolean fits(KeyEncoding encoding) {
        return snapshot().fits(encoding);
    }

    /** What the census has seen so far, as an immutable value. */
    public Snapshot snapshot() {
        return new Snapshot(count, min, max);
    }

    /**
     * An immutable census result: what a dictionary or a report keeps after the parse.
     *
     * @param count how many labels were seen
     * @param min   the smallest label; {@code Integer.MAX_VALUE} when {@code count} is zero
     * @param max   the largest label; {@code Integer.MIN_VALUE} when {@code count} is zero
     */
    public record Snapshot(long count, int min, int max) {

        public Snapshot {
            if (count < 0) {
                throw new IllegalArgumentException("count must be >= 0: " + count);
            }
            if (count > 0 && min > max) {
                throw new IllegalArgumentException("min " + min + " > max " + max);
            }
            if (count == 0 && (min != Integer.MAX_VALUE || max != Integer.MIN_VALUE)) {
                throw new IllegalArgumentException("an empty census has no extremes");
            }
        }

        public boolean fits(KeyEncoding encoding) {
            return count == 0 || (min >= encoding.minLabel() && max <= encoding.maxLabel());
        }
    }

    /**
     * @throws FrameworkException KEY_CENSUS_OVERFLOW when the labels do not fit what the policy allows, or
     *                            KEY_LABEL_RESERVED when the offending extreme is the reserved sentinel
     */
    public KeyEncoding select(KeyEncodingPolicy policy, SourceLocation where) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(where, "where");
        return switch (policy) {
            case AUTO_PRESERVE -> fits(KeyEncoding.SIGNED_BYTE) ? KeyEncoding.SIGNED_BYTE
                    : require(KeyEncoding.PACKED_LEGACY16, policy, where);
            case SIGNED_BYTE -> require(KeyEncoding.SIGNED_BYTE, policy, where);
            case PACKED_LEGACY16 -> require(KeyEncoding.PACKED_LEGACY16, policy, where);
        };
    }

    private KeyEncoding require(KeyEncoding encoding, KeyEncodingPolicy policy, SourceLocation where) {
        if (fits(encoding)) {
            return encoding;
        }
        boolean reserved = min == encoding.reservedLabel() || max == encoding.reservedLabel();
        ErrorCode code = reserved ? ErrorCode.KEY_LABEL_RESERVED : ErrorCode.KEY_CENSUS_OVERFLOW;
        String why = reserved
                ? "a label equals the reserved NULL sentinel " + encoding.reservedLabel() + " of " + encoding
                : "labels " + min + ".." + max + " do not fit " + encoding + " (" + encoding.minLabel() + ".."
                + encoding.maxLabel() + ")";
        throw new FrameworkException(Diagnostic.error(code, "key census under policy " + policy + ": " + why,
                where, "policy", policy.name(), "encoding", encoding.name(), "labelCount", Long.toString(count),
                "min", Integer.toString(min), "max", Integer.toString(max),
                "allowedMin", Integer.toString(encoding.minLabel()), "allowedMax", Integer.toString(encoding.maxLabel())));
    }
}
