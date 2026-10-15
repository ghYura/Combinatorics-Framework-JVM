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


package org.ghyura.cf.model.row;

import org.ghyura.cf.model.key.KeyRow;

import java.math.BigInteger;
import java.util.Objects;

/**
 * One row of a generated table: its keys and its legacy provenance.
 *
 * @param id     the legacy combi_id: counted per sheet table in the order rows were written; a computed table may
 *               be longer than any machine word, so ids are exact integers
 * @param parent the id of the row of the previous pass this row was generated from, or 0 when there is none
 * @param keys   the row's keys
 */
public record Row(BigInteger id, BigInteger parent, KeyRow keys) {

    public Row {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(keys, "keys");
        if (id.signum() <= 0 || parent.signum() < 0) {
            throw new IllegalArgumentException("ids start at 1 and a parent id is 0 or positive: " + id + ", " + parent);
        }
    }

    /** Whether the row was generated from a row of an earlier pass. */
    public boolean hasParent() {
        return parent.signum() > 0;
    }

    /** The same row without its parent (the legacy fw_ tables had no parent column). */
    public Row withoutParent() {
        return hasParent() ? new Row(id, BigInteger.ZERO, keys) : this;
    }
}
