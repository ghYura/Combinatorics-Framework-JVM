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

package org.ghyura.cf.input.builder;

import java.util.ArrayList;
import java.util.List;

/**
 * A typed constructor for the nine-field joiner {@code FW_(start,,Ei,relation,Ej,,end,separator,multiplicity)}.
 * The two empty slots and the relation slot are unused in canonical scenarios; up to two middle entities are
 * supported, and anything else goes through {@link ScheduleBuilder#joinRaw}. Adapted from the legacy
 * {@code com.company.excel.JoinSpec}.
 */
public final class JoinSpec {

    private final String start;
    private String end = "";
    private final List<String> middle = new ArrayList<>(2);
    private String separator = "";
    private Multiplicity multiplicity = Multiplicity.M_N;

    private JoinSpec(String start) {
        if (start == null || start.isBlank()) {
            throw new IllegalArgumentException("JoinSpec.from: start must not be blank");
        }
        this.start = start;
    }

    public static JoinSpec from(String start) {
        return new JoinSpec(start);
    }

    /** Adds middle entities (blank ones are skipped). */
    public JoinSpec via(String... middles) {
        if (middles != null) {
            for (String m : middles) {
                if (m != null && !m.isBlank()) {
                    middle.add(m);
                }
            }
        }
        return this;
    }

    public JoinSpec to(String end) {
        if (end == null || end.isBlank()) {
            throw new IllegalArgumentException("JoinSpec.to: end must not be blank");
        }
        this.end = end;
        return this;
    }

    public JoinSpec withSeparator(String separator) {
        this.separator = separator == null ? "" : separator;
        return this;
    }

    public JoinSpec as(Multiplicity multiplicity) {
        if (multiplicity == null) {
            throw new IllegalArgumentException("JoinSpec.as: multiplicity must not be null");
        }
        this.multiplicity = multiplicity;
        return this;
    }

    /** The canonical nine-field text; missing middle slots are empty. */
    public String toFwExpression() {
        String ei = middle.size() > 0 ? middle.get(0) : "";
        String ej = middle.size() > 1 ? middle.get(1) : "";
        return "FW_(" + start + ",," + ei + ",," + ej + ",," + end + "," + separator + "," + multiplicity.token() + ")";
    }

    @Override
    public String toString() {
        return toFwExpression();
    }
}
