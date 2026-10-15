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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The bytecode of one compiled source: its classes by binary name, the type the source declares, and the digest of
 * everything that decided the bytecode (source, options, class path). Immutable: the arrays are copied in and out, so a
 * cached compilation can serve many samples without sharing anything mutable.
 */
public final class CompiledClasses {

    private final String mainType;
    private final Map<String, byte[]> classes;
    private final String digest;
    private final long bytes;
    private final LineCoverage.Probes probes;

    CompiledClasses(String mainType, Map<String, byte[]> classes, String digest) {
        this(mainType, classes, digest, null);
    }

    CompiledClasses(String mainType, Map<String, byte[]> classes, String digest, LineCoverage.Probes probes) {
        this.mainType = Objects.requireNonNull(mainType, "mainType");
        TreeMap<String, byte[]> copy = new TreeMap<>();
        long total = 0;
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            copy.put(e.getKey(), e.getValue().clone());
            total += e.getValue().length + 2L * e.getKey().length() + 64;
        }
        this.classes = Collections.unmodifiableMap(copy);
        this.digest = Objects.requireNonNull(digest, "digest");
        this.bytes = total + (probes == null ? 0 : 4L * probes.total() + 64);
        this.probes = probes;
    }

    /** The same classes under another digest, with what line instrumentation made of them. */
    CompiledClasses as(String digest, LineCoverage.Probes probes) {
        return new CompiledClasses(mainType, classes, digest, probes);
    }

    /** What line instrumentation added (cf.execution.coverage=lines), or null for a plain compilation. */
    public LineCoverage.Probes probes() {
        return probes;
    }

    /** The binary name of the type the source declares (the class whose main runs). */
    public String mainType() {
        return mainType;
    }

    /** The binary names of every class the source produced. */
    public java.util.Set<String> names() {
        return classes.keySet();
    }

    /** A copy of one class's bytecode, or null. */
    public byte[] bytecode(String binaryName) {
        byte[] b = classes.get(binaryName);
        return b == null ? null : b.clone();
    }

    /** SHA-256 over source, options and class path. */
    public String digest() {
        return digest;
    }

    /** Bytes this compilation holds (bytecode plus bookkeeping). */
    public long bytes() {
        return bytes;
    }
}
