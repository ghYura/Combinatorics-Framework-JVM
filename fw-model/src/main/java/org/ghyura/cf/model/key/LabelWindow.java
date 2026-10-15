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
 * Where the value labels of a scenario start (architect decision F2). {@link Mode#PRESERVE} keeps the legacy
 * numbering S+1..S+C; {@link Mode#LEGACY_WINDOW} reproduces the legacy identity strategy of the 16-bit tier
 * (DataTypeDispatcher.plan with Tier.SHORT): the same numbering while S+C fits 32767, otherwise the window of C
 * consecutive labels inside -32767..32767 that is cheapest to write (fewest characters, a minus sign counting one),
 * the highest such window on ties ({@code uniformBestBase} scans bases from the highest down and keeps the first strict
 * minimum). Labels stay an order-preserving affine map of the cell order either way, so FW_Group's signed ordering is
 * unchanged; only the numbers a regular expression sees move, exactly as they moved in the legacy run.
 *
 * @param mode       how the labels were placed
 * @param scope      what the legacy-window gate counted
 * @param firstLabel the label of the first value in cell order
 * @param shifted    whether the window moved away from S+1
 */
public record LabelWindow(Mode mode, Scope scope, int firstLabel, boolean shifted) {

    /** The legacy 16-bit tier's usable labels: Short.MIN_VALUE stays the reserved NULL element. */
    public static final int MIN = -32767;
    public static final int MAX = 32767;
    public static final int CAPACITY = MAX - MIN + 1;

    public enum Mode { PRESERVE, LEGACY_WINDOW }

    /** What the gate counts against the 65535 labels: data sheets, values and virtual sheets, or values only. */
    public enum Scope { ALL, CELLS }

    public LabelWindow {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(scope, "scope");
    }

    /**
     * Places the labels of {@code values} values for {@code sheets} data sheets and {@code virtualSheets} virtual ones.
     *
     * @throws FrameworkException KEY_CENSUS_OVERFLOW when the legacy-window gate refuses the scenario, as the legacy
     *                            dispatcher did
     */
    public static LabelWindow place(Mode mode, Scope scope, int sheets, int virtualSheets, long values, SourceLocation where) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(scope, "scope");
        int identity = sheets + 1;
        if (mode == Mode.PRESERVE) {
            return new LabelWindow(mode, scope, identity, false);
        }
        long gate = scope == Scope.ALL ? sheets + values + virtualSheets : values;
        if (gate > CAPACITY || (long) sheets + virtualSheets > MAX) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.KEY_CENSUS_OVERFLOW, "the scenario needs " + gate + " keys ("
                    + sheets + " sheets + " + values + " values + " + virtualSheets + " virtual sheets, scope " + scope.name().toLowerCase(java.util.Locale.ROOT)
                    + "); the legacy 16-bit tier holds " + CAPACITY + " and the legacy dispatcher refused this scenario too", where,
                    "values", Long.toString(values)));
        }
        if (values == 0 || (long) sheets + values <= MAX) {
            return new LabelWindow(mode, scope, identity, false);
        }
        return new LabelWindow(mode, scope, bestBase((int) values), true);
    }

    /** Characters a label takes when written: its digits, plus one for a minus sign (DataTypeDispatcher.labelChars). */
    public static int chars(int label) {
        int a = Math.abs(label);
        int digits = a < 10 ? 1 : a < 100 ? 2 : a < 1000 ? 3 : a < 10000 ? 4 : 5;
        return label < 0 ? digits + 1 : digits;
    }

    /**
     * The first label of the {@code n}-wide window inside -32767..32767 with the fewest characters, the highest base on
     * ties: the legacy {@code uniformBestBase}, with its cost updated as the window slides instead of read from a prefix
     * table (the same integers, so the same choice).
     */
    public static int bestBase(int n) {
        if (n < 1 || n > CAPACITY) {
            throw new IllegalArgumentException("a window holds 1 to " + CAPACITY + " labels: " + n);
        }
        int hi = MAX - n + 1;
        long cost = 0;
        for (int label = hi; label <= MAX; label++) {
            cost += chars(label);
        }
        int best = hi;
        long bestCost = cost;
        for (int base = hi - 1; base >= MIN; base--) {
            cost += chars(base) - chars(base + n);
            if (cost < bestCost) {
                bestCost = cost;
                best = base;
            }
        }
        return best;
    }

    /** The last label in use for {@code values} values. */
    public int lastLabel(long values) {
        return (int) (firstLabel + values - 1);
    }

    /** A canonical text of the decision, for identities and provenance. */
    public String canonical() {
        return "labels=" + mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + ";scope="
                + scope.name().toLowerCase(java.util.Locale.ROOT) + ";first=" + firstLabel;
    }
}
