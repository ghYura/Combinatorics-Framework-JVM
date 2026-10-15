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

import org.ghyura.cf.model.Xml;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A count and how sure it is (fwgen's CardinalityEstimate): EXACT when it follows from known inputs, BOUNDED when only
 * a proven range is known, ESTIMATED when it rests on an assumed cost, UNKNOWN when no sound number exists. The value
 * is the best single figure (null when UNKNOWN); lower and upper bound the true count whenever that is proven. Every
 * count keeps its formula and the reasons and assumptions behind it, so a range or a guess is never shown with the
 * confidence of an exact count, and an unknown count never becomes zero.
 */
public record Cardinality(Mode mode, BigInteger value, BigInteger lower, BigInteger upper, String formula, List<String> reasons,
                          List<String> assumptions) {

    /** How sure a count is, from the most to the least. */
    public enum Mode {
        /** Follows from known inputs by a closed formula or a complete count. */
        EXACT,
        /** The true count is unknown but proven to lie in [lower, upper]. */
        BOUNDED,
        /** A best-effort figure resting on an assumed cost: neither exact nor proven. */
        ESTIMATED,
        /** No sound figure; only the reasons are known. */
        UNKNOWN
    }

    public Cardinality {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(formula, "formula");
        reasons = List.copyOf(reasons);
        assumptions = List.copyOf(assumptions);
        if (mode == Mode.UNKNOWN ? value != null || lower != null || upper != null
                : value == null || lower == null || upper == null || lower.compareTo(upper) > 0) {
            throw new IllegalArgumentException(mode + " with value " + value + " in [" + lower + ", " + upper + "]");
        }
    }

    public static Cardinality exact(BigInteger value, String formula, String... assumptions) {
        return new Cardinality(Mode.EXACT, value, value, value, formula, List.of(), List.of(assumptions));
    }

    /** A proven range; its figure is the upper bound, as in fwgen. */
    public static Cardinality bounded(BigInteger lower, BigInteger upper, String formula, String... reasons) {
        return new Cardinality(Mode.BOUNDED, upper, lower, upper, formula, List.of(reasons), List.of());
    }

    public static Cardinality estimated(BigInteger value, BigInteger lower, BigInteger upper, String formula, List<String> reasons,
                                        List<String> assumptions) {
        return new Cardinality(Mode.ESTIMATED, value, lower, upper, formula, reasons, assumptions);
    }

    public static Cardinality unknown(String formula, String... reasons) {
        return new Cardinality(Mode.UNKNOWN, null, null, null, formula, List.of(reasons), List.of());
    }

    public boolean known() {
        return mode != Mode.UNKNOWN;
    }

    /**
     * This count times an exact factor, keeping its confidence (fwgen scale_cardinality_exact): an exact factor of 0
     * proves 0 whatever the count, and an unknown count stays unknown.
     */
    public Cardinality times(BigInteger factor, String formula) {
        if (factor.signum() == 0) {
            return exact(BigInteger.ZERO, formula);
        }
        if (mode == Mode.UNKNOWN) {
            return new Cardinality(Mode.UNKNOWN, null, null, null, formula, reasons, assumptions);
        }
        return new Cardinality(mode, value.multiply(factor), lower.multiply(factor), upper.multiply(factor), formula, reasons, assumptions);
    }

    /** At most {@code cap}: a cap proves an upper bound even of an unknown count, and a range below the cap is unchanged. */
    public Cardinality atMost(BigInteger cap, String formula, String reason) {
        if (mode == Mode.UNKNOWN) {
            return new Cardinality(Mode.BOUNDED, cap, BigInteger.ZERO, cap, formula, with(reasons, reason), assumptions);
        }
        return new Cardinality(mode, value.min(cap), lower.min(cap), upper.min(cap), formula, reasons, assumptions);
    }

    /** Anything from 0 to this count: something may end it earlier (a stop policy, a time budget). */
    public Cardinality orFewer(String formula, String reason) {
        if (mode == Mode.UNKNOWN || upper.signum() == 0) {
            return new Cardinality(mode, value, lower, upper, formula, reasons, assumptions);
        }
        return new Cardinality(mode == Mode.EXACT ? Mode.BOUNDED : mode, upper, BigInteger.ZERO, upper, formula, with(reasons, reason),
                assumptions);
    }

    private static List<String> with(List<String> list, String more) {
        List<String> out = new ArrayList<>(list);
        out.add(more);
        return out;
    }

    /** One line: {@code 24 (EXACT: C(4,2) × 4)}, {@code [0, 30] (BOUNDED: ...)}, {@code ~120 in [60, 240] (ESTIMATED: ...)}. */
    public String describe() {
        return figure() + " (" + mode + (formula.isEmpty() ? "" : ": " + formula) + ")";
    }

    /** The figure alone: {@code 24}, {@code [0, 30]}, {@code ~120 in [60, 240]} or {@code unknown}. */
    public String figure() {
        return switch (mode) {
            case EXACT -> value.toString();
            case BOUNDED -> lower.equals(upper) ? value.toString() : "[" + lower + ", " + upper + "]";
            case ESTIMATED -> "~" + value + " in [" + lower + ", " + upper + "]";
            case UNKNOWN -> "unknown";
        };
    }

    /** {@code <estimate name=".." mode=".." value=".." lower=".." upper=".." formula=".."/>}, with its reasons and assumptions. */
    public void appendXml(StringBuilder xml, String indent, String name) {
        xml.append(indent).append("<estimate name=\"").append(Xml.escape(name)).append("\" mode=\"").append(mode).append('"');
        if (mode != Mode.UNKNOWN) {
            xml.append(" value=\"").append(value).append("\" lower=\"").append(lower).append("\" upper=\"").append(upper).append('"');
        }
        xml.append(" formula=\"").append(Xml.escape(formula)).append('"');
        if (reasons.isEmpty() && assumptions.isEmpty()) {
            xml.append("/>\n");
            return;
        }
        xml.append(">\n");
        for (String r : reasons) {
            xml.append(indent).append("  <reason>").append(Xml.escape(r)).append("</reason>\n");
        }
        for (String a : assumptions) {
            xml.append(indent).append("  <assumption>").append(Xml.escape(a)).append("</assumption>\n");
        }
        xml.append(indent).append("</estimate>\n");
    }

    @Override
    public String toString() {
        return describe();
    }
}
