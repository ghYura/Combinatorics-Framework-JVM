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

import org.ghyura.cf.model.SourceLocation;

import java.util.Arrays;
import java.util.Objects;

/**
 * The one boundary that understands the legacy NULL sentinel (B07). Legacy key arrays reserve the minimum
 * value of their width as a NULL element: the Reader drops it when it renders a row and treats a bond that
 * touches it as not applicable (CopyToReader.NULL_ELEMENT; ComboGenerationPipeline lines 223/233;
 * OptionalBondFilter line 146). Everything past this boundary uses typed states instead, and a normalized
 * {@link KeyRow} can never contain a reserved label.
 *
 * <p>The sentinel depends on the width the data was written in, which is why the caller names the
 * {@link LegacyKeyTier}: -128 is NULL in a legacy byte-tier array and an ordinary label in a legacy 16-bit
 * array.</p>
 */
public final class LegacyKeyImport {

    /** The width a legacy array was stored in. */
    public enum LegacyKeyTier {
        /** Legacy byte tier: labels -127..127, NULL element -128. */
        BYTE(KeyEncoding.SIGNED_BYTE),
        /** Legacy 16-bit tier: labels -32767..32767, NULL element -32768. */
        WIDE(KeyEncoding.PACKED_LEGACY16);

        private final KeyEncoding encoding;

        LegacyKeyTier(KeyEncoding encoding) {
            this.encoding = encoding;
        }

        public int nullSentinel() {
            return encoding.reservedLabel();
        }

        /** The encoding with the same label domain. */
        public KeyEncoding encoding() {
            return encoding;
        }
    }

    /** How the legacy table treated an empty or NULL column. */
    public enum ColumnKind {
        /** fw_final column stored as a delta: NULL or empty inherits the baseline row. */
        MANDATORY_DELTA,
        /** Optional column: NULL or empty means the optional sheet is absent. */
        OPTIONAL
    }

    /**
     * @param state              the typed state of the slot
     * @param row                the labels, non-null exactly when {@code state} is PRESENT
     * @param droppedNullElements how many legacy NULL elements were removed from the array
     */
    public record ImportedCell(SelectionState state, KeyRow row, int droppedNullElements) {
        public ImportedCell {
            Objects.requireNonNull(state, "state");
            if ((state == SelectionState.PRESENT) != (row != null)) {
                throw new IllegalArgumentException("a row is present exactly when the state is PRESENT");
            }
            if (droppedNullElements < 0) {
                throw new IllegalArgumentException("droppedNullElements must be >= 0");
            }
        }
    }

    private LegacyKeyImport() {
    }

    /**
     * Normalizes one legacy array into a typed slot.
     *
     * <ul>
     *   <li>{@code null} (SQL NULL column) or an empty array: BASELINE_REFERENCE for a
     *       {@link ColumnKind#MANDATORY_DELTA} column, ABSENT for an {@link ColumnKind#OPTIONAL} column;</li>
     *   <li>otherwise NULL-sentinel elements are removed and counted, every remaining label must be legal in
     *       the legacy tier and in {@code target}, and the slot is PRESENT. An array that held only NULL
     *       elements becomes a PRESENT empty row: the array existed, it just rendered nothing.</li>
     * </ul>
     *
     * @param legacyLabels the decoded legacy elements, or {@code null} for a NULL column
     * @throws org.ghyura.cf.model.FrameworkException KEY_LABEL_RESERVED or KEY_LABEL_OUT_OF_RANGE
     */
    public static ImportedCell importCell(ColumnKind kind, LegacyKeyTier tier, int[] legacyLabels,
                                          KeyEncoding target, SourceLocation where) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(where, "where");
        if (legacyLabels == null || legacyLabels.length == 0) {
            return new ImportedCell(kind == ColumnKind.MANDATORY_DELTA
                    ? SelectionState.BASELINE_REFERENCE : SelectionState.ABSENT, null, 0);
        }
        int sentinel = tier.nullSentinel();
        int[] kept = new int[legacyLabels.length];
        int n = 0;
        for (int i = 0; i < legacyLabels.length; i++) {
            int label = legacyLabels[i];
            if (label == sentinel) {
                continue;
            }
            tier.encoding().requireLegal(label, i, where);
            target.requireLegal(label, i, where);
            kept[n++] = label;
        }
        KeyRow row = KeyRow.ofLabels(target, Arrays.copyOf(kept, n), where);
        return new ImportedCell(SelectionState.PRESENT, row, legacyLabels.length - n);
    }
}
