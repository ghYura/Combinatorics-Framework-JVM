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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceCompilerTest {

    private static final String COUNTER = """
            // a comment with public class Fake {
            public class Counter {
                public static int FW_VAR = -1;
                static int calls;
                static class Inner { }
                public static void main(String[] args) {
                    String brace = "}{ public class Quoted";
                    calls++;
                    FW_VAR = calls - 1;
                }
            }
            """;

    @Test
    void compilesInMemoryAndEveryLoaderStartsWithFreshStaticState() throws Exception {
        JavaSourceCompiler compiler = JavaSourceCompiler.create(List.of(), 1 << 20);
        assertTrue(compiler.available());
        Compilation.Compiled first = assertInstanceOf(Compilation.Compiled.class, compiler.compile(COUNTER));
        assertFalse(first.cached());
        assertEquals("Counter", first.classes().mainType());
        assertEquals(java.util.Set.of("Counter", "Counter$Inner"), first.classes().names());
        for (int sample = 0; sample < 3; sample++) {
            Class<?> type = new CandidateClassLoader(first.classes(), getClass().getClassLoader()).loadClass("Counter");
            type.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            Field verdict = type.getDeclaredField("FW_VAR");
            assertEquals(0, verdict.getInt(null), "a fresh loader per sample: the static counter starts at zero every time");
        }
        Compilation.Compiled again = assertInstanceOf(Compilation.Compiled.class, compiler.compile(COUNTER));
        assertTrue(again.cached(), "the bytecode is reused; the classes are not");
        assertEquals(first.classes().digest(), again.classes().digest());
        assertEquals(1, compiler.cacheStats().hits());
        assertEquals(1, compiler.cacheStats().misses());
    }

    @Test
    void compileErrorsKeepTheirPositionsAndTheCacheCanBeOff() {
        JavaSourceCompiler off = JavaSourceCompiler.create(List.of(), 0);
        Compilation.Failed failed = assertInstanceOf(Compilation.Failed.class,
                off.compile("public class Broken {\n  void m() { int x = ; }\n}\n"));
        assertTrue(failed.messages().getFirst().startsWith("line 2, column "), failed.messages().toString());
        assertFalse(assertInstanceOf(Compilation.Compiled.class, off.compile(COUNTER)).cached());
        assertFalse(assertInstanceOf(Compilation.Compiled.class, off.compile(COUNTER)).cached(), "cf.compiler.cache.maxBytes=0");
    }

    @Test
    void callsThatStopTheVirtualMachineAreFound() {
        JavaSourceCompiler compiler = JavaSourceCompiler.create(List.of(), 0);
        CompiledClasses exits = ((Compilation.Compiled) compiler.compile("""
                public class Exits { public static void main(String[] a) { if (a.length > 5) System.exit(3);
                  if (a.length > 6) Runtime.getRuntime().halt(4); } }
                """)).classes();
        assertEquals(List.of("Exits.main calls java/lang/System#exit", "Exits.main calls java/lang/Runtime#halt"), ForbiddenCalls.find(exits));
        assertEquals(List.of(), ForbiddenCalls.find(((Compilation.Compiled) compiler.compile(COUNTER)).classes()));
    }

    @Test
    void theDeclaredTypeIgnoresCommentsLiteralsTextBlocksAndNestedTypes() {
        assertEquals(Optional.of("Counter"), TypeNames.mainType(COUNTER));
        assertEquals(Optional.of("Real"), TypeNames.mainType("""
                /* public class InComment { } */
                class Helper { public class NotTopLevel { } }
                public final class Real { String s = \"\"\"
                    public class InTextBlock { }
                    \"\"\"; char c = '{'; }
                """));
        assertEquals(Optional.of("Helper"), TypeNames.mainType("class Helper { }"));
        assertEquals(Optional.empty(), TypeNames.mainType("int x;"));
    }
}
