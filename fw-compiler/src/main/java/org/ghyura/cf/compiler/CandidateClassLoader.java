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

import java.util.Objects;

/**
 * Defines one compilation's classes for one sample. Each sample gets its own loader, so static fields (the verdict
 * FW_VAR among them) start fresh for every repeat even when the bytecode comes from the cache; nothing is shared
 * between samples except the immutable bytecode. A class loader is not a security boundary (architecture D07).
 */
public final class CandidateClassLoader extends ClassLoader {

    static {
        registerAsParallelCapable();
    }

    private final CompiledClasses compiled;

    public CandidateClassLoader(CompiledClasses compiled, ClassLoader parent) {
        super("cf-candidate", parent);
        this.compiled = Objects.requireNonNull(compiled, "compiled");
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = compiled.bytecode(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null && compiled.names().contains(name)) {
                c = findClass(name);
            }
            if (c == null) {
                return super.loadClass(name, resolve);
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
}
