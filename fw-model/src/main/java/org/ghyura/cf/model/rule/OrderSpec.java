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

package org.ghyura.cf.model.rule;

import java.util.List;
import java.util.Objects;

/**
 * How the ordinal condition operators rank a sheet's values (legacy sieve {@code orders} and {@code _rank}): by the
 * position in a declared list (a value not listed is an error, never a silent lexicographic guess), as numbers, or
 * as ISO dates.
 */
public sealed interface OrderSpec {

    /** The rank is the value's index in this list. */
    record Listed(List<RuleValue> values) implements OrderSpec {
        public Listed {
            values = List.copyOf(values);
        }
    }

    /** The rank is Python {@code float(value)}. */
    enum Numeric implements OrderSpec {
        NUMERIC
    }

    /** The rank is the proleptic Gregorian ordinal of the ISO date in the value's first ten characters. */
    enum Date implements OrderSpec {
        DATE
    }

    default String canonical() {
        return switch (this) {
            case Listed l -> new RuleValue.ListValue(l.values()).canonical();
            case Numeric n -> "numeric";
            case Date d -> "date";
        };
    }

    static OrderSpec listed(List<RuleValue> values) {
        return new Listed(Objects.requireNonNull(values, "values"));
    }
}
