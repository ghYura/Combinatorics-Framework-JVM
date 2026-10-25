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

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.ghyura.cf.model.row.RowSource;

/**
 * Everything a generation run reserved from its memory budget (stored tables, indexes): kept until the run's result
 * is closed, released at once when the run fails or is cancelled, or earlier when a stage no longer needs it.
 */
final class Arena implements AutoCloseable {

    private final List<AutoCloseable> owned = new ArrayList<>();

    <T extends AutoCloseable> T own(T resource) {
        owned.add(resource);
        return resource;
    }

    /** Releases one resource now, if this arena owns it. */
    void release(AutoCloseable resource) {
        if (owned.remove(resource)) {
            closeQuietly(resource, null);
        }
    }

    /** Drop obsolete row resources while keeping every dependency of the live tables. */
    void retainRows(Collection<RowSource> roots) {
        Set<RowSource> live = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<RowSource> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            RowSource row = pending.removeLast();
            if (live.add(row)) {
                pending.addAll(RowSources.inputs(row));
            }
        }
        RuntimeException failure = null;
        for (int i = owned.size() - 1; i >= 0; i--) {
            AutoCloseable resource = owned.get(i);
            if (resource instanceof RowSource row && !live.contains(row)) {
                owned.remove(i);
                failure = closeQuietly(resource, failure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (int i = owned.size() - 1; i >= 0; i--) {
            failure = closeQuietly(owned.get(i), failure);
        }
        owned.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException closeQuietly(AutoCloseable resource, RuntimeException failure) {
        try {
            resource.close();
        } catch (Exception e) {
            RuntimeException wrapped = e instanceof RuntimeException r ? r : new IllegalStateException(e);
            if (failure == null) {
                return wrapped;
            }
            failure.addSuppressed(wrapped);
        }
        return failure;
    }
}
