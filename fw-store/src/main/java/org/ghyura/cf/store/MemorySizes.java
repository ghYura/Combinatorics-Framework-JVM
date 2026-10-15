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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.lang.reflect.RecordComponent;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Conservative admission estimates for the framework's immutable record graphs; never constructs a serialized copy. */
public final class MemorySizes {
    private MemorySizes() { }

    public static long add(long a, long b) {
        if (a < 0 || b < 0 || a > Long.MAX_VALUE - b) throw limit("memory size overflow");
        return a + b;
    }

    public static long multiply(long a, long b) {
        if (a < 0 || b < 0 || (b != 0 && a > Long.MAX_VALUE / b)) throw limit("memory size overflow");
        return a * b;
    }

    /** Shared objects are deliberately counted at every occurrence. Includes collection capacity and reference copies. */
    public static long retained(Object value) {
        return retained(value, 0);
    }

    private static long retained(Object value, int depth) {
        if (depth > 64) throw limit("checkpoint state is nested deeper than 64 levels");
        if (value == null) return 0;
        if (value instanceof String text) return add(64, multiply(2, text.length()));
        if (value instanceof BigInteger integer) return add(96, multiply(2, (integer.bitLength() + 7L) / 8));
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) return 32;
        if (value instanceof Optional<?> optional) return add(32, retained(optional.orElse(null), depth + 1));
        long bytes = 128;
        if (value instanceof List<?> list) {
            for (Object entry : list) bytes = add(bytes, add(32, retained(entry, depth + 1)));
        } else if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) bytes = add(bytes,
                    add(160, add(retained(entry.getKey(), depth + 1), retained(entry.getValue(), depth + 1))));
        } else if (value.getClass().isRecord()) {
            try {
                for (RecordComponent component : value.getClass().getRecordComponents())
                    bytes = add(bytes, add(128, retained(component.getAccessor().invoke(value), depth + 1)));
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("cannot size checkpoint record " + value.getClass().getName(), e);
            }
        } else throw new IllegalArgumentException("unsupported checkpoint component " + value.getClass().getName());
        return bytes;
    }

    public static FrameworkException limit(String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, message, SourceLocation.NONE));
    }
}
