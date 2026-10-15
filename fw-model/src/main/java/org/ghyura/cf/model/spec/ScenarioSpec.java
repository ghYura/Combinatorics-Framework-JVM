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

package org.ghyura.cf.model.spec;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.rule.ConstraintSet;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The normalized, immutable scenario: what a workbook (or JSON, YAML, TOML or builder input) means once its
 * cells have been interpreted, before any plan or dictionary is built from it. Data sheets and their values,
 * the typed control sheets, and the FW_Seq rows as written. Nothing here is a database row, a POI object or a
 * mutable collection, and nothing depends on the file format the scenario came from: two inputs that mean the
 * same scenario have the same {@link #canonicalText()}.
 *
 * <p>The sheet namespace (keys 1..S) is separate from the value namespace (labels, assigned by the
 * dictionary), as in byte-migration rule B08.</p>
 *
 * @param source           where the scenario came from (a file name or a logical name); not part of its identity
 * @param sheets           data sheets in workbook order, keys 1..S
 * @param controls         the typed control sheets other than FW_Seq
 * @param hasSequenceSheet whether a FW_Seq sheet exists at all (an absent sheet is not an empty one)
 * @param sequence         the FW_Seq rows that have content, in sheet order
 * @param constraints      the constraint layer (sidecar rules, params and orders); {@link ConstraintSet#NONE} when the
 *                         scenario has none
 */
public record ScenarioSpec(String source, List<SheetSpec> sheets, ControlSpec controls, boolean hasSequenceSheet,
                           List<SeqRow> sequence, ConstraintSet constraints) {

    public ScenarioSpec {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sheets, "sheets");
        Objects.requireNonNull(controls, "controls");
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(constraints, "constraints");
        sheets = List.copyOf(sheets);
        sequence = List.copyOf(sequence);
        Set<String> names = new HashSet<>();
        int expectedKey = 1;
        for (SheetSpec sheet : sheets) {
            if (sheet.key() != expectedKey++) {
                throw new IllegalArgumentException("sheet keys must be 1..S in order; '" + sheet.name() + "' has key " + sheet.key());
            }
            if (sheet.name().startsWith("FW_")) {
                throw new IllegalArgumentException("'" + sheet.name() + "' is a control sheet name, not a data sheet");
            }
            if (!names.add(sheet.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("duplicate sheet name (ignoring case): '" + sheet.name() + "'");
            }
        }
        if (!hasSequenceSheet && !sequence.isEmpty()) {
            throw new IllegalArgumentException("sequence rows without a FW_Seq sheet");
        }
        int previous = -1;
        for (SeqRow row : sequence) {
            if (row.ordinal() <= previous) {
                throw new IllegalArgumentException("FW_Seq rows are not in strictly increasing order");
            }
            previous = row.ordinal();
        }
    }

    /** A scenario without constraints. */
    public ScenarioSpec(String source, List<SheetSpec> sheets, ControlSpec controls, boolean hasSequenceSheet,
                        List<SeqRow> sequence) {
        this(source, sheets, controls, hasSequenceSheet, sequence, ConstraintSet.NONE);
    }

    /** The same scenario with this constraint layer. */
    public ScenarioSpec withConstraints(ConstraintSet layer) {
        return new ScenarioSpec(source, sheets, controls, hasSequenceSheet, sequence, layer);
    }

    public Optional<SheetSpec> sheet(String exactName) {
        for (SheetSpec sheet : sheets) {
            if (sheet.name().equals(exactName)) {
                return Optional.of(sheet);
            }
        }
        return Optional.empty();
    }

    /** The data sheet with this key, 1..S. */
    public Optional<SheetSpec> sheetByKey(int key) {
        return key >= 1 && key <= sheets.size() ? Optional.of(sheets.get(key - 1)) : Optional.empty();
    }

    /** S: the number of data sheets. */
    public int sheetCount() {
        return sheets.size();
    }

    /** C: the number of values over all data sheets, i.e. how many labels the dictionary will issue. */
    public long valueCount() {
        long count = 0;
        for (SheetSpec sheet : sheets) {
            count += sheet.cells().size();
        }
        return count;
    }

    /**
     * A deterministic text of the logical content, independent of format, file name and every location. Two
     * inputs that mean the same scenario have the same canonical text; this is the comparison gate A03 uses.
     */
    public String canonicalText() {
        StringBuilder out = new StringBuilder(1024);
        out.append("cf.scenario/1\n");
        for (SheetSpec sheet : sheets) {
            out.append("SHEET\t").append(sheet.key()).append('\t').append(Canonical.escape(sheet.name())).append('\n');
            for (ValueCell cell : sheet.cells()) {
                out.append("VALUE\t").append(cell.kind().code()).append('\t').append(cell.optional() ? 'O' : '-')
                        .append('\t').append(cell.refined() ? 'R' : '-').append('\t').append(Canonical.escape(cell.text()))
                        .append('\n');
            }
        }
        for (SheetDecoration d : controls.sheetNames()) {
            out.append("SHEETNAME\t").append(Canonical.escape(d.sheet())).append('\t').append(Canonical.escape(d.prefix()))
                    .append('\t').append(Canonical.escape(d.ending())).append('\n');
        }
        for (LocatedText text : controls.runMeFirstOnce()) {
            out.append("RUNME\t").append(Canonical.escape(text.text())).append('\n');
        }
        for (LocatedText text : controls.arguments()) {
            out.append("ARG\t").append(Canonical.escape(text.text())).append('\n');
        }
        for (CustomVar var : controls.customVars()) {
            out.append("CUSTOM\t").append(var.index()).append('\t').append(Canonical.escape(var.declaration())).append('\n');
        }
        out.append("SEQ\t").append(hasSequenceSheet ? "present" : "absent").append('\n');
        for (SeqRow row : sequence) {
            out.append("ROW\t").append(row.ordinal());
            for (LocatedText cell : row.cells()) {
                out.append('\t').append(Canonical.escape(cell.text()));
            }
            out.append('\n');
        }
        if (!constraints.isEmpty()) {
            // only a scenario with constraints has these lines, so the identity of every other scenario is unchanged
            out.append(constraints.canonicalText());
        }
        return out.toString();
    }

    /** SHA-256 of the canonical text: the content identity of the scenario. */
    public String contentDigest() {
        return Canonical.sha256Hex(canonicalText());
    }
}
