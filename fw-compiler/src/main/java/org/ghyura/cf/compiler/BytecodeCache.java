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

package org.ghyura.cf.compiler;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compiled bytecode by digest, bounded by bytes ({@code cf.compiler.cache.maxBytes}); the least recently used entry is
 * evicted first and 0 disables caching. It holds bytecode only, never classes or loaders, so reuse saves compilation
 * time without sharing any runtime state between samples.
 */
final class BytecodeCache {

    private final long maxBytes;
    private final LinkedHashMap<String, CompiledClasses> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;
    private long hits;
    private long misses;
    private long evictions;

    BytecodeCache(long maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be >= 0: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    synchronized CompiledClasses get(String digest) {
        CompiledClasses c = entries.get(digest);
        if (c == null) {
            misses++;
        } else {
            hits++;
        }
        return c;
    }

    synchronized void put(CompiledClasses compiled) {
        if (compiled.bytes() > maxBytes || entries.containsKey(compiled.digest())) {
            return;
        }
        entries.put(compiled.digest(), compiled);
        bytes += compiled.bytes();
        Iterator<Map.Entry<String, CompiledClasses>> it = entries.entrySet().iterator();
        while (bytes > maxBytes && it.hasNext()) {
            CompiledClasses old = it.next().getValue();
            it.remove();
            bytes -= old.bytes();
            evictions++;
        }
    }

    synchronized void clear() {
        entries.clear();
        bytes = 0;
    }

    synchronized Stats stats() {
        return new Stats(entries.size(), bytes, hits, misses, evictions);
    }

    /** Cache counters: entries and bytes held now, lookups that hit and missed, entries evicted. */
    record Stats(int entries, long bytes, long hits, long misses, long evictions) {
    }
}
