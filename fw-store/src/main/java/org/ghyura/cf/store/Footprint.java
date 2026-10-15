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

import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.CustomVar;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetDecoration;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;

import java.util.List;

/**
 * Conservative upper bounds of the heap that a structure keeps reachable, so that memory can be reserved
 * before it is kept (architecture rule R08). They are computed from the content, not measured.
 *
 * <p>The bounds assume the most expensive layout a 64-bit JVM uses: 16-octet object headers (no compressed
 * class pointers), 8-octet references (no compressed oops), two octets per character (no compact strings) and
 * 8-octet alignment. A default JVM is smaller on every count, so the bounds over-estimate and never
 * under-estimate. Every object is counted where it is reached; a string that two locations share is counted
 * twice, which over-estimates as well.</p>
 */
final class Footprint {

    static final long HEADER = 16;
    static final long REFERENCE = 8;
    static final long INT = 4;
    static final long LONG = 8;

    private Footprint() {
    }

    static long align(long bytes) {
        return (bytes + 7) & ~7L;
    }

    /** An object with the given number of reference fields and primitive octets. */
    static long object(int references, long primitiveOctets) {
        return align(HEADER + references * REFERENCE + primitiveOctets);
    }

    /** An array object with {@code payloadOctets} of elements. */
    static long array(long payloadOctets) {
        return align(HEADER + INT + payloadOctets);
    }

    static long referenceArray(long elements) {
        return array(elements * REFERENCE);
    }

    /** A String: the object (value reference, hash, coder and flag) plus its char array at two octets a char. */
    static long string(String text) {
        return object(1, INT + 2) + array(2L * text.length());
    }

    /** An immutable list of {@code size} elements: the list object plus its element array. */
    static long list(List<?> list) {
        return object(1, 1) + referenceArray(list.size());
    }

    static long location(SourceLocation location) {
        return object(2, 2 * INT) + string(location.origin()) + string(location.path());
    }

    static long locatedText(LocatedText text) {
        return object(2, 0) + string(text.text()) + location(text.location());
    }

    static long valueCell(ValueCell cell) {
        return object(3, 2) + string(cell.text()) + location(cell.location());
    }

    /**
     * Everything a {@link ScenarioSpec} keeps reachable: the source name, every sheet with its name, location
     * and values, the control sheets (decorations, run-once code, arguments, custom variables) and the FW_Seq
     * rows with their cells, each with its source location. The shared enum constants of value kinds are not
     * the spec's and are not counted.
     */
    static long of(ScenarioSpec spec) {
        long total = object(4, 1) + string(spec.source()) + list(spec.sheets()) + list(spec.sequence());
        for (SheetSpec sheet : spec.sheets()) {
            total += object(3, INT) + string(sheet.name()) + location(sheet.location()) + list(sheet.cells());
            for (ValueCell cell : sheet.cells()) {
                total += valueCell(cell);
            }
        }
        ControlSpec controls = spec.controls();
        total += object(4, 0) + list(controls.sheetNames()) + list(controls.runMeFirstOnce())
                + list(controls.arguments()) + list(controls.customVars());
        for (SheetDecoration decoration : controls.sheetNames()) {
            total += object(4, 0) + string(decoration.sheet()) + string(decoration.prefix()) + string(decoration.ending())
                    + location(decoration.location());
        }
        for (LocatedText text : controls.runMeFirstOnce()) {
            total += locatedText(text);
        }
        for (LocatedText text : controls.arguments()) {
            total += locatedText(text);
        }
        for (CustomVar variable : controls.customVars()) {
            total += object(2, INT) + string(variable.declaration()) + location(variable.location());
        }
        for (SeqRow row : spec.sequence()) {
            total += object(1, INT) + list(row.cells());
            for (LocatedText cell : row.cells()) {
                total += locatedText(cell);
            }
        }
        total += constraints(spec.constraints());
        return total;
    }

    /**
     * An upper bound of what a constraint layer keeps reachable: its parsed rules, conditions, expressions, parameters
     * and orders are trees of small objects whose content is all in the canonical text; every character of that text is
     * charged 32 bytes (object headers, references and boxed numbers included) and every rule, source and parameter
     * value one kilobyte more. A conservative estimate, not a measurement.
     */
    static long constraints(org.ghyura.cf.model.rule.ConstraintSet set) {
        if (set.isEmpty() && set.sources().isEmpty()) {
            return 0;
        }
        long values = 0;
        for (var sheet : set.params().values()) {
            values += sheet.size();
        }
        return 32L * set.canonicalText().length() + 1024L * (set.rules().size() + set.sources().size() + values)
                + 32L * set.rules().stream().mapToLong(r -> r.description() == null ? 0 : r.description().length()
                        + r.location().toString().length()).sum();
    }
}
